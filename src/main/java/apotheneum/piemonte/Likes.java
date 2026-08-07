/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Likes — purple circuitry firing across the strands. On every hit — bass,
 * pitch, a random clock, or the manual Fire button — traces ignite at random
 * spots: connected right-angle paths drawing themselves in, dashed and
 * twinkling with white-hot junctions, each eroding away dot by dot. Turn up
 * Wow and the traces glitch like SpecialKube, dots strobing between white
 * and purple; hard hits still bloom the whole field at once.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Likes extends StrandPattern {

  public enum Trigger {
    BASS,
    PITCH,
    RANDOM
  }

  private static final int MAX_GLYPHS = 20;
  private static final int MAX_SEGS = 9;       // segments per glyph path
  private static final double ATTACK_MS = 220; // trace draws itself in
  private static final double HOLD_MS = 450;
  private static final double ERODE_MS = 400;  // dots drop out
  private static final int STRAND_PITCH = 2;   // columns per "strand"

  public final DiscreteParameter density =
    new DiscreteParameter("Density", 6, 2, 12)
    .setDescription("Glyphs ignited per hit");

  public final EnumParameter<Trigger> trigger =
    new EnumParameter<Trigger>("Trigger", Trigger.BASS)
    .setDescription("What fires the circuitry: bass hits, pitch hits, or a random clock");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How easily hits fire");

  public final BooleanParameter fire =
    new BooleanParameter("Fire", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Manually ignite a burst of traces");

  /** One glyph = a connected right-angle path, precomputed at spawn. */
  private final class Surface {
    boolean inited;
    final int[][] sx0 = new int[MAX_GLYPHS][MAX_SEGS];
    final int[][] sy0 = new int[MAX_GLYPHS][MAX_SEGS];
    final int[][] sx1 = new int[MAX_GLYPHS][MAX_SEGS];
    final int[][] sy1 = new int[MAX_GLYPHS][MAX_SEGS];
    final int[] nSegs = new int[MAX_GLYPHS];
    final int[] totalLen = new int[MAX_GLYPHS];
    final double[] age = new double[MAX_GLYPHS];
    final int[] seed = new int[MAX_GLYPHS];
    final boolean[] alive = new boolean[MAX_GLYPHS];
    void reset() {
      for (int i = 0; i < MAX_GLYPHS; ++i) this.alive[i] = false;
      this.inited = true;
    }

    /** Build a right-angle trace: vertical runs joined by horizontal jogs. */
    void spawn(int w, int h) {
      int gi = -1;
      for (int i = 0; i < MAX_GLYPHS; ++i) {
        if (!this.alive[i]) { gi = i; break; }
      }
      if (gi < 0) return;
      int x = (int) (Math.random() * w);
      int y = 1 + (int) (Math.random() * h * 0.5); // seeds favor the upper strands
      int n = 0;
      int total = 0;
      final int steps = 3 + (int) (Math.random() * 4); // 3-6 runs
      final boolean block = Math.random() < 0.22;      // occasional closed rectangle
      for (int k = 0; k < steps && n < MAX_SEGS - 1; ++k) {
        // vertical run downward
        final int run = (int) (h * (0.15 + Math.random() * 0.13));
        final int yEnd = Math.min(h - 1, y + run);
        this.sx0[gi][n] = x; this.sy0[gi][n] = y;
        this.sx1[gi][n] = x; this.sy1[gi][n] = yEnd;
        total += yEnd - y; ++n;
        y = yEnd;
        if (y >= h - 1) break;
        // horizontal jog 1-3 strand pitches, either direction
        final int jog = STRAND_PITCH * (1 + (int) (Math.random() * 3))
          * (Math.random() < 0.5 ? 1 : -1);
        this.sx0[gi][n] = x; this.sy0[gi][n] = y;
        this.sx1[gi][n] = x + jog; this.sy1[gi][n] = y;
        total += Math.abs(jog); ++n;
        x = x + jog;
      }
      if (block && n < MAX_SEGS) {
        // close a small rectangle off the last corner
        final int bw = STRAND_PITCH * (2 + (int) (Math.random() * 2));
        this.sx0[gi][n] = x; this.sy0[gi][n] = Math.max(0, y - 6);
        this.sx1[gi][n] = x + bw; this.sy1[gi][n] = Math.max(0, y - 6);
        total += bw; ++n;
      }
      this.nSegs[gi] = n;
      this.totalLen[gi] = Math.max(1, total);
      this.age[gi] = 0;
      this.seed[gi] = (int) (Math.random() * 1000000);
      this.alive[gi] = true;
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double trebAvg, prevTreb, sinceTrebMs = 1e9;
  private double randomTimer = 0;
  private boolean fireWasOn = false;
  private double bloom = 0;

  public Likes(LX lx) {
    // Base registers color (circuit tint), speed (lifecycle pace), size (dash scale).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("density", this.density);
    addParameter("trigger", this.trigger);
    addParameter("sensitivity", this.sensitivity);
    addParameter("fire", this.fire);
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

  /** Onset detector on the top quarter of the spectrum (pitch hits). */
  private boolean detectPitchHit(double deltaMs) {
    double treb = this.level.getValue();
    this.trebAvg += (treb - this.trebAvg) * (1 - Math.exp(-deltaMs / 400.0));
    this.sinceTrebMs += deltaMs;
    final double threshold = 1.05 + (1 - this.sensitivity.getValue()) * 0.7;
    final boolean hit = pulseHit() || (treb > this.trebAvg * threshold)
      && (treb > this.prevTreb)
      && (this.sinceTrebMs >= 160)
      && (treb > 0.005);
    this.prevTreb = treb;
    if (hit) this.sinceTrebMs = 0;
    return hit;
  }

  @Override
  protected void advance(double deltaMs) {
    final double wow = getWow();
    final boolean pitchHit = detectPitchHit(deltaMs);

    boolean fire = false;
    // manual ignition: button edge fires in any trigger mode
    final boolean fireBtn = this.fire.isOn();
    if (fireBtn && !this.fireWasOn) {
      fire = true;
    }
    this.fireWasOn = fireBtn;
    switch (this.trigger.getEnum()) {
      case BASS: fire |= this.beat; break;
      case PITCH: fire |= pitchHit; break;
      case RANDOM:
        this.randomTimer -= deltaMs;
        if (this.randomTimer <= 0) {
          this.randomTimer = 700 + Math.random() * 700; // ~0.7-1.4s, like the reference
          fire = true;
        }
        break;
    }

    if (fire) {
      final boolean mega = (this.beatLevel > 0.75 - wow * 0.25) || (wow > 0.85);
      final int n = this.density.getValuei() + (int) Math.round(wow * 4)
        + (mega ? MAX_GLYPHS : 0);
      spawnBurst(this.cube, Apotheneum.cube.exterior, n);
      spawnBurst(this.cylinder, Apotheneum.cylinder.exterior, n);
      if (mega) this.bloom = 1;
    }
    this.bloom *= Math.exp(-deltaMs / 300.0);
  }

  private void spawnBurst(Surface s, Apotheneum.Orientation o, int n) {
    if (!s.inited) s.reset();
    for (int k = 0; k < n; ++k) {
      s.spawn(o.width(), o.height());
    }
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final Surface s = isCube ? this.cube : this.cylinder;
    if (!s.inited) s.reset();
    final int w = o.width();
    final int h = o.height();
    final double speed = Math.max(0.05, getSpeed());
    final double hueShift = LXColor.h(getColor());
    final int purple = LXColor.hsb((float) (((278 + hueShift) % 360)), 92, 100);
    final int dimPurple = LXColor.hsb((float) (((262 + hueShift) % 360)), 85, 100); // violet low end
    final int hot = LXColor.lerp(purple, LXColor.WHITE, 0.7f);
    // Wow: SpecialKube-style glitch — dots strobe between white and purple
    final double glitchAmt = getWow();
    final double gt = this.timeMs * (0.02 + glitchAmt * 0.04);
    final boolean strobeOn = ((this.timeMs * 0.001 * (6.0 + glitchAmt * 4.0)) % 1.0)
      < (0.12 + glitchAmt * 0.14);
    final int tq = (int) (this.timeMs / 90); // ~11Hz dot twinkle
    final double dashOn = 0.5 + getSize() * 0.2; // mark:gap near 1:1
    final double lifeSpeed = speed * 2;

    for (int g = 0; g < MAX_GLYPHS; ++g) {
      if (!s.alive[g]) continue;
      s.age[g] += deltaMs * lifeSpeed;
      final double age = s.age[g];
      final double total = ATTACK_MS + HOLD_MS + ERODE_MS;
      if (age >= total) { s.alive[g] = false; continue; }

      // attack: the trace draws in along its path; erode: dots drop out
      final double reveal = LXUtils.clamp(age / ATTACK_MS, 0, 1);
      final double erode = LXUtils.clamp((age - ATTACK_MS - HOLD_MS) / ERODE_MS, 0, 1);
      final int drawLen = (int) Math.ceil(s.totalLen[g] * reveal);

      int walked = 0;
      for (int seg = 0; seg < s.nSegs[g]; ++seg) {
        final int dx = Integer.signum(s.sx1[g][seg] - s.sx0[g][seg]);
        final int dy = Integer.signum(s.sy1[g][seg] - s.sy0[g][seg]);
        final int len = Math.abs(s.sx1[g][seg] - s.sx0[g][seg])
          + Math.abs(s.sy1[g][seg] - s.sy0[g][seg]);
        for (int k = 0; k <= len; ++k) {
          if (walked + k > drawLen) break;
          // dashed 1:1 mark-space texture
          final double dashPhase = ((walked + k) % 6) / 6.0;
          if (dashPhase > dashOn) continue;
          // erosion: dots drop out one by one
          if (erode > 0 && hashd(s.seed[g] + (walked + k) * 131) < erode) continue;
          final int x = s.sx0[g][seg] + dx * k;
          final int y = s.sy0[g][seg] + dy * k;
          if (y < 0 || y >= h) continue;
          // per-dot twinkle + brightness jitter
          final double tw = 0.55 + 0.45 * hashd(s.seed[g] + (walked + k) * 977 + tq * 7);
          double b = tw * (1.0 - erode * 0.4) * (0.85 + this.bloom * 0.5);
          // white-hot: junctions (segment ends) and the drawing head
          final boolean junction = (k == 0 || k == len);
          final boolean head = (walked + k) >= drawLen - 2 && reveal < 1;
          int c = (junction || head) ? hot : (tw < 0.72 ? dimPurple : purple);
          if (glitchAmt > 0.05) {
            // glitch flicker: dots flip white<->purple, harder with the dial
            final double gflip = hashd(s.seed[g] + (walked + k) * 613 + (int) (gt * 10));
            if (strobeOn && gflip < 0.35 + glitchAmt * 0.4) {
              c = LXColor.WHITE;
              b *= 1.3;
            } else if (gflip > 0.94 - glitchAmt * 0.2) {
              c = LXColor.lerp(LXColor.WHITE, purple, 0.35f);
            }
          }
          addPix(o, x, y, c, LXUtils.clamp(b, 0, 1));
        }
        walked += len;
      }
    }

    // faint lingering room spill after blooms
    if (this.bloom > 0.05) {
      final int spill = LXColor.scaleBrightness(purple, (float) (this.bloom * 0.18));
      for (int x = 0; x < w; ++x) {
        for (int y = 0; y < h; ++y) {
          final int idx = o.point(x, y).index;
          this.colors[idx] = LXColor.lightest(this.colors[idx], spill);
        }
      }
    }
  }
}
