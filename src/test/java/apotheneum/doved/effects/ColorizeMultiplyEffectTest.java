package apotheneum.doved.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.ModelBuffer;
import heronarts.lx.color.GradientUtils.BlendMode;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.effect.color.ColorizeEffect;
import heronarts.lx.utils.LXUtils;

class ColorizeMultiplyEffectTest extends HeadlessLxTest {

  @ParameterizedTest
  @ValueSource(doubles = { 0, .25, .5, .75, 1 })
  void zeroBrightnessStaysBlackAtEveryDepth(double depth) {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    configureThreeColorPalette(lx, effect);
    effect.depth.setValue(depth);

    assertEquals(0x7f000000, apply(effect, 0x7f000000));
  }

  @Test
  void fixedSaturatedFirstStopStillMapsExactZeroToBlack() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.color1.setColor(LXColor.RED);
    effect.color2.setColor(LXColor.BLUE);
    effect.depth.setValue(0);

    assertEquals(0xff000000, apply(effect, 0xff000000));
  }

  @Test
  void luminosityZeroStillMapsToBlack() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.source.setValue(ColorizeMultiplyEffect.SourceMode.LUMINOSITY);
    effect.color1.setColor(LXColor.RED);
    effect.color2.setColor(LXColor.BLUE);
    effect.depth.setValue(0);

    assertEquals(0x4d000000, apply(effect, 0x4d000000));
  }

  @ParameterizedTest
  @ValueSource(ints = { 16, 64, 128, 224, 255 })
  void depthZeroMatchesColorizeRgbForOpaqueNonzeroBrightness(int value) {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.depth.setValue(0);
    effect.color1.setColor(0xffff3300);
    effect.color2.setColor(0xff0066ff);

    ColorizeEffect stock = new ColorizeEffect(lx);
    stock.setDamping(false);
    stock.color1.setColor(0xffff3300);
    stock.color2.setColor(0xff0066ff);

    int input = LXColor.rgba(value, value, value, 255);
    assertEquals(apply(stock, input) & LXColor.RGB_MASK, apply(effect, input) & LXColor.RGB_MASK);
  }

  @ParameterizedTest
  @EnumSource(BlendMode.class)
  void depthZeroMatchesColorizeRgbAcrossBlendModes(BlendMode blendMode) {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.depth.setValue(0);
    effect.blendMode.setValue(blendMode);
    effect.color1.setColor(0xffff3300);
    effect.color2.setColor(0xff0066ff);

    ColorizeEffect stock = new ColorizeEffect(lx);
    stock.setDamping(false);
    stock.blendMode.setValue(blendMode);
    stock.color1.setColor(0xffff3300);
    stock.color2.setColor(0xff0066ff);

    int input = LXColor.rgba(137, 137, 137, 255);
    assertEquals(apply(stock, input) & LXColor.RGB_MASK, apply(effect, input) & LXColor.RGB_MASK);
  }

  @Test
  void depthZeroMatchesColorizeRgbButPreservesNonOpaqueSourceAlpha() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.depth.setValue(0);
    effect.color1.setColor(0xffff3300);
    effect.color2.setColor(0xff0066ff);

    ColorizeEffect stock = new ColorizeEffect(lx);
    stock.setDamping(false);
    stock.color1.setColor(0xffff3300);
    stock.color2.setColor(0xff0066ff);

    int input = LXColor.rgba(137, 137, 137, 73);
    int stockOutput = apply(stock, input);
    int effectOutput = apply(effect, input);
    assertEquals(stockOutput & LXColor.RGB_MASK, effectOutput & LXColor.RGB_MASK);
    assertEquals(255, stockOutput >>> LXColor.ALPHA_SHIFT);
    assertEquals(73, effectOutput >>> LXColor.ALPHA_SHIFT);
  }

  static Stream<Arguments> fullDepthCases() {
    return Stream.of(
      Arguments.of(25, 0xffff0000, 0xff0000ff),
      Arguments.of(50, 0xffff0000, 0xff0000ff),
      Arguments.of(75, 0xffff0000, 0xff0000ff));
  }

  @ParameterizedTest
  @MethodSource("fullDepthCases")
  void fullDepthScalesGradientColorBySourceBrightness(int brightness, int color1, int color2) {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.color1.setColor(color1);
    effect.color2.setColor(color2);
    effect.depth.setValue(1);

    int input = LXColor.gray(brightness);
    float source = LXColor.b(input) * .01f;
    int gradient = effectColor(color1, color2, source);
    int expected = 0xff000000 | (LXColor.scaleBrightness(gradient, source) & LXColor.RGB_MASK);
    assertEquals(expected, apply(effect, input));
  }

  @Test
  void fullDepthHalfBrightnessRedHasHandDerivedArgb() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.color1.setColor(LXColor.RED);
    effect.color2.setColor(LXColor.RED);
    effect.depth.setValue(1);

    assertEquals(0xff800000, apply(effect, 0xff808080));
  }

  static Stream<Arguments> amountCases() {
    return Stream.of(
      Arguments.of(0, 0xff204060),
      Arguments.of(.5, 0xff706050),
      Arguments.of(1, 0xffc08040));
  }

  @ParameterizedTest
  @MethodSource("amountCases")
  void amountCrossfadesRgbAgainstOriginal(double amount, int expected) {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.depth.setValue(0);
    effect.color1.setColor(0xffc08040);
    effect.color2.setColor(0xffc08040);
    effect.amount.setValue(amount);

    assertEquals(expected, apply(effect, 0xff204060));
  }

  @Test
  void thresholdDoesNotRemapColorAboveCutoff() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.depth.setValue(0);
    effect.color1.setColor(LXColor.RED);
    effect.color2.setColor(LXColor.BLUE);
    int input = LXColor.gray(60);

    effect.threshold.setValue(.1);
    int lowThreshold = apply(effect, input);
    effect.threshold.setValue(.5);
    int highThreshold = apply(effect, input);

    assertEquals(lowThreshold, highThreshold);
  }

  @Test
  void thresholdEqualityIsNotGated() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.depth.setValue(0);
    effect.color1.setColor(LXColor.RED);
    effect.color2.setColor(LXColor.RED);
    effect.thresholdMode.setValue(ColorizeMultiplyEffect.ThresholdMode.BLACK);
    int input = LXColor.gray(25);
    effect.threshold.setValue(LXColor.b(input) * .01f);

    assertEquals(LXColor.RED, apply(effect, input));
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0, .35, 1 })
  void preservesSourceAlphaThroughColorization(double amount) {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.color1.setColor(LXColor.RED);
    effect.color2.setColor(LXColor.BLUE);
    effect.amount.setValue(amount);

    assertEquals(73, apply(effect, LXColor.rgba(80, 80, 80, 73)) >>> LXColor.ALPHA_SHIFT);
  }

  @Test
  void clearThresholdModeProducesTransparentPixel() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.threshold.setValue(.5);
    effect.thresholdMode.setValue(ColorizeMultiplyEffect.ThresholdMode.CLEAR);

    assertEquals(0x000a141e, apply(effect, LXColor.rgba(10, 20, 30, 97)));
  }

  @Test
  void paletteUsesBrightnessToSelectAmongThreeStops() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    configureThreeColorPalette(lx, effect);
    effect.depth.setValue(0);

    int middle = apply(effect, LXColor.gray(50));
    int green = (middle & LXColor.G_MASK) >>> LXColor.G_SHIFT;
    assertTrue(green > ((middle & LXColor.R_MASK) >>> LXColor.R_SHIFT));
    assertTrue(green > (middle & LXColor.B_MASK));

    effect.paletteDepth.setValue(.5);
    int shallow = apply(effect, LXColor.gray(50));
    effect.paletteInvert.setValue(true);
    int shallowInverted = apply(effect, LXColor.gray(50));

    assertNotEquals(middle, shallow, "palette depth changes the sampled range");
    assertNotEquals(shallow, shallowInverted, "palette inversion reverses the sampled range");
    assertTrue(((shallow & LXColor.R_MASK) >>> LXColor.R_SHIFT) > (shallow & LXColor.B_MASK));
    assertTrue((shallowInverted & LXColor.B_MASK) >
      ((shallowInverted & LXColor.R_MASK) >>> LXColor.R_SHIFT));
  }

  @Test
  void relativeModeMatchesColorizeRgb() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.depth.setValue(0);
    effect.color1.setColor(LXColor.RED);
    effect.gradientHue.setValue(120);
    effect.colorMode.setValue(ColorizeMultiplyEffect.ColorMode.RELATIVE);

    ColorizeEffect stock = new ColorizeEffect(lx);
    stock.setDamping(false);
    stock.color1.setColor(LXColor.RED);
    stock.gradientHue.setValue(120);
    stock.colorMode.setValue(ColorizeEffect.ColorMode.RELATIVE);

    int input = LXColor.gray(67);
    assertEquals(apply(stock, input) & LXColor.RGB_MASK, apply(effect, input) & LXColor.RGB_MASK);
  }

  @Test
  void linkedModeMatchesColorizeRgb() {
    LX lx = newHeadlessLx();
    lx.engine.palette.swatch.getColor(0).primary.setColor(LXColor.RED);
    ColorizeMultiplyEffect effect = effect(lx);
    effect.depth.setValue(0);
    effect.gradientHue.setValue(120);
    effect.colorMode.setValue(ColorizeMultiplyEffect.ColorMode.LINKED);

    ColorizeEffect stock = new ColorizeEffect(lx);
    stock.setDamping(false);
    stock.gradientHue.setValue(120);
    stock.colorMode.setValue(ColorizeEffect.ColorMode.LINKED);

    int input = LXColor.gray(67);
    assertEquals(apply(stock, input) & LXColor.RGB_MASK, apply(effect, input) & LXColor.RGB_MASK);
  }

  @Test
  void blackThresholdModePreservesAlpha() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.threshold.setValue(.5);
    effect.thresholdMode.setValue(ColorizeMultiplyEffect.ThresholdMode.BLACK);

    assertEquals(0x61000000, apply(effect, LXColor.rgba(10, 20, 30, 97)));
  }

  @Test
  void leaveThresholdModeDoesNotChangePixel() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.threshold.setValue(.5);
    effect.thresholdMode.setValue(ColorizeMultiplyEffect.ThresholdMode.LEAVE);
    int input = LXColor.rgba(10, 20, 30, 97);

    assertEquals(input, apply(effect, input));
  }

  @Test
  void defaultsToRgbInterpolationAndFullDepth() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);

    assertEquals(BlendMode.RGB, effect.blendMode.getEnum());
    assertEquals(1, effect.depth.getValue());
  }

  @Test
  void semanticCatalogEntryShipsWithOwningClass() {
    assertNotNull(ColorizeMultiplyEffect.class.getResource(
      "/catalog/apotheneum.doved.effects.ColorizeMultiplyEffect.md"));
  }

  @Test
  void luminosityCanDriveColorization() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = effect(lx);
    effect.source.setValue(ColorizeMultiplyEffect.SourceMode.LUMINOSITY);
    effect.color1.setColor(LXColor.RED);
    effect.color2.setColor(LXColor.BLUE);
    effect.depth.setValue(0);
    int input = LXColor.rgba(255, 0, 0, 255);
    float luminosity = LXColor.luminosity(input) * .01f;

    assertEquals(0xff000000 | (effectColor(LXColor.RED, LXColor.BLUE, luminosity) & LXColor.RGB_MASK),
      apply(effect, input));
  }

  @Test
  void instantiatesByFullyQualifiedClassName() throws LX.InstantiationException {
    LX lx = newHeadlessLx();
    LXEffect instance = lx.instantiateEffect(ColorizeMultiplyEffect.class.getName());
    try {
      assertInstanceOf(ColorizeMultiplyEffect.class, instance);
    } finally {
      instance.dispose();
    }
  }

  private static ColorizeMultiplyEffect effect(LX lx) {
    ColorizeMultiplyEffect effect = new ColorizeMultiplyEffect(lx);
    effect.setDamping(false);
    return effect;
  }

  private static void configureThreeColorPalette(LX lx, ColorizeMultiplyEffect effect) {
    lx.engine.palette.swatch.getColor(0).primary.setColor(LXColor.RED);
    lx.engine.palette.swatch.addColor().primary.setColor(LXColor.GREEN);
    lx.engine.palette.swatch.addColor().primary.setColor(LXColor.BLUE);
    effect.colorMode.setValue(ColorizeMultiplyEffect.ColorMode.PALETTE);
    effect.paletteStops.setValue(3);
  }

  private static int apply(ColorizeMultiplyEffect effect, int input) {
    return applyEffect(effect, input);
  }

  private static int apply(ColorizeEffect effect, int input) {
    return applyEffect(effect, input);
  }

  private static int applyEffect(heronarts.lx.effect.LXEffect effect, int input) {
    ModelBuffer buffer = new ModelBuffer(effect.getLX());
    try {
      buffer.getArray()[0] = input;
      effect.setBuffer(buffer);
      effect.onLoop(16);
      return buffer.getArray()[0];
    } finally {
      buffer.dispose();
    }
  }

  private static int effectColor(int color1, int color2, float amount) {
    int red = LXUtils.lerpi((color1 & LXColor.R_MASK) >>> LXColor.R_SHIFT,
      (color2 & LXColor.R_MASK) >>> LXColor.R_SHIFT, amount);
    int green = LXUtils.lerpi((color1 & LXColor.G_MASK) >>> LXColor.G_SHIFT,
      (color2 & LXColor.G_MASK) >>> LXColor.G_SHIFT, amount);
    int blue = LXUtils.lerpi(color1 & LXColor.B_MASK, color2 & LXColor.B_MASK, amount);
    return LXColor.rgba(red, green, blue, 255);
  }
}
