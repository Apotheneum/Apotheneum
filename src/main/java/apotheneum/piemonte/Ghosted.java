/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Ghosted — a starburst floor unrolled onto the walls. Thin dotted comet
 * lines fire fast from the floor to the sky, launching in a radar sweep that
 * marches clockwise around the structure — a new line every tenth of a
 * second, a full lap every few seconds. Each comet accelerates as it climbs,
 * dragging a dotted tail through the color field: hot red-orange at the
 * floor, a white shockwave band mid-height, azure up top, the whole balance
 * slowly cycling red-blue. The field breathes in swells — build, climax,
 * near-blackout, re-burst. Bipolar Speed reverses the sweep; Wow deepens the
 * swells and packs the sky. Modeled frame-by-frame on the wave.mp4 floor
 * piece.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Ghosted extends StrandPattern {

  private static final int MAX_COMETS = 80;
  // measured: climax heads cross the field in 0.4-0.6s, dim phase ~0.7 heights/s
  private static final double V_CLIMAX = 0.0033;  // heights per ms
  private static final double V_DIM = 0.0013;
  private static final double LAP_MS = 2300;      // sweep lap
  private static final double SWELL_MS = 5000;    // build->climax->blackout cycle
  private static final double HUE_CYCLE_MS = 11500;
  private static final double DOT_PITCH = 2.6;    // dotted-body spacing

  public final DiscreteParameter lines =
    new DiscreteParameter("Lines", 36, 12, 60)
    .setDescription("Comet lines fired per sweep lap");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How much the music drives the swells");

  private final class Surface {
    boolean inited;
    final double[] x = new double[MAX_COMETS];
    final double[] y = new double[MAX_COMETS];   // normalized, 1 = floor, 0 = top
    final double[] vf = new double[MAX_COMETS];
    final int[] grp = new int[MAX_COMETS]; // 0 = red group, 1 = blue group
    final boolean[] alive = new boolean[MAX_COMETS];
    void reset() {
      for (int i = 0; i < MAX_COMETS; ++i) this.alive[i] = false;
      this.inited = true;
    }
    void fire(double x01, int grp) {
      for (int i = 0; i < MAX_COMETS; ++i) {
        if (this.alive[i]) continue;
        this.grp[i] = grp;
        this.x[i] = x01;
        this.y[i] = 1.04 + Math.random() * 0.05;
        this.vf[i] = 0.8 + Math.random() * 0.4;
        this.alive[i] = true;
        return;
      }
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double sweep = 0;     // 0..1 azimuth of the radar sweep
  private double laps = 0;      // unwrapped lap counter (for group hashing)
  private double spawnAcc = 0;
  private double swellT = 0;

  public Ghosted(LX lx) {
    // Base registers color (hue shift), bipolar speed (climb rate + sweep
    // direction), size (tail length), wow (swell pressure + density).
    super(lx, 0.5, -1, 1, 0.5, 0, 1);
    addParameter("lines", this.lines);
    addParameter("sensitivity", this.sensitivity);
    addTargetParameter();
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  /** Swell envelope: build -> climax -> collapse -> near-black -> re-burst. */
  private double swellEnv() {
    final double p = (this.swellT % SWELL_MS) / SWELL_MS;
    if (p < 0.50) {
      final double u = p / 0.50;
      return 0.25 + 0.75 * u * u; // build
    } else if (p < 0.80) {
      return 1.0; // climax hold
    } else if (p < 0.90) {
      return 1.0 - (p - 0.80) / 0.10 * 0.95; // collapse
    }
    return 0.16; // lull: dim but never dead — scattered life persists
  }

  @Override
  protected void advance(double deltaMs) {
    final double speed = getSpeed();
    final double mag = Math.max(0.05, Math.abs(speed));
    final double wow = getWow();
    // music pushes the swell clock so climaxes land with the energy
    final double push = 1.0 + this.levelEnv * this.sensitivity.getValue() * 1.5
      + (this.beat ? this.beatLevel * 0.6 : 0);
    this.swellT += deltaMs * mag * 2 * push * (1 + wow * 0.4);

    // radar sweep marches around the structure; sign of Speed sets direction
    final double lap = LAP_MS / (mag * 2) / (1 + wow * 0.5);
    this.sweep += (speed >= 0 ? 1 : -1) * deltaMs / lap;
    this.sweep -= Math.floor(this.sweep);
    this.laps += deltaMs / lap;

    // sequential firing at the sweep azimuth (~lap / lines interval)
    final int nLines = Math.min(MAX_COMETS,
      this.lines.getValuei() + (int) Math.round(wow * 16));
    final double env = swellEnv();
    this.spawnAcc += deltaMs * nLines / lap * (0.45 + 0.55 * env);
    while (this.spawnAcc >= 1) {
      this.spawnAcc -= 1;
      final double jitter = (Math.random() - 0.5) * 0.02;
      // groups of consecutive lines share a color: the sweep fires red packs
      // and blue packs as it circles
      final int chunk = (int) (((this.sweep % 1.0) + 1.0) % 1.0 * 8);
      // red<->blue balance drifts on the hue cycle instead of a fixed 50/50
      final double cyc = 0.5 + 0.5 * Math.sin(2 * Math.PI * this.timeMs / HUE_CYCLE_MS);
      final int grp = (hashd(chunk * 53 + (int) this.laps * 97) < 0.25 + 0.5 * cyc) ? 0 : 1;
      final double az = ((this.sweep + jitter) % 1.0 + 1.0) % 1.0;
      this.cube.fire(az, grp);
      this.cylinder.fire(az, grp);
    }
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final Surface s = isCube ? this.cube : this.cylinder;
    if (!s.inited) s.reset();
    final int w = o.width();
    final int h = o.height();
    final double mag = Math.max(0.05, Math.abs(getSpeed()));
    final double wow = getWow();
    final double hueShift = LXColor.h(getColor());
    final double env = swellEnv();

    // the color field: hot floor core -> white shockwave band -> azure sky
    final int red = LXColor.hsb((float) (((8 + hueShift) % 360)), 100, 100);
    final int blue = LXColor.hsb((float) (((214 + hueShift) % 360)), 96, 100);
    final int tq = (int) (this.timeMs / 60);

    // climb speed rides the swell: dim crawl in lulls, racing at climax
    final double vClimb = LXUtils.lerp(V_DIM, V_CLIMAX, env) * mag * 2;
    final double tailN = (0.42 + getSize() * 0.55) * (1 + wow * 0.3); // taller lines
    final double envB = 0.30 + 0.70 * env;

    for (int i = 0; i < MAX_COMETS; ++i) {
      if (!s.alive[i]) continue;
      // outward acceleration: slow near the floor, fast approaching the top
      final double accel = 0.45 + 1.1 * (1.0 - s.y[i]);
      s.y[i] -= s.vf[i] * vClimb * accel * deltaMs;
      if (s.y[i] <= -0.12) { s.alive[i] = false; continue; }

      final int x = (int) Math.round(s.x[i] * w);
      final double headF = LXUtils.clamp(s.y[i], -0.1, 1.1) * (h - 1);
      final int headY = (int) Math.round(headF);
      final int tailLen = (int) (tailN * h);

      for (int k = 0; k <= tailLen; ++k) {
        final int yy = headY + k; // tail trails below, toward the floor
        if (yy < 0 || yy >= h) continue;
        // dotted body: dash every DOT_PITCH cells (the head is always lit)
        if (k > 1) {
          final double dphase = (k + tq * 0.35) % DOT_PITCH;
          if (dphase > DOT_PITCH * 0.55) continue;
        }
        final double fall = (k == 0) ? 1.0 : Math.exp(-1.6 * k / Math.max(1, tailLen));
        if (fall < 0.02) break;
        // comet body carries its group color (red packs, blue packs), with the
        // white shockwave band still glowing through mid-height
        final double yn = (double) yy / (h - 1);
        final int grpC = (s.grp[i] == 0) ? red : blue;
        int c;
        if (k == 0) {
          c = LXColor.WHITE; // white-hot head
        } else if (yn > 0.40 && yn < 0.55) {
          final double m = 1.0 - Math.abs(yn - 0.475) / 0.075;
          c = LXColor.lerp(grpC, LXColor.WHITE, (float) (m * 0.3));
        } else {
          c = grpC;
        }
        final double bb = fall * envB * (0.75 + 0.25 * hashd(i * 977 + yy * 131 + tq));
        addPix(o, x, yy, c, bb);
        addPix(o, x + 1, yy, c, bb * 0.55); // thicker: two-column body
      }
    }

    // --- always alive: concentric dotted rings hugging the floor (the
    // reference's center rings) and scattered twinkle dots through the lulls ---
    final int ringC = LXColor.lerp(red, LXColor.WHITE, 0.35f);
    for (int r2 = 0; r2 < 3; ++r2) {
      final int ry = h - 2 - r2 * 2;
      if (ry < 0) continue;
      final double ringB = (0.20 + 0.30 * env) * (1.0 - r2 * 0.22);
      final double ringSpin = this.timeMs * 0.004 * (1 + r2 * 0.5);
      for (int x2 = 0; x2 < w; x2 += 3) {
        final double dp = ((x2 + ringSpin) % 3.0) / 3.0;
        if (dp > 0.6) continue;
        addPix(o, x2, ry, ringC, ringB * (0.6 + 0.4 * hashd(x2 * 31 + r2 * 97 + tq)));
      }
    }
    // white shockwave ring at mid-height, swelling with the climax
    final int shockY = (int) (h * 0.475);
    final double shockB = 0.06 + 0.24 * env;
    for (int x2 = 0; x2 < w; x2 += 2) {
      addPix(o, x2, shockY, LXColor.WHITE, shockB * (0.5 + 0.5 * hashd(x2 * 53 + tq * 3)));
    }
    // lull scatter: random dim dots keep the canvas breathing when the swell dips
    if (env < 0.5) {
      final int nScatter = (int) (w * h * 0.006 * (1.0 - env));
      for (int k2 = 0; k2 < nScatter; ++k2) {
        final int sx3 = (int) (hashd(k2 * 31 + tq * 977) * w);
        final int sy3 = (int) (hashd(k2 * 53 + tq * 613) * h);
        final double tw3 = hashd(k2 * 97 + tq * 131);
        if (tw3 < 0.5) continue;
        addPix(o, sx3, sy3, (tw3 > 0.9) ? LXColor.WHITE : blue, 0.2 + 0.5 * tw3);
      }
    }

    // --- Wow: a blue gradient rises from the floor, radiating upward ---
    if (wow > 0.05) {
      final int gradBlue = LXColor.hsb((float) (((215 + hueShift) % 360)), 88, 100);
      final double gradH = h * (0.25 + 0.5 * wow);
      for (int y = h - 1; y >= h - (int) gradH && y >= 0; --y) {
        final double t = (h - 1 - y) / gradH;
        // upward-radiating pulse waves ride the gradient
        final double wave = 0.7 + 0.3 * Math.sin(2 * Math.PI * (t * 2.5 - this.timeMs * 0.0007));
        final double gb = wow * 0.45 * Math.pow(1.0 - t, 1.5) * wave;
        if (gb < 0.01) break;
        for (int x = 0; x < w; ++x) {
          addPix(o, x, y, gradBlue, gb);
        }
      }

      // --- white dotted stars stream from top and bottom, crossing mid-air ---
      final int nStars = (int) (wow * 22);
      final double starV = 0.00045 * this.timeMs;
      for (int i2 = 0; i2 < nStars; ++i2) {
        final int sx = (int) (hashd(i2 * 31 + 7) * w);
        final double sp = 0.6 + 0.8 * hashd(i2 * 61 + 3);
        // falling star
        final double yd = ((hashd(i2 * 97 + 11) + starV * sp) % 1.0) * h;
        addPix(o, sx, (int) yd, LXColor.WHITE, 0.55 + 0.35 * hashd(i2 + tq));
        // rising star, offset column so the streams visibly cross
        final int sx2 = (int) (hashd(i2 * 43 + 17) * w);
        final double yu = (1.0 - ((hashd(i2 * 53 + 29) + starV * sp) % 1.0)) * h;
        addPix(o, sx2, (int) yu, LXColor.WHITE, 0.55 + 0.35 * hashd(i2 * 7 + tq));
      }
    }
  }
}
