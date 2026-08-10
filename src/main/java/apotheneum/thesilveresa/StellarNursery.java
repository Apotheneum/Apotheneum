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
@LXComponent.Name("Stellar Nursery")
public class StellarNursery extends ApotheneumPattern {

  private static final int NZ = 96;
  private static final int MAX_STAR = 40;

  private static final float[] NOISE = new float[NZ * NZ];
  static {
    Random r = new Random(1054L);
    float[] raw = new float[NZ * NZ];
    for (int i = 0; i < raw.length; i++) raw[i] = r.nextFloat();
    for (int p = 0; p < 2; p++) {
      float[] t = new float[NZ * NZ];
      for (int y = 0; y < NZ; y++)
        for (int x = 0; x < NZ; x++) {
          float s = 0f;
          for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
              s += raw[((y + dy + NZ) % NZ) * NZ + ((x + dx + NZ) % NZ)];
          t[y * NZ + x] = s / 9f;
        }
      System.arraycopy(t, 0, raw, 0, raw.length);
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

  // fractal cloud - several octaves is what makes gas look like gas
  private static float fbm(float x, float y, int oct, float gain) {
    float sum = 0f, amp = 1f, norm = 0f, f = 1f;
    for (int i = 0; i < oct; i++) {
      sum += amp * nz(x * f, y * f);
      norm += amp;
      amp *= gain;
      f *= 2.02f;
    }
    return sum / Math.max(1e-4f, norm);
  }

  // Cloud

  private final CompoundParameter density = new CompoundParameter("Dens", 55, 0, 100)
    .setDescription("How much gas and dust is present");
  private final CompoundParameter scale = new CompoundParameter("Scale", 40, 5, 100)
    .setDescription("Size of the cloud structures - texture only, use Zoom to enlarge");
  private final CompoundParameter zoom = new CompoundParameter("Zoom", 100, 20, 500)
    .setDescription("Magnify the whole nebula, stars and all");
  private final CompoundParameter detail = new CompoundParameter("Detail", 4, 1, 6)
    .setDescription("Octaves of fractal structure");
  private final CompoundParameter wisp = new CompoundParameter("Wisp", 45, 0, 100)
    .setDescription("Stringy filamentary texture rather than smooth cloud");
  private final CompoundParameter contrast = new CompoundParameter("Contrast", 50, 0, 100)
    .setDescription("Separation between dense knots and thin gas");
  private final CompoundParameter drift = new CompoundParameter("Drift", 20, 0, 100)
    .setDescription("How fast the cloud churns");
  private final CompoundParameter seed = new CompoundParameter("Seed", 6, 0, 40)
    .setDescription("Which cloud");

  // Infant stars - the cloud is lit from WITHIN

  private final CompoundParameter stars = new CompoundParameter("Stars", 7, 0, 40)
    .setDescription("Infant stars embedded in the cloud");
  private final CompoundParameter starBri = new CompoundParameter("StarBri", 70, 0, 100)
    .setDescription("How brightly they burn");
  private final CompoundParameter bloom = new CompoundParameter("Bloom", 55, 0, 100)
    .setDescription("How far their light penetrates the surrounding gas");
  private final CompoundParameter twinkle = new CompoundParameter("Twinkle", 25, 0, 100)
    .setDescription("Variation in their output");
  private final CompoundParameter cavity = new CompoundParameter("Cavity", 35, 0, 100)
    .setDescription("How much stellar wind clears gas away around each star");

  // Dark structure

  private final CompoundParameter dust = new CompoundParameter("Dust", 40, 0, 100)
    .setDescription("Dark dust lanes silhouetted against the glow");
  private final CompoundParameter dustScale = new CompoundParameter("DustSc", 30, 5, 100)
    .setDescription("Size of the dust lanes");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 340, 0, 360)
    .setDescription("Hue of the thin outer gas");
  private final CompoundParameter hueCore = new CompoundParameter("HueCore", 40, 0, 360)
    .setDescription("Hue of the illuminated dense regions");
  private final CompoundParameter hueStar = new CompoundParameter("HueStar", 200, 0, 360)
    .setDescription("Hue of the stars themselves");
  private final CompoundParameter satNeb = new CompoundParameter("Sat", 60, 0, 100)
    .setDescription("Saturation of the thin gas");
  private final CompoundParameter satCore = new CompoundParameter("SatCore", 30, 0, 100)
    .setDescription("Saturation of the lit regions");
  private final CompoundParameter bright = new CompoundParameter("Bright", 85, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 4, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count - 1 wraps one continuous nebula");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");

  // Layout


  private final float[] stX = new float[MAX_STAR];
  private final float[] stY = new float[MAX_STAR];
  private final float[] stM = new float[MAX_STAR];
  private final float[] stP = new float[MAX_STAR];
  // The nebula is expensive per pixel (several octaves of noise). Evaluating it
  // once into a small buffer and sampling that for every surface costs less
  // than half of evaluating it separately for all 14,000 exterior pixels, and
  // a soft cloud loses nothing to the lower resolution.
  private static final int BW = 128, BH = 48;
  private final float[] bufBri = new float[BW * BH];
  private final float[] bufHue = new float[BW * BH];
  private final float[] bufSat = new float[BW * BH];
  private float nbBri, nbHue, nbSat;

  private final float[] stFlick = new float[MAX_STAR];   // per-frame, not per-pixel
  private final float[] stReach = new float[MAX_STAR];
  private final float[] stCore = new float[MAX_STAR];
  private final float[] stCav = new float[MAX_STAR];
  private int starCount = 0;

  private boolean dirty = true;
  private float time = 0f;

  private float sDens, sScale, sWisp, sContrast, sBloom, sCavity;
  private float sZoom;
  private float sStarBri, sTwinkle, sDust, sDustSc;
  private float sBr, sBk, sHue, sHueCore, sHueStar, sSat, sSatCore;
  private int sOct;

  public StellarNursery(LX lx) {
    super(lx);
    addParameter("Dens",     this.density);
    addParameter("Scale",    this.scale);
    addParameter("Zoom",     this.zoom);
    addParameter("Detail",   this.detail);
    addParameter("Wisp",     this.wisp);
    addParameter("Contrast", this.contrast);
    addParameter("Drift",    this.drift);
    addParameter("Seed",     this.seed);
    addParameter("Stars",    this.stars);
    addParameter("StarBri",  this.starBri);
    addParameter("Bloom",    this.bloom);
    addParameter("Twinkle",  this.twinkle);
    addParameter("Cavity",   this.cavity);
    addParameter("Dust",     this.dust);
    addParameter("DustSc",   this.dustScale);
    addParameter("Hue",      this.hue);
    addParameter("HueCore",  this.hueCore);
    addParameter("HueStar",  this.hueStar);
    addParameter("Sat",      this.satNeb);
    addParameter("SatCore",  this.satCore);
    addParameter("Bright",   this.bright);
    addParameter("Black",    this.black);
    addParameter("Repeat",   this.repeat);
    addParameter("Sym",      this.symmetry);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == stars || p == seed) dirty = true;
  }

