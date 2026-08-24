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
import heronarts.lx.LXLayer;
import heronarts.lx.color.LXColor;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

public class Wormhole extends ApotheneumPattern implements ApotheneumPattern.Midi {

  public final TriggerParameter trig =
    new TriggerParameter("Pulse", this::pulse)
    .setDescription("Trigger a pulse");

  public final CompoundParameter speed =
    new CompoundParameter("Speed", .1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Pulse speed");

  public final CompoundParameter accelPoint =
    new CompoundParameter("Thresh", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Accel thresh");

  public final CompoundParameter accel =
    new CompoundParameter("Accel", 0, 2)
    .setDescription("Pulse accel");

  public final CompoundParameter width =
    new CompoundParameter("Width", 3, 1, 10)
    .setDescription("Pulse width");

  public final CompoundParameter fade =
    new CompoundParameter("Fade", 2, 1, 20)
    .setDescription("Pulse fade");

  public Wormhole(LX lx) {
    super(lx);
    addParameter("trig", this.trig);
    addParameter("speed", this.speed);
    addParameter("accelPoint", this.accelPoint);
    addParameter("accel", this.accel);
    addParameter("width", this.width);
    addParameter("fade", this.fade);
  }

  final static int JOURNEY = Apotheneum.GRID_HEIGHT + Apotheneum.CYLINDER_HEIGHT + 1;

  class Ring extends LXLayer {

    private double basis = 0;
    private double extraSpeed = 0;

    Ring(LX lx) {
      super(lx);
    }

    @Override
    public void run(double deltaMs) {
      double accelPoint = Wormhole.this.accelPoint.getValuef();
      if (this.basis > accelPoint) {
        this.extraSpeed += accel.getValue() * .001 * deltaMs;
      }
      this.basis += deltaMs * .001 * (this.extraSpeed + speed.getValue());

      final double width = Wormhole.this.width.getValue();
      final double falloff = 1 / fade.getValuef();

      final double pos = JOURNEY * this.basis;

      boolean done = true;

      int ri = 0;
      for (Apotheneum.Ring ring : Apotheneum.cube.exterior.rings) {
        int p2 = Apotheneum.GRID_HEIGHT - 1 - ri;
        double b = (p2 > pos)
          ? 1 - .5 * (p2 - pos)
          : width - falloff * (pos - p2);
        if (b > 0) {
          int c = LXColor.grayn(LXUtils.min(1, b));
          done = false;
          for (LXPoint p : ring.points) {
            addColor(p.index, c);
          }
        }
        ++ri;
      }
      for (Apotheneum.Ring ring : Apotheneum.cylinder.exterior.rings) {
        int p2 = ri;
        double b = (p2 > pos)
          ? 1 - .5 * (p2 - pos)
          : width - falloff * (pos - p2);
        if (b > 0) {
          int c = LXColor.grayn(LXUtils.min(1, b));
          done = false;
          for (LXPoint p : ring.points) {
            addColor(p.index, c);
          }
        }
        ++ri;
      }

      if (done) {
        remove();
      }
    }

  }

  private void pulse() {
    addLayer(new Ring(this.lx));
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    setApotheneumColor(LXColor.BLACK);

  }

  @Override
  protected void afterLayers(double deltaMs) {
    copyExterior();
  }

  @Override
  public void noteOnReceived(MidiNoteOn noteOn) {
    pulse();
  }

}
