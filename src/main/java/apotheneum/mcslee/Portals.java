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
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Description("Portal motion around the doorways")
public class Portals extends ApotheneumPattern {

  public final CompoundParameter minMax =
    new CompoundParameter("AvgMax", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Avg/Max of x/y dist");

  public final CompoundParameter distance =
    new CompoundParameter("Distance", 0, 1)
    .setDescription("Distance from portal");

  public final CompoundParameter range =
    new CompoundParameter("Range", 1, 1, 50)
    .setDescription("Stripe range");

  public final CompoundParameter sharp =
    new CompoundParameter("Sharp", 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Stripe sharpness");

  public final CompoundParameter contrast =
    new CompoundParameter("Contrast", 1, 1, 10)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Stripe contrast");

  public final CompoundParameter yRatio =
    new CompoundParameter("Ratio", 1, 1, 2)
    .setDescription("Aspect ratio");

  public Portals(LX lx) {
    super(lx);
    addParameter("minMax", this.minMax);
    addParameter("distance", this.distance);
    addParameter("range", this.range);
    addParameter("sharp", this.sharp);
    addParameter("contrast", this.contrast);
    addParameter("yRatio", this.yRatio);
  }

  @Override
  protected void render(double deltaMs) {
    setApotheneumColor(LXColor.BLACK);
    int i = 0;
    for (LXModel column : Apotheneum.cube.exterior.columns) {
      renderColumn(column, i, true);
      ++i;
    }
    i = 0;
    for (LXModel column : Apotheneum.cylinder.exterior.columns) {
      renderColumn(column, i, false);
      ++i;
    }
    copyExterior();
  }

  protected void renderColumn(LXModel column, int index, boolean isCube) {
    int xDist = 0;
    if (isCube) {
      xDist = (int) LXUtils.max(0, Math.abs((index % 50) - 24.5) - 4.5);
    } else {
      xDist = (int) LXUtils.max(0, Math.abs((index % 30) - 14.5) - 4.5);
    }

    final double distance = this.distance.getValue();
    final double range = this.range.getValue();
    final double minMax = this.minMax.getValue();
    final double yRatio = 1. / this.yRatio.getValue();
    final double contrast = this.contrast.getValue();
    final double falloff = contrast / LXUtils.lerp(range, 1, this.sharp.getValuef());

    int pi = 0;
    for (LXPoint p : column.points) {
      double yDist = yRatio * LXUtils.max(0, column.points.length - 11 - pi);
      double avg = (xDist + yDist) * .5;
      double max = LXUtils.max(xDist, yDist);
      double dist = LXUtils.lerp(avg, max, minMax);

      double b = contrast - falloff*Math.abs(LXUtils.wrapdist(dist % range, range*distance, range));
      if (b > 0) {
        colors[p.index] = LXColor.grayn(LXUtils.min(1, b));
      }
      ++pi;
    }
  }

}
