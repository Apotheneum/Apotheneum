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
import apotheneum.ApotheneumEffect;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/mcslee")
@LXComponentName("Face Mask")
@LXComponent.Description("Mask the levels of cube faces")
public class FaceMask extends ApotheneumEffect {

  public final CompoundParameter front =
    new CompoundParameter("Front", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Mask for front");

  public final CompoundParameter right =
    new CompoundParameter("Right", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Mask for right");

  public final CompoundParameter back =
    new CompoundParameter("Back", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Mask for back");

  public final CompoundParameter left =
    new CompoundParameter("Left", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Mask for left");

  public FaceMask(LX lx) {
    super(lx);
    addParameter("front", this.front);
    addParameter("right", this.right);
    addParameter("back", this.back);
    addParameter("left", this.left);
  }

  @Override
  protected void render(double deltaMs, double enabledAmount) {
    final double front = this.front.getValue() * enabledAmount;
    final double right = this.right.getValue() * enabledAmount;
    final double back = this.back.getValue() * enabledAmount;
    final double left = this.left.getValue() * enabledAmount;
    mask(Apotheneum.cube.exterior.front, front);
    mask(Apotheneum.cube.interior.front, front);
    mask(Apotheneum.cube.exterior.right, right);
    mask(Apotheneum.cube.interior.right, right);
    mask(Apotheneum.cube.exterior.back, back);
    mask(Apotheneum.cube.interior.back, back);
    mask(Apotheneum.cube.exterior.left, left);
    mask(Apotheneum.cube.interior.left, left);
  }

  private void mask(Apotheneum.Cube.Face face, double mask) {
    if (mask <= 0) {
      setColor(face, LXColor.BLACK);
    } else if (mask < 1) {
      final int alphaMask = LXColor.blendMask(1 - mask);
      for (LXPoint p : face.model.points) {
        colors[p.index] = LXColor.multiply(colors[p.index], LXColor.BLACK, alphaMask);
      }
    }
  }

}
