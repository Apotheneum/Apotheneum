package apotheneum.doved.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apotheneum.HeadlessLxTest;
import apotheneum.doved.modulators.ColorizeStyle;
import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.color.GradientUtils.BlendMode;
import heronarts.lx.effect.color.ColorizeEffect.ColorMode;
import heronarts.lx.effect.color.ColorizeEffect.FilterMode;
import heronarts.lx.effect.color.ColorizeEffect.SourceMode;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulator.SinLFO;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkedColorizeTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;

  private LX lx;
  private ColorizeStyle style;
  private LinkedColorize effect;

  @BeforeEach
  void setUp() {
    this.lx = newHeadlessLx();
    this.style = this.lx.engine.modulation.addModulator(new ColorizeStyle("Walls"));
    this.effect = new LinkedColorize(this.lx);
    this.effect.style.setValue(this.style);
  }

  @Test
  void appliesEveryColorizeSettingFromTheSelectedStyle() {
    this.style.source.setValue(SourceMode.AVERAGE);
    this.style.blendMode.setValue(BlendMode.HSVCW);
    this.style.colorMode.setValue(ColorMode.FIXED);
    this.style.color1.setColor(0xff102030);
    this.style.color2.setColor(0xffe0d0c0);
    this.style.gradientHue.setValue(180);
    this.style.gradientSaturation.setValue(-25);
    this.style.gradientBrightness.setValue(30);
    this.style.linkedHue.setValue(-120);
    this.style.linkedSaturation.setValue(40);
    this.style.linkedBrightness.setValue(-15);
    this.style.paletteIndex.setValue(2);
    this.style.paletteStops.setValue(3);
    this.style.paletteInvert.setValue(true);
    this.style.paletteDepth.setValue(.4);
    this.style.amount.setValue(.7);
    this.style.filterThreshold.setValue(.2);
    this.style.filterMode.setValue(FilterMode.CLEAR);

    this.effect.synchronizeStyle();

    assertEquals(SourceMode.AVERAGE, this.effect.source.getEnum());
    assertEquals(BlendMode.HSVCW, this.effect.blendMode.getEnum());
    assertEquals(ColorMode.FIXED, this.effect.colorMode.getEnum());
    assertEquals(this.style.color1.getColor(), this.effect.color1.getColor());
    assertEquals(this.style.color2.getColor(), this.effect.color2.getColor());
    assertEquals(180, this.effect.gradientHue.getValue(), EPSILON);
    assertEquals(-25, this.effect.gradientSaturation.getValue(), EPSILON);
    assertEquals(30, this.effect.gradientBrightness.getValue(), EPSILON);
    assertEquals(-120, this.effect.linkedHue.getValue(), EPSILON);
    assertEquals(40, this.effect.linkedSaturation.getValue(), EPSILON);
    assertEquals(-15, this.effect.linkedBrightness.getValue(), EPSILON);
    assertEquals(2, this.effect.paletteIndex.getValuei());
    assertEquals(3, this.effect.paletteStops.getValuei());
    assertTrue(this.effect.paletteInvert.isOn());
    assertEquals(.4, this.effect.paletteDepth.getValue(), EPSILON);
    assertEquals(.7, this.effect.amount.getValue(), EPSILON);
    assertEquals(.2, this.effect.filterThreshold.getValue(), EPSILON);
    assertEquals(FilterMode.CLEAR, this.effect.filterMode.getEnum());
  }

  @Test
  void tracksAStyleParameterAfterRealLXModulation() throws Exception {
    final SinLFO lfo = this.lx.engine.modulation.addModulator(new SinLFO(0, 1, 1000));
    lfo.start();
    this.lx.engine.modulation.addModulation(
      new LXCompoundModulation(this.lx.engine.modulation, lfo, this.style.amount));
    this.lx.engine.modulation.modulations.get(0).range.setValue(1);

    for (int i = 0; i < 5; ++i) {
      this.lx.engine.run();
    }
    this.effect.synchronizeStyle();

    assertTrue(this.style.amount.getValue() > 0,
      "the style amount must receive the real LX modulation");
    assertEquals(this.style.amount.getValue(), this.effect.amount.getValue(), EPSILON,
      "a linked effect must use the style's post-modulation value");
  }

  @Test
  void refreshesItsDropdownWhenStylesAreAddedOrRemoved() {
    assertEquals(2, this.effect.style.getObjects().length);
    assertSame(this.style, this.effect.style.getObjects()[1]);
    assertArrayEquals(new String[] { "No Style", "Walls" }, this.effect.style.getOptions());

    final ColorizeStyle heart = this.lx.engine.modulation.addModulator(new ColorizeStyle("Heart"));
    assertEquals(3, this.effect.style.getObjects().length);
    assertSame(this.style, this.effect.style.getObjects()[1]);
    assertSame(heart, this.effect.style.getObjects()[2]);
    assertArrayEquals(new String[] { "No Style", "Walls", "Heart" },
      this.effect.style.getOptions());
    heart.label.setValue("Heart Style");
    assertArrayEquals(new String[] { "No Style", "Walls", "Heart Style" },
      this.effect.style.getOptions());
    this.effect.style.setValue(heart);
    assertSame(heart, this.effect.style.getObject());

    this.lx.engine.modulation.removeModulator(heart);
    assertEquals(2, this.effect.style.getObjects().length);
    assertSame(this.style, this.effect.style.getObjects()[1]);
    assertArrayEquals(new String[] { "No Style", "Walls" }, this.effect.style.getOptions());
    assertFalse(this.effect.style.getObject() == heart,
      "a removed style must never remain selected by a linked effect");
  }

  @Test
  void styleAndSelectionSurviveSaveAndLoad() {
    this.style.amount.setValue(.63);
    this.style.color1.setColor(0xff123456);
    final JsonObject savedStyle = new JsonObject();
    final JsonObject savedEffect = new JsonObject();
    this.style.save(this.lx, savedStyle);
    this.effect.save(this.lx, savedEffect);

    final LX reopened = newHeadlessLx();
    final ColorizeStyle restoredStyle =
      reopened.engine.modulation.addModulator(new ColorizeStyle("Unrestored"));
    restoredStyle.load(reopened, savedStyle);
    final LinkedColorize restoredEffect = new LinkedColorize(reopened);
    restoredEffect.load(reopened, savedEffect);
    restoredEffect.synchronizeStyle();

    assertEquals("Walls", restoredStyle.getLabel());
    assertSame(restoredStyle, restoredEffect.style.getObject());
    assertEquals(.63, restoredEffect.amount.getValue(), EPSILON);
    assertEquals(0xff123456, restoredEffect.color1.getColor());
  }

  @Test
  void suppliesAFocusedEffectUI() {
    assertTrue(this.effect instanceof UIDeviceControls);
  }
}
