/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Liftoff — on every vertical string a line climbs upward from the floor; at a
 * random height its head bursts in a bright flash, and the drawn tail lingers,
 * slowly fading out like an ember trail. Then that string launches again. Each
 * column runs on its own timing, so the piece crackles like rising fuses.
 * With music playing, the show syncs to it: kicks detonate the fuses that are
 * ready and send the next volley up, and the louder it gets the faster
 * everything climbs. In silence it crackles on its own timing, unchanged.
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
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Liftoff extends ParameterPattern {

  private static final double RISE_PER_MS = 0.0016; // normalized rise / ms at speed 1
  private static final double FLASH_MS = 260;       // burst flash decay

  public final CompoundParameter fade =
    new CompoundParameter("Fade", 0.5, 0, 1)
    .setDescription("How long the tail lingers after the burst");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How easily the music fires bursts and volleys");

  /** Per-column launch state. */
  private final class Surface {
    int w;
    double[] headY;    // current climb height (0 bottom .. 1 top)
    double[] burstH;   // height at which the head bursts
    double[] fade;     // tail brightness after burst (1 -> 0)
    double[] flash;    // burst flash envelope (1 -> 0)
    double[] riseVar;  // per-column rise-rate factor
    boolean[] rising;  // true = climbing, false = fading

    void ensure(int w) {
      if (this.headY != null && this.w == w) {
        return;
      }
      this.w = w;
      this.headY = new double[w];
      this.burstH = new double[w];
      this.fade = new double[w];
      this.flash = new double[w];
      this.riseVar = new double[w];
      this.rising = new boolean[w];
      for (int x = 0; x < w; ++x) {
        launch(x);
        this.headY[x] = Math.random() * this.burstH[x]; // stagger starts
      }
    }

    void launch(int x) {
      this.rising[x] = true;
      this.headY[x] = 0;
      this.burstH[x] = 0.35 + Math.random() * 0.65;
      this.riseVar[x] = 0.6 + Math.random() * 0.8;
      this.fade[x] = 0;
      this.flash[x] = 0;
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();

  // audio: kicks detonate ready fuses; drops launch a multi-rocket volley
  private double bassAvg, prevBass, sinceBeatMs = 1e9;
  private double levelEnv = 0;

  public Liftoff(LX lx) {
    // Base registers color, speed, size; speed = rise rate, size = burst size.
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("fade", this.fade);
    addParameter("sensitivity", this.sensitivity);
    addTargetParameter();
  }

  /** Returns 0 = no beat, 1 = beat, 2 = drop (volley). */
  private int detect(double deltaMs) {
    double bass = this.level.getValue();
    this.bassAvg += (bass - this.bassAvg) * (1 - Math.exp(-deltaMs / 400.0));
    this.sinceBeatMs += deltaMs;
    double sens = this.sensitivity.getValue();
    double threshold = 1.05 + (1 - sens) * 0.7;
    boolean beat = pulseHit() || (bass > this.bassAvg * threshold)
      && (bass > this.prevBass)
      && (this.sinceBeatMs >= 140)
      && (bass > 0.01);
    double ratio = LXUtils.clamp((bass / Math.max(1e-4, this.bassAvg) - 1.0) / 1.5, 0, 1);
    this.prevBass = bass;
    if (beat) {
      this.sinceBeatMs = 0;
    }
    double level = this.level.getValue();
    double alpha = (level > this.levelEnv)
      ? 1 - Math.exp(-deltaMs / 25.0)
      : 1 - Math.exp(-deltaMs / 220.0);
    this.levelEnv += (level - this.levelEnv) * alpha;
    if (!beat) return 0;
    return (ratio > 0.60 - sens * 0.15) ? 2 : 1;
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    final int base = getColor();
    final double speed = Math.max(0.02, getSpeed());
    final int hit = detect(deltaMs);
    // louder music -> faster fuses (silence leaves the resting cadence alone)
    final double rise = RISE_PER_MS * speed * deltaMs * (1.0 + this.levelEnv * 1.2);
    final double fadeMs = LXUtils.lerp(600, 4500, this.fade.getValue());
    final double flashBand = 0.03 + getSize() * 0.12;
    final Target t = getTarget();

    if (t != Target.CYLINDER) {
      step(this.cube, Apotheneum.cube.exterior, rise, deltaMs, fadeMs, flashBand, base, hit);
    }
    if (t != Target.CUBE) {
      step(this.cylinder, Apotheneum.cylinder.exterior, rise, deltaMs, fadeMs, flashBand, base, hit);
    }
    copyExterior();
  }

  private void step(Surface s, Apotheneum.Orientation o, double rise, double deltaMs,
      double fadeMs, double flashBand, int base, int hit) {
    final int w = o.width();
    final int h = o.height();
    s.ensure(w);

    final double wow = getWow();
    if (hit == 1) {
      // kick: detonate fuses that are most of the way up, right on the beat
      for (int x = 0; x < w; ++x) {
        if (s.rising[x] && s.headY[x] > 0.45 * s.burstH[x]
            && Math.random() < 0.45 + wow * 0.3) {
          s.headY[x] = s.burstH[x];
        }
      }
    } else if (hit == 2) {
      // the drop: multi-rocket volley — detonate everything ready AND mass-launch
      for (int x = 0; x < w; ++x) {
        if (s.rising[x] && s.headY[x] > 0.3 * s.burstH[x]) {
          s.headY[x] = s.burstH[x]; // cascade of bursts on the drop
        } else if (!s.rising[x] && Math.random() < 0.65 + wow * 0.3) {
          s.launch(x); // fresh rockets rise together as the volley
          s.burstH[x] = 0.55 + Math.random() * 0.45; // volley flies high
          s.riseVar[x] = 1.1 + Math.random() * 0.5;  // and fast
        }
      }
    }

    // advance each column's state machine
    for (int x = 0; x < w; ++x) {
      if (s.rising[x]) {
        // fuses accelerate as they climb
        s.headY[x] += rise * s.riseVar[x] * (0.6 + 0.8 * s.headY[x]);
        if (s.headY[x] >= s.burstH[x]) {
          s.headY[x] = s.burstH[x];
          s.rising[x] = false;
          s.fade[x] = 1;
          s.flash[x] = 1;
        }
      } else {
        s.fade[x] *= Math.exp(-deltaMs / (0.5 * fadeMs));
        s.flash[x] -= deltaMs / FLASH_MS;
        if (s.fade[x] < 0.01) {
          s.launch(x);
        }
      }
    }

    // render columns
    for (int x = 0; x < w; ++x) {
      final LXPoint[] points = o.column(x).points;
      final int last = points.length - 1;
      final boolean rising = s.rising[x];
      final double headY = s.headY[x];
      final double burstH = s.burstH[x];
      final double fade = s.fade[x];
      final double flash = s.flash[x];
      for (int j = 0; j < points.length; ++j) {
        final double vB = (last == 0) ? 0.0 : (double) (last - j) / last;
        int c;
        if (rising) {
          // white-hot tip decaying down the drawn tail
          final double d = headY - vB;
          if (d >= 0) {
            double b = 0.25 + 0.75 * Math.exp(-d * 8.0);
            c = LXColor.scaleBrightness(base, (float) b);
            if (d < 0.04) {
              c = LXColor.lerp(c, LXColor.WHITE, (float) (0.8 * (1.0 - d / 0.04)));
            }
          } else {
            c = LXColor.BLACK;
          }
        } else {
          c = (vB <= burstH) ? LXColor.scaleBrightness(base, (float) LXUtils.clamp(fade, 0, 1)) : LXColor.BLACK;
          if (flash > 0) {
            double d = Math.abs(vB - burstH);
            if (d < flashBand) {
              double fb = flash * Math.pow(1.0 - d / flashBand, 2);
              c = LXColor.lightest(c, LXColor.scaleBrightness(LXColor.WHITE, (float) fb));
            }
          }
        }
        this.colors[points[j].index] = c;
      }
    }
  }
}
