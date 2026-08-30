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

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;

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
 * <h2>Per-surface differentiation is structural, not gestural</h2>
 *
 * The four {@link SurfaceOffset} groups — {@link #cubeExterior}, {@link #cubeInterior},
 * {@link #cylinderExterior}, {@link #cylinderInterior} — are fixed, standing relationships each
 * surface carries, not a live per-surface control surface. Each holds:
 *
 * <ul>
 *   <li>{@code indexOffset} — an integer offset on the resolved palette index, default 0 (that
 *       surface reads exactly the shared pair/swap result, identical to every other surface).
 *       Non-zero, a surface sits a stop or two off the rest, the way channel 4's per-view
 *       {@code GradientPattern}s already put genuinely different palette colours on cube versus
 *       cylinder today.</li>
 *   <li>{@code hueOffset} / {@code satTrim} — the same bounded vocabulary
 *       {@code ColorNativePattern.ColorRole} used to carry, &#177;60&#176; and 0 to &#8722;40%
 *       respectively, for the same reasons (continuity of this rig's palette ramp; headroom in
 *       this rig's swatch saturations).</li>
 * </ul>
 *
 * <p><b>{@code indexOffset} wraps, it does not clamp.</b> {@code paletteIndex} silently clamping
 * at the swatch ceiling is a known, previously-shipped failure mode in this codebase (see
 * {@code ColorNativePattern}'s "Do not give a color-native channel its own mode table" note, and
 * {@code design/color-system.md} &#167;3's clamp warning) — a clamped offset reads as a dead knob,
 * identical to a live one that simply has nothing left to give. Wrapping means every value of
 * {@code indexOffset} is visibly a different stop; there is no plateau to land on by accident.</p>
 */
public class ApotheneumColor extends LXComponent implements LXOscComponent {

  /** Engine path; parameters live at {@code /lx/apotheneumColor/*}, mirroring how
   * {@code apotheneum.video.ApotheneumVideo} registers at {@code /lx/apotheneumVideo/*}. */
  public static final String PATH = "apotheneumColor";

  private static final int MAX_COLORS = LXSwatch.MAX_COLORS;
  private static final double MAX_HUE_OFFSET_DEGREES = 60;
  private static final double MAX_SATURATION_TRIM_PERCENT = 40;

  /**
   * The engine's registered {@code ApotheneumColor}, or {@code null} if
   * {@code apotheneum.doved.ApotheneumColorPlugin} did not register one (the plugin failed to
   * load, or is disabled). Resolved fresh every call — see the class javadoc for why there is
   * nothing to cache across calls, only within one caller's own frame if it is called per pixel.
   */
  public static ApotheneumColor get(LX lx) {
    final LXComponent child = lx.engine.getChild(PATH);
    return (child instanceof ApotheneumColor) ? (ApotheneumColor) child : null;
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

  /**
   * A surface's standing offset from the shared, gesture-driven {@link #pair}/{@link #swap}
   * result. See the class javadoc for why these are fixed relationships rather than a second
   * live control surface.
   *
   * <p>A plain field group with its three parameters registered directly on
   * {@code ApotheneumColor} under flattened {@code <path>...} keys, not a nested
   * {@code LXComponent} — kept exactly as it was when this class was still an
   * {@code LXModulator} (where a nested {@code addChild} was unsafe before the modulator had a
   * parent) so the parameter paths a live project may already reference do not move as a side
   * effect of this relocation. Now that construction always carries a real {@code lx} (see
   * {@link #ApotheneumColor(LX)}), a nested component would work too; not changed here because
   * nothing asked for it and stable paths matter more than the tidier shape.</p>
   */
  public static final class SurfaceOffset {

    public final DiscreteParameter indexOffset;
    public final CompoundParameter hueOffset;
    public final CompoundParameter satTrim;

    private SurfaceOffset(String label) {
      // Range +-2: the shared pair/swap result never leaves {1,2,3} (design/color-system.md
      // section 4's whole safety argument), so +-2 reaches every one of the five swatch stops
      // from either end without needing a wider throw than this surface's differentiation
      // actually calls for -- "a stop or two off", not a knob that can reach clear around.
      this.indexOffset = new DiscreteParameter(label, 0, -2, 3)
        .setDescription(
          "Integer offset on the resolved palette index for " + label
          + "; wraps, does not clamp, so every value is a distinct stop");

      this.hueOffset = new CompoundParameter("H-Off", 0, -MAX_HUE_OFFSET_DEGREES, MAX_HUE_OFFSET_DEGREES)
        .setUnits(CompoundParameter.Units.DEGREES)
        .setPolarity(CompoundParameter.Polarity.BIPOLAR)
        .setDescription("Hue offset applied to this surface's resolved palette color");

      this.satTrim = new CompoundParameter("S-Trim", 0, 0, -MAX_SATURATION_TRIM_PERCENT)
        .setUnits(CompoundParameter.Units.PERCENT)
        .setDescription("Saturation trim below this surface's resolved palette color");
    }
  }

  private static final String[] PAIR_OPTIONS = { "1", "2" };
  private static final String[] SWAP_OPTIONS = { "Off", "On" };

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

  public final SurfaceOffset cubeExterior;
  public final SurfaceOffset cubeInterior;
  public final SurfaceOffset cylinderExterior;
  public final SurfaceOffset cylinderInterior;

  /**
   * Constructed exactly once, by {@code apotheneum.doved.ApotheneumColorPlugin.initialize},
   * which passes the real {@code lx} straight through -- unlike the old {@code LXModulator}
   * shape, there is no window where this object exists without an {@code lx} reference.
   */
  public ApotheneumColor(LX lx) {
    super(lx, "Apotheneum Color");

    addParameter("pair", this.pair);
    addParameter("swap", this.swap);
    // See pair's javadoc: the (String, String[]) constructor above sizes the range correctly
    // but never stores the options array, so getOptions() would otherwise return null.
    this.pair.setOptions(PAIR_OPTIONS, false);
    this.swap.setOptions(SWAP_OPTIONS, false);

    // "Cube Ext"/"Cube Int" truncated in a 40px-wide knob render -- confirmed, not assumed --
    // and dropping the space alone did not reliably fix it either (proportional font: "CubeExt"
    // still clipped while "CubeInt" happened to fit). "Cub Ext"/"Cub Int" match "Cyl Ext"/
    // "Cyl Int" exactly in shape and length, which rendered in full. Kept even though this
    // class no longer builds its own knobs (the GLOBAL-pane section does), since the label is
    // still what any control binds to.
    this.cubeExterior = surface("cubeExterior", "Cub Ext");
    this.cubeInterior = surface("cubeInterior", "Cub Int");
    this.cylinderExterior = surface("cylinderExterior", "Cyl Ext");
    this.cylinderInterior = surface("cylinderInterior", "Cyl Int");
  }

  /** Builds one surface's offset group and registers its three parameters directly on this
   * component, flattened under {@code <path>...} keys -- see {@link SurfaceOffset}'s javadoc
   * for why. {@code label} becomes {@code indexOffset}'s own display name. */
  private SurfaceOffset surface(String path, String label) {
    final SurfaceOffset offset = new SurfaceOffset(label);
    addParameter(path + "IndexOffset", offset.indexOffset);
    addParameter(path + "HueOffset", offset.hueOffset);
    addParameter(path + "SatTrim", offset.satTrim);
    return offset;
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

  /** This surface's fully-resolved primary color: shared index, this surface's own offsets. */
  public int primaryColor(Surface surface) {
    return resolvedColor(primaryIndex(), surface);
  }

  /** This surface's fully-resolved secondary color: shared index, this surface's own offsets. */
  public int secondaryColor(Surface surface) {
    return resolvedColor(secondaryIndex(), surface);
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
    noteResolution(color);
    return (color == null) ? LXColor.WHITE : color.primaryColor(surface);
  }

  /** {@link #secondaryColor(Surface)}'s counterpart to {@link #resolvePrimaryOrNeutral}. */
  public static int resolveSecondaryOrNeutral(ApotheneumColor color, Surface surface) {
    noteResolution(color);
    return (color == null) ? LXColor.WHITE : color.secondaryColor(surface);
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

  private int resolvedColor(int sharedIndex, Surface surface) {
    final SurfaceOffset offset = offsetFor(surface);
    if (offset == null) {
      return ColorNativePattern.paletteColor(this.lx.engine.palette.swatch.colors, sharedIndex - 1);
    }
    final int index = wrapIndex(sharedIndex + offset.indexOffset.getValuei());
    final int base = ColorNativePattern.paletteColor(this.lx.engine.palette.swatch.colors, index - 1);
    return ColorNativePattern.applyOffsets(base, offset.hueOffset.getValue(), offset.satTrim.getValue());
  }

  private SurfaceOffset offsetFor(Surface surface) {
    if (surface == null) {
      return null;
    }
    switch (surface) {
      case CUBE_EXTERIOR: return this.cubeExterior;
      case CUBE_INTERIOR: return this.cubeInterior;
      case CYLINDER_EXTERIOR: return this.cylinderExterior;
      case CYLINDER_INTERIOR: return this.cylinderInterior;
      default: return null;
    }
  }

  /** Wraps a 1-based palette index around the swatch's stop count rather than clamping it. */
  private static int wrapIndex(int index) {
    return Math.floorMod(index - 1, MAX_COLORS) + 1;
  }

}
