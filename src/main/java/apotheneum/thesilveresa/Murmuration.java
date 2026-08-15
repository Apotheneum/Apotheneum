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
@LXComponent.Name("Murmuration")
public class Murmuration extends ApotheneumPattern {

  private static final int FW = 128, FH = 128;   // density buffer
  private static final int MAX_B = 2400;         // birds
  private static final int GN = 22;              // spatial hash cells per axis
  private static final int MAX_K = 12;           // neighbours tracked

  // Flock

  private final CompoundParameter count = new CompoundParameter("Birds", 55, 5, 100)
    .setDescription("How many birds are in the flock");
  private final CompoundParameter neighbors = new CompoundParameter("Nbrs", 7, 2, 12)
    .setDescription("Neighbours each bird tracks - starlings use about seven");
  private final CompoundParameter separation = new CompoundParameter("Sep", 55, 0, 100)
    .setDescription("Push away from crowding neighbours");
  private final CompoundParameter alignment = new CompoundParameter("Align", 60, 0, 100)
    .setDescription("Match the heading of neighbours");
  private final CompoundParameter cohesion = new CompoundParameter("Cohere", 45, 0, 100)
    .setDescription("Pull toward the centre of neighbours");
  private final CompoundParameter personal = new CompoundParameter("Space", 30, 5, 100)
    .setDescription("Distance a bird wants to keep");

  // Motion

  private final CompoundParameter speed = new CompoundParameter("Speed", 45, 5, 100)
    .setDescription("Cruising speed");
  private final CompoundParameter turn = new CompoundParameter("Turn", 50, 5, 100)
    .setDescription("How sharply a bird can change heading");
  private final CompoundParameter wander = new CompoundParameter("Wander", 25, 0, 100)
    .setDescription("Restlessness in the absence of neighbours");
  private final CompoundParameter roost = new CompoundParameter("Roost", 48, 0, 100)
    .setDescription("Pull that keeps the flock wheeling in view");

  // Predator - a murmuration is at its most dramatic under attack

  private final CompoundParameter hawk = new CompoundParameter("Hawk", 0, 0, 100)
    .setDescription("A predator the flock cleaves around");
  private final CompoundParameter hawkSpeed = new CompoundParameter("HkSpd", 40, 0, 100)
    .setDescription("How fast the predator moves");
  private final CompoundParameter hawkReach = new CompoundParameter("HkRch", 30, 5, 100)
    .setDescription("How far the panic spreads");

  // Matter

