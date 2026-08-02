package apotheneum.drmrrdmr;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.BooleanParameter;

/**
 * Performance-optimized fork of thesilveresa's Radial Bloom 2, kept as its
 * own pattern (original untouched) so existing shows referencing it are
 * unaffected.
 *
 * The original computes, per point, per frame: sqrt(x²+y²), atan2(y,x), and
 * three Math.sin() calls (jitter, warp, and the outer bloom wave) over all
 * ~13,280 points every frame. atan2 is typically the single most expensive
 * call here, and Petals (arms) plus the fixed x10 jitter multiple both
 * needed their own full sin() evaluation.
 *
 * All of that is eliminated exactly (not approximated), using only
 * sinTheta = y/r and cosTheta = x/r (already needed anyway, far cheaper
 * than atan2):
 *  - sin(arms*theta): built via the angle-addition recurrence (cos((n+1)x) =
 *    cos(nx)cos(x) - sin(nx)sin(x), etc.) - exact for any integer arms, and
 *    arms only ranges 1-12, so the recurrence is a handful of multiply-adds.
 *  - sin(10*theta + time*5): time*5 is the same for every pixel this frame,
 *    so its sin/cos are computed once per frame, not per pixel. Then
 *    sin(10*theta + timePhase) = sin(10*theta)*cos(timePhase) +
 *    cos(10*theta)*sin(timePhase) (angle-addition), with sin/cos(10*theta)
 *    from the same per-pixel recurrence used for the arms term.
 *
 * The outer/primary bloom sine (which directly shapes the visible banding)
 * is left as an accurate Math.sin() call rather than an approximation.
 *
 * New: a Sat knob (the original hardcodes full saturation).
 */
@LXCategory("Apotheneum/drmrrdmr")
@LXComponent.Name("Radial Bloom 2 Desat")
public class RadialBloom2Desat extends ApotheneumPattern {

  final CompoundParameter scale =
    new CompoundParameter("Scale", 1.5, 0.1, 5.0)
    .setDescription("Radial band frequency");

  final CompoundParameter hue =
    new CompoundParameter("Hue", 0, 0, 360)
    .setDescription("Base hue shift");

  final CompoundParameter phase =
    new CompoundParameter("Phase", 0, 0, 5)
    .setDescription("Rate of phase drift");

  final CompoundParameter warp =
    new CompoundParameter("Warp", 0.5, 0, 2)
    .setDescription("Distortion of radial axis for symmetry warping");

  final DiscreteParameter symmetry =
    new DiscreteParameter("Petals", 3, 1, 12)
    .setDescription("Number of radial petals or arms");

  final BooleanParameter inward =
    new BooleanParameter("Bloom", true)
    .setDescription("Toggles between inward and outward bloom");

  final CompoundParameter jitter =
    new CompoundParameter("Shimmer", 0, 0, 1)
    .setDescription("Adds shimmer to radius");

  final CompoundParameter saturation =
    new CompoundParameter("Sat", 100, 0, 100)
    .setDescription("Color saturation");

  private float time = 0;

  private static final float MIN_R = 1e-6f;
  private static final int JITTER_MULTIPLE = 10;

  public RadialBloom2Desat(LX lx) {
    super(lx);
    addParameter("scaleVariance", this.scale);
    addParameter("hueShift", this.hue);
    addParameter("phaseDrift", this.phase);
    addParameter("radialWarp", this.warp);
    addParameter("numPetals", this.symmetry);
    addParameter("bloomDir", this.inward);
    addParameter("radialShimmer", this.jitter);
    addParameter("saturation", this.saturation);
  }

  @Override
  public void render(double deltaMs) {
    time += deltaMs / 1000.0;
    float k = scale.getValuef();
    float baseHue = hue.getValuef();
    float drift = phase.getValuef() * time;
    float warpFactor = warp.getValuef();
    float jitterAmt = jitter.getValuef();
    int arms = symmetry.getValuei();
    boolean inwardBloom = inward.getValueb();
    float sat = saturation.getValuef();
    float radialSign = inwardBloom ? -1f : 1f;

    // time*5 is identical for every pixel this frame - its sin/cos are
    // computed once here rather than inside a per-pixel Math.sin() call.
    float jitterPhase = time * 5f;
    float sinJitterPhase = (float) Math.sin(jitterPhase);
    float cosJitterPhase = (float) Math.cos(jitterPhase);

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

      // sin(arms*theta), built via the angle-addition recurrence - exact for
      // any integer arms, no atan2 or extra Math.sin() call needed.
      float sinArmsTheta = sinOfMultiple(sinTheta, cosTheta, arms);

      // sin(10*theta + jitterPhase): same recurrence for sin/cos(10*theta),
      // then angle-addition with the once-per-frame jitterPhase sin/cos.
      float s10 = 0f, c10 = 1f;
      for (int i = 0; i < JITTER_MULTIPLE; i++) {
        float ns = s10 * cosTheta + c10 * sinTheta;
        c10 = c10 * cosTheta - s10 * sinTheta;
        s10 = ns;
      }
      float jitterOffset = jitterAmt * (s10 * cosJitterPhase + c10 * sinJitterPhase);

      float bloom = (float)Math.sin(radialSign * k * r + warpFactor * sinArmsTheta + drift + jitterOffset);
      float brightness = 100 * Math.abs(bloom);
      float hueVal = (baseHue + bloom * 120 + 360) % 360;

      colors[p.index] = LXColor.hsba(hueVal, sat, brightness, 100);
    }
  }

  // Computes sin(n*theta) from sin(theta)/cos(theta) via the angle-addition
  // recurrence (cos((i+1)x) = cos(ix)cos(x) - sin(ix)sin(x), etc.) - exact,
  // not an approximation, and cheap for the small n values this pattern uses.
  private static float sinOfMultiple(float sinTheta, float cosTheta, int n) {
    float s = 0f, c = 1f;
    for (int i = 0; i < n; i++) {
      float ns = s * cosTheta + c * sinTheta;
      c = c * cosTheta - s * sinTheta;
      s = ns;
    }
    return s;
  }
}
