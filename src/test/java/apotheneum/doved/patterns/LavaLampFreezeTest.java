package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;

/**
 * Speed reaching a genuine zero in {@link LavaLamp}, and what that has to mean: the lamp holds
 * the exact frame it was on, and picks up again from that frame when the knob comes back up.
 *
 * <p>A hold is a performance move — a lamp caught mid-trip, held while something else happens,
 * released. The knob used to bottom out at .1, which is a crawl rather than a stop: over the
 * eight seconds this test runs at the bottom of the range, a tenth speed still carries the lava
 * a visible distance. So the assertion is not "slow" but "byte-identical", frame after frame,
 * across the whole colour buffer. That is a strong enough statement to be worth making: every
 * consumer of the frame step has to degrade to a no-op at zero rather than merely to something
 * small, and the ones that take no step at all — the door containment — have to be idempotent
 * on a body they have already placed.</p>
 *
 * <p>The freeze is entered by driving the knob to the bottom of its travel rather than by
 * setting the number zero, so the test fails if the minimum is ever raised off zero again: at
 * a minimum of .1 the bottom of the knob is .1, and the buffer moves.</p>
 *
 * <p>Separate class from {@link LavaLampDoorTest} and {@link LavaLampFieldSpanTest} for the
 * reason those two are separate from each other: {@code Apotheneum} holds its model state
 * statically and {@code initialize()} returns early once it has run, so it is one {@code LX}
 * per process. Surefire runs {@code reuseForks=false}, which makes a separate class a separate
 * JVM.</p>
 */
public class LavaLampFreezeTest extends HeadlessLxTest {

  private static final double FIXED_DELTA_MS = 1000. / 60;
  /** Frames to run the lamp normally before it is held, so the held frame is a live one. */
  private static final int WARMUP_FRAMES = 300;
  /** Frames the hold is asserted over — eight seconds, far past any transient. */
  private static final int FROZEN_FRAMES = 480;
  /**
   * Frames run after the knob comes back up before motion is asserted. Short on purpose: the
   * point is that the lamp resumes immediately from where it was held, not that it eventually
   * looks different.
   */
  private static final int RESUMED_FRAMES = 2;
  /** Frames run between the two holds, so the second one starts from a live, settled lamp. */
  private static final int SETTLE_FRAMES = 120;
  /**
   * Volume for the second hold, well above the default of .16, so the deficit is several blobs
   * deep and a spawn-per-frame loop would run for the whole hold rather than closing it early.
   */
  private static final double RAISED_VOLUME = .3;
  /**
   * Volume the lamp is settled at before credit is banked, low enough that the nudge and the
   * raise that follow both have room under the parameter's maximum.
   */
  private static final double BANKING_VOLUME = .2;
  /**
   * Volume step used to bank credit. Comfortably past the area tolerance, so it opens a real
   * deficit, and worth well under a blob on either chamber, so a single spawn closes it and the
   * rest of that frame's accrual is left banked.
   */
  private static final double BANKING_STEP = .01;
  /** Nudges allowed before the test gives up on banking credit. */
  private static final int BANKING_STEPS = 4;
  /** Credit that has to be banked for the banked-credit hold to be testing anything. */
  private static final double MIN_BANKED_CREDIT = 1;
  /**
   * How much of the installation has to be lit in the held frame for the identity assertion to
   * mean anything. A dark buffer is trivially identical to itself.
   */
  private static final double MIN_LIT_FRACTION = .01;
  /**
   * The blob seed to run on. Pinned for the same reason the sibling suites pin it: a test that
   * fails has to fail for a reason someone else can reproduce.
   */
  private static final String SEED = "7";

