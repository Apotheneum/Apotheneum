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
 * {@link ModColorize} follows the shared {@link ApotheneumColor} rather than holding a second,
 * independent colour decision on top of it — and can still be tweaked a stop either way.
 *
 * <p>Asserted against {@code color1}/{@code color2} rather than against rendered pixels because
 * those two parameters <em>are</em> the contract: under {@code ColorMode.FIXED} they are the
 * whole of what stock {@code ColorizeEffect} ramps between, so proving they carry the resolved
 * palette stops proves the wiring. What the ramp then does with them is stock LX behaviour this
 * change does not touch.
 *
 * <p><b>Only the first test here renders, and the order is pinned for that reason</b> — see
 * {@code ModGradientWriteThroughTest}'s class javadoc for the {@code LXPoint.index} counter
 * problem that makes a second rendering model in one surefire fork silently no-op instead of
 * failing. Everything after it exercises the same {@code writeThrough()} path through the
 * constructor, which needs no engine run.
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
   * Everything that can only be seen on a running device, in one test.
   *
   * <p>{@code writeThrough()} fires from the constructor, from {@code load()}, and once per
   * frame — and only the per-frame path proves the thing this class exists for: a gesture on a
   * shared control, or a change to a knob on this device, reaching an already-running effect
   * with nobody reconstructing it. A parameter listener would miss exactly that (see {@code
   * ModGradientWriteThroughTest}), so asserting {@code shift}/{@code invert}/{@code global} only
   * against freshly-constructed instances would test the constructor and quietly not test the
   * mechanism. They are all here rather than in four tests because this class gets exactly one
   * rendering model per surefire fork.
   */
  @Test
  @Order(1)
  void aRunningDeviceTracksTheSharedColorAndItsOwnTweaks() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    final ApotheneumColor global = register(lx);

    final ModColorize colorize = new ModColorize(lx);
    final LXChannel channel =
      lx.engine.mixer.addChannel(new LXPattern[] { new SolidPattern(lx) });
    channel.fader.setValue(1);
    channel.addEffect(colorize);
    colorize.enabled.setValue(true);

    global.pair.setValue(0);
    lx.engine.run();
    assertEquals(global.primaryColor(null), colorize.color1.getColor());
    assertEquals(global.secondaryColor(null), colorize.color2.getColor());
    assertEquals(ColorMode.FIXED, colorize.colorMode.getEnum(),
      "Global must force the ramp onto the two ends it is driving");

    // A gesture on the shared control reaches a running device on the next frame.
    final int atPairOne = colorize.color1.getColor();
    global.pair.setValue(1);
    lx.engine.run();
    assertNotEquals(atPairOne, colorize.color1.getColor(),
      "moving the shared Pair must move a running ModColorize on the next frame");
    assertEquals(global.primaryColor(null), colorize.color1.getColor());

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

    // Invert doubles as the local swap: under FIXED the two ends are the gradient direction,
    // so exchanging them is exactly what inverting it means.
    final int end1 = colorize.color1.getColor();
    final int end2 = colorize.color2.getColor();
    assertNotEquals(end1, end2, "the two ends must differ for the swap to be observable");
    colorize.invert.setValue(1);
    lx.engine.run();
    assertEquals(end2, colorize.color1.getColor(), "Invert must exchange the two ends");
    assertEquals(end1, colorize.color2.getColor());
    colorize.invert.setValue(0);
    lx.engine.run();

    // Global off restores manual control: a hand-set picker survives every later frame, and
    // colorMode is no longer forced.
    colorize.global.setValue(0);
    colorize.colorMode.setValue(ColorMode.PALETTE);
    final int chosen = LXColor.hsb(300, 100, 100);
    colorize.color1.setColor(chosen);
    global.pair.setValue(0);
    lx.engine.run();
    lx.engine.run();
    assertEquals(chosen, colorize.color1.getColor(),
      "with Global off, writeThrough must leave this device's own picker alone");
    assertEquals(ColorMode.PALETTE, colorize.colorMode.getEnum(),
      "with Global off, colorMode must no longer be forced to FIXED");
  }

  @Test
  void globalOnTakesBothEndsFromTheSharedColor() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    final ApotheneumColor global = register(lx);
    final ModColorize colorize = new ModColorize(lx);

    assertEquals(1, colorize.global.getValuei(), "Global defaults on");
    assertEquals(global.primaryColor(null), colorize.color1.getColor());
    assertEquals(global.secondaryColor(null), colorize.color2.getColor());
  }

  /**
   * Surface-blind on purpose: a Colorize receives only {@code colors[]}, so there is no surface
   * identity left to resolve {@code axis} against. Whatever {@code axis} is set to, this device
   * must resolve the shared pair unshifted rather than silently picking one surface's answer.
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
}
