/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * SpaceBoundSpecies — a flight through a field of stars. Each face of the
 * sculpture is a window into space with its vanishing point at the center. Speed
 * is a continuum: at rest the field drifts and twinkles, the brightest stars
 * flashing four-point diffraction spikes; wind it up and the stars stream
 * outward into hyperspace warp streaks. Wow bathes the field in a slow fBm
 * nebula in a complementary hue. A pure-Java Apotheneum take on the
 * LXStudio-TE Starfield shader.
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
import heronarts.lx.utils.Noise;

@LXCategory("Apotheneum/piemonte")
public class SpaceBoundSpecies extends ParameterPattern {

  private static final double TAU = Math.PI * 2;
  private static final int MAX_STARS = 256;
  private static final double MIN_VEL = 0.05;   // constant outward drift (no center clump)
  private static final double WARP_K = 3.0;     // perspective acceleration at speed 1
  private static final double STRETCH = 6.0;    // extra streak length at speed 1
  private static final double GOLDEN = 2.399963; // golden angle: per-panel rotation offset

  public final DiscreteParameter density =
    new DiscreteParameter("Density", 128, 8, MAX_STARS)
    .setDescription("Number of stars in flight");

  public final CompoundParameter twinkle =
    new CompoundParameter("Twinkle", 0.5, 0, 1)
    .setDescription("How much the stars flicker (strongest at low speed)");

  private static final class Panel {
    final int[][] idx;
    final int w, h;
    final double cx, cy, halfDiag, rot;
    Panel(int[][] idx, int w, int h, double rot) {
      this.idx = idx; this.w = w; this.h = h;
      this.cx = w * 0.5; this.cy = h * 0.5;
      this.halfDiag = 0.5 * Math.hypot(w, h);
      this.rot = rot;
    }
  }

  private Panel[] panels;
  private Target builtTarget;

  // shared normalized-space star pool (unit disk)
  private final double[] ang = new double[MAX_STARS];
  private final double[] rad = new double[MAX_STARS];
  private final double[] radPrev = new double[MAX_STARS];
  private final double[] twPhase = new double[MAX_STARS];
  private final double[] twRate = new double[MAX_STARS];
  private final double[] baseBright = new double[MAX_STARS];
  private final boolean[] tinted = new boolean[MAX_STARS];
  private boolean inited = false;
  private double timeMs = 0;

  public SpaceBoundSpecies(LX lx) {
    // Base registers color (star tint), speed (drift->warp), size (bloom/streak), wow (nebula).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("density", this.density);
    addParameter("twinkle", this.twinkle);
    addTargetParameter();
  }

  private void buildPanels(Target t) {
    java.util.List<Panel> list = new java.util.ArrayList<>();
    int pi = 0;
    if (t != Target.CYLINDER) {
      for (Apotheneum.Cube.Face face : Apotheneum.cube.exterior.faces) {
        int w = face.columns.length;
        int h = face.columns[0].points.length;
        int[][] g = new int[w][h];
        for (int x = 0; x < w; ++x) {
          for (int y = 0; y < h; ++y) {
            g[x][y] = face.columns[x].points[y].index;
          }
        }
        list.add(new Panel(g, w, h, pi++ * GOLDEN));
      }
    }
    if (t != Target.CUBE) {
      Apotheneum.Orientation o = Apotheneum.cylinder.exterior;
      int q = o.width() / 4;
      int h = o.height();
      for (int seg = 0; seg < 4; ++seg) {
        int[][] g = new int[q][h];
        for (int x = 0; x < q; ++x) {
          for (int y = 0; y < h; ++y) {
            g[x][y] = o.point(seg * q + x, y).index;
          }
        }
        list.add(new Panel(g, q, h, pi++ * GOLDEN));
      }
    }
    this.panels = list.toArray(new Panel[0]);
    this.builtTarget = t;
  }

  private void recycle(int i) {
    this.rad[i] = 0.02 + Math.random() * 0.12;
    this.radPrev[i] = this.rad[i];
    this.ang[i] = Math.random() * TAU;
    this.twPhase[i] = Math.random() * TAU;
    this.twRate[i] = 6 + Math.random() * 8;
    this.baseBright[i] = 0.4 + 0.6 * Math.random();
    this.tinted[i] = Math.random() < 0.25;
  }

