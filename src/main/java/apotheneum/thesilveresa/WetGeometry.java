package apotheneum.thesilveresa;

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

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Wet Geometry")
public class WetGeometry extends ApotheneumPattern {

  // Simulation resolution
  private static final int   SIM_W    = 100;
  private static final int   SIM_H    = 90;
  private static final int   SIM_SIZE = SIM_W * SIM_H;
  private static final float MAX_DT   = 0.05f;

  // Parameters

  private final CompoundParameter morph = new CompoundParameter("Morph", 0.0, 0.0, 1.0)
    .setDescription("Morphology: 0=Minimal Surface  0.2=Radiolarian  0.4=Diatom  0.6=Helix  0.8=Foam  1.0=Tessellation");

  private final CompoundParameter feed = new CompoundParameter("Feed", 37, 10, 100)
    .setDescription("Gray-Scott feed rate x1000 (37=F 0.037). Snapped by Morph when AutoRD on.");

  private final CompoundParameter kill = new CompoundParameter("Kill", 60, 30, 80)
    .setDescription("Gray-Scott kill rate x1000 (60=K 0.060). Snapped by Morph when AutoRD on.");

  private final BooleanParameter autoRD = new BooleanParameter("AutoRD", true)
    .setDescription("When on, moving Morph snaps Feed/Kill/DiffA/DiffB/Scale to scene presets. Knobs free between moves.");

  private final CompoundParameter diffA = new CompoundParameter("DiffA", 100, 50, 200)
    .setDescription("Diffusion rate species A x100 (100=dA 1.0). Snapped by Morph when AutoRD on.");

  private final CompoundParameter diffB = new CompoundParameter("DiffB", 50, 10, 100)
    .setDescription("Diffusion rate species B x100 (50=dB 0.5). Snapped by Morph when AutoRD on.");

  private final CompoundParameter scale = new CompoundParameter("Scale", 100, 25, 400)
    .setDescription("Pattern scale x100 (100=default, 200=2x finer, 50=2x larger). Snapped by Morph when AutoRD on.");

  private final CompoundParameter simSpeed = new CompoundParameter("Speed", 1.0, 0.1, 50.0)
    .setDescription("Simulation speed: 1=normal, 3=triple. Above 8 increase Steps for stability.");

  private final CompoundParameter subSteps = new CompoundParameter("Steps", 4, 1, 12)
    .setDescription("Sub-steps per frame. Increase for stability at high Speed.");

  // Noise - sine-wave perturbation added to B each step.
  // Display 0-100, internal = Noise/100000 (so 8 -> 0.00008 per step).
  // Keeps patterns alive. Zero RNG calls - uses existing LUTs only.
  private final CompoundParameter noiseInject = new CompoundParameter("Noise", 8, 0, 100)
    .setDescription("Noise injection x100000 (8=gentle restlessness, 40=active evolution). Prevents freeze.");

  // Flow - scrolls the UV sampling window through the simulation grid along a
  // sine-lissajous path, reversing at boundaries. Gives a liquid drifting feel
  // with no simulation cost and no possibility of runaway.
  // Display 0-100, internal speed in UV-units/second (so 20 -> 0.02 UV/sec).
  private final CompoundParameter flow = new CompoundParameter("Flow", 0, 0, 100)
    .setDescription("UV drift speed x1000 (0=off, 20=gentle drift, 60=active flow). Bounces at grid boundaries.");

  // Flow scale - ratio of X to Y drift speed, creating lissajous path variety.
  // Display 10-30 (representing the Y speed as a fraction of X speed x10).
  // e.g. 13 means Y drifts at 1.3x X speed - produces slowly rotating ellipses.
  private final CompoundParameter flowRatio = new CompoundParameter("FlwRt", 13, 10, 30)
    .setDescription("Flow Y/X speed ratio x10 (13=1.3, creates lissajous path variety).");

  private final CompoundParameter brightness = new CompoundParameter("Bright", 90.0, 20.0, 100.0)
    .setDescription("Output brightness");

  private final CompoundParameter hueB = new CompoundParameter("HueB", 185.0, 0.0, 360.0)
    .setDescription("Hue for reactive species B (the visible pattern)");

  private final CompoundParameter hueA = new CompoundParameter("HueA", 220.0, 0.0, 360.0)
    .setDescription("Hue for stable species A (background)");

  private final CompoundParameter sat = new CompoundParameter("Sat", 80.0, 0.0, 100.0)
    .setDescription("Color saturation");

