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
    final SwatchPair swatch = new SwatchPair(config, surface, contentWidth, SWATCH_HEIGHT);
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
   * pair/swap/axis actually produce", without turning the piece on. Redraws on every parameter
   * that can move either half: {@code pair}, {@code swap}, and {@code axis} (every swatch
   * listens to all three, since each one moves every surface's resolved color at once).
   */
  private static final class SwatchPair extends UI2dComponent {

    private final ApotheneumColor config;
    private final Surface surface;

    private SwatchPair(ApotheneumColor config, Surface surface, float width, float height) {
      super(0, 0, width, height);
      this.config = config;
      this.surface = surface;
      setDescription("Resolved primary (left) and secondary (right) color for " + surface);

      addListener(config.pair, this.redraw);
      addListener(config.swap, this.redraw);
      addListener(config.axis, this.redraw);
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
