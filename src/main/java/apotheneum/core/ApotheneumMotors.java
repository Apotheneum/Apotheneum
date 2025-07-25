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

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.pattern.LXPattern;

@LXCategory("Apotheneum/core")
@LXComponentName("Motors")
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
