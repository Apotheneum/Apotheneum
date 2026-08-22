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
import java.util.List;
import java.util.Random;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.vg.VGraphics;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/doved")
@LXComponent.Name("Rockfall")
@LXComponent.Description("Water falling around a shared, endlessly scrolling field of rocks")
public class Rockfall extends ApotheneumPattern implements UIDeviceControls<Rockfall> {

  private static final int DEFAULT_ROCK_COUNT = 55;
  private static final int ROCK_HARD_CAP = 700;
  private static final double DEFAULT_ROCK_SCALE = .2;
  private static final double ROCK_SPACING_CONSTANT = DEFAULT_ROCK_COUNT;
  private static final double ROCK_COVERAGE_BUDGET = 90;
  private static final double ROCK_ROW_WORK_BUDGET = 110;
  private static final double MIN_ROCK_SCALE = .05;
  private static final double MAX_ROCK_SCALE = 2;
  private static final int BASE_CUBE_DROPLET_COUNT = 2200;
  private static final int BASE_CYLINDER_DROPLET_COUNT = 1400;
  private static final int WATER_DENSITY_PARAMETER_MAX = 400;
  private static final double TWO_PI = 2 * Math.PI;
  private static final double GOLDEN_RATIO_CONJUGATE = .6180339887498949;
  static final double VARIATION_LOOP_RADIUS = .65;
  private static final double VARIATION_INDEX_SPACING = 1.731;
  private static final double MAX_DELTA_SECONDS = .05;
  private static final double ROCK_RADIUS_MIN_ROWS = 5;
  private static final double ROCK_RADIUS_MAX_ROWS = 8;
  private static final double COARSE_NOISE_ROWS_SMOOTH = 2.6;
  private static final double COARSE_NOISE_ROWS_JAGGED = .8;
  private static final double FINE_NOISE_ROWS_SMOOTH = 1.3;
  private static final double FINE_NOISE_ROWS_JAGGED = .3;
  private static final double COARSE_NOISE_AMOUNT_MAX = .56;
  private static final double FINE_NOISE_AMOUNT_MAX = .26;
  private static final double SMOOTH_MIN_FACTOR = .18;
  private static final double GRADIENT_EPSILON = 1e-5;
  private static final double CROWN_EPSILON = .08;
  private static final double CONVERGENCE_SNAP_EPSILON = .1;
  private static final double MAX_LATERAL_SLIDE_MULTIPLIER = 2;
  private static final double MAX_LATERAL_FREE_FALL_FRACTION = .25;
  private static final double LURCH_VELOCITY = 145;
  private static final double LURCH_DECAY_PER_SECOND = 4.5;
  private static final double SPRAY_SPEED = 26;
  private static final int SPRAY_ATTEMPTS_PER_SURFACE = 600;
  private static final int TRAIL_SAMPLES_PER_CELL = 3;
  private static final double MAX_SWEEP_STEP_ROWS = .5;
  private static final String RENDER_SEED_PROPERTY = "apotheneum.rockfall.seed";
  private static final double COLOR_BRIGHTNESS_MODULATION = .45;
  private static final double COLOR_SATURATION_MODULATION = .3;

  public final CompoundParameter rockSpacing =
    new CompoundParameter("Spacing", 1, .15, 3)
    .setDescription("Separation between rocks; lower packs them closer");

  public final CompoundParameter rockScale =
    new CompoundParameter("Rock Scale", DEFAULT_ROCK_SCALE, MIN_ROCK_SCALE, MAX_ROCK_SCALE)
    .setDescription("Scale multiplier applied to each rock's base radius");

  public final CompoundParameter craggedness =
    new CompoundParameter("Craggedness", .5)
    .setDescription("Rock shape from smooth to jagged");

  public final CompoundParameter variation =
    new CompoundParameter("Variation", 0)
    .setDescription("Cyclic phase that smoothly morphs the rock field");

  public final CompoundParameter rockSpeed =
    new CompoundParameter("Rock Speed", 0, -90, 90)
    .setDescription("Rock scroll velocity; negative values move rocks downward");

  public final TriggerParameter lurch =
    new TriggerParameter("Lurch", this::onLurch)
    .setDescription("Jolt rocks upward and shake water loose from their undersides");

  public final CompoundParameter gravity =
    new CompoundParameter("Gravity", 352, 1, 400)
    .setExponent(2)
    .setDescription("Free-fall acceleration of the water");

  public final CompoundParameter slideSpeed =
    new CompoundParameter("Slide Speed", 11, 2, 30)
    .setDescription("Water speed while sliding around a rock");

  public final DiscreteParameter waterDensity =
    new DiscreteParameter("Water Density", 100, 0, WATER_DENSITY_PARAMETER_MAX + 1)
    .setDescription("Water droplet density as a percentage of the base pools");

  public final CompoundParameter streak =
    new CompoundParameter("Streak", 220, 40, 500)
    .setDescription("Terminal falling speed and streak elongation");

  public final CompoundParameter spread =
    new CompoundParameter("Spread", .08, .01, 1)
    .setDescription("Lateral velocity retained each second in free fall");

  public final CompoundParameter cling =
    new CompoundParameter("Cling", .24, .02, 1)
    .setDescription("Water contact-band width relative to rock radius");

  public final CompoundParameter converge =
    new CompoundParameter("Converge", 80, 0, 300)
    .setDescription("Acceleration drawing detached water behind a rock");

  public final CompoundParameter convergeDistance =
    new CompoundParameter("Converge Dist", 1.75, 0, 5)
    .setDescription("Convergence duration in rock radii of fall");

  public final CompoundParameter waterLevel =
    new CompoundParameter("Water Level", .38)
    .setDescription("Brightness of water droplets");

  public final CompoundParameter rockLevel =
    new CompoundParameter("Rock Level", 0)
    .setDescription("Brightness within rock silhouettes");

  public final CompoundParameter rim =
    new CompoundParameter("Rim", .55)
    .setDescription("Brightness of rock edges");

  public final CompoundParameter rimWidth =
    new CompoundParameter("Rim Width", .9, .05, 3)
    .setDescription("Rock rim width in rows before rock scaling");

  public final class ColorGroup extends LXComponent implements LXOscComponent {

