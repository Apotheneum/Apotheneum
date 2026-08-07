/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Drip — standing strands of light hang from the rim to the floor like a
 * beaded curtain in a cathedral dome, each one scintillating downward so the
 * light seems to pour without ever falling. At the base every strand bends
 * into a hook and trails outward along the floor, and a bright fountain
 * cluster glows at the center of each wall. The room moves through scenes —
 * green with a red heart, full green flood, blue beaded sparkle, violet dusk,
 * a gold flood, red matrix, a pale white passage, and near-blackout lulls —
 * some easing in, some snapping. Modeled on the holotrigger rotunda show.
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
public class Drip extends StrandPattern {

  private static final int MAX_STREAMS = 40;
  // scene legs: { perimeterHue, sat, bright, centerHue, holdMs, snap(0/1) }
  private static final double[][] SCENES = {
    { 120, 90, 100,   0,  6000, 0 }, // green columns, red center heart
    { 130, 90, 100, 130,  3500, 1 }, // full green flood (snaps in)
    { 225, 88,  95, 205,  7000, 0 }, // blue beaded sparkle, cyan center
    { 275, 80,  80, 350,  4500, 0 }, // violet dusk, rose center
    {  48, 95, 100,  48,  3500, 1 }, // gold flood
    {   0, 92, 100, 225,  6000, 0 }, // red matrix, blue center
    {   0,  0,  85,   0,  3500, 0 }, // white/lavender passage
    {   0, 60,   8,   0,  2500, 1 }, // near-blackout lull (red embers)
  };
  private static final double XFADE_MS = 1200;
  private static final double SNAP_MS = 180;

  public final DiscreteParameter streams =
    new DiscreteParameter("Streams", 16, 6, MAX_STREAMS)
    .setDescription("Standing strands per surface");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("Audio sensitivity of shimmer surge");

  private double pulse = 0;
  private double sceneClock = 0;
  private int scene = 0;

  public Drip(LX lx) {
    // Base registers color (hue shift), speed (scene pace + shimmer), size (strand weight).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("streams", this.streams);
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
    this.pulse *= Math.exp(-deltaMs / 220.0);
    final double speed = Math.max(0.05, getSpeed());
    this.sceneClock += deltaMs * speed * 2 * (1 + getWow() * 0.5);
    final double[] cur = SCENES[this.scene];
    final double sens = this.sensitivity.getValue();

    // the music drives the style: solid hits advance the scene early, and a
    // hard drop jumps the journey to a random scene — after a minimum dwell
    // so the room never thrashes
    boolean advanceScene = this.sceneClock >= cur[4];
    if (this.beat && this.sceneClock > 1200) {
      if (this.beatLevel > 0.80 - sens * 0.2) {
        // drop: leap somewhere unexpected in the journey
        this.sceneClock = 0;
        int jump = this.scene;
        while (jump == this.scene) {
          jump = (int) (Math.random() * SCENES.length);
        }
        this.scene = jump;
        return;
      } else if (this.beatLevel > 0.45 - sens * 0.15) {
        advanceScene = true; // solid hit: move the journey along now
      }
    }
    if (advanceScene) {
      this.sceneClock = 0;
      this.scene = (this.scene + 1) % SCENES.length;
    }
  }

