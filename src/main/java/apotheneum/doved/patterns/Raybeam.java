package apotheneum.doved.patterns;

import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

/**
 * Evaluates axis-aligned distance fields after moving each point into a translated,
 * rotated and scaled local coordinate frame.
 *
 * <p>The local Y axis is the axis of {@link Shape#LINE}, {@link Shape#RAY},
 * {@link Shape#CYLINDER} and {@link Shape#CONE}. Azimuth turns that axis around world Y;
 * elevation aims it from straight down through the horizon to straight up. The orientation
 * basis is computed once per frame, outside the point loop.
 *
 * <p>The pattern iterates {@link #model}, the model selected by the standard LX device view
 * selector. It clears the full output before drawing that view so switching views never leaves
 * pixels from the previous selection behind.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Raybeam")
@LXComponent.Description("Renders translated and aimed 3D distance-field shapes")
public class Raybeam extends LXPattern
  implements UIDeviceControls<Raybeam> {

  private static final double TWO_PI = 2 * Math.PI;
  private static final double HALF_PI = Math.PI / 2;

  public enum Shape {
    POINT("Point") {
      @Override
      double distance(
        double x, double y, double z, double radius, double coneSin, double coneCos) {
        return length(x, y, z);
      }
    },
    LINE("Line") {
      @Override
      double distance(
        double x, double y, double z, double radius, double coneSin, double coneCos) {
        return Math.sqrt(x * x + z * z);
      }
    },
    RAY("Ray") {
      @Override
      double distance(
        double x, double y, double z, double radius, double coneSin, double coneCos) {
        return (y >= 0) ? Math.sqrt(x * x + z * z) : length(x, y, z);
      }
    },
    PLANE("Plane") {
      @Override
      double distance(
        double x, double y, double z, double radius, double coneSin, double coneCos) {
        return Math.abs(y);
      }
    },
    CYLINDER("Cylinder") {
      @Override
      double distance(
        double x, double y, double z, double radius, double coneSin, double coneCos) {
        return Math.max(0, Math.sqrt(x * x + z * z) - radius);
      }
    },
    SPHERE("Sphere") {
      @Override
      double distance(
        double x, double y, double z, double radius, double coneSin, double coneCos) {
        return Math.max(0, length(x, y, z) - radius);
      }
    },
    CONE("Cone") {
      @Override
      double distance(
        double x, double y, double z, double radius, double coneSin, double coneCos) {

        final double radial = Math.sqrt(x * x + z * z);
        final double projection = y * coneCos + radial * coneSin;
        if (projection <= 0) {
          return length(x, y, z);
        }
        return Math.max(0, radial * coneCos - y * coneSin);
      }
    };

    private final String label;

    Shape(String label) {
      this.label = label;
    }

    abstract double distance(
      double x, double y, double z, double radius, double coneSin, double coneCos);

    boolean usesRadius() {
      return (this == CYLINDER) || (this == SPHERE);
    }

    boolean usesConeAngle() {
      return this == CONE;
    }

    private static double length(double x, double y, double z) {
      return Math.sqrt(x * x + y * y + z * z);
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum Falloff {
    LINEAR("Linear") {
      @Override
      double profile(double normalizedDistance) {
        return 1 - normalizedDistance;
      }
    },
    EXPONENTIAL("Exponential") {
      private static final double EDGE = Math.exp(-4.5);

      @Override
      double profile(double normalizedDistance) {
        return (Math.exp(-4.5 * normalizedDistance) - EDGE) / (1 - EDGE);
      }
    },
    GAUSSIAN("Gaussian") {
      private static final double EDGE = Math.exp(-4.5);

      @Override
      double profile(double normalizedDistance) {
        return (Math.exp(-4.5 * normalizedDistance * normalizedDistance) - EDGE) / (1 - EDGE);
      }
    };

    private final String label;

    Falloff(String label) {
      this.label = label;
    }

    abstract double profile(double normalizedDistance);

    double brightness(double distance, double width, double softness) {
      if (distance <= 0) {
        return 1;
      }
      if (distance >= width) {
        return 0;
      }
      final double feather = width * softness;
      if (feather <= 0) {
        return 1;
      }
      final double fadeStart = width - feather;
      if (distance <= fadeStart) {
        return 1;
      }
      return LXUtils.constrain(profile((distance - fadeStart) / feather), 0, 1);
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  /** Applies signed concentric bands while preserving the distance-field envelope. */
  static final class Ripple {
    private Ripple() {}

    static double brightness(double envelope, double distance, double amount,
      double spacing, double phase, double sharpness, double decay) {

      if (amount <= 0 || envelope <= 0) {
        return envelope;
      }

      final double wave = Math.cos(TWO_PI * (distance / spacing - phase));
      final double shapedWave = Math.copySign(Math.pow(Math.abs(wave), sharpness), wave);
      final double amplitude = amount * Math.pow(envelope, decay);
      return LXUtils.constrain(envelope + amplitude * shapedWave, 0, 1);
    }
  }

  public final CompoundParameter originX = normalized("Origin X", .5,
    "World X coordinate of the local origin");
  public final CompoundParameter originY = normalized("Origin Y", .5,
    "World Y coordinate of the local origin");
  public final CompoundParameter originZ = normalized("Origin Z", .5,
    "World Z coordinate of the local origin");

  public final CompoundParameter azimuth =
    new CompoundParameter("Azimuth", 0)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setWrappable(true)
    .setDescription("Direction around world Y, mapping 0-1 to 0-2pi");

  public final CompoundParameter elevation =
    new CompoundParameter("Elevation", 0, -HALF_PI, HALF_PI)
    .setUnits(LXParameter.Units.RADIANS)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Vertical aim from -pi/2 to +pi/2 radians");

  public final EnumParameter<Shape> shape =
    new EnumParameter<Shape>("Shape", Shape.LINE)
    .setDescription("Distance-field shape evaluated in local coordinates");

  public final CompoundParameter width =
    new CompoundParameter("Width", .1, .001, 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Distance outside an open shape or solid boundary where brightness reaches zero");

  public final EnumParameter<Falloff> falloff =
    new EnumParameter<Falloff>("Falloff", Falloff.LINEAR)
    .setDescription("Brightness profile between the shape and its width boundary");

  public final CompoundParameter softness =
    new CompoundParameter("Softness", 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Fraction of width used to feather the transition");

  public final CompoundParameter rippleAmount =
    new CompoundParameter("Ripple Amt", .35)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strength of concentric brightness ripples within the field");

  public final CompoundParameter rippleSpacing =
    new CompoundParameter("Ripple Gap", .08, .005, .5)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Distance between successive ripple crests");

  public final CompoundParameter ripplePhase =
    new CompoundParameter("Ripple Phase", 0)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setWrappable(true)
    .setDescription("Modulate upward from 0 to 1 to move ripples away from the shape");

  public final CompoundParameter rippleSharpness =
    new CompoundParameter("Ripple Sharp", 1, .25, 4)
    .setDescription("Shape of ripple bands; higher values make them narrower");

  public final CompoundParameter rippleDecay =
    new CompoundParameter("Ripple Decay", 1.5, .1, 8)
    .setDescription("How quickly ripple amplitude fades toward the field boundary");

  public final CompoundParameter radius =
    new CompoundParameter("Radius", .25, 0, 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Radius of the filled cylinder and sphere");

  public final CompoundParameter coneAngle =
    new CompoundParameter("Cone Angle", Math.PI / 4, 0, HALF_PI)
    .setUnits(LXParameter.Units.RADIANS)
    .setDescription("Opening angle from the local positive Y axis");

  public final CompoundParameter scaleX = scale("Scale X");
  public final CompoundParameter scaleY = scale("Scale Y");
  public final CompoundParameter scaleZ = scale("Scale Z");

  private final LocalFrame frame = new LocalFrame();

  public Raybeam(LX lx) {
    super(lx);
    addParameter("originX", this.originX);
    addParameter("originY", this.originY);
    addParameter("originZ", this.originZ);
    addParameter("azimuth", this.azimuth);
    addParameter("elevation", this.elevation);
    addParameter("shape", this.shape);
    addParameter("width", this.width);
    addParameter("falloff", this.falloff);
    addParameter("softness", this.softness);
    addParameter("rippleAmount", this.rippleAmount);
    addParameter("rippleSpacing", this.rippleSpacing);
    addParameter("ripplePhase", this.ripplePhase);
    addParameter("rippleSharpness", this.rippleSharpness);
    addParameter("rippleDecay", this.rippleDecay);
    addParameter("radius", this.radius);
    addParameter("coneAngle", this.coneAngle);
    addParameter("scaleX", this.scaleX);
    addParameter("scaleY", this.scaleY);
    addParameter("scaleZ", this.scaleZ);
  }

  private static CompoundParameter normalized(String label, double value, String description) {
    return new CompoundParameter(label, value)
      .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
      .setDescription(description);
  }

  private static CompoundParameter scale(String label) {
    return new CompoundParameter(label, 1, .1, 4)
      .setDescription("Local domain scale; 1 leaves this axis unchanged");
  }

  @Override
  protected void run(double deltaMs) {
    clearColors();

    this.frame.update(
      this.originX.getValue(), this.originY.getValue(), this.originZ.getValue(),
      this.azimuth.getValue() * TWO_PI, this.elevation.getValue(),
      this.scaleX.getValue(), this.scaleY.getValue(), this.scaleZ.getValue());

    final Shape shape = this.shape.getEnum();
    final Falloff falloff = this.falloff.getEnum();
    final double radius = this.radius.getValue();
    final double coneAngle = this.coneAngle.getValue();
    final double coneSin = Math.sin(coneAngle);
    final double coneCos = Math.cos(coneAngle);
    final double width = this.width.getValue();
    final double softness = this.softness.getValue();
    final double rippleAmount = this.rippleAmount.getValue();
    final double rippleSpacing = this.rippleSpacing.getValue();
    final double ripplePhase = this.ripplePhase.getValue();
    final double rippleSharpness = this.rippleSharpness.getValue();
    final double rippleDecay = this.rippleDecay.getValue();

    for (LXPoint point : this.model.points) {
      final double x = this.frame.localX(point.xn, point.yn, point.zn);
      final double y = this.frame.localY(point.xn, point.yn, point.zn);
      final double z = this.frame.localZ(point.xn, point.yn, point.zn);
      final double distance = shape.distance(x, y, z, radius, coneSin, coneCos);
      final double envelope = falloff.brightness(distance, width, softness);
      this.colors[point.index] = LXColor.grayn(Ripple.brightness(
        envelope, distance, rippleAmount, rippleSpacing, ripplePhase,
        rippleSharpness, rippleDecay));
    }
  }

  @Override
  public void buildDeviceControls(
    UI ui, UIDevice uiDevice, Raybeam raybeam) {

    final UIKnob radiusKnob = newKnob(raybeam.radius);
    radiusKnob.setEnabled(raybeam.shape.getEnum().usesRadius());
    radiusKnob.addListener(raybeam.shape,
      parameter -> radiusKnob.setEnabled(raybeam.shape.getEnum().usesRadius()));

    final UIKnob coneAngleKnob = newKnob(raybeam.coneAngle);
    coneAngleKnob.setEnabled(raybeam.shape.getEnum().usesConeAngle());
    coneAngleKnob.addListener(raybeam.shape,
      parameter -> coneAngleKnob.setEnabled(raybeam.shape.getEnum().usesConeAngle()));

    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 6);
    addColumn(uiDevice, "Origin",
      newKnob(raybeam.originX),
      newKnob(raybeam.originY),
      newKnob(raybeam.originZ));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Aim",
      newKnob(raybeam.azimuth),
      newKnob(raybeam.elevation));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Shape",
      newDropMenu(raybeam.shape),
      radiusKnob,
      coneAngleKnob);

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Edge",
      newDropMenu(raybeam.falloff),
      newKnob(raybeam.width),
      newKnob(raybeam.softness));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Ripple",
      newKnob(raybeam.rippleAmount),
      newKnob(raybeam.rippleSpacing),
      newKnob(raybeam.ripplePhase));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Ripple Shape",
      newKnob(raybeam.rippleSharpness),
      newKnob(raybeam.rippleDecay));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Scale",
      newKnob(raybeam.scaleX),
      newKnob(raybeam.scaleY),
      newKnob(raybeam.scaleZ));
  }

  /** Precomputed inverse transform from normalized world coordinates into local space. */
  static final class LocalFrame {
    private double originX;
    private double originY;
    private double originZ;
    private double xx;
    private double xz;
    private double yx;
    private double yy;
    private double yz;
    private double zx;
    private double zy;
    private double zz;

    void update(double originX, double originY, double originZ,
      double azimuth, double elevation, double scaleX, double scaleY, double scaleZ) {

      this.originX = originX;
      this.originY = originY;
      this.originZ = originZ;

      final double sinAzimuth = Math.sin(azimuth);
      final double cosAzimuth = Math.cos(azimuth);
      final double sinElevation = Math.sin(elevation);
      final double cosElevation = Math.cos(elevation);

      // Rows of the inverse rotation matrix are the local basis vectors in world space.
      this.xx = cosAzimuth / scaleX;
      this.xz = -sinAzimuth / scaleX;
      this.yx = sinAzimuth * cosElevation / scaleY;
      this.yy = sinElevation / scaleY;
      this.yz = cosAzimuth * cosElevation / scaleY;
      this.zx = sinAzimuth * sinElevation / scaleZ;
      this.zy = -cosElevation / scaleZ;
      this.zz = cosAzimuth * sinElevation / scaleZ;
    }

    double localX(double x, double y, double z) {
      return this.xx * (x - this.originX) + this.xz * (z - this.originZ);
    }

    double localY(double x, double y, double z) {
      return this.yx * (x - this.originX) + this.yy * (y - this.originY)
        + this.yz * (z - this.originZ);
    }

    double localZ(double x, double y, double z) {
      return this.zx * (x - this.originX) + this.zy * (y - this.originY)
        + this.zz * (z - this.originZ);
    }
  }
}
