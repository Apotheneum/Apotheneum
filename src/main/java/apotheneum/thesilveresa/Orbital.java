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
import heronarts.lx.parameter.TriggerParameter;

import java.util.Random;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Orbital")
public class Orbital extends ApotheneumPattern {

  private static final int FW = 128, FH = 128;
  private static final int MAX_P = 4000;
  private static final float TWO_PI = 6.2831855f;

  // Orbits

  private final TriggerParameter reseed = new TriggerParameter("Reset", this::resetBodies)
    .setDescription("Put every body back where it started");

  private final CompoundParameter bodies = new CompoundParameter("Bodies", 60, 5, 100)
    .setDescription("How much matter is in orbit");
  private final CompoundParameter inner = new CompoundParameter("Inner", 12, 0, 60)
    .setDescription("Inner edge of the disk");
  private final CompoundParameter outer = new CompoundParameter("Outer", 42, 5, 90)
    .setDescription("Outer edge of the disk");
  private final CompoundParameter gaps = new CompoundParameter("Gaps", 0, 0, 100)
    .setDescription("Resonance gaps carved in the ring");
  private final CompoundParameter gapCount = new CompoundParameter("GapN", 3, 1, 8)
    .setDescription("How many gaps");
  private final CompoundParameter thickness = new CompoundParameter("Thick", 25, 1, 100)
    .setDescription("Vertical thickness of the disk");
  private final CompoundParameter tilt = new CompoundParameter("Tilt", 55, 0, 100)
    .setDescription("Viewing angle - 0 is edge on, 100 is face on");

  // Motion

  private final CompoundParameter rate = new CompoundParameter("Rate", 40, 0, 100)
    .setDescription("Orbital speed");
  private final CompoundParameter kepler = new CompoundParameter("Kepler", 75, 0, 100)
    .setDescription("How much faster the inner orbits run - real gravity is 100");
  private final CompoundParameter eccent = new CompoundParameter("Ecc", 0, 0, 90)
    .setDescription("Orbital eccentricity");
  private final CompoundParameter precess = new CompoundParameter("Prec", 15, 0, 100)
    .setDescription("How fast the ellipses rotate");

  // Accretion

  private final CompoundParameter infall = new CompoundParameter("Infall", 0, 0, 100)
    .setDescription("Matter spiralling inward onto the centre");
  private final CompoundParameter heat = new CompoundParameter("Heat", 45, 0, 100)
    .setDescription("How much the inner disk brightens");
  private final CompoundParameter core = new CompoundParameter("Core", 30, 0, 100)
    .setDescription("Brightness of the central body");

  // Matter

