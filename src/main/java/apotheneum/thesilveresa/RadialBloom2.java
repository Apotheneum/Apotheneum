
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.BooleanParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Radial Bloom 2")
public class RadialBloom2 extends ApotheneumPattern {

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

  private float time = 0;

  public RadialBloom2(LX lx) {
    super(lx);
    addParameter("scaleVariance", this.scale);
    addParameter("hueShift", this.hue);
    addParameter("phaseDrift", this.phase);
    addParameter("radialWarp", this.warp);
    addParameter("numPetals", this.symmetry);
    addParameter("bloomDir", this.inward);
    addParameter("radialShimmer", this.jitter);
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

    for (LXPoint p : model.points) {
      float x = p.xn - 0.5f;
      float y = p.yn - 0.5f;

      float r = (float)Math.sqrt(x*x + y*y);
      float theta = (float)Math.atan2(y, x);

      float jitterOffset = jitterAmt * (float)Math.sin(theta * 10 + time * 5);
      float bloom = (float)Math.sin((inwardBloom ? -1 : 1) * k * r + warpFactor * Math.sin(theta * arms) + drift + jitterOffset);
      float brightness = 100 * Math.abs(bloom);
      float hueVal = (baseHue + bloom * 120 + 360) % 360;

      colors[p.index] = LXColor.hsba(hueVal, 100, brightness, 100);
    }
  }
}
