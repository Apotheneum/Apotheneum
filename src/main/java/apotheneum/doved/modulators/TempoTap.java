package apotheneum.doved.modulators;

import java.util.Arrays;

import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LXCategory;
import heronarts.lx.Tempo;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;

/**
 * A bindable trigger button that taps the master tempo, and an OSC beat input that syncs
 * the master tempo to an external sequencer.
 *
 * <p><b>Tap.</b> Chromatik's own TAP button is not a mappable target, and tap tempo is
 * otherwise only reachable from a dedicated control surface. Map a MIDI note from any
 * momentary button onto {@link #tap} and each press taps the tempo, exactly as clicking TAP
 * by hand does.
 *
 * <p><b>Beat sync.</b> Write the beat's position within the phrase to {@link #beat} once
 * per beat and this modulator derives the tempo from the timing between writes and locks
 * the beat count to the value. Every LX parameter is normalized over OSC by default, so the
 * whole contract is one 0-1 float per beat:
 *
 * <pre>
 *   /lx/modulation/&lt;label&gt;/beat          k / (bars * beatsPerBar), k from 0
 *   /lx/modulation/&lt;label&gt;/phraseReset   pulse &gt; 0 to jump to beat 1 of bar 1
 * </pre>
 *
 * <p>At the default 4 {@link #bars} of 4/4 that is sixteen steps, <b>counted from zero</b>
 * to match the sender: beat k of the phrase is sent as k/16 for k in 0..15, so the phrase
 * runs 0, .0625, .125, ... .9375 and repeats. Nothing else needs to
 * be sent: no BPM, no clock-source change.
 *
 * <p>The position is <em>absolute</em> within the phrase rather than a bare pulse, which is
 * what makes this self-healing — a dropped packet corrects itself on the very next beat.
 * A pulse-counting scheme cannot recover: one lost packet shifts the downbeat permanently
 * with nothing to detect it by.
 *
 * <p>Note that k starts at 1, so 0 is never a beat. That is not cosmetic. LX parameters
 * notify listeners only when the value actually changes, so a write of the value already
 * held is silent -- and a scheme sending 0 for the downbeat would drop the first beat after
 * every load, invisibly. Keeping 0 as the resting value means every real beat is a change.
 *
 * <p>The cost of locking to a phrase is that the beat count wraps, so nothing with a cycle
 * longer than the phrase can complete, and the transport jumps rather than slews once per
 * phrase. Both are deliberate: this exists to sync a show to a sequencer's phrase, and the
 * jump lands on a phrase boundary where everything shorter re-phases identically anyway.
 *
 * <p>This deliberately does <em>not</em> go through {@link Tempo.ClockSource#OSC}. It calls
 * {@link Tempo#setBpm(double)} and {@link Tempo#triggerBeatWithinBar(int, long)} directly,
 * which carry no clock-source gate, so the engine stays on the internal clock. That is the
 * whole point: under an external clock source a sequencer that stops sending freezes the
 * metronome, whereas here it simply free-runs on at the last derived tempo. A show that
 * loses its clock feed keeps moving instead of going still, and {@link #tap} and the UI
 * remain live as a manual fallback the entire time.
 *
 * <p><b>Bar output.</b> The modulator's own value is which bar of the phrase is currently
 * playing, spread across 0-1 — at the default 4 bars that is 0, 1/3, 2/3, 1 — so it can be
 * mapped straight onto a selector, a stop, or anything else that wants to change once a bar.
 * {@link #barTrigger} fires on each new bar, and phrase start is simply bar 1. The value is
 * applied the instant a beat arrives rather than in {@link #loop(double)}, so it still
 * tracks when the modulator is stopped.
 *
 * <p>The tempo is derived from the <em>median</em> of the last {@link #window} intervals
 * rather than a mean since the first beat. The median discards a late or duplicated packet
 * outright instead of averaging it in, and the sliding window keeps following the source
 * when it changes tempo — LX's own {@link Tempo#tap()} averages over every tap since the
 * sequence began, which grows steadily more immovable the longer it runs.
 */
@LXModulator.Global("Tempo Tap")
@LXModulator.Device("Tempo Tap")
@LXCategory(LXCategory.TRIGGER)
public class TempoTap extends LXModulator implements LXOscComponent, UIModulatorControls<TempoTap> {

  private static final double MS_PER_MINUTE = 60000;
  private static final long NANOS_PER_MS = 1000000L;

  /**
   * Intervals outside the tempo range LX can represent are not tempo information. Anything
   * faster than {@link Tempo#MAX_BPM} is a duplicated or bunched packet; anything slower
   * than {@link Tempo#MIN_BPM} is a gap, and a gap means the source stopped and restarted,
   * so the window is stale and gets dropped rather than blended across the silence.
   */
  private static final long MIN_INTERVAL_NANOS = (long) (MS_PER_MINUTE / Tempo.MAX_BPM) * NANOS_PER_MS;
  private static final long MAX_INTERVAL_NANOS = (long) (MS_PER_MINUTE / Tempo.MIN_BPM) * NANOS_PER_MS;

