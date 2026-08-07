/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Digits — a giant terminal readout: a handful of huge white-hot characters
 * fill the wall nearly top to bottom, swapping as a group on the beat, over a
 * dim churning field of small blue log-glyphs. On every swap, full-height red
 * and blue strand flares ignite through the digits — the front/back curtain
 * parallax — a red underline bar runs behind the readout, cyan and red debris
 * streaks tangle at the base, and every few seconds the entire room washes
 * hard red for a heartbeat. Modeled on the holotrigger glyph-display session.
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
public class Digits extends StrandPattern {

  // 4×6 block font, hex digits 0-F: 6 rows of 4-bit masks (MSB = left column)
  private static final int[][] FONT = {
    {0b0110,0b1001,0b1001,0b1001,0b1001,0b0110}, // 0
    {0b0010,0b0110,0b0010,0b0010,0b0010,0b0111}, // 1
    {0b0110,0b1001,0b0001,0b0010,0b0100,0b1111}, // 2
    {0b1110,0b0001,0b0110,0b0001,0b1001,0b0110}, // 3
    {0b1001,0b1001,0b1111,0b0001,0b0001,0b0001}, // 4
    {0b1111,0b1000,0b1110,0b0001,0b1001,0b0110}, // 5
    {0b0110,0b1000,0b1110,0b1001,0b1001,0b0110}, // 6
    {0b1111,0b0001,0b0010,0b0010,0b0100,0b0100}, // 7
    {0b0110,0b1001,0b0110,0b1001,0b1001,0b0110}, // 8
    {0b0110,0b1001,0b1001,0b0111,0b0001,0b0110}, // 9
    {0b0110,0b1001,0b1001,0b1111,0b1001,0b1001}, // A
    {0b1110,0b1001,0b1110,0b1001,0b1001,0b1110}, // B
    {0b0110,0b1001,0b1000,0b1000,0b1001,0b0110}, // C
    {0b1110,0b1001,0b1001,0b1001,0b1001,0b1110}, // D
    {0b1111,0b1000,0b1110,0b1000,0b1000,0b1111}, // E
    {0b1111,0b1000,0b1110,0b1000,0b1000,0b1000}, // F
  };
  private static final int MAX_BIG = 16;
  private static final int MAX_BG = 200;
  private static final int MAX_RAIN = 160;
  private static final int MAX_FLARES = 6;
  private static final double SWAP_MS = 420;
  // UFOAbduction velocity signature for drifting digits: fast entry, slow-motion
  // center stall, explosive exit off-canvas
  private static final int MAX_DRIFT = 14;
  private static final double DV_ENTRY = 0.00135;
  private static final double DV_STALL = 0.00024;
  private static final double DV_EXIT = 0.0075;
  private static final double DSTALL_LO = 0.60;
  private static final double DSTALL_HI = 0.44;
  private static final double DEXIT_AT = 0.30;

  public final CompoundParameter glitch =
    new CompoundParameter("Glitch", 0.4, 0, 1)
    .setDescription("How corrupted the readout gets");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Audio sensitivity of re-triggers");

  public final heronarts.lx.parameter.DiscreteParameter drifters =
    new heronarts.lx.parameter.DiscreteParameter("Drifters", 6, 0, MAX_DRIFT)
    .setDescription("Digits drifting vertically with a slow-motion center stall");

  private final int[] bigChars = new int[MAX_BIG];
  private final double[] enterT = new double[MAX_BIG]; // 0..1 slide-in progress
  private final int[] enterDir = new int[MAX_BIG];     // 0 = appear, -1 top, +1 bottom
  private final int[] bgChars = new int[MAX_BG];
  private int glitchSeed = 1;
  private double retrigger = 0;
  private double flash = 0;
  private double floodP = 0;
  private double floodTimer = 0;
  // full-height strand flares ignited on swaps
  private final int[] flareX = new int[MAX_FLARES];
  private final boolean[] flareRed = new boolean[MAX_FLARES];
  private double flareEnv = 0;

