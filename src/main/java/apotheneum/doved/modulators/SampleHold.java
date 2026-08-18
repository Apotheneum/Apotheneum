package apotheneum.doved.modulators;

import com.google.gson.JsonObject;

import heronarts.glx.ui.UI2dContainer;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;

/**
 * Samples an incoming modulation signal on a trigger and holds that value until the next
 * trigger.
 *
 * <p>Map any modulation source into {@link #signal} (it is a {@link CompoundParameter}, so
 * it is a modulation target), map a trigger into {@link #sample}, and use this modulator
 * as a modulation source wherever you want the stepped result. The output only moves at
 * trigger time, so a signal that drifts continuously — an LFO, an envelope follower, a
 * knob being swept — reaches its destination quantized to whatever fires the trigger.
 *
 * <p>This is the classic modular / Bitwig-style sample and hold. It differs from
 * {@link heronarts.lx.modulator.Randomizer} and {@link heronarts.lx.modulator.Stepper},
 * which also advance on an external trigger but emit a value they generate or have
 * configured; this one captures whatever is arriving on its input at that instant.
 *
 * <p>Sampling works whether or not the modulator is running: the captured value is applied
 * immediately rather than waiting on {@link #loop(double)}, which a stopped modulator never
 * receives. That matters on a rig where a modulator can easily be left stopped.
 *
 * <p>The held value is saved with the project and restored on load, so reopening a show does
 * not silently drop whatever this was driving back to zero until the next trigger.
 */
@LXModulator.Global("Sample & Hold")
@LXModulator.Device("Sample & Hold")
@LXCategory(LXCategory.CORE)
public class SampleHold extends LXModulator implements LXNormalizedParameter, LXTriggerSource,
  LXOscComponent, UIModulatorControls<SampleHold> {

  public final CompoundParameter signal =
    new CompoundParameter("Signal", 0)
    .setDescription("Signal to be sampled; map a modulation source here");

  public final TriggerParameter sample =
    new TriggerParameter("Sample")
    .setDescription("Captures the current signal value and holds it");

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trigger Out")
    .setDescription("Fires whenever a new value is sampled");

  private static final String KEY_HELD = "held";

  /**
   * The held sample. Not a registered parameter — it is not user-facing and nothing should
   * be able to map or set it directly — so it is serialized explicitly below.
   * {@link LXModulator#getValue()} cannot stand in for it: the modulator's value is a plain
   * field, not a parameter, so {@link heronarts.lx.LXComponent#save} never writes it.
   */
  private double held = 0;

  public SampleHold() {
    this("Sample & Hold");
  }

  public SampleHold(String label) {
    super(label);
    this.sample.onTrigger(this::takeSample);

    addParameter("signal", this.signal);
    addParameter("sample", this.sample);
    addParameter("triggerOut", this.triggerOut);
    setDescription("Samples a modulation signal on each trigger and holds it");
  }

  private void takeSample() {
    this.held = this.signal.getValue();
    // Apply immediately rather than waiting for the next loop(), so a stopped modulator
    // still tracks its triggers.
    setValue(this.held);
    this.triggerOut.trigger();
  }

  @Override
  protected double computeValue(double deltaMs) {
    return this.held;
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_HELD, this.held);
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    // Absent key means a reset to defaults (LXSerializable.Utils.resetObject loads an empty
    // object) or a project written before this was persisted — both want zero, not whatever
    // this instance happened to be holding.
    this.held = obj.has(KEY_HELD) ? obj.get(KEY_HELD).getAsDouble() : 0;
    // The value is what downstream modulation reads, and it is not restored by super.load().
    // Deliberately no triggerOut here: reopening a project is not a sample event.
    setValue(this.held);
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException(
      "SampleHold value comes from its signal input; it cannot be set directly");
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, SampleHold sampleHold) {
    uiModulator.setLayout(UI2dContainer.Layout.HORIZONTAL, 4);
    uiModulator.addChildren(
      newKnob(sampleHold.signal),
      newButton(sampleHold.sample, 56).setTriggerable(true).setLabel("Sample")
    );
  }

}
