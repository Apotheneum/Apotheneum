/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package apotheneum.doved;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.UIDuplicate;
import heronarts.glx.ui.component.UIButton;
import heronarts.glx.ui.component.UIDoubleBox;
import heronarts.glx.ui.component.UIDropMenu;
import heronarts.glx.ui.component.UIInputBox;
import heronarts.glx.ui.component.UIItemList;
import heronarts.glx.ui.component.UIKnob;
import heronarts.glx.ui.component.UIParameterControl;
import heronarts.glx.ui.component.UITextBox;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.LXSerializable;
import heronarts.lx.ModelBuffer;
import heronarts.lx.blend.AddBlend;
import heronarts.lx.blend.DarkestBlend;
import heronarts.lx.blend.DifferenceBlend;
import heronarts.lx.blend.DissolveBlend;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.blend.LightestBlend;
import heronarts.lx.blend.MultiplyBlend;
import heronarts.lx.clipboard.LXClipboardComponent;
import heronarts.lx.clipboard.LXClipboardItem;
import heronarts.lx.color.LXColor;
import heronarts.lx.command.LXCommand;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.transform.LXVector;
import apotheneum.doved.utils.Kaleidoscope;
import apotheneum.doved.utils.AssetPaths;
import apotheneum.doved.utils.Image;
import heronarts.lx.studio.LXStudio.UI;
import apotheneum.doved.DeformableSlideshowPattern;
import apotheneum.doved.components.ImageComponent;
import apotheneum.Apotheneum;

