/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * Diabolical — thick concentric diamond bands spiral out from the center in a shifting
 * rainbow, rotating and radiating endlessly, each band whitening to a bright
 * ridge along its spine. Wow ripples the whole field like a flag in the wind.
 * A graphics-forward, hypnotic field inspired by the LXStudio-TE SpiralDiamonds
 * shader.
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

@LXCategory("Apotheneum/piemonte")
public class Diabolical extends ParameterPattern {

  private static final double ROT = 0.0006;    // rotation (twist) rad/ms at spin 1, speed 1
  private static final double SCROLL = 0.0016; // outward scroll / ms at speed 1
  private static final double HUE_SCALE = 26;  // hue degrees per ring

  public final DiscreteParameter quantity =
    new DiscreteParameter("Quantity", 4, 1, 8)
    .setDescription("Number of concentric diamond rings");

  public final CompoundParameter spin =
    new CompoundParameter("Spin", 0.4, 0, 1)
    .setDescription("How fast the spiral rotates");

  private static final class Panel {
    final int[][] idx;
    final int w, h;
    Panel(int[][] idx, int w, int h) { this.idx = idx; this.w = w; this.h = h; }
  }

  private Panel[] panels;
  private Target builtTarget;

  private double twist = 0;   // spiral rotation
  private double scroll = 0;  // outward radial scroll
  private double timeMs = 0;

  public Diabolical(LX lx) {
    // Base registers color (base hue), speed (outward scroll), size (zoom).
    super(lx, 0.5, 0, 1, 0.5, 0, 1);
    addParameter("quantity", this.quantity);
    addParameter("spin", this.spin);
    addTargetParameter();
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

  private static double smoothstep(double e0, double e1, double x) {
    double t = (x - e0) / (e1 - e0);
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
    return t * t * (3 - 2 * t);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    final Target t = getTarget();
    if (this.panels == null || this.builtTarget != t) {
      buildPanels(t);
    }
    if (this.panels.length == 0) {
      return;
    }

    final double speed = Math.max(0.02, getSpeed());
    this.timeMs += deltaMs;
    this.twist += ROT * this.spin.getValue() * speed * deltaMs;
    this.scroll += SCROLL * speed * deltaMs;

    final int qn = this.quantity.getValuei();
    final double k = 0.6321 * Math.pow(qn, -0.8794); // per-quadrant stitch angle
    final double cosK = Math.cos(k), sinK = Math.sin(k);
    final double baseHue = LXColor.h(getColor());
    final double zoom = 0.6 + getSize() * 1.8;       // Size = feature scale
    final double wow = getWow();
    final double wts = this.timeMs * 0.002;

    for (Panel p : this.panels) {
      final double cx = p.w * 0.5;
      final double cy = p.h * 0.5;
      final double s = (0.5 * Math.hypot(p.w, p.h)) / zoom;
      for (int y = 0; y < p.h; ++y) {
        double uvyRow = (y - cy) / s;
        for (int x = 0; x < p.w; ++x) {
          double uvx = (x - cx) / s;
          double uvy = uvyRow;

          // Wow = flag-warp: ripple the field like cloth in the wind
          if (wow > 0.01) {
            double ox = uvx;
            uvx -= wow * 0.08 * Math.sin(uvy * 9.0 + wts);
            uvy += wow * 0.08 * Math.cos(ox * 9.0);
          }

          // rotated sign vector -> "diamond" metric (spiraling squares)
          double sx = uvx >= 0 ? 1 : -1;
          double sy = uvy >= 0 ? 1 : -1;
          double rsx = sx * cosK - sy * sinK;
          double rsy = sx * sinK + sy * cosK;
          double dd = uvx * rsx + uvy * rsy;
          if (dd < 1e-4) dd = 1e-4;

          double ang = Math.atan2(rsy, rsx);
          double ringPhase = qn * Math.log(dd) - this.scroll;
          double dx = Math.abs(Math.sin(ringPhase + ang - this.twist));
          double band = smoothstep(0.72, 0.48, dx); // thick band
          band *= band; // deepen the gaps between bands
          if (band <= 0.01) continue;
          double ridge = smoothstep(0.30, 0.05, dx); // white ridge at the spine

          double hue = ((baseHue + ringPhase * HUE_SCALE) % 360 + 360) % 360;
          int c = LXColor.hsb((float) hue, (float) (88 - 50 * ridge), 100);
          this.colors[p.idx[x][y]] = LXColor.scaleBrightness(c, (float) band);
        }
      }
    }
    copyExterior();
  }
}
