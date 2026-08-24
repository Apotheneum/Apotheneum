package apotheneum.video;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameterListener;

/**
 * Configuration for the video output — what rectangle of the model is cropped
 * and emitted.
 *
 * <p>Registered with the engine, so every value below is a canonical LX path
 * under <code>/lx/apotheneumVideo/</code>. There is no UI: these are driven over
 * OSC, or by an agent calling <code>set_parameter</code>, and they persist with
 * the project.
 *
 * <p>The component emits the raw crop at one byte per color channel, one pixel
 * per LED — no scaling, no padding. Upscaling to the video processor's signal
 * size (e.g. 2688x600, with the bottom rows left black) and any letterboxing
 * happen downstream, in the consumer's ffmpeg chain.
 *
 * <p>Default crop is 200x45: the whole wrapped cube perimeter at native
 * resolution, so downstream layout (Fit/Fill/Panels) is the thing shaping the
 * picture rather than a partial crop.
 */
public class ApotheneumVideo extends LXComponent {

  /** Engine path; parameters live at <code>/lx/apotheneumVideo/*</code>. */
  public static final String PATH = "apotheneumVideo";

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the video stream is running");

  public final DiscreteParameter cropX =
    new DiscreteParameter("X", 0, 0, VideoSource.MAX_WIDTH)
    .setDescription("Left edge of the crop, in LED columns from the left of the source");

  public final DiscreteParameter cropY =
    new DiscreteParameter("Y", 0, 0, Apotheneum.GRID_HEIGHT)
    .setDescription("Top edge of the crop, in LED rows from the top of the source");

  public final DiscreteParameter cropWidth =
    new DiscreteParameter("Width", VideoSource.MAX_WIDTH, 1, VideoSource.MAX_WIDTH + 1)
    .setDescription("Width of the crop in LED columns");

  public final DiscreteParameter cropHeight =
    new DiscreteParameter("Height", Apotheneum.GRID_HEIGHT, 1, Apotheneum.GRID_HEIGHT + 1)
    .setDescription("Height of the crop in LED rows");

  public final BoundedParameter fps =
    new BoundedParameter("FPS", 30, 1, 60)
    .setDescription("Frames per second sent to connected viewers");

  public final BooleanParameter maskDoor =
    new BooleanParameter("Mask Door", true)
    .setDescription("Black out the door cutouts, so the stream matches what is physically lit");

  /** How the cropped source is arranged onto the wall's 2688x336 usable picture area. */
  public enum Layout {
    /** Splits the source into four faces, each stretched to fill its own panel, with gaps in between. */
    PANELS,
    /** Scales the whole source uniformly to fit inside the wall, letterboxing left/right. */
    FIT,
    /** Scales the whole source uniformly to fill the wall, cropping left/right. */
    FILL;

    @Override
    public String toString() {
      return name().charAt(0) + name().substring(1).toLowerCase();
    }
  }

  /** How the 2688x336 laid-out picture is mapped onto the video processor's native 2688x600 signal. */
  public enum Processor {
    /** Sends the picture at its native height and pads the remaining rows below it with black. */
    TOP_336,
    /** Stretches the picture vertically to fill the full signal height. */
    SHRINK_600;

    @Override
    public String toString() {
      switch (this) {
        case TOP_336:
          return "Top 336px";
        case SHRINK_600:
        default:
          return "Shrink 600→336";
      }
    }
  }

  /** Which of the two configured looks is currently driving the wall. */
  public enum PresetSlot {
    A, B
  }

  public final EnumParameter<PresetSlot> activePreset =
    new EnumParameter<PresetSlot>("Preset", PresetSlot.A)
    .setDescription(
      "Which of the two configured looks (source/layout/processor/gap/panel count) is currently "
      + "driving the wall — switch here to jump between two prepared setups instead of re-dialing "
      + "each control live");

  /** The two configured looks; see {@link Preset}. Both start identical to the prior single-config defaults. */
  public final Preset presetA =
    new Preset(lx, VideoSource.EXTERIOR_PERIMETER, Layout.PANELS, Processor.TOP_336, 240, 4);
  public final Preset presetB =
    new Preset(lx, VideoSource.EXTERIOR_PERIMETER, Layout.PANELS, Processor.TOP_336, 240, 4);

  // Mirrors the active preset's source so that RawVideoServer, which reads
  // this field on every frame for every connected viewer, always sees the
  // currently-active preset's choice without needing to know presets exist
  // at all. Deliberately not a registered parameter: the persisted value
  // lives on presetA/presetB, this is just a live readable cache of "whichever
  // one is active right now." Kept in sync by the listeners below.
  final EnumParameter<VideoSource> source =
    new EnumParameter<VideoSource>("Source", VideoSource.EXTERIOR_PERIMETER);

  private final LXParameterListener presetSwitchListener = p -> syncSourceMirror();
  private final LXParameterListener presetASourceListener =
    p -> { if (this.activePreset.getEnum() == PresetSlot.A) { syncSourceMirror(); } };
  private final LXParameterListener presetBSourceListener =
    p -> { if (this.activePreset.getEnum() == PresetSlot.B) { syncSourceMirror(); } };

  public ApotheneumVideo(LX lx) {
    super(lx);
    addParameter("enabled", this.enabled);
    addParameter("cropX", this.cropX);
    addParameter("cropY", this.cropY);
    addParameter("cropWidth", this.cropWidth);
    addParameter("cropHeight", this.cropHeight);
    addParameter("fps", this.fps);
    addParameter("maskDoor", this.maskDoor);
    addParameter("activePreset", this.activePreset);
    addChild("presetA", this.presetA);
    addChild("presetB", this.presetB);

    this.activePreset.addListener(this.presetSwitchListener);
    this.presetA.source.addListener(this.presetASourceListener);
    this.presetB.source.addListener(this.presetBSourceListener);
    syncSourceMirror();
  }

  /** The preset currently selected by {@link #activePreset}. */
  Preset activePreset() {
    return (this.activePreset.getEnum() == PresetSlot.A) ? this.presetA : this.presetB;
  }

  private void syncSourceMirror() {
    this.source.setValue(activePreset().source.getEnum());
  }

  @Override
  public void dispose() {
    this.activePreset.removeListener(this.presetSwitchListener);
    this.presetA.source.removeListener(this.presetASourceListener);
    this.presetB.source.removeListener(this.presetBSourceListener);
    this.presetA.dispose();
    this.presetB.dispose();
    super.dispose();
  }

  // Not a parameter: it's not user-facing state, just the port the raw video
  // server actually bound, published here so the (studio-only) UI plugin can
  // read it without holding a reference to the core plugin instance.
  private volatile int port = -1;

  /** The port the raw video TCP server is actually listening on, or -1 before it starts. */
  int getPort() {
    return this.port;
  }

  void setPort(int port) {
    this.port = port;
  }

  // Same pattern as port: not user-facing state, just the most recent frame's
  // lit fraction, published here so the panel can show it without holding a
  // reference to the sampler that computed it.
  private volatile double litFraction = 0.0;

  /** Fraction (0-1) of the active source's crop that was actually lit in the most recently sampled frame. */
  double getLitFraction() {
    return this.litFraction;
  }

  void setLitFraction(double litFraction) {
    this.litFraction = litFraction;
  }

}
