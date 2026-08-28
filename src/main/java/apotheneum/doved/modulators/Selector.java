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
   * Raising this beyond 4 used to mean giving up on the UI entirely — see the git history of
   * this constant's javadoc for the older, incorrect reasoning about why. What actually made 8
   * reachable is described at {@link #buildModulatorControls}: past
   * {@value Selector#RICH_INPUTS} active inputs, the on-screen columns drop their per-input
   * trigger button and activity light and lay out as bare knobs instead, which is the only
   * arrangement that fits {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator
   * UIDeviceModulator}'s 126px content cap at all. That bare-knob layout is also the reason
   * this is 8 and not some higher round number: it fits two rows of four ({@link UIKnob#WIDTH}
   * limits a row to four across the available content width), and a third bare-knob row would
   * push the layout's own floor — three knob-height rows at zero spacing — past the cap with
   * no room left for the Num/Select/Prev/Next row at all. Reaching past 8 inputs on one
   * Selector needs either a narrower control than {@link UIKnob} for the extra columns or a
   * taller cap than {@code MAX_CONTROLS_HEIGHT} allows; neither exists today.
   *
   * <p>Chaining is still the way to reach further without either of those: map one selector's
   * output into another's input and the upstream group becomes reachable through that one
   * slot, giving 2 * MAX_INPUTS - 1 signals for a pair. Two things to know when doing that.
   * The receiving input's own value must be left at zero — LX modulation is additive,
   * so a non-zero base is silently summed onto whatever the upstream selector passes
   * through. And selection stays hierarchical: two Select knobs, coarse then fine, not
   * one 1-of-15 control, so a single modulator cannot sweep the whole set in order.
   */
  public static final int MAX_INPUTS = 8;

  /** Height of the light under each input knob, matching the step modulators' indicators. */
  private static final int INDICATOR_HEIGHT = 6;

  /** Height of the per-input trigger button under the light. */
  private static final int TRIGGER_HEIGHT = 16;

  /**
   * Above this many active inputs, {@link #buildModulatorControls} switches from the rich
   * per-input column (knob, trigger, light) to the bare-knob grid. See that method's javadoc
   * for the measurements behind the switch.
   */
  private static final int RICH_INPUTS = 4;

  /**
   * Rendering measured this directly (render {@code apotheneum.doved.modulators.Selector} and
   * inspect the PNG/JSON — see {@code docs/ui-rendering.md}) rather than deriving it, because
   * the first attempt at eight inputs — {@link UIKnob}-width columns kept in one wide row, or
   * the rich column repeated across two rows of four — got the arithmetic and the failure mode
   * both wrong. A single row of 8 rich columns is 348px of knob-plus-spacing against roughly
   * 200px of usable {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator} content width,
   * so that was never in question. What rendering corrected was the height side: two full rich
   * rows (68px each) plus the Num/Select/Prev/Next row (42px) come to 182px against a 126px
   * {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator#MAX_CONTROLS_HEIGHT} cap — and
   * that overflow is not a warning, it is a hard crop. Rendered at eight inputs with two rich
   * rows, the Select knob and the Num/Prev/Next row were not squeezed or overlapping, they were
   * simply gone: {@code UIDeviceModulator}'s own height is a fixed 160px regardless of its
   * content, so anything placed past the cap is drawn outside the panel and never appears.
   *
   * <p>LX's own {@code MacroKnobs} was the model to check against, since its {@code showEight}
   * toggle claims exactly this problem — eight knobs where four fit — and its javadoc credits
   * it to "shrinking" the knobs. Decompiling {@code UIMacroKnobs.buildModulatorControls} (there
   * is no source jar; {@code javap -c} on the installed
   * {@code glxstudio-*-jar-with-dependencies.jar} was enough) shows otherwise: every knob it
   * builds, eight-across or not, is a plain {@code new UIKnob(0, 0, param)} — the width and
   * height are {@link UIKnob#WIDTH} and {@link UIKnob#HEIGHT}, both fixed constants with no
   * shrinking constructor. What {@code showEight} actually toggles is two rows of four
   * full-size knobs versus one row of five; the "shrinking" is a row split, the same move this
   * method was already making at four inputs. That trick alone reduces eight rich Selector
   * columns to 140px (two rows of 68) before the control row is even added — still 14px over
   * budget on its own, which is where the row split stops being sufficient and the second
   * change, described below, becomes necessary. (This javadoc previously repeated the
   * "shrinking" claim uncorrected; a session that actually decompiled the class found it false.)
   *
   * <p>The gap between what a row split saves (140px, or with the control row 182px) and what
   * the cap allows (126px) is closed by removing the per-column trigger button and indicator
   * light — the two pieces {@link #RICH_INPUTS} inputs keep — for the rest, not by finding more
   * slack in row spacing or margins. A bare knob's height is {@link UIKnob#HEIGHT}, 42px, and
   * that is also a fixed constant: two bare rows plus the control row (also knob-height, for
   * Select) come to exactly 3 &times; 42 = 126px at zero inter-row spacing, which is the floor —
   * there is no configuration of eight on-screen inputs plus Select, Num and Prev/Next that
   * both fits the cap and keeps per-input triggers and lights, because the cap is barely enough
   * for three bare knob-height rows and nothing else. Rendering this layout at eight inputs
   * (all eight knobs, zero gaps, un-clipped) is what confirmed the floor was real and not an
   * off-by-a-few estimate.
   */
  private static final int COMPACT_ROW_SPACING = 0;

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
   * Rebuilds between two layouts as {@link #numInputs} crosses {@link #RICH_INPUTS}, the way
   * {@code MacroKnobs}' {@code showEight} rebuilds between its five- and eight-knob rows (see
   * {@link #COMPACT_ROW_SPACING} for how that was confirmed rather than assumed). The
   * alternative — always building the eight-wide compact grid, even when only a couple of
   * inputs are in use — was rejected because it would cost every existing 1-to-4-input project
   * its per-input trigger button and activity light for no benefit; those projects use exactly
   * the layout this file already shipped and rendered as correct. The rebuild only fires on
   * the four/five boundary, which is a deliberate "I need more than four" action, not something
   * a performer crosses while sweeping Select or Num during a show — Select's range and Num's
   * upper bound do not move as a side effect of this switch, only what is drawn does.
   *
   * <p>Two pieces of state are on screen in the rich layout because without them the controls
   * look inert: a light under the live input, the way the step modulators light the active
   * step, so moving Select or pressing the step buttons visibly does something even when the
   * inputs sit at similar values; and inputs past Num dimmed, so changing Num shows its effect
   * rather than only altering where Select's bands fall. The compact layout keeps the dimming —
   * {@link #activeInput} and {@link #triggerIn} stay live and mappable either way, since nothing
   * about the underlying parameters changes, only which of them get an on-screen light or
   * button.
   *
   * <p>The Num/Select/Prev/Next row is built exactly once, outside the part that gets torn
   * down and rebuilt on a rich/compact swap, and stays out of it deliberately: rendering a
   * {@code numInputs} value that crosses {@link #RICH_INPUTS} reproduced a
   * {@code ConcurrentModificationException} from disposing the Num box as part of that swap.
   * The Num box is bound to {@link #numInputs}, the same parameter whose change is driving the
   * swap — disposing it removes it as a {@code numInputs} listener while {@code numInputs}'
   * listener list is still being walked by the very change notification that triggered the
   * rebuild. Nothing about {@link #select}, {@link #triggerPrev}, or {@link #triggerNext} is
   * self-referential this way, but the row is kept intact as a unit rather than splitting out
   * just the Num box, since a control row that only sometimes rebuilds is a harder invariant to
   * keep than one that never does.
   */
  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, Selector selector) {
    final UIKnob[] inputKnobs = new UIKnob[MAX_INPUTS];
    final boolean[] compact = { selector.numInputs.getValuei() > RICH_INPUTS };

    // Persistent wrapper for the rich or compact input rows. Only this container's own
    // children are torn down and rebuilt on a mode switch; it and everything outside it
    // (the control row added below) are built once and never disposed. See this method's
    // javadoc for why the control row specifically cannot be part of the rebuilt subtree.
    final UI2dContainer inputSection = UI2dContainer.newVerticalContainer(172, 0);
    final UI2dComponent controlRow = controlRow(selector);
    uiModulator.setLayout(UI2dContainer.Layout.VERTICAL, compact[0] ? COMPACT_ROW_SPACING : 4);
    uiModulator.addChildren(inputSection, controlRow);

    final Runnable rebuildInputs = () -> {
      inputSection.removeAllChildren();
      for (int i = 0; i < MAX_INPUTS; ++i) {
        inputKnobs[i] = null;
      }
      if (compact[0]) {
        buildCompactInputs(ui, selector, inputKnobs, inputSection);
        uiModulator.setChildSpacing(COMPACT_ROW_SPACING);
      } else {
        buildRichInputs(ui, selector, inputKnobs, inputSection);
        uiModulator.setChildSpacing(4);
      }
      updateEnabledKnobs(inputKnobs, selector.numInputs.getValuei());
    };
    rebuildInputs.run();

    // Dim rather than hide: an invisible child may or may not be skipped by the container's
    // layout, and a row that reflows on every Num change would shift every knob under the
    // cursor. The only reflow this listener triggers is the rich/compact swap at RICH_INPUTS,
    // not the ordinary case of adjusting Num within one mode.
    final LXParameterListener listener = p -> {
      final int n = selector.numInputs.getValuei();
      final boolean nowCompact = n > RICH_INPUTS;
      if (nowCompact != compact[0]) {
        compact[0] = nowCompact;
        rebuildInputs.run();
      } else {
        updateEnabledKnobs(inputKnobs, n);
      }
    };
    selector.numInputs.addListener(listener);
    this.uiListeners.put(uiModulator, listener);
  }

  private void updateEnabledKnobs(UIKnob[] inputKnobs, int numActive) {
    for (int i = 0; i < inputKnobs.length; ++i) {
      if (inputKnobs[i] != null) {
        inputKnobs[i].setEnabled(i < numActive);
      }
    }
  }

  /**
   * The layout this file shipped before {@link #MAX_INPUTS} grew past {@link #RICH_INPUTS}:
   * one row of {@value #RICH_INPUTS} full columns (knob, trigger, light) at 172 &times; 68px.
   * With the Num/Select/Prev/Next row (122 &times; 42px) that is 114px total, comfortably
   * inside the 126px
   * {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator#MAX_CONTROLS_HEIGHT} cap. Only
   * ever builds columns 0 until {@link #RICH_INPUTS}; the rest of {@code inputKnobs} is left
   * {@code null} for {@link #updateEnabledKnobs} to skip.
   */
  private void buildRichInputs(
    UI ui, Selector selector, UIKnob[] inputKnobs, UI2dContainer inputSection) {
    final UI2dComponent[] inputColumns = new UI2dComponent[RICH_INPUTS];
    for (int i = 0; i < RICH_INPUTS; ++i) {
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
    inputSection.addChildren(
      UI2dContainer.newHorizontalContainer(
        UIKnob.HEIGHT + 2 + INDICATOR_HEIGHT + 2 + TRIGGER_HEIGHT, 4, inputColumns)
    );
  }

  /**
   * Bare knobs only — no per-input trigger button, no per-input light — arranged
   * {@value #RICH_INPUTS} across and two rows deep. See {@link #COMPACT_ROW_SPACING} for why
   * zero spacing between these two rows, and between this section and the control row, is
   * load-bearing: 3 &times; {@value UIKnob#HEIGHT}px is already the full 126px budget, so this
   * is the one layout that fits eight inputs in the device-modulator cap, not a stylistic
   * choice among several that would.
   */
  private void buildCompactInputs(
    UI ui, Selector selector, UIKnob[] inputKnobs, UI2dContainer inputSection) {
    for (int i = 0; i < MAX_INPUTS; ++i) {
      inputKnobs[i] = newKnob(selector.input[i]);
    }
    inputSection.setLayout(UI2dContainer.Layout.VERTICAL, COMPACT_ROW_SPACING);
    inputSection.addChildren(
      UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4,
        inputKnobs[0], inputKnobs[1], inputKnobs[2], inputKnobs[3]),
      UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4,
        inputKnobs[4], inputKnobs[5], inputKnobs[6], inputKnobs[7])
    );
  }

  private UI2dComponent controlRow(Selector selector) {
    return UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4,
      newIntegerBox(selector.numInputs, 34),
      newKnob(selector.select),
      UI2dContainer.newVerticalContainer(40, 2,
        newButton(selector.triggerPrev, 40).setTriggerable(true).setLabel("Prev"),
        newButton(selector.triggerNext, 40).setTriggerable(true).setLabel("Next")
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
