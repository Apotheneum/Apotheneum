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
import apotheneum.doved.modulators.ApotheneumGradient;

/**
 * Studio-only companion to {@link ApotheneumColorPlugin}: left-pane GLOBAL-tab sections showing
 * and driving the engine-registered {@link ApotheneumColor} and {@link ApotheneumGradient}. LX
 * loads plugins independently with no ordering guarantee, so this never holds a reference to the
 * core plugin instance — it resolves both components from the engine by path instead ({@link
 * ApotheneumColor#PATH}, {@link ApotheneumGradient#PATH}), and skips whichever one the core
 * plugin didn't register (each section is independent, so one missing component does not hide
 * the other's panel). Mirrors {@code apotheneum.video.ApotheneumVideoUIPlugin}'s split from
 * {@code apotheneum.video.ApotheneumVideoPlugin} exactly.
 *
 * <p>Colour and gradient get two separate {@code UICollapsibleSection}s rather than one merged
 * section — {@link UIApotheneumColorSection} stays exactly about {@link ApotheneumColor} (the
 * shared pair/swap gesture and the four surfaces' standing offsets), {@link
 * UIApotheneumGradientSection} is exactly about {@link ApotheneumGradient} (the shared 3D
 * direction) — the same way Video Wall and this Color section already coexist as independent
 * sections in the same GLOBAL tab rather than one panel trying to be both.
 */
@LXPlugin.Name("Apotheneum Color UI")
public class ApotheneumColorUIPlugin implements LXStudio.Plugin {

  private UIApotheneumColorSection colorSection = null;
  private UIApotheneumGradientSection gradientSection = null;

  @Override
  public void initialize(LX lx) {}

  @Override
  public void initializeUI(LXStudio lx, LXStudio.UI ui) {}

  @Override
  public void onUIReady(LXStudio lx, LXStudio.UI ui) {
    final LXComponent colorChild = lx.engine.getChild(ApotheneumColor.PATH);
    if (colorChild instanceof ApotheneumColor config) {
      this.colorSection = new UIApotheneumColorSection(ui, config, ui.leftPane.global.getContentWidth());
      ui.leftPane.global.addChildren(this.colorSection);
    } else {
      ApotheneumColorPlugin.log("core color plugin not enabled; skipping Apotheneum Color panel");
    }

    final LXComponent gradientChild = lx.engine.getChild(ApotheneumGradient.PATH);
    if (gradientChild instanceof ApotheneumGradient gradient) {
      this.gradientSection =
        new UIApotheneumGradientSection(ui, gradient, ui.leftPane.global.getContentWidth());
      ui.leftPane.global.addChildren(this.gradientSection);
    } else {
      ApotheneumColorPlugin.log("core color plugin not enabled; skipping Apotheneum Gradient panel");
    }
  }

  @Override
  public void dispose() {
    if (this.colorSection != null) {
      this.colorSection.removeFromContainer();
      this.colorSection.dispose();
      this.colorSection = null;
    }
    if (this.gradientSection != null) {
      this.gradientSection.removeFromContainer();
      this.gradientSection.dispose();
      this.gradientSection = null;
    }
  }

}
