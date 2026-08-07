/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * LockedIn — sets of thick bars lock into the center of the canvas, one color
 * at a time. Each bar is a solid column anchored to its entry edge, growing
 * toward the middle with the UFOAbduction feel — fast travel, decelerating on
 * approach — then bouncing on the same velocity model, each bar on its own
 * clock and reach, before freezing in place. Sets alternate bottom and top
 * entries, each at a new horizontal offset, cycling through five colors and
 * layering endlessly: the stack never fades — new sets keep locking in over
 * the old, and only when a color comes back around does its previous layer
 * yield to the fresh one rising from the edge. Beats punch the bounces; Wow
 * adds bounce reach and shimmer.
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
public class LockedIn extends StrandPattern {

  private static final int N_COLORS = 5;    // hues cycled across sets
  private static final int MAX_LAYERS = 10; // frozen sets kept on the canvas
  private static final int MAX_BARS = 16;
  // UFOAbduction-style approach: quick entry, deceleration into the center
  private static final double V_ENTER = 0.0016;  // heights/ms far from rest
  private static final double V_NEAR = 0.00030;  // crawl arriving at center
  private static final double BOUNCE_DECAY = 0.78; // per-bounce settle
  private static final double RELAUNCH_MS = 450;   // breath between sets
  private static final double[] HUE_STEPS = { 0, 52, 125, 205, 288 };

  public final DiscreteParameter bars =
    new DiscreteParameter("Bars", 9, 4, MAX_BARS)
    .setDescription("Bars per color set");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How hard beats punch the bounce");

  // layered set state (normalized; shared across surfaces)
  private final double[][] layX = new double[MAX_LAYERS][MAX_BARS];
  private final double[][] layLen = new double[MAX_LAYERS][MAX_BARS];
  private final double[][] layYc = new double[MAX_LAYERS][MAX_BARS];
  private final double[][] lenVar = new double[MAX_LAYERS][MAX_BARS];
  private final int[] layerHue = new int[MAX_LAYERS];
  private final boolean[] layerFromTop = new boolean[MAX_LAYERS];
  private final boolean[] layerLive = new boolean[MAX_LAYERS];

  private int gen = 0;           // total sets launched
  private int activeLayer = -1;  // layer currently animating, -1 = between sets
  private double pauseMs = 0;
  private double bopKick = 0;
  private boolean laidOut = false;

  // per-bar independent motion within the active set
  private final int[] barPhase = new int[MAX_BARS];  // 0 enter, 1 bounce, 2 frozen
  private final double[] barPos = new double[MAX_BARS];
  private final double[] barDelay = new double[MAX_BARS];
  private final double[] barVf = new double[MAX_BARS];
  private final double[] barBopPh = new double[MAX_BARS];
  private final double[] barTgt = new double[MAX_BARS];   // current bounce target
  private final int[] barBounce = new int[MAX_BARS];      // reversals remaining

  public LockedIn(LX lx) {
    // Base registers color (wheel anchor), speed (pace), size (bar thickness).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("bars", this.bars);
    addParameter("sensitivity", this.sensitivity);
    addTargetParameter();
  }

