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
@LXComponent.Name("Datum")
public class Datum extends ApotheneumPattern {

  // The scale axis runs 10^-14 to 10^28 - the full span of the piece.
  private static final float EXP_LO = -14f;
  private static final float EXP_HI =  28f;
  private static final float EXP_HUMAN = 0f;

  // Ticks at each decade, with the piece's own landmarks called out
  private static final float[] MARK_EXP = {
    -14f, -10f, -9f, -6f, -2f, 0f, 4f, 7f, 9f, 13f, 17f, 21f, 24f, 26f, 28f
  };

  // Axis

  private final CompoundParameter axisBri = new CompoundParameter("Axis", 30, 0, 100)
    .setDescription("Brightness of the scale line itself");
  private final CompoundParameter tickBri = new CompoundParameter("Ticks", 45, 0, 100)
    .setDescription("Brightness of the decade marks");
  private final CompoundParameter tickLen = new CompoundParameter("TickLen", 30, 0, 100)
    .setDescription("Length of the decade marks");
  private final CompoundParameter majorEvery = new CompoundParameter("Major", 3, 1, 8)
    .setDescription("Every Nth tick is drawn longer");
  private final CompoundParameter lines = new CompoundParameter("Lines", 1, 1, 8)
    .setDescription("How many parallel axes this one device draws");
  private final CompoundParameter spread = new CompoundParameter("Spread", 100, 0, 100)
    .setDescription("How far apart they sit - 100 puts the outermost pair on the edges");
  private final BooleanParameter cross = new BooleanParameter("Cross", false)
    .setDescription("Draw the perpendicular set too, framing the face");

  private final CompoundParameter axisPos = new CompoundParameter("AxisY", 50, 0, 100)
    .setDescription("Where the axis sits across the face");
  private final CompoundParameter thick = new CompoundParameter("Thick", 18, 2, 100)
    .setDescription("Line thickness");

  // The marker

  private final CompoundParameter youBri = new CompoundParameter("You", 90, 0, 100)
    .setDescription("Brightness of the point marking human scale");
  private final CompoundParameter youSize = new CompoundParameter("YouSize", 30, 2, 100)
    .setDescription("Size of the marker");
  private final CompoundParameter beacon = new CompoundParameter("Beacon", 45, 0, 100)
    .setDescription("Slow pulse of the marker");
  private final CompoundParameter beaconRate = new CompoundParameter("BcnRate", 30, 5, 100)
    .setDescription("How fast it pulses");
  private final CompoundParameter reach = new CompoundParameter("Reach", 35, 0, 100)
    .setDescription("How far the marker's light spills along the axis");
  private final CompoundParameter dropLine = new CompoundParameter("Drop", 55, 0, 100)
    .setDescription("Vertical line dropped from the marker to the axis");

  // Reveal

