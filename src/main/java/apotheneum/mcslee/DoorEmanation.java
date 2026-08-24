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
import heronarts.lx.LXLayer;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Door Emanation")
@LXComponent.Description("Strip motion emanating from the doors")
public class DoorEmanation extends ApotheneumPattern {

  public final CompoundParameter minRate =
    new CompoundParameter("Min Rate", 3, 1, 20)
    .setDescription("Minimum rate of motion");

  public final CompoundParameter maxRate =
    new CompoundParameter("Max Rate", 5, 1, 40)
    .setDescription("Maximum rate of motion");

  public final CompoundParameter cubeHeight =
    new CompoundParameter("Cub-H", 10, 1, 40)
    .setDescription("Maximum vertical length of motion");

  public final CompoundParameter cubeWidth =
    new CompoundParameter("Cub-W", 10, 1, 35)
    .setDescription("Maximum horizontal length of motion");

  public final CompoundParameter cylinderHeight =
    new CompoundParameter("Cyl-H", 10, 1, 40)
    .setDescription("Maximum vertical length of motion");

  public final CompoundParameter cylinderWidth =
    new CompoundParameter("Cyl-W", 10, 1, 35)
    .setDescription("Maximum horizontal length of motion");

  public final CompoundParameter fade =
    new CompoundParameter("Fade", 2, 1, 10)
    .setDescription("Fade length");

  public final CompoundParameter density =
    new CompoundParameter("Density", 3, 1, MAX_PER_STRIP)
    .setDescription("Amount of motion per strip");

  public final BooleanParameter wait =
    new BooleanParameter("Wait", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Wait to start new sparks until released");

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 1, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Direction of motion");

  private static final int MAX_PER_STRIP = 10;

  public DoorEmanation(LX lx) {
    super(lx);
    addParameter("minRate", this.minRate);
    addParameter("maxRate", this.maxRate);
    addParameter("cubeHeight", this.cubeHeight);
    addParameter("cubeWidth", this.cubeWidth);
    addParameter("cylinderHeight", this.cylinderHeight);
    addParameter("cylinderWidth", this.cylinderWidth);
    addParameter("density", this.density);
    addParameter("fade", this.fade);
    addParameter("speed", this.speed);
    addParameter("hold", this.wait);

    for (int i = 0; i < MAX_PER_STRIP; ++i) {
      for (int f = 0; f < 4; ++f) {
        for (int x = 0; x < Apotheneum.DOOR_WIDTH; ++x) {
          addLayer(new StripLayer(
            lx,
            Apotheneum.cube.exterior.column(f*Apotheneum.GRID_WIDTH + Apotheneum.Cube.DOOR_START_COLUMN + x).points,
            Apotheneum.GRID_HEIGHT - Apotheneum.DOOR_HEIGHT,
            -1,
            this.cubeHeight,
            i
          ));
          addLayer(new StripLayer(
            lx,
            Apotheneum.cylinder.exterior.column(f*(Apotheneum.RING_LENGTH/4) + Apotheneum.Cylinder.DOOR_START_COLUMN + x).points,
            Apotheneum.GRID_HEIGHT - Apotheneum.DOOR_HEIGHT,
            -1,
            this.cylinderHeight,
            i
          ));
        }
        for (int y = 0; y < Apotheneum.DOOR_HEIGHT; ++y) {
          addLayer(new StripLayer(
            lx,
            Apotheneum.cube.exterior.ring(Apotheneum.GRID_HEIGHT - 1 - y).points,
            f*Apotheneum.GRID_WIDTH + Apotheneum.Cube.DOOR_START_COLUMN - 1,
            -1,
            this.cubeWidth,
            i
          ));
          addLayer(new StripLayer(
            lx,
            Apotheneum.cube.exterior.ring(Apotheneum.GRID_HEIGHT - 1 - y).points,
            f*Apotheneum.GRID_WIDTH + Apotheneum.Cube.DOOR_START_COLUMN + Apotheneum.DOOR_WIDTH,
            1,
            this.cubeWidth,
            i
          ));
          addLayer(new StripLayer(
            lx,
            Apotheneum.cylinder.exterior.ring(Apotheneum.CYLINDER_HEIGHT - 1 - y).points,
            f*Apotheneum.RING_LENGTH/4 + Apotheneum.Cylinder.DOOR_START_COLUMN - 1,
            -1,
            this.cylinderWidth,
            i
          ));
          addLayer(new StripLayer(
            lx,
            Apotheneum.cylinder.exterior.ring(Apotheneum.CYLINDER_HEIGHT - 1 - y).points,
            f*Apotheneum.RING_LENGTH/4 + Apotheneum.Cylinder.DOOR_START_COLUMN + Apotheneum.DOOR_WIDTH,
            1,
            this.cylinderWidth,
            i
          ));
        }
      }
    }
  }

  private class StripLayer extends LXLayer {
    private final LXPoint[] strip;
    private final int start;
    private final int direction;
    private final CompoundParameter length;
    private final int dIndex;
    private double rnd;

    private StripLayer(LX lx, LXPoint[] strip, int start, int direction, CompoundParameter length, int dIndex) {
      super(lx);
      this.strip = strip;
      this.start = start;
      this.direction = direction;
      this.length = length;
      this.dIndex = dIndex;
      this.rnd = Math.random();
    }

    private boolean init = true;
    private double basis = 0;
    private boolean on = false;
    private boolean waiting = false;

    @Override
    public void run(double deltaMs) {
      final double length = this.length.getValue();
      final double spd = speed.getValue();
      final double fad = fade.getValue();
      final double falloff = 1 / fad;
      final double minBasis = -fad, maxBasis = length + fad;

      if (this.init) {
        this.init = false;
        this.on = density.getValue() > this.dIndex;
      }

      if (this.waiting) {
        if (wait.isOn()) {
          return;
        }
        this.waiting = false;
        this.basis = spd > 0 ? minBasis : maxBasis;
      } else {
        this.basis += deltaMs / 1000 * spd * LXUtils.lerp(minRate.getValue(), maxRate.getValue(), this.rnd);
      }
      if ((spd > 0 && this.basis > maxBasis) || (spd < 0 && this.basis < minBasis)) {
        this.basis = spd > 0 ? minBasis : maxBasis;
        this.rnd = Math.random();
        this.on = density.getValue() > this.dIndex;
        this.waiting = wait.isOn();
      }

      if (this.waiting || !this.on) {
        return;
      }

      for (int i = 0; i < length; ++i) {
        LXPoint p = this.strip[(2*this.strip.length + this.start + this.direction*i) % this.strip.length];
        double b = LXUtils.lerp(1, 0, this.basis / length) - falloff * Math.abs(i - this.basis);
        if (b > 0) {
          colors[p.index] = LXColor.lightest(colors[p.index], LXColor.grayn(b));
        }
      }

    }
  }

  @Override
  protected void render(double deltaMs) {
    setApotheneumColor(LXColor.BLACK);
  }

  @Override
  protected void afterLayers(double deltaMs) {
    copyExterior();
  }

}
