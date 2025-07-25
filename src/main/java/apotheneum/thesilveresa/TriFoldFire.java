
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Tri-fold Fire")
public class TriFoldFire extends ApotheneumPattern {

  final CompoundParameter triangleSize = new CompoundParameter("Scale", 3, 1, 10);
  final CompoundParameter heat = new CompoundParameter("Heat", 0.5, 0, 1);
  final CompoundParameter distort = new CompoundParameter("Distort", 0.1, 0, 1);

  public TriFoldFire(LX lx) {
    super(lx);
    addParameter("scale", this.triangleSize);
    addParameter("heat", this.heat);
    addParameter("distort", this.distort);
  }

  @Override
  public void render(double deltaMs) {
    float size = triangleSize.getValuef();
    float time = lx.engine.nowMillis / 1000.f;

    for (LXPoint p : model.points) {
      float x = p.xn;
      float y = p.yn;

      // Apply minor distortion
      x += distort.getValuef() * (float)Math.sin(10 * y + time);
      y += distort.getValuef() * (float)Math.sin(10 * x + time);

      // Convert to triangle grid
      float qx = x * size;
      float qy = y * size * 2 / (float)Math.sqrt(3);
      int xi = (int)Math.floor(qx);
      int yi = (int)Math.floor(qy);

      // Barycentric-style edge detection
      float fx = qx - xi;
      float fy = qy - yi;
      boolean upward = (xi + yi) % 2 == 0;

      float edgeThreshold = 0.08f;
      float edge = 0;

      if (upward) {
        edge = Math.min(Math.min(fx, fy), 1 - fx - fy);
      } else {
        edge = Math.min(Math.min(fx, 1 - fy), fy - fx);
      }

      float rawBrightness = 100 * (1 - heat.getValuef() * edge / edgeThreshold);
      float brightness = LXUtils.clampf(rawBrightness, 0, 100);

      // Default to white for external colorization
      int color = LXColor.gray(brightness);
      colors[p.index] = color;
    }
  }
}
