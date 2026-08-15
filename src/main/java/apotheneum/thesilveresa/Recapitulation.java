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
@LXComponent.Name("Recapitulation")
public class Recapitulation extends ApotheneumPattern {

  private static final int LAYERS = 6;
  private static final int NZ = 64;

  private static final float[] NOISE = new float[NZ * NZ];
  static {
    Random r = new Random(299792458L);
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

  // The unwinding

  private final CompoundParameter unwind = new CompoundParameter("Unwind", 0, 0, 100)
    .setDescription("Fade the scales away in reverse - cosmic first, quantum last");
  private final CompoundParameter overlap = new CompoundParameter("Overlap", 35, 0, 100)
    .setDescription("How much neighbouring scales bleed into each other as they go");
  private final CompoundParameter present = new CompoundParameter("Present", 100, 0, 100)
    .setDescription("How many scales are lit before the unwinding starts");

  // Layer weights - each scale can be balanced by hand

  private final CompoundParameter web = new CompoundParameter("Web", 70, 0, 100)
    .setDescription("Cosmic web layer");
  private final CompoundParameter galaxy = new CompoundParameter("Galaxy", 70, 0, 100)
    .setDescription("Galaxy layer");
  private final CompoundParameter star = new CompoundParameter("Star", 70, 0, 100)
    .setDescription("Stellar layer");
  private final CompoundParameter city = new CompoundParameter("City", 70, 0, 100)
    .setDescription("City layer");
  private final CompoundParameter cell = new CompoundParameter("Cell", 70, 0, 100)
    .setDescription("Cellular layer");
  private final CompoundParameter crystal = new CompoundParameter("Crystal", 70, 0, 100)
    .setDescription("Crystalline layer");

  // Motion

  private final CompoundParameter drift = new CompoundParameter("Drift", 20, 0, 100)
    .setDescription("Slow motion within the layers");
  private final CompoundParameter breathe = new CompoundParameter("Breathe", 25, 0, 100)
    .setDescription("Slow swell across the whole field");

  // The last pulse

  private final TriggerParameter pulse = new TriggerParameter("Pulse", this::firePulse)
    .setDescription("The final single pulse");
  private final CompoundParameter pulseDecay = new CompoundParameter("PulseDk", 40, 5, 100)
    .setDescription("How long the final pulse lingers");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 210, 0, 360)
    .setDescription("Hue of the largest scale");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", 120, -180, 180)
    .setDescription("Hue spread across the scales");
  private final CompoundParameter satAll = new CompoundParameter("Sat", 40, 0, 100)
    .setDescription("Saturation");
  private final CompoundParameter bright = new CompoundParameter("Bright", 85, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");

  private final float[] weight = new float[LAYERS];
  private float time = 0f;
  private float pulseLevel = 0f;
  private boolean pulsePending = false;

  private float sBr, sBk, sHue, sHueRng, sSat, sDrift, sBreathe;

  public Recapitulation(LX lx) {
    super(lx);
    addParameter("Unwind",  this.unwind);
    addParameter("Overlap", this.overlap);
    addParameter("Present", this.present);
    addParameter("Web",     this.web);
    addParameter("Galaxy",  this.galaxy);
    addParameter("Star",    this.star);
    addParameter("City",    this.city);
    addParameter("Cell",    this.cell);
    addParameter("Crystal", this.crystal);
    addParameter("Drift",   this.drift);
    addParameter("Breathe", this.breathe);
    addParameter("Pulse",   this.pulse);
    addParameter("PulseDk", this.pulseDecay);
    addParameter("Hue",     this.hue);
    addParameter("HueRng",  this.hueRange);
    addParameter("Sat",     this.satAll);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Repeat",  this.repeat);
    addParameter("Sym",     this.symmetry);
  }

  private void firePulse() {
    pulsePending = true;
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    if (pulsePending) { pulseLevel = 1f; pulsePending = false; }
    if (pulseLevel > 0f) {
      pulseLevel -= dt * (pulseDecay.getValuef() / 100f) * 0.9f;
      if (pulseLevel < 0f) pulseLevel = 0f;
    }

    // Layer 0 is the cosmic web, layer 5 the crystal lattice. The unwinding
    // sweeps from 0 upward, so the largest scales go first - the reverse of
    // the journey the piece has just made.
    float un = unwind.getValuef() / 100f;
    float ov = 0.05f + overlap.getValuef() / 100f * 0.75f;
    float pres = present.getValuef() / 100f;

    float[] base = {
      web.getValuef(), galaxy.getValuef(), star.getValuef(),
      city.getValuef(), cell.getValuef(), crystal.getValuef()
    };

    for (int i = 0; i < LAYERS; i++) {
      float at = (float) i / (LAYERS - 1);
      // each layer's own fade window; overlap widens them so they cross-fade
      float edge = un * (1f + ov) - at;
      float gone = clamp01(edge / Math.max(1e-4f, ov));
      float avail = clamp01((pres * (LAYERS + 0.5f) - i) / 1.0f);
      weight[i] = (base[i] / 100f) * (1f - gone) * avail;
    }

    sBr      = bright.getValuef();
    sBk      = black.getValuef() / 100f;
    sHue     = hue.getValuef();
    sHueRng  = hueRange.getValuef();
    sSat     = satAll.getValuef();
    sDrift   = drift.getValuef() / 100f;
    sBreathe = breathe.getValuef() / 100f;

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
        colors[row.points[ci].index] = sampleAll(u, v);
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
        colors[ring.points[pi].index] = sampleAll(u, v);
      }
    }
  }

  // Each layer is the same generator at a different frequency - which is the
  // thesis of the whole piece stated one last time in a single image.
  private float layerAt(int i, float u, float v, float t) {
    switch (i) {
      case 0: {   // cosmic web - broad filaments
        float a = nz(u * 1.6f + t * 0.02f, v * 1.6f);
        float b = nz(u * 1.6f + 4.1f, v * 1.6f - t * 0.015f);
        float ridge = 1f - Math.abs(a - b) * 3.4f;
        return clamp01(ridge) * clamp01(ridge);
      }
      case 1: {   // galaxies - discrete bright knots
        float n = nz(u * 4.5f + 11f + t * 0.03f, v * 4.5f + 3f);
        return clamp01((n - 0.62f) * 5.5f);
      }
      case 2: {   // stars - fine points
        float n = nz(u * 13f + 27f, v * 13f + 7f + t * 0.05f);
        return clamp01((n - 0.74f) * 9f);
      }
      case 3: {   // city - orthogonal grid
        float gx = Math.abs((u * 14f + t * 0.04f) % 1f - 0.5f);
        float gy = Math.abs((v * 14f) % 1f - 0.5f);
        return clamp01(1f - Math.min(gx, gy) * 7f) * 0.8f;
      }
      case 4: {   // cells - packed rounded domains
        float n = nz(u * 9f + 51f, v * 9f + 19f + t * 0.03f);
        float m = nz(u * 9f + 71f, v * 9f + 31f);
        return clamp01(1f - Math.abs(n - m) * 6f) * 0.85f;
      }
      default: {  // crystal - a tight periodic lattice
        float s = (float) (Math.sin(u * 78f) * Math.sin(v * 78f));
        return clamp01(Math.abs(s) * 1.6f - 0.55f) * 1.4f;
      }
    }
  }

  private int sampleAll(float u, float v) {
    float t = time * (0.2f + sDrift * 2.4f);
    float swell = 1f + sBreathe * 0.22f * (float) Math.sin(time * 0.35f);

    float bri = 0f, hueAcc = 0f, wSum = 0f;
    for (int i = 0; i < LAYERS; i++) {
      float w = weight[i];
      if (w <= 0.002f) continue;
      float lv = layerAt(i, u, v, t) * w;
      if (lv <= 0f) continue;
      bri += lv;
      float at = (float) i / (LAYERS - 1);
      hueAcc += lv * at;
      wSum += lv;
    }

    bri *= swell;

    // the final pulse: one omnidirectional swell, then nothing
    if (pulseLevel > 0f) {
      float dx = u - 0.5f, dy = v - 0.5f;
      float d = (float) Math.sqrt(dx * dx + dy * dy);
      float p = pulseLevel * clamp01(1f - d * 1.3f);
      bri = Math.max(bri, p * p);
      wSum += p;
    }

    bri = clamp01(bri) * (sBr / 100f);
    if (bri < sBk) return LXColor.BLACK;

    float at = wSum > 1e-5f ? hueAcc / wSum : 0f;
    float h = sHue + sHueRng * at;
    h = ((h % 360f) + 360f) % 360f;

    return LXColor.hsb(h, clamp01(sSat / 100f) * 100f, clamp01(bri) * 100f);
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
