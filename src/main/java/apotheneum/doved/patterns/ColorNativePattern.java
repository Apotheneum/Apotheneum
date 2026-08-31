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

package apotheneum.doved.patterns;

import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

import apotheneum.doved.modulators.ApotheneumColor;

/**
 * Base class for "colour-native" patterns — patterns that own their own colour logic rather
 * than rendering luminance and taking colour from a downstream Colorize-style effect. Provides
 * exactly two colour roles, {@link #primary} and {@link #secondary}, each attached at the fixed,
 * generic {@code addChild} keys {@code "primary"}/{@code "secondary"} — every colour-native
 * pattern exposes the same addresses, so {@code .../primary/amount} means the same thing on
 * every subclass.
 *
 * <p><b>2026: this class no longer owns colour, only physics coupling.</b> Each role used to
 * carry its own {@code paletteIndex}/{@code hueOffset}/{@code satTrim} — nine parameters per
 * pattern instance, duplicated across every {@code ColorNativePattern} on the rig and individually
 * relay-wired. That duplication is gone. A role now holds only {@link ColorRole#amount}, the
 * physics-coupling knob — the one thing that is genuinely per-pattern, since only the pattern
 * knows its own per-pixel physics signal. The colour itself — which palette stop, which hue/
 * saturation offset for the surface a pixel is on — comes from the single global
 * {@link ApotheneumColor}, read fresh on every call to {@link ColorRole#color}. Every
 * color-native pattern therefore agrees with every other one; the only axis that still varies
 * per pixel is which of the four physical surfaces ({@link ApotheneumColor.Surface}) it is on,
 * which {@code ApotheneumColor} resolves independently for each of the four.
 *
 * <p><b>Geometry-awareness stays here, deliberately.</b> An earlier design tried moving colour
 * resolution into a global {@code LXEffect} sitting after all content; that cannot work, because
 * an effect sees only final {@code colors[]} and has no way to couple a pattern's own per-pixel
 * physics into the result. A subclass supplies {@link ApotheneumColor.Surface} by resolving it
 * from whatever {@link apotheneum.Apotheneum.Orientation} it is already iterating (every subclass
 * already renders one physical orientation at a time — see e.g. {@code Rockfall}'s
 * {@code surfaceWaters} or {@code Waterfall}/{@code Dunes}/{@code Grass}'s per-orientation
 * {@code output}/{@code render} methods) via {@link ApotheneumColor.Surface#of}, then passes that
 * plus its own physics scalar into {@link ColorRole#color}.
 *
 * <p><b>The device-panel colour controls are built by this class, not by subclasses.</b> This
 * class implements {@link UIDeviceControls}&lt;{@code ColorNativePattern}&gt; itself and provides
 * a working {@link #buildDeviceControls} that lays out both roles' columns — a bare subclass
 * that never overrides it gets the full colour UI with zero device-control code of its own. A
 * subclass that also wants its own columns (as {@code Rockfall} does) overrides
 * {@code buildDeviceControls(UI, UIDevice, ColorNativePattern)} — note the parameter type is
 * {@code ColorNativePattern}, not the subclass, because Java does not allow a class to implement
 * the same generic interface at two different type arguments, so the subclass cannot separately
 * declare {@code UIDeviceControls<Rockfall>} — and calls {@link #buildColorDeviceControls} once
 * it has added its own columns, so the colour column lands last. See {@code Rockfall
 * .buildDeviceControls} for the pattern to follow.</p>
 *
 * <p><b>Snapshot/clip limitation:</b> {@link #primary} and {@link #secondary} are attached with
 * {@code addChild(...)}, exactly as the pattern this class generalizes did. That means their
 * {@code amount} parameter is <b>invisible to {@link heronarts.lx.snapshot.LXSnapshot} and to
 * clip recording</b>. In LX 1.2.2, {@code LXSnapshot.addDeviceView} walks only
 * {@code device.getParameters()}, {@code device.getLayers()}, and
 * {@code device.automationChildren} — and {@code automationChildren} is never populated
 * anywhere in LX 1.2.2 ({@code LXDeviceComponent.addAutomationChild} has no callers in the
 * framework at all). This is an accepted trade-off here, not an oversight — see
 * {@code ApotheneumColor} for where the parameters that actually decide colour now live, which
 * has the identical limitation for the identical reason.</p>
 *
 * <p><b>Why it extends {@link ViewMaskedPattern} rather than {@code ApotheneumPattern}
 * directly:</b> most patterns in this family write their colour buffer through global geometry
 * by point index, which per {@code lx-coding-guidelines.md} &#167;18 means honouring a
 * pattern-level model view requires a membership mask. That machinery is deliberately kept in a
 * separate base class rather than folded in here, because view masking is orthogonal to colour —
 * a brightness-only pattern should be able to inherit it too. It is entirely opt-in by call: a
 * subclass that never calls {@code updateViewMask()} behaves exactly as it did before this class
 * was reparented.</p>
 *
 * <h2>Physics arrays, mirrored and colorized — 2026-08-30</h2>
 *
 * The owner's own framing of the problem, after watching {@code Fireball} and {@code Waterfall}
 * force their interior to match their exterior: <em>"They should be modified to just copy the
 * pixels, like the white and black, and then the colorize happens after that. The colorize
 * should be a global way of doing things, not different per pattern."</em> {@link
 * #colorizeCells} is that mechanism's shared half: it walks a pattern-defined range of cells and
 * writes each one's colour independently to its real exterior point and (if it has one) its
 * real interior mirror point, resolving {@link ApotheneumColor.Surface} fresh for each of the
 * (up to two) real points a cell has, and never reading {@code colors[]} back to derive one
 * write from the other.
 *
 * <p><b>Deliberately not a fixed data shape.</b> An earlier design tried a single shared
 * "substance" array per pattern; that does not fit every subclass — {@code Waterfall} carries
 * two distinct substances (rock and water) with their own colour roles, and {@code Fireball}
 * derives both its ember and core roles from one heat value plus a recomputed noise term. The
 * owner's resolution: <em>"What if it's just multiple arrays that you can optionally pass
 * through and populate?"</em> So this class prescribes no array at all — a pattern owns
 * however many physics arrays it has, named however fits its own domain ({@code heat}, {@code
 * rockIntensity}, {@code colorSlope}), sized once at construction or on a model change, never
 * per frame. {@link #colorizeCells} never sees them; a pattern's own {@link PhysicsColorizer}
 * closure reads them and returns a finished colour. This is why adopting the mechanism costs
 * nothing for a pattern whose substance does not fit a single scalar per pixel, and nothing for
 * one that needs no mirroring at all ({@code LavaLamp} paints exterior only — see {@code
 * interiorPointIndexOrNull}'s own javadoc for why that falls out naturally rather than needing a
 * branch).
 *
 * <p>See {@code docs/color-native-pattern-substance.md} for the full design, the worked {@code
 * Fireball} adoption (before/after), and per-pattern notes on what the other six subclasses
 * would need to change to adopt this. {@code Fireball} and {@code Waterfall} are both migrated
 * and are the two worked examples; the other five are not.
 */
