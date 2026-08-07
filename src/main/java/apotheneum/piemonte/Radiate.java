/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Radiate — you stand where the performer sat, on a floor of
 * LED dots: dotted rays fan outward all around you — up the walls, since out
 * on the floor is up on the cylinder — their dot-chains crawling slowly
 * outward, faster on the bass. Dotted rings hug the center below them, red
 * against the cool rays. On every hit a wavefront of brightness blooms from
 * the center to the rim in under half a second; energetic sections keep the
 * whole room saturated in sustained color, and between sections everything
 * collapses to a sparse starfield of twinkling white-blue dots. The palette
 * steps through the reference's arc: red, emerald, teal, blue, a split
 * red/blue ring, and a magenta haze. Modeled on the ksawerykomputery studio
 * floor piece.
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
public class Radiate extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int MAX_RAYS = 90;
  private static final double SCENE_MS = 6700;   // per palette scene (~40s arc / 6)
  private static final double SCENE_XFADE = 1500;
  private static final double DUO = -1000;       // red/blue split ring scene
  private static final double DOT_SPACING = 2.6; // cells between ray dots
  private static final double BLOOM_MS = 400;    // center->rim wavefront
  // scene hues matching the reference arc: red, emerald, teal, blue, duo, magenta
  private static final double[] SCENES = { 358, 124, 180, 228, DUO, 292 };

  public final DiscreteParameter rays =
    new DiscreteParameter("Rays", 56, 12, MAX_RAYS)
    .setDescription("How many dotted rays fan around the cylinder");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How easily the music blooms and fills the room");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.CYLINDER)
    .setDescription("Which structures to render to");

  // per-ray state
  private final double[] rx = new double[MAX_RAYS];   // column position (fixed)
  private final double[] rlen = new double[MAX_RAYS]; // 0.85..1.0 coordinated length
  private final double[] binMag = new double[MAX_RAYS];
  private boolean inited = false;

  // audio
  private double bassAvg, prevBass, sinceBeatMs = 1e9;
  private double levelEnv = 0;
  private double bassEnv = 0;
  private double floodEnv = 0;   // sustained section fill (slow release)
  private double bloomT = -1;    // center->rim wavefront progress, <0 idle
  private double pulse = 0;
  private double timeMs = 0;
  private double dotFlow = 0;    // outward crawl of the dot chains
  private double ringFlow = 0;

  public Radiate(LX lx) {
    // Base registers color (global hue shift), speed (crawl rate), size (dot size/spacing).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("rays", this.rays);
    addParameter("sensitivity", this.sensitivity);
    addParameter("target", this.target);
  }

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  private void initRays() {
    for (int i = 0; i < MAX_RAYS; ++i) {
      this.rx[i] = hashd(i * 7 + 1); // normalized 0..1 around any surface
      this.rlen[i] = 0.85 + 0.15 * hashd(i * 31 + 11);
    }
    this.inited = true;
  }

  private double band(int i, int n) {
    heronarts.lx.audio.GraphicMeter m = this.lx.engine.audio.meter;
    int nb = Math.max(1, m.numBands);
    int lo = i * nb / n;
    int hi = Math.max(lo + 1, (i + 1) * nb / n);
    return m.getAveragef(lo, hi - lo);
  }

  private boolean detect(double deltaMs) {
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
    this.prevBass = bass;
    if (beat) {
      this.sinceBeatMs = 0;
    }
    double level = m.getAveragef(0, nb);
    double aUp = 1 - Math.exp(-deltaMs / 25.0);
    double aDn = 1 - Math.exp(-deltaMs / 220.0);
    this.levelEnv += (level - this.levelEnv) * ((level > this.levelEnv) ? aUp : aDn);
    this.bassEnv += (bass - this.bassEnv) * ((bass > this.bassEnv) ? aUp : aDn);
    return beat;
  }

  private double sceneHue(int which) {
    return SCENES[((which % SCENES.length) + SCENES.length) % SCENES.length];
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.timeMs += deltaMs;

    if (!this.inited) {
      initRays();
    }

    final double speed = Math.max(0.02, getSpeed());
    final double wow = getWow();
    final boolean beat = detect(deltaMs);

    // outward crawl of dot chains: slow at rest, faster on bass
    this.dotFlow += deltaMs * speed * (0.004 + this.bassEnv * 0.02) * DOT_SPACING;
    this.ringFlow += deltaMs * speed * (0.002 + this.bassEnv * 0.012);

    if (beat) {
      this.pulse = 1;
      if (this.bloomT < 0) {
        this.bloomT = 0; // fire a center->rim brightness wavefront
      }
    }
    this.pulse *= Math.exp(-deltaMs / 160.0);
    if (this.bloomT >= 0) {
      this.bloomT += deltaMs / BLOOM_MS;
      if (this.bloomT >= 1.2) this.bloomT = -1;
    }
    // sustained section fill: fast attack, ~2s release into the breakdown
    final double floodTarget = LXUtils.clamp(this.levelEnv * (1.4 + wow * 0.4), 0, 1);
    final double fa = (floodTarget > this.floodEnv)
      ? 1 - Math.exp(-deltaMs / 120.0)
      : 1 - Math.exp(-deltaMs / 2000.0);
    this.floodEnv += (floodTarget - this.floodEnv) * fa;

    final Target t = this.target.getEnum();
    if (t != Target.CUBE) {
      renderField(Apotheneum.cylinder.exterior, deltaMs);
      copyCylinderExterior();
    }
    if (t != Target.CYLINDER) {
      renderField(Apotheneum.cube.exterior, deltaMs);
      copyCubeExterior();
    }
  }

  private void renderField(Apotheneum.Orientation o, double deltaMs) {
    final int w = o.width();
    final int h = o.height();
    final double speed = Math.max(0.02, getSpeed());
    final double wow = getWow();

    // palette scenes stepping through the reference arc
    final double hueShift = LXColor.h(getColor());
    final double sceneT = this.timeMs / SCENE_MS;
    final int scene = (int) Math.floor(sceneT);
    final double xfade = LXUtils.clamp((sceneT - scene) * SCENE_MS / SCENE_XFADE, 0, 1);
    final double huePrev = sceneHue(scene - 1);
    final double hueCur = sceneHue(scene);
    final boolean duoNow = (hueCur == DUO);
    final double resolvedPrev = (huePrev == DUO) ? 228 : huePrev;
    final double resolvedCur = duoNow ? 228 : hueCur;
    final double hueMain = LXUtils.lerp(resolvedPrev,
      resolvedPrev + wrapDeg(resolvedCur - resolvedPrev), xfade) + hueShift;
    final int col = LXColor.hsb((float) (((hueMain % 360) + 360) % 360), 88, 100);
    final int hot = LXColor.lerp(col, LXColor.WHITE, 0.6f);
    final int ringRed = LXColor.hsb((float) (((358 + hueShift) % 360)), 95, 100);
    final int duoRed = ringRed;
    final int duoBlue = LXColor.hsb((float) (((222 + hueShift) % 360)), 92, 100);

    final boolean breakdown = this.levelEnv < 0.06;
    final int count = Math.min(MAX_RAYS, this.rays.getValuei() + (int) Math.round(wow * 20));
    final int nBins = Math.max(8, count / 3);
    final double aBin = 1 - Math.exp(-deltaMs / 90.0);
    final double dotR = 0.5 + getSize() * 0.8;
    final double spacing = DOT_SPACING + getSize() * 1.5;
    final double bloomFront = (this.bloomT >= 0) ? (1.0 - this.bloomT) * h : -100;
    final double fieldB = (0.40 + 0.60 * this.floodEnv) * (1.0 + this.pulse * 0.35);

    if (!breakdown) {
      // --- dotted radial rays: bottom (center) to top (rim) ---
      for (int i = 0; i < count; ++i) {
        this.binMag[i] += (band(i % nBins, nBins) - this.binMag[i]) * aBin;
        // coordinated length: the field grows and shrinks together
        final double len = h * LXUtils.clamp(
          0.30 + 0.65 * this.floodEnv + 0.10 * LXUtils.clamp(this.binMag[i] * 2, 0, 1), 0, 1)
          * this.rlen[i];
        final int x = (int) Math.round(this.rx[i] * w);
        // DUO scene: red/blue opposition split by a slowly rotating boundary
        int rayCol = col, rayHot = hot;
        if (duoNow) {
          final boolean redSide = ((this.rx[i] + this.timeMs * 0.00006) % 1.0) < 0.5;
          rayCol = redSide ? duoRed : duoBlue;
          rayHot = LXColor.lerp(rayCol, LXColor.WHITE, 0.6f);
        }
        for (int y = h - 1; y >= h - (int) len && y >= 0; --y) {
          // dotted chain: lit only on the dot cells, crawling outward (upward)
          final double sdot = ((h - 1 - y) + this.dotFlow) / spacing;
          final double f = sdot - Math.floor(sdot);
          if (f > 0.5) continue;
          double b = fieldB * (0.45 + 0.55 * (1.0 - (double) (h - 1 - y) / Math.max(1, len)));
          // bloom wavefront sweeping center->rim
          if (bloomFront > -50) {
            final double d = y - bloomFront;
            b *= 1.0 + 1.2 * Math.exp(-d * d / 18.0);
          }
          // white-hot core only on the innermost dots
          final int c = (h - 1 - y < spacing * 2) ? rayHot : rayCol;
          splat(o, w, h, x, y, dotR, b, c);
        }
      }

      // --- dotted concentric rings hugging the center (bottom region) ---
      final int nRings = 3;
      final double ringGap = h * 0.055;
      final double ringB = fieldB * (0.5 + 0.5 * this.floodEnv);
      final int ringDotStep = 3;
      final double duoSpin = duoNow ? this.timeMs * 0.004 : 0;
      for (int r = 0; r < nRings; ++r) {
        final double ry = (r * ringGap + this.ringFlow * ringGap) % (h * 0.24);
        final int yy = h - 2 - (int) Math.round(ry);
        for (int x = 0; x < w; x += ringDotStep) {
          int c = ringRed;
          if (duoNow) {
            c = (((x + (int) duoSpin) / (w / 4)) % 2 == 0) ? duoRed : duoBlue;
          }
          splat(o, w, h, x, yy, dotR, ringB * (0.8 - r * 0.18), c);
        }
      }

      // --- sustained chromatic section fill (ambient, saturated) ---
      if (this.floodEnv > 0.05) {
        final double haze = (hueCur == 292 || huePrev == 292) ? 0.55 : 0.30;
        final int fc = LXColor.scaleBrightness(
          LXColor.hsb((float) (((hueMain % 360) + 360) % 360), 75, 100),
          (float) (this.floodEnv * this.floodEnv * haze));
        for (int x = 0; x < w; ++x) {
          for (int y = 0; y < h; ++y) {
            final int idx = o.point(x, y).index;
            this.colors[idx] = LXColor.lightest(this.colors[idx], fc);
          }
        }
      }

      // --- center bloom at the floor line on hits ---
      if (this.pulse > 0.05) {
        for (int y = h - 5; y < h; ++y) {
          final double g = this.pulse * (0.8 + wow * 0.5) * Math.exp(-(h - 1 - y) / 1.8);
          for (int x = 0; x < w; ++x) {
            splat(o, w, h, x, y, 0.8, g * 0.5, hot);
          }
        }
      }
    } else {
      // --- starfield breakdown: sparse random white-blue twinkles ---
      final int star = LXColor.hsb(210, 30, 100);
      final int tq = (int) (this.timeMs / 180);
      final int nStars = (int) (w * h * 0.012);
      for (int k = 0; k < nStars; ++k) {
        final int sx = (int) (hashd(k * 31 + tq * 977) * w);
        final int sy = (int) (hashd(k * 53 + tq * 613) * h);
        final double tw = hashd(k * 97 + tq * 131);
        if (tw < 0.45) continue;
        splat(o, w, h, sx, sy, 0.8, 0.25 + 0.75 * tw, (tw > 0.92) ? LXColor.WHITE : star);
      }
    }

  }

  private static double wrapDeg(double d) {
    d = ((d % 360) + 360) % 360;
    return (d > 180) ? d - 360 : d;
  }

  /** Soft dot; x wraps, y clips. */
  private void splat(Apotheneum.Orientation o, int w, int h, double x, double y,
      double r, double bright, int color) {
    if (bright <= 0.004) return;
    final int y0 = (int) Math.max(0, Math.floor(y - r));
    final int y1 = (int) Math.min(h - 1, Math.ceil(y + r));
    final int x0 = (int) Math.floor(x - r);
    final int x1 = (int) Math.ceil(x + r);
    for (int yy = y0; yy <= y1; ++yy) {
      final double dy = yy - y;
      for (int xx = x0; xx <= x1; ++xx) {
        final double dx = xx - x;
        final double dd = Math.sqrt(dx * dx + dy * dy);
        if (dd > r + 0.5) continue;
        final double u = 1.0 - dd / (r + 0.5);
        final double f = u * u * bright;
        if (f <= 0.004) continue;
        final int xi = ((xx % w) + w) % w;
        final int idx = o.point(xi, yy).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx],
          LXColor.scaleBrightness(color, (float) LXUtils.clamp(f, 0, 1)));
      }
    }
  }
}
