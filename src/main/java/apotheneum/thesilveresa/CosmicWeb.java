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
@LXComponent.Name("Cosmic Web")
public class CosmicWeb extends ApotheneumPattern {

  private static final int FW = 144, FH = 144;
  private static final int MAX_SEED = 220;

  // Structure

  private final CompoundParameter seeds = new CompoundParameter("Nodes", 55, 6, 220)
    .setDescription("Density peaks the matter collapses onto");
  private final CompoundParameter filament = new CompoundParameter("Filament", 55, 0, 100)
    .setDescription("How sharply matter is drawn into filaments between nodes");
  private final CompoundParameter voidSize = new CompoundParameter("Voids", 50, 0, 100)
    .setDescription("How empty the spaces between filaments become");
  private final CompoundParameter clump = new CompoundParameter("Clump", 45, 0, 100)
    .setDescription("Brightness concentration at the nodes");
  private final CompoundParameter texture = new CompoundParameter("Texture", 35, 0, 100)
    .setDescription("Fine structure within the filaments");
  private final CompoundParameter thin = new CompoundParameter("Thin", 45, 0, 100)
    .setDescription("How fine the strands are - high gives a delicate web");
  private final CompoundParameter contrast = new CompoundParameter("Contrast", 50, 0, 100)
    .setDescription("Deepens the voids and keeps only the strands lit");
  private final CompoundParameter seed = new CompoundParameter("Seed", 9, 0, 40)
    .setDescription("Which realisation of the web");

  // Scale - the same generator at different zoom is the whole thesis

  private final CompoundParameter zoom = new CompoundParameter("Zoom", 50, 10, 100)
    .setDescription("How much of the web is in frame");
  private final CompoundParameter mag = new CompoundParameter("Mag", 100, 100, 900)
    .setDescription("Magnify into the web - drift needs this above 100 to have room");
  private final CompoundParameter driftX = new CompoundParameter("DriftX", 0, -100, 100)
    .setDescription("Pan across the web");
  private final CompoundParameter driftY = new CompoundParameter("DriftY", 0, -100, 100)
    .setDescription("Pan down the web");

  // Basins of attraction

  private final CompoundParameter basins = new CompoundParameter("Basins", 0, 0, 100)
    .setDescription("Waves of matter pouring along the filaments toward the nodes");
  private final CompoundParameter basinScale = new CompoundParameter("BasinSc", 40, 5, 100)
    .setDescription("Length of the travelling waves");
  private final CompoundParameter flowRate = new CompoundParameter("Flow", 40, 0, 100)
    .setDescription("How fast matter streams along the basins");

  // Inversion - scene where the voids become the subject

  private final CompoundParameter invert = new CompoundParameter("Invert", 0, 0, 100)
    .setDescription("Figure and ground exchange - the voids become the form");
  private final CompoundParameter dim = new CompoundParameter("Dim", 0, 0, 100)
    .setDescription("Fade the web toward illegibility");

  // Matter

