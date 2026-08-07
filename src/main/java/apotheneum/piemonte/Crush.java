/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Crush — segmented beams spiral down like a zoetrope drum, orbiting the
 * structure as they fall, and land band by band into a stacked palette
 * gradient. Once the canvas is full, the piece lives in pours: on a drop in
 * the music (or on its own clock in silence), a new gradient pours DOWN from
 * the top behind a large, churning liquid surface line — ComeUp inverted,
 * with a taller and more turbulent waterline — repainting the canvas color by
 * color, pour after pour. A momentary Strobe button flashes only the region
 * above the surface line.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.GradientUtils;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Crush extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final double DESC_RATE = 0.0006;   // beam descent / ms at speed 1
  private static final double POUR_RATE = 0.00035;  // pour-line descent / ms at speed 1
  private static final double ORBIT_RATE = 0.00012; // beam orbit, revs / ms at speed 1
  private static final double GRAD_STEP = 0.382;    // gradient shift per pour (golden)
  private static final double STROBE_HZ = 9;
  private static final int POURS_PER_CYCLE = 3;  // pours before the teardown
  private static final double TEAR_MS = 2800;    // slit-expansion duration

  public final DiscreteParameter bands =
    new DiscreteParameter("Bands", 8, 2, 24)
    .setDescription("Number of equal bands that stack up the height");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How easily a drop in the music starts a pour");

  public final BooleanParameter strobe =
    new BooleanParameter("Strobe", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Strobe the region above the surface line while held");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  private final GradientUtils.ColorStops stops = new GradientUtils.ColorStops();

  // build state
  private int settled = 0;
  private double beamBottom = 1;
  private double orbit = 0;        // shared zoetrope orbit phase
  private boolean full = false;
  private boolean started = false;
  private int lastN = -1;
  private double flashMs = 0;

  // pour state
  private boolean pouring = false;
  private double pourLevel = 1;    // bottom-up fraction of the surface line
  private double gradOffset = 0;   // settled gradient scroll
  private double nextOffset = 0;
  private double sincePourMs = 0;
  private int poursDone = 0;
  private boolean tearing = false;
  private double tearT = 0;

  // audio (Cope-style)
  private double bassAvg, prevBass, sinceBeatMs = 1e9;
  private double levelEnv = 0;
  private double timeMs = 0;

  public Crush(LX lx) {
    // Base registers color, speed (fall/pour rate), size (surface amplitude + edge).
    // (Band colors come from the project palette gradient, not the Color param.)
    super(lx, 0.4, 0, 1, 0.5, 0, 1);
    addParameter("bands", this.bands);
    addParameter("sensitivity", this.sensitivity);
    addParameter("strobe", this.strobe);
    addParameter("target", this.target);
  }

  private void reset(int n) {
    this.settled = 0;
    this.poursDone = 0;
    this.tearing = false;
    this.tearT = 0;
    this.full = false;
    this.pouring = false;
    this.gradOffset = 0;
    this.nextOffset = 0;
    this.beamBottom = 1.0 - 1.0 / n;
    this.started = true;
    this.lastN = n;
  }

  private int gradient(double lerp) {
    return this.stops.getColor((float) LXUtils.clamp(frac0(lerp), 0, 1),
      GradientUtils.BlendFunction.HSVM);
  }

  private static double frac0(double x) {
    return x - Math.floor(x);
  }

  private boolean detectDrop(double deltaMs) {
    heronarts.lx.audio.GraphicMeter m = this.lx.engine.audio.meter;
    int nb = Math.max(1, m.numBands);
    double bass = m.getAveragef(0, Math.max(1, nb / 4));
    this.bassAvg += (bass - this.bassAvg) * (1 - Math.exp(-deltaMs / 400.0));
    this.sinceBeatMs += deltaMs;
    double sens = this.sensitivity.getValue();
    double threshold = 1.15 + (1 - sens) * 0.8;
    boolean beat = (bass > this.bassAvg * threshold)
      && (bass > this.prevBass)
      && (this.sinceBeatMs >= 300)
      && (bass > 0.01);
    this.prevBass = bass;
    if (beat) {
      this.sinceBeatMs = 0;
    }
    double level = m.getAveragef(0, nb);
    double alpha = (level > this.levelEnv)
      ? 1 - Math.exp(-deltaMs / 25.0)
      : 1 - Math.exp(-deltaMs / 220.0);
    this.levelEnv += (level - this.levelEnv) * alpha;
    return beat;
  }

  /** Churning surface line offset (normalized) at column fraction x01. */
  private double surf(double x01, double amp) {
    final double t = this.timeMs * 0.001;
    return amp * (0.50 * Math.sin(2 * Math.PI * (x01 * 3 + t * 0.9))
                + 0.30 * Math.sin(2 * Math.PI * (x01 * 7 - t * 1.7))
                + 0.20 * Math.sin(2 * Math.PI * (x01 * 11 + t * 3.1)));
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.timeMs += deltaMs;

    final int n = this.bands.getValuei();
    if (!this.started || n != this.lastN) {
      reset(n);
    }

    final double speed = Math.max(0.02, getSpeed());
    final boolean drop = detectDrop(deltaMs);
    final double bandH = 1.0 / n;
    this.flashMs = Math.max(0, this.flashMs - deltaMs);
    final double wow = getWow();
    // Wow = frenzy: the spiral spins harder, pours churn taller and run
    // faster, and the teardown rips through quicker
    this.orbit += ORBIT_RATE * speed * deltaMs * (1 + wow * 3);

    // --- build phase: spiraling beams stack up the gradient ---
    if (!this.full) {
      double targetBottom = (double) this.settled / n;
      this.beamBottom -= DESC_RATE * speed * deltaMs;
      if (this.beamBottom <= targetBottom) {
        this.beamBottom = targetBottom;
        this.settled++;
        this.flashMs = 250;
        if (this.settled >= n) {
          this.full = true;
          this.sincePourMs = 0;
        } else {
          this.beamBottom = 1.0 - bandH;
        }
      }
    } else if (!this.pouring) {
      // full: wait for a drop (or pour on a clock in silence)
      this.sincePourMs += deltaMs;
      final boolean quiet = this.levelEnv < 0.04;
      if (drop || (quiet && this.sincePourMs > 6000)) {
        this.pouring = true;
        this.pourLevel = 1.15;
        this.nextOffset = this.gradOffset + GRAD_STEP;
      }
    } else {
      this.pourLevel -= POUR_RATE * speed * deltaMs * (1 + this.levelEnv * 0.8 + wow * 0.9);
      final double amp = (0.05 + getSize() * 0.09) * (1 + wow * 0.8);
      if (this.pourLevel < -amp - 0.05) {
        this.gradOffset = this.nextOffset;
        this.pouring = false;
        this.sincePourMs = 0;
        if (++this.poursDone >= POURS_PER_CYCLE) {
          this.tearing = true; // the loop: split, empty, and build again
          this.tearT = 0;
        }
      }
    }
    if (this.tearing) {
      this.tearT += deltaMs * Math.max(0.02, getSpeed()) * 2 * (1 + wow) / TEAR_MS;
      if (this.tearT >= 1.1) {
        final double keepOffset = this.gradOffset;
        reset(this.bands.getValuei());
        this.gradOffset = keepOffset; // next build lands on fresh palette footing
      }
    }

    // refresh palette gradient (in case the swatch changed)
    int numStops = this.lx.engine.palette.swatch.colors.size();
    this.stops.setPaletteGradient(this.lx.engine.palette, 0, Math.max(1, numStops));

    final double edge = Math.max(0.01, (0.08 + getSize() * 0.35) * bandH);
    final double fillTop = (double) this.settled / n;
    final double beamLerp = (this.settled + 0.5) / n;
    final int beamColor = this.full ? 0 : gradient(beamLerp);
    final double beamTop = this.beamBottom + bandH;
    final double flashE = this.flashMs / 250.0;
    final double amp = (0.05 + getSize() * 0.09) * (1 + wow * 0.8); // pour surface amplitude
    final boolean strobeOn = this.strobe.isOn()
      && (frac0(this.timeMs * 0.001 * STROBE_HZ) < 0.45);

    final Target t = this.target.getEnum();
    if (t != Target.CYLINDER) {
      draw(Apotheneum.cube.exterior, n, edge, fillTop, beamColor, beamTop, bandH, flashE, amp, strobeOn);
    }
    if (t != Target.CUBE) {
      draw(Apotheneum.cylinder.exterior, n, edge, fillTop, beamColor, beamTop, bandH, flashE, amp, strobeOn);
    }
    copyExterior();
  }

  private void draw(Apotheneum.Orientation o, int n, double edge,
      double fillTop, int beamColor, double beamTop, double bandH, double flashE,
      double amp, boolean strobeOn) {
    final int w = o.width();
    final int nSegs = 6; // zoetrope segmentation of the falling beam
    final double gapFrac = 0.22;

    int ci = 0;
    for (Apotheneum.Column column : o.columns()) {
      final LXPoint[] points = column.points;
      final int last = points.length - 1;
      final double x01 = (double) ci / w;
      // this column's position within the orbiting segment cycle
      final double segPhase = frac0((x01 + this.orbit) * nSegs);
      final boolean inGap = segPhase > (1.0 - gapFrac);
      final double surfOff = surf(x01, amp);

      for (int j = 0; j < points.length; ++j) {
        final double vB = (last == 0) ? 0.0 : (double) (last - j) / last;
        int c = LXColor.BLACK;
        double surfaceLine;

        if (this.full && this.tearing) {
          surfaceLine = 1.0;
          // vertical slits at the orbiting segment boundaries widen until dark
          final double lit = 1.0 - LXUtils.clamp(this.tearT, 0, 1);
          if (segPhase < lit) {
            c = gradient(vB + this.gradOffset);
            // feathered slit edges
            final double e2 = LXUtils.clamp((lit - segPhase) / 0.06, 0, 1);
            c = LXColor.scaleBrightness(c, (float) e2);
          }
        } else if (this.full) {
          if (this.pouring) {
            final double lineY = this.pourLevel + surfOff;
            surfaceLine = lineY;
            if (vB > lineY) {
              c = gradient(vB + this.nextOffset); // new gradient pouring in above
            } else {
              c = gradient(vB + this.gradOffset);
            }
            // large churning foam band on the surface line itself
            final double dLine = Math.abs(vB - lineY);
            if (dLine < 0.035) {
              final double f = 1.0 - dLine / 0.035;
              c = LXColor.lerp(c, LXColor.WHITE, (float) (0.75 * f * f));
            }
          } else {
            surfaceLine = 1.0; // idle full canvas: nothing above
            c = gradient(vB + this.gradOffset);
          }
        } else {
          surfaceLine = fillTop;
          if (vB < fillTop) {
            c = gradient(vB);
            double gd = Math.abs(vB - fillTop);
            if (flashE > 0 && gd < bandH * 0.4) {
              c = LXColor.lerp(c, LXColor.WHITE,
                (float) (flashE * flashE * smoothstep(bandH * 0.4, 0.0, gd)));
            }
          } else if (!inGap && vB >= this.beamBottom && vB <= beamTop) {
            // spiraling segmented beam: soft edges, hot white head at its bottom
            double d = Math.min(vB - this.beamBottom, beamTop - vB);
            double bri = LXUtils.clamp(d / edge, 0, 1);
            bri = bri * bri * (3 - 2 * bri);
            // feather segment edges so the orbit reads smooth
            final double segEdge = Math.min(segPhase, (1.0 - gapFrac) - segPhase) / 0.06;
            bri *= LXUtils.clamp(segEdge, 0, 1);
            double head = smoothstep(edge * 2.0, 0.0, vB - this.beamBottom);
            c = LXColor.lerp(LXColor.scaleBrightness(beamColor, (float) bri),
              LXColor.WHITE, (float) (0.5 * head * bri));
          }
        }

        // strobe: only the region ABOVE the surface line
        if (strobeOn && vB > surfaceLine) {
          c = LXColor.lightest(c, LXColor.gray(85));
        }
        this.colors[points[j].index] = c;
      }
      ++ci;
    }
  }

  private static double smoothstep(double e0, double e1, double x) {
    double t = (x - e0) / (e1 - e0);
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
    return t * t * (3 - 2 * t);
  }
}
