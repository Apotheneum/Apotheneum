/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * SpecialKube — a rain of spinning wireframe 3D cubes falling downward, each
 * confined to a single flat panel of the sculpture (a cube face, or a cylinder
 * quadrant) so the wireframes stay crisp and don't bend around the corners. The
 * lines are thick, and a fraction of the cubes are "special" — they run a heavy
 * glitch: flickering, jittering, flashing white and shifting toward cyan,
 * echoing the LXStudio-TE SpecialKube shader's glitch strobe.
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

@LXCategory("Apotheneum/piemonte")
public class SpecialKube extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int MAX_CUBES = 48;
  private static final int CYAN = LXColor.rgb(0, 255, 255);
  private static final int STROBE_COLOR = LXColor.lerp(LXColor.WHITE, CYAN, 0.30f);
  private static final double FALL = 0.00016;
  private static final double SPIN_RATE = 0.0011;
  private static final double SIZE_CELLS = 6.0;
  private static final int[][] EDGES = {
    {0,1},{0,2},{1,3},{2,3}, {4,5},{4,6},{5,7},{6,7}, {0,4},{1,5},{2,6},{3,7}
  };

  public final DiscreteParameter quantity =
    new DiscreteParameter("Quantity", 14, 1, MAX_CUBES)
    .setDescription("Number of cubes in flight");

  public final CompoundParameter spin =
    new CompoundParameter("Spin", 0.5, 0, 2)
    .setDescription("Rotation speed");

  public final CompoundParameter special =
    new CompoundParameter("Special", 0.35, 0, 1)
    .setDescription("Fraction of cubes that glitch");

  public final CompoundParameter thick =
    new CompoundParameter("Thick", 0.5, 0, 1)
    .setDescription("Wireframe line thickness");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  private static final class Panel {
    final int[][] idx;
    final int w, h;
    Panel(int[][] idx, int w, int h) { this.idx = idx; this.w = w; this.h = h; }
  }

  private Panel[] panels;
  private Target builtTarget;

  private final int[] panel = new int[MAX_CUBES];
  private final double[] nx = new double[MAX_CUBES];
  private final double[] ny = new double[MAX_CUBES];
  private final double[] sz = new double[MAX_CUBES];
  private final double[] ax = new double[MAX_CUBES];
  private final double[] ay = new double[MAX_CUBES];
  private final double[] az = new double[MAX_CUBES];
  private final double[] rax = new double[MAX_CUBES];
  private final double[] ray = new double[MAX_CUBES];
  private final double[] raz = new double[MAX_CUBES];
  private final double[] seed = new double[MAX_CUBES];
  private boolean inited = false;
  private double timeMs = 0;

  private final double[] sx = new double[8];
  private final double[] sy = new double[8];
  private final double[] sp = new double[8];

  public SpecialKube(LX lx) {
    super(lx, 0.4, 0, 2, 0.5, 0, 1);
    addParameter("quantity", this.quantity);
    addParameter("spin", this.spin);
    addParameter("special", this.special);
    addParameter("thick", this.thick);
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
        list.add(new Panel(g, w, h));
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
        list.add(new Panel(g, q, h));
      }
    }
    this.panels = list.toArray(new Panel[0]);
    this.builtTarget = t;
  }

  private void respawn(int i, boolean atTop) {
    this.panel[i] = (int) (Math.random() * this.panels.length);
    this.nx[i] = Math.random();
    this.ny[i] = atTop ? -0.25 : Math.random() * 1.2 - 0.1;
    this.sz[i] = 0.6 + 0.8 * Math.random();
    this.ax[i] = Math.random() * Math.PI * 2;
    this.ay[i] = Math.random() * Math.PI * 2;
    this.az[i] = Math.random() * Math.PI * 2;
    this.rax[i] = (0.3 + Math.random()) * (Math.random() < 0.5 ? -1 : 1);
    this.ray[i] = (0.3 + Math.random()) * (Math.random() < 0.5 ? -1 : 1);
    this.raz[i] = (0.3 + Math.random()) * (Math.random() < 0.5 ? -1 : 1);
    this.seed[i] = Math.random();
  }

  private static double frac(double x) {
    return x - Math.floor(x);
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
      for (int i = 0; i < MAX_CUBES; ++i) {
        respawn(i, false);
      }
      this.inited = true;
    }

    this.timeMs += deltaMs;
    final double speed = getSpeed();
    final double dSpin = this.spin.getValue() * SPIN_RATE * deltaMs;
    final int count = this.quantity.getValuei();
    final double fall = speed * FALL * deltaMs;
    final int base = getColor();
    final double sizeBase = SIZE_CELLS * (0.4 + getSize() * 1.6);
    final double wow = getWow();
    // Wow drags more cubes into the glitch and pushes it harder.
    final double specialFrac = this.special.getValue() + wow * (1.0 - this.special.getValue());
    final double lineR = 0.8 + this.thick.getValue() * 2.0; // thicker wireframe
    final double gt = this.timeMs * (0.02 + wow * 0.04);
    // crisp global strobe: all special cubes flash together on a short duty cycle
    final double strobeHz = 6.0 + speed * 2.0 + wow * 2.0;      // ~6..12 flashes/s
    final boolean strobeOn = frac(this.timeMs * 0.001 * strobeHz) < (0.12 + wow * 0.10);

    for (int i = 0; i < count; ++i) {
      this.ny[i] += fall;
      if (this.ny[i] > 1.25) {
        respawn(i, true);
      }
      this.ax[i] += this.rax[i] * dSpin;
      this.ay[i] += this.ray[i] * dSpin;
      this.az[i] += this.raz[i] * dSpin;

      final Panel p = this.panels[this.panel[i]];
      final double s = sizeBase * this.sz[i];
      double cx = this.nx[i] * p.w;
      double cy = this.ny[i] * p.h;

      int col = base;
      double bright = 1.0;
      if (this.seed[i] < specialFrac) {
        // heavy glitch "wow" texture between flashes
        double sd = this.seed[i];
        double glitch = (frac(gt + sd * 10) > 0.5 && frac(gt * 3.7 + sd * 5) > 0.7) ? 1 : 0;
        bright = 1.6 + glitch * 2.4;
        col = LXColor.lerp(base, CYAN, (float) (glitch * 0.5));
        if (glitch > 0) {
          double j = 1.0 + wow * 2.0;
          cx += (Math.random() - 0.5) * 4 * j; // digital jitter
          cy += (Math.random() - 0.5) * 3 * j;
        }
        // crisp rhythmic strobe: all special cubes flash cyan-white in sync + shudder
        if (strobeOn) {
          col = STROBE_COLOR;
          bright = 3.5 * (1.0 + wow * 1.5);
          double sj = 3.0 * (1.0 + wow * 2.0);
          cx += (Math.random() - 0.5) * sj;
          cy += (Math.random() - 0.5) * sj;
        }
      }

      projectCube(i, s, cx, cy);
      for (int[] e : EDGES) {
        // depth cue: nearer edges (larger perspective factor) render brighter
        double eb = bright * (0.55 + 0.45 * 0.5 * (this.sp[e[0]] + this.sp[e[1]]));
        edge(p, this.sx[e[0]], this.sy[e[0]], this.sx[e[1]], this.sy[e[1]], col, eb, lineR);
      }
    }

    copyExterior();
  }

  private void projectCube(int i, double s, double cx, double cy) {
    final double cax = Math.cos(this.ax[i]), san = Math.sin(this.ax[i]);
    final double cay = Math.cos(this.ay[i]), say = Math.sin(this.ay[i]);
    final double caz = Math.cos(this.az[i]), saz = Math.sin(this.az[i]);
    final double focal = 2.2 * s;
    for (int v = 0; v < 8; ++v) {
      double x = ((v & 1) != 0) ? s : -s;
      double y = ((v & 2) != 0) ? s : -s;
      double z = ((v & 4) != 0) ? s : -s;
      double y1 = y * cax - z * san, z1 = y * san + z * cax;
      double x2 = x * cay + z1 * say, z2 = -x * say + z1 * cay;
      double x3 = x2 * caz - y1 * saz, y3 = x2 * saz + y1 * caz;
      double persp = focal / (focal + z2);
      this.sx[v] = cx + x3 * persp;
      this.sy[v] = cy + y3 * persp;
      this.sp[v] = persp;
    }
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
        double f = Math.pow(u, 1.6) * bright;
        if (f <= 0) continue;
        int idx = p.idx[xx][yy];
        this.colors[idx] = LXColor.lightest(this.colors[idx], LXColor.scaleBrightness(color, (float) f));
      }
    }
  }
}
