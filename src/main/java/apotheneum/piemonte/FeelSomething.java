/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * FeelSomething — colored sticks rain down the surface, and when they strike the
 * floor they bounce back up, tumbling end over end and throwing off a burst of
 * bright sparkles. Each bounce loses a little energy until the stick settles and
 * a fresh one drops from the top.
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
public class FeelSomething extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int MAX_STICKS = 64;
  private static final int MAX_SPARKS = 1500;
  private static final double GRAV = 0.00010;      // cells / ms^2 (time-scaled by speed)
  private static final double SPARK_G = 0.00006;
  private static final double RESTITUTION = 0.68;  // bounce energy kept
  private static final double MIN_IMPACT = 0.010;  // below this a stick settles + relaunches
  private static final double HUE_SPREAD = 90;     // per-stick hue variety (degrees)

  public final DiscreteParameter sticks =
    new DiscreteParameter("Sticks", 24, 4, MAX_STICKS)
    .setDescription("How many sticks are falling at once");

  public final CompoundParameter sparkle =
    new CompoundParameter("Sparkle", 0.5, 0, 1)
    .setDescription("How many sparkles fly off on each bounce");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  private final class Surface {
    int w, h;
    boolean inited;
    // sticks
    final double[] kx = new double[MAX_STICKS];
    final double[] ky = new double[MAX_STICKS];
    final double[] kvy = new double[MAX_STICKS];
    final double[] klf = new double[MAX_STICKS];   // per-stick length factor
    final double[] kang = new double[MAX_STICKS];
    final double[] kav = new double[MAX_STICKS];    // angular velocity
    final double[] khue = new double[MAX_STICKS];   // 0..1 hue offset
    final boolean[] kbounced = new boolean[MAX_STICKS];
    // sparks
    final double[] spx = new double[MAX_SPARKS];
    final double[] spy = new double[MAX_SPARKS];
    final double[] spvx = new double[MAX_SPARKS];
    final double[] spvy = new double[MAX_SPARKS];
    final double[] splife = new double[MAX_SPARKS];
    final double[] spmax = new double[MAX_SPARKS];
    final boolean[] spAlive = new boolean[MAX_SPARKS];

    void ensure(int w, int h) {
      if (this.inited && this.w == w && this.h == h) {
        return;
      }
      this.w = w;
      this.h = h;
      this.inited = true;
      for (int i = 0; i < MAX_STICKS; ++i) {
        launch(i, true);
      }
      for (int i = 0; i < MAX_SPARKS; ++i) {
        this.spAlive[i] = false;
      }
    }

    void launch(int i, boolean scatter) {
      this.kx[i] = Math.random() * this.w;
      this.ky[i] = scatter ? Math.random() * this.h * 0.6 : -(Math.random() * this.h * 0.4) - 4;
      this.kvy[i] = 0;
      this.klf[i] = 0.7 + Math.random() * 0.6;
      this.kang[i] = 0;
      this.kav[i] = (Math.random() - 0.5) * 0.006; // gentle pre-bounce tumble
      this.khue[i] = Math.random();
      this.kbounced[i] = false;
    }

    /** Emit sparks from (x,y) flung outward along (ox,oy) — used for the stick's ends. */
    void sparkFrom(double x, double y, double ox, double oy, int count) {
      for (int c = 0; c < count; ++c) {
        int idx = -1;
        for (int k = 0; k < MAX_SPARKS; ++k) {
          if (!this.spAlive[k]) { idx = k; break; }
        }
        if (idx < 0) return;
        this.spx[idx] = x;
        this.spy[idx] = y;
        double eject = 0.02 + Math.random() * 0.045;
        this.spvx[idx] = ox * eject + (Math.random() - 0.5) * 0.03;
        this.spvy[idx] = oy * eject - (0.006 + Math.random() * 0.02); // slight upward arc
        this.spmax[idx] = 300 + Math.random() * 520;
        this.splife[idx] = this.spmax[idx];
        this.spAlive[idx] = true;
      }
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();

  public FeelSomething(LX lx) {
    // Base registers color (base hue), speed (time scale), size (stick length).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("sticks", this.sticks);
    addParameter("sparkle", this.sparkle);
    addParameter("target", this.target);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    final double baseHue = LXColor.h(getColor());
    final double dtS = Math.max(0.02, getSpeed()) * deltaMs; // time step
    final Target t = this.target.getEnum();
    if (t != Target.CYLINDER) {
      step(this.cube, Apotheneum.cube.exterior, dtS, baseHue);
    }
    if (t != Target.CUBE) {
      step(this.cylinder, Apotheneum.cylinder.exterior, dtS, baseHue);
    }
    copyExterior();
  }

  private void step(Surface s, Apotheneum.Orientation o, double dtS, double baseHue) {
    final int w = o.width();
    final int h = o.height();
    s.ensure(w, h);

    final int count = this.sticks.getValuei();
    final double len = 3 + getSize() * 10;
    final double bottom = h - 1;
    final double wow = getWow();
    // Wow turns each bounce into a burst storm: more sparks, more shedding.
    final double sparkAmt = this.sparkle.getValue();

    // --- sticks ---
    for (int i = 0; i < count; ++i) {
      s.kvy[i] += GRAV * dtS;
      s.ky[i] += s.kvy[i] * dtS;
      s.kang[i] += s.kav[i] * dtS;
      boolean justBounced = false;
      double impact = 0;
      if (s.ky[i] >= bottom) {
        s.ky[i] = bottom;
        impact = Math.abs(s.kvy[i]);
        if (impact < MIN_IMPACT && s.kbounced[i]) {
          s.launch(i, false); // settled -> drop a fresh one
        } else {
          s.kvy[i] = -impact * RESTITUTION;   // bounce up
          s.kbounced[i] = true;
          s.kav[i] = (Math.random() - 0.5) * 0.03; // tumble
          justBounced = true;
        }
      }

      final double dirx = Math.sin(s.kang[i]);
      final double diry = Math.cos(s.kang[i]);
      final double half = 0.5 * len * s.klf[i];
      final double ax = s.kx[i] - dirx * half, ay = s.ky[i] - diry * half;
      final double bx = s.kx[i] + dirx * half, by = s.ky[i] + diry * half;

      // sparkles fly out of the stick's two ends
      if (justBounced) {
        int n = LXUtils.constrain(
          (int) (impact * 1000 * (0.35 + sparkAmt) * (1.0 + wow * 4.0)), 2, (int) (32 + wow * 70));
        s.sparkFrom(ax, ay, -dirx, -diry, n);
        s.sparkFrom(bx, by, dirx, diry, n);
      } else if (s.kbounced[i]) {
        double rate = 0.20 + sparkAmt * 1.0 + wow * 0.6; // keep shedding off the ends while tumbling
        int shed = 1 + (int) (wow * 3);
        if (Math.random() < rate) s.sparkFrom(ax, ay, -dirx, -diry, shed);
        if (Math.random() < rate) s.sparkFrom(bx, by, dirx, diry, shed);
      }

      // draw the stick between its two ends, with white-hot tips
      final int col = LXColor.hsb(
        (float) (((baseHue + (s.khue[i] - 0.5) * HUE_SPREAD) % 360 + 360) % 360), 90, 100);
      final int hot = LXColor.lerp(col, LXColor.WHITE, 0.5f);
      final int steps = (int) Math.ceil(len);
      for (int k = 0; k <= steps; ++k) {
        double f = (steps == 0) ? 0 : (double) k / steps;
        int col2 = (f < 0.15 || f > 0.85) ? hot : col;
        final double px = ax + (bx - ax) * f;
        final double py = ay + (by - ay) * f;
        splat(o, w, h, px, py, 2.3, 0.35, col);   // soft aura around the rod
        splat(o, w, h, px, py, 1.4, 1.0, col2);   // bold core
      }
    }

    // --- sparkles: born white-hot, cooling toward the base hue as they fade ---
    final int ember = LXColor.hsb((float) baseHue, 90, 100);
    for (int i = 0; i < MAX_SPARKS; ++i) {
      if (!s.spAlive[i]) continue;
      s.splife[i] -= dtS;
      if (s.splife[i] <= 0) { s.spAlive[i] = false; continue; }
      s.spvy[i] += SPARK_G * dtS;
      s.spx[i] += s.spvx[i] * dtS;
      s.spy[i] += s.spvy[i] * dtS;
      if (s.spy[i] >= bottom && s.spvy[i] > 0) { s.spAlive[i] = false; continue; }
      double fade = s.splife[i] / s.spmax[i];
      double twinkle = 0.55 + 0.45 * Math.random(); // shimmer
      int sc = LXColor.lerp(ember, LXColor.WHITE, (float) fade);
      splat(o, w, h, s.spx[i], s.spy[i], 1.05, Math.pow(fade, 1.5) * (0.55 + 0.65 * twinkle), sc);
    }
  }

  private void splat(Apotheneum.Orientation o, int w, int h, double x, double y,
      double r, double bright, int color) {
    int y0 = (int) Math.max(0, Math.floor(y - r));
    int y1 = (int) Math.min(h - 1, Math.ceil(y + r));
    int x0 = (int) Math.floor(x - r);
    int x1 = (int) Math.ceil(x + r);
    for (int yy = y0; yy <= y1; ++yy) {
      double dy = yy - y;
      for (int xx = x0; xx <= x1; ++xx) {
        double dx = xx - x;
        double dd = Math.sqrt(dx * dx + dy * dy);
        if (dd > r) continue;
        double u = 1.0 - dd / r;
        double f = u * u * bright;
        if (f <= 0) continue;
        int xi = ((xx % w) + w) % w;
        int idx = o.point(xi, yy).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx], LXColor.scaleBrightness(color, (float) f));
      }
    }
  }
}
