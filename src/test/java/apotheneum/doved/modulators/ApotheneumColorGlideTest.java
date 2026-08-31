package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import apotheneum.doved.modulators.ApotheneumColor.Axis;
import apotheneum.doved.modulators.ApotheneumColor.Surface;
import apotheneum.doved.patterns.ColorNativePattern;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXParameterModulation;
import heronarts.lx.parameter.CompoundParameter;

/**
 * Coverage for the {@code glide}/{@code glideTimeSecs} crossfade added to {@link
 * ApotheneumColor} — see that class's javadoc above {@link ApotheneumColor#glide} for the
 * design. This file does not touch {@code src/main}; it only exercises the public surface
 * ({@code glide}, {@code glideTimeSecs}, {@code pair}/{@code swap}/{@code axis},
 * {@code primaryColor}/{@code secondaryColor}, {@code dispose()}).
 *
 * <h2>How "direct" (no-glide) expectations are computed</h2>
 *
 * Several tests need an independent oracle for what a resolution would be <em>without</em> any
 * crossfade in progress, to compare against the device under test. Rather than re-deriving
 * {@code ApotheneumColor}'s private index/wrap arithmetic by hand (which would only prove this
 * test file agrees with itself), most of these build a second, throwaway
 * {@code ApotheneumColor} against the same {@code lx} — same trick {@code mirrorOfStale} already
 * uses in {@code src/main} — set it to the same {@code pair}/{@code swap}/{@code axis} with
 * {@code glide} left at its default {@code false}, and read its {@code primaryColor}/{@code
 * secondaryColor}. That reference instance runs the exact same {@code directColor} path the
 * device under test falls back to whenever glide is off, so any divergence between the two is a
 * real bug, not an artifact of two different formulas. The reference is disposed after each use
 * so its own loop task does not linger.
 *
 * <h2>Driving frames deterministically</h2>
 *
 * {@code lx.engine.run()} normally measures a real wall-clock delta, which would make asserting
 * fade progress at a specific frame count flaky. Every test that needs to move a fade calls
 * {@code lx.engine.setFixedDeltaMs(ms)} first — the same mechanism {@code
 * ModGradientWriteThroughTest} and several {@code apotheneum.mcslee}/{@code
 * apotheneum.thesilveresa} tests already use to make {@code LXLoopTask.loop(deltaMs)} advance by
 * an exact, known amount per {@code run()} call — so the number of frames needed to complete or
 * bisect a given {@code glideTimeSecs} is exact arithmetic, not a guess.
 */
public class ApotheneumColorGlideTest extends HeadlessLxTest {

  private static ApotheneumColor register(LX lx) {
    final ApotheneumColor color = new ApotheneumColor(lx);
    lx.engine.registerComponent(ApotheneumColor.PATH, color);
    return color;
  }

  /** Same shape as {@code ApotheneumColorTest}'s private helper of the same name -- duplicated
   * here rather than shared, since that one is {@code private} to its own class. Gives each
   * stop a distinct, recognisable hue so a fade's start and end are never accidentally equal. */
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
   * Asserts every surface (and the {@code null}/{@code ModColorize} surface) on {@code color}
   * matches a freshly-built, glide-off reference instance set to the same three parameters. See
   * the class javadoc's "How direct expectations are computed" section.
   */
  private static void assertMatchesGlideOffReference(
    LX lx, ApotheneumColor color, int pairValue, int swapValue, int axisValue
  ) {
    color.pair.setValue(pairValue);
    color.swap.setValue(swapValue);
    color.axis.setValue(axisValue);

    final ApotheneumColor reference = new ApotheneumColor(lx);
    try {
      reference.pair.setValue(pairValue);
      reference.swap.setValue(swapValue);
      reference.axis.setValue(axisValue);
      assertFalse(reference.glide.isOn(), "the reference instance must itself be glide-off");

      for (Surface surface : Surface.values()) {
        assertEquals(reference.primaryColor(surface), color.primaryColor(surface),
          "pair=" + pairValue + " swap=" + swapValue + " axis=" + axisValue + " surface=" + surface);
        assertEquals(reference.secondaryColor(surface), color.secondaryColor(surface));
      }
      assertEquals(reference.primaryColor(null), color.primaryColor(null));
      assertEquals(reference.secondaryColor(null), color.secondaryColor(null));
    } finally {
      reference.dispose();
    }
  }

