package apotheneum.doved.modulators;

import heronarts.glx.ui.UI2dContainer;
import heronarts.lx.LXCategory;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.GradientUtils.BlendMode;
import heronarts.lx.color.LXPalette;
import heronarts.lx.effect.color.ColorizeEffect.ColorMode;
import heronarts.lx.effect.color.ColorizeEffect.FilterMode;
import heronarts.lx.effect.color.ColorizeEffect.SourceMode;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter.Polarity;
import heronarts.lx.parameter.LXParameter.Units;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;

/**
 * A global, named set of {@link heronarts.lx.effect.color.ColorizeEffect} settings.
 *
 * <p>Unlike an LX preset, a style remains live: every {@code Linked Colorize} assigned to it
 * reads these values every frame. Extending {@link LXModulator} gives the block an editable
 * label, project serialization, and compound parameters which accept normal LX modulation.
 */
@LXModulator.Global("Colorize Style")
@LXModulator.Device("Colorize Style")
@LXCategory(LXCategory.COLOR)
public class ColorizeStyle extends LXModulator implements UIModulatorControls<ColorizeStyle> {

  public final EnumParameter<SourceMode> source =
    new EnumParameter<SourceMode>("Source", SourceMode.BRIGHTNESS)
      .setDescription("Determines the source of the color mapping");

  public final EnumParameter<BlendMode> blendMode =
    new EnumParameter<BlendMode>("Blend Mode", BlendMode.RGB)
      .setDescription("Determines the mode of color blending");

  public final EnumParameter<ColorMode> colorMode =
    new EnumParameter<ColorMode>("Color Mode", ColorMode.FIXED)
      .setDescription("Which source the colors come from");

  public final ColorParameter color1 =
    new ColorParameter("Color 1", 0xff000000)
      .setDescription("The first color that is mapped from");

  public final ColorParameter color2 =
    new ColorParameter("Color 2", 0xffffffff)
      .setDescription("The second color that is mapped to");

  public final CompoundParameter gradientHue = offset("H-Offset", -360, 360, Units.DEGREES,
    "Amount of hue gradient");

  public final CompoundParameter gradientSaturation = offset("S-Offset", -100, 100,
    Units.PERCENT, "Amount of saturation gradient");

  public final CompoundParameter gradientBrightness = offset("B-Offset", -100, 100,
    Units.PERCENT, "Amount of brightness gradient");

  public final CompoundParameter linkedHue = offset("H-Linked", -360, 360, Units.DEGREES,
    "Hue offset from the selected palette color");

  public final CompoundParameter linkedSaturation = offset("S-Linked", -100, 100,
    Units.PERCENT, "Saturation offset from the selected palette color");

  public final CompoundParameter linkedBrightness = offset("B-Linked", -100, 100,
    Units.PERCENT, "Brightness offset from the selected palette color");

  public final DiscreteParameter paletteIndex =
    new LXPalette.IndexSelector("Index")
      .setDescription("Which index at the palette to start from");

  public final DiscreteParameter paletteStops =
    new DiscreteParameter("Stops", 5, 2, 6)
      .setDescription("How many color stops to use in the palette");

  public final BooleanParameter paletteInvert =
    new BooleanParameter("Invert", false)
      .setDescription("Invert the direction of the palette gradient");

  public final CompoundParameter paletteDepth = normalized("Depth", 1,
    "Depth of palette generation");

  public final CompoundParameter amount = normalized("Amount", 1, "Depth of colorization");

  public final CompoundParameter filterThreshold = normalized("Cutoff", 0,
    "Threshold at which to apply colorization");

  public final EnumParameter<FilterMode> filterMode =
    new EnumParameter<FilterMode>("Filter Mode", FilterMode.LEAVE)
      .setDescription("How to treat colors beneath filter threshold");

  public ColorizeStyle() {
    this("Colorize Style");
  }

  public ColorizeStyle(String label) {
    super(label);
    addParameter("source", this.source);
    addParameter("gradientHue", this.gradientHue);
    addParameter("gradientSaturation", this.gradientSaturation);
    addParameter("gradientBrightness", this.gradientBrightness);
    addParameter("colorMode", this.colorMode);
    addParameter("blendMode", this.blendMode);
    addParameter("color1", this.color1);
    addParameter("color2", this.color2);
    addParameter("paletteIndex", this.paletteIndex);
    addParameter("paletteStops", this.paletteStops);
    addParameter("paletteInvert", this.paletteInvert);
    addParameter("paletteDepth", this.paletteDepth);
    addParameter("primaryHue", this.linkedHue);
    addParameter("primarySaturation", this.linkedSaturation);
    addParameter("primaryBrightness", this.linkedBrightness);
    addParameter("amount", this.amount);
    addParameter("filterThreshold", this.filterThreshold);
    addParameter("filterMode", this.filterMode);
    setDescription("Named Colorize settings shared live by Linked Colorize effects");
    setMappingSource(false);
  }

  private static CompoundParameter offset(
    String label, double minimum, double maximum, Units units, String description) {
    return new CompoundParameter(label, 0, minimum, maximum)
      .setUnits(units)
      .setDescription(description)
      .setPolarity(Polarity.BIPOLAR);
  }

  private static CompoundParameter normalized(String label, double value, String description) {
    return new CompoundParameter(label, value)
      .setUnits(Units.PERCENT_NORMALIZED)
      .setDescription(description);
  }

  @Override
  protected double computeValue(double deltaMs) {
    return 0;
  }

  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, ColorizeStyle style) {
    uiModulator.setLayout(UI2dContainer.Layout.HORIZONTAL, 4);
    addColumn(uiModulator, "Mode",
      newDropMenu(style.source),
      newDropMenu(style.colorMode),
      newDropMenu(style.blendMode));
    addColumn(uiModulator, "Color",
      newColorControl(style.color1),
      newColorControl(style.color2));
    addColumn(uiModulator, "Level",
      newKnob(style.amount),
      newKnob(style.filterThreshold),
      newDropMenu(style.filterMode));
  }
}
