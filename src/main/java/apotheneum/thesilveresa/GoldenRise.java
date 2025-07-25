
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Golden Rise")
public class GoldenRise extends ApotheneumPattern {

  final CompoundParameter spacing =
    new CompoundParameter("Space", 0.05, 0.01, 0.2)
      .setDescription("Vertical spacing between rows");

  final CompoundParameter offset =
    new CompoundParameter("Offset", 0.5, 0.0, 1.0)
      .setDescription("Phase offset between rows");

  final CompoundParameter phiShift =
    new CompoundParameter("PhiShift", 0.0, 0.0, 1.0)
      .setDescription("Blend between uniform and golden-ratio brick widths");

  final CompoundParameter rise =
    new CompoundParameter("Rise", 1.0, 0.0, 1.0)
      .setDescription("Vertical reveal of the structure");

  final CompoundParameter contrast =
    new CompoundParameter("Contrast", 1.0, 0.1, 2.0)
      .setDescription("Brightness contrast of the brick pattern");

  final CompoundParameter time =
    new CompoundParameter("Time", 1.0, 0.0, 2.0)
      .setDescription("Speed of animated brick growth");

  final CompoundParameter curve =
    new CompoundParameter("Curve", 0.0, 0.0, 1.0)
      .setDescription("Amount of cylindrical wall curvature");

  final CompoundParameter spiral =
    new CompoundParameter("Spiral", 0.0, 0.0, 2.0)
      .setDescription("Phyllotactic spiral twist amount");

  public GoldenRise(LX lx) {
    super(lx);
    addParameter("spacing", this.spacing);
    addParameter("offset", this.offset);
    addParameter("phiShift", this.phiShift);
    addParameter("rise", this.rise);
    addParameter("contrast", this.contrast);
    addParameter("time", this.time);
    addParameter("curve", this.curve);
    addParameter("spiral", this.spiral);
  }

  @Override
  public void render(double deltaMs) {
    double rowSpacing = spacing.getValue();
    double rowOffset = offset.getValue();
    double phiBlend = phiShift.getValue();
    double phi = 1.61803398875;
    double reveal = rise.getValue();
    double contrastVal = contrast.getValue();
    double t = lx.engine.nowMillis / 1000.0;
    double buildTime = time.getValue();
    double curvature = curve.getValue();
    double twist = spiral.getValue();

    for (LXPoint p : model.points) {
      double xn = p.xn;
      double yn = p.yn;

      if (yn > reveal) {
        colors[p.index] = LXColor.BLACK;
        continue;
      }

      // Curved X position (cylindrical wrap)
      double curvedX = 0.5 + 0.5 * Math.sin((xn - 0.5) * Math.PI * 2.0 * curvature);

      // Row and spiral offset
      double row = Math.floor(yn / rowSpacing);
      boolean oddRow = ((int) row) % 2 == 1;
      double rowShift = row * twist;
      double brickWidth = (1.0 - phiBlend) + phiBlend * phi;

      // X with brick staggering and spiral twist
      double shiftedX = curvedX + (oddRow ? rowOffset * brickWidth : 0.0) + rowShift;

      // Brick boundary
      double modX = (shiftedX % brickWidth) / brickWidth;
      boolean inBrick = modX < 0.9;

      // Brick build animation per row
      double rowStart = row * 0.05; // Staggered timing
      double built = Math.min(1.0, (t - rowStart) / buildTime);
      built = Math.max(0, built);

      double brightness = inBrick ? 100 * contrastVal * built : 0;
      colors[p.index] = LXColor.gray(brightness);
    }
  }
}
