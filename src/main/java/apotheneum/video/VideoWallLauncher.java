package apotheneum.video;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the ffmpeg/ffplay child processes that lay out the raw video feed
 * for the physical wall and renders it full-screen on a chosen display.
 * Purely a process manager — no UI code lives here; see
 * {@link UIVideoWallPanel} for the control panel that drives this.
 *
 * <p>ffplay has no {@code -filter_complex}, only {@code -vf} (single input,
 * single output) — confirmed by running it directly. The Panels layout needs
 * {@code split}/{@code hstack}, so the layout runs in ffmpeg, which pipes raw
 * frames to a Java relay, which feeds a persistent ffplay for display:
 *
 * <pre>
 * ffmpeg -v error -f rawvideo ... -i tcp://127.0.0.1:port -filter_complex "..." \
 *   -map "[out]" -f rawvideo -pix_fmt rgb24 -   |   relay   |   ffplay ... -i - -fs
 * </pre>
 *
 * <p>The relay makes setting changes make-before-break: a replacement ffmpeg
 * is started and read until it has produced one complete fixed-size frame,
 * then the relay atomically adopts that source and only then stops the old
 * ffmpeg. ffplay is left alone, so its full-screen window never flickers.
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
  static final int FRAME_BYTES = OUTPUT_WIDTH * OUTPUT_HEIGHT * 3;

  // A half-size, windowed view preserves the processor signal's aspect ratio
  // while leaving macOS's menu bar and the operator's Chromatik controls visible.
  private static final int PREVIEW_WIDTH = OUTPUT_WIDTH / 2;
  private static final int PREVIEW_HEIGHT = OUTPUT_HEIGHT / 2;
  private static final String PREVIEW_TITLE = "Apotheneum Video Preview";

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

  private volatile Playback playback = null;

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
    final Playback playback = this.playback;
    return (playback != null) && playback.isRunning();
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
    command.add(Integer.toString(ApotheneumVideo.FRAME_RATE));
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
    return buildFfplayCommand(ffplayPath, true);
  }

  /** Builds a titled, non-full-screen half-size preview for the local desktop. */
  List<String> buildPreviewFfplayCommand(String ffplayPath) {
    return buildFfplayCommand(ffplayPath, false);
  }

  private List<String> buildFfplayCommand(String ffplayPath, boolean fullScreen) {
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
    command.add(Integer.toString(ApotheneumVideo.FRAME_RATE));
    // ffplay otherwise queues raw frames to preserve presentation timing.
    // For a live lighting feed, stale frames are worse than dropped ones:
    // keep the input queue shallow and follow the external clock so the
    // screen always converges on the newest complete frame.
    command.add("-fflags");
    command.add("nobuffer");
    command.add("-flags");
    command.add("low_delay");
    command.add("-framedrop");
    command.add("-sync");
    command.add("ext");
    if (!fullScreen) {
      command.add("-window_title");
      command.add(PREVIEW_TITLE);
      command.add("-x");
      command.add(Integer.toString(PREVIEW_WIDTH));
      command.add("-y");
      command.add(Integer.toString(PREVIEW_HEIGHT));
    }
    command.add("-i");
    command.add("-");
    if (fullScreen) {
      command.add("-fs");
    }
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
        return "[0:v]scale=w=" + WALL_WIDTH + ":h=" + WALL_HEIGHT
          + ":force_original_aspect_ratio=increase:flags=bilinear,crop="
          + WALL_WIDTH + ":" + WALL_HEIGHT + ":(iw-ow)/2:(ih-oh)/2[w]";
      case PANELS:
      default:
        if ((this.config.cropWidth.getValuei() != VideoSource.MAX_WIDTH)
          || (this.config.cropX.getValuei() != 0)) {
          // Panels splits the source into four equal-width faces; that only
          // lands on the real face boundaries when the crop is the whole
          // perimeter. A partial crop would create blank/misaligned panels,
          // so fall back to Fit instead.
          ApotheneumVideoPlugin.error(
            "Panels layout needs a " + VideoSource.MAX_WIDTH + "-column perimeter crop; cropWidth is "
            + this.config.cropWidth.getValuei() + ", cropX is " + this.config.cropX.getValuei()
            + ", falling back to Fit");
          return buildFitGraph();
        }
        return buildPanelsGraph();
    }
  }

  private String buildFitGraph() {
    return "[0:v]scale=w=" + WALL_WIDTH + ":h=" + WALL_HEIGHT
      + ":force_original_aspect_ratio=decrease:flags=bilinear,pad="
      + WALL_WIDTH + ":" + WALL_HEIGHT + ":(ow-iw)/2:(oh-ih)/2:black[w]";
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

  /**
   * Starts full-screen playback on the given display. On the current display,
   * an existing ffplay is retained and only ffmpeg is replaced after its first
   * complete frame. SDL fixes the full-screen display at ffplay launch, so a
   * display change starts a new playback session make-before-break and retires
   * the old one after the new session has delivered its first frame.
   */
  void start(int displayIndex) {
    start(displayIndex, true);
  }

  /** Opens a normal macOS window on the desktop, independent of wall playback. */
  void startPreview() {
    start(-1, false);
  }

  /** Opens the local preview once, or brings its existing window to the front. */
  void openOrFocusPreview() {
    final Playback current = this.playback;
    if ((current != null) && current.isRunning() && current.matches(-1, false)) {
      current.focus();
      return;
    }
    startPreview();
  }

  private void start(int displayIndex, boolean fullScreen) {
    final String ffmpegPath = findFfmpeg();
    final String ffplayPath = findFfplay();
    if ((ffmpegPath == null) || (ffplayPath == null)) {
      ApotheneumVideoPlugin.error(
        "Cannot launch video wall: missing " + (ffmpegPath == null ? "ffmpeg" : "ffplay")
        + " at any of " + String.join(", ", BIN_DIRS));
      return;
    }

    final List<String> ffmpegCommand = buildFfmpegCommand(ffmpegPath);
    final Playback current = this.playback;
    if ((current != null) && current.isRunning() && current.matches(displayIndex, fullScreen)) {
      try {
        final FrameSource nextSource = startFfmpeg(ffmpegCommand);
        if (current.relay.switchWhenReady(nextSource)) {
          ApotheneumVideoPlugin.log(
            "warming replacement " + (fullScreen ? "video-wall" : "preview") + " layout: "
            + String.join(" ", ffmpegCommand));
          return;
        }
        nextSource.stop();
      } catch (IOException iox) {
        ApotheneumVideoPlugin.error(
          "Failed to start replacement video-wall layout; keeping the current layout: " + iox.getMessage());
        return;
      }
    }

    final List<String> ffplayCommand = buildFfplayCommand(ffplayPath, fullScreen);
    ApotheneumVideoPlugin.log(
      "launching " + (fullScreen ? "video wall on display " + displayIndex : "local video preview") + ": "
      + String.join(" ", ffmpegCommand) + "  | relay |  " + String.join(" ", ffplayCommand));

    Playback replacement = null;
    try {
      replacement = startPlayback(displayIndex, fullScreen, ffmpegCommand, ffplayCommand, current);
      this.playback = replacement;
      replacement.relay.start();
    } catch (IOException iox) {
      if (replacement != null) {
        replacement.stop();
      }
      ApotheneumVideoPlugin.error("Failed to launch " + (fullScreen ? "video wall" : "video preview")
        + ": " + iox.getMessage());
    }
  }

  private Playback startPlayback(
    int displayIndex,
    boolean fullScreen,
    List<String> ffmpegCommand,
    List<String> ffplayCommand,
    Playback prior
  ) throws IOException {
    final ProcessBuilder ffplayBuilder = new ProcessBuilder(ffplayCommand);
    if (fullScreen) {
      ffplayBuilder.environment().put("SDL_VIDEO_FULLSCREEN_DISPLAY", Integer.toString(displayIndex));
    }
    // A full stdout/stderr pipe with nobody draining it would block ffplay's
    // writes and wedge the child; discard both so that can never happen.
    ffplayBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    ffplayBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

    final Process ffplay = startTracked(ffplayBuilder);
    try {
      final FrameSource ffmpeg = startFfmpeg(ffmpegCommand);
      final AtomicReference<Playback> priorHolder = new AtomicReference<>(prior);
      final AtomicReference<Playback> playbackHolder = new AtomicReference<>();
      final Runnable firstFrameDelivered = (prior == null) ? null : () -> stopPrior(priorHolder);
      final FrameRelay relay = new FrameRelay(
        ffmpeg,
        ffplay.getOutputStream(),
        FRAME_BYTES,
        firstFrameDelivered,
        () -> stopPlayback(playbackHolder)
      );
      final Playback playback = new Playback(displayIndex, fullScreen, ffplay, relay, priorHolder);
      playbackHolder.set(playback);
      return playback;
    } catch (IOException iox) {
      stopProcess(ffplay);
      throw iox;
    }
  }

  private static FrameSource startFfmpeg(List<String> command) throws IOException {
    final ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
    return new ProcessFrameSource(startTracked(builder));
  }

  private static Process startTracked(ProcessBuilder builder) throws IOException {
    final Process process = builder.start();
    LIVE_PROCESSES.add(process);
    process.onExit().thenRun(() -> LIVE_PROCESSES.remove(process));
    return process;
  }

  /**
   * Requests shutdown and returns immediately. This is called synchronously
   * from a UI button callback, which in GLX runs on the engine thread — the
   * thread that renders the installation — so the bounded {@code waitFor} and
   * any forced kill happen on a short-lived daemon worker instead of here.
   */
  void stop() {
    final Playback toStop = this.playback;
    if (toStop == null) {
      return;
    }
    this.playback = null;
    toStop.stop();
  }

  private static void stopProcess(Process process) {
    final Thread waiter = new Thread(() -> destroy(process), "Apotheneum Video Wall Stop");
    waiter.setDaemon(true);
    waiter.start();
  }

  /** Uses Cocoa's process activation API; unlike Accessibility scripting, this needs no window permission. */
  private static void focusProcess(Process process) {
    final String script = "ObjC.import('AppKit'); var app = $.NSRunningApplication"
      + ".runningApplicationWithProcessIdentifier(" + process.pid() + ");"
      + "if (app) app.activateWithOptions($.NSApplicationActivateIgnoringOtherApps);";
    try {
      new ProcessBuilder("/usr/bin/osascript", "-l", "JavaScript", "-e", script)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
    } catch (IOException iox) {
      ApotheneumVideoPlugin.error("Failed to bring video preview forward: " + iox.getMessage());
    }
  }

  private static final class Playback {

    private final int displayIndex;
    private final boolean fullScreen;
    private final Process ffplay;
    private final FrameRelay relay;
    private final AtomicReference<Playback> prior;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private Playback(
      int displayIndex,
      boolean fullScreen,
      Process ffplay,
      FrameRelay relay,
      AtomicReference<Playback> prior
    ) {
      this.displayIndex = displayIndex;
      this.fullScreen = fullScreen;
      this.ffplay = ffplay;
      this.relay = relay;
      this.prior = prior;
    }

    private boolean isRunning() {
      return this.ffplay.isAlive() && this.relay.isRunning();
    }

    private boolean matches(int displayIndex, boolean fullScreen) {
      return (this.displayIndex == displayIndex) && (this.fullScreen == fullScreen);
    }

    private void focus() {
      focusProcess(this.ffplay);
    }

    private void stop() {
      if (!this.stopped.compareAndSet(false, true)) {
        return;
      }
      final Thread waiter = new Thread(() -> {
        // Kill the reader first. If ffplay has stopped draining stdin, this
        // releases any relay write before relay.stop() closes the same pipe.
        destroy(this.ffplay);
        this.relay.stop();
        // If this replacement is stopped before it delivers a first frame, its
        // make-before-break callback never runs. Retire the prior session here
        // as well so that edge case cannot orphan a full-screen player.
        stopPrior(this.prior);
      }, "Apotheneum Video Wall Stop");
      waiter.setDaemon(true);
      waiter.start();
    }
  }

  private static void stopPlayback(AtomicReference<Playback> playbackHolder) {
    final Playback playback = playbackHolder.get();
    if (playback != null) {
      playback.stop();
    }
  }

  private static void stopPrior(AtomicReference<Playback> priorHolder) {
    final Playback prior = priorHolder.getAndSet(null);
    if (prior != null) {
      prior.stop();
    }
  }

  interface FrameSource {
    InputStream input();
    void stop();
  }

  private static final class ProcessFrameSource implements FrameSource {

    private final Process process;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private ProcessFrameSource(Process process) {
      this.process = process;
    }

    @Override
    public InputStream input() {
      return this.process.getInputStream();
    }

    @Override
    public void stop() {
      if (!this.stopped.compareAndSet(false, true)) {
        return;
      }
      stopProcess(this.process);
    }
  }

  /** Fixed-size raw-frame relay with make-before-break source switching. */
  static final class FrameRelay {

    private final ReentrantLock stateLock = new ReentrantLock();
    private final Condition stateChanged = this.stateLock.newCondition();
    private final OutputStream output;
    private final int frameBytes;
    private final Runnable firstFrameDelivered;
    private final Runnable relayTerminated;
    private final byte[] frame;
    private final Thread relayThread;

    private volatile boolean running = true;
    private FrameSource current;
    private SwitchRequest pending = null;
    private byte[] bufferedFrame = null;
    private boolean deliveredAnyFrame = false;

    FrameRelay(
      FrameSource initial,
      OutputStream output,
      int frameBytes,
      Runnable firstFrameDelivered,
      Runnable relayTerminated
    ) {
      if (frameBytes <= 0) {
        throw new IllegalArgumentException("frameBytes must be positive");
      }
      this.current = initial;
      this.output = output;
      this.frameBytes = frameBytes;
      this.firstFrameDelivered = firstFrameDelivered;
      this.relayTerminated = relayTerminated;
      this.frame = new byte[frameBytes];
      this.relayThread = new Thread(this::relay, "Apotheneum Video Wall Relay");
      this.relayThread.setDaemon(true);
    }

    void start() {
      this.relayThread.start();
    }

    boolean isRunning() {
      return this.running;
    }

    boolean switchWhenReady(FrameSource next) {
      final SwitchRequest request = new SwitchRequest(next, this.frameBytes);
      SwitchRequest replaced = null;
      this.stateLock.lock();
      try {
        if (!this.running) {
          return false;
        }
        replaced = this.pending;
        this.pending = request;
      } finally {
        this.stateLock.unlock();
      }
      if (replaced != null) {
        replaced.source.stop();
      }
      final Thread warmer = new Thread(() -> warm(request), "Apotheneum Video Wall Warmup");
      warmer.setDaemon(true);
      warmer.start();
      return true;
    }

    private void warm(SwitchRequest request) {
      boolean complete = false;
      IOException failure = null;
      try {
        complete = readFully(request.source.input(), request.firstFrame);
      } catch (IOException iox) {
        failure = iox;
      }

      FrameSource old = null;
      boolean accepted = false;
      boolean failedWhileRequested = false;
      this.stateLock.lock();
      try {
        if (this.running && (this.pending == request) && complete) {
          old = this.current;
          this.current = request.source;
          this.bufferedFrame = request.firstFrame;
          this.pending = null;
          accepted = true;
        } else if (this.pending == request) {
          this.pending = null;
          failedWhileRequested = true;
        }
        this.stateChanged.signalAll();
      } finally {
        this.stateLock.unlock();
      }

      if (accepted) {
        // The new source and its first frame are installed before the old pipe
        // is closed, so the relay can never observe a frame-less gap.
        old.stop();
        ApotheneumVideoPlugin.log("video-wall layout switch complete");
      } else {
        request.source.stop();
        if (!failedWhileRequested) {
          ApotheneumVideoPlugin.log("discarded superseded video-wall layout");
        } else if (failure != null) {
          ApotheneumVideoPlugin.error(
            failure,
            "replacement video-wall layout failed before its first complete frame"
          );
        } else {
          ApotheneumVideoPlugin.error("replacement video-wall layout ended before its first complete frame");
        }
      }
    }

    private void relay() {
      try {
        while (this.running) {
          final byte[] readyFrame;
          final FrameSource source;
          this.stateLock.lock();
          try {
            readyFrame = this.bufferedFrame;
            if (readyFrame != null) {
              this.bufferedFrame = null;
              source = null;
            } else {
              source = this.current;
            }
          } finally {
            this.stateLock.unlock();
          }

          if (readyFrame != null) {
            writeFrame(readyFrame);
            continue;
          }

          final boolean complete;
          try {
            complete = readFully(source.input(), this.frame);
          } catch (IOException iox) {
            if (sourceIsCurrent(source)) {
              throw iox;
            }
            continue;
          }
          if (!complete) {
            if (!awaitReplacement(source)) {
              break;
            }
            continue;
          }
          if (sourceIsCurrent(source)) {
            writeFrame(this.frame);
          }
        }
      } catch (IOException iox) {
        if (this.running) {
          ApotheneumVideoPlugin.error("Video-wall relay stopped: " + iox.getMessage());
        }
      } finally {
        stop();
        if (this.relayTerminated != null) {
          this.relayTerminated.run();
        }
      }
    }

    private boolean sourceIsCurrent(FrameSource source) {
      this.stateLock.lock();
      try {
        return this.running && (this.current == source);
      } finally {
        this.stateLock.unlock();
      }
    }

    /** Waits when the active source ends while a replacement is still warming. */
    private boolean awaitReplacement(FrameSource ended) {
      this.stateLock.lock();
      try {
        while (this.running && (this.current == ended) && (this.pending != null)) {
          try {
            this.stateChanged.await();
          } catch (InterruptedException ix) {
            Thread.currentThread().interrupt();
            return false;
          }
        }
        return this.running && (this.current != ended);
      } finally {
        this.stateLock.unlock();
      }
    }

    private void writeFrame(byte[] bytes) throws IOException {
      this.output.write(bytes, 0, this.frameBytes);
      if (!this.deliveredAnyFrame) {
        this.deliveredAnyFrame = true;
        if (this.firstFrameDelivered != null) {
          this.firstFrameDelivered.run();
        }
      }
    }

    void stop() {
      final FrameSource active;
      final SwitchRequest waiting;
      this.stateLock.lock();
      try {
        if (!this.running) {
          return;
        }
        this.running = false;
        active = this.current;
        waiting = this.pending;
        this.pending = null;
        this.bufferedFrame = null;
        this.stateChanged.signalAll();
      } finally {
        this.stateLock.unlock();
      }
      try {
        this.output.close();
      } catch (IOException iox) {
        // The display process may already have closed its input.
      }
      active.stop();
      if ((waiting != null) && (waiting.source != active)) {
        waiting.source.stop();
      }
      if (Thread.currentThread() != this.relayThread) {
        this.relayThread.interrupt();
      }
    }

    private static final class SwitchRequest {
      private final FrameSource source;
      private final byte[] firstFrame;

      private SwitchRequest(FrameSource source, int frameBytes) {
        this.source = source;
        this.firstFrame = new byte[frameBytes];
      }
    }
  }

  private static boolean readFully(InputStream input, byte[] buffer) throws IOException {
    int offset = 0;
    while (offset < buffer.length) {
      final int read = input.read(buffer, offset, buffer.length - offset);
      if (read < 0) {
        return false;
      }
      if (read == 0) {
        continue;
      }
      offset += read;
    }
    return true;
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

}
