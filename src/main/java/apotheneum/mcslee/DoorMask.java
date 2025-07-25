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
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponentName("Door Mask")
@LXComponent.Description("Mask the levels around doors")
public class DoorMask extends ApotheneumEffect {

  public final CompoundParameter square =
    new CompoundParameter("Square", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Square off the mask shape");

  public final CompoundParameter distance =
    new CompoundParameter("Distance", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Distance from door");

  public final CompoundParameter yRatio =
    new CompoundParameter("Ratio", 1, 1, 2)
    .setDescription("Aspect ratio");

  public final CompoundParameter contrast =
    new CompoundParameter("Contrast", 1, .05, 2)
    .setExponent(2)
    .setDescription("Contrast level");

  public final CompoundParameter invert =
    new CompoundParameter("Invert", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Invert the mask");

  public final BooleanParameter cue =
    new BooleanParameter("Cue", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Show the mask directly");

  public DoorMask(LX lx) {
    super(lx);
    addParameter("square", this.square);
    addParameter("distance", this.distance);
    addParameter("contrast", this.contrast);
    addParameter("yRatio", this.yRatio);
    addParameter("invert", this.invert);
    addParameter("cue", this.cue);
  }

  @Override
  protected void render(double deltaMs, double enabledAmount) {
    if (enabledAmount <= 0) {
      return;
    }

    int i = 0;
    for (LXModel column : Apotheneum.cube.exterior.columns) {
      renderColumn(column, i, true, enabledAmount);
      ++i;
    }
    i = 0;
    for (LXModel column : Apotheneum.cube.interior.columns) {
      renderColumn(column, i, true, enabledAmount);
      ++i;
    }
    i = 0;
    for (LXModel column : Apotheneum.cylinder.exterior.columns) {
      renderColumn(column, i, false, enabledAmount);
      ++i;
    }
    i = 0;
    for (LXModel column : Apotheneum.cylinder.interior.columns) {
      renderColumn(column, i, false, enabledAmount);
      ++i;
    }
  }

  protected void renderColumn(LXModel column, int index, boolean isCube, double amount) {
    int xDist = 0;
    if (isCube) {
      xDist = (int) LXUtils.max(0, Math.abs((index % 50) - 24.5) - 4.5);
    } else {
      xDist = (int) LXUtils.max(0, Math.abs((index % 30) - 14.5) - 4.5);
    }

    final double distance = this.distance.getValue() * Apotheneum.GRID_HEIGHT;
    final double square = this.square.getValue();
    final double yRatio = 1. / this.yRatio.getValue();
    final boolean cue = this.cue.isOn();
    final double contrast = this.contrast.getValue();
    final double sign = LXUtils.lerp(1, -1, this.invert.getValue());

    int pi = 0;
    for (LXPoint p : column.points) {
      double yDist = yRatio * LXUtils.max(0, column.points.length - 11 - pi);
      double avg = (xDist + yDist) * .5;
      double max = LXUtils.max(xDist, yDist);
      double dist = LXUtils.lerp(avg, max, square);

      int mask = LXColor.grayn(LXUtils.lerp(1, LXUtils.clamp(.5f - sign * contrast * (dist - distance), 0, 1), amount));
      if (cue) {
        colors[p.index] = mask;
      } else {
        colors[p.index] = LXColor.multiply(colors[p.index], mask, LXColor.BLEND_ALPHA_FULL);
      }
      ++pi;
    }
  }

}
