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
@LXComponent.Name("River Delta")
public class RiverDelta extends ApotheneumPattern {

  private static final int FW = 128, FH = 128;   // flow buffer
  private static final int NZ = 64;              // noise texture edge
  private static final int MAX_P = 6000;         // particle capacity
  private static final int GW = 96, GH = 96;     // guide field
  private static final int MAX_N = 3000;         // guide network segments

  // Tileable smoothed value noise, built once
  private static final float[] NOISE = new float[NZ * NZ];
  static {
    Random r = new Random(4472051L);
    float[] raw = new float[NZ * NZ];
    for (int i = 0; i < raw.length; i++) raw[i] = r.nextFloat();
    for (int pass = 0; pass < 2; pass++) {
      float[] tmp = new float[NZ * NZ];
      for (int y = 0; y < NZ; y++) {
        for (int x = 0; x < NZ; x++) {
          float sum = 0f;
          for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
              sum += raw[((y + dy + NZ) % NZ) * NZ + ((x + dx + NZ) % NZ)];
            }
          }
          tmp[y * NZ + x] = sum / 9f;
        }
      }
      System.arraycopy(tmp, 0, raw, 0, raw.length);
    }
    System.arraycopy(raw, 0, NOISE, 0, raw.length);
  }

  private static float nz(float x, float y) {
    x *= NZ; y *= NZ;
    int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
    float tx = x - x0, ty = y - y0;
    int xa = ((x0 % NZ) + NZ) % NZ, xb = (xa + 1) % NZ;
    int ya = ((y0 % NZ) + NZ) % NZ, yb = (ya + 1) % NZ;
    float a = NOISE[ya * NZ + xa], b = NOISE[ya * NZ + xb];
    float c = NOISE[yb * NZ + xa], d = NOISE[yb * NZ + xb];
    tx = tx * tx * (3f - 2f * tx);
    ty = ty * ty * (3f - 2f * ty);
    float ab = a + (b - a) * tx, cd = c + (d - c) * tx;
    return ab + (cd - ab) * ty;
  }

  // Current

  private final CompoundParameter flow = new CompoundParameter("Flow", 45, 2, 100)
    .setDescription("Downstream current speed");
  private final CompoundParameter turb = new CompoundParameter("Turb", 45, 0, 100)
    .setDescription("Turbulence tearing the current apart");
  private final CompoundParameter eddy = new CompoundParameter("Eddy", 40, 5, 100)
    .setDescription("Eddy size - low is fine chop, high is broad swirls");
  private final CompoundParameter churn = new CompoundParameter("Churn", 30, 0, 100)
    .setDescription("How fast the turbulence itself evolves");
  private final CompoundParameter shear = new CompoundParameter("Shear", 35, 0, 100)
    .setDescription("Turbulence builds as the water travels downstream");
  private final CompoundParameter confine = new CompoundParameter("Confine", 0, 0, 100)
    .setDescription("How tightly the current is held to a channel network");
  private final CompoundParameter web = new CompoundParameter("Web", 6, 3, 8)
    .setDescription("Channel network generations");
  private final CompoundParameter seed = new CompoundParameter("Seed", 11, 0, 40)
    .setDescription("Which channel network");

  // Delta form

  private final CompoundParameter spread = new CompoundParameter("Spread", 50, 0, 100)
    .setDescription("How far the flow fans out from the apex");
  private final CompoundParameter mouth = new CompoundParameter("Mouth", 30, 0, 100)
    .setDescription("Width of the source at the apex");
  private final CompoundParameter angle = new CompoundParameter("Angle", 0, 0, 360)
    .setDescription("Direction the current flows - 0 is down, 90 is left to right");
  private final CompoundParameter apexX = new CompoundParameter("ApexX", 50, 0, 100)
    .setDescription("Source position across the face");
  private final CompoundParameter apexY = new CompoundParameter("ApexY", 4, 0, 100)
    .setDescription("Source position along the face");

  // Matter

  private final CompoundParameter density = new CompoundParameter("Dens", 55, 5, 100)
    .setDescription("How much matter is in the water");
  private final CompoundParameter trail = new CompoundParameter("Trail", 60, 0, 100)
    .setDescription("How long streaks persist");
  private final CompoundParameter grain = new CompoundParameter("Grain", 40, 0, 100)
    .setDescription("Particulate hardness - low is misty, high is granular");
  private final CompoundParameter gain = new CompoundParameter("Gain", 55, 5, 100)
    .setDescription("Density to brightness response");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 205, 0, 360)
    .setDescription("Base hue at the source");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", 40, -180, 180)
    .setDescription("How far hue travels from source to edge - negative reverses");
  private final CompoundParameter hueMap = new CompoundParameter("HueMap", 0, 0, 100)
    .setDescription("What shifts the hue - 0 is distance travelled, 100 is turbulence");
  private final CompoundParameter satWater = new CompoundParameter("Sat", 55, 0, 100)
    .setDescription("Saturation at the source");
  private final CompoundParameter satRange = new CompoundParameter("SatRng", -25, -100, 100)
    .setDescription("How saturation changes along the same axis as hue");
  private final CompoundParameter spark = new CompoundParameter("Spark", 35, 0, 100)
    .setDescription("Pinpricks of white spray where the flow thins out");
  private final CompoundParameter sparkEdge = new CompoundParameter("SpkEdg", 35, 5, 100)
    .setDescription("How far in from the edge the spray reaches");
  private final CompoundParameter bright = new CompoundParameter("Bright", 90, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");


  // Particles

  private final float[] px = new float[MAX_P];
  private final float[] py = new float[MAX_P];
  private final float[] pa = new float[MAX_P];   // ray angle away from the apex
  private final float[] pv = new float[MAX_P];   // last speed, for coloring
  private int liveCount = 0;

  // An invisible dendritic network. It is never drawn - it only steers the
  // particles, so the pattern is carried entirely by moving water.
  private final float[] nX0 = new float[MAX_N];
  private final float[] nY0 = new float[MAX_N];
  private final float[] nX1 = new float[MAX_N];
  private final float[] nY1 = new float[MAX_N];
  private int nCount = 0;

  private final float[] gDirX = new float[GW * GH];
  private final float[] gDirY = new float[GW * GH];
  private final float[] gToX  = new float[GW * GH];
  private final float[] gToY  = new float[GW * GH];
  private final float[] gDist = new float[GW * GH];
  private boolean guideDirty = true;
  private double sinceGuide = 1e9;
  private float prevWeb = -1f, prevSeedG = -1f, prevSpreadG = -1f;

  private final float[] fDens = new float[FW * FH];
  private final float[] fSpeed = new float[FW * FH];

  private final Random rng = new Random(24601L);
  private float time = 0f;

  // Per-frame scratch
  private float sGain, sBr, sBk, sGrain;
  private float sHue, sHueRng, sHueMap, sSat, sSatRng, sSpark, sSpkEdge;
  private float sDMax = 1f;
  private float sApexX, sApexY;

  public RiverDelta(LX lx) {
    super(lx);
    addParameter("Flow",   this.flow);
    addParameter("Turb",   this.turb);
    addParameter("Eddy",   this.eddy);
    addParameter("Churn",  this.churn);
    addParameter("Shear",  this.shear);
    addParameter("Confine", this.confine);
    addParameter("Web",    this.web);
    addParameter("Seed",   this.seed);
    addParameter("Spread", this.spread);
    addParameter("Mouth",  this.mouth);
    addParameter("Angle",  this.angle);
    addParameter("ApexX",  this.apexX);
    addParameter("ApexY",  this.apexY);
    addParameter("Dens",   this.density);
    addParameter("Trail",  this.trail);
    addParameter("Grain",  this.grain);
    addParameter("Gain",   this.gain);
    addParameter("Hue",    this.hue);
    addParameter("HueRng", this.hueRange);
    addParameter("HueMap", this.hueMap);
    addParameter("Sat",    this.satWater);
    addParameter("SatRng", this.satRange);
    addParameter("Spark",  this.spark);
    addParameter("SpkEdg", this.sparkEdge);
    addParameter("Bright", this.bright);
    addParameter("Black",  this.black);
    addParameter("Repeat", this.repeat);
    addParameter("Sym",    this.symmetry);
  }

  // Emit a particle at the source

  private void spawn(int i, float ax, float ay, float flowAng, float mouthW, float fanRad) {
    // spread the mouth across the flow, not across the screen
    float off = (rng.nextFloat() - 0.5f) * mouthW;
    float cs = (float) Math.cos(flowAng), sn = (float) Math.sin(flowAng);
    px[i] = ax + cs * off;
    py[i] = ay + sn * off;
    // each parcel leaves the mouth on its own ray, so the flow fans out
    float r = rng.nextFloat() + rng.nextFloat() - 1f;   // clusters toward center
    pa[i] = r * fanRad;
    pv[i] = 0f;
  }

  // Curl of a scalar noise field - divergence free, so it swirls without
  // creating sources or sinks. This is what makes it read as fluid.
  private float psi(float x, float y, float sc, float t) {
    return nz(x * sc + t * 0.31f, y * sc - t * 0.17f);
  }

  private void addNet(float x0, float y0, float x1, float y1) {
    if (nCount >= MAX_N) return;
    int i = nCount++;
    nX0[i] = x0; nY0[i] = y0; nX1[i] = x1; nY1[i] = y1;
  }

  private void carveNetwork(float x, float y, float ang, float len, int d, int maxD,
                            float fanRad, Random r) {
    if (d > maxD || len < 0.012f || nCount >= MAX_N) return;
    int sub = 3;
    float sl = len / sub, px = x, py = y, pa = ang;
    float meander = (r.nextFloat() - 0.5f) * 0.55f;
    for (int i = 0; i < sub; i++) {
      pa += meander * 0.55f;
      float nx = px + (float) Math.sin(pa) * sl;
      float ny = py - (float) Math.cos(pa) * sl;
      addNet(px, py, nx, ny);
      px = nx; py = ny;
    }
    float cl = len * 0.74f;
    float half = fanRad * (0.55f + 0.45f * r.nextFloat());
    carveNetwork(px, py, pa - half, cl,         d + 1, maxD, fanRad, r);
    carveNetwork(px, py, pa + half, cl * 0.93f, d + 1, maxD, fanRad, r);
    if (d >= 1 && r.nextFloat() < 0.35f) {
      carveNetwork(px, py, pa + (r.nextFloat() - 0.5f) * half * 1.4f, cl * 0.80f,
                   d + 1, maxD, fanRad, r);
    }
  }

  private void buildGuide(int seedVal, float fanRad) {
    nCount = 0;
    int maxD = clampi(Math.round(web.getValuef()), 3, 8);
    carveNetwork(0.5f, 0f, (float) Math.PI, 0.20f, 0, maxD, fanRad,
                 new Random(seedVal * 3571L + 7L));

    for (int gy = 0; gy < GH; gy++) {
      float py = (gy + 0.5f) / GH;
      for (int gx = 0; gx < GW; gx++) {
        float px = (gx + 0.5f) / GW;
        float best = Float.MAX_VALUE;
        float bdx = 0f, bdy = 1f, btx = 0f, bty = 0f;
        for (int i = 0; i < nCount; i++) {
          float abx = nX1[i] - nX0[i], aby = nY1[i] - nY0[i];
          float l2 = abx * abx + aby * aby;
          float t = (l2 < 1e-9f) ? 0f : ((px - nX0[i]) * abx + (py - nY0[i]) * aby) / l2;
          if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
          float cx = nX0[i] + t * abx, cy = nY0[i] + t * aby;
          float dx = px - cx, dy = py - cy;
          float dd = dx * dx + dy * dy;
          if (dd < best) {
            best = dd;
            float m = (float) Math.sqrt(l2);
            if (m > 1e-6f) { bdx = abx / m; bdy = aby / m; }
            float md = (float) Math.sqrt(dd);
            if (md > 1e-6f) { btx = -dx / md; bty = -dy / md; } else { btx = 0f; bty = 0f; }
          }
        }
        int c = gy * GW + gx;
        gDirX[c] = bdx; gDirY[c] = bdy;
        gToX[c] = btx;  gToY[c] = bty;
        gDist[c] = (float) Math.sqrt(best);
      }
    }
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    float flowAng = angle.getValuef() * (float) Math.PI / 180f;
    sApexX        = apexX.getValuef() / 100f;
    sApexY        = apexY.getValuef() / 100f;
    float speed   = flow.getValuef() / 100f * 0.55f;
    float turbA   = turb.getValuef() / 100f * 0.85f;
    float sc      = 1.2f + (1f - eddy.getValuef() / 100f) * 7f;
    float chn     = churn.getValuef() / 100f * 0.9f;
    float shr     = shear.getValuef() / 100f;
    float fanRad  = spread.getValuef() / 100f * 1.15f;   // half-angle of the fan
    float mouthW  = mouth.getValuef() / 100f * 0.5f;
    int   target  = (int) (density.getValuef() / 100f * MAX_P);
    float decay   = 1f - (1f - trail.getValuef() / 100f) * 0.85f;
    if (decay > 0.97f) decay = 0.97f;

    float conf = confine.getValuef() / 100f;
    float seedVal = seed.getValuef();
    sinceGuide += deltaMs;
    if (web.getValuef() != prevWeb || seedVal != prevSeedG
        || spread.getValuef() != prevSpreadG) {
      guideDirty = true;
    }
    if (conf > 0.001f && guideDirty && sinceGuide >= 80.0) {
      buildGuide((int) seedVal, fanRad);
      prevWeb = web.getValuef();
      prevSeedG = seedVal;
      prevSpreadG = spread.getValuef();
      guideDirty = false;
      sinceGuide = 0.0;
    }

    sGrain   = grain.getValuef() / 100f;
    sGain    = gain.getValuef() / 100f * 9f;
    sBr      = bright.getValuef();
    sBk      = black.getValuef() / 100f;
    sHue     = hue.getValuef();
    sHueRng  = hueRange.getValuef();
    sHueMap  = hueMap.getValuef() / 100f;
    sSat     = satWater.getValuef();
    sSatRng  = satRange.getValuef();
    sSpark   = spark.getValuef() / 100f;
    sSpkEdge = sparkEdge.getValuef() / 100f;

    // grow or shrink the population toward Dens
    if (liveCount < target) {
      int add = Math.min(target - liveCount, 260);
      for (int i = 0; i < add; i++) spawn(liveCount++, sApexX, sApexY, flowAng, mouthW, fanRad);
    } else if (liveCount > target) {
      liveCount = target;
    }

    // fade the buffers - this is what leaves streaks behind moving matter
    float dPeak = 1e-4f;
    for (int i = 0; i < fDens.length; i++) {
      fDens[i] *= decay;
      fSpeed[i] *= decay;
      if (fDens[i] > dPeak) dPeak = fDens[i];
    }
    sDMax = dPeak;

    final float eps = 0.012f;
    for (int i = 0; i < liveCount; i++) {
      float x = px[i], y = py[i];

      // how far this parcel has travelled from the source
      float ddx = x - sApexX, ddy = y - sApexY;
      float down = clamp01((float) Math.sqrt(ddx * ddx + ddy * ddy) / 0.9f);

      // base current: each parcel runs along its own ray, offset from the
      // overall flow direction
      float th = flowAng + pa[i];
      float bx = (float) Math.sin(th);
      float by = (float) Math.cos(th);

      // turbulence builds downstream, so flow starts laminar and tears apart
      float tA = turbA * (1f - shr + shr * down * down);
      if (tA > 0.001f) {
        float tt = time * chn;
        float p1 = psi(x, y + eps, sc, tt);
        float p2 = psi(x, y - eps, sc, tt);
        float p3 = psi(x + eps, y, sc, tt);
        float p4 = psi(x - eps, y, sc, tt);
        bx += (p1 - p2) / (2f * eps) * tA * 0.09f;
        by -= (p3 - p4) / (2f * eps) * tA * 0.09f;
      }

      // steer onto the network: follow the channel when inside it, drift toward
      // the nearest one when outside. Nothing here is drawn.
      if (conf > 0.001f) {
        int qx = (int) (x * (GW - 1)), qy = (int) (y * (GH - 1));
        if (qx < 0) qx = 0; else if (qx >= GW) qx = GW - 1;
        if (qy < 0) qy = 0; else if (qy >= GH) qy = GH - 1;
        int c = qy * GW + qx;
        float near = clamp01(1f - gDist[c] / 0.055f);
        float along = conf * near;
        float toward = conf * (1f - near) * 0.9f;
        bx = bx * (1f - along) + gDirX[c] * along + gToX[c] * toward;
        by = by * (1f - along) + gDirY[c] * along + gToY[c] * toward;
      }

      float mag = (float) Math.sqrt(bx * bx + by * by);
      if (mag > 1e-5f) { bx /= mag; by /= mag; }

      float step = speed * dt;
      x += bx * step;
      y += by * step;
      pv[i] = mag;

      // leaves the field, returns at the source - the current never stops
      if (x < -0.02f || x > 1.02f || y < -0.02f || y > 1.02f) {
        spawn(i, sApexX, sApexY, flowAng, mouthW, fanRad);
        continue;
      }
      px[i] = x; py[i] = y;

      // splat across four cells so motion stays smooth
      float gx = x * (FW - 1), gy = y * (FH - 1);
      int x0 = (int) gx, y0 = (int) gy;
      if (x0 < 0 || y0 < 0 || x0 >= FW - 1 || y0 >= FH - 1) continue;
      float tx = gx - x0, ty = gy - y0;
      int c00 = y0 * FW + x0, c10 = c00 + 1, c01 = c00 + FW, c11 = c01 + 1;
      float w00 = (1f - tx) * (1f - ty), w10 = tx * (1f - ty);
      float w01 = (1f - tx) * ty,        w11 = tx * ty;
      fDens[c00] += w00; fDens[c10] += w10; fDens[c01] += w01; fDens[c11] += w11;
      float sv = pv[i];
      fSpeed[c00] += w00 * sv; fSpeed[c10] += w10 * sv;
      fSpeed[c01] += w01 * sv; fSpeed[c11] += w11 * sv;
    }

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

    // Grain hardens the response so matter reads as particulate, not mist
    float amt = d * sGain;
    float bri = 1f - (float) Math.exp(-amt);
    if (sGrain > 0f) {
      float hard = bri * bri * (3f - 2f * bri);
      bri = bri + (hard - bri) * sGrain;
    }
    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    // Hue tracks something structural - how far the water has travelled from
    // the source - with turbulence available as an alternative driver. Speed
    // alone barely varies, which is why it read as noise.
    float ddx = u - sApexX, ddy = v - sApexY;
    float dNorm = clamp01((float) Math.sqrt(ddx * ddx + ddy * ddy) / 0.9f);
    float turbN = clamp01((sp - 1f) / 1.1f);
    float driver = dNorm + (turbN - dNorm) * sHueMap;

    float hue = sHue + sHueRng * driver;
    hue = ((hue % 360f) + 360f) % 360f;
    float sat = sSat + sSatRng * driver;

    // Spray: the flow is thinnest at its edges, so glints of white land there.
    // A hash gives single-pixel twinkle - the smooth noise field would blob.
    if (sSpark > 0f) {
      // Thin water measured against the current peak density, so this holds up
      // whatever Gain, Dens and Trail are set to.
      float thin = clamp01(1f - (d / sDMax) / sSpkEdge);
      if (thin > 0f) {
        int cell = ((int) (u * 509f)) * 977 + ((int) (v * 509f)) * 131;
        int tick = (int) (time * (3f + sSpark * 20f));
        float h = hash1(cell + tick * 7919);
        float need = 1f - sSpark * 0.10f;
        if (h > need) {
          float w = (h - need) / Math.max(1e-4f, 1f - need) * thin;
          // pull the pixel toward white - always brighter than it was, never less
          bri = clamp01(bri + w * (1f - bri));
          sat = sat * (1f - w * 0.95f);
        }
      }
    }

    return LXColor.hsb(hue, clamp01(sat / 100f) * 100f, clamp01(bri) * 100f);
  }

  private static float bilerp(float c00, float c10, float c01, float c11, float tx, float ty) {
    float a = c00 + (c10 - c00) * tx;
    float b = c01 + (c11 - c01) * tx;
    return a + (b - a) * ty;
  }

  private static float hash1(int a) {
    a = (a ^ 61) ^ (a >>> 16);
    a = a + (a << 3);
    a = a ^ (a >>> 4);
    a *= 0x27d4eb2d;
    a = a ^ (a >>> 15);
    return (a & 0xFFFFFF) / (float) 0xFFFFFF;
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
