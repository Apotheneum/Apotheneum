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
import apotheneum.ApotheneumPattern;
import heronarts.glx.ui.component.UIDropMenu;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Cube Bursts")
@LXComponent.Description("MIDI reactive emanations on the cube faces")
public class CubeBursts extends Bursts implements ApotheneumPattern.Midi, UIDeviceControls<CubeBursts> {

  public final BooleanParameter allFaces =
    new BooleanParameter("All Faces", false)
    .setDescription("Burst on all faces at once");

  public final BooleanParameter allFacesSymmetry =
    new BooleanParameter("Symmetry", false)
    .setDescription("Burst on all faces are identical");

  public CubeBursts(LX lx) {
    super(lx);
    addParameter("allFaces", this.allFaces);
    addParameter("allFacesSymmetry", this.allFacesSymmetry);
  }

  @Override
  protected boolean canBurstsWrap() {
    return false;
  }

  @Override
  protected void generateBursts(int num) {
    final boolean allFaces = this.allFaces.isOn();
    final boolean allFacesSymmetry = this.allFacesSymmetry.isOn();
    for (int i = 0; i < num; ++i) {
      if (allFaces) {
        Burst b = null;
        for (Apotheneum.Cube.Face face : Apotheneum.cube.exterior.faces) {
          if (allFacesSymmetry && b != null) {
            addBurst(new Burst(face, b));
          } else {
            addBurst(b = new Burst(face));
          }
        }
      } else {
        addBurst(new Burst(Apotheneum.cube.exterior.faces[LXUtils.randomi(0, 3)]));
      }
    }
  }

  @Override
  protected void afterRender() {
    copyCubeExterior();
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, CubeBursts cubeBursts) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);

    addColumn(uiDevice,
      newButton(cubeBursts.burst).setTriggerable(true).setBorderRounding(4),
      newKnob(cubeBursts.perTrig),
      newKnob(cubeBursts.burstSpread),
      newButton(cubeBursts.allFaces).setTopMargin(12),
      newButton(cubeBursts.allFacesSymmetry)
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