  private final class Surface {
    boolean inited;
    // drifting digits riding the UFOAbduction profile
    final double[] dy = new double[MAX_DRIFT];
    final double[] dvf = new double[MAX_DRIFT];  // variance factor
    final int[] dchar = new int[MAX_DRIFT];
    final int[] dx = new int[MAX_DRIFT];
    final boolean[] dup = new boolean[MAX_DRIFT]; // true = rising
    final boolean[] dalive = new boolean[MAX_DRIFT];
    final double[] rx = new double[MAX_RAIN];
    final double[] ry = new double[MAX_RAIN];
    final double[] rv = new double[MAX_RAIN];
    final boolean[] rc = new boolean[MAX_RAIN]; // true = cyan, false = red
    final boolean[] alive = new boolean[MAX_RAIN];
    void reset() {
      for (int i = 0; i < MAX_RAIN; ++i) this.alive[i] = false;
      for (int i = 0; i < MAX_DRIFT; ++i) this.dalive[i] = false;
      this.inited = true;
    }
    void spawnDrifter(int w) {
      for (int i = 0; i < MAX_DRIFT; ++i) {
        if (this.dalive[i]) continue;
        this.dup[i] = Math.random() < 0.5;
        this.dy[i] = this.dup[i] ? 1.05 : -0.05;
        this.dvf[i] = 0.8 + Math.random() * 0.4;
        this.dchar[i] = (int) (Math.random() * 16);
        this.dx[i] = (int) (Math.random() * w);
        this.dalive[i] = true;
        return;
      }
    }
  }

  private final Surface cube = new Surface();
  private final Surface cylinder = new Surface();

  public Digits(LX lx) {
    // Base registers color (hue shift), speed (swap rate), size (digit scale).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("glitch", this.glitch);
    addParameter("sensitivity", this.sensitivity);
    addParameter("drifters", this.drifters);
    addTargetParameter();
    for (int i = 0; i < MAX_BIG; ++i) this.bigChars[i] = (int) (Math.random() * 16);
    for (int i = 0; i < MAX_BG; ++i) this.bgChars[i] = (int) (Math.random() * 16);
  }

  @Override
  protected double beatThreshold() {
    return 1.05 + (1 - this.sensitivity.getValue()) * 0.7;
  }

