/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Origami — the surface is a sheet of paper ruled with drifting diagonal lines
 * in one color. Hit the Fold button (or let a dramatic swell in the music do
 * it) and the sheet creases: the remaining area folds in half upon itself,
 * the flap sweeping over and dissolving to reveal a new layer beneath — a new
 * color, lines running the other way. Folds keep halving the old layer until
 * it's gone, then the folding continues on the next layer, a new color with
 * every crease. Wow makes each fold flash harder and crease brighter.
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
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/piemonte")
public class Origami extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final double HUE_STEP = 47;      // per-layer hue advance
  private static final double MIN_REMAIN = 0.13;  // below this, the next fold finishes the layer
  private static final double FOLD_REFRACT_MS = 900;

  public final BooleanParameter fold =
    new BooleanParameter("Fold", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Fold the sheet: collapse half the current layer");

  public final BooleanParameter audio =
    new BooleanParameter("Audio", true)
    .setDescription("Let dramatic shifts in the music trigger folds");

  public final CompoundParameter sensitivity =
    new CompoundParameter("Sens", 0.5, 0, 1)
    .setDescription("How dramatic the music shift must be to fold");

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

  // global fold state, shared by all panels so the whole piece creases together
  private int layer = 0;
  private double lo = 0, hi = 1;      // remaining old-layer interval on the fold axis
  private double foldT = -1;          // <0 idle, else 0..1 animation progress
  private int foldSide = 0;           // 0: fold left/top half over; 1: right/bottom
  private double crease = 0.5;
  private boolean finalFold = false;
  private double drift = 0;
  private double timeMs = 0;
  private boolean foldWasOn = false;

  // bass-onset state (Cope-style detector, longer refractory for "dramatic" shifts)
  private double bassAvg, prevBass, sinceBeatMs = 1e9;

  public Origami(LX lx) {
    // Base registers color (layer-0 tint), speed (drift + fold tempo), size (line
    // spacing/thickness), wow (fold drama).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("fold", this.fold);
    addParameter("audio", this.audio);
    addParameter("sensitivity", this.sensitivity);
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

  private static double hashd(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  /** Stripe angle for a layer: alternating diagonals with per-layer character. */
  private static double layerAngle(int layer) {
    double base = ((layer & 1) == 0) ? 45 : -45;
    return Math.toRadians(base + (hashd(layer * 31 + 7) - 0.5) * 18);
  }

  private int layerColor(int layer) {
    float baseHue = LXColor.h(getColor());
    float hue = (float) (((baseHue + layer * HUE_STEP) % 360 + 360) % 360);
    return LXColor.hsb(hue, 85, 100);
  }

  /** True if this layer folds along the horizontal axis (vertical creases). */
  private static boolean axisHoriz(int layer) {
    return hashd(layer * 53 + 3) < 0.5;
  }

  private boolean detectDrop(double deltaMs) {
    heronarts.lx.audio.GraphicMeter m = this.lx.engine.audio.meter;
    int nb = Math.max(1, m.numBands);
    double bass = m.getAveragef(0, Math.max(1, nb / 4));
    this.bassAvg += (bass - this.bassAvg) * (1 - Math.exp(-deltaMs / 400.0));
    this.sinceBeatMs += deltaMs;
    double sens = this.sensitivity.getValue();
    double threshold = 1.25 + (1 - sens) * 0.9; // demands a real shift, not every beat
    boolean drop = (bass > this.bassAvg * threshold)
      && (bass > this.prevBass)
      && (this.sinceBeatMs >= FOLD_REFRACT_MS)
      && (bass > 0.01);
    this.prevBass = bass;
    if (drop) {
      this.sinceBeatMs = 0;
    }
    return drop;
  }

  private void startFold() {
    final double remain = this.hi - this.lo;
    if (remain <= MIN_REMAIN) {
      // last sliver: fold the whole thing over its far edge and finish the layer
      this.finalFold = true;
      this.foldSide = 0;
      this.crease = this.hi;
    } else {
      this.finalFold = false;
      // alternate which side folds in, tracked by interval midpoint parity
      this.foldSide = (hashd(this.layer * 97 + (int) (remain * 1000)) < 0.5) ? 0 : 1;
      this.crease = 0.5 * (this.lo + this.hi);
    }
    this.foldT = 0;
  }

  private void commitFold() {
    if (this.finalFold) {
      this.layer++;
      this.lo = 0;
      this.hi = 1;
    } else if (this.foldSide == 0) {
      this.lo = this.crease;
    } else {
      this.hi = this.crease;
    }
    this.foldT = -1;
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

    this.timeMs += deltaMs;
    final double speed = Math.max(0.02, getSpeed());
    final double wow = getWow();
    this.drift += deltaMs * speed * 0.0004;

    // --- triggers: UI button edge, or a dramatic musical shift ---
    final boolean btn = this.fold.isOn();
    final boolean btnEdge = btn && !this.foldWasOn;
    this.foldWasOn = btn;
    final boolean drop = detectDrop(deltaMs) && this.audio.isOn();
    if (this.foldT < 0 && (btnEdge || drop)) {
      startFold();
    }

    // --- advance fold animation ---
    final double foldDur = 700.0 / ((0.4 + speed * 1.2) * (1.0 + wow * 0.5));
    if (this.foldT >= 0) {
      this.foldT += deltaMs / foldDur;
      if (this.foldT >= 1) {
        commitFold();
      }
    }

    final int colOld = layerColor(this.layer);
    final int colNew = layerColor(this.layer + 1);
    final double angOld = layerAngle(this.layer);
    final double angNew = layerAngle(this.layer + 1);
    final boolean horiz = axisHoriz(this.layer);
    final double period = 3 + getSize() * 10;
    final double duty = 0.32;

    final double ft = this.foldT;
    final double cosF = (ft >= 0) ? Math.cos(Math.PI * ft) : 1;
    final double lift = (ft >= 0) ? Math.sin(Math.PI * ft) : 0; // flap catching the light
    final double flapGlint = lift * (0.45 + wow * 0.75);
    final double creaseGlow = (ft >= 0) ? (0.6 + wow * 0.9) * Math.sin(Math.PI * Math.min(1, ft * 1.15)) : 0;

    for (Panel p : this.panels) {
      final double diag = 1.0 / Math.max(1, (horiz ? p.w : p.h) - 1);
      for (int x = 0; x < p.w; ++x) {
        for (int y = 0; y < p.h; ++y) {
          final double a = (horiz ? x : y) * diag; // fold-axis coordinate, 0..1
          int c;
          double b;
          double sampleA = a;      // where on the sheet we sample the old pattern
          boolean showOld;
          double glint = 0;

          if (ft < 0) {
            showOld = (a >= this.lo && a <= this.hi);
          } else if (this.foldSide == 0) {
            // flap [lo, crease] folds rightward over the crease
            final double flapEdge = this.crease - (this.crease - this.lo) * cosF;
            if (cosF > 0.02 && a >= flapEdge && a < this.crease) {
              showOld = true; // foreshortened flap still lifting
              sampleA = this.crease - (this.crease - a) / cosF;
              glint = flapGlint;
            } else if (cosF < -0.02 && a >= this.crease && a <= this.crease - (this.crease - this.lo) * cosF) {
              showOld = true; // mirrored flap landing on the far side
              sampleA = this.crease - (a - this.crease) / (-cosF);
              glint = flapGlint;
            } else {
              showOld = !this.finalFold && (a >= this.crease && a <= this.hi);
            }
          } else {
            // flap [crease, hi] folds leftward over the crease
            final double flapEdge = this.crease + (this.hi - this.crease) * cosF;
            if (cosF > 0.02 && a > this.crease && a <= flapEdge) {
              showOld = true;
              sampleA = this.crease + (a - this.crease) / cosF;
              glint = flapGlint;
            } else if (cosF < -0.02 && a <= this.crease && a >= this.crease + (this.hi - this.crease) * cosF) {
              showOld = true;
              sampleA = this.crease + (this.crease - a) / (-cosF);
              glint = flapGlint;
            } else {
              showOld = (a >= this.lo && a <= this.crease);
            }
          }

          // absorb the flap into the sheet at the very end of the fold
          double flapAlpha = 1;
          if (glint > 0 && ft > 0.78) {
            flapAlpha = LXUtils.clamp((1 - ft) / 0.22, 0, 1);
            if (flapAlpha <= 0) {
              showOld = false;
            }
          }

          // stripes: sample the appropriate layer's ruled sheet
          final double sx = (horiz ? sampleA / diag : x);
          final double sy = (horiz ? y : sampleA / diag);
          if (showOld) {
            b = sheet(sx, sy, angOld, period, duty, p, 1);
            c = colOld;
          } else {
            b = sheet(x, y, angNew, period, duty, p, -1);
            c = colNew;
          }

          if (glint > 0 && showOld) {
            b = (b + glint) * flapAlpha + (1 - flapAlpha) * b;
            c = LXColor.lerp(c, LXColor.WHITE, (float) LXUtils.clamp(glint * 0.6, 0, 1));
          }
          // white-hot crease line while folding
          if (creaseGlow > 0) {
            final double dc = Math.abs(a - this.crease);
            if (dc < 0.035) {
              final double g = creaseGlow * (1 - dc / 0.035);
              b = Math.max(b, g);
              c = LXColor.lerp(c, LXColor.WHITE, (float) LXUtils.clamp(g, 0, 1));
            }
          }

          if (b <= 0.004) continue;
          final int idx = p.idx[x][y];
          this.colors[idx] = LXColor.lightest(this.colors[idx],
            LXColor.scaleBrightness(c, (float) LXUtils.clamp(b, 0, 1)));
        }
      }
    }

    copyExterior();
  }

  /** Ruled-paper brightness at (x,y): diagonal lines over a soft color-gradient fill. */
  private double sheet(double x, double y, double angle, double period, double duty,
      Panel p, int driftDir) {
    final double s = (x * Math.cos(angle) + y * Math.sin(angle)) / period + this.drift * driftDir;
    final double v = s - Math.floor(s);
    // soft gradient fill so the layer reads as a colored sheet, lines on top
    final double grad = 0.10 + 0.20 * (y / (double) Math.max(1, p.h - 1));
    if (v < duty) {
      // gamma-shaped line profile with a bright core
      final double u = 1 - Math.abs(v / duty * 2 - 1);
      return Math.max(grad, 0.35 + 0.65 * u * u);
    }
    return grad;
  }
}
