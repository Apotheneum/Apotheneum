package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Life Forms")
public class LifeForms extends ApotheneumPattern {

  private final CompoundParameter sizeParam = new CompoundParameter("Size", 0.4, 0.1, 0.6)
    .setDescription("Base apothem size (0.1–0.6)");
  private final CompoundParameter rotation = new CompoundParameter("Rotate", 0.0, 0.0, 1.0)
    .setDescription("Rotates whole tile grid (0–1 → 0–360°)");
  private final CompoundParameter morph = new CompoundParameter("Morph", 0.0, 0.0, 1.0)
    .setDescription("0 = pentagon, 1 = star (smooth)");
  private final CompoundParameter armLen = new CompoundParameter("ArmLen", 0.6, 0.2, 1.0)
    .setDescription("Star arm length ratio (0.2–1.0)");
  private final CompoundParameter n1Param = new CompoundParameter("N1", 0.5, 0.1, 2.0)
    .setDescription("Superformula n1 (arm taper)");
  private final CompoundParameter n2Param = new CompoundParameter("N2", 2.2, 1.0, 5.0)
    .setDescription("Superformula n2 (curvature)");
  private final CompoundParameter n3Param = new CompoundParameter("N3", 2.2, 1.0, 5.0)
    .setDescription("Superformula n3 (curvature)");
  private final CompoundParameter tileRadParam = new CompoundParameter("Scalar", 2.5, 1.5, 6.0)
    .setDescription("Tiling radius multiplier (1.5–6.0)");

  private static final int LUT_SIZE = 120;
  private static final float PENT_SCALE = 2.0f;

  // cached LUTs and params
  private float[] pentLUT = new float[LUT_SIZE];
  private float[] starLUT = new float[LUT_SIZE];
  private float prevBaseAp = -1, prevALen = -1, prevN1 = -1, prevN2 = -1, prevN3 = -1;
  private boolean first = true;

  public LifeForms(LX lx) {
    super(lx);
    addParameter("Size", this.sizeParam);
    addParameter("Rotation", this.rotation);
    addParameter("Morph", this.morph);
    addParameter("ArmLen", this.armLen);
    addParameter("N1", this.n1Param);
    addParameter("N2", this.n2Param);
    addParameter("N3", this.n3Param);
    addParameter("TileRad", this.tileRadParam);
  }

  @Override
  protected void render(double deltaMs) {
    float baseAp = sizeParam.getValuef();
    float angleOff = rotation.getValuef() * (float)(2 * Math.PI);
    float mVal = morph.getValuef();
    float aLen = armLen.getValuef();
    float n1 = n1Param.getValuef(), n2 = n2Param.getValuef(), n3 = n3Param.getValuef();
    float tileRad = tileRadParam.getValuef() * baseAp;
    float sector = 2f * (float)Math.PI / 5f;

    // rebuild LUTs if params changed
    if (first || baseAp != prevBaseAp) {
      for (int i = 0; i < LUT_SIZE; i++) {
        float theta = i * (2f * (float)Math.PI / LUT_SIZE);
        float local = ((theta % sector) + sector/2f) % sector - sector/2f;
        pentLUT[i] = baseAp * PENT_SCALE / (float)Math.cos(local);
      }
      prevBaseAp = baseAp; first = false;
    }
    if (first || aLen != prevALen || n1 != prevN1 || n2 != prevN2 || n3 != prevN3) {
      for (int i = 0; i < LUT_SIZE; i++) {
        float theta = i * (2f * (float)Math.PI / LUT_SIZE);
        float sf = superFormula(theta, 5f, n1, n2, n3);
        starLUT[i] = sf * aLen * baseAp;
      }
      prevALen = aLen; prevN1 = n1; prevN2 = n2; prevN3 = n3;
    }

    // precompute tile offsets
    float[] dx = new float[6], dy = new float[6];
    dx[0] = dy[0] = 0;
    for (int k = 1; k < 6; k++) {
      float angK = k * sector;
      dx[k] = tileRad * (float)Math.cos(angK);
      dy[k] = tileRad * (float)Math.sin(angK);
    }
    float cosR = (float)Math.cos(angleOff), sinR = (float)Math.sin(angleOff);

    Cube cube = Apotheneum.cube;
    for (Face face : cube.exterior.faces) {
      processFace(face, pentLUT, starLUT, dx, dy, cosR, sinR, mVal);
    }
    if (cube.interior != null) {
      for (Face face : cube.interior.faces) {
        processFace(face, pentLUT, starLUT, dx, dy, cosR, sinR, mVal);
      }
    }
  }

  private void processFace(Face face,
                           float[] pentLUT, float[] starLUT,
                           float[] dx, float[] dy,
                           float cosR, float sinR,
                           float mVal) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    for (Row row : face.rows) {
      int ry = row.index;
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        float u0 = cx/(float)(cols-1) - 0.5f;
        float v0 = ry/(float)(rows-1) - 0.5f;
        boolean lit = false;
        for (int k = 0; k < 6; k++) {
          float ux0 = u0 - dx[k], vy0 = v0 - dy[k];
          float u = ux0 * cosR - vy0 * sinR;
          float v = ux0 * sinR + vy0 * cosR;
          float dist = (float)Math.hypot(u, v);
          float ang = (float)Math.atan2(v, u);
          int idx = (int)((ang % (2f*(float)Math.PI)) / (2f*(float)Math.PI) * LUT_SIZE);
          if (idx < 0) idx += LUT_SIZE;
          float boundary = pentLUT[idx] * (1f - mVal) + starLUT[idx] * mVal;
          if (dist <= boundary) { lit = true; break; }
        }
        colors[p.index] = lit ? LXColor.rgb(255,255,255) : 0;
      }
    }
  }

  private float superFormula(float theta, float m,
                             float n1, float n2, float n3) {
    float p1 = (float)Math.pow(Math.abs((float)Math.cos(m*theta/4f)), n2);
    float p2 = (float)Math.pow(Math.abs((float)Math.sin(m*theta/4f)), n3);
    return (float)Math.pow(p1 + p2, -1f/n1);
  }
}
