package apotheneum.doved.modulators;

import java.util.HashMap;
import java.util.Map;

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIButton;
import heronarts.glx.ui.component.UIIndicator;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;
import heronarts.lx.utils.LXUtils;

/**
 * {@link Selector}, with triggers on screen instead of knobs.
 *
 * <p>Same modulator, same routing, same {@link #select} knob with the same bands and detents.
 * The only difference is what the panel gives you per input. {@code Selector} shows a value
 * knob for each input and, past four inputs, drops the per-input trigger buttons entirely to
 * fit its height budget. This one shows the trigger button for all eight and no value knobs.
 *
 * <h2>The triggers are filtered by Select, exactly as {@code Selector}'s are</h2>
 *
 * A trigger arriving on an input passes through to {@link #triggerOut} only while that input is
 * the selected one, and is blocked otherwise. With Select on input 1, a trigger on input 2 does
 * nothing at all; move Select to 2 and the same trigger passes. That is the whole modulator —
 * a filter on triggers, the way {@code Selector} is a filter on values.
 *
 * <p><b>A trigger never changes the selection.</b> {@link #select} is the only thing that
 * chooses an input, nothing in this class writes to it except {@link #triggerPrev}/
 * {@link #triggerNext}, and it is read live so a modulator mapped onto it moves the selection
 * the same way a hand on the knob does.
 *
 * <p>This is the mirror of {@link heronarts.lx.modulator.MultiTrig}, which fans one trigger out
 * to one of several destinations; nothing in LX collects several triggers down to one.
 *
 * <h2>Why a sibling class rather than more controls on Selector</h2>
 *
 * {@code Selector.COMPACT_ROW_SPACING}'s javadoc records that at eight inputs its layout already
 * sits exactly on {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator#MAX_CONTROLS_HEIGHT}'s
 * 126px floor — three knob-height rows at zero spacing, with the per-input trigger buttons and
 * lights dropped to get there — and that overflowing the cap is a silent crop rather than a
 * warning. Its panel cannot show eight knobs and eight trigger buttons. Dropping the knobs is
 * what buys the room here, which is also why this is a separate class rather than a mode on
 * that one: the two panels want opposite halves of the same modulator.
 */
