package apotheneum.doved.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.studio.ui.midi.template.UIMidiTemplateControls;

class MidiFighterTwister64Test extends HeadlessLxTest {

  private record ControlChange(int channel, int controller, int value) {}

  private static class RecordingTemplate extends MidiFighterTwister64 {

    private final List<ControlChange> output = new ArrayList<ControlChange>();

    private RecordingTemplate(LX lx) {
      super(lx);
    }

    @Override
    protected void sendControlChange(int channel, int controller, int value) {
      this.output.add(new ControlChange(channel, controller, value));
    }

    private void synchronizeOutput() {
      initializeOutput();
    }
  }

  @Test
  void exposesAndReceivesAllFourBanks() throws InvalidMidiDataException {
    final MidiFighterTwister64 template = new MidiFighterTwister64(newHeadlessLx());

    assertEquals(64, template.knobs.length);
    assertEquals(64, template.switches.length);
    assertEquals(64, template.lights.length);
    assertTrue(template instanceof UIMidiTemplateControls<?>);
    for (int index = 0; index < MidiFighterTwister64.NUM_KNOBS; ++index) {
      assertEquals(template.knobs[index], template.getParameter("knob-" + (index + 1)));
      assertEquals(template.switches[index], template.getParameter("Switch-" + (index + 1)));
      assertEquals(template.lights[index], template.getParameter("light-" + (index + 1)));

      final int value = (index * 2) % 128;
      template.controlChangeReceived(new MidiControlChange(MidiFighterTwister64.KNOB_CHANNEL, index, value));
      assertEquals(value / 127., template.knobs[index].getNormalized());
      assertBankExpanded(template, index / 16);
      template.controlChangeReceived(new MidiControlChange(MidiFighterTwister64.SWITCH_CHANNEL, index, 127));
      assertTrue(template.switches[index].isOn());
    }
  }

  @Test
  void receivesSwitchNotesFromEveryBankAndPanicClearsThem() throws InvalidMidiDataException {
    final MidiFighterTwister64 template = new MidiFighterTwister64(newHeadlessLx());
    final int[] bankStarts = { 0, 16, 32, 48 };

    for (int index : bankStarts) {
      template.noteOnReceived(new MidiNoteOn(MidiFighterTwister64.SWITCH_CHANNEL, index, 127));
      assertTrue(template.switches[index].isOn());
      assertBankExpanded(template, index / 16);
      template.noteOffReceived(MidiNote.constructMutable(0x80, MidiFighterTwister64.SWITCH_CHANNEL, index, 0));
      assertFalse(template.switches[index].isOn());
    }

    for (int index : bankStarts) {
      template.noteOnReceived(new MidiNoteOn(MidiFighterTwister64.SWITCH_CHANNEL, index, 127));
    }
    template.midiPanicReceived();
    for (MidiFighterTwister64.Switch control : template.switches) {
      assertFalse(control.isOn());
    }
  }

  @Test
  void keepsSwitchInputsSeparateFromOnOffLightFeedback() {
    final RecordingTemplate template = new RecordingTemplate(newHeadlessLx());

    for (int index = 0; index < MidiFighterTwister64.NUM_KNOBS; ++index) {
      template.knobs[index].setNormalized(index / 63.);
      template.switches[index].setValue((index % 2) == 0);
      template.lights[index].setValue((index % 3) == 0);
    }
    template.output.clear();

    template.synchronizeOutput();

    assertEquals(128, template.output.size());
    for (int index = 0; index < MidiFighterTwister64.NUM_KNOBS; ++index) {
      assertEquals(new ControlChange(MidiFighterTwister64.KNOB_CHANNEL, index, (int) Math.round(index / 63. * 127.)), template.output.get(index));
      assertEquals(new ControlChange(MidiFighterTwister64.SWITCH_CHANNEL, index, (index % 3) == 0 ? 127 : 0), template.output.get(index + MidiFighterTwister64.NUM_KNOBS));
    }
  }

  @Test
  void sendsOnlyLightsAsSwitchFeedback() {
    final RecordingTemplate template = new RecordingTemplate(newHeadlessLx());

    template.switches[0].setValue(true);
    assertTrue(template.output.isEmpty());

    template.lights[0].setValue(true);
    template.lights[0].setValue(false);
    assertEquals(List.of(
      new ControlChange(MidiFighterTwister64.SWITCH_CHANNEL, 0, 127),
      new ControlChange(MidiFighterTwister64.SWITCH_CHANNEL, 0, 0)), template.output);
  }

  @Test
  void registersTheTemplateWithLx() {
    final LX lx = newHeadlessLx();

    new ApotheneumMidiTemplatesPlugin().initialize(lx);

    assertTrue(lx.engine.midi.getRegisteredTemplateClasses().contains(MidiFighterTwister64.class));
  }

  private static void assertBankExpanded(MidiFighterTwister64 template, int expectedBank) {
    for (int bank = 0; bank < template.bankExpanded.length; ++bank) {
      assertEquals(bank == expectedBank, template.bankExpanded[bank].isOn());
    }
  }
}