  @Test
  void speedZeroHoldsTheLampAndReleasingItResumes() throws IOException {
    final LX lx = newApotheneumLx();

    final String previousSeed = System.getProperty(LavaLamp.RENDER_SEED_PROPERTY);
    System.setProperty(LavaLamp.RENDER_SEED_PROPERTY, SEED);
    final LavaLamp lavaLamp;
    try {
      lavaLamp = new LavaLamp(lx);
    } finally {
      if (previousSeed == null) {
        System.clearProperty(LavaLamp.RENDER_SEED_PROPERTY);
      } else {
        System.setProperty(LavaLamp.RENDER_SEED_PROPERTY, previousSeed);
      }
    }
    final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { lavaLamp });
    channel.fader.setValue(1);
    lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

    assertTheKnobIsWellBehavedAtZero(lavaLamp);

    for (int frame = 0; frame < WARMUP_FRAMES; ++frame) {
      lx.engine.run();
    }

    // Bottom of the travel, not the literal number: this is what a performer's knob does, and
    // it is what makes the assertion below a statement about the parameter's range rather than
    // about one hard-coded value.
    lavaLamp.speed.setNormalized(0);
    assertEquals(
      0.,
      lavaLamp.speed.getValue(),
      "the bottom of the Speed knob is " + lavaLamp.speed.getValue() + " rather than zero, so "
        + "the lamp cannot be held still"
    );

    // One frame at zero to settle: the containment pass takes no time step, so a body the
    // previous frame left touching a lintel is placed on this one and stays there afterwards.
    lx.engine.run();
    final int[] held = lavaLamp.getColors().clone();
    assertTheHeldFrameIsWorthComparing(held);

    for (int frame = 0; frame < FROZEN_FRAMES; ++frame) {
      lx.engine.run();
      assertArrayEquals(
        held,
        lavaLamp.getColors(),
        "the lamp moved on frame " + (frame + 1) + " of a hold at Speed 0"
      );
    }

    lavaLamp.speed.setValue(1);
    for (int frame = 0; frame < RESUMED_FRAMES; ++frame) {
      lx.engine.run();
    }
    assertFalse(
      Arrays.equals(held, lavaLamp.getColors()),
      "the lamp was still on the held frame " + RESUMED_FRAMES + " frames after Speed came "
        + "back up to 1, so the hold does not release"
    );