  private final BooleanParameter helixMode = new BooleanParameter("Helix", false)
    .setDescription("Helical UV transform on cylinder");

  private final CompoundParameter helixTwist = new CompoundParameter("Twist", 2.0, 0.5, 6.0)
    .setDescription("Helix twist: full rotations across cylinder height");

  private final CompoundParameter helixSpeed = new CompoundParameter("HxSpd", 0.15, 0.0, 1.0)
    .setDescription("Helix rotation speed");

  private final BooleanParameter reseed = new BooleanParameter("Reseed", false)
    .setDescription("Reseed simulation (momentary - restarts pattern formation)");

  // Scene presets
  // { Feed(x1000), Kill(x1000), DiffA(x100), DiffB(x100), Scale(x100) }
  private static final float[][] PRESETS = {
    {  37f,  60f,  100f,  50f,   60f },  // Morph 0.0 - Minimal Surface
    {  50f,  65f,  100f,  50f,  120f },  // Morph 0.2 - Radiolarian Logic
    {  55f,  62f,  100f,  45f,  150f },  // Morph 0.4 - Diatom Variations
    {  54f,  63f,  100f,  45f,  130f },  // Morph 0.6 - A Helix Encoded
    {  60f,  62f,   90f,  40f,  180f },  // Morph 0.8 - Foam Logic
    {  35f,  57f,   80f,  35f,  220f },  // Morph 1.0 - Tessellation of Flesh
  };

  // Simulation buffers
  private float[] gridA = new float[SIM_SIZE];
  private float[] gridB = new float[SIM_SIZE];
  private float[] nextA = new float[SIM_SIZE];
  private float[] nextB = new float[SIM_SIZE];

  // Trig LUTs
  private static final int     LUT_SIZE = 1024;
  private static final float   TWO_PI   = 2f * (float) Math.PI;
  private static final float[] SIN_LUT  = new float[LUT_SIZE];
  private static final float[] COS_LUT  = new float[LUT_SIZE];

  static {
    for (int i = 0; i < LUT_SIZE; i++) {
      float a = TWO_PI * i / LUT_SIZE;
      SIN_LUT[i] = (float) Math.sin(a);
      COS_LUT[i] = (float) Math.cos(a);
    }
  }

  private float   time           = 0f;
  private boolean needsReseed    = true;
  private float   lastMorphValue = -1f;

  // UV drift state - position and velocity in normalized [0,1] UV space.
  // Velocity reverses when position hits 0 or 1, giving a bounce effect
  // that keeps the window within the grid indefinitely.
  private float driftU  = 0f;
  private float driftV  = 0f;
  private float driftVU = 1f;   // X velocity direction (+1 or -1)
  private float driftVV = 1f;   // Y velocity direction (+1 or -1)

  // Constructor

  public WetGeometry(LX lx) {
    super(lx);
    addParameter("Morph",   this.morph);
    addParameter("Feed",    this.feed);
    addParameter("Kill",    this.kill);
    addParameter("AutoRD",  this.autoRD);
    addParameter("DiffA",   this.diffA);
    addParameter("DiffB",   this.diffB);
    addParameter("Scale",   this.scale);
    addParameter("Speed",   this.simSpeed);
    addParameter("Steps",   this.subSteps);
    addParameter("Noise",   this.noiseInject);
    addParameter("Flow",    this.flow);
    addParameter("FlwRt",   this.flowRatio);
    addParameter("Bright",  this.brightness);
    addParameter("HueB",    this.hueB);
    addParameter("HueA",    this.hueA);
    addParameter("Sat",     this.sat);
    addParameter("Helix",   this.helixMode);
    addParameter("Twist",   this.helixTwist);
    addParameter("HxSpd",   this.helixSpeed);
    addParameter("Reseed",  this.reseed);
    initGrid();
  }

  // Simulation seeding

  private void initGrid() {
    for (int i = 0; i < SIM_SIZE; i++) { gridA[i] = 1f; gridB[i] = 0f; }
    java.util.Random rng = new java.util.Random();
    int numSeeds = 12 + rng.nextInt(12);
    for (int s = 0; s < numSeeds; s++) {
      int cx = 4 + rng.nextInt(SIM_W - 8);
      int cy = 4 + rng.nextInt(SIM_H - 8);
      for (int dy = -3; dy <= 3; dy++) {
        for (int dx = -3; dx <= 3; dx++) {
          int i = clampIndex(cx + dx, cy + dy);
          gridA[i] = 0.5f + rng.nextFloat() * 0.1f;
          gridB[i] = 0.25f + rng.nextFloat() * 0.1f;
        }
      }
    }
    needsReseed = false;
  }

