/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Cooked — Snake, but the whole arcade at once. A massive population of
 * glowing snakes carves right-angle paths across the surfaces, each dragging
 * a fading tail. When a snake's head runs into another snake's tail it's
 * over: the loser glitches — strobing cyan-white, jittering like SpecialKube —
 * and fades away, while a fresh snake hatches somewhere else. The Rate knob
 * sets how fast they run; Wow makes them twitchier and the deaths more
 * spectacular. Inspired by the racing right-angle strand runs of the
 * holotrigger reference.
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
public class Cooked extends ParameterPattern {

  private static final int MAX_SNAKES = 96;
  private static final int MAX_LEN = 64;
  private static final int CYAN = LXColor.rgb(0, 255, 255);
  private static final int STROBE_COLOR = LXColor.lerp(LXColor.WHITE, CYAN, 0.30f);
  private static final double DIE_MS = 500;      // glitch-out duration
  private static final double RESPAWN_MS = 400;
  private static final double BASE_STEPS_S = 26; // cells/sec at Rate 0.5
  private static final double HUE_SPREAD = 70;   // per-snake hue variety

  public final DiscreteParameter snakes =
    new DiscreteParameter("Snakes", 48, 8, MAX_SNAKES)
    .setDescription("How many snakes run at once per surface");

  public final CompoundParameter rate =
    new CompoundParameter("Rate", 0.5, 0.05, 1)
    .setDescription("How fast the snakes run");

  public final DiscreteParameter length =
    new DiscreteParameter("Length", 26, 5, MAX_LEN)
    .setDescription("Snake body length");

  private final class Surface {
    int w, h;
    boolean inited;
    // ring-buffer bodies: packed cells (y * w + x), newest at headIdx
    final int[][] body = new int[MAX_SNAKES][MAX_LEN];
    final int[] headIdx = new int[MAX_SNAKES];
    final int[] curLen = new int[MAX_SNAKES];
    final int[] dx = new int[MAX_SNAKES];
    final int[] dy = new int[MAX_SNAKES];
    final double[] stepAcc = new double[MAX_SNAKES];
    final double[] speedVar = new double[MAX_SNAKES];
    final double[] raceMs = new double[MAX_SNAKES]; // racer phase time remaining
    final double[] hue = new double[MAX_SNAKES];
    final double[] dieMs = new double[MAX_SNAKES];  // >0 while glitching out
    final double[] waitMs = new double[MAX_SNAKES]; // respawn delay
    final boolean[] alive = new boolean[MAX_SNAKES];
    int[] owner; // occupancy grid, -1 = empty

    void ensure(int w, int h) {
      if (this.inited && this.w == w && this.h == h) return;
      this.w = w;
      this.h = h;
      this.owner = new int[w * h];
      for (int i = 0; i < MAX_SNAKES; ++i) {
        this.alive[i] = false;
        this.dieMs[i] = 0;
        this.waitMs[i] = Math.random() * 600;
      }
      this.inited = true;
    }

