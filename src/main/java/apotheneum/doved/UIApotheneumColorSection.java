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

package apotheneum.doved;

import heronarts.glx.ui.UI;
import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UICollapsibleSection;
import heronarts.glx.ui.component.UIDropMenu;
import heronarts.glx.ui.component.UILabel;
import heronarts.glx.ui.vg.VGraphics;
import heronarts.lx.LX;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;

import apotheneum.doved.modulators.ApotheneumColor;
import apotheneum.doved.modulators.ApotheneumColor.Surface;

/**
 * Left-pane GLOBAL-tab section for {@link ApotheneumColor}: the shared {@code pair}/{@code swap}
 * gesture, {@code axis} (which surfaces share a stop), and a real-colour swatch per surface so
 * the owner can see what they currently produce without turning the piece on.
 *
 * <p>Modelled directly on {@code apotheneum.video.UIVideoWallPanel} — the other
 * package-contributed GLOBAL section in this codebase, and the reason this class exists as a
 * {@link UICollapsibleSection} with stacked full-width controls rather than the knob columns a
 * device panel would use.
 *
 * <p><b>2026-08-30: shrunk from six groups to two.</b> The four per-surface {@code indexOffset}/
 * {@code hueOffset}/{@code satTrim} groups are gone along with the parameters they displayed —
 * see {@link ApotheneumColor}'s class javadoc for why. This section is now SHARED ({@code pair},
 * {@code swap}, {@code axis}) and COLORS (the four resolved swatches) only.
 *
 * <ul>
 *   <li><b>The pane is 208px wide, full stop</b> — {@code heronarts.lx.studio.ui.UILeftPane.WIDTH},
 *       confirmed by decompiling the class, not assumed from a screenshot. There is no
 *       side-by-side room for anything here; every control in this section is full width,
 *       stacked vertically, exactly like Video Wall's Source/Layout/Processor/Gap rows.</li>
 *   <li><b>The section can be as tall as it needs</b> — unlike a {@code UIDeviceModulator}'s
 *       hard-fixed 160px content cap, the left pane scrolls.</li>
 * </ul>
 */
public class UIApotheneumColorSection extends UICollapsibleSection {

  // Same stacked-caption-then-control shape and the same constants Video Wall uses, so this
  // reads as a native sibling rather than a differently-proportioned bolt-on.
  private static final float CAPTION_HEIGHT = 12;
  private static final float CAPTION_SPACING = 2;
  private static final float ROW_HEIGHT = CAPTION_HEIGHT + CAPTION_SPACING + CONTROL_HEIGHT;
  private static final float GROUP_HEADING_HEIGHT = 12;
  private static final float GROUP_ROW_SPACING = 4;
  private static final float GROUP_SPACING = 6;
  private static final float SWATCH_HEIGHT = 18;
  private static final float SWATCH_ROW_HEIGHT = CAPTION_HEIGHT + CAPTION_SPACING + SWATCH_HEIGHT;

  private static final float SHARED_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + 3 * ROW_HEIGHT + GROUP_ROW_SPACING;
  private static final float COLORS_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + 4 * SWATCH_ROW_HEIGHT + 3 * GROUP_ROW_SPACING;
  private static final float CONTENT_HEIGHT =
    SHARED_GROUP_HEIGHT + GROUP_SPACING
    + COLORS_GROUP_HEIGHT;
  private static final float SECTION_HEIGHT = CONTENT_HEIGHT + PADDING + BAR_HEIGHT;

