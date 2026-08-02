package apotheneum.drmrrdmr;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

/**
 * Performance-optimized fork of thesilveresa's Radial Bloom 1, kept as its
 * own pattern (original untouched) so existing shows referencing it are
 * unaffected.
 *
 * The original computes, per point, per frame: sqrt(x²+y²), atan2(y,x), and
 * two Math.sin() calls (one for the inner sin(theta*3) warp term, one for
 * the outer bloom wave) - over all ~13,280 points in the model, every frame.
 * atan2 in particular is typically the single most expensive call here.
 *
 * sin(3*theta) is eliminated exactly (not approximated) via the triple-angle
 * identity sin(3x) = sin(x)*(3 - 4sin(x)^2), applied to sinTheta = y/r and
 * cosTheta = x/r - which are already needed anyway and are far cheaper to
 * get than a full atan2. That removes atan2 and one of the two Math.sin
 * calls entirely, with no change to the output. The outer/primary bloom
 * sine (which directly shapes the visible banding) is left as an accurate
 * Math.sin() call rather than an approximation.
 *
 * New: a Sat knob (the original hardcodes full saturation).
 */
@LXCategory("Apotheneum/drmrrdmr")
@LXComponent.Name("Radial Bloom 1 Desat")
public class RadialBloom1Desat extends ApotheneumPattern {

  final CompoundParameter scale =
    new CompoundParameter("Scale", 1.5, 0.1, 5.0)
    .setDescription("Radial band frequency");

  final CompoundParameter hue =
    new CompoundParameter("Hue", 0, 0, 360)
    .setDescription("Base hue shift");

  final CompoundParameter phase =
    new CompoundParameter("Bloom", 0, 0, 10)
    .setDescription("Speed of radial bloom oscillation");

  final CompoundParameter warp =
    new CompoundParameter("Warp", 0.5, 0, 2)
    .setDescription("Distortion of radial axis for symmetry warping");

  final CompoundParameter saturation =
    new CompoundParameter("Sat", 100, 0, 100)
    .setDescription("Color saturation");

  private float time = 0;

  private static final float MIN_R = 1e-6f;

  public RadialBloom1Desat(LX lx) {
    super(lx);
    addParameter("scaleVariance", this.scale);
    addParameter("hueShift", this.hue);
    addParameter("phaseBloom", this.phase);
    addParameter("radialWarp", this.warp);
    addParameter("saturation", this.saturation);
  }

  @Override
  public void render(double deltaMs) {
    time += deltaMs / 1000.0;
    float k = scale.getValuef();
    float baseHue = hue.getValuef();
    float phaseShift = phase.getValuef() * time;
    float warpFactor = warp.getValuef();
    float sat = saturation.getValuef();

    for (LXPoint p : model.points) {
      float x = p.xn - 0.5f;
      float y = p.yn - 0.5f;

      float r = (float)Math.sqrt(x*x + y*y);

      // sinTheta/cosTheta replace atan2(y,x) - same direction information,
      // much cheaper, and exact (r > MIN_R guards the undefined direction at
      // the exact center, matching atan2(0,0) == 0 -> sin=0, cos=1).
      float invR = (r > MIN_R) ? (1f / r) : 0f;
      float sinTheta = y * invR;
      float cosTheta = (r > MIN_R) ? (x * invR) : 1f;

      // Exact triple-angle identity for sin(3*theta) - no atan2, no second
      // Math.sin() call, and not an approximation: sin(3x) = 3sin(x) - 4sin(x)^3.
      float sin3Theta = sinTheta * (3f - 4f * sinTheta * sinTheta);

      float warped = (float)Math.sin(k * r + warpFactor * sin3Theta + phaseShift);
      float brightness = 100 * Math.abs(warped);

      float hueVal = (baseHue + warped * 120 + 360) % 360;
      colors[p.index] = LXColor.hsba(hueVal, sat, brightness, 100);
    }
  }
}
