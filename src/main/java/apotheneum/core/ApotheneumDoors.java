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

package apotheneum.core;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumEffect;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.parameter.BooleanParameter;

@LXCategory("Apotheneum/core")
@LXComponentName("Doors")
@LXComponent.Description("Masks out any colors on the virtual pixels where there are Apotheneum doorways")
public class ApotheneumDoors extends ApotheneumEffect {

  private static final int DOOR_WIDTH = 10;

  private static final int FACE_DOOR_START_COLUMN = 20;
  private static final int FACE_DOOR_START_PIXEL = 34;

  private static final int[] CYLINDER_DOOR_START_COLUMNS = { 10, 40, 70, 100 };
  private static final int CYLINDER_DOOR_START_PIXEL = 32;

  public final BooleanParameter mute =
    new BooleanParameter("Mute Doors", true)
    .setDescription("Mutes the doors");

  public ApotheneumDoors(LX lx) {
    super(lx);
    addParameter("mute", this.mute);
  }

  @Override
  protected void render(double deltaMs, double enabledAmount) {
    if (!this.mute.isOn()) {
      return;
    }

    for (int c = 0; c < DOOR_WIDTH; ++c) {

      // Mute cube face doors
      for (Apotheneum.Cube.Face face : Apotheneum.cube.faces) {
        final LXModel column = face.columns[FACE_DOOR_START_COLUMN + c];
        for (int i = FACE_DOOR_START_PIXEL; i < column.points.length; ++i) {
          colors[column.points[i].index] = LXColor.BLACK;
        }
      }

      // Mute cylinder doors
      for (int cylinderColumn : CYLINDER_DOOR_START_COLUMNS) {
        final LXModel exterior = Apotheneum.cylinder.exterior.columns[cylinderColumn + c];
        for (int i = CYLINDER_DOOR_START_PIXEL; i < exterior.points.length; ++i) {
          colors[exterior.points[i].index] = LXColor.BLACK;
        }
        if (Apotheneum.cylinder.interior != null) {
          final LXModel interior = Apotheneum.cylinder.interior.columns[cylinderColumn + c];
          for (int i = CYLINDER_DOOR_START_PIXEL; i < exterior.points.length; ++i) {
            colors[interior.points[i].index] = LXColor.BLACK;
          }
        }
      }
    }

  }

}
