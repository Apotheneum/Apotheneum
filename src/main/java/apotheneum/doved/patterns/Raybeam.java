package apotheneum.doved.patterns;

import java.util.IdentityHashMap;
import java.util.Map;

import apotheneum.doved.ui.UIRaybeam;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXNormalizationBounds;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
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
 * {@link Shape#CYLINDER}, {@link Shape#TORUS} and {@link Shape#CONE}. Azimuth turns that
 * axis around world Y; elevation aims it from straight down through the horizon to straight
 * up. Azimuth Offset adds a second, independently modulatable turn around world Y. The
 * orientation basis is computed once per frame, outside the point loop.
 * Coordinate mode may preserve the selected view's historical per-axis normalization or
 * use one physical scale for all axes so angles and round shapes remain geometrically true.
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
  private static final double CONE_HALF_SPACE_EPSILON = 1e-12;

  public enum Shape {
    POINT("Point") {
      @Override
      double distance(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {
        return length(x, y, z);
      }
    },
    LINE("Line") {
      @Override
      double distance(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {
        return Math.sqrt(x * x + z * z);
      }
    },
    RAY("Ray") {
      @Override
      double distance(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {
        return (y >= 0) ? Math.sqrt(x * x + z * z) : length(x, y, z);
      }
    },
    PLANE("Plane") {
      @Override
      double distance(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {
        return Math.abs(y);
      }
    },
    CYLINDER("Cylinder") {
      @Override
      double distance(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {
        return Math.max(0, Math.sqrt(x * x + z * z) - radius);
      }

      @Override
      boolean contains(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {
        return Math.sqrt(x * x + z * z) < radius;
      }
    },
    SPHERE("Sphere") {
      @Override
      double distance(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {
        return Math.max(0, length(x, y, z) - radius);
      }

      @Override
      boolean contains(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {
        return length(x, y, z) < radius;
      }
    },
    TORUS("Torus") {
      @Override
      double distance(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {

        final double radialOffset = Math.sqrt(x * x + z * z) - radius;
        final double centerlineDistance = Math.sqrt(radialOffset * radialOffset + y * y);
        return Math.max(0, centerlineDistance - minorRadius);
      }

      @Override
      boolean contains(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {

        final double radialOffset = Math.sqrt(x * x + z * z) - radius;
        return Math.sqrt(radialOffset * radialOffset + y * y) < minorRadius;
      }
    },
    CONE("Cone") {
      @Override
      double distance(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {

        if (coneCos <= CONE_HALF_SPACE_EPSILON) {
          return Math.max(0, -y);
        }
        final double radial = Math.sqrt(x * x + z * z);
        final double projection = y * coneCos + radial * coneSin;
        if (projection <= 0) {
          return length(x, y, z);
        }
        return Math.max(0, radial * coneCos - y * coneSin);
      }

      @Override
      boolean contains(
        double x, double y, double z, double radius, double minorRadius,
        double coneSin, double coneCos) {

        if (coneCos <= CONE_HALF_SPACE_EPSILON) {
          return y > 0;
        }
        final double radial = Math.sqrt(x * x + z * z);
        final double projection = y * coneCos + radial * coneSin;
        return (projection > 0) && (radial * coneCos - y * coneSin < 0);
      }
    };

    private final String label;

    Shape(String label) {
      this.label = label;
    }

    abstract double distance(
      double x, double y, double z, double radius, double minorRadius,
      double coneSin, double coneCos);

    boolean contains(
      double x, double y, double z, double radius, double minorRadius,
      double coneSin, double coneCos) {
      return false;
    }

    boolean usesRadius() {
      return (this == CYLINDER) || (this == SPHERE) || (this == TORUS);
    }

    boolean usesMinorRadius() {
      return this == TORUS;
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

  public enum CoordinateMode {
    PER_AXIS("Per Axis") {
      @Override
      public double axisScale(double axisRange, double maximumRange) {
        return 1;
      }
    },
    ISOTROPIC("Isotropic") {
      @Override
      public double axisScale(double axisRange, double maximumRange) {
        return (maximumRange > 0) ? axisRange / maximumRange : 1;
      }
    };

    private final String label;

    CoordinateMode(String label) {
      this.label = label;
    }

    public abstract double axisScale(double axisRange, double maximumRange);

    public double normalizedDirection(
      double direction, double axisRange, double maximumRange) {

      final double axisScale = axisScale(axisRange, maximumRange);
      return (axisScale > 0) ? direction / axisScale : 0;
    }

    public static double maximumRange(LXNormalizationBounds bounds) {
      return Math.max(bounds.xRange, Math.max(bounds.yRange, bounds.zRange));
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter originX = normalized("X", .5,
    "X coordinate of the local origin within the selected view");
  public final CompoundParameter originY = normalized("Y", .5,
    "Y coordinate of the local origin within the selected view");
  public final CompoundParameter originZ = normalized("Z", .5,
    "Z coordinate of the local origin within the selected view");

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

  public final CompoundParameter azimuthOffset =
    new CompoundParameter("Azim Offset", 0)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setWrappable(true)
    .setDescription("Additive azimuth offset around world Y, mapping 0-1 to 0-2pi");

  public final EnumParameter<Shape> shape =
    new EnumParameter<Shape>("Shape", Shape.LINE)
    .setDescription("Distance-field shape evaluated in local coordinates");

  public final BooleanParameter showRay =
    new BooleanParameter("Show Ray", false)
    .setDescription("Draw a red origin-and-aim line in the 3D preview");

  public final EnumParameter<CoordinateMode> coordinateMode =
    new EnumParameter<CoordinateMode>("Coords", CoordinateMode.PER_AXIS)
    .setDescription(
      "Per Axis preserves normalized views; Isotropic preserves physical angles and proportions");

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

  public final CompoundParameter radius =
    new CompoundParameter("Radius", .25, 0, 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Radius of the cylinder and sphere, or torus major radius");

  public final CompoundParameter minorRadius =
    new CompoundParameter("Minor Radius", .08, 0, 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Radius of the torus tube around its major ring");

  public final CompoundParameter coneAngle =
    new CompoundParameter("Cone Angle", Math.PI / 4, 0, HALF_PI)
    .setUnits(LXParameter.Units.RADIANS)
    .setDescription("Opening angle from the local positive Y axis");

  public final CompoundParameter scaleX = scale("Scale X");
  public final CompoundParameter scaleY = scale("Scale Y");
  public final CompoundParameter scaleZ = scale("Scale Z");

  private final LocalFrame frame = new LocalFrame();
  private final Map<UIDevice, PreviewOverlays> previewOverlays =
    new IdentityHashMap<UIDevice, PreviewOverlays>();

  public Raybeam(LX lx) {
    super(lx);
    addParameter("originX", this.originX);
    addParameter("originY", this.originY);
    addParameter("originZ", this.originZ);
    addParameter("azimuth", this.azimuth);
    addParameter("elevation", this.elevation);
    // Preserve the existing path so locally saved projects continue to load this control.
    addParameter("roll", this.azimuthOffset);
    addParameter("shape", this.shape);
    addParameter("showRay", this.showRay);
    addParameter("coordinateMode", this.coordinateMode);
    addParameter("width", this.width);
    addParameter("falloff", this.falloff);
    addParameter("softness", this.softness);
    addParameter("radius", this.radius);
    addParameter("minorRadius", this.minorRadius);
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
      this.azimuthOffset.getValue() * TWO_PI,
      this.scaleX.getValue(), this.scaleY.getValue(), this.scaleZ.getValue(),
      getModelView().getNormalizationBounds(), this.coordinateMode.getEnum());

    final Shape shape = this.shape.getEnum();
    final Falloff falloff = this.falloff.getEnum();
    final double radius = this.radius.getValue();
    final double minorRadius = this.minorRadius.getValue();
    final double coneAngle = this.coneAngle.getValue();
    final double coneSin = Math.sin(coneAngle);
    final double coneCos = Math.cos(coneAngle);
    final double width = this.width.getValue();
    final double softness = this.softness.getValue();
    for (LXPoint point : this.model.points) {
      final double x = this.frame.localX(point.xn, point.yn, point.zn);
      final double y = this.frame.localY(point.xn, point.yn, point.zn);
      final double z = this.frame.localZ(point.xn, point.yn, point.zn);
      final double distance =
        shape.distance(x, y, z, radius, minorRadius, coneSin, coneCos);
      final double envelope = falloff.brightness(distance, width, softness);
      this.colors[point.index] = LXColor.grayn(getBrightness(
        shape, x, y, z, radius, minorRadius, coneSin, coneCos, distance, envelope));
    }
  }

  /**
   * Converts a distance-field envelope into final grayscale output. Subclasses may override
   * this without duplicating Raybeam's parameters, local transform or shape evaluation.
   */
  protected double getBrightness(
    Shape shape, double x, double y, double z, double radius, double minorRadius,
    double coneSin, double coneCos, double distance, double envelope) {
    return envelope;
  }

  @Override
  public void buildDeviceControls(
    UI ui, UIDevice uiDevice, Raybeam raybeam) {

    final PreviewOverlays previous = this.previewOverlays.remove(uiDevice);
    if (previous != null) {
      previous.dispose(ui);
    }
    final PreviewOverlays overlays = new PreviewOverlays(raybeam);
    this.previewOverlays.put(uiDevice, overlays);
    ui.preview.addComponent(overlays.main);
    ui.previewAux.addComponent(overlays.auxiliary);

    final UIKnob radiusKnob = newKnob(raybeam.radius);
    radiusKnob.setEnabled(raybeam.shape.getEnum().usesRadius());
    radiusKnob.addListener(raybeam.shape,
      parameter -> radiusKnob.setEnabled(raybeam.shape.getEnum().usesRadius()));

    final UIKnob minorRadiusKnob = newKnob(raybeam.minorRadius);
    minorRadiusKnob.setEnabled(raybeam.shape.getEnum().usesMinorRadius());
    minorRadiusKnob.addListener(raybeam.shape,
      parameter -> minorRadiusKnob.setEnabled(raybeam.shape.getEnum().usesMinorRadius()));

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
      newKnob(raybeam.elevation),
      newKnob(raybeam.azimuthOffset));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Shape",
      newDropMenu(raybeam.shape),
      newButton(raybeam.showRay));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Space",
      newDropMenu(raybeam.coordinateMode));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Dimensions",
      radiusKnob,
      minorRadiusKnob,
      coneAngleKnob);

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Edge",
      newDropMenu(raybeam.falloff),
      newKnob(raybeam.width),
      newKnob(raybeam.softness));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Scale",
      newKnob(raybeam.scaleX),
      newKnob(raybeam.scaleY),
      newKnob(raybeam.scaleZ));
  }

  @Override
  public void disposeDeviceControls(
    UI ui, UIDevice uiDevice, Raybeam raybeam) {

    final PreviewOverlays overlays = this.previewOverlays.remove(uiDevice);
    if (overlays != null) {
      overlays.dispose(ui);
    }
  }

  private static final class PreviewOverlays {
    private final UIRaybeam main;
    private final UIRaybeam auxiliary;

    private PreviewOverlays(Raybeam raybeam) {
      this.main = new UIRaybeam(raybeam, false);
      this.auxiliary = new UIRaybeam(raybeam, true);
    }

    private void dispose(UI ui) {
      ui.preview.removeComponent(this.main);
      this.main.dispose();
      ui.previewAux.removeComponent(this.auxiliary);
      this.auxiliary.dispose();
    }
  }

  /** Precomputed inverse transform from normalized view coordinates into local space. */
  static final class LocalFrame {
    private double originX;
    private double originY;
    private double originZ;
    private double xx;
    private double xy;
    private double xz;
    private double yx;
    private double yy;
    private double yz;
    private double zx;
    private double zy;
    private double zz;

    void update(double originX, double originY, double originZ,
      double azimuth, double elevation, double azimuthOffset,
      double scaleX, double scaleY, double scaleZ) {

      update(originX, originY, originZ, azimuth, elevation, azimuthOffset,
        scaleX, scaleY, scaleZ, 1, 1, 1);
    }

    void update(double originX, double originY, double originZ,
      double azimuth, double elevation, double azimuthOffset,
      double scaleX, double scaleY, double scaleZ,
      LXNormalizationBounds bounds, CoordinateMode coordinateMode) {

      final double maximumRange = CoordinateMode.maximumRange(bounds);
      update(originX, originY, originZ, azimuth, elevation, azimuthOffset,
        scaleX, scaleY, scaleZ,
        coordinateMode.axisScale(bounds.xRange, maximumRange),
        coordinateMode.axisScale(bounds.yRange, maximumRange),
        coordinateMode.axisScale(bounds.zRange, maximumRange));
    }

    private void update(double originX, double originY, double originZ,
      double azimuth, double elevation, double azimuthOffset,
      double scaleX, double scaleY, double scaleZ,
      double coordinateScaleX, double coordinateScaleY, double coordinateScaleZ) {

      this.originX = originX;
      this.originY = originY;
      this.originZ = originZ;

      final double worldYaw = azimuth + azimuthOffset;
      final double sinAzimuth = Math.sin(worldYaw);
      final double cosAzimuth = Math.cos(worldYaw);
      final double sinElevation = Math.sin(elevation);
      final double cosElevation = Math.cos(elevation);

      // Rows of the inverse rotation matrix are the local basis vectors in world space.
      this.xx = cosAzimuth * coordinateScaleX / scaleX;
      this.xy = 0;
      this.xz = -sinAzimuth * coordinateScaleZ / scaleX;
      this.yx = sinAzimuth * cosElevation * coordinateScaleX / scaleY;
      this.yy = sinElevation * coordinateScaleY / scaleY;
      this.yz = cosAzimuth * cosElevation * coordinateScaleZ / scaleY;
      this.zx = sinAzimuth * sinElevation * coordinateScaleX / scaleZ;
      this.zy = -cosElevation * coordinateScaleY / scaleZ;
      this.zz = cosAzimuth * sinElevation * coordinateScaleZ / scaleZ;
    }

    double localX(double x, double y, double z) {
      return this.xx * (x - this.originX) + this.xy * (y - this.originY)
        + this.xz * (z - this.originZ);
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
