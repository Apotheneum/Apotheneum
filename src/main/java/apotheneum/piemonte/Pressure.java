/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Pressure — a two-channel color organ on the strand curtains: the columns
 * gate hard between near-black and saturated washes, each column committed to
 * either red or green, identities re-rolled with every major pulse. Fast
 * attack, slow release — the whole rig breathes dark-bright-dark like a
 * warehouse PA. Where red columns overdrive they bleed amber, and everything
 * pools on the floor line. Modeled on the holotrigger red/green pulse program.
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
public class Pressure extends StrandPattern {

  private static final int MAX_GROUPS = 32;

  public final DiscreteParameter columns =
    new DiscreteParameter("Columns", 7, 3, 16)
    .setDescription("Curtain panels around the surface");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Audio sensitivity of the gate");

  // per-group state, shared across surfaces so cube and cylinder pulse together
  private final double[] energy = new double[MAX_GROUPS];
  private final boolean[] isRed = new boolean[MAX_GROUPS];
  private final boolean[] firing = new boolean[MAX_GROUPS];
  private int beatCount = 0;
  private int lastIdlePulse = -1;
  private double rotPhase = 0; // slow orbit of the curtain wall around the gravity axis
  private boolean inited = false;

  public Pressure(LX lx) {
    // Base registers color (hue shift), speed (idle breathe tempo), size (unused softness).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("columns", this.columns);
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
    if (!this.inited) {
      for (int i = 0; i < MAX_GROUPS; ++i) {
        this.isRed[i] = hashd(i * 17 + 3) < 0.4;
        this.firing[i] = true;
        this.energy[i] = 0.4;
      }
      this.inited = true;
    }

    final double wow = getWow();
    // the whole curtain wall slowly orbits the vertical axis; Wow spins it up,
    // and each hit gives the drum a small extra shove
    final double speedR = Math.max(0.05, getSpeed());
    this.rotPhase += deltaMs * speedR * 2 * 0.00003 * (1 + wow * 2);
    if (this.beat) {
      this.rotPhase += 0.004 * this.beatLevel;
    }
    this.rotPhase -= Math.floor(this.rotPhase);
    if (this.beat) {
      ++this.beatCount;
      // green is home; a red minority coexists, re-rolled per pulse; rare all-red wall
      double redFrac = 0.25 + this.beatLevel * 0.15 + wow * 0.1;
      if (hashd(this.beatCount * 13 + 5) < 0.12) {
        redFrac = 0.85;
      }
      for (int i = 0; i < MAX_GROUPS; ++i) {
        this.isRed[i] = hashd(this.beatCount * 977 + i * 31) < redFrac;
        this.firing[i] = true; // the gate is temporal (whole field), not spatial
      }
    }

    // idle gate in silence: hard on/off square at the reference's ~0.7s cadence
    final double speed = Math.max(0.05, getSpeed());
    final boolean quiet = this.levelEnv < 0.05;
    final double idleCycle = this.timeMs * speed * 2 / 700.0;
    final int idlePulse = (int) idleCycle;
    if (quiet && idlePulse != this.lastIdlePulse) {
      this.lastIdlePulse = idlePulse;
      for (int i = 0; i < MAX_GROUPS; ++i) {
        this.isRed[i] = hashd(idlePulse * 977 + i * 31) < 0.30;
        this.firing[i] = true;
      }
    }
    final double idleGate = ((idleCycle - idlePulse) < 0.55) ? 0.95 : 0.04;

    // whole-field gate: fast attack, ~250ms release into a held near-black trough
    final double aAtk = 1 - Math.exp(-deltaMs / 30.0);
    final double target = quiet
      ? idleGate
      : LXUtils.clamp(0.20 + this.levelEnv * 1.0 + this.beatLevel * 0.5 + wow * 0.2, 0, 1.3);
    for (int i = 0; i < MAX_GROUPS; ++i) {
      // tiny per-curtain release offset so transitions show a lagging curtain or two
      final double aRel = 1 - Math.exp(-deltaMs / ((270.0 - wow * 90) * (1.0 + 0.15 * hashd(i * 7))));
      double a = (target > this.energy[i]) ? aAtk : aRel;
      this.energy[i] += (target - this.energy[i]) * a;
    }
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final int w = o.width();
    final int h = o.height();
    final int n = this.columns.getValuei();
    final double hueShift = LXColor.h(getColor());
    final int green = LXColor.hsb((float) (((112 + hueShift) % 360)), 98, 100);
    final int yellow = LXColor.hsb((float) (((78 + hueShift) % 360)), 95, 100);
    final int red = LXColor.hsb((float) (((2 + hueShift) % 360)), 100, 100);
    final int amber = LXColor.hsb((float) (((30 + hueShift) % 360)), 92, 100);
    final int ember = LXColor.hsb((float) (((24 + hueShift) % 360)), 90, 100);
    final int tq = (int) (this.timeMs / 80);

    for (int x = 0; x < w; ++x) {
      // panel coordinates rotate around the gravity axis
      final double xr = (((double) x / w + this.rotPhase) % 1.0) * n;
      final int g = Math.min(n - 1, (int) xr);
      final double e = this.energy[g];
      // dark wall gap between discrete curtain panels
      final double gpos = xr - g;
      if (gpos < 0.10 || gpos > 0.90) continue;
      if (e > 0.01) {
        final double shim = 0.90 + 0.10 * hashd(x * 131 + tq * 7);
        int c = this.isRed[g] ? red : green;
        // overdrive bleeds: red toward amber, green toward yellow-green
        if (e > 0.85) {
          final float od = (float) LXUtils.clamp((e - 0.85) * 4, 0, 1);
          c = this.isRed[g] ? LXColor.lerp(red, amber, od) : LXColor.lerp(green, yellow, od);
        }
        final int hot = LXColor.lerp(c, LXColor.WHITE, 0.35f);
        for (int y = 0; y < h; ++y) {
          double b = e * drip(y, h) * shim;
          b += e * pool(y, h);
          if (b <= 0.01) continue;
          // the hottest point is the floor, not the truss
          addPix(o, x, y, (e > 1.0 && y >= h - 3) ? hot : c, b);
        }
      }
    }

    // persistent warm floor ember: alive even in the blackest troughs
    for (int x = 0; x < w; ++x) {
      final double fb = 0.14 * (0.7 + 0.3 * hashd(x * 37 + tq));
      addPix(o, x, h - 1, ember, fb);
      addPix(o, x, h - 2, ember, fb * 0.45);
    }
  }
}