  private static double smoothstep(double e0, double e1, double x) {
    double t = (x - e0) / (e1 - e0);
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
    return t * t * (3 - 2 * t);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    final Target t = getTarget();
    if (this.panels == null || this.builtTarget != t) {
      buildPanels(t);
    }
    if (this.panels.length == 0) {
      return;
    }
    if (!this.inited) {
      for (int i = 0; i < MAX_STARS; ++i) {
        recycle(i);
        this.rad[i] = Math.random() * 1.1; // spread the initial field
        this.radPrev[i] = this.rad[i];
      }
      this.inited = true;
    }

    this.timeMs += deltaMs;
    final double ts = this.timeMs * 0.001;
    final double speed01 = getSpeed();
    final double dt = deltaMs * 0.001;
    final double warp = WARP_K * speed01;
    final int count = this.density.getValuei();
    final double sizeF = getSize();
    final double twAmt = this.twinkle.getValue();
    final double wow = getWow();
    final int base = getColor();
    final float tintHue = LXColor.h(base);
    final boolean nebula = wow > 0.01;
    final int nebColor = LXColor.hsb((float) ((tintHue + 150) % 360), 75, 100);

    // advance the field once, in normalized space
    for (int i = 0; i < count; ++i) {
      this.radPrev[i] = this.rad[i];
      this.rad[i] += (MIN_VEL + warp * this.rad[i]) * dt;
      if (this.rad[i] > 1.2) {
        recycle(i);
      }
    }

    for (Panel p : this.panels) {
      // slow nebula wash (only when Wow is up)
      if (nebula) {
        final float drift = (float) (ts * 0.03);
        for (int y = 0; y < p.h; ++y) {
          double uvy = (y - p.cy) / p.halfDiag;
          for (int x = 0; x < p.w; ++x) {
            double uvx = (x - p.cx) / p.halfDiag;
            float n = Noise.stb_perlin_fbm_noise3(
              (float) (uvx * 1.5 + drift + p.rot), (float) (uvy * 1.5), (float) (ts * 0.05),
              2.0f, 0.5f, 2);
            double nb = (n * 0.5 + 0.5);
            nb = nb * nb * nb;
            if (nb > 0.05) {
              int idx = p.idx[x][y];
              this.colors[idx] = LXColor.lightest(this.colors[idx],
                LXColor.scaleBrightness(nebColor, (float) (nb * wow * 0.4)));
            }
          }
        }
      }

      // stars
      for (int i = 0; i < count; ++i) {
        final double a = this.ang[i] + p.rot;
        final double ca = Math.cos(a), sa = Math.sin(a);
        final double r = this.rad[i];
        final double sx = p.cx + ca * r * p.halfDiag;
        final double sy = p.cy + sa * r * p.halfDiag;

        double tw = 1.0 - twAmt * (1.0 - 0.7 * speed01) * 0.5
          * (1.0 + Math.sin(ts * this.twRate[i] + this.twPhase[i]));
        double b = this.baseBright[i] * (0.3 + 0.7 * smoothstep(0, 1, r)) * tw;
        if (b <= 0.01) continue;
        double bloomR = (0.6 + sizeF * 3.0) * (0.4 + r);
        int col = this.tinted[i] ? LXColor.hsb(tintHue, 45, 100) : LXColor.WHITE;

        if (speed01 < 0.08) {
          splat(p, sx, sy, bloomR, b, col);
          // diffraction spikes on the hero stars
          if (this.baseBright[i] > 0.85) {
            double sp = 0.45 * b * (0.5 + 0.5 * Math.sin(ts * 2 + this.twPhase[i]));
            double so = bloomR + 1;
            splat(p, sx - so, sy, 0.6, sp, col);
            splat(p, sx + so, sy, 0.6, sp, col);
            splat(p, sx, sy - so, 0.6, sp, col);
            splat(p, sx, sy + so, 0.6, sp, col);
          }
        } else {
          double sxp = p.cx + ca * this.radPrev[i] * p.halfDiag;
          double syp = p.cy + sa * this.radPrev[i] * p.halfDiag;
          double ex = sx + (sx - sxp) * speed01 * STRETCH;
          double ey = sy + (sy - syp) * speed01 * STRETCH;
          edge(p, sxp, syp, ex, ey, col, b, bloomR);
        }
      }

      // Wow: orange particles materialize among the stars, glitching and
      // flickering — more of them the higher the dial
      if (wow > 0.05) {
        final int nGlitch = (int) Math.round(wow * 26);
        final int gq = (int) (this.timeMs / 60); // fast flicker quantum
        final int orange = LXColor.hsb(28, 95, 100);
        final int orangeHot = LXColor.lerp(orange, LXColor.WHITE, 0.5f);
        for (int gi = 0; gi < nGlitch; ++gi) {
          // ~55% duty flicker, re-rolled per quantum -> hard glitch blink
          final double gate = hashd(gi * 977 + gq * 131);
          if (gate > 0.55) continue;
          final double gx = hashd(gi * 31 + 7) * p.w
            + (hashd(gi * 61 + gq * 17) - 0.5) * 2.5; // positional jitter
          final double gy = hashd(gi * 53 + 11) * p.h
            + (hashd(gi * 43 + gq * 23) - 0.5) * 2.0;
          final double gb = (0.4 + 0.6 * hashd(gi * 97 + gq * 7)) * wow;
          splat(p, gx, gy, 0.9 + wow * 0.6, gb,
            (gate < 0.12) ? orangeHot : orange);
        }
      }
    }

    copyExterior();
  }

  private void edge(Panel p, double x0, double y0, double x1, double y1,
      int color, double bright, double r) {
    double dx = x1 - x0, dy = y1 - y0;
    int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
    if (steps < 1) steps = 1;
    for (int k = 0; k <= steps; ++k) {
      double t = (double) k / steps;
      splat(p, x0 + dx * t, y0 + dy * t, r, bright, color);
    }
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  private void splat(Panel p, double x, double y, double r, double bright, int color) {
    int y0 = (int) Math.max(0, Math.floor(y - r));
    int y1 = (int) Math.min(p.h - 1, Math.ceil(y + r));
    int x0 = (int) Math.max(0, Math.floor(x - r));
    int x1 = (int) Math.min(p.w - 1, Math.ceil(x + r));
    for (int yy = y0; yy <= y1; ++yy) {
      double ddy = yy - y;
      for (int xx = x0; xx <= x1; ++xx) {
        double ddx = xx - x;
        double dd = Math.sqrt(ddx * ddx + ddy * ddy);
        if (dd > r) continue;
        double u = 1.0 - dd / r;
        u = u * u * (3 - 2 * u);
        double f = u * u * bright;
        if (f <= 0) continue;
        int idx = p.idx[xx][yy];
        this.colors[idx] = LXColor.lightest(this.colors[idx], LXColor.scaleBrightness(color, (float) f));
      }
    }
  }
}