  /** Per-bar bounce reach: base + Wow + beat punch, widely varied per bar. */
  private double bounceAmp(int l, int b) {
    return (0.09 + getWow() * 0.06
      + this.bopKick * 0.05 * this.sensitivity.getValue())
      * (0.45 + 1.15 * hashd(l * 97 + b * 31));
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  /** Launch the next set: claim a layer (recycling the oldest) and arm bars. */
  private void launchSet() {
    final int l = this.gen % MAX_LAYERS;
    this.activeLayer = l;
    this.layerHue[l] = this.gen % N_COLORS;
    this.layerFromTop[l] = (this.gen & 1) == 1; // alternate bottom / top
    this.layerLive[l] = true;
    final double off = Math.random(); // per-set horizontal offset
    for (int b = 0; b < MAX_BARS; ++b) {
      this.layX[l][b] = (off + (double) b / MAX_BARS
        + (Math.random() - 0.5) * 0.02) % 1.0;
      this.layLen[l][b] = 0.14 + Math.random() * 0.30; // varied reach
      this.layYc[l][b] = 0.42 + Math.random() * 0.16;  // tips around center
      this.lenVar[l][b] = 0.8 + Math.random() * 0.4;
      this.barPhase[b] = 0;
      this.barPos[b] = (this.layerFromTop[l] ? -1 : 1)
        * (1.05 + Math.random() * 0.30);
      this.barDelay[b] = Math.random() * 900;
      this.barVf[b] = 0.7 + Math.random() * 0.6;
      this.barBopPh[b] = Math.random();
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
    final double dt = deltaMs * speed * 2;

    if (this.beat) {
      this.bopKick = 1;
    }
    this.bopKick *= Math.exp(-deltaMs / 260.0);

    if (this.activeLayer < 0) { // between sets: breathe, then launch the next
      this.pauseMs -= dt;
      if (this.pauseMs <= 0) {
        launchSet();
      }
      return;
    }

    final int l = this.activeLayer;
    final int nBars = this.bars.getValuei();
    boolean allFrozen = true;
    for (int b = 0; b < nBars; ++b) {
      switch (this.barPhase[b]) {
        case 0: { // entering: decelerate on approach, independently
          if (this.barDelay[b] > 0) {
            this.barDelay[b] -= dt;
            allFrozen = false;
            break;
          }
          final double dist = Math.abs(this.barPos[b]);
          final double v = this.barVf[b] * LXUtils.lerp(V_NEAR, V_ENTER,
            LXUtils.clamp(dist / 0.45, 0, 1));
          this.barPos[b] -= Math.signum(this.barPos[b]) * v * dt;
          if (dist < 0.015) {
            this.barPos[b] = 0;
            this.barPhase[b] = 1;
            // momentum carries into the first bounce, past the rest point
            this.barBounce[b] = 3 + (int) (this.barBopPh[b] * 3);
            this.barTgt[b] = (this.layerFromTop[l] ? 1 : -1) * bounceAmp(l, b);
          }
          allFrozen = false;
          break;
        }
        case 1: { // bouncing: same velocity model as the entry travel
          final double dist = Math.abs(this.barTgt[b] - this.barPos[b]);
          final double v = this.barVf[b] * LXUtils.lerp(V_NEAR, V_ENTER,
            LXUtils.clamp(dist / 0.45, 0, 1));
          this.barPos[b] += Math.signum(this.barTgt[b] - this.barPos[b]) * v * dt;
          if (dist < 0.008) {
            if (--this.barBounce[b] <= 0) {
              // ease home and freeze
              if (Math.abs(this.barPos[b]) < 0.008) {
                this.barPos[b] = 0;
                this.barPhase[b] = 2;
              } else {
                this.barTgt[b] = 0;
                this.barBounce[b] = 1;
              }
            } else {
              // reverse, each bounce a little smaller
              this.barTgt[b] = -Math.signum(this.barTgt[b])
                * bounceAmp(l, b)
                * Math.pow(BOUNCE_DECAY, 6 - this.barBounce[b]);
            }
          }
          allFrozen = false;
          break;
        }
      }
    }
    if (allFrozen) { // set locked; the stack stays — queue the next set
      ++this.gen;
      this.activeLayer = -1;
      this.pauseMs = RELAUNCH_MS;
    }
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final int w = o.width();
    final int h = o.height();
    final double hueShift = LXColor.h(getColor());
    final int nBars = this.bars.getValuei();
    final double thickC = Math.max(2, (2.5 + getSize() * 5) * (isCube ? 1.6 : 1.0));
    final int tq = (int) (this.timeMs / 80);

    for (int l = 0; l < MAX_LAYERS; ++l) {
      if (!this.layerLive[l]) continue;
      final boolean active = (l == this.activeLayer);
      final boolean fromTop = this.layerFromTop[l];
      final int col = LXColor.hsb(
        (float) (((hueShift + HUE_STEPS[this.layerHue[l]]) % 360) + 360) % 360, 92, 100);
      final int hot = LXColor.lerp(col, LXColor.WHITE, 0.55f);
      final double genB = active ? 1.0 : 0.82;

      for (int b = 0; b < nBars; ++b) {
        final double xN = this.layX[l][b];
        final double len = this.layLen[l][b] * this.lenVar[l][b];
        double yc = this.layYc[l][b];
        boolean entering = false;
        if (active) {
          if (this.barPhase[b] == 0) {
            if (this.barDelay[b] > 0) continue; // not launched yet
            yc += this.barPos[b]; // still traveling in
            entering = true;
          } else if (this.barPhase[b] == 1) {
            yc += this.barPos[b]; // bouncing on the entry velocity model
          }
        }
        // solid column anchored to its entry edge, reaching to its lead end:
        // bottom-entering bars fill floor-to-lead, top-entering sky-to-lead
        final double leadF = fromTop
          ? (yc + len / 2) * (h - 1)   // lead end grows downward from the sky
          : (yc - len / 2) * (h - 1);  // lead end grows upward from the floor
        if (fromTop ? (leadF < -1) : (leadF > h)) continue;

        final int x0 = (int) Math.round(xN * w);
        final int bw = (int) thickC;
        final double span = fromTop
          ? Math.max(1, leadF)
          : Math.max(1, (h - 1) - leadF);

        for (int dx = 0; dx < bw; ++dx) {
          final int x = x0 + dx;
          final int yStart = fromTop ? 0 : (int) Math.floor(leadF);
          final int yEnd = fromTop ? (int) Math.ceil(leadF) : h - 1;
          for (int yy = yStart; yy <= yEnd; ++yy) {
            if (yy < 0 || yy >= h) continue;
            final double dOut = fromTop ? (yy - leadF) : (leadF - yy);
            final double cov = LXUtils.clamp(1.0 - Math.max(0, dOut), 0, 1);
            if (cov <= 0.02) continue;
            // brightest at the lead end, easing back toward the anchor edge
            final double back = Math.abs(yy - leadF) / span;
            int c = col;
            double bb = genB * cov * (0.55 + 0.45 * Math.pow(1.0 - back, 0.8))
              * (0.85 + 0.15 * hashd(x * 131 + yy * 7 + (active ? tq : 0)));
            if (entering && Math.abs(yy - leadF) < 1.5) {
              c = hot;
              bb = Math.min(1, bb * 1.5);
            }
            addPix(o, x, yy, c, bb);
          }
        }
      }
    }
  }
}
