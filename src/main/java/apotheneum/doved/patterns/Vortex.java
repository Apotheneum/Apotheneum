package apotheneum.doved.patterns;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXNormalizationBounds;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A perspective whirlpool with shearing spiral arms and a glowing throat.
 *
 * <p>The field is evaluated over the standard LX device view. Its axis and vertical extent come
 * from that view's normalization bounds, so tag-selected sculptures and arbitrary models receive
 * the same geometry-relative vortex without Apotheneum-specific projection or mirroring.
 *
 * <p>Viewed from inside the cylinder, sustained full-field rotation can produce circular
 * vection: the visual field rotating around the viewer reads as the viewer rotating, which can
 * prompt compensatory postural sway. Looking straight up makes this more provocative because the
 * head-back posture is already less stable and the apparent rotation is on the roll axis.
 *
 * <p>Vection builds over roughly 10–30 seconds, so use modulated bursts rather than sustained
 * rotation. Slow direction reversals are the worst case. Fast oscillation on the order of a few
 * seconds per cycle is less provocative because vection does not have time to establish.
 *
 * <p>Higher {@link #glow Glow} darkens the near, lower rows that occupy the visual periphery in
 * the lookup view, making it a comfort control as well as an aesthetic one.
 *
 * <p>The pattern has no internal clock: motion is entirely performer-driven by LX modulators on
 * the {@link #descent Fall}, {@link #spin Spin}, or {@link #wobblePhase Phase} positions, so the
 * default state is static and carries no continuous-motion risk. Fast or large LFOs and envelopes
 * on Fall or Spin can still produce provocative full-field translation or rotation; the modulator
 * wiring is the comfort boundary.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Vortex")
@LXComponent.Description("A perspective whirlpool intended for modulated bursts, not continuous rotation")
public class Vortex extends LXPattern
  implements UIDeviceControls<Vortex> {

  static final int LUT_SIZE = 1024;
  static final double TWO_PI = 2 * Math.PI;
  static final double INV_TWO_PI = 1 / TWO_PI;
  static final double SHEAR_GAIN = .5;
  // One thousandth of a model-space unit keeps axis points finite without visibly moving them.
  static final double RADIAL_EPSILON = 1e-3;
  private static final String LOG_PREFIX = "[Vortex] ";

  public enum Horizon {
    TOP {
      @Override
      double nearness(double u) {
        return u;
      }

      @Override
      double zeta(PointState state, int index) {
        return state.topZeta[index];
      }

      @Override
      double zetaSpan(PointState state) {
        return state.topZetaSpan;
      }

      @Override
      double funnelPower(PointState state, int index) {
        return state.topFunnelPower[index];
      }

      @Override
      double wobble(PointState state, int index, double cosPhase, double sinPhase,
        double cosSecondPhase, double sinSecondPhase) {

        return .6 * (
          state.topSin07[index] * cosPhase + state.topCos07[index] * sinPhase) +
          .4 * (
            state.topSin19[index] * cosSecondPhase -
            state.topCos19[index] * sinSecondPhase);
      }
    },
    BOTTOM {
      @Override
      double nearness(double u) {
        return 1 - u;
      }

      @Override
      double zeta(PointState state, int index) {
        return state.bottomZeta[index];
      }

      @Override
      double zetaSpan(PointState state) {
        return state.bottomZetaSpan;
      }

      @Override
      double funnelPower(PointState state, int index) {
        return state.bottomFunnelPower[index];
      }

      @Override
      double wobble(PointState state, int index, double cosPhase, double sinPhase,
        double cosSecondPhase, double sinSecondPhase) {

        return .6 * (
          state.bottomSin07[index] * cosPhase + state.bottomCos07[index] * sinPhase) +
          .4 * (
            state.bottomSin19[index] * cosSecondPhase -
            state.bottomCos19[index] * sinSecondPhase);
      }
    };

    abstract double nearness(double normalizedHeight);
    abstract double zeta(PointState state, int index);
    abstract double zetaSpan(PointState state);
    abstract double funnelPower(PointState state, int index);
    abstract double wobble(PointState state, int index, double cosPhase, double sinPhase,
      double cosSecondPhase, double sinSecondPhase);
  }

  static final class PointState {
    final int[] pointIndices;
    final int[] adjacent;
    final double[] azimuth;
    final double[] normalizedHeight;
    final double[] topZeta;
    final double[] bottomZeta;
    final double[] topFunnelPower;
    final double[] bottomFunnelPower;
    final double[] topSin07;
    final double[] topCos07;
    final double[] topSin19;
    final double[] topCos19;
    final double[] bottomSin07;
    final double[] bottomCos07;
    final double[] bottomSin19;
    final double[] bottomCos19;
    final double[] phase;
    final double[] env;
    double topZetaSpan;
    double bottomZetaSpan;

    PointState(int size) {
      this.pointIndices = new int[size];
      this.adjacent = new int[size];
      this.azimuth = new double[size];
      this.normalizedHeight = new double[size];
      this.topZeta = new double[size];
      this.bottomZeta = new double[size];
      this.topFunnelPower = new double[size];
      this.bottomFunnelPower = new double[size];
      this.topSin07 = new double[size];
      this.topCos07 = new double[size];
      this.topSin19 = new double[size];
      this.topCos19 = new double[size];
      this.bottomSin07 = new double[size];
      this.bottomCos07 = new double[size];
      this.bottomSin19 = new double[size];
      this.bottomCos19 = new double[size];
      this.phase = new double[size];
      this.env = new double[size];
    }
  }

  public final CompoundParameter descent =
    new CompoundParameter("Fall", 0)
    .setDescription(
      "Position through the full perspective depth; fast LFOs or envelopes can create " +
      "provocative full-field motion");

  public final CompoundParameter spin =
    new CompoundParameter("Spin", 0)
    .setDescription(
      "Rotation position over one full turn; fast LFOs or envelopes can cause dizziness and " +
      "postural sway when viewed from inside");

  public final CompoundParameter shear =
    new CompoundParameter("Shear", .5)
    .setDescription("How strongly the narrowing throat accelerates rotation");

  public final CompoundDiscreteParameter arms =
    new CompoundDiscreteParameter("Arms", 2, 0, 9)
    .setDescription("Number of spiral arms; zero produces horizontal freefall rings");

  public final CompoundParameter twist =
    new CompoundParameter("Twist", 6, 0, 8)
    .setDescription("Angular winding per radius of axial travel");

  public final CompoundParameter throat =
    new CompoundParameter("Throat", .25, .05, 1)
    .setDescription("Radius of the narrow end of the funnel");

  public final CompoundParameter wobble =
    new CompoundParameter("Wobble", .3)
    .setDescription("Amount the vortex centerline bends and snakes with depth");

  public final CompoundParameter wobblePhase =
    new CompoundParameter("Phase", 0)
    .setDescription("Position of the centerline wobble over one full turn");

  public final CompoundParameter sharp =
    new CompoundParameter("Sharp", .5)
    .setDescription("Sharpness of the spiral arms");

  public final CompoundParameter glow =
    new CompoundParameter("Glow", .7)
    .setDescription("Brightness lift toward the distant opening");

  public final EnumParameter<Horizon> horizon =
    new EnumParameter<Horizon>("Horizon", Horizon.TOP)
    .setDescription("End of the chamber containing the vanishing point");

  PointState pointState = new PointState(0);
  final double[] waveLut = new double[LUT_SIZE];

  private double lastSharp = Double.NaN;
  private int waveLutGeneration = 0;
  private int geometryLutGeneration = 0;
  private int armsValue = 0;
  private LXModel geometryModel;
  private LXModel viewModel;
  double geometryRebuildMs = 0;

  public Vortex(LX lx) {
    super(lx);
    addParameter("descent", this.descent);
    addParameter("spin", this.spin);
    addParameter("shear", this.shear);
    addParameter("arms", this.arms);
    addParameter("twist", this.twist);
    addParameter("throat", this.throat);
    addParameter("wobble", this.wobble);
    addParameter("wobblePhase", this.wobblePhase);
    addParameter("sharp", this.sharp);
    addParameter("glow", this.glow);
    addParameter("horizon", this.horizon);
    updateWaveLut();
  }

  private void ensureGeometryLut() {
    final LXModel geometryModel = this.lx.getModel();
    final LXModel viewModel = getModelView();
    if (this.geometryModel != geometryModel || this.viewModel != viewModel) {
      rebuildGeometryLut(geometryModel, viewModel);
    }
  }

  private void rebuildGeometryLut(LXModel geometryModel, LXModel viewModel) {
    final long startNanos = System.nanoTime();
    final PointState state = new PointState(viewModel.size);
    final LXNormalizationBounds bounds = viewModel.getNormalizationBounds();
    final double cx = bounds.cx;
    final double cz = bounds.cz;
    final double apexY = bounds.yMax;
    final double baseY = bounds.yMin;
    final double yRange = bounds.yRange;
    double minTopZeta = Double.POSITIVE_INFINITY;
    double maxTopZeta = Double.NEGATIVE_INFINITY;
    double minBottomZeta = Double.POSITIVE_INFINITY;
    double maxBottomZeta = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < viewModel.points.length; ++i) {
      final LXPoint point = viewModel.points[i];
      final double dx = point.x - cx;
      final double dz = point.z - cz;
      final double radial = radial(dx, dz);
      final double normalizedHeight = (yRange > 0) ?
        LXUtils.clamp((point.y - baseY) / yRange, 0, 1) : .5;
      final double topZeta = (apexY - point.y) / radial;
      final double bottomZeta = (point.y - baseY) / radial;

      state.pointIndices[i] = point.index;
      state.azimuth[i] = Math.atan2(dz, dx);
      state.normalizedHeight[i] = normalizedHeight;
      state.topZeta[i] = topZeta;
      state.bottomZeta[i] = bottomZeta;
      state.topFunnelPower[i] = Math.pow(1 - normalizedHeight, 1.5);
      state.bottomFunnelPower[i] = Math.pow(normalizedHeight, 1.5);
      cacheWobbleTerms(state, i, topZeta, true);
      cacheWobbleTerms(state, i, bottomZeta, false);
      minTopZeta = Math.min(minTopZeta, topZeta);
      maxTopZeta = Math.max(maxTopZeta, topZeta);
      minBottomZeta = Math.min(minBottomZeta, bottomZeta);
      maxBottomZeta = Math.max(maxBottomZeta, bottomZeta);
    }
    cacheAdjacentPoints(state, viewModel.points, bounds);
    state.topZetaSpan = zetaSpan(minTopZeta, maxTopZeta);
    state.bottomZetaSpan = zetaSpan(minBottomZeta, maxBottomZeta);

    this.geometryModel = geometryModel;
    this.viewModel = viewModel;
    this.pointState = state;
    ++this.geometryLutGeneration;
    this.geometryRebuildMs = (System.nanoTime() - startNanos) / 1_000_000.;
    log(String.format(Locale.ROOT,
      "geometry points=%d centerX=%.6f centerZ=%.6f apexY=%.6f baseY=%.6f " +
      "zetaSpan(top/bottom)=%.6f/%.6f radialEpsilon=%.6f rebuildMs=%.3f",
      viewModel.size, cx, cz, apexY, baseY,
      state.topZetaSpan, state.bottomZetaSpan, RADIAL_EPSILON, this.geometryRebuildMs));
  }

  private static double zetaSpan(double minimum, double maximum) {
    return (Double.isFinite(minimum) && Double.isFinite(maximum)) ? maximum - minimum : 0;
  }

  static double radial(double dx, double dz) {
    return Math.max(Math.hypot(dx, dz), RADIAL_EPSILON);
  }

  private static void cacheWobbleTerms(
    PointState state, int index, double zeta, boolean top) {

    final double phase07 = zeta * .7;
    final double phase19 = zeta * 1.9;
    if (top) {
      state.topSin07[index] = Math.sin(phase07);
      state.topCos07[index] = Math.cos(phase07);
      state.topSin19[index] = Math.sin(phase19);
      state.topCos19[index] = Math.cos(phase19);
    } else {
      state.bottomSin07[index] = Math.sin(phase07);
      state.bottomCos07[index] = Math.cos(phase07);
      state.bottomSin19[index] = Math.sin(phase19);
      state.bottomCos19[index] = Math.cos(phase19);
    }
  }

  private static void cacheAdjacentPoints(
    PointState state, LXPoint[] points, LXNormalizationBounds bounds) {

    if (points.length < 2) {
      Arrays.fill(state.adjacent, 0);
      return;
    }

    // Nyquist attenuation compares against a real nearby sample, not an arbitrary neighboring
    // array index. A coarse spatial hash keeps this geometry-agnostic and makes the one-time
    // lookup O(n) for normal fixture densities.
    final double maximumRange = Math.max(bounds.xRange, Math.max(bounds.yRange, bounds.zRange));
    final double cellSize = Math.max(RADIAL_EPSILON, maximumRange / Math.cbrt(points.length));
    final Map<Long, List<Integer>> cells = new HashMap<>();
    for (int i = 0; i < points.length; ++i) {
      final LXPoint point = points[i];
      final int cellX = cellCoordinate(point.x, bounds.xMin, cellSize);
      final int cellY = cellCoordinate(point.y, bounds.yMin, cellSize);
      final int cellZ = cellCoordinate(point.z, bounds.zMin, cellSize);
      cells.computeIfAbsent(cellKey(cellX, cellY, cellZ), key -> new ArrayList<>()).add(i);
    }

    for (int i = 0; i < points.length; ++i) {
      final LXPoint point = points[i];
      final int cellX = cellCoordinate(point.x, bounds.xMin, cellSize);
      final int cellY = cellCoordinate(point.y, bounds.yMin, cellSize);
      final int cellZ = cellCoordinate(point.z, bounds.zMin, cellSize);
      int nearest = -1;
      double nearestDistanceSquared = Double.POSITIVE_INFINITY;
      double nearestVerticalDistance = -1;
      for (int radius = 0; radius <= 2; ++radius) {
        for (int dx = -radius; dx <= radius; ++dx) {
          for (int dy = -radius; dy <= radius; ++dy) {
            for (int dz = -radius; dz <= radius; ++dz) {
              if (radius > 0 &&
                Math.abs(dx) < radius && Math.abs(dy) < radius && Math.abs(dz) < radius) {
                continue;
              }
              final List<Integer> candidates = cells.get(
                cellKey(cellX + dx, cellY + dy, cellZ + dz));
              if (candidates == null) {
                continue;
              }
              for (int candidate : candidates) {
                if (candidate == i) {
                  continue;
                }
                final double distanceSquared = distanceSquared(point, points[candidate]);
                final double verticalDistance = Math.abs(point.y - points[candidate].y);
                if (distanceSquared < nearestDistanceSquared ||
                  (Double.compare(distanceSquared, nearestDistanceSquared) == 0 &&
                    verticalDistance > nearestVerticalDistance)) {

                  nearest = candidate;
                  nearestDistanceSquared = distanceSquared;
                  nearestVerticalDistance = verticalDistance;
                }
              }
            }
          }
        }
        if (nearest >= 0) {
          break;
        }
      }
      if (nearest < 0) {
        // Extremely sparse or irregular views can defeat the estimated cell size. This fallback
        // is rare and runs only while rebuilding the view cache.
        for (int candidate = 0; candidate < points.length; ++candidate) {
          if (candidate == i) {
            continue;
          }
          final double distanceSquared = distanceSquared(point, points[candidate]);
          if (distanceSquared < nearestDistanceSquared) {
            nearest = candidate;
            nearestDistanceSquared = distanceSquared;
          }
        }
      }
      state.adjacent[i] = nearest;
    }
  }

  private static int cellCoordinate(double coordinate, double minimum, double cellSize) {
    return (int) Math.floor((coordinate - minimum) / cellSize);
  }

  private static long cellKey(int x, int y, int z) {
    return ((long) x & 0x1fffffL) << 42 |
      ((long) y & 0x1fffffL) << 21 |
      ((long) z & 0x1fffffL);
  }

  private static double distanceSquared(LXPoint a, LXPoint b) {
    final double dx = a.x - b.x;
    final double dy = a.y - b.y;
    final double dz = a.z - b.z;
    return dx * dx + dy * dy + dz * dz;
  }

  void updateWaveLut() {
    final double sharp = this.sharp.getValue();
    if (Double.compare(sharp, this.lastSharp) == 0) {
      return;
    }
    final double exponent = 1 + 8 * sharp;
    for (int i = 0; i < LUT_SIZE; ++i) {
      final double c = .5 - .5 * Math.cos(i * TWO_PI / LUT_SIZE);
      this.waveLut[i] = Math.pow(c, exponent);
    }
    this.lastSharp = sharp;
    ++this.waveLutGeneration;
  }

  int getWaveLutGeneration() {
    return this.waveLutGeneration;
  }

  int getGeometryLutGeneration() {
    return this.geometryLutGeneration;
  }

  void step() {
    ensureGeometryLut();
    updateWaveLut();
    this.armsValue = this.arms.getValuei();
    updatePoints(this.pointState);
  }

  private void updatePoints(PointState state) {
    final Horizon horizon = this.horizon.getEnum();
    final double throat = this.throat.getValue();
    final double shear = this.shear.getValue();
    final double twist = this.twist.getValue();
    final double wobble = this.wobble.getValue();
    final double descentPhase = this.descent.getValue() * horizon.zetaSpan(state);
    final double spinPhase = this.spin.getValue() * TWO_PI;
    final double wobblePhase = this.wobblePhase.getValue() * TWO_PI;
    final double cosWobblePhase = Math.cos(wobblePhase);
    final double sinWobblePhase = Math.sin(wobblePhase);
    final double cosSecondWobblePhase = Math.cos(wobblePhase * 1.37);
    final double sinSecondWobblePhase = Math.sin(wobblePhase * 1.37);

    for (int i = 0; i < state.phase.length; ++i) {
      final double zeta = horizon.zeta(state, i);
      final double radius = throat + (1 - throat) * horizon.funnelPower(state, i);
      final double boost = Math.min(1 / (radius * radius), 8);
      final double shearOffset = shear * SHEAR_GAIN * (boost - 1);
      final double wobbleOffset = wobble * horizon.wobble(
        state, i, cosWobblePhase, sinWobblePhase,
        cosSecondWobblePhase, sinSecondWobblePhase);

      state.phase[i] =
        -twist * (zeta + descentPhase) - spinPhase - shearOffset + wobbleOffset;
    }

    final double glow = this.glow.getValue();
    for (int i = 0; i < state.phase.length; ++i) {
      final double n = horizon.nearness(state.normalizedHeight[i]);
      final double gradient = Math.abs(state.phase[i] - state.phase[state.adjacent[i]]);
      final double nyquist = LXUtils.clamp(2 - gradient / Math.PI, 0, 1);
      final double depthShade = 1 - glow + glow * (1 - n);
      state.env[i] = depthShade * nyquist;
    }
  }

  double brightness(PointState state, int index) {
    final double phase = this.armsValue * state.azimuth[index] + state.phase[index];
    final int waveIndex = (int) (phase * INV_TWO_PI * LUT_SIZE) & (LUT_SIZE - 1);
    return LXUtils.clamp(this.waveLut[waveIndex] * state.env[index], 0, 1);
  }

  @Override
  protected void run(double deltaMs) {
    clearColors();
    step();
    int index = 0;
    for (LXPoint point : getModelView().points) {
      this.colors[point.index] = LXColor.grayn(brightness(this.pointState, index++));
    }
  }

  private static void log(String message) {
    LX.log(LOG_PREFIX + message);
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Vortex vortex) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);

    addColumn(uiDevice, "Position",
      newKnob(vortex.descent),
      newKnob(vortex.spin),
      newKnob(vortex.wobblePhase));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Shape",
      newKnob(vortex.shear),
      newKnob(vortex.arms),
      newKnob(vortex.twist));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Funnel",
      newKnob(vortex.throat),
      newKnob(vortex.wobble));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "View",
      newKnob(vortex.sharp),
      newKnob(vortex.glow),
      newDropMenu(vortex.horizon));
  }
}
