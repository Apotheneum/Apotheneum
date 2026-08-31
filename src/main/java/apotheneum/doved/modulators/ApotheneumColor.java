/**
 * Copyright 2026- Dan Oved
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
 * @author Dan Oved
 */

package apotheneum.doved.modulators;

import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.LXParameter;

import apotheneum.doved.patterns.ColorNativePattern;

/**
 * The global colour state every {@link ColorNativePattern} (and, since the gradient-multiply
 * work, every {@code apotheneum.doved.effects.GradientMultiplyEffect}) reads instead of owning
 * its own palette knobs.
 *
 * <h2>Engine-registered, not user-addable — 2026-08-30</h2>
 *
 * This class used to be an {@code LXModulator} the owner added to
 * {@code lx.engine.modulation} by hand, backed by a static {@code instance} field set in the
 * constructor and cleared in {@code dispose()}. That broke for real the first night it shipped:
 * deleting the live instance while a second existed left the pointer null while a perfectly
 * good modulator sat visible in the UI, and there was no defined rule for which of two
 * instances was authoritative. Hardening that static field was the first plan; the better fix,
 * once {@code apotheneum.video.ApotheneumVideoPlugin}/{@code ApotheneumVideo} turned out to
 * already solve exactly this shape of problem, was to stop the field from needing to exist at
 * all. This class is now a plain {@link LXComponent}, constructed once and registered directly
 * on the engine — {@code lx.engine.registerComponent(PATH, ...)} — by
 * {@code apotheneum.doved.ApotheneumColorPlugin} the same way {@code ApotheneumVideo} registers
 * itself, and surfaced in the studio left pane's GLOBAL tab by
 * {@code apotheneum.doved.ApotheneumColorUIPlugin}, the same split
 * {@code ApotheneumVideoPlugin}/{@code ApotheneumVideoUIPlugin} already use (core plugin: no
 * studio dependency, keeps working headless; UI plugin: builds the panel, degrades to doing
 * nothing if the core plugin didn't register). There is no add, no delete, no undo/redo churn,
 * and therefore no second instance to have a tie-break rule about — exactly one exists for the
 * life of the engine, the same way {@code lx.engine.palette}/{@code lx.engine.tempo} do.
 *
 * <p>Resolve the live instance with {@link #get(LX)}, which reads
 * {@code lx.engine.getChild(PATH)} fresh every call rather than caching anything — there is
 * nothing left to go stale, since the engine either has this child registered or (only if the
 * core plugin failed to load) it does not. {@link #resolvePrimaryOrNeutral}/
 * {@link #resolveSecondaryOrNeutral} take the already-resolved result of {@link #get(LX)} rather
 * than re-resolving themselves, so a caller that runs per-pixel (a {@code ColorNativePattern}
 * role) calls {@link #get(LX)} once per frame and reuses it, not once per pixel.
 *
 * <h2>The two-knob scheme, kept intact, not redesigned</h2>
 *
 * {@link #pair} and {@link #swap} are exactly the existing "Color"/"Pair" and "Swap"/"Flip"
 * controls documented in this show's {@code design/color-system.md} &#167;4 — the same relay
 * arithmetic ({@code primary} base 1, {@code secondary} base 2, {@code pair} advances both by one
 * stop, {@code swap} exchanges them), just evaluated here in integer form instead of through LX
 * modulation ranges on a {@code MacroKnobs}. There is exactly one of each: turning either moves
 * every surface at once, which is the point — a performer is changing the room's colour, not one
 * surface's colour.
 *
 * <h2>Three parameters, no hue/saturation maths — 2026-08-30</h2>
 *
 * This class previously gave each of the four surfaces its own standing {@code indexOffset}/
 * {@code hueOffset}/{@code satTrim} triple — twelve extra parameters, on top of {@link #pair}/
 * {@link #swap}. The owner's own correction, once he saw the gradient controls land: <em>"I don't
 * know if we need different dimensions and different amounts because we should just use
 * everything from the palette,"</em> followed by <em>"What we need is gradient controls. Not
 * necessarily difference tweaking."</em> All of it is gone. {@link #axis} (below) replaces the
 * whole twelve-parameter structure with one three-position selector, because the owner's own
 * description of what he wants surfaces to do — <em>"cube surfaces on stop N, cylinder surfaces
 * on N+1"</em> — is entirely expressible as a fixed one-stop {@link #pair}-relative shift, not a
 * user-adjustable amount, and every surface's colour is a real, unmodified palette stop, never a
 * hue-shifted or saturation-trimmed synthesis of one. {@code design/color-system.md} (in the
 * sibling {@code chromatik-shows} repo, outside this repo's scope to edit directly) should say
 * this principle outright — colour comes from the palette; anything that manufactures a colour
 * outside it defeats the point of a deliberately-composed palette — if it does not already.
 *
 * <p>This class's whole colour surface, after the removal, is exactly three parameters:
 * {@link #pair}, {@link #swap}, {@link #axis}. Paired with {@link ApotheneumGradient}'s
 * {@code azimuth}/{@code elevation}/{@code spread}, that is six controls total across both
 * classes — the owner's own tally of what this rig's colour system needs.
 */
