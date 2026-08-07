/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * UFOAbduction — beat-locked counter-propagating waves on strand lanes. On every
 * beat, green blocks launch upward from the floor at full speed, decelerate
 * as they pass through the center — slowing to a crawl but never stopping,
 * fattening into chunky bright slabs — then accelerate back to launch speed
 * as they exit the top, like waves passing through a dense medium. Red
 * streaks run the other way, top to floor, threading through the risers.
 * Tails stretch with speed: long streamers at the edges, short stubs through
 * the slow center; white-hot cores at speed. Lanes fire in synchronized
 * clusters, the palette snaps between green-dominant, red-dominant, and
 * mixed sections, and a big drop collapses the field to black before
 * slamming a fresh wave up every lane. Modeled on the wave.mov reference.
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
public class UFOAbduction extends StrandPattern {

  private static final int MAX_HEADS = 96;
  private static final double GREEN_HUE = 120;
  private static final double RED_HUE = 10;
  // calibrated from wave.mov: 800ms transit = 165ms entry + 565ms center stall
  // + 65ms exit burst; stall:exit velocity ratio ~1:36; 900ms launch cadence
  private static final double V_ENTRY = 0.00135;  // heights/ms, bottom leg
  private static final double V_STALL = 0.00024;  // crawl through the center
  private static final double V_EXIT = 0.0075;    // explosive top exit
  private static final double V_RED = 0.0011;     // ~0.9s full-height descent
  private static final double STALL_LO = 0.60;    // stall band (y, 0 = top)
  private static final double STALL_HI = 0.44;
  private static final double EXIT_AT = 0.30;     // full burst above here
  private static final double IDLE_WAVE_MS = 900; // measured launch cadence
  private static final double SECTION_MS = 4500;  // palette section length
  private static final double COLLAPSE_MS = 450;

  public final DiscreteParameter lanes =
    new DiscreteParameter("Lanes", 14, 6, 24)
    .setDescription("Strand lanes around the surface");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Audio sensitivity of launches and drops");

  public final CompoundParameter glow =
    new CompoundParameter("Glow", 0.6, 0, 1)
    .setDescription("How far the blocks radiate light");

  public final CompoundParameter trail =
    new CompoundParameter("Trail", 0.5, 0, 1)
    .setDescription("How long the gradient tails stream behind");

