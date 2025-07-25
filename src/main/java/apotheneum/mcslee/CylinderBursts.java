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

package apotheneum.mcslee;

import apotheneum.Apotheneum;
import heronarts.glx.ui.component.UIDropMenu;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.LXComponentName;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;

@LXCategory("Apotheneum/mcslee")
@LXComponentName("Cylinder Bursts")
@LXComponent.Description("MIDI reactive emanations on the cylinder")
public class CylinderBursts extends Bursts implements UIDeviceControls<CylinderBursts> {

  public CylinderBursts(LX lx) {
    super(lx);
  }

  @Override
  protected boolean canBurstsWrap() {
    return true;
  }

  @Override
  protected void generateBursts(int num) {
    for (int i = 0; i < num; ++i) {
      addBurst(new Burst(Apotheneum.cylinder.exterior));
    }
  }

  @Override
  protected void afterRender() {
    copyCylinderExterior();
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, CylinderBursts cubeBursts) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);

    addColumn(uiDevice,
      newButton(cubeBursts.burst).setTriggerable(true).setBorderRounding(4),
      newKnob(cubeBursts.perTrig),
      newKnob(cubeBursts.burstSpread)
    ).setChildSpacing(4);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, UIKnob.WIDTH,
      "Rand",
      newKnob(cubeBursts.spinRandom, 0),
      newKnob(cubeBursts.shapeRandom, 0)
    ).setChildSpacing(4);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Form",
      newDropMenu(cubeBursts.shape2),
      newVerticalSlider(cubeBursts.shapeLerp, 100).setShowLabel(false),
      newDropMenu(cubeBursts.shape1).setDirection(UIDropMenu.Direction.UP)
    ).setChildSpacing(4);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, UIKnob.WIDTH,
      "Shape",
      newKnob(cubeBursts.burstRadius, 0),
      newKnob(cubeBursts.burstThickness, 0),
      newKnob(cubeBursts.spin, 0)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, UIKnob.WIDTH,
      "Time",
      newKnob(cubeBursts.burstTime, 0),
      newKnob(cubeBursts.burstAttack, 0),
      newKnob(cubeBursts.burstExp, 0)
    ).setChildSpacing(6);
  }

}
