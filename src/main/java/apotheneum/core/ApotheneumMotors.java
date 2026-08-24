/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package apotheneum.core;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.pattern.LXPattern;

@LXCategory("Apotheneum/core")
@LXComponent.Name("Motors")
@LXComponent.Description("Generates haptic motor movement with braking function")
public class ApotheneumMotors extends LXPattern {

  public final CompoundParameter level =
    new CompoundParameter("Level", 1, 1, 255)
    .setDescription("Motor output level");

  public final BooleanParameter brake =
    new BooleanParameter("Brake", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Applies active braking to the motors");

  public ApotheneumMotors(LX lx) {
    super(lx);
    addParameter("level", this.level);
    addParameter("brake", this.brake);
  }

  @Override
  protected void run(double deltaMs) {
    if (this.brake.isOn()) {
      setColors(LXColor.BLACK);
    } else {
      final int b = (int) Math.round(this.level.getValue());
      setColors(LXColor.rgb(b, b, b));
    }
  }

}
