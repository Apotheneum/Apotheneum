/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Cope — the structure's vertical edges lit as thick glowing bars: the four
 * corners of the cube and a set of evenly spaced lines around the cylinder. The
 * bars breathe with the music, swelling thicker as the sound gets louder, and on
 * a bass drop a bright radial glow erupts from an edge and blooms outward across
 * the surface. Audio-reactive architecture.
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
public class Cope extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final int MAX_PULSE = 8;
  private static final double MIN_BEAT_MS = 110;
  private static final double GLOW_BASE = 0.055;   // bloom growth, cells / ms at speed 1
  private static final double MAXAGE_BASE = 1400;  // pulse lifetime (ms) at speed 1

  public final CompoundParameter reactivity =
    new CompoundParameter("Reactivity", 0.6, 0, 1)
    .setDescription("How strongly audio thickens the edge bars");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Bass-drop trigger sensitivity for the radial glow");

  public final DiscreteParameter lines =
    new DiscreteParameter("Lines", 6, 1, 24)
    .setDescription("Number of vertical lines around the cylinder");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  /** A surface's edge columns + a pool of expanding glow pulses. */
  private final class Surface {
    int w, h;
    boolean inited;
    boolean isCube;
    int builtLines = -1;
    int[] edgeX = new int[0];
    final double[] ox = new double[MAX_PULSE];
    final double[] oy = new double[MAX_PULSE];
    final double[] age = new double[MAX_PULSE];
    final boolean[] alive = new boolean[MAX_PULSE];

    void ensure(int w, int h, boolean isCube, int nLines) {
      if (this.inited && this.w == w && this.h == h
          && (isCube || this.builtLines == nLines)) {
        return;
      }
      this.w = w;
      this.h = h;
      this.isCube = isCube;
      this.inited = true;
      if (isCube) {
        this.edgeX = new int[] { 0, 50, 100, 150 }; // the four vertical corners
        this.builtLines = -1;
      } else {
        this.edgeX = new int[nLines];
        for (int k = 0; k < nLines; ++k) {
          this.edgeX[k] = (int) Math.round((double) k * w / nLines);
        }
        this.builtLines = nLines;
      }
      for (int i = 0; i < MAX_PULSE; ++i) {
        this.alive[i] = false;
      }
    }

    void spawnPulse(double x, double y) {
      for (int i = 0; i < MAX_PULSE; ++i) {
        if (!this.alive[i]) {
          this.ox[i] = x;
          this.oy[i] = y;
          this.age[i] = 0;
          this.alive[i] = true;
          return;
        }
      }
      // pool full -> drop the request (oldest keeps blooming)
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();

  // shared bass-onset state (computed once per frame)
  private double bassAvg, prevBass, sinceBeatMs, beatLevel;
  private double levelEnv = 0;
  private double levelAvg = 0; // slow auto-gain reference
  private double beatKick = 0; // bars snap wide on each hit

  public Cope(LX lx) {
    // Base registers color (edge/glow tint), speed (glow expand/decay), size (base
    // thickness), wow (glow intensity). We add audio + geometry controls.
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("reactivity", this.reactivity);
    addParameter("sensitivity", this.sensitivity);
    addParameter("lines", this.lines);
    addParameter("target", this.target);
  }

  private boolean detectBeat(double deltaMs) {
    heronarts.lx.audio.GraphicMeter m = this.lx.engine.audio.meter;
    int nb = Math.max(1, m.numBands);
    double bass = m.getAveragef(0, Math.max(1, nb / 4)); // low quarter of the spectrum
    this.bassAvg += (bass - this.bassAvg) * (1 - Math.exp(-deltaMs / 400.0)); // slow moving average
    this.sinceBeatMs += deltaMs;

    double sens = this.sensitivity.getValue();
    double threshold = 1.05 + (1 - sens) * 0.7;          // sens up -> easier
    boolean beat = (bass > this.bassAvg * threshold)
      && (bass > this.prevBass)
      && (this.sinceBeatMs >= MIN_BEAT_MS)
      && (bass > 0.01);
    this.prevBass = bass;
    if (beat) {
      this.sinceBeatMs = 0;
    }
    this.beatLevel = LXUtils.clamp((bass / Math.max(1e-4, this.bassAvg) - 1.0) / 1.5, 0, 1);
    return beat;
  }

  private double broadband() {
    heronarts.lx.audio.GraphicMeter m = this.lx.engine.audio.meter;
    return m.getAveragef(0, Math.max(1, m.numBands));
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    final boolean beat = detectBeat(deltaMs);
    final double level = broadband();
    // attack/release envelope so the bars snap up quickly and relax smoothly
    double aAtk = 1 - Math.exp(-deltaMs / 25.0);
    double aRel = 1 - Math.exp(-deltaMs / 220.0);
    this.levelEnv += (level - this.levelEnv) * (level > this.levelEnv ? aAtk : aRel);
    // auto-gain drive: the envelope/average RATIO carries the dynamics (steady
    // music sits ~0.9-1.0; hits spike it, gaps drop it). Expand the contrast
    // around that resting point and snap on beats so the bars actually pump.
    this.levelAvg += (level - this.levelAvg) * (1 - Math.exp(-deltaMs / 2500.0));
    final double ratio = this.levelEnv / Math.max(1e-4, this.levelAvg);
    if (beat) {
      this.beatKick = 1;
    }
    this.beatKick *= Math.exp(-deltaMs / 250.0);
    final double norm = LXUtils.clamp((ratio - 0.55) * 1.6, 0, 1.2)
      + this.beatKick * 0.6;
    final Target t = this.target.getEnum();
    final int nLines = this.lines.getValuei();

    if (t != Target.CYLINDER) {
      step(this.cube, Apotheneum.cube.exterior, deltaMs, beat, norm, true, 0);
    }
    if (t != Target.CUBE) {
      step(this.cylinder, Apotheneum.cylinder.exterior, deltaMs, beat, norm, false, nLines);
    }
    copyExterior();
  }

  private void step(Surface s, Apotheneum.Orientation o, double deltaMs,
      boolean beat, double level, boolean isCube, int nLines) {
    final int w = o.width();
    final int h = o.height();
    s.ensure(w, h, isCube, nLines);

    final double wow = getWow();
    final int base = getColor();

    // --- thick vertical edge bars, thickness breathes with audio ---
    final double baseR = 0.5 + getSize() * 4.0 + wow * 2.0;
    final double thickMax = isCube ? w * 0.25 : 0.45 * (double) w / Math.max(1, nLines);
    final double thick = LXUtils.clamp(
      baseR + this.reactivity.getValue() * level * (thickMax - baseR) * 0.85, 0.5, thickMax);
    final double barBright = 0.50 + 0.50 * LXUtils.clamp(level * 0.8, 0, 1);
    for (int ex : s.edgeX) {
      for (int y = 0; y < h; ++y) {
        vbar(o, w, h, ex, y, thick, barBright, base);
      }
    }

    // --- bass drop spawns a radial glow from a random edge ---
    if (beat && s.edgeX.length > 0) {
      double x0 = s.edgeX[(int) (Math.random() * s.edgeX.length)];
      double y0 = (0.2 + 0.6 * Math.random()) * h;
      s.spawnPulse(x0, y0);
    }

    // --- advance + draw glow pulses (bounded bbox, X-wrapped) ---
    final double speed = getSpeed();
    final double glowSpeed = GLOW_BASE * (0.5 + speed);
    final double maxAge = MAXAGE_BASE / (0.5 + speed);
    for (int i = 0; i < MAX_PULSE; ++i) {
      if (!s.alive[i]) continue;
      s.age[i] += deltaMs;
      if (s.age[i] >= maxAge) {
        s.alive[i] = false;
        continue;
      }
      double R = glowSpeed * s.age[i] * (1.0 + wow * 0.5);
      if (R < 0.5) continue;
      double fade = LXUtils.clamp((1.0 - s.age[i] / maxAge) * (1.0 + wow), 0, 1.5);
      double cx = s.ox[i], cy = s.oy[i];
      int y0 = (int) Math.max(0, Math.floor(cy - R));
      int y1 = (int) Math.min(h - 1, Math.ceil(cy + R));
      int x0 = (int) Math.floor(cx - R);
      int x1 = (int) Math.ceil(cx + R);
      for (int yy = y0; yy <= y1; ++yy) {
        double dy = yy - cy;
        for (int xx = x0; xx <= x1; ++xx) {
          double dx = xx - cx;
          dx = dx - w * Math.rint(dx / w); // X-wrap distance
          double d = Math.sqrt(dx * dx + dy * dy);
          if (d > R) continue;
          // traveling shock ring: Gaussian shell near the wavefront
          double sigma = Math.max(1.0, 0.18 * R);
          double rd = d - 0.85 * R;
          double ring = Math.exp(-(rd * rd) / (2 * sigma * sigma));
          double b = LXUtils.clamp(ring * fade * fade, 0, 1);
          if (b <= 0) continue;
          // white flash core, only during the pulse's early life
          double core = (s.age[i] < 150) ? LXUtils.clamp(1.0 - d / (R * 0.4), 0, 1) : 0;
          int c = LXColor.lerp(base, LXColor.WHITE, (float) core);
          int xi = ((xx % w) + w) % w;
          int idx = o.point(xi, yy).index;
          this.colors[idx] = LXColor.lightest(this.colors[idx],
            LXColor.scaleBrightness(c, (float) LXUtils.clamp(b, 0, 1)));
        }
      }
    }
  }

  /** A thick vertical bar segment: 1-D horizontal falloff, X-wrapped (corner spill). */
  private void vbar(Apotheneum.Orientation o, int w, int h, double cx, int y,
      double r, double bright, int color) {
    int x0 = (int) Math.floor(cx - r);
    int x1 = (int) Math.ceil(cx + r);
    for (int xx = x0; xx <= x1; ++xx) {
      double u = 1.0 - Math.abs(xx - cx) / r;
      if (u <= 0) continue;
      double f = u * u * bright; // gamma falloff for a tighter profile
      int cc = color;
      if (u > 0.8) {
        cc = LXColor.lerp(color, LXColor.WHITE, (float) ((u - 0.8) / 0.2 * 0.6)); // hot core
      }
      int xi = ((xx % w) + w) % w;
      int idx = o.point(xi, y).index;
      this.colors[idx] = LXColor.lightest(this.colors[idx], LXColor.scaleBrightness(cc, (float) f));
    }
  }
}
