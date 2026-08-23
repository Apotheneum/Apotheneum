package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXSwatch;

public class RockfallColorTest extends HeadlessLxTest {

  @Test
  void zeroAmountProducesOneFlatColorForEveryPhysicsValue() {
    final int base = LXColor.hsb(28, 48, 70);
    assertEquals(base, Rockfall.modulatedColor(base, 0, -1));
    assertEquals(base, Rockfall.modulatedColor(base, 0, -.5));
    assertEquals(base, Rockfall.modulatedColor(base, 0, 0));
    assertEquals(base, Rockfall.modulatedColor(base, 0, .5));
    assertEquals(base, Rockfall.modulatedColor(base, 0, 1));
  }

  @Test
  void nonzeroAmountCouplesColorToPhysics() {
    final int base = LXColor.hsb(210, 82, 85);
    final int slowOrDeep = Rockfall.modulatedColor(base, .7, -1);
    final int neutral = Rockfall.modulatedColor(base, .7, 0);
    final int fastOrRim = Rockfall.modulatedColor(base, .7, 1);
    assertNotEquals(slowOrDeep, neutral);
    assertNotEquals(neutral, fastOrRim);
    assertNotEquals(slowOrDeep, fastOrRim);
    assertEquals(LXColor.h(base), LXColor.h(slowOrDeep), 1);
    assertEquals(LXColor.h(base), LXColor.h(fastOrRim), 1);
  }

  @Test
  void physicsAndExtremeSettingsAreClamped() {
    final int base = LXColor.hsb(350, 100, 60);
    assertEquals(
      Rockfall.modulatedColor(base, 1, 1),
      Rockfall.modulatedColor(base, 4, 7)
    );
    assertEquals(
      Rockfall.modulatedColor(base, 1, -1),
      Rockfall.modulatedColor(base, 4, -7)
    );
  }

  @Test
  void paletteStopsAndSingleStopFallbackAreResolvedDirectly() {
    final LX lx = newHeadlessLx();
    final LXSwatch swatch = lx.engine.palette.swatch;
    while (swatch.colors.size() > 1) {
      swatch.removeColor();
    }
    final int first = LXColor.hsb(32, 77, 61);
    swatch.colors.get(0).primary.setColor(first);
    assertEquals(first, Rockfall.paletteColor(swatch.colors, 0));
    assertEquals(first, Rockfall.paletteColor(swatch.colors, 1));

    final int second = LXColor.hsb(204, 69, 88);
    swatch.addColor().primary.setColor(second);
    assertEquals(first, Rockfall.paletteColor(swatch.colors, 0));
    assertEquals(second, Rockfall.paletteColor(swatch.colors, 1));
  }

  @Test
  void compositingPreservesPureRockAndWaterExtremes() {
    final int rock = LXColor.hsb(28, 48, 70);
    final int water = LXColor.hsb(210, 82, 85);
    assertEquals(rock, Rockfall.compositeColors(rock, 1, water, 0));
    assertEquals(water, Rockfall.compositeColors(rock, 0, water, 1));
    assertEquals(LXColor.BLACK, Rockfall.compositeColors(rock, 0, water, 0));
  }
}
