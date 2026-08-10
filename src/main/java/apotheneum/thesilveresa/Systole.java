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

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Systole")
public class Systole extends ApotheneumPattern {

  // Rhythm

  private final CompoundParameter bpm = new CompoundParameter("BPM", 62, 30, 140)
    .setDescription("Heart rate");
  private final CompoundParameter variability = new CompoundParameter("HRV", 12, 0, 100)
    .setDescription("Beat to beat variation - a real heart is never metronomic");
  private final CompoundParameter respiratory = new CompoundParameter("Breath", 25, 0, 100)
    .setDescription("Respiratory sinus arrhythmia - the rate rises and falls with breathing");

  // Waveform - the classic PQRST complex

  private final CompoundParameter rHeight = new CompoundParameter("R", 70, 0, 100)
    .setDescription("Height of the R spike");
  private final CompoundParameter pWave = new CompoundParameter("P", 22, 0, 100)
    .setDescription("Atrial depolarisation, the small bump before");
  private final CompoundParameter tWave = new CompoundParameter("T", 34, 0, 100)
    .setDescription("Ventricular repolarisation, the broad bump after");
  private final CompoundParameter qsDepth = new CompoundParameter("QS", 30, 0, 100)
    .setDescription("Depth of the Q and S deflections");
  private final CompoundParameter complexW = new CompoundParameter("Width", 40, 5, 100)
    .setDescription("Width of the QRS complex");

  // Trace

  private final CompoundParameter sweepMode = new CompoundParameter("Sweep", 100, 0, 100)
    .setDescription("0 scrolls the trace, 100 sweeps a cursor like a monitor");
  private final CompoundParameter speed = new CompoundParameter("Speed", 35, 5, 100)
    .setDescription("How fast the trace travels");
  private final CompoundParameter thick = new CompoundParameter("Thick", 30, 5, 100)
    .setDescription("Trace thickness");
  private final CompoundParameter coreWhite = new CompoundParameter("CoreWht", 85, 0, 100)
    .setDescription("How white the core burns - the glow keeps the hue");

  private final CompoundParameter halo = new CompoundParameter("Halo", 55, 0, 100)
    .setDescription("Soft glow spreading either side of the trace");
  private final CompoundParameter haloW = new CompoundParameter("HaloW", 45, 0, 100)
    .setDescription("How far the glow reaches");

