
package apotheneum.thesilveresa;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import apotheneum.ApotheneumPattern;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Aperiodic Bloom")
public class AperiodicBloom extends ApotheneumPattern {

  private final CompoundParameter scale =
    new CompoundParameter("Scale", 1, 0.1, 6)
    .setDescription("Zooms in or out on the pattern geometry");

  private final CompoundParameter tiling =
    new CompoundParameter("Tiling", 5, 1, 20)
    .setDescription("Controls repetition angular frequency");

  private final CompoundParameter hueShift =
    new CompoundParameter("Hue", 0, 0, 360)
    .setDescription("Rotates the color palette");

  public AperiodicBloom(LX lx) {
    super(lx);
    addParameter("scaleVariance", this.scale);
    addParameter("tilingKnob", this.tiling);
    addParameter("hueShift", this.hueShift);
  }

  @Override
  public void render(double deltaMs) {
    float s = scale.getValuef();
    float freq = tiling.getValuef();
    float hueBase = hueShift.getValuef();

    for (LXPoint p : model.points) {
      float angle = (float) Math.atan2(p.yn, p.xn);
      float radius = (float) Math.sqrt(p.yn * p.yn + p.xn * p.xn);

      float wave = (float) Math.sin(freq * angle + radius * s);
      float hue = (hueBase + wave * 60 + 360) % 360;

      colors[p.index] = LXColor.hsb(hue, 100, 100);
    }
  }
}
