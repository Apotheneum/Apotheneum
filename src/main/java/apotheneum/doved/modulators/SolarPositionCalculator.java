package apotheneum.doved.modulators;

import java.util.Objects;

import heronarts.lx.utils.LXUtils;

/**
 * Offline solar-position calculation using the equations published with NOAA's solar
 * calculator. The result uses the NOAA convention: azimuth is clockwise from true north,
 * and elevation is positive above the horizon.
 *
 * <p>The implementation follows the compact Meeus-based method documented at
 * https://gml.noaa.gov/grad/solcalc/calcdetails.html. It includes NOAA's standard
 * atmospheric-refraction correction and is intended for show geometry, not surveying.
 */
public final class SolarPositionCalculator {

  private static final double MILLIS_PER_DAY = 86_400_000.;
  private static final double JULIAN_UNIX_EPOCH = 2_440_587.5;

  /** Mutable result so a real-time modulator can reuse one instance without frame churn. */
  public static final class Result {
    private double azimuthDegrees;
    private double elevationDegrees;

    public double getAzimuthDegrees() {
      return this.azimuthDegrees;
    }

    public double getElevationDegrees() {
      return this.elevationDegrees;
    }
  }

  private SolarPositionCalculator() {
  }

  /**
   * Calculates apparent solar position for a UTC Unix timestamp and geographic location.
   * Longitude is positive east and negative west.
   */
  public static Result calculate(
    long epochMillis, double latitudeDegrees, double longitudeDegrees, Result result) {

    Objects.requireNonNull(result, "May not calculate into a null result");
    final double julianDay = epochMillis / MILLIS_PER_DAY + JULIAN_UNIX_EPOCH;
    final double century = (julianDay - 2_451_545.) / 36_525.;

    final double meanLongitude = normalizeDegrees(
      280.46646 + century * (36_000.76983 + century * .0003032));
    final double meanAnomaly =
      357.52911 + century * (35_999.05029 - .0001537 * century);
    final double eccentricity =
      .016708634 - century * (.000042037 + .0000001267 * century);

    final double anomalyRadians = Math.toRadians(meanAnomaly);
    final double equationOfCenter =
      Math.sin(anomalyRadians) *
        (1.914602 - century * (.004817 + .000014 * century))
      + Math.sin(2 * anomalyRadians) * (.019993 - .000101 * century)
      + Math.sin(3 * anomalyRadians) * .000289;
    final double trueLongitude = meanLongitude + equationOfCenter;
    final double omega = 125.04 - 1_934.136 * century;
    final double apparentLongitude =
      trueLongitude - .00569 - .00478 * Math.sin(Math.toRadians(omega));

    final double meanObliquity = 23 +
      (26 + (21.448 - century *
        (46.815 + century * (.00059 - century * .001813))) / 60) / 60;
    final double correctedObliquity =
      meanObliquity + .00256 * Math.cos(Math.toRadians(omega));
    final double obliquityRadians = Math.toRadians(correctedObliquity);
    final double declinationRadians = Math.asin(
      Math.sin(obliquityRadians) * Math.sin(Math.toRadians(apparentLongitude)));

    final double y = Math.pow(Math.tan(obliquityRadians / 2), 2);
    final double longitudeRadians = Math.toRadians(meanLongitude);
    final double equationOfTime = 4 * Math.toDegrees(
      y * Math.sin(2 * longitudeRadians)
      - 2 * eccentricity * Math.sin(anomalyRadians)
      + 4 * eccentricity * y * Math.sin(anomalyRadians) * Math.cos(2 * longitudeRadians)
      - .5 * y * y * Math.sin(4 * longitudeRadians)
      - 1.25 * eccentricity * eccentricity * Math.sin(2 * anomalyRadians));

    final double utcMinutes = Math.floorMod(epochMillis, (long) MILLIS_PER_DAY) / 60_000.;
    final double trueSolarMinutes = normalizeMinutes(
      utcMinutes + equationOfTime + 4 * longitudeDegrees);
    double hourAngleDegrees = trueSolarMinutes / 4 - 180;
    if (hourAngleDegrees < -180) {
      hourAngleDegrees += 360;
    }

    final double latitudeRadians = Math.toRadians(latitudeDegrees);
    final double hourAngleRadians = Math.toRadians(hourAngleDegrees);
    final double cosZenith = LXUtils.constrain(
      Math.sin(latitudeRadians) * Math.sin(declinationRadians)
      + Math.cos(latitudeRadians) * Math.cos(declinationRadians)
        * Math.cos(hourAngleRadians),
      -1, 1);
    final double geometricElevation = 90 - Math.toDegrees(Math.acos(cosZenith));

    final double azimuth = normalizeDegrees(Math.toDegrees(Math.atan2(
      Math.sin(hourAngleRadians),
      Math.cos(hourAngleRadians) * Math.sin(latitudeRadians)
        - Math.tan(declinationRadians) * Math.cos(latitudeRadians))) + 180);

    result.azimuthDegrees = azimuth;
    result.elevationDegrees = geometricElevation + refractionDegrees(geometricElevation);
    return result;
  }

  private static double refractionDegrees(double elevationDegrees) {
    if (elevationDegrees > 85) {
      return 0;
    }
    final double tangent = Math.tan(Math.toRadians(elevationDegrees));
    final double arcSeconds;
    if (elevationDegrees > 5) {
      final double tangentCubed = tangent * tangent * tangent;
      arcSeconds = 58.1 / tangent - .07 / tangentCubed
        + .000086 / (tangentCubed * tangent * tangent);
    } else if (elevationDegrees > -.575) {
      arcSeconds = 1_735 + elevationDegrees *
        (-518.2 + elevationDegrees *
          (103.4 + elevationDegrees * (-12.79 + elevationDegrees * .711)));
    } else {
      arcSeconds = -20.772 / tangent;
    }
    return arcSeconds / 3_600.;
  }

  private static double normalizeMinutes(double minutes) {
    minutes %= 1_440;
    return (minutes < 0) ? minutes + 1_440 : minutes;
  }

  static double normalizeDegrees(double degrees) {
    degrees %= 360;
    return (degrees < 0) ? degrees + 360 : degrees;
  }
}