    public final CompoundParameter amount;
    private final int paletteStop;
    private int currentColor;
    private double currentAmount;

    private ColorGroup(LX lx, String label, int paletteStop, double amount) {
      super(lx, label);
      this.paletteStop = paletteStop;
      this.amount = new CompoundParameter("Amount", amount)
        .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);
      addParameter("amount", this.amount);
    }

    private void update() {
      this.currentColor = resolvedPaletteColor(this.paletteStop);
      this.currentAmount = this.amount.getValue();
    }

    private int color(double physics) {
      return modulatedColor(this.currentColor, this.currentAmount, physics);
    }
  }

  private static final class PaletteColorPreview extends UI2dComponent {

    private final Rockfall rockfall;
    private final int paletteStop;

    private PaletteColorPreview(Rockfall rockfall, int paletteStop) {
      super(0, 0, 40, 18);
      this.rockfall = rockfall;
      this.paletteStop = paletteStop;
      setDescription("Read-only project palette color");
    }

    @Override
    protected void onDraw(heronarts.glx.ui.UI ui, VGraphics vg) {
      vg.beginPath()
        .roundedRect(0, 0, this.width, this.height, 3)
        .fillColor(this.rockfall.resolvedPaletteColor(this.paletteStop))
        .fill();
    }
  }

  public final ColorGroup rockColor;
  public final ColorGroup waterColor;

  private static final class Rock {
    private final double[] centerS;
    private double theta;
    private double worldY;
    private double baseRadius;
    private double seed;
    private double centerX;
    private double centerZ;
    private double offsetS1;
    private double offsetY1;
    private double offsetR1;
    private double offsetS2;
    private double offsetY2;
    private double offsetR2;

    private Rock(int surfaceCount) {
      this.centerS = new double[surfaceCount];
    }
  }

  private static final class Droplet {
    private double s;
    private double h;
    private double previousS;
    private double previousH;
    private double vs;
    private double vh;
    private double bias;
    private boolean contactingRock;
    private double convergenceCenterS;
    private double convergenceRadiusRows;
    private double convergenceRemainingRows;
  }

  private static final class SurfaceWater {
    private final int index;
    private final Apotheneum.Orientation orientation;
    private final int baseCount;
    private Droplet[] droplets = new Droplet[0];
    private int activeCount = 0;

    private SurfaceWater(int index, Apotheneum.Orientation orientation, int baseCount) {
      this.index = index;
      this.orientation = orientation;
      this.baseCount = baseCount;
    }
  }

  private final Random random = createRandom();
  private Rock[] rocks = new Rock[0];
  private SurfaceWater[] surfaceWaters = new SurfaceWater[0];
  private int[][] rockIndicesByRow = new int[Apotheneum.GRID_HEIGHT][0];
  private final int[] rockCountByRow = new int[Apotheneum.GRID_HEIGHT];
  private double[] rockIntensity = new double[0];
  private double[] rockPhysics = new double[0];
  private double[] waterIntensity = new double[0];
  private double[] waterSpeedTotal = new double[0];
  private double[] waterSampleWeight = new double[0];
  private double[] sdfField = new double[0];
  private LXModel geometryModel;
  private double worldCenterX;
  private double worldCenterZ;
  private double worldYMin;
  private double worldYMax;
  private double worldRowHeight;
  private double rockRadiusMin;
  private double rockRadiusMax;
  private double rockCenterRadius;
  private int activeRockCount = 0;
  private double currentRockScale = DEFAULT_ROCK_SCALE;
  private double contactBand;
  private double currentRimWidth;
  private double coarseNoiseScale;
  private double fineNoiseScale;
  private double coarseNoiseAmount;
  private double fineNoiseAmount;
  private double lastVariationPhase = Double.NaN;
  private double lurchVelocity = 0;
  private boolean sprayPending = false;
  private final LXParameterListener rockGeometryListener =
    parameter -> updateActiveRockCount();
  private final LXParameterListener waterDensityListener =
    parameter -> setWaterDensity(this.waterDensity.getValuei());

  public Rockfall(LX lx) {
    super(lx);

    addChild("rock", this.rockColor = new ColorGroup(lx, "Rock", 0, .7));
    addChild("water", this.waterColor = new ColorGroup(lx, "Water", 1, .7));

    addParameter("rockSpacing", this.rockSpacing);
    addParameter("rockScale", this.rockScale);
    addParameter("craggedness", this.craggedness);
    addParameter("variation", this.variation);
    addParameter("rockSpeed", this.rockSpeed);
    addParameter("lurch", this.lurch);
    addParameter("gravity", this.gravity);
    addParameter("slideSpeed", this.slideSpeed);
    addParameter("waterDensity", this.waterDensity);
    addParameter("streak", this.streak);
    addParameter("spread", this.spread);
    addParameter("cling", this.cling);
    addParameter("converge", this.converge);
    addParameter("convergeDistance", this.convergeDistance);
    addParameter("waterLevel", this.waterLevel);
    addParameter("rockLevel", this.rockLevel);
    addParameter("rim", this.rim);
    addParameter("rimWidth", this.rimWidth);

    if (Apotheneum.exists) {
      initializeGeometry();
    }
    this.rockSpacing.addListener(this.rockGeometryListener);
    this.rockScale.addListener(this.rockGeometryListener);
    this.waterDensity.addListener(this.waterDensityListener);
  }

  private void initializeGeometry() {
    this.geometryModel = this.lx.getModel();
    this.rockIntensity = new double[this.geometryModel.size];
    this.rockPhysics = new double[this.geometryModel.size];
    this.waterIntensity = new double[this.geometryModel.size];
    this.waterSpeedTotal = new double[this.geometryModel.size];
    this.waterSampleWeight = new double[this.geometryModel.size];
    this.sdfField = new double[this.geometryModel.size];
    int surfaceCount = 0;
    for (Apotheneum.Orientation orientation : Apotheneum.cube.orientations()) {
      if (orientation != null) {
        ++surfaceCount;
      }
    }
    for (Apotheneum.Orientation orientation : Apotheneum.cylinder.orientations()) {
      if (orientation != null) {
        ++surfaceCount;
      }
    }
    this.surfaceWaters = new SurfaceWater[surfaceCount];
    int surfaceIndex = 0;
    for (Apotheneum.Orientation orientation : Apotheneum.cube.orientations()) {
      if (orientation != null) {
        this.surfaceWaters[surfaceIndex] =
          new SurfaceWater(surfaceIndex, orientation, BASE_CUBE_DROPLET_COUNT);
        ++surfaceIndex;
      }
    }
    for (Apotheneum.Orientation orientation : Apotheneum.cylinder.orientations()) {
      if (orientation != null) {
        this.surfaceWaters[surfaceIndex] =
          new SurfaceWater(surfaceIndex, orientation, BASE_CYLINDER_DROPLET_COUNT);
        ++surfaceIndex;
      }
    }

    double xMin = Double.POSITIVE_INFINITY;
    double xMax = Double.NEGATIVE_INFINITY;
    double zMin = Double.POSITIVE_INFINITY;
    double zMax = Double.NEGATIVE_INFINITY;
    double yMin = Double.POSITIVE_INFINITY;
    double yMax = Double.NEGATIVE_INFINITY;
    for (Apotheneum.Orientation orientation : Apotheneum.cube.orientations()) {
      if (orientation != null) {
        for (LXPoint point : orientation.ring(0).points) {
          xMin = Math.min(xMin, point.x);
          xMax = Math.max(xMax, point.x);
          zMin = Math.min(zMin, point.z);
          zMax = Math.max(zMax, point.z);
        }
      }
    }
    for (SurfaceWater surface : this.surfaceWaters) {
      for (Apotheneum.Column column : surface.orientation.columns()) {
        for (LXPoint point : column.points) {
          yMin = Math.min(yMin, point.y);
          yMax = Math.max(yMax, point.y);
        }
      }
    }
    this.worldCenterX = (xMin + xMax) * .5;
    this.worldCenterZ = (zMin + zMax) * .5;
    this.worldYMin = yMin;
    this.worldYMax = yMax;
    this.worldRowHeight = (this.worldYMax - this.worldYMin) / (Apotheneum.GRID_HEIGHT - 1);
    this.rockRadiusMin = this.worldRowHeight * ROCK_RADIUS_MIN_ROWS;
    this.rockRadiusMax = this.worldRowHeight * ROCK_RADIUS_MAX_ROWS;
    this.rockCenterRadius = calculateRockCenterRadius();

    this.rocks = new Rock[0];
    this.rockIndicesByRow = new int[Apotheneum.GRID_HEIGHT][0];
    this.activeRockCount = 0;
    this.lastVariationPhase = Double.NaN;
    updateDerivedValues();
    updateActiveRockCount();
    setWaterDensity(this.waterDensity.getValuei());
  }

  private static Random createRandom() {
    final String renderSeed = System.getProperty(RENDER_SEED_PROPERTY);
    return renderSeed == null ? new Random() : new Random(Long.parseLong(renderSeed));
  }

  @Override
  public void dispose() {
    this.rockSpacing.removeListener(this.rockGeometryListener);
    this.rockScale.removeListener(this.rockGeometryListener);
    this.waterDensity.removeListener(this.waterDensityListener);
    this.rockColor.dispose();
    this.waterColor.dispose();
    super.dispose();
  }

  private double calculateRockCenterRadius() {
    double surfaceRadiusSum = 0;
    for (SurfaceWater surface : this.surfaceWaters) {
      double radiusSum = 0;
      final LXPoint[] ring = surface.orientation.ring(0).points;
      for (LXPoint point : ring) {
        radiusSum += Math.hypot(point.x - this.worldCenterX, point.z - this.worldCenterZ);
      }
      surfaceRadiusSum += radiusSum / ring.length;
    }
    return surfaceRadiusSum / this.surfaceWaters.length;
  }

  private void updateDerivedValues() {
    this.currentRockScale = this.rockScale.getValue();
    this.contactBand =
      this.rockRadiusMin * this.currentRockScale * this.cling.getValue();
    this.currentRimWidth =
      this.worldRowHeight * this.currentRockScale * this.rimWidth.getValue();
    final double craggedness = this.craggedness.getValue();
    final double coarseNoiseRows = LXUtils.lerp(
      COARSE_NOISE_ROWS_SMOOTH,
      COARSE_NOISE_ROWS_JAGGED,
      craggedness
    );
    final double fineNoiseRows = LXUtils.lerp(
      FINE_NOISE_ROWS_SMOOTH,
      FINE_NOISE_ROWS_JAGGED,
      craggedness
    );
    this.coarseNoiseScale = 1 / (this.worldRowHeight * this.currentRockScale * coarseNoiseRows);
    this.fineNoiseScale = 1 / (this.worldRowHeight * this.currentRockScale * fineNoiseRows);
    this.coarseNoiseAmount = COARSE_NOISE_AMOUNT_MAX * craggedness;
    this.fineNoiseAmount = FINE_NOISE_AMOUNT_MAX * craggedness;
  }

  private double effectiveRadius(Rock rock) {
    return rock.baseRadius * this.currentRockScale;
  }

  static int derivedRockCount(
    double scale,
    double spacing,
    int hardCap,
    double coverageBudget,
    double rowWorkBudget
  ) {
    final double spacingCount = ROCK_SPACING_CONSTANT / (spacing * spacing);
    final double coverageCount = coverageBudget / (scale * scale);
    // Sampling cost follows candidates per row, approximately count * scale,
    // while visual coverage follows count * scale^2. Keep these as separate
    // budgets so large rocks are not starved to protect the tiny-rock regime.
    final double rowWorkCount = rowWorkBudget / scale;
    return LXUtils.constrain(
      (int) Math.round(Math.min(spacingCount, Math.min(coverageCount, rowWorkCount))),
      0,
      hardCap
    );
  }

  private void updateActiveRockCount() {
    if (!Apotheneum.exists || this.geometryModel != this.lx.getModel()) {
      return;
    }
    updateDerivedValues();
    setActiveRockCount(derivedRockCount(
      this.rockScale.getValue(),
      this.rockSpacing.getValue(),
      ROCK_HARD_CAP,
      ROCK_COVERAGE_BUDGET,
      ROCK_ROW_WORK_BUDGET
    ));
  }

  private double verticalExtent(Rock rock) {
    return effectiveRadius(rock) * 1.5 + Math.max(this.contactBand, this.currentRimWidth);
  }

  private void setActiveRockCount(int requestedCount) {
    ensureRockCapacity(requestedCount);
    for (int i = this.activeRockCount; i < requestedCount; ++i) {
      activateRock(this.rocks[i], i, verticalPosition(i));
    }
    this.activeRockCount = requestedCount;
  }

  /**
   * Vertical placement from a golden-ratio additive sequence. Every prefix of this
   * sequence is evenly distributed, so shrinking the field keeps a well-spread subset
   * rather than collapsing onto whichever rocks hold the low indices. A count-dependent
   * stratification cannot do this: positions of {@code (i + r) / N} leave the first
   * {@code M} rocks bunched into the lowest {@code M/N} of the world once the count
   * drops to {@code M}.
   */
  static double verticalPosition(int rockIndex) {
    final double position = (rockIndex + 1) * GOLDEN_RATIO_CONJUGATE;
    return position - Math.floor(position);
  }

  private void ensureRockCapacity(int requestedCount) {
    if (requestedCount <= this.rocks.length) {
      return;
    }
    final int oldCapacity = this.rocks.length;
    final int newCapacity = Math.max(requestedCount, Math.max(16, oldCapacity * 2));
    this.rocks = Arrays.copyOf(this.rocks, newCapacity);
    for (int i = oldCapacity; i < newCapacity; ++i) {
      this.rocks[i] = new Rock(this.surfaceWaters.length);
    }
    this.rockIndicesByRow = new int[Apotheneum.GRID_HEIGHT][newCapacity];
  }

  private void setWaterDensity(int densityPercent) {
    if (!Apotheneum.exists || this.geometryModel != this.lx.getModel()) {
      return;
    }
    for (SurfaceWater surface : this.surfaceWaters) {
      final int requestedCount = (int) Math.round(surface.baseCount * densityPercent * .01);
      ensureDropletCapacity(surface, requestedCount);
      if (requestedCount > surface.activeCount) {
        final int height = surface.orientation.height();
        for (int i = surface.activeCount; i < requestedCount; ++i) {
          spawnDroplet(surface, surface.droplets[i], this.random.nextDouble() * height);
        }
      }
      surface.activeCount = requestedCount;
    }
  }

  private void ensureDropletCapacity(SurfaceWater surface, int requestedCount) {
    if (requestedCount <= surface.droplets.length) {
      return;
    }
    final int oldCapacity = surface.droplets.length;
    final int newCapacity = Math.max(requestedCount, Math.max(64, oldCapacity * 2));
    surface.droplets = Arrays.copyOf(surface.droplets, newCapacity);
    for (int i = oldCapacity; i < newCapacity; ++i) {
      surface.droplets[i] = new Droplet();
    }
  }

  private void activateRock(Rock rock, int rockIndex, double verticalPosition) {
    deriveRockProperties(rock, rockIndex, normalizedVariationPhase(this.variation.getValue()));
    final double radius = effectiveRadius(rock);
    rock.worldY = LXUtils.lerp(
      this.worldYMin - radius,
      this.worldYMax + radius,
      verticalPosition
    );
  }

  private void updateRockProperties() {
    final double phase = normalizedVariationPhase(this.variation.getValue());
    if (phase == this.lastVariationPhase) {
      return;
    }
    for (int rockIndex = 0; rockIndex < this.activeRockCount; ++rockIndex) {
      deriveRockProperties(this.rocks[rockIndex], rockIndex, phase);
    }
    this.lastVariationPhase = phase;
  }

  private void deriveRockProperties(Rock rock, int rockIndex, double phase) {
    final double angle = TWO_PI * phase;
    final double loopX = Math.cos(angle) * VARIATION_LOOP_RADIUS;
    final double loopY = Math.sin(angle) * VARIATION_LOOP_RADIUS;
    rock.theta = TWO_PI * variationNoise(loopX, loopY, rockIndex, .13, 7.1);
    rock.baseRadius = LXUtils.lerp(
      this.rockRadiusMin,
      this.rockRadiusMax,
      variationNoise(loopX, loopY, rockIndex, .47, 17.3)
    );
    rock.seed = rockIndex * 7.37 + 2.5 * variationNoise(loopX, loopY, rockIndex, .83, 29.7);
    rock.centerX = this.worldCenterX + this.rockCenterRadius * Math.cos(rock.theta);
    rock.centerZ = this.worldCenterZ + this.rockCenterRadius * Math.sin(rock.theta);
    for (SurfaceWater surface : this.surfaceWaters) {
      rock.centerS[surface.index] = projectRockCenterS(surface.orientation, rock);
    }
    rock.offsetS1 = LXUtils.lerp(-.31, .31,
      variationNoise(loopX, loopY, rockIndex, 1.19, 41.9)) * rock.baseRadius;
    rock.offsetY1 = LXUtils.lerp(-.26, .26,
      variationNoise(loopX, loopY, rockIndex, 1.61, 53.3)) * rock.baseRadius;
    rock.offsetR1 = LXUtils.lerp(.72, .84,
      variationNoise(loopX, loopY, rockIndex, 2.03, 67.7)) * rock.baseRadius;
    rock.offsetS2 = LXUtils.lerp(-.32, .32,
      variationNoise(loopX, loopY, rockIndex, 2.41, 79.1)) * rock.baseRadius;
    rock.offsetY2 = LXUtils.lerp(-.32, .32,
      variationNoise(loopX, loopY, rockIndex, 2.89, 97.3)) * rock.baseRadius;
    rock.offsetR2 = LXUtils.lerp(.62, .78,
      variationNoise(loopX, loopY, rockIndex, 3.31, 109.7)) * rock.baseRadius;
  }

  static double normalizedVariationPhase(double phase) {
    return phase - Math.floor(phase);
  }

  static double variationNoise(double phase, int rockIndex, double channelOffset, double z) {
    final double normalizedPhase = normalizedVariationPhase(phase);
    final double angle = TWO_PI * normalizedPhase;
    return variationNoise(
      Math.cos(angle) * VARIATION_LOOP_RADIUS,
      Math.sin(angle) * VARIATION_LOOP_RADIUS,
      rockIndex,
      channelOffset,
      z
    );
  }

  private static double variationNoise(
    double loopX,
    double loopY,
    int rockIndex,
    double channelOffset,
    double z
  ) {
    return LXUtils.noise(
      (float) (loopX + VARIATION_INDEX_SPACING * rockIndex + channelOffset),
      (float) loopY,
      (float) z
    );
  }

  private void onLurch() {
    this.lurchVelocity += LURCH_VELOCITY;
    this.sprayPending = true;
  }

  @Override
  protected void render(double deltaMs) {
    if (this.geometryModel != this.lx.getModel()) {
      initializeGeometry();
    }
    final double dt = Math.min(MAX_DELTA_SECONDS, deltaMs * .001);
    updateDerivedValues();
    updateRockProperties();
    this.rockColor.update();
    this.waterColor.update();
    Arrays.fill(this.rockIntensity, 0);
    Arrays.fill(this.waterIntensity, 0);
    Arrays.fill(this.waterSpeedTotal, 0);
    Arrays.fill(this.waterSampleWeight, 0);
    setApotheneumColor(LXColor.BLACK);

    updateRocks(dt);
    rebuildRockRowIndex();
    sampleSdfField();
    if (this.sprayPending) {
      for (SurfaceWater surface : this.surfaceWaters) {
        sprayFromUndersides(surface);
      }
      this.sprayPending = false;
    }
    for (SurfaceWater surface : this.surfaceWaters) {
      updateWater(surface, dt);
    }
    for (SurfaceWater surface : this.surfaceWaters) {
      renderRocks(surface.orientation);
      writeColorOutput(surface.orientation);
    }
  }

  private void rebuildRockRowIndex() {
    Arrays.fill(this.rockCountByRow, 0);
    for (int rockIndex = 0; rockIndex < this.activeRockCount; ++rockIndex) {
      final Rock rock = this.rocks[rockIndex];
      final double extent = verticalExtent(rock);
      final int firstRow = Math.max(0, worldRow(rock.worldY + extent));
      final int lastRow = Math.min(Apotheneum.GRID_HEIGHT - 1, worldRow(rock.worldY - extent));
      for (int row = firstRow; row <= lastRow; ++row) {
        this.rockIndicesByRow[row][this.rockCountByRow[row]++] = rockIndex;
      }
    }
  }

  private int worldRow(double worldY) {
    return (int) Math.round((this.worldYMax - worldY) / this.worldRowHeight);
  }

  private void sampleSdfField() {
    for (SurfaceWater surface : this.surfaceWaters) {
      final Apotheneum.Orientation orientation = surface.orientation;
      for (int x = 0; x < orientation.width(); ++x) {
        for (int y = 0; y < orientation.height(); ++y) {
          final LXPoint point = orientation.point(x, y);
          this.sdfField[point.index] = sdf(surface, x, point.y);
        }
      }
    }
  }

  private void updateRocks(double dt) {
    final double velocity = this.rockSpeed.getValue() + this.lurchVelocity;
    this.lurchVelocity *= Math.exp(-LURCH_DECAY_PER_SECOND * dt);
    if (Math.abs(this.lurchVelocity) < .01) {
      this.lurchVelocity = 0;
    }
    for (int i = 0; i < this.activeRockCount; ++i) {
      final Rock rock = this.rocks[i];
      rock.worldY += velocity * dt;
      final double extent = verticalExtent(rock);
      if (velocity > 0 && rock.worldY - extent > this.worldYMax) {
        rock.worldY = this.worldYMin - extent;
      } else if (velocity < 0 && rock.worldY + extent < this.worldYMin) {
        rock.worldY = this.worldYMax + extent;
      }
    }
  }

  private void updateWater(SurfaceWater surface, double dt) {
    final double damping = Math.pow(this.spread.getValue(), dt);
    final double acceleration = this.gravity.getValue();
    final double slide = this.slideSpeed.getValue();
    final double freeFallLimit = this.streak.getValue();
    final double maxLateralSpeed = maximumLateralSpeed(
      slide,
      freeFallLimit,
      acceleration
    );
    for (int dropletIndex = 0; dropletIndex < surface.activeCount; ++dropletIndex) {
      final Droplet droplet = surface.droplets[dropletIndex];
      droplet.previousS = droplet.s;
      droplet.previousH = droplet.h;

      final double d = surfaceSdf(surface.orientation, droplet.s, droplet.h);
      if (d < this.contactBand) {
        final Rock contactRock = closestRock(surface, droplet.s, droplet.h);
        if (contactRock != null) {
          droplet.contactingRock = true;
          droplet.convergenceCenterS = contactRock.centerS[surface.index];
          droplet.convergenceRadiusRows = effectiveRadius(contactRock) / this.worldRowHeight;
          droplet.convergenceRemainingRows = 0;
        }
        final double gradS = .5 * (
          surfaceSdf(surface.orientation, droplet.s + 1, droplet.h) -
          surfaceSdf(surface.orientation, droplet.s - 1, droplet.h)
        );
        final double gradH = .5 * (
          surfaceSdf(surface.orientation, droplet.s, droplet.h + 1) -
          surfaceSdf(surface.orientation, droplet.s, droplet.h - 1)
        );
        final double gradientMagnitude = Math.hypot(gradS, gradH);
        if (gradientMagnitude > GRADIENT_EPSILON) {
          final double normalS = gradS / gradientMagnitude;
          final double normalH = gradH / gradientMagnitude;
          double tangentS = -normalS * normalH;
          double tangentH = 1 - normalH * normalH;
          final double tangentMagnitude = Math.hypot(tangentS, tangentH);
          if (tangentMagnitude < CROWN_EPSILON) {
            tangentS = fallbackTangentS(normalH, droplet.bias);
            tangentH = fallbackTangentH(normalH);
          }
          final double normalizedTangentMagnitude = Math.hypot(tangentS, tangentH);
          final double contactVs = slide * tangentS / normalizedTangentMagnitude;
          final double freeFallVh = Math.min(
            freeFallLimit,
            droplet.vh + acceleration * dt
          );
          final double contactVh = normalH > 0 ?
            freeFallVh : slide * tangentH / normalizedTangentMagnitude;
          droplet.vs = contactVs;
          droplet.vh = contactVh;
          if (d < 0) {
            final double penetration = -d / gradientMagnitude;
            droplet.s += normalS * penetration;
            droplet.h += normalH * penetration;
          }
        } else {
          droplet.vs = slide * droplet.bias;
          droplet.vh = 0;
        }
      } else {
        if (droplet.contactingRock) {
          droplet.contactingRock = false;
          droplet.convergenceRemainingRows =
            this.convergeDistance.getValue() * droplet.convergenceRadiusRows;
        }
        if (droplet.convergenceRemainingRows > 0) {
          final double delta = wrappedDelta(
            droplet.s,
            droplet.convergenceCenterS,
            surface.orientation.width()
          );
          if (Math.abs(delta) > CONVERGENCE_SNAP_EPSILON) {
            droplet.vs += this.converge.getValue() * Math.signum(delta) * dt;
          } else {
            droplet.convergenceRemainingRows = 0;
          }
        }
        droplet.vh = Math.min(freeFallLimit, droplet.vh + acceleration * dt);
        droplet.vs *= damping;
      }

      droplet.vs = LXUtils.clamp(droplet.vs, -maxLateralSpeed, maxLateralSpeed);

      final double ds = droplet.vs * dt;
      final double dh = droplet.vh * dt;
      final double contactAmount = d < this.contactBand ? -1 : firstSweptContact(
        surface.orientation,
        droplet.s,
        droplet.h,
        ds,
        dh
      );
      final double movementAmount = contactAmount < 0 ? 1 : contactAmount;
      droplet.s = wrap(droplet.s + ds * movementAmount, surface.orientation.width());
      droplet.h += dh * movementAmount;
      droplet.convergenceRemainingRows = Math.max(
        0,
        droplet.convergenceRemainingRows - Math.max(0, droplet.h - droplet.previousH)
      );
      final int column = wrappedColumn(droplet.s, surface.orientation.width());
      if (droplet.h >= surface.orientation.available(column) || droplet.h < -3) {
        spawnDroplet(surface, droplet, -this.random.nextDouble() * 5);
      } else {
        renderDropletTrail(surface, droplet);
      }
    }
  }

  static double fallbackTangentS(double normalH, double bias) {
    return normalH < 0 ? bias : 0;
  }

  static double fallbackTangentH(double normalH) {
    return normalH < 0 ? .12 : 1;
  }

  static double maximumLateralSpeed(
    double slide,
    double freeFallLimit,
    double acceleration
  ) {
    final double achievable = Math.min(
      freeFallLimit,
      Math.sqrt(2 * acceleration * Apotheneum.GRID_HEIGHT)
    );
    return Math.min(
      MAX_LATERAL_SLIDE_MULTIPLIER * slide,
      MAX_LATERAL_FREE_FALL_FRACTION * achievable
    );
  }

  private double firstSweptContact(
    Apotheneum.Orientation orientation,
    double s,
    double h,
    double ds,
    double dh
  ) {
    final int samples = sweepSampleCount(ds, dh);
    for (int sample = 1; sample <= samples; ++sample) {
      final double amount = (double) sample / samples;
      if (surfaceSdf(orientation, s + ds * amount, h + dh * amount) < this.contactBand) {
        return amount;
      }
    }
    return -1;
  }

  static int sweepSampleCount(double ds, double dh) {
    return Math.max(1, (int) Math.ceil(Math.hypot(ds, dh) / MAX_SWEEP_STEP_ROWS));
  }



  private void spawnDroplet(SurfaceWater surface, Droplet droplet, double height) {
    droplet.s = this.random.nextDouble() * surface.orientation.width();
    droplet.h = height;
    droplet.previousS = droplet.s;
    droplet.previousH = droplet.h;
    droplet.vs = 0;
    droplet.vh = 5 + 8 * this.random.nextDouble();
    droplet.bias = this.random.nextBoolean() ? 1 : -1;
    droplet.contactingRock = false;
    droplet.convergenceRemainingRows = 0;
  }

  private void sprayFromUndersides(SurfaceWater surface) {
    int dropletIndex = 0;
    for (int attempt = 0;
      attempt < SPRAY_ATTEMPTS_PER_SURFACE && dropletIndex < surface.activeCount / 3;
      ++attempt) {
      final double s = this.random.nextDouble() * surface.orientation.width();
      final double h = this.random.nextDouble() * surface.orientation.height();
      final int column = wrappedColumn(s, surface.orientation.width());
      if (h >= surface.orientation.available(column)) {
        continue;
      }
      final double d = surfaceSdf(surface.orientation, s, h);
      if (Math.abs(d) > this.contactBand) {
        continue;
      }
      final double gradS = .5 * (
        surfaceSdf(surface.orientation, s + 1, h) -
        surfaceSdf(surface.orientation, s - 1, h)
      );
      final double gradH = .5 * (
        surfaceSdf(surface.orientation, s, h + 1) -
        surfaceSdf(surface.orientation, s, h - 1)
      );
      final double gradientMagnitude = Math.hypot(gradS, gradH);
      if (gradientMagnitude < GRADIENT_EPSILON) {
        continue;
      }
      final double normalS = gradS / gradientMagnitude;
      final double normalH = gradH / gradientMagnitude;
      final double downwardTangent = 1 - normalH * normalH;
      if (normalH <= .25 || downwardTangent <= .15) {
        continue;
      }
      final Droplet droplet = surface.droplets[dropletIndex++];
      final double clearance = (this.contactBand + this.worldRowHeight * .3 - d) / gradientMagnitude;
      droplet.s = wrap(s + normalS * clearance, surface.orientation.width());
      droplet.h = h + normalH * clearance;
      droplet.previousS = droplet.s;
      droplet.previousH = droplet.h;
      droplet.bias = this.random.nextBoolean() ? 1 : -1;
      droplet.vs = droplet.bias * (8 + 10 * this.random.nextDouble());
      droplet.vh = SPRAY_SPEED * (.7 + .6 * this.random.nextDouble());
      droplet.contactingRock = false;
      droplet.convergenceRemainingRows = 0;
    }
  }

  private void renderDropletTrail(SurfaceWater surface, Droplet droplet) {
    double ds = droplet.s - droplet.previousS;
    final double halfWidth = surface.orientation.width() * .5;
    if (ds > halfWidth) {
      ds -= surface.orientation.width();
    } else if (ds < -halfWidth) {
      ds += surface.orientation.width();
    }
    final double dh = droplet.h - droplet.previousH;
    final double speed = Math.hypot(droplet.vs, droplet.vh);
    final double normalizedSpeed = Math.min(1, speed / this.streak.getValue());
    final int samples = Math.max(2, (int) Math.ceil(Math.hypot(ds, dh) * TRAIL_SAMPLES_PER_CELL));
    final double beadBrightness = this.waterLevel.getValue() * LXUtils.lerp(
      .95,
      .34,
      normalizedSpeed
    );
    for (int i = 0; i < samples; ++i) {
      final double amount = (double) i / (samples - 1);
      final double brightness = beadBrightness * (.18 + .82 * amount);
      addWaterSample(
        surface.orientation,
        droplet.previousS + ds * amount,
        droplet.previousH + dh * amount,
        brightness,
        normalizedSpeed
      );
    }
  }

  private void addWaterSample(
    Apotheneum.Orientation orientation,
    double s,
    double h,
    double brightness,
    double normalizedSpeed
  ) {
    final int x0 = (int) Math.floor(s);
    final int y0 = (int) Math.floor(h);
    final double fractionX = s - x0;
    final double fractionY = h - y0;
    addWaterPixel(orientation, x0, y0,
      brightness * (1 - fractionX) * (1 - fractionY), normalizedSpeed);
    addWaterPixel(orientation, x0 + 1, y0,
      brightness * fractionX * (1 - fractionY), normalizedSpeed);
    addWaterPixel(orientation, x0, y0 + 1,
      brightness * (1 - fractionX) * fractionY, normalizedSpeed);
    addWaterPixel(orientation, x0 + 1, y0 + 1,
      brightness * fractionX * fractionY, normalizedSpeed);
  }

  private void addWaterPixel(
    Apotheneum.Orientation orientation,
    int x,
    int y,
    double brightness,
    double normalizedSpeed
  ) {
    final int column = wrappedColumn(x, orientation.width());
    if (brightness <= 0 || y < 0 || y >= orientation.available(column)) {
      return;
    }
    final int index = orientation.point(column, y).index;
    this.waterIntensity[index] = Math.min(1, this.waterIntensity[index] + brightness);
    this.waterSpeedTotal[index] += brightness * normalizedSpeed;
    this.waterSampleWeight[index] += brightness;
  }

  private void renderRocks(Apotheneum.Orientation orientation) {
    final double rockBrightness = this.rockLevel.getValue();
    final double rimBrightness = this.rim.getValue();
    for (int x = 0; x < orientation.width(); ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        final LXPoint point = orientation.point(x, y);
        final double d = this.sdfField[point.index];
        double intensity = 0;
        if (d < 0) {
          intensity = rockBrightness;
        }
        final double rimDistance = Math.abs(d);
        if (rimDistance < this.currentRimWidth) {
          intensity = Math.max(
            intensity,
            rimBrightness * (1 - rimDistance / this.currentRimWidth)
          );
        }
        this.rockIntensity[point.index] = intensity;
        if (d <= 0) {
          final double normalizedDepth = LXUtils.clamp(
            -d / (this.rockRadiusMax * this.currentRockScale),
            0,
            1
          );
          this.rockPhysics[point.index] = 1 - 2 * normalizedDepth;
        } else {
          this.rockPhysics[point.index] = 1;
        }
      }
    }
  }

  private void writeColorOutput(Apotheneum.Orientation orientation) {
    for (int x = 0; x < orientation.width(); ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        final int index = orientation.point(x, y).index;
        final double rock = this.rockIntensity[index];
        final double water = this.waterIntensity[index];
        if (water <= 0) {
          colors[index] = rock <= 0 ? LXColor.BLACK : LXColor.scaleBrightness(
            this.rockColor.color(this.rockPhysics[index]),
            rock
          );
          continue;
        }
        final double speed = this.waterSampleWeight[index] > 0 ?
          this.waterSpeedTotal[index] / this.waterSampleWeight[index] : 0;
        colors[index] = compositeColors(
          rock <= 0 ? LXColor.BLACK : this.rockColor.color(this.rockPhysics[index]),
          rock,
          this.waterColor.color(speed * 2 - 1),
          water
        );
      }
    }
  }

  private int resolvedPaletteColor(int stop) {
    return paletteColor(this.lx.engine.palette.swatch.colors, stop);
  }

  static int paletteColor(List<LXDynamicColor> colors, int stop) {
    if (colors.isEmpty()) {
      return LXColor.WHITE;
    }
    return colors.get(Math.min(stop, colors.size() - 1)).getColor();
  }

  static int modulatedColor(int color, double amount, double physics) {
    if (amount == 0) {
      return color;
    }
    final double modulation = LXUtils.clamp(amount, 0, 1) * LXUtils.clamp(physics, -1, 1);
    final double shiftedSaturation = LXUtils.clamp(
      LXColor.s(color) * (1 - COLOR_SATURATION_MODULATION * modulation),
      0,
      100
    );
    final double shiftedBrightness = LXUtils.clamp(
      LXColor.b(color) * (1 + COLOR_BRIGHTNESS_MODULATION * modulation),
      0,
      100
    );
    return LXColor.hsb(LXColor.h(color), shiftedSaturation, shiftedBrightness);
  }

  static int compositeColors(
    int rockColor,
    double rockIntensity,
    int waterColor,
    double waterIntensity
  ) {
    final int litRock = LXColor.scaleBrightness(
      rockColor,
      LXUtils.clamp(rockIntensity, 0, 1)
    );
    return waterIntensity <= 0 ? litRock : LXColor.lerp(
      litRock,
      waterColor,
      LXUtils.clamp(waterIntensity, 0, 1)
    );
  }

  private double surfaceSdf(Apotheneum.Orientation orientation, double s, double h) {
    final int x = wrappedColumn(s, orientation.width());
    final int y = Math.max(0, Math.min(orientation.height() - 1, (int) Math.round(h)));
    final LXPoint point = orientation.point(x, y);
    return this.sdfField[point.index];
  }

  private Rock closestRock(SurfaceWater surface, double s, double h) {
    final Apotheneum.Orientation orientation = surface.orientation;
    final int x = wrappedColumn(s, orientation.width());
    final int y = Math.max(0, Math.min(orientation.height() - 1, (int) Math.round(h)));
    final LXPoint point = orientation.point(x, y);
    Rock closest = null;
    double closestDistance = Double.POSITIVE_INFINITY;
    final int row = Math.max(0, Math.min(Apotheneum.GRID_HEIGHT - 1, worldRow(point.y)));
    final int candidateCount = this.rockCountByRow[row];
    for (int i = 0; i < candidateCount; ++i) {
      final Rock rock = this.rocks[this.rockIndicesByRow[row][i]];
      final double distance = rockSdf(rock, surface, x, point.y);
      if (distance < closestDistance) {
        closest = rock;
        closestDistance = distance;
      }
    }
    return closest;
  }

  private double projectRockCenterS(Apotheneum.Orientation orientation, Rock rock) {
    int closestColumn = 0;
    double closestDistanceSquared = Double.POSITIVE_INFINITY;
    for (int x = 0; x < orientation.width(); ++x) {
      final LXPoint point = orientation.point(x, 0);
      final double dx = point.x - rock.centerX;
      final double dz = point.z - rock.centerZ;
      final double distanceSquared = dx * dx + dz * dz;
      if (distanceSquared < closestDistanceSquared) {
        closestColumn = x;
        closestDistanceSquared = distanceSquared;
      }
    }
    return closestColumn;
  }

  private double sdf(SurfaceWater surface, double s, double y) {
    double field = Double.POSITIVE_INFINITY;
    final int row = Math.max(0, Math.min(Apotheneum.GRID_HEIGHT - 1, worldRow(y)));
    final int candidateCount = this.rockCountByRow[row];
    for (int i = 0; i < candidateCount; ++i) {
      final Rock rock = this.rocks[this.rockIndicesByRow[row][i]];
      field = Math.min(field, rockSdf(rock, surface, s, y));
    }
    return field;
  }

  private double rockSdf(Rock rock, SurfaceWater surface, double s, double y) {
    // Evaluate in tangent/height space rather than at one physical radius between
    // the nested chambers. centerS preserves the shared world-space angle, while
    // worldRowHeight makes a rock the same apparent row size on every surface.
    final double scale = this.currentRockScale;
    final double radius = rock.baseRadius * scale;
    final double maximumExtent = radius * 1.5;
    final double dy = y - rock.worldY;
    if (Math.abs(dy) > maximumExtent + this.contactBand) {
      return Double.POSITIVE_INFINITY;
    }
    final double dx = wrappedDelta(
      rock.centerS[surface.index],
      s,
      surface.orientation.width()
    ) * this.worldRowHeight;
    final double rejectRadius = maximumExtent + this.contactBand;
    if (dx * dx + dy * dy > rejectRadius * rejectRadius) {
      return Double.POSITIVE_INFINITY;
    }

    double rockField = sphereSdf(dx, dy, 0, radius);
    final double dx1 = dx - rock.offsetS1 * scale;
    rockField = smoothMin(
      rockField,
      sphereSdf(dx1, dy - rock.offsetY1 * scale, 0, rock.offsetR1 * scale),
      radius * SMOOTH_MIN_FACTOR
    );
    final double dx2 = dx - rock.offsetS2 * scale - radius * .08;
    rockField = smoothMin(
      rockField,
      sphereSdf(dx2, dy - rock.offsetY2 * scale, 0, rock.offsetR2 * scale),
      radius * SMOOTH_MIN_FACTOR
    );
    final double coarseNoise = LXUtils.noise(
      (float) (dx * this.coarseNoiseScale + rock.seed),
      (float) (dy * this.coarseNoiseScale - rock.seed * .37),
      (float) (rock.seed * .61)
    );
    final double fineNoise = LXUtils.noise(
      (float) (dx * this.fineNoiseScale - rock.seed * .43),
      (float) (dy * this.fineNoiseScale + rock.seed * .71),
      (float) (-rock.seed * .29)
    );
    rockField -= (coarseNoise - .5) * 2 * radius * this.coarseNoiseAmount;
    rockField -= (fineNoise - .5) * 2 * radius * this.fineNoiseAmount;
    return rockField;
  }

  private static double sphereSdf(double x, double y, double z, double radius) {
    return Math.sqrt(x * x + y * y + z * z) - radius;
  }

  private static double smoothMin(double a, double b, double amount) {
    final double h = Math.max(amount - Math.abs(a - b), 0) / amount;
    return Math.min(a, b) - h * h * amount * .25;
  }

  private static int wrappedColumn(double s, int width) {
    int column = (int) Math.round(s) % width;
    if (column < 0) {
      column += width;
    }
    return column;
  }

  private static double wrap(double value, int width) {
    value %= width;
    return value < 0 ? value + width : value;
  }

  private static double wrappedDelta(double from, double to, int width) {
    double delta = to - from;
    final double halfWidth = width * .5;
    if (delta > halfWidth) {
      delta -= width;
    } else if (delta < -halfWidth) {
      delta += width;
    }
    return delta;
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Rockfall rockfall) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Rock Geometry",
      newKnob(rockfall.rockSpacing),
      newKnob(rockfall.rockScale),
      newKnob(rockfall.craggedness)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Rock Motion",
      newKnob(rockfall.rockSpeed),
      newKnob(rockfall.variation),
      newButton(rockfall.lurch).setTriggerable(true).setBorderRounding(4)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Rock Color",
      new PaletteColorPreview(rockfall, 0),
      newKnob(rockfall.rockColor.amount),
      newKnob(rockfall.rimWidth)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Water Behavior",
      newKnob(rockfall.waterDensity),
      newKnob(rockfall.streak),
      newKnob(rockfall.spread)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Flow",
      newKnob(rockfall.gravity),
      newKnob(rockfall.slideSpeed),
      newKnob(rockfall.cling)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Contact",
      newKnob(rockfall.converge),
      newKnob(rockfall.convergeDistance)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Water Color",
      new PaletteColorPreview(rockfall, 1),
      newKnob(rockfall.waterColor.amount)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Levels",
      newKnob(rockfall.waterLevel),
      newKnob(rockfall.rockLevel),
      newKnob(rockfall.rim)
    ).setChildSpacing(6);
  }
}
