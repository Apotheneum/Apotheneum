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
import heronarts.lx.parameter.TriggerParameter;

import java.util.Random;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Topology")
public class Topology extends ApotheneumPattern {

  private static final int GW = 128, GH = 128;
  private static final int MAX_NODE = 320;
  private static final int MAX_EDGE = 1400;

  // Network

  private final CompoundParameter nodes = new CompoundParameter("Nodes", 90, 8, 320)
    .setDescription("How many nodes in the network");
  private final CompoundParameter order = new CompoundParameter("Order", 100, 0, 100)
    .setDescription("Street grid at 100, informal settlement at 0");
  private final CompoundParameter link = new CompoundParameter("Link", 40, 0, 100)
    .setDescription("Connectivity - push past the threshold and the network percolates");
  private final CompoundParameter hub = new CompoundParameter("Hub", 30, 0, 100)
    .setDescription("Preference for connecting to already-busy nodes");
  private final CompoundParameter reach = new CompoundParameter("Reach", 35, 5, 100)
    .setDescription("How far an edge can span");
  private final CompoundParameter seed = new CompoundParameter("Seed", 4, 0, 40)
    .setDescription("Which network");

  // Traffic

  private final CompoundParameter traffic = new CompoundParameter("Traffic", 55, 0, 100)
    .setDescription("Packets travelling the edges");
  private final CompoundParameter rate = new CompoundParameter("Rate", 45, 0, 100)
    .setDescription("How fast packets move");
  private final CompoundParameter packet = new CompoundParameter("Packet", 40, 5, 100)
    .setDescription("Packet length");

  // Cascade - scene 28

  private final CompoundParameter load = new CompoundParameter("Load", 0, 0, 100)
    .setDescription("Stress on the network - past capacity, nodes start failing");
  private final CompoundParameter recover = new CompoundParameter("Recover", 45, 0, 100)
    .setDescription("How quickly failed nodes come back");
  private final TriggerParameter shock = new TriggerParameter("Shock", this::triggerShock)
    .setDescription("Knock out one node and let the failure propagate");

  // Form