  /** Intervals needed before a tempo is published, so a couple of beats settle it first. */
  private static final int MIN_INTERVALS = 3;

  public final TriggerParameter tap =
    new TriggerParameter("Tap", () -> tapTempo())
    .setDescription("Tap repeatedly to set the master tempo");

  public final BoundedParameter beat =
    new BoundedParameter("Beat", 0)
    .setDescription("Beat within the phrase, 0-1; write once per beat to sync the tempo");

  /**
   * Pulse above zero to jump to beat 1 of bar 1. A dedicated input rather than overloading
   * a zero on {@link #beat}: a sender that returns to rest between beats writes zero
   * constantly, and a reset on that would snap the phrase to its downbeat every beat.
   * Separating them means {@link #beat} carries position only and this carries the
   * phrase boundary only, which is also the shape the reset gate arrives in.
   */
  public final BoundedParameter phraseReset =
    new BoundedParameter("Reset", 0)
    .setDescription("Pulse above zero to jump to beat 1 of bar 1");

  public final BooleanParameter sync =
    new BooleanParameter("Sync", true)
    .setDescription("Whether the beat input drives the master tempo");

  public final DiscreteParameter window =
    new DiscreteParameter("Window", 8, MIN_INTERVALS + 1, 33)
    .setDescription("How many recent beat intervals the tempo is derived from");

  public final DiscreteParameter bars =
    new DiscreteParameter("Bars", 4, 1, 9)
    .setDescription("Length of the phrase the beat position is measured against");

  public final TriggerParameter barTrigger =
    new TriggerParameter("Bar")
    .setDescription("Fires on the first beat of each bar");

  /** Which bar of the phrase is playing, 0-indexed. -1 until the first beat arrives. */
  private int barIndex = -1;

  private final long[] intervals = new long[32];
  private int intervalCount = 0;
  private int intervalIndex = 0;
  private long lastBeatNanos = 0;

  public TempoTap() {
    this("Tempo Tap");
  }

  public TempoTap(String label) {
    super(label);
    addParameter("tap", this.tap);
    // The OSC address is built from this key, so it is a permanent interface: renaming it
    // breaks every configured sender. OSCMacro reaches it via a per-macro customPath route
    // rather than the key being bent to match that VST's default macroN suffix.
    addParameter("beat", this.beat);
    addParameter("phraseReset", this.phraseReset);
    addParameter("sync", this.sync);
    addParameter("window", this.window);
    addParameter("bars", this.bars);
    addParameter("barTrigger", this.barTrigger);

    // Only a *change* in value is an event, which is why the input must cycle through the
    // bar rather than repeat one value. A parameter set to the value it already holds
    // notifies nobody, so a repeated write is silently not a beat.
    this.beat.addListener(p -> onBeatInput());
    this.phraseReset.addListener(p -> onPhraseReset());

    // Arriving mid-bar after a pause would otherwise fold the silence into the window.
    this.sync.addListener(p -> resetWindow());
    this.window.addListener(p -> resetWindow());
    this.bars.addListener(p -> resetWindow());

    setDescription("Taps the master tempo, or syncs it to a beat position sent over OSC");
  }

  /** Taps the master tempo. Overridable so tests can observe the call. */
  protected void tapTempo() {
    this.lx.engine.tempo.tap();
  }

  /** Sets the master tempo. Overridable so tests can observe the call. */
  protected void setTempoBpm(double bpm) {
    this.lx.engine.tempo.setBpm(bpm);
  }

  /**
   * Sets the master tempo's beat count from a 0-indexed position in the phrase.
   * Overridable so tests can observe the call.
   *
   * <p>0-indexed throughout, matching both the sender and {@link Tempo}: bar boundaries
   * fall wherever {@code beatCount % beatsPerBar == 0}, so the phrase downbeat is 0.
   */
  protected void triggerBeat(int beatIndex, long nanoTime) {
    this.lx.engine.tempo.trigger(beatIndex, nanoTime);
  }

  /** Beats per bar to quantize against. Overridable so tests need no engine. */
  protected int beatsPerBar() {
    return this.lx.engine.tempo.beatsPerBar.getValuei();
  }

  /** Beats in one phrase; the position sent over OSC is measured against this. */
  private int beatsPerPhrase() {
    return this.bars.getValuei() * beatsPerBar();
  }

  /** The current time. Overridable so tests can drive the interval math deterministically. */
  protected long nanoTime() {
    return System.nanoTime();
  }

  /**
   * Maps a 0-1 position onto a 0-indexed beat within the phrase. At the default 4 bars of
   * 4/4 that is k/16 to beat k, for k in 0..15.
   *
   * <p>The sender counts from zero, so <b>zero is the downbeat, not "no beat"</b>. Treating
   * it as a rest dropped every phrase downbeat and left everything after it a beat late.
   * The phrase boundary has its own input ({@link #phraseReset}) precisely so this one does
   * not have to reserve a value to mean something other than a position.
   */
  private int beatIndex(double normalized) {
    final int beatsPerPhrase = beatsPerPhrase();
    final int index = (int) Math.round(normalized * beatsPerPhrase);
    return (index < 0) ? 0 : ((index >= beatsPerPhrase) ? (beatsPerPhrase - 1) : index);
  }