  private final CompoundParameter persist = new CompoundParameter("Persist", 55, 0, 100)
    .setDescription("Phosphor persistence behind the cursor");
  private final CompoundParameter baseline = new CompoundParameter("Base", 50, 0, 100)
    .setDescription("Vertical position of the trace");
  private final CompoundParameter amplitude = new CompoundParameter("Amp", 60, 5, 100)
    .setDescription("Overall vertical scale");
  private final CompoundParameter grid = new CompoundParameter("Grid", 0, 0, 100)
    .setDescription("Faint monitor graticule behind the trace");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 145, 0, 360)
    .setDescription("Trace hue");
  private final CompoundParameter hueSpike = new CompoundParameter("HueR", 145, 0, 360)
    .setDescription("Hue at the R spike");
  private final CompoundParameter satTrace = new CompoundParameter("Sat", 55, 0, 100)
    .setDescription("Saturation");
  private final CompoundParameter bright = new CompoundParameter("Bright", 88, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");
  private final BooleanParameter vertical = new BooleanParameter("Vert", false)
    .setDescription("Run the trace vertically");

  // The trace's height depends only on how far along the axis you are, so it
  // was being recomputed for every row of every column - 45 times more often
  // than needed, at five Math.exp each. Now built once per frame.
  private static final int TRACE = 1024;
  private final float[] traceY = new float[TRACE];
  private final float[] traceKeep = new float[TRACE];

  private float phase = 0f;      // position within the current beat, 0..1
  private float cursor = 0f;     // where the sweep is
  private float beatLen = 1f;
  private float breathPh = 0f;
  private float lastBeatJitter = 0f;

  private float sR, sP, sT, sQS, sW, sAmp, sBase, sThick, sPersist, sGrid, sHalo, sHaloW;
  private float sCoreWht, coreness;
  private float sSweep, sBr, sBk, sHue, sHueR, sSat;
  private boolean sVert;

  public Systole(LX lx) {
    super(lx);
    addParameter("BPM",     this.bpm);
    addParameter("HRV",     this.variability);
    addParameter("Breath",  this.respiratory);
    addParameter("R",       this.rHeight);
    addParameter("P",       this.pWave);
    addParameter("T",       this.tWave);
    addParameter("QS",      this.qsDepth);
    addParameter("Width",   this.complexW);
    addParameter("Sweep",   this.sweepMode);
    addParameter("Speed",   this.speed);
    addParameter("Thick",   this.thick);
    addParameter("CoreWht", this.coreWhite);
    addParameter("Halo",    this.halo);
    addParameter("HaloW",   this.haloW);
    addParameter("Persist", this.persist);
    addParameter("Base",    this.baseline);
    addParameter("Amp",     this.amplitude);
    addParameter("Grid",    this.grid);
    addParameter("Hue",     this.hue);
    addParameter("HueR",    this.hueSpike);
    addParameter("Sat",     this.satTrace);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Repeat",  this.repeat);
    addParameter("Sym",     this.symmetry);
    addParameter("Vert",    this.vertical);
  }

  // One cardiac cycle, t in 0..1. This is the PQRST complex written out.
  private float ecg(float t) {
    t = t - (float) Math.floor(t);
    float w = 0.04f + sW * 0.10f;
    float v = 0f;

    // P wave - atrial depolarisation
    v += sP * gauss(t, 0.16f, w * 1.5f);

    // Q dip, R spike, S dip - the ventricles
    v -= sQS * gauss(t, 0.325f, w * 0.35f);
    v += sR * gauss(t, 0.36f, w * 0.28f);
    v -= sQS * 1.15f * gauss(t, 0.40f, w * 0.40f);

    // T wave - repolarisation, broad and slow
    v += sT * gauss(t, 0.60f, w * 2.6f);

    return v;
  }

  private static float gauss(float t, float mu, float sigma) {
    float d = t - mu;
    return (float) Math.exp(-(d * d) / (2f * sigma * sigma));
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);

    sR   = rHeight.getValuef() / 100f;
    sP   = pWave.getValuef() / 100f;
    sT   = tWave.getValuef() / 100f;
    sQS  = qsDepth.getValuef() / 100f;
    sW   = complexW.getValuef() / 100f;
    sAmp = amplitude.getValuef() / 100f * 0.42f;
    sBase = baseline.getValuef() / 100f;
    sThick = 0.004f + thick.getValuef() / 100f * 0.150f;
    sPersist = persist.getValuef() / 100f;
    sCoreWht = coreWhite.getValuef() / 100f;
    sHalo = halo.getValuef() / 100f;
    sHaloW = 1f + haloW.getValuef() / 100f * 6f;
    sGrid = grid.getValuef() / 100f;
    sSweep = sweepMode.getValuef() / 100f;
    sBr  = bright.getValuef();
    sBk  = black.getValuef() / 100f;
    sHue = hue.getValuef();
    sHueR = hueSpike.getValuef();
    sSat = satTrace.getValuef();
    sVert = vertical.isOn();

    // Respiratory sinus arrhythmia: the rate genuinely rises on inhalation and
    // falls on exhalation. It is the single thing that most makes a simulated
    // heartbeat feel alive rather than mechanical.
    breathPh += dt * 0.22f;
    float breath = respiratory.getValuef() / 100f * 0.12f
                 * (float) Math.sin(breathPh * 6.2832f);

    float rate = bpm.getValuef() * (1f + breath);
    beatLen = 60f / Math.max(20f, rate) * (1f + lastBeatJitter);

    phase += dt / beatLen;
    if (phase >= 1f) {
      phase -= 1f;
      float hrv = variability.getValuef() / 100f * 0.10f;
      lastBeatJitter = (float) ((Math.random() - 0.5) * 2.0 * hrv);
    }

    cursor += dt * (speed.getValuef() / 100f) * 0.42f;
    if (cursor > 1f) cursor -= 1f;

    buildTrace();
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
        colors[row.points[ci].index] = traceAt(u, v);
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
        colors[ring.points[pi].index] = traceAt(u, v);
      }
    }
  }

  private void buildTrace() {
    float beatsAcross = 2.2f;
    boolean sweep = sSweep > 0.5f;
    float decay = 7f - sPersist * 6.2f;
    for (int i = 0; i < TRACE; i++) {
      float along = (float) i / TRACE;
      float tAt;
      float keep = 1f;
      if (sweep) {
        float rel = cursor - along;
        if (rel < 0f) rel += 1f;
        keep = (float) Math.exp(-rel * decay);
        tAt = (along * beatsAcross) - (cursor * beatsAcross) + phase;
      } else {
        tAt = along * beatsAcross + phase;
      }
      traceY[i] = sBase - ecg(tAt) * sAmp;
      traceKeep[i] = keep;
    }
  }

  private int traceAt(float u, float v) {
    float along  = sVert ? v : u;
    float across = sVert ? u : v;

    // Two readings of the same trace. Scroll: the waveform slides past.
    // Sweep: a cursor crosses a static trace, the way a monitor works.
    float fi = along * TRACE;
    int ti = (int) fi;
    if (ti < 0) ti = 0; else if (ti >= TRACE - 1) ti = TRACE - 2;
    float tf = fi - ti;
    float y = traceY[ti] + (traceY[ti + 1] - traceY[ti]) * tf;
    float d = Math.abs(across - y);

    float coreB = 0f, haloB = 0f;
    if (d < sThick) {
      float t = 1f - d / sThick;
      float c = clamp01(t * 2.4f);
      coreB = c * c * (3f - 2f * c);
    }
    if (sHalo > 0f) {
      float hw = sThick * sHaloW;
      if (d < hw) {
        float g = 1f - d / hw;
        // gentler falloff than before, so the bloom actually reads
        haloB = g * g * sHalo * 1.15f;
      }
    }
    // Core and glow are tracked apart so the core can burn white while the
    // bloom around it keeps the hue - which is what makes neon look like neon.
    coreness = coreB;
    float bri = coreB + haloB * (1f - coreB);

    // phosphor persistence: the trace dims behind the cursor
    if (sSweep > 0.5f) {
      bri *= traceKeep[ti];
      // a bright dot at the cursor itself
      float dc = Math.abs(along - cursor);
      if (dc > 0.5f) dc = 1f - dc;
      if (dc < 0.010f && d < sThick * 2.2f) {
        bri = Math.max(bri, (1f - dc / 0.010f) * 0.9f);
      }
    }

    // faint monitor graticule
    if (sGrid > 0f && bri < 0.05f) {
      float gx = Math.abs((along * 20f) % 1f - 0.5f);
      float gy = Math.abs((across * 12f) % 1f - 0.5f);
      float line = Math.max(clamp01(1f - gx * 26f), clamp01(1f - gy * 26f));
      bri = Math.max(bri, line * sGrid * 0.10f);
    }

    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    // the R spike gets its own hue if you want it to flash
    float spike = clamp01((sBase - y) / Math.max(1e-4f, sAmp * 0.6f));
    float h = sHue + (sHueR - sHue) * spike;
    h = ((h % 360f) + 360f) % 360f;

    float sat = sSat * (1f - sCoreWht * coreness);

    return LXColor.hsb(h, clamp01(sat / 100f) * 100f, clamp01(bri) * 100f);
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
