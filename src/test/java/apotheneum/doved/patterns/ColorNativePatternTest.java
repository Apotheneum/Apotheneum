package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.DiscreteParameter;

public class ColorNativePatternTest extends HeadlessLxTest {

  @Test
  void zeroOffsetsAreAnExactPassthroughNotJustNumericallyEqual() {
    final int color = LXColor.hsb(212, 61, 77);
    // Identity by construction (early return), not by a round trip through h()/s()/b()/hsb()
    // that happens to land back on the same bits. This is what keeps default output
    // bit-identical to the pre-generalization pattern.
    assertEquals(color, ColorNativePattern.applyOffsets(color, 0, 0));
  }

  @Test
  void hueOffsetShiftsHueAndWrapsAtTheSeam() {
    final int base = LXColor.hsb(200, 70, 60);
    final int shifted = ColorNativePattern.applyOffsets(base, 45, 0);
    assertEquals(245, LXColor.h(shifted), 1);
    assertEquals(LXColor.s(base), LXColor.s(shifted), 1);
    assertEquals(LXColor.b(base), LXColor.b(shifted), 1);

    final int nearSeam = LXColor.hsb(350, 70, 60);
    final int wrapped = ColorNativePattern.applyOffsets(nearSeam, 30, 0);
    assertEquals(20, LXColor.h(wrapped), 1);

    final int negativeWrap = ColorNativePattern.applyOffsets(LXColor.hsb(10, 70, 60), -30, 0);
    assertEquals(340, LXColor.h(negativeWrap), 1);
  }

  @Test
  void satTrimOnlyEverReducesSaturationAtTheFunctionLevel() {
    final int base = LXColor.hsb(30, 90, 60);
    assertEquals(80, LXColor.s(ColorNativePattern.applyOffsets(base, 0, -10)), 1);
    assertEquals(50, LXColor.s(ColorNativePattern.applyOffsets(base, 0, -40)), 1);
    // Clamped at zero rather than going negative.
    assertEquals(0, LXColor.s(ColorNativePattern.applyOffsets(base, 0, -100)), 1);
  }

