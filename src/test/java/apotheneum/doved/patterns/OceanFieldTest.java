package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OceanFieldTest {

  private static final double EPSILON = 1e-9;

  private static double[] amplitudes(double turbulence) {
    final double[] amplitudes = new double[OceanField.WAVE_NUMBERS.length];
    OceanField.resolveWaveAmplitudes(turbulence, amplitudes);
    return amplitudes;
  }

  @Test
  void levelEndpointsPutThePlaneOutsideTheLedCenters() {
    final double floorY = 0;
    final double ceilingY = 44;
    final double pitch = 1;

    final double emptySurface = OceanField.surfaceY(0, floorY, ceilingY, pitch);
    final double fullSurface = OceanField.surfaceY(1, floorY, ceilingY, pitch);

    assertEquals(-.5, emptySurface, EPSILON);
    assertEquals(44.5, fullSurface, EPSILON);
    assertEquals(0, OceanField.waterCoverage((emptySurface - floorY) / pitch), EPSILON);
    assertEquals(1, OceanField.waterCoverage((fullSurface - ceilingY) / pitch), EPSILON);
    assertEquals(0, OceanField.levelEnvelope(0), EPSILON);
    assertEquals(0, OceanField.levelEnvelope(1), EPSILON);
    assertEquals(1, OceanField.levelEnvelope(.5), EPSILON);
  }

  @Test
  void fractionalWaterlineIsAntialiasedAcrossOneRow() {
    assertEquals(0, OceanField.waterCoverage(-.5), EPSILON);
    assertEquals(.5, OceanField.waterCoverage(0), EPSILON);
    assertEquals(1, OceanField.waterCoverage(.5), EPSILON);

    assertTrue(OceanField.waterCoverage(-.25) < OceanField.waterCoverage(.25));
  }

  @Test
  void doorCellsAreAbsentRatherThanDarkParticipants() {
    final int available = 34;

    assertTrue(OceanField.isAvailableCell(0, available));
    assertTrue(OceanField.isAvailableCell(available - 1, available));
    assertFalse(OceanField.isAvailableCell(available, available));
    assertFalse(OceanField.isAvailableCell(44, available));
  }

  @Test
  void meniscusHasAFullCoreAndSmoothOuterEdge() {
    final double widthRows = 2;

    assertEquals(1, OceanField.meniscus(0, widthRows), EPSILON);
    assertEquals(1, OceanField.meniscus(1, widthRows), EPSILON);
    assertTrue(OceanField.meniscus(1.5, widthRows) > 0);
    assertEquals(0, OceanField.meniscus(2, widthRows), EPSILON);
  }

  @Test
  void arcLengthUndulationIsDeterministicAndBounded() {
    final double[] amplitudes = amplitudes(.5);
    assertEquals(0, OceanField.waveRows(.2, 1.7, 0, amplitudes), EPSILON);

    final double first = OceanField.waveRows(.2, 1.7, .8, amplitudes);
    final double second = OceanField.waveRows(.2, 1.7, .8, amplitudes);
    assertEquals(first, second, EPSILON);
    assertTrue(Math.abs(first) <= OceanField.WAVE_AMPLITUDE_SUM * .8 + EPSILON);
  }

  @Test
  void arcLengthWrapsIntoUnitRangeAndAppliesTheColumnOffset() {
    assertEquals(0, OceanField.arcLength(0, 0, 120), EPSILON);
    assertEquals(.5, OceanField.arcLength(60, 0, 120), EPSILON);
    // Wraps back around rather than exceeding 1.
    assertEquals(0, OceanField.arcLength(120, 0, 120), EPSILON);
    // A column offset shifts which column lands at s = 0, wrapping negative
    // results the same way a raw column-count offset would - this is the
    // cube's front-face-center alignment used by OceanField.CUBE_S_OFFSET.
    assertEquals(0, OceanField.arcLength(24, 24, 200), EPSILON);
    assertEquals(1 - 24.0 / 200, OceanField.arcLength(0, 24, 200), EPSILON);
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
    final double cubeSOffset = OceanField.CUBE_S_OFFSET;
    final double phase = .6;
    final double amount = 1;

    final double[] values = new double[ringLength];
    for (int i = 0; i < ringLength; ++i) {
      final double s = OceanField.arcLength(i, cubeSOffset, ringLength);
      values[i] = OceanField.waveRows(s, phase, amount, amplitudes(.5));
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
  void cubeRingUndulationClosesTheSeamExactlyAtBothTurbulenceExtremes() {
    // s = 0 and s = 1 must agree exactly (integer wavenumbers), and walking a
    // full lap around the ring must land back on the starting value.
    final double phase = 1.1;
    final double amount = .5;

    for (double turbulence : new double[] { 0, 1 }) {
      final double[] amplitudes = amplitudes(turbulence);
      assertEquals(
        OceanField.waveRows(0, phase, amount, amplitudes),
        OceanField.waveRows(1, phase, amount, amplitudes),
        EPSILON
      );

      final double cubeSOffset = OceanField.CUBE_S_OFFSET;
      final double atStart = OceanField.waveRows(
        OceanField.arcLength(0, cubeSOffset, 200), phase, amount, amplitudes
      );
      final double oneLapLater = OceanField.waveRows(
        OceanField.arcLength(200, cubeSOffset, 200), phase, amount, amplitudes
      );
      assertEquals(atStart, oneLapLater, EPSILON);
    }
  }

  @Test
  void octaveUndulationIsContinuousJustEitherSideOfTheSeamAtBothTurbulenceExtremes() {
    final double phase = 1.1;
    final double amount = 2;
    final double epsilon = 1e-10;

    for (double turbulence : new double[] { 0, 1 }) {
      final double[] amplitudes = amplitudes(turbulence);
      assertEquals(
        OceanField.waveRows(epsilon, phase, amount, amplitudes),
        OceanField.waveRows(1 - epsilon, phase, amount, amplitudes),
        1e-8
      );
    }
  }

  @Test
  void turbulenceKeepsTotalWaveAmplitudeInvariant() {
    for (double turbulence : new double[] { 0, .2, .5, .8, 1 }) {
      double sum = 0;
      for (double amplitude : amplitudes(turbulence)) {
        sum += amplitude;
      }
      assertEquals(OceanField.WAVE_AMPLITUDE_SUM, sum, EPSILON);
    }
  }

  @Test
  void turbulenceChangesSmallScaleStructureRatherThanOnlyAmplitude() {
    final int columns = 200;
    final double phase = 1.1;
    final double amount = 1;
    final double smoothRoughness = meanAbsoluteSecondDifference(
      columns, phase, amount, amplitudes(0)
    );
    final double turbulentRoughness = meanAbsoluteSecondDifference(
      columns, phase, amount, amplitudes(1)
    );

    assertTrue(turbulentRoughness > smoothRoughness * 2,
      "turbulent=" + turbulentRoughness + ", smooth=" + smoothRoughness);
  }

  private static double meanAbsoluteSecondDifference(
      int columns, double phase, double amount, double[] amplitudes) {
    final double[] values = new double[columns];
    for (int i = 0; i < columns; ++i) {
      values[i] = OceanField.waveRows(i / (double) columns, phase, amount, amplitudes);
    }
    double sum = 0;
    for (int i = 0; i < columns; ++i) {
      sum += Math.abs(values[(i + 1) % columns] - 2 * values[i] +
        values[(i - 1 + columns) % columns]);
    }
    return sum / columns;
  }
}
