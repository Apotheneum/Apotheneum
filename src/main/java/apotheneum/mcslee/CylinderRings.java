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
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Cylinder Rings")
public class CylinderRings extends ApotheneumPattern {

  public final CompoundParameter pos =
    new CompoundParameter("Pos", 0, 360)
    .setWrappable(true)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Base ring position");

  public final CompoundParameter skew =
    new CompoundParameter("Skew", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Skew of the ring position");

  public CylinderRings(LX lx) {
    super(lx);
    addParameter("pos", this.pos);
    addParameter("skew", this.skew);
  }

  @Override
  protected void render(double deltaMs) {
    for (Apotheneum.Cylinder.Ring ring : Apotheneum.cylinder.exterior.rings) {
      float angle = (this.pos.getValuef() + ring.index * skew.getValuef()) % 360f;
      float pa = 0;
      for (LXPoint p : ring.points) {
        float dist = LXUtils.wrapdistf(angle, pa, 360f);
        colors[p.index] = LXColor.gray(LXUtils.max(0, 100 - dist));
        pa += 3;
      }
    }
    copyCylinderExterior();
  }

}
