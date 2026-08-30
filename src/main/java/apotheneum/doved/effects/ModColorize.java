package apotheneum.doved.effects;

import com.google.gson.JsonObject;

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.effect.color.ColorizeEffect;
import heronarts.lx.parameter.CompoundDiscreteParameter;

import apotheneum.doved.modulators.ApotheneumColor;
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
 * modulatable changes which color it applies, not what it can tell apart. Color that has
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
   * accent, which reads as a color choice rather than as a mistake and so goes unnoticed.
   * Six devices in the RobotHeart project were found sitting at 5 for exactly that reason.
   *
   * <p>Only the default differs. The minimum stays at stock's own minimum of 2, so this
   * shadow can address every value {@code paletteStops} can hold — nothing a project could
   * legitimately have saved becomes unreachable, and {@link #writeThrough()} can never clamp
   * a loaded value up.
   */
  public final CompoundDiscreteParameter stops =
    new CompoundDiscreteParameter("Stops", 2, 2, LXSwatch.MAX_COLORS + 1)
    .setDescription("How many palette stops to ramp across; drives the stock Stops, and unlike it can be modulated");

  public final CompoundDiscreteParameter invert =
    new CompoundDiscreteParameter("Invert", new String[] { "Off", "On" })
    .setDescription("Invert the palette gradient direction; drives the stock Invert, which as a BooleanParameter cannot itself be modulated");

  /**
   * Take both ends from the shared {@link ApotheneumColor} instead of from this device's own
   * Start/End pickers, so a Colorize is part of the room's colour rather than a second,
   * independent colour decision sitting on top of it.
   *
   * <p>On (the default) writes {@code ApotheneumColor}'s resolved primary into {@link #color1}
   * and its secondary into {@link #color2} every frame, and forces {@link #colorMode} to {@code
   * FIXED} so those two are what the ramp actually reads. Off is exactly this class's
   * pre-2026-08-30 behaviour, with every stock control back under manual control.
   *
   * <p><b>This changes how an already-saved ModColorize loads.</b> A project file written before
   * this parameter existed has no value for it, so those instances come back with global colour
   * on and their saved {@code colorMode} overridden to {@code FIXED}. That is the intended
   * migration -- the point of the shared colour state is that devices follow it by default --
   * but it is a visible change to existing work, not a silent no-op, so turn this off on any
   * device that was deliberately holding its own colour.
   *
   * <p>A {@code CompoundDiscreteParameter} rather than a {@code BooleanParameter} for the same
   * reason every other parameter on this class is one: only the former is an {@code
   * LXCompoundModulation.Target}, and following the room's colour is exactly the kind of thing a
   * performer wants on a knob or a MIDI switch.
   */
  public final CompoundDiscreteParameter global =
    new CompoundDiscreteParameter("Global", new String[] { "Off", "On" }, 1)
    .setDescription("Take both ends from the shared Apotheneum Color rather than this device's own Start/End pickers");

  /**
   * How many palette stops away from the shared pair this device sits, when {@link #global} is
   * on. This is the "a bit of tweaking" half of following the global colour: 0 puts this
   * Colorize on exactly the room's own pair, and a non-zero value moves both ends together
   * along the palette while still tracking every {@code pair}/{@code swap}/{@code axis} gesture.
   *
   * <p>Whole stops, and nothing else -- no hue rotation, no saturation trim. Every value this
   * can produce is still an unmodified palette colour, which is the invariant {@link
   * ApotheneumColor} exists to keep; a per-device hue offset here would reintroduce exactly the
   * off-palette synthesis that class deleted. It wraps around the live swatch, so no setting can
   * push this device off the end of the palette onto a repeated stop.
   *
   * <p>Range -4..+4 rather than 0..MAX: the shift is relative and a palette is a loop, so
   * negative values are as meaningful as positive ones and "one stop back" should not require
   * counting all the way around.
   */
  public final CompoundDiscreteParameter shift =
    new CompoundDiscreteParameter("Shift", 0, -4, 5)
    .setDescription("Palette stops away from the shared Apotheneum Color pair, when Global is on");

  /**
   * This device's own Start/End colours and {@code colorMode}, held from the moment {@link
   * #global} is switched on so switching it back off can put them back.
   *
   * <p>Without this, {@link #global} was a one-way trip. Turning it on overwrote {@link #color1}
   * /{@link #color2} and {@code colorMode} -- real, persisted parameters -- every frame; turning
   * it off merely stopped writing, leaving the shared colour and {@code FIXED} sitting in the
   * device as though the performer had chosen them. An Off&#8594;On&#8594;Off cycle therefore
   * destroyed a hand-built local look permanently, which matters most for exactly the use this
   * parameter was made a {@code CompoundDiscreteParameter} for: a MIDI switch or a modulator
   * flipping it repeatedly.
   *
   * <p>Not the same situation as {@link #stop}/{@link #stops}/{@link #invert} overwriting the
   * inherited {@code paletteIndex}/{@code paletteStops}/{@code paletteInvert}. Those shadows are
   * unconditional, so the inherited parameters are readouts for the device's whole life and
   * there is no state to lose; {@link #global} is a toggle, and a toggle that does not restore
   * is a destructive one.
   */
  private int localColor1;
  private int localColor2;
  private ColorMode localColorMode = ColorMode.FIXED;

  /** Whether {@link #localColor1} and friends hold a real captured look yet. False on a device
   * that has never had {@link #global} switched on, where there is nothing to restore and
   * restoring the field defaults would itself be the destructive act. */
  private boolean hasLocalColor = false;

  /** {@link #global}'s value as of the previous frame, so {@link #writeGlobalColor} can act on
   * the transition rather than on the level. Read per frame rather than from a parameter
   * listener because {@link #global} is a modulation target and modulation never touches the
   * base value a listener fires on -- the same reason the rest of {@link #writeThrough} is
   * per-frame.
   *
   * <p>The capture therefore happens on a transition this device actually <em>observes</em>,
   * which means a frame has to elapse between the two states. On a rig that is automatic --
   * frames run continuously while the effect is enabled. It is only visible in a test that
   * flips the parameter twice with no frame in between, and in one edge case on a rig: toggling
   * {@link #global} while this effect is disabled (so {@code run} is not being called) and then
   * re-enabling captures at the moment of re-enable rather than at the toggle. */
  private boolean wasGlobal = false;

  private static final String KEY_HAS_LOCAL_COLOR = "hasLocalColor";
  private static final String KEY_LOCAL_COLOR1 = "localColor1";
  private static final String KEY_LOCAL_COLOR2 = "localColor2";
  private static final String KEY_LOCAL_COLOR_MODE = "localColorMode";

  public ModColorize(LX lx) {
    super(lx);
    addParameter("stop", this.stop);
    addParameter("stops", this.stops);
    addParameter("invert", this.invert);
    addParameter("global", this.global);
    addParameter("shift", this.shift);
    writeThrough();
  }

  /** See {@link apotheneum.doved.patterns.ModGradient} for why this is per frame rather than
   * on a parameter listener: modulation moves the effective value without touching the base,
   * and listeners only fire on the base. */
  private void writeThrough() {
    this.paletteIndex.setValue(this.stop.getValuei());
    this.paletteStops.setValue(this.stops.getValuei());
    this.paletteInvert.setValue(this.invert.getValuei() > 0);
    writeGlobalColor();
  }

  /**
   * Drives {@link #color1}/{@link #color2} from the shared {@link ApotheneumColor} when {@link
   * #global} is on, and forces {@link #colorMode} to {@code FIXED} so the ramp reads them.
   *
   * <p><b>Surface-blind on purpose.</b> Both ends resolve against a {@code null}
   * {@code ApotheneumColor.Surface}, which skips the per-surface stop shift {@code axis}
   * applies. That is not a shortcut around {@code axis} -- it is the only honest answer here.
   * This class's own javadoc already says it: a Colorize receives nothing but {@code colors[]},
   * so by the time it runs there is no pattern form and no surface identity left to resolve
   * against; every surface would have to be given the same answer whatever was asked for. A
   * per-surface {@code axis} shift belongs on the surface-aware paths -- {@code
   * GradientMultiplyEffect}, which addresses the four surfaces by geometry, and the {@code
   * ColorNativePattern} roles, which know which surface each pixel is on. {@link #shift} is this
   * device's own offset and applies regardless.
   *
   * <p>{@link #invert} is reused as the local swap rather than getting a second parameter beside
   * it: under {@code FIXED} the two ends <em>are</em> the gradient direction, so exchanging them
   * is precisely what "invert the gradient direction" already means. It keeps driving the stock
   * {@code paletteInvert} as before, which simply has nothing to act on while {@code colorMode}
   * is {@code FIXED}.
   *
   * <p>Per frame rather than on a parameter listener, for the reason the rest of {@link
   * #writeThrough} is: modulation moves an effective value without touching the base, and
   * listeners only fire on the base. The palette stop itself can also be edited or animated
   * underneath us with no parameter change on this device at all.
   */
  private void writeGlobalColor() {
    final boolean on = this.global.getValuei() > 0;
    if (on != this.wasGlobal) {
      if (on) {
        rememberLocalColor();
        // Nudged once here, on the transition, rather than forced every frame: forcing would
        // mean a performer could never look at anything else while Global is on, and would
        // make restoreLocalColor's job ambiguous.
        this.colorMode.setValue(ColorMode.FIXED);
      } else {
        restoreLocalColor();
      }
      this.wasGlobal = on;
    }
    if (!on) {
      return;
    }
    final ApotheneumColor color = ApotheneumColor.get(getLX());
    final int stopShift = this.shift.getValuei();
    final int primary = ApotheneumColor.resolvePrimaryOrNeutral(color, null, stopShift);
    final int secondary = ApotheneumColor.resolveSecondaryOrNeutral(color, null, stopShift);
    final boolean swapped = this.invert.getValuei() > 0;
    this.color1.setColor(swapped ? secondary : primary);
    this.color2.setColor(swapped ? primary : secondary);
  }

  private void rememberLocalColor() {
    this.localColor1 = this.color1.getColor();
    this.localColor2 = this.color2.getColor();
    this.localColorMode = this.colorMode.getEnum();
    this.hasLocalColor = true;
  }

  private void restoreLocalColor() {
    if (!this.hasLocalColor) {
      return;
    }
    this.color1.setColor(this.localColor1);
    this.color2.setColor(this.localColor2);
    this.colorMode.setValue(this.localColorMode);
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    // See the fields' own javadoc: without this, a project saved with Global on comes back
    // with nothing to restore, and turning Global off would strand the device on the shared
    // colour -- the same one-way trip this state exists to prevent, just spread over a
    // save/load rather than a single toggle.
    obj.addProperty(KEY_HAS_LOCAL_COLOR, this.hasLocalColor);
    obj.addProperty(KEY_LOCAL_COLOR1, this.localColor1);
    obj.addProperty(KEY_LOCAL_COLOR2, this.localColor2);
    obj.addProperty(KEY_LOCAL_COLOR_MODE, this.localColorMode.name());
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    writeThrough();
    super.run(deltaMs, enabledAmount);
  }

  /** {@link #writeThrough()} for tests that need the per-frame path without hosting this
   * effect on a channel and running the engine -- see {@code ModColorizeGlobalColorTest}'s
   * class javadoc for why that class gets exactly one rendering model per surefire fork. */
  void writeThroughForTest() {
    writeThrough();
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_HAS_LOCAL_COLOR)) {
      this.hasLocalColor = obj.get(KEY_HAS_LOCAL_COLOR).getAsBoolean();
      this.localColor1 = obj.get(KEY_LOCAL_COLOR1).getAsInt();
      this.localColor2 = obj.get(KEY_LOCAL_COLOR2).getAsInt();
      try {
        this.localColorMode = ColorMode.valueOf(obj.get(KEY_LOCAL_COLOR_MODE).getAsString());
      } catch (IllegalArgumentException x) {
        // A mode name this build no longer has. Falling back beats refusing to load the
        // project over a control the performer may never touch again.
        this.localColorMode = ColorMode.FIXED;
      }
    } else {
      // A project written before these keys existed. super.load() has just restored the
      // performer's real Start, End and colorMode from the file, and this is the only moment
      // they are visible: global defaults on, so writeThrough() below is about to overwrite
      // all three with the shared colour. Capturing here is what makes that reversible.
      //
      // Skipping this branch was a quiet, permanent loss rather than a visible one. The
      // constructor's own capture holds stock defaults, so switching Global off would have
      // "restored" a look the performer never chose, and saving after that would write those
      // defaults into the file as the local look -- the original configuration gone, with
      // nothing on screen having reported a change.
      rememberLocalColor();
    }
    // The device comes back with global already at its saved value, so seed the transition
    // tracker from it -- otherwise a project saved with Global on would read as an off->on
    // transition on the first frame and capture the *shared* colour as the local look,
    // overwriting the very state that was just restored above.
    this.wasGlobal = this.global.getValuei() > 0;
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
   * the columns below actually does anything), both {@code ColorParameter} color pickers, the
   * Linked and Relative H/S/B triplets, and {@code paletteDepth} — is carried over unchanged.
   *
   * <p>Not mode-conditional. Stock hides the Start/End columns that don't match the current
   * {@code colorMode}, rebuilding on every change so only the relevant controls are on screen.
   * This panel skips that: every column is always present. The gap that leaves is cosmetic —
   * a Linked-mode session still sees the Fixed color picker doing nothing — not functional,
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

    // Global leads the panel rather than sitting inside Gradient: with Global on, the Start
    // and End pickers and every palette-ramp control to the right of it are readouts rather
    // than inputs, so the switch that decides that has to be the first thing read, not
    // something found after wondering why the colour pickers do nothing. Its own row keeps the
    // Gradient column under the height cap a third knob row would have pushed it past.
    addColumn(device, 92, "Global",
      row(KNOB_ROW, newKnob(colorize.global), newKnob(colorize.shift))
    );

    addVerticalBreak(ui, device);

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
