/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
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
 * @author Mark C. Slee <mark@heronarts.com>
 */

package apotheneum;

import apotheneum.ui.UIApotheneumFloorLights;
import heronarts.lx.LX;
import heronarts.lx.LXPlugin;
import heronarts.lx.studio.LXStudio;
import heronarts.lx.studio.LXStudio.UI;

@LXPlugin.Name("Apotheneum Floor Lights")
public class ApotheneumFloorPlugin implements LXStudio.Plugin {

  @Override
  public void initialize(LX lx) {
  }

  @Override
  public void initializeUI(LXStudio lx, UI ui) {
  }

  @Override
  public void onUIReady(LXStudio lx, UI ui) {
    ui.preview.addComponent(new UIApotheneumFloorLights(ui, false));
    ui.previewAux.addComponent(new UIApotheneumFloorLights(ui, true));
  }

}