public class ApotheneumColor extends LXComponent implements LXOscComponent {

  /** Engine path; parameters live at {@code /lx/apotheneumColor/*}, mirroring how
   * {@code apotheneum.video.ApotheneumVideo} registers at {@code /lx/apotheneumVideo/*}. */
  public static final String PATH = "apotheneumColor";

  /**
   * {@link Axis#values()} hoisted once. {@code Enum.values()} clones its backing array on every
   * call, and {@link #stopDelta(Surface)} sits on the per-pixel path every colour-native pattern
   * now takes ({@code ColorRole.color} -> {@link #primaryColor}/{@link #secondaryColor} ->
   * {@link #resolvedColor}), so calling it there allocated tens of thousands of throwaway arrays
   * per frame -- exactly what {@code docs/lx-coding-guidelines.md} &#167;1 forbids, and a direct
   * contradiction of {@code ColorNativePattern.colorizeCells}'s own "no allocation happens in
   * this method or anything it calls" guarantee.
   */
  private static final Axis[] AXES = Axis.values();

  /**
   * The engine's registered {@code ApotheneumColor}, or {@code null} if
   * {@code apotheneum.doved.ApotheneumColorPlugin} did not register one (the plugin failed to
   * load, or is disabled). Resolved fresh every call — see the class javadoc for why there is
   * nothing to cache across calls, only within one caller's own frame if it is called per pixel.
   */
  public static ApotheneumColor get(LX lx) {
    final LXComponent child = lx.engine.getChild(PATH);
    if (child instanceof ApotheneumColor) {
      return (ApotheneumColor) child;
    }
    return mirrorOfStale(lx, child);
  }

  /**
   * The shadow instance {@link #mirrorOfStale} keeps in step with a stale registration. One per
   * process, built on first need and never disposed -- it is not an engine child and holds
   * nothing but three parameters.
   */
  private static ApotheneumColor staleMirror = null;

