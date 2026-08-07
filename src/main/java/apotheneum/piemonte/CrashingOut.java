/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * CrashingOut — LockedIn's restless sibling. A set of thick bars beams in
 * from an edge as solid columns, each on its own clock — fast travel,
 * decelerating approach, bounces on the entry velocity model — and locks
 * into the center. But nothing stays locked: the bars keep bouncing, restless,
 * until the whole set crashes back out — each bar retreating mid-bounce in
 * reverse off its entry edge, slow to break loose and accelerating away. Then the next set enters from
 * the opposite edge at a new horizontal offset and the next of five colors.
 * Beats punch the bounces; Wow adds bounce reach and speeds the crash.
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
public class CrashingOut extends StrandPattern {

  private static final int N_COLORS = 5;
  private static final int MAX_BARS = 16;
  // UFOAbduction-style approach: quick entry, deceleration into the center
  private static final double V_ENTER = 0.0016;  // heights/ms far from rest
  private static final double V_NEAR = 0.00030;  // crawl arriving at center
  private static final double BOUNCE_MS = 1800;    // sustained bounce before the crash
  private static final double GONE = 1.25;         // off-canvas travel distance
  private static final double[] HUE_STEPS = { 0, 52, 125, 205, 288 };

  public final DiscreteParameter bars =
    new DiscreteParameter("Bars", 9, 4, MAX_BARS)
    .setDescription("Bars per color set");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How hard beats punch the bounce");

  // one set at a time (normalized; shared across surfaces)
  private final double[] barX = new double[MAX_BARS];
  private final double[] barLen = new double[MAX_BARS];
  private final double[] barYc = new double[MAX_BARS];
  private final int[] barPhase = new int[MAX_BARS]; // 0 enter, 1 bounce, 3 crash
  private final double[] barPos = new double[MAX_BARS];
  private final double[] barDelay = new double[MAX_BARS];
  private final double[] barVf = new double[MAX_BARS];
  private final double[] barRnd = new double[MAX_BARS];
  private final double[] barTgt = new double[MAX_BARS];
  private final int[] barBounce = new int[MAX_BARS];

  private int gen = 0;          // total sets launched (drives color + edge)
  private boolean fromTop = false;
  private int mode = 0;         // 0 = animating in, 1 = bouncing, 2 = crashing out
  private double holdT = 0;
  private double bopKick = 0;
  private boolean laidOut = false;

  public CrashingOut(LX lx) {
    // Base registers color (wheel anchor), speed (pace), size (bar thickness).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("bars", this.bars);
    addParameter("sensitivity", this.sensitivity);
    addTargetParameter();
  }

