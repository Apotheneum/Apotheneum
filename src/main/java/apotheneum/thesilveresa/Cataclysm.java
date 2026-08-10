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
@LXComponent.Name("Cataclysm")
public class Cataclysm extends ApotheneumPattern {

  private static final int FW = 128, FH = 128;
  private static final int MAX_SHOCK = 6;
  private static final int NZ = 64;

  private static final float[] NOISE = new float[NZ * NZ];
  static {
    Random r = new Random(6626070L);
    float[] raw = new float[NZ * NZ];
    for (int i = 0; i < raw.length; i++) raw[i] = r.nextFloat();
    for (int p = 0; p < 2; p++) {
      float[] t = new float[NZ * NZ];
      for (int y = 0; y < NZ; y++) {
        for (int x = 0; x < NZ; x++) {
          float s = 0f;
          for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
              s += raw[((y + dy + NZ) % NZ) * NZ + ((x + dx + NZ) % NZ)];
          t[y * NZ + x] = s / 9f;
        }
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

  // Per-shock angular profile: an irregular ring of knots and dents rather
  // than evenly spaced spokes. Evenly spaced spokes are what read as a
  // kaleidoscope; real remnants are lumpy.
  private static final int ANGN = 256;
  private final float[][] angProf = new float[MAX_SHOCK][ANGN];
  private final float[][] angFine = new float[MAX_SHOCK][ANGN];
  private final float[][] angRay = new float[MAX_SHOCK][ANGN];

  // Buffer: the blast is evaluated once per frame and sampled by every
  // surface, instead of recomputed for all 14,000 exterior pixels.
  private static final int BW = 112, BH = 56;
  private final float[] bufBri = new float[BW * BH];
  private final float[] bufHue = new float[BW * BH];
  private final float[] bufSat = new float[BW * BH];
  private float nbBri, nbHue, nbSat;

  // Line-of-sight projection through the expanding shell. Integrating a
  // spherical shell along z is what produces limb brightening: the edge is
  // bright because you look through more material there, and the interior
  // stays filled and softly shaded. A ring outline can never do this.
  private static final int LUTN = 192;
  private final float[][] prof = new float[MAX_SHOCK][LUTN];
  private final float[] profMax = new float[MAX_SHOCK];

  private void buildProfile(int i, float R, float w, float fill) {
    float[] L = prof[i];
    float outer = R + 3f * w;
    float outer2 = outer * outer;
    float invW = 1f / w;
    float invR = 1f / Math.max(1e-6f, R);
    float peak = 0f;
    for (int k = 0; k < LUTN; k++) {
      float rho = (float) k / (LUTN - 1);
      float span = outer2 - rho * rho;
      if (span <= 0f) { L[k] = 0f; continue; }
      float zmax = (float) Math.sqrt(span);
      int NS = 12;
      float dz = 2f * zmax / NS;
      float sum = 0f;
      for (int t = 0; t < NS; t++) {
        float z = -zmax + (t + 0.5f) * dz;
        float r3 = (float) Math.sqrt(rho * rho + z * z);
        float dd = (r3 - R) * invW;
        float shell = (float) Math.exp(-dd * dd * 0.5f);
        // ejecta still filling the cavity behind the front
        float rn = r3 * invR;
        float inner = (r3 < R) ? fill * 0.15f * rn * rn : 0f;
        sum += (shell + inner) * dz;
      }
      L[k] = sum;
      if (sum > peak) peak = sum;
    }
    float inv = 1f / (peak > 1e-6f ? peak : 1f);
    for (int k = 0; k < LUTN; k++) L[k] *= inv;
    profMax[i] = 1f;
  }

  private float profAt(int i, float rho) {
    if (rho >= 1f) return 0f;
    float f = rho * (LUTN - 1);
    int k = (int) f;
    if (k < 0) k = 0;
    if (k >= LUTN - 1) return prof[i][LUTN - 1];
    float t = f - k;
    return prof[i][k] + (prof[i][k + 1] - prof[i][k]) * t;
  }

  private void buildAngular(int slot, Random r, int lobes) {
    float[] p = angProf[slot];
    float[] q = angFine[slot];
    // a few low-frequency lobes give the overall lopsidedness
    float[] amp = new float[7];
    float[] pha = new float[7];
    for (int k = 0; k < 7; k++) {
      amp[k] = (0.55f / (k + 1.4f)) * (0.4f + r.nextFloat());
      pha[k] = r.nextFloat() * 6.2832f;
    }
    for (int i = 0; i < ANGN; i++) {
      float a = i * 6.2832f / ANGN;
      float v = 0f;
      for (int k = 0; k < 7; k++) v += amp[k] * (float) Math.sin(a * (k + 2) + pha[k]);
      p[i] = v;
    }
    // higher-frequency roughness for the filament texture on the front
    float[] amp2 = new float[6];
    float[] pha2 = new float[6];
    for (int k = 0; k < 6; k++) {
      amp2[k] = (0.5f / (k + 1f)) * r.nextFloat();
      pha2[k] = r.nextFloat() * 6.2832f;
    }
    for (int i = 0; i < ANGN; i++) {
      float a = i * 6.2832f / ANGN;
      float v = 0f;
      for (int k = 0; k < 6; k++) v += amp2[k] * (float) Math.sin(a * (lobes + k * 5) + pha2[k]);
      q[i] = v;
    }
  }

  private void buildRays(int slot, Random r) {
    float[] q = angRay[slot];
    // many narrow spikes of differing strength - the streaming light of the
    // references is thin rays, not broad lobes
    for (int i = 0; i < ANGN; i++) q[i] = 0f;
    int n = 14 + r.nextInt(10);
    for (int k = 0; k < n; k++) {
      int c = r.nextInt(ANGN);
      float amp = 0.35f + r.nextFloat() * 0.65f;
      int w = 4 + r.nextInt(10);
      for (int d = -w; d <= w; d++) {
        float f = 1f - Math.abs(d) / (float) (w + 1);
        int idx = ((c + d) % ANGN + ANGN) % ANGN;
        q[idx] = Math.max(q[idx], amp * f * f);
      }
    }
    // smooth, so a ray is a soft shaft of light rather than a hard line
    float[] t = new float[ANGN];
    for (int i = 0; i < ANGN; i++) {
      float sum = 0f;
      for (int d = -3; d <= 3; d++) sum += q[((i + d) % ANGN + ANGN) % ANGN];
      t[i] = sum / 7f;
    }
    System.arraycopy(t, 0, q, 0, ANGN);
  }

  // Detonation

  private final TriggerParameter detonate = new TriggerParameter("Blast", this::fire)
    .setDescription("Trigger a detonation");
  private final CompoundParameter autoRate = new CompoundParameter("Auto", 0, 0, 100)
    .setDescription("Detonations per minute - 0 is manual only");
  private final CompoundParameter expand = new CompoundParameter("Expand", 40, 5, 100)
    .setDescription("How fast the shell expands");
  private final CompoundParameter decel = new CompoundParameter("Decel", 45, 0, 100)
    .setDescription("How much the shell slows as it sweeps up material");
  private final CompoundParameter shellW = new CompoundParameter("Shell", 25, 2, 100)
    .setDescription("Thickness of the shock front");
  private final CompoundParameter fade = new CompoundParameter("Fade", 45, 5, 100)
    .setDescription("How quickly the remnant dims");

  // Structure

  private final CompoundParameter rays = new CompoundParameter("Rays", 45, 0, 100)
    .setDescription("Rayleigh-Taylor fingers breaking the shell apart");
  private final CompoundParameter rayN = new CompoundParameter("RayN", 14, 3, 40)
    .setDescription("Scale of the finger structure");
  private final CompoundParameter knots = new CompoundParameter("Knots", 55, 0, 100)
    .setDescription("Bright clumps of ejecta studding the shell");
  private final CompoundParameter filament = new CompoundParameter("Filament", 50, 0, 100)
    .setDescription("Wispy filamentary texture through the remnant");
  private final CompoundParameter turb = new CompoundParameter("Turb", 40, 0, 100)
    .setDescription("Turbulent mixing in the ejecta");
  private final CompoundParameter flash = new CompoundParameter("Flash", 60, 0, 100)
    .setDescription("The initial burst of light");
  private final CompoundParameter remnant = new CompoundParameter("Remnant", 55, 0, 100)
    .setDescription("How much ejecta fills the cavity behind the front");
  private final CompoundParameter core = new CompoundParameter("Core", 70, 0, 100)
    .setDescription("Brilliance of the collapsed core");
  private final CompoundParameter coreSize = new CompoundParameter("CoreSz", 18, 2, 100)
    .setDescription("How compact the core is");
  private final CompoundParameter beam = new CompoundParameter("Beam", 65, 0, 100)
    .setDescription("Light streaming outward in rays from the core");
  private final CompoundParameter beamLen = new CompoundParameter("BeamLen", 55, 5, 100)
    .setDescription("How far the rays reach");
  private final CompoundParameter contrast = new CompoundParameter("Contrast", 60, 0, 100)
    .setDescription("Separation between bright filaments and dark cavities");
  private final CompoundParameter softness = new CompoundParameter("Soft", 55, 0, 100)
    .setDescription("How diffuse the ejecta is - low is crisp, high is a soft cloud");

  // Position

  private final CompoundParameter posX = new CompoundParameter("PosX", 50, 0, 100)
    .setDescription("Detonation point across");
  private final CompoundParameter posY = new CompoundParameter("PosY", 50, 0, 100)
    .setDescription("Detonation point down");
  private final CompoundParameter scatter = new CompoundParameter("Scatter", 0, 0, 100)
    .setDescription("Random placement of successive blasts");

  // Color

  private final CompoundParameter hueHot = new CompoundParameter("HueHot", 55, 0, 360)
    .setDescription("Hue at the shock front");
  private final CompoundParameter hueMid = new CompoundParameter("HueMid", 20, 0, 360)
    .setDescription("Hue of the mid-shell ejecta");
  private final CompoundParameter hueCool = new CompoundParameter("HueCool", 265, 0, 360)
    .setDescription("Hue of the outer shock front");
  private final CompoundParameter hueVary = new CompoundParameter("HueVary", 45, 0, 180)
    .setDescription("Element to element colour variation through the ejecta");
  private final CompoundParameter satHot = new CompoundParameter("SatHot", 15, 0, 100)
    .setDescription("Saturation at the front");
  private final CompoundParameter satCool = new CompoundParameter("SatCool", 70, 0, 100)
    .setDescription("Saturation of the remnant");
  private final CompoundParameter bright = new CompoundParameter("Bright", 92, 0, 100)
    .setDescription("Overall brightness");
  private final CompoundParameter black = new CompoundParameter("Black", 3, 0, 40)
    .setDescription("Cutoff below which pixels go dark");

  // Layout

  private final CompoundParameter repeat = new CompoundParameter("Repeat", 1, 1, 8)
    .setDescription("Cylinder tile count");
  private final BooleanParameter symmetry = new BooleanParameter("Sym", false)
    .setDescription("Mirror alternate faces and tiles");

  // Shockwaves

  private final float[] sAge = new float[MAX_SHOCK];
  private final float[] sRad = new float[MAX_SHOCK];
  private final float[] sX = new float[MAX_SHOCK];
  private final float[] sY = new float[MAX_SHOCK];
  private final float[] sSeed = new float[MAX_SHOCK];
  private final boolean[] sLive = new boolean[MAX_SHOCK];

  private final Random rng = new Random(1987L);
  private float time = 0f;
  private float autoTimer = 0f;
  private boolean pending = false;

  private float sShell, sRays, sRayN, sTurb, sFlash, sRemnant, sFade, sKnots, sFilament;
  private float sHueMid, sHueVary, sSoft, sCore, sCoreSz, sBeam, sBeamLen, sContrast;
  private final float[] sRadNow = new float[MAX_SHOCK];
  // values that depend only on the shock and the frame, not on the pixel
  private final float[] shDim = new float[MAX_SHOCK];
  private final float[] shCoreAge = new float[MAX_SHOCK];
  private final float[] shShellAge = new float[MAX_SHOCK];
  private final float[] shReach2 = new float[MAX_SHOCK];
  private float sBr, sBk, sHH, sHC, sSH, sSC;

  public Cataclysm(LX lx) {
    super(lx);
    addParameter("Blast",   this.detonate);
    addParameter("Auto",    this.autoRate);
    addParameter("Expand",  this.expand);
    addParameter("Decel",   this.decel);
    addParameter("Shell",   this.shellW);
    addParameter("Fade",    this.fade);
    addParameter("Rays",    this.rays);
    addParameter("RayN",    this.rayN);
    addParameter("Knots",   this.knots);
    addParameter("Filament", this.filament);
    addParameter("Turb",    this.turb);
    addParameter("Flash",   this.flash);
    addParameter("Remnant", this.remnant);
    addParameter("Core",    this.core);
    addParameter("CoreSz",  this.coreSize);
    addParameter("Beam",    this.beam);
    addParameter("BeamLen", this.beamLen);
    addParameter("Contrast", this.contrast);
    addParameter("Soft",    this.softness);
    addParameter("PosX",    this.posX);
    addParameter("PosY",    this.posY);
    addParameter("Scatter", this.scatter);
    addParameter("HueHot",  this.hueHot);
    addParameter("HueMid",  this.hueMid);
    addParameter("HueCool", this.hueCool);
    addParameter("HueVary", this.hueVary);
    addParameter("SatHot",  this.satHot);
    addParameter("SatCool", this.satCool);
    addParameter("Bright",  this.bright);
    addParameter("Black",   this.black);
    addParameter("Repeat",  this.repeat);
    addParameter("Sym",     this.symmetry);
  }

  private void fire() {
    pending = true;
  }

  private void ignite() {
    int slot = -1;
    float oldest = -1f;
    for (int i = 0; i < MAX_SHOCK; i++) {
      if (!sLive[i]) { slot = i; break; }
      if (sAge[i] > oldest) { oldest = sAge[i]; slot = i; }
    }
    if (slot < 0) return;
    float sc = scatter.getValuef() / 100f;
    sX[slot] = posX.getValuef() / 100f + (rng.nextFloat() - 0.5f) * sc * 0.8f;
    sY[slot] = posY.getValuef() / 100f + (rng.nextFloat() - 0.5f) * sc * 0.8f;
    sAge[slot] = 0f;
    sRad[slot] = 0f;
    sSeed[slot] = rng.nextFloat() * 10f;
    buildAngular(slot, rng, clampi(Math.round(rayN.getValuef()), 3, 40));
    buildRays(slot, rng);
    sLive[slot] = true;
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float) Math.min(deltaMs / 1000.0, 0.05);
    time += dt;

    if (pending) { ignite(); pending = false; }

    float auto = autoRate.getValuef();
    if (auto > 0f) {
      autoTimer -= dt;
      if (autoTimer <= 0f) {
        ignite();
        autoTimer = 60f / Math.max(1f, auto);
      }
    }

    float exp = expand.getValuef() / 100f * 0.55f;
    float dec = decel.getValuef() / 100f;
    float fd  = fade.getValuef() / 100f * 1.1f;

    sShell   = 0.012f + shellW.getValuef() / 100f * 0.16f;
    sRays    = rays.getValuef() / 100f;
    sRayN    = rayN.getValuef();
    sTurb    = turb.getValuef() / 100f;
    sFlash   = flash.getValuef() / 100f;
    sRemnant = remnant.getValuef() / 100f;
    sKnots   = knots.getValuef() / 100f;
    sHueMid  = hueMid.getValuef();
    sHueVary = hueVary.getValuef();
    sSoft    = softness.getValuef() / 100f;
    sCore    = core.getValuef() / 100f;
    sCoreSz  = 0.004f + coreSize.getValuef() / 100f * 0.055f;
    sBeam    = beam.getValuef() / 100f;
    sBeamLen = 0.05f + beamLen.getValuef() / 100f * 0.75f;
    sContrast = contrast.getValuef() / 100f;
    sFilament = filament.getValuef() / 100f;
    sFade    = fd;
    sBr      = bright.getValuef();
    sBk      = black.getValuef() / 100f;
    sHH      = hueHot.getValuef();
    sHC      = hueCool.getValuef();
    sSH      = satHot.getValuef();
    sSC      = satCool.getValuef();

    for (int i = 0; i < MAX_SHOCK; i++) {
      if (!sLive[i]) continue;
      sAge[i] += dt;
      // A blast wave decelerates as it sweeps up the surrounding medium -
      // the Sedov-Taylor solution goes as t^(2/5) rather than straight lines.
      float v = exp * (1f - dec + dec * (float) Math.pow(1.0 + sAge[i], -0.6));
      sRad[i] += v * dt;
      if (sAge[i] * sFade > 6f) sLive[i] = false;
      if (sLive[i]) {
        float w = sShell * (0.35f + 1.9f * sSoft);
        buildProfile(i, sRad[i], Math.max(0.010f, w), sRemnant);
        sRadNow[i] = sRad[i];
        // hoisted out of the pixel loop - identical values, computed once
        shDim[i] = (float) Math.exp(-sAge[i] * sFade * 0.45f);
        shCoreAge[i] = 0.4f + 0.6f * (float) Math.exp(-sAge[i] * 0.7f);
        shShellAge[i] = clamp01(1f - sAge[i] * 0.30f);
        float rr = Math.max(sRad[i] + w * 3f, sBeamLen);
        shReach2[i] = rr * rr;
      }
    }

    buildBuffer();
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
        colors[row.points[ci].index] = sampleBuf(u, v);
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
        colors[ring.points[pi].index] = sampleBuf(u, v);
      }
    }
  }

