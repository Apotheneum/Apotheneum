package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.Tempo;
import heronarts.lx.parameter.BooleanParameter;

public class TempoTapTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;

  /**
   * Records what the modulator asks the engine to do instead of forwarding it, so the tempo
   * math here is tested in isolation from LX's. Time is driven by hand: the interval logic
   * is the whole point of the beat input, and wall-clock sleeps would make it flaky.
   */
  private static class RecordingTempoTap extends TempoTap {
    private int taps = 0;
    private long nanos = 0;
    private int beatsPerBar = 4;
    private final List<Double> bpms = new ArrayList<>();
    private final List<Integer> beats = new ArrayList<>();

    @Override
    protected void tapTempo() {
      ++this.taps;
    }

    @Override
    protected void setTempoBpm(double bpm) {
      this.bpms.add(bpm);
    }

    @Override
    protected void triggerBeat(int beatWithinPhrase, long nanoTime) {
      this.beats.add(beatWithinPhrase);
    }

    @Override
    protected int beatsPerBar() {
      return this.beatsPerBar;
    }

    @Override
    protected long nanoTime() {
      return this.nanos;
    }

    /** Advances the clock, then sends 0-indexed beat k of the phrase, in that order. */
    void beatAfterMs(double ms, int k) {
      this.nanos += (long) (ms * 1000000.);
      this.beat.setValue(k / (double) (this.bars.getValuei() * this.beatsPerBar));
    }

    double lastBpm() {
      return this.bpms.get(this.bpms.size() - 1);
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

  /** 0-indexed beats 1..count of the phrase at the given period. Starts at 1, not 0,
   * because `beat` rests at 0 and LX does not notify on a write of the value already held --
   * the downbeat after a load needs phraseReset, which theDownbeatIsZero covers. */
  private void play(double periodMs, int count) {
    for (int k = 1; k <= count; ++k) {
      this.tempoTap.beatAfterMs(periodMs, k);
    }
  }

  @Test
  void tapIsAMomentaryMappableTrigger() {
    // Both properties are what let a MIDI note bind to this and drive it as a button.
    assertEquals(BooleanParameter.Mode.MOMENTARY, this.tempoTap.tap.getMode());
    assertTrue(this.tempoTap.tap.isMappable());
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

  // ---- beat sync ----

  @Test
  void beatInputDerivesTempoFromTheIntervals() {
    // 500ms between beats is 120bpm. The first write only starts the clock -- there is no
    // interval yet -- so a tempo appears once three have accumulated.
    play(500, 4);
    assertEquals(120., this.tempoTap.lastBpm(), EPSILON);
  }

  @Test
  void tempoWaitsForEnoughIntervals() {
    play(500, 3);
    // Three writes is only two intervals: still guessing, so nothing is published.
    assertTrue(this.tempoTap.bpms.isEmpty());

    this.tempoTap.beatAfterMs(500, 4);
    assertEquals(1, this.tempoTap.bpms.size());
  }

  @Test
  void beatPositionMapsAcrossThePhrase() {
    play(500, 15);
    assertEquals(
      List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
      this.tempoTap.beats);
  }

  @Test
  void theDownbeatIsZeroNotOne() {
    // The sender counts from zero. Treating 0 as "no beat" dropped every phrase downbeat
    // and left the whole phrase one beat late -- the off-by-one this guards.
    this.tempoTap.beat.setValue(0.5);
    this.tempoTap.beats.clear();
    this.tempoTap.beat.setValue(0);
    assertEquals(List.of(0), this.tempoTap.beats);
    assertEquals(0., this.tempoTap.getValue(), EPSILON);
  }

  @Test
  void positionMatchesTheSendersBarAndBeat() {
    // Counter 3 of 16, 0-indexed, is bar 1 beat 4 -- LX bar index 0.
    this.tempoTap.beatAfterMs(500, 3);
    assertEquals(List.of(3), this.tempoTap.beats);
    assertEquals(0., this.tempoTap.getValue(), EPSILON);

    // Counter 4 crosses into bar 2.
    this.tempoTap.beatAfterMs(500, 4);
    assertEquals(1 / 3., this.tempoTap.getValue(), EPSILON);
  }

  @Test
  void aDownbeatAfterLoadNeedsThePhraseReset() {
    // beat rests at 0 and LX notifies only on an actual change, so the very first write
    // after a load, if it is the downbeat, is silently not delivered. phraseReset exists
    // to cover exactly this; nothing else can.
    this.tempoTap.beat.setValue(0);
    assertTrue(this.tempoTap.beats.isEmpty(), "a zero write onto a resting zero is silent");

    this.tempoTap.phraseReset.setValue(1);
    assertEquals(List.of(0), this.tempoTap.beats);
  }

  @Test
  void fullScaleClampsToTheLastBeatOfThePhrase() {
    // 15/16 is the real top of a 0-indexed phrase; 1.0 should not run off the end.
    this.tempoTap.beat.setValue(1.);
    assertEquals(List.of(15), this.tempoTap.beats);
  }

  @Test
  void beatPositionFollowsTimeSignature() {
    this.tempoTap.beatsPerBar = 3;
    play(500, 11);
    assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), this.tempoTap.beats);
  }

  @Test
  void repeatingAValueIsNotABeat() {
    // The documented sharp edge: a parameter set to the value it already holds notifies
    // nobody. Harmless here because positions advance, but worth pinning so nobody
    // "simplifies" the contract into a repeated pulse.
    this.tempoTap.beatAfterMs(500, 2);
    this.tempoTap.beatAfterMs(500, 2);
    assertEquals(1, this.tempoTap.beats.size());
  }

  @Test
  void aMedianIgnoresOneLatePacket() {
    // A single beat arriving late must not drag the tempo with it -- that is the whole
    // reason this uses a median rather than a mean.
    play(500, 4);
    this.tempoTap.beatAfterMs(1500, 5);
    assertEquals(120., this.tempoTap.lastBpm(), EPSILON);
  }

  @Test
  void aMeanWouldHaveDriftedOnThatLatePacket() {
    // Pins the above to the median specifically: the same input averaged is not 120.
    play(500, 4);
    this.tempoTap.beatAfterMs(1500, 5);
    final double mean = (500 + 500 + 500 + 1500) / 4.;
    assertNotEquals(60000. / mean, this.tempoTap.lastBpm(), EPSILON);
  }

  @Test
  void tempoFollowsWhenTheSourceChangesSpeed() {
    // The sliding window is the point: LX's own tap() averages since the first tap and
    // grows steadily less able to move.
    play(500, 4);
    for (int k = 5; k <= 16; ++k) {
      this.tempoTap.beatAfterMs(250, k);
    }
    assertEquals(240., this.tempoTap.lastBpm(), EPSILON);
  }

  @Test
  void aLongGapStartsTheWindowOver() {
    play(500, 4);
    this.tempoTap.bpms.clear();

    // Slower than MIN_BPM means the source stopped rather than slowed. Resuming must not
    // average the silence in, so it takes a fresh three intervals to publish again.
    this.tempoTap.beatAfterMs(10000, 5);
    this.tempoTap.beatAfterMs(500, 6);
    this.tempoTap.beatAfterMs(500, 7);
    assertTrue(this.tempoTap.bpms.isEmpty());

    this.tempoTap.beatAfterMs(500, 8);
    assertEquals(120., this.tempoTap.lastBpm(), EPSILON);
  }

  @Test
  void phaseStillAlignsAcrossAGap() {
    // The tempo window is stale after a gap, but the position in hand is not.
    play(500, 4);
    this.tempoTap.beats.clear();
    this.tempoTap.beatAfterMs(10000, 9);
    assertEquals(List.of(9), this.tempoTap.beats);
  }

  @Test
  void syncOffLeavesTheTempoAlone() {
    this.tempoTap.sync.setValue(false);
    play(500, 4);
    assertTrue(this.tempoTap.bpms.isEmpty());
    assertTrue(this.tempoTap.beats.isEmpty());
  }

  @Test
  void reenablingSyncStartsTheWindowOver() {
    play(500, 4);
    this.tempoTap.sync.setValue(false);
    this.tempoTap.sync.setValue(true);
    this.tempoTap.bpms.clear();

    this.tempoTap.beatAfterMs(500, 5);
    this.tempoTap.beatAfterMs(500, 6);
    assertTrue(this.tempoTap.bpms.isEmpty());
  }

  @Test
  void aHeldCounterWrapsWithoutLosingTempo() {
    play(500, 15);
    this.tempoTap.beatAfterMs(500, 0);
    assertEquals(0, this.tempoTap.beats.get(this.tempoTap.beats.size() - 1));
    assertEquals(120., this.tempoTap.lastBpm(), EPSILON);
  }

  @Test
  void phraseResetJumpsToTheDownbeat() {
    play(500, 6);
    this.tempoTap.beats.clear();
    this.tempoTap.phraseReset.setValue(1);
    assertEquals(List.of(0), this.tempoTap.beats, "reset jumps to the phrase downbeat");
    assertEquals(0., this.tempoTap.getValue(), EPSILON, "and back to the first bar");
  }

  @Test
  void theFallBackToRestIsNotASecondReset() {
    play(500, 6);
    this.tempoTap.beats.clear();
    this.tempoTap.phraseReset.setValue(1);
    this.tempoTap.phraseReset.setValue(0);
    assertEquals(1, this.tempoTap.beats.size(), "only the rising edge resets");
  }

  @Test
  void phraseResetDoesNotDisturbTheTempoWindow() {
    // Regression guard: wiping the window on every phrase left the tempo limping along on
    // whatever pairs survived, and the phrase never locked.
    play(500, 4);
    this.tempoTap.bpms.clear();
    this.tempoTap.nanos += 120000000L;
    this.tempoTap.phraseReset.setValue(1);
    this.tempoTap.phraseReset.setValue(0);
    this.tempoTap.beatAfterMs(380, 2);
    assertEquals(120., this.tempoTap.lastBpm(), EPSILON);
  }

  @Test
  void syncOffIgnoresThePhraseReset() {
    play(500, 4);
    this.tempoTap.sync.setValue(false);
    this.tempoTap.beats.clear();
    this.tempoTap.phraseReset.setValue(1);
    assertTrue(this.tempoTap.beats.isEmpty());
  }

  @Test
  void tappingStillWorksWhileSyncing() {
    // The two inputs are independent; adding beat sync must not have cost us the button.
    play(500, 4);
    this.tempoTap.tap.trigger();
    assertEquals(1, this.tempoTap.taps);
  }

  // ---- bar output ----

  @Test
  void valueTracksWhichBarOfThePhraseIsPlaying() {
    // Spread across the full 0-1 range so it can drive a selector directly.
    this.tempoTap.beatAfterMs(500, 1);
    assertEquals(0., this.tempoTap.getValue(), EPSILON);
    this.tempoTap.beatAfterMs(500, 4);
    assertEquals(1 / 3., this.tempoTap.getValue(), EPSILON);
    this.tempoTap.beatAfterMs(500, 8);
    assertEquals(2 / 3., this.tempoTap.getValue(), EPSILON);
    this.tempoTap.beatAfterMs(500, 12);
    assertEquals(1., this.tempoTap.getValue(), EPSILON);
  }

  @Test
  void theBarValueHoldsBetweenBars() {
    play(500, 7);
    // 0-indexed beats 4-7 are all bar 2; the value must not creep beat by beat.
    assertEquals(1 / 3., this.tempoTap.getValue(), EPSILON);
  }

  @Test
  void barTriggerFiresOncePerBar() {
    final int[] fired = { 0 };
    this.tempoTap.barTrigger.addListener(p -> {
      if (((BooleanParameter) p).isOn()) {
        ++fired[0];
      }
    });
    play(500, 15);
    assertEquals(4, fired[0]);
  }

  @Test
  void isAModulationSourceSoTheBarCanDriveTheRig() {
    assertTrue(this.tempoTap.isMappingSource());
  }

  @Test
  void beatIsAModulationTarget() {
    assertTrue(this.tempoTap.beat.isMappable());
  }

  @Test
  void aStoppedModulatorStillTracksTheBar() {
    // Modulators can sit stopped on this rig, and a stopped one never receives loop(), so
    // the value has to be applied at beat time rather than computed in the run loop.
    assertFalse(this.tempoTap.isRunning());
    play(500, 4);
    assertEquals(1 / 3., this.tempoTap.getValue(), EPSILON);
  }

}
