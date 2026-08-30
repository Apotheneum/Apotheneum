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
import heronarts.glx.ui.component.UIDoubleBox;
import heronarts.glx.ui.component.UIDropMenu;
import heronarts.glx.ui.component.UIIntegerBox;
import heronarts.glx.ui.component.UILabel;
import heronarts.glx.ui.vg.VGraphics;

import apotheneum.doved.modulators.ApotheneumColor;
import apotheneum.doved.modulators.ApotheneumColor.Surface;
import apotheneum.doved.modulators.ApotheneumColor.SurfaceOffset;

/**
 * Left-pane GLOBAL-tab section for {@link ApotheneumColor}: the shared {@code pair}/{@code swap}
 * gesture, a real-colour swatch per surface so the owner can see what they currently produce
 * without turning the piece on, then one subsection per surface for its standing
 * {@code indexOffset}/{@code hueOffset}/{@code satTrim}.
 *
 * <p>Modelled directly on {@code apotheneum.video.UIVideoWallPanel} — the other
 * package-contributed GLOBAL section in this codebase, and the reason this class exists as a
 * {@link UICollapsibleSection} with stacked full-width controls rather than the knob columns a
 * device panel would use. Two things carry over verbatim rather than being rederived:
 *
 * <ul>
 *   <li><b>The pane is 208px wide, full stop</b> — {@code heronarts.lx.studio.ui.UILeftPane.WIDTH},
 *       confirmed by decompiling the class, not assumed from a screenshot. There is no
 *       side-by-side room for anything here; every control in this section is full width,
 *       stacked vertically, exactly like Video Wall's Source/Layout/Processor/Gap rows.</li>
 *   <li><b>The section can be as tall as it needs</b> — unlike a {@code UIDeviceModulator}'s
 *       hard-fixed 160px content cap (the constraint that pushed three of four surface groups
 *       off-panel the first time this class had knob columns), the left pane scrolls, and Video
 *       Wall itself runs to several hundred pixels across four subsections. This section is six
 *       groups tall (SHARED, COLORS, and one per surface) for exactly that reason — nothing here
 *       needed hiding to fit.</li>
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
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + 2 * ROW_HEIGHT + GROUP_ROW_SPACING;
  private static final float COLORS_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + 4 * SWATCH_ROW_HEIGHT + 3 * GROUP_ROW_SPACING;
  private static final float SURFACE_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + 3 * ROW_HEIGHT + 2 * GROUP_ROW_SPACING;
  private static final float CONTENT_HEIGHT =
    SHARED_GROUP_HEIGHT + GROUP_SPACING
    + COLORS_GROUP_HEIGHT + GROUP_SPACING
    + 4 * SURFACE_GROUP_HEIGHT + 3 * GROUP_SPACING;
  private static final float SECTION_HEIGHT = CONTENT_HEIGHT + PADDING + BAR_HEIGHT;

  public UIApotheneumColorSection(UI ui, ApotheneumColor config, float width) {
    super(ui, 0, 0, width, SECTION_HEIGHT);
    setTitle("APOTHENEUM COLOR");
    setLayout(UI2dContainer.Layout.VERTICAL, GROUP_SPACING);

    final float contentWidth = getContentWidth();

    final UI2dContainer sharedGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "SHARED"),
      stackedRow(contentWidth, "Pair", new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, config.pair)),
      stackedRow(contentWidth, "Swap", new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, config.swap))
    );

    final UI2dContainer colorsGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "COLORS"),
      swatchRow(config, Surface.CUBE_EXTERIOR, "Cube Ext", contentWidth),
      swatchRow(config, Surface.CUBE_INTERIOR, "Cube Int", contentWidth),
      swatchRow(config, Surface.CYLINDER_EXTERIOR, "Cyl Ext", contentWidth),
      swatchRow(config, Surface.CYLINDER_INTERIOR, "Cyl Int", contentWidth)
    );

    addChildren(
      sharedGroup,
      colorsGroup,
      surfaceGroup(contentWidth, "CUBE EXT", config.cubeExterior),
      surfaceGroup(contentWidth, "CUBE INT", config.cubeInterior),
      surfaceGroup(contentWidth, "CYL EXT", config.cylinderExterior),
      surfaceGroup(contentWidth, "CYL INT", config.cylinderInterior)
    );
  }

  private UI2dContainer surfaceGroup(float contentWidth, String title, SurfaceOffset offset) {
    return UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, title),
      stackedRow(contentWidth, "Index Offset",
        new UIIntegerBox(0, 0, contentWidth, CONTROL_HEIGHT, offset.indexOffset)),
      stackedRow(contentWidth, "Hue Offset",
        new UIDoubleBox(0, 0, contentWidth, CONTROL_HEIGHT, offset.hueOffset)),
      stackedRow(contentWidth, "Sat Trim",
        new UIDoubleBox(0, 0, contentWidth, CONTROL_HEIGHT, offset.satTrim))
    );
  }

  private UI2dContainer swatchRow(
    ApotheneumColor config, Surface surface, String label, float contentWidth
  ) {
    final SwatchPair swatch = new SwatchPair(config, surface, contentWidth, SWATCH_HEIGHT);
    return stackedRow(contentWidth, label, swatch);
  }

  /** A caption label above a full-width control, stacked with {@link #CAPTION_SPACING} --
   * identical shape to {@code UIVideoWallPanel.stackedRow}. */
  private static UI2dContainer stackedRow(float contentWidth, String label, UI2dComponent control) {
    final UILabel caption = new UILabel(0, 0, contentWidth, CAPTION_HEIGHT).setLabel(label);
    return UI2dContainer.newVerticalContainer(contentWidth, CAPTION_SPACING, caption, control);
  }

  private static UILabel groupHeading(float contentWidth, String label) {
    return new UILabel(0, 0, contentWidth, GROUP_HEADING_HEIGHT).setLabel(label);
  }

  /**
   * Left half primary, right half secondary, for one surface — "what does the current
   * pair/swap plus this surface's own offsets actually produce", without turning the piece on.
   * Redraws on every parameter that can move either half: the two shared gesture parameters
   * (every swatch listens to both, since {@code pair}/{@code swap} move every surface at once)
   * plus this surface's own three offsets.
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
      final SurfaceOffset offset = offsetFor(config, surface);
      addListener(offset.indexOffset, this.redraw);
      addListener(offset.hueOffset, this.redraw);
      addListener(offset.satTrim, this.redraw);
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

  private static SurfaceOffset offsetFor(ApotheneumColor config, Surface surface) {
    switch (surface) {
      case CUBE_EXTERIOR: return config.cubeExterior;
      case CUBE_INTERIOR: return config.cubeInterior;
      case CYLINDER_EXTERIOR: return config.cylinderExterior;
      case CYLINDER_INTERIOR: return config.cylinderInterior;
      default: throw new IllegalArgumentException("Unknown surface: " + surface);
    }
  }

}