  private void blastAt(float u, float v) {
    float bri = 0f, temp = 0f, tint = 0f;

    for (int i = 0; i < MAX_SHOCK; i++) {
      if (!sLive[i]) continue;
      float dx = u - sX[i], dy = v - sY[i];
      float d2 = dx * dx + dy * dy;
      float R = sRadNow[i];
      if (d2 > shReach2[i]) continue;

      float d = (float) Math.sqrt(d2);
      float age = sAge[i];
      float dim = shDim[i];

      int ai = 0;
      float lump = 0f, fine = 0f, ray = 0f;
      if (d > 1e-5f) {
        ai = (int) ((fastAtan2(dy, dx) + 3.14159f) * (ANGN / 6.2832f));
        if (ai < 0) ai = 0; else if (ai >= ANGN) ai = ANGN - 1;
        lump = angProf[i][ai];
        fine = angFine[i][ai];
        ray = angRay[i][ai];
      }

      // The collapsed core: small, brilliant, and the source of everything
      // else. Both references are dominated by this point of light.
      if (sCore > 0f) {
        float c = sCoreSz / (sCoreSz + d);
        float cv = c * sCore * dim * shCoreAge[i];
        if (cv > bri) { bri = cv; temp = 1f; tint = 0f; }
      }

      // Light streaming outward in thin rays - the dominant texture of the
      // references, and the thing a shell alone can never produce.
      if (sBeam > 0f && ray > 0f && d > sCoreSz * 0.5f) {
        float fall = (float) Math.exp(-d / sBeamLen) * (0.35f / (0.35f + d));
        float rv = ray * fall * sBeam * dim * 2.4f;
        if (rv > bri) { bri = rv; temp = 0.88f; tint = 0f; }
      }

      // the flash
      if (sFlash > 0f && age < 0.55f) {
        float f = 1f - age / 0.55f;
        f = f * f * f * sFlash * clamp01(1f - d / 0.85f);
        if (f > bri) { bri = f; temp = 1f; tint = 0f; }
      }

      if (R <= 0f) continue;

      float n1 = nz(u * 6.5f + sSeed[i] * 4f, v * 6.5f - sSeed[i]);
      float squash = 1f + sRays * 0.30f * lump + sFilament * 0.10f * fine;
      if (sTurb > 0f) squash *= 1f + sTurb * 0.30f * (n1 - 0.5f);

      float p = profAt(i, d / Math.max(1e-4f, squash));
      if (p > 0.002f) {
        float clump = 1f;
        if (sKnots > 0f) clump *= 1f - sKnots * 0.75f * (1f - n1);
        if (sFilament > 0f) {
          float ridge = 1f - Math.abs(n1 - 0.5f) * 2.4f;
          clump *= 1f - sFilament * 0.5f * (1f - clamp01(ridge));
        }
        // push the ejecta apart into bright strands and dark cavities
        if (sContrast > 0f) {
          float c = clamp01((clump - 0.42f) * (1f + sContrast * 3.2f) + 0.42f);
          clump += (c - clump) * sContrast;
        }
        float val = p * clump * dim;
        if (val > bri) {
          bri = val;
          // spans the mid range so all three hue stops are reachable: the
          // front sits near HueMid, the deep interior climbs toward HueHot
          temp = (0.30f + 0.42f * clamp01(1f - d / Math.max(1e-4f, R)))
               * shShellAge[i];
          tint = n1 - 0.5f;
        }
      }
    }

    bri *= sBr / 100f;
    if (bri < sBk) { nbBri = 0f; nbHue = 0f; nbSat = 0f; return; }

    float h, sat;
    if (temp < 0.5f) {
      float t = temp * 2f;
      h = lerpHue(sHC, sHueMid, t);
      sat = sSC + (sSC * 0.15f) * t;
    } else {
      float t = (temp - 0.5f) * 2f;
      h = lerpHue(sHueMid, sHH, t);
      sat = sSC + (sSH - sSC) * t;
    }
    h += sHueVary * tint;
    h = ((h % 360f) + 360f) % 360f;

    nbBri = clamp01(bri);
    nbHue = h;
    nbSat = clamp01(sat / 100f) * 100f;
  }

