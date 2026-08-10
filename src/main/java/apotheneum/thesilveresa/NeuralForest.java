package apotheneum.thesilveresa;

import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.Apotheneum.Cylinder.Ring;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;

import java.util.Random;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Neural Forest")
public class NeuralForest extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;
  private static final float DEG    = PI / 180f;

  private static final int GRID_W = 128;
  private static final int GRID_H = 128;
  private static final int MAX_SEG = 1600;

  private static final float AXON_Y = 0.5f;      // both somas sit at mid height
  private static final float MAX_SPLIT = 0.30f;  // half the soma separation at Form=100
  private static final float AXON_WAVE = 0.055f; // how far the axon wanders off horizontal
  private static final float AXON_FREQ = 11f;    // wander cycles across the span

  // Trig LUT
  private static final int LUT = 2048;
  private static final float[] SIN = new float[LUT];
  private static final float LUT_SCALE = LUT / TWO_PI;
  static {
    for (int i = 0; i < LUT; i++) SIN[i] = (float) Math.sin(TWO_PI * i / LUT);
  }
  private static float fsin(float a) {
    int i = (int) (a * LUT_SCALE) & (LUT - 1);
    return SIN[i];
  }
  private static float fcos(float a) { return fsin(a + PI * 0.5f); }

  // Shape

  private final CompoundParameter form = new CompoundParameter("Form", 0, 0, 100)
    .setDescription("Single soma to double-ended axon");
  private final CompoundParameter fan = new CompoundParameter("Fan", 80, 20, 100)
    .setDescription("Dendrite spread arc");
  private final CompoundParameter primary = new CompoundParameter("Prim", 6, 2, 9)
    .setDescription("Primary dendrites per soma");
  private final CompoundParameter soma = new CompoundParameter("Soma", 28, 0, 100)
    .setDescription("Cell body size");
  private final CompoundParameter myelin = new CompoundParameter("Myelin", 55, 0, 100)
    .setDescription("Axon sheath banding");
  private final CompoundParameter pulse = new CompoundParameter("Pulse", 0, 0, 100)
    .setDescription("Signal leaping node to node along the axon");

  // Branching

  private final CompoundParameter angle = new CompoundParameter("Angle", 26, 5, 60)
    .setDescription("Branch angle (degrees)");
  private final CompoundParameter ratio = new CompoundParameter("Ratio", 74, 40, 92)
    .setDescription("Child length ratio");
  private final CompoundParameter depth = new CompoundParameter("Depth", 6, 3, 7)
    .setDescription("Branch generations");
  private final CompoundParameter chaos = new CompoundParameter("Chaos", 30, 0, 100)
    .setDescription("Angle jitter");
  private final CompoundParameter reach = new CompoundParameter("Reach", 45, 20, 100)
    .setDescription("Primary dendrite length");
  private final CompoundParameter seed = new CompoundParameter("Seed", 3, 0, 40)
    .setDescription("Random seed");

  // Reveal and weight

  private final CompoundParameter growth = new CompoundParameter("Growth", 100, 0, 100)
    .setDescription("Growth reveal");
  private final CompoundParameter thick = new CompoundParameter("Thick", 20, 3, 40)
    .setDescription("Line thickness");
  private final CompoundParameter tipWeight = new CompoundParameter("TipW", 45, 0, 90)
    .setDescription("How much thinner the tips get");
  private final CompoundParameter glow = new CompoundParameter("Glow", 45, 0, 100)
    .setDescription("Halo width");

  // Color - three hue stops plus independent root/tip saturation

  private final CompoundParameter hueRoot = new CompoundParameter("HueRt", 190, 0, 360)
    .setDescription("Soma hue");
  private final CompoundParameter hueMid = new CompoundParameter("HueMid", 205, 0, 360)
    .setDescription("Mid-branch hue");
  private final CompoundParameter hueTip = new CompoundParameter("HueTip", 225, 0, 360)
    .setDescription("Tip hue");
  private final CompoundParameter satRoot = new CompoundParameter("SatRt", 20, 0, 100)
    .setDescription("Soma saturation");
  private final CompoundParameter satTip = new CompoundParameter("SatTip", 70, 0, 100)
    .setDescription("Tip saturation");
  private final CompoundParameter bright = new CompoundParameter("Bright", 85, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 4, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", true)
    .setDescription("Mirror alternate faces and tiles");

  // Segment store

  private final float[] segX0 = new float[MAX_SEG];
  private final float[] segY0 = new float[MAX_SEG];
  private final float[] segX1 = new float[MAX_SEG];
  private final float[] segY1 = new float[MAX_SEG];
  private final float[] segDepth = new float[MAX_SEG];
  private final float[] segBirth = new float[MAX_SEG];
  private final float[] segLen = new float[MAX_SEG];
  private int segCount = 0;
  private float maxBirth = 0.0001f;
  private int maxDepthUsed = 1;
  private float dpsT;
  private float axonAmp = 0f;    // wander amplitude, scales in with Form
  private float axonPhase = 0f;  // varies with Seed

  // the axon is a gentle curve, not a ruled line
  private float axonY(float u) {
    return AXON_Y + axonAmp * fsin(u * AXON_FREQ + axonPhase);
  }

  private final float[] gridDist = new float[GRID_W * GRID_H];
  private final float[] gridDepth = new float[GRID_W * GRID_H];
  private final float[] gridBirth = new float[GRID_W * GRID_H];

  private boolean dirty = true;
  private double sinceRebuild = 1e9;
  private float prevSeed = -1f;
  private float time = 0f;

  // Per-frame scratch
  private float sGr, sW, sTipW, sGlowA, sBr, sBk;
  private float sHR, sHM, sHT, sSR, sST;
  private float sSplit, sSomaR, sMyelin, sPulse;

  public NeuralForest(LX lx) {
    super(lx);
    addParameter("Form",   this.form);
    addParameter("Fan",    this.fan);
    addParameter("Prim",   this.primary);
    addParameter("Soma",   this.soma);
    addParameter("Myelin", this.myelin);
    addParameter("Pulse",  this.pulse);
    addParameter("Angle",  this.angle);
    addParameter("Ratio",  this.ratio);
    addParameter("Depth",  this.depth);
    addParameter("Chaos",  this.chaos);
    addParameter("Reach",  this.reach);
    addParameter("Seed",   this.seed);
    addParameter("Growth", this.growth);
    addParameter("Thick",  this.thick);
    addParameter("TipW",   this.tipWeight);
    addParameter("Glow",   this.glow);
    addParameter("HueRt",  this.hueRoot);
    addParameter("HueMid", this.hueMid);
    addParameter("HueTip", this.hueTip);
    addParameter("SatRt",  this.satRoot);
    addParameter("SatTip", this.satTip);
    addParameter("Bright", this.bright);
    addParameter("Black",  this.black);
    addParameter("Repeat", this.repeat);
    addParameter("Sym",    this.symmetry);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == form || p == fan || p == primary || p == angle || p == ratio
        || p == depth || p == chaos || p == reach) {
      dirty = true;
    }
  }

  // Generation

  private void addSeg(float x0, float y0, float x1, float y1, float d, float birth, float len) {
    if (segCount >= MAX_SEG) return;
    int i = segCount++;
    segX0[i] = x0; segY0[i] = y0; segX1[i] = x1; segY1[i] = y1;
    segDepth[i] = d; segBirth[i] = birth; segLen[i] = len;
    if (birth + len > maxBirth) maxBirth = birth + len;
    if (d > maxDepthUsed) maxDepthUsed = (int) d;
  }

  private void grow(float x, float y, float ang, float len, int d, float birth,
                    int maxD, float angRad, float rat, float ch, Random rng) {
    if (d > maxD || len < 0.005f || segCount >= MAX_SEG) return;
    float nx = x + fsin(ang) * len;
    float ny = y - fcos(ang) * len;
    addSeg(x, y, nx, ny, d, birth, len);
    float cb = birth + len;
    float cr = len * rat;
    grow(nx, ny, ang - angRad + jitter(ch, angRad, rng), cr,         d + 1, cb, maxD, angRad, rat, ch, rng);
    grow(nx, ny, ang + angRad + jitter(ch, angRad, rng), cr * 0.92f, d + 1, cb, maxD, angRad, rat, ch, rng);
  }

  private float jitter(float ch, float angRad, Random rng) {
    return (rng.nextFloat() - 0.5f) * 2f * ch * angRad;
  }

  private void generate(int seedVal) {
    segCount = 0;
    maxBirth = 0.0001f;
    maxDepthUsed = 1;

    float split  = form.getValuef() / 100f * MAX_SPLIT;
    float fanArc = fan.getValuef() / 100f * PI;      // half-arc each side
    int   nPrim  = clampi(Math.round(primary.getValuef()), 2, 9);
    int   maxD   = clampi((int) depth.getValuef(), 3, 7);
    float angRad = angle.getValuef() * DEG;
    float rat    = ratio.getValuef() / 100f;
    float ch     = chaos.getValuef() / 100f;
    float len0   = reach.getValuef() / 100f * 0.18f;

    Random rng = new Random(seedVal * 7919L + 13L);

    axonAmp = AXON_WAVE * (split / MAX_SPLIT);
    axonPhase = (seedVal % 17) * 0.37f;

    // axon as a wandering polyline, depth 0 so it reads as root color
    if (split > 0.005f) {
      int steps = 16;
      float span = split * 2f;
      float px = 0.5f - split, py = axonY(px);
      for (int i = 1; i <= steps; i++) {
        float nx = 0.5f - split + span * i / steps;
        float ny = axonY(nx);
        addSeg(px, py, nx, ny, 0f, 0f, span / steps);
        px = nx; py = ny;
      }
    }

    // two arbors; each radiates away from the axon, so at Form=0 (both at
    // center) they combine into one full 360-degree burst
    for (int side = 0; side < 2; side++) {
      float rx = 0.5f + (side == 0 ? -split : split);
      float outward = (side == 0 ? -PI * 0.5f : PI * 0.5f);
      for (int p = 0; p < nPrim; p++) {
        float t = (nPrim == 1) ? 0.5f : (float) p / (nPrim - 1);
        float a = outward + (t - 0.5f) * 2f * fanArc;
        a += jitter(ch * 0.5f, angRad, rng);
        grow(rx, axonY(rx), a, len0, 1, 0f, maxD, angRad, rat, ch, rng);
      }
    }
  }

  private void buildDistanceField() {
    float invW = 1f / GRID_W, invH = 1f / GRID_H;
    float somaR = soma.getValuef() / 100f * 0.045f;
    float split = form.getValuef() / 100f * MAX_SPLIT;
    for (int gy = 0; gy < GRID_H; gy++) {
      float py = (gy + 0.5f) * invH;
      for (int gx = 0; gx < GRID_W; gx++) {
        float px = (gx + 0.5f) * invW;
        float best = Float.MAX_VALUE, bestDepth = 0f, bestBirth = maxBirth;
        for (int s = 0; s < segCount; s++) {
          float d = distPointSeg(px, py, segX0[s], segY0[s], segX1[s], segY1[s]);
          if (d < best) { best = d; bestDepth = segDepth[s]; bestBirth = segBirth[s] + dpsT * segLen[s]; }
        }
        // cell bodies are solid discs at the arbor roots
        if (somaR > 0f) {
          for (int side = 0; side < 2; side++) {
            float rx = 0.5f + (side == 0 ? -split : split);
            float dx = px - rx, dy = py - axonY(rx);
            float ds = (float) Math.sqrt(dx * dx + dy * dy) - somaR;
            if (ds < best) { best = ds; bestDepth = 0f; bestBirth = 0f; }
          }
        }
        int cell = gy * GRID_W + gx;
        gridDist[cell]  = best < 0f ? 0f : best;
        gridDepth[cell] = bestDepth;
        gridBirth[cell] = bestBirth;
      }
    }
  }

  private float distPointSeg(float px, float py, float ax, float ay, float bx, float by) {
    float abx = bx - ax, aby = by - ay;
    float apx = px - ax, apy = py - ay;
    float len2 = abx * abx + aby * aby;
    float t = (len2 < 1e-9f) ? 0f : (apx * abx + apy * aby) / len2;
    if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
    dpsT = t;
    float cx = ax + t * abx, cy = ay + t * aby;
    float dx = px - cx, dy = py - cy;
    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  // Main render

  @Override
  protected void render(double deltaMs) {
    time += (float) (deltaMs / 1000.0);

    float seedVal = seed.getValuef();
    sinceRebuild += deltaMs;
    boolean needRebuild = dirty || Math.abs(seedVal - prevSeed) > 0.01f;
    if (needRebuild && sinceRebuild >= 80.0) {
      generate((int) seedVal);
      buildDistanceField();
      prevSeed = seedVal;
      dirty = false;
      sinceRebuild = 0.0;
    }

    sGr     = growth.getValuef() / 100f;
    sW      = thick.getValuef() / 100f * 0.045f;
    sTipW   = tipWeight.getValuef() / 100f;
    sGlowA  = glow.getValuef() / 100f;
    sBr     = bright.getValuef();
    sBk     = black.getValuef() / 100f;
    sHR     = hueRoot.getValuef();
    sHM     = hueMid.getValuef();
    sHT     = hueTip.getValuef();
    sSR     = satRoot.getValuef();
    sST     = satTip.getValuef();
    sSplit  = form.getValuef() / 100f * MAX_SPLIT;
    sSomaR  = soma.getValuef() / 100f * 0.045f;
    sMyelin = myelin.getValuef() / 100f;
    sPulse  = pulse.getValuef() / 100f * 1.4f;   // axon traversals per second

    renderCube();
    renderCylinder();
    copyExterior();
  }

  private void renderCube() {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    boolean sym = symmetry.isOn();
    Face[] ext = cube.exterior.faces;
    for (int f = 0; f < ext.length; f++) {
      renderCubeFace(ext[f], sym && ((f & 1) == 1));
    }
  }

  private void renderCubeFace(Face face, boolean flip) {
    int cols = Apotheneum.GRID_WIDTH, rows = face.rows.length;
    for (int ri = 0; ri < rows; ri++) {
      Row row = face.rows[ri];
      float v = (float) ri / (rows - 1);
      for (int ci = 0; ci < cols; ci++) {
        float u = (float) ci / (cols - 1);
        if (flip) u = 1f - u;
        colors[row.points[ci].index] = sampleGrid(u, v);
      }
    }
  }

  private void renderCylinder() {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    Ring[] rings = cyl.exterior.rings;
    int numRings = rings.length;
    boolean sym = symmetry.isOn();
    int rep = clampi((int) repeat.getValuef(), 1, 8);
    if (sym) rep = Math.max(2, (rep / 2) * 2);

    for (int ri = 0; ri < numRings; ri++) {
      Ring ring = rings[ri];
      int np = ring.points.length;
      float v = (float) ri / (numRings - 1);
      for (int pi = 0; pi < np; pi++) {
        float g = (float) pi / np * rep;
        float u;
        if (sym) {
          float gg = g - 2f * (float) Math.floor(g * 0.5f);
          u = gg <= 1f ? gg : 2f - gg;
        } else {
          u = g - (float) Math.floor(g);
        }
        colors[ring.points[pi].index] = sampleGrid(u, v);
      }
    }
  }

  private int sampleGrid(float u, float v) {
    if (u < 0f || u > 1f || v < 0f || v > 1f) return LXColor.BLACK;

    float fx = u * (GRID_W - 1), fy = v * (GRID_H - 1);
    int x0 = (int) fx, y0 = (int) fy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= GRID_W) x1 = GRID_W - 1;
    if (y1 >= GRID_H) y1 = GRID_H - 1;
    float tx = fx - x0, ty = fy - y0;
    int b00 = y0 * GRID_W + x0, b10 = y0 * GRID_W + x1;
    int b01 = y1 * GRID_W + x0, b11 = y1 * GRID_W + x1;

    float dist   = bilerp(gridDist[b00],  gridDist[b10],  gridDist[b01],  gridDist[b11],  tx, ty);
    float depthF = bilerp(gridDepth[b00], gridDepth[b10], gridDepth[b01], gridDepth[b11], tx, ty);
    float birth  = bilerp(gridBirth[b00], gridBirth[b10], gridBirth[b01], gridBirth[b11], tx, ty);

    float birthNorm = birth / maxBirth;
    if (birthNorm > sGr) return LXColor.BLACK;

    float depthNorm = clamp01(depthF / Math.max(1, maxDepthUsed));

    // fine dendrites stay visible - TipW controls how much thinner they get
    float localW = sW * (1f - sTipW * depthNorm);

    // axon region - shared by the myelin sheaths and the conduction pulse
    boolean onAxon = false;
    float myeBand = -1f;
    if (sSplit > 0.01f && depthNorm < 0.30f) {
      float fromSoma = Math.abs(u - 0.5f);
      if (Math.abs(v - axonY(u)) < 0.06f && fromSoma < sSplit && fromSoma > sSomaR) {
        onAxon = true;
        float b = fsin(u * 90f);
        myeBand = b * b;   // 0 at the nodes, 1 mid-sheath
        if (sMyelin > 0f) localW *= (1f + sMyelin * 0.60f * myeBand);
      }
    }
    if (localW < 0.004f) localW = 0.004f;

    float core = clamp01(1f - dist / localW);
    float glowW = localW * (1f + 6f * sGlowA);
    float halo = clamp01(1f - dist / glowW);
    if (core <= 0f && halo <= 0f) return LXColor.BLACK;

    float bri = clamp01(core + halo * halo * (0.35f + 0.65f * sGlowA));

    if (sMyelin > 0f && myeBand >= 0f) bri *= (1f - sMyelin * 0.35f * (1f - myeBand));

    // saltatory conduction: the signal regenerates only at the bare nodes, so it
    // jumps between them rather than sliding along the insulated stretches
    if (sPulse > 0f && onAxon) {
      float nodeSp = PI / 90f;
      int kA = (int) Math.ceil((0.5f - sSplit) / nodeSp);
      int kB = (int) Math.floor((0.5f + sSplit) / nodeSp);
      int n = kB - kA;
      if (n > 0) {
        float ph = (time * sPulse) - (float) Math.floor(time * sPulse);
        int cur = kA + (int) (ph * n);
        int k = Math.round(u / nodeSp);
        int lag = cur - k;
        if (lag >= 0 && lag < 3) {
          float wake = (lag == 0) ? 1f : (lag == 1 ? 0.40f : 0.12f);
          float prox = 1f - clamp01(Math.abs(u - k * nodeSp) / (nodeSp * 0.5f));
          bri = clamp01(bri + wake * prox * 0.85f);
        }
      }
    }

    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    // three-stop hue ramp gives finer control than a single trunk-to-tip sweep
    float hue;
    if (depthNorm < 0.5f) hue = sHR + (sHM - sHR) * (depthNorm / 0.5f);
    else                  hue = sHM + (sHT - sHM) * ((depthNorm - 0.5f) / 0.5f);
    hue = ((hue % 360f) + 360f) % 360f;

    // root and tip saturation move independently
    float s = sSR + (sST - sSR) * depthNorm;

    return LXColor.hsb(hue, clamp01(s / 100f) * 100f, clamp01(bri) * 100f);
  }

  private static float bilerp(float c00, float c10, float c01, float c11, float tx, float ty) {
    float a = c00 + (c10 - c00) * tx;
    float b = c01 + (c11 - c01) * tx;
    return a + (b - a) * ty;
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
