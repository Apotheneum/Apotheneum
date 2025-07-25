
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Grid Resonance")
public class GridResonance extends ApotheneumPattern {

  final CompoundParameter grid =
    new CompoundParameter("Grid", 0.2, 0.01, 0.5)
      .setDescription("Spacing between grid lines (lower = more lines)");

  final CompoundParameter width =
    new CompoundParameter("Width", 0.005, 0.0005, 0.05)
      .setDescription("Line thickness (LED band width)");

  final CompoundParameter tension =
    new CompoundParameter("Tension", 0.0, -5.0, 2.0)
      .setDescription("Nonlinear spatial warping around center");

  final CompoundParameter phase =
    new CompoundParameter("Phase", 0.0, 0.0, 1.0)
      .setDescription("Phase offset for dynamic distortion");

  final CompoundParameter contrast =
    new CompoundParameter("Contrast", 1.0, 0.1, 2.0)
      .setDescription("Brightness contrast multiplier");

  public GridResonance(LX lx) {
    super(lx);
    addParameter("grid", this.grid);
    addParameter("width", this.width);
    addParameter("tension", this.tension);
    addParameter("phase", this.phase);
    addParameter("contrast", this.contrast);
  }

  @Override
  public void render(double deltaMs) {
    double spacing = grid.getValue();
    double thickness = width.getValue();
    double tensionVal = tension.getValue();
    double phaseVal = phase.getValue();
    double contrastVal = contrast.getValue();
    double density = 1.0 / spacing;

    for (LXPoint p : model.points) {
      double xn = p.xn;
      double yn = p.yn;

      // Nonlinear warp centered at 0.5
      double xWarp = 0.5 + (xn - 0.5) * Math.pow(2.0, tensionVal * (xn - 0.5));
      double yWarp = 0.5 + (yn - 0.5) * Math.pow(2.0, tensionVal * (yn - 0.5));

      double xMod = (xWarp * density + phaseVal) % 1.0;
      double yMod = (yWarp * density + phaseVal) % 1.0;

      boolean onX = xMod < thickness || xMod > 1.0 - thickness;
      boolean onY = yMod < thickness || yMod > 1.0 - thickness;

      double brightness = (onX || onY) ? 100 * contrastVal : 0;
      colors[p.index] = LXColor.gray(brightness);
    }
  }
}
