/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
 *
 * This file is derived from DJTTMidiFighterTwister in LX Studio. By using
 * LX, you agree to the terms of the LX Studio Software License and
 * Distribution Agreement, available at: http://lx.studio/license.
 */

package apotheneum.doved.midi;

import heronarts.lx.LX;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.template.LXMidiTemplate;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * MIDI Fighter Twister template for all four configured encoder banks.
 *
 * <p>The hardware's saved configuration assigns CC and switch-note numbers
 * 0 through 63 across its four banks. LX's stock template intentionally
 * exposes only the first bank; this plugin template leaves that stock option
 * intact while making every configured control mappable.
 */
@LXMidiTemplate.Name("DJTT Midi Fighter Twister (64)")
@LXMidiTemplate.DeviceName("Midi Fighter Twister")
public class MidiFighterTwister64 extends LXMidiTemplate implements LXMidiTemplate.Bidirectional {

  public static final int NUM_KNOBS = 64;

  public static final int KNOB_CHANNEL = 0;
  public static final int SWITCH_CHANNEL = 1;

  public class Knob extends BoundedParameter {

    public final int index;

    private Knob(int index) {
      super("K" + (index + 1));
      this.index = index;
      setUnits(BoundedParameter.Units.PERCENT_NORMALIZED);
      setDescription("Knob " + (index + 1));
      addParameter("knob-" + (index + 1), this);
    }
  }

  public class Switch extends BooleanParameter {

    public final int index;

    private Switch(int index) {
      super("S" + (index + 1));
      this.index = index;
      setMode(BooleanParameter.Mode.MOMENTARY);
      setDescription("Switch " + (index + 1));
      addParameter("Switch-" + (index + 1), this);
    }
  }

  public final Knob[] knobs = new Knob[NUM_KNOBS];
  public final Switch[] switches = new Switch[NUM_KNOBS];

  public MidiFighterTwister64(LX lx) {
    super(lx);
    for (int i = 0; i < NUM_KNOBS; ++i) {
      this.knobs[i] = new Knob(i);
      this.switches[i] = new Switch(i);
    }
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    super.onParameterChanged(parameter);
    if (parameter instanceof Knob) {
      final Knob knob = (Knob) parameter;
      sendControlChange(KNOB_CHANNEL, knob.index, (int) Math.round(knob.getNormalized() * 127.));
    } else if (parameter instanceof Switch) {
      final Switch control = (Switch) parameter;
      sendControlChange(SWITCH_CHANNEL, control.index, control.isOn() ? 127 : 0);
    }
  }

  @Override
  protected void initializeOutput() {
    for (Knob knob : this.knobs) {
      sendControlChange(KNOB_CHANNEL, knob.index, (int) Math.round(knob.getNormalized() * 127.));
    }
    for (Switch control : this.switches) {
      sendControlChange(SWITCH_CHANNEL, control.index, control.isOn() ? 127 : 0);
    }
  }

  // Notes are received when the Twister returns to its normal control mode.
  private void setSwitch(MidiNote note, boolean on) {
    if (note.getChannel() == SWITCH_CHANNEL) {
      final int pitch = note.getPitch();
      if (pitch < NUM_KNOBS) {
        this.switches[pitch].setValue(on);
      }
    }
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    setSwitch(note, true);
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    setSwitch(note, false);
  }

  @Override
  public void midiPanicReceived() {
    for (Switch control : this.switches) {
      control.setValue(false);
    }
  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {
    // These CCs are received by the Midi Fighter Utility Factory Reset option.
    final int knobIndex = cc.getCC();
    if (knobIndex < NUM_KNOBS) {
      switch (cc.getChannel()) {
      case KNOB_CHANNEL:
        this.knobs[knobIndex].setNormalized(cc.getNormalized());
        break;
      case SWITCH_CHANNEL:
        this.switches[knobIndex].setValue(cc.getValue() > 0);
        break;
      default:
        break;
      }
    }
  }
}
