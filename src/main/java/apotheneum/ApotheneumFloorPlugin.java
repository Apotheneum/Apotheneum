/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
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
