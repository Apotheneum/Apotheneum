package apotheneum.doved.midi;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;

/** Registers Apotheneum MIDI templates that LX does not discover automatically. */
@LXPlugin.Name("Apotheneum MIDI Templates")
public class ApotheneumMidiTemplatesPlugin implements LXPlugin {

  @Override
  public void initialize(LX lx) {
    lx.engine.midi.registerTemplate(MidiFighterTwister64.class);
  }
}
