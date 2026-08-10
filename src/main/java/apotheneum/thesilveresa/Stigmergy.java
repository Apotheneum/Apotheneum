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

import java.util.Random;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Stigmergy")
public class Stigmergy extends ApotheneumPattern {

  private static final int TW = 160, TH = 160;   // trail field
  private static final int MAX_A = 5000;         // agents
  private static final int MAX_NODES = 12;       // food sources / hubs
  private static final float TRAIL_CAP = 24f;    // pheromone saturates

  // Colony

  private final CompoundParameter agents = new CompoundParameter("Agents", 55, 5, 100)
    .setDescription("Population laying and following trail");
  private final CompoundParameter deposit = new CompoundParameter("Lay", 50, 0, 100)
    .setDescription("How strongly each agent marks its path");
  private final CompoundParameter evaporate = new CompoundParameter("Evap", 45, 0, 100)
    .setDescription("How fast trail fades - low prunes to trunk routes, high keeps rerouting");
  private final CompoundParameter follow = new CompoundParameter("Follow", 55, 0, 100)
    .setDescription("How strongly agents are drawn to existing trail");
  private final CompoundParameter sniff = new CompoundParameter("Sniff", 40, 5, 100)
    .setDescription("How far ahead an agent samples for trail");
  private final CompoundParameter waver = new CompoundParameter("Waver", 30, 0, 100)
    .setDescription("Random exploration that keeps finding new routes");
  private final CompoundParameter spread = new CompoundParameter("Spread", 35, 0, 100)
    .setDescription("How far trail diffuses sideways");

  // Network

  private final CompoundParameter nodes = new CompoundParameter("Nodes", 6, 0, 12)
    .setDescription("Food sources the colony connects - 0 is free foraging");
  private final CompoundParameter pull = new CompoundParameter("Pull", 45, 0, 100)
    .setDescription("How strongly agents are drawn between nodes");
  private final CompoundParameter layout = new CompoundParameter("Layout", 0, 0, 100)
    .setDescription("Node arrangement - 0 is scattered, 100 is a ring");
  private final CompoundParameter seed = new CompoundParameter("Seed", 7, 0, 40)
    .setDescription("Which node arrangement");
  private final CompoundParameter showNodes = new CompoundParameter("NodeBri", 45, 0, 100)
    .setDescription("Brightness of the nodes themselves");

  // Speed

  private final CompoundParameter speed = new CompoundParameter("Speed", 45, 5, 100)
    .setDescription("Agent travel speed");
  private final CompoundParameter turn = new CompoundParameter("Turn", 55, 5, 100)
    .setDescription("How sharply agents can steer onto trail");

  // Matter