  /**
   * The headline "unchanged when off" claim: with {@link ApotheneumColor#glide} at its default
   * {@code false}, resolution must match an independent glide-off reference, before any engine
   * frame runs, after several run, and again after moving {@code pair}/{@code swap}/{@code axis}.
   * Running frames at all matters here because {@code glideTask} is unconditionally registered
   * and unconditionally called every frame (see {@link ApotheneumColor#advanceGlide}'s javadoc) —
   * a bug that let the off-path mutate the cache anyway would only show up once frames actually
   * run.
   */
  @Test
  void glideOffMatchesIndependentReferenceBeforeAndAfterFramesAndParameterChanges() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 3);
    final ApotheneumColor color = register(lx);
    assertFalse(color.glide.isOn(), "glide defaults off");

    assertMatchesGlideOffReference(lx, color, 0, 0, Axis.NONE.ordinal());

    lx.engine.setFixedDeltaMs(16);
    for (int i = 0; i < 5; ++i) {
      lx.engine.run();
    }
    assertMatchesGlideOffReference(lx, color, 0, 0, Axis.NONE.ordinal());

    // Move all three parameters and repeat, both before and after more frames run.
    assertMatchesGlideOffReference(lx, color, 2, 1, Axis.SHAPE.ordinal());
    for (int i = 0; i < 5; ++i) {
      lx.engine.run();
    }
    assertMatchesGlideOffReference(lx, color, 2, 1, Axis.SHAPE.ordinal());
  }

  /**
   * With glide on and a nonzero {@code glideTimeSecs}, a {@code pair} change must not be visible
   * at all until the loop task next runs (the resolved color is still the OLD one immediately
   * after the parameter write), and must be fully visible once enough frames have run.
   */
  @Test
  void glideStartsFromOldColorAndArrivesAtNewColorAfterEnoughFrames() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 3);
    final ApotheneumColor color = register(lx);
    color.pair.setValue(0); // Same: primary == secondary == stop 1
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());
    color.glide.setValue(true);
    color.glideTimeSecs.setValue(2); // seconds

    lx.engine.setFixedDeltaMs(500);
    lx.engine.run(); // primes the cache at the current (Same) resolution

    final int oldSecondary = color.secondaryColor(Surface.CUBE_EXTERIOR);
    color.pair.setValue(1); // Near: secondary moves to stop 2 -- pair only ever moves secondary
    assertEquals(oldSecondary, color.secondaryColor(Surface.CUBE_EXTERIOR),
      "immediately after the change, before the loop task advances the fade, the resolved "
      + "color must still be the OLD one");

    // 2000ms of glide at 500ms/frame completes in exactly 4 post-change frames; run one spare.
    for (int i = 0; i < 5; ++i) {
      lx.engine.run();
    }
    final int expectedNewSecondary =
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, color.secondaryIndex() - 1);
    assertNotEquals(oldSecondary, expectedNewSecondary,
      "the two stops must actually differ for this test to mean anything");
    assertEquals(expectedNewSecondary, color.secondaryColor(Surface.CUBE_EXTERIOR),
      "after enough frames the fade must have completed to the NEW color");
  }

  /**
   * The reason this class interpolates in HSB rather than RGB, made concrete: two fully
   * saturated, complementary (180-degree) palette stops -- red and cyan -- crossfading through
   * RGB would average to a near-desaturated grey exactly at the midpoint ({@code (255,0,0)} and
   * {@code (0,255,255)} average to {@code (127,127,127)}, saturation ~0). {@link
   * ApotheneumColor#lerpHsb} instead walks the hue wheel the short way at full saturation and
   * brightness throughout, so the midpoint must land near stop 1's own saturation (100), not near
   * zero. The assertion is written against saturation specifically, and would fail under a naive
   * RGB lerp, which is the point.
   */
  @Test
  void midFadeIsAGenuineHsbBlendNotADesaturatedRgbMidpoint() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 2);
    lx.engine.palette.swatch.getColor(0).primary.setColor(LXColor.hsb(0, 100, 100)); // red
    lx.engine.palette.swatch.getColor(1).primary.setColor(LXColor.hsb(180, 100, 100)); // cyan

    final ApotheneumColor color = register(lx);
    color.pair.setValue(0); // Same: secondary starts on stop 1 (red)
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());
    color.glide.setValue(true);
    color.glideTimeSecs.setValue(2);

    lx.engine.setFixedDeltaMs(1000); // one frame = exactly half of a 2-second glide
    lx.engine.run(); // primes at pair=Same (red)

    final int oldColor = color.secondaryColor(Surface.CUBE_EXTERIOR);
    color.pair.setValue(1); // Near: secondary's target becomes stop 2 (cyan)
    final int newColorDirect =
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, color.secondaryIndex() - 1);

    lx.engine.run(); // exactly one frame: fade goes from 0 to 1000/2000 = 0.5 within this frame
    final int midColor = color.secondaryColor(Surface.CUBE_EXTERIOR);

    assertNotEquals(oldColor, midColor, "mid-fade must not still be the old color");
    assertNotEquals(newColorDirect, midColor, "mid-fade must not already be the new color");

    final float midSaturation = LXColor.s(midColor);
    assertTrue(midSaturation > 80,
      "an HSB crossfade between two fully-saturated wheel-neighbour stops must stay near full "
      + "saturation at the midpoint (got " + midSaturation + "); an RGB lerp between red and "
      + "cyan would desaturate to near-gray (~0) at exactly this point, which is the bug this "
      + "class's HSB interpolation exists to avoid"
    );
  }

  /** {@code glideTimeSecs = 0} must land on the new color after exactly one frame, even with
   * {@code glide} on -- there is no such thing as a fade with zero duration. */
  @Test
  void glideTimeSecsZeroLandsImmediatelyEvenWithGlideOn() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 2);
    final ApotheneumColor color = register(lx);
    color.pair.setValue(0);
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());
    color.glide.setValue(true);
    color.glideTimeSecs.setValue(0);

    lx.engine.setFixedDeltaMs(16);
    lx.engine.run(); // primes

    final int before = color.secondaryColor(Surface.CUBE_EXTERIOR);
    color.pair.setValue(1); // Near
    lx.engine.run(); // glideMs <= 0 forces fade straight to 1 within this one frame

    final int expectedNew =
      ColorNativePattern.paletteColor(lx.engine.palette.swatch.colors, color.secondaryIndex() - 1);
    assertNotEquals(before, expectedNew, "the two stops must differ for this test to mean anything");
    assertEquals(expectedNew, color.secondaryColor(Surface.CUBE_EXTERIOR),
      "glideTimeSecs = 0 must land on the new color after exactly one frame, with no fade");
  }

  /**
   * Turning {@code glide} on part-way through a session must not jump to black or to whatever the
   * cache last happened to hold -- {@link ApotheneumColor#advanceGlide}'s re-prime-on-re-enable
   * branch (the {@code !this.glide.isOn()} early return setting {@code primed = false}) exists
   * exactly for this. The next frame after enabling must resolve the CURRENT live parameters,
   * matching a freshly-built glide-off reference at the same settings.
   */
  @Test
  void enablingGlideMidwayResolvesTheLiveColorNotAStaleOrBlackValue() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 3);
    final ApotheneumColor color = register(lx);
    color.pair.setValue(1);
    color.swap.setValue(1);
    color.axis.setValue(Axis.SHAPE.ordinal());

    lx.engine.setFixedDeltaMs(16);
    for (int i = 0; i < 3; ++i) {
      lx.engine.run(); // glide is off throughout; the loop task no-ops beyond primed = false
    }
    color.pair.setValue(2);
    lx.engine.run();

    color.glide.setValue(true);
    lx.engine.run(); // exactly one frame after enabling

    final ApotheneumColor reference = new ApotheneumColor(lx);
    try {
      reference.pair.setValue(2);
      reference.swap.setValue(1);
      reference.axis.setValue(Axis.SHAPE.ordinal());
      for (Surface surface : Surface.values()) {
        assertEquals(reference.primaryColor(surface), color.primaryColor(surface),
          "the first frame after enabling Glide must resolve the live color, not black or a "
          + "stale value, for " + surface);
        assertEquals(reference.secondaryColor(surface), color.secondaryColor(surface));
      }
    } finally {
      reference.dispose();
    }
  }

  /** A {@code stopShift} outside the fixed cache's {@code -4..+4} range must resolve directly --
   * matching a glide-off reference exactly, and above all not throwing (an {@code
   * ArrayIndexOutOfBoundsException} against the 90-int cache is the failure mode this guards). */
  @Test
  void stopShiftOutsideCacheRangeResolvesDirectlyWithoutThrowing() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 3);
    final ApotheneumColor color = register(lx);
    color.pair.setValue(1);
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());
    color.glide.setValue(true);
    color.glideTimeSecs.setValue(2);

    lx.engine.setFixedDeltaMs(16);
    lx.engine.run(); // primes, so the in-range cache is actually populated and could be confused for these

    final ApotheneumColor reference = new ApotheneumColor(lx);
    try {
      reference.pair.setValue(1);
      reference.swap.setValue(0);
      reference.axis.setValue(Axis.NONE.ordinal());

      for (int shift : new int[] { 5, -5, 100, -100 }) {
        final int shiftValue = shift;
        assertDoesNotThrow(() -> color.primaryColor(Surface.CUBE_EXTERIOR, shiftValue),
          "a stopShift of " + shiftValue + " must not throw");
        assertEquals(
          reference.primaryColor(Surface.CUBE_EXTERIOR, shiftValue),
          color.primaryColor(Surface.CUBE_EXTERIOR, shiftValue),
          "stopShift " + shiftValue + " falls outside the -4..+4 cache and must resolve "
          + "directly, with no glide applied"
        );
      }
    } finally {
      reference.dispose();
    }
  }

  /**
   * The case a listener-based implementation would miss entirely: {@code pair} moves through
   * MODULATION (an {@link LXCompoundModulation} wired onto it), never through a direct {@code
   * setValue} on its base. {@link ApotheneumColor#advanceGlide}'s own javadoc explains why it
   * polls {@code getValuei()} every frame instead of listening for a base-value change -- exactly
   * because modulation moves the effective value without ever touching the base or firing a
   * listener. This wires a plain {@code CompoundParameter} as the modulation source (no UI, no
   * modulator needed -- {@code LXCompoundModulation} only requires an {@code
   * LXNormalizedParameter} source and reads it on demand, not on a timer) and drives it directly.
   */
  @Test
  void modulationMovingTheEffectiveValueIsPolledEvenWithNoBaseValueChange() throws LXParameterModulation.ModulationException {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 3);
    final ApotheneumColor color = register(lx);
    color.pair.setValue(0); // Same: base stays here for the rest of the test
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());
    color.glide.setValue(true);
    color.glideTimeSecs.setValue(2);

    lx.engine.setFixedDeltaMs(500);
    lx.engine.run(); // primes at pair's base+modulated value, 0 (Same)

    final int oldSecondary = color.secondaryColor(Surface.CUBE_EXTERIOR);

    // LXCompoundModulation refuses a source parameter with no owning component ("May not create
    // parameter modulation from source registered to no component"), and separately requires
    // that owning component's ancestry to reach lx.engine (the modulation engine's own parent) --
    // "not in valid scope for modulation engine" otherwise. This tiny throwaway host exists only
    // to satisfy both: it owns the source parameter and is registered directly on lx.engine, the
    // same registerComponent call ApotheneumColor itself uses.
    final ModulationSourceHost sourceHost = new ModulationSourceHost(lx);
    lx.engine.registerComponent("testModulationSource", sourceHost);
    final CompoundParameter source = sourceHost.value;
    final LXCompoundModulation modulation =
      new LXCompoundModulation(lx.engine.modulation, source, color.pair);
    // Registering with the engine hands ownership to lx -- HeadlessLxTest's disposal of this
    // test's LX instance disposes the whole modulation graph, so this test must not also call
    // modulation.dispose() itself (that double-disposes and NPEs inside teardown).
    lx.engine.modulation.addModulation(modulation);
    modulation.range.setValue(1.0); // full positive depth
    source.setValue(1.0); // push pair's EFFECTIVE value up; its base is never touched

    assertEquals(0, color.pair.getBaseValuei(),
      "the modulation must move the effective value without touching the base");
    assertNotEquals(0, color.pair.getValuei(),
      "sanity check: the modulation must actually have moved the effective (modulated) value");

    // The loop task has not run since the modulation was wired, so the resolved color is
    // still whatever was cached at prime time -- same "not yet advanced" shape as a direct
    // pair.setValue would produce.
    assertEquals(oldSecondary, color.secondaryColor(Surface.CUBE_EXTERIOR),
      "before the loop task next polls, the resolved color must still be the OLD one");

    for (int i = 0; i < 5; ++i) {
      lx.engine.run();
    }
    final int expectedNewSecondary = ColorNativePattern.paletteColor(
      lx.engine.palette.swatch.colors, color.secondaryIndex() - 1);
    assertNotEquals(oldSecondary, expectedNewSecondary,
      "the two stops must actually differ for this test to mean anything");
    assertEquals(expectedNewSecondary, color.secondaryColor(Surface.CUBE_EXTERIOR),
      "a fade driven purely by modulation -- never a base-value write -- must still reach "
      + "the new color once the loop task has run enough frames"
    );
  }

  /**
   * {@code dispose()} removes the crossfade loop task from {@code lx.engine} -- after disposing,
   * further engine frames must not throw, and must not keep mutating this instance's resolved
   * color. The latter is the more interesting half: {@code glidedColor} still checks {@code
   * glide.isOn() && primed}, both of which remain {@code true} after {@code dispose()} (neither
   * is part of what dispose touches), so a disposed-but-still-referenced instance is left
   * pointing at whichever color the cache held at the moment of disposal, frozen -- it does not
   * fall back to tracking the live parameters, because nothing is left to advance the cache that
   * {@code glidedColor} prefers.
   */
  @Test
  void disposeRemovesTheLoopTaskAndFurtherFramesDoNotThrowOrKeepMutatingTheInstance() {
    final LX lx = newHeadlessLx();
    setSwatchStops(lx, 3);
    // Not registered on the engine -- a throwaway instance this test builds and disposes itself,
    // matching dispose()'s own javadoc ("this matters ... for tests, which build and dispose
    // many instances against one LX").
    final ApotheneumColor color = new ApotheneumColor(lx);
    color.pair.setValue(1);
    color.swap.setValue(0);
    color.axis.setValue(Axis.NONE.ordinal());
    color.glide.setValue(true);
    color.glideTimeSecs.setValue(2);

    lx.engine.setFixedDeltaMs(500);
    lx.engine.run(); // primes

    color.dispose();
    final int frozen = color.primaryColor(Surface.CUBE_EXTERIOR);

    assertDoesNotThrow(() -> {
      for (int i = 0; i < 5; ++i) {
        lx.engine.run();
      }
    }, "engine frames must keep running fine after this instance's glide task is removed");

    assertEquals(frozen, color.primaryColor(Surface.CUBE_EXTERIOR),
      "with the crossfade task removed, further frames must not keep mutating this instance's "
      + "cached color");

    // A parameter change after dispose is not picked up either -- there is no loop task left to
    // poll it, and glidedColor still prefers the (now permanently frozen) cache over resolving
    // directly, since glide is still on and the cache is still primed.
    color.pair.setValue(2);
    assertEquals(frozen, color.primaryColor(Surface.CUBE_EXTERIOR),
      "with no loop task left to poll it, a parameter change after dispose is not reflected");
  }

  /** A minimal {@link LXComponent} that exists only to own a {@link CompoundParameter}, so that
   * parameter can be used as an {@link LXCompoundModulation} source -- see the comment where this
   * is constructed for why a bare, parentless parameter is rejected. */
  private static final class ModulationSourceHost extends LXComponent {
    final CompoundParameter value = new CompoundParameter("Src", 0);

    ModulationSourceHost(LX lx) {
      super(lx, "Test Modulation Source");
      addParameter("value", this.value);
    }
  }
}
