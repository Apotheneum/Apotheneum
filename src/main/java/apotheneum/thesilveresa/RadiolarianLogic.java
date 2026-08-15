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
//import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Radiolarian Logic")
public class RadiolarianLogic extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;

  // Physical pixel dimensions (from Apotheneum.java constants)
  private static final int   CUBE_COLS  = 50;    // Apotheneum.GRID_WIDTH
  private static final int   CUBE_ROWS  = 45;    // Apotheneum.GRID_HEIGHT
  private static final int   CYL_COLS   = 120;   // Cylinder.Ring.LENGTH
  private static final int   CYL_ROWS   = 43;    // Apotheneum.CYLINDER_HEIGHT

  // Trig LUT
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

  // Nassellaria lateral spine directions - static constants, never recomputed
  private static final float LAT_AX0 =  (float) Math.sin( 40.0 * Math.PI / 180.0);
  private static final float LAT_AY0 = -(float) Math.cos( 40.0 * Math.PI / 180.0);
  private static final float LAT_AX1 =  (float) Math.sin(-40.0 * Math.PI / 180.0);
  private static final float LAT_AY1 = -(float) Math.cos(-40.0 * Math.PI / 180.0);
  private static final float LAT_AX2 =  (float) Math.sin( 140.0 * Math.PI / 180.0);
  private static final float LAT_AY2 = -(float) Math.cos( 140.0 * Math.PI / 180.0);
  private static final float LAT_AX3 =  (float) Math.sin(-140.0 * Math.PI / 180.0);
  private static final float LAT_AY3 = -(float) Math.cos(-140.0 * Math.PI / 180.0);

  // Per-pixel scratch for evalLatSpine - no allocation
  private float latBri = 0f;
  private float latT   = 0f;

  // Parameters

  // Form type x10: 10=Spumellaria, 20=Nassellaria, 30=Colonial
  private final CompoundParameter form = new CompoundParameter("Form", 10, 10, 30)
    .setDescription("Form: 10=Spumellaria  20=Nassellaria  30=Colonial");

  private final CompoundParameter blend = new CompoundParameter("Blend", 0.0, 0.0, 1.0)
    .setDescription("Crossfade to next form type");

  private final CompoundParameter tileCount = new CompoundParameter("Tiles", 3, 1, 6)
    .setDescription("Forms per axis on cube faces (3=3x3 grid per face)");

  // Body radius in pixel units x10 (avoids 2dp truncation).
  // Body=120 -> radius = 12.0 pixels on both cube and cylinder.
  // Cube face is 50x45px, cylinder is 120x43px.
  private final CompoundParameter bodySize = new CompoundParameter("Body", 120, 20, 200)
    .setDescription("Body radius in pixels x10 (120=12px, looks same size on cube and cylinder)");

  // Spicule length relative to body radius x10. 18 -> 1.8x body radius.
  private final CompoundParameter spicLen = new CompoundParameter("SpLen", 18, 5, 30)
    .setDescription("Spicule length x10 relative to body radius");

  // Spicule sharpness - higher = thinner spikes
  private final CompoundParameter spicSharp = new CompoundParameter("SpShp", 14, 5, 30)
    .setDescription("Spicule taper sharpness");

  private final CompoundParameter meshDepth = new CompoundParameter("Mesh", 55, 0, 100)
    .setDescription("Geodesic mesh depth (0=smooth sphere)");

  // Ornamental ring strength 0-100
  private final CompoundParameter ringStr = new CompoundParameter("Ring", 40, 0, 100)
    .setDescription("Ornamental ring intensity");

  // Hero form rotation speed on cylinder x200
  private final CompoundParameter rotSpeed = new CompoundParameter("RotSp", 15, 0, 100)
    .setDescription("Hero form cylinder rotation speed x200");

  // Hard-coded spicule reset angle (fraction of a full turn x100)
  private static final float LOCK_SP_ANG = 50f;

  private final BooleanParameter lockSp = new BooleanParameter("LockSp", false)
    .setDescription("Reset spicule spin to a fixed angle, then continue from there");
  private final BooleanParameter lockRo = new BooleanParameter("LockRo", false)
    .setDescription("Reset circumference position to LockRoAng, then continue from there");
  private final CompoundParameter lockRoAng = new CompoundParameter("LockRoAng", 50, 0, 100)
    .setDescription("Circumference reset position x100 (fraction of a full turn)");

  private final CompoundParameter phaseVar = new CompoundParameter("Phase", 60, 0, 100)
    .setDescription("Phase variation across field (0=identical, 100=fully varied)");

  private final BooleanParameter breathe = new BooleanParameter("Breathe", true)
    .setDescription("Slow pulsation of form size");

  private final CompoundParameter breatheRate = new CompoundParameter("BrRte", 4, 1, 20)
    .setDescription("Breathing rate x10 Hz");

  private final CompoundParameter hueCore = new CompoundParameter("HueC", 45.0, 0.0, 360.0)
    .setDescription("Core hue - bright body center (~45=gold, ~15=orange)");

  private final CompoundParameter hueMid = new CompoundParameter("HueM", 185.0, 0.0, 360.0)
    .setDescription("Mid hue - body surface and mesh (~185=cyan)");

  private final CompoundParameter hueSpic = new CompoundParameter("HueS", 270.0, 0.0, 360.0)
    .setDescription("Spicule tip hue (~270=violet)");

  private final CompoundParameter spicGlow = new CompoundParameter("Glow", 18, 10, 30)
    .setDescription("Spicule brightness boost x10");

  private final CompoundParameter satBody = new CompoundParameter("SatB", 70.0, 0.0, 100.0)
    .setDescription("Body saturation");

  private final CompoundParameter satSpic = new CompoundParameter("SatS", 95.0, 0.0, 100.0)
    .setDescription("Spicule saturation");

  private final CompoundParameter blackThresh = new CompoundParameter("Black", 5, 0, 30)
    .setDescription("Black threshold x100");

  private final CompoundParameter bright = new CompoundParameter("Bright", 90.0, 20.0, 100.0)
    .setDescription("Output brightness");

  // State
  private float time = 0f;
  private float spicPhase = 0f;      // spicule spin phase fed to the forms
  private float lockSpRefTime = 0f;  // time captured when the spin lock engaged
  private boolean lockSpPrev = false;
  private float lockRoRefTime = 0f;  // time captured when the position lock engaged
  private boolean lockRoPrev = false;

  // Constructor

  public RadiolarianLogic(LX lx) {
    super(lx);
    addParameter("Form",    this.form);
    addParameter("Blend",   this.blend);
    addParameter("Tiles",   this.tileCount);
    addParameter("Body",    this.bodySize);
    addParameter("SpLen",   this.spicLen);
    addParameter("SpShp",   this.spicSharp);
    addParameter("Mesh",    this.meshDepth);
    addParameter("Ring",    this.ringStr);
    addParameter("RotSp",   this.rotSpeed);
    addParameter("LockSp",  this.lockSp);
    addParameter("LockRo",  this.lockRo);
    addParameter("LockRoAng", this.lockRoAng);
    addParameter("Phase",   this.phaseVar);
    addParameter("Breathe", this.breathe);
    addParameter("BrRte",   this.breatheRate);
    addParameter("HueC",    this.hueCore);
    addParameter("HueM",    this.hueMid);
    addParameter("HueS",    this.hueSpic);
    addParameter("Glow",    this.spicGlow);
    addParameter("SatB",    this.satBody);
    addParameter("SatS",    this.satSpic);
    addParameter("Black",   this.blackThresh);
    addParameter("Bright",  this.bright);
  }

  // Main render

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0);

    // Spicule self-rotation phase. Unlocked: tracks time exactly (unchanged
    // behavior). Locked: snaps to the fixed angle when engaged, then keeps
    // advancing at the same rate from that angle onward.
    boolean lkSp = lockSp.isOn();
    if (lkSp && !lockSpPrev) {
      lockSpRefTime = time;
    }
    lockSpPrev = lkSp;
    spicPhase = lkSp
      ? (LOCK_SP_ANG / 100f) * TWO_PI / 0.05f + (time - lockSpRefTime)
      : time;

    // Circumference position phase (turns). Unlocked: tracks time*RotSp exactly.
    // Locked: snaps to LockRoAng when engaged, then continues at the same rate.
    boolean lkRo = lockRo.isOn();
    if (lkRo && !lockRoPrev) {
      lockRoRefTime = time;
    }
    lockRoPrev = lkRo;

    float formVal = form.getValuef() / 10f;
    int   formA   = (int) Math.max(1f, Math.min(3f, formVal));
    int   formB   = Math.min(3, formA + 1);
    float blendT  = blend.getValuef();

    float tiles   = Math.max(1f, tileCount.getValuef());
    // Body radius in pixels (display x10)
    float bR      = bodySize.getValuef() / 10f;
    float sLen    = spicLen.getValuef()  / 10f;
    float sSharp  = spicSharp.getValuef();
    float mDepth  = meshDepth.getValuef() / 100f;
    float rStr    = ringStr.getValuef()   / 100f;
    float pVar    = phaseVar.getValuef()  / 100f;
    float glow    = spicGlow.getValuef()  / 10f;
    float roRate  = rotSpeed.getValuef() / 200f;
    float heroRot = lockRo.isOn()
      ? lockRoAng.getValuef() / 100f + (time - lockRoRefTime) * roRate
      : time * roRate;
    float hC      = hueCore.getValuef();
    float hM      = hueMid.getValuef();
    float hS      = hueSpic.getValuef();
    float sB      = satBody.getValuef();
    float sS      = satSpic.getValuef();
    float briV    = bright.getValuef();
    float blkThr  = blackThresh.getValuef() / 100f;

    float breathAmt = 0f;
    if (breathe.getValueb())
      breathAmt = 0.12f * fsin(time * breatheRate.getValuef() / 10f);
    bR *= (1f + breathAmt);
    float bRSq = bR * bR;

    // Param array - index map:
    // 0=bR 1=bRSq 2=sLen 3=sSharp 4=mDepth 5=rStr 6=pVar
    // 7=glow 8=hC 9=hM 10=hS 11=sB 12=sS 13=briV 14=heroRot 15=tiles 16=blkThr
    float[] p = { bR, bRSq, sLen, sSharp, mDepth, rStr, pVar,
                  glow, hC, hM, hS, sB, sS, briV, heroRot, tiles, blkThr };

    renderCube(formA, formB, blendT, p);
    renderCylinder(formA, formB, blendT, p);
  }

  // Cube

  private void renderCube(int fA, int fB, float blendT, float[] p) {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;

    boolean doBlend = blendT >= 0.01f && blendT <= 0.99f;

    for (Face face : cube.exterior.faces)
      renderCubeFace(face, fA, fB, blendT, doBlend, p);
    if (cube.interior != null)
      for (Face face : cube.interior.faces)
        renderCubeFace(face, fA, fB, blendT, doBlend, p);
  }

  private void renderCubeFace(Face face, int fA, int fB, float blendT,
                               boolean doBlend, float[] p) {
    int   cols    = CUBE_COLS;              // 50
    int   rows    = face.rows.length;      // 45
    float tiles   = p[15];

    // Tile size in pixels
    float tilePxW = (float) CUBE_COLS / tiles;
    float tilePxH = (float) CUBE_ROWS / tiles;

    for (int ri = 0; ri < rows; ri++) {
      Row row = face.rows[ri];
      // py: pixel row within the face [0, CUBE_ROWS)
      float py = ri;
      for (int ci = 0; ci < cols; ci++) {
        float px = ci;
        int col;
        if (!doBlend) {
          col = (blendT < 0.01f)
            ? evalFormCube(px, py, fA, tilePxW, tilePxH, p)
            : evalFormCube(px, py, fB, tilePxW, tilePxH, p);
        } else {
          int cA = evalFormCube(px, py, fA, tilePxW, tilePxH, p);
          int cB = evalFormCube(px, py, fB, tilePxW, tilePxH, p);
          col = blendColors(cA, cB, blendT);
        }
        colors[row.points[ci].index] = col;
      }
    }
  }

  // Cylinder

  private void renderCylinder(int fA, int fB, float blendT, float[] p) {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    boolean doBlend = blendT >= 0.01f && blendT <= 0.99f;
    renderCylOrientation(cyl.exterior,  fA, fB, blendT, doBlend, p);
    if (cyl.interior != null)
      renderCylOrientation(cyl.interior, fA, fB, blendT, doBlend, p);
  }

  private void renderCylOrientation(Cylinder.Orientation o, int fA, int fB,
                                     float blendT, boolean doBlend, float[] p) {
    Ring[] rings    = o.rings;
    int    numRings = rings.length;    // 43
    float  heroRot  = p[14];
    // Hero rotation offset in pixels around circumference
    float  rotPx    = heroRot * CYL_COLS;

    for (int ri = 0; ri < numRings; ri++) {
      Ring  ring = rings[ri];
      int   np   = ring.points.length;  // 120
      float py   = ri;                  // pixel row [0,43)
      for (int pi = 0; pi < np; pi++) {
        // px: pixel column with hero rotation applied, range [0,120)
        float px = ((pi + rotPx) % CYL_COLS + CYL_COLS) % CYL_COLS;
        int col;
        if (!doBlend) {
          col = (blendT < 0.01f)
            ? evalFormCyl(px, py, fA, p)
            : evalFormCyl(px, py, fB, p);
        } else {
          int cA = evalFormCyl(px, py, fA, p);
          int cB = evalFormCyl(px, py, fB, p);
          col = blendColors(cA, cB, blendT);
        }
        colors[ring.points[pi].index] = col;
      }
    }
  }

  // Blend

  private static int blendColors(int cA, int cB, float t) {
    float briA = LXColor.b(cA) / 100f * (1f - t);
    float briB = LXColor.b(cB) / 100f * t;
    float total = briA + briB;
    if (total < 0.001f) return LXColor.BLACK;
    float w = briB / total;
    float hue = lerpHue(LXColor.h(cA), LXColor.h(cB), w);
    float sat = lerp(LXColor.s(cA), LXColor.s(cB), w);
    return LXColor.hsb(hue, sat, Math.min(total * 100f, 100f));
  }

  // Form dispatchers

  // Cube form evaluator. px  in  [0, CUBE_COLS), py  in  [0, CUBE_ROWS).
  // Tiles the face into a grid of cells; computes pixel position within each cell.
  private int evalFormCube(float px, float py, int ft,
                            float tilePxW, float tilePxH, float[] p) {
    // Which tile cell?
    int   cellX = (int)(px / tilePxW);
    int   cellY = (int)(py / tilePxH);
    // Pixel position relative to cell center
    float cx    = px - (cellX + 0.5f) * tilePxW;
    float cy    = py - (cellY + 0.5f) * tilePxH;
    // Cell hash for phase variation
    int   hash  = cellX * 1447 + cellY * 2833;
    float cTime = time + p[6] * ((hash & 0xFF) / 255f);
    switch (ft) {
      case 2:  return evalNassellaria(cx, cy, p, cTime, false);
      case 3:  return evalColonial(cx, cy, p, cTime, false, (int)(p[15]));
      default: return evalSpumellaria(cx, cy, p, cTime);
    }
  }

  // Cylinder form evaluator. px  in  [0, CYL_COLS), py  in  [0, CYL_ROWS).
  // Single hero form centered on the cylinder; cx/cy in pixel units from center.
  private int evalFormCyl(float px, float py, int ft, float[] p) {
    // Center of cylinder face in pixel space
    float cx = px - CYL_COLS * 0.5f;
    float cy = py - CYL_ROWS * 0.5f;
    float cTime = spicPhase;  // hero form; lockable via LockSp
    switch (ft) {
      case 2:  return evalNassellaria(cx, cy, p, cTime, true);
      case 3:  return evalColonial(cx, cy, p, cTime, true, 1);
      default: return evalSpumellaria(cx, cy, p, cTime);
    }
  }

  // FORM 1 - Spumellaria
  // All distances in pixel units. bR is in pixels.

  private int evalSpumellaria(float cx, float cy, float[] p, float cTime) {
    float bR = p[0], bRSq = p[1], sLen = p[2], sSharp = p[3];
    float mDepth = p[4], rStr = p[5];
    float glow = p[7], hC = p[8], hM = p[9], hS = p[10];
    float sB = p[11], sS = p[12], briV = p[13], blkThr = p[16];

    float distSq    = cx * cx + cy * cy;
    float spicEnd   = bR * (1f + sLen);
    float spicMaxSq = spicEnd * spicEnd;
    if (distSq > spicMaxSq * 1.1f) return LXColor.BLACK;

    float bNSq    = distSq / bRSq;
    float bodyBri = clamp01(1f - bNSq);
    float angle   = fatan2(cy, cx);
    float sym     = symFoldLUT(angle, 0f);

    float meshMod = 0f;
    if (mDepth > 0f && bNSq < 1.21f) {
      float bN  = (float) Math.sqrt(bNSq);
      meshMod = mDepth * (0.5f * fcos(sym * 5f) + 0.3f * fcos(bN * PI * 2.5f)) * bodyBri;
    }
    float bodyFinal = clamp01(bodyBri + meshMod);

    float ringBri = 0f;
    if (rStr > 0f && bNSq > 0.9f && bNSq < 2.5f) {
      float bN  = (float) Math.sqrt(bNSq);
      float rd  = bN - 1.25f;
      float rEnv = clamp01(1f - (rd * rd) / 0.0225f);
      if (rEnv > 0f) ringBri = rStr * rEnv * (0.5f + 0.5f * fcos(sym * 12f));
    }

    float spicBri = 0f, spicT = 0f;
    float spicBaseSq = bRSq * 0.25f;
    if (distSq > spicBaseSq && distSq < spicMaxSq) {
      float dist = 1f / fastInvSqrt(distSq);
      spicT = clamp01((dist - bR * 0.5f) / (bR * sLen + bR * 0.5f));
      float spicEnv = (1f - spicT) * (1f - spicT);
      float step    = TWO_PI / 12f;
      float norm    = ((angle - cTime * 0.05f) % step + step) % step;
      float angFall = clamp01(1f - Math.min(norm, step - norm) * sSharp / PI);
      spicBri = spicEnv * angFall * angFall;
    }

    return toColor(bodyFinal, bodyBri, ringBri, spicBri, spicT,
                   glow, hC, hM, hS, sB, sS, briV, blkThr);
  }

  // FORM 2 - Nassellaria
  // cx/cy in pixel units from cell/cylinder center.
  // isCyl flag only affects the slow cell rotation (full rotation on cylinder
  // looks better than on the tiled cube field).

  private int evalNassellaria(float cx, float cy, float[] p, float cTime, boolean isCyl) {
    float bR = p[0], bRSq = p[1], sLen = p[2], sSharp = p[3];
    float mDepth = p[4], rStr = p[5];
    float glow = p[7], hC = p[8], hM = p[9], hS = p[10];
    float sB = p[11], sS = p[12], briV = p[13], blkThr = p[16];

    float cellRot = cTime * 0.03f;
    float cosR = fcos(cellRot), sinR = fsin(cellRot);
    float rx = cx * cosR - cy * sinR;
    float ry = cx * sinR + cy * cosR;

    float bodyOffY = bR * 0.7f;
    float bdx = rx, bdy = ry - bodyOffY;
    float bodyDistSq = bdx * bdx + bdy * bdy;
    float spineLen   = sLen * bR * 1.5f;
    float maxExt     = bR + spineLen;

    if (Math.abs(rx) > maxExt * 1.2f || Math.abs(ry + spineLen) > maxExt * 1.5f)
      return LXColor.BLACK;

    float spineBri = 0f, spineT = 0f;
    if (ry < bodyOffY - bR * 0.5f) {
      spineT = clamp01((bodyOffY - bR * 0.5f - ry) / spineLen);
      float spineWidth = bR * 0.08f * (1f - spineT * 0.7f);
      float spineEnv   = clamp01(1f - Math.abs(rx) / Math.max(spineWidth, 0.001f));
      spineBri = spineEnv * spineEnv * (1f - spineT * spineT);
    }

    float bNSq    = bodyDistSq / bRSq;
    float bodyBri = clamp01(1f - bNSq);

    float meshMod = 0f;
    if (mDepth > 0f && bNSq < 1.3f) {
      float bN  = (float) Math.sqrt(bNSq);
      int   ai  = (int)((fatan2(bdy, bdx) + PI) / TWO_PI * LUT) & (LUT - 1);
      float sym = (float)(ai % (LUT / 3)) / (LUT / 3) * (TWO_PI / 3f);
      meshMod = mDepth * (0.5f * fcos(sym * 3f) + 0.3f * fcos(bN * PI * 2f)) * bodyBri;
    }
    float bodyFinal = clamp01(bodyBri + meshMod);

    float latLen = sLen * bR * 0.8f;
    // Lateral spines - writes to latBri/latT scratch fields, no allocation
    float bestLatBri = 0f, bestLatT = 0f;
    evalLatSpine(bdx, bdy, bR, latLen, LAT_AX0, LAT_AY0);
    if (latBri > bestLatBri) { bestLatBri = latBri; bestLatT = latT; }
    evalLatSpine(bdx, bdy, bR, latLen, LAT_AX1, LAT_AY1);
    if (latBri > bestLatBri) { bestLatBri = latBri; bestLatT = latT; }
    evalLatSpine(bdx, bdy, bR, latLen, LAT_AX2, LAT_AY2);
    if (latBri > bestLatBri) { bestLatBri = latBri; bestLatT = latT; }
    evalLatSpine(bdx, bdy, bR, latLen, LAT_AX3, LAT_AY3);
    if (latBri > bestLatBri) { bestLatBri = latBri; bestLatT = latT; }

    float spicBri = Math.max(spineBri, bestLatBri);
    float spicT   = (spineBri >= bestLatBri) ? spineT : bestLatT;

    return toColor(bodyFinal, bodyBri, 0f, spicBri, spicT,
                   glow, hC, hM, hS, sB, sS, briV, blkThr);
  }

  // Writes to latBri and latT scratch fields. No return, no allocation.
  private void evalLatSpine(float bdx, float bdy, float bR, float latLen,
                              float lax, float lay) {
    float t = bdx * lax + bdy * lay;
    if (t <= 0f) { latBri = 0f; latT = 0f; return; }
    float tClamp = t < 1f ? t : 1f;
    float tNorm  = tClamp * bR / latLen;
    if (tNorm >= 1f) { latBri = 0f; latT = 0f; return; }
    float cpx    = tClamp * lax, cpy = tClamp * lay;
    float dSq    = (bdx-cpx)*(bdx-cpx) + (bdy-cpy)*(bdy-cpy);
    float w      = bR * 0.07f * (1f - tClamp * 0.6f);
    float wSq    = w * w;
    if (dSq >= wSq) { latBri = 0f; latT = 0f; return; }
    float env    = wSq > 1e-8f ? 1f - dSq / wSq : 1f;
    if (env > 1f) env = 1f;
    latBri = env * (1f - tNorm);
    latT   = tNorm;
  }

  // FORM 3 - Colonial Cluster
  // cx/cy in pixel units from cell center (cube) or cylinder center.
  // isCyl: true on cylinder - enables toroidal u-wrap across the seam.
  // On cylinder, CYL_COLS = 120 pixels is the wrap width.
  // nTiles passed through for tiled-cube context (not used on cylinder).

  private int evalColonial(float cx, float cy, float[] p, float cTime,
                            boolean isCyl, int nTiles) {
    float bR = p[0], bRSq = p[1], sLen = p[2], sSharp = p[3];
    float mDepth = p[4], rStr = p[5];
    float glow = p[7], hC = p[8], hM = p[9], hS = p[10];
    float sB = p[11], sS = p[12], briV = p[13], blkThr = p[16];

    float drift = bR * 0.12f * fsin(cTime * 0.2f);

    // Body offsets in pixel units from cell center.
    // These produce visually equal spacing on both cube faces and cylinder
    // because both cx/cy are in pixel units.
    float ox0 = -bR * 0.65f + drift;
    float ox1 =  bR * 0.65f - drift;
    float ox2 =  0f;
    float oy0 = -bR * 0.35f;
    float oy1 = -bR * 0.35f;
    float oy2 =  bR * 0.75f + drift * 0.5f;

    // Outer bounding box in pixel units
    float maxExt = bR * (1f + sLen) + bR * 0.8f;
    if (Math.abs(cx) > maxExt * 1.5f || Math.abs(cy) > maxExt * 1.5f)
      return LXColor.BLACK;

    float spicEnd    = bR * (1f + sLen);
    float spicMaxSq  = spicEnd * spicEnd;
    float spicBaseSq = bRSq * 0.25f;
    float halfBR     = bR * 0.5f;
    float spicDenom  = bR * sLen + halfBR;

    // Cylinder wrap width in pixel units
    float wrapW = isCyl ? (float) CYL_COLS : Float.MAX_VALUE;

    float bodyFinal = 0f, spicBri = 0f, ringBri = 0f, spicT = 0f;

    for (int b = 0; b < 3; b++) {
      float ox = (b == 0) ? ox0 : (b == 1) ? ox1 : ox2;
      float oy = (b == 0) ? oy0 : (b == 1) ? oy1 : oy2;
      float spicPhaseB = cTime * 0.04f + b * (TWO_PI / 3f);

      // On cylinder evaluate at cx, cx+wrapW, cx-wrapW for seam closure
      int wrapCopies = isCyl ? 3 : 1;
      for (int w = 0; w < wrapCopies; w++) {
        float xShift = (w == 0) ? 0f : (w == 1) ? wrapW : -wrapW;
        float bdx    = cx - (ox + xShift);
        float bdy    = cy - oy;
        float distSq = bdx * bdx + bdy * bdy;

        // Per-body, per-copy early exit
        if (distSq > spicMaxSq * 1.2f) continue;

        float bNSq = distSq / bRSq;
        float bBri = clamp01(1f - bNSq);

        if (mDepth > 0f && bNSq < 1.21f) {
          float bN  = (float) Math.sqrt(bNSq);
          float sym = symFoldLUT(fatan2(bdy, bdx), 0f);
          bBri = clamp01(bBri + mDepth *
            (0.5f * fcos(sym * 5f) + 0.3f * fcos(bN * PI * 2.5f)) * bBri);
        }
        if (bBri > bodyFinal) bodyFinal = bBri;

        if (rStr > 0f && bNSq > 0.9f && bNSq < 2.5f) {
          float bN   = (float) Math.sqrt(bNSq);
          float rd   = bN - 1.25f;
          float rEnv = clamp01(1f - (rd * rd) / 0.0225f);
          if (rEnv > 0f) {
            float sym = symFoldLUT(fatan2(bdy, bdx), 0f);
            float rB  = rStr * rEnv * (0.5f + 0.5f * fcos(sym * 12f));
            if (rB > ringBri) ringBri = rB;
          }
        }

        if (distSq > spicBaseSq && distSq < spicMaxSq) {
          float dist  = 1f / fastInvSqrt(distSq);
          float t     = (dist - halfBR) / spicDenom;
          if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
          float sEnv  = (1f - t) * (1f - t);
          float bAng  = fatan2(bdy, bdx);
          float step  = TWO_PI / 8f;
          float norm  = ((bAng - spicPhaseB) % step + step) % step;
          float minA  = norm < step - norm ? norm : step - norm;
          float aFall = clamp01(1f - minA * sSharp / PI);
          float sBri  = sEnv * aFall * aFall;
          if (sBri > spicBri) { spicBri = sBri; spicT = t; }
        }
      }
    }

    // Bridges - inline, no array allocation
    float brRSq = bRSq * 0.0625f;
    float mx, my, dd, bEnv;
    float bridgeBri = 0f;

    mx = (ox0 + ox1) * 0.5f; my = (oy0 + oy1) * 0.5f;
    dd = (cx-mx)*(cx-mx) + (cy-my)*(cy-my);
    bEnv = clamp01(1f - dd / brRSq);
    if (bEnv * 0.6f > bridgeBri) bridgeBri = bEnv * 0.6f;

    mx = (ox0 + ox2) * 0.5f; my = (oy0 + oy2) * 0.5f;
    dd = (cx-mx)*(cx-mx) + (cy-my)*(cy-my);
    bEnv = clamp01(1f - dd / brRSq);
    if (bEnv * 0.6f > bridgeBri) bridgeBri = bEnv * 0.6f;

    mx = (ox1 + ox2) * 0.5f; my = (oy1 + oy2) * 0.5f;
    dd = (cx-mx)*(cx-mx) + (cy-my)*(cy-my);
    bEnv = clamp01(1f - dd / brRSq);
    if (bEnv * 0.6f > bridgeBri) bridgeBri = bEnv * 0.6f;

    bodyFinal = clamp01(bodyFinal + bridgeBri);

    return toColor(bodyFinal, bodyFinal, ringBri, spicBri, spicT,
                   glow, hC, hM, hS, sB, sS, briV, blkThr);
  }

  // Color output

  private int toColor(float bodyFinal, float bodyBri, float ringBri,
                      float spicBri, float spicT, float glow,
                      float hC, float hM, float hS,
                      float sB, float sS, float briV, float blkThr) {
    float boostedSpic = spicBri * glow;
    float total = clamp01(bodyFinal + ringBri + boostedSpic);
    if (total < blkThr) return LXColor.BLACK;

    float spicWeight = clamp01(boostedSpic / Math.max(0.001f, total));
    float bodyHue    = lerpHue(hM, hC, bodyBri * bodyBri);
    float spicHue    = lerpHue(hM, hS, spicT * spicT);
    float hue        = lerpHue(bodyHue, spicHue, spicWeight);
    hue = ((hue % 360f) + 360f) % 360f;
    float sat = lerp(sB, sS, spicWeight) * clamp01(total * 3f);

    return LXColor.hsb(hue, sat, briV * total);
  }

  // Helpers

  private static float symFoldLUT(float angle, float m) {
    int ai   = (int)((angle + PI) / TWO_PI * LUT) & (LUT - 1);
    int sec5 = LUT / 5, sec2 = LUT / 2;
    float s5 = (float)(ai % sec5) / sec5 * (TWO_PI / 5f);
    float s2 = (float)(ai % sec2) / sec2 * PI;
    return lerp(s5, s2, m);
  }

  private static float fastInvSqrt(float x) {
    float xh = 0.5f * x;
    int   i  = Float.floatToIntBits(x);
    i = 0x5f3759df - (i >> 1);
    float y = Float.intBitsToFloat(i);
    return y * (1.5f - xh * y * y);
  }

  private static float lerpHue(float a, float b, float t) {
    float d = b - a;
    if (d > 180f) d -= 360f; else if (d < -180f) d += 360f;
    return a + d * t;
  }

  private float fsin(float a) {
    int i = (int)(a * LUT_SCALE) & (LUT - 1);
    return SINL[i < 0 ? i + LUT : i];
  }

  private float fcos(float a) {
    int i = (int)(a * LUT_SCALE) & (LUT - 1);
    return COSL[i < 0 ? i + LUT : i];
  }

  private static float fatan2(float y, float x) {
    if (x == 0f) return (y >= 0f) ? PI / 2f : -PI / 2f;
    float r = y / x;
    float a = r / (1f + 0.28f * r * r);
    return (x < 0f) ? (y >= 0f ? a + PI : a - PI) : a;
  }

  private static float lerp(float a, float b, float t) { return a + t * (b - a); }
  private static float clamp01(float v)                 { return v < 0f ? 0f : v > 1f ? 1f : v; }
}
