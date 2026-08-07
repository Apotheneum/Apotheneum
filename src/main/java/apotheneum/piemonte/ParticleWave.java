/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * ParticleWave — a vast rolling swell of shimmering grains sweeps around the
 * structure as a thick undulating band: a sea of glitter surging through a dark
 * starfield. The band's edges dissolve into loose grains, a foamy white crest
 * sparkles along the boundary, and sparse cool stars twinkle in the void.
 * Turning up Wow whips the sea into a storm: taller swells, faster roll, hotter
 * foam, and bursts of blue sparkle clusters in the dark.
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
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class ParticleWave extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int STAR_COLOR = LXColor.hsb(225, 35, 100);
  private static final int CLUSTER_COLOR = LXColor.lerp(STAR_COLOR, LXColor.WHITE, 0.45f);
  private static final double STAR_DENSITY = 0.016;
  private static final int MAX_CLUSTERS = 16;
  private static final int CLUSTER_GLINTS = 16;
  private static final double TAU = Math.PI * 2;

  public final CompoundParameter density =
    new CompoundParameter("Density", 0.65, 0.2, 1)
    .setDescription("Grain density of the particle sea");

  public final BooleanParameter strobe =
    new BooleanParameter("Strobe", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Light tiny dark-purple particles in the negative space while held");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  private final class Surface {
    // sparkle-cluster pool (storm surge bursts in the void)
    final double[] cx = new double[MAX_CLUSTERS];
    final double[] cy = new double[MAX_CLUSTERS];
    final double[] clife = new double[MAX_CLUSTERS];
    final double[] cmax = new double[MAX_CLUSTERS];
    final int[] cseed = new int[MAX_CLUSTERS];
    final boolean[] calive = new boolean[MAX_CLUSTERS];

    void spawnCluster(int w, int h) {
      for (int i = 0; i < MAX_CLUSTERS; ++i) {
        if (!this.calive[i]) {
          this.cx[i] = Math.random() * w;
          this.cy[i] = Math.random() * h;
          this.cmax[i] = 700 + Math.random() * 700;
          this.clife[i] = this.cmax[i];
          this.cseed[i] = (int) (Math.random() * 100000);
          this.calive[i] = true;
          return;
        }
      }
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double timeMs = 0;
  private double phase = 0;
  // audio: dynamic grain scaling rides the level and the beat
  private double bassAvg, prevBass, sinceBeatMs = 1e9;
  private double levelEnv = 0;
  private double beatPulse = 0;
  private double strobeEnv = 0;

  public ParticleWave(LX lx) {
    // Base registers color (grain tint), speed (roll rate), size (band thickness).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("density", this.density);
    addParameter("strobe", this.strobe);
    addParameter("target", this.target);
  }

  private static double hash(int x, int y, int t) {
    int h = x * 374761393 + y * 668265263 + t * 1274126177;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.timeMs += deltaMs;

    final double speed = Math.max(0.02, getSpeed());
    final double wow = getWow();
    // the swell rolls around the structure; storms roll faster
    this.phase += deltaMs * 0.00035 * speed * (1.0 + wow * 0.9);

    // audio envelopes for the dynamically scaling grains
    heronarts.lx.audio.GraphicMeter m = this.lx.engine.audio.meter;
    final int nb = Math.max(1, m.numBands);
    final double bass = m.getAveragef(0, Math.max(1, nb / 4));
    this.bassAvg += (bass - this.bassAvg) * (1 - Math.exp(-deltaMs / 400.0));
    this.sinceBeatMs += deltaMs;
    if (bass > this.bassAvg * 1.35 && bass > this.prevBass
        && this.sinceBeatMs >= 140 && bass > 0.01) {
      this.sinceBeatMs = 0;
      this.beatPulse = 1;
    }
    this.prevBass = bass;
    this.beatPulse *= Math.exp(-deltaMs / 220.0);
    final double level = m.getAveragef(0, nb);
    final double alpha = (level > this.levelEnv)
      ? 1 - Math.exp(-deltaMs / 25.0) : 1 - Math.exp(-deltaMs / 220.0);
    this.levelEnv += (level - this.levelEnv) * alpha;

    // strobe: full while held, quick decay after release
    if (this.strobe.isOn()) {
      this.strobeEnv = 1;
    } else if (this.strobeEnv > 0) {
      this.strobeEnv = Math.max(0, this.strobeEnv - deltaMs / 400.0);
    }

    final Target t = this.target.getEnum();
    if (t != Target.CYLINDER) {
      step(this.cube, Apotheneum.cube.exterior, deltaMs, speed, wow);
    }
    if (t != Target.CUBE) {
      step(this.cylinder, Apotheneum.cylinder.exterior, deltaMs, speed, wow);
    }
    copyExterior();
  }

  private void step(Surface s, Apotheneum.Orientation o, double deltaMs, double speed, double wow) {
    final int w = o.width();
    final int h = o.height();

    final int base = getColor();
    final int glint = LXColor.lerp(base, LXColor.WHITE, 0.65f);
    final int foam = LXColor.lerp(base, LXColor.WHITE, 0.85f);
    final double dens = this.density.getValue();

    // storm surge: taller swell, thicker foam
    final double amp = h * 0.18 * (1.0 + wow * 1.2);
    final double thick = h * (0.12 + getSize() * 0.28);
    final double foamW = 1.6 + wow * 1.8;
    final double drift = 0.16 * Math.sin(this.timeMs * 8.0e-5 * (0.5 + speed));
    final double ph = this.phase;

    // shimmer quanta: grains flicker fast, stars twinkle slow
    final int tq = (int) (this.timeMs / 50);
    final int tqs = (int) (this.timeMs / 400);

    // slow corner rocking: the wave's ends at each face corner drift up and
    // down at their own gentle pace, tilting the band across every face
    final double segW = w / 4.0;
    final double rockAmp = h * 0.12;
    final double rt = this.timeMs * 2.2e-4 * (0.5 + speed);

    for (int x = 0; x < w; ++x) {
      final double u = (double) x / w;
      final int seg = Math.min(3, (int) (x / segW));
      final double su = x / segW - seg;
      final double se = su * su * (3 - 2 * su); // ease between the two corners
      final double c0 = Math.sin(rt + seg * 1.9);
      final double c1 = Math.sin(rt + ((seg + 1) % 4) * 1.9);
      final double rock = rockAmp * LXUtils.lerp(c0, c1, se);
      // three traveling sine octaves shape the swell
      final double yc = h * (0.5 + drift) + rock + amp * (
          0.60 * Math.sin(TAU * (u * 2 - ph))
        + 0.25 * Math.sin(TAU * (u * 5 + ph * 1.7))
        + 0.15 * Math.sin(TAU * (u * 9 - ph * 2.3)));
      final double th = thick * (0.75 + 0.25 * Math.sin(TAU * (u * 3 + ph * 0.8)));

      for (int y = 0; y < h; ++y) {
        final double dRaw = Math.abs(y - yc) - th; // signed distance outside the band
        // granular dissolving edge: jitter the boundary per-pixel
        final double d = dRaw + (hash(x, y, tq >> 2) - 0.5) * 3.0;

        int c;
        double b;
        if (d < 0) {
          // inside the sea: hashed glitter shimmer
          final double g = hash(x, y, tq);
          if (g > dens) continue; // unlit grain this instant
          final double v = g / dens;
          b = 0.22 + 0.78 * v * v * v;
          c = (v > 0.965) ? LXColor.WHITE : ((v > 0.8) ? glint : base);
          // feather deep pixels slightly less than the fringe
          final double edge = LXUtils.clamp(-d / 3.0, 0, 1);
          b *= 0.55 + 0.45 * edge;
        } else {
          // strobe: tiny dark-purple particles fill the negative space
          if (this.strobeEnv > 0.02 && ((int) (this.timeMs / 90) & 1) == 0) {
            final double sp = hash(x, y, tq + 7717);
            if (sp < 0.14) {
              final int purple = LXColor.hsb(275, 92, 48);
              final int idx2 = o.point(x, y).index;
              this.colors[idx2] = LXColor.lightest(this.colors[idx2],
                LXColor.scaleBrightness(purple,
                  (float) (this.strobeEnv * (0.5 + 0.5 * (sp / 0.14)))));
            }
          }
          // the void: sparse, spatially-stable stars with a slow twinkle
          final double rs = hash(x, y, 0);
          if (rs >= STAR_DENSITY) continue;
          final double tw = 0.35 + 0.65 * hash(x, y, tqs + 7919);
          b = tw * (0.30 + 0.70 * (rs / STAR_DENSITY));
          c = STAR_COLOR;
        }

        // foam crest: white-hot sparkle hugging the boundary
        if (Math.abs(dRaw) < foamW) {
          final double ff = 1.0 - Math.abs(dRaw) / foamW;
          final double fg = hash(x, y, tq + 4241);
          if (fg < 0.5 + wow * 0.3) {
            b = Math.max(b, ff * (0.7 + wow * 0.8) * (0.5 + 0.5 * fg / 0.8));
            c = foam;
          }
        }

        if (b <= 0) continue;
        final int idx = o.point(x, y).index;
        this.colors[idx] = LXColor.scaleBrightness(c, (float) LXUtils.clamp(b, 0, 1));
      }
    }

    // --- hero grains: discrete particles riding the swell, scaling with the music ---
    final int nHero = 20;
    for (int i = 0; i < nHero; ++i) {
      final int gx = (int) (hash(i * 61 + 13, 5, 0) * w);
      final double gu01 = (double) gx / w;
      // sample the band center at this column (same math as the pixel loop)
      final int gseg = Math.min(3, (int) (gx / segW));
      final double gsu = gx / segW - gseg;
      final double gse = gsu * gsu * (3 - 2 * gsu);
      final double gc0 = Math.sin(rt + gseg * 1.9);
      final double gc1 = Math.sin(rt + ((gseg + 1) % 4) * 1.9);
      final double gRock = rockAmp * LXUtils.lerp(gc0, gc1, gse);
      final double gyc = h * (0.5 + drift) + gRock + amp * (
          0.60 * Math.sin(TAU * (gu01 * 2 - ph))
        + 0.25 * Math.sin(TAU * (gu01 * 5 + ph * 1.7))
        + 0.15 * Math.sin(TAU * (gu01 * 9 - ph * 2.3)));
      final double gth = thick * (0.75 + 0.25 * Math.sin(TAU * (gu01 * 3 + ph * 0.8)));
      final double gy = gyc + (hash(i * 89 + 7, 9, 0) * 2 - 1) * gth * 0.7;
      // the music scales them: level swells all, beats pop a shifting subset
      final double gr = (0.7 + hash(i * 37 + 3, 2, 0) * 0.9)
        * (1.0 + this.levelEnv * 1.8 + this.beatPulse * 2.2 * hash(i * 53 + 1, (int) (this.timeMs / 500), 0));
      final double gb = 0.45 + 0.55 * this.levelEnv + this.beatPulse * 0.3;
      final int gcol = (hash(i * 71 + 9, 4, 0) > 0.7) ? LXColor.WHITE : glint;
      final int y0g = (int) Math.max(0, Math.floor(gy - gr));
      final int y1g = (int) Math.min(h - 1, Math.ceil(gy + gr));
      for (int yy = y0g; yy <= y1g; ++yy) {
        for (int xx = (int) Math.floor(gx - gr); xx <= (int) Math.ceil(gx + gr); ++xx) {
          final double dd = Math.hypot(xx - gx, yy - gy);
          if (dd > gr) continue;
          final double uG = 1.0 - dd / (gr + 0.5);
          final int xig = ((xx % w) + w) % w;
          final int idx3 = o.point(xig, yy).index;
          this.colors[idx3] = LXColor.lightest(this.colors[idx3],
            LXColor.scaleBrightness(gcol, (float) LXUtils.clamp(uG * uG * gb, 0, 1)));
        }
      }
    }

    // --- storm sparkle clusters bursting in the void ---
    if (Math.random() < deltaMs * (0.0002 + wow * 0.005)) {
      s.spawnCluster(w, h);
    }
    for (int i = 0; i < MAX_CLUSTERS; ++i) {
      if (!s.calive[i]) continue;
      s.clife[i] -= deltaMs;
      if (s.clife[i] <= 0) { s.calive[i] = false; continue; }
      final double fade = s.clife[i] / s.cmax[i];
      final double spread = 2.0 + (1.0 - fade) * 5.0; // glints scatter outward as it dies
      for (int k = 0; k < CLUSTER_GLINTS; ++k) {
        final double gx = s.cx[i] + (hash(s.cseed[i], k, 11) - 0.5) * 2 * spread;
        final double gy = s.cy[i] + (hash(s.cseed[i], k, 37) - 0.5) * 2 * spread;
        final int yy = (int) Math.round(gy);
        if (yy < 0 || yy >= h) continue;
        final int xi = (((int) Math.round(gx)) % w + w) % w;
        final double twk = 0.4 + 0.6 * hash(s.cseed[i] + k, tq, 53);
        final double gb = fade * fade * twk;
        if (gb <= 0.02) continue;
        final int idx = o.point(xi, yy).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx],
          LXColor.scaleBrightness(CLUSTER_COLOR, (float) LXUtils.clamp(gb, 0, 1)));
      }
    }
  }
}
