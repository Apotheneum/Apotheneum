package apotheneum.doved.patterns;

import java.util.Arrays;
import java.util.Random;
import java.util.function.IntUnaryOperator;

import apotheneum.Apotheneum;
import apotheneum.doved.modulators.ApotheneumColor;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;

/**
 * A moving fireball: a white-hot core inside a corona, leaving a trail of fire and
 * embers behind it.
 *
 * <p>The pattern renders a scalar <em>heat field</em> and only maps heat to color as
 * the final step, which is what makes it read as something burning rather than as a
 * colored blob. Each surface owns a heat buffer sized to its unwrapped grid (cube
 * 200x45, cylinder 120x43) and every simulation tick does four things:
 *
 * <ol>
 * <li><b>Advect and cool</b> - each cell pulls heat from a neighbor upwind of it,
 * scaled by a decay. The upwind direction is radially outward near the head, so the
 * corona pushes out like a sun, and vertical away from it, so the wake rises like a
 * flame. Lateral jitter on the sample makes the classic self-organizing flame tongues.
 * <li><b>Stamp</b> - the core and corona are injected along the <em>segment</em> from
 * last tick's head position to this one's. Stamping only at the current point leaves a
 * dotted line when the fireball moves fast; stamping the segment gives a continuous
 * streak whose length is automatically proportional to speed.
 * <li><b>Embers</b> - a fixed pool of sparks emitted at the head, carrying a fraction
 * of its velocity plus scatter, then subject to buoyancy and drag so they fall behind
 * and drift upward as they cool.
 * <li><b>Render</b> - heat through primary and secondary palette roles, preserving
 * blackbody brightness and desaturation with a scalar per-frame heat curve.
 * </ol>
 *
 * <p>The trail needs no separate machinery: heat is stamped into the buffer in surface
 * coordinates and stays where it was put, so the head simply moves away from it.
 *
 * <p>The simulation runs on a fixed 60Hz timestep with an accumulator, so advection
 * speed does not change with the engine frame rate.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Fireball")
public class Fireball extends ColorNativePattern {

  public enum Shape {

    BOTH("Both"),
    CUBE("Cube"),
    CYLINDER("Cylinder");

    public final String label;

    private Shape(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  private static final float SIM_MS = 1000f / 60f;
  private static final float SIM_SECONDS = SIM_MS / 1000f;
  private static final int MAX_SIM_STEPS = 4;
  private static final double MAX_ACCUMULATE_MS = 100;
  private static final float TWO_PI_F = (float) (2 * Math.PI);

  private static final int HEAT_LUT_SIZE = 256;
  private static final float HEAT_EPSILON = .004f;

  /** Half-life of a heat cell at Cooling = 1, in seconds. */
  private static final float BASE_HALF_LIFE = .45f;

  /** Heat advection velocity in surface cells per second. */
  private static final float ADVECTION_SPEED = 60f;
  private static final float TURBULENCE_NOISE_SCALE = .1f;

  private static final int SPARK_POOL = 256;
  private static final long RANDOM_SEED = 0xf1aeb0a1L;
  private static final float SPARK_BUOYANCY = -11f;
  private static final float SPARK_DAMPING = .35f;
  private static final float SPARK_INHERIT = .4f;
  private static final float SPARK_MIN_RISE_VELOCITY = -2f;

  /** Upward elevation velocity, in normalized height per second, applied by Launch. */
  private static final float LAUNCH_VELOCITY = 1.4f;
  private static final float LAUNCH_GRAVITY = 2.6f;

  public final CompoundParameter azimuth =
    new CompoundParameter("Azimuth", .25)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Position of the fireball around the ring");

  public final CompoundParameter elevation =
    new CompoundParameter("Height", .45)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Height of the fireball, 0 at the floor");

  public final CompoundParameter coreSize =
    new CompoundParameter("Core", 2, 1, 6)
    .setDescription("Radius of the white-hot core, in pixels");

  public final CompoundParameter auraSize =
    new CompoundParameter("Aura", 9, 2, 25)
    .setDescription("Radius of the corona around the core, in pixels");

  public final CompoundParameter intensity =
    new CompoundParameter("Heat", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Peak heat injected at the core");

  public final CompoundParameter cooling =
    new CompoundParameter("Cooling", 1.8, .15, 4)
    .setDescription("How fast heat dies; low values leave a long trail");

  public final CompoundParameter turbulence =
    new CompoundParameter("Turbulence", 1.2, 0, 3)
    .setDescription("Lateral jitter as heat advects, which forms the flame tongues");

  public final CompoundParameter buoyancy =
    new CompoundParameter("Rise", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("0 spreads the corona radially like a sun, 1 lifts it like a flame");

  public final CompoundDiscreteParameter sparkDensity =
    // DiscreteParameter's (value, min, max) constructor treats max as exclusive.
    new CompoundDiscreteParameter("Sparks", 24, 0, SPARK_POOL + 1)
    .setDescription("Number of embers trailing the fireball");

  public final CompoundParameter sparkLife =
    new CompoundParameter("Life", 1.8, .2, 3)
    .setUnits(CompoundParameter.Units.SECONDS)
    .setDescription("How long an ember burns before dying");

  public final CompoundParameter sparkSize =
    new CompoundParameter("Size", 1, .5, 3)
    .setDescription("Radius of an ember, in pixels");

  /** Structural colour path for review or downstream Colorize compositing. */
  public final BooleanParameter monochrome =
    new BooleanParameter("Monochrome", false)
    .setDescription("Render heat as grayscale instead of the Primary and Secondary color roles");

  public final CompoundParameter gamma =
    new CompoundParameter("Gamma", 1, .4, 2.5)
    .setDescription("Shapes the heat falloff; below 1 broadens the corona");

  public final EnumParameter<Shape> shape =
    new EnumParameter<Shape>("Shape", Shape.BOTH)
    .setDescription("Which chambers the fireball burns on");

  public final TriggerParameter launch =
    new TriggerParameter("Launch", this::onLaunch)
    .setDescription("Punch the fireball upward, letting it arc back down");

  public final TriggerParameter clear =
    new TriggerParameter("Clear", this::onClear)
    .setDescription("Extinguish all heat and embers");

  /** Primary role: the white-hot core, defaulting to the first palette stop. */
  public final ColorRole coreColor;

  /** Secondary role: cool trail and embers, defaulting to the second palette stop. */
  public final ColorRole emberColor;

  // Fixed because headless renders are review evidence: the same invocation must reproduce
  // the same flicker and ember trajectories. This is deliberately not a performance control.
  private final Random random = new Random(RANDOM_SEED);
  private final float[] heatCurve = new float[HEAT_LUT_SIZE + 1];

  private final Fire cubeFire = new Fire();
  private final Fire cylinderFire = new Fire();

  private double accumulatorMs = 0;
  private float launchOffset = 0;
  private float launchVelocity = 0;

  public Fireball(LX lx) {
    super(lx, .7, .7);
    this.coreColor = this.primary;
    this.emberColor = this.secondary;
    addParameter("azimuth", this.azimuth);
    addParameter("elevation", this.elevation);
    addParameter("coreSize", this.coreSize);
    addParameter("auraSize", this.auraSize);
    addParameter("intensity", this.intensity);
    addParameter("cooling", this.cooling);
    addParameter("turbulence", this.turbulence);
    addParameter("buoyancy", this.buoyancy);
    addParameter("sparkDensity", this.sparkDensity);
    addParameter("sparkLife", this.sparkLife);
    addParameter("sparkSize", this.sparkSize);
    addParameter("monochrome", this.monochrome);
    addParameter("gamma", this.gamma);
    addParameter("shape", this.shape);
    addParameter("verticalPunch", this.launch);
    addParameter("clear", this.clear);
  }

  private void onLaunch() {
    this.launchVelocity = LAUNCH_VELOCITY;
  }

  private void onClear() {
    this.cubeFire.extinguish();
    this.cylinderFire.extinguish();
  }

  /** Test-only accessor, mirroring {@link Fire#headX()}/{@link Fire#headY()}. */
  Fire cubeFire() {
    return this.cubeFire;
  }

  /** Test-only accessor, mirroring {@link Fire#headX()}/{@link Fire#headY()}. */
  Fire cylinderFire() {
    return this.cylinderFire;
  }

  @Override
  protected void render(double deltaMs) {
    // A model view is an input model, not a write mask: this pattern writes by global point
    // index through cached geometry, so every write below is guarded by isViewPoint(), and
    // the frame's clear reaches only the view rather than the whole buffer.
    updateViewMask();
    clearView();

    final Shape shape = this.shape.getEnum();
    final boolean burnCube = (shape != Shape.CYLINDER);
    final boolean burnCylinder = (shape != Shape.CUBE);

    if (burnCube) {
      this.cubeFire.attach(Apotheneum.cube.exterior, Apotheneum.cube.interior);
    }
    if (burnCylinder) {
      this.cylinderFire.attach(Apotheneum.cylinder.exterior, Apotheneum.cylinder.interior);
    }

    // Fixed timestep, so advection covers the same number of cells per second
    // regardless of what the engine frame rate happens to be.
    this.accumulatorMs += LXUtils.min(deltaMs, MAX_ACCUMULATE_MS);
    int steps = 0;
    while ((this.accumulatorMs >= SIM_MS) && (steps < MAX_SIM_STEPS)) {
      this.accumulatorMs -= SIM_MS;
      ++steps;
      simulate(burnCube, burnCylinder);
    }

    this.coreColor.update();
    this.emberColor.update();
    buildHeatCurve();
    if (burnCube) {
      this.cubeFire.render(this.colors);
    }
    if (burnCylinder) {
      this.cylinderFire.render(this.colors);
    }
  }

  private void simulate(boolean burnCube, boolean burnCylinder) {
    // Launch is a vertical impulse on an offset that arcs back to zero under gravity,
    // so it composes with whatever the Height knob and its modulators are doing.
    if ((this.launchVelocity != 0) || (this.launchOffset != 0)) {
      this.launchVelocity -= LAUNCH_GRAVITY * SIM_SECONDS;
      this.launchOffset += this.launchVelocity * SIM_SECONDS;
      if (this.launchOffset <= 0) {
        this.launchOffset = 0;
        this.launchVelocity = 0;
      }
    }

    final float azimuth = this.azimuth.getValuef();
    final float elevation =
      LXUtils.clampf(this.elevation.getValuef() + this.launchOffset, 0, 1);

    if (burnCube) {
      this.cubeFire.step(azimuth, elevation);
    }
    if (burnCylinder) {
      // Azimuth means two different things on the two shapes. The cube's 200 columns are
      // four flat walls end to end, so walking them at constant arc-length speed sweeps
      // real-world bearing non-uniformly - fast through the middle of a wall, slower past a
      // corner. The cylinder's 120 columns are a true circle, where arc-length and bearing
      // are the same thing. A constant offset between the two only lines them up at the
      // azimuth it was tuned for and drifts apart everywhere else; see
      // Fireball.Fire#bearingAt and #arcFractionForBearing.
      //
      // The cube is the reference: its walls are flat surfaces read directly by a viewer,
      // so its constant arc-length sweep is what looks like constant speed. The cylinder's
      // position is derived to match the cube's implied bearing at every azimuth, not just
      // at one calibration point. To make the cylinder the reference instead, swap which
      // side of this ternary computes cylinderAzimuth and which drives cubeFire.step above.
      final float cylinderAzimuth = burnCube
        ? this.cylinderFire.arcFractionForBearing(this.cubeFire.bearingAt(this.cubeFire.headX()))
        // Cube isn't burning (Shape=Cylinder), so there is no cube position to follow.
        // The cylinder's own azimuth already means bearing directly, so use it as-is.
        : azimuth;
      this.cylinderFire.step(cylinderAzimuth, elevation);
    }
  }

  /** Rebuilds the scalar heat curve once per frame; role colours remain per-pixel. */
  void buildHeatCurve() {
    final float gamma = this.gamma.getValuef();
    final boolean unitGamma = (gamma == 1f);
    for (int i = 0; i <= HEAT_LUT_SIZE; ++i) {
      final float heat = (float) i / HEAT_LUT_SIZE;
      this.heatCurve[i] = unitGamma ? heat : (float) Math.pow(heat, gamma);
    }
  }

  int colorHeat(ApotheneumColor.Surface surface, float heat, float physics) {
    final float shaped = this.heatCurve[
      (heat >= 1f) ? HEAT_LUT_SIZE : (int) (heat * HEAT_LUT_SIZE)
    ];
    if (this.monochrome.isOn()) {
      return LXColor.grayn(shaped);
    }

    final int blended = LXColor.lerp(
      this.emberColor.color(surface, physics), this.coreColor.color(surface, physics), shaped);
    final float saturation = LXColor.s(blended) * blackbodySaturation(shaped);
    final float roleBrightness = LXColor.b(blended) / 100f;
    final float brightness = 100f * blackbodyBrightness(shaped) *
      LXUtils.lerpf(roleBrightness, 1f, shaped);
    return LXColor.hsb(LXColor.h(blended), saturation, brightness);
  }

  /** The old Fire ramp's saturation curve, retained without its fixed hue. */
  private static float blackbodySaturation(float heat) {
    if (heat <= .45f) {
      return 1f;
    }
    if (heat <= .75f) {
      return LXUtils.lerpf(1f, .88f, (heat - .45f) / .3f);
    }
    return LXUtils.lerpf(.88f, 0f, (heat - .75f) / .25f);
  }

  /** The old Fire ramp's blackbody brightness curve, independent of palette hue. */
  private static float blackbodyBrightness(float heat) {
    if (heat <= .15f) {
      return LXUtils.lerpf(0f, .22f, heat / .15f);
    }
    if (heat <= .45f) {
      return LXUtils.lerpf(.22f, .7f, (heat - .15f) / .3f);
    }
    if (heat <= .75f) {
      return LXUtils.lerpf(.7f, 1f, (heat - .45f) / .3f);
    }
    return 1f;
  }

  /**
   * Wraps a column index onto a ring of {@code width} columns.
   *
   * <p>Deliberately not {@code LXUtils.wrap(x, 0, width - 1)}. That helper treats both
   * endpoints as inside the range, so its period is {@code width - 1}: it returns 1 rather
   * than 0 one step past the end, {@code width - 2} rather than {@code width - 1} one step
   * before the start, and drifts a further column every lap. The heat field must wrap on the
   * same period as the head position and {@link Fire#wrapDelta}, which are continuous and
   * wrap on the full width; mixing the two periods starves the seam column and double-counts
   * its neighbour. See docs/lx-coding-guidelines.md section 19.
   */
  static int wrapColumn(int x, int width) {
    return Math.floorMod(x, width);
  }

  /**
   * The float, fractional-arc-position analog of {@link #wrapColumn}: wraps {@code x} onto
   * {@code [0, width)}. {@code Math.floorMod} is int-only, and {@code x % width} keeps the
   * sign of {@code x} in Java rather than the sign of {@code width}, so a negative {@code x}
   * needs the same explicit correction {@code wrapColumn} gets from {@code floorMod}.
   */
  static float wrapFloat(float x, float width) {
    final float wrapped = x % width;
    return (wrapped < 0) ? wrapped + width : wrapped;
  }

  private static final class Spark {
    float x, y, vx, vy, life, maxLife;
  }

  /**
   * The heat field for one unwrapped surface, plus the embers burning on it.
   */
  /**
   * One heat-field simulation. It can be constructed with dimensions and usable column heights
   * for physics tests; the normal orientation attachment only supplies point indices for render.
   */
  final class Fire {

    private Apotheneum.Orientation orientation = null;
    // The corresponding interior orientation for this shape (cube or cylinder) -- stored
    // alongside pointIndex/mirrorIndex below so render() can resolve its own real
    // ApotheneumColor.Surface identity, not just build the point-index mapping.
    private Apotheneum.Orientation mirrorOrientation = null;
    private int width = 0;
    private int height = 0;

    // Two buffers rather than an in-place update: with a purely vertical upwind
    // direction a single buffer would be safe, because every source cell lies on a row
    // this pass has not written yet. Radial buoyancy breaks that - cells below the head
    // sample from above - so the read and write buffers have to be separate.
    private float[] heat = null;
    private float[] next = null;
    private int[] pointIndex = null;
    // The interior twin of each cell. 2026-08-30: colour is now resolved independently per
    // real surface via ColorNativePattern.colorizeCells (see render() below) rather than
    // computed once and mirrored -- the substance (heat, and the recomputed colorPhysics
    // noise term) is what's shared per cell, not the finished colour. Each write is still
    // independently masked and never reads colors[] back to derive the other -- see
    // colorizeCells's own javadoc for why that guarantee survives this change.
    private int[] mirrorIndex = null;
    private boolean[] usable = null;

    // Bearing (radians, unwrapped/continuous around one lap) of each column's row-0 point
    // around this surface's own center, indexed 0..width with the extra trailing entry
    // closing the loop: bearingLut[width] == bearingLut[0] + 2*PI. Built once per attach(),
    // from real geometry, so bearingAt/arcFractionForBearing never assume either surface is
    // a particular shape. See the two methods below and the comment in Fireball#simulate.
    private float[] bearingLut = null;

    private final Spark[] sparks = new Spark[SPARK_POOL];
    private int sparkCount = 0;

    private float headX, headY, prevX, prevY, velocityX, velocityY, noiseTime;
    private boolean seeded = false;

    private Fire() {
      for (int i = 0; i < this.sparks.length; ++i) {
        this.sparks[i] = new Spark();
      }
    }

    Fire(int width, int height, IntUnaryOperator available) {
      this();
      configure(width, height, available);
    }

    /**
     * Binds to an orientation, rebuilding the buffers if the model changed underneath
     * us. The identity check means this allocates on a model change and never per
     * frame.
     */
    private void attach(Apotheneum.Orientation orientation, Apotheneum.Orientation mirror) {
      if (this.orientation == orientation) {
        return;
      }
      this.orientation = orientation;
      this.mirrorOrientation = mirror;
      configure(orientation.width(), orientation.height(), orientation::available);

      for (int x = 0; x < this.width; ++x) {
        for (int y = 0; y < this.height; ++y) {
          final int i = x * this.height + y;
          this.pointIndex[i] = orientation.point(x, y).index;
          this.mirrorIndex[i] = mirror.point(x, y).index;
        }
      }
      buildBearingLut(orientation);
    }

    /**
     * Fills {@link #bearingLut} from each column's real row-0 point, around this surface's
     * own center (the mean of those same points - not the enclosing model's center, which
     * could be pulled off-axis by an unrelated fixture sharing the model). Raw
     * {@code Math.atan2} is discontinuous at +/-PI, so each entry is unwrapped against the
     * previous one - adding or subtracting a full turn as needed - to keep the array
     * monotonic and safe to interpolate. That monotonicity is not assumed: it follows from
     * the center being enclosed by a convex ring of columns (a square or a circle, either
     * way), which every Apotheneum surface is.
     */
    private void buildBearingLut(Apotheneum.Orientation orientation) {
      final Apotheneum.Column[] columns = orientation.columns();
      final int n = columns.length;
      double cx = 0;
      double cz = 0;
      for (Apotheneum.Column column : columns) {
        cx += column.points[0].x;
        cz += column.points[0].z;
      }
      cx /= n;
      cz /= n;

      final float[] lut = new float[n + 1];
      float previous = 0;
      for (int x = 0; x < n; ++x) {
        final LXPoint p = columns[x].points[0];
        float bearing = (float) Math.atan2(p.z - cz, p.x - cx);
        if (x > 0) {
          while (bearing - previous > Math.PI) {
            bearing -= TWO_PI_F;
          }
          while (bearing - previous < -Math.PI) {
            bearing += TWO_PI_F;
          }
        }
        lut[x] = bearing;
        previous = bearing;
      }
      lut[n] = lut[0] + TWO_PI_F;
      this.bearingLut = lut;
    }

    /**
     * The true compass bearing (radians, unwrapped) at fractional arc position {@code x}
     * (column units, any real value - wrapped onto this ring before use), linearly
     * interpolated between the two columns straddling it.
     */
    float bearingAt(float x) {
      final float wrapped = wrapFloat(x, this.width);
      final int i0 = (int) Math.floor(wrapped);
      final float frac = wrapped - i0;
      return LXUtils.lerpf(this.bearingLut[i0], this.bearingLut[i0 + 1], frac);
    }

    /**
     * Inverse of {@link #bearingAt}: the fractional azimuth (0-1, matching what
     * {@link #step} expects) on this ring whose bearing matches {@code bearing} (radians,
     * any winding). Binary search over {@link #bearingLut}, which is monotonic but not
     * necessarily uniform - arc-length and bearing agree exactly only on a circle.
     */
    float arcFractionForBearing(float bearing) {
      final float base = this.bearingLut[0];
      float target = bearing - TWO_PI_F * (float) Math.floor((bearing - base) / TWO_PI_F);

      int lo = 0;
      int hi = this.width;
      while (hi - lo > 1) {
        final int mid = (lo + hi) >>> 1;
        if (this.bearingLut[mid] <= target) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      final float span = this.bearingLut[hi] - this.bearingLut[lo];
      final float frac = (span > 0) ? (target - this.bearingLut[lo]) / span : 0;
      return wrapFloat(lo + frac, this.width) / this.width;
    }

    private void configure(int width, int height, IntUnaryOperator available) {
      this.width = width;
      this.height = height;
      final int cells = width * height;
      this.heat = new float[cells];
      this.next = new float[cells];
      this.pointIndex = new int[cells];
      this.mirrorIndex = new int[cells];
      this.usable = new boolean[cells];
      for (int x = 0; x < width; ++x) {
        final int usableHeight = available.applyAsInt(x);
        for (int y = 0; y < height; ++y) {
          this.usable[x * height + y] = (y < usableHeight);
        }
      }
      this.sparkCount = 0;
      this.seeded = false;
      this.noiseTime = 0;
    }

    private void extinguish() {
      if (this.heat != null) {
        Arrays.fill(this.heat, 0f);
        Arrays.fill(this.next, 0f);
      }
      this.sparkCount = 0;
    }

    /** Shortest signed distance between two columns, accounting for the ring wrap. */
    private float wrapDelta(float dx) {
      final float half = this.width * .5f;
      while (dx > half) {
        dx -= this.width;
      }
      while (dx < -half) {
        dx += this.width;
      }
      return dx;
    }

    void step(float azimuth, float elevation) {
      final float x = azimuth * this.width;
      final float y = (1 - elevation) * (this.height - 1);
      if (!this.seeded) {
        this.headX = x;
        this.headY = y;
        this.seeded = true;
      }
      this.prevX = this.headX;
      this.prevY = this.headY;
      this.headX = x;
      this.headY = y;
      this.velocityX = wrapDelta(this.headX - this.prevX) / SIM_SECONDS;
      this.velocityY = (this.headY - this.prevY) / SIM_SECONDS;

      advect();
      stampHead();
      updateSparks();
    }

    private void advect() {
      final float decay =
        (float) Math.pow(.5, SIM_SECONDS * cooling.getValuef() / BASE_HALF_LIFE);
      final float jitterScale = turbulence.getValuef();
      final float rise = buoyancy.getValuef();
      final float auraRadius = auraSize.getValuef();
      final float headX = this.headX;
      final float headY = this.headY;
      final int w = this.width;
      final int h = this.height;
      final float[] heat = this.heat;
      final float[] next = this.next;
      final float advection = ADVECTION_SPEED * SIM_SECONDS;
      this.noiseTime += SIM_SECONDS;

      for (int x = 0; x < w; ++x) {
        final int column = x * h;
        for (int y = 0; y < h; ++y) {
          final int dst = column + y;
          if (!this.usable[dst]) {
            next[dst] = 0f;
            continue;
          }

          // Direction the heat travels: radially outward from the head where the
          // corona dominates, vertically upward everywhere else.
          final float offsetX = wrapDelta(x - headX);
          final float offsetY = y - headY;
          final float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);

          float dirX;
          float dirY;
          if (distance < 1e-3f) {
            dirX = 0f;
            dirY = -1f;
          } else {
            final float radialWeight = (float) Math.exp(-distance / auraRadius);
            final float mix = rise + (1 - rise) * (1 - radialWeight);
            dirX = LXUtils.lerpf(offsetX / distance, 0f, mix);
            dirY = LXUtils.lerpf(offsetY / distance, -1f, mix);
            final float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (length < 1e-4f) {
              dirX = 0f;
              dirY = -1f;
            } else {
              dirX /= length;
              dirY /= length;
            }
          }

          // Backtrace continuously through the flow field. Signed coherent noise
          // (empirically LXUtils.noise is approximately -1..1) perturbs only the
          // lateral axis, keeping neighboring samples coherent enough to form tongues
          // instead of the horizontal static created by per-cell white noise.
          final float turbulence = LXUtils.noise(
            x * TURBULENCE_NOISE_SCALE,
            y * TURBULENCE_NOISE_SCALE,
            this.noiseTime
          ) * jitterScale;
          final float sourceX = x - dirX * advection - dirY * turbulence;
          final float sourceY = y - dirY * advection + dirX * turbulence;
          final float source = sampleBilinear(heat, sourceX, sourceY, dst);

          final float value = source * decay;
          next[dst] = (value > HEAT_EPSILON) ? value : 0f;
        }
      }

      final float[] swap = this.heat;
      this.heat = this.next;
      this.next = swap;
    }

    /**
     * Bilinearly samples an unwrapped heat field. X wraps around the chamber; Y is
     * clamped at its physical bounds. A door sample falls back to the destination's
     * current heat, preventing a dark notch at the top of a door column.
     */
    private float sampleBilinear(float[] heat, float x, float y, int destination) {
      if ((y < 0) || (y >= this.height)) {
        return 0f;
      }

      final int x0 = (int) Math.floor(x);
      final int wrappedX0 = wrapColumn(x0, this.width);
      final int wrappedX1 = wrapColumn(x0 + 1, this.width);
      final int y0 = (int) Math.floor(y);
      final int y1 = LXUtils.min(y0 + 1, this.height - 1);
      final float tx = x - x0;
      final float ty = y - y0;
      final float a = sourceHeat(heat, wrappedX0, y0, destination);
      final float b = sourceHeat(heat, wrappedX1, y0, destination);
      final float c = sourceHeat(heat, wrappedX0, y1, destination);
      final float d = sourceHeat(heat, wrappedX1, y1, destination);
      return LXUtils.lerpf(LXUtils.lerpf(a, b, tx), LXUtils.lerpf(c, d, tx), ty);
    }

    private float sourceHeat(float[] heat, int x, int y, int destination) {
      final int source = x * this.height + y;
      return this.usable[source] ? heat[source] : heat[destination];
    }

    private void stampHead() {
      final float peak = intensity.getValuef();
      final float core = coreSize.getValuef();
      final float aura = auraSize.getValuef();

      // Stamp along the segment travelled this tick, not just at the endpoint, so a
      // fast fireball leaves a continuous streak instead of a dotted line.
      final float deltaX = wrapDelta(this.headX - this.prevX);
      final float deltaY = this.headY - this.prevY;
      final float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
      final int steps = 1 + (int) distance;

      // A slow flicker on the corona so it breathes even when the fireball is parked.
      final float flicker = .82f + .18f * Fireball.this.random.nextFloat();

      for (int i = 0; i <= steps; ++i) {
        final float t = (float) i / steps;
        final float x = this.prevX + deltaX * t;
        final float y = this.prevY + deltaY * t;
        splat(x, y, aura * flicker, peak * .35f, false);
        splat(x, y, core, peak, false);
      }
    }

    /**
     * Paints a radial blob of heat. {@code additive} sums into the field so clustered
     * embers read hotter; otherwise the maximum is taken, which keeps the overlapping
     * stamps along a motion segment from blowing out.
     */
    private void splat(float centerX, float centerY, float radius, float peak, boolean additive) {
      if ((radius <= 0) || (peak <= 0)) {
        return;
      }
      final int reach = (int) Math.ceil(radius);
      final int minY = LXUtils.max(0, (int) Math.floor(centerY) - reach);
      final int maxY = LXUtils.min(this.height - 1, (int) Math.ceil(centerY) + reach);
      final int minX = (int) Math.floor(centerX) - reach;
      final int maxX = (int) Math.ceil(centerX) + reach;
      final float inverseSquared = 1f / (radius * radius);

      for (int ix = minX; ix <= maxX; ++ix) {
        final float dx = ix - centerX;
        final int column = wrapColumn(ix, this.width) * this.height;
        for (int y = minY; y <= maxY; ++y) {
          final int i = column + y;
          if (!this.usable[i]) {
            continue;
          }
          final float dy = y - centerY;
          final float normalized = (dx * dx + dy * dy) * inverseSquared;
          if (normalized >= 1f) {
            continue;
          }
          final float falloff = 1f - normalized;
          final float value = peak * falloff * falloff;
          if (additive) {
            final float sum = this.heat[i] + value;
            this.heat[i] = (sum > 1f) ? 1f : sum;
          } else if (value > this.heat[i]) {
            this.heat[i] = value;
          }
        }
      }
    }

    private void updateSparks() {
      final int target = sparkDensity.getValuei();
      final float maxLife = sparkLife.getValuef();
      final Random random = Fireball.this.random;

      // Deficit spawning at the head, so the emission rate rises naturally whenever
      // sparks are dying faster - which is what happens when the fireball moves.
      if (this.sparkCount < target) {
        final int deficit = target - this.sparkCount;
        int spawn = 1 + (int) (deficit * .08f);
        while ((spawn-- > 0) && (this.sparkCount < target)) {
          final Spark spark = this.sparks[this.sparkCount++];
          spark.x = this.headX + (random.nextFloat() * 2f - 1f) * coreSize.getValuef();
          spark.y = this.headY - random.nextFloat() * coreSize.getValuef();
          spark.vx = this.velocityX * SPARK_INHERIT + (random.nextFloat() * 12f - 6f);
          spark.vy = Math.min(
            this.velocityY * SPARK_INHERIT - (2f + random.nextFloat() * 4f),
            SPARK_MIN_RISE_VELOCITY
          );
          spark.maxLife = maxLife * (.5f + random.nextFloat() * .5f);
          spark.life = spark.maxLife;
        }
      }

      final float damping = (float) Math.pow(SPARK_DAMPING, SIM_SECONDS);

      int i = 0;
      while (i < this.sparkCount) {
        final Spark spark = this.sparks[i];
        spark.life -= SIM_SECONDS;

        spark.vy += SPARK_BUOYANCY * SIM_SECONDS;
        spark.vx *= damping;
        spark.vy *= damping;
        spark.x += spark.vx * SIM_SECONDS;
        spark.y += spark.vy * SIM_SECONDS;

        boolean dead = (spark.life <= 0) || (i >= target);
        if (!dead) {
          final int cellX = wrapColumn(Math.round(spark.x), this.width);
          final int cellY = Math.round(spark.y);
          if ((cellY < 0) || (cellY >= this.height)
            || !this.usable[cellX * this.height + cellY]) {
            dead = true;
          }
        }

        if (dead) {
          // Swap-remove keeps the live sparks packed at the head of the pool without
          // allocating anything.
          this.sparks[i] = this.sparks[--this.sparkCount];
          this.sparks[this.sparkCount] = spark;
          continue;
        }

        ++i;
      }
    }

    private void render(int[] colors) {
      if (this.heat == null) {
        return;
      }
      // 2026-08-30: colour is resolved independently per real surface via
      // ColorNativePattern.colorizeCells, rather than computed once and mirrored -- see this
      // class's own javadoc and docs/color-native-pattern-substance.md. heat[] is the shared
      // substance every real point's colour derives from; colorPhysics(x, y) is a pure
      // function of (x, y, this.noiseTime) recomputed fresh rather than stored, since storing
      // it would cost the same as recomputing it and there is nothing else that reads it.
      final ApotheneumColor.Surface exteriorSurface = ApotheneumColor.Surface.of(this.orientation);
      final ApotheneumColor.Surface interiorSurface = ApotheneumColor.Surface.of(this.mirrorOrientation);
      final float[] heat = this.heat;
      final boolean[] usable = this.usable;
      final int height = this.height;
      Fireball.this.colorizeCells(
        this.width * height,
        this.pointIndex,
        exteriorSurface,
        this.mirrorIndex,
        interiorSurface,
        (surface, cell) -> {
          if (!usable[cell]) {
            return LXColor.BLACK;
          }
          final float value = heat[cell];
          if (value <= HEAT_EPSILON) {
            return LXColor.BLACK;
          }
          return Fireball.this.colorHeat(surface, value, colorPhysics(cell / height, cell % height));
        }
      );
      renderSparks(colors, exteriorSurface, interiorSurface);
    }

    /**
     * As {@code colorizeCells}'s dual write, but an ember only ever brightens a cell, never
     * dims one -- each surface's own resolved brightness is compared against its own
     * destination's current value, independently. Before this class adopted
     * {@code colorizeCells}, both surfaces shared one resolved colour and therefore one
     * brightness threshold; now that exterior and interior can genuinely differ (e.g. under
     * {@code ApotheneumColor}'s In/Out axis), comparing each surface against its own value is
     * the more correct behaviour, not merely an equivalent one.
     */
    private void paintBrighter(int[] colors, int cell, int exteriorColor, int interiorColor) {
      final int exterior = this.pointIndex[cell];
      if (Fireball.this.isViewPoint(exterior) && (LXColor.b(exteriorColor) > LXColor.b(colors[exterior]))) {
        colors[exterior] = exteriorColor;
      }
      final int interior = this.mirrorIndex[cell];
      if (Fireball.this.isViewPoint(interior) && (LXColor.b(interiorColor) > LXColor.b(colors[interior]))) {
        colors[interior] = interiorColor;
      }
    }

    /** Draws each live ember only at its current position, never into the heat field. */
    private void renderSparks(
      int[] colors, ApotheneumColor.Surface exteriorSurface, ApotheneumColor.Surface interiorSurface
    ) {
      final float radius = sparkSize.getValuef();
      final float peak = intensity.getValuef();
      final int reach = (int) Math.ceil(radius);

      for (int sparkIndex = 0; sparkIndex < this.sparkCount; ++sparkIndex) {
        final Spark spark = this.sparks[sparkIndex];
        final float age = spark.life / spark.maxLife;
        final float sparkPeak = peak * (float) Math.pow(age, .7);
        final int minY = LXUtils.max(0, (int) Math.floor(spark.y) - reach);
        final int maxY = LXUtils.min(this.height - 1, (int) Math.ceil(spark.y) + reach);
        final int minX = (int) Math.floor(spark.x) - reach;
        final int maxX = (int) Math.ceil(spark.x) + reach;
        final float inverseSquared = 1f / (radius * radius);

        for (int x = minX; x <= maxX; ++x) {
          final float dx = x - spark.x;
          final int column = wrapColumn(x, this.width) * this.height;
          for (int y = minY; y <= maxY; ++y) {
            final int cell = column + y;
            if (!this.usable[cell]) {
              continue;
            }
            final float dy = y - spark.y;
            final float normalized = (dx * dx + dy * dy) * inverseSquared;
            if (normalized >= 1f) {
              continue;
            }
            final float heat = sparkPeak * (1f - normalized) * (1f - normalized);
            if (heat <= HEAT_EPSILON) {
              continue;
            }
            final float physics = colorPhysics(x, y);
            paintBrighter(
              colors, cell,
              Fireball.this.colorHeat(exteriorSurface, heat, physics),
              Fireball.this.colorHeat(interiorSurface, heat, physics)
            );
          }
        }
      }
    }

    /** The same coherent noise field that laterally perturbs advection, normalized to [-1, 1]. */
    private float colorPhysics(float x, float y) {
      return LXUtils.noise(x * TURBULENCE_NOISE_SCALE, y * TURBULENCE_NOISE_SCALE, this.noiseTime);
    }

    float heatAt(int x, int y) {
      return this.heat[x * this.height + y];
    }

    boolean usableAt(int x, int y) {
      return this.usable[x * this.height + y];
    }

    int sparkCount() {
      return this.sparkCount;
    }

    boolean liveSparksArePacked() {
      for (int i = 0; i < this.sparkCount; ++i) {
        if (this.sparks[i].life <= 0) {
          return false;
        }
      }
      return true;
    }

    float headX() {
      return this.headX;
    }

    float headY() {
      return this.headY;
    }

    int width() {
      return this.width;
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Motion",
      newKnob(this.azimuth),
      newKnob(this.elevation)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Head",
      newKnob(this.coreSize),
      newKnob(this.auraSize),
      newKnob(this.intensity)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Fire",
      newKnob(this.cooling),
      newKnob(this.turbulence),
      newKnob(this.buoyancy)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Sparks",
      newKnob(this.sparkDensity),
      newKnob(this.sparkLife),
      newKnob(this.sparkSize)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Render",
      newButton(this.monochrome),
      newKnob(this.gamma),
      newDropMenu(this.shape)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Actions",
      newButton(this.launch).setTriggerable(true),
      newButton(this.clear).setTriggerable(true)).setChildSpacing(6);

    buildColorDeviceControls(ui, uiDevice);
  }
}
