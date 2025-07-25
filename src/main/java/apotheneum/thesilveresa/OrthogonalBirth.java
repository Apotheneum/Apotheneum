
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Orthogonal Birth")
public class OrthogonalBirth extends ApotheneumPattern {

  final CompoundParameter grid =
    new CompoundParameter("Grid", 0.3, 0.05, 0.5)
      .setDescription("Spacing between horizontal and vertical grid lines");

  final CompoundParameter width =
    new CompoundParameter("Width", 0.05, 0.005, 0.2)
      .setDescription("Thickness of the grid lines");

  final CompoundParameter brightness =
    new CompoundParameter("Bright", 1.0, 0.0, 1.0)
      .setDescription("Maximum brightness");

  final CompoundParameter rate =
    new CompoundParameter("Rate", 1.0, 0.1, 2.0)
      .setDescription("Speed of brightness pulsing");

  final CompoundParameter pulse =
    new CompoundParameter("Pulse", 0.5, 0.0, 1.0)
      .setDescription("Amount of brightness oscillation");

  final CompoundParameter phase =
    new CompoundParameter("Phase", 0.0, 0.0, 1.0)
      .setDescription("Offset of the brightness pulse phase");

  public OrthogonalBirth(LX lx) {
    super(lx);
    addParameter("grid", this.grid);
    addParameter("width", this.width);
    addParameter("Bright", this.brightness);
    addParameter("Rate", this.rate);
    addParameter("pulse", this.pulse);
    addParameter("Phase", this.phase);
  }

  @Override
  public void render(double deltaMs) {
    double t = lx.engine.nowMillis / 1000.0;
    double spacing = grid.getValue();
    double thickness = width.getValue();
    double pulseVal = Math.sin(2 * Math.PI * rate.getValue() * t + phase.getValue() * Math.PI * 2) * pulse.getValue();
    double bright = brightness.getValue() * (1.0 - pulse.getValue()) + pulseVal;

    for (LXPoint p : model.points) {
      double xn = p.xn;
      double yn = p.yn;

      double dx = Math.abs((xn / spacing) % 1.0 - 0.5);
      double dy = Math.abs((yn / spacing) % 1.0 - 0.5);

      boolean onX = dx < thickness;
      boolean onY = dy < thickness;

      if (onX || onY) {
        colors[p.index] = LXColor.gray(bright * 100);
      } else {
        colors[p.index] = LXColor.BLACK;
      }
    }
  }
}
