package apotheneum.doved.effects;

import java.util.IdentityHashMap;
import java.util.Map;

import apotheneum.doved.modulators.ColorizeStyle;
import heronarts.glx.ui.UI2dContainer;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.effect.color.ColorizeEffect;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;

/**
 * A stock {@link ColorizeEffect} whose parameters are supplied by one live
 * {@link ColorizeStyle} selected from the global modulation engine.
 */
@LXCategory(LXCategory.COLOR)
@LXComponent.Name("Linked Colorize")
@LXComponent.Description("Colorizes using a named, live Colorize Style")
public class LinkedColorize extends ColorizeEffect
  implements UIDeviceControls<LinkedColorize> {

  /** ObjectParameter requires at least one option; this is an explicit unlinked state. */
  private static final ColorizeStyle NO_STYLE = new ColorizeStyle("No Style");

  public final ObjectParameter<ColorizeStyle> style =
    new ObjectParameter<ColorizeStyle>("Style", new ColorizeStyle[] { NO_STYLE })
      .setDescription("The global Colorize Style this effect follows")
      .setMappable(false);

  private final LXModulationEngine.Listener modulationListener =
    new LXModulationEngine.Listener.Default() {
      @Override
      public void modulatorAdded(LXModulationEngine engine, LXModulator modulator) {
        refreshStyles();
      }

      @Override
      public void modulatorRemoved(LXModulationEngine engine, LXModulator modulator) {
        // LX notifies listeners before removing from its public list, so explicitly omit the
        // departing style rather than briefly leaving a stale selector entry behind.
        refreshStyles(modulator);
      }
    };

  private final Map<ColorizeStyle, LXParameterListener> styleLabelListeners =
    new IdentityHashMap<ColorizeStyle, LXParameterListener>();

  public LinkedColorize(LX lx) {
    super(lx);
    addParameter("style", this.style);
    lx.engine.modulation.addListener(this.modulationListener);
    refreshStyles();
  }

  /** Refreshes the selector without replacing a still-live selected style. */
  final void refreshStyles() {
    refreshStyles(null);
  }

  private void refreshStyles(LXModulator excluded) {
    int count = 1;
    for (LXModulator modulator : this.lx.engine.modulation.modulators) {
      if ((modulator != excluded) && (modulator instanceof ColorizeStyle)) {
        ++count;
      }
    }
    final ColorizeStyle[] styles = new ColorizeStyle[count];
    final String[] labels = new String[count];
    styles[0] = NO_STYLE;
    labels[0] = NO_STYLE.getLabel();
    int index = 1;
    for (LXModulator modulator : this.lx.engine.modulation.modulators) {
      if ((modulator != excluded) && (modulator instanceof ColorizeStyle colorizeStyle)) {
        styles[index++] = colorizeStyle;
      }
    }
    for (int i = 1; i < styles.length; ++i) {
      final ColorizeStyle colorizeStyle = styles[i];
      labels[i] = colorizeStyle.getLabel();
      if (!this.styleLabelListeners.containsKey(colorizeStyle)) {
        final LXParameterListener labelListener = parameter -> refreshStyles();
        colorizeStyle.label.addListener(labelListener);
        this.styleLabelListeners.put(colorizeStyle, labelListener);
      }
    }
    this.styleLabelListeners.entrySet().removeIf(entry -> {
      final ColorizeStyle colorizeStyle = entry.getKey();
      for (ColorizeStyle activeStyle : styles) {
        if (activeStyle == colorizeStyle) {
          return false;
        }
      }
      colorizeStyle.label.removeListener(entry.getValue());
      return true;
    });
    this.style.setObjects(styles, labels);
  }

  /** Applies the selected style's final (post-modulation) values to stock Colorize fields. */
  final void synchronizeStyle() {
    final ColorizeStyle selectedStyle = this.style.getObject();
    if ((selectedStyle == null) || (selectedStyle == NO_STYLE)) {
      return;
    }
    copy(this.source, selectedStyle.source);
    copy(this.blendMode, selectedStyle.blendMode);
    copy(this.colorMode, selectedStyle.colorMode);
    copy(this.color1, selectedStyle.color1);
    copy(this.color2, selectedStyle.color2);
    copy(this.gradientHue, selectedStyle.gradientHue);
    copy(this.gradientSaturation, selectedStyle.gradientSaturation);
    copy(this.gradientBrightness, selectedStyle.gradientBrightness);
    copy(this.linkedHue, selectedStyle.linkedHue);
    copy(this.linkedSaturation, selectedStyle.linkedSaturation);
    copy(this.linkedBrightness, selectedStyle.linkedBrightness);
    copy(this.paletteIndex, selectedStyle.paletteIndex);
    copy(this.paletteStops, selectedStyle.paletteStops);
    copy(this.paletteInvert, selectedStyle.paletteInvert);
    copy(this.paletteDepth, selectedStyle.paletteDepth);
    copy(this.amount, selectedStyle.amount);
    copy(this.filterThreshold, selectedStyle.filterThreshold);
    copy(this.filterMode, selectedStyle.filterMode);
  }

  private static <T extends Enum<T>> void copy(EnumParameter<T> target, EnumParameter<T> source) {
    target.setValue(source.getEnum());
  }

  private static void copy(ColorParameter target, ColorParameter source) {
    target.setColor(source.getColor());
  }

  private static void copy(CompoundParameter target, CompoundParameter source) {
    target.setValue(source.getValue());
  }

  private static void copy(DiscreteParameter target, DiscreteParameter source) {
    target.setValue(source.getValuei());
  }

  private static void copy(BooleanParameter target, BooleanParameter source) {
    target.setValue(source.isOn());
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    synchronizeStyle();
    super.run(deltaMs, enabledAmount);
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, LinkedColorize effect) {
    uiDevice.setLayout(UI2dContainer.Layout.HORIZONTAL, 4);
    addColumn(uiDevice, "Style", newDropMenu(effect.style));
  }

  @Override
  public void dispose() {
    this.lx.engine.modulation.removeListener(this.modulationListener);
    this.styleLabelListeners.forEach((colorizeStyle, listener) ->
      colorizeStyle.label.removeListener(listener));
    this.styleLabelListeners.clear();
    super.dispose();
  }
}
