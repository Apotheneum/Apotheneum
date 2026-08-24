package apotheneum.video;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXEngine;
import heronarts.lx.LXLoopTask;
import heronarts.lx.LXPlugin;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;

/**
 * Streams a cropped rectangle of the cube perimeter out of Chromatik as live video,
 * at the LEDs' own resolution — one video pixel per LED, no scaling, no
 * encoding: raw {@code rgb24} frames back to back over a plain TCP socket.
 *
 * <p>Consume it with ffplay or ffmpeg, telling it the crop size and the frame
 * rate out of band (the wire carries no header):
 * <pre>ffplay -f rawvideo -pixel_format rgb24 -video_size 200x45 -framerate 60 -i tcp://127.0.0.1:7878</pre>
 * <code>-framerate</code> must be 60: raw video carries no rate and ffmpeg's
 * rawvideo demuxer assumes 25, so a mismatch does not drop frames, it
 * accumulates latency without bound.
 * Override the port with <code>-Dapotheneum.video.port=</code>. Any upscaling
 * and letterboxing to the video processor's signal size belongs in the
 * consumer's ffmpeg chain, not here.
 *
 * <p>The crop is read from the composited frame, so it carries whatever the
 * mixer is actually showing — every pattern and effect, not just the raster
 * ones. It is deliberately not an {@link heronarts.lx.output.LXOutput}: LX
 * disables outputs on every project load, which would black the stream out
 * mid-show.
 *
 * <p>This is a plain {@link LXPlugin} — no studio/glx dependency — so the
 * server keeps running in a headless runtime that has no {@code glxstudio} on
 * its classpath. The left-pane control panel that points a display at this
 * stream is a separate studio-only plugin, {@link ApotheneumVideoUIPlugin},
 * so a missing glx class can never take this one down with it.
 */
@LXPlugin.Name("Apotheneum Video Output")
public class ApotheneumVideoPlugin implements LXPlugin {

  private LX lx = null;
  private RawVideoServer server = null;
  private ApotheneumVideo config = null;

  // A second, independent Frame/FaceRaster pair — not RawVideoServer's — so
  // that reporting the lit fraction needs no change to RawVideoServer at all.
  // It samples on every engine loop regardless of whether a viewer is
  // connected, at the same modest per-frame cost RawVideoServer already pays
  // per viewer (a threadsafe buffer copy plus a cached raster resolve).
  private LXEngine.Frame litFrame = null;
  private final FaceRaster litRaster = new FaceRaster();
  private final LXLoopTask sampleLitFractionTask = this::sampleLitFraction;

  @Override
  public void initialize(LX lx) {
    Apotheneum.initialize(lx);
    this.lx = lx;
    this.config = getOrRegisterConfig(lx);
    this.server = new RawVideoServer(lx, this.config);
    this.server.start();
    this.config.setPort(this.server.getPort());

    this.litFrame = new LXEngine.Frame(lx);
    lx.engine.addLoopTask(this.sampleLitFractionTask);
  }

  @Override
  public void dispose() {
    if (this.server != null) {
      this.server.stop();
      this.server = null;
    }
    if (this.lx != null) {
      this.lx.engine.removeLoopTask(this.sampleLitFractionTask);
    }
    this.litFrame = null;
    this.config = null;
    this.lx = null;
    // The config component is an engine child; LX owns its eventual disposal.
  }

  /**
   * Components registered on the engine outlive an individual plugin enable
   * cycle. LX exposes registration but no corresponding child removal API, so
   * re-use the canonical component when the plugin is disabled and re-enabled
   * rather than replacing it and leaking its parameter/listener graph.
   */
  static ApotheneumVideo getOrRegisterConfig(LX lx) {
    final LXComponent existing = lx.engine.getChild(ApotheneumVideo.PATH);
    if (existing == null) {
      final ApotheneumVideo config = new ApotheneumVideo(lx);
      lx.engine.registerComponent(ApotheneumVideo.PATH, config);
      return config;
    }
    if (existing instanceof ApotheneumVideo) {
      return (ApotheneumVideo) existing;
    }
    throw new IllegalStateException(
      "Engine child '" + ApotheneumVideo.PATH + "' is not an ApotheneumVideo: "
      + existing.getClass().getName());
  }

  /** Fraction of the crop that is actually lit, for the same source and crop a viewer would receive right now. */
  private void sampleLitFraction(double deltaMs) {
    if (!this.config.enabled.isOn()) {
      this.config.setLitFraction(0.0);
      return;
    }
    this.lx.engine.copyFrameThreadSafe(this.litFrame);
    final LXModel model = this.litFrame.getModel();
    final int[] indices = this.litRaster.resolve(
      model,
      this.config.source.getEnum(),
      this.config.cropX.getValuei(),
      this.config.cropY.getValuei(),
      this.config.cropWidth.getValuei(),
      this.config.cropHeight.getValuei()
    );
    this.config.setLitFraction(litFraction(indices, this.litFrame.getMain()));
  }

  /** Fraction of non-black pixels; a null/empty index set (source absent from the model) reports 0. */
  private static double litFraction(int[] indices, int[] colors) {
    if ((indices == null) || (indices.length == 0)) {
      return 0.0;
    }
    int lit = 0;
    for (int index : indices) {
      final int color = ((index >= 0) && (index < colors.length)) ? colors[index] : LXColor.BLACK;
      if ((color & 0xffffff) != 0) {
        ++lit;
      }
    }
    return (double) lit / indices.length;
  }

  private static final String PREFIX = "[APOTHENEUM VIDEO] ";

  static void log(String msg) {
    LX.log(PREFIX + msg);
  }

  static void error(String msg) {
    LX.error(PREFIX + msg);
  }

}