  private void placeStars() {
    Random r = new Random((long) seed.getValuef() * 8191L + 5L);
    starCount = clampi(Math.round(stars.getValuef()), 0, MAX_STAR);
    for (int i = 0; i < starCount; i++) {
      stX[i] = 0.10f + r.nextFloat() * 0.80f;
      stY[i] = 0.10f + r.nextFloat() * 0.80f;
      // a few bright, most modest
      float m = r.nextFloat();
      stM[i] = 0.30f + m * m * 0.70f;
      stP[i] = r.nextFloat() * 6.2832f;
    }
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt * (drift.getValuef() / 100f);

    if (dirty) { placeStars(); dirty = false; }

    sDens     = density.getValuef() / 100f;
    sZoom     = zoom.getValuef() / 100f;
    sScale    = 1.6f + (1f - scale.getValuef() / 100f) * 9f;
    sOct      = clampi(Math.round(detail.getValuef()), 1, 6);
    sWisp     = wisp.getValuef() / 100f;
    sContrast = contrast.getValuef() / 100f;
    sBloom    = 0.04f + bloom.getValuef() / 100f * 0.35f;
    sCavity   = cavity.getValuef() / 100f;
    sStarBri  = starBri.getValuef() / 100f;
    sTwinkle  = twinkle.getValuef() / 100f;
    sDust     = dust.getValuef() / 100f;
    sDustSc   = 0.8f + (1f - dustScale.getValuef() / 100f) * 9f;
    sBr       = bright.getValuef();
    sBk       = black.getValuef() / 100f;
    sHue      = hue.getValuef();
    sHueCore  = hueCore.getValuef();
    sHueStar  = hueStar.getValuef();
    sSat      = satNeb.getValuef();
    sSatCore  = satCore.getValuef();

    // Everything about a star that does not depend on the pixel is computed
    // once here. Doing the flicker per-pixel meant tens of thousands of trig
    // calls a frame, which is what pinned the CPU.
    for (int i = 0; i < starCount; i++) {
      float flick = 1f;
      if (sTwinkle > 0f) {
        flick = 1f - sTwinkle * 0.35f
              * (0.5f + 0.5f * (float) Math.sin(time * 2.1f + stP[i]));
      }
      float m = stM[i] * flick;
      stFlick[i] = m;
      stReach[i] = sBloom * (0.5f + m);
      stCore[i] = 0.006f + m * 0.010f;
      stCav[i] = sBloom * 0.35f * m;
    }

    buildBuffer();
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
        colors[row.points[ci].index] = wrapped(u, v);
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
        colors[ring.points[pi].index] = wrapped(u, v);
      }
    }
  }

  private void buildBuffer() {
    for (int y = 0; y < BH; y++) {
      float v = (y + 0.5f) / BH;
      for (int x = 0; x < BW; x++) {
        float u = (x + 0.5f) / BW;
        nebulaAt(u, v);
        int c = y * BW + x;
        bufBri[c] = nbBri; bufHue[c] = nbHue; bufSat[c] = nbSat;
      }
    }
  }

  private int wrapped(float u, float v) {
    float fx = u * BW - 0.5f, fy = v * (BH - 1);
    int x0 = (int) Math.floor(fx), y0 = (int) fy;
    float tx = fx - x0, ty = fy - y0;
    if (y0 < 0) { y0 = 0; ty = 0f; }
    if (y0 >= BH - 1) { y0 = BH - 2; ty = 1f; }
    int xa = ((x0 % BW) + BW) % BW, xb = (xa + 1) % BW;
    int r0 = y0 * BW, r1 = r0 + BW;
    float b = bilerp(bufBri[r0 + xa], bufBri[r0 + xb], bufBri[r1 + xa], bufBri[r1 + xb], tx, ty);
    if (b < 0.002f) return LXColor.BLACK;
    float h = bilerp(bufHue[r0 + xa], bufHue[r0 + xb], bufHue[r1 + xa], bufHue[r1 + xb], tx, ty);
    float sa = bilerp(bufSat[r0 + xa], bufSat[r0 + xb], bufSat[r1 + xa], bufSat[r1 + xb], tx, ty);
    return LXColor.hsb(h, sa, b * 100f);
  }

  private static float bilerp(float c00, float c10, float c01, float c11, float tx, float ty) {
    float a = c00 + (c10 - c00) * tx;
    float b = c01 + (c11 - c01) * tx;
    return a + (b - a) * ty;
  }

  private void nebulaAt(float u, float v) {
    // Zoom and pan transform the sampling position, so the cloud AND the stars
    // embedded in it move together as one object.
    if (sZoom != 1f) {
      u = 0.5f + (u - 0.5f) / sZoom;
      v = 0.5f + (v - 0.5f) / sZoom;
    }
    float t = time * 0.35f;

    // the gas cloud itself
    float g = fbm(u * sScale + t * 0.15f, v * sScale - t * 0.09f, sOct, 0.55f);

    // ridged noise gives stringy filaments rather than smooth billows
    if (sWisp > 0f) {
      float rg = 1f - Math.abs(g - 0.5f) * 2f;
      g = g + (rg - g) * sWisp;
    }

    // contrast pushes gas into knots and voids
    if (sContrast > 0f) {
      float c = clamp01((g - 0.45f) * (1f + sContrast * 3.5f) + 0.45f);
      g = g + (c - g) * sContrast;
    }
    g *= sDens;

    // Illumination: each infant star lights the gas around it. This is what
    // makes an emission nebula - the cloud does not glow on its own.
    float lit = 0f, starCore = 0f;
    for (int i = 0; i < starCount; i++) {
      float dx = u - stX[i], dy = v - stY[i];
      float d2 = dx * dx + dy * dy;
      float reach = stReach[i];
      // cheap reject before the square root
      if (d2 > reach * reach) continue;
      float d = (float) Math.sqrt(d2);
      float m = stFlick[i];
      float f = 1f - d / reach;
      lit += f * f * m;
      // stellar wind blows a cavity in the gas around each star
      if (sCavity > 0f && d < stCav[i]) {
        g *= 1f - sCavity * (1f - d / stCav[i]);
      }
      // the star itself
      if (d < stCore[i]) {
        float c = (1f - d / stCore[i]);
        starCore = Math.max(starCore, c * c * m);
      }
    }

    // dark dust lanes, silhouetted against the illuminated gas
    if (sDust > 0f) {
      float dl = fbm(u * sDustSc + 31f, v * sDustSc + 17f, 2, 0.5f);
      float lane = clamp01((dl - 0.52f) * 5f);
      g *= 1f - sDust * lane;
      lit *= 1f - sDust * lane * 0.8f;
    }

    float glow = g * (0.14f + lit * 1.5f * sStarBri);
    float bri = glow;
    float litness = clamp01(lit * sStarBri * 1.4f);

    if (starCore > 0f) {
      float sc = starCore * sStarBri;
      if (sc > bri) {
        bri = sc;
        litness = 2f;   // flag: this pixel is a star, not gas
      }
    }

    bri *= sBr / 100f;
    if (bri < sBk) { nbBri = 0f; nbHue = 0f; nbSat = 0f; return; }

    float h, s;
    if (litness > 1.5f) {
      h = sHueStar;
      s = 12f;
    } else {
      float k = clamp01(litness);
      h = sHue + (sHueCore - sHue) * k;
      s = sSat + (sSatCore - sSat) * k;
    }
    h = ((h % 360f) + 360f) % 360f;

    nbBri = clamp01(bri);
    nbHue = h;
    nbSat = clamp01(s / 100f) * 100f;
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
