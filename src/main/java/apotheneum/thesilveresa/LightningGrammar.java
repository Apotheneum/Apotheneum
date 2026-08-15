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
@LXComponent.Name("Lightning Grammar")
public class LightningGrammar extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;

  private static final int GRID_W = 128;
  private static final int GRID_H = 128;

  private final float[] gridDist  = new float[GRID_W * GRID_H];  // dist to nearest bolt segment
  private final float[] gridLead  = new float[GRID_W * GRID_H];  // leader position (v of nearest seg)
  private final float[] gridOrder = new float[GRID_W * GRID_H];  // 0=main channel, 1+=side spark

  // ─── Segment store ───────────────────────────────────────────────────────
  private static final int MAX_SEG = 900;
  private final float[] segX0 = new float[MAX_SEG];
  private final float[] segY0 = new float[MAX_SEG];
  private final float[] segX1 = new float[MAX_SEG];
  private final float[] segY1 = new float[MAX_SEG];
  private final float[] segLead = new float[MAX_SEG];
  private final float[] segOrder = new float[MAX_SEG];
  private int segCount = 0;

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

  private final CompoundParameter rate = new CompoundParameter("Rate", 40, 0, 100)
    .setDescription("Strike frequency x100");
  private final CompoundParameter jitter = new CompoundParameter("Jitter", 45, 5, 100)
    .setDescription("Lateral wander of the leader x100");
  private final CompoundParameter branchiness = new CompoundParameter("Branch", 45, 0, 100)
    .setDescription("Side-spark probability x100");
  private final CompoundParameter decay = new CompoundParameter("Decay", 35, 5, 100)
    .setDescription("Afterglow decay time x100 (higher = longer glow)");
  private final CompoundParameter thick = new CompoundParameter("Thick", 22, 3, 35)
    .setDescription("Channel thickness x100");
  private final CompoundParameter repeat = new CompoundParameter("Repeat", 2, 1, 5)
    .setDescription("Bolts around cylinder ring");
  private final CompoundParameter hueCore = new CompoundParameter("HueC", 210, 0, 360)
    .setDescription("Core hue (blue-white)");
  private final CompoundParameter hueCorona = new CompoundParameter("HueCor", 275, 0, 360)
    .setDescription("Corona hue (violet halo)");
  private final CompoundParameter glow = new CompoundParameter("Glow", 55, 0, 100)
    .setDescription("Corona halo width x100");
  private float sAuraA = 0.30f, sHAur = 180f;

  private final CompoundParameter hueAura = new CompoundParameter("HueAur", 180, 0, 360)
    .setDescription("Outer aura hue (teal)");
  private final CompoundParameter aura = new CompoundParameter("Aura", 30, 0, 100)
    .setDescription("Outer aura reach and strength x100");
  private final CompoundParameter bright = new CompoundParameter("Bright", 100, 20, 100)
    .setDescription("Output brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 5, 0, 40)
    .setDescription("Black threshold x100");
  private final CompoundParameter seed = new CompoundParameter("Seed", 0, 0, 1000)
    .setDescription("Base random seed");

  private float time = 0f;
  private float lastStrike = -100f;
  private float flashStart = -100f;
  private float nextInterval = 0.4f;
  private long  strikeCounter = 0;

  public LightningGrammar(LX lx) {
    super(lx);
    addParameter("Rate",   this.rate);
    addParameter("Jitter", this.jitter);
    addParameter("Branch", this.branchiness);
    addParameter("Decay",  this.decay);
    addParameter("Thick",  this.thick);
    addParameter("Repeat", this.repeat);
    addParameter("HueC",   this.hueCore);
    addParameter("HueCor", this.hueCorona);
    addParameter("Glow",   this.glow);
    addParameter("HueAur", this.hueAura);
    addParameter("Aura",   this.aura);
    addParameter("Bright", this.bright);
    addParameter("Black",  this.black);
    addParameter("Seed",   this.seed);
  }

  // ─── Main render ─────────────────────────────────────────────────────────

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0);

    // Strike scheduling.
    if (time - lastStrike >= nextInterval) {
      long strikeSeed = (long) seed.getValuef() * 100003L + (strikeCounter++);
      generateBolt(strikeSeed);
      buildDistanceField();
      lastStrike = time;
      flashStart = time;
      nextInterval = scheduleNext();
    }

    float age   = time - flashStart;
    float tau   = decay.getValuef() / 100f * 0.35f + 0.03f;   // 0.03..0.38 s
    float env   = (float) Math.exp(-age / tau);
    float leaderFront = clamp01(age / 0.045f);                // fast top->bottom reveal

    float w      = thick.getValuef() / 100f * 0.03f;
    float glowA  = glow.getValuef() / 100f;
    sAuraA = aura.getValuef() / 100f;
    sHAur  = hueAura.getValuef();
    float hC     = hueCore.getValuef();
    float hCor   = hueCorona.getValuef();
    float br     = bright.getValuef();
    float bk     = black.getValuef() / 100f;

    if (env < 0.01f) {
      setApotheneumColor(LXColor.BLACK);
      return;
    }

    renderCube(env, leaderFront, w, glowA, hC, hCor, br, bk);
    renderCylinder(env, leaderFront, w, glowA, hC, hCor, br, bk);
  }

  private float scheduleNext() {
    float r = rate.getValuef() / 100f;
    float base = lerp(1.4f, 0.10f, r);                        // seconds between strikes
    // Occasional rapid re-illumination (double / triple strike).
    if (Math.random() < 0.28) return base * (0.05f + 0.08f * (float) Math.random());
    return base * (0.7f + 0.6f * (float) Math.random());
  }

  // ─── Bolt generation ─────────────────────────────────────────────────────

  private void generateBolt(long boltSeed) {
    segCount = 0;
    java.util.Random rng = new java.util.Random(boltSeed);

    float jit  = jitter.getValuef() / 100f;
    float brP  = branchiness.getValuef() / 100f;

    // Main channel: top electrode -> ground, path of least resistance approximated
    // by biased descent (pick the least-resistant of a few candidate lateral steps).
    float x = 0.5f + (rng.nextFloat() - 0.5f) * 0.10f;
    float y = 0.02f;
    while (y < 0.98f && segCount < MAX_SEG) {
      float step = 0.02f + rng.nextFloat() * 0.025f;
      // Candidate lateral offsets - choose the one with lowest random "resistance".
      float bestDx = 0f, bestR = Float.MAX_VALUE;
      for (int c = 0; c < 3; c++) {
        float dx = (rng.nextFloat() - 0.5f) * 2f * jit * 0.06f;
        float r = rng.nextFloat();                // resistance field sample
        if (r < bestR) { bestR = r; bestDx = dx; }
      }
      float nx = x + bestDx;
      float ny = y + step;
      if (nx < 0.15f) nx = 0.15f; else if (nx > 0.85f) nx = 0.85f;

      addSeg(x, y, nx, ny, y, 0f);

      // Side sparks fork off the main channel and die quickly.
      if (rng.nextFloat() < brP) {
        spawnSpark(nx, ny, (rng.nextBoolean() ? 1f : -1f), jit, rng, 1);
      }
      x = nx; y = ny;
    }
  }

  private void spawnSpark(float x, float y, float dir, float jit,
                          java.util.Random rng, int order) {
    if (order > 2 || segCount >= MAX_SEG) return;
    int steps = 2 + rng.nextInt(5);
    float ang = dir * (0.5f + rng.nextFloat() * 0.6f);        // veer sideways+down
    for (int s = 0; s < steps && segCount < MAX_SEG; s++) {
      float len = 0.015f + rng.nextFloat() * 0.03f;
      float nx = x + fsin(ang) * len;
      float ny = y + fcos(ang) * len * 0.8f;
      if (nx < 0.12f) nx = 0.12f; else if (nx > 0.88f) nx = 0.88f;
      if (ny > 0.99f) ny = 0.99f;
      addSeg(x, y, nx, ny, y, order);
      // occasional sub-fork
      if (rng.nextFloat() < 0.25f)
        spawnSpark(nx, ny, dir * (rng.nextBoolean() ? 1f : -1f), jit, rng, order + 1);
      ang += (rng.nextFloat() - 0.5f) * jit;
      x = nx; y = ny;
    }
  }

  private void addSeg(float x0, float y0, float x1, float y1, float lead, float order) {
    int i = segCount++;
    segX0[i] = x0; segY0[i] = y0; segX1[i] = x1; segY1[i] = y1;
    segLead[i] = lead; segOrder[i] = order;
  }

  // ─── Distance field ──────────────────────────────────────────────────────

  private void buildDistanceField() {
    float invW = 1f / GRID_W, invH = 1f / GRID_H;
    for (int gy = 0; gy < GRID_H; gy++) {
      float py = (gy + 0.5f) * invH;
      for (int gx = 0; gx < GRID_W; gx++) {
        float px = (gx + 0.5f) * invW;
        float best = Float.MAX_VALUE, bestLead = 1f, bestOrder = 0f;
        for (int s = 0; s < segCount; s++) {
          float abx = segX1[s] - segX0[s], aby = segY1[s] - segY0[s];
          float apx = px - segX0[s], apy = py - segY0[s];
          float ab2 = abx * abx + aby * aby;
          float t = (ab2 > 1e-9f) ? (apx * abx + apy * aby) / ab2 : 0f;
          if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
          float cx = segX0[s] + t * abx, cy = segY0[s] + t * aby;
          float dx = px - cx, dy = py - cy;
          float d = dx * dx + dy * dy;
          if (d < best) {
            best = d;
            bestLead = segLead[s];       // leader position ~ segment start height
            bestOrder = segOrder[s];
          }
        }
        int cell = gy * GRID_W + gx;
        gridDist[cell]  = (float) Math.sqrt(best);
        gridLead[cell]  = bestLead;
        gridOrder[cell] = bestOrder;
      }
    }
  }

  // ─── Grid sampling ───────────────────────────────────────────────────────

  private int sampleGrid(float u, float v, float env, float leaderFront,
                         float w, float glowA, float hC, float hCor,
                         float br, float bk) {
    if (u < 0f || u > 1f || v < 0f || v > 1f) return LXColor.BLACK;

    float fx = u * (GRID_W - 1), fy = v * (GRID_H - 1);
    int x0 = (int) fx, y0 = (int) fy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= GRID_W) x1 = GRID_W - 1;
    if (y1 >= GRID_H) y1 = GRID_H - 1;
    float tx = fx - x0, ty = fy - y0;
    int b00 = y0 * GRID_W + x0, b10 = y0 * GRID_W + x1;
    int b01 = y1 * GRID_W + x0, b11 = y1 * GRID_W + x1;

    float dist  = bilerp(gridDist[b00],  gridDist[b10],  gridDist[b01],  gridDist[b11],  tx, ty);
    float lead  = bilerp(gridLead[b00],  gridLead[b10],  gridLead[b01],  gridLead[b11],  tx, ty);
    float order = bilerp(gridOrder[b00], gridOrder[b10], gridOrder[b01], gridOrder[b11], tx, ty);

    if (lead > leaderFront) return LXColor.BLACK;   // leader hasn't reached here yet

    float core = clamp01(1f - dist / w);
    float glowW = w * (1f + 8f * glowA);
    float halo  = clamp01(1f - dist / glowW);
    float auraW = glowW * (1f + 3.4f * sAuraA);
    float auraF = clamp01(1f - dist / auraW);
    if (core <= 0f && halo <= 0f && auraF <= 0f) return LXColor.BLACK;

    // Side sparks dimmer than the main channel.
    float orderDim = 1f - clamp01(order) * 0.35f;

    float coreBri = core * env * orderDim;
    float haloBri = halo * halo * env * orderDim * (0.4f + 0.6f * glowA);
    // soft power falloff keeps the aura delicate - present, never a wash
    float auraBri = auraF * (float) Math.sqrt(auraF) * env * orderDim * sAuraA * 0.50f;
    float brightness = clamp01(coreBri + haloBri + auraBri) * br;
    if (brightness < bk * br) return LXColor.BLACK;

    // Blue-white hot core -> violet corona at the halo edge.
    float t = clamp01(dist / auraW);
    float hue;
    if (t < 0.25f)      hue = hC   + (hCor  - hC)   * (t / 0.25f);
    else if (t < 0.65f) hue = hCor + (sHAur - hCor) * ((t - 0.25f) / 0.40f);
    else                hue = sHAur;
    hue = ((hue % 360f) + 360f) % 360f;
    float s = 15f + 80f * t;                        // white-hot center, saturated aura

    return LXColor.hsb(hue, s, brightness);
  }

  // ─── Cube rendering ──────────────────────────────────────────────────────

  private void renderCube(float env, float leaderFront, float w, float glowA,
                          float hC, float hCor, float br, float bk) {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    for (Face face : cube.exterior.faces)
      renderCubeFace(face, env, leaderFront, w, glowA, hC, hCor, br, bk);
    if (cube.interior != null)
      for (Face face : cube.interior.faces)
        renderCubeFace(face, env, leaderFront, w, glowA, hC, hCor, br, bk);
  }

  private void renderCubeFace(Face face, float env, float leaderFront, float w, float glowA,
                              float hC, float hCor, float br, float bk) {
    int cols = Apotheneum.GRID_WIDTH, rows = face.rows.length;
    for (int ri = 0; ri < rows; ri++) {
      Row row = face.rows[ri];
      float v = (float) ri / (rows - 1);
      for (int ci = 0; ci < cols; ci++) {
        float u = (float) ci / (cols - 1);
        colors[row.points[ci].index] =
          sampleGrid(u, v, env, leaderFront, w, glowA, hC, hCor, br, bk);
      }
    }
  }

  // ─── Cylinder rendering ──────────────────────────────────────────────────

  private void renderCylinder(float env, float leaderFront, float w, float glowA,
                              float hC, float hCor, float br, float bk) {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    renderCylOrientation(cyl.exterior, env, leaderFront, w, glowA, hC, hCor, br, bk);
    if (cyl.interior != null)
      renderCylOrientation(cyl.interior, env, leaderFront, w, glowA, hC, hCor, br, bk);
  }

  private void renderCylOrientation(Cylinder.Orientation o, float env, float leaderFront,
                                    float w, float glowA, float hC, float hCor,
                                    float br, float bk) {
    Ring[] rings = o.rings;
    int numRings = rings.length;
    int rep = clampi((int) repeat.getValuef(), 1, 5);
    for (int ri = 0; ri < numRings; ri++) {
      Ring ring = rings[ri];
      int np = ring.points.length;
      float v = (float) ri / (numRings - 1);
      for (int pi = 0; pi < np; pi++) {
        float ur = (float) pi / np * rep;
        float u = ur - (float) Math.floor(ur);
        colors[ring.points[pi].index] =
          sampleGrid(u, v, env, leaderFront, w, glowA, hC, hCor, br, bk);
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