@LXModulator.Global("Trigger Selector")
@LXModulator.Device("Trigger Selector")
@LXCategory(LXCategory.CORE)
public class TriggerSelector extends LXModulator
  implements LXNormalizedParameter, LXTriggerSource, LXOscComponent,
             UIModulatorControls<TriggerSelector> {

  /** Matches {@link Selector#MAX_INPUTS} so the two stay swappable. */
  public static final int MAX_INPUTS = Selector.MAX_INPUTS;

  /** Trigger buttons per row. Four across is what {@link UIKnob#WIDTH}-wide columns allow in a
   * {@code UIDeviceModulator}'s content width, the same limit {@code Selector} hit. */
  private static final int TRIGGERS_PER_ROW = 4;

  private static final int TRIGGER_HEIGHT = 16;
  private static final int INDICATOR_HEIGHT = 6;
  private static final int COLUMN_SPACING = 2;

  /**
   * Each input's value, passed through while that input is selected — the same parameters
   * {@code Selector.input} carries, kept so the two really are interchangeable. They have no
   * knob on this panel; map a modulator onto them, which is how {@code Selector}'s are driven
   * in practice anyway.
   */
  public final CompoundParameter[] input = new CompoundParameter[MAX_INPUTS];

  /**
   * One trigger per input. Fires {@link #triggerOut} only while its input is the selected one;
   * blocked otherwise. Identical in meaning to {@code Selector.triggerIn} — firing one does not
   * select it.
   */
  public final TriggerParameter[] triggerIn = new TriggerParameter[MAX_INPUTS];

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trig Out")
    .setDescription("Fires when the selected input's trigger fires; triggers on unselected inputs are blocked");

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

  /** One flag per input, true while that input is the one passing through. Status, not an
   * input: not mappable, since driving it from outside would claim a selection this modulator
   * is not making. Same shape as {@code Selector.activeInput}. */
  public final BooleanParameter[] activeInput = new BooleanParameter[MAX_INPUTS];

  /** Per built UI, so two panels for one modulator tear down independently — same reasoning as
   * {@code Selector.uiListeners}. */
  private final Map<UIModulator, LXParameterListener> uiListeners =
    new HashMap<UIModulator, LXParameterListener>();

  public TriggerSelector() {
    this("Trigger Selector");
  }

  public TriggerSelector(String label) {
    super(label);
    for (int i = 0; i < MAX_INPUTS; ++i) {
      final int index = i;
      this.input[i] = new CompoundParameter("In-" + (i + 1), 0)
        .setDescription("Input signal " + (i + 1));
      addParameter("input" + (i + 1), this.input[i]);

      this.triggerIn[i] = new TriggerParameter("Trig-" + (i + 1), () -> onTrigger(index))
        .setDescription("Trigger for input " + (i + 1) + "; passes through only while it is selected");
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
    setDescription("Passes one of several trigger inputs through, chosen by Select");
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

  /** Snap Select to the middle of each input's band, exactly as {@code Selector} does, so
   * dragging it lands on an input rather than near a boundary. */
  private void updateDetents() {
    final int n = this.numInputs.getValuei();
    final double[] detents = new double[n];
    for (int i = 0; i < n; ++i) {
      detents[i] = (i + .5) / n;
    }
    this.select.setDetentsNormalized(detents);
  }

  /**
   * A trigger arrived on one input. Passes it on only if that input is the selected one — the
   * filter this modulator exists to be. Reads the selection live rather than from the last
   * frame, for the reason {@code Selector.onTrigger} gives: triggers are dispatched between
   * frames, and a stopped modulator never runs {@link #computeValue} at all.
   */
  private void onTrigger(int input) {
    if (input == getSelectedIndex()) {
      this.triggerOut.trigger();
    }
  }

  /**
   * Step the base selection by the given number of inputs, wrapping — same as {@code
   * Selector.step}, including operating on the base value rather than the modulated one so a
   * modulation on Select cannot make a single press skip a band.
   */
  private void step(int delta) {
    final int n = this.numInputs.getValuei();
    final int to = Math.floorMod(bandOf(this.select.getBaseValue()) + delta, n);
    this.select.setValue((to + .5) / n);
  }

  /** Index of the input currently being passed through. Computed, not cached, for the reason
   * {@code Selector.getSelectedIndex} gives. */
  public int getSelectedIndex() {
    return bandOf(this.select.getValue());
  }

  /** The input a Select value lands on, spread evenly over the active inputs. */
  private int bandOf(double select) {
    final int n = this.numInputs.getValuei();
    return LXUtils.constrain((int) (select * n), 0, n - 1);
  }

  @Override
  public boolean isSnapshotControl(LXParameter parameter) {
    for (BooleanParameter active : this.activeInput) {
      if (parameter == active) {
        return false;
      }
    }
    return super.isSnapshotControl(parameter);
  }

  /** Keeps the lights honest on the frames a parameter change does not cover — a modulation
   * sweeping Select applies at read time and fires no listener, and a project load restores the
   * saved lights after restoring Select. */
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

  private void updateLights() {
    setLights(getSelectedIndex());
  }

  private void setLights(int index) {
    for (int i = 0; i < MAX_INPUTS; ++i) {
      this.activeInput[i].setValue(i == index);
    }
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  /**
   * Two rows of four trigger buttons, each with its activity light under it, over a
   * Num/Select/Prev/Next row.
   *
   * <p>Height, against
   * {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator#MAX_CONTROLS_HEIGHT}'s 126px:
   * each column is {@value #TRIGGER_HEIGHT} + {@value #COLUMN_SPACING} +
   * {@value #INDICATOR_HEIGHT} = 24px, two rows with 4px between them is 52px, and the control
   * row is {@link UIKnob#HEIGHT} at 42px — 98px with the section spacing, inside the cap. The
   * headroom is bought by having no per-input value knobs; {@code Selector} spends its whole
   * budget on those and has none left, which is why the two are separate panels.
   *
   * <p>One layout, so unlike {@code Selector} this never rebuilds — the {@link #numInputs}
   * listener only dims buttons past Num. Dimmed rather than hidden so a Num change does not
   * reflow the grid under the performer's finger.
   */
  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, TriggerSelector selector) {
    final UIButton[] triggers = new UIButton[MAX_INPUTS];
    final UI2dComponent[] rows = new UI2dComponent[MAX_INPUTS / TRIGGERS_PER_ROW];

    for (int row = 0; row < rows.length; ++row) {
      final UI2dComponent[] columns = new UI2dComponent[TRIGGERS_PER_ROW];
      for (int column = 0; column < TRIGGERS_PER_ROW; ++column) {
        final int i = row * TRIGGERS_PER_ROW + column;
        triggers[i] = newButton(selector.triggerIn[i], UIKnob.WIDTH)
          .setTriggerable(true)
          .setLabel(Integer.toString(i + 1));
        triggers[i].setHeight(TRIGGER_HEIGHT);
        columns[column] = UI2dContainer.newVerticalContainer(UIKnob.WIDTH, COLUMN_SPACING,
          triggers[i],
          // Read-only: the light reports which input Select has chosen. The button above it
          // fires that input's trigger, which passes only while this light is on.
          new UIIndicator(ui, 0, 0, UIKnob.WIDTH, INDICATOR_HEIGHT, selector.activeInput[i])
            .setClickable(false)
        );
      }
      rows[row] = UI2dContainer.newHorizontalContainer(
        TRIGGER_HEIGHT + COLUMN_SPACING + INDICATOR_HEIGHT, 4, columns);
    }

    uiModulator.setLayout(UI2dContainer.Layout.VERTICAL, 4);
    uiModulator.addChildren(
      UI2dContainer.newVerticalContainer(172, 4, rows),
      UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4,
        newIntegerBox(selector.numInputs, 34),
        newKnob(selector.select),
        UI2dContainer.newVerticalContainer(40, 2,
          newButton(selector.triggerPrev, 40).setTriggerable(true).setLabel("Prev"),
          newButton(selector.triggerNext, 40).setTriggerable(true).setLabel("Next")
        )
      )
    );

    final LXParameterListener listener = p -> {
      final int n = selector.numInputs.getValuei();
      for (int i = 0; i < triggers.length; ++i) {
        triggers[i].setEnabled(i < n);
      }
    };
    listener.onParameterChanged(selector.numInputs);
    selector.numInputs.addListener(listener);
    this.uiListeners.put(uiModulator, listener);
  }

  @Override
  public void disposeModulatorControls(UI ui, UIModulator uiModulator, TriggerSelector selector) {
    final LXParameterListener listener = this.uiListeners.remove(uiModulator);
    if (listener != null) {
      selector.numInputs.removeListener(listener);
    }
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    // Matching Selector and SampleHold: the value is a function of the inputs and Select, so
    // anything written here is replaced on the next frame.
    throw new UnsupportedOperationException(
      "TriggerSelector value comes from its selected input; it cannot be set directly");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }
}
