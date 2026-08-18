package apotheneum.doved.modulators;

import java.util.HashMap;
import java.util.Map;

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIKnob;
import heronarts.glx.ui.component.UIIndicator;
import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

/**
 * An N-way selector. Each input is a compound parameter, so any modulator can be
 * mapped onto it — the selector then passes exactly one of them through to its
 * output, with no blending. Selection is itself a compound parameter, so the
 * choice can be driven by an LFO, a step sequencer, MIDI, or the trigger buttons.
 *
 * <p>Triggers route alongside the signals. Each input has a trigger of its own, and a fire
 * on the selected one passes through to {@link #triggerOut} while the rest are blocked, so
 * one Select switches a source's value and its trigger together. This is the mirror of
 * {@link heronarts.lx.modulator.MultiTrig}, which fans one trigger out to one of several
 * destinations; nothing in LX collects several triggers down to one.
 */
@LXModulator.Global("Selector")
@LXModulator.Device("Selector")
@LXCategory(LXCategory.CORE)
public class Selector extends LXModulator
  implements LXNormalizedParameter, LXTriggerSource, LXOscComponent, UIModulatorControls<Selector> {

  /**
   * Raise this to add more inputs. The constructor builds the parameters from it, but each
   * input costs a whole column of UI — knob, trigger and light — whether it is in use or
   * not, and the row is already 114px of the 126px device-modulator cap and 172px of the
   * 228px usable width. Eight columns do not fit at full size; LX's macro modulators get
   * eight across only by shrinking the knobs.
   *
   * <p>Rather than raising it, two selectors chain: map one selector's output into
   * another's input and the upstream group becomes reachable through that one slot,
   * giving 2 * MAX_INPUTS - 1 signals for a pair. Two things to know when doing that.
   * The receiving input's own value must be left at zero — LX modulation is additive,
   * so a non-zero base is silently summed onto whatever the upstream selector passes
   * through. And selection stays hierarchical: two Select knobs, coarse then fine, not
   * one 1-of-7 control, so a single modulator cannot sweep the whole set in order.
   */
  public static final int MAX_INPUTS = 4;

  /** Height of the light under each input knob, matching the step modulators' indicators. */
  private static final int INDICATOR_HEIGHT = 6;

  /** Height of the per-input trigger button under the light. */
  private static final int TRIGGER_HEIGHT = 16;

  public final CompoundParameter[] input = new CompoundParameter[MAX_INPUTS];

  /** One trigger per input; only the selected one reaches {@link #triggerOut}. */
  public final TriggerParameter[] triggerIn = new TriggerParameter[MAX_INPUTS];

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trig Out")
    .setDescription("Fires when the selected input's trigger fires");

  public final DiscreteParameter numInputs =
    new DiscreteParameter("Num", MAX_INPUTS, 1, MAX_INPUTS + 1)
    .setDescription("How many inputs the selector spans");

  public final CompoundParameter select =
    new CompoundParameter("Select", 0)
    .setDescription("Which input is passed through, spread evenly over the active inputs");

  public final TriggerParameter triggerNext =
    new TriggerParameter("Next", () -> step(1))
    .setDescription("Advance to the next input, wrapping around");

  public final TriggerParameter triggerPrev =
    new TriggerParameter("Prev", () -> step(-1))
    .setDescription("Return to the previous input, wrapping around");

  /**
   * One flag per input, true while that input is the one passing through. Named and shaped
   * after {@link heronarts.lx.modulator.StepModulator#activeStep} so the UI can light the
   * live input the way the step modulators light the active step. Status, not an input:
   * not mappable, since driving it from outside would claim a selection the selector is
   * not making.
   */
  public final BooleanParameter[] activeInput = new BooleanParameter[MAX_INPUTS];

  /**
   * The Num listener each built UI added, keyed by that UI. One modulator can have its
   * controls built more than once — a global pane and a device strip, or any rebuild — and
   * a flat list would let disposing either one tear down the listener the other still needs.
   */
  private final Map<UIModulator, LXParameterListener> uiListeners =
    new HashMap<UIModulator, LXParameterListener>();

  public Selector() {
    this("Selector");
  }

  public Selector(String label) {
    super(label);
    for (int i = 0; i < MAX_INPUTS; ++i) {
      this.input[i] = new CompoundParameter("In-" + (i + 1), 0)
        .setDescription("Input signal " + (i + 1));
      addParameter("input" + (i + 1), this.input[i]);
      final int index = i;
      this.triggerIn[i] = new TriggerParameter("Trig-" + (i + 1), () -> onTrigger(index))
        .setDescription("Trigger for input " + (i + 1) + "; passes through while it is selected");
      addParameter("triggerIn" + (i + 1), this.triggerIn[i]);
      this.activeInput[i] = new BooleanParameter("Active-" + (i + 1), i == 0)
        .setMappable(false)
        .setDescription("Whether input " + (i + 1) + " is the one passing through");
    }
    addParameter("numInputs", this.numInputs);
    addParameter("select", this.select);
    addParameter("triggerOut", this.triggerOut);
    addParameter("triggerNext", this.triggerNext);
    addParameter("triggerPrev", this.triggerPrev);
    for (int i = 0; i < MAX_INPUTS; ++i) {
      addParameter("activeInput" + (i + 1), this.activeInput[i]);
    }
    setDescription("Passes one of several modulated inputs through, selecting discretely");
    updateDetents();
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.numInputs) {
      updateDetents();
      updateLights();
    } else if (p == this.select) {
      updateLights();
    }
  }

  /**
   * Snap the manual Select knob to the middle of each input's band, so dragging it
   * lands cleanly on an input rather than near a boundary.
   */
  private void updateDetents() {
    final int n = this.numInputs.getValuei();
    final double[] detents = new double[n];
    for (int i = 0; i < n; ++i) {
      detents[i] = (i + .5) / n;
    }
    this.select.setDetentsNormalized(detents);
  }

  /**
   * Step the base selection by the given number of inputs, wrapping. Operates on the base
   * value so that stepping is independent of any modulation applied to Select.
   *
   * <p>Deliberately not {@code select.nextDetent(true)}, even though the detents are these
   * same band centres: detent stepping moves from the parameter's modulated value, so with
   * a modulation on Select a single press can skip a band. Stepping has to move the knob
   * under the modulation, not the sum.
   */
  private void step(int delta) {
    final int n = this.numInputs.getValuei();
    final int to = Math.floorMod(bandOf(this.select.getBaseValue()) + delta, n);
    this.select.setValue((to + .5) / n);
  }

  @Override
  public boolean isSnapshotControl(LXParameter parameter) {
    // The lights report what the selector is doing right now; a snapshot restoring old ones
    // would just be overwritten on the next frame anyway.
    for (BooleanParameter active : this.activeInput) {
      if (parameter == active) {
        return false;
      }
    }
    return super.isSnapshotControl(parameter);
  }

  /**
   * Index of the input currently being passed through. Computed rather than cached: a
   * stopped modulator never runs {@link #computeValue}, and the trigger path routes on the
   * live selection, so a cached index would report a different input from the one actually
   * receiving triggers.
   */
  public int getSelectedIndex() {
    return bandOf(this.select.getValue());
  }

  /** The input a Select value lands on, spread evenly over the active inputs. */
  private int bandOf(double select) {
    final int n = this.numInputs.getValuei();
    return LXUtils.constrain((int) (select * n), 0, n - 1);
  }

  /**
   * Runs every frame whether or not the modulator is running, which is what keeps the
   * lights honest in the two cases parameter changes miss: a modulation sweeping Select
   * applies at read time and fires no listener on the target, and a project load restores
   * the saved lights after restoring Select. Both would otherwise leave the lights naming a
   * different input from the one onTrigger routes to.
   */
  @Override
  protected void postRun(double deltaMs) {
    updateLights();
  }

  @Override
  protected double computeValue(double deltaMs) {
    final int index = getSelectedIndex();
    setLights(index);
    return this.input[index].getValue();
  }

  /**
   * Keep the lights honest between frames. Selection changes arrive as parameter changes,
   * which happen whether or not the modulator is running — so without this the lights hold
   * the last frame's selection while triggers route to the new one.
   */
  private void updateLights() {
    setLights(getSelectedIndex());
  }

  private void setLights(int index) {
    for (int i = 0; i < MAX_INPUTS; ++i) {
      this.activeInput[i].setValue(i == index);
    }
  }

  /**
   * A trigger arrived on one input. Reads the selection live rather than reusing the index
   * from the last frame: triggers are dispatched between frames, and a stopped modulator
   * never runs {@link #computeValue} at all, so a cached index would be stale exactly when
   * a trigger patch most needs it.
   */
  private void onTrigger(int input) {
    if (input == getSelectedIndex()) {
      this.triggerOut.trigger();
    }
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  /**
   * Two rows rather than one. The right pane is {@value
   * heronarts.lx.studio.ui.UIRightPane#WIDTH}px wide and a {@link UIKnob} is
   * {@value UIKnob#WIDTH}px, so the four inputs plus Select do not fit across a single
   * row at full size. LX's macro modulators get eight knobs into that width only by
   * shrinking them; here the inputs are the thing being read at a glance, so they keep
   * their size and the controls move below them.
   *
   * <p>Two pieces of state are on screen because without them the controls look inert: a
   * light under the live input, the way the step modulators light the active step, so
   * moving Select or pressing the step buttons visibly does something even when the inputs
   * sit at similar values; and inputs past Num dimmed, so changing Num shows its effect
   * rather than only altering where Select's bands fall.
   */
  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, Selector selector) {
    final UIKnob[] inputKnobs = new UIKnob[MAX_INPUTS];
    final UI2dComponent[] inputColumns = new UI2dComponent[MAX_INPUTS];
    for (int i = 0; i < MAX_INPUTS; ++i) {
      inputKnobs[i] = newKnob(selector.input[i]);
      inputColumns[i] = UI2dContainer.newVerticalContainer(UIKnob.WIDTH, 2,
        inputKnobs[i],
        newButton(selector.triggerIn[i], UIKnob.WIDTH).setTriggerable(true).setLabel("Trig")
          .setHeight(TRIGGER_HEIGHT),
        // Bottom of the column, under everything it describes. Read-only: the light reports
        // the selection, it does not make one.
        new UIIndicator(ui, 0, 0, UIKnob.WIDTH, INDICATOR_HEIGHT, selector.activeInput[i])
          .setClickable(false)
      );
    }

    // Dim rather than hide: an invisible child may or may not be skipped by the container's
    // layout, and a row that reflows as Num changes would shift every knob under the cursor.
    final LXParameterListener updateEnabled = p -> {
      final int n = selector.numInputs.getValuei();
      for (int i = 0; i < MAX_INPUTS; ++i) {
        inputKnobs[i].setEnabled(i < n);
      }
    };
    updateEnabled.onParameterChanged(selector.numInputs);
    selector.numInputs.addListener(updateEnabled);
    this.uiListeners.put(uiModulator, updateEnabled);

    // Each input is a column: value knob, its own trigger, and the light saying whether it
    // is live underneath both. Controls beneath, count first. Two rows come to 114px,
    // inside the 126px device-modulator content cap.
    uiModulator.setLayout(UI2dContainer.Layout.VERTICAL, 4);
    uiModulator.addChildren(
      UI2dContainer.newHorizontalContainer(
        UIKnob.HEIGHT + 2 + INDICATOR_HEIGHT + 2 + TRIGGER_HEIGHT, 4, inputColumns),
      UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4,
        newIntegerBox(selector.numInputs, 34),
        newKnob(selector.select),
        UI2dContainer.newVerticalContainer(40, 2,
          newButton(selector.triggerPrev, 40).setTriggerable(true).setLabel("Prev"),
          newButton(selector.triggerNext, 40).setTriggerable(true).setLabel("Next")
        )
      )
    );
  }

  @Override
  public void disposeModulatorControls(UI ui, UIModulator uiModulator, Selector selector) {
    final LXParameterListener listener = this.uiListeners.remove(uiModulator);
    if (listener != null) {
      selector.numInputs.removeListener(listener);
    }
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    // Matching SampleHold: the value is a function of the inputs and Select, so anything
    // written here is replaced on the next frame. Failing loudly beats reverting silently.
    throw new UnsupportedOperationException(
      "Selector value comes from its selected input; it cannot be set directly");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }
}
