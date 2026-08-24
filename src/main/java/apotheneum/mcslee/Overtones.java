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
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Description("Vertically oscillating sinusoids that fall in and out of sync")
public class Overtones extends ApotheneumPattern {

  public final CompoundParameter base =
    new CompoundParameter("Base", 0, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Base oscillation");

  public final CompoundParameter amplitude =
    new CompoundParameter("Amp", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Oscillation amplitude");

  public final CompoundParameter width =
    new CompoundParameter("Width", 4, 0, 20)
    .setDescription("Stripe width");

  public final CompoundParameter offset =
    new CompoundParameter("Offset", 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Offset through cycle");

  public final BooleanParameter outputTriggers =
    new BooleanParameter("Outputs", false)
    .setDescription("Whether output triggers are fired");

  public final TriggerParameter peak =
    new TriggerParameter("Peak", this::peak);

  public final TriggerParameter floor =
    new TriggerParameter("Floor", this::floor);

  private final OscMessage oscPeak = new OscMessage("/overtones/peak");
  private final OscMessage oscFloor = new OscMessage("/overtones/floor");

  public Overtones(LX lx) {
    super(lx);
    addParameter("base", this.base);
    addParameter("amplitude", this.amplitude);
    addParameter("width", this.width);
    addParameter("offset", this.offset);
    addParameter("peak", this.peak);
    addParameter("floor", this.floor);
    addParameter("outputTriggers", this.outputTriggers);
  }

  private static final int NUM_TONES = 30;

  private final double[] toneDegrees = new double[NUM_TONES + 1];
  private final double[] tonePosition = new double[NUM_TONES + 1];

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    final double base = this.base.getValue();
    final double offset = this.offset.getNormalized();
    final double amplitude = this.amplitude.getValuef();
    final float width = this.width.getValuef();
    final float falloff = 100 / width;

    int si = 0;

    boolean peaked = false;
    boolean floored = false;

    for (int i = 0; i < NUM_TONES + 1; ++i) {
      final double degrees = (base + offset * i * 360) % 360;
      this.tonePosition[i] = (Apotheneum.CYLINDER_HEIGHT-1) *
        (0.5 + - 0.5 * amplitude * Math.sin(Math.toRadians(degrees)));

      if ((this.toneDegrees[i] < 90) && (degrees >= 90)) {
        peaked = true;
      }
      if ((this.toneDegrees[i] < 270) && (degrees >= 270)) {
        floored = true;
      }
      this.toneDegrees[i] = degrees;
    }

    // Map tones over all strips
    for (Apotheneum.Column strip : Apotheneum.cylinder.exterior.columns) {
      final int sm = si % NUM_TONES;
      final int sd = si / NUM_TONES;
      final int toneIndex = (sd % 2 == 0) ? sm : (NUM_TONES - sm);
      final double pos = this.tonePosition[toneIndex];
      int pi = 0;
      for (LXPoint p : strip.points) {
        colors[p.index] = LXColor.gray(LXUtils.max(0, 100 - falloff * Math.abs(pi - pos)));
        ++pi;
      }
      ++si;
    }
    copyCylinderExterior();

    if (this.outputTriggers.isOn()) {
      if (peaked) {
        this.peak.trigger();
      }
      if (floored) {
        this.floor.trigger();
      }
    }
  }

  private void peak() {
    Apotheneum.osc2Ableton(this.oscPeak);
  }

  private void floor() {
    Apotheneum.osc2Ableton(this.oscFloor);
  }

}