  private final CompoundParameter grain = new CompoundParameter("Grain", 35, 0, 100)
    .setDescription("Trail hardness - low is diffuse, high is sharp-edged");
  private final CompoundParameter gain = new CompoundParameter("Gain", 50, 5, 100)
    .setDescription("Trail strength to brightness response");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 38, 0, 360)
    .setDescription("Base hue of faint trail");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", 30, -180, 180)
    .setDescription("Hue shift from faint trail to established route");
  private final CompoundParameter satTrail = new CompoundParameter("Sat", 55, 0, 100)
    .setDescription("Saturation of faint trail");
  private final CompoundParameter satRange = new CompoundParameter("SatRng", -30, -100, 100)
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

  // Agents

  private final float[] ax = new float[MAX_A];
  private final float[] ay = new float[MAX_A];
  private final float[] aang = new float[MAX_A];
  private final int[] aTarget = new int[MAX_A];
  private int liveCount = 0;

  private final float[] trail = new float[TW * TH];
  private final float[] blur = new float[TW * TH];

  private final float[] nodeX = new float[MAX_NODES];
  private final float[] nodeY = new float[MAX_NODES];
  private int nodeCount = 0;
  private float prevSeed = -1f, prevLayout = -1f, prevNodes = -1f;

  private final Random rng = new Random(31337L);
  private float tMax = 1f;

  private float sGain, sBr, sBk, sGrain;
  private float sHue, sHueRng, sSat, sSatRng, sNodeBri;

  public Stigmergy(LX lx) {
    super(lx);
    addParameter("Agents",  this.agents);
    addParameter("Lay",     this.deposit);
    addParameter("Evap",    this.evaporate);
    addParameter("Follow",  this.follow);
    addParameter("Sniff",   this.sniff);
    addParameter("Waver",   this.waver);
    addParameter("Spread",  this.spread);
    addParameter("Nodes",   this.nodes);
    addParameter("Pull",    this.pull);
    addParameter("Layout",  this.layout);
    addParameter("Seed",    this.seed);
    addParameter("NodeBri", this.showNodes);
    addParameter("Speed",   this.speed);
    addParameter("Turn",    this.turn);
    addParameter("Grain",   this.grain);
    addParameter("Gain",    this.gain);
    addParameter("Hue",     this.hue);
    addParameter("HueRng",  this.hueRange);
    addParameter("Sat",     this.satTrail);
    addParameter("SatRng",  this.satRange);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Repeat",  this.repeat);
    addParameter("Sym",     this.symmetry);
  }

  private void placeNodes() {
    nodeCount = clampi(Math.round(nodes.getValuef()), 0, MAX_NODES);
    Random r = new Random((long) seed.getValuef() * 2749L + 17L);
    float ring = layout.getValuef() / 100f;
    for (int i = 0; i < nodeCount; i++) {
      float sx = 0.12f + r.nextFloat() * 0.76f;
      float sy = 0.12f + r.nextFloat() * 0.76f;
      float a = (float) (i * 2.0 * Math.PI / Math.max(1, nodeCount));
      float rx = 0.5f + (float) Math.cos(a) * 0.35f;
      float ry = 0.5f + (float) Math.sin(a) * 0.35f;
      nodeX[i] = sx + (rx - sx) * ring;
      nodeY[i] = sy + (ry - sy) * ring;
    }
  }

  private void hatch(int i) {
    if (nodeCount > 0) {
      int n = rng.nextInt(nodeCount);
      ax[i] = nodeX[n] + (rng.nextFloat() - 0.5f) * 0.02f;
      ay[i] = nodeY[n] + (rng.nextFloat() - 0.5f) * 0.02f;
      aTarget[i] = nodeCount > 1 ? pickOther(n) : n;
    } else {
      ax[i] = rng.nextFloat();
      ay[i] = rng.nextFloat();
      aTarget[i] = -1;
    }
    aang[i] = rng.nextFloat() * 6.2832f;
  }

  private int pickOther(int not) {
    int n = rng.nextInt(nodeCount);
    if (n == not) n = (n + 1) % nodeCount;
    return n;
  }

  private float sampleTrail(float x, float y) {
    int gx = (int) (x * TW), gy = (int) (y * TH);
    if (gx < 0 || gy < 0 || gx >= TW || gy >= TH) return 0f;
    return trail[gy * TW + gx];
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);

    if (seed.getValuef() != prevSeed || layout.getValuef() != prevLayout
        || nodes.getValuef() != prevNodes) {
      placeNodes();
      prevSeed = seed.getValuef();
      prevLayout = layout.getValuef();
      prevNodes = nodes.getValuef();
    }

    int target = (int) (agents.getValuef() / 100f * MAX_A);
    if (target < 1) target = 1;
    while (liveCount < target) hatch(liveCount++);
    if (liveCount > target) liveCount = target;

    float lay    = deposit.getValuef() / 100f * 2.4f;
    float evap   = evaporate.getValuef() / 100f;
    float foll   = follow.getValuef() / 100f * 3.2f;
    float snif   = 0.008f + sniff.getValuef() / 100f * 0.055f;
    float wav    = waver.getValuef() / 100f * 3.4f;
    float diff   = spread.getValuef() / 100f;
    float pullA  = pull.getValuef() / 100f * 2.6f;
    float cruise = speed.getValuef() / 100f * 0.30f;
    float turnA  = turn.getValuef() / 100f * 7f;

    sGrain   = grain.getValuef() / 100f;
    sGain    = gain.getValuef() / 100f * 7f;
    sBr      = bright.getValuef();
    sBk      = black.getValuef() / 100f;
    sHue     = hue.getValuef();
    sHueRng  = hueRange.getValuef();
    sSat     = satTrail.getValuef();
    sSatRng  = satRange.getValuef();
    sNodeBri = showNodes.getValuef() / 100f;

    // Evaporation is the whole difference between the two behaviours: fast
    // decay keeps the colony rerouting, slow decay lets the mesh prune down
    // to a few trunk routes.
    float keep = 1f - evap * 0.06f;
    if (keep < 0.86f) keep = 0.86f;
    for (int i = 0; i < trail.length; i++) trail[i] *= keep;

    // sideways diffusion, so routes have soft shoulders rather than aliasing
    if (diff > 0.001f) {
      float w = diff * 0.22f;
      for (int y = 1; y < TH - 1; y++) {
        int row = y * TW;
        for (int x = 1; x < TW - 1; x++) {
          int c = row + x;
          float s = trail[c - 1] + trail[c + 1] + trail[c - TW] + trail[c + TW];
          blur[c] = trail[c] * (1f - w) + s * 0.25f * w;
        }
      }
      System.arraycopy(blur, TW, trail, TW, (TH - 2) * TW);
    }

    for (int i = 0; i < liveCount; i++) {
      float x = ax[i], y = ay[i], a = aang[i];

      // Sample trail ahead, left and right - the classic three-sensor rule.
      // Agents are attracted to their own species' deposits, which is what
      // makes a path self-reinforcing.
      float aheadA = a, leftA = a - 0.6f, rightA = a + 0.6f;
      float sA = sampleTrail(x + (float) Math.cos(aheadA) * snif, y + (float) Math.sin(aheadA) * snif);
      float sL = sampleTrail(x + (float) Math.cos(leftA)  * snif, y + (float) Math.sin(leftA)  * snif);
      float sR = sampleTrail(x + (float) Math.cos(rightA) * snif, y + (float) Math.sin(rightA) * snif);

      float steer = 0f;
      if (sA >= sL && sA >= sR) {
        steer = 0f;
      } else if (sL > sR) {
        steer = -1f;
      } else if (sR > sL) {
        steer = 1f;
      }
      a += steer * foll * turnA * dt;

      // drawn toward the node it is currently heading for
      if (aTarget[i] >= 0 && pullA > 0f) {
        float dx = nodeX[aTarget[i]] - x, dy = nodeY[aTarget[i]] - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.03f) {
          aTarget[i] = nodeCount > 1 ? pickOther(aTarget[i]) : aTarget[i];
        } else if (d > 1e-5f) {
          float want = (float) Math.atan2(dy / d, dx / d);
          float diffA = want - a;
          while (diffA > 3.1416f) diffA -= 6.2832f;
          while (diffA < -3.1416f) diffA += 6.2832f;
          a += diffA * pullA * dt;
        }
      }

      a += (rng.nextFloat() - 0.5f) * wav * dt;

      x += (float) Math.cos(a) * cruise * dt;
      y += (float) Math.sin(a) * cruise * dt;

      if (x < 0.005f || x > 0.995f || y < 0.005f || y > 0.995f) {
        if (nodeCount > 0) {
          hatch(i);
          continue;
        }
        if (x < 0.005f) { x = 0.005f; a = 3.1416f - a; }
        if (x > 0.995f) { x = 0.995f; a = 3.1416f - a; }
        if (y < 0.005f) { y = 0.005f; a = -a; }
        if (y > 0.995f) { y = 0.995f; a = -a; }
      }

      ax[i] = x; ay[i] = y; aang[i] = a;

      int gx = (int) (x * TW), gy = (int) (y * TH);
      if (gx >= 0 && gy >= 0 && gx < TW && gy < TH) {
        int c = gy * TW + gx;
        // saturating deposit, otherwise node hotspots swamp the whole field
        if (trail[c] < TRAIL_CAP) trail[c] += lay;
      }
    }

    tMax = TRAIL_CAP;

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
    float gx = u * (TW - 1), gy = v * (TH - 1);
    int x0 = (int) gx, y0 = (int) gy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= TW) x1 = TW - 1;
    if (y1 >= TH) y1 = TH - 1;
    float tx = gx - x0, ty = gy - y0;
    float t = bilerp(trail[y0 * TW + x0], trail[y0 * TW + x1],
                     trail[y1 * TW + x0], trail[y1 * TW + x1], tx, ty);

    float bri = 1f - (float) Math.exp(-t * sGain);
    if (sGrain > 0f) {
      float hard = bri * bri * (3f - 2f * bri);
      bri = bri + (hard - bri) * sGrain;
    }

    // nodes glow faintly so the network being solved stays legible
    if (sNodeBri > 0f) {
      for (int i = 0; i < nodeCount; i++) {
        float dx = u - nodeX[i], dy = v - nodeY[i];
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.022f) {
          float g = (1f - d / 0.022f) * sNodeBri;
          if (g > bri) bri = g;
        }
      }
    }

    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    // established routes read differently from exploratory wandering
    float strength = clamp01(t / tMax);
    float h = sHue + sHueRng * strength;
    h = ((h % 360f) + 360f) % 360f;
    float s = sSat + sSatRng * strength;

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