  private final CompoundParameter trail = new CompoundParameter("Trail", 45, 0, 100)
    .setDescription("How long motion streaks persist");
  private final CompoundParameter grain = new CompoundParameter("Grain", 45, 0, 100)
    .setDescription("Particulate hardness - low is smoky, high is granular");
  private final CompoundParameter gain = new CompoundParameter("Gain", 55, 5, 100)
    .setDescription("Density to brightness response");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 30, 0, 360)
    .setDescription("Base hue");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", 25, -180, 180)
    .setDescription("How far hue travels across the driver - negative reverses");
  private final CompoundParameter hueMap = new CompoundParameter("HueMap", 40, 0, 100)
    .setDescription("What shifts the hue - 0 is flock density, 100 is speed");
  private final CompoundParameter satBird = new CompoundParameter("Sat", 35, 0, 100)
    .setDescription("Base saturation");
  private final CompoundParameter satRange = new CompoundParameter("SatRng", -20, -100, 100)
    .setDescription("How saturation changes across the same driver");
  private final CompoundParameter bright = new CompoundParameter("Bright", 90, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");

  // Birds

  private final float[] bx = new float[MAX_B];
  private final float[] by = new float[MAX_B];
  private final float[] bvx = new float[MAX_B];
  private final float[] bvy = new float[MAX_B];
  private int liveCount = 0;

  // Spatial hash - a linked list per cell, rebuilt each frame, no allocation
  private final int[] cellHead = new int[GN * GN];
  private final int[] nextBird = new int[MAX_B];

  // Scratch for the nearest-neighbour search
  private final float[] kDist = new float[MAX_K];
  private final int[] kIdx = new int[MAX_K];

  private final float[] fDens = new float[FW * FH];
  private final float[] fSpeed = new float[FW * FH];

  private final Random rng = new Random(8675309L);
  private float time = 0f;
  private float hawkX = 0.5f, hawkY = 0.5f, hawkAng = 0f;
  private float dMax = 1f;

  private float sGain, sBr, sBk, sGrain;
  private float sHue, sHueRng, sHueMap, sSat, sSatRng;

  public Murmuration(LX lx) {
    super(lx);
    addParameter("Birds",  this.count);
    addParameter("Nbrs",   this.neighbors);
    addParameter("Sep",    this.separation);
    addParameter("Align",  this.alignment);
    addParameter("Cohere", this.cohesion);
    addParameter("Space",  this.personal);
    addParameter("Speed",  this.speed);
    addParameter("Turn",   this.turn);
    addParameter("Wander", this.wander);
    addParameter("Roost",  this.roost);
    addParameter("Hawk",   this.hawk);
    addParameter("HkSpd",  this.hawkSpeed);
    addParameter("HkRch",  this.hawkReach);
    addParameter("Trail",  this.trail);
    addParameter("Grain",  this.grain);
    addParameter("Gain",   this.gain);
    addParameter("Hue",    this.hue);
    addParameter("HueRng", this.hueRange);
    addParameter("HueMap", this.hueMap);
    addParameter("Sat",    this.satBird);
    addParameter("SatRng", this.satRange);
    addParameter("Bright", this.bright);
    addParameter("Black",  this.black);
    addParameter("Repeat", this.repeat);
    addParameter("Sym",    this.symmetry);
  }

  private void hatch(int i) {
    bx[i] = 0.25f + rng.nextFloat() * 0.5f;
    by[i] = 0.25f + rng.nextFloat() * 0.5f;
    float a = rng.nextFloat() * 6.2832f;
    bvx[i] = (float) Math.cos(a);
    bvy[i] = (float) Math.sin(a);
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    int target = (int) (count.getValuef() / 100f * MAX_B);
    if (target < 2) target = 2;
    while (liveCount < target) hatch(liveCount++);
    if (liveCount > target) liveCount = target;

    int   kWant  = clampi(Math.round(neighbors.getValuef()), 2, MAX_K);
    float sepA   = separation.getValuef() / 100f * 2.2f;
    float aliA   = alignment.getValuef() / 100f * 1.6f;
    float cohA   = cohesion.getValuef() / 100f * 1.1f;
    float space  = personal.getValuef() / 100f * 0.075f;
    float cruise = speed.getValuef() / 100f * 0.42f;
    float turnA  = turn.getValuef() / 100f * 9f;
    float wanA   = wander.getValuef() / 100f * 1.4f;
    float roostA = roost.getValuef() / 100f * 1.3f;
    float hawkA  = hawk.getValuef() / 100f;
    float hawkR  = hawkReach.getValuef() / 100f * 0.35f;
    float decay  = 1f - (1f - trail.getValuef() / 100f) * 0.9f;
    if (decay > 0.96f) decay = 0.96f;

    sGrain  = grain.getValuef() / 100f;
    sGain   = gain.getValuef() / 100f * 9f;
    sBr     = bright.getValuef();
    sBk     = black.getValuef() / 100f;
    sHue    = hue.getValuef();
    sHueRng = hueRange.getValuef();
    sHueMap = hueMap.getValuef() / 100f;
    sSat    = satBird.getValuef();
    sSatRng = satRange.getValuef();

    // predator drifts on its own wandering course
    if (hawkA > 0f) {
      hawkAng += (rng.nextFloat() - 0.5f) * 2.4f * dt;
      float hs = hawkSpeed.getValuef() / 100f * 0.5f;
      hawkX += (float) Math.cos(hawkAng) * hs * dt;
      hawkY += (float) Math.sin(hawkAng) * hs * dt;
      if (hawkX < 0.05f || hawkX > 0.95f) { hawkAng = 3.1416f - hawkAng; hawkX = clamp01(hawkX); }
      if (hawkY < 0.05f || hawkY > 0.95f) { hawkAng = -hawkAng; hawkY = clamp01(hawkY); }
    }

    // rebuild the spatial hash
    java.util.Arrays.fill(cellHead, -1);
    for (int i = 0; i < liveCount; i++) {
      int c = cellOf(bx[i], by[i]);
      nextBird[i] = cellHead[c];
      cellHead[c] = i;
    }

    for (int i = 0; i < fDens.length; i++) {
      fDens[i] *= decay;
      fSpeed[i] *= decay;
    }

    float peak = 1e-4f;

    for (int i = 0; i < liveCount; i++) {
      float x = bx[i], y = by[i];

      // Starlings track a fixed NUMBER of neighbours, not everything within a
      // radius. That topological rule is why a flock stays coherent however
      // much it stretches or compresses.
      int found = 0;
      int gx = (int) (x * GN), gy = (int) (y * GN);
      if (gx < 0) gx = 0; else if (gx >= GN) gx = GN - 1;
      if (gy < 0) gy = 0; else if (gy >= GN) gy = GN - 1;
      for (int oy = -1; oy <= 1; oy++) {
        int cy = gy + oy;
        if (cy < 0 || cy >= GN) continue;
        for (int ox = -1; ox <= 1; ox++) {
          int cx = gx + ox;
          if (cx < 0 || cx >= GN) continue;
          for (int j = cellHead[cy * GN + cx]; j >= 0; j = nextBird[j]) {
            if (j == i) continue;
            float dx = bx[j] - x, dy = by[j] - y;
            float d2 = dx * dx + dy * dy;
            if (found < kWant) {
              int p = found++;
              while (p > 0 && kDist[p - 1] > d2) { kDist[p] = kDist[p - 1]; kIdx[p] = kIdx[p - 1]; p--; }
              kDist[p] = d2; kIdx[p] = j;
            } else if (d2 < kDist[found - 1]) {
              int p = found - 1;
              while (p > 0 && kDist[p - 1] > d2) { kDist[p] = kDist[p - 1]; kIdx[p] = kIdx[p - 1]; p--; }
              kDist[p] = d2; kIdx[p] = j;
            }
          }
        }
      }

      float sx = 0f, sy = 0f;    // separation
      float ax = 0f, ay = 0f;    // alignment
      float cx2 = 0f, cy2 = 0f;  // cohesion
      for (int n = 0; n < found; n++) {
        int j = kIdx[n];
        float dx = bx[j] - x, dy = by[j] - y;
        float d = (float) Math.sqrt(kDist[n]);
        if (d < space && d > 1e-5f) {
          float push = (space - d) / space;
          sx -= dx / d * push;
          sy -= dy / d * push;
        }
        ax += bvx[j]; ay += bvy[j];
        cx2 += bx[j]; cy2 += by[j];
      }

      float fx = 0f, fy = 0f;
      if (found > 0) {
        float inv = 1f / found;
        ax *= inv; ay *= inv;
        cx2 = cx2 * inv - x; cy2 = cy2 * inv - y;
        fx += sx * sepA + ax * aliA + cx2 * cohA;
        fy += sy * sepA + ay * aliA + cy2 * cohA;
      }

      // keep the flock wheeling in frame rather than drifting off it
      float rx = 0.5f - x, ry = 0.5f - y;
      float rd = (float) Math.sqrt(rx * rx + ry * ry);
      if (rd > 0.30f) {
        float pull = (rd - 0.30f) / 0.20f;
        if (pull > 1f) pull = 1f;
        fx += rx / Math.max(rd, 1e-5f) * pull * roostA;
        fy += ry / Math.max(rd, 1e-5f) * pull * roostA;
      }

      if (wanA > 0f) {
        fx += (rng.nextFloat() - 0.5f) * wanA;
        fy += (rng.nextFloat() - 0.5f) * wanA;
      }

      if (hawkA > 0f) {
        float dx = x - hawkX, dy = y - hawkY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < hawkR && d > 1e-5f) {
          float panic = (hawkR - d) / hawkR;
          panic *= panic;
          fx += dx / d * panic * hawkA * 14f;
          fy += dy / d * panic * hawkA * 14f;
        }
      }

      // steer rather than teleport - a bird can only turn so fast
      float vx = bvx[i] + fx * turnA * dt;
      float vy = bvy[i] + fy * turnA * dt;
      float m = (float) Math.sqrt(vx * vx + vy * vy);
      if (m > 1e-5f) { vx /= m; vy /= m; }
      bvx[i] = vx; bvy[i] = vy;

      x += vx * cruise * dt;
      y += vy * cruise * dt;
      if (x < 0.01f) { x = 0.01f; bvx[i] = Math.abs(vx); }
      if (x > 0.99f) { x = 0.99f; bvx[i] = -Math.abs(vx); }
      if (y < 0.01f) { y = 0.01f; bvy[i] = Math.abs(vy); }
      if (y > 0.99f) { y = 0.99f; bvy[i] = -Math.abs(vy); }
      bx[i] = x; by[i] = y;

      float sp = m;
      float px = x * (FW - 1), py = y * (FH - 1);
      int x0 = (int) px, y0 = (int) py;
      if (x0 < 0 || y0 < 0 || x0 >= FW - 1 || y0 >= FH - 1) continue;
      float tx = px - x0, ty = py - y0;
      int c00 = y0 * FW + x0, c10 = c00 + 1, c01 = c00 + FW, c11 = c01 + 1;
      float w00 = (1f - tx) * (1f - ty), w10 = tx * (1f - ty);
      float w01 = (1f - tx) * ty,        w11 = tx * ty;
      fDens[c00] += w00; fDens[c10] += w10; fDens[c01] += w01; fDens[c11] += w11;
      fSpeed[c00] += w00 * sp; fSpeed[c10] += w10 * sp;
      fSpeed[c01] += w01 * sp; fSpeed[c11] += w11 * sp;
    }

    for (int i = 0; i < fDens.length; i++) {
      if (fDens[i] > peak) peak = fDens[i];
    }
    dMax = peak;

    renderCube();
    renderCylinder();
    copyExterior();
  }

  private int cellOf(float x, float y) {
    int gx = (int) (x * GN), gy = (int) (y * GN);
    if (gx < 0) gx = 0; else if (gx >= GN) gx = GN - 1;
    if (gy < 0) gy = 0; else if (gy >= GN) gy = GN - 1;
    return gy * GN + gx;
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
    float gx = u * (FW - 1), gy = v * (FH - 1);
    int x0 = (int) gx, y0 = (int) gy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= FW) x1 = FW - 1;
    if (y1 >= FH) y1 = FH - 1;
    float tx = gx - x0, ty = gy - y0;
    int c00 = y0 * FW + x0, c10 = y0 * FW + x1;
    int c01 = y1 * FW + x0, c11 = y1 * FW + x1;

    float d = bilerp(fDens[c00], fDens[c10], fDens[c01], fDens[c11], tx, ty);
    if (d < 1e-4f) return LXColor.BLACK;
    float sp = bilerp(fSpeed[c00], fSpeed[c10], fSpeed[c01], fSpeed[c11], tx, ty) / d;

    float amt = d * sGain;
    float bri = 1f - (float) Math.exp(-amt);
    if (sGrain > 0f) {
      float hard = bri * bri * (3f - 2f * bri);
      bri = bri + (hard - bri) * sGrain;
    }
    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    // density relative to the densest part of the flock, so colour tracks the
    // banding that sweeps through a murmuration as birds bank
    float dens = clamp01(d / dMax);
    float fast = clamp01(sp);
    float driver = dens + (fast - dens) * sHueMap;

    float h = sHue + sHueRng * driver;
    h = ((h % 360f) + 360f) % 360f;
    float s = sSat + sSatRng * driver;

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