  // Main render

  @Override
  protected void render(double deltaMs) {
    float dt = Math.min((float)(deltaMs / 1000.0), MAX_DT);
    time += dt;

    if (reseed.getValueb()) { initGrid(); reseed.setValue(false); }
    if (needsReseed) initGrid();

    // AutoRD: snap knobs to preset only when Morph moves
    if (autoRD.getValueb()) {
      float cm = morph.getValuef();
      if (cm != lastMorphValue) {
        lastMorphValue = cm;
        float m  = cm * (PRESETS.length - 1);
        int   lo = (int) m;
        int   hi = Math.min(lo + 1, PRESETS.length - 1);
        float t  = m - lo;
        feed.setValue( lerp(PRESETS[lo][0], PRESETS[hi][0], t) );
        kill.setValue( lerp(PRESETS[lo][1], PRESETS[hi][1], t) );
        diffA.setValue(lerp(PRESETS[lo][2], PRESETS[hi][2], t) );
        diffB.setValue(lerp(PRESETS[lo][3], PRESETS[hi][3], t) );
        scale.setValue(lerp(PRESETS[lo][4], PRESETS[hi][4], t) );
      }
    }

    // Convert display units -> simulation units
    float F     = feed.getValuef()  / 1000f;
    float K     = kill.getValuef()  / 1000f;
    float dA    = diffA.getValuef() / 100f;
    float dB    = diffB.getValuef() / 100f;
    float noise = noiseInject.getValuef() / 100000f;

    // Step simulation
    int   steps  = Math.max(1, (int) subSteps.getValuef());
    float stepDt = simSpeed.getValuef() * dt / steps;
    for (int s = 0; s < steps; s++) stepGrayScott(F, K, dA, dB, stepDt, noise);

    // Update UV drift (bounce within [0,1])
    float flowSpeed = flow.getValuef() / 1000f;  // UV units per second
    if (flowSpeed > 0f) {
      float ratio = flowRatio.getValuef() / 10f;  // Y/X speed ratio
      float stepU = flowSpeed * dt * driftVU;
      float stepV = flowSpeed * ratio * dt * driftVV;

      driftU += stepU;
      driftV += stepV;

      // Bounce: reverse direction at boundaries, clamp to [0,1]
      if (driftU >= 1f) { driftU = 1f; driftVU = -1f; }
      else if (driftU <= 0f) { driftU = 0f; driftVU =  1f; }
      if (driftV >= 1f) { driftV = 1f; driftVV = -1f; }
      else if (driftV <= 0f) { driftV = 0f; driftVV =  1f; }
    }

    renderCube();
    renderCylinder();
  }

  // Gray-Scott step

  private void stepGrayScott(float F, float K, float dA, float dB, float dt, float noise) {
    for (int y = 0; y < SIM_H; y++) {
      for (int x = 0; x < SIM_W; x++) {
        int   i    = idx(x, y);
        float a    = gridA[i];
        float b    = gridB[i];
        float lapA = gridA[idx(x-1,y)] + gridA[idx(x+1,y)]
                   + gridA[idx(x,y-1)] + gridA[idx(x,y+1)] - 4f * a;
        float lapB = gridB[idx(x-1,y)] + gridB[idx(x+1,y)]
                   + gridB[idx(x,y-1)] + gridB[idx(x,y+1)] - 4f * b;
        float rxn  = a * b * b;
        // Sine-wave noise: two LUT lookups per cell, zero RNG, negligible cost.
        float n = 0f;
        if (noise > 0f) {
          int lx = ((int)(x * 174) + (int)(time * 1331)) & 0x3FF;
          int ly = ((int)(y * 133) + (int)(time * 993))  & 0x3FF;
          n = noise * (SIN_LUT[lx] + SIN_LUT[ly]) * 0.5f;
        }
        nextA[i] = clamp01(a + dt * (dA * lapA - rxn + F * (1f - a)));
        nextB[i] = clamp01(b + dt * (dB * lapB + rxn - (F + K) * b) + n);
      }
    }
    float[] tmp = gridA; gridA = nextA; nextA = tmp;
    tmp = gridB; gridB = nextB; nextB = tmp;
  }

  // Cube rendering

