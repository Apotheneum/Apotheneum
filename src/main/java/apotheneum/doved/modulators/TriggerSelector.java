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
 * {@link Selector}, selected by pressing a pad instead of by turning a knob.
 *
 * <p>{@code Selector} routes triggers already — each input has a {@code Trig} of its own and a
 * fire on the selected one passes through — but the <em>selection</em> is a
 * {@link CompoundParameter}, so choosing an input means turning Select to the right band or
 * stepping Prev/Next to it. That is the wrong gesture for a performer with a pad grid in front
 * of them: reaching input 6 costs five presses of Next, or a knob sweep across every input in
 * between, and both pass audibly through the inputs they cross. This class gives every input a
 * {@link #selectInput} trigger, so input 6 is one press and nothing in between is ever
 * selected.
 *
 * <h2>Why a sibling class rather than more controls on Selector</h2>
 *
 * These triggers could have been added to {@code Selector} itself — they are ordinary
 * parameters, and nothing in its routing would have had to change. Its panel is what makes that
 * impossible: {@code Selector.COMPACT_ROW_SPACING}'s javadoc records that at eight inputs its
 * layout is already exactly at
 * {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator#MAX_CONTROLS_HEIGHT}'s 126px floor
 * — three knob-height rows at zero spacing, with the per-input trigger buttons and lights
 * already dropped to get there — and that overflowing that cap is not a warning but a silent
 * crop, drawing controls outside the panel where they never appear. There is no room for eight
 * more buttons. Splitting the two apart also keeps each one's panel honest about what it is:
 * one is knob-selected with per-input triggers, the other is pad-selected.
 *
 * <p>The two are otherwise the same modulator and route identically, so a patch can swap one
 * for the other without rethinking anything downstream.
 *
 * <h2>The pads move Select; they do not replace it</h2>
 *
 * {@link #select} is the same knob {@code Selector} has, with the same bands and the same
 * detents, and {@link #getSelectedIndex} reads it the same way. A pad press sets it to its
 * input's band centre — the identical value Prev/Next lands on — so the knob always shows what
 * is selected, a modulator can still sweep it, and a pad press and a step are indistinguishable
 * afterwards. An earlier version of this class held the selection in a separate discrete
 * parameter instead and dropped the knob entirely; that left the panel with no way to see or
 * sweep the selection, and made a modulator advertised as swappable with {@code Selector}
 * behave unlike it.
 */
@LXModulator.Global("Trigger Selector")
@LXModulator.Device("Trigger Selector")
@LXCategory(LXCategory.CORE)
public class TriggerSelector extends LXModulator
  implements LXNormalizedParameter, LXTriggerSource, LXOscComponent,
             UIModulatorControls<TriggerSelector> {

  /** Matches {@link Selector#MAX_INPUTS} so the two stay swappable. Eight pads fit this
   * panel comfortably — see {@link #buildModulatorControls} for the height arithmetic. */
  public static final int MAX_INPUTS = Selector.MAX_INPUTS;

  /** Pads per row. Four across is what {@link UIKnob#WIDTH}-wide columns allow in a
   * {@code UIDeviceModulator}'s content width, the same limit {@code Selector} hit. */
  private static final int PADS_PER_ROW = 4;

  private static final int PAD_HEIGHT = 16;
  private static final int INDICATOR_HEIGHT = 6;
  private static final int PAD_COLUMN_SPACING = 2;

  public final CompoundParameter[] input = new CompoundParameter[MAX_INPUTS];

  /**
   * Selects its input, immediately. The control this class exists for.
   *
   * <p>Distinct from {@link #triggerIn}: this one changes <em>which</em> input is passing
   * through, that one is a signal arriving <em>on</em> an input and is passed through only
   * while that input is already selected. A pad grid usually wants both wired to the same
   * physical pad, which is what {@link #fireOnSelect} is for.
   */
  public final TriggerParameter[] selectInput = new TriggerParameter[MAX_INPUTS];

  /** One trigger per input; only the selected one reaches {@link #triggerOut}. Identical in
   * meaning to {@code Selector.triggerIn}, so the two route the same way. */
  public final TriggerParameter[] triggerIn = new TriggerParameter[MAX_INPUTS];

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trig Out")
    .setDescription("Fires when the selected input's trigger fires, or when a pad selects an input while Fire is on");

  /**
   * Whether pressing a {@link #selectInput} pad also fires {@link #triggerOut}.
   *
   * <p>On — the default — one press both switches the source and announces the switch
   * downstream, which is what a pad grid driving a shot rack wants: the pad is the event. Off
   * makes selection silent, for a patch that wants to arm an input ahead of a trigger arriving
   * on it separately.
   */
  public final BooleanParameter fireOnSelect =
    new BooleanParameter("Fire", true)
    .setDescription("Whether selecting an input with a pad also fires the output trigger");

  public final DiscreteParameter numInputs =
    new DiscreteParameter("Num", MAX_INPUTS, 1, MAX_INPUTS + 1)
    .setDescription("How many inputs the selector spans");

  /**
   * Which input is passed through, spread evenly over the active inputs — the same control,
   * with the same meaning and the same detents, as {@code Selector.select}.
   *
   * <p>This class first stored the selection as a {@link DiscreteParameter} of its own instead,
   * on the reasoning that a pad-selected modulator has no knob to derive it from. That was
   * wrong twice over. It cost the panel its Select knob, so the selection could be pressed but
   * not seen or swept; and it meant this modulator's selection did not behave like {@code
   * Selector}'s despite the two being advertised as swappable. The pads now do exactly what
   * Prev/Next already did — move this knob — so there is one notion of "which input" here,
   * not two.
   */
  public final CompoundParameter select =
    new CompoundParameter("Select", 0)
    .setDescription("Which input is passed through, spread evenly over the active inputs");

  public final TriggerParameter triggerNext =
    new TriggerParameter("Next", () -> step(1))
    .setDescription("Advance to the next input, wrapping around");

  public final TriggerParameter triggerPrev =
    new TriggerParameter("Prev", () -> step(-1))
    .setDescription("Return to the previous input, wrapping around");

  /** One flag per input, true while that input is the one passing through — same shape and
   * purpose as {@code Selector.activeInput}, and read-only for the same reason. */
  public final BooleanParameter[] activeInput = new BooleanParameter[MAX_INPUTS];

  /** Per built UI, so two panels for one modulator tear down independently. Same reasoning as
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

      this.selectInput[i] = new TriggerParameter("Sel-" + (i + 1), () -> onSelect(index))
        .setDescription("Select input " + (i + 1));
      addParameter("selectInput" + (i + 1), this.selectInput[i]);

      this.triggerIn[i] = new TriggerParameter("Trig-" + (i + 1), () -> onTrigger(index))
        .setDescription("Trigger for input " + (i + 1) + "; passes through while it is selected");
      addParameter("triggerIn" + (i + 1), this.triggerIn[i]);

      this.activeInput[i] = new BooleanParameter("Active-" + (i + 1), i == 0)
        .setMappable(false)
        .setDescription("Whether input " + (i + 1) + " is the one passing through");
    }
    addParameter("numInputs", this.numInputs);
    addParameter("select", this.select);
    addParameter("fireOnSelect", this.fireOnSelect);
    addParameter("triggerOut", this.triggerOut);
    addParameter("triggerNext", this.triggerNext);
    addParameter("triggerPrev", this.triggerPrev);
    for (int i = 0; i < MAX_INPUTS; ++i) {
      addParameter("activeInput" + (i + 1), this.activeInput[i]);
    }
    setDescription("Passes one of several modulated inputs through, selected by pressing a pad");
    updateDetents();
    updateLights();
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

  /** Snap the Select knob to the middle of each input's band, exactly as {@code Selector}
   * does, so dragging it lands on an input rather than near a boundary. */
  private void updateDetents() {
    final int n = this.numInputs.getValuei();
    final double[] detents = new double[n];
    for (int i = 0; i < n; ++i) {
      detents[i] = (i + .5) / n;
    }
    this.select.setDetentsNormalized(detents);
  }

  /**
   * A pad was pressed. Selects unconditionally, then fires downstream if {@link #fireOnSelect}
   * is on — including when the pad names the input that was already selected, since re-pressing
   * a pad is a deliberate re-fire, not a no-op.
   */
  private void onSelect(int input) {
    final int n = this.numInputs.getValuei();
    if (input < n) {
      // The band's centre, which is also its detent -- the same value Prev/Next lands on, so a
      // pad press and a step are indistinguishable afterwards.
      this.select.setValue((input + .5) / n);
    }
    if (this.fireOnSelect.isOn()) {
      this.triggerOut.trigger();
    }
  }

  /**
   * A trigger arrived on one input. Reads the selection live rather than from the last frame,
   * for the reason {@code Selector.onTrigger} gives: triggers are dispatched between frames and
   * a stopped modulator never runs {@link #computeValue}.
   */
  private void onTrigger(int input) {
    if (input == getSelectedIndex()) {
      this.triggerOut.trigger();
    }
  }

  /**
   * Step the base selection by the given number of inputs, wrapping — same as {@code
   * Selector.step}, including operating on the base value rather than the modulated one, so a
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

  /** Keeps the lights honest on the frames a parameter change does not cover — a modulator
   * driving {@link #selected}, and a project load restoring lights after the selection. */
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
   * Two rows of four pads, each with the activity light under it, over a Num/Fire/Prev/Next
   * row.
   *
   * <p>Height, against
   * {@link heronarts.lx.studio.ui.modulation.UIDeviceModulator#MAX_CONTROLS_HEIGHT}'s 126px:
   * each pad column is {@value #PAD_HEIGHT} + {@value #PAD_COLUMN_SPACING} +
   * {@value #INDICATOR_HEIGHT} = 24px, two rows with 4px between them is 52px, and the control
   * row is {@link UIKnob#HEIGHT} at 42px — 98px with the 4px section spacing, comfortably
   * inside the cap. That headroom is the whole reason this is a separate panel: {@code
   * Selector} spends its entire budget on eight knobs and has none left, as its {@code
   * COMPACT_ROW_SPACING} javadoc records.
   *
   * <p>Unlike {@code Selector} this never rebuilds between layouts — there is one layout — so
   * the {@link #numInputs} listener only dims pads past Num rather than tearing anything down,
   * and none of the {@code ConcurrentModificationException} care that method needs applies.
   * Dimmed rather than hidden, for the same reason: a row that reflows on every Num change
   * would shift every pad under the performer's finger.
   */
  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, TriggerSelector selector) {
    final UIButton[] pads = new UIButton[MAX_INPUTS];
    final UI2dComponent[] rows = new UI2dComponent[MAX_INPUTS / PADS_PER_ROW];

    for (int row = 0; row < rows.length; ++row) {
      final UI2dComponent[] columns = new UI2dComponent[PADS_PER_ROW];
      for (int column = 0; column < PADS_PER_ROW; ++column) {
        final int i = row * PADS_PER_ROW + column;
        pads[i] = newButton(selector.selectInput[i], UIKnob.WIDTH)
          .setTriggerable(true)
          .setLabel(Integer.toString(i + 1));
        pads[i].setHeight(PAD_HEIGHT);
        columns[column] = UI2dContainer.newVerticalContainer(UIKnob.WIDTH, PAD_COLUMN_SPACING,
          pads[i],
          // Read-only: the light reports the selection, the pad above it makes one.
          new UIIndicator(ui, 0, 0, UIKnob.WIDTH, INDICATOR_HEIGHT, selector.activeInput[i])
            .setClickable(false)
        );
      }
      rows[row] = UI2dContainer.newHorizontalContainer(
        PAD_HEIGHT + PAD_COLUMN_SPACING + INDICATOR_HEIGHT, 4, columns);
    }

    uiModulator.setLayout(UI2dContainer.Layout.VERTICAL, 4);
    uiModulator.addChildren(
      UI2dContainer.newVerticalContainer(172, 4, rows),
      UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4,
        newIntegerBox(selector.numInputs, 34),
        newKnob(selector.select),
        newButton(selector.fireOnSelect, 34).setLabel("Fire"),
        UI2dContainer.newVerticalContainer(40, 2,
          newButton(selector.triggerPrev, 40).setTriggerable(true).setLabel("Prev"),
          newButton(selector.triggerNext, 40).setTriggerable(true).setLabel("Next")
        )
      )
    );

    final LXParameterListener listener = p -> {
      final int n = selector.numInputs.getValuei();
      for (int i = 0; i < pads.length; ++i) {
        pads[i].setEnabled(i < n);
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
    // Matching Selector and SampleHold: the value is a function of the inputs and the
    // selection, so anything written here is replaced on the next frame.
    throw new UnsupportedOperationException(
      "TriggerSelector value comes from its selected input; it cannot be set directly");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }
}
