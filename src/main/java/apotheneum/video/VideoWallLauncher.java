package apotheneum.video;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Owns the ffmpeg/ffplay child process pair that lays out the raw video feed
 * for the physical wall and renders it full-screen on a chosen display.
 * Purely a process manager — no UI code lives here; see
 * {@link UIVideoWallPanel} for the control panel that drives this.
 *
 * <p>ffplay has no {@code -filter_complex}, only {@code -vf} (single input,
 * single output) — confirmed by running it directly. The Panels layout needs
 * {@code split}/{@code hstack}, so the layout runs in ffmpeg, which pipes raw
 * frames to ffplay for display:
 *
 * <pre>
 * ffmpeg -v error -f rawvideo ... -i tcp://127.0.0.1:port -filter_complex "..." \
 *   -map "[out]" -f rawvideo -pix_fmt rgb24 -   |   ffplay ... -i - -fs
 * </pre>
 *
 * <p>Both binaries are found by probing a fixed list of absolute install
 * locations, not by searching {@code PATH}: a Finder-launched Chromatik
 * inherits no Homebrew PATH at all, so a PATH search would silently fail
 * every time.
 */
final class VideoWallLauncher {

  private static final String[] BIN_DIRS = {
    "/opt/homebrew/bin",
    "/usr/local/bin",
    "/opt/local/bin",
  };

  // The wall's actual usable picture area, once laid out but before the
  // processor's own crop/scale to its native 2688x600 signal.
  private static final int WALL_WIDTH = 2688;
  private static final int WALL_HEIGHT = 336;

  // The video processor's native signal: picture on top, a black bar below
  // (Top 336px mode), or the whole signal height (Shrink 600→336 mode).
  private static final int OUTPUT_WIDTH = 2688;
  private static final int OUTPUT_HEIGHT = 600;

  private static final long STOP_WAIT_SECONDS = 2;