  /**
   * Reads a <em>stale</em> registration -- the component left behind when the package is
   * reinstalled over a running Chromatik -- through a locally-built mirror.
   *
   * <p>Reinstalling loads the new classes under a new classloader while the engine still holds
   * the instance the previous one registered. Its class has the identical name and is a
   * different {@code Class} object, so {@code instanceof} is false and this method used to
   * return null: every colour-native pattern and {@code GradientMultiplyEffect} fell back to
   * neutral white, and stayed there until Chromatik was restarted. That is a restart per
   * install, which during a build session is most of the session.
   *
   * <p>Nothing here touches the registration. {@code LXEngine.registerComponent} only adds, LX
   * exposes no child removal, and swapping the instance would break a live project's modulation
   * mappings, which address these parameters through that exact object. Instead the stale
   * component is <em>read</em>: {@link LXComponent#getParameter(String)} returns an {@link
   * heronarts.lx.parameter.LXParameter}, and that type comes from LX's own classloader rather
   * than the package's, so it is the same type on both sides of a reload and can be read across
   * the boundary that defeats {@code instanceof}. The values come back modulated, so a
   * modulator or MIDI knob driving the real component still moves what patterns resolve.
   *
   * <p>Returns null for anything that is not this class by name -- a genuinely foreign component
   * at this path is a real error and still resolves to the neutral fallback.
   */
  private static ApotheneumColor mirrorOfStale(LX lx, LXComponent child) {
    if ((child == null) || !child.getClass().getName().equals(ApotheneumColor.class.getName())) {
      return null;
    }
    if (staleMirror == null) {
      staleMirror = new ApotheneumColor(lx);
    }
    copyParameter(child, "pair", staleMirror.pair);
    copyParameter(child, "swap", staleMirror.swap);
    copyParameter(child, "axis", staleMirror.axis);
    return staleMirror;
  }

  private static void copyParameter(LXComponent from, String path, CompoundDiscreteParameter to) {
    final LXParameter source = from.getParameter(path);
    if (source != null) {
      to.setValue((int) Math.round(source.getValue()));
    }
  }

  /** Which of the installation's four independently-addressable surfaces a pixel is on. */
  public enum Surface {
    CUBE_EXTERIOR,
    CUBE_INTERIOR,
    CYLINDER_EXTERIOR,
    CYLINDER_INTERIOR;

    /**
     * Resolves a surface from the {@link apotheneum.Apotheneum.Orientation} object a pattern is
     * already iterating — every {@code ColorNativePattern} subclass renders one physical
     * orientation at a time (see e.g. {@code Rockfall}'s {@code surfaceWaters}, {@code
     * Waterfall}/{@code Dunes}/{@code Grass}'s per-orientation {@code output}/{@code render}
     * methods), so this is a reference-equality lookup against geometry the pattern already has
     * in hand, not new geometry-detection machinery. Returns {@code null} if Apotheneum isn't
     * loaded, or the orientation matches none of the four (e.g. a model without an interior).
     */
    public static Surface of(apotheneum.Apotheneum.Orientation orientation) {
      if ((orientation == null)
        || (apotheneum.Apotheneum.cube == null)
        || (apotheneum.Apotheneum.cylinder == null)) {
        return null;
      }
      if (orientation == apotheneum.Apotheneum.cube.exterior) {
        return CUBE_EXTERIOR;
      }
      if (orientation == apotheneum.Apotheneum.cube.interior) {
        return CUBE_INTERIOR;
      }
      if (orientation == apotheneum.Apotheneum.cylinder.exterior) {
        return CYLINDER_EXTERIOR;
      }
      if (orientation == apotheneum.Apotheneum.cylinder.interior) {
        return CYLINDER_INTERIOR;
      }
      return null;
    }
  }

  private static final String[] PAIR_OPTIONS = { "1", "2" };
  private static final String[] SWAP_OPTIONS = { "Off", "On" };
  // Order is part of the contract, not an implementation detail: the owner plays this from one
  // physical knob, and a 3-option discrete parameter divides a MIDI CC's 0-127 range into thirds
  // in declaration order -- "all the way down" lands on index 0, "middle third" on index 1, "all
  // the way up" on index 2. His words: "None would be the knob 11. If it's all the way down,
  // it's none. If it's a third of the way, it's shape, and if it's all the way, it's inside/
  // outside." Reordering these strings (or the Axis enum below, which must match index-for-index)
  // silently remaps what the knob's physical position means.
  private static final String[] AXIS_OPTIONS = { "None", "Shape", "In/Out" };

