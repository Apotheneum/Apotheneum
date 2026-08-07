/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * TheHumanRace — a vast landscape scattered with little glowing stick figures,
 * gathered in loose bunches and stretching to a horizon. A slow camera drifts
 * and pans across them, and because each face of the sculpture looks out in its
 * own direction the crowd wraps around you in a 360 degree panorama. Most figures
 * glow pale blue-white; a few are vivid specks. They sway as if walking.
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
import heronarts.lx.utils.Noise;

@LXCategory("Apotheneum/piemonte")
public class TheHumanRace extends ParameterPattern {

  private static final int NUM = 2600;         // figure pool
  private static final int CLUSTERS = 44;
  private static final double AREA = 70;       // half-size of the field (world units)
  private static final double PERIOD = 2 * AREA; // toroidal wrap so the camera glides forever
  private static final double NEAR = 1.0;
  private static final double H_EYE = 2.6;     // camera height above the ground
  private static final double GLIDE = 0.005;   // world units / ms at speed 1
  private static final double FAR = 46;        // distance where the crowd fogs out
  private static final double TERR_FREQ = 0.055; // terrain feature scale
  private static final double TERR_MAXH = 4.5;   // max hill/mountain height (world units)
  private static final float PALE_SAT = 22;

  public final DiscreteParameter density =
    new DiscreteParameter("Density", 2200, 200, NUM)
    .setDescription("How many figures are drawn");

  public final CompoundParameter terrain =
    new CompoundParameter("Terrain", 0.6, 0, 1)
    .setDescription("Height of the hills and mountains");

  public final CompoundParameter specks =
    new CompoundParameter("Specks", 0.15, 0, 1)
    .setDescription("Fraction of figures that are vivid colored specks");

  public final CompoundParameter horizon =
    new CompoundParameter("Horizon", 0.14, 0.02, 0.45)
    .setDescription("Where the horizon sits (lower = more landscape, less sky)");

  private static final class Panel {
    final int[][] idx;
    final int w, h;
    final double facing; // world yaw this panel looks toward
    Panel(int[][] idx, int w, int h, double facing) {
      this.idx = idx; this.w = w; this.h = h; this.facing = facing;
    }
  }

  private Panel[] panels;
  private Target builtTarget;

  // crowd (built once)
  private final double[] fx = new double[NUM];
  private final double[] fz = new double[NUM];
  private final double[] speckHue = new double[NUM];
  private final double[] speckRand = new double[NUM];
  private final double[] wphase = new double[NUM];
  private final double[] wspeed = new double[NUM];
  private final double[] fh = new double[NUM]; // terrain height under each figure
  private boolean crowdBuilt = false;

  private double camX = 0, camZ = 0, timeMs = 0;

  public TheHumanRace(LX lx) {
    // Base registers color (crowd tint), speed (camera + walk), size (figure size).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("density", this.density);
    addParameter("terrain", this.terrain);
    addParameter("specks", this.specks);
    addParameter("horizon", this.horizon);
    addTargetParameter();
  }

  private void buildCrowd() {
    double[] cxs = new double[CLUSTERS];
    double[] czs = new double[CLUSTERS];
    for (int c = 0; c < CLUSTERS; ++c) {
      cxs[c] = (Math.random() * 2 - 1) * AREA;
      czs[c] = (Math.random() * 2 - 1) * AREA;
    }
    for (int i = 0; i < NUM; ++i) {
      if (Math.random() < 0.8) {
        int c = (int) (Math.random() * CLUSTERS);
        double sigma = 4 + Math.random() * 8;
        this.fx[i] = cxs[c] + (Math.random() - Math.random()) * sigma;
        this.fz[i] = czs[c] + (Math.random() - Math.random()) * sigma;
      } else {
        this.fx[i] = (Math.random() * 2 - 1) * AREA;
        this.fz[i] = (Math.random() * 2 - 1) * AREA;
      }
      this.fh[i] = Noise.stb_perlin_fbm_noise3(
        (float) (this.fx[i] * TERR_FREQ), (float) (this.fz[i] * TERR_FREQ), 0f, 2f, 0.5f, 4);
      this.speckHue[i] = Math.random();
      this.speckRand[i] = Math.random();
      this.wphase[i] = Math.random() * Math.PI * 2;
      this.wspeed[i] = 0.002 + Math.random() * 0.004;
    }
    this.crowdBuilt = true;
  }

  private void buildPanels(Target t) {
    java.util.List<Panel> list = new java.util.ArrayList<>();
    if (t != Target.CYLINDER) {
      Apotheneum.Cube.Face[] faces = Apotheneum.cube.exterior.faces;
      for (int f = 0; f < faces.length; ++f) {
        int w = faces[f].columns.length;
        int h = faces[f].columns[0].points.length;
        int[][] g = new int[w][h];
        for (int x = 0; x < w; ++x) {
          for (int y = 0; y < h; ++y) {
            g[x][y] = faces[f].columns[x].points[y].index;
          }
        }
        list.add(new Panel(g, w, h, f * Math.PI / 2));
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
        list.add(new Panel(g, q, h, seg * Math.PI / 2));
      }
    }
    this.panels = list.toArray(new Panel[0]);
    this.builtTarget = t;
  }

