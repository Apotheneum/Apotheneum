/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Destination — a blazing point of light at the center of each face, throwing
 * radial streaks outward with a bright horizontal bar across the middle, the
 * whole thing flickering like a distant sun seen at the end of a long dark hall.
 * Each ray carries its own palette hue, and Wow fans the ray hues further apart
 * into a spectral burst. A "warp destination" glow. Inspired by the LXStudio-TE
 * FourStar shader.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Destination extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final double TAU = Math.PI * 2;
  private static final int MAX_RAYS = 16;
  private static final int CYAN = LXColor.rgb(0, 255, 255);
  private static final int STROBE_COLOR = LXColor.lerp(LXColor.WHITE, CYAN, 0.30f);
  private static final double RING_GAP = 9.0;     // cells between glitch rings
  private static final double PULSE_SPEED = 0.045; // ring expansion, cells/ms
  private static final double SPIN = 0.0002; // ray rotation, rad / ms at speed 1

  public final DiscreteParameter rays =
    new DiscreteParameter("Rays", 6, 2, MAX_RAYS)
    .setDescription("Number of light streaks");

  public final CompoundParameter flicker =
    new CompoundParameter("Flicker", 0.5, 0, 1)
    .setDescription("How much the light flickers");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  private static final class Panel {
    final int[][] idx;
    final int w, h;
    Panel(int[][] idx, int w, int h) { this.idx = idx; this.w = w; this.h = h; }
  }

  private Panel[] panels;
  private Target builtTarget;

  private double baseAngle = 0;
  private double timeMs = 0;
  public final BooleanParameter strobe =
    new BooleanParameter("Strobe", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Glitch glow pulses out from the star while held");

  private double strobeEnv = 0;
  private double pulseClock = 0;

  private final double[] rayFlick = new double[MAX_RAYS];
  private final int[] rayColor = new int[MAX_RAYS];

  public Destination(LX lx) {
    // Base registers color (flare tint), speed (spin + flicker rate), size (core/ray reach).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("rays", this.rays);
    addParameter("flicker", this.flicker);
    addParameter("strobe", this.strobe);
    addParameter("target", this.target);
  }

  private void buildPanels(Target t) {
    java.util.List<Panel> list = new java.util.ArrayList<>();
    if (t != Target.CYLINDER) {
      for (Apotheneum.Cube.Face face : Apotheneum.cube.exterior.faces) {
        int w = face.columns.length;
        int h = face.columns[0].points.length;
        int[][] g = new int[w][h];
        for (int x = 0; x < w; ++x) {
          for (int y = 0; y < h; ++y) {
            g[x][y] = face.columns[x].points[y].index;
          }
        }
        list.add(new Panel(g, w, h));
      }
    }
    if (t != Target.CUBE) {
      Apotheneum.Orientation o = Apotheneum.cylinder.exterior;
      int q = o.width() / 4;
      int h = o.height();
      for (int seg = 0; seg < 4; ++seg) {
        int[][] g = new int[q][h];
        for (int x = 0; x < q; ++x) {
          for (int y = 0; y < h; ++y) {
            g[x][y] = o.point(seg * q + x, y).index;
          }
        }
        list.add(new Panel(g, q, h));
      }
    }
    this.panels = list.toArray(new Panel[0]);
    this.builtTarget = t;
  }

  private static double angDiff(double a, double b) {
    double d = Math.abs(a - b) % TAU;
    return Math.min(d, TAU - d);
  }

  private static double smoothstep(double e0, double e1, double x) {
    double t = (x - e0) / (e1 - e0);
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
    return t * t * (3 - 2 * t);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    final Target t = this.target.getEnum();
    if (this.panels == null || this.builtTarget != t) {
      buildPanels(t);
    }
    if (this.panels.length == 0) {
      return;
    }

    final double speed = Math.max(0.02, getSpeed());
    this.timeMs += deltaMs;
    this.baseAngle += SPIN * speed * deltaMs;
    final double ts = this.timeMs * 0.001;

    // global flicker + per-ray flicker (computed once per frame)
    final double flAmt = this.flicker.getValue();
    double fl = 0.5 + 0.5 * Math.sin(ts * 11 * speed) * Math.sin(ts * 6.7 * speed + 1.3);
    final double gFlicker = LXUtils.lerp(1.0, 0.35 + 0.65 * fl, flAmt);
    final int nRays = this.rays.getValuei();
    for (int n = 0; n < nRays; ++n) {
      double rf = 0.5 + 0.5 * Math.sin(ts * 9 * speed + n * 2.1) * Math.cos(ts * 5 * speed + n * 1.7);
      this.rayFlick[n] = LXUtils.lerp(1.0, 0.25 + 0.75 * rf, flAmt);
    }

    final double size = getSize();
    final int base = getColor();

    // per-ray palette hues, fanned wider apart by Wow
    final double wow = getWow();
    final float baseHue = LXColor.h(base);
    for (int n = 0; n < nRays; ++n) {
      this.rayColor[n] = LXColor.hsb((float) ((baseHue + n * (30.0 + 60.0 * wow)) % 360), 85, 100);
    }

    // SpecialKube-style glitch strobe: cyan-white shockrings pulse outward
    // from the star while the button is held, strobing and jittering
    if (this.strobe.isOn()) {
      this.strobeEnv = 1;
      this.pulseClock += deltaMs;
    } else if (this.strobeEnv > 0) {
      this.strobeEnv = Math.max(0, this.strobeEnv - deltaMs / 600.0);
      this.pulseClock += deltaMs;
    }
    final boolean strobeGate = (this.timeMs % 125.0) < 78; // ~8Hz glitch flicker
    final double glitchJit = (this.strobeEnv > 0) ? (Math.random() - 0.5) * 2.5 : 0;

    final double coreK = 0.10 + size * 0.22;   // brighter/larger core with Size
    final double exposure = 0.05 + size * 0.05; // ray angular half-width
    final double rayFade = 0.55;                // rays fade toward the edge
    final double step = TAU / nRays;

    for (Panel p : this.panels) {
      final double cx = p.w * 0.5;
      final double cy = p.h * 0.5;
      final double halfDiag = 0.5 * Math.hypot(p.w, p.h);
      final double bandW = 1.5 + size * 3.5;
      for (int y = 0; y < p.h; ++y) {
        double dyp = y - cy;
        for (int x = 0; x < p.w; ++x) {
          double dxp = x - cx;
          double dist = Math.hypot(dxp, dyp) / halfDiag; // 0..~1
          double ang = Math.atan2(dyp, dxp);

          // bright inverse-distance core
          double core = coreK / (dist + 0.06);

          // radial streaks, tracking the strongest ray for its color
          double rayB = 0;
          double rayMax = 0;
          int rayC = base;
          for (int n = 0; n < nRays; ++n) {
            double rayAng = this.baseAngle + n * step;
            double ad = angDiff(ang, rayAng);
            if (ad < exposure) {
              double tt = 1.0 - ad / exposure;
              double a = tt * tt * tt * Math.max(0, 1.0 - rayFade * dist);
              rayB += a * this.rayFlick[n];
              if (a * this.rayFlick[n] > rayMax) {
                rayMax = a * this.rayFlick[n];
                rayC = this.rayColor[n];
              }
            }
          }

          // horizontal light bar
          double band = LXUtils.clamp(1.0 - Math.abs(dyp) / bandW, 0, 1)
            * Math.max(0, 1.0 - dist * 0.6);

          // glitch shockrings: concentric cyan-white pulses expanding from the
          // star, strobing hard and jittering like SpecialKube's special cubes
          double glitch = 0;
          if (this.strobeEnv > 0.01 && strobeGate) {
            final double distC = dist * halfDiag + glitchJit;
            double dRing = (distC - this.pulseClock * PULSE_SPEED) % RING_GAP;
            if (dRing < 0) dRing += RING_GAP;
            dRing = Math.min(dRing, RING_GAP - dRing);
            final double ring = Math.exp(-dRing * dRing / 2.2);
            glitch = this.strobeEnv * ring * Math.max(0.25, 1.4 - dist * 1.1);
          }

          double b = (core + rayB * 0.8 + band * 0.7) * gFlicker;
          b *= smoothstep(1.05, 0.45, dist); // vignette toward the panel edges
          if (b <= 0.01 && glitch <= 0.01) continue;
          b = LXUtils.clamp(b, 0, 1);

          // tint toward the winning ray, then whiten the hot core
          int cbase = LXColor.lerp(base, rayC, (float) LXUtils.clamp(rayMax * 1.5, 0, 1));
          int c = LXColor.lerp(cbase, LXColor.WHITE, (float) LXUtils.clamp(core * 1.2, 0, 1));
          int outC = LXColor.scaleBrightness(c, (float) b);
          if (glitch > 0.01) {
            outC = LXColor.lightest(outC,
              LXColor.scaleBrightness(STROBE_COLOR, (float) LXUtils.clamp(glitch, 0, 1)));
          }
          this.colors[p.idx[x][y]] = outC;
        }
      }
    }
    copyExterior();
  }
}