  /**
   * Which pair of adjacent palette stops primary/secondary resolve from, everywhere. The
   * knob-12 "Color"/"Pair" analog from {@code design/color-system.md} section 4: base 1 or
   * base 2.
   *
   * <p><b>{@code CompoundDiscreteParameter(String, String[])} does not actually store the
   * options array.</b> Confirmed by disassembling {@code CompoundDiscreteParameter}'s own
   * bytecode: unlike {@code DiscreteParameter}'s identical-looking constructor (which does
   * {@code this.options = options}), the {@code Compound} subclass's version only reads
   * {@code options.length} to size the range and never assigns the field, so
   * {@code getOptions()} silently returns {@code null} afterwards -- crashing any UI control
   * that dereferences it with no null guard. The explicit {@link #setOptions(String[])} calls
   * in the constructor below are the real fix, not decorative.</p>
   */
  public final CompoundDiscreteParameter pair =
    new CompoundDiscreteParameter("Pair", PAIR_OPTIONS)
    .setDescription("Which pair of adjacent palette stops primary/secondary resolve from, everywhere");

  /**
   * Exchanges primary and secondary, everywhere. The switch-12 "Swap"/"Flip" analog. A
   * {@code CompoundDiscreteParameter} rather than a {@code BooleanParameter}, matching
   * {@code ModColorize.invert}'s precedent: only the former is an
   * {@code LXCompoundModulation.Target}. See {@link #pair}'s javadoc for why its options are
   * set explicitly in the constructor rather than trusted to this constructor call.
   */
  public final CompoundDiscreteParameter swap =
    new CompoundDiscreteParameter("Swap", SWAP_OPTIONS)
    .setDescription("Exchange primary and secondary, everywhere");

  /**
   * Which surfaces read the same palette stop as which others, everywhere. {@code
   * CompoundDiscreteParameter} for the same reason as {@link #pair}/{@link #swap}: an
   * {@code LXCompoundModulation.Target}, which a {@code BooleanParameter} (or a triple of
   * booleans) is not, and which matters because a MIDI knob is meant to drive this directly. See
   * {@link #pair}'s javadoc for why {@link #AXIS_OPTIONS} is also passed to
   * {@link #setOptions(String[], boolean)} explicitly in the constructor below -- the same
   * {@code CompoundDiscreteParameter(String, String[])} storage bug applies here too.
   *
   * <p>See {@link Axis} for the full design: what each of the three settings means, why the
   * order is fixed by the owner's own physical knob mapping, why there is no per-surface
   * amount/offset knob underneath it (a fixed one-stop shift is the entire "difference" story),
   * why {@code Axis.NONE} restores exactly rather than by zeroing anything, and the one named,
   * narrower asymmetry ({@code Fireball}/{@code Waterfall} on {@code Axis.INSIDE_OUTSIDE}) this
   * design could not remove without changing those two patterns' own paint architecture.
   */
  public final CompoundDiscreteParameter axis =
    new CompoundDiscreteParameter("Axis", AXIS_OPTIONS)
    .setDescription(
      "Which surfaces share a palette stop: None (all four match), Shape (cube matches cube, "
      + "cylinder matches cylinder, the two sit one stop apart), or In/Out (exteriors match, "
      + "interiors match, the two sit one stop apart)");

