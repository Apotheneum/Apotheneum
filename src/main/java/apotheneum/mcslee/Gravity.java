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
import heronarts.lx.osc.OscInt;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Gravity")
@LXComponent.Description("Objects moving under gravitational forces")
public class Gravity extends ApotheneumPattern implements UIDeviceControls<Gravity> {

  private class Orb extends LXLayer {

    private final int numCols;
    private final Apotheneum.Column[] columns;
    private final int yMax;
    private final float rnd;
    private float x;
    private float y = 0;
    private float vy = 0;
    private float vx = 0;
    private final float vxd;
    private final boolean box;

    private Orb(LX lx, Apotheneum.Column[] columns, boolean box) {
      super(lx);
      this.columns = columns;
      this.numCols = columns.length;
      this.yMax = columns[0].size;
      this.rnd = (float) Math.random();
      this.x = LXUtils.randomf(this.numCols-1);
      this.y = this.yMax;
      this.vxd = Math.random() < 0.5 ? 1 : -1;
      this.box = box;
    }

    private static final float GRAVITY_RANGE = 40;
    private static final float PUSH_RANGE = 40;
    private static final float OSC_THRESHOLD = 4f;

    @Override
    public void run(double deltaMs) {
      boolean active =
        (this.box && cubeOn.isOn()) ||
        (!this.box && cylinderOn.isOn());
      if (!active) {
        return;
      }

      final float gravity = Gravity.this.gravity.getValuef();
      final float gravityDirection = Gravity.this.gravityDirection.getValuef();
      final float bounce = Gravity.this.bounce.getValuef();
      final float punch = Gravity.this.punch.getValuef();
      final float friction = Gravity.this.friction.getValuef();
      final float frictionDirection = this.vy > 0 ? -1 : 1;
      final float variance = Gravity.this.variance.getValuef();
      final float push = Gravity.this.push.getValuef();
      final float brake = Gravity.this.brake.getValuef();

      final float dt = (float) (deltaMs * .001);

      final float brakeDirection = this.vx > 0 ? -1 : 1;
      final float ax = PUSH_RANGE * (
        push * this.vxd * LXUtils.lerpf(1, 1-variance, this.rnd) +
        brakeDirection * brake
      );

      this.x += this.vx * dt + .5f * ax * dt * dt;
      this.vx += ax * dt;
      if (this.box) {
        if (!LXUtils.inRange(this.x, 0, this.numCols-1)) {
          this.vx = -this.vx;
          if (Math.abs(this.vx) > OSC_THRESHOLD) {
            oscWall.flag(this.vx);
          }
        }
        if (this.x < 0) {
          this.x = -this.x;
        } else if (this.x >= this.numCols - 1) {
          this.x = 2*this.numCols - 2 - this.x;
        }
      }
      this.x = (this.numCols + this.x) % this.numCols;

      final float ay = GRAVITY_RANGE * (
        gravity * -gravityDirection * LXUtils.lerpf(1, 1-variance, this.rnd) +
        friction * frictionDirection +
        frictionDirection * LXUtils.lerpf(0, .25f*variance, this.rnd)
      );
      this.y += this.vy * dt + .5f * ay * dt * dt;
      this.vy += ay * dt;

      final float impact = Math.abs(this.vy);
      if (!LXUtils.inRange(this.y, 0, this.yMax)) {
        this.vy = -this.vy * bounce + frictionDirection * punch * GRAVITY_RANGE;
      }
      if (this.y < 0) {
        this.y = -this.y;
        if (this.y < 0.1) {
          this.y = 0;
        }
        if (impact > OSC_THRESHOLD) {
          oscPeak.flag(impact);
        }
      } else if (this.y >= this.yMax) {
        this.y = 2*this.yMax - this.y;
        if (this.y > this.yMax - 0.1) {
          this.y = this.yMax;
        }
        if (impact > OSC_THRESHOLD) {
          oscFloor.flag(impact);
        }
      }


      final float radius =
        LXUtils.lerpf(1 - shrink.getValuef(), 1, this.y / this.yMax) *
        LXUtils.lerpf(minRadius.getValuef(), maxRadius.getValuef(), this.rnd);
      final float falloff = 1 / radius;

      int xMin = (int) Math.floor(this.x - radius);
      int xMax = (int) Math.ceil(this.x + radius);
      if (this.box) {
        xMin = LXUtils.max(0, xMin);
        xMax = LXUtils.min(this.numCols - 1, xMax);
      }

      for (int x = xMin; x <= xMax; ++x) {
        int y = 0;
        for (LXPoint p : this.columns[(x + this.numCols) % this.numCols].points) {
          double dist = LXUtils.distf(x, y, this.x, this.y);
          double b = 1 - falloff * dist * dist / radius;
          if (b > 0) {
            colors[p.index] = LXColor.lightest(colors[p.index], LXColor.grayn(b));
          }
          ++y;
        }
      }

    }
  }

