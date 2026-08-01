package apotheneum.drmrrdmr;

import apotheneum.ApotheneumPattern;
import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.Apotheneum.Cylinder.Ring;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

/**
 * A generalized Newton-fractal pattern: rather than the Mandelbrot/Julia sets
 * most fractal LED patterns lean on, this finds the roots of z^N - c(t) by
 * Newton's method and colors each pixel by which root it falls toward and
 * how smoothly/quickly it gets there. That produces exactly what a 50x50
 * grid wants - broad, continuously-varying basins of color (no fine detail
 * needed to read the structure) with lacy, fast-changing filigree only along
 * the basin boundaries (which is where the "sparkle" comes from as things
 * animate). The roots themselves slowly rotate around the unit circle
 * (c(t) = e^{i*N*rootPhase(t)}), which continuously redraws the whole basin
 * structure rather than just recoloring a static image. "Twist" rotates the
 * Newton correction step itself (a generalized/relaxed Newton iteration) -
 * away from 0 it warps the classic petal-shaped basins into swirling,
 * asymmetric ones, which is the "less commonly known" part: almost nobody
 * renders this variant even though it's a one-line change to the iteration.
 */
@LXCategory("Apotheneum/drmrrdmr")
@LXComponent.Name("Ex Machina")
public class ExMachina extends ApotheneumPattern {

  private final CompoundDiscreteParameter roots = new CompoundDiscreteParameter("Roots", 3, 2, 9)
    .setDescription("Number of roots of z^N - c(t) - the fractal's basic symmetry");

  private final CompoundParameter rotate = new CompoundParameter("Rotate", 8.0, -60.0, 60.0)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Degrees/second the roots drift around the unit circle - the main driver of continuous change. Negative reverses direction");

  private final CompoundParameter twist = new CompoundParameter("Twist", 0.0, -50.0, 50.0)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Rotates the Newton correction step itself (a generalized/relaxed Newton iteration) - away from 0 warps the classic basins into asymmetric swirls");

  private final CompoundParameter zoom = new CompoundParameter("Zoom", 1.2, 0.3, 4.0)
    .setDescription("View scale - the interesting structure lives around the unit circle");

  private final CompoundDiscreteParameter detail = new CompoundDiscreteParameter("Detail", 10, 3, 25)
    .setDescription("Max Newton iterations - higher adds finer boundary filigree (more sparkle, more cost)");

  private final CompoundParameter bands = new CompoundParameter("Bands", 1.0, 0.1, 6.0)
    .setDescription("Gradient band frequency - low is a few huge smooth gradients, high is many fine rings");

  private final CompoundParameter shimmer = new CompoundParameter("Shimmer", 0.4, 0.0, 3.0)
    .setDescription("Speed of an independent color/brightness phase drift - keeps things alive even with Rotate at 0");

  private final CompoundParameter contrast = new CompoundParameter("Contrast", 1.0, 0.3, 3.0)
    .setDescription("Shapes the brightness bands - above 1 punches up contrast, below 1 flattens toward grey");

  private final BooleanParameter color = new BooleanParameter("Color", false)
    .setDescription("Toggle color (hue per root basin) vs. pure greyscale");

  private final CompoundParameter sat = new CompoundParameter("Sat", 70.0, 0.0, 100.0)
    .setDescription("Color saturation, when Color is on");

  private final CompoundParameter hueSpread = new CompoundParameter("Hue Spread", 1.0, 0.0, 3.0)
    .setDescription("How much the brightness bands additionally shift hue within each basin, when Color is on");

  private float rootPhase = 0f;   // radians, wrapped by 2*PI - a root's angle
  private float shimmerPhase = 0f; // cycles, wrapped by 1.0

  // Shimmer's cyclic phase freezes wherever it happens to be the instant
  // Shimmer hits 0 - if the wave were shown as-is, that's just as likely to
  // freeze on a bright static frame as a dark one. This envelope eases the
  // wave's overall visibility toward 0 (black) whenever Shimmer is at 0, and
  // back toward 1 when it isn't, so the transition is always a gentle fade
  // rather than a freeze-in-place or a hard cut.
  private float shimmerEnvelope = 1f;

  // Frame-invariant Newton-fractal state, computed once per render() instead
  // of once per pixel (previously up to 18 trig calls were being repeated
  // for every rendered pixel, even though none of these depend on pixel
  // position - only on rootPhase/Roots, which are the same for the whole
  // frame).
  private float targetR = 1f;
  private float targetI = 0f;
  private float[] rootCosCache = new float[0];
  private float[] rootSinCache = new float[0];

  private int[] exteriorCache;

