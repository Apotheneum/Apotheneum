package apotheneum.doved.patterns;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/doved")
@LXComponent.Name("Flood")
@LXComponent.Description("A continuous world-space waterline filling the cube and cylinder")
public class Flood extends ApotheneumPattern implements UIDeviceControls<Flood> {

  private static final int SURFACE_COLOR = LXColor.hsb(186, 82, 82);
  private static final int DEEP_COLOR = LXColor.hsb(224, 100, 24);
  private static final int MENISCUS_COLOR = LXColor.hsb(178, 28, 100);

  /**
   * Column-index offset that aligns arc-length 0 on the cube ring with arc-length
   * 0 on the cylinder ring, so azimuthal terms stay in phase across the seam.
   *
   * Cube ring order is front(0-49), right(50-99), back(100-149), left(150-199)
   * (Apotheneum.Cube.Orientation). Cylinder column 0 sits at world (x, z) =
   * (cubeSide/2, cubeSide/2 - radius) - straight out from the shared center
   * toward the front face (Apotheneum.lxf). Front-face column i sits at world x
   * = nodeInset + i * nodeSpacing, which equals cubeSide/2 (the same azimuth) at
   * i = (GRID_WIDTH - 1) / 2.0 = 24.5, for any nodeInset/nodeSpacing, because the
   * 50 columns are spaced evenly across the symmetric [nodeInset, cubeSide -
   * nodeInset] interval. Both rings also increase in the same rotational sense
   * (front -> right -> back -> left matches increasing cylinder azimuth), so no
   * direction flip is needed - only this offset.
   */
  private static final double CUBE_S_OFFSET = (Apotheneum.GRID_WIDTH - 1) / 2.0;

