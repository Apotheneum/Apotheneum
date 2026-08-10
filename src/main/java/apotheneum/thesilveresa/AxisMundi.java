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
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Axis Mundi")
public class AxisMundi extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;
  private static final float DEG    = PI / 180f;

  private static final int GRID_W = 128;
  private static final int GRID_H = 128;
  private static final int LAYERS = 3;

  private final float[][] layerDist  = new float[LAYERS][GRID_W * GRID_H];
  private final float[][] layerBirth = new float[LAYERS][GRID_W * GRID_H];
  private final float[]   layerMaxBirth = new float[LAYERS];
  private boolean dirty = true;

  // Shared scratch segment store (used per layer during generation).
  private static final int MAX_SEG = 1400;
  private final float[] segX0 = new float[MAX_SEG];
  private final float[] segY0 = new float[MAX_SEG];
  private final float[] segX1 = new float[MAX_SEG];
  private final float[] segY1 = new float[MAX_SEG];
  private final float[] segBirth = new float[MAX_SEG];
  private int   segCount = 0;
  private float genMaxBirth = 1f;

  // ─── Trig LUT ────────────────────────────────────────────────────────────
  private static final int     LUT       = 1024;
  private static final float   LUT_SCALE = LUT / TWO_PI;
  private static final float[] SINL      = new float[LUT];
  private static final float[] COSL      = new float[LUT];
  static {
    for (int i = 0; i < LUT; i++) {
      float a = TWO_PI * i / LUT;
      SINL[i] = (float) Math.sin(a);
      COSL[i] = (float) Math.cos(a);
    }
  }

  // ─── Parameters ──────────────────────────────────────────────────────────

  private final CompoundParameter complexity = new CompoundParameter("Complex", 7, 4, 9)
    .setDescription("Recursion depth of each arbor (structural)");
  private final CompoundParameter angle = new CompoundParameter("Angle", 26, 8, 55)
    .setDescription("Base branch angle degrees (structural)");
  private final CompoundParameter growth = new CompoundParameter("Growth", 100, 0, 100)
    .setDescription("Growth reveal x100 (0=seeds, 100=full)");
  private final CompoundParameter resonance = new CompoundParameter("Reson", 55, 0, 100)
    .setDescription("Phase-shift pulse depth between layers x100");
  private final CompoundParameter resRate = new CompoundParameter("ResRte", 20, 2, 100)
    .setDescription("Resonance pulse rate x100");
  private final CompoundParameter trunkBoost = new CompoundParameter("Trunk", 60, 0, 100)
    .setDescription("Central trunk emphasis x100");
  private final CompoundParameter thick = new CompoundParameter("Thick", 42, 10, 100)
    .setDescription("Branch thickness x100");
  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 6)
    .setDescription("Trunks around cylinder ring (1 = single Axis Mundi)");
  private final CompoundParameter hue0 = new CompoundParameter("Hue0", 32, 0, 360)
    .setDescription("Trunk layer hue");
  private final CompoundParameter hue1 = new CompoundParameter("Hue1", 275, 0, 360)
    .setDescription("Neural layer hue");
  private final CompoundParameter hue2 = new CompoundParameter("Hue2", 175, 0, 360)
    .setDescription("Delta layer hue");
  private final CompoundParameter sat = new CompoundParameter("Sat", 72, 0, 100)
    .setDescription("Saturation");
  private final CompoundParameter glow = new CompoundParameter("Glow", 45, 0, 100)
    .setDescription("Halo / glow x100");
  private final CompoundParameter bright = new CompoundParameter("Bright", 95, 20, 100)
    .setDescription("Output brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 6, 0, 40)
    .setDescription("Black threshold x100");
  private final CompoundParameter seed = new CompoundParameter("Seed", 0, 0, 1000)
    .setDescription("Random seed (change to regrow)");

  private float time = 0f;
  private float prevSeed = -1f;

  public AxisMundi(LX lx) {
    super(lx);
    addParameter("Complex", this.complexity);
    addParameter("Angle",   this.angle);
    addParameter("Growth",  this.growth);
    addParameter("Reson",   this.resonance);
    addParameter("ResRte",  this.resRate);
    addParameter("Trunk",   this.trunkBoost);
    addParameter("Thick",   this.thick);
    addParameter("Repeat",  this.repeat);
    addParameter("Hue0",    this.hue0);
    addParameter("Hue1",    this.hue1);
    addParameter("Hue2",    this.hue2);
    addParameter("Sat",     this.sat);
    addParameter("Glow",    this.glow);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Seed",    this.seed);
  }

  @Override
  public void onParameterChanged(heronarts.lx.parameter.LXParameter p) {
    if (p == complexity || p == angle) dirty = true;
  }

  // ─── Main render ─────────────────────────────────────────────────────────

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0);

    float seedVal = seed.getValuef();
    if (dirty || Math.abs(seedVal - prevSeed) > 0.01f) {
      generateAllLayers((int) seedVal);
      prevSeed = seedVal;
      dirty = false;
    }

    float gr    = growth.getValuef() / 100f;
    float w     = thick.getValuef() / 100f * 0.045f;
    float glowA = glow.getValuef() / 100f;
    float res   = resonance.getValuef() / 100f;
    float rRate = resRate.getValuef() / 100f * 3f;
    float trunk = trunkBoost.getValuef() / 100f;
    float st    = sat.getValuef();
    float br    = bright.getValuef();
    float bk    = black.getValuef() / 100f;

    // Per-layer resonance envelopes (phase-shifted by 120 deg).
    float e0 = 1f - res * 0.5f * (1f - fsin(time * rRate));
    float e1 = 1f - res * 0.5f * (1f - fsin(time * rRate + TWO_PI / 3f));
    float e2 = 1f - res * 0.5f * (1f - fsin(time * rRate + 2f * TWO_PI / 3f));

    float h0 = hue0.getValuef(), h1 = hue1.getValuef(), h2 = hue2.getValuef();

    renderCube(gr, w, glowA, trunk, e0, e1, e2, h0, h1, h2, st, br, bk);
    renderCylinder(gr, w, glowA, trunk, e0, e1, e2, h0, h1, h2, st, br, bk);
  }

  // ─── Generation (three arbors) ───────────────────────────────────────────

  private void generateAllLayers(int randSeed) {
    int   maxD   = clampi((int) complexity.getValuef(), 4, 9);
    float angRad = angle.getValuef() * DEG;

    // Layer 0 - world trunk/tree: upright, symmetric, deep.
    genLayer(0, randSeed + 11, 0, 0.5f, 0.93f, 0f, 0.24f, maxD, angRad, 0.72f, 0.18f);
    // Layer 1 - neural fan: dense, high variance, centered.
    genLayer(1, randSeed + 29, 3, 0.5f, 0.80f, 0f, 0.16f, Math.min(maxD, 7), angRad * 1.15f, 0.66f, 0.55f);
    // Layer 2 - delta: inverted, wide distributaries.
    genLayer(2, randSeed + 47, 2, 0.5f, 0.07f, PI, 0.19f, Math.min(maxD, 8), angRad * 1.3f, 0.78f, 0.40f);
  }

  private void genLayer(int layer, int randSeed, int style,
                        float rootX, float rootY, float rootAng, float len0,
                        int maxD, float angRad, float ratio, float chaos) {
    segCount = 0;
    genMaxBirth = 0.0001f;
    java.util.Random rng = new java.util.Random((long) randSeed * 2654435761L + style);
    grow(rootX, rootY, rootAng, len0, 0, 0f, maxD, style, angRad, ratio, chaos, rng);
    if (genMaxBirth < 0.0001f) genMaxBirth = 0.0001f;
    layerMaxBirth[layer] = genMaxBirth;
    buildLayerField(layer);
  }

  private void grow(float x, float y, float ang, float len,
                    int d, float birth, int maxD, int style,
                    float angRad, float rat, float ch, java.util.Random rng) {
    if (d > maxD || len < 0.008f || segCount >= MAX_SEG) return;

    float nx = x + fsin(ang) * len;
    float ny = y - fcos(ang) * len;

    int idx = segCount++;
    segX0[idx] = x;  segY0[idx] = y;
    segX1[idx] = nx; segY1[idx] = ny;
    segBirth[idx] = birth;
    float childBirth = birth + len;
    if (childBirth > genMaxBirth) genMaxBirth = childBirth;

    float cr = len * rat;
    switch (style) {
      case 2: // delta
        grow(nx, ny, ang - angRad * 1.4f + j(ch, angRad, rng), cr * 1.05f, d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        grow(nx, ny, ang               + j(ch, angRad, rng),   cr,         d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad * 1.4f + j(ch, angRad, rng), cr * 1.05f, d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        break;
      case 3: // neural
        grow(nx, ny, ang - angRad        + j(ch, angRad, rng), cr,         d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        grow(nx, ny, ang - angRad * 0.3f + j(ch, angRad, rng), cr * 0.85f, d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad * 0.4f + j(ch, angRad, rng), cr * 0.85f, d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad        + j(ch, angRad, rng), cr,         d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        break;
      default: // upright tree
        grow(nx, ny, ang - angRad + j(ch, angRad, rng), cr,         d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        grow(nx, ny, ang          + j(ch, angRad, rng), cr * 0.9f,  d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad + j(ch, angRad, rng), cr,         d + 1, childBirth, maxD, style, angRad, rat, ch, rng);
        break;
    }
  }

  private float j(float ch, float angRad, java.util.Random rng) {
    return (rng.nextFloat() - 0.5f) * 2f * ch * angRad;
  }

  private void buildLayerField(int layer) {
    float[] dOut = layerDist[layer];
    float[] bOut = layerBirth[layer];
    float invW = 1f / GRID_W, invH = 1f / GRID_H;
    for (int gy = 0; gy < GRID_H; gy++) {
      float py = (gy + 0.5f) * invH;
      for (int gx = 0; gx < GRID_W; gx++) {
        float px = (gx + 0.5f) * invW;
        float best = Float.MAX_VALUE, bestBirth = genMaxBirth;
        for (int s = 0; s < segCount; s++) {
          float abx = segX1[s] - segX0[s], aby = segY1[s] - segY0[s];
          float apx = px - segX0[s], apy = py - segY0[s];
          float ab2 = abx * abx + aby * aby;
          float t = (ab2 > 1e-9f) ? (apx * abx + apy * aby) / ab2 : 0f;
          if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
          float cx = segX0[s] + t * abx, cy = segY0[s] + t * aby;
          float dx = px - cx, dy = py - cy;
          float dd = dx * dx + dy * dy;
          if (dd < best) { best = dd; bestBirth = segBirth[s]; }
        }
        int cell = gy * GRID_W + gx;
        dOut[cell] = (float) Math.sqrt(best);
        bOut[cell] = bestBirth;
      }
    }
  }

  // ─── Sampling ────────────────────────────────────────────────────────────

  // brightness [0,1] for one layer at (u,v), with growth reveal
  private float sampleLayer(int layer, float u, float v, float gr, float w, float glowA) {
    float fx = u * (GRID_W - 1), fy = v * (GRID_H - 1);
    int x0 = (int) fx, y0 = (int) fy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= GRID_W) x1 = GRID_W - 1;
    if (y1 >= GRID_H) y1 = GRID_H - 1;
    float tx = fx - x0, ty = fy - y0;
    int b00 = y0 * GRID_W + x0, b10 = y0 * GRID_W + x1;
    int b01 = y1 * GRID_W + x0, b11 = y1 * GRID_W + x1;

    float[] dA = layerDist[layer];
    float[] bA = layerBirth[layer];
    float dist  = bilerp(dA[b00], dA[b10], dA[b01], dA[b11], tx, ty);
    float birth = bilerp(bA[b00], bA[b10], bA[b01], bA[b11], tx, ty);

    if (birth / layerMaxBirth[layer] > gr) return 0f;

    float core = clamp01(1f - dist / w);
    float glowW = w * (1f + 5f * glowA);
    float halo  = clamp01(1f - dist / glowW) * 0.5f * glowA;
    return core > halo ? core : halo;
  }

  private int sampleComposite(float u, float v, float gr, float w, float glowA,
                              float trunk, float e0, float e1, float e2,
                              float h0, float h1, float h2,
                              float st, float br, float bk) {
    if (u < 0f || u > 1f || v < 0f || v > 1f) return LXColor.BLACK;

    float l0 = sampleLayer(0, u, v, gr, w, glowA) * e0;
    float l1 = sampleLayer(1, u, v, gr, w, glowA) * e1;
    float l2 = sampleLayer(2, u, v, gr, w, glowA) * e2;

    // Trunk emphasis: layer 0 brighter near the central axis (u~0.5).
    float axis = 1f - Math.abs(u - 0.5f) * 2f;
    l0 *= 1f + trunk * clamp01(axis) * 0.8f;

    float total = l0 + l1 + l2;
    if (total < bk) return LXColor.BLACK;

    // Additive hue blend weighted by each layer's contribution.
    float inv = 1f / total;
    float wr = l0 * inv, wg = l1 * inv, wb = l2 * inv;

    // Blend hues on the color wheel via weighted vector sum.
    float hx = wr * fcos(h0 * DEG) + wg * fcos(h1 * DEG) + wb * fcos(h2 * DEG);
    float hy = wr * fsin(h0 * DEG) + wg * fsin(h1 * DEG) + wb * fsin(h2 * DEG);
    float hue = (float) Math.atan2(hy, hx) / DEG;
    hue = ((hue % 360f) + 360f) % 360f;

    float brightness = clamp01(total) * br;
    return LXColor.hsb(hue, st, brightness);
  }

  // ─── Cube rendering ──────────────────────────────────────────────────────

  private void renderCube(float gr, float w, float glowA, float trunk,
                          float e0, float e1, float e2, float h0, float h1, float h2,
                          float st, float br, float bk) {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    for (Face face : cube.exterior.faces)
      renderCubeFace(face, gr, w, glowA, trunk, e0, e1, e2, h0, h1, h2, st, br, bk);
    if (cube.interior != null)
      for (Face face : cube.interior.faces)
        renderCubeFace(face, gr, w, glowA, trunk, e0, e1, e2, h0, h1, h2, st, br, bk);
  }

  private void renderCubeFace(Face face, float gr, float w, float glowA, float trunk,
                              float e0, float e1, float e2, float h0, float h1, float h2,
                              float st, float br, float bk) {
    int cols = Apotheneum.GRID_WIDTH, rows = face.rows.length;
    for (int ri = 0; ri < rows; ri++) {
      Row row = face.rows[ri];
      float v = (float) ri / (rows - 1);
      for (int ci = 0; ci < cols; ci++) {
        float u = (float) ci / (cols - 1);
        colors[row.points[ci].index] =
          sampleComposite(u, v, gr, w, glowA, trunk, e0, e1, e2, h0, h1, h2, st, br, bk);
      }
    }
  }

  // ─── Cylinder rendering ──────────────────────────────────────────────────

  private void renderCylinder(float gr, float w, float glowA, float trunk,
                              float e0, float e1, float e2, float h0, float h1, float h2,
                              float st, float br, float bk) {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    renderCylOrientation(cyl.exterior, gr, w, glowA, trunk, e0, e1, e2, h0, h1, h2, st, br, bk);
    if (cyl.interior != null)
      renderCylOrientation(cyl.interior, gr, w, glowA, trunk, e0, e1, e2, h0, h1, h2, st, br, bk);
  }

  private void renderCylOrientation(Cylinder.Orientation o, float gr, float w, float glowA,
                                    float trunk, float e0, float e1, float e2,
                                    float h0, float h1, float h2,
                                    float st, float br, float bk) {
    Ring[] rings = o.rings;
    int numRings = rings.length;
    int rep = clampi((int) repeat.getValuef(), 1, 6);
    for (int ri = 0; ri < numRings; ri++) {
      Ring ring = rings[ri];
      int np = ring.points.length;
      float v = (float) ri / (numRings - 1);
      for (int pi = 0; pi < np; pi++) {
        float ur = (float) pi / np * rep;
        float u = ur - (float) Math.floor(ur);
        colors[ring.points[pi].index] =
          sampleComposite(u, v, gr, w, glowA, trunk, e0, e1, e2, h0, h1, h2, st, br, bk);
      }
    }
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private float fsin(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return SINL[i < 0 ? i + LUT : i]; }
  private float fcos(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return COSL[i < 0 ? i + LUT : i]; }

  private static float bilerp(float c00, float c10, float c01, float c11, float tx, float ty) {
    return lerp(lerp(c00, c10, tx), lerp(c01, c11, tx), ty);
  }
  private static float lerp(float a, float b, float t) { return a + t * (b - a); }
  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }
  private static int   clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