  private void renderCube() {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    for (Face face : cube.exterior.faces) renderFace(face);
    if (cube.interior != null)
      for (Face face : cube.interior.faces) renderFace(face);
  }

  private void renderFace(Face face) {
    int   cols    = Apotheneum.GRID_WIDTH;
    int   rows    = face.rows.length;
    float invCols = 1f / Math.max(1, cols - 1);
    float invRows = 1f / Math.max(1, rows - 1);
    for (int ri = 0; ri < rows; ri++) {
      Row   row = face.rows[ri];
      float v   = ri * invRows;
      for (int ci = 0; ci < cols; ci++)
        colors[row.points[ci].index] = toColor(sampleB(ci * invCols, v));
    }
  }

  // Cylinder rendering

  private void renderCylinder() {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    renderCylOrientation(cyl.exterior);
    if (cyl.interior != null) renderCylOrientation(cyl.interior);
  }

  private void renderCylOrientation(Cylinder.Orientation o) {
    Ring[]  rings    = o.rings;
    int     numRings = rings.length;
    boolean helix    = helixMode.getValueb();
    float   twist    = helixTwist.getValuef();
    float   hxTime   = time * helixSpeed.getValuef();
    for (int ri = 0; ri < numRings; ri++) {
      Ring  ring      = rings[ri];
      int   numPoints = ring.points.length;
      float v         = (float) ri / Math.max(1, numRings - 1);
      for (int pi = 0; pi < numPoints; pi++) {
        float u  = (float) pi / numPoints;
        float su = helix ? frac(u + v * twist + hxTime) : u;
        colors[ring.points[pi].index] = toColor(sampleB(su, v));
      }
    }
  }

  // Sampling

  // Sample B at UV coordinates, applying Scale tiling and Flow drift.
  // The drift offset (driftU, driftV) scrolls the window through the grid,
  // bouncing at boundaries - no toroidal seam, no runaway possible.
  private float sampleB(float u, float v) {
    float sc = scale.getValuef() / 100f;
    // Apply drift then tile with frac for seamless wrap
    float su = frac((u + driftU) * sc);
    float sv = frac((v + driftV) * sc);
    return sampleGrid(gridB, su, sv);
  }

  // Bilinear sample of any grid at normalized UV in [0,1]. Toroidal wrap.
  private float sampleGrid(float[] grid, float u, float v) {
    float fx = u * (SIM_W - 1);
    float fy = v * (SIM_H - 1);
    int   x0 = (int) fx,  y0 = (int) fy;
    float tx = fx - x0,   ty = fy - y0;
    int   x1 = (x0 + 1) % SIM_W, y1 = (y0 + 1) % SIM_H;
    x0 = ((x0 % SIM_W) + SIM_W) % SIM_W;
    y0 = ((y0 % SIM_H) + SIM_H) % SIM_H;
    return lerp(
      lerp(grid[y0*SIM_W+x0], grid[y0*SIM_W+x1], tx),
      lerp(grid[y1*SIM_W+x0], grid[y1*SIM_W+x1], tx), ty);
  }

  // Coloring

  private int toColor(float b) {
    float norm   = clamp01(b * 2.5f);
    float hue    = lerp(hueA.getValuef(), hueB.getValuef(), norm);
    float satOut = sat.getValuef() * (0.3f + 0.7f * norm);
    float briOut = brightness.getValuef() * (0.05f + 0.95f * norm);
    return LXColor.hsb(hue % 360f, satOut, briOut);
  }

  // Index helpers

  private int idx(int x, int y) {
    return (((y % SIM_H) + SIM_H) % SIM_H) * SIM_W
         + (((x % SIM_W) + SIM_W) % SIM_W);
  }

  private int clampIndex(int x, int y) {
    return Math.max(0, Math.min(SIM_H-1, y)) * SIM_W
         + Math.max(0, Math.min(SIM_W-1, x));
  }

  // Math helpers

  private static float lerp(float a, float b, float t) { return a + t * (b - a); }
  private static float frac(float v) { v %= 1f; return v < 0f ? v + 1f : v; }
  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private float fastSin(float angle) {
    int i = (int)((angle / TWO_PI) * LUT_SIZE) % LUT_SIZE;
    return SIN_LUT[i < 0 ? i + LUT_SIZE : i];
  }

  private float fastCos(float angle) {
    int i = (int)((angle / TWO_PI) * LUT_SIZE) % LUT_SIZE;
    return COS_LUT[i < 0 ? i + LUT_SIZE : i];
  }
}
