/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * ComeUp — a glass of water filling up. On the cylinder and/or cube (Target;
 * interior and exterior,
 * two independent glasses out of lockstep) a liquid tide rises bottom-to-top with
 * a crisp, turbulent, foamy waterline. The body below the surface teems with
 * rising bubbles and scintillating sparkles. Each time the glass fills, the flood
 * color rotates to a new hue, so the piece cycles color with every fill.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.utils.Noise;

@LXCategory("Apotheneum/piemonte")
public class ComeUp extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final double RISE_RATE_PER_MS = 1.0 / 6000.0; // full rise in ~6s at speed 1
  private static final double MAX_HOLD_MS = 2500.0;
  private static final double TEMPORAL_HZ = 0.5;
  private static final double EDGE_BAND = 0.02;                // crisp waterline
  private static final double INV_BAND = 1.0 / EDGE_BAND;
  private static final int MAX_BUBBLES = 96;
  private static final double BUBBLE_RISE = 0.00010;           // normalized rise / ms at speed 1
  private static final double HUE_STEP = 43.0;                 // hue advance per fill (deg)
  private static final double INTERIOR_HUE_OFFSET = 137.0;     // interior glass differs

  public final DiscreteParameter detail =
    new DiscreteParameter("Detail", 3, 1, 11)
    .setDescription("Spatial frequency of the surface noise");

  public final CompoundParameter chop =
    new CompoundParameter("Chop", 0.35, 0, 1)
    .setDescription("Turbulent whitewater chop (dreamy roll to full storm)");

  public final CompoundParameter hold =
    new CompoundParameter("Hold", 0.30, 0, 1)
    .setDescription("How long the tide rests at the crest before the next surge");

  public final DiscreteParameter bubbles =
    new DiscreteParameter("Bubbles", 40, 0, MAX_BUBBLES)
    .setDescription("Bubbles rising through each tide");

  public final CompoundParameter sparkle =
    new CompoundParameter("Sparkle", 0.4, 0, 1)
    .setDescription("Sparse scintillating glints suspended in the liquid");

  private enum Phase {
    RISING,
    HOLD
  }

  /** Per-surface animation state, so interior/exterior are fully independent. */
  private static final class Tide {
    double fillLevel;
    double holdElapsedMs;
    double timeMs;
    double currOffsetDeg;   // hue offset of the flood currently rising
    double prevOffsetDeg;   // hue offset of the receding flood above it
    Phase phase = Phase.RISING;

    // bubbles (normalized: bx 0..1 wraps, by 0=bottom..1=top)
    final double[] bx = new double[MAX_BUBBLES];
    final double[] by = new double[MAX_BUBBLES];
    final double[] bsize = new double[MAX_BUBBLES];
    final double[] bspeed = new double[MAX_BUBBLES];
    final double[] bphase = new double[MAX_BUBBLES];
    final boolean[] binit = new boolean[MAX_BUBBLES];

    Tide(double fillLevel, double timeMs, double hueSeedDeg) {
      this.fillLevel = fillLevel;
      this.timeMs = timeMs;
      this.currOffsetDeg = hueSeedDeg;
      this.prevOffsetDeg = hueSeedDeg;
    }

    void advance(double deltaMs, double speed, double holdAmt) {
      this.timeMs += deltaMs;
      if (this.phase == Phase.RISING) {
        this.fillLevel += deltaMs * speed * RISE_RATE_PER_MS;
        if (this.fillLevel >= 1.0) {
          this.fillLevel = 1.0;
          this.holdElapsedMs = 0.0;
          this.phase = Phase.HOLD;
        }
      } else {
        this.holdElapsedMs += deltaMs;
        if (this.holdElapsedMs >= holdAmt * MAX_HOLD_MS) {
          // next glass fills with a fresh hue rising over the previous color
          this.prevOffsetDeg = this.currOffsetDeg;
          this.currOffsetDeg = (this.currOffsetDeg + HUE_STEP) % 360.0;
          this.fillLevel = 0.0;
          this.phase = Phase.RISING;
        }
      }
    }

    private void spawnBubble(int i) {
      this.bx[i] = Math.random();
      this.by[i] = 0;
      this.bsize[i] = 0.6 + Math.random() * 1.2;
      this.bspeed[i] = 0.6 + Math.random() * 0.9;
      this.bphase[i] = Math.random() * Math.PI * 2;
      this.binit[i] = true;
    }

    void updateBubbles(double deltaMs, double speed, int count) {
      for (int i = 0; i < count; ++i) {
        if (!this.binit[i]) {
          spawnBubble(i);
          // stagger initial heights so they don't all start at the floor
          this.by[i] = Math.random() * Math.max(0.01, this.fillLevel);
        }
        this.by[i] += BUBBLE_RISE * speed * this.bspeed[i] * deltaMs;
        this.bx[i] += Math.sin(this.timeMs * 0.002 + this.bphase[i]) * 0.00003 * deltaMs;
        this.bx[i] -= Math.floor(this.bx[i]);
        // pop at the surface, respawn at the floor
        if (this.by[i] >= this.fillLevel || this.by[i] > 1.0) {
          spawnBubble(i);
        }
      }
    }
  }

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures the glass fills");

  private final Tide exterior = new Tide(0.0, 0.0, 0.0);
  // Interior seeded out of lockstep (height + time) and in a different hue.
  private final Tide interior = new Tide(0.4, 4000.0, INTERIOR_HUE_OFFSET);

  public ComeUp(LX lx) {
    // Base registers color (starting hue), speed (rise rate), size (surface amplitude), wow.
    super(lx, 1, 0.1, 4, 0.1, 0, 0.3);
    addParameter("detail", this.detail);
    addParameter("chop", this.chop);
    addParameter("hold", this.hold);
    addParameter("bubbles", this.bubbles);
    addParameter("sparkle", this.sparkle);
    addParameter("target", this.target);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    // power curve: default (1.0) unchanged, but the ends bite — crawl at the
    // bottom of the knob, ~9x fill rate at the top
    final double speed = Math.pow(getSpeed(), 1.6);
    final double holdAmt = this.hold.getValue();
    final int bubbleCount = this.bubbles.getValuei();
    final double sparkleAmt = this.sparkle.getValue();
    final double wow = getWow();
    final int base = getColor();

    final Target t = this.target.getEnum();
    this.exterior.advance(deltaMs, speed, holdAmt);
    this.exterior.updateBubbles(deltaMs, speed, bubbleCount);
    if (Apotheneum.hasInterior) {
      this.interior.advance(deltaMs, speed, holdAmt);
      this.interior.updateBubbles(deltaMs, speed, bubbleCount);
    }

    // one shared fluid state per surface pair: cube and cylinder fill in step
    if (t != Target.CUBE) {
      renderTide(this.exterior, Apotheneum.cylinder.exterior, base, bubbleCount, sparkleAmt, wow);
      if (Apotheneum.hasInterior) {
        renderTide(this.interior, Apotheneum.cylinder.interior, base, bubbleCount, sparkleAmt, wow);
      }
    }
    if (t != Target.CYLINDER) {
      renderTide(this.exterior, Apotheneum.cube.exterior, base, bubbleCount, sparkleAmt, wow);
      if (Apotheneum.hasInterior) {
        renderTide(this.interior, Apotheneum.cube.interior, base, bubbleCount, sparkleAmt, wow);
      }
    }

    // Intentionally no copyExterior(): the two surfaces render independently.
  }

  private void renderTide(Tide tide, Apotheneum.Orientation o, int baseColor,
      int bubbleCount, double sparkleAmt, double wow) {
    // Rotate hue only; keep the palette color's saturation/brightness.
    final float baseHue = LXColor.h(baseColor);
    final float baseSat = LXColor.s(baseColor);
    final float baseBri = LXColor.b(baseColor);
    final int curr = LXColor.hsb((float) ((baseHue + tide.currOffsetDeg) % 360.0), baseSat, baseBri);
    final int prev = LXColor.hsb((float) ((baseHue + tide.prevOffsetDeg) % 360.0), baseSat, baseBri);

    final double amp = getSize();
    final double chopAmt = this.chop.getValue();
    final float freq = Math.max(1, this.detail.getValuei());
    final float t = (float) (tide.timeMs * 0.001 * TEMPORAL_HZ);
    final float driftX = t * 0.3f;
    final float driftZ = t * 0.17f;
    final double level = tide.fillLevel;

    final double foamW = 0.012 + 0.020 * chopAmt;
    final double foamGain = (0.55 + 0.45 * chopAmt) * (1.0 + wow * 0.8);
    final double sTime = tide.timeMs * 0.004;
    final int tq = (int) sTime;
    final double tf = sTime - tq;
    final double twEnv = Math.sin(tf * Math.PI);
    final float glint = (float) (twEnv * twEnv);
    final double sparkThresh = 1.0 - sparkleAmt * (0.05 + wow * 0.08);

    // --- liquid body + churning surface + foam + sparkles ---
    for (Apotheneum.Column column : o.columns()) {
      final LXPoint[] points = column.points;
      final int len = points.length;
      final int last = len - 1;
      for (int j = 0; j < len; ++j) {
        final LXPoint p = points[j];
        final float nx = (p.xn + driftX) * freq;
        final float nz = (p.zn + driftZ) * freq;
        final float base = Noise.stb_perlin_fbm_noise3(nx, nz, t, 2.0f, 0.5f, 3);
        final float chopRaw =
          Noise.stb_perlin_turbulence_noise3(nx * 2.3f, nz * 2.3f, t * 1.7f, 2.2f, 0.5f, 4);
        final float chopN = (chopRaw - 0.5f) * 2.0f;
        final double n = base + chopN * chopAmt * 1.2;

        // Y=0 is the top of the column, so invert the index for a bottom-up fill.
        final double vBottom = (last == 0) ? 0.0 : (double) (last - j) / last;
        final double localLevel = level + n * amp;
        final double d = localLevel - vBottom; // >0 below the surface (in the liquid)
        final double mix = LXUtils.clamp(0.5 + 0.5 * d * INV_BAND, 0, 1);

        int c;
        if (mix <= 0.0) {
          c = prev;
        } else if (mix >= 1.0) {
          c = curr;
        } else {
          c = LXColor.lerp(prev, curr, (float) mix);
        }

        // depth-shade the liquid body: brightest at the waterline, noise-modulated below
        if (d > 0) {
          double glow = 0.62 + 0.23 * LXUtils.clamp(1.0 - d * 2.0, 0, 1) + 0.15 * (base * 0.5 + 0.5);
          c = LXColor.scaleBrightness(c, (float) Math.min(1.0, glow));
        }

        // whitewater foam riding the turbulent surface
        final double fd = Math.abs(d) / foamW;
        if (fd < 1.0) {
          double foam = (1.0 - fd) * LXUtils.clamp((chopRaw - 0.35) * 3.0, 0, 1) * foamGain;
          if (foam > 0) {
            foam = LXUtils.clamp(foam, 0, 1);
            foam = foam * foam * (3 - 2 * foam);
            c = LXColor.lerp(c, LXColor.WHITE, (float) foam);
          }
        }

        // sparse scintillating glints suspended below the surface
        if (sparkleAmt > 0 && d > 0) {
          int hs = p.index * 374761393 + tq * 668265263;
          hs = (hs ^ (hs >>> 13)) * 1274126177;
          double r = ((hs >>> 8) & 0xFFFF) / 65535.0;
          if (r > sparkThresh) {
            c = LXColor.lightest(c, LXColor.scaleBrightness(LXColor.WHITE, glint));
          }
        }

        this.colors[p.index] = c;
      }
    }

    // --- bubbles rising through the liquid ---
    if (bubbleCount > 0 && level > 0.02) {
      final int w = o.width();
      final int h = o.height();
      final int bubbleColor = LXColor.lerp(curr, LXColor.WHITE, 0.7f);
      for (int i = 0; i < bubbleCount; ++i) {
        if (!tide.binit[i] || tide.by[i] >= level) {
          continue; // only visible within the liquid body
        }
        double gx = tide.bx[i] * w;
        double gy = (h - 1) * (1.0 - tide.by[i]); // by=0 bottom -> y=h-1
        // brighter deep down, fading as it nears the surface
        double depth = (level - tide.by[i]) / level;
        double b = 0.35 + 0.65 * LXUtils.clamp(depth * 1.8, 0, 1);
        splat(o, w, h, gx, gy, 1.0 + tide.bsize[i], b, bubbleColor);
      }
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
