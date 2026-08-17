package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.Tempo;
import heronarts.lx.parameter.BooleanParameter;

public class TempoTapTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;

  /** Counts taps instead of forwarding them, so the tempo averaging in LX stays out of scope. */
  private static class RecordingTempoTap extends TempoTap {
    private int taps = 0;

    @Override
    protected void tapTempo() {
      ++this.taps;
    }
  }

  private RecordingTempoTap tempoTap;

  @BeforeEach
  void setUp() {
    // No LX here: every test below this line works on a standalone modulator. Only
    // tapReachesTempoOnceAddedToEngine needs an engine, and builds its own — each
    // `new LX(...)` starts audio and MIDI device-scan threads, which is the expensive
    // and deadlock-prone part of the fixture.
    this.tempoTap = new RecordingTempoTap();
  }

  @Test
  void tapIsAMomentaryMappableTrigger() {
    // Both properties are what let a MIDI note bind to this and drive it as a button.
    assertEquals(BooleanParameter.Mode.MOMENTARY, this.tempoTap.tap.getMode());
    assertTrue(this.tempoTap.tap.isMappable());
  }

  @Test
  void isNotAModulationSource() {
    assertFalse(this.tempoTap.isMappingSource());
  }

  @Test
  void eachTriggerTapsOnce() {
    this.tempoTap.tap.trigger();
    assertEquals(1, this.tempoTap.taps);
  }

  @Test
  void everyPressTaps() {
    for (int i = 0; i < 4; ++i) {
      this.tempoTap.tap.trigger();
    }
    assertEquals(4, this.tempoTap.taps);
  }

  @Test
  void releasingDoesNotTapAgain() {
    // A mapped MIDI note drives both edges; only the press should count.
    this.tempoTap.tap.setValue(true);
    this.tempoTap.tap.setValue(false);
    assertEquals(1, this.tempoTap.taps);
  }

  @Test
  void loopingDoesNotTapOnItsOwn() {
    for (int i = 0; i < 100; ++i) {
      this.tempoTap.loop(1000. / 60.);
    }
    assertEquals(0, this.tempoTap.taps);
    assertEquals(0, this.tempoTap.getValue(), EPSILON);
  }

  @Test
  void tapReachesTempoOnceAddedToEngine() {
    // The only test that runs the real tapTempo(), so it is the only thing standing
    // between a stubbed-out body and a modulator that silently does nothing. It also
    // covers the lx == null NPE a standalone instance would hit: TempoTap resolves the
    // tempo through this.lx, which LXComponent only populates in setParent.
    final LX lx = newHeadlessLx();
    final TempoTap added = lx.engine.modulation.addModulator(new TempoTap());
    assertEquals(Tempo.DEFAULT_BPM, lx.engine.tempo.bpm.getValue(), EPSILON);

    // Tempo.tap() only computes a tempo from the fourth tap on. Back-to-back taps imply
    // a near-zero beat period, so the computed BPM saturates at the parameter's max --
    // an exact expected value, with no dependence on sleep timing.
    for (int i = 0; i < 4; ++i) {
      added.tap.trigger();
    }
    assertEquals(Tempo.MAX_BPM, lx.engine.tempo.bpm.getValue(), EPSILON);
  }

}
