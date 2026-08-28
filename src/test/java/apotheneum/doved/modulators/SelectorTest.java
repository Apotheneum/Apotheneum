package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXParameterModulation.ModulationException;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXNormalizedParameter;

public class SelectorTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;

  private LX lx;
  private Selector selector;

  @BeforeEach
  void setUp() {
    this.lx = newHeadlessLx();
    // Register with the modulation engine: this is what assigns a component id, and it is
    // the state a modulator is actually in when a show drives it.
    this.selector = this.lx.engine.modulation.addModulator(new Selector());
    this.selector.start();
    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      this.selector.input[i].setValue(.1 * (i + 1));
    }
  }

  /** The output is only recomputed on a loop pass, so every assertion follows one. */
  private double output() {
    this.selector.loop(1000. / 60.);
    return this.selector.getValue();
  }

  /** Reads the selected index after a frame, alongside the output that frame produced. */
  private int index() {
    output();
    return this.selector.getSelectedIndex();
  }

  /** Points Select at the middle of the given input's band, as the trigger buttons do. */
  private void selectBand(int index, int of) {
    this.selector.select.setValue((index + .5) / of);
  }

  @Test
  void passesTheSelectedInputThrough() {
    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      selectBand(i, Selector.MAX_INPUTS);
      assertEquals(.1 * (i + 1), output(), EPSILON);
      assertEquals(i, index());
    }
  }

  /** Passing through means passing through unchanged: no blend with the neighbouring input. */
  @Test
  void outputTracksLiveChangesOnTheSelectedInputOnly() {
    selectBand(1, Selector.MAX_INPUTS);
    this.selector.input[1].setValue(.75);
    assertEquals(.75, output(), EPSILON);

    this.selector.input[0].setValue(1);
    this.selector.input[2].setValue(1);
    assertEquals(.75, output(), EPSILON, "unselected inputs must not reach the output");
  }

  @Test
  void selectSpansOnlyTheActiveInputs() {
    this.selector.numInputs.setValue(2);

    // The full sweep of Select now covers two inputs rather than four, so the top of the
    // range lands on In-2 instead of leaving half the sweep on unused inputs.
    this.selector.select.setValue(.25);
    assertEquals(.1, output(), EPSILON);
    assertEquals(0, index(), toIndexMessage());

    this.selector.select.setValue(.75);
    assertEquals(.2, output(), EPSILON);
    assertEquals(1, index());
  }

  /** The band boundary is the first sample of the next input, not the last of the previous. */
  @Test
  void bandBoundariesLandOnTheHigherInput() {
    this.selector.numInputs.setValue(2);

    this.selector.select.setValue(.5 - EPSILON);
    assertEquals(.1, output(), EPSILON);

    this.selector.select.setValue(.5);
    assertEquals(.2, output(), EPSILON);
  }

  /** Select at its maximum must clamp to the last input rather than index off the end. */
  @Test
  void fullySweptSelectClampsToTheLastInput() {
    this.selector.select.setValue(1);
    assertEquals(.1 * Selector.MAX_INPUTS, output(), EPSILON);
    assertEquals(Selector.MAX_INPUTS - 1, index());

    this.selector.numInputs.setValue(1);
    assertEquals(.1, output(), EPSILON);
    assertEquals(0, index());
  }

  /**
   * Shrinking Num while Select points past the new end must fall back to a live input.
   * Otherwise the output would read an input the patch no longer considers connected.
   */
  @Test
  void shrinkingNumInputsPullsSelectionBackIntoRange() {
    // The last band, not a fixed index: this test broke silently when MAX_INPUTS grew from 4
    // to 8, because band 3 of 4 (the old last band) is no longer band 3 of 8 (now well short
    // of the end). Anchoring to MAX_INPUTS - 1 keeps "Select points past the new end" true
    // regardless of how many inputs exist.
    selectBand(Selector.MAX_INPUTS - 1, Selector.MAX_INPUTS);
    assertEquals(.1 * Selector.MAX_INPUTS, output(), EPSILON);
    assertEquals(Selector.MAX_INPUTS - 1, index(), toIndexMessage());

    this.selector.numInputs.setValue(2);
    assertEquals(.2, output(), EPSILON);
    assertEquals(1, index());
  }

  @Test
  void nextAdvancesOneInputAndWraps() {
    selectBand(0, Selector.MAX_INPUTS);
    for (int i = 1; i < Selector.MAX_INPUTS; ++i) {
      this.selector.triggerNext.trigger();
      assertEquals(.1 * (i + 1), output(), EPSILON);
    }
    this.selector.triggerNext.trigger();
    assertEquals(.1, output(), EPSILON, "Next past the last input wraps to the first");
  }

  @Test
  void prevStepsBackAndWraps() {
    selectBand(0, Selector.MAX_INPUTS);
    this.selector.triggerPrev.trigger();
    assertEquals(.1 * Selector.MAX_INPUTS, output(), EPSILON,
      "Prev from the first input wraps to the last");

    this.selector.triggerPrev.trigger();
    assertEquals(.1 * (Selector.MAX_INPUTS - 1), output(), EPSILON);
  }

  @Test
  void steppingWrapsWithinTheActiveInputsOnly() {
    this.selector.numInputs.setValue(2);
    selectBand(1, 2);
    assertEquals(.2, output(), EPSILON);

    this.selector.triggerNext.trigger();
    assertEquals(.1, output(), EPSILON, "wrap happens at Num, not at MAX_INPUTS");
  }

  /**
   * The point of the module: a modulator patched onto an input reaches the output when
   * that input is selected. Inputs are CompoundParameters precisely so this is possible.
   */
  @Test
  void modulationOnAnInputReachesTheOutput() throws ModulationException {
    final FixedSource source = addSource(.5);
    modulate(source, this.selector.input[1], 1);

    this.selector.input[1].setValue(.25);
    selectBand(1, Selector.MAX_INPUTS);
    assertEquals(.75, output(), EPSILON, "base .25 plus a half-scale modulation");

    source.set(1);
    assertEquals(1, output(), EPSILON);

    selectBand(0, Selector.MAX_INPUTS);
    assertEquals(.1, output(), EPSILON, "modulation on an unselected input stays out of the output");
  }

  /**
   * The other half: the choice itself is modulatable. This is why Select is a
   * CompoundParameter and not a DiscreteParameter dropdown — LX only allows modulation
   * onto a CompoundParameter, so a dropdown could never be swept by an LFO or sequencer.
   */
  @Test
  void modulationOnSelectDrivesTheChoice() throws ModulationException {
    final FixedSource source = addSource(0);
    modulate(source, this.selector.select, 1);

    this.selector.select.setValue(0);
    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      source.set((i + .5) / Selector.MAX_INPUTS);
      assertEquals(.1 * (i + 1), output(), EPSILON,
        "a modulator sweeping Select must walk the inputs in order");
    }
  }

  /** Stepping moves the knob under the modulation, not the modulated result. */
  @Test
  void steppingIsIndependentOfModulationOnSelect() throws ModulationException {
    final FixedSource source = addSource(0);
    // A quarter-range offset: enough to shift the modulated value without ever being the
    // thing that decides which input the step lands on.
    modulate(source, this.selector.select, .25);
    source.set(1);

    selectBand(0, Selector.MAX_INPUTS);
    final double base = this.selector.select.getBaseValue();
    this.selector.triggerNext.trigger();

    assertEquals(base + 1. / Selector.MAX_INPUTS, this.selector.select.getBaseValue(), EPSILON,
      "Next moves the base value by exactly one band regardless of the modulation on top");
    assertNotEquals(this.selector.select.getBaseValue(), this.selector.select.getValue(),
      "precondition: the modulation is still displacing the value that gets read");
  }

  /** Dragging Select by hand snaps onto an input rather than near a band boundary. */
  @Test
  void detentsSnapToBandCentres() {
    assertTrue(this.selector.select.isDetentEnabled());

    this.selector.select.setValue(0);
    this.selector.select.nextDetent();
    assertEquals(.5 / Selector.MAX_INPUTS, this.selector.select.getBaseValue(), EPSILON);
    assertEquals(.1, output(), EPSILON);

    this.selector.numInputs.setValue(2);
    this.selector.select.setValue(0);
    this.selector.select.nextDetent();
    assertEquals(.25, this.selector.select.getBaseValue(), EPSILON,
      "detents follow Num so they keep landing on the middle of a live input");
  }

  /**
   * Chaining is the documented alternative to raising MAX_INPUTS, so it needs to keep
   * working: an upstream selector mapped into one input slot makes its whole group
   * reachable through that slot, for 2 * MAX_INPUTS - 1 signals across a pair. The
   * receiving slot is spent on the chain, which is where the missing signal goes.
   */
  @Test
  void selectorsChainToExtendTheInputCount() throws ModulationException {
    final Selector upstream =
      this.lx.engine.modulation.addModulator(new Selector("Upstream"));
    upstream.start();
    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      upstream.input[i].setValue(.5 + .01 * i);
    }

    final int slot = Selector.MAX_INPUTS - 1;
    modulate(upstream, this.selector.input[slot], 1);
    // LX modulation is additive, so the receiving slot has to be zeroed or its own value
    // is summed onto whatever the upstream selector passes through.
    this.selector.input[slot].setValue(0);

    // The slots ahead of the chained one still carry their own signals.
    for (int i = 0; i < slot; ++i) {
      selectBand(i, Selector.MAX_INPUTS);
      upstream.loop(1000. / 60.);
      assertEquals(.1 * (i + 1), output(), EPSILON);
    }

    // And the chained slot reaches every upstream input in turn.
    selectBand(slot, Selector.MAX_INPUTS);
    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      upstream.select.setValue((i + .5) / Selector.MAX_INPUTS);
      upstream.loop(1000. / 60.);
      assertEquals(.5 + .01 * i, output(), EPSILON,
        "the upstream group must be reachable through the chained slot");
    }
  }

  /**
   * The lights are what make the UI show its state: without them, four inputs sitting at
   * similar values look identical whichever one is selected. Exactly one is lit.
   */
  @Test
  void exactlyOneLightMarksTheLiveInput() {
    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      selectBand(i, Selector.MAX_INPUTS);
      output();
      for (int j = 0; j < Selector.MAX_INPUTS; ++j) {
        assertEquals(i == j, this.selector.activeInput[j].isOn(),
          "light " + (j + 1) + " while input " + (i + 1) + " is selected");
      }
    }

    this.selector.numInputs.setValue(2);
    this.selector.select.setValue(1);
    output();
    assertTrue(this.selector.activeInput[1].isOn(), "the last active input lights at full sweep");
    assertFalse(this.selector.activeInput[3].isOn(), "an input beyond Num never lights");
  }

  /** Status, not an input: nothing outside the selector should be able to drive the lights. */
  @Test
  void lightsAreNotMappableOrSnapshotted() {
    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      assertFalse(this.selector.activeInput[i].isMappable());
      assertFalse(this.selector.isSnapshotControl(this.selector.activeInput[i]));
    }
    assertTrue(this.selector.isSnapshotControl(this.selector.select));
  }

  /** The trigger path routes on the same selection the signal path does. */
  @Test
  void onlyTheSelectedInputsTriggerPassesThrough() {
    final AtomicInteger fires = countTriggerOut();

    selectBand(1, Selector.MAX_INPUTS);
    output();
    this.selector.triggerIn[1].trigger();
    assertEquals(1, fires.get());

    this.selector.triggerIn[0].trigger();
    this.selector.triggerIn[2].trigger();
    this.selector.triggerIn[3].trigger();
    assertEquals(1, fires.get(), "an unselected input's trigger is blocked, not queued");

    selectBand(3, Selector.MAX_INPUTS);
    this.selector.triggerIn[3].trigger();
    assertEquals(2, fires.get(), "and the newly selected input's trigger passes");
  }

  /**
   * Triggers arrive between frames, and a stopped modulator never loops at all — so the
   * trigger path has to read the selection live rather than reuse the last frame's index.
   */
  @Test
  void triggersRouteWithoutWaitingForAFrame() {
    final AtomicInteger fires = countTriggerOut();

    // Move the selection and fire in the same breath, with no loop() in between.
    selectBand(2, Selector.MAX_INPUTS);
    this.selector.triggerIn[2].trigger();
    assertEquals(1, fires.get(), "routing must not lag a frame behind Select");

    this.selector.stop();
    assertFalse(this.selector.isRunning());
    selectBand(0, Selector.MAX_INPUTS);
    this.selector.triggerIn[0].trigger();
    assertEquals(2, fires.get(), "a stopped selector still routes triggers");
  }

  @Test
  void steppingRoutesTheTriggerPathToo() {
    final AtomicInteger fires = countTriggerOut();
    selectBand(0, Selector.MAX_INPUTS);

    this.selector.triggerNext.trigger();
    this.selector.triggerIn[1].trigger();
    assertEquals(1, fires.get(), "Next moves both paths, not just the signal");

    this.selector.triggerIn[0].trigger();
    assertEquals(1, fires.get());
  }

  /**
   * A stopped modulator never loops, so status that only updated in computeValue would hold
   * the last frame's selection while the trigger path routed on the live one — the lights
   * and getSelectedIndex() naming a different input from the one actually passing triggers.
   */
  @Test
  void statusStaysCurrentWhileStopped() {
    final AtomicInteger fires = countTriggerOut();
    selectBand(0, Selector.MAX_INPUTS);
    output();

    this.selector.stop();
    assertFalse(this.selector.isRunning());

    selectBand(2, Selector.MAX_INPUTS);
    assertEquals(2, this.selector.getSelectedIndex(), "index must not wait on a frame");
    assertTrue(this.selector.activeInput[2].isOn(), "the light must follow with no frame either");
    assertFalse(this.selector.activeInput[0].isOn());

    this.selector.triggerIn[2].trigger();
    assertEquals(1, fires.get(), "and the trigger path agrees with what the lights show");
  }

  /** Same divergence, reached through the step buttons rather than the knob. */
  @Test
  void steppingUpdatesStatusWhileStopped() {
    selectBand(0, Selector.MAX_INPUTS);
    output();
    this.selector.stop();

    this.selector.triggerNext.trigger();
    assertEquals(1, this.selector.getSelectedIndex());
    assertTrue(this.selector.activeInput[1].isOn());

    this.selector.numInputs.setValue(1);
    assertEquals(0, this.selector.getSelectedIndex(), "shrinking Num re-lights within range");
    assertTrue(this.selector.activeInput[0].isOn());
    assertFalse(this.selector.activeInput[1].isOn());
  }

  @Test
  void triggerOutIsTheTriggerSource() {
    assertEquals(this.selector.triggerOut, this.selector.getTriggerSource());
  }

  private AtomicInteger countTriggerOut() {
    final AtomicInteger count = new AtomicInteger();
    this.selector.triggerOut.addListener(p -> {
      if (this.selector.triggerOut.isOn()) {
        count.incrementAndGet();
      }
    });
    return count;
  }

  @Test
  void outputIsNormalizedForDownstreamMapping() {
    selectBand(2, Selector.MAX_INPUTS);
    output();
    assertEquals(this.selector.getValue(), this.selector.getNormalized(), EPSILON);
  }

  /**
   * The output is a function of the inputs and Select, so a write would be replaced on the
   * next frame. Failing loudly beats reverting silently, which is what SampleHold does too.
   */
  @Test
  void theOutputCannotBeSetDirectly() {
    assertThrows(UnsupportedOperationException.class, () -> this.selector.setNormalized(.42));
  }

  /**
   * Modulation applies at read time and fires no listener on the target, so a modulated
   * Select moves the routing without any parameter change to react to. postRun runs every
   * frame whether or not the modulator is running, which is what keeps the lights with it.
   */
  @Test
  void lightsFollowAModulatedSelectWhileStopped() throws ModulationException {
    final FixedSource source = addSource(0);
    modulate(source, this.selector.select, 1);
    this.selector.select.setValue(0);
    this.selector.stop();

    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      source.set((i + .5) / Selector.MAX_INPUTS);
      this.selector.loop(1000. / 60.);
      assertEquals(i, this.selector.getSelectedIndex());
      assertTrue(this.selector.activeInput[i].isOn(),
        "light " + (i + 1) + " must follow a modulated Select even while stopped");
    }
  }

  /**
   * The lights are registered parameters, as StepModulator's activeStep are, so a project
   * restores them after restoring Select — potentially replaying stale ones. A frame must
   * put them back in agreement even if the selector loads stopped.
   */
  @Test
  void staleLightsFromAProjectAreCorrected() {
    selectBand(3, Selector.MAX_INPUTS);
    output();
    this.selector.stop();

    // What a load of a project saved with a different input lit would leave behind.
    this.selector.activeInput[0].setValue(true);
    this.selector.activeInput[3].setValue(false);

    this.selector.loop(1000. / 60.);
    assertTrue(this.selector.activeInput[3].isOn(), "the live input is re-lit");
    assertFalse(this.selector.activeInput[0].isOn(), "and the restored one is cleared");
  }

  @Test
  void everyInputIsRegisteredAndModulatable() {
    // A regression guard on the number itself, folded in here rather than its own test:
    // MAX_INPUTS's javadoc explains why 8 is the ceiling given the current UI budget, and
    // that explanation goes stale silently if the constant drifts without anyone re-checking
    // the layout math.
    assertEquals(8, Selector.MAX_INPUTS);

    for (int i = 0; i < Selector.MAX_INPUTS; ++i) {
      assertSame(this.selector.input[i], this.selector.getParameter("input" + (i + 1)),
        "inputs must be registered parameters or they will not save, map or restore");
      assertTrue(this.selector.input[i].isMappable());
    }
    assertSame(this.selector.select, this.selector.getParameter("select"));
    assertTrue(this.selector.select.isMappable());
  }

  /**
   * Banding, trigger routing, and the activeInput lights, all with Num above
   * {@code RICH_INPUTS} — the boundary where {@code buildModulatorControls} switches its
   * on-screen layout to bare knobs with no per-input trigger button or light. None of that UI
   * split is visible to this class's own routing logic, which is the point of testing it here:
   * Num=6 must behave exactly like the below-the-boundary cases already covered above, just
   * with more bands and no on-screen affordance for inputs 5-8. Combined into one test, rather
   * than three, to keep this file's total headless-LX construct/dispose count down — see
   * {@link apotheneum.HeadlessLxTest}'s javadoc on the CoreMIDI race that extra instances feed.
   */
  @Test
  void behaviorAboveFourActiveInputs() {
    this.selector.numInputs.setValue(6);

    // Banding: the sweep covers exactly the six active inputs.
    selectBand(0, 6);
    assertEquals(.1, output(), EPSILON);
    assertEquals(0, index(), toIndexMessage());
    selectBand(5, 6);
    assertEquals(.6, output(), EPSILON, "the sixth active input, not the eighth");
    assertEquals(5, index(), toIndexMessage());

    // Trigger routing: input 6 (index 5) is reachable now that Num allows it; 7 and 8 are not.
    final AtomicInteger fires = countTriggerOut();
    output();
    this.selector.triggerIn[5].trigger();
    assertEquals(1, fires.get(), "input 6 is active now that Num allows it");
    this.selector.triggerIn[0].trigger();
    this.selector.triggerIn[6].trigger();
    this.selector.triggerIn[7].trigger();
    assertEquals(1, fires.get(),
      "an unselected input's trigger is blocked even past RICH_INPUTS");
    selectBand(2, 6);
    this.selector.triggerIn[2].trigger();
    assertEquals(2, fires.get(), "and the newly selected input's trigger passes");

    // activeInput lights: exactly one on at a time, tracked for all MAX_INPUTS regardless of
    // whether the compact UI gives inputs 5-8 an on-screen light to show it.
    for (int i = 0; i < 6; ++i) {
      selectBand(i, 6);
      output();
      for (int j = 0; j < Selector.MAX_INPUTS; ++j) {
        assertEquals(i == j, this.selector.activeInput[j].isOn(),
          "light " + (j + 1) + " while input " + (i + 1) + " is selected, Num=6");
      }
    }
  }

  /**
   * A project saved before {@link Selector#MAX_INPUTS} grew from 4 to 8 has no
   * {@code input5}..{@code input8}, {@code triggerIn5}..{@code triggerIn8}, or
   * {@code activeInput5}..{@code activeInput8} keys, and its {@code numInputs} was capped at
   * 4. LX's own parameter loading is what has to tolerate the missing keys — this test exists
   * to confirm that assumption holds for this component's parameter set rather than take it on
   * faith, since a component that generated its own keys from a range (like {@code addParameter
   * ("input" + (i + 1), ...)} in the constructor here) is exactly the shape that would break if
   * load ever required every registered key to be present.
   */
  @Test
  void loadingAProjectSavedBeforeEightInputsToleratesMissingKeys() {
    final JsonObject saved = new JsonObject();
    this.selector.save(this.lx, saved);
    // Not what this test is about: this.selector's own id is still live in the engine, and
    // loading it verbatim into a second, already-registered Selector would collide with the
    // original rather than exercise parameter tolerance. A real project load restores ids into
    // freshly-deserialized components that do not exist yet, so no such collision arises there.
    saved.remove(LXComponent.KEY_ID);
    final JsonObject parameters = saved.getAsJsonObject(LXComponent.KEY_PARAMETERS);
    for (int i = 5; i <= Selector.MAX_INPUTS; ++i) {
      parameters.remove("input" + i);
      parameters.remove("triggerIn" + i);
      parameters.remove("activeInput" + i);
    }
    // A project from before MAX_INPUTS grew could never have saved a Num above the old cap.
    parameters.addProperty("numInputs", 4);

    final Selector loaded = this.lx.engine.modulation.addModulator(new Selector("Loaded"));
    assertDoesNotThrow(() -> loaded.load(this.lx, saved),
      "loading a pre-expansion project must not throw for keys that did not exist yet");

    for (int i = 4; i < Selector.MAX_INPUTS; ++i) {
      assertEquals(0, loaded.input[i].getValue(), EPSILON,
        "input " + (i + 1) + " keeps its constructor default; nothing in the saved JSON names it");
      assertFalse(loaded.activeInput[i].isOn(),
        "activeInput " + (i + 1) + " keeps its constructor default of off");
    }
    assertEquals(4, loaded.numInputs.getValuei(), "the restored Num value carries over normally");
  }

  private String toIndexMessage() {
    return "Select " + this.selector.select.getValue() + " over " + this.selector.numInputs.getValuei() + " inputs";
  }

  private FixedSource addSource(double value) {
    final FixedSource source = this.lx.engine.modulation.addModulator(new FixedSource());
    source.set(value);
    return source;
  }

  private void modulate(LXNormalizedParameter source, CompoundParameter target, double range)
    throws ModulationException {
    final LXCompoundModulation modulation =
      new LXCompoundModulation(this.lx.engine.modulation, source, target);
    this.lx.engine.modulation.addModulation(modulation);
    modulation.range.setValue(range);
  }

  /**
   * A modulation source held at whatever value a test sets. Stock modulators either move on
   * their own or need a clock; here the input value is the thing under test, so it has to
   * hold still. Never started, so nothing overwrites the value between assertions.
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
