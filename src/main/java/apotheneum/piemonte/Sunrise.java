/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Sunrise — a sun literally rises from the bottom of each facade. The disc
 * climbs from below the horizon — huge, molten, all orange and yellow —
 * radiating thick yellow and orange beams outward at angled directions. The higher the sun climbs, the more beams break out, each one
 * flaring in from nothing and swaying gently as it burns. At the peak the
 * day turns and the sun eases back down, beams folding away, before dawn
 * breaks again. Beats flare the beams; Wow speeds the sway and sharpens the
 * flare.
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
public class Sunrise extends StrandPattern {

  private static final int MAX_BEAMS = 24;
  private static final double CYCLE_MS = 52000;  // full rise-and-set at speed 0.5
  private static final double Y_LOW = 1.14;      // start below the horizon
  private static final double Y_HIGH = 0.28;     // peak height (0 = top)

  public final DiscreteParameter beams =
    new DiscreteParameter("Beams", 16, 6, MAX_BEAMS)
    .setDescription("Beams radiating at full rise");

  // per-frame beam table (no per-pixel trig re-derivation)
  private final double[] beamAng = new double[MAX_BEAMS];
  private final double[] beamHw = new double[MAX_BEAMS];
  private final double[] beamVis = new double[MAX_BEAMS];
  private final boolean[] beamWhite = new boolean[MAX_BEAMS];

  private double phase = 0; // 0..1 around the rise-and-set cycle
  private double flare = 0; // beat flare envelope

  public Sunrise(LX lx) {
    // Base registers color (sun hue anchor), speed (day pace), size (sun radius).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("beams", this.beams);
    addTargetParameter();
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  /** How far the sun has risen, 0..1, easing at both horizon and peak. */
  private double riseAmount() {
    final double tri = 1.0 - Math.abs(1.0 - 2.0 * this.phase); // rise then set
    return tri * tri * (3 - 2 * tri);
  }

  @Override
  protected void advance(double deltaMs) {
    final double speed = Math.max(0.05, getSpeed());
    this.phase += deltaMs * speed * 2 / CYCLE_MS;
    this.phase -= Math.floor(this.phase);

    if (this.beat) {
      this.flare = 1;
    }
    this.flare *= Math.exp(-deltaMs / 300.0);

    // beam table: angles fan the sky, swaying gently; more break out with rise
    final double rise = riseAmount();
    final double wow = getWow();
    final int n = this.beams.getValuei();
    final double sway = this.timeMs * 0.001 * (0.25 + wow * 1.1);
    for (int i = 0; i < n; ++i) {
      // spread over the full circle; low-threshold beams appear first
      this.beamAng[i] = (double) i / n * 2 * Math.PI
        + hashd(i * 77 + 5) * 0.5
        + Math.sin(sway + hashd(i * 31) * 6.28) * (0.05 + wow * 0.10);
      this.beamHw[i] = Math.toRadians(5 + 7 * hashd(i * 131 + 9)); // thick
      final double thr = hashd(i * 53 + 3) * 0.8;
      this.beamVis[i] = LXUtils.clamp((rise - thr) / 0.12, 0, 1);
      this.beamWhite[i] = (i % 3) == 2; // every third beam burns orange
    }
  }

  private static double angDiff(double a, double b) {
    double d = a - b;
    while (d > Math.PI) d -= 2 * Math.PI;
    while (d < -Math.PI) d += 2 * Math.PI;
    return d;
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final int w = o.width();
    final int h = o.height();
    final double rise = riseAmount();
    final double wow = getWow();
    final int n = this.beams.getValuei();
    final int tq = (int) (this.timeMs / 90);

    final float hue = (LXColor.h(getColor()) + 46) % 360; // Hue 0 = classic yellow sun
    final int core = LXColor.hsb(hue, 42, 100);            // molten yellow center
    final int body = LXColor.hsb(hue, 88, 100);            // yellow
    final int rim = LXColor.hsb((hue + 22) % 360, 95, 96); // orange edge/halo
    final int beamY = LXColor.hsb(hue, 80, 100);
    final int beamW = LXColor.hsb((hue + 24) % 360, 92, 100); // orange beams

    final double R = (0.20 + getSize() * 0.35) * h; // sun radius in rows
    final double yc = LXUtils.lerp(Y_LOW, Y_HIGH, rise);
    final double sunY = yc * (h - 1);
    final double beamGain = (0.75 + 0.25 * this.levelEnv + this.flare * 0.5)
      * LXUtils.clamp(rise * 3, 0, 1);

    final int nSuns = isCube ? 4 : 2; // one per cube face, two on the cylinder
    final int half = w / (2 * nSuns);

    for (int s = 0; s < nSuns; ++s) {
      final int cx = (int) ((s + 0.5) * w / (double) nSuns);
      for (int dx = -half; dx < half; ++dx) {
        final int x = ((cx + dx) % w + w) % w;
        for (int y = 0; y < h; ++y) {
          final double ddy = sunY - y; // positive above the sun
          final double r = Math.sqrt(dx * dx + ddy * ddy);

          if (r <= R + 1.5) { // the disc: white core to yellow to orange rim
            final double u = r / R;
            final double edge = LXUtils.clamp(R + 1.0 - r, 0, 1);
            final int c = (u < 0.45) ? core
              : (u < 0.85) ? LXColor.lerp(body, core,
                  (float) ((0.85 - u) / 0.40) * 0.5f)
              : LXColor.lerp(rim, body, (float) LXUtils.clamp((1.0 - u) / 0.15, 0, 1));
            final double boil = 0.92 + 0.08 * hashd(x * 131 + y * 7 + tq);
            addPix(o, x, y, c, edge * boil);
            continue;
          }

          // warm halo hugging the disc
          final double halo = Math.exp(-(r - R) / (R * 0.55)) * 0.45;
          if (halo > 0.02) {
            addPix(o, x, y, rim, halo);
          }

          // the beams: thick angled rays, more of them the higher the sun
          final double ang = Math.atan2(-ddy, dx); // -π/2 = straight up... flip below
          double bb = 0;
          boolean white = false;
          for (int i = 0; i < n; ++i) {
            if (this.beamVis[i] <= 0) continue;
            final double d = Math.abs(angDiff(ang, this.beamAng[i]));
            if (d >= this.beamHw[i]) continue;
            final double aFall = 1.0 - (d / this.beamHw[i]) * (d / this.beamHw[i]);
            final double rFall = Math.exp(-1.5 * (r - R) / h);
            final double v = this.beamVis[i] * aFall * rFall;
            if (v > bb) {
              bb = v;
              white = this.beamWhite[i];
            }
          }
          if (bb > 0.02) {
            final double tw = 0.85 + 0.15 * hashd(x * 977 + y * 53 + tq * 7);
            addPix(o, x, y, white ? beamW : beamY,
              Math.min(1, bb * beamGain * tw * (1 + wow * 0.3)));
          }
        }
      }
    }
  }
}
