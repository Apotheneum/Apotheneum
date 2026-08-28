package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonObject;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXParameterModulation.ModulationException;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.pattern.LXPattern;

/**
 * Pins the behaviour {@link ModGradient} and {@link apotheneum.doved.effects.ModColorize}
 * exist for: a shadow parameter that forwards to an inherited one <em>under modulation</em>.
 *
 * <p>The distinction these assert is the whole reason write-through runs per frame rather than
 * from a parameter listener, and it is not visible from the shadow parameter alone. A listener
 * fires on base-value changes; modulation never touches the base, it contributes to the
 * effective value. So a listener-based implementation looks correct in every test that sets a
 * value by hand and is silently dead in exactly the case the class was written for. That
 * regression shipped once and was caught on the live rig — {@code Stop} reading 3 while the
 * inherited {@code Index} sat at 1 — which is late, manual, and easy to miss.
 *
 * <p>{@link #everyShadowForwardsNotJustTheFirstOne()} pins the same thing for the other three
 * shadows. They were added to the class without being added to {@code writeThrough()}, so they
 * were inert on arrival; that too was caught by review rather than by a test.
 *
 * <p><b>Only the first test here may render, and the order is pinned for that reason.</b>
 * {@code LXPoint.index} comes from a JVM-global static counter with no reset, while a
 * pattern's {@code colors} buffer is sized to its own model — so only the first model built
 * in a surefire fork has indices that fit its own buffer. A second rendering model throws
 * {@code ArrayIndexOutOfBoundsException} inside {@code GradientPattern.run}, and
 * {@code LXEngine.run()} <em>swallows</em> that, sets its {@code runFailed} flag, and turns
 * every later {@code engine.run()} into a silent no-op — so the symptom is not an error but
 * assertions that quietly read stale values. The class-level fix is {@code reuseForks=false};
 * within a class, keep rendering to one test. The load test below never renders, so its own
 * LX is harmless.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ModGradientWriteThroughTest extends HeadlessLxTest {

  @Test
  @Order(1)
  void writeThroughForwardsEveryShadowIncludingUnderModulation() throws ModulationException {
    final LX lx = newHeadlessLx();
    final ModGradient pattern = new ModGradient(lx);
    final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { pattern });
    channel.fader.setValue(1);
    lx.engine.setFixedDeltaMs(16);

    // The constructor calls writeThrough(), so a freshly added device is consistent before
    // its first frame rather than wrong for one. stops defaults to 2, not stock's MAX_COLORS.
    assertEquals(1, pattern.engine.paletteIndex.getValuei());
    assertEquals(2, pattern.engine.paletteStops.getValuei());

    // All four shadows forward, not just stop. The other three were added to the class
    // without being added to writeThrough() and shipped inert; review caught that, not a test.
    pattern.stop.setValue(2);
    pattern.stops.setValue(4);
    pattern.invert.setValue(1);
    pattern.spin.setValue(1);
    lx.engine.run();
    assertEquals(2, pattern.engine.paletteIndex.getValuei());
    assertEquals(4, pattern.engine.paletteStops.getValuei());
    assertTrue(pattern.engine.gradientInvert.isOn());
    assertTrue(pattern.engine.rotate.isOn());

    // ...and forward the off state too, so they are not one-way latches.
    pattern.invert.setValue(0);
    pattern.spin.setValue(0);
    lx.engine.run();
    assertFalse(pattern.engine.gradientInvert.isOn());
    assertFalse(pattern.engine.rotate.isOn());

    // The case the class exists for. A listener on `stop` fires on base-value changes only;
    // modulation leaves the base alone and contributes to the effective value, so a
    // listener-based write-through is silently dead here while looking correct everywhere else.
    pattern.stop.setValue(1);
    final FixedSource source = new FixedSource();
    lx.engine.modulation.addModulator(source);
    final LXCompoundModulation modulation =
      new LXCompoundModulation(lx.engine.modulation, source, pattern.stop);
    lx.engine.modulation.addModulation(modulation);
    // stop spans 1..5, so half depth at a full source is two stops: base 1 -> effective 3.
    modulation.range.setValue(.5);
    source.set(1);
    lx.engine.run();

    assertEquals(3, pattern.stop.getValuei());
    assertEquals(3, pattern.engine.paletteIndex.getValuei(),
      "modulation must reach the inherited parameter; this is the case a listener misses");
    assertEquals(1, (int) pattern.stop.getBaseValue(), "the knob itself must not have moved");

    source.set(0);
    lx.engine.run();
    assertEquals(1, pattern.engine.paletteIndex.getValuei());
  }

  /**
   * A modulation source held at whatever a test sets, mirroring {@code SelectorTest}'s. Stock
   * modulators either move on their own or need a clock; here the source value is a fixture,
   * so it has to hold still between assertions.
   */
  private static class FixedSource extends LXModulator implements LXNormalizedParameter {

    private FixedSource() {
      super("Fixed");
    }

    private void set(double value) {
      setValue(value);
    }

    @Override
    protected double computeValue(double deltaMs) {
      return getValue();
    }

    @Override
    public LXNormalizedParameter setNormalized(double value) {
      setValue(value);
      return this;
    }

    @Override
    public double getNormalized() {
      return getValue();
    }
  }

}
