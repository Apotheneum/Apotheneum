package apotheneum.doved.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import apotheneum.HeadlessLxTest;
import apotheneum.doved.modulators.ApotheneumColor;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.effect.color.ColorizeEffect.ColorMode;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.color.SolidPattern;

/**
 * {@link ModColorize} takes both gradient ends from the shared {@link ApotheneumColor},
 * unconditionally.
 *
 * <p>Asserted against {@code color1}/{@code color2} rather than rendered pixels because those
 * two <em>are</em> the contract: under {@code ColorMode.FIXED} they are the whole of what stock
 * {@code ColorizeEffect} ramps between, so proving they carry the resolved palette stops proves
 * the wiring. What the ramp does with them afterwards is stock LX behaviour this change does not
 * touch.
 *
 * <p><b>Only the first test here renders, and the order is pinned for that reason</b> — see
 * {@code ModGradientWriteThroughTest}'s class javadoc for the {@code LXPoint.index} counter
 * problem that makes a second rendering model in one surefire fork silently no-op rather than
 * fail. Everything after it goes through the constructor's own write-through, which needs no
 * engine run.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ModColorizeGlobalColorTest extends HeadlessLxTest {

  /** Three distinct stops, so "which stop did it land on" is answerable, not just "did it move". */
  private static void setSwatchStops(LX lx) {
    final LXSwatch swatch = lx.engine.palette.swatch;
    while (swatch.colors.size() < 3) {
      swatch.addColor();
    }
    for (int i = 0; i < 3; ++i) {
      swatch.getColor(i).primary.setColor(LXColor.hsb(80 * i, 95, 90));
    }
  }

  private static ApotheneumColor register(LX lx) {
    final ApotheneumColor color = new ApotheneumColor(lx);
    lx.engine.registerComponent(ApotheneumColor.PATH, color);
    return color;
  }

  /**
   * Everything that can only be seen on a running device, in one test — this class gets exactly
   * one rendering model per surefire fork.
   *
   * <p>{@code writeThrough()} fires from the constructor, from {@code load()}, and once per
   * frame, and only the per-frame path proves the thing this device exists for: a gesture on the
   * shared control reaching an already-running effect with nobody reconstructing it. A parameter
   * listener would miss exactly that, which is why the write-through is per frame (see {@code
   * ModGradientWriteThroughTest}).
   */
  @Test
  @Order(1)
  void aRunningDeviceTracksTheSharedColorAndItsOwnShift() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    final ApotheneumColor global = register(lx);

    final ModColorize colorize = new ModColorize(lx);
    final LXChannel channel =
      lx.engine.mixer.addChannel(new LXPattern[] { new SolidPattern(lx) });
    channel.fader.setValue(1);
    channel.addEffect(colorize);
    colorize.enabled.setValue(true);

    // Same: primary and secondary both sit on stop 1, so the two ends start out equal -- that
    // is a deliberate setting (see ApotheneumColor.pair's javadoc), not a broken wiring.
    global.pair.setValue(0);
    lx.engine.run();
    assertEquals(global.primaryColor(null), colorize.color1.getColor());
    assertEquals(global.secondaryColor(null), colorize.color2.getColor());
    assertEquals(colorize.color1.getColor(), colorize.color2.getColor(),
      "Same must collapse both ends onto one stop");
    assertEquals(ColorMode.FIXED, colorize.colorMode.getEnum(),
      "colorMode is held at FIXED, the only mode in which color1/color2 are the gradient");

    // A gesture on Pair reaches a running device's secondary end (color2) on the next frame --
    // Pair only ever moves secondary; primary is pinned at stop 1 (see ApotheneumColor.pair).
    final int atSame = colorize.color2.getColor();
    global.pair.setValue(1); // Near
    lx.engine.run();
    assertNotEquals(atSame, colorize.color2.getColor(),
      "moving the shared Pair must move a running ModColorize's color2 on the next frame");
    assertEquals(global.secondaryColor(null), colorize.color2.getColor());

    // A gesture on Swap reaches a running device's primary end (color1) on the next frame --
    // Swap is what exchanges which shared color primary reads.
    final int beforeSwap = colorize.color1.getColor();
    global.swap.setValue(1);
    lx.engine.run();
    assertNotEquals(beforeSwap, colorize.color1.getColor(),
      "moving the shared Swap must move a running ModColorize's color1 on the next frame");
    assertEquals(global.primaryColor(null), colorize.color1.getColor());
    global.swap.setValue(0);
    lx.engine.run();

    // Shift: a whole-stop offset that still tracks the gesture and stays on the palette.
    final int unshifted = colorize.color1.getColor();
    colorize.shift.setValue(1);
    lx.engine.run();
    assertNotEquals(unshifted, colorize.color1.getColor(),
      "a one-stop shift must land somewhere else on a three-stop palette");
    assertEquals(global.primaryColor(null, 1), colorize.color1.getColor());

    colorize.shift.setValue(3);
    lx.engine.run();
    assertEquals(unshifted, colorize.color1.getColor(),
      "a shift of one whole palette wraps back onto the stop it started from");

    colorize.shift.setValue(-1);
    lx.engine.run();
    assertEquals(global.primaryColor(null, 2), colorize.color1.getColor(),
      "negative shifts wrap the other way rather than clamping at the first stop");

    colorize.shift.setValue(0);
    lx.engine.run();

    // Invert exchanges the two ends: under FIXED they are the gradient direction.
    final int end1 = colorize.color1.getColor();
    final int end2 = colorize.color2.getColor();
    assertNotEquals(end1, end2, "the two ends must differ for the swap to be observable");
    colorize.invert.setValue(1);
    lx.engine.run();
    assertEquals(end2, colorize.color1.getColor(), "Invert must exchange the two ends");
    assertEquals(end1, colorize.color2.getColor());

    // A hand-set picker does not survive, and must not: these are readouts now, the same as
    // paletteIndex/paletteStops/paletteInvert already are on this device.
    colorize.invert.setValue(0);
    colorize.color1.setColor(LXColor.hsb(300, 100, 100));
    lx.engine.run();
    assertEquals(global.primaryColor(null), colorize.color1.getColor(),
      "color1 is driven every frame; a hand-set value is overwritten rather than kept");
  }

  @Test
  void bothEndsComeFromTheSharedColor() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    final ApotheneumColor global = register(lx);
    final ModColorize colorize = new ModColorize(lx);

    assertEquals(global.primaryColor(null), colorize.color1.getColor());
    assertEquals(global.secondaryColor(null), colorize.color2.getColor());
    assertEquals(ColorMode.FIXED, colorize.colorMode.getEnum());
  }

  /**
   * Surface-blind on purpose: a Colorize receives only {@code colors[]}, so there is no surface
   * identity left to resolve {@code axis} against. Whatever {@code axis} is set to, this device
   * resolves the shared pair unshifted rather than silently picking one surface's answer.
   */
  @Test
  void axisDoesNotShiftASurfaceBlindEffect() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    final ApotheneumColor global = register(lx);

    global.axis.setValue(ApotheneumColor.Axis.NONE.ordinal());
    final int underNone = new ModColorize(lx).color1.getColor();

    global.axis.setValue(ApotheneumColor.Axis.SHAPE.ordinal());
    assertEquals(underNone, new ModColorize(lx).color1.getColor());

    global.axis.setValue(ApotheneumColor.Axis.INSIDE_OUTSIDE.ordinal());
    assertEquals(underNone, new ModColorize(lx).color1.getColor());
    assertEquals(global.primaryColor(null), underNone);
  }

  /** With no ApotheneumColor registered, both ends fall back to neutral white rather than
   * throwing -- the same fallback every other consumer of this state uses. */
  @Test
  void withNoSharedColorRegisteredBothEndsResolveNeutralWhite() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    final ModColorize colorize = new ModColorize(lx);
    assertEquals(LXColor.WHITE, colorize.color1.getColor());
    assertEquals(LXColor.WHITE, colorize.color2.getColor());
  }

  /**
   * The crash regression, kept: driving color1/color2 while the device sat in LINKED or RELATIVE
   * fed ColorizeEffect's own derivation of color2 its own output, and LXEngine.run() died with a
   * StackOverflowError on the first frame. colorMode is held at FIXED every frame, so a saved
   * mode cannot stand.
   */
  @Test
  void aSavedNonFixedModeIsHeldAtFixedRatherThanRecursing() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    register(lx);
    final ModColorize colorize = new ModColorize(lx);
    for (ColorMode mode : new ColorMode[] { ColorMode.LINKED, ColorMode.RELATIVE, ColorMode.PALETTE }) {
      colorize.colorMode.setValue(mode);
      colorize.writeThroughForTest();
      assertEquals(ColorMode.FIXED, colorize.colorMode.getEnum(),
        mode + " must be held back to FIXED rather than left to feed color2 its own output");
    }
  }
}
