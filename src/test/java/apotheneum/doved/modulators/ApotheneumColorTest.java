package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import apotheneum.doved.patterns.ColorNativePattern;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;

public class ApotheneumColorTest extends HeadlessLxTest {

  // Every ApotheneumColor built here is added to lx.engine.modulation: an LXModulator has no
  // working `lx` reference (needed for primaryColor()/secondaryColor() to reach the project
  // palette) until it is registered with an engine -- unlike ColorNativePattern's roles, which
  // get `lx` straight from the pattern's own constructor. Each test ends by handing it back to
  // engine.modulation.removeModulator(...), which both disposes it exactly once (confirmed by
  // disassembling LXModulatorComponent.removeModulator: it calls LX.dispose(component)
  // internally) and drops it from the engine's tracked list, so HeadlessLxTest's own @AfterEach
  // teardown of the whole LX instance never encounters it a second time.

  @Test
  void constructingSetsTheSingletonAndDisposeClearsIt() {
    final LX lx = newHeadlessLx();
    assertNull(ApotheneumColor.instance, "no instance should exist before this test builds one");
    final ApotheneumColor color = lx.engine.modulation.addModulator(new ApotheneumColor());
    assertSame(color, ApotheneumColor.instance);
    lx.engine.modulation.removeModulator(color);
    assertNull(ApotheneumColor.instance);
  }

  @Test
  void aSecondInstanceBecomesTheOneRead() {
    // Not the supported shape (exactly one is expected), but must not throw -- and whichever
    // was constructed most recently is the one every ColorNativePattern reads, since a role
    // resolves through the live static field at call time, not a reference captured earlier.
    final LX lx = newHeadlessLx();
    final ApotheneumColor first = lx.engine.modulation.addModulator(new ApotheneumColor());
    final ApotheneumColor second = lx.engine.modulation.addModulator(new ApotheneumColor());
    assertSame(second, ApotheneumColor.instance);
    lx.engine.modulation.removeModulator(second);
    lx.engine.modulation.removeModulator(first);
  }

  @Test
  void surfaceOfReturnsNullWithoutApotheneumGeometry() {
    newHeadlessLx();
    // The default test model (HeadlessLxTest.newModel()) is a plain GridModel, not an
    // Apotheneum fixture, so Apotheneum.exists is false and there is no orientation to resolve
    // against -- surfaceOf must fail safe (null) rather than NPE.
    assertNull(ApotheneumColor.Surface.of(null));
  }

  @Test
  void pairAndSwapReproduceTheFourStateRelayTable() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = lx.engine.modulation.addModulator(new ApotheneumColor());

    // design/color-system.md section 4's table, reproduced exactly: primary base 1/secondary
    // base 2, pair advances both by one stop, swap exchanges the two roles.
    color.pair.setValue(0);
    color.swap.setValue(0);
    assertEquals(1, color.primaryIndex());
    assertEquals(2, color.secondaryIndex());

    color.pair.setValue(1);
    color.swap.setValue(0);
    assertEquals(2, color.primaryIndex());
    assertEquals(3, color.secondaryIndex());

    color.pair.setValue(0);
    color.swap.setValue(1);
    assertEquals(2, color.primaryIndex());
    assertEquals(1, color.secondaryIndex());

    color.pair.setValue(1);
    color.swap.setValue(1);
    assertEquals(3, color.primaryIndex());
    assertEquals(2, color.secondaryIndex());

