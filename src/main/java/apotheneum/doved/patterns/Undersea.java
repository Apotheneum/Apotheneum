package apotheneum.doved.patterns;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

/**
 * An immersive underwater bed: depth colour, caustics, rays, bubble plumes,
 * and a performable creature shadow. Flood remains responsible for drawing
 * the water surface itself.
 *
 * <p>The Level macro deliberately is not a direct water height. From 0 through
 * {@link #VISIBLE_WATERLINE_START}, the installation is fully submerged and
 * Level changes implied depth. Only the final part of the range lowers a
 * waterline into view for transitions to or from Flood.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Undersea")
@LXComponent.Description("Underwater depth, caustics, god rays, bubbles, and a creature shadow")
public class Undersea extends ApotheneumPattern {

  static final double VISIBLE_WATERLINE_START = .82;
  static final double LOWEST_WATER_LEVEL = .62;

  private static final int FEATURE_COLUMNS = 14;
  private static final int FEATURE_Y_MIN = -2;
  private static final int FEATURE_Y_COUNT = 10;
  private static final int FEATURE_COUNT = FEATURE_COLUMNS * FEATURE_Y_COUNT;
  private static final double CELL_HEIGHT_ROWS = 10.5;
  private static final double FEATURE_ORBIT_RADIUS = .17;

  private static final int RAY_COUNT = 4;
  private static final double[] RAY_BASE_S = { .04, .28, .57, .82 };
  private static final double[] RAY_PHASE = { .2, 1.9, 3.8, 5.1 };

  private static final int MAX_BUBBLES = 72;
  private static final int INITIAL_BUBBLES = 18;
  private static final double[] PLUME_S = { .09, .43, .76 };
  private static final int POP_FRAME_COUNT = 2;

  private static final double SHADOW_DURATION_SECONDS = 7.5;
  private static final double SHADOW_TRAVEL = .78;

  private static final int CAUSTIC_COLOR = LXColor.lerp(
    OceanField.SURFACE_COLOR,
    OceanField.MENISCUS_COLOR,
    .72f
  );
  private static final int BUBBLE_COLOR = LXColor.lerp(
    OceanField.SURFACE_COLOR,
    LXColor.WHITE,
    .76f
  );

  public final CompoundParameter level =
    new CompoundParameter("Level", .35, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription(
      "Implied depth while submerged; the final 18% lowers a transitional waterline into view"
    );

  public final CompoundParameter causticSpeed =
    new CompoundParameter("C Speed", .16, .03, .6)
    .setDescription("Speed of the slowly deforming caustic web");

  public final CompoundParameter causticDepth =
    new CompoundParameter("C Depth", .58, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How far caustic light reaches below the implied surface");

  public final CompoundParameter bubbleRate =
    new CompoundParameter("Bubbles", .38, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Bubble emission rate from the three fixed plumes");

  public final CompoundParameter rayIntensity =
    new CompoundParameter("Rays", .36, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Brightness of the drifting overhead god rays");

  public final TriggerParameter shadow =
    new TriggerParameter("Shadow", this::triggerShadow)
    .setDescription("Send a large creature shadow gliding across the caustics");

  private final OceanField.GeometryCache geometry = new OceanField.GeometryCache();

  private final double[] featureBaseX = new double[FEATURE_COUNT];
  private final double[] featureBaseY = new double[FEATURE_COUNT];
  private final double[] featurePhase = new double[FEATURE_COUNT];
  private final double[] featureRate = new double[FEATURE_COUNT];
  private final double[] featureX = new double[FEATURE_COUNT];
  private final double[] featureY = new double[FEATURE_COUNT];
  private final double[] previousRidgeGap;

  private final double[] rayCenter = new double[RAY_COUNT];
  private final double[] rayStrength = new double[RAY_COUNT];

  private final Bubble[] bubbles = new Bubble[MAX_BUBBLES];
  private double bubbleAccumulator;
  private int nextPlume;
  private int randomState = 0x41c64e6d;

  private double elapsedSeconds;
  private double wavePhase;
  private double causticTime;
  private boolean shadowActive;
  private double shadowAge;
  private double shadowStartS;

  public Undersea(LX lx) {
    super(lx);
    addParameter("level", this.level);
    addParameter("causticSpeed", this.causticSpeed);
    addParameter("causticDepth", this.causticDepth);
    addParameter("bubbleRate", this.bubbleRate);
    addParameter("rayIntensity", this.rayIntensity);
    addParameter("shadow", this.shadow);

    this.previousRidgeGap = new double[lx.getModel().size];
    initializeFeaturePoints();
    for (int i = 0; i < this.bubbles.length; ++i) {
      this.bubbles[i] = new Bubble();
    }
    for (int i = 0; i < INITIAL_BUBBLES; ++i) {
      spawnBubble(.04 + .94 * random01());
    }
  }

  private void initializeFeaturePoints() {
    for (int y = 0; y < FEATURE_Y_COUNT; ++y) {
      final int cellY = y + FEATURE_Y_MIN;
      for (int x = 0; x < FEATURE_COLUMNS; ++x) {
        final int index = featureIndex(x, cellY);
        final int seed = x * 0x1f123bb5 ^ cellY * 0x6d2b79f5;
        this.featureBaseX[index] = .25 + .5 * OceanField.hash01(seed ^ 0x2c1b3c6d);
        this.featureBaseY[index] = .25 + .5 * OceanField.hash01(seed ^ 0x5a17d3e9);
        this.featurePhase[index] = LX.TWO_PI * OceanField.hash01(seed ^ 0x73a4f621);
        this.featureRate[index] = .68 + .46 * OceanField.hash01(seed ^ 0x19e3779b);
      }
    }
  }

  private void triggerShadow() {
    this.shadowActive = true;
    this.shadowAge = 0;
    this.shadowStartS = random01();
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.geometry.update();

    final double deltaSeconds = Math.min(.1, Math.max(0, deltaMs * .001));
    this.elapsedSeconds += deltaSeconds;
    this.wavePhase = (this.wavePhase + deltaSeconds * .23) % LX.TWO_PI;
    this.causticTime = (this.causticTime + deltaSeconds * this.causticSpeed.getValue()) % 10_000;
    updateFeaturePoints();
    updateRays();

    final double levelInput = this.level.getValue();
    final double waterLevel = waterLevel(levelInput);
    final double impliedDepth = impliedDepth(levelInput);
    final double baseSurfaceY = OceanField.surfaceY(
      waterLevel,
      this.geometry.floorY(),
      this.geometry.ceilingY(),
      this.geometry.rowPitch()
    );
    final double levelEnvelope = OceanField.levelEnvelope(waterLevel);
    final double surfaceV = LXUtils.clamp(
      (this.geometry.ceilingY() - baseSurfaceY) /
        (this.geometry.ceilingY() - this.geometry.floorY()),
      0,
      1
    );

    updateBubbles(deltaSeconds, surfaceV);
    updateShadow(deltaSeconds);

    renderOrientation(
      Apotheneum.cube.exterior,
      baseSurfaceY,
      levelEnvelope,
      impliedDepth,
      OceanField.CUBE_S_OFFSET
    );
    renderOrientation(
      Apotheneum.cylinder.exterior,
      baseSurfaceY,
      levelEnvelope,
      impliedDepth,
      0
    );

    renderBubbles(Apotheneum.cube.exterior, OceanField.CUBE_S_OFFSET);
    renderBubbles(Apotheneum.cylinder.exterior, 0);
    if (this.shadowActive) {
      applyShadow(Apotheneum.cube.exterior, OceanField.CUBE_S_OFFSET);
      applyShadow(Apotheneum.cylinder.exterior, 0);
    }
    ageBubblePops();
    copyExterior();
  }

  private void updateFeaturePoints() {
    for (int i = 0; i < FEATURE_COUNT; ++i) {
      final double angle = this.featurePhase[i] +
        LX.TWO_PI * this.causticTime * this.featureRate[i];
      this.featureX[i] = this.featureBaseX[i] + FEATURE_ORBIT_RADIUS * Math.cos(angle);
      this.featureY[i] = this.featureBaseY[i] + FEATURE_ORBIT_RADIUS * Math.sin(angle);
    }
  }

  private void updateRays() {
    for (int i = 0; i < RAY_COUNT; ++i) {
      final double phase = RAY_PHASE[i];
      this.rayCenter[i] = wrap01(
        RAY_BASE_S[i] +
        .026 * Math.sin(this.elapsedSeconds * (.55 + .045 * i) + phase) +
        .008 * Math.sin(this.elapsedSeconds * .21 - 1.7 * phase)
      );
      this.rayStrength[i] = .56 + .44 * (
        .5 + .5 * Math.sin(this.elapsedSeconds * (.37 + .03 * i) + 1.3 * phase)
      );
    }
  }

  private void renderOrientation(
      Apotheneum.Orientation orientation,
      double baseSurfaceY,
      double levelEnvelope,
      double impliedDepth,
      double columnOffset) {
    final int ringLength = orientation.columns().length;
    final double verticalRows =
      (this.geometry.ceilingY() - this.geometry.floorY()) / this.geometry.rowPitch() + 1;
    final double depthGradeExponent = LXUtils.lerp(.9, .43, impliedDepth);
    final double depthBrightnessRate = LXUtils.lerp(1.15, 3.25, impliedDepth);
    final double causticReach = Math.max(
      .045,
      LXUtils.lerp(.24, .72, this.causticDepth.getValue()) *
        LXUtils.lerp(1, .52, impliedDepth)
    );
    final double causticEnergy = LXUtils.lerp(1, .56, impliedDepth);
    final double rayReach = LXUtils.lerp(.78, .27, impliedDepth);

    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final double s = OceanField.arcLength(columnIndex, columnOffset, ringLength);
      final double surfaceY = baseSurfaceY + this.geometry.rowPitch() * OceanField.waveRows(
        s,
        this.wavePhase,
        .34 * levelEnvelope
      );
      final int available = orientation.available(columnIndex);
      ++columnIndex;

      for (int row = 0; row < available; ++row) {
        final LXPoint point = column.points[row];
        final double signedRows = (surfaceY - point.y) / this.geometry.rowPitch();
        final double coverage = OceanField.waterCoverage(signedRows);
        if (coverage <= 0) {
          continue;
        }

        final double depth = LXUtils.clamp(signedRows / verticalRows, 0, 1);
        final double grade = Math.pow(depth, depthGradeExponent);
        final int depthColor = LXColor.lerp(
          OceanField.SURFACE_COLOR,
          OceanField.DEEP_COLOR,
          (float) grade
        );
        final double depthBrightness = .32 + .68 * Math.exp(-depthBrightnessRate * depth);
        int color = LXColor.scaleBrightness(
          depthColor,
          (float) LXUtils.clamp(coverage * depthBrightness, 0, 1)
        );

        final double ridgeGap = worleyGap(s, row);
        final double merger = mergerFlash(this.previousRidgeGap[point.index], ridgeGap);
        this.previousRidgeGap[point.index] = ridgeGap;
        final double caustic = causticEnergy * Math.exp(-depth / causticReach) *
          (.68 * ridgeBand(ridgeGap) + .64 * merger);
        if (caustic > 0) {
          color = LXColor.lightest(
            color,
            LXColor.scaleBrightness(
              CAUSTIC_COLOR,
              (float) LXUtils.clamp(coverage * caustic, 0, 1)
            )
          );
        }

        final double ray = rayBrightness(s) * this.rayIntensity.getValue() *
          Math.exp(-depth / rayReach);
        if (ray > 0) {
          color = LXColor.lerp(
            color,
            CAUSTIC_COLOR,
            (float) LXUtils.clamp(coverage * ray * 1.15, 0, .62)
          );
        }
        this.colors[point.index] = color;
      }
    }
  }

  private double worleyGap(double s, int row) {
    final double domainX = s * FEATURE_COLUMNS;
    final double domainY = row / CELL_HEIGHT_ROWS;
    final int baseX = (int) Math.floor(domainX);
    final int baseY = (int) Math.floor(domainY);
    double first = Double.POSITIVE_INFINITY;
    double second = Double.POSITIVE_INFINITY;

    for (int dy = -1; dy <= 1; ++dy) {
      final int cellY = baseY + dy;
      for (int dx = -1; dx <= 1; ++dx) {
        final int cellX = baseX + dx;
        final int feature = featureIndex(wrapCellX(cellX), cellY);
        final double x = cellX + this.featureX[feature];
        final double y = cellY + this.featureY[feature];
        final double offsetX = domainX - x;
        final double offsetY = domainY - y;
        final double distanceSquared = offsetX * offsetX + offsetY * offsetY;
        if (distanceSquared < first) {
          second = first;
          first = distanceSquared;
        } else if (distanceSquared < second) {
          second = distanceSquared;
        }
      }
    }
    return Math.sqrt(second) - Math.sqrt(first);
  }

  private double rayBrightness(double s) {
    double brightness = 0;
    for (int i = 0; i < RAY_COUNT; ++i) {
      final double distance = circularDistance(s, this.rayCenter[i]);
      final double beam = 1 - OceanField.smoothstep(.002, .01, distance);
      brightness = Math.max(brightness, beam * this.rayStrength[i]);
    }
    return brightness;
  }

  private void updateBubbles(double deltaSeconds, double surfaceV) {
    for (Bubble bubble : this.bubbles) {
      if (!bubble.active || bubble.popFrames > 0) {
        continue;
      }
      bubble.v -= bubble.velocity * deltaSeconds;
      if (bubble.v <= surfaceV) {
        bubble.v = surfaceV;
        bubble.popFrames = POP_FRAME_COUNT;
      }
    }

    this.bubbleAccumulator += deltaSeconds * this.bubbleRate.getValue() * 12;
    while (this.bubbleAccumulator >= 1) {
      spawnBubble(1);
      this.bubbleAccumulator -= 1;
    }
  }

  private void spawnBubble(double v) {
    Bubble target = null;
    for (Bubble bubble : this.bubbles) {
      if (!bubble.active) {
        target = bubble;
        break;
      }
    }
    if (target == null) {
      return;
    }

    final double plume = PLUME_S[this.nextPlume];
    this.nextPlume = (this.nextPlume + 1) % PLUME_S.length;
    target.active = true;
    target.popFrames = 0;
    target.v = v;
    target.s = wrap01(plume + (random01() - .5) * .018);
    target.velocity = (6.2 + 2.5 * random01()) / (Apotheneum.GRID_HEIGHT - 1.0);
    target.radius = .72 + .3 * random01();
    target.wobblePhase = LX.TWO_PI * random01();
    target.wobbleRate = 1.1 + 1.3 * random01();
  }

  private void renderBubbles(Apotheneum.Orientation orientation, double columnOffset) {
    final int ringLength = orientation.columns().length;
    final int height = orientation.height();
    for (Bubble bubble : this.bubbles) {
      if (!bubble.active) {
        continue;
      }
      final double wobble = .78 * Math.sin(
        bubble.wobblePhase + this.elapsedSeconds * bubble.wobbleRate
      ) + .24 * Math.sin(
        3 * bubble.wobblePhase + this.elapsedSeconds * 1.7 * bubble.wobbleRate
      );
      final double centerColumn = bubble.s * ringLength + columnOffset + wobble;
      final double centerRow = bubble.v * (height - 1);
      final double radius = bubble.popFrames > 0
        ? (bubble.popFrames == POP_FRAME_COUNT ? 2.45 : 1.65)
        : bubble.radius;
      final double brightness = bubble.popFrames > 0 ? 1 : .76;
      final int extent = (int) Math.ceil(radius + 1);
      final int roundedColumn = (int) Math.round(centerColumn);
      final int roundedRow = (int) Math.round(centerRow);

      for (int dx = -extent; dx <= extent; ++dx) {
        final int columnIndex = wrapIndex(roundedColumn + dx, ringLength);
        final int available = orientation.available(columnIndex);
        final double xDistance = roundedColumn + dx - centerColumn;
        for (int dy = -extent; dy <= extent; ++dy) {
          final int row = roundedRow + dy;
          if (!OceanField.isAvailableCell(row, available)) {
            continue;
          }
          final double yDistance = row - centerRow;
          final double distance = Math.sqrt(
            xDistance * xDistance + yDistance * yDistance
          );
          final double falloff = 1 - OceanField.smoothstep(radius - .7, radius + .35, distance);
          if (falloff <= 0) {
            continue;
          }
          final LXPoint point = orientation.column(columnIndex).points[row];
          this.colors[point.index] = LXColor.lightest(
            this.colors[point.index],
            LXColor.scaleBrightness(
              BUBBLE_COLOR,
              (float) LXUtils.clamp(brightness * falloff, 0, 1)
            )
          );
        }
      }
    }
  }

  private void ageBubblePops() {
    for (Bubble bubble : this.bubbles) {
      if (bubble.active && bubble.popFrames > 0) {
        --bubble.popFrames;
        if (bubble.popFrames == 0) {
          bubble.active = false;
        }
      }
    }
  }

  private void updateShadow(double deltaSeconds) {
    if (!this.shadowActive) {
      return;
    }
    this.shadowAge += deltaSeconds;
    if (this.shadowAge >= SHADOW_DURATION_SECONDS) {
      this.shadowActive = false;
    }
  }

  private void applyShadow(Apotheneum.Orientation orientation, double columnOffset) {
    final int ringLength = orientation.columns().length;
    final int height = orientation.height();
    final double normalizedAge = LXUtils.clamp(
      this.shadowAge / SHADOW_DURATION_SECONDS,
      0,
      1
    );
    final double progress = shadowProgress(normalizedAge);
    final double centerS = wrap01(this.shadowStartS + SHADOW_TRAVEL * progress);
    final double centerRow = height * (
      .29 + .055 * Math.sin(Math.PI * progress) + .025 * Math.sin(5.5 * progress)
    );

    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final double s = OceanField.arcLength(columnIndex, columnOffset, ringLength);
      final int available = orientation.available(columnIndex);
      ++columnIndex;
      for (int row = 0; row < available; ++row) {
        final double mask = shadowMask(s, row, centerS, centerRow, ringLength);
        if (mask > 0) {
          final LXPoint point = column.points[row];
          this.colors[point.index] = LXColor.scaleBrightness(
            this.colors[point.index],
            (float) (1 - .88 * mask)
          );
        }
      }
    }
  }

  static double waterLevel(double inputLevel) {
    if (inputLevel <= VISIBLE_WATERLINE_START) {
      return 1;
    }
    final double transition = OceanField.smoothstep(
      VISIBLE_WATERLINE_START,
      1,
      inputLevel
    );
    return LXUtils.lerp(1, LOWEST_WATER_LEVEL, transition);
  }

  static double impliedDepth(double inputLevel) {
    return OceanField.smoothstep(
      0,
      VISIBLE_WATERLINE_START,
      Math.min(inputLevel, VISIBLE_WATERLINE_START)
    );
  }

  static double ridgeBand(double ridgeGap) {
    return 1 - OceanField.smoothstep(.045, .27, ridgeGap);
  }

  static double mergerFlash(double previousGap, double ridgeGap) {
    final double collapse = previousGap - ridgeGap;
    final double temporal = OceanField.smoothstep(.0006, .006, collapse);
    final double wideningRegion = 1 - OceanField.smoothstep(.09, .46, ridgeGap);
    return temporal * wideningRegion;
  }

  static double shadowProgress(double normalizedAge) {
    final double t = LXUtils.clamp(normalizedAge, 0, 1);
    if (t < .18) {
      return .22 * easeOutCubic(t / .18);
    }
    if (t < .48) {
      return .22 + .35 * (t - .18) / .3;
    }
    if (t < .62) {
      return .57 + .035 * OceanField.smoothstep(.48, .62, t);
    }
    if (t < .8) {
      return .605 + .25 * easeOutCubic((t - .62) / .18);
    }
    return .855 + .145 * OceanField.smoothstep(.8, 1, t);
  }

  static double shadowMask(
      double s,
      double row,
      double centerS,
      double centerRow,
      int ringLength) {
    double signedS = s - centerS;
    signedS -= Math.floor(signedS + .5);
    final double along = signedS * 180;
    if (along <= -6.5 || along >= 2.5) {
      return 0;
    }
    final double taper = along < -.4
      ? OceanField.smoothstep(-6.5, -.4, along)
      : 1 - OceanField.smoothstep(.2, 2.5, along);
    final double curve = 1.05 * Math.sin(.68 * (along + 5.8)) * (1 - .55 * taper);
    final double halfHeight = .55 + 3.55 * taper;
    final double vertical = 1 - OceanField.smoothstep(
      halfHeight - .75,
      halfHeight + .45,
      Math.abs(row - centerRow - curve)
    );
    final double surfaceScale = LXUtils.clamp(ringLength / 180.0, .66, 1.12);
    return taper * vertical * (.82 + .18 * surfaceScale);
  }

  static double circularDistance(double a, double b) {
    final double distance = Math.abs(a - b);
    return Math.min(distance, 1 - distance);
  }

  private static double easeOutCubic(double value) {
    final double inverse = 1 - value;
    return 1 - inverse * inverse * inverse;
  }

  private static double wrap01(double value) {
    return value - Math.floor(value);
  }

  private static int wrapCellX(int x) {
    final int wrapped = x % FEATURE_COLUMNS;
    return wrapped < 0 ? wrapped + FEATURE_COLUMNS : wrapped;
  }

  private static int wrapIndex(int index, int length) {
    final int wrapped = index % length;
    return wrapped < 0 ? wrapped + length : wrapped;
  }

  private static int featureIndex(int x, int y) {
    return (y - FEATURE_Y_MIN) * FEATURE_COLUMNS + x;
  }

  private double random01() {
    int value = this.randomState;
    value ^= value << 13;
    value ^= value >>> 17;
    value ^= value << 5;
    this.randomState = value;
    return (value & 0x7fffffff) / (double) Integer.MAX_VALUE;
  }

  private static final class Bubble {
    boolean active;
    int popFrames;
    double s;
    double v;
    double velocity;
    double radius;
    double wobblePhase;
    double wobbleRate;
  }
}
