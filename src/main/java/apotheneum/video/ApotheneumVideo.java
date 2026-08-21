package apotheneum.video;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;

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
 * <p>Default crop is 192x24: the wrapped cube perimeter at native resolution.
 */
public class ApotheneumVideo extends LXComponent {

  /** Engine path; parameters live at <code>/lx/apotheneumVideo/*</code>. */
  public static final String PATH = "apotheneumVideo";

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the video stream is running");

  public final EnumParameter<VideoSource> source =
    new EnumParameter<VideoSource>("Source", VideoSource.EXTERIOR_PERIMETER)
    .setDescription("Which cube face, or all four wrapped into one strip");

  public final DiscreteParameter cropX =
    new DiscreteParameter("X", 0, 0, VideoSource.MAX_WIDTH)
    .setDescription("Left edge of the crop, in LED columns from the left of the source");

  public final DiscreteParameter cropY =
    new DiscreteParameter("Y", 0, 0, Apotheneum.GRID_HEIGHT)
    .setDescription("Top edge of the crop, in LED rows from the top of the source");

  public final DiscreteParameter cropWidth =
    new DiscreteParameter("Width", 192, 1, VideoSource.MAX_WIDTH + 1)
    .setDescription("Width of the crop in LED columns");

  public final DiscreteParameter cropHeight =
    new DiscreteParameter("Height", 24, 1, Apotheneum.GRID_HEIGHT + 1)
    .setDescription("Height of the crop in LED rows");

  public final BoundedParameter fps =
    new BoundedParameter("FPS", 30, 1, 60)
    .setDescription("Frames per second sent to connected viewers");

  public final BooleanParameter maskDoor =
    new BooleanParameter("Mask Door", true)
    .setDescription("Black out the door cutouts, so the stream matches what is physically lit");

  public ApotheneumVideo(LX lx) {
    super(lx);
    addParameter("enabled", this.enabled);
    addParameter("source", this.source);
    addParameter("cropX", this.cropX);
    addParameter("cropY", this.cropY);
    addParameter("cropWidth", this.cropWidth);
    addParameter("cropHeight", this.cropHeight);
    addParameter("fps", this.fps);
    addParameter("maskDoor", this.maskDoor);
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

}
