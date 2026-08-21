package apotheneum.video;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Owns the ffplay child process that renders the raw video feed full-screen
 * on a chosen display, upscaled with nearest-neighbour scaling and padded to
 * the video wall's native 2688x600 signal. Purely a process manager — no UI
 * code lives here; see {@link UIVideoWallPanel} for the control panel that
 * drives this.
 *
 * <p>ffplay is found by probing a fixed list of absolute install locations,
 * not by searching {@code PATH}: a Finder-launched Chromatik inherits no
 * Homebrew PATH at all, so a PATH search would silently fail every time.
 */
final class VideoWallLauncher {

  private static final String[] FFPLAY_CANDIDATES = {
    "/opt/homebrew/bin/ffplay",
    "/usr/local/bin/ffplay",
    "/opt/local/bin/ffplay",
  };

  // The video processor's native signal: picture on top, a black bar below.
  private static final int OUTPUT_WIDTH = 2688;
  private static final int OUTPUT_HEIGHT = 600;

  private static final long STOP_WAIT_SECONDS = 2;

  // Every ffplay this JVM has started, so a Chromatik exit can never leave
  // one orphaned regardless of how the process ends.
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

  private Process process = null;

  VideoWallLauncher(ApotheneumVideo config, int port) {
    this.config = config;
    this.port = port;
  }

  /** First candidate that is actually present and executable, or {@code null} if none is. */
  static String findFfplay() {
    for (String candidate : FFPLAY_CANDIDATES) {
      if (new File(candidate).canExecute()) {
        return candidate;
      }
    }
    return null;
  }

  boolean isRunning() {
    return (this.process != null) && this.process.isAlive();
  }

  /** Builds the ffplay command line for the live config, without launching anything. */
  List<String> buildCommand(String ffplayPath) {
    final int cropWidth = this.config.cropWidth.getValuei();
    final int cropHeight = this.config.cropHeight.getValuei();
    final int tile = Math.max(1, Math.min(OUTPUT_WIDTH / cropWidth, OUTPUT_HEIGHT / cropHeight));
    // Bilinear, not nearest: the wall is viewed from far enough away that soft edges
    // read better than exact LED squares. Making this selectable is filed separately.
    final String filter =
      "scale=" + (cropWidth * tile) + ":" + (cropHeight * tile)
      + ":flags=bilinear,pad=" + OUTPUT_WIDTH + ":" + OUTPUT_HEIGHT + ":0:0:black";

    final List<String> command = new ArrayList<>();
    command.add(ffplayPath);
    command.add("-loglevel");
    command.add("warning");
    command.add("-fflags");
    command.add("nobuffer");
    command.add("-flags");
    command.add("low_delay");
    command.add("-probesize");
    command.add("32");
    command.add("-f");
    command.add("rawvideo");
    command.add("-pixel_format");
    command.add("rgb24");
    command.add("-video_size");
    command.add(cropWidth + "x" + cropHeight);
    command.add("-framerate");
    command.add(formatRate(this.config.fps.getValue()));
    command.add("-i");
    command.add("tcp://127.0.0.1:" + this.port);
    command.add("-vf");
    command.add(filter);
    command.add("-fs");
    return command;
  }

  /** Starts ffplay full-screen on the given display, stopping any prior instance first. */
  void start(int displayIndex) {
    stop();

    final String ffplayPath = findFfplay();
    if (ffplayPath == null) {
      ApotheneumVideoPlugin.error(
        "Cannot launch video wall: ffplay not found at any of " + String.join(", ", FFPLAY_CANDIDATES));
      return;
    }

    final List<String> command = buildCommand(ffplayPath);
    ApotheneumVideoPlugin.log(
      "launching video wall on display " + displayIndex + ": " + String.join(" ", command));

    final ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().put("SDL_VIDEO_FULLSCREEN_DISPLAY", Integer.toString(displayIndex));
    // A full stdout/stderr pipe with nobody draining it would block ffplay's
    // writes and wedge the child; discard both so that can never happen.
    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);

    try {
      this.process = builder.start();
      LIVE_PROCESSES.add(this.process);
    } catch (IOException iox) {
      ApotheneumVideoPlugin.error("Failed to launch ffplay: " + iox.getMessage());
      this.process = null;
    }
  }

  /**
   * Requests shutdown and returns immediately. This is called synchronously
   * from a UI button callback, which in GLX runs on the engine thread — the
   * thread that renders the installation — so the bounded {@code waitFor} and
   * any forced kill happen on a short-lived daemon worker instead of here.
   */
  void stop() {
    if (this.process == null) {
      return;
    }
    final Process toStop = this.process;
    this.process = null;
    LIVE_PROCESSES.remove(toStop);
    final Thread waiter = new Thread(() -> destroy(toStop), "Apotheneum Video Wall Stop");
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