  // Every process this JVM has started (ffmpeg and ffplay alike), so a
  // Chromatik exit can never leave one orphaned regardless of how the other
  // end of the pipe dies.
  private static final Set<Process> LIVE_PROCESSES = ConcurrentHashMap.newKeySet();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      for (Process process : LIVE_PROCESSES) {
        destroy(process);
      }
    }, "Apotheneum Video Wall Shutdown"));
  }

  private final ApotheneumVideo config;
  private final int port;

  // Index 0 is ffmpeg (does the layout, reads the TCP feed), index 1 is
  // ffplay (displays it). Null when nothing is running.
  private List<Process> processes = null;

  VideoWallLauncher(ApotheneumVideo config, int port) {
    this.config = config;
    this.port = port;
  }

  /** First candidate that is actually present and executable, or {@code null} if none is. */
  private static String findBinary(String name) {
    for (String dir : BIN_DIRS) {
      final File candidate = new File(dir, name);
      if (candidate.canExecute()) {
        return candidate.getPath();
      }
    }
    return null;
  }

  static String findFfplay() {
    return findBinary("ffplay");
  }

  static String findFfmpeg() {
    return findBinary("ffmpeg");
  }

  boolean isRunning() {
    return (this.processes != null) && this.processes.get(1).isAlive();
  }

  /** Builds the ffmpeg command line for the live config: layout + processor, raw video out to stdout. */
  List<String> buildFfmpegCommand(String ffmpegPath) {
    final int cropWidth = this.config.cropWidth.getValuei();
    final int cropHeight = this.config.cropHeight.getValuei();
    final String filterGraph = buildLayoutGraph() + ";" + buildProcessorGraph();

    final List<String> command = new ArrayList<>();
    command.add(ffmpegPath);
    command.add("-v");
    command.add("error");
    command.add("-f");
    command.add("rawvideo");
    command.add("-pixel_format");
    command.add("rgb24");
    command.add("-video_size");
    command.add(cropWidth + "x" + cropHeight);
    command.add("-framerate");
    command.add(formatRate(this.config.fps.getValue()));
    // The TCP feed carries no rate of its own and ffmpeg buffers a socket
    // input by default; without these, latency on the live feed grows
    // unbounded instead of staying current.
    command.add("-fflags");
    command.add("nobuffer");
    command.add("-flags");
    command.add("low_delay");
    command.add("-probesize");
    command.add("32");
    command.add("-i");
    command.add("tcp://127.0.0.1:" + this.port);
    command.add("-filter_complex");
    command.add(filterGraph);
    command.add("-map");
    command.add("[out]");
    command.add("-f");
    command.add("rawvideo");
    command.add("-pix_fmt");
    command.add("rgb24");
    command.add("-");
    return command;
  }

  /** Builds the ffplay command line for the live config: full-screen display of ffmpeg's stdout. */
  List<String> buildFfplayCommand(String ffplayPath) {
    final List<String> command = new ArrayList<>();
    command.add(ffplayPath);
    command.add("-loglevel");
    command.add("warning");
    command.add("-f");
    command.add("rawvideo");
    command.add("-pixel_format");
    command.add("rgb24");
    command.add("-video_size");
    command.add(OUTPUT_WIDTH + "x" + OUTPUT_HEIGHT);
    command.add("-framerate");
    command.add(formatRate(this.config.fps.getValue()));
    command.add("-i");
    command.add("-");
    command.add("-fs");
    return command;
  }

  /**
   * Lays the crop out onto {@code WALL_WIDTH}x{@code WALL_HEIGHT}, ending in a
   * {@code [w]} output. Verified against the live feed; not a place to invent
   * variants.
   */
  private String buildLayoutGraph() {
    switch (this.config.activePreset().layout.getEnum()) {
      case FIT:
        return buildFitGraph();
      case FILL:
        return "[0:v]scale=" + WALL_WIDTH + ":-2:flags=bilinear,crop="
          + WALL_WIDTH + ":" + WALL_HEIGHT + ":0:(ih-" + WALL_HEIGHT + ")/2[w]";
      case PANELS:
      default:
        if (this.config.cropWidth.getValuei() != VideoSource.MAX_WIDTH) {
          // Panels splits the source into four equal-width faces; that only
          // lands on the real face boundaries (multiples of GRID_WIDTH) when
          // the crop is the whole perimeter. A partial crop would slice
          // every face at the wrong seam, so fall back to Fit instead of
          // producing silently misaligned panels.
          ApotheneumVideoPlugin.error(
            "Panels layout needs the full " + VideoSource.MAX_WIDTH + "-column perimeter crop to align "
            + "with face boundaries; cropWidth is " + this.config.cropWidth.getValuei()
            + ", falling back to Fit");
          return buildFitGraph();
        }
        return buildPanelsGraph();
    }
  }

  private String buildFitGraph() {
    return "[0:v]scale=-2:" + WALL_HEIGHT + ":flags=bilinear,pad="
      + WALL_WIDTH + ":" + WALL_HEIGHT + ":(ow-iw)/2:0:black[w]";
  }

  private String buildPanelsGraph() {
    final int requestedGap = this.config.activePreset().gap.getValuei();
    final int n = this.config.activePreset().panelCount.getValuei();
    final int cropWidth = this.config.cropWidth.getValuei();
    final int cropHeight = this.config.cropHeight.getValuei();
    final int quarterCropWidth = cropWidth / 4;

    // gap is a maximum, not an absolute: below this, a face is height-limited
    // (WALL_HEIGHT tall at square pixels) rather than gap-limited, so shrinking
    // the gap further would waste picture quality for no reason.
    final int maxFaceWidth = WALL_HEIGHT * quarterCropWidth / cropHeight;
    final int maxGap = (WALL_WIDTH - n * maxFaceWidth) / (n + 1);
    final int gap = Math.max(0, Math.min(requestedGap, maxGap));

    int faceWidth = (WALL_WIDTH - (n + 1) * gap) / n;
    int faceHeight = faceWidth * cropHeight / quarterCropWidth;
    if (faceHeight > WALL_HEIGHT) {
      // Height-limited: a face can be at most WALL_HEIGHT tall at square
      // pixels, so shrink its width to match rather than stretching it.
      faceHeight = WALL_HEIGHT;
      faceWidth = faceHeight * quarterCropWidth / cropHeight;
    }
    if (gap != requestedGap) {
      ApotheneumVideoPlugin.log(
        "panels: " + n + " x " + faceWidth + "x" + faceHeight + ", gap " + gap
        + " (requested " + requestedGap + ")");
    }
    if (faceWidth <= 0) {
      ApotheneumVideoPlugin.error(
        "Panels layout with panelCount=" + n + " and gap=" + gap + " leaves no room for a face "
        + "(computed width " + faceWidth + "); falling back to Fit");
      return buildFitGraph();
    }
    final int cell = faceWidth + gap;
    final int stripWidth = faceWidth * 4;

    final StringBuilder graph = new StringBuilder();
    graph.append("[0:v]scale=").append(stripWidth).append(":").append(faceHeight)
      .append(":flags=bilinear,format=rgb24[s];");
    graph.append("[s]split=").append(n);
    for (int i = 0; i < n; ++i) {
      graph.append("[p").append(i).append("]");
    }
    graph.append(";");
    for (int i = 0; i < n; ++i) {
      final int faceX = (i % 4) * faceWidth;
      graph.append("[p").append(i).append("]crop=").append(faceWidth).append(":").append(faceHeight)
        .append(":").append(faceX).append(":0,pad=").append(cell).append(":").append(faceHeight)
        .append(":").append(gap).append(":0:black[f").append(i).append("];");
    }
    for (int i = 0; i < n; ++i) {
      graph.append("[f").append(i).append("]");
    }
    graph.append("hstack=inputs=").append(n).append(",pad=")
      .append(WALL_WIDTH).append(":").append(WALL_HEIGHT).append(":0:(oh-ih)/2:black[w]");
    return graph.toString();
  }

  /**
   * Maps the {@code [w]} wall-space picture onto the processor's native
   * {@code OUTPUT_WIDTH}x{@code OUTPUT_HEIGHT} signal, ending in a
   * {@code [out]} output.
   */
  private String buildProcessorGraph() {
    switch (this.config.activePreset().processor.getEnum()) {
      case SHRINK_600:
        return "[w]scale=" + OUTPUT_WIDTH + ":" + OUTPUT_HEIGHT + ":flags=bilinear[out]";
      case TOP_336:
      default:
        return "[w]pad=" + OUTPUT_WIDTH + ":" + OUTPUT_HEIGHT + ":0:0:black[out]";
    }
  }

  /** Starts the ffmpeg/ffplay pipeline full-screen on the given display, stopping any prior instance first. */
  void start(int displayIndex) {
    stop();

    final String ffmpegPath = findFfmpeg();
    final String ffplayPath = findFfplay();
    if ((ffmpegPath == null) || (ffplayPath == null)) {
      ApotheneumVideoPlugin.error(
        "Cannot launch video wall: missing " + (ffmpegPath == null ? "ffmpeg" : "ffplay")
        + " at any of " + String.join(", ", BIN_DIRS));
      return;
    }

    final List<String> ffmpegCommand = buildFfmpegCommand(ffmpegPath);
    final List<String> ffplayCommand = buildFfplayCommand(ffplayPath);
    ApotheneumVideoPlugin.log(
      "launching video wall on display " + displayIndex + ": "
      + String.join(" ", ffmpegCommand) + "  |  " + String.join(" ", ffplayCommand));

    final ProcessBuilder ffmpegBuilder = new ProcessBuilder(ffmpegCommand);
    // ffmpeg's stdout feeds ffplay's stdin via startPipeline; only stderr is ours to manage.
    ffmpegBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

    final ProcessBuilder ffplayBuilder = new ProcessBuilder(ffplayCommand);
    ffplayBuilder.environment().put("SDL_VIDEO_FULLSCREEN_DISPLAY", Integer.toString(displayIndex));
    // A full stdout/stderr pipe with nobody draining it would block ffplay's
    // writes and wedge the child; discard both so that can never happen.
    ffplayBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    ffplayBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

    try {
      // startPipeline (not a shell string) keeps both children directly killable;
      // a shell wrapper would leave them orphaned when the shell itself is destroyed.
      final List<Process> started = ProcessBuilder.startPipeline(List.of(ffmpegBuilder, ffplayBuilder));
      this.processes = started;
      LIVE_PROCESSES.addAll(started);
    } catch (IOException iox) {
      ApotheneumVideoPlugin.error("Failed to launch video wall pipeline: " + iox.getMessage());
      this.processes = null;
    }
  }

  /**
   * Requests shutdown and returns immediately. This is called synchronously
   * from a UI button callback, which in GLX runs on the engine thread — the
   * thread that renders the installation — so the bounded {@code waitFor} and
   * any forced kill happen on a short-lived daemon worker instead of here.
   */
  void stop() {
    if (this.processes == null) {
      return;
    }
    final List<Process> toStop = this.processes;
    this.processes = null;
    LIVE_PROCESSES.removeAll(toStop);
    final Thread waiter = new Thread(() -> {
      for (Process process : toStop) {
        destroy(process);
      }
    }, "Apotheneum Video Wall Stop");
    waiter.setDaemon(true);
    waiter.start();
  }

  private static void destroy(Process process) {
    if (!process.isAlive()) {
      return;
    }
    process.destroy();
    try {
      if (!process.waitFor(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException ix) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private static String formatRate(double fps) {
    if (fps == Math.rint(fps)) {
      return Long.toString((long) fps);
    }
    return Double.toString(fps);
  }

}
