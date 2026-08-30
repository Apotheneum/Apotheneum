package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import apotheneum.doved.patterns.ColorNativePattern;
import apotheneum.doved.modulators.ApotheneumColor.Axis;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXSwatch;

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

  /**
   * Resizes the live swatch to exactly {@code stopCount} stops and gives each one a distinct,
   * recognisable hue, so an assertion can name <em>which</em> stop was resolved rather than only
   * that two resolutions differed.
   */
  private static void setSwatchStops(LX lx, int stopCount) {
    final LXSwatch swatch = lx.engine.palette.swatch;
    while (swatch.colors.size() < stopCount) {
      swatch.addColor();
    }
    while (swatch.colors.size() > stopCount) {
      swatch.removeColor();
    }
    for (int i = 0; i < stopCount; ++i) {
      swatch.getColor(i).primary.setColor(LXColor.hsb(60 * i, 100, 100));
    }
  }

  @Test
  void shapeAxisWrapsAtTheCeilingRatherThanClamping() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = register(lx);

    // secondaryIndex reaches its own maximum of 3 at pair=1/swap=0 (design/color-system.md
    // section 4: neither role ever exceeds stop 3). A fresh LX's default swatch carries
    // exactly one color, so every index wraps straight back to stop 1 -- still a legal,
    // non-clamped result, not a plateau.
    color.pair.setValue(1);
    color.swap.setValue(0);
    color.axis.setValue(Axis.SHAPE.ordinal());
    assertEquals(3, color.secondaryIndex());
    assertEquals(
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 0),
      color.secondaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      "3 + 1 must wrap around a one-stop swatch back to stop 1, not clamp at stop 3"
    );
  }

  /**
   * The case the one-stop test above structurally cannot see, and the reason this one exists.
   *
   * <p>{@code wrapIndex} wrapped around {@code LXSwatch.MAX_COLORS} -- the swatch's fixed
   * <em>capacity</em> -- rather than around the number of stops the swatch actually holds. On a
   * one-colour swatch that is invisible: every index collapses onto stop 1 whether it wrapped or
   * was clamped, so the test above passed throughout the bug's life. On the two-stop swatch here
   * the two behaviours separate completely: {@code pair = 2} resolves primary to stop 2, and
   * under any non-{@code NONE} axis the shifted surface asks for stop 3, which <em>wrapping</em>
   * around two stops answers as stop 1 and <em>clamping</em> answers as stop 2 -- the same stop
   * the unshifted surface already has. Clamping therefore made the {@link
   * ApotheneumColor#axis} control do nothing at all on any palette smaller than the swatch's
   * capacity, which is every palette this show uses.
   *
   * <p>Asserted against the named stop colours, not merely {@code assertNotEquals}: "these two
   * differ" would also pass if the shift landed on some third wrong stop.
   */
  @Test
  void axisWrapsAroundTheLiveStopCountNotTheSwatchCapacity() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 2);
    final ApotheneumColor color = register(lx);

    final int stop1 = ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 0);
    final int stop2 = ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 1);
    assertNotEquals(stop1, stop2, "the two stops must be distinguishable for this test to mean anything");

    color.pair.setValue(1);
    color.swap.setValue(0);
    color.axis.setValue(Axis.SHAPE.ordinal());

    assertEquals(2, color.primaryIndex());
    assertEquals(
      stop2,
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "the unshifted surface sits on stop 2"
    );
    assertEquals(
      stop1,
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      "stop 2 + one-stop shift must wrap around a two-stop swatch to stop 1, not clamp back onto stop 2"
    );
  }

  /** The same failure stated as the symptom a performer would report: the Axis knob does
   * nothing. Kept separate from the test above because this is the observable claim, and it
   * should keep holding even if the exact stop arithmetic above is ever redesigned. */
  @Test
  void axisStillSeparatesSurfacesOnASwatchSmallerThanCapacity() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 2);
    final ApotheneumColor color = register(lx);
    color.pair.setValue(1);
    color.swap.setValue(0);

    color.axis.setValue(Axis.NONE.ordinal());
    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      "None must put every surface on one stop"
    );

    color.axis.setValue(Axis.SHAPE.ordinal());
    assertNotEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      "Shape must separate cube from cylinder on a two-stop palette, not silently do nothing"
    );

    color.axis.setValue(Axis.INSIDE_OUTSIDE.ordinal());
    assertNotEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CUBE_INTERIOR),
      "In/Out must separate exterior from interior on a two-stop palette"
    );
  }

  /** {@code shift} moves both roles along the palette while still tracking pair/swap -- the
   * per-device tweak {@code ModColorize} exposes. Wraps like every other resolution here. */
  @Test
  void stopShiftMovesAlongThePaletteAndWraps() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 3);
    final ApotheneumColor color = register(lx);
    color.pair.setValue(0);
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());

    final int unshifted = color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR);
    assertEquals(
      unshifted,
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR, 0),
      "shift 0 must be exactly the unshifted resolution"
    );
    assertNotEquals(
      unshifted,
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR, 1),
      "shift 1 must land on a different stop"
    );
    assertEquals(
      unshifted,
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR, 3),
      "a shift of one full palette wraps back onto the same stop"
    );
    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR, 2),
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR, -1),
      "negative shifts wrap the other way rather than clamping at stop 1"
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
