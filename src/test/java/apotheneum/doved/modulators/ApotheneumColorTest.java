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

  /**
   * Registers a fresh {@code ApotheneumColor} directly on {@code lx.engine} -- the same call
   * {@code apotheneum.doved.ApotheneumColorPlugin.getOrRegisterConfig} makes in a real session,
   * just without that method's "reuse if already registered" check, since every test here
   * starts from a fresh {@code LX} with nothing registered yet.
   */
  private static ApotheneumColor register(LX lx) {
    final ApotheneumColor color = new ApotheneumColor(lx);
    lx.engine.registerComponent(ApotheneumColor.PATH, color);
    return color;
  }

  @Test
  void getReturnsNullBeforeAnythingIsRegistered() {
    final LX lx = newHeadlessLx();
    assertNull(ApotheneumColor.get(lx));
  }

  @Test
  void getReturnsTheRegisteredInstance() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = register(lx);
    assertSame(color, ApotheneumColor.get(lx));
  }

  @Test
  void registeredComponentIsDisposedWhenLxDisposes() {
    // No explicit assertion here beyond "does not throw" -- disposeLx() (HeadlessLxTest's own
    // @AfterEach) runs after this test method returns and disposes lx, which must cleanly
    // dispose the registered ApotheneumColor as an ordinary engine child. A leak or a
    // double-dispose here would fail the *next* @AfterEach, not this test body, which is the
    // point: registration means LX owns the lifecycle, not this test.
    final LX lx = newHeadlessLx();
    register(lx);
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
    final ApotheneumColor color = register(lx);

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
  }

  @Test
  void everySurfaceMatchesTheSharedIndexWithZeroOffsets() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = register(lx);

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
  }

  @Test
  void shapeAxisMatchesCubeSurfacesAndDiffersFromCylinderSurfaces() {
    final LX lx = newHeadlessLx();
    // A fresh LX's default swatch carries one color; a second, distinct one is needed so the
    // one-stop shift Shape/In-Out apply can actually be told apart from doing nothing.
    lx.engine.palette.swatch.addColor();
    lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(0, 90, 70));
    lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(200, 90, 70));

    final ApotheneumColor color = register(lx);
    color.pair.setValue(0);
    color.swap.setValue(0);
    color.axis.setValue(1); // Shape

    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CUBE_INTERIOR),
      "Shape must match the two cube surfaces to each other"
    );
    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_INTERIOR),
      "Shape must match the two cylinder surfaces to each other"
    );
    assertNotEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      "Shape must differ cube from cylinder by one stop"
    );
    assertEquals(
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, color.primaryIndex()),
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      "cylinder reads exactly one stop up from the shared index under Shape"
    );
  }

  @Test
  void insideOutsideAxisMatchesExteriorsAndDiffersFromInteriors() {
    final LX lx = newHeadlessLx();
    lx.engine.palette.swatch.addColor();
    lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(0, 90, 70));
    lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(200, 90, 70));

    final ApotheneumColor color = register(lx);
    color.pair.setValue(0);
    color.swap.setValue(0);
    color.axis.setValue(2); // In/Out

    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      "In/Out must match the two exterior surfaces to each other"
    );
    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_INTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_INTERIOR),
      "In/Out must match the two interior surfaces to each other"
    );
    assertNotEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CUBE_INTERIOR),
      "In/Out must differ exterior from interior by one stop"
    );
  }

  @Test
  void shapeAxisWrapsAtTheCeilingRatherThanClamping() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = register(lx);

    // secondaryIndex reaches its own maximum of 3 at pair=1/swap=0 (design/color-system.md
    // section 4: neither role ever exceeds stop 3). A fresh LX's default swatch carries
    // exactly one color, so MAX_COLORS is 1 and every index wraps straight back to stop 1 --
    // still a legal, non-clamped result, not a plateau.
    color.pair.setValue(1);
    color.swap.setValue(0);
    color.axis.setValue(1); // Shape
    assertEquals(3, color.secondaryIndex());
    assertEquals(
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 0),
      color.secondaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      "3 + 1 must wrap around a one-stop swatch back to stop 1, not clamp at stop 3"
    );
  }

  @Test
  void resolvePrimaryOrNeutralFallsBackToWhiteWhenNoInstanceIsResolved() {
    newHeadlessLx();
    assertEquals(
      LXColor.WHITE,
      ApotheneumColor.resolvePrimaryOrNeutral(null, ApotheneumColor.Surface.CUBE_EXTERIOR)
    );
    assertEquals(
      LXColor.WHITE,
      ApotheneumColor.resolveSecondaryOrNeutral(null, ApotheneumColor.Surface.CUBE_EXTERIOR)
    );
  }

  @Test
  void resolvePrimaryOrNeutralReadsThroughToARealInstance() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = register(lx);
    color.pair.setValue(0);
    color.swap.setValue(0);

    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      ApotheneumColor.resolvePrimaryOrNeutral(color, ApotheneumColor.Surface.CUBE_EXTERIOR)
    );
    assertEquals(
      color.secondaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      ApotheneumColor.resolveSecondaryOrNeutral(color, ApotheneumColor.Surface.CUBE_EXTERIOR)
    );
  }
}
