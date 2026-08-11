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
@LXComponent.Name("Icosahedral Capsid")
public class IcosahedralCapsid extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;

  // Grid: 128x128, 3 channels per cell
  private static final int GRID_W  = 128;
  private static final int GRID_H  = 128;
  private static final int GRID_CH = 3;  // bodyBri, facetBri, edgeBri

  private final float[] grid = new float[GRID_W * GRID_H * GRID_CH];
  private boolean dirty = true;

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

  // Scratch fields - no allocation in render loop
  private final float[] sCell = new float[GRID_CH];
  private final float[] cosP  = new float[36];  // per-cell phase, max 6x6
  private final float[] sinP  = new float[36];

  // Parameters

  private final CompoundParameter tileCount = new CompoundParameter("Tiles", 1, 1, 6)
    .setDescription("Capsids per axis (1=single hero, 6=6x6 grid)");

  // Body radius in UV units x100. Body=30 -> 0.30, fills tile well.
  private final CompoundParameter bodySize = new CompoundParameter("Body", 30, 8, 45)
    .setDescription("Capsid radius x100 in UV space");

  // Facet count selector: governs the triangular subdivision level.
  // Real icosahedral viruses use T-numbers: T=1 (12 facets), T=3 (60),
  // T=7 (140 - adenovirus), T=13 (260 - reovirus).
  // We use T = 1, 3, 4, 7, 9, 12, 13, 16.
  private final CompoundParameter tNumber = new CompoundParameter("T", 3, 1, 16)
    .setDescription("Triangulation number (capsid complexity, like real viruses)");

  // Edge ridge intensity - how prominent the facet boundaries are.
  private final CompoundParameter edgeIntensity = new CompoundParameter("Edge", 50, 0, 100)
    .setDescription("Edge ridge prominence between facets");

  private final CompoundParameter facetDepth = new CompoundParameter("Facet", 60, 0, 100)
    .setDescription("Facet brightness variation (faceted dome look)");

  // Radial brightness falloff - controls the 3D-feeling dome depth.
  private final CompoundParameter depth = new CompoundParameter("Depth", 70, 0, 100)
    .setDescription("Radial brightness gradient (3D dome depth feel)");

  // Hero form rotation speed on cylinder.
  private final CompoundParameter rotSpeed = new CompoundParameter("RotSp", 12, 0, 100)
    .setDescription("Hero capsid cylinder rotation speed x200");

  // Static offset, on top of whatever RotSp is drifting. Cylinder only.
  private final CompoundParameter rotate = new CompoundParameter("Rot", 90, 0, 360)
    .setDescription("Static rotation of hero forms around cylinder");

  private final CompoundParameter phaseVar = new CompoundParameter("Phase", 60, 0, 100)
    .setDescription("Phase variation across tiled field");

  private final BooleanParameter breathe = new BooleanParameter("Breathe", true)
    .setDescription("Slow pulsation of capsid size");

  private final CompoundParameter breatheRate = new CompoundParameter("BrRte", 4, 1, 20)
    .setDescription("Breathing rate x10 Hz");

  // Core hue - bright center
  private final CompoundParameter hueCore = new CompoundParameter("HueC", 50.0, 0.0, 360.0)
    .setDescription("Core hue - bright capsid center (~50=warm gold)");

  // Surface hue - facet body
  private final CompoundParameter hueSurf = new CompoundParameter("HueS", 200.0, 0.0, 360.0)
    .setDescription("Surface hue - capsid facets (~200=cyan)");

  // Edge hue - facet ridges
  private final CompoundParameter hueEdge = new CompoundParameter("HueE", 285.0, 0.0, 360.0)
    .setDescription("Edge hue - facet boundary ridges (~285=violet)");

  private final CompoundParameter edgeGlow = new CompoundParameter("Glow", 18, 10, 30)
    .setDescription("Edge brightness boost x10");

  private final CompoundParameter satBody = new CompoundParameter("SatB", 70.0, 0.0, 100.0)
    .setDescription("Body saturation");

  private final CompoundParameter satEdge = new CompoundParameter("SatE", 95.0, 0.0, 100.0)
    .setDescription("Edge saturation");

  private final CompoundParameter blackThresh = new CompoundParameter("Black", 5, 0, 30)
    .setDescription("Black threshold x100");

  private final CompoundParameter bright = new CompoundParameter("Bright", 90.0, 20.0, 100.0)
    .setDescription("Output brightness");

  private float time = 0f;
  // Wrapped every frame. An unwrapped time * speed loses angular precision over
  // a night-long run, and makes any change to RotSp jump the hero position.
  private float heroPhase = 0f;

  // Constructor

  public IcosahedralCapsid(LX lx) {
    super(lx);
    addParameter("Tiles",   this.tileCount);
    addParameter("Body",    this.bodySize);
    addParameter("T",       this.tNumber);
    addParameter("Edge",    this.edgeIntensity);
    addParameter("Facet",   this.facetDepth);
    addParameter("Depth",   this.depth);
    addParameter("RotSp",   this.rotSpeed);
    addParameter("Phase",   this.phaseVar);
    addParameter("Breathe", this.breathe);
    addParameter("BrRte",   this.breatheRate);
    addParameter("HueC",    this.hueCore);
    addParameter("HueS",    this.hueSurf);
    addParameter("HueE",    this.hueEdge);
    addParameter("Glow",    this.edgeGlow);
    addParameter("SatB",    this.satBody);
    addParameter("SatE",    this.satEdge);
    addParameter("Black",   this.blackThresh);
    addParameter("Bright",  this.bright);
    addParameter("Rot",     this.rotate);
  }

  @Override
  public void onParameterChanged(heronarts.lx.parameter.LXParameter p) {
    if (p == bodySize || p == tNumber || p == edgeIntensity ||
        p == facetDepth || p == depth) {
      dirty = true;
    }
  }

  // Main render

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0);

    float bR    = bodySize.getValuef() / 100f;
    float tNum  = tNumber.getValuef();
    float eInt  = edgeIntensity.getValuef() / 100f;
    float fDep  = facetDepth.getValuef()   / 100f;
    float dDep  = depth.getValuef()        / 100f;

    if (dirty) {
      computeGrid(bR, tNum, eInt, fDep, dDep);
      dirty = false;
    }

    float tiles   = Math.max(1f, tileCount.getValuef());
    heroPhase += (float)(deltaMs / 1000.0) * rotSpeed.getValuef() / 200f;
    heroPhase -= (float) Math.floor(heroPhase);
    float heroRot = heroPhase + rotate.getValuef() / 360f;
    float pVar    = phaseVar.getValuef() / 100f;
    float breathAmt = breathe.getValueb() ?
      0.06f * fsin(time * breatheRate.getValuef() / 10f) : 0f;

    float hC = hueCore.getValuef();
    float hS = hueSurf.getValuef();
    float hE = hueEdge.getValuef();
    float sB = satBody.getValuef();
    float sE = satEdge.getValuef();
    float gl = edgeGlow.getValuef() / 10f;
    float br = bright.getValuef();
    float bk = blackThresh.getValuef() / 100f;

    renderCube(tiles, pVar, breathAmt, hC, hS, hE, sB, sE, gl, br, bk);
    renderCylinder(heroRot, breathAmt, hC, hS, hE, sB, sE, gl, br, bk);
  }

  // Grid computation

  // Compute the capsid into the grid. UV [0,1]x[0,1], center at (0.5,0.5).
  // The capsid is a triangulated dome stored in a square grid.
  // Cylinder rendering will stretch sample coordinates to match aspect ratio.
  private void computeGrid(float bR, float tNum, float eInt, float fDep, float dDep) {
    float bRSq = bR * bR;
    float invW = 1f / (GRID_W - 1);
    float invH = 1f / (GRID_H - 1);

    // T-number determines how many facets - use it to choose 5- and 6-fold
    // modulation frequencies. Real icosahedral T=1 -> 12 facets (5-fold dominant),
    // T=3 -> 60 (mixed 5/6 fold), higher T -> 6-fold dominant.
    int   tN     = (int)(tNum + 0.5f);
    int   nFacet5 = Math.max(5, tN * 2);     // primary 5-fold rate
    int   nFacet6 = Math.max(6, tN * 3);     // secondary 6-fold rate
    // Radial banding (concentric ridges)
    float radFreq = 2f + tNum * 0.5f;

    for (int gy = 0; gy < GRID_H; gy++) {
      float cy = (float) gy * invH - 0.5f;
      for (int gx = 0; gx < GRID_W; gx++) {
        float cx = (float) gx * invW - 0.5f;
        float dSq = cx * cx + cy * cy;

        int base = (gy * GRID_W + gx) * GRID_CH;

        if (dSq > bRSq) {
          grid[base]   = 0f;
          grid[base+1] = 0f;
          grid[base+2] = 0f;
          continue;
        }

        float bNSq = dSq / bRSq;
        // Smooth quadratic body falloff
        float bodyBri = 1f - bNSq;

        // 3D-dome depth shading - emphasizes center
        bodyBri = bodyBri * (1f - dDep) + bodyBri * bodyBri * dDep;

        float angle = fatan2(cy, cx);
        float bN    = (float) Math.sqrt(bNSq);

        // Facet modulation: two overlapping symmetries (5-fold + 6-fold)
        // produces the icosahedral quasi-equivalence look
        float f5 = fcos(angle * nFacet5);
        float f6 = fcos(angle * nFacet6);
        // Radial banding mimics concentric facet rings
        float rB = fcos(bN * PI * radFreq);
        // Combined facet brightness: dome darkens slightly at facet boundaries
        float facetMod = 0.5f * f5 + 0.3f * f6 + 0.2f * rB;
        float facetBri = bodyBri * (1f - fDep * 0.5f * (1f - facetMod));

        // Edge ridges: sharp peaks where the facet modulation crosses zero
        // (the ridges between facets). |facetMod| near 0 -> bright ridge.
        float edgeProx = 1f - Math.abs(facetMod);
        edgeProx = edgeProx * edgeProx;  // sharpen
        // Edges only visible on the dome's near surface (bNSq < 0.85)
        float edgeMask = clamp01(1f - bNSq / 0.85f);
        float edgeBri  = eInt * edgeProx * edgeMask * bodyBri;

        grid[base]   = clamp01(facetBri);
        grid[base+1] = clamp01(bodyBri);
        grid[base+2] = clamp01(edgeBri);
      }
    }
  }

  // Grid sampling

  private void sampleGrid(float u, float v, float[] out) {
    if (u < 0f) u = 0f; else if (u > 1f) u = 1f;
    if (v < 0f) v = 0f; else if (v > 1f) v = 1f;
    float gx = u * (GRID_W - 1);
    float gy = v * (GRID_H - 1);
    int x0 = (int) gx, y0 = (int) gy;
    float tx = gx - x0, ty = gy - y0;
    if (x0 >= GRID_W - 1) { x0 = GRID_W - 2; tx = 1f; }
    if (y0 >= GRID_H - 1) { y0 = GRID_H - 2; ty = 1f; }
    int x1 = x0 + 1, y1 = y0 + 1;
    int b00 = (y0 * GRID_W + x0) * GRID_CH;
    int b10 = (y0 * GRID_W + x1) * GRID_CH;
    int b01 = (y1 * GRID_W + x0) * GRID_CH;
    int b11 = (y1 * GRID_W + x1) * GRID_CH;
    for (int c = 0; c < GRID_CH; c++)
      out[c] = lerp(
        lerp(grid[b00+c], grid[b10+c], tx),
        lerp(grid[b01+c], grid[b11+c], tx), ty);
  }

  // Cube rendering

  private void renderCube(float tiles, float pVar, float breathAmt,
                           float hC, float hS, float hE, float sB, float sE,
                           float gl, float br, float bk) {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    int nTiles = (int) tiles;

    // Precompute phase rotation per tile cell
    // Base phase from pVar applies to all tiles, plus per-tile variation from hash
    for (int ty = 0; ty < nTiles; ty++)
      for (int tx = 0; tx < nTiles; tx++) {
        int hash = (tx * 1447 + ty * 2833) & 0xFF;
        float perTileVariation = (hash / 255f) * TWO_PI;  // random offset per tile
        float phase = pVar * TWO_PI + perTileVariation;  // base phase + variation
        cosP[ty * nTiles + tx] = fcos(phase);
        sinP[ty * nTiles + tx] = fsin(phase);
      }

    for (Face face : cube.exterior.faces)
      renderCubeFace(face, tiles, nTiles, breathAmt, hC, hS, hE, sB, sE, gl, br, bk);
    if (cube.interior != null)
      for (Face face : cube.interior.faces)
        renderCubeFace(face, tiles, nTiles, breathAmt, hC, hS, hE, sB, sE, gl, br, bk);
  }

  private void renderCubeFace(Face face, float tiles, int nTiles, float breathAmt,
                               float hC, float hS, float hE, float sB, float sE,
                               float gl, float br, float bk) {
    int cols = Apotheneum.GRID_WIDTH, rows = face.rows.length;
    float scale = 1f - breathAmt;
    for (int ri = 0; ri < rows; ri++) {
      Row row = face.rows[ri];
      float vN = (float) ri / (rows - 1);
      int ty = (int)(vN * tiles); if (ty >= nTiles) ty = nTiles - 1;
      float vT = vN * tiles - ty;
      for (int ci = 0; ci < cols; ci++) {
        float uN = (float) ci / (cols - 1);
        int tx = (int)(uN * tiles); if (tx >= nTiles) tx = nTiles - 1;
        float uT = uN * tiles - tx;

        float cp = cosP[ty * nTiles + tx];
        float sp = sinP[ty * nTiles + tx];
        float du = uT - 0.5f, dv = vT - 0.5f;
        float rdu = du * cp - dv * sp, rdv = du * sp + dv * cp;

        float u = 0.5f + rdu * scale;
        float v = 0.5f + rdv * scale;

        sampleGrid(u, v, sCell);
        colors[row.points[ci].index] = cellToColor(sCell, hC, hS, hE, sB, sE, gl, br, bk);
      }
    }
  }

  // Cylinder rendering

  private void renderCylinder(float heroRot, float breathAmt,
                               float hC, float hS, float hE, float sB, float sE,
                               float gl, float br, float bk) {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    renderCylOrientation(cyl.exterior, heroRot, breathAmt, hC, hS, hE, sB, sE, gl, br, bk);
    if (cyl.interior != null)
      renderCylOrientation(cyl.interior, heroRot, breathAmt, hC, hS, hE, sB, sE, gl, br, bk);
  }

  private void renderCylOrientation(Cylinder.Orientation o, float heroRot, float breathAmt,
                                     float hC, float hS, float hE, float sB, float sE,
                                     float gl, float br, float bk) {
    Ring[] rings = o.rings;
    int numRings = rings.length;
    float scale = 1f - breathAmt;
    final float XS = 120f / 43f;  // stretch X to match cylinder pixel aspect

    for (int ri = 0; ri < numRings; ri++) {
      Ring ring = rings[ri];
      int np = ring.points.length;
      float vN = (float) ri / (numRings - 1);
      float v = 0.5f + (vN - 0.5f) * scale;
      for (int pi = 0; pi < np; pi++) {
        float uN = ((float) pi / np + heroRot) % 1f;
        if (uN < 0f) uN += 1f;

        // Sample two hero capsule positions: one at current rotation, one at 180 deg opposite
        // Don't stretch yet - work in [0,1] space, then scale
        float u1 = uN;
        float u2 = (uN + 0.5f) % 1f;  // exactly 180 deg opposite (no wrapping artifacts)

        // Now stretch both
        float us1 = 0.5f + (u1 - 0.5f) * XS * scale;
        float us2 = 0.5f + (u2 - 0.5f) * XS * scale;

        // Clamp to [0,1] (no wrapping on stretched coords)
        if (us1 < 0f) us1 = 0f; else if (us1 > 1f) us1 = 1f;
        if (us2 < 0f) us2 = 0f; else if (us2 > 1f) us2 = 1f;

        sampleGrid(us1, v, sCell);
        int c1 = cellToColor(sCell, hC, hS, hE, sB, sE, gl, br, bk);

        sampleGrid(us2, v, sCell);
        int c2 = cellToColor(sCell, hC, hS, hE, sB, sE, gl, br, bk);

        // Blend: show both capsules - pick the brighter one
        float b1 = LXColor.b(c1);
        float b2 = LXColor.b(c2);
        int finalColor = (b1 >= b2) ? c1 : c2;
        colors[ring.points[pi].index] = finalColor;
      }
    }
  }

  // Cell -> color

  // cell[0] = facetBri (modulated body brightness)
  // cell[1] = bodyBri (raw smooth body)
  // cell[2] = edgeBri (ridge peaks)
  // Color blending:
  // bodyBri² drives core->surface hue shift (center warm, edge cool)
  // edgeBri drives surface->edge hue shift (ridges shift toward HueE)
  private int cellToColor(float[] cell, float hC, float hS, float hE,
                           float sB, float sE, float gl, float br, float bk) {
    float facetBri = cell[0];
    float bodyBri  = cell[1];
    float edgeBri  = cell[2];

    float boostedEdge = edgeBri * gl;
    float total = clamp01(facetBri + boostedEdge);
    if (total < bk) return LXColor.BLACK;

    float edgeW = clamp01(boostedEdge / Math.max(0.001f, total));
    // Body hue: core (center) -> surface (rim)
    float bodyHue = lerpHue(hS, hC, bodyBri * bodyBri);
    // Final hue: body -> edge
    float hue = lerpHue(bodyHue, hE, edgeW);
    hue = ((hue % 360f) + 360f) % 360f;

    float sat = lerp(sB, sE, edgeW) * clamp01(total * 3f);
    return LXColor.hsb(hue, sat, br * total);
  }

  // Helpers

  private static float lerpHue(float a, float b, float t) {
    float d = b - a;
    if (d > 180f) d -= 360f; else if (d < -180f) d += 360f;
    return a + d * t;
  }

  private float fsin(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return SINL[i < 0 ? i + LUT : i]; }
  private float fcos(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return COSL[i < 0 ? i + LUT : i]; }

  private static float fatan2(float y, float x) {
    if (x == 0f) return (y >= 0f) ? PI / 2f : -PI / 2f;
    float r = y / x, a = r / (1f + 0.28f * r * r);
    return (x < 0f) ? (y >= 0f ? a + PI : a - PI) : a;
  }

  private static float lerp(float a, float b, float t) { return a + t * (b - a); }
  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }
}
