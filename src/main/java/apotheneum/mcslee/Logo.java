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

import java.util.Arrays;

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
@LXComponent.Description("Square and circle logo on cube faces")
public class Logo extends ApotheneumPattern {

  private final static int BOX_THRESH = 16;
  private final static int MIN_BOX = 17;
  private final static float MAX_BOX = 22.5f;
  private final static float DEFAULT_CIRCLE_RADIUS = .22f;

  public final CompoundParameter circleRadius =
    new CompoundParameter("Circle Radius", DEFAULT_CIRCLE_RADIUS, .2, .4)
    .setDescription("Circle radius");

  public final CompoundParameter circleContrast=
    new CompoundParameter("Circle Contrast", 4, 1, 10)
    .setDescription("Circle contrast");

  public final CompoundParameter front =
    new CompoundParameter("Front", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Front level");

  public final CompoundParameter right =
    new CompoundParameter("Right", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Right level");

  public final CompoundParameter back =
    new CompoundParameter("Back", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Back level");

  public final CompoundParameter left =
    new CompoundParameter("Left", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Left level");

  public Logo(LX lx) {
    super(lx);
    addParameter("circleRadius", this.circleRadius);
    addParameter("circleContrast", this.circleContrast);
    addParameter("front", this.front);
    addParameter("right", this.right);
    addParameter("back", this.back);
    addParameter("left", this.left);
  }

  private final float[] level = new float[Apotheneum.GRID_WIDTH * Apotheneum.GRID_HEIGHT];

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    setColor(Apotheneum.cube.exterior.front, LXColor.BLACK);

    final float circleRadius = this.circleRadius.getValuef();
    final float circleContrast = this.circleContrast.getValuef();

    Arrays.fill(this.level, 0);

    int ai = 0;
    for (int xi = 0; xi < Apotheneum.GRID_WIDTH; ++xi) {
      final float xn = xi / (float) (Apotheneum.GRID_WIDTH-1);
      final float xd = Math.abs(xi - ((Apotheneum.GRID_WIDTH-1) * .5f));

      for (int yi = 0; yi < Apotheneum.GRID_HEIGHT; ++yi) {
        final float yn = yi / (float) (Apotheneum.GRID_HEIGHT-1);
        final float yd = Math.abs(yi - ((Apotheneum.GRID_HEIGHT-1) * .5f));
        if (xd > BOX_THRESH || yd > BOX_THRESH) {
          if (LXUtils.inRange(xd, MIN_BOX, MAX_BOX) && (yd < MAX_BOX)) {
            this.level[ai] = 1;
            // colors[p.index] = LXColor.WHITE;
          }
          if (LXUtils.inRange(yd, MIN_BOX, MAX_BOX) && (xd < MAX_BOX)) {
            this.level[ai] = 1;
            // colors[p.index] = LXColor.WHITE;
          }
        } else {
          final float d = LXUtils.distf(.5f, .5f, xn, yn);
          this.level[ai] = LXUtils.clampf(circleContrast - 10 * circleContrast * Math.abs(circleRadius - d), 0, 1);
          // colors[p.index] = LXColor.grayn(LXUtils.clamp(circleContrast - 10 * circleContrast * Math.abs(circleRadius - d), 0, 1));
        }
        ++ai;
      }
    }

    final float front = this.front.getValuef();
    final float right = this.right.getValuef();
    final float back = this.back.getValuef();
    final float left = this.left.getValuef();

    ai = 0;
    for (LXPoint p : Apotheneum.cube.exterior.front.model.points) {
      colors[p.index] = LXColor.grayn(this.level[ai++] * front);
    }
    ai = 0;
    for (LXPoint p : Apotheneum.cube.exterior.right.model.points) {
      colors[p.index] = LXColor.grayn(this.level[ai++] * right);
    }
    ai = 0;
    for (LXPoint p : Apotheneum.cube.exterior.back.model.points) {
      colors[p.index] = LXColor.grayn(this.level[ai++] * back);
    }
    ai = 0;
    for (LXPoint p : Apotheneum.cube.exterior.left.model.points) {
      colors[p.index] = LXColor.grayn(this.level[ai++] * left);
    }

    copyCubeExterior();
  }

}