  private boolean bufCleared = false;

  private void buildBuffer() {
    boolean any = false;
    for (int i = 0; i < MAX_SHOCK; i++) if (sLive[i]) { any = true; break; }
    if (!any) {
      // idle between detonations - clear once, then cost nothing per frame
      if (!bufCleared) {
        java.util.Arrays.fill(bufBri, 0f);
        java.util.Arrays.fill(bufHue, 0f);
        java.util.Arrays.fill(bufSat, 0f);
        bufCleared = true;
      }
      return;
    }
    bufCleared = false;
    for (int y = 0; y < BH; y++) {
      float v = (y + 0.5f) / BH;
      for (int x = 0; x < BW; x++) {
        blastAt((x + 0.5f) / BW, v);
        int c = y * BW + x;
        bufBri[c] = nbBri; bufHue[c] = nbHue; bufSat[c] = nbSat;
      }
    }
  }

  private int sampleBuf(float u, float v) {
    float fx = u * (BW - 1), fy = v * (BH - 1);
    int x0 = (int) fx, y0 = (int) fy;
    int x1 = x0 + 1 >= BW ? BW - 1 : x0 + 1;
    int y1 = y0 + 1 >= BH ? BH - 1 : y0 + 1;
    if (x0 < 0) x0 = 0; if (y0 < 0) y0 = 0;
    float tx = fx - x0, ty = fy - y0;
    int r0 = y0 * BW, r1 = y1 * BW;
    float b = bl(bufBri[r0 + x0], bufBri[r0 + x1], bufBri[r1 + x0], bufBri[r1 + x1], tx, ty);
    if (b < 0.002f) return LXColor.BLACK;
    float h = bl(bufHue[r0 + x0], bufHue[r0 + x1], bufHue[r1 + x0], bufHue[r1 + x1], tx, ty);
    float sa = bl(bufSat[r0 + x0], bufSat[r0 + x1], bufSat[r1 + x0], bufSat[r1 + x1], tx, ty);
    return LXColor.hsb(h, sa, b * 100f);
  }

  private static float bl(float c00, float c10, float c01, float c11, float tx, float ty) {
    float a = c00 + (c10 - c00) * tx;
    float b = c01 + (c11 - c01) * tx;
    return a + (b - a) * ty;
  }

  // take the short way round the colour wheel, otherwise a violet-to-orange
  // ramp detours through green
  private static float lerpHue(float a, float b, float t) {
    float d = b - a;
    while (d > 180f) d -= 360f;
    while (d < -180f) d += 360f;
    return a + d * t;
  }

  // Polynomial atan2, accurate to about 0.005 rad. The angle only selects one
  // of 256 buckets, so this lands in the same bucket as the exact call and
  // costs a fraction as much.
  private static float fastAtan2(float y, float x) {
    float ax = Math.abs(x), ay = Math.abs(y);
    float a = Math.min(ax, ay) / (Math.max(ax, ay) + 1e-18f);
    float sq = a * a;
    float r = ((-0.0464964749f * sq + 0.15931422f) * sq - 0.327622764f) * sq * a + a;
    if (ay > ax) r = 1.57079637f - r;
    if (x < 0f) r = 3.14159274f - r;
    if (y < 0f) r = -r;
    return r;
  }

  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

  private static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
