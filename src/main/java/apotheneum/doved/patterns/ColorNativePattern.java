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

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.vg.VGraphics;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

/**
 * Base class for "colour-native" patterns — patterns that own their own colour logic rather
 * than rendering luminance and taking colour from a downstream Colorize-style effect. Provides
 * exactly two colour roles, {@link #primary} and {@link #secondary}, each attached at the fixed,
 * generic {@code addChild} keys {@code "primary"}/{@code "secondary"} — every colour-native
 * pattern exposes the same addresses, so {@code .../primary/hueOffset} means the same thing on
 * every subclass. Both the roles' component labels and their device-panel column headers are
 * the fixed generic strings "Primary"/"Secondary" too — nothing subclass-specific, so a pattern
 * that drops these controls into its own panel (see {@link #buildColorDeviceControls}) reads
 * correctly regardless of what the pattern itself is called. A subclass supplies only each
 * role's default palette index and physics-coupling amount — genuinely per-pattern numeric
 * choices, not labels. Each role exposes the same four-parameter, modulatable vocabulary —
 * deliberately echoing {@code apotheneum.doved.effects.ColorizeMultiplyEffect}'s palette/offset
 * controls so the two feel like one system:
 *
 * <ul>
 *   <li>{@code paletteIndex} — which stop of the project palette swatch this role reads, 1-based
 *       to match {@link heronarts.lx.color.LXPalette.IndexSelector} — the convention every other
 *       palette selector in the app uses, including {@code ColorizeMultiplyEffect}'s own
 *       {@code paletteIndex}. This field cannot literally be an {@code IndexSelector}: that class
 *       extends plain {@link heronarts.lx.parameter.DiscreteParameter}, which is not an
 *       {@link heronarts.lx.modulation.LXCompoundModulation.Target} — the same reason this
 *       vocabulary needs {@link CompoundDiscreteParameter} in the first place. It stays
 *       discrete/snapping — it is not a continuous parameter floored at the read site. See
 *       {@link ColorRole} for how it still gets the same live-editable index labels an
 *       {@code IndexSelector} gets, despite not being one.</li>
 *   <li>{@code hueOffset} — hue offset in degrees, range &#177;60 (not &#177;180). This rig's
 *       palette pairs read as a continuous cool ramp; a wider offset breaks that continuity, so
 *       the bounds keep the knob's whole throw inside the usable region.</li>
 *   <li>{@code satTrim} — a one-sided saturation trim, range 0 to -40% (not a symmetric offset).
 *       This rig's swatches sit at saturation 92-95, so there is almost no headroom upward; a
 *       symmetric knob would have a dead half. The real use is pulling one role slightly down so
 *       two roles reading the same palette stop still read as different colours.</li>
 *   <li>{@code amount} — couples this role's colour to the physics-driven perturbation the
 *       subclass supplies per pixel. Unchanged from the pre-generalization behaviour this class
 *       replaces (range, units, and default are the caller's choice, same as before).</li>
 * </ul>
 *
 * <p><b>Colour resolution order is fixed and matters:</b> resolve the palette stop, then apply
 * {@code hueOffset}/{@code satTrim} ({@link #applyOffsets}), then apply the physics-driven
 * perturbation ({@link #modulatedColor}), then — outside this class, wherever a subclass
 * composites and writes into its {@code colors[]} buffer — brightness scaling by whatever
 * intensity mask the subclass computes for that pixel. The offsets adjust the <i>chosen</i>
 * colour; the physics perturbation is a relative wobble layered on top of that choice.
 * Reversing this order makes the two fight each other instead of composing.</p>
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
 * it has added its own columns, so the colour columns land last and stay contiguous. See
 * {@code Rockfall.buildDeviceControls} for the pattern to follow.</p>
 *
 * <p><b>Snapshot/clip limitation:</b> {@link #primary} and {@link #secondary} are attached with
 * {@code addChild(...)}, exactly as the pattern this class generalizes did. That means their
 * four parameters apiece are <b>invisible to {@link heronarts.lx.snapshot.LXSnapshot} and to
 * clip recording</b>. In LX 1.2.2, {@code LXSnapshot.addDeviceView} walks only
 * {@code device.getParameters()}, {@code device.getLayers()}, and
 * {@code device.automationChildren} — and {@code automationChildren} is never populated
 * anywhere in LX 1.2.2 ({@code LXDeviceComponent.addAutomationChild} has no callers in the
 * framework at all). A snapshot or clip taken against a colour-native pattern will not capture
 * or restore either role's {@code paletteIndex}/{@code hueOffset}/{@code satTrim}/{@code amount}.
 * This is an accepted trade-off here, not an oversight: these controls are meant to be driven by
 * modulation, which resolves any parameter by its full path regardless of where in the component
 * tree it is registered, so it is unaffected by this gap. Do not try to work around it (e.g. by
 * promoting role parameters to top-level {@code addParameter} registrations purely so snapshots
 * see them) — the grouped, addChild-based presentation is intentional and matches the source
 * pattern's existing UI grouping.</p>
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
  private static final double MAX_HUE_OFFSET_DEGREES = 60;
  private static final double MAX_SATURATION_TRIM_PERCENT = 40;
  private static final String PRIMARY_PATH = "primary";
  private static final String PRIMARY_LABEL = "Primary";
  private static final String SECONDARY_PATH = "secondary";
  private static final String SECONDARY_LABEL = "Secondary";

  /**
   * One colour role (primary or secondary): a palette-stop selector plus hue/saturation offsets
   * plus the physics-coupling amount. See {@link ColorNativePattern} for the full vocabulary.
   *
   * <p>{@link #paletteIndex} cannot be an {@link heronarts.lx.color.LXPalette.IndexSelector} (see
   * class javadoc), so it cannot join that class's private static {@code selectors} registry that
   * gives every live {@code IndexSelector} its option labels refreshed whenever the project's
   * palette index names ({@code LXPalette.label1}..{@code label5}) change. This class reproduces
   * that behaviour independently: it listens on {@code lx.engine.palette.labels} directly (a
   * public field) and mirrors {@code LXPalette}'s own fallback — a custom name when one is set,
   * else the 1-based number as a string — onto {@link #paletteIndex} via
   * {@code setOptions(String[], false)}. The two label sets are computed the same way but are not
   * the same array instance, since {@code LXPalette}'s is private; this only matters if something
   * outside this class mutates that array in place rather than through {@code label1..label5},
   * which nothing in LX does.</p>
   */
  public static final class ColorRole extends LXComponent implements LXOscComponent {

    public final CompoundDiscreteParameter paletteIndex;
    public final CompoundParameter hueOffset;
    public final CompoundParameter satTrim;
    public final CompoundParameter amount;

    private int currentColor;
    private double currentAmount;
    private boolean colorEnabled;
    private final BooleanParameter colorToggle;

    private final LXParameterListener paletteLabelListener = p -> refreshPaletteIndexOptions();

    private ColorRole(
      LX lx, String label, int defaultPaletteIndex, double defaultAmount, BooleanParameter colorToggle
    ) {
      super(lx, label);
      this.colorToggle = colorToggle;

      this.paletteIndex =
        new CompoundDiscreteParameter("Index", defaultPaletteIndex, 1, LXSwatch.MAX_COLORS + 1)
        .setDescription("Project palette swatch stop this role reads (1-based)");

      this.hueOffset =
        new CompoundParameter("H-Off", 0, -MAX_HUE_OFFSET_DEGREES, MAX_HUE_OFFSET_DEGREES)
        .setUnits(CompoundParameter.Units.DEGREES)
        .setPolarity(LXParameter.Polarity.BIPOLAR)
        .setDescription("Hue offset applied to the resolved palette color");

      this.satTrim =
        new CompoundParameter("S-Trim", 0, 0, -MAX_SATURATION_TRIM_PERCENT)
        .setUnits(CompoundParameter.Units.PERCENT)
        .setDescription("Saturation trim below the resolved palette color; cannot raise saturation");

      this.amount = new CompoundParameter("Amount", defaultAmount)
        .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
        .setDescription("Coupling of the physics-driven perturbation to this role's color");

      addParameter("paletteIndex", this.paletteIndex);
      addParameter("hueOffset", this.hueOffset);
      addParameter("satTrim", this.satTrim);
      addParameter("amount", this.amount);

      refreshPaletteIndexOptions();
      for (StringParameter paletteLabel : lx.engine.palette.labels) {
        paletteLabel.addListener(this.paletteLabelListener);
      }
    }

    /**
     * Mirrors {@code LXPalette}'s own selector-option behaviour: a custom
     * {@code label1..label5} name when the performer has set one, otherwise the 1-based index as
     * a string. Called once at construction and again on every {@code label1..label5} change.
     */
    private void refreshPaletteIndexOptions() {
      final StringParameter[] paletteLabels = this.lx.engine.palette.labels;
      final String[] options = new String[paletteLabels.length];
      for (int i = 0; i < options.length; ++i) {
        final String customLabel = paletteLabels[i].getString();
        options[i] = customLabel.isEmpty() ? String.valueOf(i + 1) : customLabel;
      }
      // false: the range is already fixed to [1, MAX_COLORS] at construction; this only ever
      // replaces the label strings, matching options.length against that fixed range.
      this.paletteIndex.setOptions(options, false);
    }

    void update() {
      final int base = resolvedPaletteColor(this.paletteIndex.getValuei());
      this.currentColor = applyOffsets(base, this.hueOffset.getValue(), this.satTrim.getValue());
      this.currentAmount = this.amount.getValue();
      this.colorEnabled = this.colorToggle.isOn();
    }

    private int resolvedPaletteColor(int index) {
      return paletteColor(this.lx.engine.palette.swatch.colors, index - 1);
    }

    int color(double physics) {
      return this.colorEnabled
        ? modulatedColor(this.currentColor, this.currentAmount, physics)
        : modulatedColor(LXColor.WHITE, this.currentAmount, physics);
    }

    @Override
    public void dispose() {
      for (StringParameter paletteLabel : this.lx.engine.palette.labels) {
        paletteLabel.removeListener(this.paletteLabelListener);
      }
      super.dispose();
    }
  }

  /**
   * Read-only preview swatch of a role's <i>effective</i> resting colour — the resolved palette
   * stop after {@link #applyOffsets} (hue offset, saturation trim), matching what the pattern
   * actually renders with at rest. It deliberately excludes the physics-driven
   * {@link #modulatedColor} perturbation: that varies per pixel and per frame, so there is no
   * single right value to show for it.
   *
   * <p>Repaints on any change to the values it displays: {@code role.paletteIndex},
   * {@code role.hueOffset}, {@code role.satTrim}, and the live project palette itself — a
   * performer can edit the colour sitting at a fixed index out from under this preview (e.g.
   * from the Palette panel), and the chip needs to track that too, not just index changes.
   * {@code paletteIndex}/{@code hueOffset}/{@code satTrim} listeners are registered through
   * {@link UIObject#addListener(LXListenableParameter, LXParameterListener)}, which
   * {@code UIObject.dispose()} already tears down automatically; only the
   * {@link LXSwatch.Listener} needs an explicit {@link #dispose()} override.</p>
   */
  static final class PaletteColorPreview extends UI2dComponent {

    private final ColorRole role;
    private final LXSwatch swatch;

    private final LXSwatch.Listener swatchListener = new LXSwatch.Listener() {
      @Override
      public void colorAdded(LXSwatch swatch, LXDynamicColor color) {
        attachColorListeners(color);
        redraw();
      }

      @Override
      public void colorRemoved(LXSwatch swatch, LXDynamicColor color) {
        // The listeners attached below are torn down in dispose() along with everything else
        // addListener(...) tracked; a color removed from the swatch before this component is
        // disposed can cause at most one harmless extra redraw() in the meantime.
        redraw();
      }
    };

    PaletteColorPreview(LX lx, ColorRole role) {
      super(0, 0, 40, 18);
      this.role = role;
      setDescription(
        "Effective color this role renders with at rest: palette stop after hue offset and "
        + "saturation trim (excludes the per-pixel physics wobble)");

      addListener(role.paletteIndex, this.redraw);
      addListener(role.hueOffset, this.redraw);
      addListener(role.satTrim, this.redraw);

      this.swatch = lx.engine.palette.swatch;
      for (LXDynamicColor color : this.swatch.colors) {
        attachColorListeners(color);
      }
      this.swatch.addListener(this.swatchListener);
    }

    /** Repaints whenever any parameter that can move this color under a fixed index changes. */
    private void attachColorListeners(LXDynamicColor color) {
      for (LXParameter parameter : color.getParameters()) {
        if (parameter instanceof LXListenableParameter listenable) {
          addListener(listenable, this.redraw);
        }
      }
    }

    @Override
    protected void onDraw(heronarts.glx.ui.UI ui, VGraphics vg) {
      final int base = this.role.resolvedPaletteColor(this.role.paletteIndex.getValuei());
      final int effective =
        applyOffsets(base, this.role.hueOffset.getValue(), this.role.satTrim.getValue());
      vg.beginPath()
        .roundedRect(0, 0, this.width, this.height, 3)
        .fillColor(effective)
        .fill();
    }

    @Override
    public void dispose() {
      this.swatch.removeListener(this.swatchListener);
      super.dispose();
    }
  }

  /** The pattern's primary colour role, attached at the fixed {@code addChild} key "primary". */
  public final ColorRole primary;

  /** The pattern's secondary colour role, attached at the fixed {@code addChild} key "secondary". */
  public final ColorRole secondary;

  /** Enables palette colour; off resolves every role as a neutral tone at the same brightness. */
  public final BooleanParameter color = new BooleanParameter("Color", true)
    .setDescription("Enable palette color; off preserves brightness and physics in neutral tones");

  protected ColorNativePattern(
    LX lx,
    int primaryPaletteIndex,
    double primaryAmount,
    int secondaryPaletteIndex,
    double secondaryAmount
  ) {
    super(lx);
    addParameter("color", this.color);
    this.primary = colorRole(PRIMARY_PATH, PRIMARY_LABEL, primaryPaletteIndex, primaryAmount);
    this.secondary = colorRole(SECONDARY_PATH, SECONDARY_LABEL, secondaryPaletteIndex, secondaryAmount);
  }

  private ColorRole colorRole(String path, String label, int defaultPaletteIndex, double defaultAmount) {
    final ColorRole role = new ColorRole(
      this.lx, label, defaultPaletteIndex, defaultAmount, this.color);
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
   * colour columns, horizontally laid out. A subclass with its own controls overrides this
   * (parameter type {@code ColorNativePattern}, not the subclass — see class javadoc), adds its
   * own columns, and calls {@link #buildColorDeviceControls} last so the colour columns land at
   * the end of the panel.
   */
  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);
    buildColorDeviceControls(ui, uiDevice);
  }

  /**
   * Appends both roles' colour columns to {@code uiDevice}, contiguous with each other. Does
   * <b>not</b> call {@code uiDevice.setLayout(...)} — the caller must already have established
   * the device's layout (a subclass overriding {@link #buildDeviceControls} does this once,
   * before adding any columns, its own or these).
   *
   * <p>Each role needs 5 controls (a palette preview plus 4 parameters), one more than the
   * repository's 3-controls-per-column limit, so each role still spans two columns. The first is
   * headed "Primary"/"Secondary"; the second is deliberately left without its own header rather
   * than inventing a second per-role label like "Tint" — the two are adjacent with no other
   * column between them, which is the only grouping signal confirmed to render correctly (an
   * absent {@code addVerticalBreak} between them was tried and rejected: the rendered panel drew
   * an identical divider there regardless, so it did not actually signal a tighter pairing).</p>
   */
  protected final void buildColorDeviceControls(UI ui, UIDevice uiDevice) {
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Color",
      newButton(this.color)
    ).setChildSpacing(6);
    addRoleColumns(ui, uiDevice, this.primary, PRIMARY_LABEL);
    addRoleColumns(ui, uiDevice, this.secondary, SECONDARY_LABEL);
  }

  // Default COL_WIDTH (UIControls.COL_WIDTH, 52px) clips "Secondary" to "Secondar" -- confirmed
  // by an actual render, not assumed. This widens just the header-bearing column enough for the
  // longer of the two role labels to render in full.
  private static final float ROLE_COLUMN_WIDTH = 68;

  private void addRoleColumns(UI ui, UIDevice uiDevice, ColorRole role, String label) {
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, ROLE_COLUMN_WIDTH, label,
      new PaletteColorPreview(this.lx, role),
      newKnob(role.paletteIndex),
      newKnob(role.amount)
    ).setChildSpacing(6);
    addColumn(uiDevice,
      newKnob(role.hueOffset),
      newKnob(role.satTrim)
    ).setChildSpacing(6);
  }

  static int paletteColor(List<LXDynamicColor> colors, int stop) {
    if (colors.isEmpty()) {
      return LXColor.WHITE;
    }
    return colors.get(Math.min(Math.max(stop, 0), colors.size() - 1)).getColor();
  }

  /**
   * Applies a hue offset (degrees) and a saturation trim (percent) to a resolved palette color.
   * This function clamps saturation to [0, 100] but does not itself forbid a positive
   * {@code satTrimPercent} — the "cannot raise saturation" guarantee comes from
   * {@link ColorRole#satTrim}'s parameter range being bounded to [-40, 0], not from this
   * function refusing a positive input. Exits early, returning {@code color} unchanged, when
   * both offsets are exactly zero — the default — so default output is bit-identical to the
   * unmodified palette color rather than a numerically-equal-but-reconstructed one.
   */
  static int applyOffsets(int color, double hueOffsetDegrees, double satTrimPercent) {
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