  @Test
  void satTrimParameterCannotBeSetAboveZero() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    rockfall.rockColor.satTrim.setValue(50);
    // Bounded parameters clamp silently (docs/lx-coding-guidelines.md #13); the range itself
    // is the real guarantee that this knob can never raise saturation.
    assertEquals(0, rockfall.rockColor.satTrim.getValue(), 0);
    assertEquals(0, rockfall.rockColor.satTrim.range.max, 0);
    assertEquals(-40, rockfall.rockColor.satTrim.range.min, 0);
    rockfall.dispose();
  }

  @Test
  void hueOffsetRangeIsSixtyDegreesEachWayNotOneEighty() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    assertEquals(-60, rockfall.rockColor.hueOffset.range.min, 0);
    assertEquals(60, rockfall.rockColor.hueOffset.range.max, 0);
    rockfall.rockColor.hueOffset.setValue(1000);
    assertEquals(60, rockfall.rockColor.hueOffset.getValue(), 0);
    rockfall.rockColor.hueOffset.setValue(-1000);
    assertEquals(-60, rockfall.rockColor.hueOffset.getValue(), 0);
    rockfall.dispose();
  }

  @Test
  void paletteIndexIsCompoundDiscreteSoItCanTerminateModulation() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    // Load-bearing type check: a plain DiscreteParameter does not implement
    // LXCompoundModulation.Target and cannot be a modulation destination.
    assertInstanceOf(CompoundDiscreteParameter.class, rockfall.rockColor.paletteIndex);
    assertInstanceOf(LXCompoundModulation.Target.class, rockfall.rockColor.paletteIndex);
    assertTrue(rockfall.rockColor.paletteIndex instanceof DiscreteParameter);
    rockfall.dispose();
  }

  @Test
  void paletteIndexStaysDiscreteAndSnapping() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    rockfall.rockColor.paletteIndex.setValue(2.9);
    // A discrete parameter snaps/floors rather than reporting a fractional value back.
    assertEquals(2, rockfall.rockColor.paletteIndex.getValuei());
    assertEquals(2., rockfall.rockColor.paletteIndex.getValue(), 0);
    rockfall.dispose();
  }

  @Test
  void paletteIndexIsOneBasedMatchingIndexSelectorConvention() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    assertEquals(1, rockfall.rockColor.paletteIndex.getMinValue());
    assertEquals(5, rockfall.rockColor.paletteIndex.getMaxValue());
    // Defaults preserve which swatch stop each role reads: rock's 1-based index 1 and
    // water's 2 resolve to 0-based swatch stops 0 and 1 respectively (index - 1) -- the same
    // stops the pre-generalization Rockfall's hand-rolled ColorGroup read.
    assertEquals(1, rockfall.rockColor.paletteIndex.getValuei());
    assertEquals(2, rockfall.waterColor.paletteIndex.getValuei());
    // Cannot literally be an LXPalette.IndexSelector (see class javadoc): that concrete type
    // extends plain DiscreteParameter, not CompoundDiscreteParameter, and is not a modulation
    // target -- distinct enough that the two aren't even instanceof-comparable at compile time,
    // which is itself a form of proof.
    assertFalse(LXPalette.IndexSelector.class.isInstance(rockfall.rockColor.paletteIndex));
    rockfall.dispose();
  }

  @Test
  void paletteIndexOptionLabelsMirrorTheProjectPaletteNames() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    // LXPalette's label1..label5 default to "1".."5" as literal text already.
    assertArrayEquals(
      new String[] { "1", "2", "3", "4", "5" },
      rockfall.rockColor.paletteIndex.getOptions()
    );

    lx.engine.palette.label1.setValue("Warm");
    assertEquals("Warm", rockfall.rockColor.paletteIndex.getOptions()[0]);
    // Every role's paletteIndex mirrors the same project-wide names -- there is one set of
    // palette index labels, not one per role.
    assertEquals("Warm", rockfall.waterColor.paletteIndex.getOptions()[0]);

    // An explicitly emptied label falls back to the 1-based number as a string, matching
    // LXPalette.updateSelectors()'s own fallback for a real IndexSelector.
    lx.engine.palette.label1.setValue("");
    assertEquals("1", rockfall.rockColor.paletteIndex.getOptions()[0]);

    rockfall.dispose();
  }

  @Test
  void defaultParameterValuesReproduceThePreGeneralizationColor() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    final LXSwatch swatch = lx.engine.palette.swatch;

    rockfall.rockColor.update();
    rockfall.waterColor.update();

    for (double physics : new double[] { -1, -.5, 0, .3, 1 }) {
      final int expectedRock = ColorNativePattern.modulatedColor(
        ColorNativePattern.paletteColor(swatch.colors, 0), .7, physics);
      final int expectedWater = ColorNativePattern.modulatedColor(
        ColorNativePattern.paletteColor(swatch.colors, 1), .7, physics);
      assertEquals(expectedRock, rockfall.rockColor.color(physics));
      assertEquals(expectedWater, rockfall.waterColor.color(physics));
    }
    rockfall.dispose();
  }

  @Test
  void resolutionOrderAppliesOffsetsBeforePhysicsPerturbation() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    final LXSwatch swatch = lx.engine.palette.swatch;
    swatch.colors.get(0).primary.setColor(LXColor.hsb(200, 80, 60));

    rockfall.rockColor.hueOffset.setValue(40);
    rockfall.rockColor.satTrim.setValue(-20);
    rockfall.rockColor.amount.setValue(1);
    rockfall.rockColor.update();

    // modulatedColor never touches hue, so if hueOffset were applied after (or fought by) the
    // physics perturbation instead of before it, the hue seen downstream would drift with
    // physics. It must not: the offset is baked into the color physics perturbs, so hue stays
    // pinned at base+offset across the whole physics range.
    assertEquals(240, LXColor.h(rockfall.rockColor.color(0)), 1);
    assertEquals(240, LXColor.h(rockfall.rockColor.color(1)), 1);
    assertEquals(240, LXColor.h(rockfall.rockColor.color(-1)), 1);

    // The saturation trim is likewise baked into the color before physics perturbs it: at
    // rest (physics=0, no perturbation) it should read as base saturation minus the trim.
    assertEquals(60, LXColor.s(rockfall.rockColor.color(0)), 1);

    // Physics still visibly perturbs on top of the offset color (amount=1, nonzero physics).
    assertNotEquals(rockfall.rockColor.color(0), rockfall.rockColor.color(1));
    assertNotEquals(rockfall.rockColor.color(0), rockfall.rockColor.color(-1));

    rockfall.dispose();
  }

  // --- PaletteColorPreview: the read-only swatch chip on the device panel ---
  //
  // These tests cover what is headlessly verifiable: that the chip's constructor actually wires
  // up listeners on every value that can change the color it displays, and that dispose() tears
  // them back down. LXListenableParameter.addListener() throwing IllegalStateException on a
  // duplicate is the mechanism used to prove a listener is *already* registered, since there is
  // no public way to just ask a parameter "who is listening to you" -- and this repo has no
  // headless way to watch glx actually repaint a pixel (that needs a real GLFW/BGFX window via
  // ./scripts/render-ui, which is not part of `mvn -Ptests test`; see docs/ui-rendering.md).
  // The framework contract that a redraw() on an attached, visible component reliably repaints
  // it on the next frame is verified by reading heronarts.glx.ui.UI2dComponent's own
  // predraw()/draw() source, not re-tested here.

  @Test
  void paletteColorPreviewListensForEveryValueThatCanChangeTheDisplayedColor() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    final ColorNativePattern.PaletteColorPreview preview =
      new ColorNativePattern.PaletteColorPreview(lx, rockfall.rockColor);

    assertThrows(IllegalStateException.class,
      () -> rockfall.rockColor.paletteIndex.addListener(preview.redraw),
      "paletteIndex should already carry the preview's redraw listener");
    assertThrows(IllegalStateException.class,
      () -> rockfall.rockColor.hueOffset.addListener(preview.redraw),
      "hueOffset should already carry the preview's redraw listener");
    assertThrows(IllegalStateException.class,
      () -> rockfall.rockColor.satTrim.addListener(preview.redraw),
      "satTrim should already carry the preview's redraw listener");

    // The live project palette: a color sitting at a fixed index can be edited underneath it
    // (e.g. from the Palette panel), and the chip must track that too, not just index changes.
    final LXDynamicColor firstColor = lx.engine.palette.swatch.colors.get(0);
    assertThrows(IllegalStateException.class,
      () -> firstColor.primary.addListener(preview.redraw),
      "the active swatch's first color should already carry the preview's redraw listener");

    preview.dispose();
    rockfall.dispose();
  }

  @Test
  void paletteColorPreviewDisposeRemovesItsListeners() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    final ColorNativePattern.PaletteColorPreview preview =
      new ColorNativePattern.PaletteColorPreview(lx, rockfall.rockColor);

    preview.dispose();

    // Once disposed, none of these are registered any more, so re-adding must succeed instead
    // of throwing IllegalStateException for a duplicate -- proving dispose() actually removed
    // them rather than merely being callable without error.
    assertDoesNotThrow(() -> rockfall.rockColor.paletteIndex.addListener(preview.redraw));
    assertDoesNotThrow(() -> rockfall.rockColor.hueOffset.addListener(preview.redraw));
    assertDoesNotThrow(() -> rockfall.rockColor.satTrim.addListener(preview.redraw));
    final LXDynamicColor firstColor = lx.engine.palette.swatch.colors.get(0);
    assertDoesNotThrow(() -> firstColor.primary.addListener(preview.redraw));

    // Clean up what those assertions just (re-)registered, so this test does not itself leak a
    // listener onto a color/parameter that outlives it.
    rockfall.rockColor.paletteIndex.removeListener(preview.redraw);
    rockfall.rockColor.hueOffset.removeListener(preview.redraw);
    rockfall.rockColor.satTrim.removeListener(preview.redraw);
    firstColor.primary.removeListener(preview.redraw);

    rockfall.dispose();
  }

  @Test
  void paletteColorPreviewAttachesListenersToColorsAddedAfterConstruction() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    final ColorNativePattern.PaletteColorPreview preview =
      new ColorNativePattern.PaletteColorPreview(lx, rockfall.rockColor);

    final LXDynamicColor addedColor = lx.engine.palette.swatch.addColor();
    assertThrows(IllegalStateException.class,
      () -> addedColor.primary.addListener(preview.redraw),
      "a color added to the swatch after the preview was constructed should still be tracked");

    preview.dispose();
    rockfall.dispose();
  }

  @Test
  void paletteColorPreviewShowsTheEffectiveRestingColorNotTheRawPaletteStop() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    final LXSwatch swatch = lx.engine.palette.swatch;
    swatch.colors.get(0).primary.setColor(LXColor.hsb(200, 90, 70));

    rockfall.rockColor.hueOffset.setValue(30);
    rockfall.rockColor.satTrim.setValue(-20);
    rockfall.rockColor.amount.setValue(1);
    rockfall.rockColor.update();

    // What the chip's onDraw computes: resolvedPaletteColor -> applyOffsets, with no physics
    // perturbation applied (physics varies per pixel/frame; the chip shows a single resting
    // value). This must differ from the untouched palette stop once hueOffset/satTrim are
    // nonzero, and must equal the role's own resting color (physics = 0).
    final int rawStop = ColorNativePattern.paletteColor(swatch.colors, 0);
    final int chipColor = ColorNativePattern.applyOffsets(
      rawStop, rockfall.rockColor.hueOffset.getValue(), rockfall.rockColor.satTrim.getValue());

    assertNotEquals(rawStop, chipColor);
    assertEquals(rockfall.rockColor.color(0), chipColor);
    // Physics-perturbed values (nonzero physics, amount=1) must NOT be what the chip shows.
    assertNotEquals(rockfall.rockColor.color(1), chipColor);

    rockfall.dispose();
  }
}