  /**
   * The three {@link #axis} settings, index-matched to {@link #AXIS_OPTIONS} -- {@code
   * Axis.values()[this.axis.getValuei()]} depends on this order being exactly {@code NONE},
   * {@code SHAPE}, {@code INSIDE_OUTSIDE}, matching {@code "None"}, {@code "Shape"}, {@code
   * "In/Out"}. This order is fixed by the owner's own physical knob mapping (see
   * {@link #AXIS_OPTIONS}'s comment), not a stylistic choice.
   *
   * <h2>What each setting does, in palette-stop terms</h2>
   *
   * {@link #stopDelta(Surface)} is the entire mechanism: each surface's resolved index is
   * {@code sharedIndex + stopDelta(surface)}, wrapped. {@code stopDelta} is 0 for every surface
   * under {@link #NONE} (one shared stop, the whole piece one colour); under {@link #SHAPE} it
   * is 0 for the two cube surfaces and 1 for the two cylinder surfaces (cube and cylinder sit
   * one stop apart, exterior and interior of the same shape match exactly); under {@link
   * #INSIDE_OUTSIDE} it is 0 for the two exterior surfaces and 1 for the two interior surfaces
   * (exteriors and interiors sit one stop apart, the two surfaces of the same
   * exterior/interior role match exactly). There is no adjustable "how many stops apart" knob:
   * the owner's own description -- <em>"cube surfaces on stop N, cylinder surfaces on N+1"</em>
   * -- names a fixed one-stop shift, not a tunable amount, and a per-surface offset on top of
   * that would be exactly the "difference tweaking" he said this rig does not need.
   *
   * <h2>Why a mode/axis control rather than always-independent surfaces</h2>
   *
   * Reported directly by the owner, watching the piece: <em>"seems like the cube is always the
   * same, and the cylinder is always the same. We can't have a difference between the cube's
   * interior and exterior. I want to either vary interior and exterior or cube and cylinder,"</em>
   * and, after two follow-ups: <em>"or none at all; aka all the same color,"</em> and then the
   * exact knob mapping quoted above. Investigation (see {@code
   * apotheneum.doved.effects.GradientMultiplyEffect}'s and each {@code
   * apotheneum.doved.patterns.ColorNativePattern} subclass's own notes) found the underlying
   * asymmetry was real but narrower than "colour-native patterns can't do this": of the seven
   * {@code ColorNativePattern} subclasses, {@code Fireball} and {@code Waterfall} alone painted
   * each shape's colour once against its own {@code Surface.of(...exterior)} identity and
   * mechanically duplicated that value onto the interior mirror point ({@code Fireball}'s
   * {@code paint()}/{@code paintBrighter()}; {@code Waterfall}'s bulk {@code copyExterior()}),
   * a deliberate, pre-{@code ApotheneumColor} design (view-mask correctness for {@code Fireball};
   * simple bulk-copy convenience for {@code Waterfall}) -- not a limitation of this class or of
   * {@code ColorNativePattern}. Both have since been migrated onto {@code
   * ColorNativePattern.colorizeCells} and resolve per surface; see the note below. {@code Dunes}, {@code Grass}, {@code Jungle} and {@code Rockfall}
   * already resolve {@code Surface.of(orientation)} independently for interior and exterior (see
   * each one's {@code output}/{@code writeColors} or, for {@code Rockfall}, its per-orientation
   * {@code SurfaceWater}), so they already support every axis setting correctly, in full parity
   * with {@code GradientMultiplyEffect}. {@code LavaLamp} paints exterior only and never touches
   * interior, so it is unaffected either way.
   *
   * <p><b>The {@code Fireball}/{@code Waterfall} asymmetry this section used to describe is
   * closed -- both are migrated.</b> Until 2026-08-30 those two patterns queried {@code
   * ApotheneumColor} exactly twice per frame, once per shape and both times for the *exterior*
   * identity, mirroring the result onto the interior; under {@link #INSIDE_OUTSIDE} both
   * exteriors carry {@code stopDelta = 0}, so the cube query and the cylinder query resolved the
   * identical colour and the setting collapsed to look exactly like {@link #NONE} rather than
   * like a muted version of itself. Both now resolve colour independently per real surface
   * through {@code ColorNativePattern.colorizeCells}: {@code Fireball}'s {@code Fire.render}
   * passes both {@code Surface.of(orientation)} and {@code Surface.of(mirrorOrientation)}, and
   * its embers do the same through {@code paintBrighter}; {@code Waterfall}'s bulk {@code
   * copyExterior()} is gone in favour of a per-surface {@code colorizeShape} pass. The physics
   * stays shared per cell -- correct, since interior and exterior are physically coincident --
   * and only the colour resolution splits. All seven {@code ColorNativePattern} subclasses
   * therefore honour all three settings, in full parity with {@code GradientMultiplyEffect};
   * there is no remaining disagreement between the Global (effect) and Natural (pattern) paths.
   *
   * <p>The one thing that has not changed: {@code LavaLamp} paints exterior only and never
   * touches interior, so {@link #INSIDE_OUTSIDE} has nothing to differentiate there. That is
   * that pattern's own scope, not a colour-resolution gap.
   *
   * <h2>Global (the effect path) has no such gap</h2>
   *
   * {@code GradientMultiplyEffect} runs over already-finished {@code colors[]} and addresses all
   * four real surfaces directly by geometry every frame, regardless of how the hosted pattern
   * painted them -- so it resolves {@link #axis} correctly on all three settings, on every
   * pattern, unconditionally. The asymmetry above is specific to two named {@code
   * ColorNativePattern} subclasses resolving their own colour internally; it does not touch the
   * global/effect path at all.
   *
   * <h2>None does not zero anything</h2>
   *
   * There is nothing left to zero. Earlier drafts of this control kept the four surfaces'
   * standing offsets as separate parameters and had {@link #stopDelta(Surface)}'s predecessor
   * choose which one to *read*; that shape is gone along with the offsets themselves (see the
   * class javadoc's "Three parameters, no hue/saturation maths" section) -- {@link #axis} is now
   * the entire per-surface state, computed fresh from {@link #stopDelta(Surface)} on every call,
   * so switching it is inherently non-destructive: there is no secondary value anywhere that a
   * switch could overwrite or lose.
   */
  public enum Axis { NONE, SHAPE, INSIDE_OUTSIDE }

