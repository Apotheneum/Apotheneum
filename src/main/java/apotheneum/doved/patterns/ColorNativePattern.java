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
     * Refreshes the cached colour-enable flag once per frame. A subclass calls this once per
     * frame per role, exactly as before; call it before any {@link #color} calls for that frame.
     */
    void update() {
      this.colorEnabled = this.colorToggle.isOn();
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
      final ApotheneumColor color = ApotheneumColor.instance;
      if (color == null) {
        // No ApotheneumColor in the project (not yet added, or removed): neutral rather than
        // throwing, matching how Apotheneum.exists gates ApotheneumPattern.
        return LXColor.WHITE;
      }
      return this.isPrimary ? color.primaryColor(surface) : color.secondaryColor(surface);
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
