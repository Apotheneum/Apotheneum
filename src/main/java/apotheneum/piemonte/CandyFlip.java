/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * CandyFlip — rainbow comets roam the surface; each lives briefly then bursts
 * into a fresh generation of comets flying outward, cascading until the energy
 * runs out. White-hot heads trail fading rainbow gradients that shift hue with
 * each generation. An Apotheneum take on a vertex-to-vertex cascade.
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
public class CandyFlip extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int MAX_P = 256;
  private static final double BASE_VEL = 0.03;   // grid cells / ms at speed 1
  private static final double BURST_CYCLE = 2400; // ms (speed-scaled) between gen-0 bursts
  private static final double BASE_AGE = 1100;    // ms (speed-scaled) particle life
  private static final int FAN = 3;               // children per burst
  private static final double GEN_HUE = 95;       // hue shift per generation
  private static final double TRAIL_HUE = 26;     // hue shift along the trail (rainbow streak)
  private static final double HUE_DRIFT = 0.03;   // hue drift per ms

  public final DiscreteParameter density =
    new DiscreteParameter("Density", 5, 1, 16)
    .setDescription("Gen-0 comets spawned per burst");

  public final DiscreteParameter generations =
    new DiscreteParameter("Gens", 4, 1, 6)
    .setDescription("How many generations the cascade goes");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  /** A 2D cascade field on one orientation (wraps in X, bounces in Y). */
  private final class Surface {
    int w, h;
    boolean inited;
    final double[] px = new double[MAX_P];
    final double[] py = new double[MAX_P];
    final double[] vx = new double[MAX_P];
    final double[] vy = new double[MAX_P];
    final int[] gen = new int[MAX_P];
    final double[] age = new double[MAX_P];
    final double[] maxAge = new double[MAX_P];
    final boolean[] alive = new boolean[MAX_P];
    double spawnAcc;

    void ensure(int w, int h) {
      if (this.inited && this.w == w && this.h == h) {
        return;
      }
      this.w = w;
      this.h = h;
      this.inited = true;
      this.spawnAcc = 0;
      for (int i = 0; i < MAX_P; ++i) {
        this.alive[i] = false;
      }
    }

    int freeSlot() {
      for (int i = 0; i < MAX_P; ++i) {
        if (!this.alive[i]) {
          return i;
        }
      }
      return -1;
    }

    void spawn(double x, double y, int generation) {
      int i = freeSlot();
      if (i < 0) {
        return;
      }
      this.px[i] = x;
      this.py[i] = y;
      double theta = Math.random() * Math.PI * 2;
      double mag = 0.6 + 0.7 * Math.random();
      this.vx[i] = Math.cos(theta) * mag;
      this.vy[i] = Math.sin(theta) * mag;
      this.gen[i] = generation;
      // negative age staggers each particle's start so bursts don't pop in lockstep
      this.age[i] = -Math.random() * 200;
      this.maxAge[i] = BASE_AGE * (0.7 + 0.6 * Math.random());
      this.alive[i] = true;
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double timeMs = 0;

  public CandyFlip(LX lx) {
    // Base registers color (base hue), speed, size; speed is a magnitude here.
    super(lx, 0.4, 0, 1, 0.5, 0, 1);
    addParameter("density", this.density);
    addParameter("generations", this.generations);
    addParameter("target", this.target);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.timeMs += deltaMs;

    final Target t = this.target.getEnum();
    final double baseHue = LXColor.h(getColor());
    if (t != Target.CYLINDER) {
      step(this.cube, Apotheneum.cube.exterior, deltaMs, baseHue);
    }
    if (t != Target.CUBE) {
      step(this.cylinder, Apotheneum.cylinder.exterior, deltaMs, baseHue);
    }
    copyExterior();
  }

  private void step(Surface s, Apotheneum.Orientation o, double deltaMs, double baseHue) {
    final int w = o.width();
    final int h = o.height();
    s.ensure(w, h);

    final double speed = Math.max(0.02, getSpeed());
    final double size = getSize();
    final double wow = getWow();
    final double move = speed * BASE_VEL * deltaMs;
    final int maxGen = this.generations.getValuei();
    // Wow makes the cascade explosive: more children per burst, hotter heads.
    final int fan = FAN + (int) Math.round(wow * 5);

    // --- seed gen-0 bursts on a timer ---
    s.spawnAcc += deltaMs * speed;
    if (s.spawnAcc >= BURST_CYCLE) {
      s.spawnAcc -= BURST_CYCLE;
      int n = this.density.getValuei();
      for (int k = 0; k < n; ++k) {
        s.spawn(Math.random() * w, Math.random() * h, 0);
      }
    }

    // --- update: move, age, burst into next generation ---
    for (int i = 0; i < MAX_P; ++i) {
      if (!s.alive[i]) continue;
      double x = s.px[i] + s.vx[i] * move;
      double y = s.py[i] + s.vy[i] * move;
      x = ((x % w) + w) % w;
      if (y < 0) { y = -y; s.vy[i] = -s.vy[i]; }
      else if (y > h - 1) { y = 2 * (h - 1) - y; s.vy[i] = -s.vy[i]; }
      s.px[i] = x;
      s.py[i] = LXUtils.clamp(y, 0, h - 1);

      s.age[i] += deltaMs * speed;
      if (s.age[i] >= s.maxAge[i]) {
        if (s.gen[i] + 1 < maxGen) {
          for (int c = 0; c < fan; ++c) {
            s.spawn(s.px[i], s.py[i], s.gen[i] + 1);
          }
        }
        s.alive[i] = false;
      }
    }

    // --- render: white-hot heads + fading rainbow trails ---
    final double headR = (0.8 + size * 1.5) * (1.0 + wow * 1.2); // wow -> bigger, hotter heads
    final int tailCells = (int) (16 + size * 90);
    for (int i = 0; i < MAX_P; ++i) {
      if (!s.alive[i]) continue;
      // fade out over the particle's life so bursts feel like they spend energy,
      // with a brief birth pop that flares then settles
      double lifeFade = Math.min(1.6,
        (1.0 - 0.6 * (s.age[i] / s.maxAge[i])) * (1.0 + 0.8 * Math.exp(-s.age[i] / 120.0)));
      splat(s, o, s.px[i], s.py[i], headR, LXUtils.clamp(lifeFade, 0, 1), LXColor.WHITE);

      double vlen = Math.sqrt(s.vx[i] * s.vx[i] + s.vy[i] * s.vy[i]);
      if (vlen < 1e-4) continue;
      double ux = s.vx[i] / vlen, uy = s.vy[i] / vlen;
      for (int k = 1; k <= tailCells; ++k) {
        double f = Math.exp(-2.5 * k / tailCells) * lifeFade;
        if (f <= 0) break;
        double hue = baseHue + s.gen[i] * GEN_HUE + k * TRAIL_HUE + this.timeMs * HUE_DRIFT;
        hue = ((hue % 360) + 360) % 360;
        // trail emerges white from the head and blooms into color
        float sat = (float) (100.0 * Math.min(1.0, k / (0.25 * tailCells)));
        int col = LXColor.hsb((float) hue, sat, 100);
        splat(s, o, s.px[i] - ux * k * 0.9, s.py[i] - uy * k * 0.9, 0.9, LXUtils.clamp(f, 0, 1), col);
      }
    }
  }

  /** Draw a soft round dab at (x,y); x wraps, y is clamped to the surface. */
  private void splat(Surface s, Apotheneum.Orientation o, double x, double y,
      double r, double bright, int color) {
    final int w = s.w, h = s.h;
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
        double f = (1.0 - dd / r) * bright;
        if (f <= 0) continue;
        int xi = ((xx % w) + w) % w;
        int idx = o.point(xi, yy).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx], LXColor.scaleBrightness(color, (float) f));
      }
    }
  }
}