  private final CompoundParameter gain = new CompoundParameter("Gain", 55, 5, 100)
    .setDescription("Density to brightness response");
  private final CompoundParameter grain = new CompoundParameter("Grain", 40, 0, 100)
    .setDescription("Particulate hardness");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 218, 0, 360)
    .setDescription("Hue of the sparse regions");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", -45, -180, 180)
    .setDescription("Hue shift toward the dense nodes");
  private final CompoundParameter satWeb = new CompoundParameter("Sat", 50, 0, 100)
    .setDescription("Saturation of the sparse regions");
  private final CompoundParameter satRange = new CompoundParameter("SatRng", -30, -100, 100)
    .setDescription("Saturation shift toward the nodes");
  private final CompoundParameter bright = new CompoundParameter("Bright", 88, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");

  // Web

  private final float[] nX = new float[MAX_SEED];
  private final float[] nY = new float[MAX_SEED];
  private final float[] nM = new float[MAX_SEED];
  private int nCount = 0;

  private final float[] field = new float[FW * FH];
  private final float[] dense = new float[FW * FH];
  private final float[] voidf = new float[FW * FH];
  private float voidPeak = 1f;

  private boolean dirty = true;
  private double sinceRebuild = 1e9;
  private float time = 0f;
  private float scaleNow = 1f;

  private float sGain, sBr, sBk, sGrain, sHue, sHueRng, sSat, sSatRng, sContrast, sPow;
  private float sInvert, sDim, sBasins, sFlow, sBasinSc;

  public CosmicWeb(LX lx) {
    super(lx);
    addParameter("Nodes",    this.seeds);
    addParameter("Filament", this.filament);
    addParameter("Voids",    this.voidSize);
    addParameter("Clump",    this.clump);
    addParameter("Texture",  this.texture);
    addParameter("Thin",     this.thin);
    addParameter("Contrast", this.contrast);
    addParameter("Seed",     this.seed);
    addParameter("Zoom",     this.zoom);
    addParameter("Mag",      this.mag);
    addParameter("DriftX",   this.driftX);
    addParameter("DriftY",   this.driftY);
    addParameter("Basins",   this.basins);
    addParameter("BasinSc",  this.basinScale);
    addParameter("Flow",     this.flowRate);
    addParameter("Invert",   this.invert);
    addParameter("Dim",      this.dim);
    addParameter("Gain",     this.gain);
    addParameter("Grain",    this.grain);
    addParameter("Hue",      this.hue);
    addParameter("HueRng",   this.hueRange);
    addParameter("Sat",      this.satWeb);
    addParameter("SatRng",   this.satRange);
    addParameter("Bright",   this.bright);
    addParameter("Black",    this.black);
    addParameter("Repeat",   this.repeat);
    addParameter("Sym",      this.symmetry);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == seeds || p == filament || p == voidSize || p == clump
        || p == texture || p == seed || p == zoom || p == thin) {
      dirty = true;
    }
  }

  private float fieldPeak = 1f;

  // The tone curve (exponential exposure, grain, contrast) depends only on the
  // density, so it is tabulated once instead of an exp and a pow per pixel.
  private static final int TONE = 512;
  private final float[] tone = new float[TONE];
  private float toneGain = -1f, toneGrain = -1f, toneContrast = -1f;

  private void buildTone() {
    for (int i = 0; i < TONE; i++) {
      float d = (float) i / (TONE - 1);
      float b = 1f - (float) Math.exp(-d * sGain);
      if (sGrain > 0f) {
        float hard = b * b * (3f - 2f * b);
        b = b + (hard - b) * sGrain;
      }
      tone[i] = b;
    }
    toneGain = sGain; toneGrain = sGrain;
  }

  private float toneAt(float d) {
    if (d <= 0f) return 0f;
    if (d >= 1f) return tone[TONE - 1];
    float f = d * (TONE - 1);
    int k = (int) f;
    return tone[k] + (tone[k + 1] - tone[k]) * (f - k);
  }

  private static final int SINN = 2048;
  private static final float[] SINT = new float[SINN];
  static { for (int i = 0; i < SINN; i++) SINT[i] = (float) Math.sin(6.2831855 * i / SINN); }
  private static float fsin(float a) {
    return SINT[(int) (a * (SINN / 6.2831855f)) & (SINN - 1)];
  }

  private void build() {
    fieldPeak = 1e-4f;
    voidPeak = 1e-4f;
    Random r = new Random((long) seed.getValuef() * 5171L + 11L);
    nCount = clampi(Math.round(seeds.getValuef()), 6, MAX_SEED);
    float z = zoom.getValuef() / 100f;
    // seeds spread beyond the frame so the web continues past the edges
    float span = 1f + 1.6f * (1f - z);
    for (int i = 0; i < nCount; i++) {
      nX[i] = -0.5f * (span - 1f) + r.nextFloat() * span;
      nY[i] = -0.5f * (span - 1f) + r.nextFloat() * span;
      // a few rare heavy nodes, many light ones
      float m = r.nextFloat();
      nM[i] = 0.25f + m * m * m * 3.2f;
    }

    float fil = filament.getValuef() / 100f;
    float voidA = voidSize.getValuef() / 100f;
    float clumpA = clump.getValuef() / 100f;
    float tex = texture.getValuef() / 100f;
    float thinA = thin.getValuef() / 100f;

    // Build the density field. Each point is scored by the two nearest seeds:
    // being close to ONE gives a node, being close to BOTH gives a filament,
    // being far from both gives a void. That second-nearest term is the whole
    // trick - it is what makes strands rather than blobs.
    for (int gy = 0; gy < FH; gy++) {
      float py = (gy + 0.5f) / FH;
      for (int gx = 0; gx < FW; gx++) {
        float px = (gx + 0.5f) / FW;
        float d1 = Float.MAX_VALUE, d2 = Float.MAX_VALUE;
        float m1 = 1f;
        for (int i = 0; i < nCount; i++) {
          float dx = px - nX[i], dy = py - nY[i];
          float d = (float) Math.sqrt(dx * dx + dy * dy) / nM[i];
          if (d < d1) { d2 = d1; d1 = d; m1 = nM[i]; }
          else if (d < d2) { d2 = d; }
        }
        // node term: bright close to a single seed
        float node = (float) Math.exp(-d1 * (7f + 16f * clumpA)) * (0.5f + 0.5f * m1 / 3.4f);
        // filament term: bright where two seeds are nearly equidistant
        float ridge = (float) Math.exp(-(d2 - d1) * (14f + 42f * fil + 420f * thinA * thinA));
        ridge *= (float) Math.exp(-d1 * (1.6f + 3.4f * voidA));
        float v = node + ridge * (0.35f + 0.65f * fil);
        if (tex > 0f) {
          float n = (float) ((Math.sin(px * 61.7 + py * 37.1) * 0.5 + 0.5)
                           * (Math.sin(px * 23.3 - py * 47.9) * 0.5 + 0.5));
          v *= 1f - tex * 0.5f * n;
        }
        int c = gy * FW + gx;
        field[c] = v;
        if (v > fieldPeak) fieldPeak = v;
        dense[c] = clamp01(node * 2.2f);
        // How far this point sits from any node. This is the void as a thing
        // in its own right, not one minus the filaments.
        voidf[c] = d1;
        if (d1 > voidPeak) voidPeak = d1;
      }
    }
  }

  private void normaliseField() {
    // packing more nodes into the frame raised the density everywhere, which
    // is why zooming in blew the brightness out
    float inv = 1f / Math.max(1e-4f, fieldPeak);
    for (int i = 0; i < field.length; i++) field[i] *= inv;
    fieldPeak = 1f;
    float invV = 1f / Math.max(1e-4f, voidPeak);
    for (int i = 0; i < voidf.length; i++) voidf[i] *= invV;
    voidPeak = 1f;
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    sinceRebuild += deltaMs;
    if (dirty && sinceRebuild >= 90.0) {
      build();
      normaliseField();
      dirty = false;
      sinceRebuild = 0.0;
    }
    if (nCount == 0) { build(); normaliseField(); }


    sGain    = gain.getValuef() / 100f * 6f;
    sGrain   = grain.getValuef() / 100f;
    sContrast = contrast.getValuef() / 100f;
    if (sGain != toneGain || sGrain != toneGrain) buildTone();
    sPow = 1f + sContrast * 3.2f;
    sBr      = bright.getValuef();
    sBk      = black.getValuef() / 100f;
    sHue     = hue.getValuef();
    sHueRng  = hueRange.getValuef();
    sSat     = satWeb.getValuef();
    sSatRng  = satRange.getValuef();
    sInvert  = invert.getValuef() / 100f;
    sDim     = dim.getValuef() / 100f;
    sBasins  = basins.getValuef() / 100f;
    scaleNow = mag.getValuef() / 100f;
    sFlow    = flowRate.getValuef() / 100f;
    sBasinSc = 2f + (1f - basinScale.getValuef() / 100f) * 22f;

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
        colors[row.points[ci].index] = sampleWeb(u, v);
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
        colors[ring.points[pi].index] = sampleWeb(u, v);
      }
    }
  }

  private int sampleWeb(float u, float v) {
    // pan and Hubble expansion move the sampling window, not the web itself
    // The window can only pan as far as the field extends. At Mag 100 the
    // window already covers everything, so any drift would run off the edge -
    // which is why half the structure went dark.
    float half = 0.5f / scaleNow;
    float room = Math.max(0f, 0.5f - half);
    float cx = 0.5f + driftX.getValuef() / 100f * room;
    float cy = 0.5f + driftY.getValuef() / 100f * room;
    float su = cx + (u - 0.5f) / scaleNow;
    float sv = cy + (v - 0.5f) / scaleNow;
    if (su < 0f) su = 0f; else if (su > 1f) su = 1f;
    if (sv < 0f) sv = 0f; else if (sv > 1f) sv = 1f;

    float gx = su * (FW - 1), gy = sv * (FH - 1);
    int x0 = (int) gx, y0 = (int) gy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= FW) x1 = FW - 1;
    if (y1 >= FH) y1 = FH - 1;
    float tx = gx - x0, ty = gy - y0;
    int c00 = y0 * FW + x0, c10 = y0 * FW + x1;
    int c01 = y1 * FW + x0, c11 = y1 * FW + x1;

    float d = bilerp(field[c00], field[c10], field[c01], field[c11], tx, ty);
    float nodeness = bilerp(dense[c00], dense[c10], dense[c01], dense[c11], tx, ty);
    float vv = bilerp(voidf[c00], voidf[c10], voidf[c01], voidf[c11], tx, ty);

    float bri = toneAt(d);
    // matter streaming along the basins toward the nodes
    if (sBasins > 0f) {
      // Waves travel along the density gradient, so light flows through the
      // filaments toward the nodes while the structure itself stays put.
      float band = fsin((d * sBasinSc - time * sFlow * 3.2f) * 3.1416f);
      bri *= 1f + sBasins * 0.85f * band * clamp01(d * 1.5f);
    }

    // Figure and ground exchange: the voids become the subject and the
    // filaments the negative space.
    if (sInvert > 0f) {
      // Both images carry their own structure and their own exposure, so the
      // midpoint blends two pictures instead of collapsing to flat grey.
      float briVoid = vv * vv * (3f - 2f * vv);
      bri = bri + (briVoid - bri) * sInvert;
    }

    // Applied after the inversion, so it shapes whichever view is showing.
    // Before, the inverted image got no contrast at all and washed out.
    if (sContrast > 0f) {
      // cheaper than pow and visually identical over 0..1
      float b2 = bri * bri;
      bri = bri + (b2 * (sPow > 3f ? b2 : bri) - bri) * clamp01(sContrast);
    }

    if (sDim > 0f) bri *= 1f - sDim * 0.92f;

    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    float drv = sInvert > 0.5f ? clamp01(1f - nodeness) : nodeness;
    float h = sHue + sHueRng * drv;
    h = ((h % 360f) + 360f) % 360f;
    float s = sSat + sSatRng * drv;

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
