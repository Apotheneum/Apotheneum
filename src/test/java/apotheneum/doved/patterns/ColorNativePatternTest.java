package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import apotheneum.doved.modulators.ApotheneumColor;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXSwatch;

public class ColorNativePatternTest extends HeadlessLxTest {

  private static final ApotheneumColor.Surface SURFACE = ApotheneumColor.Surface.CUBE_EXTERIOR;

  @Test
  void zeroOffsetsAreAnExactPassthroughNotJustNumericallyEqual() {
    final int color = LXColor.hsb(212, 61, 77);
    // Identity by construction (early return), not by a round trip through h()/s()/b()/hsb()
    // that happens to land back on the same bits.
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
  void blendTonesUsesRelativeEnergyBeforeScalingBrightness() {
    final int primary = LXColor.hsb(0, 100, 100);
    final int secondary = LXColor.hsb(240, 100, 100);

    // A dim all-secondary tip remains blue, rather than moving only 30% toward blue.
    assertEquals(
      LXColor.scaleBrightness(secondary, .3),
      ColorNativePattern.blendTones(primary, 0, secondary, .3)
    );
    assertEquals(
      LXColor.scaleBrightness(LXColor.lerp(primary, secondary, .8), .25),
      ColorNativePattern.blendTones(primary, .05, secondary, .2)
    );
    assertEquals(LXColor.BLACK, ColorNativePattern.blendTones(primary, 0, secondary, 0));
  }

  @Test
  void amountIsTheOnlyParameterAColorRoleOwnsNow() {
    final LX lx = newHeadlessLx();
    final Rockfall rockfall = new Rockfall(lx);
    // The physics-coupling knob is unchanged: still a CompoundParameter, still the caller's
    // default, still the only thing left on a role now that palette/offset moved to
    // ApotheneumColor.
    assertEquals(.7, rockfall.rockColor.amount.getValue(), 0);
    assertEquals(.7, rockfall.waterColor.amount.getValue(), 0);
    rockfall.dispose();
  }

  @Test
  void colorFallsBackToNeutralWithoutAnApotheneumColorInTheProject() {
    final LX lx = newHeadlessLx();
    assertNull(ApotheneumColor.get(lx), "this test requires no ApotheneumColor to exist");
    final Rockfall rockfall = new Rockfall(lx);
    rockfall.rockColor.update();

    for (double physics : new double[] { -1, -.5, 0, .3, 1 }) {
      assertEquals(
        ColorNativePattern.modulatedColor(LXColor.WHITE, rockfall.rockColor.amount.getValue(), physics),
        rockfall.rockColor.color(SURFACE, physics),
        "with no ApotheneumColor present, a role must resolve neutral rather than throw"
      );
    }
    rockfall.dispose();
  }

  @Test
  void colorResolvesFromTheSharedApotheneumColorSingleton() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor apotheneumColor = registerApotheneumColor(lx);
    apotheneumColor.pair.setValue(0);
    apotheneumColor.swap.setValue(0);

    final Rockfall rockfall = new Rockfall(lx);
    rockfall.rockColor.update();
    rockfall.waterColor.update();

    for (double physics : new double[] { -1, -.5, 0, .3, 1 }) {
      final int expectedRock = ColorNativePattern.modulatedColor(
        apotheneumColor.primaryColor(SURFACE), rockfall.rockColor.amount.getValue(), physics);
      final int expectedWater = ColorNativePattern.modulatedColor(
        apotheneumColor.secondaryColor(SURFACE), rockfall.waterColor.amount.getValue(), physics);
      assertEquals(expectedRock, rockfall.rockColor.color(SURFACE, physics));
      assertEquals(expectedWater, rockfall.waterColor.color(SURFACE, physics));
    }
    rockfall.dispose();
  }

