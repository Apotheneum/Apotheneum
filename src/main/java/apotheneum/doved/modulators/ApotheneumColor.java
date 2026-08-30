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

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;

import apotheneum.Apotheneum;
import apotheneum.doved.patterns.ColorNativePattern;

/**
 * The global colour state every {@link ColorNativePattern} reads instead of owning its own
 * palette knobs. Replaces per-pattern {@code paletteIndex}/{@code hueOffset}/{@code satTrim} —
 * see {@code ColorNativePattern}'s class javadoc for why that duplication existed and why it is
 * gone. This class is the single place that decision now lives.
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
 * The four {@link SurfaceOffset} children — {@link #cubeExterior}, {@link #cubeInterior},
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
@LXModulator.Global("Apotheneum Color")
@LXModulator.Device("Apotheneum Color")
@LXCategory("Apotheneum/doved")
public class ApotheneumColor extends LXModulator
  implements LXOscComponent, UIModulatorControls<ApotheneumColor> {

  private static final int MAX_COLORS = LXSwatch.MAX_COLORS;
  private static final double MAX_HUE_OFFSET_DEGREES = 60;
  private static final double MAX_SATURATION_TRIM_PERCENT = 40;

  /**
   * The one live {@code ApotheneumColor} every {@code ColorNativePattern} reads, mirroring the
   * static-singleton-with-lifecycle pattern {@link Apotheneum#cube}/{@link Apotheneum#cylinder}
   * already use for exactly this need: a piece of global state reached by a plain Java reference,
   * not by an OSC/path lookup. Set in the constructor, cleared in {@link #dispose()}. Only one
   * instance is expected to exist in a project at a time; a second construction logs a warning
   * rather than silently shadowing the first, since only whichever instance is assigned last
   * would ever be read.
   */
  public static ApotheneumColor instance = null;

  /** Which of the installation's four independently-addressable surfaces a pixel is on. */
  public enum Surface {
    CUBE_EXTERIOR,
    CUBE_INTERIOR,
    CYLINDER_EXTERIOR,
    CYLINDER_INTERIOR;

    /**
     * Resolves a surface from the {@link Apotheneum.Orientation} object a pattern is already
     * iterating — every {@code ColorNativePattern} subclass renders one physical orientation at a
     * time (see e.g. {@code Rockfall}'s {@code surfaceWaters}, {@code Waterfall}/{@code Dunes}/
     * {@code Grass}'s per-orientation {@code output}/{@code render} methods), so this is a
     * reference-equality lookup against geometry the pattern already has in hand, not new
     * geometry-detection machinery. Returns {@code null} if Apotheneum isn't loaded, or the
     * orientation matches none of the four (e.g. a model without an interior).
     */
    public static Surface of(Apotheneum.Orientation orientation) {
      if ((orientation == null) || (Apotheneum.cube == null) || (Apotheneum.cylinder == null)) {
        return null;
      }
      if (orientation == Apotheneum.cube.exterior) {
        return CUBE_EXTERIOR;
      }
      if (orientation == Apotheneum.cube.interior) {
        return CUBE_INTERIOR;
      }
      if (orientation == Apotheneum.cylinder.exterior) {
        return CYLINDER_EXTERIOR;
      }
      if (orientation == Apotheneum.cylinder.interior) {
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
   * <p>Deliberately <b>not</b> an {@code LXComponent} of its own, unlike
   * {@code ColorNativePattern.ColorRole}: {@code addChild} requires the calling component to
   * already be registered (have a parent/{@code lx}), and an {@code LXModulator} does not have
   * either until <i>after</i> its own constructor returns and it is added to an engine (e.g.
   * {@code lx.engine.modulation.addModulator(...)}) -- a pattern's {@code super(lx)} already
   * carries an {@code LX} reference at construction time, a bare {@code LXModulator(String)}
   * does not. Building these as plain field groups and registering each parameter directly on
   * {@code ApotheneumColor} itself (which {@link #addParameter} allows freely at construction
   * time, exactly as {@code Selector}/{@code SampleHold} already do for their own parameters)
   * sidesteps that ordering hazard entirely rather than deferring construction to work around
   * it.</p>
   */
  public static final class SurfaceOffset {

    public final DiscreteParameter indexOffset;
    public final CompoundParameter hueOffset;
    public final CompoundParameter satTrim;

    private SurfaceOffset() {
      // Range +-2: the shared pair/swap result never leaves {1,2,3} (design/color-system.md
      // section 4's whole safety argument), so +-2 reaches every one of the five swatch stops
      // from either end without needing a wider throw than this surface's differentiation
      // actually calls for -- "a stop or two off", not a knob that can reach clear around.
      this.indexOffset = new DiscreteParameter("Index Offset", 0, -2, 3)
        .setDescription(
          "Integer offset on the resolved palette index for this surface; wraps, does not "
          + "clamp, so every value is a distinct stop");

      this.hueOffset = new CompoundParameter("H-Off", 0, -MAX_HUE_OFFSET_DEGREES, MAX_HUE_OFFSET_DEGREES)
        .setUnits(CompoundParameter.Units.DEGREES)
        .setPolarity(CompoundParameter.Polarity.BIPOLAR)
        .setDescription("Hue offset applied to this surface's resolved palette color");

      this.satTrim = new CompoundParameter("S-Trim", 0, 0, -MAX_SATURATION_TRIM_PERCENT)
        .setUnits(CompoundParameter.Units.PERCENT)
        .setDescription("Saturation trim below this surface's resolved palette color");
    }
  }

  /**
   * Which pair of adjacent palette stops primary/secondary resolve from, everywhere. The knob-12
   * "Color"/"Pair" analog from {@code design/color-system.md} section 4: base 1 or base 2.
   */
  public final CompoundDiscreteParameter pair =
    new CompoundDiscreteParameter("Pair", new String[] { "1", "2" })
    .setDescription("Which pair of adjacent palette stops primary/secondary resolve from, everywhere");

  /**
   * Exchanges primary and secondary, everywhere. The switch-12 "Swap"/"Flip" analog. A
   * {@code CompoundDiscreteParameter} rather than a {@code BooleanParameter}, matching
   * {@code ModColorize.invert}'s precedent: only the former is an
   * {@code LXCompoundModulation.Target}.
   */
  public final CompoundDiscreteParameter swap =
    new CompoundDiscreteParameter("Swap", new String[] { "Off", "On" })
    .setDescription("Exchange primary and secondary, everywhere");

  public final SurfaceOffset cubeExterior;
  public final SurfaceOffset cubeInterior;
  public final SurfaceOffset cylinderExterior;
  public final SurfaceOffset cylinderInterior;

  public ApotheneumColor() {
    this("Apotheneum Color");
  }

  public ApotheneumColor(String label) {
    super(label);

    addParameter("pair", this.pair);
    addParameter("swap", this.swap);

    this.cubeExterior = surface("cubeExterior");
    this.cubeInterior = surface("cubeInterior");
    this.cylinderExterior = surface("cylinderExterior");
    this.cylinderInterior = surface("cylinderInterior");

    if (instance != null) {
      LX.log(
        "[APOTHENEUM] Warning: a second ApotheneumColor was constructed; only the most "
        + "recently constructed instance is read by ColorNativePattern");
    }
    instance = this;
  }

  /** Builds one surface's offset group and registers its three parameters directly on this
   * modulator, flattened under {@code <path>...} keys since the offset itself is not a nested
   * {@code LXComponent} -- see {@link SurfaceOffset}'s javadoc for why. */
  private SurfaceOffset surface(String path) {
    final SurfaceOffset offset = new SurfaceOffset();
    addParameter(path + "IndexOffset", offset.indexOffset);
    addParameter(path + "HueOffset", offset.hueOffset);
    addParameter(path + "SatTrim", offset.satTrim);
    return offset;
  }

  @Override
  public void dispose() {
    if (instance == this) {
      instance = null;
    }
    super.dispose();
  }

  /**
   * This modulator's own scalar output is unused — nothing reads {@code getValue()} on it, the
   * same way a {@code Selector}'s per-input parameters matter more than its aggregate value.
   * Every real output is one of {@link #primaryColor}/{@link #secondaryColor}, read directly by
   * a {@code ColorNativePattern} rather than through modulation.
   */
  @Override
  protected double computeValue(double deltaMs) {
    return 0;
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

  private static final float SURFACE_COLUMN_WIDTH = 44;

  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, ApotheneumColor color) {
    uiModulator.setLayout(UI2dContainer.Layout.HORIZONTAL, 4);

    uiModulator.addChildren(
      UI2dContainer.newVerticalContainer(UIKnob.HEIGHT * 2 + 8, 8,
        newDropMenu(color.pair),
        newDropMenu(color.swap)
      ),
      surfaceColumn(color.cubeExterior, "Cube Ext"),
      surfaceColumn(color.cubeInterior, "Cube Int"),
      surfaceColumn(color.cylinderExterior, "Cyl Ext"),
      surfaceColumn(color.cylinderInterior, "Cyl Int")
    );
  }

  private UI2dComponent surfaceColumn(SurfaceOffset offset, String label) {
    return UI2dContainer.newVerticalContainer(UIKnob.HEIGHT * 2 + 8, 4,
      newIntegerBox(offset.indexOffset, SURFACE_COLUMN_WIDTH),
      newKnob(offset.hueOffset),
      newKnob(offset.satTrim)
    );
  }

}
