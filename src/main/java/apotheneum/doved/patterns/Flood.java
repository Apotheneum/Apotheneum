package apotheneum.doved.patterns;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/doved")
@LXComponent.Name("Flood")
@LXComponent.Description("A continuous world-space waterline filling the cube and cylinder")
public class Flood extends ColorNativePattern {

  // waveRows' two-term sum (see OceanField#waveRows) has magnitude at most .82 + .42 = 1.24;
  // wavePhysics divides the raw wave value by the amplitude actually driving it times this
  // bound, rather than by the fixed bound alone, so the result is a shape-only signal that
  // reaches close to +-1 at a genuine crest or trough regardless of how small Agitate is
  // dialed in.
  private static final double WAVE_SHAPE_MAGNITUDE = .82 + .42;

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

  /** The surface/shallow end of the water ramp. Alias for {@link ColorNativePattern#primary}. */
  public final ColorRole surfaceColor;

  /** The deep end of the water ramp. Alias for {@link ColorNativePattern#secondary}. */
  public final ColorRole deepColor;

  private final OceanField.GeometryCache geometry = new OceanField.GeometryCache();

  private double wavePhase;
  private double elapsedSeconds;
  private boolean surgeActive;
  private double surgePosition;
  private double surgeStart;

  public Flood(LX lx) {
    super(lx, 1, .5, 2, .5);
    this.surfaceColor = this.primary;
    this.deepColor = this.secondary;
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
    updateViewMask();
    clearView();
    this.geometry.update();
    this.primary.update();
    this.secondary.update();

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
    final double baseSurfaceY = OceanField.surfaceY(
      level,
      this.geometry.floorY(),
      this.geometry.ceilingY(),
      this.geometry.rowPitch()
    );
    final double levelEnvelope = OceanField.levelEnvelope(level);
    final double effectiveAgitation = levelEnvelope * agitation;

    renderOrientation(
      Apotheneum.cube.exterior,
      baseSurfaceY,
      levelEnvelope,
      effectiveAgitation,
      OceanField.CUBE_S_OFFSET
    );
    renderOrientation(
      Apotheneum.cylinder.exterior,
      baseSurfaceY,
      levelEnvelope,
      effectiveAgitation,
      0
    );
    copyExteriorMasked(Apotheneum.cube.exterior, Apotheneum.cube.interior);
    copyExteriorMasked(Apotheneum.cylinder.exterior, Apotheneum.cylinder.interior);
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
    final double verticalRows =
      (this.geometry.ceilingY() - this.geometry.floorY()) / this.geometry.rowPitch() + 1;
    final int ringLength = orientation.columns().length;

    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final double s = OceanField.arcLength(columnIndex, columnOffset, ringLength);
      final double surge = this.surgeActive ? levelEnvelope * OceanField.surgeProfile(
        s,
        this.surgePosition,
        surgeWidth
      ) : 0;
      final double waveRows = OceanField.waveRows(s, this.wavePhase, effectiveAgitation);
      final double surfaceY = baseSurfaceY + this.geometry.rowPitch() * (
        waveRows + surgeHeight * surge
      );
      // Both roles are resolved once per column at the same physics argument -- the local
      // wave shape, not the per-row depth -- and lerped per row by depth below, following
      // LavaLamp's two-stop ramp with depth in place of temperature.
      final double physics = wavePhysics(waveRows, effectiveAgitation);
      final int surfaceColorAtPhysics = this.primary.color(physics);
      final int deepColorAtPhysics = this.secondary.color(physics);
      final int available = orientation.available(columnIndex);
      ++columnIndex;

      for (int row = 0; row < column.points.length; ++row) {
        if (!OceanField.isAvailableCell(row, available)) {
          continue;
        }

        final LXPoint point = column.points[row];
        final double signedRows = (surfaceY - point.y) / this.geometry.rowPitch();
        final double waterCoverage = OceanField.waterCoverage(signedRows);
        final double meniscus = levelEnvelope * OceanField.meniscus(signedRows, meniscusWidth);
        if (waterCoverage <= 0 && meniscus <= 0) {
          continue;
        }

        final double depth = LXUtils.clamp(signedRows / verticalRows, 0, 1);
        // LXColor.lerp(a, b, t) returns a at t=0, so primary (surface) goes first and this
        // depth term must stay 0-at-surface, 1-at-deep -- unchanged from the constant-color
        // lerp this replaces.
        final int depthColor = LXColor.lerp(
          surfaceColorAtPhysics,
          deepColorAtPhysics,
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
          meniscusBrightness += meniscus * OceanField.sparkle(
            point.index,
            this.elapsedSeconds,
            sparkle
          );
        }
        if (meniscusBrightness > 0) {
          // Foam is a fixed constant, not a palette role -- see OceanField#MENISCUS_COLOR.
          color = LXColor.lightest(
            color,
            LXColor.scaleBrightness(
              OceanField.MENISCUS_COLOR,
              (float) LXUtils.clamp(meniscusBrightness, 0, 1)
            )
          );
        }

        if (isViewPoint(point.index)) {
          this.colors[point.index] = color;
        }
      }
    }
  }

  /**
   * Local surface displacement relative to rest, as the signed [-1, 1] scalar the palette
   * roles couple to: crest positive, trough negative. {@code waveRowsValue} is already the
   * natural quantity Flood computes for the wave shape itself; dividing it by the amplitude
   * actually driving it (rather than clamping the raw, amplitude-scaled value) is what keeps
   * this from hugging zero at a small Agitate setting -- a full crest or trough reads as
   * physics near +-1 regardless of how much the wave is actually displacing the surface.
   * Returns exactly 0 at zero amplitude (Agitate effectively off) rather than dividing by
   * zero: a flat sea has no crest or trough to report.
   */
  static double wavePhysics(double waveRowsValue, double amplitude) {
    if (amplitude <= 0) {
      return 0;
    }
    return LXUtils.clamp(waveRowsValue / (amplitude * WAVE_SHAPE_MAGNITUDE), -1, 1);
  }

  /**
   * Mirrors the already-composited exterior colors onto the interior, but only within the
   * current view. {@code ApotheneumPattern.copyExterior()} is a raw arraycopy over whole
   * orientations with no {@code isViewPoint()} awareness (see {@link ViewMaskedPattern}'s
   * class javadoc), so a view that excludes the interior would still have it painted
   * underneath -- the same hole {@code FireballViewTest} covers for Fireball's old
   * {@code copyExterior()} call. Point-by-point with the same gate as every other write here
   * closes it.
   */
  private void copyExteriorMasked(Apotheneum.Orientation exterior, Apotheneum.Orientation interior) {
    if (interior == null) {
      return;
    }
    final Apotheneum.Column[] interiorColumns = interior.columns();
    int columnIndex = 0;
    for (Apotheneum.Column column : exterior.columns()) {
      final LXPoint[] interiorPoints = interiorColumns[columnIndex].points;
      for (int row = 0; row < column.points.length; ++row) {
        final int interiorIndex = interiorPoints[row].index;
        if (isViewPoint(interiorIndex)) {
          this.colors[interiorIndex] = this.colors[column.points[row].index];
        }
      }
      ++columnIndex;
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Flood",
      newKnob(this.level),
      newKnob(this.meniscusWidth)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Surface",
      newKnob(this.agitation),
      newKnob(this.sparkle)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Water",
      newKnob(this.depthFalloff)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Surge",
      newButton(this.surge).setTriggerable(true),
      newKnob(this.surgeSpeed),
      newKnob(this.surgeWidth)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Wave",
      newKnob(this.surgeHeight),
      newKnob(this.surgeAngle)
    ).setChildSpacing(6);

    buildColorDeviceControls(ui, uiDevice);
  }
}
