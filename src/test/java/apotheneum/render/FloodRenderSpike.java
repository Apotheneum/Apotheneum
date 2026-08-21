package apotheneum.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import apotheneum.Apotheneum;
import apotheneum.doved.patterns.Flood;
import heronarts.lx.LX;
import heronarts.lx.LXEngine;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;

/**
 * One-off animated render driver for Flood review GIFs, built on top of the
 * same fixture-loading / frame-loop / ffmpeg-assembly machinery as
 * {@link RenderSpike}. RenderSpike's CLI only supports static parameter
 * values set once before the render; Flood's review clips need a level
 * sweep, an agitation ramp, and timed surge triggers, so this driver adds a
 * per-frame parameter callback on top of the same primitives instead of
 * reinventing them. Test scope only - never shipped, never run in CI.
 *
 * Usage: {@code java apotheneum.render.FloodRenderSpike <clip-name>}, one of
 * the names in {@link #CLIPS}, or "all" to render every clip in one process
 * run (each clip still gets a fresh Flood instance and its own
 * animation state; only the LX/fixture/model are reused, per the "one LX per
 * process" rule in docs/headless-rendering.md).
 */
public final class FloodRenderSpike {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final Path OUTPUT_DIRECTORY = Path.of("target/flood-renders");
  private static final String FIXTURE_NAME = "Apotheneum";
  private static final int EXPECTED_POINT_COUNT = 28_320;
  private static final int SCALE = 4;
  private static final int FACE_BOUNDARY_WIDTH = 2;
  private static final int FACE_BOUNDARY_COLOR = 0x4050a0;
  private static final double REVIEW_GAIN = 2.;
  private static final double REVIEW_GAMMA = .5;
  private static final double FIXED_DELTA_MS = 1000. / 60.;
  private static final int GIF_FRAME_INTERVAL = 3; // 60fps engine -> 20fps GIF

  private FloodRenderSpike() {
  }

  private interface FrameUpdater {
    /**
     * @param pattern        the freshly constructed pattern for this clip
     * @param engineFrame    1-based engine frame number for this clip
     * @param progress       engineFrame / totalEngineFrames, in (0, 1]
     * @param elapsedSeconds seconds of clip time elapsed including this frame
     */
    void update(LXPattern pattern, int engineFrame, double progress, double elapsedSeconds);
  }

  private record Clip(
    String name,
    Function<LX, LXPattern> factory,
    double durationSeconds,
    FrameUpdater updater
  ) {
    int engineFrameCount() {
      return (int) Math.round(this.durationSeconds * 60);
    }
  }

  private static final List<Clip> CLIPS = List.of(
    new Clip("01-slow-fill", Flood::new, 7, (pattern, frame, progress, t) -> {
      final Flood flood = (Flood) pattern;
      flood.level.setValue(progress);
      flood.agitation.setValue(.35);
    }),

    new Clip("02-surge-lap", Flood::new, 7, (pattern, frame, progress, t) -> {
      final Flood flood = (Flood) pattern;
      flood.level.setValue(.5);
      flood.surgeSpeed.setValue(.16);
      flood.surgeWidth.setValue(.06);
      flood.surgeAngle.setValue(0);
      if (frame == 1) {
        flood.surge.trigger();
      }
    }),

    new Clip("03-glassy-vs-choppy", Flood::new, 8, (pattern, frame, progress, t) -> {
      final Flood flood = (Flood) pattern;
      flood.level.setValue(.5);
      flood.agitation.setValue(.05 + .9 * progress);
    }),

    new Clip("04-sliver-surge", Flood::new, 8, (pattern, frame, progress, t) -> {
      final Flood flood = (Flood) pattern;
      flood.level.setValue(.5);
      flood.surgeWidth.setValue(.02);
      flood.surgeSpeed.setValue(.7);
      flood.surgeHeight.setValue(8);
      flood.agitation.setValue(.15);
      // One full-width surge takes (1 + surgeWidth) / surgeSpeed seconds to
      // finish its lap; retrigger a beat after that so a new one launches
      // as the previous one closes out, reading as a repeating ring.
      final double period = 1.5;
      final double sinceLast = t % period;
      if (frame == 1 || sinceLast < FIXED_DELTA_MS / 1000.) {
        flood.surge.trigger();
      }
    }),

    new Clip("05-ankle-deep", Flood::new, 6, (pattern, frame, progress, t) -> {
      final Flood flood = (Flood) pattern;
      flood.level.setValue(.15);
      flood.meniscusWidth.setValue(.5);
      flood.sparkle.setValue(.85);
      flood.agitation.setValue(.2);
    }),

    new Clip("06-near-submerged", Flood::new, 6, (pattern, frame, progress, t) -> {
      final Flood flood = (Flood) pattern;
      flood.level.setValue(.85);
      flood.meniscusWidth.setValue(4);
      flood.depthFalloff.setValue(.92);
      flood.agitation.setValue(.3);
    }),

    new Clip("07-undulation", Flood::new, 6, (pattern, frame, progress, t) -> {
      final Flood flood = (Flood) pattern;
      flood.level.setValue(.5);
      flood.agitation.setValue(1);
      flood.meniscusWidth.setValue(1);
    })
  );

