/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Superbloom — rare rains hit the desert and the whole canvas erupts. Giant
 * green pixel-chunk buds appear scattered across the surface, swell, then
 * blossom open into top-down flowers in wild multi-colors all their own: six
 * species, each with two or three petal colors alternating around the bloom. The open flowers turn with the music — spinning
 * up as the level swells, pulsing on beats — and after a lifetime each bloom
 * folds away, its place taken by a fresh bud somewhere new. Wow pushes the
 * garden into overdrive: harder beat-pulses, shimmering petals, faster unfurl.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Superbloom extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int MAX_FLOWERS = 12;
  private static final double BUD_MS = 900;
  private static final double UNFURL_MS = 1500;
  private static final double WILT_MS = 950;
  private static final double MIN_BEAT_MS = 150;
  private static final double TAU = Math.PI * 2;

  // --- the six species: petals, petal sharpness, connected-core size, inner
  // layer (scale, angular offset; 0 = none), palette hue offsets, center size ---
  private static final int[]      F_PETALS = {  8,    6,   12,    5,   10,    5  };
  private static final double[]   F_SHARP  = { 1.8,  3.0,  4.0,  1.2,  2.2,  1.6 };
  private static final double[]   F_CMIN   = { 0.10, 0.12, 0.08, 0.18, 0.10, 0.15 };
  private static final double[]   F_INNER  = {  0,   0.55,  0,    0,   0.60, 0.50 };
  private static final double[][] F_HUES   = { {0, 40}, {0, 150, 210}, {0, 25},
                                               {0, 180}, {0, 90, 320}, {0, 60} };
  private static final double[]   F_CENTER = { 0.14, 0.12, 0.22, 0.13, 0.12, 0.16 };

  public final DiscreteParameter flowers =
    new DiscreteParameter("Flowers", 6, 1, MAX_FLOWERS)
    .setDescription("How many flowers live in the garden at once");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  private static final class Panel {
    final int[][] idx;
    final int w, h;
    final boolean wrap;
    Panel(int[][] idx, int w, int h, boolean wrap) {
      this.idx = idx; this.w = w; this.h = h; this.wrap = wrap;
    }
  }

  private Panel[] panels;
  private Target builtTarget;

  private final int[] fpanel = new int[MAX_FLOWERS];
  private final int[] ftype = new int[MAX_FLOWERS];
  private final double[] fx = new double[MAX_FLOWERS];
  private final double[] fy = new double[MAX_FLOWERS];
  private final double[] fr = new double[MAX_FLOWERS];    // full-bloom radius
  private final double[] fage = new double[MAX_FLOWERS];
  private final double[] flife = new double[MAX_FLOWERS]; // bloom hold duration
  private final double[] frot = new double[MAX_FLOWERS];
  private final double[] fdir = new double[MAX_FLOWERS];
  private final double[] fhue = new double[MAX_FLOWERS]; // each flower's own hue
  private final double[] fpulse = new double[MAX_FLOWERS];
  private final boolean[] falive = new boolean[MAX_FLOWERS];
  private boolean inited = false;

  // audio: level envelope spins the garden, beats make it pulse
  private double bassAvg, prevBass, sinceBeatMs = 1e9;
  private double levelEnv = 0;

  public Superbloom(LX lx) {
    // Base registers color (garden hue anchor), speed (tempo), size (bloom radius).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("flowers", this.flowers);
    addParameter("target", this.target);
  }

  private void buildPanels(Target t) {
    java.util.List<Panel> list = new java.util.ArrayList<>();
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
        list.add(new Panel(g, w, h, false));
      }
    }
    if (t != Target.CUBE) {
      Apotheneum.Orientation o = Apotheneum.cylinder.exterior;
      int w = o.width();
      int h = o.height();
      int[][] g = new int[w][h];
      for (int x = 0; x < w; ++x) {
        for (int y = 0; y < h; ++y) {
          g[x][y] = o.point(x, y).index;
        }
      }
      list.add(new Panel(g, w, h, true));
    }
    this.panels = list.toArray(new Panel[0]);
    this.builtTarget = t;
  }

  private void spawn(int i, boolean scatterAge) {
    // panel weighted by width
    int total = 0;
    for (Panel p : this.panels) total += p.w;
    final double radius = (6 + getSize() * 14) * (0.8 + 0.4 * Math.random());

    // a few placement attempts to avoid piling on a living neighbor
    int bestPanel = 0;
    double bestX = 0, bestY = 0, bestScore = -1;
    for (int attempt = 0; attempt < 8; ++attempt) {
      int pick = (int) (Math.random() * total);
      int pi = 0;
      for (int k = 0; k < this.panels.length; ++k) {
        pick -= this.panels[k].w;
        if (pick < 0) { pi = k; break; }
      }
      final Panel p = this.panels[pi];
      final double cx = p.wrap ? Math.random() * p.w
        : radius * 0.5 + Math.random() * (p.w - radius);
      final double cy = p.h * (0.22 + Math.random() * 0.56);
      double nearest = 1e9;
      for (int k = 0; k < MAX_FLOWERS; ++k) {
        if (!this.falive[k] || k == i || this.fpanel[k] != pi) continue;
        double dx = Math.abs(this.fx[k] - cx);
        if (p.wrap) dx = Math.min(dx, p.w - dx);
        final double dy = this.fy[k] - cy;
        nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy) - this.fr[k]);
      }
      if (nearest > bestScore) {
        bestScore = nearest;
        bestPanel = pi;
        bestX = cx;
        bestY = cy;
      }
    }

    this.fpanel[i] = bestPanel;
    this.fx[i] = bestX;
    this.fy[i] = bestY;
    this.fr[i] = radius;
    this.ftype[i] = (int) (Math.random() * F_PETALS.length);
    this.fhue[i] = Math.random() * 360; // wild-type color, independent of the palette
    this.flife[i] = 7000 + Math.random() * 7000;
    this.fage[i] = scatterAge ? Math.random() * (BUD_MS + UNFURL_MS + this.flife[i]) : 0;
    this.frot[i] = Math.random() * TAU;
    this.fdir[i] = (Math.random() < 0.5) ? 1 : -1;
    this.fpulse[i] = 0;
    this.falive[i] = true;
  }

  private boolean detectBeat(double deltaMs) {
    heronarts.lx.audio.GraphicMeter m = this.lx.engine.audio.meter;
    int nb = Math.max(1, m.numBands);
    double bass = m.getAveragef(0, Math.max(1, nb / 4));
    this.bassAvg += (bass - this.bassAvg) * (1 - Math.exp(-deltaMs / 400.0));
    this.sinceBeatMs += deltaMs;
    boolean beat = (bass > this.bassAvg * 1.3)
      && (bass > this.prevBass)
      && (this.sinceBeatMs >= MIN_BEAT_MS)
      && (bass > 0.01);
    this.prevBass = bass;
    if (beat) {
      this.sinceBeatMs = 0;
    }
    // broadband level envelope: snappy attack, easy release
    double level = m.getAveragef(0, nb);
    double alpha = (level > this.levelEnv)
      ? 1 - Math.exp(-deltaMs / 25.0)
      : 1 - Math.exp(-deltaMs / 220.0);
    this.levelEnv += (level - this.levelEnv) * alpha;
    return beat;
  }

  private static double smooth(double u) {
    u = LXUtils.clamp(u, 0, 1);
    return u * u * (3 - 2 * u);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    final Target t = this.target.getEnum();
    if (this.panels == null || this.builtTarget != t) {
      buildPanels(t);
      this.inited = false;
    }
    if (this.panels.length == 0) {
      return;
    }
    if (!this.inited) {
      for (int i = 0; i < MAX_FLOWERS; ++i) {
        this.falive[i] = false;
      }
      final int n0 = this.flowers.getValuei();
      for (int i = 0; i < n0; ++i) {
        spawn(i, true); // staggered ages so the garden isn't synchronized
      }
      this.inited = true;
    }

    final boolean beat = detectBeat(deltaMs);
    final double speed = Math.max(0.02, getSpeed());
    final double wow = getWow();
    final double dt = deltaMs * (0.4 + speed * 1.6);
    final int want = this.flowers.getValuei();
    // rotation: gentle drift, spun up hard by the music
    final double spin = deltaMs * speed * 0.00055 * (1.0 + this.levelEnv * 3.0 + wow * 0.5);

    int alive = 0;
    for (int i = 0; i < MAX_FLOWERS; ++i) {
      if (this.falive[i]) ++alive;
    }

    for (int i = 0; i < MAX_FLOWERS; ++i) {
      if (!this.falive[i]) {
        if (alive < want) {
          spawn(i, false);
          ++alive;
        } else {
          continue;
        }
      } else if (alive > want && this.fage[i] < BUD_MS) {
        // thin unopened buds when the Flowers knob comes down
        this.falive[i] = false;
        --alive;
        continue;
      }

      this.fage[i] += dt;
      this.frot[i] += spin * this.fdir[i];
      if (beat) {
        this.fpulse[i] = 1;
      }
      this.fpulse[i] *= Math.exp(-deltaMs / 180.0);

      final double totalLife = BUD_MS + UNFURL_MS + this.flife[i] + WILT_MS;
      if (this.fage[i] >= totalLife) {
        this.falive[i] = false;
        spawn(i, false); // a new bud opens somewhere else
      }

      draw(i, wow);
    }

    copyExterior();
  }

  private void draw(int i, double wow) {
    final Panel p = this.panels[this.fpanel[i]];
    final int type = this.ftype[i];
    final double age = this.fage[i];
    final double pulse = this.fpulse[i] * (0.5 + wow);

    // lifecycle scales
    double open;   // 0 = closed bud, 1 = fully open
    double scale;  // overall size envelope
    if (age < BUD_MS) {
      open = 0;
      scale = smooth(age / BUD_MS);
    } else if (age < BUD_MS + UNFURL_MS) {
      open = smooth((age - BUD_MS) / (UNFURL_MS / (1.0 + wow * 0.6)));
      scale = 1;
    } else if (age < BUD_MS + UNFURL_MS + this.flife[i]) {
      open = 1;
      scale = 1;
    } else {
      open = 1;
      final double wt = (age - BUD_MS - UNFURL_MS - this.flife[i]) / WILT_MS;
      scale = 1 - smooth(wt);
    }
    if (scale <= 0.01) return;

    final double R = this.fr[i] * scale * (1.0 + pulse * 0.07);
    final int petals = F_PETALS[type];
    final double sharp = F_SHARP[type];
    final double cmin = F_CMIN[type];
    final double inner = F_INNER[type];
    final double[] hues = F_HUES[type];
    final double centerR = F_CENTER[type] * R;

    // petal palette: this flower's own wild hue plus its species offsets
    final float flowerHue = (float) this.fhue[i];
    final int nc = hues.length;
    final double rot = this.frot[i];
    final double petalLen = R * (0.25 + 0.75 * open);
    final double pulseBright = 1.0 + pulse * 0.45;
    final int centerCol = LXColor.hsb(48, 70, 100); // golden stamen

    // Wow: continued blooming — once open, an extra ring of large petals keeps
    // growing outward through the bloom hold, reaching further the higher the dial
    final double holdT = LXUtils.clamp(
      (age - BUD_MS - UNFURL_MS) / Math.max(1.0, this.flife[i]), 0, 1);
    final double expand = (wow > 0.05 && open >= 1)
      ? smooth(LXUtils.clamp(holdT * (1.2 + wow * 1.4), 0, 1)) * wow : 0;
    final double Rx = R * (1.0 + 0.85 * expand); // expanded reach
    final double petalLen2 = Rx;

    final int y0 = (int) Math.max(0, Math.floor(this.fy[i] - Rx - 1));
    final int y1 = (int) Math.min(p.h - 1, Math.ceil(this.fy[i] + Rx + 1));
    final int x0 = (int) Math.floor(this.fx[i] - Rx - 1);
    final int x1 = (int) Math.ceil(this.fx[i] + Rx + 1);

    for (int yy = y0; yy <= y1; ++yy) {
      final double dy = yy - this.fy[i];
      for (int xx = x0; xx <= x1; ++xx) {
        final double dx = xx - this.fx[i];

        int c = 0;
        double b = 0;

        if (open <= 0) {
          // --- bud: a giant chunky pixel, quantized into blocks ---
          final double q = Math.max(2, R * 0.16);
          final double bxq = Math.floor(dx / q) * q + q * 0.5;
          final double byq = Math.floor(dy / q) * q + q * 0.5;
          final double budR = R * 0.30;
          if (Math.max(Math.abs(bxq), Math.abs(byq)) < budR) {
            final int bi = (int) Math.floor(dx / q) * 7 + (int) Math.floor(dy / q) * 131;
            final double v = 0.55 + 0.45 * hashd(bi + i * 977);
            // young buds are green, in blocky shades like unripe growth
            c = LXColor.hsb((float) (100 + 32 * hashd(bi * 13 + i * 331)),
              (float) (78 + 17 * hashd(bi * 29 + 5)), 100);
            b = scale * v * (0.75 + 0.25 * Math.sin(age * 0.006)); // breathing
          }
        } else {
          final double d = Math.sqrt(dx * dx + dy * dy);
          if (d <= Rx + 1) {
            final double theta = Math.atan2(dy, dx) - rot;
            // continued-bloom ring: large petals growing outward past the flower
            if (expand > 0.02 && d > R * 0.82) {
              final double aX = (theta + Math.PI / petals) * petals * 0.5;
              final double prX = Math.pow(Math.abs(Math.cos(aX)), sharp * 0.7);
              final double edgeX = petalLen2 * (cmin + (1 - cmin) * prX);
              if (d < edgeX) {
                final int pidxX = (int) Math.floor((theta + Math.PI / petals) / (TAU / petals) + 0.5);
                final int ciX = (((pidxX + 2) % nc) + nc) % nc;
                final double uX = 1 - (d - R * 0.82) / Math.max(1e-3, edgeX - R * 0.82);
                c = LXColor.hsb((flowerHue + (float) hues[ciX]) % 360,
                  (float) (78 - 18 * uX), 100);
                b = (0.30 + 0.55 * smooth(uX)) * pulseBright * expand;
              }
            }
            // outer petal ring
            double a = theta * petals * 0.5;
            final double pr = Math.pow(Math.abs(Math.cos(a)), sharp);
            final double edge = petalLen * (cmin + (1 - cmin) * pr);
            if (d < edge) {
              final int pidx = (int) Math.floor(theta / (TAU / petals) + 0.5);
              final int ci = ((pidx % nc) + nc) % nc;
              final double u = 1 - d / Math.max(1e-3, edge);
              c = LXColor.hsb((flowerHue + (float) hues[ci]) % 360, (float) (88 - 20 * u), 100);
              b = (0.40 + 0.60 * smooth(u * 1.4)) * pulseBright;
              // vein highlight down the middle of each petal
              b *= 1.0 + 0.35 * Math.pow(pr, 6);
            }
            // inner petal ring, offset half a petal, layered on top
            if (inner > 0) {
              final double thetaI = theta + Math.PI / petals;
              final double ai = thetaI * petals * 0.5;
              final double pri = Math.pow(Math.abs(Math.cos(ai)), sharp);
              final double edgeI = petalLen * inner * (cmin + (1 - cmin) * pri);
              if (d < edgeI) {
                final int pidx = (int) Math.floor(thetaI / (TAU / petals) + 0.5);
                final int ci = ((pidx + 1) % nc + nc) % nc;
                final double u = 1 - d / Math.max(1e-3, edgeI);
                c = LXColor.hsb((flowerHue + (float) hues[ci]) % 360, (float) (80 - 25 * u), 100);
                b = (0.55 + 0.45 * smooth(u)) * pulseBright;
              }
            }
            // stamen: warm bright center disc
            if (d < centerR * (0.4 + 0.6 * open)) {
              final double u = 1 - d / Math.max(1e-3, centerR);
              c = LXColor.lerp(centerCol, LXColor.WHITE, (float) (u * 0.5));
              b = (0.8 + 0.2 * u) * pulseBright;
            }
            // petal shimmer at high wow
            if (b > 0 && wow > 0.3) {
              b *= 1.0 + (wow - 0.3) * 0.5 * hashd(xx * 131 + yy * 7 + (int) (age * 0.02));
            }
          }
        }

        if (b <= 0.004) continue;
        int xi = xx;
        if (xi < 0 || xi >= p.w) {
          if (!p.wrap) continue;
          xi = ((xi % p.w) + p.w) % p.w;
        }
        final int idx = p.idx[xi][yy];
        this.colors[idx] = LXColor.lightest(this.colors[idx],
          LXColor.scaleBrightness(c, (float) LXUtils.clamp(b, 0, 1)));
      }
    }
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }
}
