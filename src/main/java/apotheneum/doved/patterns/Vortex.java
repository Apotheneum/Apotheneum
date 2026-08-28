package apotheneum.doved.patterns;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;
import java.util.Locale;

/**
 * A perspective whirlpool with shearing spiral arms and a glowing throat.
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
public class Vortex extends ApotheneumPattern
  implements UIDeviceControls<Vortex> {

  static final int LUT_SIZE = 1024;
  static final double TWO_PI = 2 * Math.PI;
  static final double INV_TWO_PI = 1 / TWO_PI;
  static final double SHEAR_GAIN = .5;
  /**
   * How much faster the arms wind at the throat than at the far end when {@link #curl Curl} is
   * at full travel. The warp is {@code 1 - (1 - t)^p} with {@code p = 1 + CURL_GAIN * curl},
   * whose gradient at the throat is exactly {@code p} -- so winding there scales linearly with
   * the control and turning Curl up always produces more curl.
   *
   * <p>An earlier form, {@code t^(1/(1+gain*curl))}, was not monotonic: its throat gradient
   * {@code e * t^(e-1)} peaks near {@code e = 0.18} and falls away again, so past roughly
   * half travel more Curl produced visibly less. Both forms preserve the endpoints; only this
   * one is monotonic in the control.
   */
  static final double CURL_GAIN = 20;
  private static final String LOG_PREFIX = "[Vortex] ";

  public enum Horizon {
    TOP {
      @Override
      double nearness(double u) {
        return u;
      }

      @Override
      double zeta(double rowY, double rowRadial, double apexY, double baseY) {
        return (apexY - rowY) / rowRadial;
      }

      @Override
      double zetaSpan(SurfaceState state) {
        return state.topZetaSpan;
      }

      @Override
      double zetaMin(SurfaceState state) {
        return state.topZetaMin;
      }
    },
    BOTTOM {
      @Override
      double nearness(double u) {
        return 1 - u;
      }

      @Override
      double zeta(double rowY, double rowRadial, double apexY, double baseY) {
        return (rowY - baseY) / rowRadial;
      }

      @Override
      double zetaSpan(SurfaceState state) {
        return state.bottomZetaSpan;
      }

      @Override
      double zetaMin(SurfaceState state) {
        return state.bottomZetaMin;
      }
    };

    abstract double nearness(double u);
    abstract double zeta(double rowY, double rowRadial, double apexY, double baseY);
    abstract double zetaSpan(SurfaceState state);
    abstract double zetaMin(SurfaceState state);
  }

  public enum Wrap {
    WORLD {
      @Override
      double azimuth(LXPoint point, int columnIndex, int columnCount, double cx, double cz) {
        return Math.atan2(point.z - cz, point.x - cx);
      }
    },
    UNWRAPPED {
      @Override
      double azimuth(LXPoint point, int columnIndex, int columnCount, double cx, double cz) {
        return columnIndex * TWO_PI / columnCount;
      }
    };

    abstract double azimuth(
      LXPoint point, int columnIndex, int columnCount, double cx, double cz);
  }

  static final class SurfaceState {
    final double[] azimuth;
    final double[] rowY;
    final double[] rowRadial;
    final double[] rowPhase;
    final double[] env;
    double topZetaSpan;
    double bottomZetaSpan;
    double topZetaMin;
    double bottomZetaMin;

    SurfaceState(int columns, int rows) {
      this.azimuth = new double[columns];
      this.rowY = new double[rows];
      this.rowRadial = new double[rows];
      this.rowPhase = new double[rows];
      this.env = new double[rows];
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
    .setDescription(
      "How strongly the narrowing throat accelerates rotation. Has no effect at Throat=1, "
      + "where the funnel does not narrow and there is nothing to accelerate");

  public final CompoundDiscreteParameter arms =
    new CompoundDiscreteParameter("Arms", 2, 0, 9)
    .setDescription("Number of spiral arms; zero produces horizontal freefall rings");

  public final CompoundParameter twist =
    new CompoundParameter("Twist", 6, 0, 20)
    .setDescription("Angular winding per radius of axial travel");

  public final CompoundParameter throat =
    new CompoundParameter("Throat", .25, .05, 1)
    .setDescription(
      "Radius of the narrow end of the funnel. Acts only through Shear, so it has no effect "
      + "at Shear=0; at Throat=1 the funnel is a cylinder and Shear itself goes flat");

  public final CompoundParameter curl =
    new CompoundParameter("Curl", 0)
    .setDescription(
      "Tightens the arms as they wind into the throat; zero keeps winding even along the axis");

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

  public final EnumParameter<Wrap> wrap =
    new EnumParameter<Wrap>("Wrap", Wrap.WORLD)
    .setDescription("World-space or evenly unwrapped column azimuth");

  public final BooleanParameter cube =
    new BooleanParameter("Cube", true)
    .setDescription("Whether the vortex appears on the cube");

  public final BooleanParameter cylinder =
    new BooleanParameter("Cylinder", true)
    .setDescription("Whether the vortex appears on the cylinder");

  final SurfaceState cubeState =
    new SurfaceState(Apotheneum.Cube.Ring.LENGTH, Apotheneum.GRID_HEIGHT);
  final SurfaceState cylinderState =
    new SurfaceState(Apotheneum.Cylinder.Ring.LENGTH, Apotheneum.CYLINDER_HEIGHT);
  final double[] waveLut = new double[LUT_SIZE];

  private double lastSharp = Double.NaN;
  private int waveLutGeneration = 0;
  private int armsValue = 0;
  private double spinAngle = 0;
  private LXModel geometryModel = null;
  double apexY = 0;
  double baseY = 0;

  public Vortex(LX lx) {
    super(lx);
    addParameter("descent", this.descent);
    addParameter("spin", this.spin);
    addParameter("shear", this.shear);
    addParameter("arms", this.arms);
    addParameter("twist", this.twist);
    addParameter("throat", this.throat);
    addParameter("curl", this.curl);
    addParameter("wobble", this.wobble);
    addParameter("wobblePhase", this.wobblePhase);
    addParameter("sharp", this.sharp);
    addParameter("glow", this.glow);
    addParameter("horizon", this.horizon);
    addParameter("wrap", this.wrap);
    addParameter("cube", this.cube);
    addParameter("cylinder", this.cylinder);
    updateWaveLut();
    rebuildGeometryLuts(this.model);
    rebuildAzimuthLuts(this.model);
    this.geometryModel = this.model;
  }

  /**
   * Marks the caches stale rather than rebuilding here.
   *
   * <p>{@code super(lx)} registers this listener before {@code Apotheneum.initialize(lx)}
   * registers the helper's own, so on a model reload this callback runs while
   * {@code Apotheneum.cube}/{@code cylinder} still reference the previous geometry. Rebuilding
   * now would cache stale azimuth and row geometry and never be corrected, since the helper's
   * later refresh does not trigger a second callback. Deferring to the next render — by which
   * point every model listener has run — is what guarantees the caches see current geometry.
   */
  @Override
  protected void onModelChanged(LXModel model) {
    this.geometryModel = null;
  }

  private void refreshGeometryIfStale() {
    if (this.geometryModel != this.model) {
      rebuildGeometryLuts(this.model);
      rebuildAzimuthLuts(this.model);
      this.geometryModel = this.model;
    }
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    super.onParameterChanged(parameter);
    if (parameter == this.wrap) {
      rebuildAzimuthLuts(this.model);
    }
  }

  /**
   * Mean X of a surface's own columns. The enclosing {@code LXModel} is not usable as the
   * vortex axis: the installation may carry additional fixtures (a separate sculpture, a
   * haptics array) that pull {@code model.cx}/{@code model.cz} away from the Apotheneum's
   * center, which would make arm spacing and rotation nonuniform even though the cube and
   * cylinder have not moved. Derive the axis from the surface geometry instead.
   */
  private static double surfaceCenterX(Apotheneum.Orientation orientation) {
    double sum = 0;
    final Apotheneum.Column[] columns = orientation.columns();
    for (Apotheneum.Column column : columns) {
      sum += column.points[0].x;
    }
    return sum / columns.length;
  }

  /** Mean Z of a surface's own columns. See {@link #surfaceCenterX}. */
  private static double surfaceCenterZ(Apotheneum.Orientation orientation) {
    double sum = 0;
    final Apotheneum.Column[] columns = orientation.columns();
    for (Apotheneum.Column column : columns) {
      sum += column.points[0].z;
    }
    return sum / columns.length;
  }

  private void rebuildAzimuthLuts(LXModel model) {
    if (!Apotheneum.exists) {
      return;
    }
    final Apotheneum.Orientation cube = Apotheneum.cube.exterior;
    final Apotheneum.Orientation cylinder = Apotheneum.cylinder.exterior;
    fillAzimuthLut(this.cubeState, cube, surfaceCenterX(cube), surfaceCenterZ(cube));
    fillAzimuthLut(
      this.cylinderState, cylinder, surfaceCenterX(cylinder), surfaceCenterZ(cylinder));
  }

  private void rebuildGeometryLuts(LXModel model) {
    if (!Apotheneum.exists) {
      return;
    }
    this.apexY = Double.NEGATIVE_INFINITY;
    this.baseY = Double.POSITIVE_INFINITY;
    includeVerticalBounds(Apotheneum.cube.exterior);
    includeVerticalBounds(Apotheneum.cylinder.exterior);

    final Apotheneum.Orientation cube = Apotheneum.cube.exterior;
    final Apotheneum.Orientation cylinder = Apotheneum.cylinder.exterior;
    fillRowGeometry(this.cubeState, cube, surfaceCenterX(cube), surfaceCenterZ(cube));
    fillRowGeometry(
      this.cylinderState, cylinder, surfaceCenterX(cylinder), surfaceCenterZ(cylinder));
    log(String.format(Locale.ROOT,
      "geometry apexY=%.6f baseY=%.6f " +
      "cubeZetaSpan(top/bottom)=%.6f/%.6f cubeZetaMin(top/bottom)=%.6f/%.6f " +
      "cylinderZetaSpan(top/bottom)=%.6f/%.6f cylinderZetaMin(top/bottom)=%.6f/%.6f",
      this.apexY, this.baseY,
      this.cubeState.topZetaSpan, this.cubeState.bottomZetaSpan,
      this.cubeState.topZetaMin, this.cubeState.bottomZetaMin,
      this.cylinderState.topZetaSpan, this.cylinderState.bottomZetaSpan,
      this.cylinderState.topZetaMin, this.cylinderState.bottomZetaMin));
  }

  private void includeVerticalBounds(Apotheneum.Orientation orientation) {
    for (Apotheneum.Column column : orientation.columns()) {
      for (LXPoint point : column.points) {
        this.apexY = Math.max(this.apexY, point.y);
        this.baseY = Math.min(this.baseY, point.y);
      }
    }
  }

  private void fillRowGeometry(
    SurfaceState state, Apotheneum.Orientation orientation, double cx, double cz) {

    final Apotheneum.Column[] columns = orientation.columns();
    double minTopZeta = Double.POSITIVE_INFINITY;
    double maxTopZeta = Double.NEGATIVE_INFINITY;
    double minBottomZeta = Double.POSITIVE_INFINITY;
    double maxBottomZeta = Double.NEGATIVE_INFINITY;
    for (int y = 0; y < state.rowY.length; ++y) {
      double ySum = 0;
      double radialSum = 0;
      for (Apotheneum.Column column : columns) {
        final LXPoint point = column.points[y];
        final double dx = point.x - cx;
        final double dz = point.z - cz;
        ySum += point.y;
        radialSum += Math.sqrt(dx * dx + dz * dz);
      }
      state.rowY[y] = ySum / columns.length;
      state.rowRadial[y] = radialSum / columns.length;
      if (state.rowRadial[y] <= 0) {
        throw new IllegalStateException("Vortex row radius must be positive at row " + y);
      }

      final double topZeta = Horizon.TOP.zeta(
        state.rowY[y], state.rowRadial[y], this.apexY, this.baseY);
      final double bottomZeta = Horizon.BOTTOM.zeta(
        state.rowY[y], state.rowRadial[y], this.apexY, this.baseY);
      minTopZeta = Math.min(minTopZeta, topZeta);
      maxTopZeta = Math.max(maxTopZeta, topZeta);
      minBottomZeta = Math.min(minBottomZeta, bottomZeta);
      maxBottomZeta = Math.max(maxBottomZeta, bottomZeta);
    }
    state.topZetaSpan = maxTopZeta - minTopZeta;
    state.bottomZetaSpan = maxBottomZeta - minBottomZeta;
    // Curl needs the surface's own near end, not zero. A surface whose top row sits below the
    // shared installation apex starts at a positive zeta, so normalizing by span alone would
    // never reach the steep end of the warp -- the tightest winding would fall above the
    // physical LEDs instead of on them.
    state.topZetaMin = minTopZeta;
    state.bottomZetaMin = minBottomZeta;
  }

  private void fillAzimuthLut(
    SurfaceState state, Apotheneum.Orientation orientation, double cx, double cz) {

    final Wrap wrap = this.wrap.getEnum();
    final Apotheneum.Column[] columns = orientation.columns();
    for (int x = 0; x < columns.length; ++x) {
      state.azimuth[x] = wrap.azimuth(columns[x].points[0], x, columns.length, cx, cz);
    }
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

  void step() {
    refreshGeometryIfStale();
    updateWaveLut();
    this.armsValue = this.arms.getValuei();
    this.spinAngle = this.spin.getValue() * TWO_PI;
    updateRows(this.cubeState);
    updateRows(this.cylinderState);
  }

  private void updateRows(SurfaceState state) {
    final int height = state.rowPhase.length;
    final Horizon horizon = this.horizon.getEnum();
    final double throat = this.throat.getValue();
    final double shear = this.shear.getValue();
    final double twist = this.twist.getValue();
    final double wobble = this.wobble.getValue();
    final double descentPhase = this.descent.getValue() * horizon.zetaSpan(state);
    final double wobblePhase = this.wobblePhase.getValue() * TWO_PI;

    // Curl compresses the axial coordinate toward the anchor end, so winding accelerates into
    // the throat instead of advancing at a constant rate. The geometry-derived zeta is linear
    // in height, which is physically honest but visually uniform; the earlier synthetic
    // perspective mapping produced a tight curl at the throat as a side effect of
    // double-counting perspective. This restores that look deliberately, as a stylization
    // rather than as a claim about distance. At Curl=0 the exponent is 1 and this is exactly
    // the linear geometry-anchored mapping, so the shipped default is unchanged.
    final double curlPower = 1 + CURL_GAIN * this.curl.getValue();
    final double zetaSpan = horizon.zetaSpan(state);
    final double zetaMin = horizon.zetaMin(state);
    final double invZetaSpan = (zetaSpan > 0) ? 1 / zetaSpan : 0;

    for (int y = 0; y < height; ++y) {
      final double u = (y + .5) / height;
      final double n = horizon.nearness(u);
      double zeta = horizon.zeta(
        state.rowY[y], state.rowRadial[y], this.apexY, this.baseY);
      if (curlPower != 1) {
        // Warp in normalized axial space so both endpoints are preserved: Fall still traverses
        // exactly one full span, and zetaSpan stays the correct descent range. Normalizing from
        // the surface's own near end rather than from zero is what puts the steep part of the
        // curve on the first real row instead of somewhere above the fixture.
        final double t = LXUtils.clamp((zeta - zetaMin) * invZetaSpan, 0, 1);
        zeta = zetaMin + zetaSpan * (1 - Math.pow(1 - t, curlPower));
      }
      final double radius = throat + (1 - throat) * Math.pow(1 - n, 1.5);
      final double boost = Math.min(1 / (radius * radius), 8);
      final double shearOffset = shear * SHEAR_GAIN * (boost - 1);
      final double wob = wobble * (
        // Both phase multipliers must be whole turns, or Phase does not close: a looping LFO
        // driving it 0 -> 1 would land the second term short of its starting value and jump
        // on wrap. The counter-sign and the differing zeta rates still keep the two terms
        // from moving together, which is what makes the centerline snake rather than swing.
        .6 * Math.sin(zeta * .7 + wobblePhase) +
        .4 * Math.sin(zeta * 1.9 - 2 * wobblePhase));

      // Spin is deliberately NOT folded in here. It is an azimuthal rotation, so it belongs
      // on the arm-dependent term in brightness(); adding it to the row phase would shift the
      // wave even at Arms=0, where the image is azimuthally invariant, turning a documented
      // rotation control into a second depth control that duplicates Fall.
      state.rowPhase[y] =
        -twist * (zeta + descentPhase) - shearOffset + wob;
    }

    final double glow = this.glow.getValue();
    for (int y = 0; y < height; ++y) {
      final double u = (y + .5) / height;
      final double n = horizon.nearness(u);
      final int adjacent = (y == 0) ? 1 : y - 1;
      final double gradient = Math.abs(state.rowPhase[y] - state.rowPhase[adjacent]);
      final double nyquist = LXUtils.clamp(2 - gradient / Math.PI, 0, 1);
      final double depthShade = 1 - glow + glow * (1 - n);
      state.env[y] = depthShade * nyquist;
    }
  }

  double brightness(SurfaceState state, int x, int y) {
    final double phase =
      this.armsValue * (state.azimuth[x] - this.spinAngle) + state.rowPhase[y];
    final int waveIndex = (int) (phase * INV_TWO_PI * LUT_SIZE) & (LUT_SIZE - 1);
    return LXUtils.clamp(this.waveLut[waveIndex] * state.env[y], 0, 1);
  }

  @Override
  protected void render(double deltaMs) {
    step();

    if (this.cube.isOn()) {
      renderSurface(Apotheneum.cube.exterior, this.cubeState);
    } else {
      setColor(Apotheneum.cube.exterior, LXColor.BLACK);
    }

    if (this.cylinder.isOn()) {
      renderSurface(Apotheneum.cylinder.exterior, this.cylinderState);
    } else {
      setColor(Apotheneum.cylinder.exterior, LXColor.BLACK);
    }

    copyExterior();
  }

  private void renderSurface(Apotheneum.Orientation orientation, SurfaceState state) {
    final Apotheneum.Column[] columns = orientation.columns();
    final int height = state.rowPhase.length;
    for (int x = 0; x < columns.length; ++x) {
      final Apotheneum.Column column = columns[x];
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        columnColor(column, y, LXColor.grayn(brightness(state, x, y)));
      }
      for (int y = available; y < height; ++y) {
        columnColor(column, y, LXColor.BLACK);
      }
    }
  }

  private void columnColor(Apotheneum.Column column, int y, int color) {
    this.colors[column.points[y].index] = color;
  }

  private static void log(String message) {
    LX.log(LOG_PREFIX + message);
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Vortex vortex) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 5);

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
      newKnob(vortex.curl),
      newKnob(vortex.wobble));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "View",
      newKnob(vortex.sharp),
      newKnob(vortex.glow),
      newDropMenu(vortex.horizon));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Surfaces",
      newDropMenu(vortex.wrap),
      newButton(vortex.cube),
      newButton(vortex.cylinder));
  }
}
