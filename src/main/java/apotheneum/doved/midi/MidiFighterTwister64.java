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
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.midi.template.UIMidiTemplate;
import heronarts.lx.studio.ui.midi.template.UIMidiTemplateControls;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIButton;
import heronarts.glx.ui.component.UICollapsibleSection;
import heronarts.glx.ui.component.UIKnob;

/**
 * MIDI Fighter Twister template for all four configured encoder banks.
 *
 * <p>The hardware's saved configuration assigns CC and switch-note numbers
 * 0 through 63 across its four banks. LX's stock template intentionally
 * exposes only the first bank; this plugin template leaves that stock option
 * intact while making every configured control mappable.
 */
@LXMidiTemplate.Name("Twister 64")
@LXMidiTemplate.DeviceName("Midi Fighter Twister")
public class MidiFighterTwister64 extends LXMidiTemplate
  implements LXMidiTemplate.Bidirectional, UIMidiTemplateControls<MidiFighterTwister64> {

  public static final int NUM_KNOBS = 64;

  public static final int KNOB_CHANNEL = 0;
  public static final int SWITCH_CHANNEL = 1;

  private static final int BANK_COUNT = 4;
  private static final int KNOBS_PER_BANK = NUM_KNOBS / BANK_COUNT;
  private static final int COLUMNS = 4;
  private static final int COLUMN_WIDTH = 52;
  private static final int ROW_HEIGHT = 46;
  private static final int BANK_CONTENT_HEIGHT = 182;
  // UICollapsibleSection's content inset plus title bar, which are not public API.
  private static final int BANK_SECTION_CHROME_HEIGHT = 24;
  private static final int BANK_HEIGHT = BANK_CONTENT_HEIGHT + BANK_SECTION_CHROME_HEIGHT;

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

  /**
   * Output-only state for the switch LED. A value of 127 selects the active
   * color configured on the Twister; 0 restores its configured inactive color.
   */
  public class Light extends BooleanParameter {

    public final int index;

    private Light(int index) {
      super("L" + (index + 1));
      this.index = index;
      setDescription("Light " + (index + 1));
      addParameter("light-" + (index + 1), this);
    }
  }

  public final Knob[] knobs = new Knob[NUM_KNOBS];
  public final Switch[] switches = new Switch[NUM_KNOBS];
  public final Light[] lights = new Light[NUM_KNOBS];
  public final BooleanParameter[] bankExpanded = new BooleanParameter[BANK_COUNT];

  public MidiFighterTwister64(LX lx) {
    super(lx);
    for (int i = 0; i < NUM_KNOBS; ++i) {
      this.knobs[i] = new Knob(i);
      this.switches[i] = new Switch(i);
      this.lights[i] = new Light(i);
    }
    for (int bank = 0; bank < BANK_COUNT; ++bank) {
      this.bankExpanded[bank] = new BooleanParameter("Bank " + (bank + 1), bank == 0);
      addParameter("bank-" + (bank + 1) + "-expanded", this.bankExpanded[bank]);
    }
  }

  @Override
  public void buildMidiTemplateControls(UI ui, UIMidiTemplate uiTemplate,
    MidiFighterTwister64 template) {
    final UICollapsibleSection[] bankSections = new UICollapsibleSection[BANK_COUNT];
    uiTemplate.setLayout(UI2dContainer.Layout.VERTICAL, 2);
    for (int bank = 0; bank < BANK_COUNT; ++bank) {
      final int bankOffset = bank * KNOBS_PER_BANK;
      final UICollapsibleSection bankSection = new UICollapsibleSection(ui, 0, 0,
        uiTemplate.getContentWidth(), BANK_HEIGHT)
        .setExpandedParameter(template.bankExpanded[bank])
        .setTitle("BANK " + (bank + 1));
      bankSections[bank] = bankSection;
      for (int index = 0; index < KNOBS_PER_BANK; ++index) {
        final int x = (index % COLUMNS) * COLUMN_WIDTH;
        final int y = 2 + (index / COLUMNS) * ROW_HEIGHT;
        final int controlIndex = bankOffset + index;
        bankSection.addChildren(
          new UIKnob(x, y, template.knobs[controlIndex]),
          new UIButton(x + 41, y + 10, 10, 10, template.switches[controlIndex])
            .setLabel("")
            .setTriggerable(true)
            .setBorderRounding(5),
          new UIButton(x + 41, y + 24, 10, 10, template.lights[controlIndex])
            .setLabel("")
            .setBorderRounding(2));
      }
      uiTemplate.addChildren(bankSection);
      uiTemplate.addListener(template.bankExpanded[bank], parameter ->
        updateContentHeight(uiTemplate, bankSections));
    }
    updateContentHeight(uiTemplate, bankSections);
  }

  private static void updateContentHeight(UIMidiTemplate uiTemplate,
    UICollapsibleSection[] bankSections) {
    float height = 0;
    for (UICollapsibleSection bankSection : bankSections) {
      if (bankSection != null) {
        height += bankSection.getHeight();
      }
    }
    uiTemplate.setContentHeight(height + 2 * (BANK_COUNT - 1));
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    super.onParameterChanged(parameter);
    if (parameter instanceof Knob) {
      final Knob knob = (Knob) parameter;
      sendControlChange(KNOB_CHANNEL, knob.index, (int) Math.round(knob.getNormalized() * 127.));
    } else if (parameter instanceof Light) {
      final Light light = (Light) parameter;
      sendControlChange(SWITCH_CHANNEL, light.index, light.isOn() ? 127 : 0);
    }
  }

  @Override
  protected void initializeOutput() {
    for (Knob knob : this.knobs) {
      sendControlChange(KNOB_CHANNEL, knob.index, (int) Math.round(knob.getNormalized() * 127.));
    }
    for (Light light : this.lights) {
      sendControlChange(SWITCH_CHANNEL, light.index, light.isOn() ? 127 : 0);
    }
  }

  // Notes are received when the Twister returns to its normal control mode.
  private void setSwitch(MidiNote note, boolean on) {
    if (note.getChannel() == SWITCH_CHANNEL) {
      final int pitch = note.getPitch();
      if (pitch < NUM_KNOBS) {
        activateBank(pitch / KNOBS_PER_BANK);
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
      activateBank(knobIndex / KNOBS_PER_BANK);
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

  private void activateBank(int activeBank) {
    for (int bank = 0; bank < BANK_COUNT; ++bank) {
      this.bankExpanded[bank].setValue(bank == activeBank);
    }
  }
}