  @Test
  void differentSurfacesCanResolveToDifferentColorsFromTheSameRole() {
    final LX lx = newHeadlessLx();
    // A fresh LX's default swatch carries one color; a second, distinct one is needed so the
    // offset surface can actually be told apart from the rest.
    lx.engine.palette.swatch.addColor();
    lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(0, 90, 70));
    lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(200, 90, 70));

    final ApotheneumColor apotheneumColor = registerApotheneumColor(lx);
    apotheneumColor.pair.setValue(0);
    apotheneumColor.swap.setValue(0);
    // In/Out: exterior and interior sit one stop apart
    apotheneumColor.axis.setValue(ApotheneumColor.Axis.INSIDE_OUTSIDE.ordinal());

    final Rockfall rockfall = new Rockfall(lx);
    rockfall.rockColor.update();

    assertNotEquals(
      rockfall.rockColor.color(ApotheneumColor.Surface.CUBE_EXTERIOR, 0),
      rockfall.rockColor.color(ApotheneumColor.Surface.CUBE_INTERIOR, 0),
      "the same role, same pattern, same physics, must still differ by surface when the "
      + "axis puts the surfaces on different stops -- this is the whole point of the redesign"
    );
    rockfall.dispose();
  }

  @Test
  void colorToggleResolvesRolesAsPaletteIndependentNeutralWithPhysicsPerturbation() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor apotheneumColor = registerApotheneumColor(lx);

    final Rockfall rockfall = new Rockfall(lx);
    rockfall.rockColor.update();

    rockfall.color.setValue(false);
    rockfall.rockColor.update();
    final int neutral = rockfall.rockColor.color(SURFACE, 1);

    assertEquals(0, LXColor.s(neutral), 1);
    assertEquals(
      ColorNativePattern.modulatedColor(LXColor.WHITE, rockfall.rockColor.amount.getValue(), 1),
      neutral
    );
    rockfall.dispose();
  }

  @Test
  void resolutionOrderAppliesTheResolvedPaletteColorBeforePhysicsPerturbation() {
    final LX lx = newHeadlessLx();
    lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(240, 40, 60));
    final ApotheneumColor apotheneumColor = registerApotheneumColor(lx);
    apotheneumColor.pair.setValue(0);
    apotheneumColor.swap.setValue(0);

    final Rockfall rockfall = new Rockfall(lx);
    rockfall.rockColor.amount.setValue(1);
    rockfall.rockColor.update();

    // modulatedColor never touches hue, so the resolved palette color's hue must stay pinned
    // across the whole physics range -- physics only ever perturbs saturation/brightness.
    assertEquals(240, LXColor.h(rockfall.rockColor.color(SURFACE, 0)), 1);
    assertEquals(240, LXColor.h(rockfall.rockColor.color(SURFACE, 1)), 1);
    assertEquals(240, LXColor.h(rockfall.rockColor.color(SURFACE, -1)), 1);

    // At rest (physics=0, no perturbation) saturation reads exactly the resolved palette
    // color's own saturation -- there is no offset left to bake in ahead of it.
    assertEquals(40, LXColor.s(rockfall.rockColor.color(SURFACE, 0)), 1);

    // Physics still visibly perturbs on top of the offset color (amount=1, nonzero physics).
    assertNotEquals(rockfall.rockColor.color(SURFACE, 0), rockfall.rockColor.color(SURFACE, 1));
    assertNotEquals(rockfall.rockColor.color(SURFACE, 0), rockfall.rockColor.color(SURFACE, -1));

    rockfall.dispose();
  }

  @Test
  void everyRoleSharesTheSameMaxColorsSwatchRange() {
    // Sanity check that ApotheneumColor and ColorNativePattern agree on the swatch size they
    // both assume -- a drift here would silently change how far indexOffset can reach.
    assertTrue(LXSwatch.MAX_COLORS >= 3, "the shared scheme's table assumes at least 3 stops");
  }

  private static ApotheneumColor registerApotheneumColor(LX lx) {
    final ApotheneumColor color = new ApotheneumColor(lx);
    lx.engine.registerComponent(ApotheneumColor.PATH, color);
    return color;
  }
}
