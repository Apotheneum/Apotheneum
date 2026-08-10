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
@LXComponent.Name("Spectral")
public class Spectral extends ApotheneumPattern {

  private static final int MAX_LINE = 600;
  private static final int SPEC = 512;

  private static final int SINN = 4096;
  private static final float[] SINT = new float[SINN];
  static { for (int i = 0; i < SINN; i++) SINT[i] = (float) Math.sin(6.2831853 * i / SINN); }
  private static float fsin(float a) {
    return SINT[(int) (a * (SINN / 6.2831853f)) & (SINN - 1)];
  }

  // The forest

  private final CompoundParameter lines = new CompoundParameter("Lines", 55, 0, 100)
    .setDescription("How many absorption lines - clouds the light passed through");
  private final CompoundParameter depth = new CompoundParameter("Depth", 65, 0, 100)
    .setDescription("How much light each cloud removes");
  private final CompoundParameter width = new CompoundParameter("Width", 30, 1, 100)
    .setDescription("Line width");
  private final CompoundParameter clumpy = new CompoundParameter("Clump", 55, 0, 100)
    .setDescription("How clustered the lines are - the web's own structure");
  private final CompoundParameter seed = new CompoundParameter("Seed", 12, 0, 40)
    .setDescription("Which sightline through the universe");

  // Redshift

  private final CompoundParameter redshift = new CompoundParameter("Redshift", 30, 0, 100)
    .setDescription("How far back the sightline reaches - deeper means denser forest");
  private final CompoundParameter speed = new CompoundParameter("Speed", 100, 0, 100)
    .setDescription("Master pace for all motion - squared, so the low end has room to breathe");

  private final CompoundParameter drift = new CompoundParameter("Drift", 15, -100, 100)
    .setDescription("Scroll through the spectrum");
  private final CompoundParameter spin = new CompoundParameter("Spin", 0, -100, 100)
    .setDescription("Rotate the bands around the cylinder - negative reverses");
  private final CompoundParameter shear = new CompoundParameter("Shear", 0, 0, 100)
    .setDescription("Each band rotates at a different rate, so they slide past one another");
  private final CompoundParameter trough = new CompoundParameter("Trough", 0, 0, 100)
    .setDescription("Gunn-Peterson: past reionisation the lines merge into silence");

  // Continuum

  private final CompoundParameter emission = new CompoundParameter("Emit", 70, 0, 100)
    .setDescription("Brightness of the quasar continuum behind the forest");
  private final CompoundParameter tilt = new CompoundParameter("Tilt", 35, -100, 100)
    .setDescription("Slope of the continuum across the spectrum");
  private final CompoundParameter noise = new CompoundParameter("Noise", 12, 0, 100)
    .setDescription("Instrument noise in the measurement");

  // Form