  /** Eased 0..1 entry progress into the current scene (fast when it snaps). */
  private double sceneIn() {
    final double dur = (SCENES[this.scene][5] > 0.5) ? SNAP_MS : XFADE_MS;
    final double f = LXUtils.clamp(this.sceneClock / dur, 0, 1);
    return f * f * (3 - 2 * f);
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final int w = o.width();
    final int h = o.height();
    final double wow = getWow();
    final double hueShift = LXColor.h(getColor());

    final double[] cur = SCENES[this.scene];
    final double[] prev = SCENES[((this.scene - 1) % SCENES.length + SCENES.length) % SCENES.length];
    final double in = sceneIn();
    final double hue = LXUtils.lerp(prev[0], prev[0] + wrapDeg(cur[0] - prev[0]), in) + hueShift;
    final double sat = LXUtils.lerp(prev[1], cur[1], in);
    final double sceneBright = LXUtils.lerp(prev[2], cur[2], in) / 100.0;
    final double cHue = LXUtils.lerp(prev[3], prev[3] + wrapDeg(cur[3] - prev[3]), in) + hueShift;
    final int colPerim = LXColor.hsb((float) (((hue % 360) + 360) % 360), (float) sat, 100);
    final int colCenter = LXColor.hsb((float) (((cHue % 360) + 360) % 360),
      (float) Math.max(sat, 60), 100);

    // strand population scales with scene brightness: floods fill the wall,
    // blackout lulls collapse to a few dim embers
    final int count = Math.max(2, (int) Math.round(
      Math.min(MAX_STREAMS, this.streams.getValuei() + wow * 8) * (0.25 + 0.75 * sceneBright)));
    final boolean glyphScene = (this.scene == 5 || this.scene == 0); // matrix-textured legs
    final double shimSurge = 1.0 + this.levelEnv * this.sensitivity.getValue() + this.pulse * 0.4;
    final int floorY = h - 6;

    for (int i = 0; i < count; ++i) {
      // fixed even spacing with per-strand jitter: a standing curtain
      final int cx = (int) ((i + 0.5) * w / count + (hashd(i * 31 + 7) - 0.5) * 3);
      // center/perimeter bicolor split: strands near each wall's center take the accent
      final double centerDist = Math.abs(((cx % w) + w) % w % (isCube ? 50 : w)
        - (isCube ? 25 : w / 2)) / (double) (isCube ? 25 : w / 2);
      final int col = (centerDist < 0.25) ? colCenter : colPerim;

      for (int y = 0; y <= floorY; ++y) {
        double b;
        if (glyphScene) {
          // blocky code-rain segments descending
          final int seg = (int) Math.floor((y - this.timeMs * 0.010) / 3.0);
          b = (hashd(cx * 131 + seg * 977) > 0.45) ? 0.85 : 0.08;
        } else {
          // beaded scintillation scrolling down a stationary strand
          final double spark = hashd(cx * 262147 + (int) Math.floor(y - this.timeMs * 0.012) * 8191);
          b = (spark > 0.55) ? 0.55 + 0.45 * spark : 0.12;
        }
        b *= sceneBright * shimSurge * (0.75 + 0.25 * drip(y, h)) * (0.7 + 0.3 * getSize());
        addPix(o, cx, y, col, b);
      }

      // persistent floor hook curling outward
      final int dir = (hashd(i * 53 + 11) < 0.5) ? -1 : 1;
      for (int k = 1; k <= 6; ++k) {
        final double wig = Math.sin(this.timeMs * 0.002 + i * 1.7) * 1.0;
        final int yy = Math.min(h - 1, floorY + (int) Math.round(k * 0.4 + wig * (k / 6.0)));
        addPix(o, cx + dir * k, yy, col, sceneBright * (1.0 - k / 8.0) * 0.8);
      }
    }

    // fountain cluster glowing at the center of each wall
    final int nWalls = isCube ? 4 : 1;
    final int wallW = w / nWalls;
    for (int wi = 0; wi < nWalls; ++wi) {
      final int cx0 = wi * wallW + wallW / 2;
      for (int dx = -3; dx <= 3; ++dx) {
        for (int y = floorY; y < h; ++y) {
          final double g = Math.exp(-dx * dx / 4.0) * (0.55 + this.pulse * 0.3);
          addPix(o, cx0 + dx, y, colCenter, g * sceneBright);
        }
      }
    }

    // waterline scrim: only in the blue scenes, at ~43% height
    if (this.scene == 2) {
      final int scrimY = (int) (h * 0.43);
      final int scrim = LXColor.hsb((float) (((215 + hueShift) % 360)), 80, 100);
      for (int x = 0; x < w; ++x) {
        addPix(o, x, scrimY, scrim, 0.22 * in);
        addPix(o, x, scrimY + 1, scrim, 0.10 * in);
      }
    }
  }

  private static double wrapDeg(double d) {
    d = ((d % 360) + 360) % 360;
    return (d > 180) ? d - 360 : d;
  }
}
