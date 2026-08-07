/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Overflow — a top-lit strand screen: a segmented band of equalizer blocks
 * hangs in the upper-middle of the curtains, each block its own bar of red or
 * green, reconfiguring like letterforms every few hundred milliseconds. The
 * field swings between green-dominant and red-dominant, pulses with the beat,
 * floods the whole room green on drops, and occasionally sends a full-height
 * red strand running down into the glowing floor pools coiled at the base.
 * Modeled on the holotrigger green/red cascade program.
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
public class Overflow extends StrandPattern {

  private static final int MAX_RUNS = 10;
  private static final double GREEN_HUE = 130;
  private static final double RED_HUE = 12;

  public final DiscreteParameter drips =
    new DiscreteParameter("Drips", 4, 0, MAX_RUNS)
    .setDescription("Full-height red strand runs at once");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Audio sensitivity of the pulse and flood");

  private final class Surface {
    boolean inited;
    // full-height red runs feeding floor pools
    final int[] rx = new int[MAX_RUNS];
    final double[] rlife = new double[MAX_RUNS];
    final boolean[] alive = new boolean[MAX_RUNS];
    void reset() {
      for (int i = 0; i < MAX_RUNS; ++i) this.alive[i] = false;
      this.inited = true;
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();
  private double flashEnv = 0;
  private double beatPulse = 0;
  private double lfo = 0;

  public Overflow(LX lx) {
    // Base registers color (hue shift), speed (block reconfigure tempo), size (band height).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("drips", this.drips);
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
    final double speed = Math.max(0.05, getSpeed());
    this.lfo += deltaMs * 0.0004 * speed; // red/green dominance swing ~1-2s
    if (this.beat) {
      this.beatPulse = 1;
      if (this.beatLevel > 0.5 || this.levelEnv > 0.55) {
        this.flashEnv = 1; // the drop: whole room floods green
      }
    }
    this.beatPulse *= Math.exp(-deltaMs / 150.0);
    this.flashEnv *= Math.exp(-deltaMs / 700.0); // sustained, ambient
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final Surface s = isCube ? this.cube : this.cylinder;
    if (!s.inited) s.reset();

    final int w = o.width();
    final int h = o.height();
    final double wow = getWow();
    final double speed = Math.max(0.05, getSpeed());
    final double hueShift = LXColor.h(getColor());
    final int green = LXColor.hsb((float) (((GREEN_HUE + hueShift) % 360)), 95, 100);
    final int red = LXColor.hsb((float) (((RED_HUE + hueShift) % 360)), 98, 100);
    final int tq = (int) (this.timeMs / 90);

    // audio-grown band anchored in the upper-middle; the floor stays dark
    final double env = LXUtils.clamp(
      Math.max(this.levelEnv * (1.2 + wow * 0.5), 0.35) + this.beatLevel * 0.15, 0, 1);
    final double bandCenter = h * 0.30;
    final double bandH = h * (0.10 + (0.14 + getSize() * 0.24) * env);
    final double pulse = 0.55 + 0.45 * this.beatPulse;
    // whole-field red<->green dominance swing; mid-swing = checkerboard
    final double redBias = 0.5 + 0.45 * Math.sin(2 * Math.PI * this.lfo * 0.5);

    final int blockW = Math.max(3, w / 25);
    final int blockQ = (int) (this.timeMs * speed / 420); // blocks reconfigure ~2.4/s
    for (int x = 0; x < w; ++x) {
      final int bx = x / blockW;
      final boolean isRed = hashd(bx * 131 + blockQ * 977) < redBias;
      // per-block equalizer heights, top and bottom independently jittered
      final double hUp = bandH * (0.35 + 0.65 * hashd(bx * 7 + blockQ * 53));
      final double hDn = bandH * (0.35 + 0.65 * hashd(bx * 11 + blockQ * 29));
      final int c = isRed ? red : green;
      final double shim = 0.88 + 0.12 * hashd(x * 131 + tq * 7);
      final int y0 = Math.max(0, (int) (bandCenter - hUp));
      final int y1 = Math.min(h - 1, (int) (bandCenter + hDn));
      for (int y = y0; y <= y1; ++y) {
        final double dy = (y < bandCenter)
          ? 1.0 - (bandCenter - y) / Math.max(1.0, hUp)
          : 1.0 - (y - bandCenter) / Math.max(1.0, hDn);
        final double b = (0.30 + 0.70 * dy * dy) * shim * pulse;
        addPix(o, x, y, c, b);
      }
    }

    // full-height red strand runs feeding floor pools
    final int maxRuns = this.drips.getValuei();
    int alive = 0;
    for (int i = 0; i < MAX_RUNS; ++i) if (s.alive[i]) ++alive;
    if (alive < maxRuns && Math.random() < deltaMs * (0.0008 + this.beatLevel * 0.004)) {
      for (int i = 0; i < MAX_RUNS; ++i) {
        if (!s.alive[i]) {
          s.rx[i] = (int) (Math.random() * w);
          s.rlife[i] = 900 + Math.random() * 1200;
          s.alive[i] = true;
          break;
        }
      }
    }
    for (int i = 0; i < MAX_RUNS; ++i) {
      if (!s.alive[i]) continue;
      s.rlife[i] -= deltaMs;
      if (s.rlife[i] <= 0) { s.alive[i] = false; continue; }
      final double rb = Math.min(1, s.rlife[i] / 300.0); // fade out at the end
      for (int y = (int) bandCenter; y < h; ++y) {
        final double tw2 = 0.7 + 0.3 * hashd(s.rx[i] * 31 + y * 7 + tq);
        addPix(o, s.rx[i], y, red, rb * tw2 * 0.85);
      }
      // bright pool where the run meets the floor
      for (int dx = -2; dx <= 2; ++dx) {
        addPix(o, s.rx[i] + dx, h - 1, red, rb * (0.8 - 0.2 * Math.abs(dx)));
        addPix(o, s.rx[i] + dx, h - 2, red, rb * (0.5 - 0.12 * Math.abs(dx)));
      }
    }

    // big coiled floor pools, colored by the block above them
    for (int p = 0; p < 6; ++p) {
      final double dir = (p % 2 == 0) ? 1 : -1;
      final double px = (hashd(p * 97 + 5) * w + dir * this.timeMs * 0.002) % w;
      final int bx = (((int) px % w) + w) % w / blockW;
      final int pc = (hashd(bx * 131 + blockQ * 977) < redBias) ? red : green;
      for (int dx = -8; dx <= 8; ++dx) {
        final double g = Math.exp(-dx * dx / 18.0);
        addPix(o, (int) px + dx, h - 1, pc, g * 0.65);
        addPix(o, (int) px + dx, h - 2, pc, g * 0.40);
        addPix(o, (int) px + dx, h - 3, pc, g * 0.18);
      }
    }

    // the drop: sustained ambient green flood filling even the dark rows
    if (this.flashEnv > 0.01) {
      final int fc = LXColor.scaleBrightness(green,
        (float) (this.flashEnv * (0.55 + wow * 0.35)));
      for (int x = 0; x < w; ++x) {
        for (int y = 0; y < h; ++y) {
          final int idx = o.point(x, y).index;
          this.colors[idx] = LXColor.lightest(this.colors[idx], fc);
        }
      }
    }
  }
}