  private final CompoundParameter bands = new CompoundParameter("Bands", 1, 1, 12)
    .setDescription("How many spectra are stacked");
  private final CompoundParameter bandGap = new CompoundParameter("Gap", 25, 0, 100)
    .setDescription("Dark space between stacked spectra");
  private final CompoundParameter vertical = new CompoundParameter("Vert", 0, 0, 100)
    .setDescription("Run the spectrum vertically instead of across");
  private final CompoundParameter glow = new CompoundParameter("Glow", 30, 0, 100)
    .setDescription("Bloom on the surviving light");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 30, 0, 360)
    .setDescription("Hue at the blue end of the spectrum");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", -60, -180, 180)
    .setDescription("Hue shift across the spectrum - redshift made visible");
  private final CompoundParameter satSpec = new CompoundParameter("Sat", 45, 0, 100)
    .setDescription("Saturation");
  private final CompoundParameter satRange = new CompoundParameter("SatRng", 25, -100, 100)
    .setDescription("Saturation shift across the spectrum");
  private final CompoundParameter bright = new CompoundParameter("Bright", 90, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 2, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");

  private final float[] lPos = new float[MAX_LINE];
  private final float[] lStr = new float[MAX_LINE];
  private final float[] lWid = new float[MAX_LINE];
  private int lineCount = 0;

  private final float[] spectrum = new float[SPEC];

  private boolean dirty = true;
  private double sinceRebuild = 1e9;
  private float time = 0f;

  private float sBr, sBk, sHue, sHueRng, sSat, sSatRng;
  private float sBands, sGap, sVert, sGlow, sNoise, sDriftPos;
  private float sSpinPos, sShear;
  private float driftAcc = 0f, spinAcc = 0f;

  public Spectral(LX lx) {
    super(lx);
    addParameter("Lines",    this.lines);
    addParameter("Depth",    this.depth);
    addParameter("Width",    this.width);
    addParameter("Clump",    this.clumpy);
    addParameter("Seed",     this.seed);
    addParameter("Redshift", this.redshift);
    addParameter("Speed",    this.speed);
    addParameter("Drift",    this.drift);
    addParameter("Spin",     this.spin);
    addParameter("Shear",    this.shear);
    addParameter("Trough",   this.trough);
    addParameter("Emit",     this.emission);
    addParameter("Tilt",     this.tilt);
    addParameter("Noise",    this.noise);
    addParameter("Bands",    this.bands);
    addParameter("Gap",      this.bandGap);
    addParameter("Vert",     this.vertical);
    addParameter("Glow",     this.glow);
    addParameter("Hue",      this.hue);
    addParameter("HueRng",   this.hueRange);
    addParameter("Sat",      this.satSpec);
    addParameter("SatRng",   this.satRange);
    addParameter("Bright",   this.bright);
    addParameter("Black",    this.black);
    addParameter("Repeat",   this.repeat);
    addParameter("Sym",      this.symmetry);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == lines || p == depth || p == width || p == clumpy || p == seed
        || p == redshift || p == trough || p == emission || p == tilt) {
      dirty = true;
    }
  }

  private void build() {
    Random r = new Random((long) seed.getValuef() * 4211L + 19L);
    float z = redshift.getValuef() / 100f;
    // the deeper the sightline, the more clouds it has passed through
    lineCount = (int) (lines.getValuef() / 100f * MAX_LINE * (0.25f + 0.75f * z));
    if (lineCount > MAX_LINE) lineCount = MAX_LINE;
    float cl = clumpy.getValuef() / 100f;
    float dep = depth.getValuef() / 100f;
    float wid = width.getValuef() / 100f;

    for (int i = 0; i < lineCount; i++) {
      float p;
      if (cl > 0f && r.nextFloat() < cl) {
        // clouds cluster, because the web they trace is itself clustered
        float centre = r.nextFloat();
        p = clamp01(centre + (float) r.nextGaussian() * 0.02f);
      } else {
        p = r.nextFloat();
      }
      lPos[i] = p;
      float s = r.nextFloat();
      lStr[i] = dep * (0.12f + s * s * 0.88f);
      lWid[i] = (0.0015f + wid * 0.010f) * (0.5f + r.nextFloat());
    }

    // Build the spectrum once: continuum minus every absorption line.
    float emit = emission.getValuef() / 100f;
    float slope = tilt.getValuef() / 100f;
    float tr = trough.getValuef() / 100f;
    // A line is only a few thousandths of the spectrum wide, so touching all
    // 512 bins for each of up to 600 lines was around a hundred times more
    // work than needed. Accumulate each line into just the bins it reaches.
    float[] absorbBuf = new float[SPEC];
    for (int j = 0; j < lineCount; j++) {
      float w = lWid[j];
      float reach = w * 4f;
      int i0 = (int) ((lPos[j] - reach) * (SPEC - 1));
      int i1 = (int) ((lPos[j] + reach) * (SPEC - 1)) + 1;
      if (i0 < 0) i0 = 0;
      if (i1 > SPEC) i1 = SPEC;
      float inv = 1f / (2f * w * w);
      float str = lStr[j];
      for (int i = i0; i < i1; i++) {
        float d = (float) i / (SPEC - 1) - lPos[j];
        absorbBuf[i] += str * (float) Math.exp(-(d * d) * inv);
      }
    }

    for (int i = 0; i < SPEC; i++) {
      float x = (float) i / (SPEC - 1);
      float cont = emit * (1f + slope * (x - 0.5f));
      float absorb = absorbBuf[i];
      // Gunn-Peterson: below a wavelength the lines merge and the light
      // stops arriving altogether. The forest ends not because structure
      // ends, but because the universe was not yet transparent.
      float gp = 0f;
      if (tr > 0f) {
        float edge = 1f - tr;
        if (x < edge) gp = clamp01((edge - x) / Math.max(0.03f, edge * 0.5f));
      }
      float v = cont * (float) Math.exp(-absorb) * (1f - gp);
      spectrum[i] = clamp01(v);
    }
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    sinceRebuild += deltaMs;
    if (dirty && sinceRebuild >= 90.0) {
      build();
      dirty = false;
      sinceRebuild = 0.0;
    }
    if (lineCount == 0 && spectrum[0] == 0f) build();

    // Accumulated rather than time x rate. Multiplying by elapsed time meant
    // any change to the rate teleported the spectrum, which would have made
    // these knobs unusable to automate. It also keeps the value bounded.
    // Squared so the bottom of the travel is finely resolved - at 100 this is
    // exactly the old rate, at 20 it is a twenty-fifth of it.
    float sp = speed.getValuef() / 100f;
    sp *= sp;
    driftAcc += drift.getValuef() / 100f * 0.12f * sp * dt;
    driftAcc -= (float) Math.floor(driftAcc);
    spinAcc += spin.getValuef() / 100f * 0.55f * sp * dt;
    spinAcc -= (float) Math.floor(spinAcc);
    sDriftPos = driftAcc;
    sSpinPos = spinAcc;
    sShear = shear.getValuef() / 100f;
    sBands  = clampi(Math.round(bands.getValuef()), 1, 12);
    sGap    = bandGap.getValuef() / 100f;
    sVert   = vertical.getValuef() / 100f;
    sGlow   = glow.getValuef() / 100f;
    sNoise  = noise.getValuef() / 100f;
    sBr     = bright.getValuef();
    sBk     = black.getValuef() / 100f;
    sHue    = hue.getValuef();
    sHueRng = hueRange.getValuef();
    sSat    = satSpec.getValuef();
    sSatRng = satRange.getValuef();

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
        colors[row.points[ci].index] = sampleSpec(u, v);
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
        colors[ring.points[pi].index] = sampleSpec(u, v);
      }
    }
  }

  private float specAt(float x) {
    x = x - (float) Math.floor(x);
    float f = x * (SPEC - 1);
    int i0 = (int) f;
    int i1 = i0 + 1;
    if (i1 >= SPEC) i1 = SPEC - 1;
    float t = f - i0;
    return spectrum[i0] + (spectrum[i1] - spectrum[i0]) * t;
  }

  private int sampleSpec(float u, float v) {
    // the spectral axis can run across or down
    float along = u + (v - u) * sVert;
    float across = v + (u - v) * sVert;

    int nb = (int) sBands;
    float bandH = 1f / nb;
    float within = (across % bandH) / bandH;
    // dark gutters between stacked spectra
    float g = sGap * 0.5f;
    if (within < g || within > 1f - g) return LXColor.BLACK;

    int bandIdx = (int) (across / bandH);
    // each stacked band is a different sightline through the universe
    float offset = bandIdx * 0.137f;

    // each band can turn at its own rate, so they slide past one another
    float spin = sSpinPos * (1f + sShear * bandIdx * 0.6f);
    float x = along + sDriftPos + spin + offset;
    float val = specAt(x);

    if (sGlow > 0f) {
      float s = 1.5f / SPEC;
      float b = (specAt(x - s) + specAt(x + s)) * 0.5f;
      val = val + (b - val) * -sGlow * 0.35f + val * sGlow * 0.25f;
    }

    if (sNoise > 0f) {
      float n = fsin(x * 811.7f + bandIdx * 13.1f) * 0.5f + 0.5f;
      val *= 1f - sNoise * 0.35f * n;
    }

    // brightness tapers at the top and bottom of each band so it reads as a
    // trace rather than a solid block
    float shape = fsin(3.14159265f * clamp01((within - g) / Math.max(1e-4f, 1f - 2f * g)));
    float bri = clamp01(val) * (0.35f + 0.65f * shape);

    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    float pos = x - (float) Math.floor(x);
    float h = sHue + sHueRng * pos;
    h = ((h % 360f) + 360f) % 360f;
    float s = sSat + sSatRng * pos;

    return LXColor.hsb(h, clamp01(s / 100f) * 100f, clamp01(bri) * 100f);
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
