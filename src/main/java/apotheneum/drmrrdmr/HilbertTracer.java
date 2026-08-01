package apotheneum.drmrrdmr;

import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Traces a Hilbert curve on each surface using bright, decaying pulses -
 * there's no room for a literal background at this resolution, so the curve
 * itself is only ever revealed by where the pulses have recently been.
 *
 * Each cube face and the cylinder independently generate their own Hilbert
 * curve (own geometry mapping, own trail, own pulses - so they never look
 * synchronized), gridded at up to 64x64 cells:
 *
 *  - Depth 1 sizes the curve to fit inside the surface with a small margin
 *    (each cell drawn as a wide block of LEDs).
 *  - Depth 2 and up instead size the curve so it fills and crops the whole
 *    surface - the same strategy depth 6 already used naturally, once the
 *    curve grid exceeds the physical grid.
 *  - The cylinder tiles its curve horizontally to cover the full
 *    circumference (clipping the last, partial repeat at the edge) rather
 *    than centering one curve in a sea of black, since it's much wider than
 *    it is tall.
 *
 * Below depth 5, where cells are wide enough to matter, two things soften
 * the "blocky" look: a sinusoidal brightness falloff from each block's
 * center out to an adjustable minimum, and pulses that step through those
 * blocks one physical LED at a time (via a boustrophedon sub-path) instead
 * of lighting an entire block the instant the curve reaches it.
 */
@LXCategory("Apotheneum/drmrrdmr")
@LXComponent.Name("Hilbert Tracer")
public class HilbertTracer extends ApotheneumPattern {

  private static final int MIN_ORDER = 1;
  private static final int MAX_ORDER = 6; // 2^6 = 64, the "up to 64x64" cap
  private static final int MAX_PULSES = 128;
  private static final int SPEED_REFERENCE_ORDER = 4; // order at which the Speed knob applies unscaled
  private static final int GRADIENT_MAX_ORDER = 5; // gradient + per-LED stepping apply for order < this

  // Low-discrepancy per-pulse phase offset for Equidistant mode (see
  // respacePulses). Irrational, so i*GOLDEN_RATIO_CONJUGATE mod 1 never
  // repeats or lines back up with another pulse's offset.
  private static final float GOLDEN_RATIO_CONJUGATE = 0.6180339887f;

  private final CompoundParameter depth = new CompoundParameter("Depth", 4, MIN_ORDER, MAX_ORDER)
    .setDescription("Hilbert curve iteration depth - continuous knob, floored to an integer. Depth 1 widens each curve cell to fit the surface with a small margin; depth 2 and up fill and crop the whole surface, same as depth 6");

  private final CompoundDiscreteParameter density = new CompoundDiscreteParameter("Density", 3, 1, MAX_PULSES + 1)
    .setDescription("Number of simultaneous pulses tracing each surface");

  private final CompoundParameter speed = new CompoundParameter("Speed", 2.5, -10, 10)
    .setDescription("Pulse traversal speed, in curve-cells/second at depth 4. Scales with iteration depth so higher-depth curves (far more cells) still trace at a comparable pace. Negative reverses direction");

  private final BooleanParameter overdrive = new BooleanParameter("Overdrive", false)
    .setDescription("Multiplies Speed by 10x");

  private final CompoundParameter decay = new CompoundParameter("Decay", 1.0, 0.05, 8.0)
    .setDescription("Trail decay rate - higher fades the trail faster");

  private final BooleanParameter equidistant = new BooleanParameter("Equidistant", false)
    .setDescription("Place pulses evenly around the curve (closest achievable approximation) instead of at random positions");