    lx.engine.modulation.removeModulator(color);
  }

  @Test
  void everySurfaceMatchesTheSharedIndexWithZeroOffsets() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = lx.engine.modulation.addModulator(new ApotheneumColor());

    color.pair.setValue(0);
    color.swap.setValue(0);

    final int expectedPrimary =
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, color.primaryIndex() - 1);
    final int expectedSecondary =
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, color.secondaryIndex() - 1);

    for (ApotheneumColor.Surface surface : ApotheneumColor.Surface.values()) {
      assertEquals(expectedPrimary, color.primaryColor(surface),
        "with every offset at its default of 0, " + surface + " must match the shared index");
      assertEquals(expectedSecondary, color.secondaryColor(surface));
    }

    lx.engine.modulation.removeModulator(color);
  }

  @Test
  void indexOffsetDifferentiatesOneSurfaceFromTheRest() {
    final LX lx = newHeadlessLx();
    // A fresh LX's default swatch carries one color; a second, distinct one is needed so two
    // different resolved indices can actually be told apart.
    lx.engine.palette.swatch.addColor();
    lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(0, 90, 70));
    lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(200, 90, 70));

    final ApotheneumColor color = lx.engine.modulation.addModulator(new ApotheneumColor());
    color.pair.setValue(0);
    color.swap.setValue(0);
    color.cubeExterior.indexOffset.setValue(1);

    assertNotEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_INTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "a non-zero indexOffset on one surface must not silently match the rest"
    );
    assertEquals(
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, color.primaryIndex()),
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "indexOffset 1 reads the next palette stop up"
    );

    lx.engine.modulation.removeModulator(color);
  }

  @Test
  void offsetAtTheCeilingStaysAtTheLastStopWithoutWrapping() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = lx.engine.modulation.addModulator(new ApotheneumColor());

    // secondaryIndex reaches its own maximum of 3 at pair=1/swap=0 (design/color-system.md
    // section 4: neither role ever exceeds stop 3). 3 + 2 (indexOffset's own maximum) = 5,
    // the swatch's last stop exactly -- still a legal, non-wrapped index.
    color.pair.setValue(1);
    color.swap.setValue(0);
    assertEquals(3, color.secondaryIndex());
    color.cylinderExterior.indexOffset.setValue(2);
    assertEquals(
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 5 - 1),
      color.secondaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR)
    );

    lx.engine.modulation.removeModulator(color);
  }

  @Test
  void indexOffsetWrapsRatherThanClampingBelowTheFloor() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = lx.engine.modulation.addModulator(new ApotheneumColor());

    // primaryIndex is 1 at pair=0/swap=0, the lowest value the shared scheme ever produces.
    // 1 + (-2) (indexOffset's own minimum) = -1, below the swatch's floor of 1. A clamp would
    // land this back on stop 1 -- indistinguishable from indexOffset doing nothing. Wrapping
    // instead must land on a distinct, valid stop: floorMod(-1 - 1, 5) + 1 = 4.
    color.pair.setValue(0);
    color.swap.setValue(0);
    assertEquals(1, color.primaryIndex());
    color.cubeExterior.indexOffset.setValue(-2);
    assertEquals(
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 4 - 1),
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "1 + (-2) must wrap to stop 4, not clamp back to stop 1"
    );
    // A neighboring surface at the same shared index but zero offset must still read stop 1 --
    // proving the wrap is local to the one surface whose offset triggered it.
    assertEquals(
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 1 - 1),
      color.primaryColor(ApotheneumColor.Surface.CUBE_INTERIOR)
    );

    lx.engine.modulation.removeModulator(color);
  }

  @Test
  void hueOffsetAndSatTrimApplyOnTopOfTheSharedIndex() {
    final LX lx = newHeadlessLx();
    lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(200, 80, 60));
    final ApotheneumColor color = lx.engine.modulation.addModulator(new ApotheneumColor());

    color.pair.setValue(0);
    color.swap.setValue(0);
    assertEquals(1, color.primaryIndex());
    color.cubeExterior.hueOffset.setValue(40);
    color.cubeExterior.satTrim.setValue(-20);

    final int resolved = color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR);
    assertEquals(240, LXColor.h(resolved), 1);
    assertEquals(60, LXColor.s(resolved), 1);

    lx.engine.modulation.removeModulator(color);
  }

  @Test
  void noApotheneumInstanceMeansNoLiveSingleton() {
    newHeadlessLx();
    assertNull(ApotheneumColor.instance);
  }
}
