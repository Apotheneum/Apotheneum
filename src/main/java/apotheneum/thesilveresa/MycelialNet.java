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
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Mycelial Net")
public class MycelialNet extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;

  private static final int GRID_W = 128;
  private static final int GRID_H = 128;

  private final float[] gridDist = new float[GRID_W * GRID_H];  // dist to nearest edge
  private final float[] gridFlow = new float[GRID_W * GRID_H];  // interpolated flow along edge
  private boolean dirty = true;

  // ─── Graph store ─────────────────────────────────────────────────────────
  private static final int MAX_NODE = 260;
  private static final int MAX_EDGE = 520;
  private final float[] nodeX = new float[MAX_NODE];
  private final float[] nodeY = new float[MAX_NODE];
  private final float[] nodeFlow = new float[MAX_NODE];
  private int nodeCount = 0;

  private final int[] edgeA = new int[MAX_EDGE];
  private final int[] edgeB = new int[MAX_EDGE];
  private int edgeCount = 0;
  private float maxFlow = 1f;

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

  private final CompoundParameter density = new CompoundParameter("Density", 55, 20, 100)
    .setDescription("Network node count / complexity (structural)");
  private final CompoundParameter spread = new CompoundParameter("Spread", 35, 5, 80)
    .setDescription("Vertical wander of hyphae (structural)");
  private final CompoundParameter loops = new CompoundParameter("Loops", 40, 0, 100)
    .setDescription("Anastomosis probability x100 (structural)");
  private final CompoundParameter growth = new CompoundParameter("Growth", 100, 0, 100)
    .setDescription("Growth reveal x100 (0=origin, 100=full web)");
  private final CompoundParameter thick = new CompoundParameter("Thick", 30, 8, 90)
    .setDescription("Hypha thickness x100");
  private final CompoundParameter flowDensity = new CompoundParameter("FlowDen", 30, 0, 100)
    .setDescription("Nutrient pulse density x100");
  private final CompoundParameter flowSpeed = new CompoundParameter("FlowSp", 30, 0, 100)
    .setDescription("Nutrient pulse speed x100");
  private final CompoundParameter repeat = new CompoundParameter("Repeat", 2, 1, 6)
    .setDescription("Web tiles around cylinder ring");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", true);
  private final CompoundParameter hue = new CompoundParameter("Hue", 40, 0, 360)
    .setDescription("Base hypha hue");
  private final CompoundParameter huePulse = new CompoundParameter("HueP", 55, 0, 360)
    .setDescription("Nutrient pulse hue");
  private final CompoundParameter sat = new CompoundParameter("Sat", 45, 0, 100)
    .setDescription("Saturation");
  private final CompoundParameter glow = new CompoundParameter("Glow", 35, 0, 100)
    .setDescription("Halo / glow x100");
  private final CompoundParameter bright = new CompoundParameter("Bright", 92, 20, 100)
    .setDescription("Output brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 6, 0, 40)
    .setDescription("Black threshold x100");
  private final CompoundParameter seed = new CompoundParameter("Seed", 0, 0, 1000)
    .setDescription("Random seed (change to regrow)");

  private float time = 0f;
  private float prevSeed = -1f;

  public MycelialNet(LX lx) {
    super(lx);
    addParameter("Density", this.density);
    addParameter("Spread",  this.spread);
    addParameter("Loops",   this.loops);
    addParameter("Growth",  this.growth);
    addParameter("Thick",   this.thick);
    addParameter("FlowDen", this.flowDensity);
    addParameter("FlowSp",  this.flowSpeed);
    addParameter("Repeat",  this.repeat);
    addParameter("Sym",     this.symmetry);
    this.symmetry.setDescription("Mirror alternate faces / tiles for continuous symmetry");
    addParameter("Hue",     this.hue);
    addParameter("HueP",    this.huePulse);
    addParameter("Sat",     this.sat);
    addParameter("Glow",    this.glow);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Seed",    this.seed);
  }

  @Override
  public void onParameterChanged(heronarts.lx.parameter.LXParameter p) {
    if (p == density || p == spread || p == loops) dirty = true;
  }

  // ─── Main render ─────────────────────────────────────────────────────────

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0);

    float seedVal = seed.getValuef();
    if (dirty || Math.abs(seedVal - prevSeed) > 0.01f) {
      generate((int) seedVal);
      buildDistanceField();
      prevSeed = seedVal;
      dirty = false;
    }

    float gr     = growth.getValuef() / 100f;
    float w      = thick.getValuef() / 100f * 0.035f;
    float glowA  = glow.getValuef() / 100f;
    float fDen   = flowDensity.getValuef() / 100f * 12f;   // pulse repeats along flow
    float fSp    = flowSpeed.getValuef() / 100f * 1.5f;
    float pulseAmt = flowDensity.getValuef() / 100f;        // 0 hides pulses
    float h      = hue.getValuef();
    float hP     = huePulse.getValuef();
    float st     = sat.getValuef();
    float br     = bright.getValuef();
    float bk     = black.getValuef() / 100f;

    float flowPhase = time * fSp;

    renderCube(gr, w, glowA, fDen, fSp, pulseAmt, flowPhase, h, hP, st, br, bk);
    renderCylinder(gr, w, glowA, fDen, fSp, pulseAmt, flowPhase, h, hP, st, br, bk);
  }

  // ─── Graph generation ────────────────────────────────────────────────────

  private void generate(int randSeed) {
    nodeCount = 0;
    edgeCount = 0;
    maxFlow = 0.0001f;

    int   target = (int)(density.getValuef() * 2.4f);       // ~48..240 nodes
    if (target > MAX_NODE) target = MAX_NODE;
    float vWander = spread.getValuef() / 100f;               // vertical spread
    float loopP   = loops.getValuef() / 100f;

    java.util.Random rng = new java.util.Random((long) randSeed * 40503L + 17);

    // Origin at left-center.
    addNode(0.05f, 0.5f, 0f);

    // Simple frontier queue (index ring buffer).
    int[] frontier = new int[MAX_NODE];
    int fHead = 0, fTail = 0;
    frontier[fTail++] = 0;

    while (nodeCount < target && fHead < fTail) {
      int i = frontier[fHead++];
      float x = nodeX[i], y = nodeY[i], flow = nodeFlow[i];

      int branches = 1 + rng.nextInt(3);           // 1..3
      for (int b = 0; b < branches && nodeCount < target; b++) {
        // Horizontally-biased heading: mostly rightward, some vertical wander.
        float ang = (rng.nextFloat() - 0.5f) * 2f * vWander * (PI * 0.5f);
        float len = 0.05f + rng.nextFloat() * 0.10f;
        float nx = x + fcos(ang) * len;
        float ny = y + fsin(ang) * len;
        if (nx > 0.98f) nx = 0.98f;
        if (ny < 0.03f) ny = 0.03f; else if (ny > 0.97f) ny = 0.97f;

        // Anastomosis: fuse to a nearby existing node instead of spawning.
        int near = nearestNode(nx, ny, i, 0.06f);
        if (near >= 0 && rng.nextFloat() < loopP) {
          addEdge(i, near);
        } else {
          int ni = addNode(nx, ny, flow + len);
          if (ni >= 0) {
            addEdge(i, ni);
            if (fTail < MAX_NODE) frontier[fTail++] = ni;
          }
        }
      }
    }

    // A few long-range fusions to close larger loops (information shortcuts).
    int extraLoops = (int)(loopP * 20f);
    for (int k = 0; k < extraLoops && edgeCount < MAX_EDGE; k++) {
      int a = rng.nextInt(nodeCount);
      int nb = nearestNode(nodeX[a], nodeY[a], a, 0.14f);
      if (nb >= 0) addEdge(a, nb);
    }

    for (int n = 0; n < nodeCount; n++)
      if (nodeFlow[n] > maxFlow) maxFlow = nodeFlow[n];
    if (maxFlow < 0.0001f) maxFlow = 0.0001f;
  }

  private int addNode(float x, float y, float flow) {
    if (nodeCount >= MAX_NODE) return -1;
    int i = nodeCount++;
    nodeX[i] = x; nodeY[i] = y; nodeFlow[i] = flow;
    return i;
  }

  private void addEdge(int a, int b) {
    if (edgeCount >= MAX_EDGE || a == b) return;
    edgeA[edgeCount] = a; edgeB[edgeCount] = b; edgeCount++;
  }

  private int nearestNode(float x, float y, int exclude, float maxDist) {
    int best = -1; float bestD = maxDist * maxDist;
    for (int n = 0; n < nodeCount; n++) {
      if (n == exclude) continue;
      float dx = nodeX[n] - x, dy = nodeY[n] - y;
      float d = dx * dx + dy * dy;
      if (d < bestD) { bestD = d; best = n; }
    }
    return best;
  }

  // ─── Distance field ──────────────────────────────────────────────────────

  private void buildDistanceField() {
    float invW = 1f / GRID_W, invH = 1f / GRID_H;
    for (int gy = 0; gy < GRID_H; gy++) {
      float py = (gy + 0.5f) * invH;
      for (int gx = 0; gx < GRID_W; gx++) {
        float px = (gx + 0.5f) * invW;
        float best = Float.MAX_VALUE, bestFlow = maxFlow;
        for (int e = 0; e < edgeCount; e++) {
          int a = edgeA[e], b = edgeB[e];
          float ax = nodeX[a], ay = nodeY[a], bx = nodeX[b], by = nodeY[b];
          float abx = bx - ax, aby = by - ay;
          float apx = px - ax, apy = py - ay;
          float ab2 = abx * abx + aby * aby;
          float t = (ab2 > 1e-9f) ? (apx * abx + apy * aby) / ab2 : 0f;
          if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
          float cx = ax + t * abx, cy = ay + t * aby;
          float dx = px - cx, dy = py - cy;
          float d = dx * dx + dy * dy;
          if (d < best) {
            best = d;
            bestFlow = nodeFlow[a] + t * (nodeFlow[b] - nodeFlow[a]);
          }
        }
        int cell = gy * GRID_W + gx;
        gridDist[cell] = (float) Math.sqrt(best);
        gridFlow[cell] = bestFlow;
      }
    }
  }

  // ─── Grid sampling ───────────────────────────────────────────────────────

  private int sampleGrid(float u, float v, float gr, float w, float glowA,
                         float fDen, float fSp, float pulseAmt, float flowPhase,
                         float h, float hP, float st, float br, float bk) {
    if (u < 0f || u > 1f || v < 0f || v > 1f) return LXColor.BLACK;

    float fx = u * (GRID_W - 1), fy = v * (GRID_H - 1);
    int x0 = (int) fx, y0 = (int) fy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= GRID_W) x1 = GRID_W - 1;
    if (y1 >= GRID_H) y1 = GRID_H - 1;
    float tx = fx - x0, ty = fy - y0;
    int b00 = y0 * GRID_W + x0, b10 = y0 * GRID_W + x1;
    int b01 = y1 * GRID_W + x0, b11 = y1 * GRID_W + x1;

    float dist = bilerp(gridDist[b00], gridDist[b10], gridDist[b01], gridDist[b11], tx, ty);
    float flow = bilerp(gridFlow[b00], gridFlow[b10], gridFlow[b01], gridFlow[b11], tx, ty);

    float flowNorm = flow / maxFlow;
    if (flowNorm > gr) return LXColor.BLACK;   // not yet grown

    float core = clamp01(1f - dist / w);
    float glowW = w * (1f + 5f * glowA);
    float halo  = clamp01(1f - dist / glowW) * 0.5f * glowA;
    float lineBri = core > halo ? core : halo;
    if (lineBri < bk) return LXColor.BLACK;

    // Nutrient pulse traveling outward along the flow axis.
    float pulse = 0f;
    if (pulseAmt > 0f) {
      float phase = flow * fDen - flowPhase;
      float fr = phase - (float) Math.floor(phase);     // [0,1)
      // sharp bright crest
      float tri = 1f - Math.abs(fr - 0.5f) * 2f;         // triangle 0..1
      pulse = clamp01((tri - 0.7f) / 0.3f) * pulseAmt;
    }

    float brightness = lineBri * br;
    float hCol = h;
    float sCol = st;
    if (pulse > 0f) {
      // pulses ride on top: brighten and shift hue toward the pulse color
      brightness = clamp01(lineBri + pulse * 0.8f) * br;
      hCol = h + (hP - h) * pulse;
      sCol = st + (100f - st) * pulse * 0.5f;
    }
    if (brightness < bk * br) return LXColor.BLACK;

    hCol = ((hCol % 360f) + 360f) % 360f;
    return LXColor.hsb(hCol, sCol, brightness);
  }

  // ─── Cube rendering ──────────────────────────────────────────────────────

  private void renderCube(float gr, float w, float glowA, float fDen, float fSp,
                          float pulseAmt, float flowPhase, float h, float hP,
                          float st, float br, float bk) {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    boolean sym = symmetry.isOn();
    Face[] ext = cube.exterior.faces;
    for (int f = 0; f < ext.length; f++)
      renderCubeFace(ext[f], sym && ((f & 1) == 1), gr, w, glowA, fDen, fSp, pulseAmt, flowPhase, h, hP, st, br, bk);
    if (cube.interior != null) {
      Face[] in = cube.interior.faces;
      for (int f = 0; f < in.length; f++)
        renderCubeFace(in[f], sym && ((f & 1) == 1), gr, w, glowA, fDen, fSp, pulseAmt, flowPhase, h, hP, st, br, bk);
    }
  }

  private void renderCubeFace(Face face, boolean flip, float gr, float w, float glowA, float fDen, float fSp,
                              float pulseAmt, float flowPhase, float h, float hP,
                              float st, float br, float bk) {
    int cols = Apotheneum.GRID_WIDTH, rows = face.rows.length;
    for (int ri = 0; ri < rows; ri++) {
      Row row = face.rows[ri];
      float v = (float) ri / (rows - 1);
      for (int ci = 0; ci < cols; ci++) {
        float u = (float) ci / (cols - 1);
        if (flip) u = 1f - u;
        colors[row.points[ci].index] =
          sampleGrid(u, v, gr, w, glowA, fDen, fSp, pulseAmt, flowPhase, h, hP, st, br, bk);
      }
    }
  }

  // ─── Cylinder rendering ──────────────────────────────────────────────────

  private void renderCylinder(float gr, float w, float glowA, float fDen, float fSp,
                              float pulseAmt, float flowPhase, float h, float hP,
                              float st, float br, float bk) {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    renderCylOrientation(cyl.exterior, gr, w, glowA, fDen, fSp, pulseAmt, flowPhase, h, hP, st, br, bk);
    if (cyl.interior != null)
      renderCylOrientation(cyl.interior, gr, w, glowA, fDen, fSp, pulseAmt, flowPhase, h, hP, st, br, bk);
  }

  private void renderCylOrientation(Cylinder.Orientation o, float gr, float w, float glowA,
                                    float fDen, float fSp, float pulseAmt, float flowPhase,
                                    float h, float hP, float st, float br, float bk) {
    Ring[] rings = o.rings;
    int numRings = rings.length;
    boolean sym = symmetry.isOn();
    int rep = clampi((int) repeat.getValuef(), 1, 6);
    if (sym) rep = Math.max(2, (rep / 2) * 2);   // even tile count wraps seamlessly
    for (int ri = 0; ri < numRings; ri++) {
      Ring ring = rings[ri];
      int np = ring.points.length;
      float v = (float) ri / (numRings - 1);
      for (int pi = 0; pi < np; pi++) {
        float g = (float) pi / np * rep;
        float u;
        if (sym) {
          float gg = g - 2f * (float) Math.floor(g * 0.5f);   // g mod 2 in [0,2)
          u = gg <= 1f ? gg : 2f - gg;                        // triangle fold
        } else {
          u = g - (float) Math.floor(g);
        }
        colors[ring.points[pi].index] =
          sampleGrid(u, v, gr, w, glowA, fDen, fSp, pulseAmt, flowPhase, h, hP, st, br, bk);
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
