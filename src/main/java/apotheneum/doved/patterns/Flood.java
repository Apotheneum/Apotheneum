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

  private final OceanField.GeometryCache geometry = new OceanField.GeometryCache();

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
    this.geometry.update();

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
      final double surfaceY = baseSurfaceY + this.geometry.rowPitch() * (
        OceanField.waveRows(s, this.wavePhase, effectiveAgitation) + surgeHeight * surge
      );
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
        final int depthColor = LXColor.lerp(
          OceanField.SURFACE_COLOR,
          OceanField.DEEP_COLOR,
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
          color = LXColor.lightest(
            color,
            LXColor.scaleBrightness(
              OceanField.MENISCUS_COLOR,
              (float) LXUtils.clamp(meniscusBrightness, 0, 1)
            )
          );
        }

        this.colors[point.index] = color;
      }
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
