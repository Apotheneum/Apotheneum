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
@LXComponent.Name("Lattice Bloom")
public class LatticeBloom extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;
  private static final float SQRT3  = (float) Math.sqrt(3.0);

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

  // Parameters

  // Number of lattice rows fitting in the surface.
  private final CompoundParameter density = new CompoundParameter("Density", 8, 3, 16)
    .setDescription("Lattice density (rows per surface, higher=more spheres)");

  // Sphere radius relative to lattice spacing.
  private final CompoundParameter sphereSize = new CompoundParameter("Size", 45, 20, 60)
    .setDescription("Sphere radius x100 (45=spheres just touching)");

  // Bloom: 0 = spheres scattered far from lattice positions (chaos), 1 = perfect lattice.
  // Animation parameter - sweep from 0->1 to show lattice emerging from disorder.
  private final CompoundParameter bloom = new CompoundParameter("Bloom", 100, 0, 100)
    .setDescription("Lattice formation x100 (0=maximum disorder/scatter, 100=perfect lattice)");

  // Defect: 0 = perfect, 1 = strong vacancy.
  // At >0 a single sphere near center is removed and surrounding spheres
  // are displaced slightly inward (strain field).
  private final CompoundParameter defect = new CompoundParameter("Defect", 0, 0, 100)
    .setDescription("Defect intensity x100 (vacancy + strain)");

  // Defect strain radius - how many neighbors are displaced.
  private final CompoundParameter strainR = new CompoundParameter("Strain", 30, 0, 100)
    .setDescription("Strain field reach x100 around defect");

  // Polymorph: 0 = FCC (hexagonal close packing), 1 = simple cubic.
  // Phase transition between two distinct arrangements of the same atoms.
  private final CompoundParameter polymorph = new CompoundParameter("Poly", 0, 0, 100)
    .setDescription("Polymorphic shift x100 (0=FCC, 100=cubic)");

  private final CompoundParameter rotSpeed = new CompoundParameter("RotSp", 0, 0, 20)
    .setDescription("Lattice rotation speed (max 20 RPM)");

  private final BooleanParameter breathe = new BooleanParameter("Breathe", false)
    .setDescription("Slow size pulsation");

  private final CompoundParameter breatheRate = new CompoundParameter("BrRte", 3, 1, 20)
    .setDescription("Breathing rate x10 Hz");

  // Core hue - sphere centers
  private final CompoundParameter hueCore = new CompoundParameter("HueC", 38.0, 0.0, 360.0)
    .setDescription("Sphere core hue (~38=warm gold)");

  // Surface hue - sphere edges
  private final CompoundParameter hueSurf = new CompoundParameter("HueS", 195.0, 0.0, 360.0)
    .setDescription("Sphere surface hue (~195=cool cyan)");

  // Defect strain hue - for distressed neighbours
  private final CompoundParameter hueStrain = new CompoundParameter("HueR", 350.0, 0.0, 360.0)
    .setDescription("Strain hue (~350=red, for stressed neighbours)");

  private final CompoundParameter satBody = new CompoundParameter("SatB", 75.0, 0.0, 100.0)
    .setDescription("Body saturation");

  private final CompoundParameter blackThresh = new CompoundParameter("Black", 5, 0, 30)
    .setDescription("Black threshold x100");

  private final CompoundParameter bright = new CompoundParameter("Bright", 95.0, 20.0, 100.0)
    .setDescription("Output brightness");

  private float time = 0f;

  // Constructor

  public LatticeBloom(LX lx) {
    super(lx);
    addParameter("Density", this.density);
    addParameter("Size",    this.sphereSize);
    addParameter("Bloom",   this.bloom);
    addParameter("Defect",  this.defect);
    addParameter("Strain",  this.strainR);
    addParameter("Poly",    this.polymorph);
    addParameter("RotSp",   this.rotSpeed);
    addParameter("Breathe", this.breathe);
    addParameter("BrRte",   this.breatheRate);
    addParameter("HueC",    this.hueCore);
    addParameter("HueS",    this.hueSurf);
    addParameter("HueR",    this.hueStrain);
    addParameter("SatB",    this.satBody);
    addParameter("Black",   this.blackThresh);
    addParameter("Bright",  this.bright);
  }

  // Main render

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0);

    float dens   = density.getValuef();
    float sR     = sphereSize.getValuef() / 100f / dens;  // sphere radius in UV
    float bl     = bloom.getValuef() / 100f;
    float def    = defect.getValuef() / 100f;
    float strn   = strainR.getValuef() / 100f;
    float poly   = polymorph.getValuef() / 100f;
    float rotSpeedVal = rotSpeed.getValuef() / 20f;  // normalize 0-20 to [0,1]
    // Apply squared easing: slower across range
    rotSpeedVal = rotSpeedVal * rotSpeedVal;  // square for easing (reduces max speed)
    float rot = time * rotSpeedVal * 0.5f * TWO_PI;  // 0.5 = much gentler scaling
    float cosR   = fcos(rot), sinR = fsin(rot);
    float breath = breathe.getValueb() ?
      0.10f * fsin(time * breatheRate.getValuef() / 10f) : 0f;
    float sRBreathed = sR * (1f + breath);
    float sRSq = sRBreathed * sRBreathed;  // precomputed once per frame

    float hC  = hueCore.getValuef();
    float hS  = hueSurf.getValuef();
    float hStrn = hueStrain.getValuef();
    float sat = satBody.getValuef();
    float br  = bright.getValuef();
    float bk  = blackThresh.getValuef() / 100f;

    renderCube(dens, sRBreathed, sRSq, bl, def, strn, poly, cosR, sinR,
               hC, hS, hStrn, sat, br, bk);
    renderCylinder(dens, sRBreathed, sRSq, bl, def, strn, poly, cosR, sinR,
                   hC, hS, hStrn, sat, br, bk);
  }

  // Sphere field evaluation

  // Evaluate the lattice at a given UV point.
  // xs parameter: 1.0 for cube (square), 120/43 for cylinder (stretch lattice to match pixel aspect).
  // We stretch the lattice spacing, not the coordinates, so geometry stays correct.
  private int evalLattice(float u, float v, float dens, float sR, float sRSq,
                           float bl, float def, float strn, float poly,
                           float cosR, float sinR,
                           float hC, float hS, float hStrn,
                           float sat, float br, float bk, float xs) {
    // Apply rotation around (0.5,0.5)
    float du = u - 0.5f, dv = v - 0.5f;
    float ru = du * cosR - dv * sinR + 0.5f;
    float rv = du * sinR + dv * cosR + 0.5f;

    float dx = (1f / dens);  // no xs stretch here - done at sample time
    // Polymorph: blend between hex (FCC) and square (cubic) row spacing
    float dyHex = dx * SQRT3 * 0.5f;
    float dy = lerp(dyHex, dx, poly);
    // And blend the row offset (FCC has 0.5 offset, cubic has 0)
    float rowOffset = (1f - poly) * 0.5f;

    // Which lattice row/col are we in?
    int   row     = (int)(rv / dy + 100f) - 100;  // floor
    float vCell   = rv - row * dy;                 // [0, dy)
    // Determine column with row offset for odd rows
    float colOff  = (row & 1) * rowOffset * dx;
    int   col     = (int)((ru - colOff) / dx + 100f) - 100;

    // Best sphere center - check neighbourhood cells
    // Adaptive search: scattered spheres need wider search
    int searchRadius = 2;
    float bestSpicBri = 0f;
    float bestSphereDx = 0f, bestSphereDy = 0f;
    float bestStrain = 0f;
    boolean bestIsDefect = false;

    for (int dRow = -searchRadius; dRow <= searchRadius; dRow++) {
      int rowN = row + dRow;
      float rowOffN = (rowN & 1) * rowOffset * dx;
      for (int dCol = -searchRadius; dCol <= searchRadius; dCol++) {
        int colN = col + dCol;
        // Sphere center in UV space
        float cx = colN * dx + rowOffN + dx * 0.5f;
        float cy = rowN * dy + dy * 0.5f;

        // Bloom: scatter positions based on cell
        if (bl < 1f) {
          int hash = (colN * 1447 + rowN * 2833) & 0xFFFF;
          float jx = ((hash & 0xFF) / 255f - 0.5f) * dx * 1.8f;
          float jy = (((hash >> 8) & 0xFF) / 255f - 0.5f) * dy * 1.8f;
          cx += jx * (1f - bl);
          cy += jy * (1f - bl);
        }

        // Defect: if this is the sphere nearest center and def>0, remove it
        boolean isDefect = false;
        float defStrain = 0f;
        if (def > 0f) {
          float defDx = cx - 0.5f, defDy = cy - 0.5f;
          float defDistSq = defDx * defDx + defDy * defDy;
          // The sphere "at the center" - within one cell of (0.5,0.5)
          if (defDistSq < (dx * 0.5f) * (dx * 0.5f)) {
            isDefect = true;
          } else if (defDistSq < strn * strn) {
            // Within strain radius - neighbour is stressed
            defStrain = def * (1f - (float) Math.sqrt(defDistSq) / Math.max(strn, 0.001f));
          }
        }

        if (isDefect && def > 0.5f) continue;  // skip removed sphere

        // Distance from pixel to this sphere center
        float pdx = ru - cx, pdy = rv - cy;
        float pdSq = pdx * pdx + pdy * pdy;
        if (pdSq > sRSq) continue;

        // Soft sphere brightness - quadratic falloff
        float bNSq = pdSq / sRSq;
        float bri = 1f - bNSq;
        // Strain reduces brightness slightly
        bri *= (1f - defStrain * 0.4f);
        // Defect partial: fade out as def goes from 0->1
        if (isDefect) bri *= (1f - def);

        if (bri > bestSpicBri) {
          bestSpicBri = bri;
          bestSphereDx = pdx; bestSphereDy = pdy;
          bestStrain = defStrain;
          bestIsDefect = isDefect;
        }
      }
    }

    if (bestSpicBri < bk) return LXColor.BLACK;

    // Hue: center->surface gradient based on sphere brightness
    float bodyHue = lerpHue(hS, hC, bestSpicBri * bestSpicBri);
    // Strain shifts toward strain hue
    float hue = lerpHue(bodyHue, hStrn, bestStrain);
    hue = ((hue % 360f) + 360f) % 360f;

    return LXColor.hsb(hue, sat, br * bestSpicBri);
  }

  // Cube rendering

  private void renderCube(float dens, float sR, float sRSq, float bl, float def, float strn,
                           float poly, float cosR, float sinR,
                           float hC, float hS, float hStrn,
                           float sat, float br, float bk) {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    for (Face face : cube.exterior.faces)
      renderCubeFace(face, dens, sR, sRSq, bl, def, strn, poly, cosR, sinR, hC, hS, hStrn, sat, br, bk);
    if (cube.interior != null)
      for (Face face : cube.interior.faces)
        renderCubeFace(face, dens, sR, sRSq, bl, def, strn, poly, cosR, sinR, hC, hS, hStrn, sat, br, bk);
  }

  private void renderCubeFace(Face face, float dens, float sR, float sRSq, float bl, float def,
                                float strn, float poly, float cosR, float sinR,
                                float hC, float hS, float hStrn,
                                float sat, float br, float bk) {
    int cols = Apotheneum.GRID_WIDTH, rows = face.rows.length;
    for (int ri = 0; ri < rows; ri++) {
      Row row = face.rows[ri];
      float v = (float) ri / (rows - 1);
      for (int ci = 0; ci < cols; ci++) {
        float u = (float) ci / (cols - 1);
        // Apply rotation before eval
        float du = u - 0.5f, dv = v - 0.5f;
        float ru = du * cosR - dv * sinR + 0.5f;
        float rv = du * sinR + dv * cosR + 0.5f;

        colors[row.points[ci].index] = evalLattice(ru, rv, dens, sR, sRSq, bl, def, strn,
                                                    poly, cosR, sinR,
                                                    hC, hS, hStrn, sat, br, bk, 1.0f);
      }
    }
  }

  // Cylinder rendering

  private void renderCylinder(float dens, float sR, float sRSq, float bl, float def,
                               float strn, float poly, float cosR, float sinR,
                               float hC, float hS, float hStrn,
                               float sat, float br, float bk) {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    renderCylOrientation(cyl.exterior, dens, sR, sRSq, bl, def, strn, poly, cosR, sinR, hC, hS, hStrn, sat, br, bk);
    if (cyl.interior != null)
      renderCylOrientation(cyl.interior, dens, sR, sRSq, bl, def, strn, poly, cosR, sinR, hC, hS, hStrn, sat, br, bk);
  }

  private void renderCylOrientation(Cylinder.Orientation o, float dens, float sR, float sRSq,
                                      float bl, float def, float strn, float poly,
                                      float cosR, float sinR, float hC, float hS,
                                      float hStrn, float sat, float br, float bk) {
    Ring[] rings = o.rings;
    int numRings = rings.length;
    final float XS = 120f / 43f;  // stretch to match cylinder aspect: 120 wide / 43 tall

    for (int ri = 0; ri < numRings; ri++) {
      Ring ring = rings[ri];
      int np = ring.points.length;
      float vN = (float) ri / (numRings - 1);
      float v = vN;

      for (int pi = 0; pi < np; pi++) {
        float u = (float) pi / np;
        // Stretch U to match cylinder width: makes a square form appear circular
        u = 0.5f + (u - 0.5f) * XS;

        // Apply rotation
        float du = u - 0.5f, dv = v - 0.5f;
        float ru = du * cosR - dv * sinR + 0.5f;
        float rv = du * sinR + dv * cosR + 0.5f;
        // Wrap seamlessly
        ru = ((ru % 1f) + 1f) % 1f;

        colors[ring.points[pi].index] = evalLattice(ru, rv, dens, sR, sRSq, bl, def, strn,
                                                     poly, cosR, sinR,
                                                     hC, hS, hStrn, sat, br, bk, 1.0f);
      }
    }
  }

  // Helpers

  private static float lerpHue(float a, float b, float t) {
    float d = b - a;
    if (d > 180f) d -= 360f; else if (d < -180f) d += 360f;
    return a + d * t;
  }

  private float fsin(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return SINL[i < 0 ? i + LUT : i]; }
  private float fcos(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return COSL[i < 0 ? i + LUT : i]; }

  private static float lerp(float a, float b, float t) { return a + t * (b - a); }
}
