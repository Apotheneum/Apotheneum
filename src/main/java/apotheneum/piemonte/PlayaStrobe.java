/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * PlayaStrobe — UFOAbduction stripped to pure white. Blocks of white light
 * beam up the lanes with the measured velocity signature — fast entry, a
 * slow-motion crawl through the center, a burst out the top — and as each
 * block enters the center of its border it strobes briefly, a hard little
 * flutter before it glides on. Around them, thin white lines spin
 * horizontally like a cyclone, shearing faster near the top rim as the vortex
 * slowly climbs. The operator's Strobe button still fires the full-structure
 * pulsating white burst that decays away after release. Wow feeds the cyclone
 * and hardens every strobe.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class PlayaStrobe extends StrandPattern {

  private static final int MAX_HEADS = 64;
  private static final int MAX_LINES = 16;
  private static final int WHITE_SOFT = LXColor.hsb(210, 6, 100); // barely-cool white
  // UFOAbduction's measured rise profile
  private static final double V_ENTRY = 0.00135;
  private static final double V_STALL = 0.00024;
  private static final double V_EXIT = 0.0075;
  private static final double STALL_LO = 0.60;
  private static final double STALL_HI = 0.44;
  private static final double EXIT_AT = 0.30;
  private static final double IDLE_WAVE_MS = 900;
  private static final double STALL_STROBE_MS = 320; // brief flutter entering the stall
  private static final double DECAY_MS = 1500;       // button strobe fade-out
  private static final double STROBE_DUTY = 0.45;
  private static final double CYCLONE_REV_S = 0.30;

  public final DiscreteParameter lanes =
    new DiscreteParameter("Lanes", 12, 6, 24)
    .setDescription("Lanes the white blocks beam up");

  public final BooleanParameter strobe =
    new BooleanParameter("Strobe", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Fire a pulsating white strobe burst; hold to sustain");

  public final CompoundParameter rate =
    new CompoundParameter("Rate", 9, 2, 16)
    .setDescription("Strobe pulse rate in flashes per second");

  private final class Surface {
    boolean inited;
    final int[] lane = new int[MAX_HEADS];
    final double[] y = new double[MAX_HEADS];
    final double[] vf = new double[MAX_HEADS];      // variance factor
    final double[] stallMs = new double[MAX_HEADS]; // time spent in the stall band
    final boolean[] alive = new boolean[MAX_HEADS];
    void reset() {
      for (int i = 0; i < MAX_HEADS; ++i) this.alive[i] = false;
      this.inited = true;
    }
    void spawn(int lane) {
      for (int i = 0; i < MAX_HEADS; ++i) {
        if (this.alive[i]) continue;
        this.lane[i] = lane;
        this.y[i] = 1.02 + Math.random() * 0.10;
        this.vf[i] = 0.8 + Math.random() * 0.4;
        this.stallMs[i] = 0;
        this.alive[i] = true;
        return;
      }
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double waveTimer = 0;
  private double strobeEnv = 0;
  private double cyclone = 0; // shared spin phase

  public PlayaStrobe(LX lx) {
    // Base registers color (unused: pure white), speed (rise + spin), size (block thickness).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("lanes", this.lanes);
    addParameter("strobe", this.strobe);
    addParameter("rate", this.rate);
    addTargetParameter();
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  /** UFOAbduction's rise profile: fast entry, center stall, exit burst. */
  private static double riseProfile(double y) {
    if (y > STALL_LO) {
      final double u = LXUtils.clamp((y - STALL_LO) / 0.10, 0, 1);
      return LXUtils.lerp(V_STALL, V_ENTRY, u * u * (3 - 2 * u));
    } else if (y > STALL_HI) {
      return V_STALL;
    }
    final double u = LXUtils.clamp((STALL_HI - y) / (STALL_HI - EXIT_AT), 0, 1);
    return LXUtils.lerp(V_STALL, V_EXIT, u * u * (3 - 2 * u));
  }

  @Override
  protected void advance(double deltaMs) {
    final double speed = Math.max(0.05, getSpeed());
    final double wow = getWow();
    this.cyclone += deltaMs * 0.001 * CYCLONE_REV_S * speed * 2 * (1 + wow * 0.5);
    this.cyclone -= Math.floor(this.cyclone);

    // beat-locked launch waves with an idle cadence fallback
    this.waveTimer += deltaMs * speed * 2;
    if ((this.beat && this.beatLevel > 0.15) || this.waveTimer >= IDLE_WAVE_MS) {
      this.waveTimer = 0;
      final double strength = LXUtils.clamp(
        this.levelEnv * 1.6 + this.beatLevel * 0.4 + wow * 0.3, 0.2, 1);
      launchWave(this.cube, strength);
      launchWave(this.cylinder, strength);
    }

    // operator strobe: full while held, decaying burst after release
    if (this.strobe.isOn()) {
      this.strobeEnv = 1;
    } else if (this.strobeEnv > 0) {
      this.strobeEnv = Math.max(0, this.strobeEnv - deltaMs / DECAY_MS);
    }
  }

  private void launchWave(Surface s, double strength) {
    if (!s.inited) s.reset();
    final int n = this.lanes.getValuei();
    for (int l = 0; l < n; ++l) {
      if (hashd(l * 641 + (int) (this.timeMs * 3)) > 0.35 + strength * 0.55) continue;
      s.spawn(l);
    }
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final Surface s = isCube ? this.cube : this.cylinder;
    if (!s.inited) s.reset();
    final int w = o.width();
    final int h = o.height();
    final double speed = Math.max(0.05, getSpeed());
    final double wow = getWow();
    final int nLanes = this.lanes.getValuei();
    final double laneW = (double) w / nLanes;
    final int tq = (int) (this.timeMs / 70);

    // --- white blocks beaming up, fluttering as they enter the center stall ---
    for (int i = 0; i < MAX_HEADS; ++i) {
      if (!s.alive[i]) continue;
      final double yp = s.y[i];
      final boolean inStall = (yp <= STALL_LO && yp >= STALL_HI);
      // Wow = slow-motion: the drops decelerate and stretch into long streaks
      s.y[i] -= s.vf[i] * riseProfile(yp) * speed * 2 * deltaMs * (1.0 - wow * 0.65);
      if (s.y[i] <= -0.08) { s.alive[i] = false; continue; }
      if (inStall) {
        s.stallMs[i] += deltaMs;
      }

      final double vNorm = LXUtils.clamp(riseProfile(yp) / V_ENTRY, 0, 1.2);
      final double thickN = LXUtils.lerp(0.10 * (0.6 + getSize() * 0.9), 0.045, Math.min(1, vNorm))
        * (1.0 + wow * 0.6); // stretched bodies in slow motion
      final int thick = Math.max(2, (int) Math.round(thickN * h));
      final double tailN = LXUtils.clamp(vNorm * 0.8 * (1.0 + wow * 2.0), 0.12, 1.6);
      final int tail = (int) Math.round(tailN * h);

      // the brief strobe as the block enters its center stall
      double gate = 1.0;
      if (inStall && s.stallMs[i] < STALL_STROBE_MS * (1 + wow * 0.8)) {
        gate = (((int) (this.timeMs / 45)) & 1) == 0 ? 1.35 : 0.12; // ~11Hz flutter
      }

      final int x0 = (int) (s.lane[i] * laneW);
      final int x1 = Math.min(w - 1, (int) ((s.lane[i] + 1) * laneW) - 2);
      final double leadF = LXUtils.clamp(s.y[i], -0.1, 1.1) * (h - 1);
      final double trailF = leadF + (thick - 1);
      for (int x = x0; x <= x1; ++x) {
        for (int yy = (int) Math.floor(leadF) - 1; yy <= (int) Math.ceil(trailF) + 1; ++yy) {
          if (yy < 0 || yy >= h) continue;
          final double dOut = (yy < leadF) ? (leadF - yy) : ((yy > trailF) ? (yy - trailF) : 0);
          final double cov = LXUtils.clamp(1.0 - dOut, 0, 1);
          if (cov <= 0.02) continue;
          final double u = LXUtils.clamp(1.0 - (yy - leadF) / Math.max(1, thick), 0, 1);
          addPix(o, x, yy, (u > 0.5) ? LXColor.WHITE : WHITE_SOFT,
            gate * (0.55 + 0.45 * u) * cov);
        }
        // tail streams below the block
        for (int k = 1; k <= tail; ++k) {
          final int yy = (int) Math.round(trailF) + k;
          if (yy >= h) break;
          final double fFall = Math.exp(-2.0 * k / tail);
          if (fFall < 0.02) break;
          addPix(o, x, yy, WHITE_SOFT, gate * fFall * 0.7);
        }
      }
    }

    // --- the cyclone: thin white lines shearing around the canvas ---
    final int nLines = 8 + (int) Math.round(wow * (MAX_LINES - 8));
    final double rise = (this.timeMs * 0.00002 * speed) % 1.0;
    for (int i = 0; i < nLines; ++i) {
      final double band = hashd(i * 53 + 11); // 0 = top .. 1 = lower
      final double segLen = 0.18 + 0.22 * hashd(i * 97 + 3);
      // higher lines spin faster: cyclone shear
      final double spin = this.cyclone * (1.0 + (1.0 - band) * 1.2) + hashd(i * 31 + 7);
      final double xc = (spin - Math.floor(spin)) * w;
      final int x0 = (int) Math.floor(xc);
      final double xf = xc - x0;
      final int yTop = (int) ((((band * 0.55 - rise * 0.3) % 1.0) + 1.0) % 1.0 * h);
      final int yBot = Math.min(h - 1, yTop + (int) (segLen * h));
      for (int yy = yTop; yy <= yBot; ++yy) {
        final double tw = 0.55 + 0.45 * hashd(i * 977 + yy * 131 + tq * 3);
        addPix(o, x0, yy, WHITE_SOFT, 0.8 * tw * (1 - xf));
        addPix(o, x0 + 1, yy, WHITE_SOFT, 0.8 * tw * xf);
      }
    }

    // --- operator strobe: full-structure pulsating burst (original behavior) ---
    if (this.strobeEnv > 0) {
      final double hz = this.rate.getValue() * (1.0 + wow * 0.7);
      if ((this.timeMs * 0.001 * hz) % 1.0 < STROBE_DUTY) {
        final int flash = LXColor.scaleBrightness(LXColor.WHITE,
          (float) (this.strobeEnv * this.strobeEnv));
        for (int x = 0; x < w; ++x) {
          for (int y = 0; y < h; ++y) {
            final int idx = o.point(x, y).index;
            this.colors[idx] = LXColor.lightest(this.colors[idx], flash);
          }
        }
      }
    }
  }
}