  private static double wrap(double d) {
    return d - PERIOD * Math.rint(d / PERIOD);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    if (!this.crowdBuilt) {
      buildCrowd();
    }
    final Target t = getTarget();
    if (this.panels == null || this.builtTarget != t) {
      buildPanels(t);
    }
    if (this.panels.length == 0) {
      return;
    }

    final double speed = Math.max(0.02, getSpeed());
    // Wow = festival: the crowd dances harder, more vivid figures light up,
    // and the camera sweeps faster through them
    final double wow = getWow();
    final double dtS = speed * deltaMs * (1 + wow * 0.6);
    this.timeMs += deltaMs;
    this.camZ += GLIDE * dtS * (1 + wow);           // glide forward
    this.camX = 18 * Math.sin(this.timeMs * 0.00005 * (1 + wow)); // side drift
    final double baseYaw = 0.35 * Math.sin(this.timeMs * 0.00008); // gentle pan

    // advance the walk cycle
    final int count = this.density.getValuei();
    for (int i = 0; i < count; ++i) {
      this.wphase[i] += this.wspeed[i] * dtS;
    }

    final double baseHue = LXColor.h(getColor());
    final double specksFrac = Math.min(1, this.specks.getValue() * (1 + wow * 3));
    final double sizeF = 0.5 + getSize() * 1.5;
    final double terrScale = this.terrain.getValue() * TERR_MAXH;

    for (Panel p : this.panels) {
      final double yaw = p.facing + baseYaw;
      final double cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
      final double horizon = p.h * this.horizon.getValue();
      final double focV = p.h * 0.6;
      // dim cool glow line along the horizon (figures draw over it)
      int hy = (int) horizon;
      for (int gy = Math.max(0, hy - 2); gy <= Math.min(p.h - 1, hy + 2); ++gy) {
        double gg = Math.exp(-((gy - horizon) * (gy - horizon)) / (2 * 1.2 * 1.2));
        int gc = LXColor.hsb(225, 30, (float) (16 * gg));
        for (int gx = 0; gx < p.w; ++gx) {
          this.colors[p.idx[gx][gy]] = LXColor.lightest(this.colors[p.idx[gx][gy]], gc);
        }
      }
      for (int i = 0; i < count; ++i) {
        double dx = wrap(this.fx[i] - this.camX);
        double dz = wrap(this.fz[i] - this.camZ);
        double fwd = dx * syaw + dz * cyaw;
        if (fwd <= NEAR || fwd > FAR) continue; // fog cull the far crowd
        double rightRatio = (dx * cyaw - dz * syaw) / fwd;
        if (rightRatio < -1.05 || rightRatio > 1.05) continue;

        double px = (0.5 + 0.5 * rightRatio) * p.w;
        // figures stand on terrain: hills lift them toward/over the horizon, valleys drop them
        double pyBase = horizon + ((H_EYE - this.fh[i] * terrScale) / fwd) * focV;
        double figH = (1.7 / fwd) * focV * sizeF;
        if (pyBase - figH > p.h - 1 || pyBase < -figH) continue; // off panel
        // distance fog: near figures full, fading out toward FAR (no bright horizon bunch)
        double fogT = LXUtils.clamp((FAR - fwd) / (FAR - NEAR) * 1.3, 0, 1);
        double bright = Math.pow(fogT, 1.8) * (0.75 + 0.5 * this.speckRand[i]);

        boolean speck = this.speckRand[i] < specksFrac;
        int color;
        if (speck) {
          color = LXColor.hsb((float) (this.speckHue[i] * 360), 85, 100);
        } else {
          color = LXColor.hsb((float) baseHue, PALE_SAT, 100);
          // cool-grade the distant crowd toward a hazy blue
          double far = 1.0 - fogT;
          color = LXColor.lerp(color, LXColor.hsb(225, 40, 70), (float) (0.6 * far));
        }

        drawFigure(p, px, pyBase, figH, bright, color, this.wphase[i]);
      }
    }

    copyExterior();
  }

  private void drawFigure(Panel p, double px, double py, double h,
      double bright, int color, double wphase) {
    if (h < 2.5) {
      splat(p, px, py - 0.4, 0.55 + h * 0.22, bright, color);
      return;
    }
    final double r = Math.max(0.55, h * 0.05);
    final double headR = h * 0.16;
    final double headCy = py - h * 0.86;
    final double hipY = py - h * 0.40;
    final double shoulderY = headCy + headR + h * 0.06;
    final double sway = Math.sin(wphase) * h * (0.13 + getWow() * 0.14); // dancing at high Wow

    splat(p, px, headCy, headR, bright, color);                       // head
    line(p, px, headCy + headR, px, hipY, r, bright, color);          // torso
    line(p, px, hipY, px - h * 0.14 + sway, py, r, bright, color);    // left leg
    line(p, px, hipY, px + h * 0.14 - sway, py, r, bright, color);    // right leg
    line(p, px, shoulderY, px - h * 0.20 - sway, shoulderY + h * 0.16, r, bright, color); // left arm
    line(p, px, shoulderY, px + h * 0.20 + sway, shoulderY + h * 0.16, r, bright, color); // right arm
  }

  private void line(Panel p, double x0, double y0, double x1, double y1,
      double r, double bright, int color) {
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
        double f = (1.0 - dd / r) * bright;
        if (f <= 0) continue;
        int idx = p.idx[xx][yy];
        this.colors[idx] = LXColor.lightest(this.colors[idx], LXColor.scaleBrightness(color, (float) f));
      }
    }
  }
}
