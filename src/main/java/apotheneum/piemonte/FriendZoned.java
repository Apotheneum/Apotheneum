/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * FriendZoned — concentric circles are born at the center of each facade and
 * slowly expand outward. Close to the center each ring is a continuous
 * circle; as it expands, cracks nucleate one by one at random points along
 * its edge and spread open like fracturing glass, and by the time a ring
 * nears the canvas edge it has crumbled into drifting lines and dots — then
 * it shatters, the dots detaching as embers that fly free with the ring's
 * angular momentum and burn out. Every ring spins on its own — randomly
 * clockwise or counter-clockwise — and the radial gap between successive
 * rings is randomized. On the cylinder the rings rise instead:
 * each circle wraps the full circumference and expands from the base to the
 * top. Splits bounds how many cracks a ring can develop; Spin scales the
 * rotation rate; Size runs the stroke from hairline to fat; beats flare
 * the rings; a Pulse tap sweeps
 * every ring to white and melts back to the set color; Wow layers on the
 * flourish — a radiant aura blooming off every ring, white-hot leading
 * edges, a slow per-ring throb, deeper fragment flicker, and a wider hue
 * spread.
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
public class FriendZoned extends StrandPattern {

  private static final int MAX_RINGS = 14;
  private static final int MAX_EMBERS = 160;
  private static final double V_EXPAND = 0.00013;  // normalized radius per ms at speed 0.5
  private static final double SHATTER_AT = 0.95;   // at the canvas edge, rings shatter

  /** Upper bound on the per-ring random split count (each ring rolls 3..Splits). */
  public final DiscreteParameter splits =
    new DiscreteParameter("Splits", 12, 4, 25)
    .setDescription("Maximum splits a ring can crack into");

  /** Rotation-rate multiplier on every ring's random spin. */
  public final CompoundParameter spin =
    new CompoundParameter("Spin", 1, 0, 4)
    .setDescription("Rotation speed of the rings");

  // ring slots (normalized radius space; shared across surfaces)
  private final boolean[] alive = new boolean[MAX_RINGS];
  private final double[] rad = new double[MAX_RINGS];    // 0 = center, 1 = canvas edge
  private final double[] rot = new double[MAX_RINGS];    // accumulated spin (radians)
  private final double[] rotV = new double[MAX_RINGS];   // signed spin rate, rad/ms
  private final int[] segs = new int[MAX_RINGS];         // primary split count
  private final double[] segPh = new double[MAX_RINGS];  // split-mask phase
  private final double[] gap = new double[MAX_RINGS];    // radial gap before the next ring
  private final double[] hueOff = new double[MAX_RINGS];
  private final int[] seed = new int[MAX_RINGS];         // salts per-split crack timing

  // ember pool: shattered ring fragments in free flight (angle/radius space)
  private final boolean[] eAlive = new boolean[MAX_EMBERS];
  private final double[] eAng = new double[MAX_EMBERS];
  private final double[] eRad = new double[MAX_EMBERS];
  private final double[] eVr = new double[MAX_EMBERS];   // radial fling, per ms
  private final double[] eVa = new double[MAX_EMBERS];   // angular drift, per ms
  private final double[] eLife = new double[MAX_EMBERS];
  private final double[] eMs = new double[MAX_EMBERS];
  private final double[] eHueOff = new double[MAX_EMBERS];

  private int newest = -1;
  private double flare = 0;
  // Pulse-tap whiteout: rings ease to white, then melt back to color
  private double whiteEnv = 0;
  private boolean whiteRising = false;

  public FriendZoned(LX lx) {
    // Base registers hue (ring color), speed (expansion rate), size (ring thickness).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("splits", this.splits);
    addParameter("spin", this.spin);
    addTargetParameter();
  }

