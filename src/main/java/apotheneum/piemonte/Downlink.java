/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Downlink — the strand curtains run a raw machine-room program: emerald
 * matrix-rain pours down the columns while the whole room washes through slow
 * color floods — green, then red, then amber — and sparse blocky data-glyphs
 * flicker on and re-roll with the beat. Cyan service strips glow along the
 * floor line. Modeled on the holotrigger green/red studio session.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Downlink extends StrandPattern {

  private static final int MAX_DROPS = 400;
  private static final int GLYPHS = 10;
  // green <-> red flood; the downward lerp sweeps through amber mid-fade
  private static final double[] SCENE_HUES = { 125, 4 };
  private static final double SCENE_MS = 3200;
  private static final double XFADE_MS = 1500;

  public final CompoundParameter rain =
    new CompoundParameter("Rain", 0.6, 0, 1)
    .setDescription("Matrix-rain density");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Audio sensitivity of floods and glyph re-rolls");

  private final class Surface {
    boolean inited;
    final double[] px = new double[MAX_DROPS];
    final double[] py = new double[MAX_DROPS];
    final double[] pv = new double[MAX_DROPS];
    final boolean[] alive = new boolean[MAX_DROPS];
    // glyph blocks: column-group rectangles that re-roll on beats
    final int[] gx = new int[GLYPHS];
    final int[] gy = new int[GLYPHS];
    final boolean[] gon = new boolean[GLYPHS];
    void reset(int w, int h) {
      for (int i = 0; i < MAX_DROPS; ++i) this.alive[i] = false;
      for (int i = 0; i < GLYPHS; ++i) rollGlyph(this, i, w, h);
      this.inited = true;
    }
  }

  private static void rollGlyph(Surface s, int i, int w, int h) {
    s.gx[i] = (int) (Math.random() * w);
    s.gy[i] = (int) (Math.random() * (h - 8)) + 1;
    s.gon[i] = Math.random() < 0.65;
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double pulse = 0;
  private double floodLvl = 0; // asymmetric flood envelope: snap up, breathe down

  public Downlink(LX lx) {
    // Base registers color (hue shift), speed (rain fall rate), size (glyph scale).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("rain", this.rain);
    addParameter("sensitivity", this.sensitivity);
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

  @Override
  protected void advance(double deltaMs) {
    if (this.beat) {
      this.pulse = 1;
    }
    this.pulse *= Math.exp(-deltaMs / 200.0);
    // flood breathes: ~250ms attack, ~1.1s release (reference envelope)
    final double wow = getWow();
    final double target = LXUtils.clamp(
      0.04 + 0.85 * this.levelEnv * this.levelEnv * (1 + wow) + this.pulse * 0.18, 0, 0.95);
    final double tau = (target > this.floodLvl) ? 250.0 : 1100.0;
    this.floodLvl += (target - this.floodLvl) * (1 - Math.exp(-deltaMs / tau));
  }

  /** Current flood hue, crossfading between scene hues on a slow cycle. */
  private double floodHue() {
    final double t = this.timeMs / SCENE_MS;
    final int scene = (int) Math.floor(t);
    final double into = (t - scene) * SCENE_MS;
    final double f = LXUtils.clamp(into / XFADE_MS, 0, 1);
    final double a = SCENE_HUES[((scene - 1) % SCENE_HUES.length + SCENE_HUES.length) % SCENE_HUES.length];
    final double b = SCENE_HUES[(scene % SCENE_HUES.length + SCENE_HUES.length) % SCENE_HUES.length];
    double d = ((b - a) % 360 + 360) % 360;
    if (d > 180) d -= 360;
    return a + d * f;
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final Surface s = isCube ? this.cube : this.cylinder;
    final int w = o.width();
    final int h = o.height();
    if (!s.inited) s.reset(w, h);

    final double wow = getWow();
    final double speed = Math.max(0.05, getSpeed());
    final double hueShift = LXColor.h(getColor());
    final double fh = floodHue() + hueShift;
    final int scene = LXColor.hsb((float) (((fh % 360) + 360) % 360), 92, 100);
    // the three color identities are hard-separated in the reference: green
    // rain and red bars persist over whatever the flood is doing
    final int rainC = LXColor.hsb((float) (((125 + hueShift) % 360) + 360) % 360, 90, 100);
    final int glyphC = LXColor.hsb((float) (((4 + hueShift) % 360) + 360) % 360, 95, 100);
    final int glyphHot = LXColor.hsb((float) (((32 + hueShift) % 360) + 360) % 360, 100, 100);

    // global flood wash: dim ambient that surges toward full on swells
    final double floodB = LXUtils.clamp(this.floodLvl + wow * 0.10, 0, 0.95);
    final int floodC = LXColor.scaleBrightness(scene, (float) floodB);
    for (int x = 0; x < w; ++x) {
      for (int y = 0; y < h; ++y) {
        final int idx = o.point(x, y).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx], floodC);
      }
    }

    // matrix rain
    final double dens = this.rain.getValue() * (0.5 + 0.5 * this.levelEnv + wow * 0.6);
    final double spawnRate = dens * w * 0.0007 * deltaMs;
    int toSpawn = (int) spawnRate + ((Math.random() < spawnRate % 1) ? 1 : 0);
    for (int i = 0; i < MAX_DROPS && toSpawn > 0; ++i) {
      if (s.alive[i]) continue;
      s.px[i] = (int) (Math.random() * w);
      s.py[i] = -Math.random() * 5;
      s.pv[i] = (0.08 + Math.random() * 0.05) * speed; // full drop in ~0.7-1.1s at default
      s.alive[i] = true;
      --toSpawn;
    }
    final double tail = 16 + getSize() * 12; // reaches the longest staircases
    for (int i = 0; i < MAX_DROPS; ++i) {
      if (!s.alive[i]) continue;
      s.py[i] += s.pv[i] * deltaMs;
      if (s.py[i] >= h + tail) { s.alive[i] = false; continue; }
      comet(o, s.px[i], s.py[i], tail, rainC, 0.95, true);
    }

    // data-bars hold position ~1.2s; only their internal dots churn fast
    final double rollP = deltaMs / 1200.0;
    for (int i = 0; i < GLYPHS; ++i) {
      if (Math.random() < rollP || (this.beat && Math.random() < 0.25 + wow * 0.3)) {
        rollGlyph(s, i, w, h);
      }
    }
    final int gw = 2 + (int) (getSize() * 1.2);
    final int gh = 8 + (int) (getSize() * 6);
    final int tq = (int) (this.timeMs / 120);
    for (int i = 0; i < GLYPHS; ++i) {
      if (!s.gon[i]) continue;
      for (int dx = 0; dx < gw; ++dx) {
        for (int dy = 0; dy < gh; ++dy) {
          // dot-matrix bit texture churning inside the persistent bar
          final double bit = hashd(i * 977 + dx * 31 + dy * 7 + tq);
          if (bit < 0.35) continue;
          addPix(o, s.gx[i] + dx, s.gy[i] + dy,
            (bit > 0.82) ? glyphHot : glyphC, 0.85 + this.pulse * 0.3);
        }
      }
    }

    // warm console ring at the floor line (red/amber segments, never cyan)
    final int ringAmber = LXColor.hsb((float) (((34 + hueShift) % 360) + 360) % 360, 95, 100);
    for (int x = 0; x < w; ++x) {
      final double hh = hashd(x * 53 + (isCube ? 1 : 2));
      if (hh < 0.45) {
        final int c = (hashd(x * 17 + 3) < 0.5) ? glyphC : ringAmber;
        addPix(o, x, h - 1, c, 0.45 + 0.30 * hashd(x * 7 + tq));
        addPix(o, x, h - 2, c, 0.22);
      }
    }
    // green strand-pile puddles glittering on the floor
    for (int x = 0; x < w; ++x) {
      if (hashd(x * 29 + (isCube ? 7 : 9)) < 0.30) {
        final double b = 0.30 + 0.40 * hashd(x * 11 + tq) + 0.25 * this.levelEnv;
        addPix(o, x, h - 1, rainC, b);
        addPix(o, x, h - 2, rainC, b * 0.6);
        addPix(o, x, h - 3, rainC, b * 0.3);
      }
    }
  }
}
