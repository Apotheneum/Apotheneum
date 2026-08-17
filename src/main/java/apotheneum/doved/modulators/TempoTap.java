package apotheneum.doved.modulators;

import heronarts.glx.ui.UI2dContainer;
import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;

/**
 * A bindable trigger button that taps the master tempo.
 *
 * <p>Chromatik's own TAP button is not a mappable target, and tap tempo is otherwise only
 * reachable from a dedicated control surface. Map a MIDI note from any momentary button
 * onto {@link #tap} and each press taps the tempo, exactly as clicking TAP by hand does.
 */
@LXModulator.Global("Tempo Tap")
@LXModulator.Device("Tempo Tap")
@LXCategory(LXCategory.TRIGGER)
public class TempoTap extends LXModulator implements LXOscComponent, UIModulatorControls<TempoTap> {

  public final TriggerParameter tap =
    new TriggerParameter("Tap", () -> tapTempo())
    .setDescription("Tap repeatedly to set the master tempo");

  public TempoTap() {
    this("Tempo Tap");
  }

  public TempoTap(String label) {
    super(label);
    addParameter("tap", this.tap);
    setMappingSource(false);
  }

  /** Taps the master tempo. Overridable so tests can observe the call. */
  protected void tapTempo() {
    this.lx.engine.tempo.tap();
  }

  @Override
  protected double computeValue(double deltaMs) {
    // Not relevant, this modulator exists only to hold a bindable trigger
    return 0;
  }

  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, TempoTap tempoTap) {
    // Without this the modulator pane falls back to UIModulatorControls.Missing and logs
    // "No UI implementation found for type: TempoTap".
    uiModulator.setLayout(UI2dContainer.Layout.HORIZONTAL, 4);
    uiModulator.addChildren(
      newButton(tempoTap.tap, 60).setTriggerable(true)
    );
  }

}