  /** The {@link #axis} setting currently selected. Public so callers and tests name a constant
   * rather than repeating the raw ordinal {@link #AXIS_OPTIONS} happens to sit at -- {@code
   * axis.setValue(ApotheneumColor.Axis.SHAPE.ordinal())} reads as what it means and stops
   * compiling if the enum is ever renamed away underneath it. */
  public Axis axis() {
    return AXES[this.axis.getValuei()];
  }

  /**
   * Constructed exactly once, by {@code apotheneum.doved.ApotheneumColorPlugin.initialize},
   * which passes the real {@code lx} straight through -- unlike the old {@code LXModulator}
   * shape, there is no window where this object exists without an {@code lx} reference.
   */
  public ApotheneumColor(LX lx) {
    super(lx, "Apotheneum Color");

    addParameter("pair", this.pair);
    addParameter("swap", this.swap);
    addParameter("axis", this.axis);
    // See pair's javadoc: the (String, String[]) constructor above sizes the range correctly
    // but never stores the options array, so getOptions() would otherwise return null.
    this.pair.setOptions(PAIR_OPTIONS, false);
    this.swap.setOptions(SWAP_OPTIONS, false);
    this.axis.setOptions(AXIS_OPTIONS, false);
  }

  /** Base stop (1 or 2) before {@link #swap} exchanges the two roles. */
  private int baseIndex() {
    return (this.pair.getValuei() == 1) ? 2 : 1;
  }

  private boolean isSwapped() {
    return this.swap.getValuei() == 1;
  }

  /** The shared, gesture-driven index primary resolves from, before any surface offset. */
  public int primaryIndex() {
    final int base = baseIndex();
    return isSwapped() ? base + 1 : base;
  }

  /** The shared, gesture-driven index secondary resolves from, before any surface offset. */
  public int secondaryIndex() {
    final int base = baseIndex();
    return isSwapped() ? base : base + 1;
  }

