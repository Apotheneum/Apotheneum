package apotheneum.doved.patterns;

import java.util.Arrays;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;

/**
 * A local, travelling, triggered spilling breaker rendered as a single-valued
 * height field.
 *
 * <p>The event deliberately stops at stage 1: its crest never detaches from the
 * water surface and it does not form an overhang or barrel. A long back and a
 * short face provide the spatial asymmetry, while the event timing provides a
 * slow steepen, fast slump, and long wash.
 *
 * <p>Each trigger starts one bounded event: the local footprint travels until
 * the wash envelope reaches zero, then remains idle, and retriggering restarts
 * it at the selected launch azimuth. Enabling Circle instead restarts the
 * envelope in place each time it completes, so the wave laps the ring
 * continuously; it is self-starting, and it never resets the foam mid-lap
 * because whitewater belongs to the ring rather than to one event.
 *
 * <p>Reverse flips the travel direction by mirroring the signed arc distance,
 * so the asymmetric profile, the peel and the foam drift all flip together and
 * the steep face keeps leading rather than the wave running backwards.
 *
 * <p>Water color follows the project palette through the two {@link ColorNativePattern}
 * roles -- {@link #surfaceColor} ({@code primary}) and {@link #deepColor} ({@code secondary})
 * -- both resolved at the same per-column physics argument (see {@link #crestPhysics}) and
 * blended by depth, the same shape as {@code LavaLamp}'s temperature ramp. The crest line and
 * the whitewater foam stay a fixed constant, {@link OceanField#MENISCUS_COLOR}: foam is white
 * because it scatters light rather than because of the water's pigment, so it does not track
 * a palette stop the way the water body does.
 *
 * <p>Requested break height is capped at 85% of the LED rows between the base
 * waterline and the ceiling, with half a row reserved for the antialiased crest.
 * This makes the wave progressively smaller near the ceiling instead of clipping
 * or pretending that height can keep rising when no headroom remains.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Breaker")
@LXComponent.Description("A local spilling wave that travels, breaks, and leaves whitewater")
public class Breaker extends ColorNativePattern {

  static final double APPROACH_SECONDS = 2.2;
  static final double COLLAPSE_SECONDS = .35;
  static final double WASH_SECONDS = 2.25;
  static final double EVENT_SECONDS = APPROACH_SECONDS + COLLAPSE_SECONDS + WASH_SECONDS;

  private static final int CUBE_RING_LENGTH = 4 * Apotheneum.GRID_WIDTH;
  private static final double BACK_FRACTION = .78;
  private static final double INITIAL_FACE_FRACTION = .34;
  private static final double BREAKING_FACE_FRACTION = .22;
  private static final double COLLAPSED_FACE_FRACTION = .16;
  private static final double APPROACH_TRAVEL = -.075;
  private static final double THROW_TRAVEL = .026;
  private static final double WASH_TRAVEL = .042;
  private static final double PEEL_DELAY_FRACTION = .45;
  private static final double FOAM_DECAY_RATE = 2.25;
  private static final double FOAM_GRAVITY_ROWS = 18;
  private static final int FOAM_POOL_SIZE = 192;
  /** Ring-space width against which the shape's small crest-motion nudges were tuned. */
  private static final double REFERENCE_WIDTH_S = 50.0 / CUBE_RING_LENGTH;

  public final CompoundParameter level =
    new CompoundParameter("Level", .3, .08, .9)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Base waterline height in world space");

  public final CompoundParameter breakHeight =
    new CompoundParameter("Height", 14, 4, 22)
    .setDescription("Requested crest height in LED rows, capped by available headroom");

  public final CompoundParameter eventWidth =
    new CompoundParameter("Width", .18, .08, .36)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Planar wave width as a fraction of the full 3D crossing distance (2R)");

  public final CompoundParameter breakAzimuth =
    new CompoundParameter("Azimuth", 0, 0, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setWrappable(true)
    .setDescription("Compass direction the planar wave travels; selects ring position in Circle mode");

  public final BooleanParameter snapToFaces =
    new BooleanParameter("Face Snap", false)
    .setDescription("Circle mode only: round the ring azimuth to the nearest cube-face centre");

  // One crossing per event. Anything faster finishes the crossing before the wash phase
  // begins, so the wave is already off-model while its foam drains and the tail of the
  // event renders as dead air -- at .4 crossings/sec the last 2.3s of a 4.8s event were
  // literally unchanging. At this rate the crest is still on the far side of the room
  // through the collapse and wash, which is when they actually read.
  public static final double ONE_CROSSING_PER_EVENT = 1 / EVENT_SECONDS;

  public final CompoundParameter travelSpeed =
    new CompoundParameter(
      "Travel",
      ONE_CROSSING_PER_EVENT,
      .5 * ONE_CROSSING_PER_EVENT,
      3 * ONE_CROSSING_PER_EVENT)
    .setDescription("Planar travel speed in full crossings per second; the default crosses exactly once per event (Circle mode uses ring laps per second)");

  public final BooleanParameter reverse =
    new BooleanParameter("Reverse", false)
    .setDescription("Reverse the travel direction; the steep face always leads");

  public final BooleanParameter circle =
    new BooleanParameter("Circle", false)
    .setDescription("Use the original continuous ring-lap behavior instead of a straight 3D crossing");

  public final CompoundParameter pace =
    new CompoundParameter("Pace", 1, .6, 1.6)
    .setDescription("Playback rate of the shape envelope; travel stays in real-time crossings per second");

  public final CompoundParameter foamAmount =
    new CompoundParameter("Foam", .8, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Density and brightness of the collapse foam burst");

  public final TriggerParameter breakWave =
    new TriggerParameter("Break", this::triggerBreak)
    .setDescription("Launch or restart the breaker at the selected azimuth");

  /** The shallow/surface end of the water ramp. Alias for {@link ColorNativePattern#primary}. */
  public final ColorRole surfaceColor;

  /** The deep end of the water ramp. Alias for {@link ColorNativePattern#secondary}. */
  public final ColorRole deepColor;

  private final OceanField.GeometryCache geometry = new OceanField.GeometryCache();
  private final FoamParticle[] foamParticles = new FoamParticle[FOAM_POOL_SIZE];
  private final int[] foamFeedback;

  private boolean eventActive;
  private boolean resetFoam;
  private double eventSeconds;
  private double eventTravelLaps;
  private double steeredAzimuthS = Double.NaN;
  private double breakCenterS;
  private double planeCenterX;
  private double planeCenterZ;
  private double planeDirX = Double.NaN;
  private double planeDirZ;
  private double planeRadius;
  private double planeFront;
  private double planeEventHalfWidth;
  private boolean planeCenterCached;
  private double texturePhase;
  private double foamSpawnAccumulator;
  private int foamPoolCursor;
  private int foamSerial;

  public Breaker(LX lx) {
    super(lx, 1, .5, 2, .5);
    this.surfaceColor = this.primary;
    this.deepColor = this.secondary;
    // LX assigns the pattern's own colors buffer when it joins a channel, but
    // the model is already available here because ApotheneumPattern initialized
    // it in super(lx). Size the independent feedback buffer from that model.
    this.foamFeedback = new int[lx.getModel().size];
    for (int i = 0; i < this.foamParticles.length; ++i) {
      this.foamParticles[i] = new FoamParticle();
    }

    addParameter("level", this.level);
    addParameter("breakHeight", this.breakHeight);
    addParameter("eventWidth", this.eventWidth);
    addParameter("breakAzimuth", this.breakAzimuth);
    addParameter("snapToFaces", this.snapToFaces);
    addParameter("travelSpeed", this.travelSpeed);
    addParameter("reverse", this.reverse);
    addParameter("circle", this.circle);
    addParameter("pace", this.pace);
    addParameter("foamAmount", this.foamAmount);
    addParameter("break", this.breakWave);
  }

  private void triggerBreak() {
    if (this.circle.isOn()) {
      this.breakCenterS = resolvedBreakS(
        this.breakAzimuth.getValue() / 360.,
        this.snapToFaces.isOn()
      );
      this.steeredAzimuthS = this.breakCenterS;
    } else {
      updatePlaneGeometry();
      this.planeEventHalfWidth = planarWidth(this.eventWidth.getValue(), this.planeRadius) * .5;
      this.planeFront = planarStartFront(this.planeRadius, this.planeEventHalfWidth);
    }
    this.eventSeconds = 0;
    this.eventTravelLaps = 0;
    this.eventActive = true;
    this.resetFoam = true;
  }

  @Override
  protected void render(double deltaMs) {
    updateViewMask();
    clearView();
    this.geometry.update();
    this.primary.update();
    this.secondary.update();

    final double deltaSeconds = Math.max(0, deltaMs) * .001;
    if (this.resetFoam) {
      clearFoam();
      this.resetFoam = false;
    }
    final double foamDecay = feedbackDecay(FOAM_DECAY_RATE, deltaSeconds);

    this.texturePhase = (this.texturePhase + deltaSeconds * .72) % LX.TWO_PI;
    final boolean circular = this.circle.isOn();
    final double direction = travelDirection(this.reverse.isOn());
    // Circle mode is self-starting: switching it on begins a lap without also
    // needing the trigger, which is what "make it circle" asks for.
    if (circular && !this.eventActive) {
      triggerBreak();
    }
    if (!circular) {
      updatePlaneGeometry();
    }
    if (this.eventActive) {
      this.eventSeconds += deltaSeconds * this.pace.getValue();
      if (circular) {
        // The ring behavior is intentionally unchanged in Circle mode, including live azimuth steering.
        final double azimuthS = resolvedBreakS(
          this.breakAzimuth.getValue() / 360.,
          this.snapToFaces.isOn()
        );
        if (Double.isNaN(this.steeredAzimuthS)) {
          this.steeredAzimuthS = azimuthS;
        } else if (azimuthS != this.steeredAzimuthS) {
          this.breakCenterS = wrapRingPosition(
            this.breakCenterS + signedArcDistance(azimuthS, this.steeredAzimuthS)
          );
          this.steeredAzimuthS = azimuthS;
        }
        this.eventTravelLaps += Math.abs(direction * this.travelSpeed.getValue() * deltaSeconds);
        this.breakCenterS = wrapRingPosition(
          this.breakCenterS + direction * this.travelSpeed.getValue() * deltaSeconds
        );
      } else {
        this.planeFront = Math.min(
          planarEndFront(this.planeRadius, this.planeEventHalfWidth),
          this.planeFront + planarCrossingDistance(this.planeRadius, this.travelSpeed.getValue(), deltaSeconds)
        );
      }
      if (this.eventSeconds >= EVENT_SECONDS) {
        if (circular) {
          // Carry the remainder so the envelope restarts seamlessly at the
          // position already reached. Foam is deliberately NOT reset here: it
          // belongs to the ring, not the event, so a new lap must not wipe the
          // whitewater the previous one just laid down.
          this.eventSeconds -= EVENT_SECONDS;
          this.eventTravelLaps = 0;
        } else {
          this.eventSeconds = EVENT_SECONDS;
          this.eventActive = false;
        }
      }
    }

    final double baseSurfaceY = OceanField.surfaceY(
      this.level.getValue(),
      this.geometry.floorY(),
      this.geometry.ceilingY(),
      this.geometry.rowPitch()
    );
    final double requestedHeightRows = effectiveBreakHeightRows(
      this.breakHeight.getValue(),
      baseSurfaceY,
      this.geometry.ceilingY(),
      this.geometry.rowPitch()
    );
    final double widthS = circular
      ? this.eventWidth.getValue()
      : planarWidth(this.eventWidth.getValue(), this.planeRadius);
    final double faceFraction = this.eventActive
      ? faceFraction(this.eventSeconds)
      : BREAKING_FACE_FRACTION;
    final double crestS = profileCrestS(
      circular ? this.breakCenterS : this.planeFront,
      widthS,
      faceFraction,
      (this.eventActive ? crestOffset(this.eventSeconds) : WASH_TRAVEL)
        * (circular ? 1 : widthS / REFERENCE_WIDTH_S),
      direction
    );

    updateFoam(deltaSeconds, baseSurfaceY, crestS, widthS, direction, circular);

    renderOrientation(
      Apotheneum.cube.exterior,
      baseSurfaceY,
      requestedHeightRows,
      this.eventSeconds,
      crestS,
      widthS,
      faceFraction,
      OceanField.CUBE_S_OFFSET,
      foamDecay,
      direction,
      circular
    );
    renderOrientation(
      Apotheneum.cylinder.exterior,
      baseSurfaceY,
      requestedHeightRows,
      this.eventSeconds,
      crestS,
      widthS,
      faceFraction,
      0,
      foamDecay,
      direction,
      circular
    );

    renderFoamOrientation(Apotheneum.cube.exterior, OceanField.CUBE_S_OFFSET, circular);
    renderFoamOrientation(Apotheneum.cylinder.exterior, 0, circular);
    compositeFoam(Apotheneum.cube.exterior);
    compositeFoam(Apotheneum.cylinder.exterior);
    copyExteriorMasked(Apotheneum.cube.exterior, Apotheneum.cube.interior);
    copyExteriorMasked(Apotheneum.cylinder.exterior, Apotheneum.cylinder.interior);
  }

  private void renderOrientation(
      Apotheneum.Orientation orientation,
      double baseSurfaceY,
      double heightRows,
      double eventSeconds,
      double crestS,
      double widthS,
      double faceFraction,
      double columnOffset,
      double foamDecay,
      double direction,
      boolean circular) {
    final int ringLength = orientation.columns().length;
    final double verticalRows =
      (this.geometry.ceilingY() - this.geometry.floorY()) / this.geometry.rowPitch() + 1;

    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final double s = OceanField.arcLength(columnIndex, columnOffset, ringLength);
      // Mirroring the distance is what makes Reverse work: back/face asymmetry,
      // the peel and the crest mask are all expressed in terms of it, so the
      // steep face keeps leading instead of the wave running backwards.
      final double distance = circular
        ? direction * signedArcDistance(s, crestS)
        : planarDistance(column.points[0], crestS);
      final double profile = spatialProfile(distance, widthS, faceFraction);
      final double normalizedDistance = distance / widthS;
      final double eventAmount = peeledHeightEnvelope(eventSeconds, normalizedDistance);
      // Both roles are resolved once per column at the same physics argument -- how much of
      // the requested break height this column is currently showing -- and lerped per row by
      // depth below, following LavaLamp's two-stop ramp with depth in place of temperature.
      final double physics = crestPhysics(eventAmount, profile);
      final int surface = this.primary.color(physics);
      final int deep = this.secondary.color(physics);
      final double surfaceY = baseSurfaceY
        + this.geometry.rowPitch() * heightRows * eventAmount * profile;
      final double crestMask = eventAmount * (
        1 - OceanField.smoothstep(.018, .065, Math.abs(normalizedDistance))
      );
      final boolean onBack = normalizedDistance < 0 && normalizedDistance > -BACK_FRACTION;
      final boolean onFace = normalizedDistance >= 0 && normalizedDistance < faceFraction;
      final double backTexture = .72 + .11 * Math.sin(LX.TWO_PI * 11 * s + this.texturePhase)
        + .07 * Math.sin(LX.TWO_PI * 17 * s - 1.31 * this.texturePhase);
      final int available = orientation.available(columnIndex++);

      for (int row = 0; row < available; ++row) {
        final LXPoint point = column.points[row];
        final int pointIndex = point.index;
        final boolean inView = isViewPoint(pointIndex);
        if (inView && this.foamFeedback[pointIndex] != LXColor.BLACK) {
          this.foamFeedback[pointIndex] = LXColor.scaleBrightness(
            this.foamFeedback[pointIndex],
            (float) foamDecay
          );
        }
        final double signedRows = (surfaceY - point.y) / this.geometry.rowPitch();
        final double coverage = OceanField.waterCoverage(signedRows);
        final double crestLine = crestMask * (
          1 - OceanField.smoothstep(.42, 1.08, Math.abs(signedRows))
        );
        if (coverage <= 0 && crestLine <= 0) {
          continue;
        }

        final double depth = LXUtils.clamp(signedRows / verticalRows, 0, 1);
        // LXColor.lerp(a, b, t) returns a at t=0, so primary (surface) goes first and this
        // depth term must stay 0-at-surface, 1-at-deep -- unchanged from the LinkedColorParameter
        // lerp this replaces.
        int color = LXColor.lerp(
          surface,
          deep,
          (float) Math.sqrt(depth)
        );
        double brightness = coverage * (.9 - .58 * Math.sqrt(depth));
        if (onBack) {
          brightness *= LXUtils.lerp(
            .64,
            LXUtils.clamp(backTexture, .48, .9),
            eventAmount
          );
        } else if (onFace) {
          final double faceShadow = OceanField.smoothstep(.6, 8, signedRows);
          brightness *= LXUtils.lerp(
            .64,
            LXUtils.lerp(.4, .68, faceShadow),
            eventAmount
          );
        } else {
          brightness *= .64;
        }

        // A narrow, high-contrast seam immediately below the crest is the
        // stage-1 remnant of a barrel. It never changes the surface topology.
        final double seam = crestMask
          * OceanField.smoothstep(.72, 1.12, signedRows)
          * (1 - OceanField.smoothstep(2.05, 2.48, signedRows));
        brightness *= LXUtils.lerp(1, .1, seam);
        color = LXColor.scaleBrightness(
          color,
          (float) LXUtils.clamp(brightness, 0, 1)
        );

        if (crestLine > 0) {
          // Foam is a fixed constant, not a palette role -- see OceanField#MENISCUS_COLOR.
          color = LXColor.lightest(
            color,
            LXColor.scaleBrightness(
              OceanField.MENISCUS_COLOR,
              (float) LXUtils.clamp(.45 + .55 * crestLine, 0, 1)
            )
          );
        }
        if (inView) {
          this.colors[pointIndex] = color;
        }
      }
    }
  }

  /**
   * Local surface displacement relative to rest, as the [-1, 1] physics scalar the palette
   * roles couple to. The event height field only ever lifts the surface above rest -- there is
   * no trough -- so this is {@code eventAmount * profile}: the same fraction of the requested
   * break height that already scales how far {@code surfaceY} sits above {@code baseSurfaceY}
   * (the height itself cancels out of that ratio, so this is already normalized by the
   * amplitude actually driving the surface). 0 at rest, approaching 1 at a full crest. Never
   * negative for this pattern, which is within the [-1, 1] contract, not a violation of it.
   */
  static double crestPhysics(double eventAmount, double profile) {
    return LXUtils.clamp(eventAmount * profile, -1, 1);
  }

  private void updateFoam(
      double deltaSeconds,
      double baseSurfaceY,
      double crestS,
      double widthS,
      double direction,
      boolean circular) {
    final double pitch = this.geometry.rowPitch();
    for (FoamParticle particle : this.foamParticles) {
      if (!particle.active) {
        continue;
      }
      particle.age += deltaSeconds;
      if (particle.age >= particle.life || particle.y < baseSurfaceY - 2.5 * pitch) {
        particle.active = false;
        continue;
      }
      particle.s = circular
        ? advanceRingPosition(particle.s, particle.velocityS, deltaSeconds)
        : particle.s + particle.velocityS * deltaSeconds;
      particle.y += particle.velocityY * deltaSeconds;
      particle.velocityY -= FOAM_GRAVITY_ROWS * pitch * deltaSeconds;
    }

    if (!this.eventActive) {
      return;
    }
    final double burst = foamBurst(this.eventSeconds) * this.foamAmount.getValue();
    this.foamSpawnAccumulator += burst * 150 * deltaSeconds;
    while (this.foamSpawnAccumulator >= 1) {
      spawnFoam(baseSurfaceY, crestS, widthS, faceFraction(this.eventSeconds), direction, circular);
      this.foamSpawnAccumulator -= 1;
    }
  }

  private void spawnFoam(
      double baseSurfaceY,
      double crestS,
      double widthS,
      double faceFraction,
      double direction,
      boolean circular) {
    FoamParticle particle = null;
    for (int i = 0; i < this.foamParticles.length; ++i) {
      final int index = (this.foamPoolCursor + i) % this.foamParticles.length;
      if (!this.foamParticles[index].active) {
        particle = this.foamParticles[index];
        this.foamPoolCursor = (index + 1) % this.foamParticles.length;
        break;
      }
    }
    if (particle == null) {
      return;
    }

    final int serial = ++this.foamSerial;
    final double lateral = random01(serial, 0x2c1b3c6d) - .5;
    final double lift = random01(serial, 0x6d2b79f5);
    particle.active = true;
    particle.age = 0;
    particle.life = .9 + .8 * random01(serial, 0x1b873593);
    particle.s = crestS + (circular ? direction : 1) * peelOffset(
      this.eventSeconds, widthS, faceFraction
    ) + .12 * widthS * lateral;
    if (circular) {
      particle.s = wrapRingPosition(particle.s);
    }
    particle.y = baseSurfaceY + this.geometry.rowPitch() * (.35 + 1.2 * lift);
    particle.velocityS = (circular ? direction : 1) * (.006 + .024 * random01(serial, 0x51ed270b))
      * (circular ? 1 : 2 * this.planeRadius);
    particle.velocityY = this.geometry.rowPitch() * (1.5 + 3 * random01(serial, 0x7f4a7c15));
    particle.brightness = .55 + .45 * random01(serial, 0x165667b1);
  }

  private void renderFoamOrientation(
      Apotheneum.Orientation orientation, double columnOffset, boolean circular) {
    if (!circular) {
      renderPlanarFoamOrientation(orientation);
      return;
    }
    final int ringLength = orientation.columns().length;
    for (FoamParticle particle : this.foamParticles) {
      if (!particle.active) {
        continue;
      }
      final double wrappedS = particle.s - Math.floor(particle.s);
      double columnPosition = wrappedS * ringLength + columnOffset;
      columnPosition -= Math.floor(columnPosition / ringLength) * ringLength;
      final int column0 = (int) Math.floor(columnPosition);
      final int column1 = (column0 + 1) % ringLength;
      final double columnFraction = columnPosition - column0;

      final double rowPosition = (this.geometry.ceilingY() - particle.y) / this.geometry.rowPitch();
      final int row0 = (int) Math.floor(rowPosition);
      final int row1 = row0 + 1;
      final double rowFraction = rowPosition - row0;
      final double lifeFade = Math.sin(Math.PI * particle.age / particle.life);
      final double brightness = particle.brightness * this.foamAmount.getValue()
        * Math.sqrt(Math.max(0, lifeFade));

      addFoamPixel(orientation, column0, row0, brightness * (1 - columnFraction) * (1 - rowFraction));
      addFoamPixel(orientation, column1, row0, brightness * columnFraction * (1 - rowFraction));
      addFoamPixel(orientation, column0, row1, brightness * (1 - columnFraction) * rowFraction);
      addFoamPixel(orientation, column1, row1, brightness * columnFraction * rowFraction);
    }
  }

  /** Paint planar foam at its unwrapped world-space coordinate, never around a ring seam. */
  private void renderPlanarFoamOrientation(Apotheneum.Orientation orientation) {
    final double pitch = this.geometry.rowPitch();
    for (FoamParticle particle : this.foamParticles) {
      if (!particle.active) {
        continue;
      }
      final int row = (int) Math.round((this.geometry.ceilingY() - particle.y) / pitch);
      final double lifeFade = Math.sin(Math.PI * particle.age / particle.life);
      final double brightness = particle.brightness * this.foamAmount.getValue()
        * Math.sqrt(Math.max(0, lifeFade));
      int columnIndex = 0;
      for (Apotheneum.Column column : orientation.columns()) {
        final double distance = Math.abs(planarDistance(column.points[0], particle.s));
        final double columnBrightness = brightness * (1 - distance / pitch);
        addFoamPixel(orientation, columnIndex++, row, columnBrightness);
      }
    }
  }

  private void addFoamPixel(
      Apotheneum.Orientation orientation,
      int columnIndex,
      int row,
      double brightness) {
    if (brightness <= .01 || !OceanField.isAvailableCell(row, orientation.available(columnIndex))) {
      return;
    }
    final int pointIndex = orientation.columns()[columnIndex].points[row].index;
    if (!isViewPoint(pointIndex)) {
      return;
    }
    // Foam is a fixed constant, not a palette role -- see OceanField#MENISCUS_COLOR.
    final int foamColor = LXColor.scaleBrightness(
      OceanField.MENISCUS_COLOR,
      (float) LXUtils.clamp(brightness, 0, 1)
    );
    this.foamFeedback[pointIndex] = LXColor.blend(
      this.foamFeedback[pointIndex],
      foamColor,
      LXColor.Blend.ADD
    );
  }

  private void compositeFoam(Apotheneum.Orientation orientation) {
    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final int available = orientation.available(columnIndex++);
      for (int row = 0; row < available; ++row) {
        final int pointIndex = column.points[row].index;
        if (isViewPoint(pointIndex)) {
          this.colors[pointIndex] = LXColor.lightest(
            this.colors[pointIndex],
            this.foamFeedback[pointIndex]
          );
        }
      }
    }
  }

  /**
   * Mirrors the already-composited exterior colors onto the interior, but only within the
   * current view. {@code ApotheneumPattern.copyExterior()} is a raw arraycopy over whole
   * orientations with no {@code isViewPoint()} awareness (see {@link ViewMaskedPattern}'s
   * class javadoc), so a view that excludes the interior would still have it painted
   * underneath -- the same hole {@code FireballViewTest} covers for Fireball's old
   * {@code copyExterior()} call. Point-by-point with the same gate as every other write here
   * closes it.
   */
  private void copyExteriorMasked(Apotheneum.Orientation exterior, Apotheneum.Orientation interior) {
    if (interior == null) {
      return;
    }
    final Apotheneum.Column[] interiorColumns = interior.columns();
    int columnIndex = 0;
    for (Apotheneum.Column column : exterior.columns()) {
      final LXPoint[] interiorPoints = interiorColumns[columnIndex].points;
      for (int row = 0; row < column.points.length; ++row) {
        final int interiorIndex = interiorPoints[row].index;
        if (isViewPoint(interiorIndex)) {
          this.colors[interiorIndex] = this.colors[column.points[row].index];
        }
      }
      ++columnIndex;
    }
  }

  private void clearFoam() {
    Arrays.fill(this.foamFeedback, LXColor.BLACK);
    for (FoamParticle particle : this.foamParticles) {
      particle.active = false;
    }
    this.foamSpawnAccumulator = 0;
    this.foamPoolCursor = 0;
  }

  static double resolvedBreakS(double requestedS, boolean snapToFaces) {
    final double resolved = snapToFaces ? Math.round(requestedS * 4) / 4. : requestedS;
    return resolved - Math.floor(resolved);
  }

  static double signedArcDistance(double s, double center) {
    double distance = s - center;
    distance -= Math.floor(distance + .5);
    return distance;
  }

  static double travelDirection(boolean reverse) {
    return reverse ? -1 : 1;
  }

  static double advanceRingPosition(double positionS, double lapsPerSecond, double seconds) {
    return wrapRingPosition(positionS + lapsPerSecond * Math.max(0, seconds));
  }

  static double wrapRingPosition(double positionS) {
    return positionS - Math.floor(positionS);
  }

  private void updatePlaneGeometry() {
    final double theta = Math.toRadians(this.breakAzimuth.getValue());
    final double reverse = travelDirection(this.reverse.isOn());
    final double dirX = reverse * Math.cos(theta);
    final double dirZ = reverse * Math.sin(theta);
    if (!this.planeCenterCached) {
      double minX = Double.POSITIVE_INFINITY;
      double maxX = Double.NEGATIVE_INFINITY;
      double minZ = Double.POSITIVE_INFINITY;
      double maxZ = Double.NEGATIVE_INFINITY;
      for (LXPoint point : this.model.points) {
        minX = Math.min(minX, point.x);
        maxX = Math.max(maxX, point.x);
        minZ = Math.min(minZ, point.z);
        maxZ = Math.max(maxZ, point.z);
      }
      this.planeCenterX = .5 * (minX + maxX);
      this.planeCenterZ = .5 * (minZ + maxZ);
      this.planeCenterCached = true;
    }
    if (dirX == this.planeDirX && dirZ == this.planeDirZ) {
      return;
    }
    this.planeDirX = dirX;
    this.planeDirZ = dirZ;
    double radius = 0;
    for (LXPoint point : this.model.points) {
      radius = Math.max(radius, Math.abs(planarProjection(
        point.x, point.z, this.planeCenterX, this.planeCenterZ, dirX, dirZ
      )));
    }
    this.planeRadius = radius;
  }

  private double planarDistance(LXPoint point, double front) {
    return planarProjection(
      point.x, point.z, this.planeCenterX, this.planeCenterZ, this.planeDirX, this.planeDirZ
    ) - front;
  }

  static double planarProjection(
      double x, double z, double centerX, double centerZ, double dirX, double dirZ) {
    return (x - centerX) * dirX + (z - centerZ) * dirZ;
  }

  static double planarWidth(double widthFraction, double radius) {
    return widthFraction * 2 * radius;
  }

  static double planarStartFront(double radius, double halfWidth) {
    return -(radius + halfWidth);
  }

  static double planarEndFront(double radius, double halfWidth) {
    return radius + halfWidth;
  }

  static double planarCrossingDistance(double radius, double crossingsPerSecond, double seconds) {
    return 2 * radius * crossingsPerSecond * Math.max(0, seconds);
  }

  static double spatialProfile(double distance, double width, double faceFraction) {
    if (width <= 0 || faceFraction <= 0) {
      return 0;
    }
    final double x = distance / width;
    if (x < -BACK_FRACTION || x > faceFraction) {
      return 0;
    }
    if (x < 0) {
      final double t = (x + BACK_FRACTION) / BACK_FRACTION;
      return smootherstep(t);
    }
    final double t = x / faceFraction;
    return 1 - smootherstep(t);
  }

  static double profileCrestS(
      double footprintCenterS,
      double width,
      double faceFraction,
      double motionOffset,
      double direction) {
    // The selected azimuth is the footprint centre, not the asymmetric
    // profile's crest. At peak steepness BACK_FRACTION + faceFraction = 1,
    // putting the support at centre +/- width/2 and keeping a 50-column wave
    // within the selected 50-column cube face.
    return footprintCenterS
      + direction * (.5 * (BACK_FRACTION - faceFraction) * width + motionOffset);
  }

  static double heightEnvelope(double seconds) {
    if (seconds <= 0 || seconds >= EVENT_SECONDS) {
      return 0;
    }
    if (seconds < APPROACH_SECONDS) {
      return .12 + .88 * smootherstep(seconds / APPROACH_SECONDS);
    }
    if (seconds < APPROACH_SECONDS + COLLAPSE_SECONDS) {
      final double t = (seconds - APPROACH_SECONDS) / COLLAPSE_SECONDS;
      return LXUtils.lerp(1, .22, smootherstep(t));
    }
    final double t = (seconds - APPROACH_SECONDS - COLLAPSE_SECONDS) / WASH_SECONDS;
    return .22 * (1 - smootherstep(t));
  }

  static double peeledHeightEnvelope(double seconds, double normalizedDistance) {
    if (seconds < APPROACH_SECONDS ||
        seconds >= APPROACH_SECONDS + COLLAPSE_SECONDS) {
      return heightEnvelope(seconds);
    }
    final double footprintPosition = LXUtils.clamp(
      (normalizedDistance + BACK_FRACTION) /
        (BACK_FRACTION + BREAKING_FACE_FRACTION),
      0,
      1
    );
    final double collapseProgress =
      (seconds - APPROACH_SECONDS) / COLLAPSE_SECONDS;
    final double localProgress = LXUtils.clamp(
      (collapseProgress - PEEL_DELAY_FRACTION * footprintPosition) /
        (1 - PEEL_DELAY_FRACTION),
      0,
      1
    );
    return LXUtils.lerp(1, .22, smootherstep(localProgress));
  }

  static double peelOffset(double seconds, double width, double faceFraction) {
    final double progress = LXUtils.clamp(
      (seconds - APPROACH_SECONDS) / COLLAPSE_SECONDS,
      0,
      1
    );
    return width * LXUtils.lerp(
      -BACK_FRACTION,
      faceFraction,
      smootherstep(progress)
    );
  }

  static double faceFraction(double seconds) {
    if (seconds <= 0) {
      return INITIAL_FACE_FRACTION;
    }
    if (seconds < APPROACH_SECONDS) {
      final double steepen = smootherstep(Math.min(1, seconds / (APPROACH_SECONDS * .86)));
      return LXUtils.lerp(INITIAL_FACE_FRACTION, BREAKING_FACE_FRACTION, steepen);
    }
    if (seconds < APPROACH_SECONDS + COLLAPSE_SECONDS) {
      final double t = (seconds - APPROACH_SECONDS) / COLLAPSE_SECONDS;
      return LXUtils.lerp(BREAKING_FACE_FRACTION, COLLAPSED_FACE_FRACTION, smootherstep(t));
    }
    final double t = Math.min(1,
      (seconds - APPROACH_SECONDS - COLLAPSE_SECONDS) / (WASH_SECONDS * .55));
    return LXUtils.lerp(COLLAPSED_FACE_FRACTION, INITIAL_FACE_FRACTION, smootherstep(t));
  }

  static double crestOffset(double seconds) {
    if (seconds <= 0) {
      return APPROACH_TRAVEL;
    }
    if (seconds < APPROACH_SECONDS) {
      final double t = Math.min(1, seconds / (APPROACH_SECONDS * .93));
      return LXUtils.lerp(APPROACH_TRAVEL, 0, easeOutCubic(t));
    }
    if (seconds < APPROACH_SECONDS + COLLAPSE_SECONDS) {
      final double t = (seconds - APPROACH_SECONDS) / COLLAPSE_SECONDS;
      return LXUtils.lerp(0, THROW_TRAVEL, easeOutCubic(t));
    }
    final double t = Math.min(1,
      (seconds - APPROACH_SECONDS - COLLAPSE_SECONDS) / (WASH_SECONDS * .7));
    return LXUtils.lerp(THROW_TRAVEL, WASH_TRAVEL, smootherstep(t));
  }

  static double foamBurst(double seconds) {
    final double start = APPROACH_SECONDS - .08;
    final double end = APPROACH_SECONDS + COLLAPSE_SECONDS + .28;
    if (seconds <= start || seconds >= end) {
      return 0;
    }
    final double t = (seconds - start) / (end - start);
    return Math.sin(Math.PI * t) * Math.sin(Math.PI * t);
  }

  static double effectiveBreakHeightRows(
      double requestedRows,
      double baseSurfaceY,
      double ceilingY,
      double rowPitch) {
    if (rowPitch <= 0) {
      return 0;
    }
    final double headroomRows = Math.max(0, (ceilingY - baseSurfaceY) / rowPitch - .5);
    return Math.min(Math.max(0, requestedRows), .85 * headroomRows);
  }

  static double feedbackDecay(double lambda, double deltaSeconds) {
    return Math.exp(-Math.max(0, lambda) * Math.max(0, deltaSeconds));
  }

  private static double smootherstep(double value) {
    final double t = LXUtils.clamp(value, 0, 1);
    return t * t * t * (t * (t * 6 - 15) + 10);
  }

  private static double easeOutCubic(double value) {
    final double t = LXUtils.clamp(value, 0, 1);
    final double inverse = 1 - t;
    return 1 - inverse * inverse * inverse;
  }

  private static double random01(int serial, int salt) {
    return OceanField.hash01(serial * 0x1f123bb5 ^ salt);
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Water",
      newKnob(this.level),
      newKnob(this.breakHeight),
      newKnob(this.eventWidth)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Crash",
      newButton(this.breakWave).setTriggerable(true),
      newKnob(this.breakAzimuth),
      newButton(this.snapToFaces)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Motion",
      newKnob(this.travelSpeed),
      newButton(this.circle),
      newButton(this.reverse)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Shape",
      newKnob(this.pace),
      newKnob(this.foamAmount)
    ).setChildSpacing(6);

    buildColorDeviceControls(ui, uiDevice);
  }

  private static final class FoamParticle {
    boolean active;
    double s;
    double y;
    double velocityS;
    double velocityY;
    double age;
    double life;
    double brightness;
  }
}