    void hatch(int i) {
      final int x = (int) (Math.random() * this.w);
      final int y = 1 + (int) (Math.random() * (this.h - 2));
      // start moving in a random cardinal direction
      if (Math.random() < 0.6) { // mostly horizontal, like the reference runs
        this.dx[i] = Math.random() < 0.5 ? 1 : -1;
        this.dy[i] = 0;
      } else {
        this.dx[i] = 0;
        this.dy[i] = Math.random() < 0.5 ? 1 : -1;
      }
      this.headIdx[i] = 0;
      this.curLen[i] = 1;
      this.body[i][0] = y * this.w + x;
      this.stepAcc[i] = 0;
      this.speedVar[i] = 0.7 + Math.random() * 0.6;
      // most snakes hatch as fast racers, then settle into crawlers
      this.raceMs[i] = (Math.random() < 0.7) ? 900 + Math.random() * 900 : 0;
      this.hue[i] = Math.random();
      this.dieMs[i] = 0;
      this.alive[i] = true;
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double timeMs = 0;

  public Cooked(LX lx) {
    // Base registers color (base hue), speed (unused: Rate governs), size (glow width).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("snakes", this.snakes);
    addParameter("rate", this.rate);
    addParameter("length", this.length);
    addTargetParameter();
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.timeMs += deltaMs;
    final Target t = getTarget();
    if (t != Target.CYLINDER) {
      step(this.cube, Apotheneum.cube.exterior, deltaMs);
    }
    if (t != Target.CUBE) {
      step(this.cylinder, Apotheneum.cylinder.exterior, deltaMs);
    }
    copyExterior();
  }

  private void step(Surface s, Apotheneum.Orientation o, double deltaMs) {
    final int w = o.width();
    final int h = o.height();
    s.ensure(w, h);

    final double wow = getWow();
    final int want = Math.min(MAX_SNAKES, this.snakes.getValuei());
    final int maxLen = this.length.getValuei();
    // parameterized speed: Rate knob, quadratic for reach at the top end
    final double r = this.rate.getValue();
    final double stepsPerMs = BASE_STEPS_S * (0.15 + 1.85 * r * r) * 2 / 1000.0;
    final double turnCrawl = 0.12 + wow * 0.18; // crawlers meander
    final double turnRace = 0.025 + wow * 0.06;  // racers run straight, rare jogs

    // rebuild occupancy from living bodies
    java.util.Arrays.fill(s.owner, -1);
    for (int i = 0; i < MAX_SNAKES; ++i) {
      if (!s.alive[i] || s.dieMs[i] > 0) continue;
      for (int k = 0; k < s.curLen[i]; ++k) {
        final int cell = s.body[i][(s.headIdx[i] - k + MAX_LEN * 2) % MAX_LEN];
        s.owner[cell] = i;
      }
    }

    int alive = 0;
    for (int i = 0; i < MAX_SNAKES; ++i) {
      if (s.alive[i]) ++alive;
    }

    for (int i = 0; i < MAX_SNAKES; ++i) {
      if (!s.alive[i]) {
        if (alive < want) {
          s.waitMs[i] -= deltaMs;
          if (s.waitMs[i] <= 0) {
            s.hatch(i);
            ++alive;
          }
        }
        continue;
      }

      // dying: glitch out, then free the slot
      if (s.dieMs[i] > 0) {
        s.dieMs[i] -= deltaMs;
        if (s.dieMs[i] <= 0) {
          s.alive[i] = false;
          s.waitMs[i] = RESPAWN_MS + Math.random() * 600;
          --alive;
        }
        continue;
      }

      // advance in grid steps; racers run ~3.5x until they settle
      final boolean racing = s.raceMs[i] > 0;
      if (racing) {
        s.raceMs[i] -= deltaMs;
      }
      s.stepAcc[i] += deltaMs * stepsPerMs * s.speedVar[i] * (racing ? 3.5 : 1.0);
      int guard = 0;
      while (s.stepAcc[i] >= 1 && guard++ < 6) {
        s.stepAcc[i] -= 1;

        // occasional right-angle turn; forced at the top/bottom walls
        final int hx = s.body[i][s.headIdx[i]] % w;
        final int hy = s.body[i][s.headIdx[i]] / w;
        if (Math.random() < (racing ? turnRace : turnCrawl)) {
          if (s.dx[i] != 0) {
            s.dx[i] = 0;
            s.dy[i] = Math.random() < 0.5 ? 1 : -1;
          } else {
            s.dy[i] = 0;
            s.dx[i] = Math.random() < 0.5 ? 1 : -1;
          }
        }
        int ny = hy + s.dy[i];
        if (ny < 0 || ny >= h) { // bounce off floor/ceiling into a horizontal run
          s.dy[i] = 0;
          s.dx[i] = Math.random() < 0.5 ? 1 : -1;
          ny = hy;
        }
        final int nx = ((hx + s.dx[i]) % w + w) % w;
        final int cell = ny * w + nx;

        // the kill rule: another snake's body ends this one
        final int hit = s.owner[cell];
        if (hit >= 0 && hit != i) {
          s.dieMs[i] = DIE_MS * (1 + wow * 0.6);
          break;
        }

        // slither forward; note the cell the tail vacates BEFORE the head
        // moves — computing it after resolves to the still-occupied tail and
        // leaves a stale owner entry (false collisions on multi-step frames)
        final boolean willGrow = s.curLen[i] < Math.min(maxLen, MAX_LEN);
        final int vacated = willGrow ? -1
          : s.body[i][(s.headIdx[i] - s.curLen[i] + 1 + MAX_LEN * 2) % MAX_LEN];
        s.headIdx[i] = (s.headIdx[i] + 1) % MAX_LEN;
        s.body[i][s.headIdx[i]] = cell;
        if (willGrow) {
          s.curLen[i]++;
        }
        s.owner[cell] = i;
        if (vacated >= 0 && vacated != cell && s.owner[vacated] == i) {
          boolean stillBody = false;
          for (int k = 0; k < s.curLen[i]; ++k) {
            if (s.body[i][(s.headIdx[i] - k + MAX_LEN * 2) % MAX_LEN] == vacated) {
              stillBody = true;
              break;
            }
          }
          if (!stillBody) {
            s.owner[vacated] = -1;
          }
        }
      }
    }

    // --- draw ---
    final double baseHue = LXColor.h(getColor());
    final int tq = (int) (this.timeMs / 60);
    for (int i = 0; i < MAX_SNAKES; ++i) {
      if (!s.alive[i]) continue;
      final boolean dying = s.dieMs[i] > 0;
      final double dieF = dying ? LXUtils.clamp(s.dieMs[i] / DIE_MS, 0, 1) : 1;
      final double grpHue = (s.hue[i] < 0.7)
        ? 120 + (s.hue[i] / 0.7 - 0.5) * 22   // green family (~70%)
        : 10 + ((s.hue[i] - 0.7) / 0.3 - 0.5) * 20; // red-orange family (~30%)
      final float hue = (float) (((baseHue - 120 + grpHue) % 360 + 360) % 360);
      final int col = LXColor.hsb(hue, 92, 100);
      final int head = LXColor.lerp(col, LXColor.WHITE, 0.6f);
      // dying snakes strobe cyan-white in sync, SpecialKube-style
      final boolean strobeOn = dying && ((tq + i) & 1) == 0;

      for (int k = 0; k < s.curLen[i]; ++k) {
        final int cell = s.body[i][(s.headIdx[i] - k + MAX_LEN * 2) % MAX_LEN];
        int x = cell % w;
        final int y = cell / w;
        double b = (k == 0 ? 1.0 : Math.exp(-2.0 * k / s.curLen[i])) * dieF;
        int c = (k == 0) ? head : col;
        if (dying) {
          if (strobeOn) {
            c = STROBE_COLOR;
            b = Math.min(1, b * 2.2);
            x = ((x + (int) ((Math.random() - 0.5) * (2 + getWow() * 4))) % w + w) % w; // jitter
          } else {
            b *= 0.4;
          }
        }
        final int idx = o.point(x, y).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx],
          LXColor.scaleBrightness(c, (float) LXUtils.clamp(b, 0, 1)));
      }
    }
  }
}