  /** This surface's fully-resolved primary color: shared index, this surface's own stop shift. */
  public int primaryColor(Surface surface) {
    return primaryColor(surface, 0);
  }

  /** This surface's fully-resolved secondary color: shared index, this surface's own stop shift. */
  public int secondaryColor(Surface surface) {
    return secondaryColor(surface, 0);
  }

  /**
   * {@link #primaryColor(Surface)} moved a further {@code stopShift} palette stops along, wrapped
   * around the live swatch exactly as every other resolution here is.
   *
   * <p>For a caller that wants to <em>follow</em> the shared pair/swap gesture while sitting
   * somewhere else in the palette than the piece as a whole -- {@code
   * apotheneum.doved.effects.ModColorize} is the one that does, so a Colorize can be tinted a
   * stop or two off the room without being pinned to an absolute stop that stops tracking the
   * moment {@link #pair} moves. Deliberately a whole-stop shift and nothing else: the result is
   * still an unmodified palette colour, which is the invariant this whole class exists to keep
   * (see the class javadoc). {@code stopShift = 0} is exactly {@link #primaryColor(Surface)}.
   */
  public int primaryColor(Surface surface, int stopShift) {
    return resolvedColor(primaryIndex() + stopShift, surface);
  }

  /** {@link #primaryColor(Surface, int)}'s secondary counterpart. */
  public int secondaryColor(Surface surface, int stopShift) {
    return resolvedColor(secondaryIndex() + stopShift, surface);
  }

  /**
   * Which of "no instance" / "an instance" this process last logged, so
   * {@link #resolvePrimaryOrNeutral}/{@link #resolveSecondaryOrNeutral} log exactly once per
   * transition in *either* direction rather than only ever announcing the bad news. 2026-08-29's
   * log announced the fallback starting but never announced it ending, so "no ApotheneumColor
   * found" sitting at the end of the log was indistinguishable from "still broken right now" --
   * the recovery needs to be exactly as loud as the loss for this to answer "is it working"
   * instead of raising the question.
   */
  private enum ResolutionState { UNKNOWN, MISSING, PRESENT }

  private static ResolutionState loggedState = ResolutionState.UNKNOWN;

  /**
   * {@link #primaryColor(Surface)} against an already-resolved {@code color} (see {@link
   * #get(LX)}), or neutral white with a one-time-per-transition log line if {@code color} is
   * {@code null}. The single place this fallback is implemented -- {@code
   * ColorNativePattern.ColorRole} and {@code GradientMultiplyEffect} both call this rather than
   * each re-implementing the null check and the log gate.
   */
  public static int resolvePrimaryOrNeutral(ApotheneumColor color, Surface surface) {
    return resolvePrimaryOrNeutral(color, surface, 0);
  }

  /** {@link #resolvePrimaryOrNeutral(ApotheneumColor, Surface)} with a stop shift -- see
   * {@link #primaryColor(Surface, int)} for what the shift means and who wants one. */
  public static int resolvePrimaryOrNeutral(ApotheneumColor color, Surface surface, int stopShift) {
    noteResolution(color);
    return (color == null) ? LXColor.WHITE : color.primaryColor(surface, stopShift);
  }

  /** {@link #secondaryColor(Surface)}'s counterpart to {@link #resolvePrimaryOrNeutral}. */
  public static int resolveSecondaryOrNeutral(ApotheneumColor color, Surface surface) {
    return resolveSecondaryOrNeutral(color, surface, 0);
  }

  /** {@link #resolveSecondaryOrNeutral(ApotheneumColor, Surface)} with a stop shift. */
  public static int resolveSecondaryOrNeutral(ApotheneumColor color, Surface surface, int stopShift) {
    noteResolution(color);
    return (color == null) ? LXColor.WHITE : color.secondaryColor(surface, stopShift);
  }

