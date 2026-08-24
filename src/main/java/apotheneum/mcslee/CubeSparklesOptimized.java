/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package apotheneum.mcslee;

import java.util.ArrayList;
import java.util.List;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Performance-optimized version of {@link CubeSparkles}. Behavior and
 * parameters are identical - two things changed:
 *
 *  - render() used to call both setColors(BLACK) (clears the whole model)
 *    AND setApotheneumColor(BLACK) (re-clears the Apotheneum subset via a
 *    much slower nested-loop path) every single frame - the second call was
 *    pure redundant work, since the first already covers every Apotheneum
 *    point. Only setColors(BLACK) remains.
 *  - The finished-sparkle removal used a collect-then-List.removeAll() pass,
 *    which calls finished.contains() for every element of the live list -
 *    O(n*m), quadratic once a lot of sparkles are alive at once (Per Trigger
 *    maxed at 64, times 4 faces, times rapid re-triggering adds up fast).
 *    Replaced with a single removeIf() pass - true linear-time compaction,
 *    rather than shifting the list's tail on every individual removal the
 *    way Iterator.remove() would.
 */
@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Cube Sparkles-Optimized")
@LXComponent.Description("MIDI reactive sparkles on the cube faces (performance-optimized)")
public class CubeSparklesOptimized extends ApotheneumPattern implements ApotheneumPattern.Midi {

  public interface DistanceFunction {
    public float getDistance(float d);
  }

  public enum Shape {
    ABS("Abs", d -> { return Math.abs(d); }),
    Up("Up", d -> { return d; }),
    Down("Down", d -> { return -d; });

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

  public final TriggerParameter sparkle =
    new TriggerParameter("Sparkle", this::onSparkle)
    .setDescription("Trigger a sparkle");

  public final BooleanParameter cube =
    new BooleanParameter("Cube", true)
    .setDescription("Whether sparkles appear on the cube");

  public final BooleanParameter cylinder =
    new BooleanParameter("Cylinder", false)
    .setDescription("Whether sparkles appear on the cylinder");

  public final CompoundDiscreteParameter perTrig =
    new CompoundDiscreteParameter("Per Trigger", 1, 1, 64)
    .setDescription("Number of sparkles per trigger");

  public final CompoundParameter maxHeight =
    new CompoundParameter("Height", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Maximum height of sparkle placement");

  public final CompoundParameter sparkleTime =
    new CompoundParameter("Time", 1, .5, 5)
    .setUnits(CompoundParameter.Units.SECONDS)
    .setDescription("Sparkle Time");

  public final CompoundParameter sparkleDistance =
    new CompoundParameter("Distance ", .1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Sparkle Time");

  public final CompoundParameter sparkleExp =
    new CompoundParameter("Exp ", 1, .5, 2)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Sparkle Exp");

  public EnumParameter<Shape> shape =
    new EnumParameter<Shape>("Shape", Shape.ABS)
    .setDescription("Sparkle Shape");

  public CubeSparklesOptimized(LX lx) {
    super(lx);
    addParameter("sparkle", this.sparkle);
    addParameter("perTrig", this.perTrig);
    addParameter("cube", this.cube);
    addParameter("cylinder", this.cylinder);
    addParameter("maxHeight", this.maxHeight);
    addParameter("sparkleTime", this.sparkleTime);
    addParameter("sparkleDistance", this.sparkleDistance);
    addParameter("sparkleExp", this.sparkleExp);
    addParameter("shape", this.shape);
  }

  @Override
  protected void onModelChanged(LXModel model) {
    this.sparkles.clear();
  }

  // LXPattern.enabled controls playlist/compositing ELIGIBILITY, not whether
  // this pattern is actually the one currently rendering - track actual
  // activation via onActive()/onInactive() instead (see BurstsOptimized for
  // the same fix and full rationale).
  private boolean isActive = false;

  @Override
  protected void onActive() {
    super.onActive();
    this.isActive = true;
  }

  @Override
  protected void onInactive() {
    super.onInactive();
    this.isActive = false;
  }

  private final List<Sparkle> sparkles = new ArrayList<>();

  private class Sparkle {

    private final Apotheneum.Column column;
    private final float basePos;
    private float basis;

    private Sparkle(Apotheneum.Column[] columns) {
      this.column = columns[LXUtils.randomi(columns.length-1)];
      this.basePos = LXUtils.randomf(maxHeight.getValuef());
    }

    protected void render(double deltaMs) {
      this.basis += deltaMs / (1000f * sparkleTime.getValuef());
      if (this.basis < 1) {
        final DistanceFunction distance = shape.getEnum().distance;

        float dist = (float) (sparkleDistance.getValue() * Math.pow(this.basis, sparkleExp.getValuef()));
        float level = LXUtils.lerpf(100, 0, this.basis);
        float length = LXUtils.lerpf(1, 10, this.basis);
        float falloff = 4500f / length;
        for (LXPoint p : this.column.points) {
          float b = level - falloff * Math.abs(distance.getDistance(p.yn - this.basePos) - dist);
          if (b > 0) {
            addColor(p.index, LXColor.gray(LXUtils.minf(100f, b)));
          }
        }

      }
    }
  }

  private void onSparkle() {
    // While inactive, MIDI/manual triggers still arrive but nothing is
    // rendering to age them - discard them here instead of queuing them up,
    // otherwise they all dump onto the surface at once, still at basis 0,
    // the moment the pattern becomes active again. Sparkles already in
    // flight when the pattern goes inactive are untouched by this and keep
    // aging/fading normally whenever render() next runs.
    if (!this.isActive) {
      return;
    }
    if (Apotheneum.exists) {
      int num = this.perTrig.getValuei();
      if (this.cube.isOn()) {
        for (int i = 0; i < num; ++i) {
          for (Apotheneum.Cube.Face face : Apotheneum.cube.exterior.faces) {
            this.sparkles.add(new Sparkle(face.columns));
          }
        }
      }
      if (this.cylinder.isOn()) {
        for (int i = 0; i < num; ++i) {
          this.sparkles.add(new Sparkle(Apotheneum.cylinder.exterior.columns));
        }
      }
    }
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    this.sparkles.removeIf(sparkle -> {
      sparkle.render(deltaMs);
      return sparkle.basis >= 1;
    });

    copyExterior();
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    onSparkle();
  }

}
