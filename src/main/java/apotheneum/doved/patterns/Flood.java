package apotheneum.doved.patterns;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/doved")
@LXComponent.Name("Flood")
@LXComponent.Description("A continuous world-space waterline filling the cube and cylinder")
public class Flood extends ColorNativePattern {

  // OceanField normalizes every turbulence spectrum to this fixed magnitude;
  // wavePhysics divides the raw wave value by the amplitude actually driving it times this
  // bound, rather than by the fixed bound alone, so the result is a shape-only signal that
  // reaches close to +-1 at a genuine crest or trough regardless of how small Agitate is
  // dialed in.
  private static final double WAVE_SHAPE_MAGNITUDE = OceanField.WAVE_AMPLITUDE_SUM;

  // Least-squares log fit of today's {.62, .34, .19, .11} amplitudes at k={2,3,5,8}.
  // That fixed spectrum was described as approximately 1/k, but its exact ratios fit p=1.23247.
  static final double DEFAULT_TURBULENCE = .5116872018738605;

  public final CompoundParameter level =
    new CompoundParameter("Level", 0, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Height of the waterline in world space");

  public final CompoundParameter meniscusWidth =
    new CompoundParameter("Width", 1.5, .5, 4)
    .setDescription("Width of the bright meniscus in LED rows");

  public final CompoundParameter agitation =
    new CompoundParameter("Agitate", .25, 0, 2)
    .setDescription("Amount and speed of waterline undulation");

  public final CompoundParameter turbulence =
    new CompoundParameter("Turbulence", DEFAULT_TURBULENCE, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Spectral distribution from rolling swell to detailed chop");

  public final CompoundParameter depthFalloff =
    new CompoundParameter("Depth", .65, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How quickly the water darkens with depth");

  public final CompoundParameter sparkle =
    new CompoundParameter("Sparkle", .25, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Density and brightness of glints on the meniscus");

  public final CompoundParameter foam =
    new CompoundParameter("Foam", .3, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How white and foamy the crest is: widens, brightens and whitens the waterline together");

  /** The surface/shallow end of the water ramp. Alias for {@link ColorNativePattern#primary}. */
  public final ColorRole surfaceColor;

  /** The deep end of the water ramp. Alias for {@link ColorNativePattern#secondary}. */
  public final ColorRole deepColor;

  private final OceanField.GeometryCache geometry = new OceanField.GeometryCache();

  private double wavePhase;
  private double elapsedSeconds;
  private final double[] waveAmplitudes = new double[OceanField.WAVE_NUMBERS.length];
  private double resolvedTurbulence = Double.NaN;

  public Flood(LX lx) {
    super(lx, 1, .5, 2, .5);
    this.surfaceColor = this.primary;
    this.deepColor = this.secondary;
    addParameter("level", this.level);
    addParameter("meniscusWidth", this.meniscusWidth);
    addParameter("agitation", this.agitation);
    addParameter("turbulence", this.turbulence);
    addParameter("depthFalloff", this.depthFalloff);
    addParameter("sparkle", this.sparkle);
    addParameter("foam", this.foam);
  }

  @Override
  protected void render(double deltaMs) {
    updateViewMask();
    clearView();
    this.geometry.update();
    this.primary.update();
    this.secondary.update();

    final double agitation = this.agitation.getValue();
    resolveWaveAmplitudes();
    // Deliberately NOT wrapped at TWO_PI. Each octave in OceanField#waveRows advances by
    // sqrt(k/2) * wavePhase, and those multipliers are irrational for k=3 and k=5 -- so
    // wrapping the shared phase at TWO_PI made those terms jump by a non-multiple of TWO_PI
    // every wrap, a visible snap in the surface roughly every 2 seconds at high agitation.
    // Letting the phase grow keeps every octave continuous. Double precision holds ~1e8
    // radians with better than 1e-8 resolution, which is years of continuous running at
    // these rates, so unbounded growth is not a practical concern.
    this.wavePhase += deltaMs * .001 * (.18 + 1.4 * agitation);
    this.elapsedSeconds += deltaMs * .001;

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
      this.waveAmplitudes,
      OceanField.CUBE_S_OFFSET
    );
    renderOrientation(
      Apotheneum.cylinder.exterior,
      baseSurfaceY,
      levelEnvelope,
      effectiveAgitation,
      this.waveAmplitudes,
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
      double[] waveAmplitudes,
      double columnOffset) {
    final double meniscusWidth = this.meniscusWidth.getValue();
    final double depthFalloff = this.depthFalloff.getValue();
    final double sparkle = this.sparkle.getValue();
    final double foam = this.foam.getValue();
    // Foam is a macro over the whole crest character, not just its color: it widens the band,
    // brightens it, and whitens it together, so one knob moves from a barely-there waterline in
    // the water's own hue to a broad blown-out white line. meniscusWidth stays the fine control
    // over the band's base size; this scales it.
    final double foamWidth = meniscusWidth * (.7 + .6 * foam);
    final double foamGain = .45 + .75 * foam;
    final double verticalRows =
      (this.geometry.ceilingY() - this.geometry.floorY()) / this.geometry.rowPitch() + 1;
    final int ringLength = orientation.columns().length;

    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final double s = OceanField.arcLength(columnIndex, columnOffset, ringLength);
      final double waveRows = OceanField.waveRows(
        s, this.wavePhase, effectiveAgitation, waveAmplitudes
      );
      final double surfaceY = baseSurfaceY + this.geometry.rowPitch() * waveRows;
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
        final double meniscus = levelEnvelope * OceanField.meniscus(signedRows, foamWidth);
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

        double meniscusBrightness = meniscus * (.72 + .2 * effectiveAgitation) * foamGain;
        if (sparkle > 0 && meniscus > 0) {
          meniscusBrightness += meniscus * OceanField.sparkle(
            point.index,
            this.elapsedSeconds,
            sparkle
          );
        }
        if (meniscusBrightness > 0) {
          // The waterline seen from below is bright *water*, not foam, until it is actually
          // breaking -- so the crest tone is a blend from this role's own surface color toward
          // pure white. At foam=0 the line reads as a lit crest in the palette's own hue; at
          // foam=1 it is genuinely white, for when the surface should read as breaking. The
          // target is white rather than a fixed pale-cyan constant so the top of the knob is
          // actually reachable; the old constant sat at 28% saturation and could never get
          // there. Still two palette roles -- this borrows the surface role, it does not add one.
          final int crestColor = LXColor.lerp(
            surfaceColorAtPhysics,
            LXColor.WHITE,
            (float) foam
          );
          color = LXColor.lightest(
            color,
            LXColor.scaleBrightness(
              crestColor,
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

  private void resolveWaveAmplitudes() {
    final double turbulence = this.turbulence.getValue();
    if (turbulence != this.resolvedTurbulence) {
      OceanField.resolveWaveAmplitudes(turbulence, this.waveAmplitudes);
      this.resolvedTurbulence = turbulence;
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
      newKnob(this.meniscusWidth),
      newKnob(this.foam)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Surface",
      newKnob(this.agitation),
      newKnob(this.turbulence),
      newKnob(this.sparkle)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Water",
      newKnob(this.depthFalloff)
    ).setChildSpacing(6);

    buildColorDeviceControls(ui, uiDevice);
  }
}
