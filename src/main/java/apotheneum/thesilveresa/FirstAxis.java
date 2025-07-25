package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("First Axis")
public class FirstAxis extends ApotheneumPattern {

  final CompoundParameter symmetry =
    new CompoundParameter("Sym", 0.0, 0.0, 1.0)
      .setDescription("Fade in vertical axis to form a cross");

  final CompoundParameter lineWidth =
    new CompoundParameter("Width", 0.05, 0.01, 1.0)
      .setDescription("Thickness of axis lines");

  final CompoundParameter brightness =
    new CompoundParameter("Bright", 1.0, 0.0, 1.0)
      .setDescription("Brightness of the axis lines");

  public FirstAxis(LX lx) {
    super(lx);
    addParameter("symmetry", this.symmetry);
    addParameter("lineWidth", this.lineWidth);
    addParameter("brightness", this.brightness);
  }

  @Override
  public void render(double deltaMs) {
    double w = lineWidth.getValue();
    double b = brightness.getValue();
    double s = symmetry.getValue();

    for (LXPoint p : model.points) {
      // Horizontal axis = bright when Y ~ 0
      double horiz = Math.max(0.0, 1.0 - Math.abs(p.yn) / w);

      // Vertical axis = bright when X ~ 0
      double vert = Math.max(0.0, 1.0 - Math.abs(p.xn) / w);

      // Blend in vertical axis with symmetry knob
      double value = horiz + s * vert;

      double level = Math.min(1.0, value) * b * 100;
      colors[p.index] = LXColor.gray(level);
    }
  }
}