  private final CompoundParameter gradientMin = new CompoundParameter("Grad Min", 0.2, 0.0, 1.0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Minimum brightness of the sinusoidal falloff away from the curve line, at iteration depths below 5");

  private static class Pulse {
    float pos;
  }

  private static class TracerSurface {
    LXPoint[] points;
    int[] pointPathIndex;
    float[] pointGradientCos;
    boolean gradientEnabled;
    int cellSize;
    float[] trail;
    boolean[] freshlyPainted; // which trail slots a pulse touched THIS frame
    int pathLength;
    final Pulse[] pulses = new Pulse[MAX_PULSES];

    // Persist across geometry rebuilds (unlike everything above, which gets
    // replaced whenever the depth knob changes) - indexed by physical point,
    // not by trail slot, so a depth change can't invalidate them.
    float[] lastBrightness;
    float[] fadeout;

    TracerSurface() {
      for (int i = 0; i < MAX_PULSES; ++i) {
        this.pulses[i] = new Pulse();
      }
    }
  }

  private final TracerSurface cubeFront = new TracerSurface();
  private final TracerSurface cubeRight = new TracerSurface();
  private final TracerSurface cubeBack = new TracerSurface();
  private final TracerSurface cubeLeft = new TracerSurface();
  private final TracerSurface cylinderSurface = new TracerSurface();

  private int builtOrder = -1;
  private int lastDensity = -1;
  private boolean lastEquidistant = false;

  public HilbertTracer(LX lx) {
    super(lx);
    addParameter("Depth", this.depth);
    addParameter("Density", this.density);
    addParameter("Speed", this.speed);
    addParameter("Overdrive", this.overdrive);
    addParameter("Decay", this.decay);
    addParameter("Equidistant", this.equidistant);
    addParameter("Grad Min", this.gradientMin);
  }

  @Override
  protected void onModelChanged(LXModel model) {
    this.builtOrder = -1;
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) (deltaMs / 1000.0);

    int order = LXUtils.constrain((int) Math.floor(this.depth.getValuef()), MIN_ORDER, MAX_ORDER);
    int activeDensity = this.density.getValuei();
    boolean equidistantOn = this.equidistant.isOn();

    Cube cube = Apotheneum.cube;
    Cylinder cylinder = Apotheneum.cylinder;

    boolean orderChanged = (order != this.builtOrder);
    if (orderChanged) {
      if (cube != null) {
        buildSurface(this.cubeFront, cube.exterior.front.columns, order, false);
        buildSurface(this.cubeRight, cube.exterior.right.columns, order, false);
        buildSurface(this.cubeBack, cube.exterior.back.columns, order, false);
        buildSurface(this.cubeLeft, cube.exterior.left.columns, order, false);
      }
      if (cylinder != null) {
        buildSurface(this.cylinderSurface, cylinder.exterior.columns, order, true);
      }
      this.builtOrder = order;
    }

    if (orderChanged || (activeDensity != this.lastDensity) || (equidistantOn != this.lastEquidistant)) {
      respacePulses(this.cubeFront, activeDensity, equidistantOn);
      respacePulses(this.cubeRight, activeDensity, equidistantOn);
      respacePulses(this.cubeBack, activeDensity, equidistantOn);
      respacePulses(this.cubeLeft, activeDensity, equidistantOn);
      respacePulses(this.cylinderSurface, activeDensity, equidistantOn);
      this.lastDensity = activeDensity;
      this.lastEquidistant = equidistantOn;
    }

    float speedScale = (1 << order) / (float) (1 << SPEED_REFERENCE_ORDER);
    float overdriveMult = this.overdrive.isOn() ? 10f : 1f;
    float cellsPerSecond = this.speed.getValuef() * speedScale * overdriveMult;
    float decayFactor = (float) Math.exp(-this.decay.getValuef() * dt);
    float gradMinFrac = this.gradientMin.getValuef();

    if (cube != null) {
      updateAndRenderSurface(this.cubeFront, cellsPerSecond, dt, decayFactor, activeDensity, gradMinFrac);
      updateAndRenderSurface(this.cubeRight, cellsPerSecond, dt, decayFactor, activeDensity, gradMinFrac);
      updateAndRenderSurface(this.cubeBack, cellsPerSecond, dt, decayFactor, activeDensity, gradMinFrac);
      updateAndRenderSurface(this.cubeLeft, cellsPerSecond, dt, decayFactor, activeDensity, gradMinFrac);
      if (cube.interior != null) {
        copyCubeExterior();
      }
    }

    if (cylinder != null) {
      updateAndRenderSurface(this.cylinderSurface, cellsPerSecond, dt, decayFactor, activeDensity, gradMinFrac);
      if (cylinder.interior != null) {
        copyCylinderExterior();
      }
    }
  }

