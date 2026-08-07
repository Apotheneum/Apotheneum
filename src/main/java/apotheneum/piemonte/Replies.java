/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Replies — glowing dots glide along the cylinder's rings or columns, each
 * trailing a soft afterglow. When two dots collide they burst — and the cube
 * answers: the face looking toward the collision flickers in reply, the outer
 * chamber echoing the inner one. Wow chains the bursts into cascades that
 * ripple around the cylinder and set multiple faces talking at once.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LinkedColorParameter;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Replies extends ParameterPattern {

  public enum Axis {
    RINGS,
    COLUMNS
  }

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final double SPEED_SCALE = 0.0006; // loop fraction per ms at speed 1
  private static final double EPS = 0.012;          // collision proximity (loop fraction)
  private static final double COOLDOWN_MS = 220;    // per-sequence burst debounce
  private static final double FLASH_MS = 300;       // interior burst-flash decay

  public final EnumParameter<Axis> axis =
    new EnumParameter<Axis>("Axis", Axis.RINGS)
    .setDescription("Whether dots travel along rings (around) or columns (vertical)");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("BOTH = full call-and-response; CYLINDER = dots only; CUBE = replies only");

  public final DiscreteParameter density =
    new DiscreteParameter("Density", 3, 2, 9)
    .setDescription("Dots per sequence (need 2+ to collide)");

  public final CompoundParameter tail =
    new CompoundParameter("Tail", 0.5, 0.05, 1)
    .setDescription("Afterglow length behind each dot");

  public final CompoundParameter reply =
    new CompoundParameter("Reply", 0.5, 0, 1)
    .setDescription("How long the cube's flicker lingers after a burst");

  // Inherited `color` (from ParameterPattern) is the interior dot color.
  public final LinkedColorParameter exteriorColor =
    new LinkedColorParameter("Outer")
    .setDescription("Cube flicker color (the reply)");

  /** Per-structure dot simulation + exterior reply state. */
  private final class Surface {
    boolean rings;
    int dotsPer;
    int numSeq;
    double[] pos;       // numSeq * dotsPer, in [0,1)
    double[] vel;       // numSeq * dotsPer, signed unit-ish factor
    double[] cooldown;  // per sequence (ms)
    boolean[] wasHit;   // per sequence: overlapped last frame (edge trigger)
    double[] flashEnv;  // per sequence (0..1)
    double[] flashPos;  // per sequence, in [0,1)
    double energy;      // exterior reply energy (0..1)
    // smoothed flicker: hold a random target, ease toward it (no white-noise strobe)
    double flickerLp = 1;
    double flickerTarget = 1;
    double flickerMs = 0;

    boolean matches(boolean rings, int dotsPer, int numSeq) {
      return this.pos != null && this.rings == rings
        && this.dotsPer == dotsPer && this.numSeq == numSeq;
    }

    void init(boolean rings, int dotsPer, int numSeq) {
      this.rings = rings;
      this.dotsPer = dotsPer;
      this.numSeq = numSeq;
      this.pos = new double[numSeq * dotsPer];
      this.vel = new double[numSeq * dotsPer];
      this.cooldown = new double[numSeq];
      this.wasHit = new boolean[numSeq];
      this.flashEnv = new double[numSeq];
      this.flashPos = new double[numSeq];
      this.energy = 0;
      for (int i = 0; i < this.pos.length; ++i) {
        this.pos[i] = Math.random();
        double mag = 0.4 + 0.6 * Math.random();
        this.vel[i] = (Math.random() < 0.5 ? -mag : mag);
      }
    }
  }

  private final Surface cylinder = new Surface();
  private final double[] faceEnergy = new double[4]; // per cube face reply

  public Replies(LX lx) {
    // Base registers color (interior dots), speed, size; we add the rest.
    super(lx, 0.4, -1, 1, 0.5, 0, 1);
    addParameter("exteriorColor", this.exteriorColor);
    addParameter("axis", this.axis);
    addParameter("target", this.target);
    addParameter("density", this.density);
    addParameter("tail", this.tail);
    addParameter("reply", this.reply);
    // setMode after addParameter: the parameter needs its parent/LX set first.
    this.exteriorColor.setMode(LinkedColorParameter.Mode.PALETTE);
  }

  private static double frac(double x) {
    return x - Math.floor(x);
  }

  private static double wrapDelta(double d) {
    return d - Math.rint(d); // nearest, → [-0.5, 0.5]
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    final boolean rings = this.axis.getEnum() == Axis.RINGS;
    final int dotsPer = this.density.getValuei();
    final Target t = this.target.getEnum();
    final int dotBase = getColor();
    final int replyBase = this.exteriorColor.calcColor();

    // the show lives on the cylinder; the cube answers
    step(this.cylinder, Apotheneum.cylinder.exterior, rings, dotsPer, deltaMs,
      dotBase, replyBase, t);
    copyCylinderExterior();
  }

  private void step(Surface s, Apotheneum.Orientation cyl,
      boolean rings, int dotsPer, double deltaMs, int interiorBase, int exteriorBase,
      Target t) {

    final Apotheneum.Sequence[] sequences = rings ? cyl.rings() : cyl.columns();
    final int numSeq = sequences.length;
    if (!s.matches(rings, dotsPer, numSeq)) {
      s.init(rings, dotsPer, numSeq);
    }

    final double speed = getSpeed();
    final double step = speed * SPEED_SCALE * deltaMs;
    // Widen the collision window so fast dots can't tunnel past each other.
    final double eps = Math.max(EPS, 1.5 * Math.abs(step));
    final double gap = 1.0 / dotsPer;
    final double tailLen = this.tail.getValue() * gap;
    final double gamma = LXUtils.lerp(3.0, 0.5, getSize());
    // Wow = chain reactions: bursts flare wider and echo across the surface
    final double wow = getWow();
    final double flashRadius = Math.min(0.1 + wow * 0.15, (1.5 + wow * 2.5) * gap);

    // --- advance + collide ---
    int bursts = 0;
    final boolean drawDots = (t != Target.CUBE);
    final boolean doReply = (t != Target.CYLINDER);
    for (int q = 0; q < numSeq; ++q) {
      final int o = q * dotsPer;
      for (int k = 0; k < dotsPer; ++k) {
        s.pos[o + k] = frac(s.pos[o + k] + s.vel[o + k] * step);
      }
      s.cooldown[q] = Math.max(0, s.cooldown[q] - deltaMs);
      s.flashEnv[q] = Math.max(0, s.flashEnv[q] - deltaMs / FLASH_MS);

      boolean hit = false;
      double hitPos = 0;
      for (int a = 0; a < dotsPer && !hit; ++a) {
        for (int b = a + 1; b < dotsPer; ++b) {
          if (Math.abs(wrapDelta(s.pos[o + a] - s.pos[o + b])) < eps) {
            hit = true;
            hitPos = s.pos[o + a];
            break;
          }
        }
      }
      // fire on the transition into overlap only — sustained proximity
      // (e.g. two dots parked together at Speed 0) must not refire forever
      final boolean entered = hit && !s.wasHit[q];
      s.wasHit[q] = hit;
      if (entered && s.cooldown[q] <= 0) {
        s.cooldown[q] = COOLDOWN_MS;
        s.flashEnv[q] = 1.0;
        s.flashPos[q] = hitPos;
        // the cube face looking toward this collision answers
        final double azimuth = rings ? hitPos : ((double) q / numSeq);
        this.faceEnergy[((int) Math.floor(azimuth * 4 + 0.5)) % 4] = 1.0;
        // echo: a burst ripples into a neighboring sequence at high Wow
        if (wow > 0.25 && Math.random() < wow * 0.7) {
          final int q2 = (q + 1 + (int) (Math.random() * 3)) % s.flashEnv.length;
          s.flashEnv[q2] = Math.max(s.flashEnv[q2], 0.7);
          s.flashPos[q2] = Math.random();
        }
        ++bursts;
      }
    }

    // --- reply energy decays per cube face ---
    final double decayMs = LXUtils.lerp(150, 1400, this.reply.getValue());
    for (int fi = 0; fi < 4; ++fi) {
      this.faceEnergy[fi] *= Math.exp(-deltaMs / (0.5 * decayMs));
      if (this.faceEnergy[fi] < 0.01) {
        this.faceEnergy[fi] = 0;
      }
    }

    // actual travel direction carries speed's sign too, not just vel's
    final double dir = (step < 0) ? -1 : 1;

    // --- draw cylinder dots + burst flashes ---
    if (drawDots)
    for (int q = 0; q < numSeq; ++q) {
      final int o = q * dotsPer;
      final LXPoint[] points = sequences[q].points;
      final int len = points.length;
      final double flEnv = s.flashEnv[q];
      final double flPos = s.flashPos[q];
      for (int j = 0; j < len; ++j) {
        final double pj = (double) j / len;
        double dotBright = 0;
        for (int k = 0; k < dotsPer; ++k) {
          final double rel = wrapDelta(pj - s.pos[o + k]);
          // tail trails behind the dot's direction of travel
          final double g = (s.vel[o + k] * dir >= 0) ? -rel : rel;
          if (g >= 0 && g < tailLen) {
            final double bk = Math.pow(1.0 - g / tailLen, gamma);
            if (bk > dotBright) {
              dotBright = bk;
            }
          }
        }
        int c = LXColor.scaleBrightness(interiorBase, (float) dotBright);
        if (flEnv > 0) {
          final double fd = Math.abs(wrapDelta(pj - flPos));
          if (fd < flashRadius) {
            final double fb = flEnv * Math.pow(1.0 - fd / flashRadius, 2);
            c = LXColor.lightest(c, LXColor.scaleBrightness(LXColor.WHITE, (float) fb));
          }
        }
        this.colors[points[j].index] = c;
      }
    }

    // --- the cube answers: flickering wash on the faces that were addressed ---
    s.flickerMs += deltaMs;
    if (s.flickerMs > 80) {
      s.flickerMs = 0;
      s.flickerTarget = 0.35 + 0.65 * Math.random();
    }
    s.flickerLp += (s.flickerTarget - s.flickerLp) * Math.min(1.0, deltaMs / 60.0);
    if (doReply) {
      for (int fi = 0; fi < 4; ++fi) {
        if (this.faceEnergy[fi] <= 0) continue;
        final int c = LXColor.scaleBrightness(exteriorBase,
          (float) (this.faceEnergy[fi] * s.flickerLp));
        setColor(Apotheneum.cube.exterior.faces[fi], c);
        if (Apotheneum.hasInterior) {
          setColor(Apotheneum.cube.interior.faces[fi], c);
        }
      }
    }
  }
}