  private static final float TWO_PI = (float) (2.0 * Math.PI);
  private static final float CONV_EPS = 0.02f;
  private static final float CONV_EPS_SQ = CONV_EPS * CONV_EPS;
  private static final float SHIMMER_ENVELOPE_RATE = 2.5f; // ~0.4s fade time constant

  public ExMachina(LX lx) {
    super(lx);
    addParameter("Roots", this.roots);
    addParameter("Rotate", this.rotate);
    addParameter("Twist", this.twist);
    addParameter("Zoom", this.zoom);
    addParameter("Detail", this.detail);
    addParameter("Bands", this.bands);
    addParameter("Shimmer", this.shimmer);
    addParameter("Contrast", this.contrast);
    addParameter("Color", this.color);
    addParameter("Sat", this.sat);
    addParameter("Hue Spread", this.hueSpread);
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) (deltaMs / 1000.0);

    rootPhase += dt * (float) Math.toRadians(rotate.getValuef());
    rootPhase %= TWO_PI; // exact period of the trig calls below, so this never introduces a jump

    shimmerPhase += dt * shimmer.getValuef();
    shimmerPhase %= 1f; // exact period of the cos() call below, same reasoning

    float shimmerTarget = (shimmer.getValuef() > 0f) ? 1f : 0f;
    shimmerEnvelope += (shimmerTarget - shimmerEnvelope) * Math.min(1f, SHIMMER_ENVELOPE_RATE * dt);

    // Target c(t) = e^{i*N*rootPhase} and the N root coordinates themselves
    // only depend on Roots/rootPhase, not on pixel position - compute once
    // here rather than inside calculateNewtonColor.
    int numRootsForFrame = roots.getValuei();
    float cAngle = numRootsForFrame * rootPhase;
    targetR = (float) Math.cos(cAngle);
    targetI = (float) Math.sin(cAngle);
    if (rootCosCache.length != numRootsForFrame) {
      rootCosCache = new float[numRootsForFrame];
      rootSinCache = new float[numRootsForFrame];
    }
    for (int k = 0; k < numRootsForFrame; k++) {
      float ra = rootPhase + TWO_PI * k / numRootsForFrame;
      rootCosCache[k] = (float) Math.cos(ra);
      rootSinCache[k] = (float) Math.sin(ra);
    }