  public static void main(String[] args) throws Exception {
    if (args.length != 1 || args[0].isBlank()) {
      throw new IllegalArgumentException("Usage: FloodRenderSpike <clip-name>|all");
    }
    final List<Clip> selected = args[0].equals("all")
      ? CLIPS
      : List.of(CLIPS.stream().filter(c -> c.name().equals(args[0])).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unknown clip: " + args[0])));

    preflightFfmpeg();
    Files.createDirectories(OUTPUT_DIRECTORY);

    final Path mediaPath = Files.createTempDirectory("apotheneum-flood-render-");
    LX lx = null;
    try {
      copyFixtureMedia(mediaPath);

      final LX.Flags flags = new LX.Flags();
      flags.loadPreferences = false;
      flags.mediaPath = mediaPath.toString();
      flags.outputMode = LX.Flags.OutputMode.INACTIVE;

      lx = new LX(flags);
      lx.engine.output.enabled.setValue(false);
      assertOutputDisabled(lx, "LX construction");

      final heronarts.lx.structure.JsonFixture fixture =
        new heronarts.lx.structure.JsonFixture(lx, FIXTURE_NAME);
      lx.structure.addFixture(fixture);
      lx.structure.beforeEngineRun();
      if (fixture.error.isOn()) {
        throw new IllegalStateException("Fixture load failed: " + fixture.errorMessage.getString());
      }
      assertOutputDisabled(lx, "fixture load");

      Apotheneum.initialize(lx);
      if (!Apotheneum.exists) {
        throw new IllegalStateException("Apotheneum.exists was false after loading the real fixture");
      }
      final int pointCount = lx.getModel().size;
      if (pointCount != EXPECTED_POINT_COUNT) {
        LX.warning("FloodRenderSpike expected " + EXPECTED_POINT_COUNT + " points but loaded " + pointCount);
      }
      lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

      LXChannel channel = null;
      for (Clip clip : selected) {
        final LXPattern pattern = clip.factory().apply(lx);
        if (channel == null) {
          channel = lx.engine.mixer.addChannel(new LXPattern[] { pattern });
        } else {
          channel.addPattern(pattern);
          channel.goPattern(pattern, true); // skip transition: instant switch, no cross-blended frames
        }
        renderClip(lx, clip, pattern, mediaPath.resolve("render-frames"));
      }

      LX.log("FloodRenderSpike outputDirectory=" + OUTPUT_DIRECTORY.toAbsolutePath());
      LX.log("FloodRenderSpike outputEnabled=" + lx.engine.output.enabled.isOn());
    } finally {
      try {
        if (lx != null) {
          lx.dispose();
        }
      } finally {
        deleteTree(mediaPath);
      }
    }
  }

  private static void renderClip(
    LX lx,
    Clip clip,
    LXPattern pattern,
    Path temporaryFramesRoot
  ) throws IOException, InterruptedException {
    LX.log("FloodRenderSpike clip=" + clip.name() + " durationSeconds=" + clip.durationSeconds());
    final int engineFrameCount = clip.engineFrameCount();
    final LXEngine.Frame outputFrame = new LXEngine.Frame(lx);
    outputFrame.setModel(lx.getModel());

    final Path frameDirectory = Files.createDirectories(temporaryFramesRoot.resolve(clip.name()));
    int writtenFrames = 0;
    int nonBlackPeakCount = 0;
    double elapsedSeconds = 0;

    for (int frameNumber = 1; frameNumber <= engineFrameCount; ++frameNumber) {
      elapsedSeconds += FIXED_DELTA_MS / 1000.;
      final double progress = frameNumber / (double) engineFrameCount;
      clip.updater().update(pattern, frameNumber, progress, elapsedSeconds);

      assertOutputDisabled(lx, clip.name() + " before frame " + frameNumber);
      lx.engine.run();
      assertOutputDisabled(lx, clip.name() + " after frame " + frameNumber);

      lx.engine.copyFrameThreadSafe(outputFrame);
      final int[] colors = outputFrame.getMain();
      final int nonBlack = countNonBlackCubeExterior(colors);
      nonBlackPeakCount = Math.max(nonBlackPeakCount, nonBlack);

      if (frameNumber % GIF_FRAME_INTERVAL == 0) {
        ++writtenFrames;
        final BufferedImage image = renderCubeExterior(colors);
        writePng(image, frameDirectory.resolve("frame-%03d.png".formatted(writtenFrames)));
      }
    }

    final Apotheneum.Orientation cubeExterior = Apotheneum.cube.exterior;
    int usable = 0;
    for (int x = 0; x < cubeExterior.columns().length; ++x) {
      usable += cubeExterior.available(x);
    }
    LX.log(String.format(
      Locale.ROOT,
      "FloodRenderSpike clip=%s peakNonBlackFraction=%.6f engineFrames=%d gifFrames=%d",
      clip.name(),
      nonBlackPeakCount / (double) usable,
      engineFrameCount,
      writtenFrames
    ));

    final Path gifPath = OUTPUT_DIRECTORY.resolve(clip.name() + ".gif");
    assembleGif(frameDirectory, gifPath);
    LX.log("FloodRenderSpike output=" + gifPath.toAbsolutePath() + " bytes=" + Files.size(gifPath));
    deleteTree(frameDirectory);
  }

  private static int countNonBlackCubeExterior(int[] colors) {
    final Apotheneum.Orientation orientation = Apotheneum.cube.exterior;
    final Apotheneum.Column[] columns = orientation.columns();
    int nonBlack = 0;
    for (int x = 0; x < columns.length; ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        if ((colors[columns[x].points[y].index] & LXColor.RGB_MASK) != 0) {
          ++nonBlack;
        }
      }
    }
    return nonBlack;
  }

