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
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/drmrrdmr")
@LXComponent.Name("Fractal Flow")
public class FractalFlow extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.5, 0.0, 3.0)
    .setDescription("Fractal bloom / Julia movement speed");
  private final CompoundParameter hueSpeed = new CompoundParameter("Hue Speed", 2.0, 0.0, 2.0)
    .setDescription("Color cycle speed - independent of fractal/Julia speed");
  private final CompoundParameter complexity = new CompoundParameter("Complex", 4.0, 2.0, 8.0)
    .setDescription("Fractal iteration depth");
  private final CompoundParameter zoom = new CompoundParameter("Zoom", 0.3, -1.0, 6.0)
    .setDescription("Fractal zoom level");
  private final CompoundParameter surgeIntensity = new CompoundParameter("Surge", 0.8, 0.2, 1.5)
    .setDescription("Bloom intensity");
  private final CompoundParameter centerX = new CompoundParameter("CenterX", 0.0, -1.0, 1.0)
    .setDescription("Fractal center X");
  private final CompoundParameter centerY = new CompoundParameter("CenterY", 0.0, -1.0, 1.0)
    .setDescription("Fractal center Y");
  private final BooleanParameter mandelbrot = new BooleanParameter("Mandel", true)
    .setDescription("Mandelbrot vs Julia set");
  private final CompoundParameter sat = new CompoundParameter("Sat", 90.0, 0.0, 100.0)
    .setDescription("Color saturation");
  private final BooleanParameter brightnessGradient = new BooleanParameter("B-Grad", false)
    .setDescription("Layer an escape-velocity-driven brightness gradient on top of the existing surge brightness, without replacing it");
  private final CompoundParameter brtSpeed = new CompoundParameter("Brt Speed", 2.0, 0.0, 2.0)
    .setDescription("Brightness gradient cycle speed - independent of hue speed and fractal/Julia speed");
  private final CompoundParameter brt = new CompoundParameter("Brt", 0.0, 0.0, 100.0)
    .setDescription("Brightness gradient depth - at 0 this layer has no effect at all, leaving the existing surge-driven brightness untouched");
  private final CompoundParameter juliaAmp = new CompoundParameter("J-Amp", 0.3, 0.0, 1.2)
    .setDescription("Julia constant amplitude (the scalar OUTSIDE sin/cos) - how far the animated Julia constant swings from center");
  private final CompoundParameter juliaFreq = new CompoundParameter("J-Freq", 0.7, 0.02, 4.0)
    .setDescription("Julia constant oscillation rate (the scalar INSIDE sin/cos) - lower values stretch out one cycle, making the brief convergence 'pop' easier to isolate");

  private float time = 0f;
  private float hueTime = 0f;
  private float brtTime = 0f;
  private int[] exteriorCache;
  private int[] interiorCache;
  private boolean cacheValid = false;

  // Pre-computed palette for performance
  private static final float[] SURGE_HUES = { 220f, 280f, 320f, 20f, 60f, 180f };
  private static final int MAX_ITERATIONS = 32; // Reasonable limit for real-time
  private static final float TWO_PI = (float) (2.0 * Math.PI);

  // The original Julia constant used different amplitude/frequency scalars for
  // its X and Y components (0.3/0.7 and 0.4/0.9), which is what kept its
  // motion from being a plain circle. J-Amp/J-Freq now drive the X component
  // directly and the Y component through these fixed ratios, so the same
  // Lissajous-style wobble is preserved while both scalars stay adjustable
  // with one pair of knobs instead of four.
  private static final float JULIA_Y_AMP_RATIO = 4f / 3f;   // matches original 0.4 / 0.3
  private static final float JULIA_Y_FREQ_RATIO = 9f / 7f;  // matches original 0.9 / 0.7

  // Zoom's range now spans 0 (it divides the pixel-to-complex-plane mapping),
  // so a small floor keeps the knob from passing through an actual divide-by-zero.
  private static final float MIN_ZOOM_MAGNITUDE = 0.01f;

  public FractalFlow(LX lx) {
    super(lx);
    addParameter("Speed", this.speed);
    addParameter("Hue Speed", this.hueSpeed);
    addParameter("Complex", this.complexity);
    addParameter("Zoom", this.zoom);
    addParameter("Surge", this.surgeIntensity);
    addParameter("CenterX", this.centerX);
    addParameter("CenterY", this.centerY);
    addParameter("Mandel", this.mandelbrot);
    addParameter("Sat", this.sat);
    addParameter("B-Grad", this.brightnessGradient);
    addParameter("Brt Speed", this.brtSpeed);
    addParameter("Brt", this.brt);
    addParameter("J-Amp", this.juliaAmp);
    addParameter("J-Freq", this.juliaFreq);
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float)(deltaMs / 1000.0);
    time += dt * speed.getValuef();

    // Independent hue-cycle clock - no longer derived from `time`, so color
    // rotation speed no longer depends on the fractal/Julia speed knob.
    hueTime += dt * hueSpeed.getValuef();
    hueTime %= SURGE_HUES.length; // exact period of the final modulo below, so this never introduces a jump

    // Independent brightness-gradient clock, same idea as hueTime: wrapped by
    // exactly 1.0 (one full cos() cycle), so the wrap can never introduce a jump.
    brtTime += dt * brtSpeed.getValuef();
    brtTime %= 1f;

    cacheValid = false; // Invalidate cache each frame for animation

    // Render geometries with face copying optimization
    Cube cube = Apotheneum.cube;
    if (cube != null) {
      // Compute exterior pattern
      Face referenceFace = cube.exterior.faces[0];
      computeExteriorPattern(referenceFace);

      // Copy to all exterior faces
      for (Face face : cube.exterior.faces) {
        copyFromExteriorCache(face);
      }

      // Compute interior if different, otherwise copy
      if (cube.interior != null) {
        computeInteriorPattern(cube.interior.faces[0]);
        for (Face face : cube.interior.faces) {
          copyFromInteriorCache(face);
        }
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

    computeFacePattern(face, exteriorCache, false);
  }

  private void computeInteriorPattern(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;

    if (interiorCache == null || interiorCache.length != cols * rows) {
      interiorCache = new int[cols * rows];
    }

    computeFacePattern(face, interiorCache, true);
  }

  private float safeZoom() {
    float zoomLevel = zoom.getValuef();
    if (Math.abs(zoomLevel) < MIN_ZOOM_MAGNITUDE) {
      return (zoomLevel < 0) ? -MIN_ZOOM_MAGNITUDE : MIN_ZOOM_MAGNITUDE;
    }
    return zoomLevel;
  }

  private void computeFacePattern(Face face, int[] cache, boolean isInterior) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);

    float zoomLevel = safeZoom();
    float cx = centerX.getValuef();
    float cy = centerY.getValuef();
    int maxIter = Math.min((int)complexity.getValuef() * 4, MAX_ITERATIONS);

    // Interior gets inverted perspective
    float perspective = isInterior ? -1.2f : 1.0f;

    int cacheIndex = 0;
    for (int rowIdx = 0; rowIdx < rows; rowIdx++) {
      for (int colIdx = 0; colIdx < cols; colIdx++) {
        // Map to complex plane
        float u = (colIdx * invCols - 0.5f) * perspective / zoomLevel + cx;
        float v = (rowIdx * invRows - 0.5f) * perspective / zoomLevel + cy;

        cache[cacheIndex++] = calculateFractalColor(u, v, maxIter);
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

  private void copyFromInteriorCache(Face face) {
    int cols = face.columns.length;
    int cacheIndex = 0;

    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        colors[p.index] = interiorCache[cacheIndex++];
      }
    }
  }

  private void processCylinder(Cylinder cylinder) {
    // Process exterior and interior separately for different perspectives
    processCylinderOrientation(cylinder.exterior, false);

    if (cylinder.interior != null) {
      processCylinderOrientation(cylinder.interior, true);
    }
  }

  private void processCylinderOrientation(Cylinder.Orientation orientation, boolean isInterior) {
    Ring[] rings = orientation.rings;
    int numRings = rings.length;

    float zoomLevel = safeZoom();
    float cx = centerX.getValuef();
    float cy = centerY.getValuef();
    int maxIter = Math.min((int)complexity.getValuef() * 4, MAX_ITERATIONS);
    float perspective = isInterior ? -0.8f : 1.0f;

    for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
      Ring ring = rings[ringIndex];
      int pointsPerRing = ring.points.length;

      float v = ((float)ringIndex / Math.max(1, numRings - 1) - 0.5f) * perspective / zoomLevel + cy;

      for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
        LXPoint p = ring.points[pointIndex];
        float u = ((float)pointIndex / pointsPerRing - 0.5f) * perspective / zoomLevel + cx;

        int color = calculateFractalColor(u, v, maxIter);
        colors[p.index] = color;
      }
    }
  }

  private int calculateFractalColor(float x0, float y0, int maxIter) {
    // Animated Julia set parameters. J-Amp is the scalar OUTSIDE sin/cos
    // (how far the constant travels); J-Freq is the scalar INSIDE sin/cos
    // (how fast it travels, i.e. how long one cycle takes). The Y component
    // keeps the original's fixed ratio to X so the orbit stays an organic
    // wobble instead of a plain circle.
    float amp = juliaAmp.getValuef();
    float freq = juliaFreq.getValuef();
    float jx = amp * (float)Math.cos(time * freq);
    float jy = amp * JULIA_Y_AMP_RATIO * (float)Math.sin(time * freq * JULIA_Y_FREQ_RATIO);

    float x = x0;
    float y = y0;
    int iter = 0;

    // Choose fractal type
    if (mandelbrot.getValueb()) {
      // Mandelbrot set: z = z² + c
      while (x * x + y * y <= 4f && iter < maxIter) {
        float xtemp = x * x - y * y + x0;
        y = 2f * x * y + y0;
        x = xtemp;
        iter++;
      }
    } else {
      // Julia set: z = z² + c (with animated c)
      while (x * x + y * y <= 4f && iter < maxIter) {
        float xtemp = x * x - y * y + jx;
        y = 2f * x * y + jy;
        x = xtemp;
        iter++;
      }
    }

    if (iter >= maxIter) return 0; // Inside set

    // Smooth iteration count for better coloring
    float smoothIter = iter + 1f - (float)Math.log(Math.log(x*x + y*y) / Math.log(2)) / (float)Math.log(2);

    // Apply surge effect
    float surge = surgeIntensity.getValuef();
    float surgeFactor = 1f + surge * (float)Math.sin(time * 2f + smoothIter * 0.5f);

    // Map to color - driven by the independent hue clock, not the fractal's `time`
    float hueIndex = (smoothIter * 0.3f + hueTime) % SURGE_HUES.length;
    int hueIdx = (int)hueIndex;
    float hueBlend = hueIndex - hueIdx;

    float hue1 = SURGE_HUES[hueIdx];
    float hue2 = SURGE_HUES[(hueIdx + 1) % SURGE_HUES.length];
    float hue = hue1 * (1f - hueBlend) + hue2 * hueBlend;

    float brightness = Math.min((1f - smoothIter / maxIter) * 100f * surgeFactor, 100f);

    // Optional brightness gradient, layered on top of (not replacing) the
    // surge brightness above. Driven by the same escape-velocity value
    // (smoothIter) the hue mapping uses, but animated by its own Brt Speed
    // clock instead of hueTime, so the two can run independently. At Brt=0
    // this is a no-op (gradWave == 1 exactly), so the surge brightness is
    // left completely untouched until the knob is turned up.
    if (brightnessGradient.getValueb()) {
      float amt = brt.getValuef() / 100f;
      if (amt > 0f) {
        float brtCycles = smoothIter * 0.3f + brtTime;
        float gradWave = 1f + amt * (float) Math.cos(brtCycles * TWO_PI);
        brightness = LXUtils.clampf(brightness * gradWave, 0f, 100f);
      }
    }

    return LXColor.hsb(hue, sat.getValuef(), brightness);
  }
}