  private void onBeatInput() {
    if (!this.sync.isOn()) {
      return;
    }
    final long now = nanoTime();
    if (this.lastBeatNanos > 0) {
      final long interval = now - this.lastBeatNanos;
      if (interval > MAX_INTERVAL_NANOS) {
        // The source stopped and came back. Everything before the gap describes a
        // different stretch of time, so start the window over rather than average across it.
        resetWindow();
      } else if (interval >= MIN_INTERVAL_NANOS) {
        pushInterval(interval);
      }
      // Below MIN_INTERVAL_NANOS the packet arrived too close to be a real beat; it still
      // aligns the phase below, but contributes no tempo information.
    }
    this.lastBeatNanos = now;

    if (this.intervalCount >= MIN_INTERVALS) {
      final double bpm = MS_PER_MINUTE / (medianIntervalNanos() / (double) NANOS_PER_MS);
      if (bpm >= Tempo.MIN_BPM && bpm <= Tempo.MAX_BPM) {
        setTempoBpm(bpm);
      }
    }
    final int beatIndex = beatIndex(this.beat.getValue());
    triggerBeat(beatIndex, now);
    updateBar(beatIndex / beatsPerBar());
  }

  private void onPhraseReset() {
    if (!this.sync.isOn() || (this.phraseReset.getValue() <= 0)) {
      // Zero is the fall back to rest after the pulse, not a second reset.
      return;
    }
    // Deliberately no interval contribution: a reset gate's timing against the beat grid
    // is not tempo information, and feeding it in would read as a short beat.
    triggerBeat(0, nanoTime());
    updateBar(0);
  }

  private void pushInterval(long interval) {
    final int size = this.window.getValuei();
    this.intervals[this.intervalIndex % size] = interval;
    this.intervalIndex = (this.intervalIndex + 1) % size;
    if (this.intervalCount < size) {
      ++this.intervalCount;
    }
  }

  private long medianIntervalNanos() {
    final long[] sorted = Arrays.copyOf(this.intervals, this.intervalCount);
    Arrays.sort(sorted);
    final int mid = sorted.length / 2;
    return ((sorted.length % 2) == 0) ? ((sorted[mid - 1] + sorted[mid]) / 2) : sorted[mid];
  }

  private void updateBar(int barIndex) {
    if (barIndex == this.barIndex) {
      return;
    }
    this.barIndex = barIndex;
    final int bars = this.bars.getValuei();
    // Spread across the full range rather than bars-many steps of 1/bars: a modulation
    // range can always be scaled down at the destination, but never up past 1.
    setValue((bars > 1) ? (barIndex / (double) (bars - 1)) : 0);
    this.barTrigger.trigger();
  }

  private void resetWindow() {
    this.intervalCount = 0;
    this.intervalIndex = 0;
    this.lastBeatNanos = 0;
  }

  @Override
  protected double computeValue(double deltaMs) {
    // The value only moves when a beat arrives, and updateBar applies it there directly so
    // that a stopped modulator -- which never receives loop() -- still tracks the bar.
    return getValue();
  }

  /** Button row height; {@code UIButton}'s default, and what {@link #newButton} builds. */
  private static final int BUTTON_HEIGHT = 16;

  /**
   * Two homogeneous rows rather than one wide one. Without this the modulator pane falls
   * back to UIModulatorControls.Missing and logs "No UI implementation found for type:
   * TempoTap".
   *
   * <p>Six controls in a single horizontal row came to roughly 280px against
   * {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator UIDeviceModulator}'s ~200px
   * of usable content width: Bars was sliced in half at the panel edge and the bar trigger
   * never drew at all. Overflow here is a hard crop, not a warning — the panel's height is a
   * fixed 160px whatever it contains, so anything past the edge is simply drawn outside it.
   * Rendered to confirm; see {@code docs/ui-rendering.md}.
   *
   * <p>Rows are kept homogeneous because a 16px button and a 42px knob side by side are
   * top-aligned, which reads as ragged. Knobs above, buttons below: 128px and 144px wide
   * respectively, and 62px tall together against the 126px cap.
   */
  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, TempoTap tempoTap) {
    uiModulator.setLayout(UI2dContainer.Layout.VERTICAL, 4);
    uiModulator.addChildren(
      UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4).addChildren(
        newKnob(tempoTap.beat),
        newKnob(tempoTap.phraseReset),
        newKnob(tempoTap.window),
        newKnob(tempoTap.bars)
      ),
      UI2dContainer.newHorizontalContainer(BUTTON_HEIGHT, 4).addChildren(
        newButton(tempoTap.tap, 56).setTriggerable(true),
        newButton(tempoTap.sync, 40),
        newButton(tempoTap.barTrigger, 40).setTriggerable(true)
      )
    );
  }

}
