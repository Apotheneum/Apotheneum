package apotheneum.doved.patterns;

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.pattern.color.GradientPattern;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;

/**
 * A {@link GradientPattern} whose palette stop can be driven by a modulator.
 *
 * <p>Stock {@code GradientPattern} builds its Index control from
 * {@link heronarts.lx.color.LXPalette.IndexSelector}, which extends
 * {@link heronarts.lx.parameter.DiscreteParameter}. Only {@code CompoundParameter} and
 * {@code CompoundDiscreteParameter} implement {@code LXCompoundModulation.Target}, so
 * nothing can modulate it — no knob, no LFO, no macro. That is the one thing standing
 * between this rig's colour system and a single master control sweeping the whole room's
 * palette assignment.
 *
 * <p>The field itself is {@code public final} and cannot be replaced by a subclass, so this
 * adds {@link #stop} beside it — a {@code CompoundDiscreteParameter}, and therefore a legal
 * modulation target — and writes through to the inherited parameter. The inherited Index
 * knob becomes a readout: it still shows which stop is live, but this one is in control.
 *
 * <p>{@link #stops}, {@link #invert}, and {@link #spin} exist for the same reason and by the
 * same mechanism as {@link #stop}: each shadows an inherited field LX declared with a type
 * that cannot be modulated ({@code paletteStops} is a plain {@code DiscreteParameter};
 * {@code gradientInvert} and {@code rotate} are {@code BooleanParameter}s) and writes through
 * to it. {@link #stops} defaults to 2 rather than stock's default of {@code MAX_COLORS} for
 * the same reason {@link #stop} exists in the first place: this repo's colour rule pins the
 * stop count at 2 (see {@code design/color-system.md} §3), and a fresh device that defaults to
 * ramping through every slot including the accent reads as a colour choice rather than a
 * mistake, so it goes unnoticed.
 *
 * <p>Write-through runs every frame from {@link #run}, not from a parameter listener — see
 * that method and {@link #writeThrough()} for why a listener is the wrong tool here. It also
 * fires once at construction and again after {@link #load}, so a freshly added or just-loaded
 * device is already in sync before its first frame.
 *
 * <p>{@code GradientPattern} registers {@code heronarts.lx.studio.ui.pattern.UIGradientPattern}
 * as its device UI in glxstudio — a fixed panel built directly from {@code engine}'s fields,
 * with no extension point a subclass can hook. {@link #stop}, {@link #stops}, {@link #invert},
 * and {@link #spin} are on the rig and modulatable (verified over MCP) but invisible: nothing
 * ever draws them, and the stock panel keeps showing {@code paletteIndex} / {@code
 * paletteStops} / {@code gradientInvert} / {@code rotate} as though turning them still did
 * something. This class implements {@link UIDeviceControls} to replace that panel outright.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Mod Gradient")
@LXComponent.Description("Gradient whose palette stop is a modulation target")
public class ModGradient extends GradientPattern implements UIDeviceControls<ModGradient> {

  public final CompoundDiscreteParameter stop =
    new CompoundDiscreteParameter("Stop", 1, 1, LXSwatch.MAX_COLORS + 1)
    .setDescription("Palette stop this pattern reads; drives the stock Index, and unlike it can be modulated");

  /**
   * Defaults to 2, where the stock parameter defaults to {@code MAX_COLORS}. LX's default is
   * the worst case for this rig: a fresh Gradient ramps through every slot including the
   * accent, which reads as a colour choice rather than as a mistake and so goes unnoticed.
   */
  public final CompoundDiscreteParameter stops =
    new CompoundDiscreteParameter("Stops", 2, 2, LXSwatch.MAX_COLORS + 1)
    .setDescription("How many palette stops to ramp across; drives the stock Stops, and unlike it can be modulated");

  public final CompoundDiscreteParameter invert =
    new CompoundDiscreteParameter("Invert", new String[] { "Off", "On" })
    .setDescription("Invert the gradient direction; drives the stock Invert, which as a BooleanParameter cannot itself be modulated");

  public final CompoundDiscreteParameter spin =
    new CompoundDiscreteParameter("Spin", new String[] { "Off", "On" })
    .setDescription("Rotate the geometry; drives the stock Rotate, which as a BooleanParameter cannot itself be modulated");

  public ModGradient(LX lx) {
    super(lx);
    addParameter("stop", this.stop);
    addParameter("stops", this.stops);
    addParameter("invert", this.invert);
    addParameter("spin", this.spin);
    writeThrough();
  }

  /**
   * Per frame, not on a listener. A parameter listener fires on changes to the BASE value —
   * what a knob or {@code setValue} writes — and modulation never touches the base: it
   * leaves {@code baseValue} alone and contributes to the effective value read back by
   * {@link heronarts.lx.parameter.CompoundDiscreteParameter#getValuei()}. So a listener here
   * fires only when someone turns Stop by hand, which is the one case that did not need it,
   * and stays silent under exactly the modulation this class exists to allow. Verified the
   * wrong way round on the rig first: Stop read 3 with {@code modulated: true} while the
   * inherited Index sat at 1.
   */
  private void writeThrough() {
    this.engine.paletteIndex.setValue(this.stop.getValuei());
    this.engine.paletteStops.setValue(this.stops.getValuei());
    this.engine.gradientInvert.setValue(this.invert.getValuei() > 0);
    this.engine.rotate.setValue(this.spin.getValuei() > 0);
  }

  @Override
  public void run(double deltaMs) {
    writeThrough();
    super.run(deltaMs);
  }

  @Override
  public void load(LX lx, com.google.gson.JsonObject obj) {
    super.load(lx, obj);
    // The inherited paletteIndex is restored by super.load() from whatever was saved, which
    // may disagree with a stop value written by an older project or a hand-edited file.
    // This one is the source of truth, so re-assert it after the load settles.
    writeThrough();
  }

  /** Row height for a horizontal group of {@link UIKnob}s, or a lone knob. */
  private static final float KNOB_ROW = UIKnob.HEIGHT;

  /** Row height for a horizontal group of {@code UISlider}s, or a slider mixed with a
   * dropdown or label — a rendered {@code render-ui} pass measured the stock slider's own
   * height at 30px, taller than the 16px this originally assumed from {@code UIDropMenu}
   * alone, and that mismatch is what let the first attempt at this layout overlap the next
   * column: a column's declared width does not grow to fit a wider child, so an
   * under-measured row silently spills into whatever comes after it instead of clipping
   * visibly. */
  private static final float SLIDER_ROW = 30;

  /** Row height for a horizontal group of dropdowns only, with no slider alongside them. */
  private static final float DROPDOWN_ROW = 16;

  /** {@code UIColorControl}'s own fixed size, measured the same way — 40 &times; 42, matching
   * {@link UIKnob}'s footprint rather than the width passed to
   * {@code newColorControl(param, width)}. That width argument turned out to be an X
   * position, not a size, in this version of glxstudio; passing one had no visible effect,
   * so the calls below use the no-width overload instead of implying a control over sizing
   * that does not exist. */
  private static final float COLOR_ROW = UIKnob.HEIGHT;

  /**
   * Rebuilds {@code GradientPattern}'s panel, substituting this class's own {@link #stop},
   * {@link #stops}, and {@link #invert} for the stock Index / Stops / Invert controls, and
   * {@link #spin} for stock Rotate. Everything else the engine exposes is carried over
   * unchanged: {@code blendMode}, {@code colorMode} (Fixed / Linked / Palette — still the
   * switch that decides which colour source actually reaches the output), both colour
   * pickers, the gradient shape controls ({@code gradientClamp}, {@code gradientPhase},
   * {@code gradientScale}, {@code gradientCompress}) and its H/S/B range, all three axes'
   * mode/amount/offset, and {@code yaw}/{@code pitch}/{@code roll} — which still turn the
   * geometry once {@link #spin} is on, exactly as {@code rotate} gated them before.
   *
   * <p>Not mode-conditional, for the same reason and with the same trade-off as
   * {@link apotheneum.doved.effects.ModColorize#buildDeviceControls}: stock rebuilds its Colors
   * column when {@code colorMode} changes so only the relevant colour controls show; this
   * panel leaves all of them on screen all the time rather than reproduce that rebuild
   * machinery for a five-column pattern panel that already fits comfortably inside
   * {@link heronarts.lx.studio.ui.device.UIDevice#CONTENT_HEIGHT}.
   *
   * <p>Five columns rather than stock's denser layout because the three-direct-control limit
   * this repository checks on a vertical container (see {@code RenderDeviceUI}) is dodged by
   * packing controls into horizontal {@link #row} strips instead of stacking them — and a
   * strip of knob-height rows adds up fast against the 160px content cap. Splitting the
   * gradient-shape controls into their own "Shape" column, separate from the H/S/B "Range"
   * sliders, is what keeps both under that cap rather than one tall column that would clip.
   */
  @Override
  public void buildDeviceControls(UI ui, UIDevice device, ModGradient gradient) {
    device.setLayout(UI2dContainer.Layout.HORIZONTAL);
    device.setChildSpacing(4);

    // The second swatch's own built-in caption draws the stock label "Secondary" (9
    // characters) inside a fixed 40px control with no wrapping or ellipsis, and a render
    // caught it cut down to "Second" with no indication anything was cut. Left as-is:
    // UIColorPicker (which UIColorControl extends) draws that caption itself in its own
    // onDraw, with no label API — unlike UISlider/UIKnob/UIDropMenu it does not extend
    // UIParameterControl, so there is no setLabel() here to shorten it with, and renaming
    // the engine's own secondaryColor parameter to work around a display width is a
    // heavier and more surprising fix than the truncation it would solve. See the class
    // javadoc on {@link #buildDeviceControls} for the render this was caught in.
    addColumn(device, 112, "Colors",
      row(DROPDOWN_ROW, newDropMenu(gradient.engine.blendMode), newDropMenu(gradient.engine.colorMode)),
      row(COLOR_ROW,
        newColorControl(gradient.engine.fixedColor),
        newColorControl(gradient.engine.secondaryColor))
    );

    addVerticalBreak(ui, device);

    // The replacement for stock Index / Stops / Invert: our modulatable stop, stops, and
    // invert as knobs, the one widget here that draws a modulation ring — the whole reason
    // these exist as CompoundDiscreteParameter instead of the DiscreteParameter/
    // BooleanParameter stock uses. gradientClamp/Phase/Scale/Compress are untouched by
    // writeThrough() and stay as the stock controls.
    addColumn(device, 136, "Shape",
      row(KNOB_ROW, newKnob(gradient.stop), newKnob(gradient.stops), newKnob(gradient.invert)),
      row(SLIDER_ROW, newDropMenu(gradient.engine.gradientClamp), newHorizontalSlider(gradient.engine.gradientPhase)),
      row(SLIDER_ROW, newHorizontalSlider(gradient.engine.gradientScale), newHorizontalSlider(gradient.engine.gradientCompress))
    );

    // Wider than the default 52px slider used elsewhere in this panel: at the default width a
    // render clipped "Brightness Range" mid-word to "Brightnes", with "Saturation Range"
    // faring only slightly better by clipping cleanly to "Saturation" but still losing
    // "Range" outright. 84px got every label but "Brightness Range" (the longest of the
    // four) to draw in full, missing only its final "e"; 104px was enough for that one too.
    addColumn(device, 212, "Range",
      row(SLIDER_ROW,
        newHorizontalSlider(gradient.engine.gradient, 104),
        newHorizontalSlider(gradient.engine.gradientRange, 104)),
      row(SLIDER_ROW,
        newHorizontalSlider(gradient.engine.saturationRange, 104),
        newHorizontalSlider(gradient.engine.brightnessRange, 104))
    );

    addVerticalBreak(ui, device);

    addColumn(device, 190, "Axis",
      axisRow(ui, "X", gradient.engine.xMode, gradient.engine.xAmount, gradient.engine.xOffset),
      axisRow(ui, "Y", gradient.engine.yMode, gradient.engine.yAmount, gradient.engine.yOffset),
      axisRow(ui, "Z", gradient.engine.zMode, gradient.engine.zAmount, gradient.engine.zOffset)
    );

    addVerticalBreak(ui, device);

    // spin replaces stock Rotate; yaw/pitch/roll are untouched and still only turn the
    // geometry while spin (like rotate before it) is on.
    addColumn(device, 168, "Rotate",
      row(KNOB_ROW, newKnob(gradient.spin)),
      row(SLIDER_ROW,
        newHorizontalSlider(gradient.engine.yaw),
        newHorizontalSlider(gradient.engine.pitch),
        newHorizontalSlider(gradient.engine.roll))
    );
  }

  /**
   * One axis's mode, amount, and offset, packed into a single row behind a narrow letter
   * label. The label earns its place despite {@code newHorizontalSlider} already drawing
   * each slider's own parameter name ({@code "X-Amt"}, {@code "X-Off"}) underneath it: a
   * {@code UIDropMenu} shows only its currently selected option text, e.g. {@code "Angle"},
   * with nothing on screen to say which axis that dropdown belongs to.
   */
  private UI2dComponent axisRow(
    UI ui, String label, DiscreteParameter mode,
    LXListenableNormalizedParameter amount, LXListenableNormalizedParameter offset) {
    return row(SLIDER_ROW,
      controlLabel(ui, label, 14),
      newDropMenu(mode),
      newHorizontalSlider(amount),
      newHorizontalSlider(offset));
  }

  /**
   * A horizontal strip of controls. Packing every group into one of these, rather than letting
   * them stack as the direct children of an {@code addColumn} container, is what keeps each
   * column under this repository's three-direct-control limit on a vertical container — the
   * check in {@code RenderDeviceUI} only counts a container's immediate
   * {@code UIParameterComponent} children, and a nested horizontal row is not one.
   */
  private static UI2dComponent row(float height, UI2dComponent... controls) {
    return UI2dContainer.newHorizontalContainer(height, 4, controls);
  }

}
