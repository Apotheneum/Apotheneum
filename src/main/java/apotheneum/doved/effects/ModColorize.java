package apotheneum.doved.effects;

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.effect.color.ColorizeEffect;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;

/**
 * A {@link ColorizeEffect} whose palette stop can be driven by a modulator.
 *
 * <p>The companion to {@link apotheneum.doved.patterns.ModGradient}, for the same reason and
 * by the same mechanism: stock {@code ColorizeEffect} builds its Index from
 * {@link heronarts.lx.color.LXPalette.IndexSelector}, a plain
 * {@link heronarts.lx.parameter.DiscreteParameter}, which no modulation can target. This
 * adds {@link #stop} beside it and writes through, leaving the inherited Index knob as a
 * readout.
 *
 * <p>Note this stays form-blind, as every Colorize is — it receives only {@code colors[]},
 * so a pattern's internal distinctions are already gone by the time it runs. Making the stop
 * modulatable changes which colour it applies, not what it can tell apart. Colour that has
 * to respect a pattern's own form still belongs in a
 * {@code ColorNativePattern} role.
 *
 * <p>{@code ColorizeEffect} registers {@code heronarts.lx.studio.ui.effect.UIColorizeEffect}
 * as its device UI in glxstudio, and that class is not visible to a subclass — it draws a
 * fixed set of controls built from the stock fields directly, with no extension point. Adding
 * {@link #stop} etc. beside {@code paletteIndex} therefore does nothing for the performer: the
 * new parameters work (they're on the rig, modulatable, verified over MCP) but nothing ever
 * draws a knob for them, and the panel still shows the superseded {@code paletteIndex} /
 * {@code paletteStops} / {@code paletteInvert} controls as if they were live. This class
 * implements {@link UIDeviceControls} to replace that panel outright rather than patch it.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Mod Colorize")
@LXComponent.Description("Colorize whose palette stop is a modulation target")
public class ModColorize extends ColorizeEffect implements UIDeviceControls<ModColorize> {

  public final CompoundDiscreteParameter stop =
    new CompoundDiscreteParameter("Stop", 1, 1, LXSwatch.MAX_COLORS + 1)
    .setDescription("Palette stop this effect reads; drives the stock Index, and unlike it can be modulated");

  /**
   * Defaults to 2, where the stock parameter defaults to {@code MAX_COLORS}. LX's default is
   * the worst case for this rig: a fresh Colorize ramps through every slot including the
   * accent, which reads as a colour choice rather than as a mistake and so goes unnoticed.
   * Six devices in the RobotHeart project were found sitting at 5 for exactly that reason.
   */
  public final CompoundDiscreteParameter stops =
    new CompoundDiscreteParameter("Stops", 2, 2, LXSwatch.MAX_COLORS + 1)
    .setDescription("How many palette stops to ramp across; drives the stock Stops, and unlike it can be modulated");

  public final CompoundDiscreteParameter invert =
    new CompoundDiscreteParameter("Invert", new String[] { "Off", "On" })
    .setDescription("Invert the palette gradient direction; drives the stock Invert, which as a BooleanParameter cannot itself be modulated");

  public ModColorize(LX lx) {
    super(lx);
    addParameter("stop", this.stop);
    addParameter("stops", this.stops);
    addParameter("invert", this.invert);
    writeThrough();
  }

  /** See {@link apotheneum.doved.patterns.ModGradient} for why this is per frame rather than
   * on a parameter listener: modulation moves the effective value without touching the base,
   * and listeners only fire on the base. */
  private void writeThrough() {
    this.paletteIndex.setValue(this.stop.getValuei());
    this.paletteStops.setValue(this.stops.getValuei());
    this.paletteInvert.setValue(this.invert.getValuei() > 0);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    writeThrough();
    super.run(deltaMs, enabledAmount);
  }

  @Override
  public void load(LX lx, com.google.gson.JsonObject obj) {
    super.load(lx, obj);
    // See ModGradient: this parameter is the source of truth, so re-assert it once the
    // inherited paletteIndex has been restored from the project file.
    writeThrough();
  }

  /** Row height for a horizontal group of {@link UIKnob}s, or a lone knob. */
  private static final float KNOB_ROW = UIKnob.HEIGHT;

  /** Row height for a horizontal group of {@code UISlider}s, or a slider mixed with a
   * dropdown — a rendered {@code render-ui} pass measured the stock slider's own height at
   * 30px. A column's declared width does not grow to fit a wider or taller child, so an
   * under-measured row silently spills into whatever comes after it instead of clipping
   * visibly; see {@link apotheneum.doved.patterns.ModGradient} for the render that caught it
   * the first time. */
  private static final float SLIDER_ROW = 30;

  /** Row height for a horizontal group of dropdowns (and {@code UIDoubleBox}, which matches
   * a dropdown's height) only, with no slider alongside them. */
  private static final float DROPDOWN_ROW = 16;

  /** {@code UIColorControl}'s own fixed size, measured the same way — 40 &times; 42, matching
   * {@link UIKnob}'s footprint rather than the width passed to
   * {@code newColorControl(param, width)}. That width argument turned out to be an X
   * position, not a size, in this version of glxstudio, so the calls below use the no-width
   * overload instead of implying a control over sizing that does not exist. */
  private static final float COLOR_ROW = UIKnob.HEIGHT;

  /**
   * Rebuilds {@code ColorizeEffect}'s panel, substituting this class's own
   * controls for the three the stock panel built from {@code paletteIndex}, {@code
   * paletteStops}, and {@code paletteInvert}. Everything else stock kept configurable —
   * {@code source}, {@code filterThreshold} / {@code filterMode}, {@code blendMode}, {@code
   * colorMode} (Fixed / Linked / Relative / Palette — still the switch that decides which of
   * the columns below actually does anything), both {@code ColorParameter} colour pickers, the
   * Linked and Relative H/S/B triplets, and {@code paletteDepth} — is carried over unchanged.
   *
   * <p>Not mode-conditional. Stock hides the Start/End columns that don't match the current
   * {@code colorMode}, rebuilding on every change so only the relevant controls are on screen.
   * This panel skips that: every column is always present. The gap that leaves is cosmetic —
   * a Linked-mode session still sees the Fixed colour picker doing nothing — not functional,
   * since nothing here is hidden that the current mode needs. Reproducing the rebuild-on-change
   * wiring stock uses (see {@code UIColorizeEffect}'s constructor-captured listener) would
   * mean adding and tearing down an {@code onParameterChanged} hook here for a decluttering
   * benefit only, which isn't worth the added state for a four-column panel that already fits
   * {@link heronarts.lx.studio.ui.device.UIDevice#CONTENT_HEIGHT} with room to spare.
   */
  @Override
  public void buildDeviceControls(UI ui, UIDevice device, ModColorize colorize) {
    device.setLayout(UI2dContainer.Layout.HORIZONTAL);
    device.setChildSpacing(4);

    // source gets an explicit 80px, not the 52px default: a render clipped its default
    // selection, "Brightness", to "Brightn". Stock UIColorizeEffect widens this same dropdown
    // to the same 80px for the same reason — SourceMode's longest option doesn't fit COL_WIDTH.
    addColumn(device, 140, "Mode",
      row(DROPDOWN_ROW, newDropMenu(colorize.source, 80), newDropMenu(colorize.blendMode)),
      row(SLIDER_ROW, newDropMenu(colorize.colorMode), newHorizontalSlider(colorize.amount)),
      row(DROPDOWN_ROW, newDoubleBox(colorize.filterThreshold), newDropMenu(colorize.filterMode))
    );

    addVerticalBreak(ui, device);

    addColumn(device, 168, "Start",
      row(COLOR_ROW, newColorControl(colorize.color1)),
      row(SLIDER_ROW,
        newHorizontalSlider(colorize.linkedHue),
        newHorizontalSlider(colorize.linkedSaturation),
        newHorizontalSlider(colorize.linkedBrightness))
    );

    addVerticalBreak(ui, device);

    // The replacement for the stock Index / Stops / Invert column: our modulatable stop,
    // stops, and invert as knobs — a knob is the one widget in this framework that draws a
    // modulation ring, which is the entire reason these exist as CompoundDiscreteParameter
    // instead of the plain DiscreteParameter stock uses. paletteDepth is untouched by
    // writeThrough(), so it stays as the stock slider rather than gaining a knob of its own.
    addColumn(device, 132, "Gradient",
      row(KNOB_ROW, newKnob(colorize.stop), newKnob(colorize.stops), newKnob(colorize.invert)),
      row(SLIDER_ROW, newHorizontalSlider(colorize.paletteDepth))
    );

    addVerticalBreak(ui, device);

    addColumn(device, 168, "End",
      row(COLOR_ROW, newColorControl(colorize.color2)),
      row(SLIDER_ROW,
        newHorizontalSlider(colorize.gradientHue),
        newHorizontalSlider(colorize.gradientSaturation),
        newHorizontalSlider(colorize.gradientBrightness))
    );
  }

  /**
   * A horizontal strip of controls. Packing every group of controls into one of these,
   * rather than letting them stack as the direct children of an {@code addColumn} container,
   * is what keeps each column under this repository's three-direct-control limit on a
   * vertical container — the check in {@code RenderDeviceUI} only counts a container's
   * immediate {@code UIParameterComponent} children, and a nested horizontal row is not one.
   */
  private static UI2dComponent row(float height, UI2dComponent... controls) {
    return UI2dContainer.newHorizontalContainer(height, 4, controls);
  }

}