  /** UFOAbduction's measured profile: brisk entry, center stall, exit burst. */
  private static double driftProfile(double y) {
    if (y > DSTALL_LO) {
      final double u = LXUtils.clamp((y - DSTALL_LO) / 0.10, 0, 1);
      return LXUtils.lerp(DV_STALL, DV_ENTRY, u * u * (3 - 2 * u));
    } else if (y > DSTALL_HI) {
      return DV_STALL;
    }
    final double u = LXUtils.clamp((DSTALL_HI - y) / (DSTALL_HI - DEXIT_AT), 0, 1);
    return LXUtils.lerp(DV_STALL, DV_EXIT, u * u * (3 - 2 * u));
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
    final double wow = getWow();

    // the big readout swaps as a group (~420ms, or on beats)
    this.retrigger += deltaMs * speed * 2 * (1 + wow * 0.6);
    if (this.retrigger >= SWAP_MS || (this.beat && this.beatLevel > 0.35)) {
      this.retrigger = 0;
      for (int i = 0; i < MAX_BIG; ++i) {
        this.bigChars[i] = (int) (Math.random() * 16);
        // some digits just appear; others slide in from the top or bottom
        final double r = Math.random();
        if (r < 0.4) {
          this.enterDir[i] = 0;
          this.enterT[i] = 1;
        } else {
          this.enterDir[i] = (r < 0.7) ? -1 : 1;
          this.enterT[i] = 0;
        }
      }
      this.glitchSeed = (int) (Math.random() * 100000) + 1;
      this.flash = 1;
      // ignite full-height red/blue strand flares through the digits
      this.flareEnv = 1;
      for (int i = 0; i < MAX_FLARES; ++i) {
        this.flareX[i] = (int) (Math.random() * 200);
        this.flareRed[i] = (i & 1) == 0;
      }
    }
    // background log field churns per-cell, fast
    final double p = deltaMs / 140.0;
    for (int i = 0; i < MAX_BG; ++i) {
      if (Math.random() < p) {
        this.bgChars[i] = (int) (Math.random() * 16);
      }
    }
    // rare hard red flood, every ~4-5s
    this.floodTimer += deltaMs;
    if (this.floodTimer >= 4200 + hashd((int) this.timeMs) * 1200) {
      this.floodTimer = 0;
      this.floodP = 1;
    }
    for (int i = 0; i < MAX_BIG; ++i) {
      if (this.enterT[i] < 1) {
        this.enterT[i] = Math.min(1, this.enterT[i] + deltaMs / 350.0);
      }
    }
    this.flash *= Math.exp(-deltaMs / 150.0);
    this.floodP *= Math.exp(-deltaMs / 260.0);
    this.flareEnv *= Math.exp(-deltaMs / 300.0);
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final Surface s = isCube ? this.cube : this.cylinder;
    if (!s.inited) s.reset();
    final int w = o.width();
    final int h = o.height();

    final double wow = getWow();
    final double gl = this.glitch.getValue() + wow * 0.5;
    final double hueShift = LXColor.h(getColor());
    final int blue = LXColor.hsb((float) (((225 + hueShift) % 360)), 95, 100);
    final int white = LXColor.lerp(blue, LXColor.WHITE, 0.90f); // white-hot, faint cool edge
    final int red = LXColor.hsb((float) (((0 + hueShift) % 360) + 360) % 360, 100, 100);
    final int cyan = LXColor.hsb((float) (((187 + hueShift) % 360)), 90, 100);

    // --- background: dim churning field of small blue log-glyphs ---
    final int cols = w / 5;
    final int rows = h / 7;
    for (int cy = 0; cy < rows; ++cy) {
      for (int cxI = 0; cxI < cols; ++cxI) {
        final int gi = (cy * cols + cxI) % MAX_BG;
        final int[] glyph = FONT[this.bgChars[gi]];
        final int gx = cxI * 5;
        final int gy = cy * 7 + 1;
        final double gb = 0.14 + 0.08 * hashd(gi * 31 + (int) (this.timeMs / 200));
        for (int row = 0; row < 6; ++row) {
          final int bits = glyph[row];
          for (int b2 = 0; b2 < 4; ++b2) {
            if ((bits & (0b1000 >> b2)) == 0) continue;
            addPix(o, gx + b2, gy + row, blue, gb);
          }
        }
      }
    }

    // --- red underline bar behind the readout ---
    final int barY = (int) (h * 0.62);
    for (int x = 0; x < w; ++x) {
      addPix(o, x, barY, red, 0.22 + this.flash * 0.3);
    }

    // --- foreground: giant white-hot characters, near-full height ---
    // Size drives the digit scale: small readout by default, giant at full
    final int scaleY = Math.max(1, (int) Math.round(Math.pow(2, getSize() * 2.6)));
    final int scaleX = Math.max(1, scaleY / 3); // tall/narrow ~1:3
    final int bigW = 4 * scaleX + scaleX;       // char cell incl gap
    final int segW = isCube ? 50 : w;
    final int segs = Math.max(1, w / segW);
    final int perSeg = Math.max(1, (segW - 2) / bigW);
    final int y0 = (h - 6 * scaleY) / 2;
    final double bright = 0.95 + this.flash * 0.5;

    for (int seg = 0; seg < segs; ++seg) {
      final int xBase = seg * segW + (segW - perSeg * bigW) / 2;
      for (int ci = 0; ci < perSeg; ++ci) {
        final int gi = (seg * perSeg + ci) % MAX_BIG;
        final int[] glyph = FONT[this.bigChars[gi]];
        final int cx = xBase + ci * bigW;
        // glitch: occasional dropped column or lateral jitter
        final int jit = (hashd(this.glitchSeed + gi * 31) < gl * 0.45)
          ? ((hashd(this.glitchSeed + gi * 77) < 0.5) ? scaleX : -scaleX) : 0;
        final int dropCol = (hashd(this.glitchSeed + gi * 131) < gl * 0.5)
          ? (int) (hashd(this.glitchSeed + gi * 7) * 4) : -1;
        // slide-in entrance: eased offset from off-canvas (0 for pop-ins)
        final double et = this.enterT[gi];
        final double ease = et * et * (3 - 2 * et);
        final int yOff = (int) Math.round(this.enterDir[gi] * (1.0 - ease) * h * 0.85);
        for (int row = 0; row < 6; ++row) {
          final int bits = glyph[row];
          for (int b2 = 0; b2 < 4; ++b2) {
            if ((bits & (0b1000 >> b2)) == 0 || b2 == dropCol) continue;
            for (int sx = 0; sx < scaleX; ++sx) {
              for (int sy = 0; sy < scaleY; ++sy) {
                addPix(o, cx + b2 * scaleX + sx + jit, y0 + row * scaleY + sy + yOff, white, bright);
              }
            }
          }
        }
      }
    }

    // --- full-height red/blue strand flares through the digits on each swap ---
    if (this.flareEnv > 0.03) {
      for (int i = 0; i < MAX_FLARES; ++i) {
        final int fx = this.flareX[i] % w;
        final int fc = this.flareRed[i] ? red : blue;
        for (int y = 0; y < h; ++y) {
          addPix(o, fx, y, fc, this.flareEnv * 0.55);
        }
      }
    }

    // --- drifting digits: rise or fall with the slow-motion center stall ---
    final int nDrift = this.drifters.getValuei();
    int aliveD = 0;
    for (int i = 0; i < MAX_DRIFT; ++i) if (s.dalive[i]) ++aliveD;
    if (aliveD < nDrift && Math.random() < deltaMs * 0.002) {
      s.spawnDrifter(w);
    }
    final double dScaleY2 = Math.max(2, (h - 6) / 6 / 2); // half the readout scale
    final double speedD = Math.max(0.05, getSpeed()) * 2;
    for (int i = 0; i < MAX_DRIFT; ++i) {
      if (!s.dalive[i]) continue;
      // profile is defined for rising travel; mirror it for fallers
      final double yp = s.dup[i] ? s.dy[i] : 1.0 - s.dy[i];
      final double vh = driftProfile(yp) * s.dvf[i] * speedD;
      s.dy[i] += s.dup[i] ? -vh * deltaMs : vh * deltaMs;
      if (s.dy[i] < -0.12 || s.dy[i] > 1.12) { s.dalive[i] = false; continue; }
      // slow center = brighter, whiter (the slow-motion moment reads hot)
      final double stall = 1.0 - LXUtils.clamp(Math.abs(yp - 0.52) / 0.16, 0, 1);
      final int[] glyph = FONT[s.dchar[i]];
      final int gy = (int) Math.round(s.dy[i] * (h - 1)) - (int) (3 * dScaleY2);
      final int sc = (int) dScaleY2;
      final double db = 0.55 + 0.45 * stall;
      final int dc = LXColor.lerp(blue, LXColor.WHITE, (float) (0.5 + 0.4 * stall));
      for (int row = 0; row < 6; ++row) {
        final int bits = glyph[row];
        for (int b2 = 0; b2 < 4; ++b2) {
          if ((bits & (0b1000 >> b2)) == 0) continue;
          for (int sy2 = 0; sy2 < sc; ++sy2) {
            final int yy = gy + row * sc + sy2;
            if (yy < 0 || yy >= h) continue;
            addPix(o, s.dx[i] + b2, yy, dc, db);
          }
        }
      }
    }

    // --- cyan/red debris streaks tangled at the base ---
    final double spawnRate = w * 0.0009 * deltaMs * (1 + this.levelEnv + wow);
    int toSpawn = (int) spawnRate + ((Math.random() < spawnRate % 1) ? 1 : 0);
    for (int i = 0; i < MAX_RAIN && toSpawn > 0; ++i) {
      if (s.alive[i]) continue;
      s.rx[i] = Math.random() * w;
      s.ry[i] = h - 7 - Math.random() * 3;
      s.rv[i] = 0.035 + Math.random() * 0.03;
      s.rc[i] = Math.random() < 0.5;
      s.alive[i] = true;
      --toSpawn;
    }
    for (int i = 0; i < MAX_RAIN; ++i) {
      if (!s.alive[i]) continue;
      s.ry[i] += s.rv[i] * deltaMs;
      if (s.ry[i] >= h + 3) { s.alive[i] = false; continue; }
      comet(o, s.rx[i], s.ry[i], 3.5, s.rc[i] ? cyan : red, 0.8, false);
    }

    // --- the rare hard flood: everything pulled toward red, whites included ---
    if (this.floodP > 0.02) {
      final float amt = (float) (this.floodP * 0.85);
      for (int x = 0; x < w; ++x) {
        for (int y = 0; y < h; ++y) {
          final int idx = o.point(x, y).index;
          this.colors[idx] = LXColor.lerp(this.colors[idx], red, amt);
        }
      }
    }
  }
}
