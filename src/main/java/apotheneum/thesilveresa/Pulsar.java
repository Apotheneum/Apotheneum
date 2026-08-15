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
@LXComponent.Name("Pulsar")
public class Pulsar extends ApotheneumPattern {

  private static final float TWO_PI = 6.2831855f;

  // Rotation - a pulsar keeps time better than most clocks

  private final CompoundParameter period = new CompoundParameter("Period", 35, 1, 100)
    .setDescription("Rotation period - low is a millisecond pulsar, high is a very slow sweep");
  private final CompoundParameter beams = new CompoundParameter("Beams", 2, 1, 4)
    .setDescription("Emission beams - a real pulsar has two, from opposite poles");
  private final CompoundParameter beamWidth = new CompoundParameter("Width", 18, 1, 100)
    .setDescription("Angular width of the beam");
  private final CompoundParameter misalign = new CompoundParameter("Tilt", 30, 0, 100)
    .setDescription("Angle between the magnetic and rotation axes");

  // Beam

  private final CompoundParameter sweep = new CompoundParameter("Sweep", 70, 0, 100)
    .setDescription("How far the beam reaches across the surface");
  private final CompoundParameter falloff = new CompoundParameter("Fall", 45, 0, 100)
    .setDescription("How sharply the beam fades with distance");
  private final CompoundParameter smear = new CompoundParameter("Smear", 35, 0, 100)
    .setDescription("Persistence trailing behind the sweep");
  private final CompoundParameter duty = new CompoundParameter("Duty", 25, 1, 100)
    .setDescription("Fraction of the rotation the beam is visible");

  // Star

  private final CompoundParameter starSize = new CompoundParameter("Star", 30, 0, 100)
    .setDescription("Size of the neutron star itself");
  private final CompoundParameter starBri = new CompoundParameter("StarBri", 85, 0, 100)
    .setDescription("Brightness of the star body");
  private final CompoundParameter satStar = new CompoundParameter("SatStr", 45, 0, 100)
    .setDescription("Star saturation, independent of everything else");
  private final CompoundParameter pulseDepth = new CompoundParameter("Pulse", 20, 0, 100)
    .setDescription("How far the star dims between pulses");
  private final CompoundParameter field = new CompoundParameter("Field", 55, 0, 100)
    .setDescription("Magnetic field lines arcing from pole to pole");
  private final CompoundParameter fieldN = new CompoundParameter("FieldN", 6, 2, 20)
    .setDescription("How many field loops");
  private final CompoundParameter fieldReach = new CompoundParameter("FieldRch", 45, 5, 100)
    .setDescription("How far the field extends");
  private final CompoundParameter hueField = new CompoundParameter("HueFld", 225, 0, 360)
    .setDescription("Field line hue");
  private final CompoundParameter hueStar = new CompoundParameter("HueStr", 300, 0, 360)
    .setDescription("Star body hue");
  private final CompoundParameter limb = new CompoundParameter("Limb", 60, 0, 100)
    .setDescription("Brightening at the star's edge");
  private final CompoundParameter shadow = new CompoundParameter("Shadow", 0, 0, 100)
    .setDescription("The star's shadow falling across the field loops");
  private final CompoundParameter shadowAng = new CompoundParameter("ShadAng", 90, 0, 360)
    .setDescription("Direction the shadow falls");
  private final CompoundParameter glowStar = new CompoundParameter("GlowStr", 40, 0, 100)
    .setDescription("Halo around the star");
  private final CompoundParameter glowField = new CompoundParameter("GlowFld", 35, 0, 100)
    .setDescription("Softness of the field loops");
  private final CompoundParameter glowJet = new CompoundParameter("GlowJet", 30, 0, 100)
    .setDescription("Halo around the jets");
  private final CompoundParameter glitch = new CompoundParameter("Glitch", 0, 0, 100)
    .setDescription("Starquakes - sudden tiny jumps in the rotation rate");
  private final CompoundParameter spinDown = new CompoundParameter("SpinDn", 0, 0, 100)
    .setDescription("The slow loss of rotational energy over time");

  // Position

  private final CompoundParameter posX = new CompoundParameter("PosX", 50, 0, 100)
    .setDescription("Where the star sits horizontally");
  private final CompoundParameter posY = new CompoundParameter("PosY", 50, 0, 100)
    .setDescription("Where the star sits vertically");