  private final class Surface {
    boolean inited;
    final int[] lane = new int[MAX_HEADS];
    final double[] y = new double[MAX_HEADS];     // normalized 0..1, 0 = top
    final double[] v = new double[MAX_HEADS];     // heights/ms, +down -up
    final boolean[] isRed = new boolean[MAX_HEADS];
    final boolean[] thin = new boolean[MAX_HEADS]; // single-line red beams
    final boolean[] waveR = new boolean[MAX_HEADS]; // reds riding the stall profile
    final boolean[] alive = new boolean[MAX_HEADS];
    void reset() {
      for (int i = 0; i < MAX_HEADS; ++i) this.alive[i] = false;
      this.inited = true;
    }
    void spawn(int lane, boolean red) {
      for (int i = 0; i < MAX_HEADS; ++i) {
        if (this.alive[i]) continue;
        this.lane[i] = lane;
        this.isRed[i] = red;
        this.thin[i] = red && Math.random() < 0.5;
        // half the single lines mirror the risers' stall trajectory downward;
        // the rest plummet straight to the floor
        this.waveR[i] = red && this.thin[i] && Math.random() < 0.5;
        if (red) {
          this.y[i] = -0.02 - Math.random() * 0.08;
          this.v[i] = this.waveR[i]
            ? (0.9 + Math.random() * 0.2) // variance factor for the profile
            : V_RED * (0.9 + Math.random() * 0.25) * (this.thin[i] ? 1.25 : 1.0);
        } else {
          this.y[i] = 1.02 + Math.random() * 0.10;
          this.v[i] = -(0.75 + Math.random() * 0.5); // wide variance staggers the field
        }
        this.alive[i] = true;
        return;
      }
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double waveTimer = 0;
  private double launchFlash = 0;
  private double collapseT = -1;
  private boolean relaunchQueued = false;
  private int section = 0;

  public UFOAbduction(LX lx) {
    // Base registers color (hue shift), speed (cadence + velocity), size (block thickness).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("lanes", this.lanes);
    addParameter("sensitivity", this.sensitivity);
    addParameter("glow", this.glow);
    addParameter("trail", this.trail);
    addTargetParameter();
  }

  @Override
  protected double beatThreshold() {
    return 1.05 + (1 - this.sensitivity.getValue()) * 0.7;
  }

  /** Measured rise profile: brisk entry, long center stall, explosive exit. */
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

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  /** Section palette mode: 0 = green-dominant, 1 = red-dominant, 2 = mixed. */
  private int sectionMode() {
    final double r = hashd(this.section * 613 + 29);
    return (r < 0.42) ? 0 : (r < 0.68 ? 1 : 2);
  }

  // pending staggered spawns for launch wipes (lane, color, delay)
  private final int[] pendLane = new int[MAX_HEADS];
  private final boolean[] pendRed = new boolean[MAX_HEADS];
  private final double[] pendMs = new double[MAX_HEADS];
  private final boolean[] pendCube = new boolean[MAX_HEADS];
  private final boolean[] pendAlive = new boolean[MAX_HEADS];

  private void launchWave(Surface s, boolean isCube, int nLanes, double strength, boolean forceAll) {
    final int mode = sectionMode();
    final double redFrac = (mode == 0) ? 0.20 : (mode == 1 ? 0.75 : 0.5);
    // per-event wipe: 0 = global, 1 = left->right, 2 = right->left, 3 = edges->center
    final int wipe = (int) (hashd((int) (this.timeMs * 7) + 31) * 4);
    for (int l = 0; l < nLanes; ++l) {
      final int cluster = l / 3;
      final double on = hashd(this.section * 977 + cluster * 131 + (int) (this.timeMs / 400));
      if (!forceAll && on > 0.40 + strength * 0.55) continue;
      final boolean red = hashd(this.section * 313 + cluster * 53) < redFrac;
      double delay = 0;
      final double u = (double) l / Math.max(1, nLanes - 1);
      if (wipe == 1) delay = u * 370;
      else if (wipe == 2) delay = (1 - u) * 370;
      else if (wipe == 3) delay = (1.0 - Math.abs(u - 0.5) * 2) * 200; // edges first
      // per-lane scatter on every wave so blocks never march in one big group
      delay += hashd(l * 641 + (int) (this.timeMs * 3)) * 280;
      queueSpawn(s, isCube, l, red, delay);
      if (mode == 2 && hashd(l * 97 + (int) this.timeMs) < 0.3) {
        queueSpawn(s, isCube, l, !red, delay + 60);
      }
    }
  }

  private void queueSpawn(Surface s, boolean isCube, int lane, boolean red, double delay) {
    if (delay <= 0) {
      s.spawn(lane, red);
      return;
    }
    for (int i = 0; i < MAX_HEADS; ++i) {
      if (!this.pendAlive[i]) {
        this.pendLane[i] = lane;
        this.pendRed[i] = red;
        this.pendMs[i] = delay;
        this.pendCube[i] = isCube;
        this.pendAlive[i] = true;
        return;
      }
    }
  }

  @Override
  protected void advance(double deltaMs) {
    final double speed = Math.max(0.05, getSpeed());
    final double wow = getWow();
    this.section = (int) (this.timeMs / SECTION_MS);

    // collapse-to-black then hard re-drop on big hits
    if (this.collapseT >= 0) {
      this.collapseT += deltaMs;
      if (this.collapseT >= COLLAPSE_MS) {
        this.collapseT = -1;
        this.cube.reset();
        this.cylinder.reset();
        // drop queued staggered spawns too, so a wave launched just before
        // the collapse can't fire into the clean relaunch
        java.util.Arrays.fill(this.pendAlive, false);
        this.relaunchQueued = true;
      }
    } else if (this.beat && this.beatLevel > 0.80 - wow * 0.15) {
      this.collapseT = 0;
    }

    // beat-locked launches, with an idle subdivision cadence as fallback
    this.waveTimer += deltaMs * speed * 2;
    final boolean waveDue = this.waveTimer >= IDLE_WAVE_MS;
    final boolean launch = this.relaunchQueued
      || (this.collapseT < 0 && ((this.beat && this.beatLevel > 0.15) || waveDue));
    if (launch) {
      this.waveTimer = 0;
      this.launchFlash = 1;
      final double strength = this.relaunchQueued ? 1.0
        : LXUtils.clamp(this.levelEnv * 1.6 + this.beatLevel * 0.4 + wow * 0.3, 0.15, 1);
      final int n = this.lanes.getValuei();
      // only feed the surfaces actually being rendered — a hidden target
      // would otherwise pile up frozen heads at their spawn points
      final Target t = getTarget();
      if (t != Target.CYLINDER) {
        launchWave(this.cube, true, n, strength, this.relaunchQueued);
      }
      if (t != Target.CUBE) {
        launchWave(this.cylinder, false, n, strength, this.relaunchQueued);
      }
      this.relaunchQueued = false;
    }
    this.launchFlash *= Math.exp(-deltaMs / 160.0);

    // fire staggered wipe spawns as their delays elapse
    for (int i = 0; i < MAX_HEADS; ++i) {
      if (!this.pendAlive[i]) continue;
      this.pendMs[i] -= deltaMs;
      if (this.pendMs[i] <= 0) {
        (this.pendCube[i] ? this.cube : this.cylinder).spawn(this.pendLane[i], this.pendRed[i]);
        this.pendAlive[i] = false;
      }
    }
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final Surface s = isCube ? this.cube : this.cylinder;
    if (!s.inited) s.reset();
    final int w = o.width();
    final int h = o.height();
    final double wow = getWow();
    final double speed = Math.max(0.05, getSpeed());
    final double hueShift = LXColor.h(getColor());
    final int green = LXColor.hsb((float) (((GREEN_HUE + hueShift) % 360)), 96, 100);
    final int red = LXColor.hsb((float) (((RED_HUE + hueShift) % 360)), 98, 100);
    final int nLanes = this.lanes.getValuei();
    final double laneW = (double) w / nLanes;
    // collapse envelope dims everything toward black before the re-drop
    final double collapse = (this.collapseT >= 0)
      ? 1.0 - this.collapseT / COLLAPSE_MS : 1.0;

    for (int i = 0; i < MAX_HEADS; ++i) {
      if (!s.alive[i]) continue;

      // --- velocity field: green decelerates through the center and accelerates
      // back out — waves passing through a dense medium, never stopping ---
      final double vScale = speed * 2; // default Speed 0.5 == measured reference speed
      if (s.isRed[i]) {
        if (s.waveR[i]) {
          // mirrored riser trajectory: zoom in from the top, slow-motion crawl
          // through the center, burst out the floor
          s.y[i] += s.v[i] * riseProfile(1.0 - s.y[i]) * vScale * deltaMs;
        } else {
          s.y[i] += s.v[i] * vScale * deltaMs; // straight plummet
        }
        if (s.y[i] >= 1.05) { s.alive[i] = false; continue; }
      } else {
        s.y[i] += s.v[i] * riseProfile(s.y[i]) * vScale * deltaMs;
        if (s.y[i] <= -0.08) { s.alive[i] = false; continue; } // bursts out the top
      }

      // --- geometry: thick through the slow center, thin at speed; tail follows ---
      double vNorm;
      if (s.isRed[i] && !s.waveR[i]) {
        vNorm = LXUtils.clamp(Math.abs(s.v[i]) / V_ENTRY, 0, 1.2);
      } else {
        final double yp = s.isRed[i] ? 1.0 - s.y[i] : s.y[i];
        vNorm = LXUtils.clamp(riseProfile(yp) / V_ENTRY, 0, 1.2);
      }
      final double thickN = LXUtils.lerp(0.11 * (0.6 + getSize() * 0.9), 0.045, Math.min(1, vNorm));
      final int thick = Math.max(2, (int) Math.round(thickN * h));
      // Trail dial: 0.5 == 2x the original length; up to ~4x at full
      final double trailK = 0.7 + this.trail.getValue() * 2.6;
      final double tailN = LXUtils.clamp(vNorm * 0.85 * trailK, 0.16 * trailK, 1.9);
      final int tail = (int) Math.round(tailN * h);
      double bright = collapse;
      if (bright <= 0.01) continue;

      final int base = s.isRed[i] ? red : green;
      // white-hot core at launch speed and on the beat flash
      final double hot = LXUtils.clamp(vNorm * 0.7 + this.launchFlash * 0.4, 0, 0.85);
      final int headC = LXColor.lerp(base, LXColor.WHITE, (float) hot);

      int x0 = (int) (s.lane[i] * laneW);
      int x1 = Math.min(w - 1, (int) ((s.lane[i] + 1) * laneW) - 2); // 1-col dark seam
      if (s.isRed[i]) {
        // red runs thin down the lane: some 2-wide strands, some single lines
        final int cxr = (x0 + x1) / 2;
        x0 = cxr;
        x1 = s.thin[i] ? cxr : Math.min(w - 1, cxr + 1);
      }
      // strictly monotonic travel: the slow-motion crawl through the center is
      // pure velocity — no positional oscillation (it read as shaking at LED scale)
      final double ycF = LXUtils.clamp(s.y[i], -0.1, 1.1) * (h - 1);
      final int yc = (int) Math.round(ycF);
      final int dir = s.isRed[i] ? 1 : -1; // travel direction (+down)
      // red pulses as it descends: a throb riding the moving packet
      final double throb = s.isRed[i]
        ? 0.55 + 0.45 * Math.sin(2 * Math.PI * this.timeMs / 1000.0 + i * 0.4) : 1.0;

      final int headThick = s.isRed[i] ? Math.max(2, thick / 2) : thick; // compact red pulse

      // radiant aura: soft gradient bloom bleeding off the block (ComeUp-style),
      // strongest around the slow heavy slabs; the Glow dial sets reach + intensity
      final double glowV = this.glow.getValue();
      final int auraR = (s.isRed[i] ? 1 : 2) + (int) Math.round(glowV * 4);
      final double auraB = bright * throb * (s.isRed[i] ? 0.5 : 1.0)
        * (0.08 + glowV * 0.50) * (1.0 - 0.4 * Math.min(1, vNorm));
      if (auraB > 0.02) {
        final int byTop = Math.min(yc, yc - dir * (headThick - 1));
        final int byBot = Math.max(yc, yc - dir * (headThick - 1));
        for (int x = x0 - auraR; x <= x1 + auraR; ++x) {
          final int dxo = (x < x0) ? (x0 - x) : ((x > x1) ? (x - x1) : 0);
          for (int yy = byTop - auraR; yy <= byBot + auraR; ++yy) {
            if (yy < 0 || yy >= h) continue;
            final int dyo = (yy < byTop) ? (byTop - yy) : ((yy > byBot) ? (yy - byBot) : 0);
            final int dout = dxo + dyo;
            if (dout == 0) continue; // inside the block
            addPix(o, x, yy, base, auraB * Math.exp(-dout * (1.4 - glowV * 0.7)));
          }
        }
      }

      // continuous block span with sub-pixel feathered edges — glides, no snapping
      final double leadF = ycF;
      final double trailF = ycF - dir * (headThick - 1);
      final double topF = Math.min(leadF, trailF);
      final double botF = Math.max(leadF, trailF);
      for (int x = x0; x <= x1; ++x) {
        for (int yy = (int) Math.floor(topF) - 1; yy <= (int) Math.ceil(botF) + 1; ++yy) {
          if (yy < 0 || yy >= h) continue;
          final double dOut = (yy < topF) ? (topF - yy) : ((yy > botF) ? (yy - botF) : 0);
          final double cov = LXUtils.clamp(1.0 - dOut, 0, 1); // edge feather
          if (cov <= 0.02) continue;
          final double u = LXUtils.clamp(1.0 - Math.abs(leadF - yy) / headThick, 0, 1);
          addPix(o, x, yy, (u > 0.5) ? headC : base, bright * throb * (0.55 + 0.45 * u) * cov);
        }
        // tail streams behind, sub-pixel anchored to the trailing edge
        for (int k = 1; k <= tail; ++k) {
          final double yyF = trailF - dir * k;
          final double fFall = Math.exp(-1.8 * k / tail);
          if (fFall < 0.02) break;
          final int yy0 = (int) Math.floor(yyF);
          final double frac = yyF - yy0;
          final double tb = bright * throb * fFall * 0.8;
          if (yy0 >= 0 && yy0 < h) addPix(o, x, yy0, base, tb * (1 - frac));
          if (yy0 + 1 >= 0 && yy0 + 1 < h) addPix(o, x, yy0 + 1, base, tb * frac);
        }
      }
    }
  }
}
