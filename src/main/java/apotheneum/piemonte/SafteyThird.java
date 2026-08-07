/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * SafteyThird — a bold dead smiley across the whole piece, in the full-facade spirit
 * of Ex Machina. One huge grinning face per cube facade (two around the
 * cylinder): a bright disc textured with slow Ex Machina-style radial
 * shimmer bands, X X eyes cut dark into the glow, and a wide smile arc.
 * The face tilts gently side to side and pulses on beats. Wow sends it
 * over the edge: the X eyes flash hot red, the face jitters, and hard
 * strobe frames drop out of the disc. Hue steers the face color live;
 * Squiggle waves the smile into a wobbling scrawl and Rotate spins the
 * whole face.
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
public class SafteyThird extends StrandPattern {

  // face geometry in normalized disc coordinates (r = 1 at the rim)
  private static final double EYE_X = 0.38, EYE_Y = -0.26;
  private static final double EYE_R = 0.17;    // reach of each X arm
  private static final double EYE_W = 0.075;   // X stroke half-width
  private static final double MOUTH_CY = 0.02; // smile arc circle center (y)
  private static final double MOUTH_R = 0.55;  // smile arc radius
  private static final double MOUTH_W = 0.07;  // smile stroke half-width
  private static final double MOUTH_MIN_Y = 0.30; // only the lower arc shows

  public final CompoundParameter bands =
    new CompoundParameter("Bands", 2.2, 0.5, 6.0)
    .setDescription("Radial shimmer band frequency inside the face");

  public final CompoundParameter squiggle =
    new CompoundParameter("Squiggle", 0, 0, 1)
    .setDescription("Waves the smile into an animated squiggle");

  public final CompoundParameter rotate =
    new CompoundParameter("Rotate", 0, -60, 60)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Degrees/second the whole face spins; negative reverses");

  private double shimmer = 0;
  private double tilt = 0;
  private double spin = 0;
  private double squigPhase = 0;
  private double kick = 0;

  public SafteyThird(LX lx) {
    // Base registers color (face color), speed (shimmer/tilt pace), size (face radius).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("bands", this.bands);
    addParameter("squiggle", this.squiggle);
    addParameter("rotate", this.rotate);
    addTargetParameter();
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
    this.shimmer += deltaMs * 0.0005 * speed * 2;
    this.tilt = 0.10 * Math.sin(this.timeMs * 0.0009 * speed * 2);
    this.spin += deltaMs * 0.001 * Math.toRadians(this.rotate.getValue());
    this.spin %= (2 * Math.PI);
    this.squigPhase += deltaMs * 0.006 * speed * 2;
    if (this.beat) {
      this.kick = 1;
    }
    this.kick *= Math.exp(-deltaMs / 280.0);
  }

  @Override
  protected void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube) {
    final int w = o.width();
    final int h = o.height();
    final double wow = getWow();
    final double bandF = this.bands.getValue();
    final int tq = (int) (this.timeMs / 70);

    final int face = getColor();
    final int rimC = LXColor.scaleBrightness(face, 0.72f);
    final int redX = LXColor.hsb(2, 98, 100);

    // Wow chaos: eye flashes, face jitter, strobe dropout frames
    final boolean eyesHot = wow > 0.15 && ((tq / 3) % 4 == 0 || wow > 0.8);
    final int jitter = (wow > 0.4 && hashd(tq * 7) < wow * 0.5)
      ? (int) ((hashd(tq * 13) - 0.5) * wow * 6) : 0;
    final boolean dropFrame = wow > 0.6 && hashd(tq * 31 + 5) < (wow - 0.6) * 0.6;
    if (dropFrame) {
      return; // hard strobe blackout frame
    }

    final int nFaces = isCube ? 4 : 2; // one per cube facade, two on the cylinder
    final int half = w / (2 * nFaces);
    final double R = Math.min(half, h * 0.5) * (0.62 + getSize() * 0.55)
      * (1 + this.kick * 0.06);
    final double cy = h * 0.48;
    final double ang = this.tilt + this.spin;
    final double cosT = Math.cos(ang), sinT = Math.sin(ang);
    final double squig = this.squiggle.getValue();

    for (int f = 0; f < nFaces; ++f) {
      final int cx = (int) ((f + 0.5) * w / (double) nFaces) + jitter;
      for (int dx = -half; dx < half; ++dx) {
        final int x = ((cx + dx) % w + w) % w;
        for (int y = 0; y < h; ++y) {
          // head-tilt rotation into normalized face coordinates
          final double rx = (dx * cosT - (y - cy) * sinT) / R;
          final double ry = (dx * sinT + (y - cy) * cosT) / R;
          final double r = Math.sqrt(rx * rx + ry * ry);
          if (r > 1.03) continue;
          final double edge = LXUtils.clamp((1.0 - r) * R + 1.0, 0, 1);

          // X X eyes: diagonal cross strokes, mirrored left/right
          final double eu = Math.abs(Math.abs(rx) - EYE_X);
          final double ev = ry - EYE_Y;
          final boolean inEyeBox = eu < EYE_R && Math.abs(ev) < EYE_R;
          final boolean inX = inEyeBox
            && Math.abs(eu - Math.abs(ev)) < EYE_W;

          // the smile: lower arc of an offset circle; Squiggle waves it
          final double rm = Math.sqrt(rx * rx + (ry - MOUTH_CY) * (ry - MOUTH_CY));
          double mouthR = MOUTH_R;
          if (squig > 0) {
            final double th = Math.atan2(rx, ry - MOUTH_CY);
            mouthR += squig * 0.11
              * Math.sin(th * 9 + this.squigPhase);
          }
          final boolean inMouth = ry > MOUTH_MIN_Y
            && Math.abs(rm - mouthR) < MOUTH_W;

          if (inX) {
            if (eyesHot) { // Wow: the X eyes burn red
              addPix(o, x, y, redX, edge);
            }
            continue; // otherwise cut dark into the glow
          }
          if (inMouth) {
            continue; // the smile stays dark
          }

          // the disc: Ex Machina-style radial shimmer bands
          final double wave = 0.5 + 0.5
            * Math.cos((r * bandF - this.shimmer) * 2 * Math.PI);
          final double bb = (0.55 + 0.45 * Math.pow(wave, 1.6))
            * (0.92 + 0.08 * hashd(x * 131 + y * 7 + tq))
            * (1 + this.kick * 0.20);
          addPix(o, x, y, (r > 0.92) ? rimC : face, Math.min(1, bb * edge));
        }
      }
    }
  }
}