    assertTheHoldSurvivesAVolumeRaise(lx, lavaLamp);
    assertTheHoldSurvivesBankedSpawnCredit(lx, lavaLamp);
  }

  /**
   * The hold with the lamp under its target volume, which is the case a settled lamp cannot
   * reach and therefore cannot test.
   *
   * <p>Everything above freezes a lamp that is already holding the volume it was asked for, so
   * {@code reconcileVolume} finds nothing to do and the hold is trivially still. Raise Volume
   * first and the lamp is in deficit when the knob goes down, which puts the replenishment path
   * — the one part of the update that is not a multiplication by the frame step — inside the
   * hold. A per-frame spawn cap keeps welling up a blob every frame there, and the lamp a
   * performer thinks is frozen goes on filling; a spawn budget that accrues with simulated time
   * does not advance at all at zero, and the frame stands.</p>
   *
   * <p>Both ends are asserted, because either one alone is satisfiable by a broken lamp: the
   * deficit has to be real while the lamp is held, and the fill has to actually happen once
   * Speed comes back up. A hold that is still because the lamp had nothing to do proves
   * nothing, and one that is still because replenishment was removed outright is a different
   * bug wearing this test as cover.</p>
   */
  private void assertTheHoldSurvivesAVolumeRaise(LX lx, LavaLamp lavaLamp) {
    for (int frame = 0; frame < SETTLE_FRAMES; ++frame) {
      lx.engine.run();
    }

    // Raised and held with no frame in between, so the lamp enters the hold in deficit rather
    // than getting a chance to fill first.
    lavaLamp.volume.setValue(RAISED_VOLUME);
    lavaLamp.speed.setNormalized(0);

    lx.engine.run();
    final int[] held = lavaLamp.getColors().clone();
    assertTheHeldFrameIsWorthComparing(held);
    assertTrue(
      lavaLamp.volumeDeficit() > 0,
      "the lamp was already at its Volume target when it was held, so this holds a settled lamp "
        + "and says nothing about the replenishment path"
    );

    for (int frame = 0; frame < FROZEN_FRAMES; ++frame) {
      lx.engine.run();
      assertArrayEquals(
        held,
        lavaLamp.getColors(),
        "the lamp moved on frame " + (frame + 1) + " of a hold at Speed 0 with Volume raised, so "
          + "it is still welling up blobs while it is supposed to be held"
      );
    }

    assertTrue(
      lavaLamp.volumeDeficit() > 0,
      "the lamp met its Volume target during the hold, which it can only have done by spawning"
    );

    lavaLamp.speed.setValue(1);
    for (int frame = 0; frame < RESUMED_FRAMES; ++frame) {
      lx.engine.run();
    }
    assertFalse(
      Arrays.equals(held, lavaLamp.getColors()),
      "the lamp did not resume filling after Speed came back up, so the hold suppresses "
        + "replenishment rather than deferring it"
    );
  }

  /**
   * The hold entered with spawn credit already banked, which is the case a budget that merely
   * stops accruing at Speed 0 does not cover.
   *
   * <p>The budget accrues with simulated time, so nothing is added to it while the lamp is held.
   * That is not the same as nothing being spendable. A lamp that closed a deficit part-way
   * through a frame's accrual keeps the remainder — there is no deficit for the next frame's
   * clamp to reach — so it can arrive at a hold with several blobs' worth of credit in hand. Drop
   * Speed to zero and then raise Volume and the replenishment path spends that credit
   * immediately: a blob wells up on the first held frame, in a frame a performer is holding
   * still. Refusing to spend at a zero step rather than merely refusing to accrue is what makes
   * the hold unconditional, whatever the lamp was doing before it.</p>
   *
   * <p>Volume is raised after the frame the hold is measured from, not before it, because the
   * banked credit is finite: it buys one spawn and then it is gone. Cloning the reference frame
   * first is what puts that single spawn inside the assertion instead of before it.</p>
   */
  private void assertTheHoldSurvivesBankedSpawnCredit(LX lx, LavaLamp lavaLamp) {
    // Settle at a volume with headroom above it, running at the top of the Speed knob so a
    // frame's accrual is several blobs' worth and closing a small deficit leaves a surplus.
    lavaLamp.volume.setValue(BANKING_VOLUME);
    lavaLamp.speed.setNormalized(1);
    for (int frame = 0; frame < SETTLE_FRAMES; ++frame) {
      lx.engine.run();
    }

    // One small Volume step per frame until both chambers are carrying credit. A step opens a
    // deficit worth less than one blob, so the spawn that closes it overshoots the target and
    // the frame ends with the rest of its accrual unspent and nothing left to clamp it.
    double banking = BANKING_VOLUME;
    for (int step = 0; step < BANKING_STEPS; ++step) {
      banking += BANKING_STEP;
      lavaLamp.volume.setValue(banking);
      lx.engine.run();
      if (lavaLamp.spawnCredit() >= MIN_BANKED_CREDIT) {
        break;
      }
    }
    assertTrue(
      lavaLamp.spawnCredit() >= MIN_BANKED_CREDIT,
      "only " + lavaLamp.spawnCredit() + " blobs of spawn credit were banked after " + BANKING_STEPS
        + " Volume steps, so the hold below is not being entered with credit in hand and says "
        + "nothing about spending it"
    );

    lavaLamp.speed.setNormalized(0);
    // The settle frame runs before Volume is raised, so the lamp is at its target across it and
    // the replenishment path is not entered: the credit is still there to be spent afterwards.
    lx.engine.run();
    final int[] held = lavaLamp.getColors().clone();
    assertTheHeldFrameIsWorthComparing(held);
    assertTrue(
      lavaLamp.spawnCredit() >= MIN_BANKED_CREDIT,
      "the credit was down to " + lavaLamp.spawnCredit() + " blobs by the frame the hold is "
        + "measured from, so it was spent before the assertion rather than inside it"
    );

    lavaLamp.volume.setValue(RAISED_VOLUME);

    for (int frame = 0; frame < FROZEN_FRAMES; ++frame) {
      lx.engine.run();
      if (frame == 0) {
        // Read on the first held frame rather than before it: the lamp picks the new Volume up
        // when it renders, so a deficit asked for earlier than that is the old target's.
        assertTrue(
          lavaLamp.volumeDeficit() > 0,
          "raising Volume to " + RAISED_VOLUME + " left the lamp at its target, so these held "
            + "frames have no replenishment to refuse"
        );
      }
      assertArrayEquals(
        held,
        lavaLamp.getColors(),
        "the lamp moved on frame " + (frame + 1) + " of a hold at Speed 0 that was entered with "
          + "spawn credit banked, so credit saved up before the hold is still spendable during it"
      );
    }

    assertTrue(
      lavaLamp.volumeDeficit() > 0,
      "the lamp met its Volume target during the hold, which it can only have done by spawning"
    );

    lavaLamp.speed.setValue(1);
    for (int frame = 0; frame < RESUMED_FRAMES; ++frame) {
      lx.engine.run();
    }
    assertFalse(
      Arrays.equals(held, lavaLamp.getColors()),
      "the lamp did not resume filling after Speed came back up, so refusing to spend credit "
        + "during the hold discards it rather than deferring it"
    );
  }

  /**
   * The Speed knob's mapping, which the zero minimum has to survive rather than merely permit.
   *
   * <p>The parameter carries an exponent, so its value is not a linear reading of the knob and
   * a zero minimum is exactly the case where a badly behaved curve would show up — a division
   * by the minimum, or a fractional power of it. LX maps it as
   * {@code min + (max - min) * normalized^exponent}, so at a zero minimum the curve is
   * {@code 4 * normalized^2}: monotonic, exactly zero at the bottom, and no NaN anywhere. It
   * also lands the default of 1 at the centre of the knob's travel, which the old .1 minimum
   * did not.</p>
   */
  private void assertTheKnobIsWellBehavedAtZero(LavaLamp lavaLamp) {
    assertEquals(
      1.,
      lavaLamp.speed.getValue(),
      "Speed no longer defaults to 1, which would change what the pattern does out of the box"
    );
    assertEquals(
      4.,
      lavaLamp.speed.range.max,
      "the top of the Speed range moved, so this mapping check is describing a different knob"
    );
    double previous = Double.NEGATIVE_INFINITY;
    for (int step = 0; step <= 100; ++step) {
      final double normalized = step / 100.;
      lavaLamp.speed.setNormalized(normalized);
      final double value = lavaLamp.speed.getValue();
      assertFalse(
        Double.isNaN(value) || Double.isInfinite(value),
        "Speed resolved to " + value + " at knob position " + normalized
      );
      assertTrue(
        value >= previous,
        "Speed fell from " + previous + " to " + value + " going up the knob to " + normalized
      );
      previous = value;
    }
    lavaLamp.speed.setNormalized(0);
    assertEquals(0., lavaLamp.speed.getValue(), "the bottom of the Speed knob is not zero");
    lavaLamp.speed.setNormalized(1);
    assertEquals(4., lavaLamp.speed.getValue(), "the top of the Speed knob is not the maximum");
    lavaLamp.speed.reset();
    assertEquals(1., lavaLamp.speed.getValue(), "Speed did not reset to its default");
  }

  /** A held frame with almost nothing lit would satisfy the identity assertion vacuously. */
  private void assertTheHeldFrameIsWorthComparing(int[] held) {
    int lit = 0;
    for (int color : held) {
      if ((color & LXColor.RGB_MASK) != 0) {
        ++lit;
      }
    }
    final double fraction = lit / (double) held.length;
    assertTrue(
      fraction >= MIN_LIT_FRACTION,
      "only " + fraction + " of the installation was lit on the held frame, so asserting it "
        + "does not change says nothing"
    );
  }
}
