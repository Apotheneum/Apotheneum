/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * TunnelVision — vertical tunnels of glowing hoops climb the surfaces. Each
 * chain draws upward ring by ring from its base — every ring smaller and
 * closer than the last, a tunnel receding into the sky — hangs complete for a
 * beat, then collapses in the same order. The music runs the show: bass
 * onsets punch new tunnels open (hard hits open several at once) and the
 * louder it gets the more tunnels climb at once. A soft glint sweeps around
 * each hoop; Wow opens more tunnels, drives the cadence, and sharpens the
 * shine. In silence a few tunnels keep drawing on their own.
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
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class TunnelVision extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int MAX_CHAINS = 64;
  private static final int MAX_RINGS = 18;
  private static final double PERSP = 0.91;     // per-ring shrink into the tunnel
  private static final double STAGGER_MS = 90;  // per-ring delay along the chain
  private static final double GROW_MS = 150;    // ring pop-in
  private static final double HOLD_MS = 550;    // complete chain hangs here
  private static final double SHRINK_MS = 240;  // ring collapse
  private static final double REF_BEAT_MS = 550; // lifecycle reference tempo (~109 BPM)
  private static final double TAU = Math.PI * 2;

  public final DiscreteParameter chains =
    new DiscreteParameter("Chains", 36, 4, MAX_CHAINS)
    .setDescription("How many hoop chains are alive at once");

  public final DiscreteParameter rings =
    new DiscreteParameter("Rings", 12, 4, MAX_RINGS)
    .setDescription("Hoops per chain");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How readily the music opens new tunnels");

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

  private final int[] cpanel = new int[MAX_CHAINS];
  private final double[] cx0 = new double[MAX_CHAINS];
  private final double[] cy0 = new double[MAX_CHAINS];
  private final double[] cdir = new double[MAX_CHAINS];
  private final double[] cslope = new double[MAX_CHAINS];
  private final double[] cspace = new double[MAX_CHAINS];
  private final double[] cage = new double[MAX_CHAINS];
  private final double[] cwait = new double[MAX_CHAINS]; // respawn delay
  private final int[] cn = new int[MAX_CHAINS];
  private final boolean[] calive = new boolean[MAX_CHAINS];
  private boolean inited = false;
  private double timeMs = 0;

  // audio: tunnels open on bass onsets, population swells with the level
  private double bassAvg, prevBass, sinceBeatMs = 1e9;
  private double levelEnv = 0;
  private double beatPeriodMs = REF_BEAT_MS; // tracked tempo: EMA of onset spacing

  /** Returns onset strength 0..1, or -1 if no beat this frame. */
  private double detect(double deltaMs) {
    heronarts.lx.audio.GraphicMeter m = this.lx.engine.audio.meter;
    int nb = Math.max(1, m.numBands);
    double bass = m.getAveragef(0, Math.max(1, nb / 4));
    this.bassAvg += (bass - this.bassAvg) * (1 - Math.exp(-deltaMs / 400.0));
    this.sinceBeatMs += deltaMs;
    double sens = this.sensitivity.getValue();
    double threshold = 1.05 + (1 - sens) * 0.7;
    boolean beat = (bass > this.bassAvg * threshold)
      && (bass > this.prevBass)
      && (this.sinceBeatMs >= 140)
      && (bass > 0.01);
    double ratio = LXUtils.clamp((bass / Math.max(1e-4, this.bassAvg) - 1.0) / 1.5, 0, 1);
    this.prevBass = bass;
    if (beat) {
      // track the tempo: smooth the spacing between onsets (clamped to sane BPM)
      double interval = LXUtils.clamp(this.sinceBeatMs, 240, 1500);
      this.beatPeriodMs += (interval - this.beatPeriodMs) * 0.25;
      this.sinceBeatMs = 0;
    } else if (this.sinceBeatMs > 4000) {
      // no music: relax back to the reference cadence
      this.beatPeriodMs += (REF_BEAT_MS - this.beatPeriodMs) * (1 - Math.exp(-deltaMs / 2000.0));
    }
    double level = m.getAveragef(0, nb);
    double alpha = (level > this.levelEnv)
      ? 1 - Math.exp(-deltaMs / 25.0)
      : 1 - Math.exp(-deltaMs / 220.0);
    this.levelEnv += (level - this.levelEnv) * alpha;
    return beat ? ratio : -1;
  }

  public TunnelVision(LX lx) {
    // Base registers color (hoop tint), speed (sequence tempo), size (hoop radius).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("chains", this.chains);
    addParameter("rings", this.rings);
    addParameter("sensitivity", this.sensitivity);
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
      list.add(new Panel(g, w, h, true)); // seamless around the cylinder
    }
    this.panels = list.toArray(new Panel[0]);
    this.builtTarget = t;
  }

  private void spawn(int i) {
    // pick a panel weighted by width so the cylinder gets its share
    int total = 0;
    for (Panel p : this.panels) total += p.w;
    int pick = (int) (Math.random() * total);
    int pi = 0;
    for (int k = 0; k < this.panels.length; ++k) {
      pick -= this.panels[k].w;
      if (pick < 0) { pi = k; break; }
    }
    final Panel p = this.panels[pi];
    final int n = this.rings.getValuei();
    final double baseR = ringRadius(p);
    // wider spacing relative to the small hoops stretches the tunnel tall
    final double space = baseR * (1.1 + Math.random() * 0.5);

    this.cpanel[i] = pi;
    // vertical tunnels: rings stack upward with a slight lateral lean
    this.cdir[i] = (Math.random() < 0.5) ? 1 : -1;
    this.cslope[i] = (Math.random() - 0.5) * 0.22; // lateral drift per step
    this.cspace[i] = space;
    this.cn[i] = n;
    if (p.wrap) {
      this.cx0[i] = Math.random() * p.w;
    } else {
      double margin = baseR + 1;
      this.cx0[i] = (p.w > 2 * margin)
        ? margin + Math.random() * (p.w - 2 * margin) : p.w * 0.5;
    }
    // base ring near the floor; the chain draws upward from here
    this.cy0[i] = p.h * (0.72 + Math.random() * 0.20);
    this.cage[i] = 0;
    this.calive[i] = true;
  }

  private double ringRadius(Panel p) {
    // small hoops so many tunnels fit per face
    return (0.035 + getSize() * 0.075) * Math.min(p.w, p.h) + 1;
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
      for (int i = 0; i < MAX_CHAINS; ++i) {
        this.calive[i] = false;
        this.cwait[i] = Math.random() * 800;
      }
      this.inited = true;
    }

    this.timeMs += deltaMs;
    final double speed = Math.max(0.02, getSpeed());
    final double wow = getWow();
    // lifecycle clock follows the music's tempo: faster track, faster tunnels
    final double tempoScale = REF_BEAT_MS / this.beatPeriodMs;
    final double dt = deltaMs * (0.4 + speed * 1.6) * (1.0 + wow * 0.6) * tempoScale;
    final int base = getColor();
    final int hoop = LXColor.lerp(base, LXColor.WHITE, 0.35f);
    final int hot = LXColor.lerp(base, LXColor.WHITE, 0.85f);
    final double onset = detect(deltaMs);
    final boolean quiet = this.levelEnv < 0.04;
    // the music sets the population: level raises the ceiling, Wow raises it more
    final int want = Math.min(MAX_CHAINS, this.chains.getValuei()
      + (int) Math.round(this.levelEnv * 14 + wow * 10));
    final double glintAmt = 0.35 + wow * 0.65;
    final double ga = this.timeMs * 0.0012; // glint sweep angle

    int active = 0;
    for (int i = 0; i < MAX_CHAINS; ++i) {
      if (this.calive[i]) ++active;
    }

    // bass onsets punch new tunnels open, harder hits open several at once
    if (onset >= 0) {
      int burst = 2 + (int) Math.round(onset * 5 + wow * 3);
      for (int i = 0; i < MAX_CHAINS && burst > 0 && active < want; ++i) {
        if (!this.calive[i]) {
          spawn(i);
          ++active;
          --burst;
        }
      }
    }

    for (int i = 0; i < MAX_CHAINS; ++i) {
      if (!this.calive[i]) {
        // in silence, a gentle timer keeps a few tunnels drawing
        if (quiet && active < this.chains.getValuei()) {
          this.cwait[i] -= dt;
          if (this.cwait[i] <= 0) {
            spawn(i);
            ++active;
          }
        }
        continue;
      }

      this.cage[i] += dt;
      final int n = this.cn[i];
      final double collapseStart = n * STAGGER_MS + GROW_MS + HOLD_MS;
      final double deathAt = collapseStart + n * STAGGER_MS + SHRINK_MS;
      if (this.cage[i] >= deathAt) {
        this.calive[i] = false;
        this.cwait[i] = 300 + Math.random() * 900;
        --active;
        continue;
      }

      final Panel p = this.panels[this.cpanel[i]];
      final double baseR = ringRadius(p);
      double cx = this.cx0[i];
      double cy = this.cy0[i];
      double r = baseR;
      double space = this.cspace[i];

      for (int k = 0; k < n; ++k) {
        final double tb = this.cage[i] - k * STAGGER_MS;
        double sc = 0;
        double flash = 0;
        if (tb > 0) {
          sc = smooth(tb / GROW_MS);
          flash = Math.exp(-tb / 140.0); // birth pop
          final double tc = this.cage[i] - collapseStart - k * STAGGER_MS;
          if (tc > 0) {
            final double c = 1.0 - tc / SHRINK_MS;
            if (c <= 0) {
              sc = 0;
            } else {
              sc *= smooth(c);
              flash = Math.max(flash, 0.7 * (1.0 - c)); // brighten as it collapses
            }
          }
        }
        if (sc > 0.02) {
          final double rr = r * sc;
          final int col = (flash > 0.25) ? hot : hoop;
          final double bright = LXUtils.clamp(0.85 + flash * 0.9, 0, 1.75);
          ring(p, cx, cy, rr, 1.15, bright, col, ga + k * 0.9, glintAmt);
          if (rr > 4.5) {
            // inner rim doubles the hoop, like an LED ring inside its frame
            ring(p, cx, cy, rr * 0.80, 0.9, bright * 0.45, hoop, ga + k * 0.9 + Math.PI, glintAmt * 0.6);
          }
        }
        // step up the tunnel: rings stack vertically with a slight lean
        cx += this.cslope[i] * space;
        cy -= space;
        r *= PERSP;
        space *= PERSP;
      }
    }

    copyExterior();
  }

  /** Draw a hoop outline with a rotating glint highlight. */
  private void ring(Panel p, double cx, double cy, double r, double th,
      double bright, int color, double glintAngle, double glintAmt) {
    final int y0 = (int) Math.max(0, Math.floor(cy - r - th));
    final int y1 = (int) Math.min(p.h - 1, Math.ceil(cy + r + th));
    final int x0 = (int) Math.floor(cx - r - th);
    final int x1 = (int) Math.ceil(cx + r + th);
    final double gc = Math.cos(glintAngle), gs = Math.sin(glintAngle);
    for (int yy = y0; yy <= y1; ++yy) {
      final double dy = yy - cy;
      for (int xx = x0; xx <= x1; ++xx) {
        final double dx = xx - cx;
        final double d = Math.sqrt(dx * dx + dy * dy);
        final double band = 1.0 - Math.abs(d - r) / th;
        if (band <= 0) continue;
        double f = Math.pow(band, 1.6) * bright;
        // specular glint sweeping around the hoop
        if (d > 1e-3) {
          final double ca = (dx * gc + dy * gs) / d;
          if (ca > 0) {
            f *= 1.0 + glintAmt * ca * ca * ca * ca;
          }
        }
        if (f <= 0.004) continue;
        int xi = xx;
        if (xi < 0 || xi >= p.w) {
          if (!p.wrap) continue;
          xi = ((xi % p.w) + p.w) % p.w;
        }
        final int idx = p.idx[xi][yy];
        this.colors[idx] = LXColor.lightest(this.colors[idx],
          LXColor.scaleBrightness(color, (float) LXUtils.clamp(f, 0, 1)));
      }
    }
  }
}
