package apotheneum.doved.modulators;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIDoubleBox;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;
import heronarts.lx.utils.LXUtils;

/**
 * Produces an incoming sunlight ray from an offline astronomical calculation.
 *
 * <p>The sun determines a direction but not one unique ray: sunlight is effectively a
 * parallel field. {@link #offsetX}, {@link #offsetY} and {@link #offsetZ} choose which ray
 * crosses the installation, relative to its normalized center. At zero offset the ray passes
 * through the center; an offset Y of -.5 moves it to floor level. The origin is the intersection
 * of that ray with the sun-facing side of the normalized unit model, and the output angles point
 * from that origin inward toward the offset target. They therefore map directly to the origin
 * and aim controls of a transformed distance-field RAY.
 *
 * <p>Output parameters are bounded rather than compound: they are modulation sources, not
 * targets. Their normalized values map directly to the corresponding normalized pattern
 * parameters even though angles are displayed in degrees here. Use full positive modulation
 * depth for exact tracking; reducing the depth intentionally compresses the solar trajectory.
 */
@LXModulator.Global("Solar Position")
@LXModulator.Device("Solar Position")
@LXCategory(LXCategory.CORE)
public class SolarPosition extends LXModulator implements LXNormalizedParameter,
  LXOscComponent, UIModulatorControls<SolarPosition> {

  public enum TimeSource {
    SYSTEM("System"),
    MANUAL("Manual");

    private final String label;

    TimeSource(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  private static final String DEFAULT_ZONE = "America/Los_Angeles";
  private static final double MILLIS_PER_HOUR = 3_600_000.;
  private static final double DIRECTION_EPSILON = 1e-12;

  public final CompoundParameter latitude =
    new CompoundParameter("Latitude", 40.79, -90, 90)
    .setUnits(LXParameter.Units.DEGREES)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Geographic latitude, positive north");

  public final CompoundParameter longitude =
    new CompoundParameter("Longitude", -119.21, -180, 180)
    .setUnits(LXParameter.Units.DEGREES)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Geographic longitude, positive east and negative west");

  public final CompoundParameter northOffset =
    new CompoundParameter("North", 0, 0, 360)
    .setUnits(LXParameter.Units.DEGREES)
    .setWrappable(true)
    .setDescription("True bearing of the installation's normalized +Z axis");

  public final CompoundParameter offsetX = offset("Offset X");
  public final CompoundParameter offsetY = offset("Offset Y");
  public final CompoundParameter offsetZ = offset("Offset Z");

  public final EnumParameter<TimeSource> timeSource =
    new EnumParameter<TimeSource>("Clock", TimeSource.SYSTEM)
    .setDescription("Use the computer clock or the configured rehearsal time");

  public final DiscreteParameter manualYear;
  public final DiscreteParameter manualMonth;
  public final DiscreteParameter manualDay;

  public final CompoundParameter manualTime =
    new CompoundParameter("Time", 18, 0, 24)
    .setDescription("Manual local time as decimal hours; remains modulatable for day-cycle automation");

  public final CompoundParameter timeScale =
    new CompoundParameter("Scale", 1, 0, 3_600)
    .setExponent(3)
    .setDescription("Simulated seconds per real second; 0 freezes manual time and 1 is real time");

  public final StringParameter timeZone =
    new StringParameter("Zone", DEFAULT_ZONE)
    .setDescription("IANA timezone used to interpret the manual date and time");

  public final BoundedParameter originX = output("Origin X", 0, 1,
    "Sun-facing unit-box intersection for the selected ray");
  public final BoundedParameter originY = output("Origin Y", 0, 1,
    "Sun-facing unit-box intersection for the selected ray");
  public final BoundedParameter originZ = output("Origin Z", 0, 1,
    "Sun-facing unit-box intersection for the selected ray");
  public final BoundedParameter azimuth =
    output("Ray Azim", 0, 360, "Incoming ray bearing in the installation frame")
    .setUnits(LXParameter.Units.DEGREES)
    .setWrappable(true);
  public final BoundedParameter elevation =
    output("Ray Elev", -90, 90, "Incoming ray elevation; negative points downward")
    .setUnits(LXParameter.Units.DEGREES);
  public final BoundedParameter altitudeNorm = output("Sun Alt", 0, 1,
    "Solar elevation normalized from the horizon to zenith; zero below the horizon");

  private final Clock clock;
  private final SolarPositionCalculator.Result solar =
    new SolarPositionCalculator.Result();
  private final Ray ray = new Ray();

  private TimeSource activeTimeSource = null;
  private long lastSystemMillis;
  private double systemSimulatedMillis;
  private double manualElapsedMillis;

  private ZoneId validZone = ZoneId.of(DEFAULT_ZONE);
  private String zoneInput = DEFAULT_ZONE;
  private int cachedYear = Integer.MIN_VALUE;
  private int cachedMonth = Integer.MIN_VALUE;
  private int cachedDay = Integer.MIN_VALUE;
  private double cachedManualTime = Double.NaN;
  private String cachedZone = null;
  private long manualBaseMillis;

  public SolarPosition() {
    this(Clock.systemUTC());
  }

  SolarPosition(Clock clock) {
    super("Solar Position");
    this.clock = Objects.requireNonNull(clock, "May not use a null clock");

    final LocalDate today = LocalDate.now(this.clock.withZone(this.validZone));
    this.manualYear = new DiscreteParameter("Year", today.getYear(), 2000, 2101)
      .setDescription("Year of the manual rehearsal date");
    this.manualMonth = new DiscreteParameter("Month", today.getMonthValue(), 1, 13)
      .setDescription("Month of the manual rehearsal date");
    this.manualDay = new DiscreteParameter("Day", today.getDayOfMonth(), 1, 32)
      .setDescription("Day of the manual rehearsal date; invalid month endings clamp safely");

    addParameter("latitude", this.latitude);
    addParameter("longitude", this.longitude);
    addParameter("northOffset", this.northOffset);
    addParameter("offsetX", this.offsetX);
    addParameter("offsetY", this.offsetY);
    addParameter("offsetZ", this.offsetZ);
    addParameter("timeSource", this.timeSource);
    addParameter("manualYear", this.manualYear);
    addParameter("manualMonth", this.manualMonth);
    addParameter("manualDay", this.manualDay);
    addParameter("manualTime", this.manualTime);
    addParameter("timeScale", this.timeScale);
    addParameter("timeZone", this.timeZone);
    addParameter("originX", this.originX);
    addParameter("originY", this.originY);
    addParameter("originZ", this.originZ);
    addParameter("azimuth", this.azimuth);
    addParameter("elevation", this.elevation);
    addParameter("altitudeNorm", this.altitudeNorm);

    setDescription("Tracks the real or rehearsed sun through a point offset from model center");
    setUnits(LXParameter.Units.PERCENT_NORMALIZED);
  }

  private static CompoundParameter offset(String label) {
    return new CompoundParameter(label, 0, -.5, .5)
      .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
      .setPolarity(LXParameter.Polarity.BIPOLAR)
      .setDescription("Offset from model center for the selected sunlight ray");
  }

  private static BoundedParameter output(
    String label, double min, double max, String description) {

    return new BoundedParameter(label, min, min, max)
      .setDescription(description);
  }

  @Override
  protected double computeValue(double deltaMs) {
    final long epochMillis = effectiveTimeMillis(deltaMs);
    SolarPositionCalculator.calculate(
      epochMillis, this.latitude.getValue(), this.longitude.getValue(), this.solar);

    projectIncomingRay(
      this.solar.getAzimuthDegrees(), this.solar.getElevationDegrees(),
      this.northOffset.getValue(),
      this.offsetX.getValue(), this.offsetY.getValue(), this.offsetZ.getValue(),
      this.ray);

    this.originX.setValue(this.ray.originX);
    this.originY.setValue(this.ray.originY);
    this.originZ.setValue(this.ray.originZ);
    this.azimuth.setValue(this.ray.azimuthDegrees);
    this.elevation.setValue(this.ray.elevationDegrees);
    this.altitudeNorm.setValue(LXUtils.constrain(
      this.solar.getElevationDegrees() / 90, 0, 1));
    return this.altitudeNorm.getValue();
  }

  private long effectiveTimeMillis(double deltaMs) {
    final TimeSource source = this.timeSource.getEnum();
    if (source != this.activeTimeSource) {
      this.activeTimeSource = source;
      if (source == TimeSource.SYSTEM) {
        this.lastSystemMillis = this.clock.millis();
        this.systemSimulatedMillis = this.lastSystemMillis;
      } else {
        this.manualElapsedMillis = 0;
      }
    }

    if (source == TimeSource.SYSTEM) {
      final long now = this.clock.millis();
      final long realDelta = now - this.lastSystemMillis;
      if (realDelta < 0) {
        this.systemSimulatedMillis = now;
      } else {
        this.systemSimulatedMillis += realDelta * this.timeScale.getValue();
      }
      this.lastSystemMillis = now;
      return Math.round(this.systemSimulatedMillis);
    }

    updateManualBase();
    this.manualElapsedMillis += Math.max(0, deltaMs) * this.timeScale.getValue();
    return this.manualBaseMillis + Math.round(this.manualElapsedMillis);
  }

  private void updateManualBase() {
    resolveZone();
    final int year = this.manualYear.getValuei();
    final int month = this.manualMonth.getValuei();
    final int requestedDay = this.manualDay.getValuei();
    final int day = Math.min(requestedDay, daysInMonth(year, month));
    final double manualTime = this.manualTime.getValue();
    final String zone = this.validZone.getId();
    if (year != this.cachedYear || month != this.cachedMonth || day != this.cachedDay
      || Double.compare(manualTime, this.cachedManualTime) != 0
      || !zone.equals(this.cachedZone)) {
      final long wallMillis = Math.round(manualTime * MILLIS_PER_HOUR);
      final LocalDateTime wallTime = LocalDate.of(year, month, day).atStartOfDay()
        .plusNanos(wallMillis * 1_000_000);
      this.manualBaseMillis = wallTime.atZone(this.validZone).toInstant().toEpochMilli();
      this.cachedYear = year;
      this.cachedMonth = month;
      this.cachedDay = day;
      this.cachedManualTime = manualTime;
      this.cachedZone = zone;
    }
  }

  private static int daysInMonth(int year, int month) {
    return switch (month) {
      case 2 -> isLeapYear(year) ? 29 : 28;
      case 4, 6, 9, 11 -> 30;
      default -> 31;
    };
  }

  private static boolean isLeapYear(int year) {
    return (year % 4 == 0) && ((year % 100 != 0) || (year % 400 == 0));
  }

  private void resolveZone() {
    final String requested = this.timeZone.getString();
    if (!requested.equals(this.zoneInput)) {
      this.zoneInput = requested;
      try {
        this.validZone = ZoneId.of(requested);
      } catch (DateTimeException x) {
        LX.warning("[SolarPosition] Invalid timezone '" + requested
          + "', continuing with " + this.validZone.getId());
      }
    }
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException(
      "SolarPosition output is calculated; it cannot be set directly");
  }

  @Override
  public boolean isSnapshotControl(LXParameter parameter) {
    if (isOutput(parameter)) {
      return false;
    }
    return super.isSnapshotControl(parameter);
  }

  @Override
  public boolean isClipAutomationControl(LXListenableNormalizedParameter parameter) {
    if (isOutput(parameter)) {
      return false;
    }
    return super.isClipAutomationControl(parameter);
  }

  private boolean isOutput(LXParameter parameter) {
    return parameter == this.originX || parameter == this.originY || parameter == this.originZ
      || parameter == this.azimuth || parameter == this.elevation
      || parameter == this.altitudeNorm;
  }

  @Override
  public void buildModulatorControls(
    UI ui, UIModulator uiModulator, SolarPosition solarPosition) {

    uiModulator.setLayout(UI2dContainer.Layout.VERTICAL, 4);
    uiModulator.addChildren(
      row(
        newDoubleBox(solarPosition.latitude, 38),
        newDoubleBox(solarPosition.longitude, 38),
        newDoubleBox(solarPosition.northOffset, 34),
        newTextBox(solarPosition.timeZone, 50),
        outputBox(solarPosition.originX, 24),
        outputBox(solarPosition.originY, 24)),
      row(
        newEnumBox(solarPosition.timeSource, 34),
        newIntegerBox(solarPosition.manualYear, 30),
        newIntegerBox(solarPosition.manualMonth, 22),
        newIntegerBox(solarPosition.manualDay, 22),
        newDoubleBox(solarPosition.manualTime, 38),
        outputBox(solarPosition.originZ, 22),
        outputBox(solarPosition.azimuth, 26)),
      row(
        newDoubleBox(solarPosition.timeScale, 36),
        newDoubleBox(solarPosition.offsetX, 36),
        newDoubleBox(solarPosition.offsetY, 36),
        newDoubleBox(solarPosition.offsetZ, 36),
        outputBox(solarPosition.elevation, 28),
        outputBox(solarPosition.altitudeNorm, 28)));
  }

  private static UI2dContainer row(UI2dComponent... children) {
    return UI2dContainer.newHorizontalContainer(16, 4, children);
  }

  private UIDoubleBox outputBox(BoundedParameter parameter, float width) {
    final UIDoubleBox box = newDoubleBox(parameter, width);
    box.setEditable(false);
    return box;
  }

  static final class Ray {
    double originX;
    double originY;
    double originZ;
    double azimuthDegrees;
    double elevationDegrees;
  }

  static Ray projectIncomingRay(
    double solarAzimuthDegrees, double solarElevationDegrees, double northOffsetDegrees,
    double offsetX, double offsetY, double offsetZ, Ray ray) {

    Objects.requireNonNull(ray, "May not project into a null ray");
    final double targetX = .5 + offsetX;
    final double targetY = .5 + offsetY;
    final double targetZ = .5 + offsetZ;
    final double modelSunAzimuth = Math.toRadians(
      SolarPositionCalculator.normalizeDegrees(solarAzimuthDegrees - northOffsetDegrees));
    final double solarElevation = Math.toRadians(solarElevationDegrees);
    final double horizontal = Math.cos(solarElevation);
    final double sunX = Math.sin(modelSunAzimuth) * horizontal;
    final double sunY = Math.sin(solarElevation);
    final double sunZ = Math.cos(modelSunAzimuth) * horizontal;

    double distance = Double.POSITIVE_INFINITY;
    distance = boundaryDistance(targetX, sunX, distance);
    distance = boundaryDistance(targetY, sunY, distance);
    distance = boundaryDistance(targetZ, sunZ, distance);

    ray.originX = LXUtils.constrain(targetX + sunX * distance, 0, 1);
    ray.originY = LXUtils.constrain(targetY + sunY * distance, 0, 1);
    ray.originZ = LXUtils.constrain(targetZ + sunZ * distance, 0, 1);
    ray.azimuthDegrees = SolarPositionCalculator.normalizeDegrees(
      solarAzimuthDegrees - northOffsetDegrees + 180);
    ray.elevationDegrees = -solarElevationDegrees;
    return ray;
  }

  private static double boundaryDistance(double target, double direction, double current) {
    if (direction > DIRECTION_EPSILON) {
      return Math.min(current, (1 - target) / direction);
    }
    if (direction < -DIRECTION_EPSILON) {
      return Math.min(current, -target / direction);
    }
    return current;
  }
}