  public final CompoundParameter level =
    new CompoundParameter("Level", 0, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Height of the waterline in world space");

  public final CompoundParameter meniscusWidth =
    new CompoundParameter("Width", 1.5, .5, 4)
    .setDescription("Width of the bright meniscus in LED rows");

  public final CompoundParameter agitation =
    new CompoundParameter("Agitate", .25, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount and speed of waterline undulation");

  public final CompoundParameter depthFalloff =
    new CompoundParameter("Depth", .65, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How quickly the water darkens with depth");

  public final CompoundParameter sparkle =
    new CompoundParameter("Sparkle", .25, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Density and brightness of glints on the meniscus");

  public final CompoundParameter surgeSpeed =
    new CompoundParameter("S Speed", .16, .03, .8)
    .setDescription("Travel speed of the surge in ring laps per second");

  public final CompoundParameter surgeWidth =
    new CompoundParameter("S Width", .06, .015, .5)
    .setDescription("Width of the traveling surge as a fraction of one full lap around the ring");

  public final CompoundParameter surgeHeight =
    new CompoundParameter("S Height", 5, .5, 12)
    .setDescription("Height of the traveling surge in LED rows");

  public final CompoundParameter surgeAngle =
    new CompoundParameter("S Angle", 0, 0, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setWrappable(true)
    .setDescription("Starting azimuth of the surge around the ring");

  public final TriggerParameter surge =
    new TriggerParameter("Surge", this::triggerSurge)
    .setDescription("Launch or restart the traveling surge");

  private Apotheneum.Cube cachedCube;
  private Apotheneum.Cylinder cachedCylinder;
  private double floorY;
  private double ceilingY;
  private double rowPitch;
  private final Bounds geometryBounds = new Bounds();

  private double wavePhase;
  private double elapsedSeconds;
  private boolean surgeActive;
  private double surgePosition;
  private double surgeStart;

  public Flood(LX lx) {
    super(lx);
    addParameter("level", this.level);
    addParameter("meniscusWidth", this.meniscusWidth);
    addParameter("agitation", this.agitation);
    addParameter("depthFalloff", this.depthFalloff);
    addParameter("sparkle", this.sparkle);
    addParameter("surgeSpeed", this.surgeSpeed);
    addParameter("surgeWidth", this.surgeWidth);
    addParameter("surgeHeight", this.surgeHeight);
    addParameter("surgeAngle", this.surgeAngle);
    addParameter("surge", this.surge);
  }

  private void triggerSurge() {
    final double startS = this.surgeAngle.getValue() / 360.0;
    this.surgePosition = startS - .5 * this.surgeWidth.getValue();
    this.surgeStart = this.surgePosition;
    this.surgeActive = true;
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    updateGeometryCache();

    final double agitation = this.agitation.getValue();
    this.wavePhase = (this.wavePhase + deltaMs * .001 * (.18 + 1.4 * agitation)) % LX.TWO_PI;
    this.elapsedSeconds += deltaMs * .001;

    if (this.surgeActive) {
      this.surgePosition += deltaMs * .001 * this.surgeSpeed.getValue();
      final double travel = 1 + this.surgeWidth.getValue();
      if (this.surgePosition - this.surgeStart > travel) {
        this.surgeActive = false;
      }
    }

    final double level = this.level.getValue();
    final double baseSurfaceY = FloodField.surfaceY(level, this.floorY, this.ceilingY, this.rowPitch);
    final double levelEnvelope = FloodField.levelEnvelope(level);
    final double effectiveAgitation = levelEnvelope * agitation;

    renderOrientation(
      Apotheneum.cube.exterior,
      baseSurfaceY,
      levelEnvelope,
      effectiveAgitation,
      CUBE_S_OFFSET
    );
    renderOrientation(
      Apotheneum.cylinder.exterior,
      baseSurfaceY,
      levelEnvelope,
      effectiveAgitation,
      0
    );
    copyExterior();
  }

  private void renderOrientation(
      Apotheneum.Orientation orientation,
      double baseSurfaceY,
      double levelEnvelope,
      double effectiveAgitation,
      double columnOffset) {
    final double meniscusWidth = this.meniscusWidth.getValue();
    final double depthFalloff = this.depthFalloff.getValue();
    final double sparkle = this.sparkle.getValue();
    final double surgeWidth = this.surgeWidth.getValue();
    final double surgeHeight = this.surgeHeight.getValue();
    final double verticalRows = (this.ceilingY - this.floorY) / this.rowPitch + 1;
    final int ringLength = orientation.columns().length;

    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final double s = FloodField.arcLength(columnIndex, columnOffset, ringLength);
      final double surge = this.surgeActive ? levelEnvelope * FloodField.surgeProfile(
        s,
        this.surgePosition,
        surgeWidth
      ) : 0;
      final double surfaceY = baseSurfaceY + this.rowPitch * (
        FloodField.waveRows(s, this.wavePhase, effectiveAgitation) + surgeHeight * surge
      );
      final int available = orientation.available(columnIndex);
      ++columnIndex;

      for (int row = 0; row < column.points.length; ++row) {
        if (!FloodField.isAvailableCell(row, available)) {
          continue;
        }

        final LXPoint point = column.points[row];
        final double signedRows = (surfaceY - point.y) / this.rowPitch;
        final double waterCoverage = FloodField.waterCoverage(signedRows);
        final double meniscus = levelEnvelope * FloodField.meniscus(signedRows, meniscusWidth);
        if (waterCoverage <= 0 && meniscus <= 0) {
          continue;
        }

        final double depth = LXUtils.clamp(signedRows / verticalRows, 0, 1);
        final int depthColor = LXColor.lerp(
          SURFACE_COLOR,
          DEEP_COLOR,
          (float) Math.sqrt(depth)
        );
        final double attenuation = LXUtils.lerp(
          1,
          .16 + .84 * Math.exp(-3.2 * depth),
          depthFalloff
        );
        int color = LXColor.scaleBrightness(
          depthColor,
          (float) (waterCoverage * attenuation)
        );

        double meniscusBrightness = meniscus * (.72 + .2 * effectiveAgitation + .28 * surge);
        if (sparkle > 0 && meniscus > 0) {
          meniscusBrightness += meniscus * FloodField.sparkle(
            point.index,
            this.elapsedSeconds,
            sparkle
          );
        }
        if (meniscusBrightness > 0) {
          color = LXColor.lightest(
            color,
            LXColor.scaleBrightness(
              MENISCUS_COLOR,
              (float) LXUtils.clamp(meniscusBrightness, 0, 1)
            )
          );
        }

        this.colors[point.index] = color;
      }
    }
  }

  private void updateGeometryCache() {
    if ((this.cachedCube == Apotheneum.cube) &&
        (this.cachedCylinder == Apotheneum.cylinder)) {
      return;
    }

    this.cachedCube = Apotheneum.cube;
    this.cachedCylinder = Apotheneum.cylinder;

    this.geometryBounds.reset();
    includeGeometry(Apotheneum.cube.exterior);
    includeGeometry(Apotheneum.cylinder.exterior);

    this.floorY = this.geometryBounds.minY;
    this.ceilingY = this.geometryBounds.maxY;

    final LXPoint[] referenceColumn = Apotheneum.cube.exterior.columns[0].points;
    this.rowPitch = Math.abs(referenceColumn[1].y - referenceColumn[0].y);
    if (this.rowPitch <= 0) {
      this.rowPitch = 1;
    }
  }

  private void includeGeometry(Apotheneum.Orientation orientation) {
    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final int available = orientation.available(columnIndex++);
      for (int row = 0; row < available; ++row) {
        this.geometryBounds.include(column.points[row]);
      }
    }
  }

  private static final class Bounds {
    double minY;
    double maxY;

    void reset() {
      this.minY = Double.POSITIVE_INFINITY;
      this.maxY = Double.NEGATIVE_INFINITY;
    }

    void include(LXPoint point) {
      this.minY = Math.min(this.minY, point.y);
      this.maxY = Math.max(this.maxY, point.y);
    }
  }

  static final class FloodField {

    private FloodField() {}

    static double surfaceY(double level, double floorY, double ceilingY, double rowPitch) {
      return floorY - .5 * rowPitch + level * (ceilingY - floorY + rowPitch);
    }

    static double levelEnvelope(double level) {
      return Math.sin(Math.PI * level);
    }

    static boolean isAvailableCell(int row, int available) {
      return row >= 0 && row < available;
    }

    static double waterCoverage(double signedRows) {
      return smoothstep(-.5, .5, signedRows);
    }

    static double meniscus(double signedRows, double widthRows) {
      final double halfWidth = .5 * widthRows;
      return 1 - smoothstep(halfWidth, halfWidth + 1, Math.abs(signedRows));
    }

    /**
     * Normalized arc-length position around a ring: 0..1, wrapping. columnIndex
     * is the column's position in orientation.columns() order; columnOffset
     * shifts which column lands at s = 0 (see Flood.CUBE_S_OFFSET); ringLength is
     * the column count for that orientation's ring (200 cube, 120 cylinder).
     */
    static double arcLength(int columnIndex, double columnOffset, int ringLength) {
      final double s = (columnIndex - columnOffset) / ringLength;
      return s - Math.floor(s);
    }

    /**
     * Azimuthal undulation as a function of arc length s (0..1, wrapping).
     * Integer wavenumbers make s = 0 and s = 1 agree exactly, so the seam at the
     * ring's start/end closes with no discontinuity - see docs/ocean-and-organic-
     * patterns.md section 1.1.
     */
    static double waveRows(double s, double phase, double amount) {
      return amount * (
        .82 * Math.sin(LX.TWO_PI * 2 * s + phase) +
        .42 * Math.sin(LX.TWO_PI * 3 * s - .71 * phase + 1.37)
      );
    }

    /**
     * Raised-cosine falloff of a surge centered at `position`, both in arc-length
     * s (0..1). Distance wraps across the s = 0 / s = 1 seam so the surge reads
     * as travelling around the ring rather than stopping at an artificial edge.
     * `position` need not itself be pre-wrapped - it is taken modulo 1 here - so
     * callers can track it as a monotonically increasing value while active.
     */
    static double surgeProfile(double s, double position, double width) {
      final double halfWidth = .5 * width;
      final double wrappedPosition = position - Math.floor(position);
      double distance = Math.abs(s - wrappedPosition);
      distance = Math.min(distance, 1 - distance);
      if (distance >= halfWidth) {
        return 0;
      }
      return .5 + .5 * Math.cos(Math.PI * distance / halfWidth);
    }

    static double sparkle(int pointIndex, double elapsedSeconds, double amount) {
      final double pointOffset = hash01(pointIndex * 0x1f123bb5);
      final double localTime = elapsedSeconds * 2.2 + pointOffset;
      final int cycle = (int) Math.floor(localTime);
      final double basis = localTime - cycle;
      final double gate = hash01(pointIndex * 0x6d2b79f5 ^ cycle * 0x1b873593);
      if (gate < 1 - .12 * amount) {
        return 0;
      }
      final double envelope = Math.sin(Math.PI * basis);
      return amount * envelope * envelope * (.55 + .45 * hash01(pointIndex ^ cycle));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
      final double t = LXUtils.clamp((value - edge0) / (edge1 - edge0), 0, 1);
      return t * t * (3 - 2 * t);
    }

    private static double hash01(int value) {
      int hash = value;
      hash ^= hash >>> 16;
      hash *= 0x7feb352d;
      hash ^= hash >>> 15;
      hash *= 0x846ca68b;
      hash ^= hash >>> 16;
      return (hash & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Flood flood) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Flood",
      newKnob(flood.level),
      newKnob(flood.meniscusWidth)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Surface",
      newKnob(flood.agitation),
      newKnob(flood.sparkle)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Water",
      newKnob(flood.depthFalloff)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Surge",
      newButton(flood.surge).setTriggerable(true),
      newKnob(flood.surgeSpeed),
      newKnob(flood.surgeWidth)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Wave",
      newKnob(flood.surgeHeight),
      newKnob(flood.surgeAngle)
    ).setChildSpacing(6);
  }
}