@LXCategory(Apotheneum.IMAGE_CATEGORY)
@LXComponentName("Kaleidsocope Slideshow")
public class DeformableSlideshowPattern extends LXPattern
    implements UIDeviceControls<DeformableSlideshowPattern> {

  public enum AutoCycleMode {
    NEXT,
    RANDOM;

    @Override
    public String toString() {
      switch (this) {
        case NEXT:
          return "Next";
        default:
        case RANDOM:
          return "Random";
      }
    }
  };

  public interface Listener {
    public void imageAdded(DeformableSlideshowPattern slideshow, ImageComponent image);

    public void imageRemoved(DeformableSlideshowPattern slideshow, ImageComponent image);

    public void imageMoved(DeformableSlideshowPattern slideshow, ImageComponent image);

    public void imageWillChange(DeformableSlideshowPattern slideshow, ImageComponent from, ImageComponent to);

    public void imageDidChange(DeformableSlideshowPattern slideshow, ImageComponent image);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<ImageComponent> mutableImages = new ArrayList<ImageComponent>();
  public final List<ImageComponent> images = Collections.unmodifiableList(this.mutableImages);
  private LXMatrix matrix = new LXMatrix();

  /**
   * Whether auto pattern transition is enabled on this channel
   */
  public final BooleanParameter autoCycleEnabled = new BooleanParameter("Auto-Cycle", false)
      .setDescription("When enabled, this channel will automatically cycle between its patterns");

  /**
   * Auto-cycle to a random pattern, not the next one
   */
  public final EnumParameter<AutoCycleMode> autoCycleMode = new EnumParameter<AutoCycleMode>("Auto-Cycle Mode",
      AutoCycleMode.NEXT)
      .setDescription("Mode of auto cycling");

  /**
   * Time in seconds after which transition thru the image set is automatically
   * initiated.
   */
  public final BoundedParameter autoCycleTimeSecs = new BoundedParameter("Cycle Time", 1, 0, 60 * 60 * 4)
      .setDescription("Sets the number of seconds after which the channel cycles to the next image")
      .setUnits(LXParameter.Units.SECONDS);

  public final BoundedParameter transitionTimeSecs = new BoundedParameter("Transition Time", 5, .1, 180)
      .setDescription("Sets the duration of blending transitions between images")
      .setUnits(LXParameter.Units.SECONDS);

  public final BooleanParameter transitionEnabled = new BooleanParameter("Transitions", true)
      .setDescription("When enabled, transitions between images use a blend");

  public final ObjectParameter<LXBlend> transitionBlendMode = new ObjectParameter<LXBlend>("Blend Mode", new LXBlend[] {
      new DissolveBlend(lx),
      new AddBlend(lx),
      new MultiplyBlend(lx),
      new LightestBlend(lx),
      new DarkestBlend(lx),
      new DifferenceBlend(lx)
  })
      .setDescription("Which blend to use between images");

  public final DiscreteParameter focusedImage = new DiscreteParameter("Focused Image", 0, 1)
      .setDescription("Which image has focus in the UI");

  private static final int NO_IMAGE_INDEX = -1;

  private double autoCycleProgress = 0;
  private double transitionProgress = 0;
  private int activeImageIndex = NO_IMAGE_INDEX;
  private int nextImageIndex = NO_IMAGE_INDEX;

  private long transitionMillis = 0;
  private boolean inTransition = false;

  private final ModelBuffer blendBuffer = new ModelBuffer(lx);

  public DeformableSlideshowPattern(LX lx) {
    super(lx);

    addParameter("focusedImage", this.focusedImage);
    addArray("image", this.images);
  }

  private void _reindexImages() {
    int i = 0;
    for (ImageComponent image : this.images) {
      image.setIndex(i++);
    }
  }

  public ImageComponent getActiveImage() {
    // print out the active image index:
    return (this.activeImageIndex >= 0) ? this.images.get(this.activeImageIndex) : null;
  }

  public ImageComponent getNextImage() {
    return (this.nextImageIndex >= 0) ? this.images.get(this.nextImageIndex) : null;
  }

  public ImageComponent getTargetImage() {
    return (this.inTransition) ? getNextImage() : getActiveImage();
  }

  private DeformableSlideshowPattern goImage(ImageComponent image) {
    return goImage(image.getIndex());
  }

  public DeformableSlideshowPattern goImage(int index) {
    if (index < 0 || index >= this.images.size()) {
      throw new IllegalArgumentException("Image index out of bounds: " + index);
    }
    if (this.inTransition) {
      finishTransition();
    }
    this.nextImageIndex = index;
    startTransition();
    return this;
  }

  public DeformableSlideshowPattern goNextImage() {
    if (this.inTransition) {
      return this;
    }
    if (this.images.size() <= 1) {
      return this;
    }
    this.nextImageIndex = this.activeImageIndex;
    do {
      this.nextImageIndex = (this.nextImageIndex + 1) % this.images.size();
    } while ((this.nextImageIndex != this.activeImageIndex) && !getNextImage().autoCycleEligible.isOn());
    if (this.nextImageIndex != this.activeImageIndex) {
      startTransition();
    }
    return this;
  }

  private final List<ImageComponent> randomEligible = new ArrayList<ImageComponent>();

  public final DeformableSlideshowPattern goRandomImage() {
    if (this.inTransition) {
      return this;
    }
    if (this.images.size() <= 1) {
      return this;
    }
    ImageComponent activeImage = getActiveImage();
    this.randomEligible.clear();
    for (ImageComponent image : this.images) {
      if (image != activeImage && image.autoCycleEligible.isOn()) {
        this.randomEligible.add(image);
      }
    }
    int numEligible = this.randomEligible.size();
    if (numEligible > 0) {
      return goImage(
          this.randomEligible.get(LXUtils.constrain((int) LXUtils.random(0, numEligible), 0, numEligible - 1)));
    }
    return this;
  }

  private void startTransition() {
    ImageComponent activeImage = getActiveImage();
    ImageComponent nextImage = getNextImage();
    if (activeImage == nextImage) {
      return;
    }
    for (Listener listener : this.listeners) {
      listener.imageWillChange(this, activeImage, nextImage);
    }
    if (this.transitionEnabled.isOn()) {
      this.inTransition = true;
      this.transitionMillis = this.lx.engine.nowMillis;
    } else {
      finishTransition();
    }
  }

  private void cancelTransition() {
    if (this.inTransition) {
      this.inTransition = false;
      this.transitionMillis = this.lx.engine.nowMillis;
      this.nextImageIndex = this.activeImageIndex;
      ImageComponent activeImage = getActiveImage();
      for (Listener listener : listeners) {
        listener.imageDidChange(this, activeImage);
      }
    }
  }

  private void finishTransition() {
    this.activeImageIndex = this.nextImageIndex;
    ImageComponent activeImage = getActiveImage();
    this.inTransition = false;
    this.transitionMillis = this.lx.engine.nowMillis;
    for (Listener listener : listeners) {
      listener.imageDidChange(this, activeImage);
    }
  }

  public ImageComponent getFocusedImage() {
    if (this.images.isEmpty()) {
      return null;
    }
    return this.images.get(this.focusedImage.getValuei());
  }

  public ImageComponent addImage() {
    return addImage(-1, null);
  }

  public ImageComponent addImage(int index) {
    return addImage(index, null);
  }

  public ImageComponent addImage(int index, JsonObject imageObj) {
    if (index > this.images.size()) {
      throw new IllegalArgumentException("Cannot add image at invalid index: " + index);
    }

    ImageComponent focusedImage = getFocusedImage();

    ImageComponent image = new ImageComponent(this.lx, this);
    if (index < 0) {
      this.mutableImages.add(image);
    } else {
      this.mutableImages.add(index, image);
    }
    _reindexImages();

    if (imageObj != null) {
      image.load(lx, imageObj);
    } else {
      image.label.setValue("Image-" + (image.getIndex() + 1));
    }

    for (Listener listener : this.listeners) {
      listener.imageAdded(this, image);
    }

    this.focusedImage.setRange(Math.max(1, this.images.size()));
    if (focusedImage != null) {
      // Retain the focused pattern index if it has been shifted
      this.focusedImage.setValue(focusedImage.getIndex());
    } else {
      // Otherwise send a bang - newly added pattern is focused
      this.focusedImage.bang();
    }

    // First image added, automatically becomes active
    if (this.images.size() == 1) {
      this.activeImageIndex = this.nextImageIndex = 0;
      ImageComponent activeImage = getActiveImage();
      for (Listener listener : this.listeners) {
        listener.imageDidChange(this, activeImage);
      }
    }

    return image;
  }

  public void removeImage(ImageComponent image) {
    int index = this.mutableImages.indexOf(image);
    if (index < 0) {
      throw new IllegalArgumentException("Cannot remove image not in slideshow: " + image);
    }

    int focusedImageIndex = this.focusedImage.getValuei();

    if (this.inTransition) {
      if (image == getNextImage()) {
        cancelTransition();
      } else if (image == getActiveImage()) {
        finishTransition();
      }
    }

    ImageComponent activeImage = getActiveImage();

    this.mutableImages.remove(image);
    _reindexImages();

    // Update indices
    if (this.activeImageIndex > index) {
      --this.activeImageIndex;
    } else if (this.activeImageIndex >= this.mutableImages.size()) {
      this.activeImageIndex = this.mutableImages.size() - 1;
    }
    if (this.nextImageIndex > index) {
      --this.nextImageIndex;
    } else if (this.nextImageIndex >= this.mutableImages.size()) {
      this.nextImageIndex = this.mutableImages.size() - 1;
    }

    if (focusedImageIndex > index) {
      --focusedImageIndex;
    } else if (focusedImageIndex >= this.images.size()) {
      focusedImageIndex = this.images.size() - 1;
    }
    if ((focusedImageIndex >= 0) && (this.focusedImage.getValuei() != focusedImageIndex)) {
      this.focusedImage.setValue(focusedImageIndex);
    } else {
      // Either the value wasn't changed, or we removed the last one
      this.focusedImage.bang();
    }

    this.focusedImage.setRange(Math.max(1, this.images.size()));

    // Notify that it's removed
    for (Listener listener : this.listeners) {
      listener.imageRemoved(this, image);
    }

    // If we removed the active image, update it
    if ((activeImage == image) && !this.images.isEmpty()) {
      activeImage = getActiveImage();
      for (Listener listener : this.listeners) {
        listener.imageDidChange(this, activeImage);
      }
    }

    image.dispose();
  }

  public void moveImage(ImageComponent image, int index) {
    ImageComponent focusedImage = getFocusedImage();
    ImageComponent activeImage = getActiveImage();
    ImageComponent nextImage = getNextImage();

    this.mutableImages.remove(image);
    this.mutableImages.add(index, image);
    _reindexImages();

    this.activeImageIndex = activeImage.getIndex();
    this.nextImageIndex = nextImage.getIndex();

    if (image == focusedImage) {
      this.focusedImage.setValue(image.getIndex());
    }

    for (Listener listener : this.listeners) {
      listener.imageMoved(this, image);
    }
  }

  public void addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null SlideshowPattern.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("May not add duplicate SlideshowPattern.Listener: " + listener);
    }
    this.listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered SlideshowPattern.Listener: " + listener);
    }
    this.listeners.remove(listener);
  }

  /**
   * Return progress towards making a cycle
   *
   * @return amount of progress towards the next cycle
   */
  public double getAutoCycleProgress() {
    return this.autoCycleProgress;
  }

  /**
   * Return progress through a transition
   *
   * @return amount of progress thru current transition
   */
  public double getTransitionProgress() {
    return this.transitionProgress;
  }

  @Override
  protected void run(double deltaMs) {
    // Check for transition completion
    if (this.inTransition) {
      double transitionMs = this.lx.engine.nowMillis - this.transitionMillis;
      double transitionDone = 1000 * this.transitionTimeSecs.getValue();
      if (transitionMs >= transitionDone) {
        finishTransition();
      }
    }

    // Auto-cycle if appropriate
    if (!this.inTransition) {
      this.autoCycleProgress = (this.lx.engine.nowMillis - this.transitionMillis)
          / (1000 * this.autoCycleTimeSecs.getValue());
      if (this.autoCycleProgress >= 1) {
        this.autoCycleProgress = 1;
        if (this.autoCycleEnabled.isOn()) {
          switch (this.autoCycleMode.getEnum()) {
            case NEXT:
              goNextImage();
              break;
            case RANDOM:
              goRandomImage();
              break;
          }
        }
      }
    }

    // Calculate transition progress
    if (this.inTransition) {
      this.autoCycleProgress = 1.;
      this.transitionProgress = (this.lx.engine.nowMillis - this.transitionMillis)
          / (1000 * this.transitionTimeSecs.getValue());
    } else {
      this.transitionProgress = 0;
    }

    ImageComponent image = getActiveImage();
    if (image == null || !image.hasImage()) {
      setColors(LXColor.BLACK);
    } else {
      image.advance(deltaMs);
      renderImage(image, this.colors, deltaMs);

      if (this.inTransition) {
        int[] blendColors = this.blendBuffer.getArray();
        renderImage(getNextImage(), blendColors, deltaMs);
        this.transitionBlendMode.getObject().lerp(this.colors, blendColors, transitionProgress, this.colors,
            this.model);
      }
    }
  }

  protected void computeMatrix() {
    ImageComponent activeImage = this.images.get(this.activeImageIndex);

    final float tx = activeImage.positionX();
    final float ty = activeImage.positionY();
    final float tz = activeImage.translateZ.getValuef();

    Image activeImageFrame = activeImage.getCurrentFrame();
    final float imageAspect = activeImageFrame != null ? activeImageFrame.getAspectRatio() : 1;
    final float modelAspect = model.xRange / model.yRange;

    final float xAspect = imageAspect > modelAspect ? 1
        : LXUtils.lerpf(
            1,
            modelAspect / imageAspect,
            activeImage.stretchAspect.getValuef());

    final float yAspect = imageAspect < modelAspect ? 1
        : LXUtils.lerpf(
            1,
            imageAspect / modelAspect,
            activeImage.stretchAspect.getValuef());

    final float scale = activeImage.scale.getValuef() * activeImage.scaleRange.getValuef();

    this.matrix
        .identity()
        .translate(.5f, .5f, .5f)
        .scale(
            xAspect * activeImage.stretchX.getValuef() / LXUtils.maxf(.0001f, scale * activeImage.scaleX.getValuef()),
            yAspect * activeImage.stretchY.getValuef() / LXUtils.maxf(.0001f, scale * activeImage.scaleY.getValuef()),
            1)
        .rotateZ(activeImage.roll.getValuef() * LX.PIf / 180f)
        .rotateX(activeImage.pitch.getValuef() * LX.PIf / 180f)
        .rotateY(activeImage.yaw.getValuef() * LX.PIf / 180f)
        .translate(-.5f - tx, -.5f + ty, -.5f - tz);
  }

  float distanceX;
  float distanceY;

  protected void renderImage(ImageComponent image, int[] buffer, double deltaMs) {
    computeMatrix();
    Image pixels = image.getCurrentFrame();
    ImageComponent.ImageCoordinateFunction function = image.imageMode.getEnum().function;
    float xn, yn;
    for (LXPoint p : model.points) {
      LXVector pD = image.kaleidoscope.deform(p.xn, p.yn, p.zn);

      xn = function.getCoordinate(pD.x * matrix.m11 + (1 - pD.y) * matrix.m12 + pD.z * matrix.m13 + matrix.m14);
      yn = function.getCoordinate(pD.x * matrix.m21 + (1 - pD.y) * matrix.m22 + pD.z * matrix.m23 + matrix.m24);
      if (xn < 0 || yn < 0) {
        buffer[p.index] = LXColor.BLACK;
      } else {
        buffer[p.index] = pixels.getNormalized(xn, yn);
      }
    }
  }

  private static final String KEY_IMAGES = "images";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_IMAGES, LXSerializable.Utils.toArray(lx, this.images));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    for (int i = this.images.size() - 1; i >= 0; --i) {
      removeImage(this.images.get(i));
    }
    if (obj.has(KEY_IMAGES)) {
      JsonArray imageArr = obj.getAsJsonArray(KEY_IMAGES);
      for (JsonElement imageElem : imageArr) {
        addImage().load(lx, imageElem.getAsJsonObject());
      }
    }
    super.load(lx, obj);
  }

  @Override
  public void dispose() {
    for (ImageComponent image : this.images) {
      image.dispose();
    }
    this.mutableImages.clear();
    this.listeners.clear();
    super.dispose();
  }

  // copied and pasted from UISlideshowPattern:

  private static class Command {

    private static class AddImage extends LXCommand {
      private final ComponentReference<DeformableSlideshowPattern> slideshow;
      private ComponentReference<ImageComponent> image = null;
      private final String relativePath;
      private JsonObject imageObj;

      public AddImage(DeformableSlideshowPattern slideshow, String relativePath) {
        this.slideshow = new ComponentReference<DeformableSlideshowPattern>(slideshow);

        this.relativePath = relativePath;
      }

      public AddImage(DeformableSlideshowPattern slideshow, JsonObject imageObj) {
        this.slideshow = new ComponentReference<DeformableSlideshowPattern>(slideshow);
        this.imageObj = imageObj;
        this.relativePath = null;
      }

      @Override
      public String getDescription() {
        return "Add Image";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        ImageComponent image = this.slideshow.get().addImage();
        if (this.imageObj != null) {
          image.load(lx, this.imageObj);
        } else {
          // String absolutePath = AssetPaths.toAbsolutePathFromAssets(relativePath);

          ReplaceImage.setImagePath(image, relativePath);
          this.imageObj = LXSerializable.Utils.toObject(image);
        }
        this.image = new ComponentReference<ImageComponent>(image);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.slideshow.get().removeImage(this.image.get());
      }
    }

    private static class RemoveImage extends LXCommand.RemoveComponent {

      private final ComponentReference<DeformableSlideshowPattern> slideshow;
      private ComponentReference<ImageComponent> image = null;
      private JsonObject imageObj;
      private final int index;
      private final boolean isActive;
      private final boolean isFocused;

      private RemoveImage(DeformableSlideshowPattern slideshow, ImageComponent image) {
        super(image);
        this.slideshow = new ComponentReference<DeformableSlideshowPattern>(slideshow);
        this.image = new ComponentReference<ImageComponent>(image);
        this.imageObj = LXSerializable.Utils.toObject(image);
        this.index = image.getIndex();
        this.isActive = slideshow.getActiveImage() == image;
        this.isFocused = slideshow.getFocusedImage() == image;
      }

      @Override
      public String getDescription() {
        return "Remove Image";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.slideshow.get().removeImage(this.image.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        DeformableSlideshowPattern slideshow = this.slideshow.get();
        ImageComponent image = slideshow.addImage(this.index, this.imageObj);
        if (this.isActive) {
          slideshow.goImage(image.getIndex());
        }
        if (this.isFocused) {
          slideshow.focusedImage.setValue(image.getIndex());
        }
        super.undo(lx);
      }
    }

    private static class MoveImage extends LXCommand {
      private final ComponentReference<DeformableSlideshowPattern> slideshow;
      private final ComponentReference<ImageComponent> image;
      private final int fromIndex, toIndex;

      private MoveImage(DeformableSlideshowPattern slideshow, ImageComponent image, int toIndex) {
        this.slideshow = new ComponentReference<DeformableSlideshowPattern>(slideshow);
        this.image = new ComponentReference<ImageComponent>(image);
        this.fromIndex = image.getIndex();
        this.toIndex = toIndex;
      }

      @Override
      public String getDescription() {
        return "Move Image";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        slideshow.get().moveImage(this.image.get(), this.toIndex);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        slideshow.get().moveImage(this.image.get(), this.fromIndex);
      }
    }

    private static class ReplaceImage extends LXCommand {

      private final ComponentReference<ImageComponent> image;
      private final String oldPath, newPath;
      private final String oldLabel;

      private ReplaceImage(ImageComponent image, String relativePath) {
        this.image = new ComponentReference<ImageComponent>(image);
        this.oldPath = image.fileName.getString();
        this.oldLabel = image.label.getString();
        this.newPath = relativePath;
      }

      @Override
      public String getDescription() {
        return "Replace Image";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        setImagePath(this.image.get(), this.newPath);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.image.get().fileName.setValue(this.oldPath);
        this.image.get().label.setValue(this.oldLabel);
      }

      private static void setImagePath(ImageComponent image, String relativePath) {
        String name = AssetPaths.toAbsolutePathFromAssets(relativePath);
        int separator = name.lastIndexOf(File.separatorChar);
        if (separator > 0) {
          name = name.substring(separator + 1);
        }
        image.label.setValue(name);
        image.fileName.setValue(relativePath);
      }

    }

    public static class GoImage extends LXCommand {

      private final ComponentReference<DeformableSlideshowPattern> slideshow;
      private final ComponentReference<ImageComponent> prevImage;
      private final ComponentReference<ImageComponent> nextImage;

      public GoImage(DeformableSlideshowPattern channel, ImageComponent nextImage) {
        this.slideshow = new ComponentReference<DeformableSlideshowPattern>(channel);
        this.prevImage = new ComponentReference<ImageComponent>(channel.getActiveImage());
        this.nextImage = new ComponentReference<ImageComponent>(nextImage);
      }

      @Override
      public String getDescription() {
        return "Change Image";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.slideshow.get().goImage(this.nextImage.get().getIndex());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.slideshow.get().goImage(this.prevImage.get().getIndex());
      }
    }

  }

  private ImageList imageList;
  private DeformableSlideshowPattern slideshow;
  private DeformableSlideshowPattern.Listener slideshowListener;

  private final Map<ImageComponent, ImageItem> imageMap = new HashMap<ImageComponent, ImageItem>();
  private final Map<ImageComponent, ImageControls> imageControls = new HashMap<ImageComponent, ImageControls>();

  private class UISlideshowControls extends UI2dContainer {
    public UISlideshowControls(UI ui, DeformableSlideshowPattern slideshow, float width, float height) {
      super(0, 0, width, height);
      new UIButton(0, 0, 16, 16)
          .setIcon(ui.theme.iconPatternTransition)
          .setParameter(slideshow.transitionEnabled)
          .setTextOffset(0, -1)
          .addToContainer(this);
      new UIDropMenu(18, 0, 70, 16, slideshow.transitionBlendMode)
          .setDirection(UIDropMenu.Direction.UP)
          .addToContainer(this);
      new UIDoubleBox(90, 0, 50, 16)
          .setParameter(slideshow.transitionTimeSecs)
          .setNormalizedMouseEditing(false)
          .setShiftMultiplier(.1f)
          .setProgressIndicator(new UIInputBox.ProgressIndicator() {
            public boolean hasProgress() {
              return slideshow.transitionEnabled.isOn();
            }

            public double getProgress() {
              return slideshow.getTransitionProgress();
            }
          })
          .addToContainer(this);

      // Auto cycle controls
      new UIButton(0, 20, 16, 16)
          .setIcon(ui.theme.iconPatternRotate)
          .setParameter(slideshow.autoCycleEnabled)
          .addToContainer(this);
      new UIDropMenu(18, 20, 70, 16, slideshow.autoCycleMode)
          .setDirection(UIDropMenu.Direction.UP)
          .addToContainer(this);
      new UIDoubleBox(90, 20, 50, 16)
          .setParameter(slideshow.autoCycleTimeSecs)
          .setNormalizedMouseEditing(false)
          .setShiftMultiplier(60)
          .setProgressIndicator(new UIInputBox.ProgressIndicator() {
            public boolean hasProgress() {
              return slideshow.autoCycleEnabled.isOn();
            }

            public double getProgress() {
              return slideshow.getAutoCycleProgress();
            }
          })
          .addToContainer(this);
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, DeformableSlideshowPattern slideshow) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL);
    uiDevice.setChildSpacing(8);
    this.slideshow = slideshow;

    final int listWidth = 140;

    this.imageList = new ImageList(ui, 0, 0, listWidth, 100);
    this.imageList.setReorderable(true);
    this.imageList.setRenamable(true);
    this.imageList.setDeletable(true);
    this.imageList.setShowCheckboxes(true);
    this.imageList.setDescription("Images loaded into the slideshow, click to select, double-click to activate");

    addColumn(uiDevice, listWidth,
        new UIButton.Action(listWidth, 16, "Add Image") {
          @Override
          public void onClick() {
            ui.lx.showOpenFileDialog(
                "Open Image",
                "Image File",
                new String[] { "jpg", "jpeg", "png", "gif" },
                new File("Assets/").toString(),
                (path) -> {
                  // Convert both paths to absolute and normalize
                  // Check if the given path is within the "Assets" directory
                  if (AssetPaths.isInAssetsFolder(path)) {
                    ui.lx.command.perform(new Command.AddImage(slideshow, AssetPaths.toRelativePathFromAssets(path)));
                  } else {
                    LX.error(null, "Image file must be within the Assets directory");
                    lx.pushError(new Error(), "Image file must be within the Assets directory");
                  }
                });
          }
        }.setDescription("Use this button to load a new image file into the slideshow"),
        this.imageList,
        new UISlideshowControls(ui, slideshow, listWidth, 32)).setChildSpacing(4);

    for (ImageComponent image : slideshow.images) {
      addImage(ui, uiDevice, image);
    }

    uiDevice.addListener(slideshow.focusedImage, p -> {
      focusImage(slideshow.getFocusedImage());
    }, true);

    slideshow.addListener(this.slideshowListener = new DeformableSlideshowPattern.Listener() {

      @Override
      public void imageAdded(DeformableSlideshowPattern slideshow, ImageComponent image) {
        addImage(ui, uiDevice, image);
      }

      @Override
      public void imageRemoved(DeformableSlideshowPattern slideshow, ImageComponent image) {
        removeImage(ui, uiDevice, image);
      }

      @Override
      public void imageMoved(DeformableSlideshowPattern slideshow, ImageComponent image) {
        moveImage(ui, uiDevice, image);
      }

      @Override
      public void imageWillChange(DeformableSlideshowPattern slideshow, ImageComponent from, ImageComponent to) {
        imageList.redraw();
      }

      @Override
      public void imageDidChange(DeformableSlideshowPattern slideshow, ImageComponent image) {
        imageList.redraw();
      }

    });
  }

  @Override
  public void disposeDeviceControls(UI ui, UIDevice uiDevice, DeformableSlideshowPattern slideshow) {
    this.slideshow.removeListener(this.slideshowListener);
  }

  private class ImageList extends UIItemList.ScrollList implements UIDuplicate {

    public class ClipboardImage extends LXClipboardComponent<ImageComponent> {
      public ClipboardImage(ImageComponent image) {
        super(ImageComponent.class, image);
      }
    }

    public ImageList(heronarts.glx.ui.UI ui, int x, int y, int w, int h) {
      super(ui, x, y, w, h);
    }

    @Override
    public void onPaste(LXClipboardItem item) {
      if (item instanceof ClipboardImage) {
        ClipboardImage component = (ClipboardImage) item;
        slideshow.getLX().command.perform(new Command.AddImage(slideshow, component.getComponentObject()));
      }
    }

    @Override
    public LXClipboardItem onCopy() {
      ImageComponent image = slideshow.getFocusedImage();
      if (image != null) {
        return new ClipboardImage(image);
      }
      return null;
    }
  }

  private void addImage(UI ui, UIDevice uiDevice, ImageComponent image) {
    ImageItem item = new ImageItem(image);
    this.imageMap.put(image, item);

    ImageControls controls = new ImageControls(ui, image);
    controls.setVisible(false);
    this.imageControls.put(image, controls);
    controls.addToContainer(uiDevice);

    this.imageList.addItem(image.getIndex(), item);
    updateIndices();
  }

  private void removeImage(UI ui, UIDevice uiDevice, ImageComponent image) {
    this.imageList.removeItem(this.imageMap.remove(image));
    this.imageControls.remove(image).removeFromContainer().dispose();
    updateIndices();
  }

  private void moveImage(UI ui, UIDevice uiDevice, ImageComponent image) {
    ImageItem item = this.imageMap.get(image);
    this.imageList.moveItem(item, image.getIndex());
    updateIndices();
  }

  private void updateIndices() {
    for (ImageControls controls : this.imageControls.values()) {
      controls.setIndexString();
    }
  }

  private void focusImage(ImageComponent image) {
    for (ImageControls controls : this.imageControls.values()) {
      controls.setVisible(controls.image == image);
    }
  }

  private class ImageControls extends UI2dContainer {

    private final ImageComponent image;
    private final StringParameter indexString = new StringParameter("Index");

    private ImageControls(UI ui, ImageComponent image) {
      super(0, 0, 0, UIDevice.CONTENT_HEIGHT);
      this.image = image;
      setLayout(UI2dContainer.Layout.HORIZONTAL);
      setChildSpacing(2);
      setIndexString();

      addListener(image.label, imageList.redraw);

      addColumn(this, "Image",
          new UITextBox(0, 0, COL_WIDTH, 16) {
            @Override
            public String getDescription() {
              return "Image " + indexString.getString() + " - " + image.fileName.getString();
            }
          }.setParameter(this.indexString).setEditable(false),
          newDropMenu(image.imageMode),
          sectionLabel("Actions").setTopMargin(6),
          new UIButton.Action(COL_WIDTH, 16, "Reload").setParameter(image.reload),
          new UIButton.Action(COL_WIDTH, 16, "Replace") {
            @Override
            public void onClick() {
              ui.lx.showOpenFileDialog(
                  "Replace Image",
                  "Image File",
                  new String[] { "jpg", "jpeg", "png", "gif" },
                  image.fileName.getString(),
                  (absolutePath) -> {
                    // Convert both paths to absolute and normalize
                    // Check if the given path is within the "Assets" directory
                    if (AssetPaths.isInAssetsFolder(absolutePath)) {
                      ui.lx.command
                          .perform(new Command.ReplaceImage(image, AssetPaths.toRelativePathFromAssets(absolutePath)));
                    } else {
                      LX.error(null, "Image file must be within the Assets directory");
                      lx.pushError(new Error(), "Image file must be within the Assets directory");
                    }
                  });
            }
          }.setDescription("Use this button to load a new image file into this slot"),

          new UIButton.Action(COL_WIDTH, 16, "Trigger") {
            @Override
            public void onClick() {
              ui.lx.command.perform(new Command.GoImage(slideshow, image));
            }

            @Override
            public String getDescription() {
              return UIParameterControl.getDescription(image.trigger);
            }
          }.setControlTarget(image.trigger)

      ).setChildSpacing(4);

      addColumn(this, "Animation", newKnob(image.speed)).setChildSpacing(4);

      addColumn(this, UIKnob.WIDTH, "Move",
          newKnob(image.translateX, 0),
          newKnob(image.translateY, 0),
          newKnob(image.translateZ, 0)).setChildSpacing(6);

      addVerticalBreak(ui, this);

      addColumn(this, UIKnob.WIDTH, "Kaleidoscope",
          newKnob(image.kaleidoscope.params.segments, 0),
          newKnob(image.kaleidoscope.params.rotatePhi, 0),
          newKnob(image.kaleidoscope.params.rotateTheta, 0)).setChildSpacing(6);

      addColumn(this, UIKnob.WIDTH, "KCenter",
          newKnob(image.kaleidoscope.params.x, 0),
          newKnob(image.kaleidoscope.params.y, 0),
          newKnob(image.kaleidoscope.params.z, 0)).setChildSpacing(6);

      addVerticalBreak(ui, this);

      addColumn(this, UIKnob.WIDTH, "Rotate",
          newKnob(image.yaw, 0),
          newKnob(image.pitch, 0),
          newKnob(image.roll, 0)).setChildSpacing(6);

      addColumn(this, UIKnob.WIDTH, "Speed",
          newKnob(image.speedX, 0),
          newKnob(image.speedY, 0),
          new UIButton.Action(COL_WIDTH - 8, 16, "reset").setParameter(image.resetDistance))
          .setChildSpacing(6);

      addColumn(this, UIKnob.WIDTH, "Scale",
          newKnob(image.scaleX, 0),
          newKnob(image.scaleY, 0),
          newKnob(image.scale, 0)).setChildSpacing(6);

      addColumn(this, UIKnob.WIDTH, "Scroll",
          // newKnob(image.scrollX, 0),
          // newKnob(image.scrollY, 0),
          newDoubleBox(image.scaleRange, UIKnob.WIDTH).setTopMargin(6),
          controlLabel(ui, "Range", UIKnob.WIDTH).setTopMargin(1)).setChildSpacing(6).setLeftMargin(2);

      addColumn(this, UIKnob.WIDTH, "Stretch",
          newKnob(image.stretchX, 0),
          newKnob(image.stretchY, 0),
          newKnob(image.stretchAspect, 0)).setChildSpacing(6).setLeftMargin(2).setRightMargin(2);

    }

    private void setIndexString() {
      this.indexString.setValue(String.valueOf(image.getIndex() + 1));
    }
  }

  private class ImageItem extends UIItemList.Item {

    private final ImageComponent image;

    private ImageItem(ImageComponent image) {
      this.image = image;
    }

    @Override
    public boolean isActive() {
      return (this.image == slideshow.getActiveImage()) ||
          (this.image == slideshow.getNextImage());
    }

    @Override
    public int getActiveColor(heronarts.glx.ui.UI ui) {
      return (this.image == slideshow.getActiveImage()) ? ui.theme.primaryColor.get()
          : ui.theme.listItemSecondaryColor.get();
    }

    @Override
    public void onActivate() {
      if (slideshow.getNextImage() == this.image) {
        slideshow.goImage(this.image.getIndex());
      } else if (slideshow.getTargetImage() != this.image) {
        slideshow.getLX().command.perform(new Command.GoImage(slideshow, this.image));
      }
    }

    @Override
    public String getLabel() {
      return this.image.getLabel();
    }

    @Override
    public void onRename(String name) {
      slideshow.getLX().command.perform(new LXCommand.Parameter.SetString(this.image.label, name));
    }

    @Override
    public void onReorder(int order) {
      slideshow.getLX().command.perform(new Command.MoveImage(slideshow, this.image, order));
    }

    @Override
    public void onDelete() {
      slideshow.getLX().command.perform(new Command.RemoveImage(slideshow, this.image));
    }

    @Override
    public void onFocus() {
      slideshow.focusedImage.setValue(this.image.getIndex());
    }

    @Override
    public boolean isChecked() {
      return this.image.autoCycleEligible.isOn();
    }

    @Override
    public void onCheck(boolean checked) {
      slideshow.getLX().command.perform(new LXCommand.Parameter.SetNormalized(this.image.autoCycleEligible, checked));
    }

  }
}