  public final CompoundParameter gravity =
    new CompoundParameter("Gravity", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strenth of gravity");

  public final CompoundParameter gravityDirection =
    new CompoundParameter("Direction", -1, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Gravity direction");

  public final CompoundParameter bounce =
    new CompoundParameter("Bounce", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strength of bounce");

  public final CompoundParameter punch =
    new CompoundParameter("Punch", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strenth of punch");

  public final CompoundParameter friction =
    new CompoundParameter("Friction", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strenth of friction");

  public final CompoundParameter minRadius =
    new CompoundParameter("Min Rad", 5, 1, 25)
    .setDescription("Minimum radius");

  public final CompoundParameter maxRadius =
    new CompoundParameter("Max Rad", 20, 1, 25)
    .setDescription("Minimum radius");

  public final CompoundParameter shrink =
    new CompoundParameter("Shrink", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Shrink as moving higher");

  public final CompoundParameter variance =
    new CompoundParameter("Variance", 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Gravitational variance");

  public final CompoundParameter push =
    new CompoundParameter("Push", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Push the orbs laterally");

  public final CompoundParameter brake =
    new CompoundParameter("Brake", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Brake the orbs lateral motion");

  public final BooleanParameter output =
    new BooleanParameter("Outputs", false)
    .setDescription("Whether output OSC is fired");

  public final BooleanParameter cubeOn =
    new BooleanParameter("Cube", true)
    .setDescription("Whether cube objects are active");

  public final BooleanParameter cylinderOn =
    new BooleanParameter("Cylinder", false)
    .setDescription("Whether cylinder objects are active");

  private final OscTrigger oscPeak = new OscTrigger("/gravity/peak");
  private final OscTrigger oscFloor = new OscTrigger("/gravity/floor");
  private final OscTrigger oscWall = new OscTrigger("/gravity/wall");

  private class OscTrigger {

    private static final double RETRIG_LIMIT_MS = 60;

    private final OscMessage message;
    private boolean flag = false;
    private final OscInt velocity;
    private double reset = 0;

    private OscTrigger(String message) {
      this.message = new OscMessage(message).add(this.velocity = new OscInt());
    }

    private void pre(double deltaMs) {
      this.flag = false;
      this.velocity.setValue(1);
      this.reset += deltaMs;
    }

    private void flag(double v) {
      double abs = .25f * Math.abs(v);
      this.velocity.setValue(LXUtils.max(this.velocity.getValue(), LXUtils.min(127, (int) (abs * abs))));
      this.flag = true;
    }

    private void post() {
      if (this.flag && (this.reset > RETRIG_LIMIT_MS)) {
        if (output.isOn()) {
          Apotheneum.osc2Ableton(this.message);
        }
        this.reset = 0;
      }
    }
  }

  public Gravity(LX lx) {
    super(lx);
    addParameter("gravity", this.gravity);
    addParameter("gravityDirection", this.gravityDirection);
    addParameter("variance", this.variance);
    addParameter("bounce", this.bounce);
    addParameter("punch", this.punch);
    addParameter("friction", this.friction);
    addParameter("minRadius", this.minRadius);
    addParameter("maxRadius", this.maxRadius);
    addParameter("shrink", this.shrink);
    addParameter("push", this.push);
    addParameter("brake", this.brake);
    addParameter("output", this.output);
    addParameter("cubeOn", this.cubeOn);
    addParameter("cylinderOn", this.cylinderOn);

    for (int i = 0; i < 20; ++i) {
      addLayer(new Orb(lx, Apotheneum.cube.exterior.faces[i % 4].columns, true));
    }
    for (int i = 0; i < 10; ++i) {
      addLayer(new Orb(lx, Apotheneum.cylinder.exterior.columns, false));
    }
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.oscWall.pre(deltaMs);
    this.oscPeak.pre(deltaMs);
    this.oscFloor.pre(deltaMs);
  }

  @Override
  protected void afterLayers(double deltaMs) {
    copyExterior();
    this.oscWall.post();
    this.oscPeak.post();
    this.oscFloor.post();
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Gravity gravity) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);

    addColumn(uiDevice,
      "Gravity",
      newKnob(gravity.gravity),
      newKnob(gravity.gravityDirection),
      newKnob(gravity.variance)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Forces",
      newKnob(gravity.friction),
      newKnob(gravity.bounce),
      newKnob(gravity.punch)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Lateral",
      newKnob(gravity.push),
      newKnob(gravity.brake)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Size",
      newKnob(gravity.minRadius),
      newKnob(gravity.maxRadius),
      newKnob(gravity.shrink)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Out",
      newButton(gravity.cubeOn),
      newButton(gravity.cylinderOn),
      newButton(gravity.output)
    ).setChildSpacing(6);

  }

}
