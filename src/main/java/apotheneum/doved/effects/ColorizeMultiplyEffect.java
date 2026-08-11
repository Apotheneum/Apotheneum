/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * Gradient configuration and parameter behavior are derived from
 * heronarts.lx.effect.color.ColorizeEffect. Rendering changes by Dan Oved.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 * @author Dan Oved
 */

package apotheneum.doved.effects;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.GradientUtils.BlendFunction;
import heronarts.lx.color.GradientUtils.BlendMode;
import heronarts.lx.color.GradientUtils.ColorStops;
import heronarts.lx.color.GradientUtils.GradientFunction;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.COLOR)
@LXComponent.Description("Colorizes by brightness while preserving the source form")
public class ColorizeMultiplyEffect extends LXEffect implements GradientFunction {

  public interface SourceFunction {
    float getLerpFactor(int argb);
  }

  public enum SourceMode {
    BRIGHTNESS("Brightness", (argb) -> LXColor.b(argb) * .01f),
    LUMINOSITY("Luminosity", (argb) -> LXColor.luminosity(argb) * .01f);

    private final String label;
    public final SourceFunction lerp;

    SourceMode(String label, SourceFunction lerp) {
      this.label = label;
      this.lerp = lerp;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum ColorMode {
    FIXED("Fixed"),
    RELATIVE("Relative"),
    LINKED("Linked"),
    PALETTE("Palette");

    private final String label;

    ColorMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum ThresholdMode {
    LEAVE("Leave"),
    BLACK("Black"),
    CLEAR("Clear");

    private final String label;

    ThresholdMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter depth =
    new CompoundParameter("Depth", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Blends from plain colorize RGB with source alpha retained to full brightness scaling");

  public final EnumParameter<SourceMode> source =
    new EnumParameter<SourceMode>("Source", SourceMode.BRIGHTNESS)
    .setDescription("Source value used to index and scale the gradient");

  public final CompoundParameter threshold =
    new CompoundParameter("Threshold", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Source values below this level are gated without remapping the gradient");

  public final EnumParameter<ThresholdMode> thresholdMode =
    new EnumParameter<ThresholdMode>("Threshold Mode", ThresholdMode.LEAVE)
    .setDescription("How to treat colors beneath the threshold");

  public final EnumParameter<BlendMode> blendMode =
    new EnumParameter<BlendMode>("Blend Mode", BlendMode.RGB)
    .setDescription("How colors are interpolated between gradient stops");

  public final EnumParameter<ColorMode> colorMode =
    new EnumParameter<ColorMode>("Color Mode", ColorMode.FIXED)
    .setDescription("Which source supplies the gradient colors");

  public final ColorParameter color1 =
    new ColorParameter("Color 1", 0xff000000)
    .setDescription("The first fixed or relative gradient color");

  public final ColorParameter color2 =
    new ColorParameter("Color 2", 0xffffffff)
    .setDescription("The second fixed gradient color");

  public final CompoundParameter gradientHue =
    new CompoundParameter("H-Offset", 0, -360, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Hue offset of the relative or linked gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter gradientSaturation =
    new CompoundParameter("S-Offset", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Saturation offset of the relative or linked gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter gradientBrightness =
    new CompoundParameter("B-Offset", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Brightness offset of the relative or linked gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedHue =
    new CompoundParameter("H-Linked", 0, -360, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Hue offset from the linked palette color")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedSaturation =
    new CompoundParameter("S-Linked", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Saturation offset from the linked palette color")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedBrightness =
    new CompoundParameter("B-Linked", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Brightness offset from the linked palette color")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final DiscreteParameter paletteIndex =
    new LXPalette.IndexSelector("Index")
    .setDescription("First palette color used by the gradient");

  public final DiscreteParameter paletteStops =
    new DiscreteParameter("Stops", LXSwatch.MAX_COLORS, 2, LXSwatch.MAX_COLORS + 1)
    .setDescription("Number of palette colors used by the gradient");

  public final BooleanParameter paletteInvert =
    new BooleanParameter("Invert", false)
    .setDescription("Reverses the palette gradient");

  public final CompoundParameter paletteDepth =
    new CompoundParameter("Palette Depth", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Fraction of the palette gradient used");

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Crossfade between the input and colorized result");

  private final ColorStops colorStops = new ColorStops();

  public ColorizeMultiplyEffect(LX lx) {
    super(lx);
    addParameter("depth", this.depth);
    addParameter("source", this.source);
    addParameter("threshold", this.threshold);
    addParameter("thresholdMode", this.thresholdMode);
    addParameter("blendMode", this.blendMode);
    addParameter("colorMode", this.colorMode);
    addParameter("color1", this.color1);
    addParameter("color2", this.color2);
    addParameter("gradientHue", this.gradientHue);
    addParameter("gradientSaturation", this.gradientSaturation);
    addParameter("gradientBrightness", this.gradientBrightness);
    addParameter("primaryHue", this.linkedHue);
    addParameter("primarySaturation", this.linkedSaturation);
    addParameter("primaryBrightness", this.linkedBrightness);
    addParameter("paletteIndex", this.paletteIndex);
    addParameter("paletteStops", this.paletteStops);
    addParameter("paletteInvert", this.paletteInvert);
    addParameter("paletteDepth", this.paletteDepth);
    addParameter("amount", this.amount);
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    if (parameter == this.colorMode ||
        parameter == this.color1 ||
        parameter == this.gradientHue ||
        parameter == this.gradientSaturation ||
        parameter == this.gradientBrightness ||
        parameter == this.linkedHue ||
        parameter == this.linkedSaturation ||
        parameter == this.linkedBrightness) {
      updateGradientParameters();
    }
  }

  private void updateGradientParameters() {
    if (this.colorMode.getEnum() == ColorMode.RELATIVE) {
      this.color2.brightness.setValue(this.color1.brightness.getValue() + this.gradientBrightness.getValue());
      this.color2.saturation.setValue(this.color1.saturation.getValue() + this.gradientSaturation.getValue());
      this.color2.hue.setValue((360 + this.color1.hue.getValue() + this.gradientHue.getValue()) % 360);
    } else if (this.colorMode.getEnum() == ColorMode.LINKED) {
      LXDynamicColor swatchColor = getSwatchColor();
      this.color1.brightness.setValue(swatchColor.getBrightness() + this.linkedBrightness.getValue());
      this.color1.saturation.setValue(swatchColor.getSaturation() + this.linkedSaturation.getValue());
      this.color1.hue.setValue((360 + swatchColor.getHue() + this.linkedHue.getValue()) % 360);
      this.color2.brightness.setValue(swatchColor.getBrightness() + this.gradientBrightness.getValue());
      this.color2.saturation.setValue(swatchColor.getSaturation() + this.gradientSaturation.getValue());
      this.color2.hue.setValue((360 + swatchColor.getHue() + this.gradientHue.getValue()) % 360);
    }
  }

  public LXDynamicColor getSwatchColor() {
    return this.lx.engine.palette.getSwatchColor(this.paletteIndex.getValuei() - 1);
  }

  private void updateColorStops() {
    switch (this.colorMode.getEnum()) {
      case FIXED -> {
        this.colorStops.stops[0].set(this.color1);
        this.colorStops.stops[1].set(this.color2);
        this.colorStops.setNumStops(2);
      }
      case RELATIVE -> {
        this.colorStops.stops[0].set(this.color1);
        this.colorStops.stops[1].set(this.color1,
          this.gradientHue.getValuef(),
          this.gradientSaturation.getValuef(),
          this.gradientBrightness.getValuef());
        this.colorStops.setNumStops(2);
      }
      case LINKED -> {
        LXDynamicColor swatchColor = getSwatchColor();
        this.colorStops.stops[0].set(swatchColor,
          this.linkedHue.getValuef(),
          this.linkedSaturation.getValuef(),
          this.linkedBrightness.getValuef());
        this.colorStops.stops[1].set(swatchColor,
          this.gradientHue.getValuef(),
          this.gradientSaturation.getValuef(),
          this.gradientBrightness.getValuef());
        this.colorStops.setNumStops(2);
      }
      case PALETTE -> this.colorStops.setPaletteGradient(
        this.lx.engine.palette,
        this.paletteIndex.getValuei() - 1,
        this.paletteStops.getValuei());
    }
  }

  @Override
  public int getGradientColor(float lerp) {
    lerp *= this.paletteDepth.getValuef();
    if (this.paletteInvert.isOn()) {
      lerp = 1 - lerp;
    }
    return this.colorStops.getColor(lerp, this.blendMode.getEnum().function);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    updateGradientParameters();
    updateColorStops();

    final float effectAmount = (float) (enabledAmount * this.amount.getValue());
    if (effectAmount == 0) {
      return;
    }

    final SourceFunction sourceFunction = this.source.getEnum().lerp;
    final BlendFunction blendFunction = this.blendMode.getEnum().function;
    final boolean palette = this.colorMode.getEnum() == ColorMode.PALETTE;
    final float gradientDepth = palette ? this.paletteDepth.getValuef() : 1;
    final boolean invert = palette && this.paletteInvert.isOn();
    final float depth = this.depth.getValuef();
    final boolean zeroDepth = depth == 0;
    final boolean fullDepth = depth == 1;
    final float threshold = this.threshold.getValuef();
    final ThresholdMode thresholdMode = this.thresholdMode.getEnum();

    for (LXPoint point : this.model.points) {
      final int index = point.index;
      final int original = this.colors[index];
      final float sourceValue = sourceFunction.getLerpFactor(original);

      if (sourceValue < threshold) {
        switch (thresholdMode) {
          case LEAVE -> { }
          case BLACK -> this.colors[index] = blendRgbPreserveAlpha(original, LXColor.BLACK, effectAmount);
          case CLEAR -> this.colors[index] = fadeAlpha(original, effectAmount);
        }
        continue;
      }

      // Exact zero is the core black-preservation guarantee, including at depth=0.
      if (sourceValue == 0) {
        this.colors[index] = blendRgbPreserveAlpha(original, LXColor.BLACK, effectAmount);
        continue;
      }

      float gradientLerp = sourceValue * gradientDepth;
      if (invert) {
        gradientLerp = 1 - gradientLerp;
      }
      final int gradientColor = this.colorStops.getColor(gradientLerp, blendFunction);
      final int depthColor;
      if (zeroDepth) {
        depthColor = gradientColor;
      } else {
        final int multipliedColor = LXColor.scaleBrightness(gradientColor, sourceValue);
        depthColor = fullDepth ? multipliedColor : lerpRgb(gradientColor, multipliedColor, depth);
      }
      this.colors[index] = blendRgbPreserveAlpha(original, depthColor, effectAmount);
    }
  }

  private static int fadeAlpha(int color, float amount) {
    if (amount == 1) {
      return color & LXColor.RGB_MASK;
    }
    final int alpha = LXUtils.lerpi(color >>> LXColor.ALPHA_SHIFT, 0, amount);
    return (alpha << LXColor.ALPHA_SHIFT) | (color & LXColor.RGB_MASK);
  }

  private static int blendRgbPreserveAlpha(int original, int target, float amount) {
    if (amount == 1) {
      return (original & LXColor.ALPHA_MASK) | (target & LXColor.RGB_MASK);
    }
    return (original & LXColor.ALPHA_MASK) | (lerpRgb(original, target, amount) & LXColor.RGB_MASK);
  }

  private static int lerpRgb(int from, int to, float amount) {
    final int red = LXUtils.lerpi(
      (from & LXColor.R_MASK) >>> LXColor.R_SHIFT,
      (to & LXColor.R_MASK) >>> LXColor.R_SHIFT,
      amount);
    final int green = LXUtils.lerpi(
      (from & LXColor.G_MASK) >>> LXColor.G_SHIFT,
      (to & LXColor.G_MASK) >>> LXColor.G_SHIFT,
      amount);
    final int blue = LXUtils.lerpi(from & LXColor.B_MASK, to & LXColor.B_MASK, amount);
    return (red << LXColor.R_SHIFT) | (green << LXColor.G_SHIFT) | blue;
  }
}