  private static int ceilDiv(int a, int b) {
    return (a + b - 1) / b;
  }

  /**
   * Builds the curve for one surface: sizes/crops (or tiles) it against that
   * surface's physical grid, and maps every physical LED to a trail slot -
   * either one slot per curve cell (depth >= 5), or, for depth < 5, one slot
   * per individual LED along a boustrophedon sub-path through each enlarged
   * cell, so pulses can step through a block one LED at a time. Only runs
   * when the iteration depth knob's floored value changes.
   */
  private void buildSurface(TracerSurface surface, Apotheneum.Column[] columns, int order, boolean tile) {
    int width = columns.length;
    int height = columns[0].points.length;
    int n = 1 << order;

    boolean gradientEnabled = order < GRADIENT_MAX_ORDER;

    int cellSize;
    if (tile) {
      // Cylinder: size off height alone - width is filled by tiling this square.
      cellSize = (order <= 1) ? Math.max(1, height / n) : Math.max(1, ceilDiv(height, n));
    } else {
      // Cube face: depth 1 fits-with-margin (min dimension, floor); depth 2+
      // fills-and-crops the whole face (max dimension, ceiling) - same as
      // depth 6 already did once the curve outgrew the face.
      cellSize = (order <= 1)
        ? Math.max(1, Math.min(width, height) / n)
        : Math.max(1, ceilDiv(Math.max(width, height), n));
    }

    int usedW = n * cellSize; // also the tile width, when tiling
    int usedH = n * cellSize;
    int offsetX = tile ? 0 : (width - usedW) / 2;
    int offsetY = (height - usedH) / 2;

    int total = n * n;
    int[] hx = new int[total];
    int[] hy = new int[total];
    hilbertXY(order, hx, hy);

    int pathLength;
    int[] pathIndexGrid = new int[usedW * usedH]; // [px * usedH + py] -> trail slot

    if (gradientEnabled) {
      pathLength = usedW * usedH;
      int k = 0;
      for (int d = 0; d < total; ++d) {
        int baseX = hx[d] * cellSize;
        int baseY = hy[d] * cellSize;
        for (int ly = 0; ly < cellSize; ++ly) {
          boolean forward = (ly % 2) == 0;
          for (int s = 0; s < cellSize; ++s) {
            int lx = forward ? s : (cellSize - 1 - s);
            pathIndexGrid[(baseX + lx) * usedH + (baseY + ly)] = k++;
          }
        }
      }
    } else {
      pathLength = total;
      for (int d = 0; d < total; ++d) {
        int baseX = hx[d] * cellSize;
        int baseY = hy[d] * cellSize;
        for (int ly = 0; ly < cellSize; ++ly) {
          for (int lx = 0; lx < cellSize; ++lx) {
            pathIndexGrid[(baseX + lx) * usedH + (baseY + ly)] = d;
          }
        }
      }
    }

    int numPoints = width * height;
    LXPoint[] points = new LXPoint[numPoints];
    int[] pointPathIndex = new int[numPoints];
    float[] pointGradientCos = gradientEnabled ? new float[numPoints] : null;

    float halfCell = cellSize / 2f;
    float center = (cellSize - 1) / 2f;

    int i = 0;
    for (int col = 0; col < width; ++col) {
      Apotheneum.Column column = columns[col];
      int cx = tile ? Math.floorMod(col, usedW) : (col - offsetX);
      boolean colInRange = tile || ((cx >= 0) && (cx < usedW));
      for (int row = 0; row < height; ++row) {
        points[i] = column.points[row];
        int cy = row - offsetY;
        if (colInRange && (cy >= 0) && (cy < usedH)) {
          pointPathIndex[i] = pathIndexGrid[cx * usedH + cy];
          if (gradientEnabled) {
            int lx = cx % cellSize;
            int ly = cy % cellSize;
            float dx = lx - center;
            float dy = ly - center;
            float d = Math.max(Math.abs(dx), Math.abs(dy)) / Math.max(halfCell, 1e-6f);
            d = LXUtils.clampf(d, 0f, 1f);
            pointGradientCos[i] = (float) Math.cos(d * (Math.PI / 2.0));
          }
        } else {
          pointPathIndex[i] = -1;
        }
        ++i;
      }
    }

    // A depth change replaces the trail/mapping above outright - it has to,
    // the geometry is different now. But the LEDs themselves shouldn't just
    // cut to black: fold whatever was actually lit under the outgoing
    // mapping into the (physical-point-indexed, rebuild-proof) fadeout
    // buffer, so it keeps decaying on its own while the new depth's pulses
    // build up their own picture from scratch.
    if ((surface.lastBrightness != null) && (surface.lastBrightness.length == numPoints)) {
      for (int j = 0; j < numPoints; ++j) {
        if (surface.lastBrightness[j] > surface.fadeout[j]) {
          surface.fadeout[j] = surface.lastBrightness[j];
        }
      }
    } else {
      surface.lastBrightness = new float[numPoints];
      surface.fadeout = new float[numPoints];
    }

    surface.points = points;
    surface.pointPathIndex = pointPathIndex;
    surface.pointGradientCos = pointGradientCos;
    surface.gradientEnabled = gradientEnabled;
    surface.cellSize = cellSize;
    surface.pathLength = pathLength;
    surface.trail = new float[pathLength];
    surface.freshlyPainted = new boolean[pathLength];
  }