public abstract class ColorNativePattern extends ViewMaskedPattern
  implements UIDeviceControls<ColorNativePattern> {

  private static final double COLOR_BRIGHTNESS_MODULATION = .45;
  private static final double COLOR_SATURATION_MODULATION = .3;
  private static final String PRIMARY_PATH = "primary";
  private static final String PRIMARY_LABEL = "Primary";
  private static final String SECONDARY_PATH = "secondary";
  private static final String SECONDARY_LABEL = "Secondary";

  /**
   * One colour role (primary or secondary): physics-coupling only. See {@link ColorNativePattern}
   * for why the palette/offset vocabulary this used to carry moved to {@link ApotheneumColor}.
   */
  public static final class ColorRole extends LXComponent implements LXOscComponent {

    /** Couples this role's colour to the physics-driven perturbation the subclass supplies per
     * pixel. Unchanged from before this class stopped owning colour: range, units, and default
     * are still the caller's choice. */
    public final CompoundParameter amount;

    private final boolean isPrimary;
    private final BooleanParameter colorToggle;
    private boolean colorEnabled;

    /**
     * The engine's {@code ApotheneumColor}, re-resolved once per frame in {@link #update()}
     * rather than once per pixel in {@link #color} -- {@link ApotheneumColor#get(LX)} is a
     * single {@code lx.engine.getChild(...)} lookup, cheap enough to not need caching at all,
     * but a pattern like {@code Rockfall} calls {@link #color} up to once per point (28,320
     * points), so resolving it once per frame and reusing the result for every pixel in that
     * frame is the same "cheap, not stale beyond one frame" trade this class already made for
     * {@link #colorEnabled}.
     */
    private ApotheneumColor activeColor;

    private ColorRole(
      LX lx, String label, double defaultAmount, BooleanParameter colorToggle, boolean isPrimary
    ) {
      super(lx, label);
      this.colorToggle = colorToggle;
      this.isPrimary = isPrimary;

      this.amount = new CompoundParameter("Amount", defaultAmount)
        .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
        .setDescription("Coupling of the physics-driven perturbation to this role's color");

      addParameter("amount", this.amount);
    }

    /**
     * Refreshes the cached colour-enable flag and the cached {@code ApotheneumColor} reference
     * once per frame. A subclass calls this once per frame per role, exactly as before; call it
     * before any {@link #color} calls for that frame.
     */
    void update() {
      this.colorEnabled = this.colorToggle.isOn();
      this.activeColor = ApotheneumColor.get(this.lx);
    }

    /**
     * Resolves this role's fully-composited colour for one pixel on {@code surface}: the base
     * colour comes from the shared {@link ApotheneumColor} (or neutral white if none is present
     * in the project), then this role's own {@link #amount} couples the physics perturbation on
     * top, exactly as before. {@code surface} is resolved by the caller from whatever
     * {@code Apotheneum.Orientation} it is currently rendering — see
     * {@link ApotheneumColor.Surface#of}.
     */
    int color(ApotheneumColor.Surface surface, double physics) {
      final int base = resolveBase(surface);
      return this.colorEnabled
        ? modulatedColor(base, this.amount.getValue(), physics)
        : modulatedColor(LXColor.WHITE, this.amount.getValue(), physics);
    }

    private int resolveBase(ApotheneumColor.Surface surface) {
      // No ApotheneumColor registered on the engine (the core plugin failed to load -- should
      // not happen in practice now that registration is engine-owned, not user-added) resolves
      // neutral rather than throwing, matching how Apotheneum.exists gates ApotheneumPattern --
      // and logs once on the transition rather than staying silent, which is exactly what made
      // this indistinguishable from "resolving wrong" on 2026-08-29. That fallback and its
      // one-time-per-transition log live on ApotheneumColor itself now
      // (resolvePrimaryOrNeutral/resolveSecondaryOrNeutral), shared with
      // GradientMultiplyEffect, rather than duplicated here -- one fallback to get right, not
      // two copies that could drift.
      return this.isPrimary
        ? ApotheneumColor.resolvePrimaryOrNeutral(this.activeColor, surface)
        : ApotheneumColor.resolveSecondaryOrNeutral(this.activeColor, surface);
    }
  }

  /** The pattern's primary colour role, attached at the fixed {@code addChild} key "primary". */
  public final ColorRole primary;

  /** The pattern's secondary colour role, attached at the fixed {@code addChild} key "secondary". */
  public final ColorRole secondary;

  /** Enables palette colour; off resolves every role as a neutral tone at the same brightness. */
  public final BooleanParameter color = new BooleanParameter("Color", true)
    .setDescription("Enable palette color; off preserves brightness and physics in neutral tones");

  protected ColorNativePattern(LX lx, double primaryAmount, double secondaryAmount) {
    super(lx);
    addParameter("color", this.color);
    this.primary = colorRole(PRIMARY_PATH, PRIMARY_LABEL, primaryAmount, true);
    this.secondary = colorRole(SECONDARY_PATH, SECONDARY_LABEL, secondaryAmount, false);
  }

  private ColorRole colorRole(String path, String label, double defaultAmount, boolean isPrimary) {
    final ColorRole role = new ColorRole(this.lx, label, defaultAmount, this.color, isPrimary);
    addChild(path, role);
    return role;
  }

  @Override
  public void dispose() {
    this.primary.dispose();
    this.secondary.dispose();
    super.dispose();
  }

  /**
   * Default device panel for a colour-native pattern that adds nothing of its own: just the
   * colour column. A subclass with its own controls overrides this (parameter type
   * {@code ColorNativePattern}, not the subclass — see class javadoc), adds its own columns, and
   * calls {@link #buildColorDeviceControls} last so the colour column lands at the end of the
   * panel.
   */
  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);
    buildColorDeviceControls(ui, uiDevice);
  }

  /**
   * Appends the colour column to {@code uiDevice}. Does <b>not</b> call
   * {@code uiDevice.setLayout(...)} — the caller must already have established the device's
   * layout (a subclass overriding {@link #buildDeviceControls} does this once, before adding any
   * columns, its own or this one).
   *
   * <p>Down from four columns (two roles times a palette-preview-plus-four-knobs column apiece)
   * to one: the "Color" enable button and each role's single remaining knob,
   * {@code amount} — three controls, one column, matching this repository's column limit
   * directly rather than needing the two-column-per-role split the old, wider vocabulary
   * required.</p>
   */
  protected final void buildColorDeviceControls(UI ui, UIDevice uiDevice) {
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Color",
      newButton(this.color),
      newKnob(this.primary.amount),
      newKnob(this.secondary.amount)
    ).setChildSpacing(6);
  }

  /**
   * Resolves the final colour for one cell already known to belong to {@code surface} --
   * the whole of what a pattern needs to supply {@link #colorizeCells}. A pattern's own
   * "physics" array(s) (however many it has, named however it likes -- {@code heat},
   * {@code rockIntensity}, {@code colorSlope}) live entirely behind this lambda's closure;
   * {@link #colorizeCells} never sees them and never needs to know how many there are. See
   * {@code docs/color-native-pattern-substance.md} for the full design and a worked example.
   */
  @FunctionalInterface
  public interface PhysicsColorizer {
    int colorFor(ApotheneumColor.Surface surface, int cell);
  }

  /**
   * Walks a pattern-defined range of cells {@code [0, cellCount)}, resolving and writing each
   * cell's colour independently for its real exterior point and -- if it has one -- its real
   * interior mirror point, via {@code colorAt}. This is the shared "mirror the uncolorized
   * substance, then colorize per real surface" mechanism -- see {@code
   * docs/color-native-pattern-substance.md} for the design and {@code Fireball} for the worked
   * adoption.
   *
   * <p>{@code exteriorPointIndex}/{@code interiorPointIndexOrNull} map a cell to a real point's
   * global colour-buffer index -- generalized from {@code Fireball}'s own pre-existing {@code
   * pointIndex}/{@code mirrorIndex} arrays. A negative entry means "no real point for this
   * cell" and is skipped without calling {@code colorAt} for it, so a ragged or
   * partially-usable cell range (a door-shortened column) needs no special-casing beyond
   * marking those cells with a negative index. {@code interiorPointIndexOrNull} (and {@code
   * interiorSurfaceOrNull}) may be {@code null} outright for a pattern with no interior
   * geometry for this cell range at all (see {@code LavaLamp}) -- the mirror write is then
   * skipped for every cell, not attempted and masked away; this is the "no branch needed" case
   * falling out naturally rather than requiring the caller to special-case it.
   *
   * <p>Each of the up to two writes per cell is independently guarded by {@link
   * #isViewPoint(int)} and never reads {@code colors[]} back to derive the other -- the same
   * two properties {@code Fireball}'s own hand-written mirror already had (view-mask
   * correctness; never assuming what a different pattern or a bulk copy left behind), now
   * enforced once here instead of by convention in every subclass that wants them. {@code
   * colorAt} is called once per real surface a cell actually has a point on -- once for
   * exterior-only geometry, twice (once per surface identity) for a cell with a real interior
   * mirror -- so a pattern with its own bespoke per-role colour blend (like {@code Fireball}'s
   * ember/core blackbody curve) keeps that blend entirely as it was; only the surface-walking,
   * view-masking and write mechanics move here.
   *
   * <p>No allocation happens in this method or anything it calls -- {@code exteriorPointIndex}/
   * {@code interiorPointIndexOrNull} are arrays the pattern already allocated once (at
   * construction or on a model change, exactly as {@code Fireball}'s {@code attach()} already
   * did before this method existed).
   */
  protected final void colorizeCells(
    int cellCount,
    int[] exteriorPointIndex,
    ApotheneumColor.Surface exteriorSurface,
    int[] interiorPointIndexOrNull,
    ApotheneumColor.Surface interiorSurfaceOrNull,
    PhysicsColorizer colorAt
  ) {
    for (int cell = 0; cell < cellCount; ++cell) {
      final int exteriorPoint = exteriorPointIndex[cell];
      if ((exteriorPoint >= 0) && isViewPoint(exteriorPoint)) {
        colors[exteriorPoint] = colorAt.colorFor(exteriorSurface, cell);
      }
      if (interiorPointIndexOrNull != null) {
        final int interiorPoint = interiorPointIndexOrNull[cell];
        if ((interiorPoint >= 0) && isViewPoint(interiorPoint)) {
          colors[interiorPoint] = colorAt.colorFor(interiorSurfaceOrNull, cell);
        }
      }
    }
  }

  /**
   * Public and static because {@link ApotheneumColor} calls this directly rather than
   * re-deriving the same clamp-to-a-valid-stop logic — this is the one place it is implemented.
   */
  public static int paletteColor(List<LXDynamicColor> colors, int stop) {
    if (colors.isEmpty()) {
      return LXColor.WHITE;
    }
    return colors.get(Math.min(Math.max(stop, 0), colors.size() - 1)).getColor();
  }

  /**
   * Applies a hue offset (degrees) and a saturation trim (percent) to a resolved palette color.
   * This function clamps saturation to [0, 100] but does not itself forbid a positive
   * {@code satTrimPercent} — the "cannot raise saturation" guarantee comes from the caller's
   * offset parameter being range-bounded to [-40, 0], not from this function refusing a positive
   * input. Exits early, returning {@code color} unchanged, when both offsets are exactly zero —
   * the default — so default output is bit-identical to the unmodified palette color rather than
   * a numerically-equal-but-reconstructed one.
   *
   * <p>Public and static because {@link ApotheneumColor} calls this directly rather than
   * re-deriving the same offset math — this is the one place it is implemented.</p>
   */
  public static int applyOffsets(int color, double hueOffsetDegrees, double satTrimPercent) {
    if (hueOffsetDegrees == 0 && satTrimPercent == 0) {
      return color;
    }
    final double hue = wrapDegrees(LXColor.h(color) + hueOffsetDegrees);
    final double saturation = LXUtils.clamp(LXColor.s(color) + satTrimPercent, 0, 100);
    return LXColor.hsb(hue, saturation, LXColor.b(color));
  }

  private static double wrapDegrees(double degrees) {
    final double wrapped = degrees % 360;
    return wrapped < 0 ? wrapped + 360 : wrapped;
  }

  static int modulatedColor(int color, double amount, double physics) {
    if (amount == 0) {
      return color;
    }
    final double modulation = LXUtils.clamp(amount, 0, 1) * LXUtils.clamp(physics, -1, 1);
    final double shiftedSaturation = LXUtils.clamp(
      LXColor.s(color) * (1 - COLOR_SATURATION_MODULATION * modulation),
      0,
      100
    );
    final double shiftedBrightness = LXUtils.clamp(
      LXColor.b(color) * (1 + COLOR_BRIGHTNESS_MODULATION * modulation),
      0,
      100
    );
    return LXColor.hsb(LXColor.h(color), shiftedSaturation, shiftedBrightness);
  }

  static int compositeColors(
    int primaryColor,
    double primaryIntensity,
    int secondaryColor,
    double secondaryIntensity
  ) {
    final int litPrimary = LXColor.scaleBrightness(
      primaryColor,
      LXUtils.clamp(primaryIntensity, 0, 1)
    );
    return secondaryIntensity <= 0 ? litPrimary : LXColor.lerp(
      litPrimary,
      secondaryColor,
      LXUtils.clamp(secondaryIntensity, 0, 1)
    );
  }

  /**
   * Mixes two tones of the same element by their relative energies, then scales the result by
   * their total energy. Unlike {@link #compositeColors(int, double, int, double)}, which treats
   * the secondary color as an overlay, a dim pixel made entirely of the secondary tone remains
   * fully secondary-colored.
   */
  static int blendTones(
    int primaryColor,
    double primaryIntensity,
    int secondaryColor,
    double secondaryIntensity
  ) {
    final double total = primaryIntensity + secondaryIntensity;
    if (total <= 0) {
      return LXColor.BLACK;
    }
    final int tone = LXColor.lerp(primaryColor, secondaryColor, secondaryIntensity / total);
    return LXColor.scaleBrightness(tone, LXUtils.clamp(total, 0, 1));
  }
}