  private static void noteResolution(ApotheneumColor color) {
    if (color == null) {
      if (loggedState != ResolutionState.MISSING) {
        loggedState = ResolutionState.MISSING;
        LX.log(
          "[APOTHENEUM] ApotheneumColor: no instance registered on the engine -- every "
          + "colour-native pattern and GradientMultiplyEffect is resolving neutral white. "
          + "Expected only if apotheneum.doved.ApotheneumColorPlugin failed to load; check the "
          + "log above this line for why.");
      }
    } else if (loggedState != ResolutionState.PRESENT) {
      loggedState = ResolutionState.PRESENT;
      LX.log(
        "[APOTHENEUM] ApotheneumColor: resolving from the instance at " + color.getPath()
        + " -- colour-native patterns and GradientMultiplyEffect are live again.");
    }
  }

  /** {@code sharedIndex} shifted by {@code surface}'s {@link #stopDelta(Surface)} under the
   * current {@link #axis}, wrapped, and looked up directly in the palette -- no hue or
   * saturation math on top; every resolved colour is a real, unmodified palette stop. */
  private int resolvedColor(int sharedIndex, Surface surface) {
    final List<LXDynamicColor> colors = this.lx.engine.palette.swatch.colors;
    final int index = (surface == null) ? sharedIndex : (sharedIndex + stopDelta(surface));
    return ColorNativePattern.paletteColor(colors, wrapIndex(index, colors.size()) - 1);
  }

  /** See {@link Axis}'s javadoc for what each setting means in palette-stop terms. */
  private int stopDelta(Surface surface) {
    switch (AXES[this.axis.getValuei()]) {
      case SHAPE:
        return isCube(surface) ? 0 : 1;
      case INSIDE_OUTSIDE:
        return isExterior(surface) ? 0 : 1;
      case NONE:
      default:
        return 0;
    }
  }

  private static boolean isCube(Surface surface) {
    return (surface == Surface.CUBE_EXTERIOR) || (surface == Surface.CUBE_INTERIOR);
  }

  private static boolean isExterior(Surface surface) {
    return (surface == Surface.CUBE_EXTERIOR) || (surface == Surface.CYLINDER_EXTERIOR);
  }

  /**
   * Wraps a 1-based palette index around the swatch's <em>live</em> stop count rather than
   * clamping it.
   *
   * <p><b>{@code stopCount} is the number of stops the swatch actually holds right now, not
   * {@link LXSwatch#MAX_COLORS}.</b> This wrapped around the latter until 2026-08-30, which made
   * the method a no-op on every real palette: {@code MAX_COLORS} is the swatch's fixed
   * <em>capacity</em>, and this show's palettes run two to six stops, so an index that should
   * have wrapped back to stop 1 stayed out past the end and {@link
   * ColorNativePattern#paletteColor} then <em>clamped</em> it to the last real stop. Worked
   * example on a two-stop swatch: {@link #pair} = 2 with any non-{@code NONE} {@link #axis}
   * resolves the shifted surface to {@code 2 + 1 = 3}, which clamped back onto stop 2 -- the
   * same stop the unshifted surface resolved -- so {@link #axis} had no visible effect at all,
   * which is the opposite of what this method's own name promised. The unit test covering it
   * used a one-colour swatch, where wrapping and clamping are indistinguishable, so it passed
   * throughout.
   *
   * <p>Returns {@code 1} for an empty swatch rather than dividing by zero. That is defensive
   * only, and deliberately not unit-tested: {@code LXSwatch.removeColor} refuses the last stop
   * outright ({@code IllegalStateException: Cannot remove first color from a swatch}), so no
   * sequence of API calls can produce a swatch this sees as empty. The guard costs one branch
   * and turns a hypothetical {@code ArithmeticException} into the same white {@link
   * ColorNativePattern#paletteColor} independently answers for an empty list.
   */
  private static int wrapIndex(int index, int stopCount) {
    if (stopCount <= 0) {
      return 1;
    }
    return Math.floorMod(index - 1, stopCount) + 1;
  }

}