  /** Per-bar bounce reach: base + Wow + beat punch, widely varied per bar. */
  private double bounceAmp(int b) {
    return (0.09 + getWow() * 0.06
      + this.bopKick * 0.05 * this.sensitivity.getValue())
      * (0.45 + 1.15 * hashd(this.gen * 97 + b * 31));
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  private void launchSet() {
    this.fromTop = (this.gen & 1) == 1; // alternate bottom / top entries
    this.mode = 0;
    final double off = Math.random(); // per-set horizontal offset
    for (int b = 0; b < MAX_BARS; ++b) {
      this.barX[b] = (off + (double) b / MAX_BARS
        + (Math.random() - 0.5) * 0.02) % 1.0;
      this.barLen[b] = (0.14 + Math.random() * 0.30) * (0.8 + Math.random() * 0.4);
      this.barYc[b] = 0.42 + Math.random() * 0.16;
      this.barPhase[b] = 0;
      this.barPos[b] = (this.fromTop ? -1 : 1) * (1.05 + Math.random() * 0.30);
      this.barDelay[b] = Math.random() * 900;
      this.barVf[b] = 0.7 + Math.random() * 0.6;
      this.barRnd[b] = Math.random();
      this.barTgt[b] = 0;
      this.barBounce[b] = 0;
    }
  }

  @Override
  protected void advance(double deltaMs) {
    if (!this.laidOut) {
      this.gen = 0;
      launchSet();
      this.laidOut = true;
    }
    final double speed = Math.max(0.05, getSpeed());
    final double wow = getWow();
    final double dt = deltaMs * speed * 2;

    if (this.beat) {
      this.bopKick = 1;
    }
    this.bopKick *= Math.exp(-deltaMs / 260.0);

    final int nBars = this.bars.getValuei();

    // bars never sit still: they enter, bounce, and bounce until they crash away
    boolean allArrived = true;
    boolean allGone = true;
    for (int b = 0; b < nBars; ++b) {
      switch (this.barPhase[b]) {
        case 0: { // entering: decelerate on approach, independently
          allArrived = false;
          allGone = false;
          if (this.barDelay[b] > 0) {
            this.barDelay[b] -= dt;
            break;
          }
          final double dist = Math.abs(this.barPos[b]);
          final double v = this.barVf[b] * LXUtils.lerp(V_NEAR, V_ENTER,
            LXUtils.clamp(dist / 0.45, 0, 1));
          this.barPos[b] -= Math.signum(this.barPos[b]) * v * dt;
          if (dist < 0.015) {
            this.barPos[b] = 0;
            this.barPhase[b] = 1;
            this.barBounce[b] = 0;
            this.barTgt[b] = (this.fromTop ? 1 : -1) * bounceAmp(b);
          }
          break;
        }
        case 1: { // bouncing: same velocity model as the entry travel, sustained
          allGone = false;
          final double dist = Math.abs(this.barTgt[b] - this.barPos[b]);
          final double v = this.barVf[b] * LXUtils.lerp(V_NEAR, V_ENTER,
            LXUtils.clamp(dist / 0.45, 0, 1));
          this.barPos[b] += Math.signum(this.barTgt[b] - this.barPos[b]) * v * dt;
          if (dist < 0.008) {
            // reverse into a fresh bounce, each with its own reach — no settling
            ++this.barBounce[b];
            this.barTgt[b] = -Math.signum(this.barTgt[b]) * bounceAmp(b)
              * (0.55 + 0.45 * hashd(this.gen * 31 + b * 7 + this.barBounce[b] * 13));
          }
          break;
        }
        case 3: { // crashing out: the entry run in reverse, off the canvas
          if (this.barDelay[b] > 0) {
            this.barDelay[b] -= dt;
            allGone = false;
            break;
          }
          final double dist = Math.abs(this.barPos[b]);
          // slow to break loose, accelerating as it pulls away
          final double v = this.barVf[b] * LXUtils.lerp(V_NEAR, V_ENTER,
            LXUtils.clamp(dist / 0.45, 0, 1)) * (1 + wow * 0.8);
          final double dir = this.fromTop ? -1 : 1; // back toward its entry edge
          this.barPos[b] += dir * v * dt;
          if (dist < GONE) {
            allGone = false;
          }
          break;
        }
      }
    }

    if (this.mode == 0 && allArrived) { // everyone's in and bouncing
      this.mode = 1;
      this.holdT = 0;
    } else if (this.mode == 1) { // bounce it out, then break for the edges
      this.holdT += dt;
      if (this.holdT >= BOUNCE_MS) {
        this.mode = 2;
        for (int b = 0; b < MAX_BARS; ++b) {
          this.barPhase[b] = 3;
          this.barDelay[b] = Math.random() * 500; // staggered break-loose
        }
      }
    } else if (this.mode == 2 && allGone) { // next color from the opposite edge
      ++this.gen;
      launchSet();
    }
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final int w = o.width();
    final int h = o.height();
    final int nBars = this.bars.getValuei();
    final double thickC = Math.max(2, (2.5 + getSize() * 5) * (isCube ? 1.6 : 1.0));
    final int tq = (int) (this.timeMs / 80);

    final int col = LXColor.hsb(
      (float) (((LXColor.h(getColor()) + HUE_STEPS[this.gen % N_COLORS]) % 360) + 360) % 360,
      92, 100);
    final int hot = LXColor.lerp(col, LXColor.WHITE, 0.55f);

    for (int b = 0; b < nBars; ++b) {
      final double len = this.barLen[b];
      double yc = this.barYc[b];
      boolean traveling = false;
      if (this.barPhase[b] == 0 || this.barPhase[b] == 3) {
        if (this.barPhase[b] == 0 && this.barDelay[b] > 0) continue; // not launched
        yc += this.barPos[b];
        traveling = true;
      } else if (this.barPhase[b] == 1) {
        yc += this.barPos[b]; // bouncing
      }
      // solid column anchored to its entry edge, reaching to its lead end
      final double leadF = this.fromTop
        ? (yc + len / 2) * (h - 1)
        : (yc - len / 2) * (h - 1);
      if (this.fromTop ? (leadF < -1) : (leadF > h)) continue;

      final int x0 = (int) Math.round(this.barX[b] * w);
      final int bw = (int) thickC;
      final double span = this.fromTop
        ? Math.max(1, leadF)
        : Math.max(1, (h - 1) - leadF);

      for (int dx = 0; dx < bw; ++dx) {
        final int x = x0 + dx;
        final int yStart = this.fromTop ? 0 : (int) Math.floor(leadF);
        final int yEnd = this.fromTop ? (int) Math.ceil(leadF) : h - 1;
        for (int yy = yStart; yy <= yEnd; ++yy) {
          if (yy < 0 || yy >= h) continue;
          final double dOut = this.fromTop ? (yy - leadF) : (leadF - yy);
          final double cov = LXUtils.clamp(1.0 - Math.max(0, dOut), 0, 1);
          if (cov <= 0.02) continue;
          final double back = Math.abs(yy - leadF) / span;
          int c = col;
          double bb = cov * (0.55 + 0.45 * Math.pow(1.0 - back, 0.8))
            * (0.85 + 0.15 * hashd(x * 131 + yy * 7 + tq));
          if (traveling && Math.abs(yy - leadF) < 1.5) {
            c = hot;
            bb = Math.min(1, bb * 1.5);
          }
          addPix(o, x, yy, c, bb);
        }
      }
    }
  }
}
