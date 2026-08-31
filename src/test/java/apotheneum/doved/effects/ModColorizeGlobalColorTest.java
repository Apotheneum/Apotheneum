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
import com.google.gson.JsonObject;

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

  /** A ModColorize with Global switched on, the way a performer enables it -- Global now
   * defaults off so an existing project is never silently repainted on load. */
  private static ModColorize globalOn(LX lx) {
    final ModColorize colorize = new ModColorize(lx);
    colorize.global.setValue(1);
    colorize.writeThroughForTest();
    return colorize;
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
    colorize.global.setValue(1);

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

    // Global off hands the device back: writeThrough must stop touching the pickers.
    colorize.global.setValue(0);
    lx.engine.run();
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

    // The round trip, which is the whole point: Off -> On -> Off must land back on the
    // hand-built look, not strand the device on the shared colour. Global is a modulation
    // target precisely so a MIDI switch can do this repeatedly, so a one-way toggle would
    // destroy a performer's setup the first time they flipped it.
    final int chosenEnd = LXColor.hsb(120, 100, 100);
    colorize.color2.setColor(chosenEnd);
    lx.engine.run();

    colorize.global.setValue(1);
    lx.engine.run();
    assertNotEquals(chosen, colorize.color1.getColor(),
      "Global on must actually take over the ends, or the restore below proves nothing");
    assertEquals(ColorMode.FIXED, colorize.colorMode.getEnum(),
      "switching Global on nudges the ramp onto the two ends it is driving");

    colorize.global.setValue(0);
    lx.engine.run();
    assertEquals(chosen, colorize.color1.getColor(),
      "Off -> On -> Off must restore this device's own Start colour");
    assertEquals(chosenEnd, colorize.color2.getColor(),
      "Off -> On -> Off must restore this device's own End colour");
    assertEquals(ColorMode.PALETTE, colorize.colorMode.getEnum(),
      "Off -> On -> Off must restore the colorMode the performer had chosen");

    // And it must survive repeating, not just the first cycle.
    for (int cycle = 0; cycle < 3; ++cycle) {
      colorize.global.setValue(1);
      lx.engine.run();
      colorize.global.setValue(0);
      lx.engine.run();
    }
    assertEquals(chosen, colorize.color1.getColor(),
      "repeated Global cycling must keep restoring the same local look");
    assertEquals(ColorMode.PALETTE, colorize.colorMode.getEnum());
  }

  /**
   * The captured local look has to survive a project round trip too. A project saved while
   * Global was on carries no live Start/End of its own -- those parameters hold the shared
   * colour at that moment -- so without persisting the capture, loading and then switching
   * Global off would strand the device on the shared colour: the same one-way trip, just
   * spread across a save/load instead of a single toggle.
   */
  @Test
  void theCapturedLocalLookSurvivesASaveLoadRoundTrip() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    register(lx);

    final ModColorize saved = new ModColorize(lx);
    // A frame while Global is off, before building the local look: the capture happens on the
    // observed off->on transition, so the device has to actually see the off state first.
    // On a live rig frames run continuously and this is automatic.
    saved.global.setValue(0);
    saved.writeThroughForTest();

    final int chosen = LXColor.hsb(300, 100, 100);
    saved.color1.setColor(chosen);
    saved.colorMode.setValue(ColorMode.PALETTE);

    saved.global.setValue(1);
    saved.writeThroughForTest();
    assertNotEquals(chosen, saved.color1.getColor(), "Global on must have taken over the ends");

    final JsonObject obj = new JsonObject();
    saved.save(lx, obj);
    // A real project load constructs the device from the file, not alongside the instance that
    // wrote it. Disposing first reproduces that; leaving both alive collides on every
    // serialized component id, this device's and its nested modulation engine's alike.
    saved.dispose();

    final ModColorize loaded = new ModColorize(lx);
    loaded.load(lx, obj);
    assertEquals(1, loaded.global.getValuei(), "Global's own value round-trips");

    loaded.global.setValue(0);
    loaded.writeThroughForTest();
    assertEquals(chosen, loaded.color1.getColor(),
      "the local Start colour captured before saving must come back when Global goes off");
    assertEquals(ColorMode.PALETTE, loaded.colorMode.getEnum(),
      "and so must the colorMode");
  }

  /**
   * A project written before Global existed carries no captured local look, but it does carry
   * the performer's real Start/End/colorMode -- and {@code super.load()} restoring them is the
   * only moment those values are visible, because Global defaults on and the write-through
   * immediately replaces all three with the shared colour.
   *
   * <p>Without capturing at that moment the loss was quiet and permanent: switching Global off
   * would "restore" the constructor's stock defaults, a look the performer never chose, and the
   * next save would write those defaults into the file as the local look. Nothing on screen
   * would have reported a change.
   */
  @Test
  void aLegacyProjectsColoursAreCapturedRatherThanQuietlyReplaced() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    register(lx);

    // Build a project file the way a pre-Global build would have written one: real Start/End
    // and colorMode, and none of the local-capture keys.
    final ModColorize legacy = new ModColorize(lx);
    final int savedStart = LXColor.hsb(300, 100, 100);
    final int savedEnd = LXColor.hsb(120, 100, 100);
    legacy.color1.setColor(savedStart);
    legacy.color2.setColor(savedEnd);
    legacy.colorMode.setValue(ColorMode.PALETTE);

    final JsonObject obj = new JsonObject();
    legacy.save(lx, obj);
    obj.remove("hasLocalColor");
    obj.remove("localColor1");
    obj.remove("localColor2");
    obj.remove("localColorMode");
    legacy.dispose();

    final ModColorize loaded = new ModColorize(lx);
    loaded.load(lx, obj);
    assertEquals(1, loaded.global.getValuei(), "Global defaults on for a project that predates it");
    assertNotEquals(savedStart, loaded.color1.getColor(),
      "Global on has taken over the ends, which is what makes the capture necessary");

    loaded.global.setValue(0);
    loaded.writeThroughForTest();
    assertEquals(savedStart, loaded.color1.getColor(),
      "switching Global off must hand back the Start colour the legacy project actually held, "
      + "not the constructor's stock default");
    assertEquals(savedEnd, loaded.color2.getColor(),
      "and the End colour it actually held");
    assertEquals(ColorMode.PALETTE, loaded.colorMode.getEnum(),
      "and the colorMode it actually held");
  }

  @Test
  void globalOnTakesBothEndsFromTheSharedColor() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    final ApotheneumColor global = register(lx);
    final ModColorize colorize = globalOn(lx);

    assertEquals(1, new ModColorize(lx).global.getValuei(),
      "Global defaults on -- a device follows the shared colour unless told otherwise");
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
    final int underNone = globalOn(lx).color1.getColor();

    global.axis.setValue(ApotheneumColor.Axis.SHAPE.ordinal());
    assertEquals(underNone, globalOn(lx).color1.getColor());

    global.axis.setValue(ApotheneumColor.Axis.INSIDE_OUTSIDE.ordinal());
    assertEquals(underNone, globalOn(lx).color1.getColor());
    assertEquals(global.primaryColor(null), underNone);
  }

  /** With no ApotheneumColor registered, both ends fall back to neutral white rather than
   * throwing -- the same fallback every other consumer of this state uses. */
  @Test
  void withNoSharedColorRegisteredBothEndsResolveNeutralWhite() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx);
    final ModColorize colorize = globalOn(lx);
    assertEquals(LXColor.WHITE, colorize.color1.getColor());
    assertEquals(LXColor.WHITE, colorize.color2.getColor());
  }
}
