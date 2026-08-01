package apotheneum.drmrrdmr;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;

@LXCategory("Apotheneum/drmrrdmr")
@LXComponent.Name("Aux Fader")
@LXComponent.Description("A simple brightness fader")
public class AuxFader extends LXEffect implements UIDeviceControls<AuxFader> {

  public final CompoundParameter level = new CompoundParameter("Level", 1, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Fader level");

  public AuxFader(LX lx) {
    super(lx);
    addParameter("level", this.level);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    if (enabledAmount <= 0) {
      return;
    }
    // Interpolate from neutral (1 = no dimming), not from black - otherwise
    // enabling/disabling the effect fades to/from black regardless of the
    // Level setting, instead of fading between "no effect" and "dimmed to
    // Level".
    float amount = (float) LXUtils.lerp(1.0, this.level.getValue(), enabledAmount);
    if (amount >= 1f) {
      return;
    }
    for (int i = 0; i < this.colors.length; ++i) {
      this.colors[i] = LXColor.scaleBrightness(this.colors[i], amount);
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, AuxFader auxFader) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);
    addColumn(uiDevice,
      "Fader",
      newVerticalSlider(auxFader.level, 100).setShowLabel(false)
    ).setChildSpacing(4);
  }

}
