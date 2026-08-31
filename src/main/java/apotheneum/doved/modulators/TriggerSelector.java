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
 * <h2>Select proposes, the selection holds</h2>
 *
 * {@link #selected} is the selection. {@link #select} is a knob that <em>moves</em> it — by hand
 * or from a modulator — and does so only when it actually moves, never continuously. A pad
 * writes {@link #selected} directly. Whichever acted last wins, and nothing overwrites it on a
 * later frame.
 *
 * <p>That asymmetry is not incidental, it is the point, and this class shipped once without it.
 * Having the pads write into {@code select} so the index could be read back the way {@code
 * Selector} reads it looks equivalent and is not: LX modulation is additive over a parameter's
 * base value, so on a Select with anything mapped to it, a pad moved the base while the
 * modulation kept contributing, and the effective value read back kept landing in a different
 * band than the pad had asked for. The selection visibly flipped between two inputs. A pad
 * mapped to one input has to be able to say "input 3" and have that stand.
 *
 * <p>The cost is that the Select knob does not swing to show a pad's choice — it shows where
 * the knob is, which is what it is for. The activity lights under the pads are what report the
 * live selection, and they are correct in every case.
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

  /** How far {@link #select} must move before it is taken as a deliberate change. Small enough
   * that any real knob movement or modulation counts, large enough that float noise does not
   * quietly steal the selection back from a pad. */
  private static final double SELECT_EPSILON = 1e-9;

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
   * The manual/modulated way to choose an input, spread evenly over the active inputs, with the
   * same bands and detents as {@code Selector.select}.
   *
   * <p><b>This knob does not hold the selection; {@link #selected} does.</b> Moving it — by hand
   * or from a modulator — sets {@link #selected} to the band it lands in, and that is all it
   * ever does. It is read on change, never continuously.
   *
   * <p>That distinction is the whole design, and getting it wrong broke this on the rig. The
   * previous version had the pads write into this knob, so that {@code getSelectedIndex} could
   * read it back the way {@code Selector} does. LX modulation is additive over a parameter's
   * base value, so on a Select with any modulation mapped to it a pad press moved the base while
   * the modulation went on contributing — and the effective value, which is what was read back,
   * kept resolving to a different band than the pad had asked for. The selection appeared to
   * flip between two inputs. A pad has to be able to say "input 3" and have that stand,
   * regardless of what is mapped to Select.
   */
  public final CompoundParameter select =
    new CompoundParameter("Select", 0)
    .setDescription("Move to choose an input by hand or from a modulator; a pad press overrides it until it moves again");

  /**
   * The input actually being passed through — the authoritative selection, and the one thing
   * {@link #getSelectedIndex} reads.
   *
   * <p>Set by a {@link #selectInput} pad, by {@link #triggerPrev}/{@link #triggerNext}, and by
   * {@link #select} whenever that knob moves. Whichever acted last wins, and nothing overwrites
   * it on a later frame: a pad press stands until something else asks for a different input,
   * which is exactly what a pad mapped to one input needs. A real parameter rather than a field
   * so it saves with the project and is visible to snapshots and clips.
   */
  public final DiscreteParameter selected =
    new DiscreteParameter("Sel", 0, 0, MAX_INPUTS)
    .setDescription("Which input is currently passed through");

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

  /** {@link #select}'s value as of the last look, so {@link #applySelectKnob} can tell a
   * deliberate move from the knob simply sitting where it was. NaN until the first look. */
  private double lastSelect = Double.NaN;

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
    addParameter("selected", this.selected);
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
      clampSelection();
      updateLights();
    } else if (p == this.select) {
      // Covers a by-hand move immediately rather than waiting for the next frame's poll.
      // Modulation fires no listener here, which is why pollSelect exists as well.
      applySelectKnob();
      updateLights();
    } else if (p == this.selected) {
      updateLights();
    }
  }

  /** Shrinking Num past the live selection would otherwise leave it on an input no longer in
   * play, with the lights naming one the pads cannot reach. */
  private void clampSelection() {
    final int n = this.numInputs.getValuei();
    if (this.selected.getValuei() >= n) {
      this.selected.setValue(n - 1);
    }
  }

  /**
   * Takes the selection from {@link #select}, but only when that knob has actually moved since
   * it was last looked at.
   *
   * <p>"Only on change" is what lets a pad press stand. If this read Select every frame, a pad
   * asking for input 3 would be overwritten on the very next frame by whatever band Select
   * happened to sit in — which is the bug this class shipped with. Polling the value rather
   * than relying on {@link #onParameterChanged} is what makes a modulator mapped onto Select
   * work too: modulation contributes to a parameter's effective value without touching the base
   * value a listener fires on.
   */
  private void applySelectKnob() {
    final double value = this.select.getValue();
    if (!Double.isNaN(this.lastSelect) && (Math.abs(value - this.lastSelect) < SELECT_EPSILON)) {
      return;
    }
    this.lastSelect = value;
    final int band = bandOf(value);
    if (band != this.selected.getValuei()) {
      this.selected.setValue(band);
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
    if (input < this.numInputs.getValuei()) {
      // Straight to the authoritative selection. Deliberately NOT into Select: see that
      // parameter's javadoc for why writing there made a pad fight its own modulation.
      this.selected.setValue(input);
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
    this.selected.setValue(Math.floorMod(getSelectedIndex() + delta, n));
  }

  /** The input currently being passed through. */
  public int getSelectedIndex() {
    return LXUtils.constrain(this.selected.getValuei(), 0, this.numInputs.getValuei() - 1);
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
    applySelectKnob();
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
