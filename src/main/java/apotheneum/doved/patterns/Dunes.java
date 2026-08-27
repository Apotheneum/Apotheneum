/**
 * Copyright 2026- Dan Oved
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Dan Oved
 */

package apotheneum.doved.patterns;

import java.util.Arrays;
import java.util.Random;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;

/**
 * A side-on desert, evolved as a 2D cellular sand bed and rendered through one depth slice.
 *
 * <p>The bed wraps in both dimensions. Ring-column wrapping makes a dune continuous around a
 * chamber; depth wrapping removes an artificial back wall from a scene that is otherwise an
 * endlessly repeating field. The viewer sees the middle depth slice, so genuinely 2D changes
 * in the field move brinks through the visible cutting plane instead of merely translating a
 * fixed one-dimensional silhouette.</p>
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Dunes")
@LXComponent.Description("Migrating desert dunes with saltating grains streaming from their crests")
public class Dunes extends ColorNativePattern {

  private static final String LOG_PREFIX = "[Dunes] ";
  private static final int BED_DEPTH = 56;
  private static final int VIEW_DEPTH = BED_DEPTH / 2;
  private static final float SLAB = .25f;
  private static final float INITIAL_SAND = 3f;
  private static final float BARE_SAND = SLAB * .5f;
  // A flat 200x56 bed needs millions of sub-slab events before its first brinks emerge. This
  // stays well below a perceptible construction stall on the installation JVM.
  private static final int WARMUP_EVENTS = 2_000_000;
  private static final int MAX_HOPS = 8;
  // A long lee ray is important at this resolution: a short ray leaves both sides of a crest
  // equally erodible and degenerates the characteristic stoss/brink/slip sawtooth into noise.
  private static final int MAX_SHADOW_DISTANCE = 48;
  private static final double SHADOW_SLOPE = Math.tan(Math.toRadians(15));
  private static final double RIPPLE_WAVELENGTH = 4;
  private static final double MAX_DELTA_SECONDS = .05;
  private static final int MIN_EVENTS_PER_FRAME = 8;
  private static final int MAX_EVENTS_PER_FRAME = 820;
  // Strong aligned wind can keep several staggered grains airborne from the same crest. The
  // pool remains fixed so this density never allocates during rendering.
  private static final int MAX_GRAINS_PER_COLUMN = 4;
  private static final double DEFAULT_BASE = .52;
  // The simulation is allowed to make large local piles, but its visible cutting plane is
  // normalized to an installation-scale dune relief. At the default Relief this is 11.5 rows
  // peak-to-trough; at full Relief it is capped at 16 rows.
  private static final double MAX_PROFILE_OFFSET_ROWS = 8;
  // Only remove the sub-slab CA teeth. Broader reconstruction averages away the real brinks
  // that distinguish the four-to-six dunes visible across an individual cube face.
  private static final int PROFILE_SMOOTH_RADIUS = 4;
  private static final double SURFACE_DETAIL_FADE_ROWS = 2.5;
  private static final double DEEP_SAND_LEVEL = .045;
  private static final double TWO_PI = 2 * Math.PI;
  private static final long DEFAULT_SEED = 0x64756e6573L;

  public final CompoundParameter wind =
    new CompoundParameter("Wind", .35, 0, 1)
    .setDescription("Strength of along-wall sand transport");

  public final CompoundParameter windAzimuth =
    new CompoundParameter("Dir", 0, 0, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("World-space direction the wind crosses the installation");

  public final CompoundParameter drift =
    new CompoundParameter("Drift", 3, .05, 6)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Slow wind-direction drift in degrees per minute; prevents static pile-up");

  public final CompoundParameter rate =
    new CompoundParameter("Rate", .18, 0, 1)
    .setDescription("Saltation events per frame from meditative to dramatic");

  public final CompoundParameter repose =
    new CompoundParameter("Repose", 34, 26, 42)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Avalanche angle: lower values make broader, gentler dunes");

  public final CompoundParameter supply =
    new CompoundParameter("Supply", .72, .1, 1)
    .setDescription("Fraction of the mobile sand bed participating in transport");

  public final CompoundParameter scale =
    new CompoundParameter("Scale", .75, .25, 2)
    .setDescription("Saltation hop length and resulting dune scale");

  public final CompoundParameter relief =
    new CompoundParameter("Relief", .72, .1, 1)
    .setDescription("Visible vertical relief of the simulated dune profile");

  public final CompoundParameter base =
    new CompoundParameter("Base", DEFAULT_BASE, .2, .85)
    .setDescription("Base height; low settings can place troughs in door gaps");

  public final CompoundParameter travel =
    new CompoundParameter("Travel", 0, 0, 1)
    .setDescription("Move the viewpoint through depth in the dune field");

  public final CompoundParameter travelRate =
    new CompoundParameter("Cruise", 0, 0, .2)
    .setDescription("Automatic travel cycles per minute; zero leaves the viewpoint still");

  public final CompoundParameter depth =
    new CompoundParameter("Depth", 10, 1, 14)
    .setDescription("Rows of gentle light falloff below each sand surface");

  public final CompoundParameter grains =
    new CompoundParameter("Grains", .42, 0, 1)
    .setDescription("Density of airborne saltation grains");

  public final CompoundParameter hop =
    new CompoundParameter("Hop", .55, .1, 1)
    .setDescription("Length and speed of visible ballistic grain hops");

  public final CompoundParameter lift =
    new CompoundParameter("Lift", .45, 0, 1)
    .setDescription("Height of grains lifted from high-shear crests");

  public final CompoundParameter ripples =
    new CompoundParameter("Ripples", .34, 0, 1)
    .setDescription("Brightness-only travelling corrugation on windward slopes");

  public final CompoundParameter shade =
    new CompoundParameter("Shade", .68, 0, 1)
    .setDescription("Slope shading between bright stoss slopes and dark slip faces");

  public final TriggerParameter reseed =
    new TriggerParameter("Reseed", this::reseedFields)
    .setDescription("Build a fresh dune field and pre-run its saltation warm-up");

  private static final class Grain {
    private double x;
    private double y;
    private double launchY;
    private double velocity;
    private double life;
    private double age;
    private double arc;
    private double settle;
    private int direction;
    private boolean active;
  }

  /** One unwrapped surface: 2D height field, intensity buffers, and a fixed grain pool. */
  private final class Field {
    private final int width;
    private final int height;
    private final float[] bed;
    private final float[] sandEnergy;
    private final float[] grainEnergy;
    private final Grain[] grainPool;
    private final float[] profile;
    private final float[] renderedProfile;
    private int grainCursor;
    private int sliceLower = VIEW_DEPTH;
    private double sliceFraction;
    private double meanBed;
    private double renderedProfileScale;
    private long warmupNanos;
    private int warmupSourceShadows;
    private int warmupTargetShadows;
    private boolean recordingWarmup;

    private Field(int width, int height) {
      this.width = width;
      this.height = height;
      this.bed = new float[width * BED_DEPTH];
      this.sandEnergy = new float[width * height];
      this.grainEnergy = new float[width * height];
      this.profile = new float[width];
      this.renderedProfile = new float[width];
      this.grainPool = new Grain[width * MAX_GRAINS_PER_COLUMN];
      for (int i = 0; i < this.grainPool.length; ++i) {
        this.grainPool[i] = new Grain();
      }
      seedAndWarmup();
    }

    private void seedAndWarmup() {
      Arrays.fill(this.bed, INITIAL_SAND);
      // A sub-slab disturbance breaks perfect symmetry. The mass stays close to a flat bed;
      // Werner's asymmetric deposition and shadow rule create the larger forms during warm-up.
      for (int i = 0; i < this.bed.length; ++i) {
        this.bed[i] += (random.nextFloat() - .5f) * SLAB;
      }
      for (Grain grain : this.grainPool) {
        grain.active = false;
      }
      final long started = System.nanoTime();
      this.warmupSourceShadows = 0;
      this.warmupTargetShadows = 0;
      this.recordingWarmup = true;
      for (int i = 0; i < WARMUP_EVENTS; ++i) {
        saltate(null, 1, .75, 34, .75, false);
      }
      this.recordingWarmup = false;
      this.warmupNanos = System.nanoTime() - started;
      refreshProfile();
    }

    private int bedIndex(int x, int d) {
      return Math.floorMod(d, BED_DEPTH) * this.width + Math.floorMod(x, this.width);
    }

    private float bedAt(int x, int d) {
      return this.bed[bedIndex(x, d)];
    }

    private void selectSlice(double travel) {
      final double slice = VIEW_DEPTH + travel * BED_DEPTH;
      this.sliceLower = (int) Math.floor(slice);
      this.sliceFraction = slice - this.sliceLower;
    }

    private double sliceBedAt(int x) {
      return sliceBedAt(x, this.sliceLower, this.sliceFraction);
    }

    private double sliceBedAt(int x, int lower, double fraction) {
      final double near = bedAt(x, lower);
      return (fraction == 0) ? near : LXUtils.lerp(near, bedAt(x, lower + 1), fraction);
    }

    private void addBed(int x, int d, float amount) {
      this.bed[bedIndex(x, d)] += amount;
    }

    private boolean inShadow(int x, int d, int direction) {
      final float here = bedAt(x, d);
      for (int distance = 1; distance <= MAX_SHADOW_DISTANCE; ++distance) {
        final float upwind = bedAt(x - direction * distance, d);
        if (here < upwind - distance * SHADOW_SLOPE) {
          return true;
        }
      }
      return false;
    }

    /** Executes one Werner saltation event, including lee shadow and local two-axis avalanches. */
    private void saltate(
      Apotheneum.Orientation orientation,
      int direction,
      double transport,
      double reposeDegrees,
      double scaleValue,
      boolean emitGrain
    ) {
      if (random.nextDouble() > transport) {
        return;
      }
      final int sourceX = random.nextInt(this.width);
      final int sourceD = random.nextInt(BED_DEPTH);
      final boolean sourceShadow = inShadow(sourceX, sourceD, direction);
      if (sourceShadow && this.recordingWarmup) {
        ++this.warmupSourceShadows;
      }
      if (bedAt(sourceX, sourceD) <= BARE_SAND || sourceShadow) {
        return;
      }
      addBed(sourceX, sourceD, -SLAB);
      final int hopLength = Math.max(2, (int) Math.round(LXUtils.lerp(3, 13, scaleValue)));
      int targetX = sourceX;
      int targetD = sourceD;
      for (int attempt = 0; attempt < MAX_HOPS; ++attempt) {
        targetX += direction * hopLength;
        // The small cross-wind random walk is what gives the bed a 2D, not stripe-like, life.
        targetD += random.nextInt(3) - 1;
        final boolean shadow = inShadow(targetX, targetD, direction);
        if (shadow && this.recordingWarmup) {
          ++this.warmupTargetShadows;
        }
        final boolean occupied = bedAt(targetX, targetD) > BARE_SAND;
        final double depositProbability = shadow ? 1 : (occupied ? .6 : .4);
        if ((attempt == MAX_HOPS - 1) || (random.nextDouble() < depositProbability)) {
          addBed(targetX, targetD, SLAB);
          avalanche(targetX, targetD, reposeDegrees);
          final double brink = brinkAt(targetX, direction);
          final double alongWall = emitGrain ? alongWallWindAt(orientation, targetX) : 0;
          final double alignment = windAlignment(alongWall);
          // Arrival events provide naturally staggered launch times. Their direction and
          // frequency both vanish with the component of wind that actually runs along this wall.
          if (emitGrain && brink > .15 && alignment > 0
            && random.nextDouble() < grainsValue * alignment) {
            emitGrain(targetX, alongWall, brink);
          }
          return;
        }
      }
    }

    private void avalanche(int x, int d, double reposeDegrees) {
      final float maximumSlope = (float) Math.tan(Math.toRadians(reposeDegrees));
      int fromX = x;
      int fromD = d;
      // A moved slab can uncover another unstable local slope. Bound this small local relaxation
      // so an exceptionally long frame never turns one saltation event into unbounded work.
      for (int pass = 0; pass < 16; ++pass) {
        final float from = bedAt(fromX, fromD);
        int toX = fromX;
        int toD = fromD;
        float lowest = from;
        final float west = bedAt(fromX - 1, fromD);
        final float east = bedAt(fromX + 1, fromD);
        final float near = bedAt(fromX, fromD - 1);
        final float far = bedAt(fromX, fromD + 1);
        if (west < lowest) { lowest = west; toX = fromX - 1; toD = fromD; }
        if (east < lowest) { lowest = east; toX = fromX + 1; toD = fromD; }
        if (near < lowest) { lowest = near; toX = fromX; toD = fromD - 1; }
        if (far < lowest) { lowest = far; toX = fromX; toD = fromD + 1; }
        if (from - lowest <= maximumSlope) {
          return;
        }
        addBed(fromX, fromD, -SLAB);
        addBed(toX, toD, SLAB);
        fromX = toX;
        fromD = toD;
      }
    }

    private double shearAt(int x, int direction) {
      final double rise = sliceBedAt(x) - sliceBedAt(x - direction * 2);
      final double fall = sliceBedAt(x + direction * 2) - sliceBedAt(x);
      return LXUtils.clamp(Math.max(rise, -fall) / (2 * SLAB), 0, 1);
    }

    /** A plume can only launch at a crest: sand must rise into and fall away from this cell. */
    private double brinkAt(int x, int direction) {
      final double crest = sliceBedAt(x);
      final double approach = crest - sliceBedAt(x - direction);
      final double departure = crest - sliceBedAt(x + direction);
      return LXUtils.clamp(Math.min(approach, departure) / SLAB, 0, 1);
    }

    private void emitGrain(int x, double alongWall, double shear) {
      final double alignment = windAlignment(alongWall);
      if (shear <= .08 || alignment == 0) {
        return;
      }
      final Grain grain = this.grainPool[this.grainCursor++ % this.grainPool.length];
      grain.x = x + .5;
      // Saltation leaves a persistent black gap above the brink. Its low, long trajectory reads
      // as a downwind stream, rather than as independent high-arcing sparkles.
      grain.launchY = surfaceY(x) - LXUtils.lerp(.9, 1.55, liftValue) * (.85 + .15 * shear);
      grain.y = grain.launchY;
      grain.velocity = alongWall * LXUtils.lerp(18, 42, hopValue) * (.9 + .1 * shear);
      grain.life = LXUtils.lerp(.8, 1.35, hopValue);
      grain.arc = LXUtils.lerp(.45, 1.35, liftValue) * (.9 + .1 * shear);
      grain.settle = LXUtils.lerp(.95, 1.55, hopValue);
      grain.age = 0;
      grain.direction = alongWall < 0 ? -1 : 1;
      grain.active = true;
    }

    private void refreshProfile() {
      double total = 0;
      for (int x = 0; x < this.width; ++x) {
        final float value = (float) sliceBedAt(x);
        this.profile[x] = value;
        total += value;
      }
      this.meanBed = total / this.width;
      // This short triangular reconstruction removes isolated one-LED teeth while preserving
      // the CA's own asymmetric stoss/brink/lee forms rather than imposing an idealized curve.
      for (int x = 0; x < this.width; ++x) {
        double sum = 0;
        double weight = 0;
        for (int offset = -PROFILE_SMOOTH_RADIUS; offset <= PROFILE_SMOOTH_RADIUS; ++offset) {
          final double sampleWeight = PROFILE_SMOOTH_RADIUS + 1 - Math.abs(offset);
          sum += this.profile[Math.floorMod(x + offset, this.width)] * sampleWeight;
          weight += sampleWeight;
        }
        this.renderedProfile[x] = (float) (sum / weight);
      }
      double largestDeviation = 0;
      for (float value : this.renderedProfile) {
        largestDeviation = Math.max(largestDeviation, Math.abs(value - this.meanBed));
      }
      this.renderedProfileScale = MAX_PROFILE_OFFSET_ROWS / Math.max(largestDeviation, SLAB);
    }

    private double surfaceY(int x) {
      return this.height * baseHorizonValue - (this.renderedProfile[Math.floorMod(x, this.width)] - this.meanBed)
        * this.renderedProfileScale * reliefValue;
    }

    private void run(double dt, Apotheneum.Orientation orientation) {
      selectSlice(travelValue);
      final int events = (int) Math.round(LXUtils.lerp(
        MIN_EVENTS_PER_FRAME,
        MAX_EVENTS_PER_FRAME,
        rateValue * rateValue
      ));
      for (int i = 0; i < events; ++i) {
        final int x = random.nextInt(this.width);
        final LXPoint root = orientation.columns()[x].points[this.height - 1];
        final LXPoint next = orientation.columns()[(x + 1) % this.width].points[this.height - 1];
        final double tangentX = next.x - root.x;
        final double tangentZ = next.z - root.z;
        final double length = Math.hypot(tangentX, tangentZ);
        if (length == 0) {
          continue;
        }
        final double along = (windX * tangentX + windZ * tangentZ) / length;
        saltate(orientation, along < 0 ? -1 : 1, Math.abs(along) * windValue * supplyValue,
          reposeValue, scaleValue, true);
      }
      refreshProfile();
      Arrays.fill(this.sandEnergy, 0);
      Arrays.fill(this.grainEnergy, 0);
      renderBed(orientation);
      renderGrains(dt, orientation);
      output(orientation);
    }

    private double[] horizonMetrics(double baseHorizon, double relief, double travel) {
      selectSlice(travel);
      refreshProfile();
      double total = 0;
      double minimum = Double.POSITIVE_INFINITY;
      double maximum = Double.NEGATIVE_INFINITY;
      for (int x = 0; x < this.width; ++x) {
        final double y = this.height * baseHorizon - (this.renderedProfile[x] - this.meanBed)
          * this.renderedProfileScale * relief;
        total += y;
        minimum = Math.min(minimum, y);
        maximum = Math.max(maximum, y);
      }
      return new double[] { total / this.width, minimum, maximum };
    }

    private double sliceCorrelation(double firstTravel, double secondTravel) {
      final double firstSlice = VIEW_DEPTH + firstTravel * BED_DEPTH;
      final int firstLower = (int) Math.floor(firstSlice);
      final double firstFraction = firstSlice - firstLower;
      final double secondSlice = VIEW_DEPTH + secondTravel * BED_DEPTH;
      final int secondLower = (int) Math.floor(secondSlice);
      final double secondFraction = secondSlice - secondLower;
      double firstTotal = 0;
      double secondTotal = 0;
      double firstSquareTotal = 0;
      double secondSquareTotal = 0;
      double productTotal = 0;
      for (int x = 0; x < this.width; ++x) {
        final double first = sliceBedAt(x, firstLower, firstFraction);
        final double second = sliceBedAt(x, secondLower, secondFraction);
        firstTotal += first;
        secondTotal += second;
        firstSquareTotal += first * first;
        secondSquareTotal += second * second;
        productTotal += first * second;
      }
      final double covariance = productTotal - firstTotal * secondTotal / this.width;
      final double firstVariance = firstSquareTotal - firstTotal * firstTotal / this.width;
      final double secondVariance = secondSquareTotal - secondTotal * secondTotal / this.width;
      return covariance / Math.sqrt(firstVariance * secondVariance);
    }

    /** Signed normalized wind projected onto this wall column's tangent, in [-1, 1]. */
    private double alongWallWindAt(Apotheneum.Orientation orientation, int x) {
      final int column = Math.floorMod(x, this.width);
      final LXPoint root = orientation.columns()[column].points[this.height - 1];
      final LXPoint next = orientation.columns()[(column + 1) % this.width].points[this.height - 1];
      final double tangentX = next.x - root.x;
      final double tangentZ = next.z - root.z;
      final double length = Math.hypot(tangentX, tangentZ);
      return (length == 0) ? 0 : (windX * tangentX + windZ * tangentZ) / length;
    }

    /** Smoothly gate saltation plumes off on head-on walls rather than choosing an arbitrary sign. */
    private static double windAlignment(double alongWall) {
      final double normalized = LXUtils.clamp((Math.abs(alongWall) - .12) / .88, 0, 1);
      return normalized * normalized * (3 - 2 * normalized);
    }

    private int directionAt(Apotheneum.Orientation orientation, int x) {
      final LXPoint root = orientation.columns()[x].points[this.height - 1];
      final LXPoint next = orientation.columns()[(x + 1) % this.width].points[this.height - 1];
      return (windX * (next.x - root.x) + windZ * (next.z - root.z)) < 0 ? -1 : 1;
    }

    private void renderBed(Apotheneum.Orientation orientation) {
      for (int x = 0; x < this.width; ++x) {
        final int direction = directionAt(orientation, x);
        final double surface = surfaceY(x);
        final double slope = (this.renderedProfile[Math.floorMod(x + direction, this.width)]
          - this.renderedProfile[Math.floorMod(x - direction, this.width)]) * .5;
        // Rising in the downwind direction is the lit stoss slope; dropping after the brink is
        // the lee slip face. This intentionally shades the surface rather than filling a wall.
        final double signedSlope = LXUtils.clamp(slope / (SLAB * .55), -1, 1);
        // Keep this range deliberately dramatic enough to read from across the chamber: the
        // positive (windward) stoss face catches light, while the negative lee face falls into
        // a deep sand shadow beneath the brink.
        final double surfaceLighting = LXUtils.clamp(
          1 + shadeValue * signedSlope * .72,
          .18,
          1
        );
        final double shear = shearAt(x, direction);
        for (int y = Math.max(0, (int) Math.floor(surface)); y < this.height; ++y) {
          final double belowSurface = y - surface;
          // Ripple and slope detail belong to the skin of the dune. Keeping them at full
          // strength through the body made their per-column values look like vertical curtains.
          final double surfaceDetail = Math.exp(-belowSurface / SURFACE_DETAIL_FADE_ROWS);
          final double lighting = 1 + (surfaceLighting - 1) * surfaceDetail;
          // Fractional coverage softens the one-row horizon transition. It keeps broad stoss
          // slopes from resolving as hard square teeth without changing the filled sand body.
          final double edgeCoverage = LXUtils.clamp(y + 1 - surface, 0, 1);
          final double ripple = 1 + ripplesValue * .16 * shear * surfaceDetail
            * Math.sin(TWO_PI * (x / RIPPLE_WAVELENGTH - ripplePhase * (1 + shear)));
          final int index = y * this.width + x;
          // It is a landscape, not a wire: every row beneath the horizon carries sand. The
          // exponential falloff saves the eye from a uniform bright wall, while the non-zero
          // floor guarantees troughs still have a dim mass all the way to the physical floor.
          final double depthLight = DEEP_SAND_LEVEL + (1 - DEEP_SAND_LEVEL)
            * Math.exp(-belowSurface / depthValue);
          this.sandEnergy[index] = (float) LXUtils.clamp(
            lighting * ripple * depthLight * edgeCoverage, 0, 1);
        }
      }
    }

    private void renderGrains(double dt, Apotheneum.Orientation orientation) {
      for (Grain grain : this.grainPool) {
        if (!grain.active) {
          continue;
        }
        grain.age += dt;
        if (grain.age >= grain.life) {
          grain.active = false;
          continue;
        }
        grain.x += grain.velocity * dt;
        // A cube corner can turn a raked wall into a head-on one. Saltation does not keep
        // tracing the unwrapped ring through that corner; it settles as the tangent component
        // vanishes, leaving the two head-on faces visibly calm.
        final double alignment = windAlignment(alongWallWindAt(orientation, (int) Math.floor(grain.x)));
        if (alignment == 0) {
          grain.active = false;
          continue;
        }
        // This is deliberately a shallow saltation hop: it crosses many columns while rising
        // less than two rows, leaving a detached, coherent low plume above the lee.
        final double progress = grain.age / grain.life;
        grain.y = grain.launchY - grain.arc * 4 * progress * (1 - progress)
          + grain.settle * progress;
        // Once a grain returns to the lee surface it is sand again. Do not let a particle paint
        // inside the mass; the intervening black is what makes the saltation plume detach.
        if (grain.y >= surfaceY((int) Math.floor(grain.x)) - .35) {
          grain.active = false;
          continue;
        }
        splatGrainStreak(grain, alignment * (1 - progress) * (.35 + .65 * grainsValue));
      }
    }

    /** Three fixed samples leave a two-pixel trail downwind, making the low hop legible in stills. */
    private void splatGrainStreak(Grain grain, double amount) {
      splatGrain(grain.x, grain.y, amount * .8);
      splatGrain(grain.x - grain.direction * .9, grain.y, amount * .45);
      splatGrain(grain.x - grain.direction * 1.8, grain.y, amount * .2);
    }

    private void splatGrain(double x, double y, double amount) {
      final int y0 = (int) Math.floor(y);
      if (y0 < -1 || y0 >= this.height) {
        return;
      }
      final int x0 = (int) Math.floor(x);
      final double fx = x - x0;
      final double fy = y - y0;
      // The eighth-power interpolation is Grass's Sharp-style point weighting: a sparse veil
      // remains grains, not a soft fog, at this low resolution.
      final double wx1 = sharpenWeight(fx);
      final double wy1 = sharpenWeight(fy);
      addGrain(x0, y0, amount * (1 - wx1) * (1 - wy1));
      addGrain(x0 + 1, y0, amount * wx1 * (1 - wy1));
      addGrain(x0, y0 + 1, amount * (1 - wx1) * wy1);
      addGrain(x0 + 1, y0 + 1, amount * wx1 * wy1);
    }

    private void addGrain(int x, int y, double amount) {
      if (y >= 0 && y < this.height) {
        final int index = y * this.width + Math.floorMod(x, this.width);
        this.grainEnergy[index] = (float) Math.min(1, this.grainEnergy[index] + amount);
      }
    }

    private void output(Apotheneum.Orientation orientation) {
      final Apotheneum.Column[] columns = orientation.columns();
      for (int x = 0; x < this.width; ++x) {
        final LXPoint[] points = columns[x].points;
        final int available = orientation.available(x);
        for (int y = 0; y < available; ++y) {
          if (!isViewPoint(points[y].index)) {
            continue;
          }
          final int bufferIndex = y * this.width + x;
          final double sand = this.sandEnergy[bufferIndex];
          final double grain = this.grainEnergy[bufferIndex];
          final double slope = (this.renderedProfile[Math.floorMod(x + 1, this.width)]
            - this.renderedProfile[Math.floorMod(x - 1, this.width)]) / (2 * SLAB);
          final int direction = directionAt(orientation, x);
          // ColorNative's slope position is also a per-column signal. Fade it with depth just
          // like the lighting and ripple terms, otherwise its hue/value variation becomes a
          // second vertical curtain through the continuous sand body.
          final double surfaceDetail = Math.exp(-Math.max(0, y - surfaceY(x))
            / SURFACE_DETAIL_FADE_ROWS);
          final double colorSlope = LXUtils.clamp(direction * slope * surfaceDetail, -1, 1);
          colors[points[y].index] = (sand <= 0 && grain <= 0) ? LXColor.BLACK : compositeColors(
            primary.color(colorSlope), sand,
            secondary.color(-colorSlope), grain
          );
        }
        for (int y = available; y < points.length; ++y) {
          if (isViewPoint(points[y].index)) {
            colors[points[y].index] = LXColor.BLACK;
          }
        }
      }
    }
  }

  private final Random random = new Random(DEFAULT_SEED);
  private final Field cubeField = new Field(Apotheneum.Cube.Ring.LENGTH, Apotheneum.GRID_HEIGHT);
  private final Field cylinderField = new Field(Apotheneum.Cylinder.Ring.LENGTH, Apotheneum.CYLINDER_HEIGHT);

  // Resolved once per render, never read from parameter objects inside the simulation loops.
  private double windValue;
  private double windX;
  private double windZ;
  private double rateValue;
  private double reposeValue;
  private double supplyValue;
  private double scaleValue;
  private double reliefValue;
  private double baseHorizonValue;
  private double travelValue;
  private double travelRateValue;
  private double depthValue;
  private double grainsValue;
  private double hopValue;
  private double liftValue;
  private double ripplesValue;
  private double shadeValue;
  private double driftPhase;
  private double ripplePhase;
  private double travelPhase;

  public Dunes(LX lx) {
    super(lx, 1, .7, 2, .7);
    addParameter("wind", this.wind);
    addParameter("windAzimuth", this.windAzimuth);
    addParameter("drift", this.drift);
    addParameter("rate", this.rate);
    addParameter("repose", this.repose);
    addParameter("supply", this.supply);
    addParameter("scale", this.scale);
    addParameter("relief", this.relief);
    addParameter("base", this.base);
    addParameter("travel", this.travel);
    addParameter("travelRate", this.travelRate);
    addParameter("depth", this.depth);
    addParameter("grains", this.grains);
    addParameter("hop", this.hop);
    addParameter("lift", this.lift);
    addParameter("ripples", this.ripples);
    addParameter("shade", this.shade);
    addParameter("reseed", this.reseed);
    log("warm-up cube=" + millis(this.cubeField.warmupNanos)
      + "ms cylinder=" + millis(this.cylinderField.warmupNanos) + "ms"
      + " shadow-source=" + this.cubeField.warmupSourceShadows + "/" + WARMUP_EVENTS
      + " shadow-target=" + this.cubeField.warmupTargetShadows + "/" + WARMUP_EVENTS);
  }

  private static long millis(long nanos) {
    return Math.round(nanos / 1_000_000.);
  }

  private static void log(String message) {
    LX.log(LOG_PREFIX + message);
  }

  private void reseedFields() {
    this.cubeField.seedAndWarmup();
    this.cylinderField.seedAndWarmup();
    log("reseed warm-up cube=" + millis(this.cubeField.warmupNanos)
      + "ms cylinder=" + millis(this.cylinderField.warmupNanos) + "ms"
      + " shadow-source=" + this.cubeField.warmupSourceShadows + "/" + WARMUP_EVENTS
      + " shadow-target=" + this.cubeField.warmupTargetShadows + "/" + WARMUP_EVENTS);
  }

  /** Package-visible regression probe for the default cube cutting plane, in output rows. */
  double[] cubeHorizonMetrics() {
    return cubeHorizonMetrics(this.base.getValue(), this.travel.getValue());
  }

  double[] cubeHorizonMetrics(double base, double travel) {
    return this.cubeField.horizonMetrics(baseHorizon(base), this.relief.getValue(), travel);
  }

  double cubeSliceCorrelation(double firstTravel, double secondTravel) {
    return this.cubeField.sliceCorrelation(firstTravel, secondTravel);
  }

  private static double baseHorizon(double base) {
    // Base is height above the floor: lower values lower the view into the door rows. The
    // default maps exactly to the prior fixed .52 image-space horizon fraction.
    return 2 * DEFAULT_BASE - base;
  }

  private static double sharpenWeight(double fraction) {
    double sharp = fraction * fraction;
    sharp *= sharp;
    sharp *= sharp;
    final double complement = 1 - fraction;
    double sharpComplement = complement * complement;
    sharpComplement *= sharpComplement;
    sharpComplement *= sharpComplement;
    return sharp / (sharp + sharpComplement);
  }

  @Override
  protected void render(double deltaMs) {
    updateViewMask();
    this.primary.update();
    this.secondary.update();
    final double dt = Math.min(deltaMs * .001, MAX_DELTA_SECONDS);
    this.windValue = this.wind.getValue();
    this.rateValue = this.rate.getValue();
    this.reposeValue = this.repose.getValue();
    this.supplyValue = this.supply.getValue();
    this.scaleValue = this.scale.getValue();
    this.reliefValue = this.relief.getValue();
    this.baseHorizonValue = baseHorizon(this.base.getValue());
    this.travelRateValue = this.travelRate.getValue();
    this.travelPhase += dt * this.travelRateValue / 60.;
    this.travelPhase -= Math.floor(this.travelPhase);
    this.travelValue = this.travel.getValue() + this.travelPhase;
    this.travelValue -= Math.floor(this.travelValue);
    this.depthValue = this.depth.getValue();
    this.grainsValue = this.grains.getValue();
    this.hopValue = this.hop.getValue();
    this.liftValue = this.lift.getValue();
    this.ripplesValue = this.ripples.getValue();
    this.shadeValue = this.shade.getValue();
    this.driftPhase += dt * this.drift.getValue() / 60.;
    this.ripplePhase += dt * LXUtils.lerp(.15, 1.8, this.hopValue);
    final double angle = Math.toRadians(this.windAzimuth.getValue() + this.driftPhase);
    this.windX = Math.cos(angle);
    this.windZ = Math.sin(angle);

    this.cubeField.run(dt, Apotheneum.cube.exterior);
    this.cylinderField.run(dt, Apotheneum.cylinder.exterior);
    if (Apotheneum.hasInterior) {
      this.cubeField.output(Apotheneum.cube.interior);
      this.cylinderField.output(Apotheneum.cylinder.interior);
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);
    addColumn(uiDevice, "Wind", newKnob(this.wind), newKnob(this.windAzimuth), newKnob(this.drift))
      .setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Sand", newKnob(this.rate), newKnob(this.repose), newKnob(this.supply))
      .setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Dune", newKnob(this.scale), newKnob(this.relief), newKnob(this.base))
      .setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Slice", newKnob(this.travel), newKnob(this.travelRate),
      newKnob(this.depth))
      .setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Veil", newKnob(this.grains), newKnob(this.hop), newKnob(this.lift))
      .setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Surface", newKnob(this.ripples), newKnob(this.shade),
      newButton(this.reseed).setTriggerable(true)).setChildSpacing(6);
    buildColorDeviceControls(ui, uiDevice);
  }
}
