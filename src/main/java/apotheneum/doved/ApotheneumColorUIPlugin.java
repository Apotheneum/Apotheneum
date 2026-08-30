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

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPlugin;
import heronarts.lx.studio.LXStudio;

import apotheneum.doved.modulators.ApotheneumColor;

/**
 * Studio-only companion to {@link ApotheneumColorPlugin}: a left-pane GLOBAL-tab section
 * showing and driving the engine-registered {@link ApotheneumColor}. LX loads plugins
 * independently with no ordering guarantee, so this never holds a reference to the core
 * plugin instance — it resolves {@link ApotheneumColor} from the engine by path instead
 * ({@link ApotheneumColor#PATH}), and does nothing if the core plugin isn't enabled. Mirrors
 * {@code apotheneum.video.ApotheneumVideoUIPlugin}'s split from
 * {@code apotheneum.video.ApotheneumVideoPlugin} exactly.
 */
@LXPlugin.Name("Apotheneum Color UI")
public class ApotheneumColorUIPlugin implements LXStudio.Plugin {

  private UIApotheneumColorSection section = null;

  @Override
  public void initialize(LX lx) {}

  @Override
  public void initializeUI(LXStudio lx, LXStudio.UI ui) {}

  @Override
  public void onUIReady(LXStudio lx, LXStudio.UI ui) {
    final LXComponent child = lx.engine.getChild(ApotheneumColor.PATH);
    if (!(child instanceof ApotheneumColor)) {
      ApotheneumColorPlugin.log("core color plugin not enabled; skipping Apotheneum Color panel");
      return;
    }
    final ApotheneumColor config = (ApotheneumColor) child;
    this.section = new UIApotheneumColorSection(ui, config, ui.leftPane.global.getContentWidth());
    ui.leftPane.global.addChildren(this.section);
  }

  @Override
  public void dispose() {
    if (this.section != null) {
      this.section.removeFromContainer();
      this.section.dispose();
      this.section = null;
    }
  }

}