  private final CompoundParameter nodeSize = new CompoundParameter("NodeSz", 35, 0, 100)
    .setDescription("Node size");
  private final CompoundParameter edgeWidth = new CompoundParameter("EdgeW", 22, 0, 100)
    .setDescription("Edge thickness");
  private final CompoundParameter edgeBri = new CompoundParameter("EdgeBri", 45, 0, 100)
    .setDescription("Brightness of the idle edges");
  private final CompoundParameter glow = new CompoundParameter("Glow", 35, 0, 100)
    .setDescription("Halo around nodes and edges");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 200, 0, 360)
    .setDescription("Base hue");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", 30, -180, 180)
    .setDescription("Hue shift from quiet edges to busy hubs");
  private final CompoundParameter hueFail = new CompoundParameter("HueFail", 8, 0, 360)
    .setDescription("Hue of failing nodes");
  private final CompoundParameter satNet = new CompoundParameter("Sat", 55, 0, 100)
    .setDescription("Base saturation");
  private final CompoundParameter satRange = new CompoundParameter("SatRng", -25, -100, 100)
    .setDescription("Saturation shift along the same axis");
  private final CompoundParameter bright = new CompoundParameter("Bright", 88, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");

  // Graph

  private final float[] nX = new float[MAX_NODE];
  private final float[] nY = new float[MAX_NODE];
  private final int[] nDeg = new int[MAX_NODE];
  private final float[] nHealth = new float[MAX_NODE];   // 1 = fine, 0 = failed
  private final float[] nStress = new float[MAX_NODE];
  private int nodeCount = 0;

  private final int[] eA = new int[MAX_EDGE];
  private final int[] eB = new int[MAX_EDGE];
  private final float[] ePhase = new float[MAX_EDGE];
  private final float[] eSpeed = new float[MAX_EDGE];
  private int edgeCount = 0;

  private final float[] fBri = new float[GW * GH];
  private final float[] fDrv = new float[GW * GH];
  private final float[] fFail = new float[GW * GH];

  private final Random rng = new Random(90210L);
  private boolean dirty = true;
  private double sinceRebuild = 1e9;
  private float time = 0f;
  private boolean shockPending = false;

  private float sBr, sBk, sHue, sHueRng, sHueFail, sSat, sSatRng;

  public Topology(LX lx) {
    super(lx);
    addParameter("Nodes",   this.nodes);
    addParameter("Order",   this.order);
    addParameter("Link",    this.link);
    addParameter("Hub",     this.hub);
    addParameter("Reach",   this.reach);
    addParameter("Seed",    this.seed);
    addParameter("Traffic", this.traffic);
    addParameter("Rate",    this.rate);
    addParameter("Packet",  this.packet);
    addParameter("Load",    this.load);
    addParameter("Recover", this.recover);
    addParameter("Shock",   this.shock);
    addParameter("NodeSz",  this.nodeSize);
    addParameter("EdgeW",   this.edgeWidth);
    addParameter("EdgeBri", this.edgeBri);
    addParameter("Glow",    this.glow);
    addParameter("Hue",     this.hue);
    addParameter("HueRng",  this.hueRange);
    addParameter("HueFail", this.hueFail);
    addParameter("Sat",     this.satNet);
    addParameter("SatRng",  this.satRange);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Repeat",  this.repeat);
    addParameter("Sym",     this.symmetry);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == nodes || p == order || p == link || p == hub || p == reach || p == seed) {
      dirty = true;
    }
  }

  private void triggerShock() {
    shockPending = true;
  }

  private void build() {
    Random r = new Random((long) seed.getValuef() * 7717L + 3L);
    nodeCount = clampi(Math.round(nodes.getValuef()), 8, MAX_NODE);
    float ord = order.getValuef() / 100f;

    // A grid at Order=100, drifting into scattered aggregation as it falls.
    // The city and the informal settlement are the same generator.
    int side = (int) Math.ceil(Math.sqrt(nodeCount));
    for (int i = 0; i < nodeCount; i++) {
      int gx = i % side, gy = i / side;
      float regX = 0.08f + (side == 1 ? 0.5f : (float) gx / (side - 1)) * 0.84f;
      float regY = 0.08f + (side == 1 ? 0.5f : (float) gy / (side - 1)) * 0.84f;
      float ranX = 0.05f + r.nextFloat() * 0.90f;
      float ranY = 0.05f + r.nextFloat() * 0.90f;
      nX[i] = ranX + (regX - ranX) * ord;
      nY[i] = ranY + (regY - ranY) * ord;
      nDeg[i] = 0;
      nHealth[i] = 1f;
      nStress[i] = 0f;
    }

    edgeCount = 0;
    float maxLen = reach.getValuef() / 100f * 0.42f;
    float linkP = link.getValuef() / 100f;
    float hubP = hub.getValuef() / 100f;

    // every node joins its nearest neighbour, so there is always a skeleton to
    // read; Link then decides how much redundant connective tissue grows on it
    for (int i = 0; i < nodeCount && edgeCount < MAX_EDGE; i++) {
      int best = -1;
      float bestD = Float.MAX_VALUE;
      for (int j = 0; j < nodeCount; j++) {
        if (j == i) continue;
        float dx = nX[j] - nX[i], dy = nY[j] - nY[i];
        float d = dx * dx + dy * dy;
        if (d < bestD) { bestD = d; best = j; }
      }
      if (best > i) {
        int e = edgeCount++;
        eA[e] = i; eB[e] = best;
        ePhase[e] = r.nextFloat();
        eSpeed[e] = 0.6f + r.nextFloat() * 0.8f;
        nDeg[i]++; nDeg[best]++;
      }
    }

    for (int i = 0; i < nodeCount && edgeCount < MAX_EDGE; i++) {
      for (int j = i + 1; j < nodeCount && edgeCount < MAX_EDGE; j++) {
        float dx = nX[j] - nX[i], dy = nY[j] - nY[i];
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > maxLen) continue;
        // short links stay likely; only the longest span falls away sharply
        float t = d / maxLen;
        float p = linkP * (1f - t * t * t);
        p *= 1f + hubP * 0.6f * (nDeg[i] + nDeg[j]) / 4f;
        if (r.nextFloat() < p) {
          int e = edgeCount++;
          eA[e] = i; eB[e] = j;
          ePhase[e] = r.nextFloat();
          eSpeed[e] = 0.6f + r.nextFloat() * 0.8f;
          nDeg[i]++; nDeg[j]++;
        }
      }
    }
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    sinceRebuild += deltaMs;
    if (dirty && sinceRebuild >= 80.0) {
      build();
      dirty = false;
      sinceRebuild = 0.0;
    }
    if (nodeCount == 0) build();

    float loadA = load.getValuef() / 100f;
    float recA = recover.getValuef() / 100f;
    float trafA = traffic.getValuef() / 100f;
    float rateA = rate.getValuef() / 100f * 1.6f;
    float pkt = 0.02f + packet.getValuef() / 100f * 0.30f;
    float nodeR = nodeSize.getValuef() / 100f * 0.030f;
    float edgeR = edgeWidth.getValuef() / 100f * 0.012f;
    float edgeB = edgeBri.getValuef() / 100f;
    float glowA = glow.getValuef() / 100f;

    sBr      = bright.getValuef();
    sBk      = black.getValuef() / 100f;
    sHue     = hue.getValuef();
    sHueRng  = hueRange.getValuef();
    sHueFail = hueFail.getValuef();
    sSat     = satNet.getValuef();
    sSatRng  = satRange.getValuef();

    // Cascading failure. A node carries load proportional to its degree; when
    // it fails its traffic reroutes onto neighbours, which can push them over
    // too. This is the same tipping the score calls for in scene 28.
    int maxDeg = 1;
    for (int i = 0; i < nodeCount; i++) if (nDeg[i] > maxDeg) maxDeg = nDeg[i];

    if (shockPending) {
      int victim = 0, best = -1;
      for (int i = 0; i < nodeCount; i++) {
        if (nDeg[i] > best) { best = nDeg[i]; victim = i; }
      }
      nHealth[victim] = 0f;
      nStress[victim] = 2f;
      shockPending = false;
    }

    for (int i = 0; i < nodeCount; i++) {
      float base = loadA * (0.35f + 0.65f * nDeg[i] / maxDeg);
      // load shed from failed neighbours lands here
      float extra = 0f;
      for (int e = 0; e < edgeCount; e++) {
        if (eA[e] == i && nHealth[eB[e]] < 0.4f) extra += 0.16f * loadA;
        else if (eB[e] == i && nHealth[eA[e]] < 0.4f) extra += 0.16f * loadA;
      }
      nStress[i] = nStress[i] * 0.92f + (base + extra) * 0.08f * 8f;
      if (nStress[i] > 1f && nHealth[i] > 0f) {
        nHealth[i] -= dt * 2.4f;
        if (nHealth[i] < 0f) nHealth[i] = 0f;
      } else if (nHealth[i] < 1f) {
        nHealth[i] += dt * recA * 0.5f;
        if (nHealth[i] > 1f) nHealth[i] = 1f;
      }
    }

    java.util.Arrays.fill(fBri, 0f);
    java.util.Arrays.fill(fDrv, 0f);
    java.util.Arrays.fill(fFail, 0f);

    // edges
    for (int e = 0; e < edgeCount; e++) {
      int a = eA[e], b = eB[e];
      float health = Math.min(nHealth[a], nHealth[b]);
      if (health <= 0.02f) continue;
      float x0 = nX[a], y0 = nY[a], x1 = nX[b], y1 = nY[b];
      float dx = x1 - x0, dy = y1 - y0;
      float len = (float) Math.sqrt(dx * dx + dy * dy);
      int steps = Math.max(2, (int) (len * 150f));
      float busy = (nDeg[a] + nDeg[b]) / (2f * maxDeg);

      float head = -1f;
      if (trafA > 0f) {
        head = (ePhase[e] + time * rateA * eSpeed[e]) % 1f;
      }

      for (int s = 0; s <= steps; s++) {
        float t = (float) s / steps;
        float px = x0 + dx * t, py = y0 + dy * t;
        float v = edgeB * health;
        if (head >= 0f) {
          float d = Math.abs(t - head);
          if (d > 0.5f) d = 1f - d;
          if (d < pkt) {
            float p = 1f - d / pkt;
            v += p * p * trafA * health;
          }
        }
        splat(px, py, edgeR, v, busy, 1f - health, glowA);
      }
    }

    // nodes
    for (int i = 0; i < nodeCount; i++) {
      float busy = (float) nDeg[i] / maxDeg;
      float v = 0.35f + 0.65f * busy;
      splat(nX[i], nY[i], nodeR * (0.5f + 0.5f * busy),
            v * Math.max(nHealth[i], 0.10f), busy, 1f - nHealth[i], glowA);
    }

    renderCube();
    renderCylinder();
    copyExterior();
  }

  private void splat(float x, float y, float radius, float value, float drv,
                     float fail, float glowA) {
    if (value <= 0f) return;
    float r = radius * (1f + 2.5f * glowA);
    int x0 = (int) ((x - r) * GW), x1 = (int) ((x + r) * GW) + 1;
    int y0 = (int) ((y - r) * GH), y1 = (int) ((y + r) * GH) + 1;
    if (x0 < 0) x0 = 0; if (y0 < 0) y0 = 0;
    if (x1 > GW) x1 = GW; if (y1 > GH) y1 = GH;
    float rr = r * r;
    for (int gy = y0; gy < y1; gy++) {
      float py = (gy + 0.5f) / GH - y;
      for (int gx = x0; gx < x1; gx++) {
        float px = (gx + 0.5f) / GW - x;
        float d2 = px * px + py * py;
        if (d2 > rr) continue;
        float d = (float) Math.sqrt(d2);
        float core = d <= radius ? 1f : 1f - (d - radius) / Math.max(1e-5f, r - radius);
        core = core * core;
        float add = value * core;
        int c = gy * GW + gx;
        if (add > fBri[c]) {
          fBri[c] = add;
          fDrv[c] = drv;
          fFail[c] = fail;
        }
      }
    }
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
        colors[row.points[ci].index] = sampleField(u, v);
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
        colors[ring.points[pi].index] = sampleField(u, v);
      }
    }
  }

  private int sampleField(float u, float v) {
    float gx = u * (GW - 1), gy = v * (GH - 1);
    int x0 = (int) gx, y0 = (int) gy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= GW) x1 = GW - 1;
    if (y1 >= GH) y1 = GH - 1;
    float tx = gx - x0, ty = gy - y0;
    int c00 = y0 * GW + x0, c10 = y0 * GW + x1;
    int c01 = y1 * GW + x0, c11 = y1 * GW + x1;

    float bri = bilerp(fBri[c00], fBri[c10], fBri[c01], fBri[c11], tx, ty);
    if (bri <= 1e-4f) return LXColor.BLACK;
    float drv = bilerp(fDrv[c00], fDrv[c10], fDrv[c01], fDrv[c11], tx, ty);
    float fail = bilerp(fFail[c00], fFail[c10], fFail[c01], fFail[c11], tx, ty);

    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    float h = sHue + sHueRng * drv;
    float s = sSat + sSatRng * drv;
    if (fail > 0.02f) {
      h = h + (sHueFail - h) * clamp01(fail);
      s = s + (100f - s) * clamp01(fail) * 0.8f;
    }
    h = ((h % 360f) + 360f) % 360f;

    return LXColor.hsb(h, clamp01(s / 100f) * 100f, clamp01(bri) * 100f);
  }

  private static float bilerp(float c00, float c10, float c01, float c11, float tx, float ty) {
    float a = c00 + (c10 - c00) * tx;
    float b = c01 + (c11 - c01) * tx;
    return a + (b - a) * ty;
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
