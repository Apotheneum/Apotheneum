/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Nice — the multi-color orb chamber from the holotrigger arcade reference. Glowing circles in vivid mixed colors —
 * golden yellows, oranges, ice blues, magentas, greens — float among the
 * strands at varied sizes and heights, each with a white-hot core, a soft
 * speckled body, and light spilling down the strands beneath it. Every orb
 * rides the UFOAbduction velocity signature: a fast beam up from the floor,
 * a long slow-motion hover through the center of the room, then a burst out
 * the top as a fresh orb is born below. Orbs throb like lanterns as they
 * hover; beats kick their glow and hatch extras. Wow packs the room with
 * more orbs and hardens the throb toward a flicker.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Nice extends StrandPattern {

  private static final int MAX_ORBS = 40;
  // UFOAbduction's measured rise profile: fast entry, center stall, exit burst
  private static final double V_ENTRY = 0.00135;
  private static final double V_STALL = 0.00024;
  private static final double V_EXIT = 0.0075;
  private static final double STALL_LO = 0.60;
  private static final double STALL_HI = 0.44;
  private static final double EXIT_AT = 0.30;
  // the reference's color families: golds, oranges, ice blues, magentas, greens
  private static final double[] FAMILY_HUES = { 46, 24, 205, 315, 135, 190 };

  public final DiscreteParameter orbs =
    new DiscreteParameter("Orbs", 14, 4, MAX_ORBS)
    .setDescription("Orbs floating in the chamber");

  // one shared simulation in normalized coordinates, rendered to both surfaces
  private final double[] ox = new double[MAX_ORBS];
  private final double[] oy = new double[MAX_ORBS];
  private final double[] or = new double[MAX_ORBS];   // radius, fraction of height
  private final double[] ohue = new double[MAX_ORBS];
  private final double[] ovf = new double[MAX_ORBS];  // speed variance
  private final double[] oph = new double[MAX_ORBS];  // throb phase
  private final boolean[] alive = new boolean[MAX_ORBS];
  private double kick = 0;
  private int seed = 1;

  public Nice(LX lx) {
    // Base registers color (hue shift), speed (rise pace), size (orb radius).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("orbs", this.orbs);
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

  private void spawn(int i) {
    ++this.seed;
    this.ox[i] = hashd(this.seed * 97 + i * 13);
    this.oy[i] = 1.06 + hashd(this.seed * 61 + 7) * 0.25; // below the floor
    this.or[i] = 0.05 + hashd(this.seed * 31 + 3) * 0.10; // varied sizes
    // mostly family colors with the occasional wildcard, like the reference
    final double pick = hashd(this.seed * 53 + 11);
    this.ohue[i] = (pick < 0.85)
      ? FAMILY_HUES[(int) (hashd(this.seed * 17 + 5) * FAMILY_HUES.length)]
        + (hashd(this.seed * 7 + 1) - 0.5) * 18
      : hashd(this.seed * 41 + 9) * 360;
    this.ovf[i] = 0.7 + hashd(this.seed * 23 + 15) * 0.6;
    this.oph[i] = hashd(this.seed * 89 + 21) * Math.PI * 2;
    this.alive[i] = true;
  }

  @Override
  protected void advance(double deltaMs) {
    final double speed = Math.max(0.05, getSpeed());
    final double wow = getWow();

    if (this.beat) {
      this.kick = 1;
    }
    this.kick *= Math.exp(-deltaMs / 260.0);

    // population target; Wow packs the room, beats hatch a couple extra
    final int want = Math.min(MAX_ORBS, (int) (this.orbs.getValuei()
      * (1 + wow * 0.7) + this.kick * 2));
    int count = 0;
    for (int i = 0; i < MAX_ORBS; ++i) {
      if (this.alive[i]) ++count;
    }
    for (int i = 0; i < MAX_ORBS && count < want; ++i) {
      if (!this.alive[i]) {
        spawn(i);
        ++count;
      }
    }

    // each orb rides the abduction profile up through the chamber
    for (int i = 0; i < MAX_ORBS; ++i) {
      if (!this.alive[i]) continue;
      this.oy[i] -= this.ovf[i] * riseProfile(this.oy[i]) * speed * 2 * deltaMs;
      if (this.oy[i] <= -0.12) {
        this.alive[i] = false; // out the top; a fresh orb hatches below
      }
    }
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final int w = o.width();
    final int h = o.height();
    final double wow = getWow();
    final double hueShift = LXColor.h(getColor());
    final double sizeF = 0.6 + getSize() * 1.1;
    final int tq = (int) (this.timeMs / 80);
    final double glowKick = 1 + this.kick * 0.35 + this.levelEnv * 0.25;

    for (int i = 0; i < MAX_ORBS; ++i) {
      if (!this.alive[i]) continue;
      final double yp = this.oy[i];
      final boolean stalled = (yp <= STALL_LO && yp >= STALL_HI);

      final float hue = (float) (((this.ohue[i] + hueShift) % 360 + 360) % 360);
      final int body = LXColor.hsb(hue, 92, 100);
      final int core = LXColor.lerp(body, LXColor.WHITE, 0.55f);

      // lantern throb while hovering; Wow hardens it toward a flicker
      double throb = 1.0;
      if (stalled) {
        final double sIn = Math.sin(this.timeMs * (0.004 + wow * 0.006) + this.oph[i]);
        throb = 0.78 + 0.22 * sIn;
        if (wow > 0.6 && hashd(i * 131 + tq * 7) < (wow - 0.6) * 0.9) {
          throb *= 0.35; // hard flicker drops at high Wow
        }
      }

      final double cy = yp * (h - 1);
      final double R = this.or[i] * h * sizeF;
      final int cx = (int) (this.ox[i] * w);
      final int iR = (int) Math.ceil(R);

      // the orb: white-hot core, colored body, soft speckled edge
      for (int dy = -iR; dy <= iR; ++dy) {
        final int yy = (int) Math.round(cy) + dy;
        if (yy < 0 || yy >= h) continue;
        for (int dx = -iR; dx <= iR; ++dx) {
          final double d = Math.sqrt(dx * dx + dy * dy) / R;
          if (d > 1) continue;
          final double g = Math.exp(-d * d * 2.6);
          // strand speckle: vertical thread texture inside the orb
          final double sp = 0.72 + 0.28 * hashd((cx + dx) * 131 + yy * 7 + tq);
          final int c = (d < 0.30) ? core : body;
          addPix(o, cx + dx, yy, c,
            Math.min(1, g * sp * throb * glowKick));
        }
      }

      // light spilling down the strands beneath the orb
      final int spill = (int) (R * 2.5);
      for (int k = 1; k <= spill; ++k) {
        final int yy = (int) Math.round(cy) + iR + k;
        if (yy >= h) break;
        final double f = Math.exp(-1.8 * k / spill);
        if (f < 0.03) break;
        for (int dx = -iR / 2; dx <= iR / 2; ++dx) {
          final double tw = 0.5 + 0.5 * hashd((cx + dx) * 977 + yy * 53 + tq * 3);
          addPix(o, cx + dx, yy, body, f * 0.30 * tw * throb);
        }
      }
    }
  }
}
