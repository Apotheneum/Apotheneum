package apotheneum.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.pattern.LXPattern;
import org.junit.jupiter.api.Test;

public class RenderSpikeTest extends HeadlessLxTest {

  @Test
  void paletteComponentsMustBeFiniteAndInRange() {
    assertEquals(192, RenderSpike.parsePaletteComponent("192,95,75", "192", 360));
    assertThrows(
      IllegalArgumentException.class,
      () -> RenderSpike.parsePaletteComponent("NaN,95,75", "NaN", 360)
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> RenderSpike.parsePaletteComponent("Infinity,95,75", "Infinity", 360)
    );
  }

  @Test
  void modulationUsesARealCompoundParameterTarget() throws Exception {
    final LX lx = newHeadlessLx();
    final ModulationTestPattern pattern = new ModulationTestPattern(lx);
    lx.engine.mixer.addChannel(new LXPattern[] { pattern });

    assertSame(pattern.position, RenderSpike.resolveModulationTarget(pattern, "position"));
    RenderSpike.applyModulation(lx, pattern, "position:1");
    assertEquals(1, pattern.position.getModulations().size());

    lx.engine.setFixedDeltaMs(500);
    lx.engine.run();
    assertTrue(pattern.position.getValue() > 0, "the registered SawLFO drives the target");
  }

  @Test
  void modulationRejectsUnknownAndNonModulatableParameters() {
    final ModulationTestPattern pattern = new ModulationTestPattern(newHeadlessLx());

    assertThrows(
      IllegalArgumentException.class,
      () -> RenderSpike.resolveModulationTarget(pattern, "missing")
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> RenderSpike.resolveModulationTarget(pattern, "mode")
    );
  }

  @Test
  void modulationSweepsTheFullRangeOfABipolarTarget() throws Exception {
    final LX lx = newHeadlessLx();
    final ModulationTestPattern pattern = new ModulationTestPattern(lx);
    lx.engine.mixer.addChannel(new LXPattern[] { pattern });

    RenderSpike.applyModulation(lx, pattern, "bipolar:1");
    assertEquals(
      -1, pattern.bipolar.getValue(), 1e-9,
      "the sweep starts at the target's minimum, not at the numeric value zero");

    lx.engine.setFixedDeltaMs(50);
    double minimum = pattern.bipolar.getValue();
    double maximum = pattern.bipolar.getValue();
    for (int frame = 0; frame < 19; ++frame) {
      lx.engine.run();
      minimum = Math.min(minimum, pattern.bipolar.getValue());
      maximum = Math.max(maximum, pattern.bipolar.getValue());
    }
    assertEquals(-1, minimum, 1e-9, "the sweep reaches the target's minimum");
    assertTrue(maximum > 0.9, "the sweep reaches the target's maximum, got " + maximum);
  }

  @Test
  void everyRenderVariantReplaysTheSameParameterSweep() throws Exception {
    final LX lx = newHeadlessLx();
    final ModulationTestPattern pattern = new ModulationTestPattern(lx);
    lx.engine.mixer.addChannel(new LXPattern[] { pattern });

    final SawLFO saw = RenderSpike.applyModulation(lx, pattern, "position:1");
    lx.engine.setFixedDeltaMs(100);

    final double[] bypass = sweep(lx, pattern, 6);
    // A second variant that simply kept running would resume mid-cycle; RenderSpike restarts
    // the basis so the effects render covers the identical parameter sequence.
    saw.setBasis(0);
    assertArrayEquals(bypass, sweep(lx, pattern, 6), 1e-9);
  }

  private static double[] sweep(LX lx, ModulationTestPattern pattern, int frames) {
    final double[] values = new double[frames];
    for (int frame = 0; frame < frames; ++frame) {
      lx.engine.run();
      values[frame] = pattern.position.getValue();
    }
    return values;
  }

  private static class ModulationTestPattern extends LXPattern {

    private final CompoundParameter position = new CompoundParameter("Position", 0);
    private final CompoundParameter bipolar = new CompoundParameter("Bipolar", -1, -1, 1);
    private final DiscreteParameter mode = new DiscreteParameter("Mode", 0, 2);

    private ModulationTestPattern(LX lx) {
      super(lx);
      addParameter("position", this.position);
      addParameter("bipolar", this.bipolar);
      addParameter("mode", this.mode);
    }

    @Override
    protected void run(double deltaMs) {
    }
  }
}