  private static double frac(double v) {
    return v - Math.floor(v);
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  private void spawnRing() {
    for (int i = 0; i < MAX_RINGS; ++i) {
      if (this.alive[i]) continue;
      this.alive[i] = true;
      this.rad[i] = 0;
      this.rot[i] = Math.random() * 2 * Math.PI;
      // random spin, randomly clockwise or counter-clockwise
      this.rotV[i] = (Math.random() < 0.5 ? -1 : 1)
        * (0.00008 + Math.random() * 0.00030);
      this.segs[i] = 3 + (int) (Math.random() * (this.splits.getValuei() - 2));
      this.segPh[i] = Math.random();
      this.gap[i] = 0.05 + Math.random() * 0.12;
      this.hueOff[i] = (Math.random() - 0.5) * 36;
      this.seed[i] = (int) (Math.random() * 1e6);
      this.newest = i;
      return;
    }
  }

  @Override
  protected void advance(double deltaMs) {
    final double dt = deltaMs * Math.max(0.05, getSpeed()) * 2;

    if (this.beat) {
      this.flare = 1;
    }
    this.flare *= Math.exp(-deltaMs / 300.0);

    // a Pulse tap sweeps every ring to white and back: quick ease up
    // (~70ms), slow melt back down (~550ms) — no strobing
    if (pulseHit()) {
      this.whiteRising = true;
    }
    if (this.whiteRising) {
      this.whiteEnv += (1 - this.whiteEnv) * (1 - Math.exp(-deltaMs / 70.0));
      if (this.whiteEnv > 0.96) {
        this.whiteRising = false;
      }
    } else {
      this.whiteEnv *= Math.exp(-deltaMs / 550.0);
    }

    for (int i = 0; i < MAX_RINGS; ++i) {
      if (!this.alive[i]) continue;
      this.rad[i] += V_EXPAND * dt; // uniform expansion keeps the gaps intact
      this.rot[i] += this.rotV[i] * this.spin.getValue() * dt;
      if (this.rad[i] >= SHATTER_AT) {
        spawnEmbers(i); // the ring shatters — its dots detach and fly
        this.alive[i] = false;
        if (this.newest == i) this.newest = -1;
      }
    }

    // embers: fling outward with the ring's angular momentum, then burn out
    for (int e = 0; e < MAX_EMBERS; ++e) {
      if (!this.eAlive[e]) continue;
      this.eRad[e] += this.eVr[e] * dt;
      this.eAng[e] += this.eVa[e] * dt;
      this.eLife[e] -= dt;
      if (this.eLife[e] <= 0 || this.eRad[e] > 1.4) {
        this.eAlive[e] = false;
      }
    }

    // continuous stream: birth the next ring once the youngest has cleared
    // its own randomized gap
    if (this.newest < 0 || this.rad[this.newest] >= this.gap[this.newest]) {
      spawnRing();
    }
  }

  /**
   * Angular coverage of ring i at angle theta, given how far out it is (u).
   * Splits crack open partway out, widen, then the surviving arcs crumble
   * into dots toward the canvas edge.
   */
  private double arcMask(int i, double theta, double u, int tq) {
    final double a = theta + this.rot[i];
    double m = 1;

    // each split nucleates at its own radius and spreads open individually,
    // so cracks appear one by one and propagate around the ring
    final double pos = a / (2 * Math.PI) * this.segs[i] + this.segPh[i];
    final long j = Math.round(pos);
    final int jIdx = (int) (((j % this.segs[i]) + this.segs[i]) % this.segs[i]);
    final double cj = crackOpen(i, jIdx, u);
    if (cj > 0) {
      final double dGap = Math.abs(pos - j);       // 0 at split center
      final double gapW = 0.005 + 0.42 * cj;       // hairline crack → wide gap
      m *= LXUtils.clamp((dGap - gapW * 0.5) / 0.10, 0, 1);
    }

    final double dotty = LXUtils.clamp((u - 0.62) / 0.34, 0, 1);
    if (dotty > 0 && m > 0) {
      final double f2 = frac(a / (2 * Math.PI) * this.segs[i] * 3 + this.segPh[i] * 2.7);
      final double d2 = Math.min(f2, 1 - f2);      // 0 mid-dot
      final double duty = 1.0 - 0.62 * dotty;      // arcs shrink to dots
      final double dm = LXUtils.clamp((duty * 0.5 - d2) / 0.10, 0, 1);
      m *= LXUtils.lerp(1, dm, dotty);
      // crumbled fragments shimmer; Wow deepens the flicker
      final double depth = 0.12 + getWow() * 0.5;
      m *= 1 - depth * dotty * hashd(i * 977 + (int) (f2 * 37) + tq);
    }
    return m;
  }

  /** How far split jIdx of ring i has opened at expansion u (0 none → 1 full). */
  private double crackOpen(int i, int jIdx, double u) {
    final double start = 0.14 + 0.42 * hashd(this.seed[i] + jIdx * 97);
    return LXUtils.clamp((u - start) / 0.30, 0, 1);
  }

  /** Shatter: the ring's end-stage dots detach as embers in free flight. */
  private void spawnEmbers(int i) {
    final int nDots = this.segs[i] * 3;
    final double spinV = this.spin.getValue();
    for (int k = 0; k < nDots; ++k) {
      // dot centers of the secondary (dotting) mask, in ring-local angle
      final double a = (k - this.segPh[i] * 2.7) / nDots * 2 * Math.PI;
      // skip dots that already vanished inside a fully opened crack
      final double pos = a / (2 * Math.PI) * this.segs[i] + this.segPh[i];
      final long j = Math.round(pos);
      final int jIdx = (int) (((j % this.segs[i]) + this.segs[i]) % this.segs[i]);
      final double gapW = 0.005 + 0.42 * crackOpen(i, jIdx, SHATTER_AT);
      if (LXUtils.clamp((Math.abs(pos - j) - gapW * 0.5) / 0.10, 0, 1) < 0.25) continue;
      for (int e = 0; e < MAX_EMBERS; ++e) {
        if (this.eAlive[e]) continue;
        this.eAlive[e] = true;
        this.eAng[e] = a - this.rot[i]; // world angle at the moment of shatter
        this.eRad[e] = SHATTER_AT;
        this.eVr[e] = V_EXPAND * (1.6 + Math.random() * 1.6);
        this.eVa[e] = this.rotV[i] * spinV + (Math.random() - 0.5) * 0.00015;
        this.eMs[e] = 600 + Math.random() * 600;
        this.eLife[e] = this.eMs[e];
        this.eHueOff[e] = this.hueOff[i];
        break;
      }
    }
  }

  /** One ember: a soft 3x3 spark, white-hot young, cooling as it burns out. */
  private void drawEmber(Apotheneum.Orientation o, int x, int y, int e,
      float baseHue, double gain, float wht, int tq) {
    final double lifeF = this.eLife[e] / this.eMs[e];
    final double flick = 0.55 + 0.45 * hashd(e * 77 + tq * 13);
    int c = LXColor.hsb(
      (float) ((((baseHue + this.eHueOff[e]) % 360) + 360) % 360), 92, 100);
    final float hotf = (float) (LXUtils.clamp((lifeF - 0.55) / 0.45, 0, 1) * 0.55);
    if (hotf > 0) {
      c = LXColor.lerp(c, LXColor.WHITE, hotf);
    }
    if (wht > 0.01) {
      c = LXColor.lerp(c, LXColor.WHITE, wht);
    }
    final double bb = lifeF * flick * gain;
    for (int dxo = -1; dxo <= 1; ++dxo) {
      for (int dyo = -1; dyo <= 1; ++dyo) {
        final double fall = (dxo == 0 && dyo == 0) ? 1
          : (dxo != 0 && dyo != 0) ? 0.25 : 0.45;
        addPix(o, x + dxo, y + dyo, c, Math.min(1, bb * fall));
      }
    }
  }

  private int ringColor(int i, float baseHue, double u) {
    final double spread = 1 + getWow() * 1.5;
    final float hu = (float) ((((baseHue + this.hueOff[i] * spread) % 360) + 360) % 360);
    final int c = LXColor.hsb(hu, 92, 100);
    // rings are born white-hot at the center, cooling into color as they grow
    final float hotf = (float) (LXUtils.clamp((0.15 - u) / 0.15, 0, 1) * 0.7);
    return (hotf > 0) ? LXColor.lerp(c, LXColor.WHITE, hotf) : c;
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final int w = o.width();
    final int h = o.height();
    final int tq = (int) (this.timeMs / 90);
    final float baseHue = LXColor.h(getColor());
    final double gain = 1 + this.flare * 0.5;

    // Pulse whiteout blend for this frame
    final float wht = (float) (this.whiteEnv * 0.9);

    // Wow flourish: radiant aura bloom off every ring, a white-hot leading
    // edge, and a slow per-ring throb (plus the deeper fragment flicker and
    // wider hue spread inside arcMask/ringColor). Inert at Wow 0.
    final double wow = getWow();

    if (isCube) {
      // circles expand from the center of each face
      final int faceW = w / 4;
      final double cyf = (h - 1) / 2.0;
      final double cxf = (faceW - 1) / 2.0;
      final double rMax = Math.sqrt(cxf * cxf + cyf * cyf);
      final double halfW = 0.2 + getSize() * 3.0; // Size: hairline → fat stroke

      for (int face = 0; face < 4; ++face) {
        final int x0 = face * faceW;
        for (int lx = 0; lx < faceW; ++lx) {
          final double dx = lx - cxf;
          for (int y = 0; y < h; ++y) {
            final double dy = y - cyf;
            final double rPix = Math.sqrt(dx * dx + dy * dy);
            double theta = 0;
            boolean haveTheta = false;
            for (int i = 0; i < MAX_RINGS; ++i) {
              if (!this.alive[i]) continue;
              final double dSigned = rPix - this.rad[i] * rMax; // + outside the ring
              final double dR = Math.abs(dSigned);
              if (dR > halfW * 3 + 1 + wow * halfW * 5) continue;
              if (!haveTheta) {
                theta = Math.atan2(dy, dx);
                haveTheta = true;
              }
              // gaussian cross-section: bright core melting into a soft glow
              final double dn = dR / (halfW + 0.35);
              double cov = Math.exp(-1.6 * dn * dn);
              if (wow > 0.01) {
                // wide dim aura bleeding off the stroke
                final double an = dR / (halfW * 2.5 + 1.2);
                cov += wow * 0.35 * Math.exp(-0.9 * an * an);
              }
              final double m = arcMask(i, theta, this.rad[i], tq);
              if (cov * m <= 0.02) continue;
              int c = ringColor(i, baseHue, this.rad[i]);
              if (wow > 0.01 && dSigned > 0) {
                // white-hot leading (outer) edge, saturated body behind it
                c = LXColor.lerp(c, LXColor.WHITE,
                  (float) (wow * 0.4 * Math.min(1, dSigned / (halfW + 0.5))));
              }
              if (wht > 0.01) {
                c = LXColor.lerp(c, LXColor.WHITE, wht);
              }
              final double throb = 1 - wow * 0.3
                * (0.5 + 0.5 * Math.sin(this.timeMs * 0.0045 + i * 1.7));
              addPix(o, x0 + lx, y, c, Math.min(1, cov * m * gain * throb));
            }
          }
        }

        // shattered-ring embers flying off this face
        for (int e = 0; e < MAX_EMBERS; ++e) {
          if (!this.eAlive[e]) continue;
          final double px = cxf + Math.cos(this.eAng[e]) * this.eRad[e] * rMax;
          final double py = cyf + Math.sin(this.eAng[e]) * this.eRad[e] * rMax;
          if (px < -2 || px > faceW + 1 || py < -2 || py > h + 1) continue;
          drawEmber(o, x0 + (int) Math.round(px), (int) Math.round(py),
            e, baseHue, gain, wht, tq);
        }
      }
    } else {
      // on the cylinder the circles wrap the circumference and rise from the base
      final double halfW = 0.15 + getSize() * 2.2; // Size: hairline → fat stroke
      for (int x = 0; x < w; ++x) {
        final double theta = (double) x / w * 2 * Math.PI;
        for (int i = 0; i < MAX_RINGS; ++i) {
          if (!this.alive[i]) continue;
          final double ringY = (h - 1) - this.rad[i] * (h - 1);
          final double reach = halfW * 3 + 1 + wow * halfW * 5;
          final int yLo = (int) Math.floor(ringY - reach);
          final int yHi = (int) Math.ceil(ringY + reach);
          final double m = arcMask(i, theta, this.rad[i], tq);
          if (m <= 0.02) continue;
          final int base = ringColor(i, baseHue, this.rad[i]);
          final double throb = 1 - wow * 0.3
            * (0.5 + 0.5 * Math.sin(this.timeMs * 0.0045 + i * 1.7));
          for (int y = Math.max(0, yLo); y <= Math.min(h - 1, yHi); ++y) {
            final double dSigned = ringY - y; // + above the ring: its travel direction
            final double dR = Math.abs(dSigned);
            // gaussian cross-section, matching the cube's soft-glow ring profile
            final double dn = dR / (halfW + 0.35);
            double cov = Math.exp(-1.6 * dn * dn);
            if (wow > 0.01) {
              final double an = dR / (halfW * 2.5 + 1.2);
              cov += wow * 0.35 * Math.exp(-0.9 * an * an);
            }
            if (cov <= 0.02) continue;
            int c = base;
            if (wow > 0.01 && dSigned > 0) {
              c = LXColor.lerp(c, LXColor.WHITE,
                (float) (wow * 0.4 * Math.min(1, dSigned / (halfW + 0.5))));
            }
            if (wht > 0.01) {
              c = LXColor.lerp(c, LXColor.WHITE, wht);
            }
            addPix(o, x, y, c, Math.min(1, cov * m * gain * throb));
          }
        }
      }

      // shattered-ring embers flinging up off the top rim
      for (int e = 0; e < MAX_EMBERS; ++e) {
        if (!this.eAlive[e]) continue;
        final double exf = this.eAng[e] / (2 * Math.PI) * w;
        final double eyf = (h - 1) - this.eRad[e] * (h - 1);
        drawEmber(o, (int) Math.round(exf), (int) Math.round(eyf),
          e, baseHue, gain, wht, tq);
      }
    }
  }
}
