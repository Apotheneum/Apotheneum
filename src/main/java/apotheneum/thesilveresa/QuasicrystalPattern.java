package apotheneum.thesilveresa;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXComponentName("Quasicrystal")
@LXCategory("Apotheneum/thesilveresa")
public class QuasicrystalPattern extends ApotheneumPattern {

  public final BooleanParameter mirrorNSFaces =
    new BooleanParameter("Mirror North/South Faces", false);

  public final CompoundParameter symmetry =
    new CompoundParameter("Symmetry", 2, 1, 7);

  public final CompoundParameter scale =
    new CompoundParameter("Scale", 0.5);

  public final CompoundParameter rotation =
    new CompoundParameter("Rotation", 0.5);

  public final CompoundParameter hue1 =
    new CompoundParameter("Hue A", 0.6);

  public final CompoundParameter hue2 =
    new CompoundParameter("Hue B", 0.3);

  public final CompoundParameter hue3 =
    new CompoundParameter("Hue C", 0.1);

  public final CompoundParameter saturation =
    new CompoundParameter("Saturation", 0.85);

  // NOTE: if parameter range beyond 0-1 need to specify in constructor
  public final CompoundParameter brightness =
    new CompoundParameter("Brightness", 1.25, 0, 2);

  // NOTE: if parameter range beyond 0-1 need to specify in constructor
  public final CompoundParameter contrast =
    new CompoundParameter("Contrast", 1.1, 0, 2);

  public QuasicrystalPattern(LX lx) {
    super(lx);
    addParameter("mirrorNSFaces", this.mirrorNSFaces);
    addParameter("symmetry", this.symmetry);
    addParameter("scale", this.scale);
    addParameter("rotation", this.rotation);
    addParameter("hue1", this.hue1);
    addParameter("hue2", this.hue2);
    addParameter("hue3", this.hue3);
    addParameter("saturation", this.saturation);
    addParameter("brightness", this.brightness);
    addParameter("contrast", this.contrast);

  }

  @Override
  protected void render(double deltaMs) {
    final boolean mirrorNS = this.mirrorNSFaces.isOn();
    final double symmetryFactor = this.symmetry.getValue();
    final double effectiveScale = 0.2 + this.scale.getValue() * 1.5;
    final double angle = this.rotation.getValue() * Math.PI * 2;
    final double hue1 = this.hue1.getValue();
    final double hue2 = this.hue2.getValue();
    final double hue3 = this.hue3.getValue();
    final double sat = this.saturation.getValue();
    final double brt = this.brightness.getValue();
    final double contrast = this.contrast.getValue();

    setColors(LXColor.BLACK);

    for (LXPoint p : Apotheneum.cube.exterior.front.model.points) {
      double u = p.xn;
      double v = p.yn;

      double px = ((u * symmetryFactor * effectiveScale) % 1.0 - 0.5);
      double py = ((v * symmetryFactor * effectiveScale) % 1.0 - 0.5);

      double q = (2.0 / 3.0) * px;
      double r = (-1.0 / 3.0) * px + (Math.sqrt(3) / 3.0) * py;
      double s = -q - r;

      double tileValue = Math.cos(symmetryFactor * Math.atan2(s, q) + angle)
        + Math.cos(symmetryFactor * Math.atan2(r, s) + angle);

      double blend = 0.5 + 0.5 * Math.sin(tileValue * Math.PI);
      double hue = (blend < 0.5) ? (hue1 * (1 - 2 * blend) + hue2 * (2 * blend))
        : (hue2 * (2 - 2 * blend) + hue3 * (2 * blend - 1));

      // NOTE: need to constrain level
      double level = LXUtils.min(1, brt * Math.abs(tileValue) * contrast);

      this.colors[p.index] = LXColor.hsb(hue * 360, sat * 100, level * 100);
    }

    // Mirror logic: Copy to South face flipped if enabled, else copy as-is
    if (mirrorNS) {
      copyMirror(Apotheneum.cube.exterior.front, Apotheneum.cube.exterior.back);
    } else {
      copy(Apotheneum.cube.exterior.front, Apotheneum.cube.exterior.back);
    }

    // Copy to remaining faces
    copy(Apotheneum.cube.exterior.front, Apotheneum.cube.exterior.left);
    copy(Apotheneum.cube.exterior.front, Apotheneum.cube.exterior.right);

    // Copy exterior to interior
    copyCubeExterior();

    // TODO - cylinder needs generating separately, it has a different number of points, can't use copy()
    // from cube faces to the cylinder
  }
}