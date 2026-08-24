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

package apotheneum.test;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/Test")
@LXComponent.Name("Strip Diagnostic")
@LXComponent.Description("Test Routine to light sequences on strips")
public class StripDiagnostic extends ApotheneumPattern {

  public final DiscreteParameter start = new DiscreteParameter("Start", 0, 46);
  public final DiscreteParameter num = new DiscreteParameter("Num", 1, 0, 46);
  public final BooleanParameter muteOn = new BooleanParameter("MuteOn", false);
  public final DiscreteParameter mute = new DiscreteParameter("Mute", 0, 46);

  public StripDiagnostic(LX lx) {
    super(lx);
    addParameter("start", this.start);
    addParameter("num", this.num);
    addParameter("muteOn", this.muteOn);
    addParameter("mute", this.mute);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    setApotheneumColor(LXColor.BLACK);

    for (Apotheneum.Column column : Apotheneum.cube.exterior.columns) {
      renderColumn(column);
    }
    for (Apotheneum.Column column : Apotheneum.cylinder.exterior.columns) {
      renderColumn(column);
    }
    copyExterior();

  }

  protected void renderColumn(Apotheneum.Column model) {
    final int start = this.start.getValuei();
    final int num = this.num.getValuei();
    final int mute = this.mute.getValuei();
    final boolean isMute = this.muteOn.isOn();

    int i = 0;
    for (LXPoint p : model.points) {
      if (LXUtils.inRange(i, start, start+num - 1)) {
        colors[p.index] = LXColor.WHITE;
      }
      if (isMute && (mute == i)) {
        colors[p.index] = LXColor.BLACK;
      }
      ++i;
    }
  }

}