  private static BufferedImage renderCubeExterior(int[] colors) {
    final Apotheneum.Orientation orientation = Apotheneum.cube.exterior;
    final int width = orientation.width();
    final int height = orientation.height();
    final int scaledWidth = width * SCALE;
    final BufferedImage scaled = new BufferedImage(scaledWidth, height * SCALE, BufferedImage.TYPE_INT_RGB);
    final int[] pixels = ((DataBufferInt) scaled.getRaster().getDataBuffer()).getData();
    final Apotheneum.Column[] columns = orientation.columns();
    for (int x = 0; x < width; ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < height; ++y) {
        final int color = (y < available) ? colors[columns[x].points[y].index] : LXColor.BLACK;
        final int reviewColor = boostForReview(color);
        for (int scaleY = 0; scaleY < SCALE; ++scaleY) {
          final int pixelOffset = (y * SCALE + scaleY) * scaledWidth + x * SCALE;
          Arrays.fill(pixels, pixelOffset, pixelOffset + SCALE, reviewColor);
        }
      }
    }
    markFaceBoundaries(scaled);
    return scaled;
  }

  private static int boostForReview(int color) {
    final int red = boostChannel((color >> 16) & 0xff);
    final int green = boostChannel((color >> 8) & 0xff);
    final int blue = boostChannel(color & 0xff);
    return (red << 16) | (green << 8) | blue;
  }

  private static int boostChannel(int channel) {
    final double normalized = channel / 255.;
    return (int) Math.round(255. * Math.min(1., REVIEW_GAIN * Math.pow(normalized, REVIEW_GAMMA)));
  }

  private static void markFaceBoundaries(BufferedImage image) {
    final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    for (int faceIndex = 1; faceIndex < Apotheneum.cube.exterior.faces.length; ++faceIndex) {
      final int boundaryX = faceIndex * Apotheneum.GRID_WIDTH * SCALE;
      for (int offset = 0; offset < FACE_BOUNDARY_WIDTH; ++offset) {
        final int x = boundaryX - FACE_BOUNDARY_WIDTH / 2 + offset;
        for (int y = 0; y < image.getHeight(); ++y) {
          pixels[y * image.getWidth() + x] = FACE_BOUNDARY_COLOR;
        }
      }
    }
  }

  private static void assembleGif(Path frameDirectory, Path outputPath) throws IOException, InterruptedException {
    final Process process = new ProcessBuilder(
      "ffmpeg",
      "-nostdin",
      "-y",
      "-loglevel",
      "error",
      "-framerate",
      "20",
      "-start_number",
      "1",
      "-i",
      frameDirectory.resolve("frame-%03d.png").toString(),
      "-vf",
      "scale=iw:ih:flags=neighbor,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=none",
      "-loop",
      "0",
      outputPath.toString()
    ).redirectErrorStream(true).start();
    final String ffmpegOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final int exitCode;
    try {
      exitCode = process.waitFor();
    } catch (InterruptedException ix) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw ix;
    }
    if (exitCode != 0) {
      throw new IOException("ffmpeg failed for " + outputPath + ": " + ffmpegOutput.strip());
    }
  }

  private static void preflightFfmpeg() throws IOException, InterruptedException {
    final Process process;
    try {
      process = new ProcessBuilder("ffmpeg", "-version")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
    } catch (IOException iox) {
      throw new IOException(
        "ffmpeg is required for headless pattern rendering; install it and ensure it is on PATH",
        iox
      );
    }
    final int exitCode;
    try {
      exitCode = process.waitFor();
    } catch (InterruptedException ix) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw ix;
    }
    if (exitCode != 0) {
      throw new IOException("ffmpeg preflight failed with exit code " + exitCode);
    }
  }

  private static void copyFixtureMedia(Path mediaPath) throws IOException {
    final Path destination = Files.createDirectories(mediaPath.resolve("Fixtures"));
    try (Stream<Path> sources = Files.list(SOURCE_FIXTURES)) {
      for (Path source : sources.filter(Files::isRegularFile).toList()) {
        Files.copy(source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private static void assertOutputDisabled(LX lx, String stage) {
    if (lx.engine.output.enabled.isOn()) {
      throw new IllegalStateException("LX engine output became enabled during " + stage);
    }
  }

  private static void writePng(BufferedImage image, Path path) throws IOException {
    if (!ImageIO.write(image, "png", path.toFile())) {
      throw new IOException("No PNG writer available for " + path);
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
