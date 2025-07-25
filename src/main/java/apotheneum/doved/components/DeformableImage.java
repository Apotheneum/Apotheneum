package apotheneum.doved.components;

import heronarts.lx.LXComponent;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.LX;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.pattern.image.ImagePattern.Image;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.transform.LXVector;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.utils.LXUtils;
import heronarts.glx.GLXUtils;

import apotheneum.doved.utils.AssetPaths;
import apotheneum.doved.utils.Kaleidoscope;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.image.ImagePattern;

public class DeformableImage extends Image implements LXComponent.Renamable, LXOscComponent {

  // Auto-cycle eligibility (following SlideshowPattern pattern)
  public final BooleanParameter autoCycleEligible = new BooleanParameter("Cycle", true)
      .setDescription("Whether the image is eligible for auto-cycle");

  // Kaleidoscope parameters - this is the main additional functionality we need
  public final Kaleidoscope kaleidoscope = new Kaleidoscope();

  public final StringParameter fileRelativePath = new StringParameter("FileRelativePath", (String) null);

  private int index;

  // Our own matrix for transformations since the parent's matrix is private
  private final LXMatrix transformMatrix = new LXMatrix();

  public DeformableImage(LX lx) {
    super(lx);

    addParameter("autoCycleEligible", this.autoCycleEligible);

    // Add kaleidoscope parameters
    addParameter("segments", this.kaleidoscope.params.segments);
    addParameter("krtheta", this.kaleidoscope.params.rotateTheta);
    addParameter("krphi", this.kaleidoscope.params.rotatePhi);
    addParameter("kx", this.kaleidoscope.params.x);
    addParameter("ky", this.kaleidoscope.params.y);
    addParameter("kz", this.kaleidoscope.params.z);
    addParameter("fileRelativePath", this.fileRelativePath);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.fileRelativePath) {
      // Convert relative path to absolute path for loading, following the original
      // pattern
      String relativePath = this.fileRelativePath.getString();

      String absolutePath = AssetPaths.toAbsolutePathFromAssets(relativePath);

      this.fileName.setValue(absolutePath);
    }
  }

  public void advance(double ms) {
    this.animateGif(ms); // Use the parent's GIF animation
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

  /**
   * Compute our own transformation matrix (replicating parent's computeMatrix
   * logic)
   * since the parent's matrix field is private
   */
  private void computeTransformMatrix(LXModel model) {
    float tx = this.translateX.getValuef();
    float ty = this.translateY.getValuef();
    float tz = this.translateZ.getValuef();
    float imageAspect = this.hasImage() ? this.getImage().getAspectRatio() : 1.0f;
    float modelAspect = model.xRange / model.yRange;
    float xAspect = imageAspect > modelAspect ? 1.0f
        : LXUtils.lerpf(1.0f, modelAspect / imageAspect, this.stretchAspect.getValuef());
    float yAspect = imageAspect < modelAspect ? 1.0f
        : LXUtils.lerpf(1.0f, imageAspect / modelAspect, this.stretchAspect.getValuef());
    float scale = this.scale.getValuef() * this.scaleRange.getValuef();

    this.transformMatrix.identity()
        .translate(0.5f, 0.5f, 0.5f)
        .scale(
            xAspect * this.stretchX.getValuef() / LXUtils.maxf(1.0E-4f, scale * this.scaleX.getValuef()),
            yAspect * this.stretchY.getValuef() / LXUtils.maxf(1.0E-4f, scale * this.scaleY.getValuef()),
            1.0f)
        .rotateZ(this.roll.getValuef() * 3.1415927f / 180.0f)
        .rotateX(this.pitch.getValuef() * 3.1415927f / 180.0f)
        .rotateY(this.yaw.getValuef() * 3.1415927f / 180.0f)
        .translate(-0.5f - tx, -0.5f + ty, -0.5f - tz);
  }

  /**
   * Render the image with kaleidoscope deformation applied
   * This is the key method that adds kaleidoscope functionality to the base Image
   * class
   */
  @Override
  public void render(LXModel model, int[] colors) {
    int backgroundColor = this.backgroundMode.getEnum().color;

    if (!this.hasImage()) {
      // Fill with background color if no image
      for (LXPoint p : model.points) {
        colors[p.index] = backgroundColor;
      }
    } else {
      this.computeTransformMatrix(model);

      // Get the imageMode for coordinate function
      ImagePattern.ImageMode imageMode = this.imageMode.getEnum();
      float scrollX = (1.0f - this.scrollX.getValuef()) % 1.0f;
      float scrollY = (1.0f - this.scrollY.getValuef()) % 1.0f;
      GLXUtils.Image glxImage = this.getImage();

      if (glxImage != null) {
        for (LXPoint p : model.points) {
          // Apply kaleidoscope deformation first - this is our key addition
          LXVector pD = this.kaleidoscope.deform(p.xn, p.yn, p.zn);

          // Apply matrix transformation for position, rotation, scale (same as parent)
          float rawXn = pD.x * this.transformMatrix.m11 + (1.0f - pD.y) * this.transformMatrix.m12
              + pD.z * this.transformMatrix.m13 + this.transformMatrix.m14;
          float rawYn = pD.x * this.transformMatrix.m21 + (1.0f - pD.y) * this.transformMatrix.m22
              + pD.z * this.transformMatrix.m23 + this.transformMatrix.m24;

          // Apply the image mode coordinate function (CLAMP, CLIP, TILE, MIRROR)
          float xn = applyImageModeCoordinate(imageMode, rawXn);
          float yn = applyImageModeCoordinate(imageMode, rawYn);

          // Check if coordinates are out of bounds (for CLIP mode)
          if (xn < 0.0f || yn < 0.0f) {
            colors[p.index] = backgroundColor;
          } else {
            // Apply scroll and get the color
            colors[p.index] = glxImage.getNormalized((xn + scrollX) % 1.0f, (yn + scrollY) % 1.0f);
          }
        }
      } else {
        for (LXPoint p : model.points) {
          colors[p.index] = backgroundColor;
        }
      }
    }
  }

  /**
   * Apply the coordinate function based on the image mode
   * This replicates the logic from ImagePattern.ImageMode enum
   */
  private float applyImageModeCoordinate(ImagePattern.ImageMode mode, float raw) {
    switch (mode) {
      case CLAMP:
        return LXUtils.clampf(raw, 0.0f, 1.0f);
      case CLIP:
        return raw > 1.0f ? -1.0f : raw;
      case TILE:
        return raw - (float) Math.floor(raw);
      case MIRROR:
        float floor = (float) Math.floor(raw);
        float diff = raw - floor;
        return (int) floor % 2 == 0 ? diff : 1.0f - diff;
      default:
        return raw;
    }
  }

  // All other functionality (hasImage, render, position, scaling, etc.)
  // is inherited from ImagePattern.Image
}