  private final CompoundParameter reveal = new CompoundParameter("Reveal", 100, 0, 100)
    .setDescription("Draw the axis outward from the human scale");
  private final CompoundParameter fromCentre = new CompoundParameter("Outward", 100, 0, 100)
    .setDescription("0 draws left to right, 100 draws outward from you in both directions");
  private final CompoundParameter dim = new CompoundParameter("Dim", 0, 0, 100)
    .setDescription("Fade the whole diagram");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 210, 0, 360)
    .setDescription("Hue at the small end of the scale");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", 110, -180, 180)
    .setDescription("Hue shift across the whole span - small to cosmic");
  private final CompoundParameter hueYou = new CompoundParameter("HueYou", 45, 0, 360)
    .setDescription("Hue of the marker");
  private final CompoundParameter satAxis = new CompoundParameter("Sat", 45, 0, 100)
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
    .setDescription("Run the scale vertically");

  private float time = 0f;

  private float sAxis, sTick, sTickLen, sAxisY, sThick, sMajor;
  private int sLines; private float sSpread; private boolean sCross, axIsYou;
  private float sYou, sYouSize, sBeacon, sReach, sDrop;
  private float sReveal, sOutward, sDim;
  private float sBr, sBk, sHue, sHueRng, sHueYou, sSat;
  private boolean sVert;
  private float humanU;

  public Datum(LX lx) {
    super(lx);
    addParameter("Axis",    this.axisBri);
    addParameter("Ticks",   this.tickBri);
    addParameter("TickLen", this.tickLen);
    addParameter("Major",   this.majorEvery);
    addParameter("Lines",   this.lines);
    addParameter("Spread",  this.spread);
    addParameter("Cross",   this.cross);
    addParameter("AxisY",   this.axisPos);
    addParameter("Thick",   this.thick);
    addParameter("You",     this.youBri);
    addParameter("YouSize", this.youSize);
    addParameter("Beacon",  this.beacon);
    addParameter("BcnRate", this.beaconRate);
    addParameter("Reach",   this.reach);
    addParameter("Drop",    this.dropLine);
    addParameter("Reveal",  this.reveal);
    addParameter("Outward", this.fromCentre);
    addParameter("Dim",     this.dim);
    addParameter("Hue",     this.hue);
    addParameter("HueRng",  this.hueRange);
    addParameter("HueYou",  this.hueYou);
    addParameter("Sat",     this.satAxis);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Repeat",  this.repeat);
    addParameter("Sym",     this.symmetry);
    addParameter("Vert",    this.vertical);
  }

  private static float expToU(float e) {
    return (e - EXP_LO) / (EXP_HI - EXP_LO);
  }

  // Fixed positions, so they are worked out once at load rather than fifteen
  // divisions per pixel.
  private static final float[] MARK_U = new float[MARK_EXP.length];
  private static final boolean[] MARK_MAJOR3 = new boolean[MARK_EXP.length];
  static {
    for (int i = 0; i < MARK_EXP.length; i++) MARK_U[i] = expToU(MARK_EXP[i]);
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    sAxis    = axisBri.getValuef() / 100f;
    sTick    = tickBri.getValuef() / 100f;
    sTickLen = tickLen.getValuef() / 100f * 0.16f;
    sMajor   = majorEvery.getValuef();
    sAxisY   = axisPos.getValuef() / 100f;
    sLines   = clampi(Math.round(lines.getValuef()), 1, 8);
    sSpread  = spread.getValuef() / 100f;
    sCross   = cross.isOn();
    sThick   = 0.003f + thick.getValuef() / 100f * 0.020f;
    sYou     = youBri.getValuef() / 100f;
    sYouSize = 0.004f + youSize.getValuef() / 100f * 0.045f;
    sReach   = reach.getValuef() / 100f * 0.30f;
    sDrop    = dropLine.getValuef() / 100f;
    sReveal  = reveal.getValuef() / 100f;
    sOutward = fromCentre.getValuef() / 100f;
    sDim     = dim.getValuef() / 100f;
    sBr      = bright.getValuef();
    sBk      = black.getValuef() / 100f;
    sHue     = hue.getValuef();
    sHueRng  = hueRange.getValuef();
    sHueYou  = hueYou.getValuef();
    sSat     = satAxis.getValuef();
    sVert    = vertical.isOn();

    float rate = beaconRate.getValuef() / 100f * 1.4f;
    sBeacon = 1f - beacon.getValuef() / 100f * 0.5f
            * (0.5f + 0.5f * (float) Math.sin(time * rate * 3.1416f));

    humanU = expToU(EXP_HUMAN);

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
        colors[row.points[ci].index] = diagramAt(u, v);
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
        colors[ring.points[pi].index] = diagramAt(u, v);
      }
    }
  }

  private int diagramAt(float u, float v) {
    float along  = sVert ? v : u;
    float across = sVert ? u : v;

    // The reveal can sweep left to right, or grow outward from human scale in
    // both directions at once - which is the truer gesture for this scene.
    float lin = along;
    float rad = Math.abs(along - humanU) / Math.max(humanU, 1f - humanU);
    float progress = lin + (rad - lin) * sOutward;
    if (progress > sReveal) return LXColor.BLACK;

    // One device draws every line. Four separate devices meant four full
    // passes over every pixel and four channel blends; this is one pass.
    float bri = 0f;
    boolean isYou = false;
    float hueAlong = along;

    for (int k = 0; k < sLines; k++) {
      float pos = (sLines == 1) ? sAxisY
                : sAxisY + ((float) k / (sLines - 1) - 0.5f) * sSpread;
      float b = axisAt(along, across, pos);
      if (b > bri) { bri = b; isYou = axIsYou; hueAlong = along; }
      if (sCross) {
        float b2 = axisAt(across, along, pos);
        if (b2 > bri) { bri = b2; isYou = axIsYou; hueAlong = across; }
      }
    }

    if (sDim > 0f) bri *= 1f - sDim * 0.95f;
    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;

    float h;
    float s = sSat;
    if (isYou) {
      h = sHueYou;
      s = sSat * 0.55f;
    } else {
      h = sHue + sHueRng * hueAlong;
    }
    h = ((h % 360f) + 360f) % 360f;
    return LXColor.hsb(h, clamp01(s / 100f) * 100f, clamp01(bri) * 100f);
  }

  private float axisAt(float along, float across, float axisY) {
    float d = Math.abs(across - axisY);
    float bri = 0f;
    axIsYou = false;

    // the axis
    if (sAxis > 0f && d < sThick) {
      bri = (1f - d / sThick) * sAxis;
    }

    // decade ticks - skipped entirely for pixels nowhere near this axis,
    // which is most of them
    if (sTick > 0f && d < sTickLen) {
      for (int i = 0; i < MARK_EXP.length; i++) {
        float du = Math.abs(along - MARK_U[i]);
        if (du < sThick * 0.9f) {
          boolean major = (i % (int) Math.max(1, sMajor)) == 0;
          float len = sTickLen * (major ? 1f : 0.55f);
          if (d < len) {
            float t = (1f - du / (sThick * 0.9f)) * (1f - d / len) * sTick;
            if (t > bri) bri = t;
          }
        }
      }
    }

    // light spilling along the axis from the marker
    if (sReach > 0f) {
      float du = Math.abs(along - humanU);
      if (du < sReach && d < sThick * 2.2f) {
        float s = (1f - du / sReach);
        s = s * s * sYou * 0.5f;
        if (s > bri) bri = s;
      }
    }

    // the vertical drop line locating the marker on the axis
    if (sDrop > 0f) {
      float du = Math.abs(along - humanU);
      float top = Math.min(axisY, axisY - 0.12f);
      if (du < sThick * 0.8f && across > top && across < axisY) {
        float s = (1f - du / (sThick * 0.8f)) * sDrop * 0.55f;
        if (s > bri) bri = s;
      }
    }

    // YOU ARE HERE
    if (sYou > 0f) {
      float dx = along - humanU;
      float dy = across - axisY;
      float dd2 = dx * dx + dy * dy;
      if (dd2 < sYouSize * sYouSize) {
        float c = 1f - (float) Math.sqrt(dd2) / sYouSize;
        c = c * c * sYou * sBeacon;
        if (c > bri * 0.5f) { bri = Math.max(bri, c); axIsYou = true; }
      }
    }

    return bri;
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