  private final CompoundParameter trail = new CompoundParameter("Trail", 62, 0, 100)
    .setDescription("Persistence of the orbital streaks");
  private final CompoundParameter grain = new CompoundParameter("Grain", 40, 0, 100)
    .setDescription("Particulate hardness");
  private final CompoundParameter gain = new CompoundParameter("Gain", 50, 5, 100)
    .setDescription("Density to brightness response");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 32, 0, 360)
    .setDescription("Hue at the outer edge");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", -35, -180, 180)
    .setDescription("Hue shift from outer edge inward");
  private final CompoundParameter satDisk = new CompoundParameter("Sat", 55, 0, 100)
    .setDescription("Saturation at the outer edge");
  private final CompoundParameter satRange = new CompoundParameter("SatRng", -40, -100, 100)
    .setDescription("Saturation shift inward");
  private final CompoundParameter bright = new CompoundParameter("Bright", 88, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");

  // Particles - each keeps its orbital elements, not a position

  // Normalised radius; the real orbit is derived from Inner and Outer every
  // frame. Storing an absolute radius meant those knobs only ever reached
  // bodies that did not exist yet.
  private final float[] pN = new float[MAX_P];
  private final float[] pFall = new float[MAX_P];
  private final float[] pPh = new float[MAX_P];   // phase
  private final float[] pZ = new float[MAX_P];    // height above the plane
  private final float[] pW = new float[MAX_P];    // argument of periapsis
  private int liveCount = 0;

  private final float[] fDens = new float[FW * FH];
  private final float[] fRad = new float[FW * FH];

  private final Random rng = new Random(11235L);
  private float time = 0f;

  private float sGain, sBr, sBk, sGrain, sHue, sHueRng, sSat, sSatRng;

  public Orbital(LX lx) {
    super(lx);
    addParameter("Reset",  this.reseed);
    addParameter("Bodies", this.bodies);
    addParameter("Inner",  this.inner);
    addParameter("Outer",  this.outer);
    addParameter("Gaps",   this.gaps);
    addParameter("GapN",   this.gapCount);
    addParameter("Thick",  this.thickness);
    addParameter("Tilt",   this.tilt);
    addParameter("Rate",   this.rate);
    addParameter("Kepler", this.kepler);
    addParameter("Ecc",    this.eccent);
    addParameter("Prec",   this.precess);
    addParameter("Infall", this.infall);
    addParameter("Heat",   this.heat);
    addParameter("Core",   this.core);
    addParameter("Trail",  this.trail);
    addParameter("Grain",  this.grain);
    addParameter("Gain",   this.gain);
    addParameter("Hue",    this.hue);
    addParameter("HueRng", this.hueRange);
    addParameter("Sat",    this.satDisk);
    addParameter("SatRng", this.satRange);
    addParameter("Bright", this.bright);
    addParameter("Black",  this.black);
    addParameter("Repeat", this.repeat);
    addParameter("Sym",    this.symmetry);
    resetBodies();
  }

  // Fixed seed, so Reset always gives exactly the same arrangement.
  private void resetBodies() {
    Random r = new Random(20260809L);
    for (int i = 0; i < MAX_P; i++) {
      pN[i] = r.nextFloat();
      pPh[i] = r.nextFloat() * TWO_PI;
      pZ[i] = r.nextFloat() - 0.5f;
      pW[i] = r.nextFloat() * TWO_PI;
      pFall[i] = 0f;
    }
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    float lo = inner.getValuef() / 100f * 0.5f;
    float hi = outer.getValuef() / 100f * 0.5f;
    if (hi < lo + 0.01f) hi = lo + 0.01f;

    int target = (int) (bodies.getValuef() / 100f * MAX_P);
    if (target < 1) target = 1;
    liveCount = target;

    float spin   = rate.getValuef() / 100f * 1.7f;
    float kep    = kepler.getValuef() / 100f;
    float ecc    = eccent.getValuef() / 100f * 0.85f;
    float prec   = precess.getValuef() / 100f * 0.5f;
    float thick  = thickness.getValuef() / 100f * 0.30f;
    float tiltA  = tilt.getValuef() / 100f;
    float gapA   = gaps.getValuef() / 100f;
    int   gapN   = clampi(Math.round(gapCount.getValuef()), 1, 8);
    float fall   = infall.getValuef() / 100f;
    float heatA  = heat.getValuef() / 100f;
    float coreA  = core.getValuef() / 100f;
    float decay  = 1f - (1f - trail.getValuef() / 100f) * 0.9f;
    if (decay > 0.965f) decay = 0.965f;

    sGrain  = grain.getValuef() / 100f;
    sGain   = gain.getValuef() / 100f * 8f;
    sBr     = bright.getValuef();
    sBk     = black.getValuef() / 100f;
    sHue    = hue.getValuef();
    sHueRng = hueRange.getValuef();
    sSat    = satDisk.getValuef();
    sSatRng = satRange.getValuef();

    for (int i = 0; i < fDens.length; i++) {
      fDens[i] *= decay;
      fRad[i] *= decay;
    }

    for (int i = 0; i < liveCount; i++) {
      float aN = pN[i] - pFall[i];
      aN = aN - (float) Math.floor(aN);
      float a = lo + aN * Math.max(1e-4f, hi - lo);

      // Kepler's third law: inner orbits run faster. At Kepler=0 the disk turns
      // as a rigid body, which is what a solid ring looks like; at 100 it
      // shears, which is what gravity actually does.
      float rNorm = (a - lo) / Math.max(1e-4f, hi - lo);
      float w = spin * (1f - kep + kep * (float) Math.pow(Math.max(a, 0.02f) / hi, -1.5f) * 0.35f);
      // Wrapped so the angle never grows large enough to lose float
      // precision on a long unbroken run. cos and sin are unchanged by a
      // full turn, so this cannot alter a single pixel.
      pPh[i] += w * dt;
      if (pPh[i] > 6.2831855f) pPh[i] -= 6.2831855f;
      else if (pPh[i] < 0f) pPh[i] += 6.2831855f;
      pW[i] += prec * dt;
      if (pW[i] > 6.2831855f) pW[i] -= 6.2831855f;
      else if (pW[i] < 0f) pW[i] += 6.2831855f;

      if (fall > 0f) {
        pFall[i] += fall * dt * 0.08f;
        if (pFall[i] > 1f) pFall[i] -= 1f;
      } else if (pFall[i] != 0f) {
        pFall[i] -= pFall[i] * Math.min(1f, dt * 0.8f);
        if (Math.abs(pFall[i]) < 1e-4f) pFall[i] = 0f;
      }

      // resonance gaps - matter is cleared at particular radii
      float keep = 1f;

      if (gapA > 0f) {
        float g = (float) Math.sin(rNorm * gapN * 3.1416f);
        keep = 1f - gapA * (1f - Math.min(1f, Math.abs(g) * 3.2f));
        if (keep < 0f) keep = 0f;
      }
      if (keep < 0.02f) continue;

      float ph = pPh[i];

      float r = a * (1f - ecc * ecc) / (1f + ecc * (float) Math.cos(ph - pW[i]));

      float x = 0.5f + (float) Math.cos(ph) * r;
      // tilt squashes the circle into the ellipse we see from an angle
      float y = 0.5f + (float) Math.sin(ph) * r * (1f - tiltA * 0.92f)
                + pZ[i] * thick * (1f - tiltA * 0.5f);

      if (x < 0f || x > 1f || y < 0f || y > 1f) continue;

      float gx = x * (FW - 1), gy = y * (FH - 1);
      int x0 = (int) gx, y0 = (int) gy;
      if (x0 < 0 || y0 < 0 || x0 >= FW - 1 || y0 >= FH - 1) continue;
      float tx = gx - x0, ty = gy - y0;
      int c00 = y0 * FW + x0, c10 = c00 + 1, c01 = c00 + FW, c11 = c01 + 1;
      // inner orbits are hotter, which is the accretion disk signature
      float wgt = keep * (1f + heatA * 3.5f * (1f - rNorm));
      float w00 = (1f - tx) * (1f - ty) * wgt, w10 = tx * (1f - ty) * wgt;
      float w01 = (1f - tx) * ty * wgt,        w11 = tx * ty * wgt;
      fDens[c00] += w00; fDens[c10] += w10; fDens[c01] += w01; fDens[c11] += w11;
      fRad[c00] += w00 * rNorm; fRad[c10] += w10 * rNorm;
      fRad[c01] += w01 * rNorm; fRad[c11] += w11 * rNorm;
    }

    // central body
    if (coreA > 0f) {
      float cr = 0.012f + coreA * 0.045f;
      int x0 = (int) ((0.5f - cr) * FW), x1 = (int) ((0.5f + cr) * FW) + 1;
      int y0 = (int) ((0.5f - cr) * FH), y1 = (int) ((0.5f + cr) * FH) + 1;
      if (x0 < 0) x0 = 0; if (y0 < 0) y0 = 0;
      if (x1 > FW) x1 = FW; if (y1 > FH) y1 = FH;
      for (int gy = y0; gy < y1; gy++) {
        float dy = (gy + 0.5f) / FH - 0.5f;
        for (int gx = x0; gx < x1; gx++) {
          float dx = (gx + 0.5f) / FW - 0.5f;
          float d = (float) Math.sqrt(dx * dx + dy * dy);
          if (d > cr) continue;
          float v = (1f - d / cr);
          int c = gy * FW + gx;
          float add = v * v * coreA * 22f;
          if (add > fDens[c]) { fDens[c] = add; fRad[c] = 0f; }
        }
      }
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
    for (int f = 0; f < ext.length; f++) renderCubeFace(ext[f], sym && ((f & 1) == 1));
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
    float rad = bilerp(fRad[c00], fRad[c10], fRad[c01], fRad[c11], tx, ty) / d;

    float bri = 1f - (float) Math.exp(-d * sGain);
    if (sGrain > 0f) {
      float hard = bri * bri * (3f - 2f * bri);
      bri = bri + (hard - bri) * sGrain;
    }
    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    float inward = clamp01(1f - rad);
    float h = sHue + sHueRng * inward;
    h = ((h % 360f) + 360f) % 360f;
    float s = sSat + sSatRng * inward;

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
