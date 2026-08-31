package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  /**
   * 2026-08-31's replacement for the old design/color-system.md &#167;4 relay table, which no
   * longer applies -- {@link ApotheneumColor#pair} stopped selecting a base stop and now only
   * chooses how far apart the room's two colours sit ({@code Same}/{@code Near}/{@code Far}),
   * with primary pinned at stop 1 and secondary at {@code 1 + pair}; {@link
   * ApotheneumColor#swap} still exchanges which of those two is primary. This is the new table,
   * exhaustive over all three {@code pair} values and both {@code swap} values.
   */
  @Test
  void pairChoosesDistanceAndSwapChoosesWhichRoleIsPrimary() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = register(lx);

    // Same: both roles sit on stop 1, regardless of swap -- exchanging two equal roles is a
    // no-op, which is the point of Same (see ApotheneumColor.pair's javadoc).
    color.pair.setValue(0);
    color.swap.setValue(0);
    assertEquals(1, color.primaryIndex());
    assertEquals(1, color.secondaryIndex());

    color.pair.setValue(0);
    color.swap.setValue(1);
    assertEquals(1, color.primaryIndex());
    assertEquals(1, color.secondaryIndex());

    // Near: stops 1 and 2, the wheel-neighbour field pair.
    color.pair.setValue(1);
    color.swap.setValue(0);
    assertEquals(1, color.primaryIndex());
    assertEquals(2, color.secondaryIndex());

    color.pair.setValue(1);
    color.swap.setValue(1);
    assertEquals(2, color.primaryIndex());
    assertEquals(1, color.secondaryIndex());

    // Far: stops 1 and 3, the split-complement accent.
    color.pair.setValue(2);
    color.swap.setValue(0);
    assertEquals(1, color.primaryIndex());
    assertEquals(3, color.secondaryIndex());

    color.pair.setValue(2);
    color.swap.setValue(1);
    assertEquals(3, color.primaryIndex());
    assertEquals(1, color.secondaryIndex());
  }

  @Test
  void everySurfaceMatchesTheSharedIndexWithZeroOffsets() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = register(lx);

    color.pair.setValue(0);
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());

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

  /**
   * 2026-08-31: {@link Axis#SHAPE} no longer shifts the far-side surfaces one stop up -- it
   * <em>exchanges</em> which of the two shared colours (primary/secondary) the cylinder reads,
   * relative to the cube. That exchange is only observable when primary and secondary actually
   * differ, so this needs {@code pair = Near} (or {@code Far}); at {@code pair = Same} the two
   * roles are equal and exchanging them is invisible by design (see {@code
   * pairChoosesDistanceAndSwapChoosesWhichRoleIsPrimary}).
   */
  @Test
  void shapeAxisMatchesCubeSurfacesAndDiffersFromCylinderSurfaces() {
    final LX lx = newHeadlessLx();
    // A fresh LX's default swatch carries one color; a second, distinct one is needed so the
    // exchange Shape performs can actually be told apart from doing nothing.
    lx.engine.palette.swatch.addColor();
    lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(0, 90, 70));
    lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(200, 90, 70));

    final ApotheneumColor color = register(lx);
    color.pair.setValue(1); // Near: primary = stop 1, secondary = stop 2, so there is something to exchange
    color.swap.setValue(0);
    color.axis.setValue(Axis.SHAPE.ordinal());

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
      "Shape must differ cube from cylinder -- they are reading the two shared colors the other way round"
    );
    // The exchange, stated explicitly: the cylinder's primary is the cube's secondary, and
    // vice versa -- not some third stop reached by shifting.
    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      color.secondaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "under Shape, the cylinder's primary must be exactly the cube's secondary"
    );
    assertEquals(
      color.secondaryColor(ApotheneumColor.Surface.CYLINDER_EXTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "under Shape, the cylinder's secondary must be exactly the cube's primary"
    );
  }

  /** {@link Axis#INSIDE_OUTSIDE}'s counterpart to the Shape test above: the exchange happens
   * between interiors and exteriors instead of between cylinder and cube. Same reasoning for why
   * {@code pair} must be at least {@code Near}. */
  @Test
  void insideOutsideAxisMatchesExteriorsAndDiffersFromInteriors() {
    final LX lx = newHeadlessLx();
    lx.engine.palette.swatch.addColor();
    lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(0, 90, 70));
    lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(200, 90, 70));

    final ApotheneumColor color = register(lx);
    color.pair.setValue(1); // Near: primary = stop 1, secondary = stop 2
    color.swap.setValue(0);
    color.axis.setValue(Axis.INSIDE_OUTSIDE.ordinal());

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
      "In/Out must differ exterior from interior -- they are reading the two shared colors the other way round"
    );
    // The exchange, stated explicitly, as in the Shape test above.
    assertEquals(
      color.primaryColor(ApotheneumColor.Surface.CUBE_INTERIOR),
      color.secondaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "under In/Out, the interior's primary must be exactly the exterior's secondary"
    );
    assertEquals(
      color.secondaryColor(ApotheneumColor.Surface.CUBE_INTERIOR),
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "under In/Out, the interior's secondary must be exactly the exterior's primary"
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

  /**
   * 2026-08-31: {@link Axis} no longer shifts a stop, so it cannot be the thing that overruns
   * the palette and needs to wrap. {@link ApotheneumColor#pair}'s {@code Far} setting is now the
   * route that can: {@code farStop() = 1 + pair}, so {@code Far} (pair value 2) asks for stop 3
   * unconditionally, regardless of how many stops the live swatch actually holds. On a fresh
   * LX's one-stop default swatch that request must wrap back to stop 1, not plateau or clamp at
   * stop 1 by coincidence -- see {@code pairFarWrapsAroundTheLiveStopCountNotTheSwatchCapacity}
   * below for the two-stop case where wrapping and clamping actually produce different answers.
   */
  @Test
  void pairFarWrapsAtTheCeilingRatherThanClamping() {
    final LX lx = newHeadlessLx();
    final ApotheneumColor color = register(lx);

    color.pair.setValue(2); // Far: primary = stop 1, secondary = stop 3
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());
    assertEquals(3, color.secondaryIndex());
    assertEquals(
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 0),
      color.secondaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "stop 3 must wrap around a one-stop swatch back to stop 1, not clamp at stop 3"
    );
  }

  /**
   * The case the one-stop test above structurally cannot see, and the reason this one exists.
   *
   * <p>{@code wrapIndex} wrapped around {@code LXSwatch.MAX_COLORS} -- the swatch's fixed
   * <em>capacity</em> -- rather than around the number of stops the swatch actually holds. On a
   * one-colour swatch that is invisible: every index collapses onto stop 1 whether it wrapped or
   * was clamped, so the test above passed throughout the bug's life. On the two-stop swatch here
   * the two behaviours separate completely: {@code pair = Far} asks for stop 3, which
   * <em>wrapping</em> around two stops answers as stop 1 and <em>clamping</em> answers as stop 2
   * -- the same stop {@code Near} would have given. Clamping therefore made the {@code Far}
   * setting indistinguishable from {@code Near} on any palette smaller than the swatch's
   * capacity, which is every palette this show uses.
   *
   * <p>Asserted against the named stop colours, not merely {@code assertNotEquals}: "these two
   * differ" would also pass if the request landed on some third wrong stop.
   */
  @Test
  void pairFarWrapsAroundTheLiveStopCountNotTheSwatchCapacity() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 2);
    final ApotheneumColor color = register(lx);

    final int stop1 = ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 0);
    final int stop2 = ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, 1);
    assertNotEquals(stop1, stop2, "the two stops must be distinguishable for this test to mean anything");

    color.pair.setValue(2); // Far
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());

    assertEquals(1, color.primaryIndex());
    assertEquals(3, color.secondaryIndex());
    assertEquals(
      stop1,
      color.primaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "primary sits on stop 1"
    );
    assertEquals(
      stop1,
      color.secondaryColor(ApotheneumColor.Surface.CUBE_EXTERIOR),
      "stop 3 must wrap around a two-stop swatch back to stop 1, not clamp back onto stop 2"
    );
  }

  /** The same failure stated as the symptom a performer would report: the Axis knob does
   * nothing. Kept separate from the test above because this is the observable claim, and it
   * should keep holding even if the exact stop arithmetic above is ever redesigned. Needs
   * {@code pair = Near} (not the default {@code Same}) -- with equal roles there is nothing for
   * Axis to visibly exchange, and this is deliberate (see {@code
   * pairChoosesDistanceAndSwapChoosesWhichRoleIsPrimary}), not a gap this test should paper
   * over. */
  @Test
  void axisStillSeparatesSurfacesOnASwatchSmallerThanCapacity() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 2);
    final ApotheneumColor color = register(lx);
    color.pair.setValue(1); // Near: two distinct stops to exchange
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

  /**
   * The headline invariant of the 2026-08-31 redesign, exhaustively: no combination of {@code
   * pair}/{@code swap}/{@code axis} may ever put more than two distinct palette stops on the
   * wall at once. This is the regression guard for the bug the redesign fixes -- the old scheme
   * (base stop from {@code pair}, secondary pinned one stop above it, {@code axis} shifting the
   * whole pair one further stop for half the surfaces) could reach three stops simultaneously on
   * a three-stop swatch, which is the entire palette including the accent, and is exactly what
   * the owner reported seeing live (see {@link ApotheneumColor#pair}'s javadoc for the quote).
   *
   * <p>Collects every surface's primary <em>and</em> secondary color, for every {@code pair}
   * value, every {@code swap} value, and every {@link Axis}, and asserts the resulting set never
   * exceeds two entries.
   */
  @Test
  void atMostTwoStopsEverReachTheWallAtOnce() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 3);
    final ApotheneumColor color = register(lx);

    for (int pairValue = 0; pairValue < 3; ++pairValue) {
      for (int swapValue = 0; swapValue < 2; ++swapValue) {
        for (Axis axis : Axis.values()) {
          color.pair.setValue(pairValue);
          color.swap.setValue(swapValue);
          color.axis.setValue(axis.ordinal());

          final java.util.Set<Integer> colorsOnTheWall = new java.util.HashSet<>();
          for (ApotheneumColor.Surface surface : ApotheneumColor.Surface.values()) {
            colorsOnTheWall.add(color.primaryColor(surface));
            colorsOnTheWall.add(color.secondaryColor(surface));
          }

          assertTrue(
            colorsOnTheWall.size() <= 2,
            "pair=" + pairValue + " swap=" + swapValue + " axis=" + axis + " put "
            + colorsOnTheWall.size() + " distinct stops on the wall at once -- at most two are "
            + "ever allowed"
          );
        }
      }
    }
  }
}
