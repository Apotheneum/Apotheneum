
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Forming Order")
public class FormingOrder extends ApotheneumPattern {

  final CompoundParameter grid =
    new CompoundParameter("Grid", 0.2, 0.01, 0.5)
      .setDescription("Spacing between grid lines (lower = more lines)");

  final CompoundParameter width =
    new CompoundParameter("Width", 0.005, 0.0005, 0.05)
      .setDescription("Line thickness (LED band width)");

  final CompoundParameter skew =
    new CompoundParameter("Skew", 0.0, 0.0, 1.0)
      .setDescription("Morph between square and isometric skewing");

  public FormingOrder(LX lx) {
    super(lx);
    addParameter("grid", this.grid);
    addParameter("width", this.width);
    addParameter("skew", this.skew);
  }

  @Override
  public void render(double deltaMs) {
    double t = lx.engine.nowMillis / 1000.0;

    float gridSpacing = grid.getValuef();
    float halfWidth = width.getValuef() / 2;
    float skewFactor = skew.getValuef();

    for (LXPoint p : model.points) {
      float xn = p.xn - 0.5f;
      float yn = p.yn - 0.5f;

      // Isometric morphing projection
      float isoX = xn * (1 - skewFactor) + (xn - yn) * skewFactor * 0.5f;
      float isoY = yn * (1 - skewFactor) + (xn + yn) * skewFactor * 0.5f * 0.577f;

      float gx = isoX / gridSpacing;
      float gy = isoY / gridSpacing;

      float fx = gx - Math.round(gx);
      float fy = gy - Math.round(gy);

      float d = Math.min(Math.abs(fx), Math.abs(fy));

      if (d < halfWidth / gridSpacing) {
        setColor(p.index, LXColor.gray(100 + (int)(155 * (1 - d * gridSpacing / halfWidth))));
      } else {
        setColor(p.index, LXColor.BLACK);
      }
    }
  }
}
