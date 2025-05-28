package apotheneum.doved.components;

import heronarts.lx.LXComponent;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.LX;
import heronarts.lx.parameter.LXParameter;

import apotheneum.doved.utils.AssetPaths;
import apotheneum.doved.utils.GifFrameExtractor;
import apotheneum.doved.utils.Image;
import apotheneum.doved.utils.Kaleidoscope;
import java.io.IOException;

public class ImageComponent extends LXComponent implements LXComponent.Renamable, LXOscComponent {

  public final StringParameter fileName = new StringParameter("File", null);

  public interface ImageCoordinateFunction {
    public float getCoordinate(float raw);
  }

  public enum ImageMode {
    CLAMP("Clamp", (raw) -> {
      return LXUtils.clampf(raw, 0, 1);
    }),

    CLIP("Clip", (raw) -> {
      return (raw > 1) ? -1 : raw;
    }),
    TILE("Tile", (raw) -> {
      return raw - (float) Math.floor(raw);
    });

    public final String label;
    public final ImageCoordinateFunction function;

    private ImageMode(String label, ImageCoordinateFunction function) {
      this.label = label;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final TriggerParameter trigger = new TriggerParameter("Trigger", () -> {
    // goImage(this.index);
  })
      .setDescription("Trigger the image");

  public final TriggerParameter reload = new TriggerParameter("Reload", () -> {
    this.fileName.bang();
  })
      .setDescription("Reload the image file from disk");

  public final BooleanParameter autoCycleEligible = new BooleanParameter("Cycle", true)
      .setDescription("Whether the image is eligible for auto-cycle");

  public final EnumParameter<ImageMode> imageMode = new EnumParameter<ImageMode>("Mode", ImageMode.TILE)
      .setDescription("How the image wraps");

  public final CompoundParameter speed = new CompoundParameter(
      "speed", 0.1, -1.0, 1.0);

  public final CompoundParameter speedSlope = new CompoundParameter(
      "speedSlope", 8.0, 0.0, 10.0);

  public final CompoundParameter translateX = new CompoundParameter("X-Pos", 0, -1, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Center position of the image on X axis");

  public final CompoundParameter translateY = new CompoundParameter("Y-Pos", 0, -1, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Center position of the image on Y axis");

  public final CompoundParameter translateZ = new CompoundParameter("Z-Pos", 0, -1, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Center position of the image on Z axis");

  public final CompoundParameter scale = new CompoundParameter("Scale", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Scale the image to its aspect ratio");

  public final BoundedParameter scaleRange = new BoundedParameter("Scale Range", 1, .1, 10)
      .setDescription("Range of the scale adjustement");

  public final CompoundParameter scaleX = new CompoundParameter("X-Scale", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Scale the image on the X-axis");

  public final CompoundParameter scaleY = new CompoundParameter("Y-Scale", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Scale the image on the Y-axis");

  public final CompoundParameter stretchX = new CompoundParameter("X-Stretch", 1, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Strech the image on the X-axis");

  public final CompoundParameter stretchY = new CompoundParameter("Y-Stretch", 1, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Stretch the image on the Y-axis");

  public final CompoundParameter stretchAspect = new CompoundParameter("Aspect", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Stretch the image to its natural aspect ratio");

  public final CompoundParameter speedX = new CompoundParameter("X-Speed", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Speed of the image on the X-axis");

  public final CompoundParameter speedY = new CompoundParameter("Y-Speed", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Speed of the image on the Y-axis");

  public final TriggerParameter resetDistance = new TriggerParameter("Reset-Distance", () -> {
    this.makeDistanceZero();
  })
      .setDescription("Zero out the distance moved from speed");

  // Kaleidoscope parameters
  public final Kaleidoscope kaleidoscope = new Kaleidoscope();

  // Rotation parameters
  public final CompoundParameter yaw = new CompoundParameter("Yaw", 0, -360, 360)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.DEGREES)
      .setDescription("Rotation of the image about the vertical axis");

  public final CompoundParameter pitch = new CompoundParameter("Pitch", 0, -360, 360)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.DEGREES)
      .setDescription("Pitch of the image about the horizontal plane");

  public final CompoundParameter roll = new CompoundParameter("Roll", 0, -360, 360)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.DEGREES)
      .setDescription("Roll of the image");

  private int index;
  public apotheneum.doved.utils.Image stillImage;
  private apotheneum.doved.utils.Image[] imageFrames = null;
  private double position = 0;
  private double distanceX = 0;
  private double distanceY = 0;

  public ImageComponent(LX lx, LXComponent parent) {
    super(lx, "Image");
    setParent(parent);

    addParameter("imageMode", this.imageMode);
    addParameter("fileName", this.fileName);
    addParameter("autoCycleEligible", this.autoCycleEligible);
    addParameter("trigger", this.trigger);
    addParameter("reload", this.reload);
    addParameter("speed", this.speed);
    addParameter("translateX", this.translateX);
    addParameter("translateY", this.translateY);
    addParameter("translateZ", this.translateZ);
    addParameter("scale", this.scale);
    addParameter("scaleRange", this.scaleRange);
    addParameter("scaleX", this.scaleX);
    addParameter("scaleY", this.scaleY);
    addParameter("stretchX", this.stretchX);
    addParameter("stretchY", this.stretchY);
    addParameter("stretchAspect", this.stretchAspect);
    addParameter("speedX", this.speedX);
    addParameter("speedY", this.speedY);
    addParameter("resetDistance", this.resetDistance);

    // Add kaleidoscope parameters
    addParameter("segments", this.kaleidoscope.params.segments);
    addParameter("krtheta", this.kaleidoscope.params.rotateTheta);
    addParameter("krphi", this.kaleidoscope.params.rotatePhi);
    addParameter("kx", this.kaleidoscope.params.x);
    addParameter("ky", this.kaleidoscope.params.y);
    addParameter("kz", this.kaleidoscope.params.z);

    // Add rotation parameters
    addParameter("yaw", this.yaw);
    addParameter("pitch", this.pitch);
    addParameter("roll", this.roll);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.fileName) {
      String fileName = AssetPaths.toAbsolutePathFromAssets(this.fileName.getString());
      if (fileName == null) {
        this.stillImage = null;
        this.imageFrames = null;
      } else {
        try {
          // if is a gif (ends with .gif):
          if (fileName.toLowerCase().endsWith(".gif")) {
            this.imageFrames = GifFrameExtractor.extractFrames(fileName);
            this.stillImage = null;
          } else {
            this.imageFrames = null;
            this.stillImage = Image.loadImage(fileName);
          }

          // this.imagethis. = GLXUtils.loadImage(fileName) as Image.IHasPixels;
        } catch (IOException iox) {
          LX.error(null, "Error loading image file " + fileName + ": " + iox.getLocalizedMessage());
          lx.pushError(iox, "Error loading image file: " + fileName);
          this.stillImage = null;
        }
      }
    }
  }

  public boolean hasImage() {
    return this.stillImage != null || this.imageFrames != null;
  }

  public Image getCurrentFrame() {
    if (this.stillImage != null)
      return this.stillImage;
    if (this.imageFrames != null) {
      int frameIndex = (int) (this.position);
      int framePosition = frameIndex % this.imageFrames.length;
      // make sure it is at least 0 (as to not crash)
      return this.imageFrames[(Math.max(framePosition, 0))];
    }

    return null;
  }

  public void advance(double ms) {
    this.position += ms * this.speed.getValue() / this.speedSlope.getValue();
    this.distanceX += ms * this.speedX.getValue() / 1000.f;
    this.distanceY += ms * this.speedY.getValue() / 1000.f;
  }

  public void makeDistanceZero() {
    System.out.println("Reset distance");
    this.distanceX = 0;
    this.distanceY = 0;
  }

  public float positionX() {
    return this.translateX.getValuef() + (float) this.distanceX;
  }

  public float positionY() {
    return this.translateY.getValuef() + (float) this.distanceY;
  }

  public int getIndex() {
    return this.index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  @Override
  public String getPath() {
    return "image/" + (this.index + 1);
  }

}