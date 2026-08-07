/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Bruh — a heavy downpour of vertical streaks falling through the piece like rain
 * in a bamboo forest. Where each drop strikes the floor, water splashes upward in
 * a small arc and falls back. Dense, continuous, elemental.
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
public class Bruh extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int MAX_DROPS = 160;
  private static final int MAX_SPLASH = 600;
  private static final double FALL = 0.045;      // cells / ms at speed 1
  private static final double GRAVITY = 0.00018; // splash gravity, cells / ms^2

  public final DiscreteParameter drops =
    new DiscreteParameter("Drops", 60, 10, MAX_DROPS)
    .setDescription("How many raindrops are falling at once");

  public final CompoundParameter splash =
    new CompoundParameter("Splash", 0.5, 0, 1)
    .setDescription("How much water splashes up on impact");

  public final CompoundParameter trail =
    new CompoundParameter("Trail", 0.5, 0, 1)
    .setDescription("How long the rain streaks trail behind each drop");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  private final class Surface {
    int w, h;
    boolean inited;
    // drops
    final double[] dx = new double[MAX_DROPS];
    final double[] dy = new double[MAX_DROPS];
    final double[] dvy = new double[MAX_DROPS]; // per-drop speed factor
    final double[] dlen = new double[MAX_DROPS];
    // splash particles
    final double[] sx = new double[MAX_SPLASH];
    final double[] sy = new double[MAX_SPLASH];
    final double[] svx = new double[MAX_SPLASH];
    final double[] svy = new double[MAX_SPLASH];
    final double[] slife = new double[MAX_SPLASH];
    final double[] smax = new double[MAX_SPLASH];
    final boolean[] salive = new boolean[MAX_SPLASH];

    void ensure(int w, int h) {
      if (this.inited && this.w == w && this.h == h) {
        return;
      }
      this.w = w;
      this.h = h;
      this.inited = true;
      for (int i = 0; i < MAX_DROPS; ++i) {
        respawnDrop(i, true);
      }
      for (int i = 0; i < MAX_SPLASH; ++i) {
        this.salive[i] = false;
      }
    }

    void respawnDrop(int i, boolean scatter) {
      this.dx[i] = Math.random() * this.w;
      this.dy[i] = scatter ? Math.random() * this.h : -(Math.random() * 12);
      this.dvy[i] = 0.7 + Math.random() * 0.6;
      this.dlen[i] = 1; // set at render from Size
    }

    void spawnSplash(double x, int count, double energy) {
      for (int c = 0; c < count; ++c) {
        int idx = -1;
        for (int k = 0; k < MAX_SPLASH; ++k) {
          if (!this.salive[k]) { idx = k; break; }
        }
        if (idx < 0) return;
        this.sx[idx] = x;
        this.sy[idx] = this.h - 1;
        this.svx[idx] = (Math.random() - 0.5) * 0.03;
        this.svy[idx] = -(0.015 + Math.random() * 0.03) * (0.5 + energy); // upward
        this.smax[idx] = 350 + Math.random() * 450;
        this.slife[idx] = this.smax[idx];
        this.salive[idx] = true;
      }
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();

  public Bruh(LX lx) {
    // Base registers color, speed, size; speed = fall speed, size = streak/splash size.
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("drops", this.drops);
    addParameter("splash", this.splash);
    addParameter("trail", this.trail);
    addParameter("target", this.target);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    final int base = getColor();
    final Target t = this.target.getEnum();
    if (t != Target.CYLINDER) {
      step(this.cube, Apotheneum.cube.exterior, deltaMs, base);
    }
    if (t != Target.CUBE) {
      step(this.cylinder, Apotheneum.cylinder.exterior, deltaMs, base);
    }
    copyExterior();
  }

  private void step(Surface s, Apotheneum.Orientation o, double deltaMs, int base) {
    final int w = o.width();
    final int h = o.height();
    s.ensure(w, h);

    final double speed = Math.max(0.02, getSpeed());
    final double size = getSize();
    final double wow = getWow();
    // Wow = downpour surge: more drops falling faster, eruptive splashes
    final int count = Math.min(MAX_DROPS, (int) (this.drops.getValuei() * (1 + wow * 1.5)));
    final double fall = FALL * speed * deltaMs * (1 + wow * 0.8);
    // Trail dial: 0.5 == the original streak length, up to ~3.3x at full
    final double streak = (2 + size * 9) * (0.7 + this.trail.getValue() * 2.6);
    // Wow makes impacts erupt: many more droplets, more upward energy, whiter foam.
    final double splashAmt = this.splash.getValue();
    final double splashEnergy = splashAmt + wow;
    final int splashCount = (int) (1 + splashAmt * 14 + wow * 14);
    final int splashColor = LXColor.lerp(base, LXColor.WHITE, (float) LXUtils.clamp(0.6 + wow * 0.4, 0, 1));
    final int headColor = LXColor.lerp(base, LXColor.WHITE, 0.5f); // hot bead at the drop head

    // --- drops ---
    for (int i = 0; i < count; ++i) {
      s.dy[i] += fall * s.dvy[i];
      if (s.dy[i] >= h - 1) {
        if (splashEnergy > 0) {
          s.spawnSplash(s.dx[i], splashCount, splashEnergy);
        }
        s.respawnDrop(i, false);
      }
      // render vertical streak: head at dy, trail above, fading up
      int xi = (((int) Math.round(s.dx[i])) % w + w) % w;
      int head = (int) s.dy[i];
      double hf = s.dy[i] - Math.floor(s.dy[i]); // sub-pixel head fraction
      for (int k = 0; k <= (int) streak; ++k) {
        int row = head - k;
        if (row < 0 || row >= h) continue;
        double b = Math.exp(-2.2 * k / (streak + 1)); // reads along the full trail
        int c = base;
        if (k == 0) {
          b *= (0.4 + 0.6 * hf);
          c = headColor;
        }
        int idx = o.point(xi, row).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx], LXColor.scaleBrightness(c, (float) b));
      }
      // smooth falling bead: light the row below the head at the leftover fraction
      int below = head + 1;
      if (below >= 0 && below < h) {
        int idx = o.point(xi, below).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx], LXColor.scaleBrightness(base, (float) (hf * 0.5)));
      }
    }

    // --- splashes ---
    final double r = 0.9 + size * 0.9 + splashAmt * 1.3 + wow * 1.5;
    for (int i = 0; i < MAX_SPLASH; ++i) {
      if (!s.salive[i]) continue;
      s.slife[i] -= deltaMs;
      if (s.slife[i] <= 0) { s.salive[i] = false; continue; }
      s.svy[i] += GRAVITY * deltaMs;          // gravity pulls back down
      s.sy[i] += s.svy[i] * speed * deltaMs;
      s.sx[i] += s.svx[i] * speed * deltaMs;
      if (s.sy[i] >= h - 1 && s.svy[i] > 0) { s.salive[i] = false; continue; } // fell back
      double fade = s.slife[i] / s.smax[i];
      splat(o, w, h, s.sx[i], s.sy[i], r, Math.pow(fade, 1.5) * (0.55 + 0.65 * splashAmt + wow * 0.4), splashColor);
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
