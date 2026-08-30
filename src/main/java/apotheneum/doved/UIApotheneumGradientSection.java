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
import heronarts.glx.ui.component.UILabel;

import apotheneum.doved.modulators.ApotheneumGradient;

/**
 * Left-pane GLOBAL-tab section for {@link ApotheneumGradient}: the one shared 3D direction every
 * {@code GradientMultiplyEffect} instance reads, as two knobs (azimuth, elevation) rather than
 * the four independent per-surface angles the old 2D-raster design needed — see {@link
 * ApotheneumGradient}'s class javadoc for why one direction through 3D space replaces those four.
 *
 * <p>Modelled directly on {@link UIApotheneumColorSection} — same 208px-wide, full-width,
 * stacked-control shape ({@code heronarts.lx.studio.ui.UILeftPane.WIDTH}, see that class's
 * javadoc for the confirmation this section reuses rather than re-deriving), same {@link
 * UICollapsibleSection} base, added alongside it in the same GLOBAL tab by {@link
 * ApotheneumColorUIPlugin}.
 */
public class UIApotheneumGradientSection extends UICollapsibleSection {

  private static final float CAPTION_HEIGHT = 12;
  private static final float CAPTION_SPACING = 2;
  private static final float ROW_HEIGHT = CAPTION_HEIGHT + CAPTION_SPACING + CONTROL_HEIGHT;
  private static final float GROUP_HEADING_HEIGHT = 12;
  private static final float GROUP_ROW_SPACING = 4;

  private static final float SHARED_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + 2 * ROW_HEIGHT + GROUP_ROW_SPACING;
  private static final float CONTENT_HEIGHT = SHARED_GROUP_HEIGHT;
  private static final float SECTION_HEIGHT = CONTENT_HEIGHT + PADDING + BAR_HEIGHT;

  public UIApotheneumGradientSection(UI ui, ApotheneumGradient gradient, float width) {
    super(ui, 0, 0, width, SECTION_HEIGHT);
    setTitle("APOTHENEUM GRADIENT");
    setLayout(UI2dContainer.Layout.VERTICAL, GROUP_ROW_SPACING);

    final float contentWidth = getContentWidth();

    final UI2dContainer sharedGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "DIRECTION"),
      stackedRow(contentWidth, "Azimuth",
        new UIDoubleBox(0, 0, contentWidth, CONTROL_HEIGHT, gradient.azimuth)),
      stackedRow(contentWidth, "Elevation",
        new UIDoubleBox(0, 0, contentWidth, CONTROL_HEIGHT, gradient.elevation))
    );

    addChildren(sharedGroup);
  }

  /** A caption label above a full-width control, stacked with {@link #CAPTION_SPACING} --
   * identical shape to {@code UIApotheneumColorSection.stackedRow}. */
  private static UI2dContainer stackedRow(float contentWidth, String label, UI2dComponent control) {
    final UILabel caption = new UILabel(0, 0, contentWidth, CAPTION_HEIGHT).setLabel(label);
    return UI2dContainer.newVerticalContainer(contentWidth, CAPTION_SPACING, caption, control);
  }

  private static UILabel groupHeading(float contentWidth, String label) {
    return new UILabel(0, 0, contentWidth, GROUP_HEADING_HEIGHT).setLabel(label);
  }

}
