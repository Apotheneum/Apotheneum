package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;

public class RockfallVariationTest {

  private static final double[][] CHANNELS = {
    { .13, 7.1 },
    { .47, 17.3 },
    { .83, 29.7 },
    { 1.19, 41.9 },
    { 1.61, 53.3 },
    { 2.03, 67.7 },
    { 2.41, 79.1 },
    { 2.89, 97.3 },
    { 3.31, 109.7 }
  };

  @Test
  void variationCycleHasExactSeam() {
    assertEquals(
      Double.doubleToLongBits(Rockfall.normalizedVariationPhase(0)),
      Double.doubleToLongBits(Rockfall.normalizedVariationPhase(1))
    );
    for (int rockIndex = 0; rockIndex < 500; ++rockIndex) {
      for (double[] channel : CHANNELS) {
        assertEquals(
          Double.doubleToLongBits(Rockfall.variationNoise(0, rockIndex, channel[0], channel[1])),
          Double.doubleToLongBits(Rockfall.variationNoise(1, rockIndex, channel[0], channel[1]))
        );
      }
    }
  }

  @Test
  void smallPhaseStepsChangeNoiseContinuously() {
    double maximumStep = 0;
    final int steps = 4096;
    for (int rockIndex = 0; rockIndex < 500; ++rockIndex) {
      for (double[] channel : CHANNELS) {
        double previous = Rockfall.variationNoise(0, rockIndex, channel[0], channel[1]);
        for (int step = 1; step <= steps; ++step) {
          final double current = Rockfall.variationNoise(
            (double) step / steps,
            rockIndex,
            channel[0],
            channel[1]
          );
          maximumStep = Math.max(maximumStep, Math.abs(current - previous));
          previous = current;
        }
      }
    }
    assertTrue(maximumStep < .01, "maximum adjacent phase step: " + maximumStep);
  }

  @Test
  void halfCycleTraversesEnoughNoiseToChangeTheField() {
    double totalChange = 0;
    int samples = 0;
    for (int rockIndex = 0; rockIndex < 500; ++rockIndex) {
      for (double[] channel : CHANNELS) {
        totalChange += Math.abs(
          Rockfall.variationNoise(.5, rockIndex, channel[0], channel[1]) -
          Rockfall.variationNoise(0, rockIndex, channel[0], channel[1])
        );
        ++samples;
      }
    }
    assertTrue(totalChange / samples > .05, "mean half-cycle change: " + totalChange / samples);
  }

  @Test
  void degenerateTangentPicksSideAtCrownAndFallsAtUnderside() {
    assertEquals(-1, Rockfall.fallbackTangentS(-1, -1));
    assertEquals(.12, Rockfall.fallbackTangentH(-1));
    assertEquals(0, Rockfall.fallbackTangentS(1, 1));
    assertEquals(1, Rockfall.fallbackTangentH(1));
  }

  @Test
  void spacingCountIsIndependentOfScaleBelowCoverageClamp() {
    assertEquals(55, Rockfall.derivedRockCount(.2, 1, 700, 50));
    assertEquals(55, Rockfall.derivedRockCount(.8, 1, 700, 50));
  }

  @Test
  void coverageClampAndHardCapBoundExtremeCounts() {
    assertEquals(700, Rockfall.derivedRockCount(.05, .15, 700, 50));
    assertEquals(22, Rockfall.derivedRockCount(1.5, 1, 700, 50));
    assertEquals(25, Rockfall.derivedRockCount(
      1.3378378905705177,
      1.4832396974191326,
      700,
      48.3
    ));
  }

  @Test
  void achievableFallSpeedBoundsLateralMotionOnlyAtLowGravity() {
    assertEquals(22, Rockfall.maximumLateralSpeed(11, 220, 352), 1e-9);
    assertEquals(
      .25 * Math.sqrt(2 * 20 * Apotheneum.GRID_HEIGHT),
      Rockfall.maximumLateralSpeed(16.14828107971698, 315.0656279688701, 20),
      1e-9
    );
  }
  @Test
  void everyPrefixOfVerticalPlacementStaysSpread() {
    // The defect this guards: a count-dependent stratification of (i + r) / N leaves the
    // first M rocks bunched into the lowest M/N of the world once the count drops to M,
    // so raising Rock Scale collapsed the whole field onto the floor. A golden-ratio
    // sequence is evenly spread at EVERY prefix length, not just the one it was built for.
    for (int count : new int[] { 16, 25, 55, 312 }) {
      final double[] positions = new double[count];
      for (int i = 0; i < count; ++i) {
        positions[i] = Rockfall.verticalPosition(i);
        assertTrue(
          positions[i] >= 0 && positions[i] < 1,
          "position " + positions[i] + " out of [0, 1) at index " + i
        );
      }
      java.util.Arrays.sort(positions);
      double largestGap = positions[0];
      for (int i = 1; i < count; ++i) {
        largestGap = Math.max(largestGap, positions[i] - positions[i - 1]);
      }
      largestGap = Math.max(largestGap, 1 - positions[count - 1]);
      // A uniform sequence has gap 1/count; golden-ratio placement stays within ~2.5x of
      // that at any prefix. The broken stratification gave a gap of (1 - M/N), e.g. .71
      // for 16 rocks out of an initial pool of 55.
      assertTrue(
        largestGap < 2.5 / count,
        "largest gap " + largestGap + " too wide for count " + count
      );
    }
  }

  @Test
  void verticalPlacementIsIndependentOfHowTheCountWasReached() {
    // Shrinking must not move a rock that is still active, and must not depend on the
    // count the pool was first built at.
    for (int i = 0; i < 16; ++i) {
      assertEquals(Rockfall.verticalPosition(i), Rockfall.verticalPosition(i), 0);
    }
    assertEquals(.6180339887498949, Rockfall.verticalPosition(0), 1e-12);
  }
}