    Cube cube = Apotheneum.cube;
    if (cube != null) {
      Face referenceFace = cube.exterior.faces[0];
      computeExteriorPattern(referenceFace);

      for (Face face : cube.exterior.faces) {
        copyFromExteriorCache(face);
      }

      if (cube.interior != null) {
        copyCubeExterior();
      }
    }

    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder != null) {
      processCylinder(cylinder);
    }
  }

  private void computeExteriorPattern(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;

    if (exteriorCache == null || exteriorCache.length != cols * rows) {
      exteriorCache = new int[cols * rows];
    }

    int numRoots = roots.getValuei();
    float twistRad = (float) Math.toRadians(twist.getValuef());
    int maxIter = detail.getValuei();
    float bandScale = bands.getValuef();
    float contrastPow = contrast.getValuef();
    boolean colorOn = color.isOn();
    float satVal = sat.getValuef();
    float hueSpreadVal = hueSpread.getValuef();
    float zoomLevel = zoom.getValuef();

    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);

    int cacheIndex = 0;
    for (int rowIdx = 0; rowIdx < rows; rowIdx++) {
      for (int colIdx = 0; colIdx < cols; colIdx++) {
        float u = (colIdx * invCols - 0.5f) / zoomLevel;
        float v = (rowIdx * invRows - 0.5f) / zoomLevel;

        exteriorCache[cacheIndex++] = calculateNewtonColor(
          u, v, numRoots, twistRad, maxIter, bandScale, contrastPow, colorOn, satVal, hueSpreadVal, shimmerEnvelope
        );
      }
    }
  }

  private void copyFromExteriorCache(Face face) {
    int cols = face.columns.length;
    int cacheIndex = 0;

    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        colors[p.index] = exteriorCache[cacheIndex++];
      }
    }
  }

  private void processCylinder(Cylinder cylinder) {
    processCylinderOrientation(cylinder.exterior);

    if (cylinder.interior != null) {
      copyCylinderExterior();
    }
  }

  private void processCylinderOrientation(Cylinder.Orientation orientation) {
    Ring[] ringsArr = orientation.rings;
    int numRings = ringsArr.length;

    int numRoots = roots.getValuei();
    float twistRad = (float) Math.toRadians(twist.getValuef());
    int maxIter = detail.getValuei();
    float bandScale = bands.getValuef();
    float contrastPow = contrast.getValuef();
    boolean colorOn = color.isOn();
    float satVal = sat.getValuef();
    float hueSpreadVal = hueSpread.getValuef();
    float zoomLevel = zoom.getValuef();

    for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
      Ring ring = ringsArr[ringIndex];
      int pointsPerRing = ring.points.length;

      float v = ((float) ringIndex / Math.max(1, numRings - 1) - 0.5f) / zoomLevel;

      for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
        LXPoint p = ring.points[pointIndex];
        float u = ((float) pointIndex / pointsPerRing - 0.5f) / zoomLevel;

        colors[p.index] = calculateNewtonColor(
          u, v, numRoots, twistRad, maxIter, bandScale, contrastPow, colorOn, satVal, hueSpreadVal, shimmerEnvelope
        );
      }
    }
  }

  private int calculateNewtonColor(
    float u, float v, int numRoots, float twistRad, int maxIter,
    float bandScale, float contrastPow, boolean colorOn, float satVal, float hueSpreadVal,
    float envelope
  ) {
    float zr = u;
    float zi = v;

    // Target c(t) = e^{i * N * rootPhase}, whose N-th roots (the fractal's
    // attractors) sit at angle rootPhase + 2*pi*k/N for k = 0..N-1 - so as
    // rootPhase drifts, every root sweeps around the unit circle together
    // and the whole basin structure continuously redraws itself. Precomputed
    // once per frame in render() (targetR/targetI), not per pixel.
    float cr = targetR;
    float ci = targetI;

    float twistCos = (float) Math.cos(twistRad);
    float twistSin = (float) Math.sin(twistRad);

    int iter = 0;
    float fMagSq = Float.MAX_VALUE;

    for (; iter < maxIter; iter++) {
      // z^(N-1) via repeated complex multiply (N is small, 2-8)
      float pr = 1f, pi = 0f;
      for (int k = 0; k < numRoots - 1; k++) {
        float npr = pr * zr - pi * zi;
        float npi = pr * zi + pi * zr;
        pr = npr;
        pi = npi;
      }

      // z^N = z^(N-1) * z
      float zNr = pr * zr - pi * zi;
      float zNi = pr * zi + pi * zr;

      float fr = zNr - cr;
      float fi = zNi - ci;
      fMagSq = fr * fr + fi * fi;
      if (fMagSq < CONV_EPS_SQ) {
        break;
      }

      // f'(z) = N * z^(N-1)
      float fpr = numRoots * pr;
      float fpi = numRoots * pi;
      float denom = fpr * fpr + fpi * fpi;
      if (denom < 1e-12f) {
        break; // near a critical point - bail rather than divide by ~0
      }

      // correction = f(z) / f'(z)
      float corR = (fr * fpr + fi * fpi) / denom;
      float corI = (fi * fpr - fr * fpi) / denom;

      // Twist rotates the correction direction - the generalized/relaxed
      // Newton step. At Twist=0 this is a no-op (classic Newton fractal).
      float rCorR = corR * twistCos - corI * twistSin;
      float rCorI = corR * twistSin + corI * twistCos;

      zr -= rCorR;
      zi -= rCorI;
    }

    // Which of the N roots did we land nearest? Drives hue in color mode.
    // Root coordinates are precomputed once per frame in render() (rootCosCache/
    // rootSinCache), not recomputed here per pixel.
    int basin = 0;
    float bestDistSq = Float.MAX_VALUE;
    for (int k = 0; k < numRoots; k++) {
      float dr = zr - rootCosCache[k];
      float di = zi - rootSinCache[k];
      float d = dr * dr + di * di;
      if (d < bestDistSq) {
        bestDistSq = d;
        basin = k;
      }
    }

    // Smooth (fractional) iteration count, continuous across the integer
    // iteration boundaries - this is what keeps the basins as broad smooth
    // gradients instead of hard iteration-count rings.
    float smoothFrac = LXUtils.clampf(1f - (float) Math.sqrt(fMagSq) / CONV_EPS, 0f, 1f);
    float smoothIter = iter + smoothFrac;

    float bandPhase = smoothIter * bandScale * 0.15f + shimmerPhase;
    float wave = 0.5f + 0.5f * (float) Math.cos(bandPhase * TWO_PI);
    wave = (float) Math.pow(wave, contrastPow);
    // Ease the whole wave toward black when Shimmer is at 0, instead of just
    // freezing its cyclic phase wherever it happened to land.
    float brightness = wave * envelope * 100f;

    if (!colorOn) {
      return LXColor.gray(brightness);
    }

    float hue = (basin * 360f / numRoots) + wave * 60f * hueSpreadVal;
    hue %= 360f;
    if (hue < 0) {
      hue += 360f;
    }

    return LXColor.hsb(hue, satVal, brightness);
  }

}
