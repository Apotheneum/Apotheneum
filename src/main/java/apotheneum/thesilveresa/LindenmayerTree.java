package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.Apotheneum.Cylinder.Ring;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Lindenmayer Tree")
public class LindenmayerTree extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;
  private static final float DEG    = PI / 180f;

  private static final float FACE_W_PX = 50f;
  private static final float FACE_H_PX = 45f;
  private static final float PAD_X_PX   = 0f;   // pixels left free at left/right
  private static final float PAD_TOP_PX = 0f;   // pixels left free at the top
  private static final float PAD_BOT_PX = 1f;   // trunk base sits off the floor
  private static final float MAX_STRETCH = 1.6f;

  // Lung envelope (mode 1)
  private static final float LUNG_CARINA_Y = 0.20f;   // where the trachea splits
  private static final float LUNG_TRUNK_TOP = -0.10f; // starts off-canvas so Breathe never uncovers the top
  private static final float LUNG_SPREAD = 0.95f;     // initial bronchus angle off vertical
  private static final float LUNG_LEN0 = 0.17f;       // first bronchus length
  // target box for ONE lobe (mirrored for the other); raised to clear the doors
  private static final float LUNG_TX0 = 0.560f, LUNG_TX1 = 0.905f;
  private static final float LUNG_TY0 = 0.175f, LUNG_TY1 = 0.70f;
  private static final float LUNG_APEX_ANG = 0.85f;   // upward fan from the hilum
  private static final float LUNG_TAPER = 0.00f;      // diaphragm taper toward the base
  private static final float LUNG_ASYM = 1.00f;       // left lobe narrower (cardiac notch)
  private static final float LUNG_TIPW = 0.50f;       // gentler width falloff so fine bronchioles survive
  private static final float LUNG_HUE_BIAS = 5.0f;   // >1 keeps most of the tree red, yellow only at tips

  private static final int GRID_W = 128;
  private static final int GRID_H = 128;

  private final float[] gridDist  = new float[GRID_W * GRID_H];
  private final float[] gridDepth = new float[GRID_W * GRID_H];
  private final float[] gridBirth = new float[GRID_W * GRID_H];
  private boolean dirty = true;
  private double sinceRebuild = 1e9;   // ms since last distance-field rebuild

  private static final int MAX_SEG = 1600;

  // Segment bins for the distance field. The search below returns exactly the
  // same nearest segment as scanning all of them, but stops as soon as no
  // unexamined bin could hold anything closer.
  private static final int DFB = 32;
  private final int[] binStart = new int[DFB * DFB + 1];
  private final int[] binCount = new int[DFB * DFB];
  private int[] binItems = new int[MAX_SEG * 6];

  private void buildSegmentBins() {
    java.util.Arrays.fill(binCount, 0);
    for (int s = 0; s < segCount; s++) {
      int x0 = binIdx(Math.min(segX0[s], segX1[s]));
      int x1 = binIdx(Math.max(segX0[s], segX1[s]));
      int y0 = binIdx(Math.min(segY0[s], segY1[s]));
      int y1 = binIdx(Math.max(segY0[s], segY1[s]));
      for (int by = y0; by <= y1; by++)
        for (int bx = x0; bx <= x1; bx++) binCount[by * DFB + bx]++;
    }
    int run = 0;
    for (int i = 0; i < DFB * DFB; i++) { binStart[i] = run; run += binCount[i]; }
    binStart[DFB * DFB] = run;
    if (binItems.length < run) binItems = new int[run];
    int[] fill = new int[DFB * DFB];
    for (int s = 0; s < segCount; s++) {
      int x0 = binIdx(Math.min(segX0[s], segX1[s]));
      int x1 = binIdx(Math.max(segX0[s], segX1[s]));
      int y0 = binIdx(Math.min(segY0[s], segY1[s]));
      int y1 = binIdx(Math.max(segY0[s], segY1[s]));
      for (int by = y0; by <= y1; by++)
        for (int bx = x0; bx <= x1; bx++) {
          int b = by * DFB + bx;
          binItems[binStart[b] + fill[b]++] = s;
        }
    }
  }

  private static int binIdx(float v) {
    int i = (int) (v * DFB);
    return i < 0 ? 0 : (i >= DFB ? DFB - 1 : i);
  }
  private final float[] segX0 = new float[MAX_SEG];
  private final float[] segY0 = new float[MAX_SEG];
  private final float[] segX1 = new float[MAX_SEG];
  private final float[] segY1 = new float[MAX_SEG];
  private final float[] segDepth = new float[MAX_SEG];
  private final float[] segBirth = new float[MAX_SEG];
  private final float[] segLen = new float[MAX_SEG];
  private float dpsT;   // projection param from the last distPointSeg call
  private boolean lungMask = false;
  private int   segCount = 0;
  private float maxBirth = 1f;
  private int   maxDepthUsed = 1;

  // ─── Trig LUT ────────────────────────────────────────────────────────────
  private static final int     LUT       = 1024;
  private static final float   LUT_SCALE = LUT / TWO_PI;
  private static final float[] SINL      = new float[LUT];
  private static final float[] COSL      = new float[LUT];
  static {
    for (int i = 0; i < LUT; i++) {
      float a = TWO_PI * i / LUT;
      SINL[i] = (float) Math.sin(a);
      COSL[i] = (float) Math.cos(a);
    }
  }

  // ─── Parameters ──────────────────────────────────────────────────────────

  private final CompoundParameter mode = new CompoundParameter("Mode", 0, 0, 3)
    .setDescription("0=First Branch, 1=Bronchial, 2=River Delta, 3=Neural Forest");
  private final CompoundParameter angle = new CompoundParameter("Angle", 25, 5, 60)
    .setDescription("Base branch angle (degrees)");
  private final CompoundParameter ratio = new CompoundParameter("Ratio", 70, 45, 92)
    .setDescription("Child/parent length ratio x100");
  private final CompoundParameter depth = new CompoundParameter("Depth", 7, 3, 9)
    .setDescription("Recursion depth (structural)");
  private final CompoundParameter chaos = new CompoundParameter("Chaos", 20, 0, 100)
    .setDescription("Angular + length jitter x100");
  private final CompoundParameter growth = new CompoundParameter("Growth", 100, 0, 100)
    .setDescription("Growth reveal x100 (0=seed, 100=full canopy)");
  private final CompoundParameter thick = new CompoundParameter("Thick", 45, 10, 100)
    .setDescription("Branch thickness x100");
  private final CompoundParameter repeat = new CompoundParameter("Repeat", 3, 1, 8)
    .setDescription("Trees around cylinder ring");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", true);
  private final CompoundParameter reflect = new CompoundParameter("Reflect", 0, 0, 100)
    .setDescription("Mirror reflection across the y-axis x100");
  private final CompoundParameter breathe = new CompoundParameter("Breathe", 0, 0, 100)
    .setDescription("Breathing amplitude x100");
  private final CompoundParameter breatheRate = new CompoundParameter("BrRte", 20, 5, 100)
    .setDescription("Breathing rate x100");
  private final CompoundParameter hueTrunk = new CompoundParameter("HueT", 30, 0, 360)
    .setDescription("Trunk hue");
  private final CompoundParameter hueTip = new CompoundParameter("HueTip", 140, 0, 360)
    .setDescription("Tip hue");
  private final CompoundParameter sat = new CompoundParameter("Sat", 70, 0, 100)
    .setDescription("Saturation");
  private final CompoundParameter glow = new CompoundParameter("Glow", 40, 0, 100)
    .setDescription("Halo / glow around branches x100");
  private final CompoundParameter bright = new CompoundParameter("Bright", 95, 20, 100)
    .setDescription("Output brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 6, 0, 40)
    .setDescription("Black threshold x100");
  private final CompoundParameter seed = new CompoundParameter("Seed", 0, 0, 1000)
    .setDescription("Random seed (change to regrow)");

  private float time = 0f;
  private float prevSeed = -1f;

  // Frame-constant scratch (set each render, read by sample methods).
  private float sGr, sW, sGlowA, sHT, sHTip, sSt, sBr, sBk, sScale, sReflect;

  public LindenmayerTree(LX lx) {
    super(lx);
    addParameter("Mode",    this.mode);
    addParameter("Angle",   this.angle);
    addParameter("Ratio",   this.ratio);
    addParameter("Depth",   this.depth);
    addParameter("Chaos",   this.chaos);
    addParameter("Growth",  this.growth);
    addParameter("Thick",   this.thick);
    addParameter("Repeat",  this.repeat);
    addParameter("Sym",     this.symmetry);
    this.symmetry.setDescription("Mirror alternate faces / tiles for continuous symmetry");
    addParameter("Reflect", this.reflect);
    addParameter("Breathe", this.breathe);
    addParameter("BrRte",   this.breatheRate);
    addParameter("HueT",    this.hueTrunk);
    addParameter("HueTip",  this.hueTip);
    addParameter("Sat",     this.sat);
    addParameter("Glow",    this.glow);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Seed",    this.seed);
  }

  @Override
  public void onParameterChanged(heronarts.lx.parameter.LXParameter p) {
    if (p == mode || p == angle || p == ratio || p == depth || p == chaos) {
      dirty = true;
    }
  }

  // ─── Main render ─────────────────────────────────────────────────────────

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0);

    float seedVal = seed.getValuef();
    sinceRebuild += deltaMs;
    boolean needRebuild = dirty || Math.abs(seedVal - prevSeed) > 0.01f;
    if (needRebuild && sinceRebuild >= 80.0) {
      generate((int) seedVal);
      buildDistanceField();
      prevSeed = seedVal;
      dirty = false;
      sinceRebuild = 0.0;
    }

    sGr      = growth.getValuef() / 100f;
    sW       = thick.getValuef() / 100f * 0.045f;
    sGlowA   = glow.getValuef() / 100f;
    sHT      = hueTrunk.getValuef();
    sHTip    = hueTip.getValuef();
    sSt      = sat.getValuef();
    sBr      = bright.getValuef();
    sBk      = black.getValuef() / 100f;
    sReflect = reflect.getValuef() / 100f;

    float bAmt  = breathe.getValuef() / 100f * 0.10f;
    float bRate = breatheRate.getValuef() / 100f * 4f;
    sScale = 1f - bAmt * fsin(time * bRate);

    renderCube();
    renderCylinder();
  }

  // ─── L-system generation ─────────────────────────────────────────────────

  private void generate(int randSeed) {
    segCount = 0;
    maxBirth = 0.0001f;
    maxDepthUsed = 1;

    int   md      = clampi(Math.round(mode.getValuef()), 0, 3);
    float angRad  = angle.getValuef() * DEG;
    float rat     = ratio.getValuef() / 100f;
    int   maxD    = clampi((int) depth.getValuef(), 3, 9);
    float ch      = chaos.getValuef() / 100f;

    java.util.Random rng = new java.util.Random((long) randSeed * 2654435761L + md);

    lungMask = (md == 1);
    if (lungMask) {
      buildLungs(maxD, angRad, rat, rng, ch);
      if (maxBirth < 0.0001f) maxBirth = 0.0001f;
      return;
    }

    float rootX, rootY, rootAng, len0;
    if (md == 2) {           // Delta: source up (mountains), fans down to the sea
      rootX = 0.5f; rootY = 0.06f; rootAng = PI; len0 = 0.20f;
    } else if (md == 3) {    // Neural: soma low-center, wide fan
      rootX = 0.5f; rootY = 0.82f; rootAng = 0f; len0 = 0.15f;
    } else {                 // First Branch / Bronchial: upright from the floor
      rootX = 0.5f; rootY = 0.92f; rootAng = 0f; len0 = 0.22f;
    }

    grow(rootX, rootY, rootAng, len0, 0, 0f, maxD, md, angRad, rat, ch, rng);
    if (maxBirth < 0.0001f) maxBirth = 0.0001f;

    fitToFrame();
  }

  // scale + recenter segments to fill the frame
  private void fitToFrame() {
    if (segCount == 0) return;
    float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
    float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    for (int s = 0; s < segCount; s++) {
      minX = Math.min(minX, Math.min(segX0[s], segX1[s]));
      maxX = Math.max(maxX, Math.max(segX0[s], segX1[s]));
      minY = Math.min(minY, Math.min(segY0[s], segY1[s]));
      maxY = Math.max(maxY, Math.max(segY0[s], segY1[s]));
    }
    float spanX = Math.max(maxX - minX, 1e-4f);
    float spanY = Math.max(maxY - minY, 1e-4f);

    float tx0 = PAD_X_PX / FACE_W_PX,   tx1 = 1f - PAD_X_PX / FACE_W_PX;
    float ty0 = PAD_TOP_PX / FACE_H_PX, ty1 = 1f - PAD_BOT_PX / FACE_H_PX;

    float scX = (tx1 - tx0) / spanX;
    float scY = (ty1 - ty0) / spanY;

    // cap anisotropy so branch angles stay plausible
    if (scX > scY * MAX_STRETCH) scX = scY * MAX_STRETCH;
    if (scY > scX * MAX_STRETCH) scY = scX * MAX_STRETCH;

    float cx = (minX + maxX) * 0.5f, cy = (minY + maxY) * 0.5f;
    float dcx = (tx0 + tx1) * 0.5f,  dcy = (ty0 + ty1) * 0.5f;
    for (int s = 0; s < segCount; s++) {
      segX0[s] = dcx + (segX0[s] - cx) * scX;
      segY0[s] = dcy + (segY0[s] - cy) * scY;
      segX1[s] = dcx + (segX1[s] - cx) * scX;
      segY1[s] = dcy + (segY1[s] - cy) * scY;
    }
  }


  private void grow(float x, float y, float ang, float len,
                    int d, float birth, int maxD, int md,
                    float angRad, float rat, float ch, java.util.Random rng) {
    if (d > maxD || len < 0.008f || segCount >= MAX_SEG) return;

    float nx = x + fsin(ang) * len;
    float ny = y - fcos(ang) * len;   // ang=0 -> straight up (v decreases)


    int idx = segCount++;
    segX0[idx] = x;  segY0[idx] = y;
    segX1[idx] = nx; segY1[idx] = ny;
    segDepth[idx] = d;
    segBirth[idx] = birth;
    segLen[idx] = len;
    float childBirth = birth + len;
    if (childBirth > maxBirth) maxBirth = childBirth;
    if (d > maxDepthUsed) maxDepthUsed = d;

    float cr = len * rat;

    switch (md) {
      case 0: // First Branch - clean recursive binary bifurcation (sparse)
        grow(nx, ny, ang - angRad + j(ch, angRad, rng), cr, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad + j(ch, angRad, rng), cr, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        break;
      case 1: // Bronchial - dichotomous, asymmetric major/minor bronchi
        grow(nx, ny, ang - angRad * 0.78f + j(ch, angRad, rng), cr,         d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad * 1.15f + j(ch, angRad, rng), cr * 0.76f, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        break;
      case 2: // Delta - wide distributaries fanning downward + a main channel
        grow(nx, ny, ang - angRad * 1.7f + j(ch, angRad, rng), cr * 1.02f, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        grow(nx, ny, ang - angRad * 0.5f + j(ch, angRad, rng), cr * 0.88f, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad * 0.5f + j(ch, angRad, rng), cr * 0.88f, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad * 1.7f + j(ch, angRad, rng), cr * 1.02f, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        break;
      default: // Neural - dense dendritic fan
        grow(nx, ny, ang - angRad        + j(ch, angRad, rng), cr,         d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        grow(nx, ny, ang - angRad * 0.3f + j(ch, angRad, rng), cr * 0.85f, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad * 0.4f + j(ch, angRad, rng), cr * 0.85f, d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        grow(nx, ny, ang + angRad        + j(ch, angRad, rng), cr,         d + 1, childBirth, maxD, md, angRad, rat, ch, rng);
        break;
    }
  }

  // lung silhouette in UV: tapered apex, broad base, mediastinal gap down the
  // middle and a cardiac notch on the viewer-right lobe


  // one lobe grown then mirrored, with an explicit trachea and carina
  private void buildLungs(int maxD, float angRad, float rat, java.util.Random rng, float ch) {
    // trachea: top edge down to the carina
    addSeg(0.5f, LUNG_TRUNK_TOP, 0.5f, LUNG_CARINA_Y, 0f, 0f, LUNG_CARINA_Y - LUNG_TRUNK_TOP);

    // grow one lobe, then fit it into its target box (this is what fills the
    // lobe densely); the fit moves the lobe, so an explicit connector is added
    // afterwards to keep it attached to the carina
    int lobeStart = segCount;
    grow(0.5f, LUNG_CARINA_Y, PI - LUNG_SPREAD, LUNG_LEN0, 2, LUNG_CARINA_Y,
         maxD, 1, angRad, rat, ch, rng);
    // bronchi also radiate upward from the hilum, filling the lobe apex
    if (segCount > lobeStart) {
      float hx = segX1[lobeStart], hy = segY1[lobeStart];
      grow(hx, hy, LUNG_APEX_ANG, LUNG_LEN0 * 0.72f, 3, LUNG_CARINA_Y,
           maxD - 1, 1, angRad, rat * 0.98f, ch, rng);
    }

    fitRange(lobeStart, segCount, LUNG_TX0, LUNG_TX1, LUNG_TY0, LUNG_TY1);

    // diaphragm taper: the lobe narrows toward its medial edge as it descends
    for (int t = lobeStart; t < segCount; t++) {
      segX0[t] = taperX(segX0[t], segY0[t]);
      segX1[t] = taperX(segX1[t], segY1[t]);
    }

    // connector from the carina to wherever the fitted lobe now begins
    if (segCount > lobeStart) {
      float rx = segX0[lobeStart], ry = segY0[lobeStart];
      float dx = rx - 0.5f, dy = ry - LUNG_CARINA_Y;
      addSeg(0.5f, LUNG_CARINA_Y, rx, ry, 1f, LUNG_CARINA_Y,
             (float) Math.sqrt(dx * dx + dy * dy));
    }

    // mirror everything except the trachea
    int n = segCount;
    float lat = 1f - LUNG_TX1;
    for (int t = 1; t < n && segCount < MAX_SEG; t++) {
      int i = segCount++;
      segX0[i] = lat + ((1f - segX0[t]) - lat) * LUNG_ASYM; segY0[i] = segY0[t];
      segX1[i] = lat + ((1f - segX1[t]) - lat) * LUNG_ASYM; segY1[i] = segY1[t];
      segDepth[i] = segDepth[t]; segBirth[i] = segBirth[t]; segLen[i] = segLen[t];
    }
  }

  private float taperX(float x, float y) {
    float t = clamp01((y - LUNG_TY0) / (LUNG_TY1 - LUNG_TY0));
    return LUNG_TX0 + (x - LUNG_TX0) * (1f - LUNG_TAPER * t * t);
  }

  private void fitRange(int from, int to, float tx0, float tx1, float ty0, float ty1) {
    if (to <= from) return;
    float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
    float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    for (int t = from; t < to; t++) {
      minX = Math.min(minX, Math.min(segX0[t], segX1[t]));
      maxX = Math.max(maxX, Math.max(segX0[t], segX1[t]));
      minY = Math.min(minY, Math.min(segY0[t], segY1[t]));
      maxY = Math.max(maxY, Math.max(segY0[t], segY1[t]));
    }
    float scX = (tx1 - tx0) / Math.max(maxX - minX, 1e-4f);
    float scY = (ty1 - ty0) / Math.max(maxY - minY, 1e-4f);
    float cx = (minX + maxX) * 0.5f, cy = (minY + maxY) * 0.5f;
    float dcx = (tx0 + tx1) * 0.5f, dcy = (ty0 + ty1) * 0.5f;
    for (int t = from; t < to; t++) {
      segX0[t] = dcx + (segX0[t] - cx) * scX;  segY0[t] = dcy + (segY0[t] - cy) * scY;
      segX1[t] = dcx + (segX1[t] - cx) * scX;  segY1[t] = dcy + (segY1[t] - cy) * scY;
    }
  }


  private void addSeg(float x0, float y0, float x1, float y1, float d, float birth, float len) {
    if (segCount >= MAX_SEG) return;
    int i = segCount++;
    segX0[i] = x0; segY0[i] = y0; segX1[i] = x1; segY1[i] = y1;
    segDepth[i] = d; segBirth[i] = birth; segLen[i] = len;
    if (birth + len > maxBirth) maxBirth = birth + len;
    if (d > maxDepthUsed) maxDepthUsed = (int) d;
  }

  private float j(float ch, float angRad, java.util.Random rng) {
    return (rng.nextFloat() - 0.5f) * 2f * ch * angRad;
  }

  // ─── Distance field ──────────────────────────────────────────────────────

  private void buildDistanceField() {
    buildSegmentBins();
    float invW = 1f / GRID_W, invH = 1f / GRID_H;
    for (int gy = 0; gy < GRID_H; gy++) {
      float py = (gy + 0.5f) * invH;
      for (int gx = 0; gx < GRID_W; gx++) {
        float px = (gx + 0.5f) * invW;
        float best = Float.MAX_VALUE, bestDepth = 0f, bestBirth = maxBirth;
        int cbx = binIdx(px), cby = binIdx(py);
        for (int ring = 0; ring < DFB; ring++) {
          // everything outside this ring is at least this far away
          if (ring > 0 && best <= (ring - 1) * (1f / DFB)) break;
          int lo = cbx - ring, hi = cbx + ring, lo2 = cby - ring, hi2 = cby + ring;
          for (int by = lo2; by <= hi2; by++) {
            if (by < 0 || by >= DFB) continue;
            boolean edgeRow = (by == lo2 || by == hi2);
            for (int bx = lo; bx <= hi; bx++) {
              if (bx < 0 || bx >= DFB) continue;
              if (!edgeRow && bx != lo && bx != hi) continue;
              int b = by * DFB + bx;
              for (int k = binStart[b], e = binStart[b] + binCount[b]; k < e; k++) {
                int s = binItems[k];
                float d = distPointSeg(px, py, segX0[s], segY0[s], segX1[s], segY1[s]);
                if (d < best) { best = d; bestDepth = segDepth[s]; bestBirth = segBirth[s] + dpsT * segLen[s]; }
              }
            }
          }
        }
        int cell = gy * GRID_W + gx;
        gridDist[cell]  = best;
        gridDepth[cell] = bestDepth;
        gridBirth[cell] = bestBirth;
      }
    }
  }

  private float distPointSeg(float px, float py,
                                    float ax, float ay, float bx, float by) {
    float abx = bx - ax, aby = by - ay;
    float apx = px - ax, apy = py - ay;
    float ab2 = abx * abx + aby * aby;
    float t = (ab2 > 1e-9f) ? (apx * abx + apy * aby) / ab2 : 0f;
    if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
    dpsT = t;
    float cx = ax + t * abx, cy = ay + t * aby;
    float dx = px - cx, dy = py - cy;
    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  // ─── Sampling ────────────────────────────────────────────────────────────

  // breathing + reflection, then sample the grid
  private int sampleFinal(float u, float v) {
    // Breathing scale about center.
    float bu = 0.5f + (u - 0.5f) / sScale;
    float bv = 0.5f + (v - 0.5f) / sScale;

    int c1 = sampleGrid(bu, bv, 1f);
    if (sReflect <= 0.001f) return c1;
    int c2 = sampleGrid(1f - bu, bv, sReflect);
    return (LXColor.b(c1) >= LXColor.b(c2)) ? c1 : c2;
  }

  private int sampleGrid(float u, float v, float briScale) {
    // the trunk continues above the frame; Breathe contraction samples past the
    // top edge, so clamp there instead of going black
    if (lungMask && v < 0f) v = 0f;
    if (u < 0f || u > 1f || v < 0f || v > 1f) return LXColor.BLACK;

    float fx = u * (GRID_W - 1), fy = v * (GRID_H - 1);
    int x0 = (int) fx, y0 = (int) fy;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (x1 >= GRID_W) x1 = GRID_W - 1;
    if (y1 >= GRID_H) y1 = GRID_H - 1;
    float tx = fx - x0, ty = fy - y0;
    int b00 = y0 * GRID_W + x0, b10 = y0 * GRID_W + x1;
    int b01 = y1 * GRID_W + x0, b11 = y1 * GRID_W + x1;

    float dist   = bilerp(gridDist[b00],  gridDist[b10],  gridDist[b01],  gridDist[b11],  tx, ty);
    float depthF = bilerp(gridDepth[b00], gridDepth[b10], gridDepth[b01], gridDepth[b11], tx, ty);
    float birth  = bilerp(gridBirth[b00], gridBirth[b10], gridBirth[b01], gridBirth[b11], tx, ty);

    float birthNorm = birth / maxBirth;
    if (birthNorm > sGr) return LXColor.BLACK;

    float depthNorm = depthF / Math.max(1, maxDepthUsed);

    float localW = sW * (1f - (lungMask ? LUNG_TIPW : 0.6f) * depthNorm);
    if (localW < 0.004f) localW = 0.004f;

    float core = clamp01(1f - dist / localW);
    float glowW = localW * (1f + 5f * sGlowA);
    float halo  = clamp01(1f - dist / glowW) * 0.55f * sGlowA;
    float lineBri = core > halo ? core : halo;
    if (lineBri < sBk) return LXColor.BLACK;

    float front = clamp01(1f - (sGr - birthNorm) * 5f);
    float brightness = lineBri * (0.7f + 0.3f * front) * sBr * briScale;
    if (brightness < sBk * sBr) return LXColor.BLACK;

    float hueT = depthNorm;
    if (lungMask) hueT = (float) Math.pow(depthNorm, LUNG_HUE_BIAS);
    float hue = sHT + (sHTip - sHT) * hueT;
    hue = ((hue % 360f) + 360f) % 360f;
    float s = sSt * (1f - 0.25f * front);
    return LXColor.hsb(hue, s, brightness);
  }

  // ─── Cube rendering ──────────────────────────────────────────────────────

  private void renderCube() {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    boolean sym = symmetry.isOn();
    Face[] ext = cube.exterior.faces;
    for (int f = 0; f < ext.length; f++)
      renderCubeFace(ext[f], sym && ((f & 1) == 1));
    if (cube.interior != null) {
      Face[] in = cube.interior.faces;
      for (int f = 0; f < in.length; f++)
        renderCubeFace(in[f], sym && ((f & 1) == 1));
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
        colors[row.points[ci].index] = sampleFinal(u, v);
      }
    }
  }

  // ─── Cylinder rendering ──────────────────────────────────────────────────

  private void renderCylinder() {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    renderCylOrientation(cyl.exterior);
    if (cyl.interior != null) renderCylOrientation(cyl.interior);
  }

  private void renderCylOrientation(Cylinder.Orientation o) {
    Ring[] rings = o.rings;
    int numRings = rings.length;
    boolean sym = symmetry.isOn();
    int rep = clampi((int) repeat.getValuef(), 1, 8);
    // Alternate-mirror tiling needs an even tile count to wrap seamlessly.
    if (sym) rep = Math.max(2, (rep / 2) * 2);

    for (int ri = 0; ri < numRings; ri++) {
      Ring ring = rings[ri];
      int np = ring.points.length;
      float v = (float) ri / (numRings - 1);
      for (int pi = 0; pi < np; pi++) {
        float g = (float) pi / np * rep;
        float u;
        if (sym) {
          // Triangle fold: 0->1->0->1… - alternate tiles mirror, seamless on a ring.
          float gg = g - 2f * (float) Math.floor(g * 0.5f);   // g mod 2 in [0,2)
          u = gg <= 1f ? gg : 2f - gg;
        } else {
          u = g - (float) Math.floor(g);
        }
        colors[ring.points[pi].index] = sampleFinal(u, v);
      }
    }
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private float fsin(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return SINL[i < 0 ? i + LUT : i]; }
  private float fcos(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return COSL[i < 0 ? i + LUT : i]; }

  private static float bilerp(float c00, float c10, float c01, float c11, float tx, float ty) {
    return lerp(lerp(c00, c10, tx), lerp(c01, c11, tx), ty);
  }
  private static float lerp(float a, float b, float t) { return a + t * (b - a); }
  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }
  private static int   clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
