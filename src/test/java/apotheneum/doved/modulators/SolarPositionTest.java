package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import apotheneum.doved.patterns.TransformedDistanceField;
import heronarts.lx.LX;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;

public class SolarPositionTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;
  private static final Instant REFERENCE_INSTANT = Instant.parse("2003-10-17T19:30:30Z");

  private LX lx;
  private SolarPosition solarPosition;

  @BeforeEach
  void setUp() {
    this.lx = newHeadlessLx();
    this.solarPosition = this.lx.engine.modulation.addModulator(
      new SolarPosition(Clock.fixed(REFERENCE_INSTANT, ZoneOffset.UTC)));
    this.solarPosition.start();
  }

  @Test
  void calculatorMatchesNrelSpaReferenceCase() {
    final SolarPositionCalculator.Result result = new SolarPositionCalculator.Result();
    SolarPositionCalculator.calculate(
      REFERENCE_INSTANT.toEpochMilli(), 39.742476, -105.1786, result);

    // NREL SPA report Appendix A: azimuth 194.34024, zenith 50.11162. The compact NOAA
    // method deliberately trades sub-arcminute precision for much less code.
    assertEquals(194.34024, result.getAzimuthDegrees(), .1);
    assertEquals(90 - 50.11162, result.getElevationDegrees(), .1);
  }

  @Test
  void manualClockInterpretsDateAndTimeInConfiguredIanaZone() {
    this.solarPosition.latitude.setValue(39.742476);
    this.solarPosition.longitude.setValue(-105.1786);
    this.solarPosition.timeSource.setValue(SolarPosition.TimeSource.MANUAL);
    // IANA's Etc/GMT signs are intentionally reversed: this is a fixed UTC-7 zone,
    // matching the timezone input in the published NREL reference case.
    this.solarPosition.timeZone.setValue("Etc/GMT+7");
    this.solarPosition.manualYear.setValue(2003);
    this.solarPosition.manualMonth.setValue(10);
    this.solarPosition.manualDay.setValue(17);
    this.solarPosition.manualTime.setValue(12 + 30. / 60 + 30. / 3_600);
    this.solarPosition.timeScale.setValue(0);
    this.solarPosition.loop(0);

    assertEquals(
      SolarPositionCalculator.normalizeDegrees(194.34024 + 180),
      this.solarPosition.azimuth.getValue(), .1);
    assertEquals(-(90 - 50.11162), this.solarPosition.elevation.getValue(), .1);
  }

  @Test
  void incomingRayStartsSunwardAndPassesThroughFloorCenter() {
    final SolarPosition.Ray ray = new SolarPosition.Ray();
    SolarPosition.projectIncomingRay(0, 45, 0, .5, 0, .5, ray);

    assertEquals(.5, ray.originX, EPSILON);
    assertEquals(.5, ray.originY, EPSILON);
    assertEquals(1, ray.originZ, EPSILON);
    assertEquals(180, ray.azimuthDegrees, EPSILON);
    assertEquals(-45, ray.elevationDegrees, EPSILON);

    // From the origin, the incoming direction reaches the configured center-floor target.
    final double travel = Math.sqrt(.5);
    final double directionY = Math.sin(Math.toRadians(ray.elevationDegrees));
    final double directionZ = Math.cos(Math.toRadians(ray.elevationDegrees))
      * Math.cos(Math.toRadians(ray.azimuthDegrees));
    assertEquals(0, ray.originY + travel * directionY, EPSILON);
    assertEquals(.5, ray.originZ + travel * directionZ, EPSILON);
  }

  @Test
  void northOffsetRotatesTheRayIntoTheInstallationFrame() {
    final SolarPosition.Ray ray = new SolarPosition.Ray();
    // If model +Z itself points east, a sun due east is directly along model +Z.
    SolarPosition.projectIncomingRay(90, 0, 90, .5, .5, .5, ray);

    assertEquals(.5, ray.originX, EPSILON);
    assertEquals(.5, ray.originY, EPSILON);
    assertEquals(1, ray.originZ, EPSILON);
    assertEquals(180, ray.azimuthDegrees, EPSILON);
  }

  @Test
  void belowHorizonRemainsPhysicalAndDaylightOutputGatesIt() {
    final SolarPosition.Ray ray = new SolarPosition.Ray();
    SolarPosition.projectIncomingRay(180, -10, 0, .5, 0, .5, ray);

    assertEquals(.5, ray.originX, EPSILON);
    assertEquals(0, ray.originY, EPSILON);
    assertEquals(.5, ray.originZ, EPSILON);
    assertEquals(0, ray.azimuthDegrees, EPSILON);
    assertEquals(10, ray.elevationDegrees, EPSILON);

    // 08:00 UTC is the middle of the night at the configured Black Rock longitude.
    configureManualUtc(8);
    this.solarPosition.loop(0);
    assertEquals(0, this.solarPosition.altitudeNorm.getValue(), EPSILON);
    assertTrue(this.solarPosition.elevation.getValue() > 0,
      "an incoming ray from a below-horizon sun points upward");
  }

  @Test
  void manualTimeIsModulatableAndSurvivesClockModeChanges() {
    assertTrue(this.solarPosition.manualTime instanceof CompoundParameter);
    this.solarPosition.manualTime.setValue(7.25);

    this.solarPosition.timeSource.setValue(SolarPosition.TimeSource.SYSTEM);
    this.solarPosition.loop(0);
    this.solarPosition.timeSource.setValue(SolarPosition.TimeSource.MANUAL);
    this.solarPosition.loop(0);

    assertEquals(7.25, this.solarPosition.manualTime.getBaseValue(), EPSILON);
  }

  @Test
  void outputsAreReadOnlySourcesRatherThanCompoundTargets() {
    final BoundedParameter[] outputs = {
      this.solarPosition.originX, this.solarPosition.originY, this.solarPosition.originZ,
      this.solarPosition.azimuth, this.solarPosition.elevation,
      this.solarPosition.altitudeNorm
    };
    for (BoundedParameter output : outputs) {
      assertTrue(!(output instanceof CompoundParameter));
      assertSame(output, this.solarPosition.getParameter(output.getPath()));
    }
    assertThrows(UnsupportedOperationException.class,
      () -> this.solarPosition.setNormalized(.5));
  }

  @Test
  void eachOutputCanDriveTheMatchingDistanceFieldInput() throws Exception {
    configureManualUtc(12);
    this.solarPosition.loop(0);

    final TransformedDistanceField pattern = new TransformedDistanceField(this.lx);
    this.lx.engine.mixer.addChannel(new LXPattern[] { pattern });
    final BoundedParameter[] sources = {
      this.solarPosition.originX, this.solarPosition.originY, this.solarPosition.originZ,
      this.solarPosition.azimuth, this.solarPosition.elevation
    };
    final CompoundParameter[] targets = {
      pattern.originX, pattern.originY, pattern.originZ, pattern.azimuth, pattern.elevation
    };

    for (int i = 0; i < sources.length; ++i) {
      targets[i].setNormalized(0);
      final LXCompoundModulation modulation = new LXCompoundModulation(
        this.lx.engine.modulation, sources[i], targets[i]);
      this.lx.engine.modulation.addModulation(modulation);
      modulation.range.setValue(1);
      assertEquals(sources[i].getNormalized(), targets[i].getNormalized(), EPSILON,
        sources[i].getLabel() + " should map directly into " + targets[i].getLabel());
    }
  }

  @Test
  void providesItsOwnCompactModulatorUi() {
    assertTrue(this.solarPosition instanceof UIModulatorControls);
  }

  private void configureManualUtc(double hour) {
    this.solarPosition.timeSource.setValue(SolarPosition.TimeSource.MANUAL);
    this.solarPosition.timeZone.setValue("UTC");
    this.solarPosition.manualYear.setValue(2003);
    this.solarPosition.manualMonth.setValue(10);
    this.solarPosition.manualDay.setValue(17);
    this.solarPosition.manualTime.setValue(hour);
    this.solarPosition.timeScale.setValue(0);
  }
}
