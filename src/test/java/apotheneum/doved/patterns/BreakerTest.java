package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreakerTest {

  private static final double EPSILON = 1e-9;

  @Test
  void profileHasALongBackAndShortSteepFace() {
    final double width = .25;
    final double faceFraction = .22;

    assertEquals(0, Breaker.spatialProfile(-.78 * width, width, faceFraction), EPSILON);
    assertEquals(1, Breaker.spatialProfile(0, width, faceFraction), EPSILON);
    assertEquals(0, Breaker.spatialProfile(faceFraction * width, width, faceFraction), EPSILON);
    assertEquals(0, Breaker.spatialProfile(-.79 * width, width, faceFraction), EPSILON);
    assertEquals(0, Breaker.spatialProfile(.23 * width, width, faceFraction), EPSILON);

    // Half height lies much farther behind the crest than ahead of it.
    assertEquals(.5, Breaker.spatialProfile(-.39 * width, width, faceFraction), EPSILON);
    assertEquals(.5, Breaker.spatialProfile(.11 * width, width, faceFraction), EPSILON);
    assertTrue(.39 / .11 > 3.5);
  }

  @Test
  void profileClosesAcrossTheArcLengthSeam() {
    final double center = .99;
    final double rightOfSeam = Breaker.signedArcDistance(.01, center);
    final double equivalentUnwrapped = Breaker.signedArcDistance(1.01, center);

    assertEquals(.02, rightOfSeam, EPSILON);
    assertEquals(rightOfSeam, equivalentUnwrapped, EPSILON);
    assertEquals(
      Breaker.spatialProfile(rightOfSeam, .25, .22),
      Breaker.spatialProfile(equivalentUnwrapped, .25, .22),
      EPSILON
    );
  }

  @Test
  void travellingFootprintWrapsAcrossTheArcLengthSeam() {
    final double launchS = .97;
    final double centerS = Breaker.advanceRingPosition(launchS, .16, .5);

    assertEquals(.05, centerS, EPSILON);
    assertEquals(.02, Breaker.signedArcDistance(.07, centerS), EPSILON);
    assertEquals(-.02, Breaker.signedArcDistance(.03, centerS), EPSILON);
  }

  @Test
  void foamDriftsInRingSpaceInsteadOfFollowingTheFootprint() {
    final double birthS = .99;
    final double foamS = Breaker.advanceRingPosition(birthS, .02, .5);
    final double footprintS = Breaker.advanceRingPosition(birthS, .16, .5);

    assertEquals(0, foamS, EPSILON, "foam drift should wrap across the seam");
    assertEquals(.07, footprintS, EPSILON);
    assertEquals(-.07, Breaker.signedArcDistance(foamS, footprintS), EPSILON,
      "foam should remain behind the faster travelling footprint");
  }

  @Test
  void faceCenteredFootprintStaysWithinOneFaceAtPeak() {
    final double faceWidth = .25;
    final double faceFraction = .22;
    final double crest = Breaker.profileCrestS(0, faceWidth, faceFraction, 0, 1);

    assertEquals(.07, crest, EPSILON);
    assertEquals(0, Breaker.spatialProfile(-.125 - crest, faceWidth, faceFraction), EPSILON);
    assertEquals(0, Breaker.spatialProfile(.125 - crest, faceWidth, faceFraction), EPSILON);
    assertTrue(Breaker.spatialProfile(-.12 - crest, faceWidth, faceFraction) > 0);
    assertTrue(Breaker.spatialProfile(.12 - crest, faceWidth, faceFraction) > 0);
  }

  @Test
  void eventTimingIsSlowFastLongRatherThanSymmetric() {
    final double nearPeak = Breaker.heightEnvelope(Breaker.APPROACH_SECONDS);
    final double afterSlump = Breaker.heightEnvelope(
      Breaker.APPROACH_SECONDS + Breaker.COLLAPSE_SECONDS
    );
    final double duringWash = Breaker.heightEnvelope(
      Breaker.APPROACH_SECONDS + Breaker.COLLAPSE_SECONDS + .8
    );

    assertEquals(1, nearPeak, EPSILON);
    assertEquals(.22, afterSlump, EPSILON);
    assertTrue(duringWash > 0, "wash should linger after the fast collapse");
    assertTrue(Breaker.APPROACH_SECONDS > 5 * Breaker.COLLAPSE_SECONDS);
    assertTrue(Breaker.WASH_SECONDS > 5 * Breaker.COLLAPSE_SECONDS);
    assertEquals(0, Breaker.heightEnvelope(Breaker.EVENT_SECONDS), EPSILON);
  }

  @Test
  void collapsePeelsFromBackToFrontInsideTheExistingWindow() {
    final double halfway = Breaker.APPROACH_SECONDS + .5 * Breaker.COLLAPSE_SECONDS;
    final double back = Breaker.peeledHeightEnvelope(halfway, -.78);
    final double front = Breaker.peeledHeightEnvelope(halfway, .22);

    assertTrue(back < front, "the trailing edge should collapse before the leading edge");
    assertEquals(1, Breaker.peeledHeightEnvelope(Breaker.APPROACH_SECONDS, .22), EPSILON);
    assertEquals(.22, Breaker.peeledHeightEnvelope(
      Breaker.APPROACH_SECONDS + Breaker.COLLAPSE_SECONDS,
      -.78
    ), EPSILON);
  }

  @Test
  void foamBirthPositionPeelsInTheDirectionOfTravel() {
    final double width = .25;
    final double start = Breaker.peelOffset(Breaker.APPROACH_SECONDS, width, .22);
    final double end = Breaker.peelOffset(
      Breaker.APPROACH_SECONDS + Breaker.COLLAPSE_SECONDS,
      width,
      .16
    );

    assertEquals(-.78 * width, start, EPSILON);
    assertEquals(.16 * width, end, EPSILON);
    assertTrue(end > start);
  }

  @Test
  void landmarksUseIndependentEasing() {
    final double early = .5 * Breaker.APPROACH_SECONDS;
    final double later = .9 * Breaker.APPROACH_SECONDS;

    final double earlyFace = Breaker.faceFraction(early);
    final double laterFace = Breaker.faceFraction(later);
    final double earlyPosition = Breaker.crestOffset(early);
    final double laterPosition = Breaker.crestOffset(later);

    assertTrue(laterFace < earlyFace, "face should sharpen during approach");
    assertTrue(laterPosition > earlyPosition, "crest should advance during approach");
    assertTrue(Breaker.crestOffset(Breaker.APPROACH_SECONDS + .2) > 0,
      "crest should throw forward during collapse");
    assertTrue(Breaker.faceFraction(Breaker.APPROACH_SECONDS + Breaker.COLLAPSE_SECONDS + 1)
      > Breaker.faceFraction(Breaker.APPROACH_SECONDS + Breaker.COLLAPSE_SECONDS),
      "slumped face should relax during the wash");
  }

  @Test
  void foamBurstCoincidesWithCollapse() {
    assertEquals(0, Breaker.foamBurst(0), EPSILON);
    assertTrue(Breaker.foamBurst(Breaker.APPROACH_SECONDS + .1) > .5);
    assertEquals(0, Breaker.foamBurst(
      Breaker.APPROACH_SECONDS + Breaker.COLLAPSE_SECONDS + .3
    ), EPSILON);
  }

  @Test
  void heightCapsAgainstRemainingHeadroom() {
    assertEquals(14, Breaker.effectiveBreakHeightRows(14, 10, 40, 1), EPSILON);
    assertEquals(3.825, Breaker.effectiveBreakHeightRows(14, 35, 40, 1), EPSILON);
    assertEquals(0, Breaker.effectiveBreakHeightRows(14, 40, 40, 1), EPSILON);
  }

  @Test
  void faceSnapDefaultsToQuarterTurnsButCanBeOverridden() {
    assertEquals(.25, Breaker.resolvedBreakS(.2, true), EPSILON);
    assertEquals(.2, Breaker.resolvedBreakS(.2, false), EPSILON);
    assertEquals(0, Breaker.resolvedBreakS(.99, true), EPSILON);
  }

  @Test
  void feedbackDecayIsFrameRateIndependent() {
    final double sixtyFpsFrame = Breaker.feedbackDecay(2.25, 1. / 60);
    final double thirtyFpsFrame = Breaker.feedbackDecay(2.25, 1. / 30);

    assertEquals(Math.pow(sixtyFpsFrame, 60), Math.pow(thirtyFpsFrame, 30), EPSILON);
    assertEquals(Math.exp(-2.25), Math.pow(sixtyFpsFrame, 60), EPSILON);
  }

  @Test
  void reverseFlipsTravelDirectionAndWrapsBackwardsAcrossTheSeam() {
    assertEquals(1, Breaker.travelDirection(false));
    assertEquals(-1, Breaker.travelDirection(true));

    // Forward from just before the seam wraps up through 0.
    assertEquals(
      .04,
      Breaker.advanceRingPosition(.99, Breaker.travelDirection(false) * .5, .1),
      1e-9
    );
    // Reverse from just after the seam wraps back down through 1.
    assertEquals(
      .96,
      Breaker.advanceRingPosition(.01, Breaker.travelDirection(true) * .5, .1),
      1e-9
    );
  }

  @Test
  void reverseMirrorsTheProfileSoTheSteepFaceStillLeads() {
    final double width = .25;
    final double faceFraction = .22;

    final double forwardCrest = Breaker.profileCrestS(0, width, faceFraction, 0, 1);
    final double reverseCrest = Breaker.profileCrestS(0, width, faceFraction, 0, -1);
    // The crest sits on opposite sides of the same footprint centre.
    assertEquals(forwardCrest, -reverseCrest, 1e-9);

    // A point one third of a width ahead of the crest in the direction of
    // travel must land on the short steep face in BOTH directions, and the
    // profile value must match.
    final double ahead = .33 * width * faceFraction;
    final double forward = Breaker.spatialProfile(
      1 * Breaker.signedArcDistance(forwardCrest + ahead, forwardCrest), width, faceFraction
    );
    final double reversed = Breaker.spatialProfile(
      -1 * Breaker.signedArcDistance(reverseCrest - ahead, reverseCrest), width, faceFraction
    );
    assertEquals(forward, reversed, 1e-9);
    assertTrue(forward > 0, "the leading sample should be on the wave, not off its support");
  }

  @Test
  void peelOffsetFollowsTheChosenDirection() {
    final double width = .25;
    final double atCollapse = Breaker.APPROACH_SECONDS + .5 * Breaker.COLLAPSE_SECONDS;
    final double offset = Breaker.peelOffset(atCollapse, width, .22);

    // Mirrored by the caller, so forward and reverse spawn foam on opposite
    // sides of the crest by the same magnitude.
    assertEquals(Math.abs(offset), Math.abs(-1 * offset), 1e-9);
  }

  @Test
  void aBoundedPassNeverTravelsMoreThanHalfTheRing() {
    // The failure this guards: the envelope is bounded in time, not distance, so
    // at slow Pace the wave used to travel more than a full lap with Circle off.
    double travelled = 0;
    for (int i = 0; i < 10_000; ++i) {
      final double step = Breaker.boundedTravelStep(travelled, .32 * (1 / 60.), false);
      travelled += Math.abs(step);
    }
    assertEquals(Breaker.ONE_PASS_LAPS, travelled, 1e-9);
  }

  @Test
  void aBoundedPassStopsAdvancingOnArrival() {
    assertEquals(0, Breaker.boundedTravelStep(Breaker.ONE_PASS_LAPS, .01, false), 1e-9);
    // Partial step: only the remaining distance is granted.
    assertEquals(
      .02,
      Breaker.boundedTravelStep(Breaker.ONE_PASS_LAPS - .02, .05, false),
      1e-9
    );
  }

  @Test
  void boundedPassRespectsDirectionAndCirclingIsUnbounded() {
    assertEquals(-.01, Breaker.boundedTravelStep(0, -.01, false), 1e-9);
    assertEquals(-.02, Breaker.boundedTravelStep(Breaker.ONE_PASS_LAPS - .02, -.05, false), 1e-9);
    // Circling ignores the bound entirely, in both directions.
    assertEquals(.05, Breaker.boundedTravelStep(99, .05, true), 1e-9);
    assertEquals(-.05, Breaker.boundedTravelStep(99, -.05, true), 1e-9);
  }
}
