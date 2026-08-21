package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FloodTest {

  private static final double EPSILON = 1e-9;

  @Test
  void levelEndpointsPutThePlaneOutsideTheLedCenters() {
    final double floorY = 0;
    final double ceilingY = 44;
    final double pitch = 1;

    final double emptySurface = Flood.FloodField.surfaceY(0, floorY, ceilingY, pitch);
    final double fullSurface = Flood.FloodField.surfaceY(1, floorY, ceilingY, pitch);

    assertEquals(-.5, emptySurface, EPSILON);
    assertEquals(44.5, fullSurface, EPSILON);
    assertEquals(0, Flood.FloodField.waterCoverage((emptySurface - floorY) / pitch), EPSILON);
    assertEquals(1, Flood.FloodField.waterCoverage((fullSurface - ceilingY) / pitch), EPSILON);
    assertEquals(0, Flood.FloodField.levelEnvelope(0), EPSILON);
    assertEquals(0, Flood.FloodField.levelEnvelope(1), EPSILON);
    assertEquals(1, Flood.FloodField.levelEnvelope(.5), EPSILON);
  }

  @Test
  void fractionalWaterlineIsAntialiasedAcrossOneRow() {
    assertEquals(0, Flood.FloodField.waterCoverage(-.5), EPSILON);
    assertEquals(.5, Flood.FloodField.waterCoverage(0), EPSILON);
    assertEquals(1, Flood.FloodField.waterCoverage(.5), EPSILON);

    assertTrue(Flood.FloodField.waterCoverage(-.25) < Flood.FloodField.waterCoverage(.25));
  }

  @Test
  void doorCellsAreAbsentRatherThanDarkParticipants() {
    final int available = 34;

    assertTrue(Flood.FloodField.isAvailableCell(0, available));
    assertTrue(Flood.FloodField.isAvailableCell(available - 1, available));
    assertFalse(Flood.FloodField.isAvailableCell(available, available));
    assertFalse(Flood.FloodField.isAvailableCell(44, available));
  }

  @Test
  void meniscusHasAFullCoreAndSmoothOuterEdge() {
    final double widthRows = 2;

    assertEquals(1, Flood.FloodField.meniscus(0, widthRows), EPSILON);
    assertEquals(1, Flood.FloodField.meniscus(1, widthRows), EPSILON);
    assertTrue(Flood.FloodField.meniscus(1.5, widthRows) > 0);
    assertEquals(0, Flood.FloodField.meniscus(2, widthRows), EPSILON);
  }

  @Test
  void arcLengthUndulationIsDeterministicAndBounded() {
    assertEquals(0, Flood.FloodField.waveRows(.2, 1.7, 0), EPSILON);

    final double first = Flood.FloodField.waveRows(.2, 1.7, .8);
    final double second = Flood.FloodField.waveRows(.2, 1.7, .8);
    assertEquals(first, second, EPSILON);
    assertTrue(Math.abs(first) <= (.82 + .42) * .8 + EPSILON);
  }

  @Test
  void arcLengthWrapsIntoUnitRangeAndAppliesTheColumnOffset() {
    assertEquals(0, Flood.FloodField.arcLength(0, 0, 120), EPSILON);
    assertEquals(.5, Flood.FloodField.arcLength(60, 0, 120), EPSILON);
    // Wraps back around rather than exceeding 1.
    assertEquals(0, Flood.FloodField.arcLength(120, 0, 120), EPSILON);
    // A column offset shifts which column lands at s = 0, wrapping negative
    // results the same way a raw column-count offset would - this is the
    // cube's front-face-center alignment used by Flood.CUBE_S_OFFSET.
    assertEquals(0, Flood.FloodField.arcLength(24, 24, 200), EPSILON);
    assertEquals(1 - 24.0 / 200, Flood.FloodField.arcLength(0, 24, 200), EPSILON);
  }

  @Test
  void surgeIsLocalizedWithinItsConfiguredWidth() {
    final double position = .2;
    final double width = .4;

    assertEquals(1, Flood.FloodField.surgeProfile(position, position, width), EPSILON);
    assertEquals(.5, Flood.FloodField.surgeProfile(position - .1, position, width), EPSILON);
    assertEquals(.5, Flood.FloodField.surgeProfile(position + .1, position, width), EPSILON);
    assertEquals(0, Flood.FloodField.surgeProfile(position - .2, position, width), EPSILON);
    assertEquals(0, Flood.FloodField.surgeProfile(position + .2, position, width), EPSILON);
  }

  @Test
  void surgeWrapsAcrossTheSeamBetweenSEquals0And1() {
    final double width = .4;

    // s = .05 and position = .95 are only .1 apart going the "short way"
    // around the seam, not .9 apart along the unwrapped number line.
    assertEquals(.5, Flood.FloodField.surgeProfile(.05, .95, width), EPSILON);
    assertEquals(.5, Flood.FloodField.surgeProfile(.95, .05, width), EPSILON);
  }

  @Test
  void surgeProfileWrapsAnUnboundedTravelingPosition() {
    final double width = .4;

    // A position that has travelled more than a full lap (e.g. tracked as a
    // monotonically increasing accumulator) is taken modulo 1 internally.
    assertEquals(1, Flood.FloodField.surgeProfile(.2, 3.2, width), EPSILON);
    assertEquals(1, Flood.FloodField.surgeProfile(.2, -.8, width), EPSILON);
  }

  @Test
  void cubeRingUndulationStaysSmoothAcrossFaceCorners() {
    // Regression test for the corner-spike bug: parameterizing the azimuthal
    // undulation by linear world (x, z) produced a 28x curvature spike at each
    // of the cube's four face corners (max|2nd diff| 0.0797 vs mean 0.0029),
    // because a linear function of (x, z) is only piecewise-linear around a
    // square ring. Parameterizing by arc length s removes the corners: the
    // design doc measured a 2.4 roughness ratio here (vs 2.1 for a true
    // circle), so this asserts a generous ceiling of 5 to catch any
    // regression back toward corner-driven spikes without being flaky.
    final int ringLength = 200;
    final double cubeSOffset = 24.5; // (Apotheneum.GRID_WIDTH - 1) / 2.0
    final double phase = .6;
    final double amount = 1;

    final double[] values = new double[ringLength];
    for (int i = 0; i < ringLength; ++i) {
      final double s = Flood.FloodField.arcLength(i, cubeSOffset, ringLength);
      values[i] = Flood.FloodField.waveRows(s, phase, amount);
    }

    double sumAbsSecondDiff = 0;
    double maxAbsSecondDiff = 0;
    for (int i = 0; i < ringLength; ++i) {
      final double prev = values[(i - 1 + ringLength) % ringLength];
      final double curr = values[i];
      final double next = values[(i + 1) % ringLength];
      final double secondDiff = Math.abs(next - 2 * curr + prev);
      sumAbsSecondDiff += secondDiff;
      maxAbsSecondDiff = Math.max(maxAbsSecondDiff, secondDiff);
    }
    final double meanAbsSecondDiff = sumAbsSecondDiff / ringLength;

    assertTrue(
      maxAbsSecondDiff / meanAbsSecondDiff < 5,
      "roughness ratio " + (maxAbsSecondDiff / meanAbsSecondDiff) + " should stay smooth around the ring"
    );
  }

  @Test
  void cubeRingUndulationClosesTheSeamExactly() {
    // s = 0 and s = 1 must agree exactly (integer wavenumbers), and walking a
    // full lap around the ring must land back on the starting value.
    final double phase = 1.1;
    final double amount = .5;

    assertEquals(
      Flood.FloodField.waveRows(0, phase, amount),
      Flood.FloodField.waveRows(1, phase, amount),
      EPSILON
    );

    final double cubeSOffset = 24.5;
    final double atStart = Flood.FloodField.waveRows(
      Flood.FloodField.arcLength(0, cubeSOffset, 200),
      phase,
      amount
    );
    final double oneLapLater = Flood.FloodField.waveRows(
      Flood.FloodField.arcLength(200, cubeSOffset, 200),
      phase,
      amount
    );
    assertEquals(atStart, oneLapLater, EPSILON);
  }
}