  public UIApotheneumColorSection(UI ui, ApotheneumColor config, float width) {
    super(ui, 0, 0, width, SECTION_HEIGHT);
    setTitle("APOTHENEUM COLOR");
    setLayout(UI2dContainer.Layout.VERTICAL, GROUP_SPACING);

    final float contentWidth = getContentWidth();

    final UI2dContainer sharedGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "SHARED"),
      stackedRow(contentWidth, "Pair", new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, config.pair)),
      stackedRow(contentWidth, "Swap", new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, config.swap)),
      stackedRow(contentWidth, "Axis", new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, config.axis))
    );

    final UI2dContainer colorsGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "COLORS"),
      swatchRow(config, Surface.CUBE_EXTERIOR, "Cube Ext", contentWidth),
      swatchRow(config, Surface.CUBE_INTERIOR, "Cube Int", contentWidth),
      swatchRow(config, Surface.CYLINDER_EXTERIOR, "Cyl Ext", contentWidth),
      swatchRow(config, Surface.CYLINDER_INTERIOR, "Cyl Int", contentWidth)
    );

    addChildren(sharedGroup, colorsGroup);
  }

  private UI2dContainer swatchRow(
    ApotheneumColor config, Surface surface, String label, float contentWidth
  ) {
    final SwatchPair swatch =
      new SwatchPair(config.getLX(), config, surface, contentWidth, SWATCH_HEIGHT);
    return stackedRow(contentWidth, label, swatch);
  }

  /** A caption label above a full-width control, stacked with {@link #CAPTION_SPACING} --
   * identical shape to {@code UIVideoWallPanel.stackedRow}. */
  static UI2dContainer stackedRow(float contentWidth, String label, UI2dComponent control) {
    final UILabel caption = new UILabel(0, 0, contentWidth, CAPTION_HEIGHT).setLabel(label);
    return UI2dContainer.newVerticalContainer(contentWidth, CAPTION_SPACING, caption, control);
  }

  static UILabel groupHeading(float contentWidth, String label) {
    return new UILabel(0, 0, contentWidth, GROUP_HEADING_HEIGHT).setLabel(label);
  }

  /**
   * Left half primary, right half secondary, for one surface — "what does the current
   * pair/swap/axis actually produce", without turning the piece on.
   *
   * <p>Redraws on every parameter that can move either half. That is {@code pair}, {@code swap}
   * and {@code axis} — every swatch listens to all three, since each one moves every surface's
   * resolved color at once — <b>and the palette stops themselves</b>. The stop listeners are not
   * optional decoration: {@link ApotheneumColor} resolves nothing but real palette stops, so a
   * performer dragging a stop's hue changes what every one of these swatches should be showing
   * without touching any of the three parameters above. Listening to the three alone left the
   * swatches displaying cached pre-edit colors while the patterns had already moved to the
   * edited ones — a preview that disagrees with the piece is worse than no preview, because it
   * is trusted. This restores the {@code LXSwatch.Listener} + per-{@link LXDynamicColor}
   * parameter-listener pair the {@code PaletteColorPreview} this class replaced already had.
   *
   * <p>{@code lx.engine.palette.swatch} is a {@code final} field on {@code LXPalette} — recalling
   * a saved swatch copies into it rather than swapping the object — so binding to it once in the
   * constructor stays correct for the life of this component.
   *
   * <p>One case is deliberately not covered: an {@link LXDynamicColor} in an animating mode
   * (cycle/oscillate) changes color every frame with no parameter change to listen for, so its
   * swatch here updates only when something else triggers a redraw. Following that would mean
   * repainting this section every frame for a 208px-wide preview; the parameter-driven cases
   * above are the ones a performer actually edits.
   */
  private static final class SwatchPair extends UI2dComponent {

    private final ApotheneumColor config;
    private final Surface surface;
    private final LXSwatch swatch;

    private final LXSwatch.Listener swatchListener = new LXSwatch.Listener() {
      @Override
      public void colorAdded(LXSwatch swatch, LXDynamicColor color) {
        attachColorListeners(color);
        redraw();
      }

      @Override
      public void colorRemoved(LXSwatch swatch, LXDynamicColor color) {
        // Listeners attached to the removed color are torn down in dispose() along with
        // everything else addListener(...) tracked; until then a removed color can cause at
        // most one harmless extra redraw. Note a removal also changes what every *remaining*
        // swatch resolves, since ApotheneumColor.wrapIndex wraps around the live stop count --
        // so this redraw is required, not merely tidy.
        redraw();
      }
    };

    private SwatchPair(LX lx, ApotheneumColor config, Surface surface, float width, float height) {
      super(0, 0, width, height);
      this.config = config;
      this.surface = surface;
      setDescription("Resolved primary (left) and secondary (right) color for " + surface);

      addListener(config.pair, this.redraw);
      addListener(config.swap, this.redraw);
      addListener(config.axis, this.redraw);

      this.swatch = lx.engine.palette.swatch;
      for (LXDynamicColor color : this.swatch.colors) {
        attachColorListeners(color);
      }
      this.swatch.addListener(this.swatchListener);
    }

    /** Repaints whenever any parameter that can move this stop's color changes. */
    private void attachColorListeners(LXDynamicColor color) {
      for (LXParameter parameter : color.getParameters()) {
        if (parameter instanceof LXListenableParameter listenable) {
          addListener(listenable, this.redraw);
        }
      }
    }

    @Override
    public void dispose() {
      this.swatch.removeListener(this.swatchListener);
      super.dispose();
    }

    @Override
    protected void onDraw(UI ui, VGraphics vg) {
      final int primary = this.config.primaryColor(this.surface);
      final int secondary = this.config.secondaryColor(this.surface);
      final float half = this.width / 2f;
      vg.beginPath().rect(0, 0, half, this.height).fillColor(primary).fill();
      vg.beginPath().rect(half, 0, this.width - half, this.height).fillColor(secondary).fill();
    }
  }

}
