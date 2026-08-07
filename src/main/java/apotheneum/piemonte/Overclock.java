/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Overclock — a dense fixed curtain of vertical strands shimmers with light
 * streaming downward into a bright pool at the floor, while a circuit maze of
 * horizontal traces and right-angle jogs glows near the hang line, slowly
 * re-routing itself. The whole room breathes between emerald and azure in long
 * sustained phases — each handover fading through near-black and blooming back
 * in the new color, passing through cyan on the way. Modeled on the
 * holotrigger Shimmer Hammer program.
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
public class Overclock extends StrandPattern {

  private static final int MAX_PACKETS = 12;
  private static final double HUE_A = 125; // emerald
  private static final double HUE_B = 210; // azure
  private static final double CYCLE_MS = 8000; // full green->blue->green breath
  private static final double JOG_INTERVAL_MS = 1500;
  private static final int STRAND_SPACING = 5; // fixed curtain pitch

  public final DiscreteParameter traces =
    new DiscreteParameter("Traces", 6, 2, MAX_PACKETS)
    .setDescription("Circuit traces re-routing in the top maze");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Audio sensitivity of the swell");

  private final class Surface {
    boolean inited;
    // maze traces confined to the hang line
    final double[] px = new double[MAX_PACKETS];
    final double[] py = new double[MAX_PACKETS];
    final double[] pv = new double[MAX_PACKETS];
    final double[] jogT = new double[MAX_PACKETS];
    final int[] jx0 = new int[MAX_PACKETS];
    final int[] jx1 = new int[MAX_PACKETS];
    final int[] jy = new int[MAX_PACKETS];
    final double[] jenv = new double[MAX_PACKETS];
    void reset(int w, int h) {
      for (int i = 0; i < MAX_PACKETS; ++i) {
        this.px[i] = Math.random() * w;
        this.py[i] = Math.random() * h * 0.28;
        this.pv[i] = (0.002 + Math.random() * 0.0025) * (Math.random() < 0.5 ? 1 : -1);
        this.jogT[i] = Math.random() * JOG_INTERVAL_MS;
        this.jenv[i] = 0;
      }
      this.inited = true;
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double pulse = 0;

  public Overclock(LX lx) {
    // Base registers color (hue shift), speed (breath pace), size (shimmer depth).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("traces", this.traces);
    addParameter("sensitivity", this.sensitivity);
    addTargetParameter();
  }

  @Override
  protected double beatThreshold() {
    return 1.05 + (1 - this.sensitivity.getValue()) * 0.7;
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  @Override
  protected void advance(double deltaMs) {
    if (this.beat) {
      this.pulse = 1;
    }
    this.pulse *= Math.exp(-deltaMs / 190.0);
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final Surface s = isCube ? this.cube : this.cylinder;
    final int w = o.width();
    final int h = o.height();
    if (!s.inited) s.reset(w, h);

    final double wow = getWow();
    final double speed = Math.max(0.05, getSpeed());
    final double hueShift = LXColor.h(getColor());

    // sustained green<->blue breath with plateaus at each pole; handovers pass
    // through cyan AND through a near-black trough
    final double cyc = (this.timeMs * speed * 2) % CYCLE_MS / CYCLE_MS;
    double c = 0.5 - 0.5 * Math.cos(2 * Math.PI * cyc);
    final double mix = c * c * (3 - 2 * c);
    final double crossover = 1 - Math.abs(mix - 0.5) * 2;
    final double breath = LXUtils.lerp(1.0, 0.15, crossover * crossover);
    final double hue = LXUtils.lerp(HUE_A, HUE_B, mix) + hueShift;
    final int col = LXColor.hsb((float) (((hue % 360) + 360) % 360), 92, 100);
    final double gain = breath * (1.0 + this.pulse * 0.15) * (0.6 + 0.4 * this.levelEnv + wow * 0.3);
    final int tq = (int) (this.timeMs / 70);

    // fixed dense strand curtain: shimmer scrolls downward, draining into the pool
    final double shimDepth = 0.35 + getSize() * 0.30;
    for (int bx = STRAND_SPACING / 2; bx < w; bx += STRAND_SPACING) {
      final int jitter = (int) ((hashd(bx * 31 + 7) - 0.5) * 2);
      final int x = bx + jitter;
      for (int y = 0; y < h; ++y) {
        // downward-scrolling seed: light streams toward the floor
        final double sh = (1 - shimDepth) + shimDepth * hashd(x * 131 + (y - tq) * 7);
        double b = 0.30 * gain * sh * drip(y, h);
        // the base pool drains last: bottom rows lean on pool() with less breath-gating
        b += (0.5 * pool(y, h)) * sh * (0.4 + 0.6 * breath) * (1.0 + this.pulse * 0.2);
        addPix(o, x, y, col, b);
      }
    }

    // circuit maze near the hang line: slow traces with right-angle re-routes
    final int count = Math.min(MAX_PACKETS, this.traces.getValuei() + (int) Math.round(wow * 3));
    final int mazeH = (int) (h * 0.30);
    for (int i = 0; i < count; ++i) {
      s.py[i] += s.pv[i] * speed * deltaMs;
      if (s.py[i] < 0) { s.py[i] = 0; s.pv[i] = -s.pv[i]; }
      else if (s.py[i] > mazeH) { s.py[i] = mazeH; s.pv[i] = -s.pv[i]; }

      s.jogT[i] -= deltaMs;
      if (s.jogT[i] <= 0) {
        s.jogT[i] = JOG_INTERVAL_MS * (0.6 + Math.random()) / (1 + wow);
        if (Math.random() < 0.6) {
          final int dx = (3 + (int) (Math.random() * 6)) * (Math.random() < 0.5 ? 1 : -1);
          s.jx0[i] = (int) s.px[i];
          s.jx1[i] = (int) s.px[i] + dx;
          s.jy[i] = (int) s.py[i];
          s.jenv[i] = 1;
          s.px[i] = ((s.px[i] + dx) % w + w) % w;
        }
      }

      // short vertical trace segment
      for (int k = 0; k <= 4; ++k) {
        final double b = gain * 0.9 * Math.exp(-0.5 * k);
        addPix(o, (int) s.px[i], (int) s.py[i] - k, col, b);
        addPix(o, (int) s.px[i] + 1, (int) s.py[i] - k, col, b * 0.5);
      }
      // glowing right-angle bridge from the last re-route
      if (s.jenv[i] > 0.02) {
        final int lo = Math.min(s.jx0[i], s.jx1[i]);
        final int hi2 = Math.max(s.jx0[i], s.jx1[i]);
        for (int x = lo; x <= hi2; ++x) {
          addPix(o, x, s.jy[i], col, s.jenv[i] * gain * 0.85);
          addPix(o, x, s.jy[i] + 1, col, s.jenv[i] * gain * 0.35);
        }
        s.jenv[i] *= Math.exp(-deltaMs / 600.0); // traces linger
      }
    }
  }
}