  // Color

  private final CompoundParameter hue = new CompoundParameter("Hue", 195, 0, 360)
    .setDescription("Beam hue");
  private final CompoundParameter hueRange = new CompoundParameter("HueRng", 25, -180, 180)
    .setDescription("Hue shift along the beam");
  private final CompoundParameter satBeam = new CompoundParameter("Sat", 40, 0, 100)
    .setDescription("Saturation at the star");
  private final CompoundParameter satRange = new CompoundParameter("SatRng", 35, -100, 100)
    .setDescription("Saturation shift along the beam");
  private final CompoundParameter bright = new CompoundParameter("Bright", 90, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");
  private final BooleanParameter cylSweep = new BooleanParameter("CylSwp", true)
    .setDescription("On the cylinder, sweep the beam around the walls");

  private float phase = 0f;
  private float rateNow = 1f;
  private float glitchTimer = 0f;
  private float spinLoss = 1f;

  private float sPhase, sBeamW, sBeams, sSweep, sFall, sSmear, sDuty;
  private float sStar, sPX, sPY, sTilt;
  private float sField, sFieldN, sFieldRch, sHueFld, sHueStr, sLimb;
  private float sStarBri, sSatStr, sPulse, sShadow, sShadAng;
  private float sGlowStr, sGlowFld, sGlowJet;
  private float sBr, sBk, sHue, sHueRng, sSat, sSatRng;
  private boolean sCylSweep;

  public Pulsar(LX lx) {
    super(lx);
    addParameter("Period", this.period);
    addParameter("Beams",  this.beams);
    addParameter("Width",  this.beamWidth);
    addParameter("Tilt",   this.misalign);
    addParameter("Sweep",  this.sweep);
    addParameter("Fall",   this.falloff);
    addParameter("Smear",  this.smear);
    addParameter("Duty",   this.duty);
    addParameter("Star",   this.starSize);
    addParameter("StarBri", this.starBri);
    addParameter("SatStr", this.satStar);
    addParameter("Pulse",  this.pulseDepth);
    addParameter("Field",  this.field);
    addParameter("FieldN", this.fieldN);
    addParameter("FieldRch", this.fieldReach);
    addParameter("HueFld", this.hueField);
    addParameter("HueStr", this.hueStar);
    addParameter("Limb",   this.limb);
    addParameter("Shadow", this.shadow);
    addParameter("ShadAng", this.shadowAng);
    addParameter("GlowStr", this.glowStar);
    addParameter("GlowFld", this.glowField);
    addParameter("GlowJet", this.glowJet);
    addParameter("Glitch", this.glitch);
    addParameter("SpinDn", this.spinDown);
    addParameter("PosX",   this.posX);
    addParameter("PosY",   this.posY);
    addParameter("Hue",    this.hue);
    addParameter("HueRng", this.hueRange);
    addParameter("Sat",    this.satBeam);
    addParameter("SatRng", this.satRange);
    addParameter("Bright", this.bright);
    addParameter("Black",  this.black);
    addParameter("Repeat", this.repeat);
    addParameter("Sym",    this.symmetry);
    addParameter("CylSwp", this.cylSweep);
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);

    // period knob is inverted: low value means fast rotation
    float per = 0.06f + (float) Math.pow(period.getValuef() / 100f, 2f) * 40f;
    float baseRate = TWO_PI / per;

    // spin-down: a pulsar loses rotational energy and slows, very gradually
    float sd = spinDown.getValuef() / 100f;
    if (sd > 0f) {
      spinLoss -= sd * dt * 0.004f;
      if (spinLoss < 0.25f) spinLoss = 0.25f;
    } else {
      spinLoss += (1f - spinLoss) * Math.min(1f, dt * 0.5f);
    }

    // glitches: starquakes make the rotation jump forward suddenly
    float gl = glitch.getValuef() / 100f;
    rateNow = baseRate * spinLoss;
    if (gl > 0f) {
      glitchTimer -= dt;
      if (glitchTimer <= 0f) {
        glitchTimer = 2.5f + (1f - gl) * 20f;
        phase += 0.10f * gl * TWO_PI;
      }
    }

    phase += rateNow * dt;
    if (phase > TWO_PI) phase -= TWO_PI;

    sPhase    = phase;
    sBeams    = clampi(Math.round(beams.getValuef()), 1, 4);
    sBeamW    = 0.02f + beamWidth.getValuef() / 100f * 0.9f;
    sSweep    = 0.05f + sweep.getValuef() / 100f * 1.1f;
    sFall     = 0.2f + falloff.getValuef() / 100f * 3.5f;
    sSmear    = smear.getValuef() / 100f;
    sDuty     = duty.getValuef() / 100f;
    sStar     = starSize.getValuef() / 100f * 0.20f;
    sField    = field.getValuef() / 100f;
    sFieldN   = fieldN.getValuef();
    sFieldRch = 0.05f + fieldReach.getValuef() / 100f * 0.55f;
    sHueFld   = hueField.getValuef();
    sHueStr   = hueStar.getValuef();
    sLimb     = limb.getValuef() / 100f;
    sStarBri  = starBri.getValuef() / 100f;
    sSatStr   = satStar.getValuef();
    sPulse    = pulseDepth.getValuef() / 100f * 0.55f;
    sShadow   = shadow.getValuef() / 100f;
    sShadAng  = shadowAng.getValuef() * 3.14159f / 180f;
    sGlowStr  = glowStar.getValuef() / 100f;
    sGlowFld  = glowField.getValuef() / 100f;
    sGlowJet  = glowJet.getValuef() / 100f;
    sTilt     = misalign.getValuef() / 100f;
    sPX       = posX.getValuef() / 100f;
    sPY       = posY.getValuef() / 100f;
    sBr       = bright.getValuef();
    sBk       = black.getValuef() / 100f;
    sHue      = hue.getValuef();
    sHueRng   = hueRange.getValuef();
    sSat      = satBeam.getValuef();
    sSatRng   = satRange.getValuef();
    sCylSweep = cylSweep.isOn();

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
        colors[row.points[ci].index] = beamAt(u, v);
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
        if (sCylSweep) {
          // Around the cylinder the beam is a genuine lighthouse: the angle IS
          // the position on the wall, so the sweep travels the circumference.
          float ang = (float) pi / np * TWO_PI;
          colors[ring.points[pi].index] = sweepAt(ang, v);
        } else {
          float g = (float) pi / np * rep;
          float u;
          if (sym) {
            float gg = g - 2f * (float) Math.floor(g * 0.5f);
            u = gg <= 1f ? gg : 2f - gg;
          } else {
            u = g - (float) Math.floor(g);
          }
          colors[ring.points[pi].index] = beamAt(u, v);
        }
      }
    }
  }

  // beam seen as a rotating spoke on a flat surface
  private int beamAt(float u, float v) {
    float dx = u - sPX, dy = v - sPY;
    float d = (float) Math.sqrt(dx * dx + dy * dy);

    float bri = 0f, along = 0f;
    int kind = 0;   // 0 jet, 1 field, 2 star

    if (d > 1e-5f) {
      float ang = (float) Math.atan2(dy, dx);

      // Magnetic field: a dipole's field lines follow r = L sin^2(theta) from
      // the axis, so lines of constant L are the familiar loops running pole
      // to pole. This is the element that makes it read as a magnetosphere
      // rather than a spinning searchlight.
      if (sField > 0f && d < sFieldRch) {
        float rel = ang - sPhase;
        float sn = (float) Math.sin(rel);
        float st = sn * sn;
        if (st > 0.02f) {
          float L = d / st;
          float q = L * sFieldN / sFieldRch;
          float frac = q - (float) Math.floor(q);
          float line = 1f - Math.abs(frac - 0.5f) * 2f;
          // Glow softens the ridge instead of sharpening it, so loops read as
          // luminous filaments rather than hairlines.
          float pw = 3f - 2f * sGlowFld;
          line = (float) Math.pow(line, pw);
          float fade = clamp01(1f - d / sFieldRch);
          float fv = line * fade * sField * 1.9f * clamp01(st * 2.2f);
          // the star blocks the loops passing behind it
          if (sShadow > 0f) {
            float sd = Math.abs(angDiff(ang, sShadAng));
            if (sd < 0.55f) fv *= 1f - sShadow * (1f - sd / 0.55f);
          }
          if (fv > bri) { bri = fv; along = clamp01(d / sFieldRch); kind = 1; }
        }
      }

      // the polar jets
      float best = 0f;
      for (int b = 0; b < sBeams; b++) {
        float ba = sPhase + b * TWO_PI / sBeams;
        float diff = angDiff(ang, ba);
        float w = sBeamW * (1f + 1.6f * sGlowJet) * (0.35f + 0.65f * Math.min(1f, d / Math.max(0.05f, sSweep)));
        float m = 1f - Math.abs(diff) / w;
        if (m > 0f) {
          if (diff > 0f && sSmear > 0f) m = Math.max(m, (1f - diff / (w * (1f + 4f * sSmear))) * 0.55f);
          if (m > best) best = m;
        }
      }
      if (best > 0f) {
        float reach = clamp01(1f - d / sSweep);
        float jv = best * (float) Math.pow(reach, sFall);
        if (jv > bri) { bri = jv; along = clamp01(d / sSweep); kind = 0; }
      }
    }

    // The star: a lit sphere with a bright limb, not a flat disc.
    if (sStar > 0f) {
      float pulse = (1f - sPulse) + sPulse * (0.5f + 0.5f * (float) Math.cos(sPhase * sBeams));
      if (d < sStar) {
        float n = d / sStar;
        float sphere = (float) Math.sqrt(Math.max(0f, 1f - n * n));
        // Limb now ADDS light at the rim rather than subtracting it from the
        // middle, which is why higher values used to darken the whole star.
        float edge = 1f + sLimb * 1.6f * (1f - sphere);
        float c = clamp01((0.55f + 0.45f * sphere) * edge) * pulse * sStarBri;
        if (c > bri) { bri = c; along = 0f; kind = 2; }
      } else if (sGlowStr > 0f) {
        float gw = sStar * (0.15f + sGlowStr * 1.6f);
        float h = (float) Math.exp(-(d - sStar) / gw);
        float c = h * h * pulse * sStarBri * 0.75f;
        if (c > bri) { bri = c; along = 0f; kind = 2; }
      }
    }

    return shadeKind(bri, along, kind);
  }

  private int shadeKind(float bri, float along, int kind) {
    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;
    float h, s;
    if (kind == 1) {
      h = sHueFld;
      s = sSat + sSatRng * along * 0.5f;
    } else if (kind == 2) {
      h = sHueStr;
      s = sSatStr;
    } else {
      h = sHue + sHueRng * along;
      s = sSat + sSatRng * along;
    }
    h = ((h % 360f) + 360f) % 360f;
    return LXColor.hsb(h, clamp01(s / 100f) * 100f, clamp01(bri) * 100f);
  }

  // beam seen as a sweep travelling around the cylinder wall
  private int sweepAt(float ang, float v) {
    float best = 0f;
    for (int b = 0; b < sBeams; b++) {
      float ba = sPhase + b * TWO_PI / sBeams;
      float diff = angDiff(ang, ba);
      float w = sBeamW * (0.6f + 0.4f * sDuty);
      float m = 1f - Math.abs(diff) / w;
      if (m > 0f) {
        if (diff > 0f && sSmear > 0f) m = Math.max(m, (1f - diff / (w * (1f + 4f * sSmear))) * 0.55f);
        if (m > best) best = m;
      }
    }
    // the misalignment between the magnetic and spin axes makes the beam ride
    // up and down the wall as it goes round
    float band = 0.5f + sTilt * 0.45f * (float) Math.sin(sPhase);
    float dv = Math.abs(v - band);
    float vert = clamp01(1f - dv / Math.max(0.05f, sSweep * 0.8f));
    float bri = best * (float) Math.pow(vert, sFall * 0.5f);
    return shade(bri, clamp01(dv * 2f));
  }

  private int shade(float bri, float along) {
    bri *= sBr / 100f;
    if (bri < sBk) return LXColor.BLACK;
    float h = sHue + sHueRng * along;
    h = ((h % 360f) + 360f) % 360f;
    float s = sSat + sSatRng * along;
    return LXColor.hsb(h, clamp01(s / 100f) * 100f, clamp01(bri) * 100f);
  }

  private static float angDiff(float a, float b) {
    float d = a - b;
    while (d > 3.14159f) d -= TWO_PI;
    while (d < -3.14159f) d += TWO_PI;
    return d;
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
