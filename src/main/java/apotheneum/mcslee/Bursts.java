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

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix2f;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

public abstract class Bursts extends ApotheneumPattern implements ApotheneumPattern.Midi {

  public interface DistanceFunction {
    public float getDistance(float xd, float yd);
  }

  public enum Shape {
    CIRCLE("Circle", (xd, yd) -> { return (float) Math.sqrt(xd*xd + yd*yd); }),
    SQUARE("Square", (xd, yd) -> { return LXUtils.maxf(Math.abs(xd), Math.abs(yd)); }),
    DIAMOND("Diamond", (xd, yd) -> { return .5f * Math.abs(xd) + .5f * Math.abs(yd); }),
    CROSS("Cross", (xd, yd) -> { return LXUtils.minf(Math.abs(xd), Math.abs(yd)); });

    public final String label;
    public final DistanceFunction distance;

    private Shape(String label, DistanceFunction distance) {
      this.label = label;
      this.distance = distance;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final TriggerParameter burst =
    new TriggerParameter("Burst", this::onBurst)
    .setDescription("Trigger a burst");

  public EnumParameter<Shape> shape1 =
    new EnumParameter<Shape>("Shape 1", Shape.CIRCLE)
    .setDescription("Burst shape 1");

  public EnumParameter<Shape> shape2 =
    new EnumParameter<Shape>("Shape 2", Shape.CIRCLE)
    .setDescription("Burst shape 2");

  public final CompoundParameter shapeLerp =
    new CompoundParameter("Shape", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Blend between two shapes");

  public final CompoundParameter shapeRandom =
    new CompoundParameter("Shp-Rnd", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Per-burst randomization applied to shape");

  public final CompoundDiscreteParameter perTrig =
    new CompoundDiscreteParameter("Per Trigger", 1, 1, 16)
    .setDescription("Number of bursts per trigger");

  public final CompoundParameter burstTime =
    new CompoundParameter("Time", 1, .5, 5)
    .setUnits(CompoundParameter.Units.SECONDS)
    .setDescription("Burst Time");

  public final CompoundParameter burstRadius =
    new CompoundParameter("Radius ", .1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Burst Radius");

  public final CompoundParameter burstThickness =
    new CompoundParameter("Thickness ", .05, .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Burst Thickness");

  public final CompoundParameter burstAttack =
    new CompoundParameter("Atk ", 0, .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Burst Attack");

  public final CompoundParameter burstExp =
    new CompoundParameter("Exp ", 1, .25, 3)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Burst Exp");

  public final CompoundParameter burstSpread =
    new CompoundParameter("Spread", .5, 0, 2)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Spread of burst initial position from origin");

  public final CompoundParameter spin =
    new CompoundParameter("Spin", 0, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setWrappable(true)
    .setDescription("Spin the bursts");

  public final CompoundParameter spinRandom =
    new CompoundParameter("Spin-Rnd", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Randomize per-burst spin");

  public Bursts(LX lx) {
    super(lx);
    addParameter("burst", this.burst);
    addParameter("shape1", this.shape1);
    addParameter("shape2", this.shape2);
    addParameter("shapeLerp", this.shapeLerp);
    addParameter("shapeRandom", this.shapeRandom);
    addParameter("spin", this.spin);
    addParameter("spinRandom", this.spinRandom);
    addParameter("perTrig", this.perTrig);
    addParameter("burstRadius", this.burstRadius);
    addParameter("burstThickness", this.burstThickness);
    addParameter("burstTime", this.burstTime);
    addParameter("burstExp", this.burstExp);
    addParameter("burstAttack", this.burstAttack);
    addParameter("burstSpread", this.burstSpread);
  }

  @Override
  protected void onModelChanged(LXModel model) {
    this.bursts.clear();
  }

  private final List<Burst> bursts = new ArrayList<>();

  protected void addBurst(Burst burst) {
    this.bursts.add(burst);
  }

  protected class Burst {

    private final LXModel[] columns;
    private float basis;
    private final float xn;
    private final float yn;
    private final float shapeRnd;

    private final Matrix2f spinRandomMatrix = new Matrix2f();
    private final Matrix2f spinCompositeMatrix = new Matrix2f();

    protected Burst(Apotheneum.Cube.Face face) {
      this(face.columns, null);
    }

    protected Burst(Apotheneum.Cube.Face face, Burst copy) {
      this(face.columns, copy);
    }

    protected Burst(Apotheneum.Orientation orientation) {
      this(orientation.columns(), null);
    }

    protected Burst(LXModel[] columns, Burst copy) {
      this.columns = columns;

      if (copy != null) {

        this.spinRandomMatrix.set(copy.spinRandomMatrix);
        this.shapeRnd = copy.shapeRnd;
        this.xn = copy.xn;
        this.yn = copy.yn;

      } else {

        this.spinRandomMatrix.rotation((float) LXUtils.lerp(0, LX.TWO_PI * spinRandom.getValue(), Math.random()));

        final float shapeLimit = .5f * shapeRandom.getValuef();
        this.shapeRnd = (float) LXUtils.lerp(-shapeLimit, shapeLimit, Math.random());

        float spread = burstSpread.getValuef();
        if (spread <= 1) {
          this.xn = (float) LXUtils.lerp(.5 - .5*spread, .5 + .5*spread, Math.random());
          this.yn = (float) LXUtils.lerp(.5 - .5*spread, .5 + .5*spread, Math.random());
        } else {
          float s2 = spread - 1;
          this.xn = Math.random() > 0.5 ?
            (float) LXUtils.lerp(0, .5 - .5*s2, Math.random()) :
            (float) LXUtils.lerp(.5 + .5*s2, 1, Math.random());
          this.yn = Math.random() > 0.5 ?
            (float) LXUtils.lerp(0, .5 - .5*s2, Math.random()) :
            (float) LXUtils.lerp(.5 + .5*s2, 1, Math.random());
        }
      }
    }

    protected void render(double deltaMs) {
      final DistanceFunction distance1 = shape1.getEnum().distance;
      final DistanceFunction distance2 = shape2.getEnum().distance;
      final float sLerp = LXUtils.clampf(this.shapeRnd + shapeLerp.getValuef(), 0, 1);

      this.spinCompositeMatrix.set(this.spinRandomMatrix);
      this.spinCompositeMatrix.mul(spinMatrix);

      this.basis += deltaMs / (1000f * burstTime.getValuef());
      if (this.basis < 1) {
        final double thickness = LXUtils.lerp(0.01, burstThickness.getValuef(), LXUtils.min(1, this.basis / burstAttack.getValue()));
        final float falloff = 100 / (float) thickness;

        final float radius = (float) (burstRadius.getValue() * Math.pow(this.basis, burstExp.getValuef()));
        final float level = LXUtils.lerpf(100, 0, this.basis);

        final boolean wrap = canBurstsWrap();
        final float wrapOffset = wrap ? 0 : 1;

        int x = 0;
        for (LXModel column : this.columns) {
          int y = 0;
          for (LXPoint p : column.points) {
            float pxn = x / (this.columns.length - wrapOffset);
            float pyn = y / (column.points.length - 1f);
            float dx = wrap ? 3f * wrapdiff(this.xn, pxn) : this.xn - pxn;
            float dy = this.yn - pyn;
            float xd = this.spinCompositeMatrix.m00 * dx + this.spinCompositeMatrix.m10 * dy;
            float yd = this.spinCompositeMatrix.m01 * dx + this.spinCompositeMatrix.m11 * dy;

            float dist = LXUtils.lerpf(
              distance1.getDistance(xd, yd),
              distance2.getDistance(xd, yd),
              sLerp
            );
            double b = level - falloff * Math.abs(dist - radius);
            if (b > 0) {
              colors[p.index] = LXColor.lightest(colors[p.index], LXColor.gray(b));
            }
            ++y;
          }
          ++x;
        }
      }
    }

    protected float wrapdiff(float x1, float x2) {
      if (x1 >= x2) {
        float d1 = x1 - x2;
        float d2 = x2 - (x1-1);
        return (d1 < d2) ? d1 : -d2;
      } else {
        float d1 = x2 - x1;
        float d2 = x1 - (x2-1);
        return (d1 < d2) ? -d1 : d2;
      }
    }
  }

  protected abstract boolean canBurstsWrap();

  protected abstract void generateBursts(int num);

  private void onBurst() {
    if (Apotheneum.exists) {
      final int num = this.perTrig.getValuei();
      generateBursts(num);
    }
  }

  private final List<Burst> finished = new ArrayList<>();

  private final Matrix2f spinMatrix = new Matrix2f();

  @Override
  protected final void render(double deltaMs) {
    setApotheneumColor(LXColor.BLACK);
    spinMatrix.rotation((float) Math.toRadians(spin.getValuef()));

    this.finished.clear();
    for (Burst burst : this.bursts) {
      burst.render(deltaMs);
      if (burst.basis >= 1) {
        this.finished.add(burst);
      }
    }
    if (!this.finished.isEmpty()) {
      this.bursts.removeAll(this.finished);
      this.finished.clear();
    }

    afterRender();
  }

  protected abstract void afterRender();

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    onBurst();
  }

}
