/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * ReUp — every vertical string is lit solid. On each bass hit, a random group of
 * strings is knocked dark; they then fade — "re-up" — back to full, while new
 * groups drop out on the next hits. Louder hits knock out wider groups. An
 * audio-reactive curtain that keeps rebuilding itself.
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
public class ReUp extends ParameterPattern {

  private static final double REUP_PER_MS = 0.0016; // fade-back rate at speed 1
  private static final double MIN_BEAT_MS = 110;    // debounce between hits

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Beat sensitivity (higher = triggers more easily)");

  /** Per-surface column brightness (0=dark .. 1=lit), fading back toward 1. */
  private final class Surface {
    int w;
    float[] bright;
    void ensure(int w) {
      if (this.bright == null || this.w != w) {
        this.w = w;
        this.bright = new float[w];
        java.util.Arrays.fill(this.bright, 1f);
      }
    }
    void knockout(int start, int len) {
      for (int k = 0; k < len; ++k) {
        this.bright[((start + k) % this.w + this.w) % this.w] = 0f;
      }
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();

  // beat detection state
  private double bassAvg = 0;
  private double prevBass = 0;
  private double sinceBeatMs = 0;
  private double beatLevel = 0;

  public ReUp(LX lx) {
    // Base registers color, speed, size; speed = re-up rate, size = knockout group size.
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("sensitivity", this.sensitivity);
    addTargetParameter();
  }

  private boolean detectBeat(double deltaMs) {
    double bass = this.level.getValue();
    this.bassAvg += (bass - this.bassAvg) * (1 - Math.exp(-deltaMs / 400.0)); // slow moving average
    this.sinceBeatMs += deltaMs;

    double sens = this.sensitivity.getValue();
    double threshold = 1.05 + (1 - sens) * 0.7;          // sens up -> easier
    boolean beat = pulseHit() || (bass > this.bassAvg * threshold)
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

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    boolean beat = detectBeat(deltaMs);

    // Wow = mayhem: wider knockouts, faster re-up, and hard hits black out
    // the entire curtain in one sweep
    final double wow = getWow();
    final double reup = REUP_PER_MS * Math.max(0.02, getSpeed()) * deltaMs * (1 + wow * 1.2);
    final int base = getColor();
    final Target t = getTarget();

    if (t != Target.CYLINDER) {
      step(this.cube, Apotheneum.cube.exterior, reup, beat, this.beatLevel, base);
    }
    if (t != Target.CUBE) {
      step(this.cylinder, Apotheneum.cylinder.exterior, reup, beat, this.beatLevel, base);
    }
    copyExterior();
  }

  private void step(Surface s, Apotheneum.Orientation o, double reup, boolean beat, double level, int base) {
    final int w = o.width();
    s.ensure(w);

    // fade columns back up
    for (int i = 0; i < w; ++i) {
      if (s.bright[i] < 1f) {
        s.bright[i] = (float) Math.min(1.0, s.bright[i] + reup);
      }
    }
    // on a beat, knock out a random contiguous group (wider with louder hits + Size)
    if (beat) {
      final double wowK = getWow();
      if (wowK > 0.5 && level > 0.7) {
        s.knockout(0, w); // total blackout wave: the whole curtain re-ups at once
      } else {
        double frac = (0.04 + (0.06 + getSize() * 0.30) * level) * (1 + wowK * 1.5);
        int len = Math.max(1, (int) (w * frac));
        int start = (int) (Math.random() * w);
        s.knockout(start, len);
        // extra clusters for a busier feel as Wow rises
        if (Math.random() < 0.5 * level + wowK * 0.4) {
          s.knockout((int) (Math.random() * w), Math.max(1, len / 2));
        }
        if (wowK > 0.3 && Math.random() < wowK * 0.5) {
          s.knockout((int) (Math.random() * w), Math.max(1, len / 2));
        }
      }
    }

    // render solid columns at their current brightness (gamma for perceptual fade)
    for (int x = 0; x < w; ++x) {
      float b = s.bright[x];
      if (b <= 0f) continue;
      float disp = (float) (b * b);
      int c = LXColor.scaleBrightness(base, disp);
      for (LXPoint p : o.column(x).points) {
        this.colors[p.index] = c;
      }
    }
  }
}
