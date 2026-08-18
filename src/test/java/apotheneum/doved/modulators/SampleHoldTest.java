package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulator.SinLFO;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;

public class SampleHoldTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;

  private LX lx;
  private SampleHold sampleHold;
  private AtomicInteger fires;

  @BeforeEach
  void setUp() {
    this.lx = newHeadlessLx();
    // Register with the modulation engine: this is what assigns a component id, and it is
    // the state a modulator is actually in when a show drives it.
    this.sampleHold = this.lx.engine.modulation.addModulator(new SampleHold());
    this.fires = new AtomicInteger();
    final LXParameterListener countFires = (LXParameter p) -> {
      if (this.sampleHold.triggerOut.isOn()) {
        this.fires.incrementAndGet();
      }
    };
    this.sampleHold.triggerOut.addListener(countFires);
  }

  @Test
  void triggerCapturesTheCurrentSignal() {
    this.sampleHold.signal.setValue(0.75);
    this.sampleHold.sample.trigger();
    assertEquals(0.75, this.sampleHold.getValue(), EPSILON);
  }

  /** The whole point: the signal moving is not enough — only a trigger moves the output. */
  @Test
  void signalChangesDoNotReachTheOutputUntilTriggered() {
    this.sampleHold.signal.setValue(0.25);
    this.sampleHold.sample.trigger();
    assertEquals(0.25, this.sampleHold.getValue(), EPSILON);

    this.sampleHold.signal.setValue(0.9);
    assertEquals(0.25, this.sampleHold.getValue(), EPSILON, "output must hold until the next trigger");

    this.sampleHold.sample.trigger();
    assertEquals(0.9, this.sampleHold.getValue(), EPSILON);
  }

  /** A held sample survives engine frames — loop() must not drift or decay it. */
  @Test
  void heldValueSurvivesEngineFrames() {
    this.sampleHold.signal.setValue(0.4);
    this.sampleHold.sample.trigger();

    this.sampleHold.start();
    this.sampleHold.signal.setValue(0.8);
    for (int i = 0; i < 100; ++i) {
      this.lx.engine.run();
    }
    assertEquals(0.4, this.sampleHold.getValue(), EPSILON);
  }

  /** Sampling must not depend on the modulator being started. */
  @Test
  void samplesWhileStopped() {
    assertFalse(this.sampleHold.isRunning());
    this.sampleHold.signal.setValue(0.6);
    this.sampleHold.sample.trigger();
    assertEquals(0.6, this.sampleHold.getValue(), EPSILON);
  }

  @Test
  void eachTriggerResamples() {
    this.sampleHold.signal.setValue(0.1);
    this.sampleHold.sample.trigger();
    this.sampleHold.signal.setValue(0.2);
    this.sampleHold.sample.trigger();
    this.sampleHold.signal.setValue(0.3);
    this.sampleHold.sample.trigger();
    assertEquals(0.3, this.sampleHold.getValue(), EPSILON);
    assertEquals(3, this.fires.get(), "each sample should emit a trigger downstream");
  }

  @Test
  void resamplingTheSameValueStillFires() {
    this.sampleHold.signal.setValue(0.5);
    this.sampleHold.sample.trigger();
    this.sampleHold.sample.trigger();
    assertEquals(2, this.fires.get());
    assertEquals(0.5, this.sampleHold.getValue(), EPSILON);
  }

  @Test
  void startsAtZeroBeforeAnyTrigger() {
    assertEquals(0, this.sampleHold.getValue(), EPSILON);
    assertEquals(0, this.fires.get());
  }

  /** It has to be usable as a modulation source, which is the entire delivery mechanism. */
  @Test
  void normalizedOutputTracksTheHeldSample() {
    this.sampleHold.signal.setValue(0.42);
    this.sampleHold.sample.trigger();
    assertEquals(0.42, this.sampleHold.getNormalized(), EPSILON);
  }

  /** The value comes from the signal input; writing it directly would desync the hold. */
  @Test
  void normalizedCannotBeSetDirectly() {
    assertThrows(UnsupportedOperationException.class, () -> this.sampleHold.setNormalized(0.5));
  }

  @Test
  void triggerOutIsTheTriggerSource() {
    assertEquals(this.sampleHold.triggerOut, this.sampleHold.getTriggerSource());
  }

  /**
   * Modulators have no UIDeviceControls.Default equivalent to fall back on, so one that
   * doesn't implement UIModulatorControls renders a placeholder and logs "No UI
   * implementation found" — at runtime, with nothing failing at build time.
   */
  @Test
  void providesItsOwnModulatorUI() {
    assertTrue(this.sampleHold instanceof UIModulatorControls);
  }

  /**
   * The one that matters: an actual modulation routed into signal, through the real
   * modulation engine, sampled across real engine frames. Setting signal directly with
   * setValue (as the tests above do) exercises the knob path, not the path a show uses —
   * and "you can map an LFO into this" is the entire premise of the modulator.
   */
  @Test
  void samplesAValueDrivenByARealModulation() throws Exception {
    final SinLFO lfo = this.lx.engine.modulation.addModulator(new SinLFO(0, 1, 1000));
    lfo.start();
    this.lx.engine.modulation.addModulation(
      new LXCompoundModulation(this.lx.engine.modulation, lfo, this.sampleHold.signal));
    // A modulation applies its source scaled by its range, which defaults to zero.
    this.lx.engine.modulation.modulations.get(0).range.setValue(1);

    // Let the LFO climb away from zero, then capture wherever it happens to be.
    for (int i = 0; i < 5; ++i) {
      this.lx.engine.run();
    }
    this.sampleHold.sample.trigger();
    final double firstSample = this.sampleHold.getValue();
    assertTrue(firstSample > 0, "LFO should have moved the modulated signal off zero");

    // Now let it keep moving with no trigger: the output must not follow.
    for (int i = 0; i < 20; ++i) {
      this.lx.engine.run();
    }
    assertEquals(firstSample, this.sampleHold.getValue(), EPSILON,
      "output must hold while the modulated signal keeps moving");

    // And a second trigger must pick up the signal's new position.
    this.sampleHold.sample.trigger();
    assertNotEquals(firstSample, this.sampleHold.getValue(),
      "a later trigger should capture the signal's new value");
  }

  /**
   * The held value is what downstream modulation reads, and LXModulator.value is a plain
   * field that LXComponent.save() never writes — so without explicit serialization, reopening
   * a project drops the output to zero until someone triggers again.
   */
  @Test
  void heldValueSurvivesSaveAndLoad() {
    this.sampleHold.signal.setValue(0.42);
    this.sampleHold.sample.trigger();
    assertEquals(0.42, this.sampleHold.getValue(), EPSILON);

    final JsonObject saved = new JsonObject();
    this.sampleHold.save(this.lx, saved);

    // A second LX, not a second modulator in the same one: loading a saved component into a
    // live sibling collides on component id.
    final LX reopened = newHeadlessLx();
    final SampleHold restored = reopened.engine.modulation.addModulator(new SampleHold());
    final AtomicInteger restoredFires = new AtomicInteger();
    restored.triggerOut.addListener(p -> {
      if (restored.triggerOut.isOn()) {
        restoredFires.incrementAndGet();
      }
    });
    restored.load(reopened, saved);

    assertEquals(0.42, restored.getValue(), EPSILON, "held sample must survive a project reload");
    assertEquals(0, restoredFires.get(), "reopening a project is not a sample event");
  }

  /** Holding must continue from the restored value, not from a stale in-memory one. */
  @Test
  void restoredValueStillHoldsUntilTheNextTrigger() {
    this.sampleHold.signal.setValue(0.6);
    this.sampleHold.sample.trigger();
    final JsonObject saved = new JsonObject();
    this.sampleHold.save(this.lx, saved);

    final LX reopened = newHeadlessLx();
    final SampleHold restored = reopened.engine.modulation.addModulator(new SampleHold());
    restored.load(reopened, saved);

    restored.signal.setValue(0.1);
    assertEquals(0.6, restored.getValue(), EPSILON, "signal alone must not move a restored hold");
    restored.sample.trigger();
    assertEquals(0.1, restored.getValue(), EPSILON);
  }

  /** An empty object is how LX resets a component to defaults; it must not keep the old hold. */
  @Test
  void loadingAnObjectWithoutTheKeyResetsTheHold() {
    this.sampleHold.signal.setValue(0.8);
    this.sampleHold.sample.trigger();
    assertEquals(0.8, this.sampleHold.getValue(), EPSILON);

    this.sampleHold.load(this.lx, new JsonObject());
    assertEquals(0, this.sampleHold.getValue(), EPSILON);
  }

}