  /**
   * Standard iterative Hilbert d->(x,y) conversion. n = 2^order is the
   * curve's side length; outX/outY (length n*n) receive the cell visited at
   * each step of the curve.
   */
  private static void hilbertXY(int order, int[] outX, int[] outY) {
    int n = 1 << order;
    int total = n * n;
    for (int d = 0; d < total; ++d) {
      int t = d;
      int x = 0, y = 0;
      for (int s = 1; s < n; s *= 2) {
        int rx = 1 & (t / 2);
        int ry = 1 & (t ^ rx);
        if (ry == 0) {
          if (rx == 1) {
            x = s - 1 - x;
            y = s - 1 - y;
          }
          int tmp = x;
          x = y;
          y = tmp;
        }
        x += s * rx;
        y += s * ry;
        t /= 4;
      }
      outX[d] = x;
      outY[d] = y;
    }
  }

  private void respacePulses(TracerSurface surface, int activeDensity, boolean equidistantOn) {
    int pathLength = surface.pathLength;
    if (pathLength <= 0) {
      return;
    }
    int active = Math.min(activeDensity, MAX_PULSES);
    for (int i = 0; i < active; ++i) {
      float pos;
      if (equidistantOn) {
        float basePos = i * (pathLength / (float) active);
        // Every pulse moves at the same speed, so exactly-even spacing means
        // every pulse sits at the identical fractional offset relative to
        // the trail's discretization grid - whenever that spacing divides
        // evenly (very common when Density is a power of 2, since pathLength
        // is built from powers of 2 too), every pulse crosses its next
        // block/LED boundary on the *same* rendered frame. The whole surface
        // then visibly steps once every few frames instead of flowing
        // continuously, even though the actual frame rate is unaffected.
        // A small per-pulse sub-unit phase offset (low-discrepancy, so it
        // never re-aligns) keeps the pulses effectively evenly spaced while
        // staggering exactly when each one crosses a boundary.
        float jitter = ((i * GOLDEN_RATIO_CONJUGATE) % 1f) - 0.5f;
        pos = basePos + jitter;
      } else {
        pos = (float) (Math.random() * pathLength);
      }
      if (pos < 0) {
        pos += pathLength;
      } else if (pos >= pathLength) {
        pos -= pathLength;
      }
      surface.pulses[i].pos = pos;
    }
  }

