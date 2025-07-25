
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Radial Bloom 1")
public class RadialBloom1 extends ApotheneumPattern {

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

    private float time = 0;

    public RadialBloom1(LX lx) {
      super(lx);
      addParameter("scaleVariance", this.scale);
      addParameter("hueShift", this.hue);
      addParameter("phaseBloom", this.phase);
      addParameter("radialWarp", this.warp);
    }

    @Override
    public void render(double deltaMs) {
      time += deltaMs / 1000.0;
      float k = scale.getValuef();
      float baseHue = hue.getValuef();
      float phaseShift = phase.getValuef() * time;
      float warpFactor = warp.getValuef();

      for (LXPoint p : model.points) {
        float x = p.xn - 0.5f;
        float y = p.yn - 0.5f;

        float r = (float)Math.sqrt(x*x + y*y);
        float theta = (float)Math.atan2(y, x);

        float warped = (float)Math.sin(k * r + warpFactor * Math.sin(theta * 3) + phaseShift);
        float brightness = 100 * Math.abs(warped);

        float hueVal = (baseHue + warped * 120 + 360) % 360;
        colors[p.index] = LXColor.hsba(hueVal, 100, brightness, 100);
      }
    }
  }
