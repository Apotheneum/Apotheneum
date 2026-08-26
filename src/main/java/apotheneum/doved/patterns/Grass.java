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
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.utils.Noise;

/**
 * A field of tall grass rooted at the bottom of each surface, bending under wind.
 *
 * Three standard ingredients, composed:
 *
 * 1. A gust field. Fractal Perlin noise sampled in world space, advected by
 *    {@link #gustSpeed} so gusts travel in one direction across the building.
 *    Neighbouring blades read neighbouring samples, including across surface seams,
 *    which is what makes the motion read as wind rather than as per-blade jitter.
 *
 * 2. A damped harmonic oscillator per blade. Wind sets a target bend; the blade chases it
 *    with a spring, so it overshoots on a gust front and recoils behind it. Without this
 *    the field is just a scrolling texture — the recoil is the whole effect.
 *
 * 3. A cantilever bend profile. Tangent angle theta(s) = bend * s^2 along the blade's arc
 *    length s, integrated stepwise: the root stays planted and near-vertical while
 *    curvature accumulates toward the tip, which is the shape a blade of grass actually
 *    takes. Integrating along arc length also foreshortens the blade as it lays over,
 *    instead of stretching it.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Grass")
@LXComponent.Description("Tall grass bending and recoiling under travelling gusts of wind")
public class Grass extends ApotheneumPattern implements UIDeviceControls<Grass> {

  private static final double TWO_PI = 2 * Math.PI;

  // Blades are placed by a golden-ratio sequence, so any prefix of the array is evenly
  // spread around the ring and the Density knob can add or remove blades without
  // reshuffling the ones that stay.
  private static final double GOLDEN_RATIO_CONJUGATE = .6180339887498949;
  private static final double MAX_BLADES_PER_COLUMN = 3;

  // Long frames must not blow up the integrator, and a paused engine must not launch the
  // whole field on resume.
  private static final double MAX_DELTA_SECONDS = .05;

  // One noise unit is roughly one gust cell. This gives several cells across the building.
  private static final double GUST_FIELD_SCALE = .012;
  private static final float GUST_LACUNARITY = 2;
  private static final float GUST_GAIN = .5f;
  private static final int GUST_OCTAVES = 3;
  // Three-octave fbm at gain .5 lands well inside [-1, 1]; normalize so the Gust knob
  // reads as a fraction of the base wind.
  private static final double GUST_NORMALIZATION = 1.8;
  private static final double GUST_MAX_WIDTHS_PER_SECOND = .7;
  private static final double GUST_TEMPORAL_RATE = .5;
  private static final double TURBULENCE_TEMPORAL_RATE = 1.6;
  private static final double TURBULENCE_BEND = .55;

  private static final double MAX_BEND = 1.5;
  private static final double MAX_OUT_OF_PLANE_BEND = 1.0;
  private static final double MIN_FREQUENCY_HZ = .35;
  private static final double MAX_FREQUENCY_HZ = 2.6;
  private static final double MIN_SPRING_STIFFNESS = .65;
  private static final double MAX_SPRING_STIFFNESS = 1.65;
  // Keep omega * h below this in the integrator; substep when a frame is longer.
  private static final double MAX_INTEGRATION_ANGLE = .25;

  // Steps per blade row. Half-row steps keep the arc continuous under bilinear splatting.
  private static final double STEPS_PER_ROW = 2;
  private static final double MIN_BLADE_HEIGHT = .08;
  // The last of the blade fades out, so a tip reads as a tip rather than a cut stem.
  private static final double TIP_FADE_START = .82;
  private static final int SHARP_WEIGHT_POWER = 8;

  private static final long DEFAULT_SEED = 0x67726173L;

  public final CompoundParameter wind =
    new CompoundParameter("Wind", .3, -1, 1)
    .setDescription("Steady wind strength and direction; negative blows the other way");

  public final CompoundParameter gust =
    new CompoundParameter("Gust", .5, 0, 1)
    .setDescription("Depth of the travelling gust field layered on the steady wind");

  public final CompoundParameter gustSpeed =
    new CompoundParameter("Speed", .35, 0, 2)
    .setDescription("Gust-front speed from 0 to 1.4 building widths per second");

  public final CompoundParameter windAzimuth =
    new CompoundParameter("Dir", 0, 0, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("World-space direction the wind travels across the building");

  public final CompoundParameter density =
    new CompoundParameter("Density", .9, .1, MAX_BLADES_PER_COLUMN)
    .setDescription("Blades per column of the surface");

  public final CompoundParameter bladeHeight =
    new CompoundParameter("Height", .7, .15, 1)
    .setDescription("Mean blade height as a fraction of the surface height");

  public final CompoundParameter heightVariation =
    new CompoundParameter("Spread", .45, 0, 1)
    .setDescription("Spread of blade heights around the mean");

  public final CompoundParameter stiffness =
    new CompoundParameter("Spring", .45, 0, 1)
    .setDescription("Spring stiffness of a blade; higher snaps back faster and bends less");

  public final CompoundParameter damping =
    new CompoundParameter("Damp", .28, .04, 1)
    .setDescription("Damping ratio; low values let blades oscillate after a gust passes");

  public final TriggerParameter reseed =
    new TriggerParameter("Reseed", this::reseedFields)
    .setDescription("Regrow the field with new blade positions, heights and stiffnesses");

  public final CompoundParameter tipGlow =
    new CompoundParameter("Tips", .65, 0, 1)
    .setDescription("Brightness of the tips relative to the roots");

  public final CompoundParameter sharpness =
    new CompoundParameter("Sharp", .6, 0, 1)
    .setDescription("Crispness of blade edges from smooth to nearly single-pixel strokes");

  public final CompoundParameter turbulence =
    new CompoundParameter("Turb", .45, 0, 1)
    .setDescription("Sideways eddies where wind hits a surface head-on");

  private static class Blade {
    double rootX;
    double height;
    double frequencyScale;
    double dragScale;
    double brightness;
    // Live oscillator state, in radians and radians per second.
    double angle;
    double angularVelocity;
  }

  /** One unwrapped surface: its blades and accumulation buffer. */
  private class Field {

    private final int width;
    private final int height;
    // Zero keeps the surface seamless; a positive width clips each blade to its root segment.
    private final int segmentWidth;
    private final float[] buffer;
    private final Blade[] blades;
    private int activeCount;
    private Field(int width, int height, int segmentWidth) {
      this.width = width;
      this.height = height;
      this.segmentWidth = segmentWidth;
      this.buffer = new float[width * height];
      this.blades = new Blade[(int) Math.ceil(width * MAX_BLADES_PER_COLUMN)];
      for (int i = 0; i < this.blades.length; ++i) {
        this.blades[i] = new Blade();
      }
      seed();
    }

    private void seed() {
      final double offset = random.nextDouble();
      this.activeCount = 0;
      for (int i = 0; i < this.blades.length; ++i) {
        final Blade blade = this.blades[i];
        blade.rootX = ((offset + i * GOLDEN_RATIO_CONJUGATE) % 1) * this.width;
        blade.height = random.nextDouble();
        // Squared, so most blades are near the base frequency and a few are notably
        // floppier — an even spread reads as a mechanical sweep.
        final double f = random.nextDouble();
        blade.frequencyScale = .55 + 1.1 * f * f;
        blade.dragScale = .7 + .6 * random.nextDouble();
        blade.brightness = .55 + .45 * random.nextDouble();
        blade.angle = 0;
        blade.angularVelocity = 0;
      }
    }

    private void run(double dt, Apotheneum.Orientation orientation) {
      Arrays.fill(this.buffer, 0f);

      final int active = LXUtils.clamp((int) Math.round(this.width * densityValue), 0, this.blades.length);
      for (int i = 0; i < active; ++i) {
        final Blade blade = this.blades[i];
        final int column = Math.floorMod((int) Math.floor(blade.rootX), this.width);
        final LXPoint root = orientation.columns()[column].points[this.height - 1];
        final LXPoint nextRoot = orientation.columns()[(column + 1) % this.width].points[this.height - 1];
        final double tangentX = nextRoot.x - root.x;
        final double tangentZ = nextRoot.z - root.z;
        final double tangentLength = Math.hypot(tangentX, tangentZ);
        final double windTangent = (tangentLength == 0) ? 0
          : (windX * tangentX + windZ * tangentZ) / tangentLength;
        final double windNormal = (tangentLength == 0) ? 0
          : Math.abs((-windX * tangentZ + windZ * tangentX) / tangentLength);
        final double strength = strengthAt(root.x, root.z);
        final double turbulence = turbulenceAt(root.x, root.z) * turbulenceValue * windMagnitude
          * windNormal * TURBULENCE_BEND;
        final double outOfPlaneBend = LXUtils.clamp(
          MAX_OUT_OF_PLANE_BEND * strength * windNormal * blade.dragScale,
          0, MAX_OUT_OF_PLANE_BEND);
        final double target = targetBend(blade, strength, windTangent, turbulence);
        if (i >= this.activeCount) {
          blade.angle = target;
          blade.angularVelocity = 0;
        }
        update(blade, target, dt);
        draw(blade, outOfPlaneBend);
      }
      this.activeCount = active;
      output(orientation);
    }

    private double strengthAt(double x, double z) {
      final double downwind = (x * windX + z * windZ) * GUST_FIELD_SCALE - gustPhase;
      final double crosswind = (-x * windZ + z * windX) * GUST_FIELD_SCALE;
      final float noise = Noise.stb_perlin_fbm_noise3(
        (float) downwind,
        (float) crosswind,
        (float) gustTime,
        GUST_LACUNARITY, GUST_GAIN, GUST_OCTAVES);
      // At low wind, passing gusts dominate the motion. At high wind, the steady term
      // pushes the blades over while gusts continue to ride on top of it.
      final double steady = windMagnitude * windMagnitude;
      final double gustAmount = gustValue * windMagnitude * (1 - .35 * windMagnitude);
      return Math.max(0, steady + gustAmount * noise * GUST_NORMALIZATION);
    }

    private double turbulenceAt(double x, double z) {
      final double downwind = (x * windX + z * windZ) * GUST_FIELD_SCALE;
      final double crosswind = (-x * windZ + z * windX) * GUST_FIELD_SCALE;
      return Noise.stb_perlin_fbm_noise3(
        (float) (crosswind * 1.7 + 13.1),
        (float) (downwind * 1.7 - 7.7),
        (float) turbulenceTime,
        GUST_LACUNARITY, GUST_GAIN, GUST_OCTAVES);
    }

    private double targetBend(Blade blade, double strength, double windTangent, double turbulence) {
      return LXUtils.clamp(blade.dragScale * (MAX_BEND * strength * windTangent + turbulence)
        / springStiffness, -MAX_BEND, MAX_BEND);
    }

    private void update(Blade blade, double target, double dt) {
      final double omega = baseOmega * blade.frequencyScale;

      // Semi-implicit Euler, substepped so a long frame can't run the spring unstable.
      final int steps = 1 + (int) (dt * omega / MAX_INTEGRATION_ANGLE);
      final double h = dt / steps;
      for (int i = 0; i < steps; ++i) {
        final double accel =
          omega * omega * (target - blade.angle) - 2 * dampingValue * omega * blade.angularVelocity;
        blade.angularVelocity += accel * h;
        blade.angle += blade.angularVelocity * h;
      }
    }

    private void draw(Blade blade, double outOfPlaneBend) {
      final double rows = bladeRows(blade, this.height) * Math.cos(outOfPlaneBend);
      final int steps = (int) Math.ceil(rows * STEPS_PER_ROW);
      if (steps < 1) {
        return;
      }
      final double segment = rows / steps;
      final double bend = LXUtils.clamp(blade.angle, -MAX_BEND, MAX_BEND);
      // Deposit per step scales with step length, so brightness is independent of how
      // finely the blade happens to be subdivided.
      final double deposit = blade.brightness * segment / STEPS_PER_ROW;

      double x = blade.rootX;
      double y = this.height - 1;
      final int segmentStart = (this.segmentWidth == 0) ? 0
        : ((int) Math.floor(blade.rootX) / this.segmentWidth) * this.segmentWidth;
      for (int i = 1; i <= steps; ++i) {
        final double s = i / (double) steps;
        final double theta = bend * s * s;
        x += Math.sin(theta) * segment;
        y -= Math.cos(theta) * segment;
        splat(x, y, deposit * level(s), segmentStart);
      }
    }

    private void splat(double x, double y, double amount, int segmentStart) {
      final int y0 = (int) Math.floor(y);
      if ((y0 < -1) || (y0 >= this.height)) {
        return;
      }
      final double fx = x - Math.floor(x);
      final double fy = y - y0;
      final double wx1 = sharpenWeight(fx);
      final double wy1 = sharpenWeight(fy);
      final double wx0 = 1 - wx1;
      final double wy0 = 1 - wy1;
      final int x0 = (int) Math.floor(x);
      final int x1 = x0 + 1;
      if (this.segmentWidth == 0) {
        final int wrappedX0 = Math.floorMod(x0, this.width);
        final int wrappedX1 = (wrappedX0 + 1) % this.width;
        add(wrappedX0, y0, amount * wx0 * wy0);
        add(wrappedX1, y0, amount * wx1 * wy0);
        add(wrappedX0, y0 + 1, amount * wx0 * wy1);
        add(wrappedX1, y0 + 1, amount * wx1 * wy1);
        return;
      }
      final int segmentEnd = segmentStart + this.segmentWidth;
      if ((x0 >= segmentStart) && (x0 < segmentEnd)) {
        add(x0, y0, amount * wx0 * wy0);
        add(x0, y0 + 1, amount * wx0 * wy1);
      }
      if ((x1 >= segmentStart) && (x1 < segmentEnd)) {
        add(x1, y0, amount * wx1 * wy0);
        add(x1, y0 + 1, amount * wx1 * wy1);
      }
    }

    private void add(int x, int y, double amount) {
      if ((y < 0) || (y >= this.height)) {
        return;
      }
      this.buffer[y * this.width + x] += (float) amount;
    }

    private void output(Apotheneum.Orientation orientation) {
      final Apotheneum.Column[] columns = orientation.columns();
      for (int c = 0; c < columns.length; ++c) {
        final LXPoint[] points = columns[c].points;
        // Door columns carry a full complement of points, but the ones below the door
        // header have no LED behind them.
        final int available = orientation.available(c);
        for (int y = 0; y < available; ++y) {
          if (!isViewPoint(points[y].index)) {
            continue;
          }
          final float value = this.buffer[y * this.width + c];
          colors[points[y].index] = (value <= 0) ? LXColor.BLACK
            : LXColor.gray(100 * Math.min(1, value));
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
  private final Field cubeField = new Field(
    Apotheneum.Cube.Ring.LENGTH, Apotheneum.GRID_HEIGHT, Apotheneum.GRID_WIDTH);
  private final Field cylinderField = new Field(
    Apotheneum.Cylinder.Ring.LENGTH, Apotheneum.CYLINDER_HEIGHT, 0);
  private LXModel viewModel;
  private boolean[] viewMask;

  // Per-frame values, resolved once in render() rather than per blade.
  private double windMagnitude;
  private double windX;
  private double windZ;
  private double gustValue;
  private double gustSpeedValue;
  private double densityValue;
  private double baseOmega;
  private double springStiffness;
  private double dampingValue;
  private double gustPhase;
  private double gustTime;
  private double turbulenceTime;
  private double turbulenceValue;
  private double rootLevel;
  private double tipLevel;
  private double sharpnessValue;
  private double meanHeight;
  private double heightSpread;

  public Grass(LX lx) {
    super(lx);
    addParameter("wind", this.wind);
    addParameter("gust", this.gust);
    addParameter("gustSpeed", this.gustSpeed);
    addParameter("windAzimuth", this.windAzimuth);
    addParameter("density", this.density);
    addParameter("bladeHeight", this.bladeHeight);
    addParameter("heightVariation", this.heightVariation);
    addParameter("stiffness", this.stiffness);
    addParameter("damping", this.damping);
    addParameter("reseed", this.reseed);
    addParameter("tipGlow", this.tipGlow);
    addParameter("sharpness", this.sharpness);
    addParameter("turbulence", this.turbulence);
  }

  private void reseedFields() {
    this.cubeField.seed();
    this.cylinderField.seed();
  }

  private void updateViewMask() {
    final LXModel model = getModelView();
    if (this.viewModel == model) {
      return;
    }
    Arrays.fill(colors, LXColor.BLACK);
    this.viewModel = model;
    if (model == this.lx.getModel()) {
      this.viewMask = null;
      return;
    }
    this.viewMask = new boolean[this.lx.getModel().size];
    for (LXPoint point : model.points) {
      this.viewMask[point.index] = true;
    }
  }

  private boolean isViewPoint(int index) {
    return (this.viewMask == null) || this.viewMask[index];
  }

  private double sharpenWeight(double fraction) {
    if (this.sharpnessValue == 0) {
      return fraction;
    }
    double sharp = fraction * fraction;
    sharp *= sharp;
    sharp *= sharp;
    final double complement = 1 - fraction;
    double sharpComplement = complement * complement;
    sharpComplement *= sharpComplement;
    sharpComplement *= sharpComplement;
    sharp /= sharp + sharpComplement;
    return LXUtils.lerp(fraction, sharp, this.sharpnessValue);
  }

  private double bladeRows(Blade blade, int surfaceHeight) {
    final double scale =
      LXUtils.lerp(1 - this.heightSpread, 1 + this.heightSpread * .6, blade.height);
    return LXUtils.clamp(this.meanHeight * scale, MIN_BLADE_HEIGHT, 1) * (surfaceHeight - 1);
  }

  /** Brightness along the blade, from root to tip, with the last of the tip fading out. */
  private double level(double s) {
    double level = LXUtils.lerp(this.rootLevel, this.tipLevel, s);
    if (s > TIP_FADE_START) {
      level *= (1 - s) / (1 - TIP_FADE_START);
    }
    return level;
  }

  @Override
  protected void render(double deltaMs) {
    updateViewMask();
    final double dt = Math.min(deltaMs * .001, MAX_DELTA_SECONDS);
    final double windValue = this.wind.getValue();

    this.windMagnitude = Math.abs(windValue);
    final double windAngle = Math.toRadians(this.windAzimuth.getValue());
    final double windSign = (windValue < 0) ? -1 : 1;
    this.windX = windSign * Math.cos(windAngle);
    this.windZ = windSign * Math.sin(windAngle);
    this.gustValue = this.gust.getValue();
    this.gustSpeedValue = this.gustSpeed.getValue();
    this.densityValue = this.density.getValue();
    this.baseOmega = TWO_PI * LXUtils.lerp(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, this.stiffness.getValue());
    this.springStiffness = LXUtils.lerp(
      MIN_SPRING_STIFFNESS, MAX_SPRING_STIFFNESS, this.stiffness.getValue());
    this.dampingValue = this.damping.getValue();
    this.meanHeight = this.bladeHeight.getValue();
    this.heightSpread = this.heightVariation.getValue();
    final double glow = this.tipGlow.getValue();
    this.rootLevel = LXUtils.lerp(1, .5, glow);
    this.tipLevel = LXUtils.lerp(.45, 1, glow);
    this.sharpnessValue = this.sharpness.getValue();
    this.turbulenceValue = this.turbulence.getValue();
    final LXModel windModel = getModelView();
    final double buildingWidth = Math.max(windModel.xRange, windModel.zRange);
    final double gustSpeed = this.gustSpeedValue * GUST_MAX_WIDTHS_PER_SECOND;
    this.gustPhase += dt * gustSpeed * buildingWidth * GUST_FIELD_SCALE;
    this.gustTime += dt * this.gustSpeedValue * GUST_TEMPORAL_RATE;
    this.turbulenceTime += dt * this.gustSpeedValue * TURBULENCE_TEMPORAL_RATE;

    this.cubeField.run(dt, Apotheneum.cube.exterior);
    this.cylinderField.run(dt, Apotheneum.cylinder.exterior);
    if (Apotheneum.hasInterior) {
      this.cubeField.output(Apotheneum.cube.interior);
      this.cylinderField.output(Apotheneum.cylinder.interior);
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Grass grass) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Wind",
      newKnob(grass.wind),
      newKnob(grass.windAzimuth),
      newKnob(grass.turbulence)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Gusts",
      newKnob(grass.gust),
      newKnob(grass.gustSpeed)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Field",
      newKnob(grass.density),
      newKnob(grass.bladeHeight),
      newKnob(grass.heightVariation)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Blade",
      newKnob(grass.stiffness),
      newKnob(grass.damping),
      newKnob(grass.tipGlow)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Render",
      newKnob(grass.sharpness),
      newButton(grass.reseed).setTriggerable(true)
    ).setChildSpacing(6);
  }
}