  private void updateAndRenderSurface(TracerSurface surface, float cellsPerSecond, float dt, float decayFactor, int activeDensity, float gradMinFrac) {
    float[] trail = surface.trail;
    if (trail == null) {
      return;
    }
    int pathLength = surface.pathLength;

    for (int i = 0; i < trail.length; ++i) {
      trail[i] *= decayFactor;
    }

    boolean[] freshlyPainted = surface.freshlyPainted;
    for (int i = 0; i < freshlyPainted.length; ++i) {
      freshlyPainted[i] = false;
    }

    float[] fadeout = surface.fadeout;
    for (int i = 0; i < fadeout.length; ++i) {
      fadeout[i] *= decayFactor;
    }

    // Cells/second is the pattern's stable unit of pace; surfaces whose
    // trail is indexed per-LED (gradient depths) need proportionally more
    // trail-slots/second to cover one cell in the same amount of time.
    float unitsPerCell = surface.gradientEnabled ? (float) (surface.cellSize * surface.cellSize) : 1f;
    float delta = cellsPerSecond * unitsPerCell * dt;
    if (delta > pathLength) {
      delta = pathLength;
    } else if (delta < -pathLength) {
      delta = -pathLength;
    }

    int active = Math.min(activeDensity, MAX_PULSES);
    for (int i = 0; i < active; ++i) {
      Pulse pulse = surface.pulses[i];
      float a = pulse.pos;

      // Paint every slot the pulse sweeps through this frame, not just its
      // endpoint - at high speeds a pulse can move many slots per frame, and
      // only marking the endpoint would leave the trail dotted with gaps
      // instead of tracing continuously.
      int steps = (int) Math.ceil(Math.abs(delta));
      if (steps < 1) {
        steps = 1;
      }
      for (int s = 0; s <= steps; ++s) {
        float t = (steps == 0) ? 0f : ((float) s / steps);
        float p = a + delta * t;
        int idx = (int) Math.floor(p) % pathLength;
        if (idx < 0) {
          idx += pathLength;
        }
        trail[idx] = 1f;
        freshlyPainted[idx] = true;
      }

      float newPos = (a + delta) % pathLength;
      if (newPos < 0) {
        newPos += pathLength;
      }
      pulse.pos = newPos;
    }

    LXPoint[] points = surface.points;
    int[] pointPathIndex = surface.pointPathIndex;
    float[] gradCos = surface.pointGradientCos;
    float[] lastBrightness = surface.lastBrightness;
    boolean grad = surface.gradientEnabled;
    for (int i = 0; i < points.length; ++i) {
      int ci = pointPathIndex[i];
      float b = (ci < 0) ? 0f : trail[ci];
      // Only shape the decaying trail with the position-based gradient - a
      // slot a pulse is touching THIS frame shows at full, undimmed
      // brightness first, and only takes on the gradient's falloff once it
      // starts decaying on subsequent frames.
      if (grad && (ci >= 0) && !freshlyPainted[ci]) {
        b *= gradMinFrac + (1f - gradMinFrac) * gradCos[i];
      }
      // Whichever is brighter: the current depth's own trail, or the
      // still-fading remnant of whatever depth was showing before it.
      float finalB = Math.max(b, fadeout[i]);
      lastBrightness[i] = finalB;
      colors[points[i].index] = LXColor.gray(LXUtils.clampf(finalB * 100f, 0f, 100f));
    }
  }

}
