/**
 * Copyright 2026- Patrick Piemonte
 *
 * Created by patrick piemonte
 *
 * WYD — a field of fine flowing waveform lines that ripple and drift like silk
 * or wind-blown hair. The streamlines are warped by multi-octave noise and lit by
 * a slow second noise field so crests glow bright while the hollows fall into
 * complementary-tinted shadow, giving the waves real depth. The whole field
 * flows seamlessly around the sculpture in 3D.
 *
 * WARNING: Flashing imagery, best viewed in deep playa
 */

package apotheneum.piemonte;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.utils.Noise;

@LXCategory("Apotheneum/piemonte")
public class WYD extends ParameterPattern {

  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  private static final double TAU = Math.PI * 2;
  private static final float FX = 2.2f;    // horizontal noise frequency (flow warp)
  private static final float LX_F = 1.3f;  // lighting-field frequency
  private static final double WARP = 1.35; // how strongly the flow bends the lines

  public final DiscreteParameter density =
    new DiscreteParameter("Density", 16, 4, 44)
    .setDescription("Number of flowing wave lines");

  public final CompoundParameter depth =
    new CompoundParameter("Depth", 0.6, 0, 1)
    .setDescription("Strength of the light/shadow that gives the waves depth");

  public final EnumParameter<Target> target =
    new EnumParameter<Target>("Target", Target.BOTH)
    .setDescription("Which structures to render to");

  private double timeMs = 0;

  public WYD(LX lx) {
    // Base registers color (wave tint), speed (flow drift), size (line thickness).
    super(lx, 0.4, 0, 1, 0.5, 0, 1);
    addParameter("density", this.density);
    addParameter("depth", this.depth);
    addParameter("target", this.target);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.timeMs += deltaMs;

    final double speed = getSpeed();
    final double wow = getWow();
    // Wow = wind gust: the silk whips harder, flows faster, crests blaze
    final double drift = this.timeMs * 0.00035 * speed * (1 + wow * 1.2); // horizontal flow
    final float tEvolve = (float) (this.timeMs * 0.0002 * (0.4 + speed));
    final double dens = this.density.getValuei();
    final double sharp = 3.0 + (1.0 - getSize()) * 34.0;    // thinner lines when Size is low
    final double depthAmt = this.depth.getValue();
    final int base = getColor();
    // hollows fall toward a complementary shadow tint instead of flat darkness
    final int shadow = LXColor.hsb(
      (float) ((LXColor.h(base) + 320) % 360),
      (float) Math.min(100, LXColor.s(base) + 10), 100);
    final Target t = this.target.getEnum();

    if (t != Target.CYLINDER) {
      draw(Apotheneum.cube.exterior, drift, tEvolve, dens, sharp, depthAmt, base, shadow);
    }
    if (t != Target.CUBE) {
      draw(Apotheneum.cylinder.exterior, drift, tEvolve, dens, sharp, depthAmt, base, shadow);
    }
    copyExterior();
  }

  private void draw(Apotheneum.Orientation o, double drift, float tEvolve,
      double dens, double sharp, double depthAmt, int base, int shadow) {
    // fade the fine secondary layer out as density approaches Nyquist
    final double v2w = 0.35 * smoothstep(20.0, 12.0, dens);
    for (Apotheneum.Column column : o.columns()) {
      for (LXPoint p : column.points) {
        // warp field bends the horizontal streamlines (seamless in 3D world space)
        float warp = Noise.stb_perlin_fbm_noise3(
          (float) (p.xn * FX + drift), p.zn * FX, tEvolve, 2.0f, 0.5f, 3);
        double phase = (p.yn * dens - warp * WARP * (1 + getWow() * 1.4)) * TAU;
        double v = 0.5 + 0.5 * Math.cos(phase);
        double line = Math.pow(v, sharp);
        // a finer secondary layer for silky detail
        if (v2w > 0.01) {
          double v2 = 0.5 + 0.5 * Math.cos((p.yn * dens * 2.3 - warp * WARP) * TAU);
          line = Math.max(line, v2w * Math.pow(v2, sharp * 1.6));
        }

        // slow lighting field -> crests glow, hollows fall dark (depth)
        float lf = Noise.stb_perlin_fbm_noise3(
          (float) (p.xn * LX_F + drift * 0.5), p.zn * LX_F,
          (float) (p.yn * 0.8 + tEvolve * 0.6), 2.0f, 0.5f, 2);
        double light = LXUtils.lerp(1.0, LXUtils.clamp(0.5 + 0.75 * lf, 0.08, 1.0), depthAmt);

        double b = line * light;
        if (b <= 0.01) continue;
        int c0 = LXColor.lerp(shadow, base, (float) light);
        float ll = (float) (line * light);
        int c = LXColor.lerp(c0, LXColor.WHITE, ll * ll * 0.85f);
        this.colors[p.index] = LXColor.scaleBrightness(c, (float) LXUtils.clamp(b, 0, 1));
      }
    }
  }

  private static double smoothstep(double e0, double e1, double x) {
    double t = (x - e0) / (e1 - e0);
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
    return t * t * (3 - 2 * t);
  }
}
