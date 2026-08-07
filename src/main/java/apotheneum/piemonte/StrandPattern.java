/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * StrandPattern — shared engine for the "hanging light-strand curtain" family of
 * patterns (Overflow, Whiteout, Mainframe, Pressure, Biorhythm, HolyWater,
 * Motherboard, Hexed, Heartthrob), modeled on the holotrigger installation:
 * vertical strands of light with a top-lit drip gradient, glowing floor pools,
 * comet rain, and audio reactivity driven by the external Level/Pulse inputs
 * on ParameterPattern. Subclasses implement one "program" each; this base
 * provides the column/drip/pool math, a comet renderer, and wrapped-x pixel
 * writes.
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.utils.LXUtils;

public abstract class StrandPattern extends ParameterPattern {

  protected StrandPattern(LX lx,
      double speedDef, double speedMin, double speedMax,
      double sizeDef, double sizeMin, double sizeMax) {
    super(lx, speedDef, speedMin, speedMax, sizeDef, sizeMin, sizeMax);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    stepAudio(deltaMs);
    advance(deltaMs);

    final Target t = getTarget();
    if (t != Target.CYLINDER) {
      renderStrands(Apotheneum.cube.exterior, deltaMs, true);
    }
    if (t != Target.CUBE) {
      renderStrands(Apotheneum.cylinder.exterior, deltaMs, false);
    }
    copyExterior();
  }

  /** Per-frame global state update before surfaces render. */
  protected void advance(double deltaMs) {}

  /** Draw one surface's strand program. Called for cube and/or cylinder exterior. */
  protected abstract void renderStrands(Apotheneum.Orientation o, double deltaMs, boolean isCube);

  // ------------------------------------------------------------------
  // strand look utilities

  /** Top-lit drip gradient: strands are brightest at the hang point. */
  protected static double drip(int y, int h) {
    return 1.0 - 0.35 * (double) y / Math.max(1, h - 1);
  }

  /** Additive floor-pool boost for the bottom rows (strand puddles glowing). */
  protected static double pool(int y, int h) {
    int fromBottom = (h - 1) - y;
    if (fromBottom >= 4) return 0;
    return 0.35 * (1.0 - fromBottom / 4.0);
  }

  /**
   * Mirror the rows above the floor line into the bottom rows at reduced gain —
   * the reflective-floor signature. Call AFTER drawing surface content.
   */
  protected void mirrorFloor(Apotheneum.Orientation o, int rows, float gain) {
    final int w = o.width();
    final int h = o.height();
    final int axis = h - rows;
    for (int x = 0; x < w; ++x) {
      for (int y = axis; y < h; ++y) {
        int src = 2 * axis - 1 - y;
        if (src < 0) continue;
        int c = this.colors[o.point(x, src).index];
        int idx = o.point(x, y).index;
        this.colors[idx] = LXColor.lightest(this.colors[idx],
          LXColor.scaleBrightness(c, gain));
      }
    }
  }

  /** Additive pixel write with wrapped x; y outside the surface is dropped. */
  protected void addPix(Apotheneum.Orientation o, int x, int y, int color, double b) {
    if (b <= 0.004) return;
    final int h = o.height();
    if (y < 0 || y >= h) return;
    final int w = o.width();
    final int xi = ((x % w) + w) % w;
    final int idx = o.point(xi, y).index;
    this.colors[idx] = LXColor.lightest(this.colors[idx],
      LXColor.scaleBrightness(color, (float) LXUtils.clamp(b, 0, 1)));
  }

  /**
   * Falling comet: sub-pixel head at (x, y) with an exponential tail rising
   * above it. White-hot head when hot is true.
   */
  protected void comet(Apotheneum.Orientation o, double x, double y, double tail,
      int color, double bright, boolean hot) {
    final int xi = (int) Math.round(x);
    final int head = (int) Math.floor(y);
    final double hf = y - head;
    final int n = Math.max(1, (int) tail);
    final int headColor = hot ? LXColor.lerp(color, LXColor.WHITE, 0.55f) : color;
    for (int k = 0; k <= n; ++k) {
      double b = bright * Math.exp(-2.2 * k / tail);
      addPix(o, xi, head - k, (k == 0) ? headColor : color, (k == 0) ? b * (0.4 + 0.6 * hf) : b);
    }
    addPix(o, xi, head + 1, headColor, bright * hf * 0.5);
  }
}
