package apotheneum.video;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXPlugin;

/**
 * Streams a cropped rectangle of one cube face out of Chromatik as live video,
 * at the LEDs' own resolution — one video pixel per LED, no scaling, no
 * encoding: raw {@code rgb24} frames back to back over a plain TCP socket.
 *
 * <p>Consume it with ffplay or ffmpeg, telling it the crop size and the frame
 * rate out of band (the wire carries no header):
 * <pre>ffplay -f rawvideo -pixel_format rgb24 -video_size 192x24 -framerate 30 -i tcp://127.0.0.1:7878</pre>
 * <code>-framerate</code> must match this component's {@code fps} parameter:
 * raw video carries no rate and ffmpeg's rawvideo demuxer assumes 25, so a
 * mismatch does not drop frames, it accumulates latency without bound.
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

  private RawVideoServer server = null;
  private ApotheneumVideo config = null;

  @Override
  public void initialize(LX lx) {
    Apotheneum.initialize(lx);
    this.config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, this.config);
    this.server = new RawVideoServer(lx, this.config);
    this.server.start();
    this.config.setPort(this.server.getPort());
  }

  @Override
  public void dispose() {
    if (this.server != null) {
      this.server.stop();
      this.server = null;
    }
    // The config component is an engine child; LX disposes it with the engine.
  }

  private static final String PREFIX = "[APOTHENEUM VIDEO] ";

  static void log(String msg) {
    LX.log(PREFIX + msg);
  }

  static void error(String msg) {
    LX.error(PREFIX + msg);
  }

}
