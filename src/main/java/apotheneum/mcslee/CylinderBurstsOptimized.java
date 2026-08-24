/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
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
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Cylinder Bursts-Optimized")
@LXComponent.Description("MIDI reactive emanations on the cylinder (performance-optimized)")
public class CylinderBurstsOptimized extends BurstsOptimized implements UIDeviceControls<CylinderBurstsOptimized> {

  public CylinderBurstsOptimized(LX lx) {
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
  public void buildDeviceControls(UI ui, UIDevice uiDevice, CylinderBurstsOptimized cubeBursts) {
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
