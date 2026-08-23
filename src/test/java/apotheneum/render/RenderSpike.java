package apotheneum.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXEngine;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXPoint;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXPatternEngine.CompositeMode;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.JsonFixture;

/**
 * Headless proof that the shipped Apotheneum fixture can render a real pattern to images.
 * Run with {@code mvn -Ptests test-compile exec:exec} from the repository root.
 */
public final class RenderSpike {

  @FunctionalInterface
  public interface FrameUpdater<P extends LXPattern> {
    /**
     * Updates a freshly constructed pattern immediately before an engine frame.
     * Frame numbers are 1-based, progress is in (0, 1], and elapsed seconds
     * includes the frame about to be rendered.
     */
    void update(P pattern, int engineFrame, double progress, double elapsedSeconds);
  }

  public record AnimatedClip<P extends LXPattern>(
    String name,
    Function<LX, P> factory,
    double durationSeconds,
    FrameUpdater<P> updater
  ) {
    public AnimatedClip {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Clip name must not be blank");
      }
      Objects.requireNonNull(factory, "Clip factory must not be null");
      if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
        throw new IllegalArgumentException("Clip duration must be a positive finite number");
      }
      Objects.requireNonNull(updater, "Clip updater must not be null");
    }

    int engineFrameCount() {
      return (int) Math.round(this.durationSeconds * 60);
    }
  }

  public record AnimatedOptions(Path outputDirectory, int gifFrameInterval, int gifFrameRate) {
    public AnimatedOptions {
      Objects.requireNonNull(outputDirectory, "Output directory must not be null");
      if (gifFrameInterval <= 0 || gifFrameRate <= 0) {
        throw new IllegalArgumentException("GIF frame interval and frame rate must be positive");
      }
      if (60 % gifFrameInterval != 0 || gifFrameRate != 60 / gifFrameInterval) {
        throw new IllegalArgumentException(
          "GIF frame interval and frame rate must preserve the 60fps engine's real-time motion"
        );
      }
    }
  }

  /**
   * One pattern in a blend-mode channel. Layers appear on the channel in list
   * order, and the updater receives the concrete pattern type created by the
   * factory.
   */
  public record CompositeLayer<P extends LXPattern>(
    String name,
    Function<LX, P> factory,
    double compositeLevel,
    FrameUpdater<P> updater
  ) {
    public CompositeLayer {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Composite layer name must not be blank");
      }
      Objects.requireNonNull(factory, "Composite layer factory must not be null");
      if (!Double.isFinite(compositeLevel) || compositeLevel < 0 || compositeLevel > 1) {
        throw new IllegalArgumentException("Composite level must be finite and between 0 and 1");
      }
      Objects.requireNonNull(updater, "Composite layer updater must not be null");
    }
  }

  /**
   * An animated, ordered set of patterns rendered together on one blend-mode
   * channel. The first layer uses Normal over the transparent channel
   * background; every subsequent layer uses the named blend mode.
   */
  public record CompositeClip(
    String name,
    String blendMode,
    double durationSeconds,
    List<CompositeLayer<?>> layers
  ) {
    public CompositeClip {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Composite clip name must not be blank");
      }
      if (blendMode == null || blendMode.isBlank()) {
        throw new IllegalArgumentException("Composite blend mode must not be blank");
      }
      if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
        throw new IllegalArgumentException("Composite clip duration must be a positive finite number");
      }
      Objects.requireNonNull(layers, "Composite layers must not be null");
      layers = List.copyOf(layers);
      if (layers.isEmpty()) {
        throw new IllegalArgumentException("At least one composite layer is required");
      }
      final List<String> layerNames = new ArrayList<>(layers.size());
      for (CompositeLayer<?> layer : layers) {
        Objects.requireNonNull(layer, "Composite layers must not contain null");
        if (layerNames.contains(layer.name())) {
          throw new IllegalArgumentException("Duplicate composite layer name: " + layer.name());
        }
        layerNames.add(layer.name());
      }
    }

    int engineFrameCount() {
      return (int) Math.round(this.durationSeconds * 60);
    }
  }

  public record ColumnCrop(int startColumn, int width) {
    public ColumnCrop {
      if (startColumn < 0) {
        throw new IllegalArgumentException("Crop start column must be zero or greater");
      }
      if (width <= 0) {
        throw new IllegalArgumentException("Crop width must be positive");
      }
    }

    int sourceColumn(int outputColumn, int ringWidth) {
      if (width > ringWidth) {
        throw new IllegalArgumentException(
          "Crop width " + width + " exceeds surface ring width " + ringWidth
        );
      }
      return Math.floorMod(startColumn + outputColumn, ringWidth);
    }

    int normalizedStart(int ringWidth) {
      return Math.floorMod(startColumn, ringWidth);
    }
  }

  private enum RenderSurface {
    CUBE_EXTERIOR("cube-exterior", false, true),
    CUBE_INTERIOR("cube-interior", true, true),
    CYLINDER_EXTERIOR("cylinder-exterior", false, false),
    CYLINDER_INTERIOR("cylinder-interior", true, false);

    private final String fileStem;
    private final boolean interior;
    private final boolean cube;

    RenderSurface(String fileStem, boolean interior, boolean cube) {
      this.fileStem = fileStem;
      this.interior = interior;
      this.cube = cube;
    }

    private Apotheneum.Orientation orientation() {
      return switch (this) {
        case CUBE_EXTERIOR -> Apotheneum.cube.exterior;
        case CUBE_INTERIOR -> Apotheneum.cube.interior;
        case CYLINDER_EXTERIOR -> Apotheneum.cylinder.exterior;
        case CYLINDER_INTERIOR -> Apotheneum.cylinder.interior;
      };
    }
  }

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final Path OUTPUT_DIRECTORY = Path.of("target/spike");
  private static final String DEFAULT_PATTERN_CLASS_NAME = "apotheneum.doved.patterns.Fireflies";
  private static final String FIXTURE_NAME = "Apotheneum";
  private static final int EXPECTED_POINT_COUNT = 28_320;
  private static final int ENGINE_FRAME_COUNT = 300;
  private static final int GIF_FRAME_INTERVAL = 2;
  private static final int GIF_FRAME_COUNT = ENGINE_FRAME_COUNT / GIF_FRAME_INTERVAL;
  private static final int CONTACT_SAMPLE_INTERVAL = 10;
  private static final int CONTACT_SAMPLE_COUNT = ENGINE_FRAME_COUNT / CONTACT_SAMPLE_INTERVAL;
  private static final int SCALE = 4;
  private static final int FACE_BOUNDARY_WIDTH = 2;
  private static final int FACE_BOUNDARY_COLOR = 0x4050a0;
  private static final int CONTACT_COLUMNS = 3;
  private static final int CONTACT_GAP = 8;
  private static final double REVIEW_GAIN = 2.;
  private static final double REVIEW_GAMMA = .5;
  private static final double FIXED_DELTA_MS = 1000. / 60.;

  private RenderSpike() {
  }

  public static void main(String[] args) throws Exception {
    final Class<? extends LXPattern> patternClass = resolvePatternClass(args);
    final String parameterAssignments = optionalArgument(args, 1, "params=");
    final List<Class<? extends LXEffect>> effectClasses = resolveEffectClasses(args);
    final ColumnCrop crop = resolveCrop();
    preflightFfmpeg();

    final long jvmStartMillis = ManagementFactory.getRuntimeMXBean().getStartTime();
    final Path mediaPath = Files.createTempDirectory("apotheneum-render-spike-");
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

      final JsonFixture fixture = new JsonFixture(lx, FIXTURE_NAME);
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
      final long startupAndParseMs = System.currentTimeMillis() - jvmStartMillis;
      LX.log("RenderSpike Apotheneum.exists=true");
      LX.log("RenderSpike hasInterior=" + Apotheneum.hasInterior);
      LX.log("RenderSpike pointCount=" + pointCount + " expected=" + EXPECTED_POINT_COUNT);
      LX.log("RenderSpike jvmStartAndFixtureParseMs=" + startupAndParseMs);
      if (pointCount != EXPECTED_POINT_COUNT) {
        LX.warning("RenderSpike expected " + EXPECTED_POINT_COUNT + " points but loaded " + pointCount);
      }

      final LXPattern pattern = instantiatePattern(patternClass, lx);
      LX.log("RenderSpike patternClass=" + patternClass.getName());
      applyParameters(pattern, parameterAssignments);
      final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { pattern });
      final List<LXEffect> effects = addEffects(channel, effectClasses, lx);
      lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

      resetOutputDirectory();
      final Path temporaryFramesRoot = mediaPath.resolve("render-frames");
      if (!effects.isEmpty()) {
        LX.log("RenderSpike variant=bypass");
      }
      renderVariant(lx, pattern, "bypass", "", temporaryFramesRoot, crop);
      if (!effects.isEmpty()) {
        setEffectsEnabled(effects, true);
        renderVariant(lx, pattern, "effects", "-effects", temporaryFramesRoot, crop);
      }

      LX.log("RenderSpike outputDirectory=" + OUTPUT_DIRECTORY.toAbsolutePath());
      LX.log("RenderSpike outputEnabled=" + lx.engine.output.enabled.isOn());
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

  /**
   * Renders one or all clips from a pattern-owned catalog. Select a clip with
   * {@code -Dclip=<name>}; the default is {@code all}. Each clip gets a fresh
   * pattern instance while every clip shares the process's single LX instance.
   */
  public static <P extends LXPattern> void renderClips(
    List<AnimatedClip<P>> clips,
    AnimatedOptions options
  ) throws Exception {
    Objects.requireNonNull(clips, "Clip list must not be null");
    Objects.requireNonNull(options, "Animated options must not be null");
    if (clips.isEmpty()) {
      throw new IllegalArgumentException("At least one animated clip is required");
    }
    final Map<String, AnimatedClip<P>> clipsByName = new TreeMap<>();
    for (AnimatedClip<P> clip : clips) {
      Objects.requireNonNull(clip, "Clip list must not contain null");
      if (clipsByName.put(clip.name(), clip) != null) {
        throw new IllegalArgumentException("Duplicate animated clip name: " + clip.name());
      }
    }
    final String requestedClip = System.getProperty("clip", "all").strip();
    final List<AnimatedClip<P>> selectedClips;
    if (requestedClip.isEmpty() || requestedClip.equalsIgnoreCase("all")) {
      selectedClips = clips;
    } else {
      final AnimatedClip<P> selectedClip = clipsByName.get(requestedClip);
      if (selectedClip == null) {
        throw new IllegalArgumentException(
          "Unknown clip '" + requestedClip + "'. Available clips: " + clipNames(clips)
        );
      }
      selectedClips = List.of(selectedClip);
    }
    final ColumnCrop crop = resolveCrop();
    preflightFfmpeg();
    resetDirectory(options.outputDirectory());

    final Path mediaPath = Files.createTempDirectory("apotheneum-animated-render-");
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

      final JsonFixture fixture = new JsonFixture(lx, FIXTURE_NAME);
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
      if (lx.getModel().size != EXPECTED_POINT_COUNT) {
        LX.warning(
          "RenderSpike expected " + EXPECTED_POINT_COUNT + " points but loaded " + lx.getModel().size
        );
      }
      lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

      LXChannel channel = null;
      final Path temporaryFramesRoot = mediaPath.resolve("render-frames");
      for (AnimatedClip<P> clip : selectedClips) {
        final P pattern = clip.factory().apply(lx);
        if (pattern == null) {
          throw new IllegalStateException("Clip factory returned null for " + clip.name());
        }
        if (channel == null) {
          channel = lx.engine.mixer.addChannel(new LXPattern[] { pattern });
        } else {
          channel.addPattern(pattern);
          channel.goPattern(pattern, true);
        }
        renderAnimatedClip(lx, clip, pattern, options, crop, temporaryFramesRoot);
      }

      LX.log("RenderSpike outputDirectory=" + options.outputDirectory().toAbsolutePath());
      LX.log("RenderSpike outputEnabled=" + lx.engine.output.enabled.isOn());
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

  /**
   * Renders one or all ordered multi-pattern clips on blend-mode channels.
   * Select a clip with {@code -Dclip=<name>}; the default is {@code all}.
   */
  public static void renderCompositeClips(
    List<CompositeClip> clips,
    AnimatedOptions options
  ) throws Exception {
    Objects.requireNonNull(clips, "Composite clip list must not be null");
    Objects.requireNonNull(options, "Animated options must not be null");
    if (clips.isEmpty()) {
      throw new IllegalArgumentException("At least one composite clip is required");
    }
    final Map<String, CompositeClip> clipsByName = new TreeMap<>();
    for (CompositeClip clip : clips) {
      Objects.requireNonNull(clip, "Composite clip list must not contain null");
      if (clipsByName.put(clip.name(), clip) != null) {
        throw new IllegalArgumentException("Duplicate composite clip name: " + clip.name());
      }
    }
    final String requestedClip = System.getProperty("clip", "all").strip();
    final List<CompositeClip> selectedClips;
    if (requestedClip.isEmpty() || requestedClip.equalsIgnoreCase("all")) {
      selectedClips = clips;
    } else {
      final CompositeClip selectedClip = clipsByName.get(requestedClip);
      if (selectedClip == null) {
        throw new IllegalArgumentException(
          "Unknown clip '" + requestedClip + "'. Available clips: " +
          String.join(", ", clipsByName.keySet())
        );
      }
      selectedClips = List.of(selectedClip);
    }
    final ColumnCrop crop = resolveCrop();
    preflightFfmpeg();
    resetDirectory(options.outputDirectory());

    final Path mediaPath = Files.createTempDirectory("apotheneum-composite-render-");
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

      final JsonFixture fixture = new JsonFixture(lx, FIXTURE_NAME);
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
      if (lx.getModel().size != EXPECTED_POINT_COUNT) {
        LX.warning(
          "RenderSpike expected " + EXPECTED_POINT_COUNT + " points but loaded " + lx.getModel().size
        );
      }
      lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

      LXChannel channel = null;
      final Path temporaryFramesRoot = mediaPath.resolve("render-frames");
      for (CompositeClip clip : selectedClips) {
        final List<CompositeRuntime> runtimes = instantiateCompositeLayers(clip.layers(), lx);
        final LXPattern[] patterns = new LXPattern[runtimes.size()];
        for (int index = 0; index < runtimes.size(); ++index) {
          patterns[index] = runtimes.get(index).pattern();
        }
        if (channel == null) {
          channel = lx.engine.mixer.addChannel(patterns);
        } else {
          // Leave blend mode first so every previously active pattern is
          // deactivated, then replace the ordered pattern list on this channel.
          channel.patternEngine.compositeMode.setValue(CompositeMode.PLAYLIST);
          channel.setPatterns(patterns);
        }
        configureCompositeChannel(channel, clip, runtimes);
        renderCompositeClip(lx, clip, runtimes, options, crop, temporaryFramesRoot);
      }

      LX.log("RenderSpike outputDirectory=" + options.outputDirectory().toAbsolutePath());
      LX.log("RenderSpike outputEnabled=" + lx.engine.output.enabled.isOn());
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

  private static List<CompositeRuntime> instantiateCompositeLayers(
    List<CompositeLayer<?>> layers,
    LX lx
  ) {
    final List<CompositeRuntime> runtimes = new ArrayList<>(layers.size());
    for (CompositeLayer<?> layer : layers) {
      runtimes.add(instantiateCompositeLayer(layer, lx));
    }
    return runtimes;
  }

  private static <P extends LXPattern> CompositeRuntime instantiateCompositeLayer(
    CompositeLayer<P> layer,
    LX lx
  ) {
    final P pattern = layer.factory().apply(lx);
    if (pattern == null) {
      throw new IllegalStateException("Composite layer factory returned null for " + layer.name());
    }
    return new CompositeRuntime(
      layer.name(),
      pattern,
      layer.compositeLevel(),
      (frame, progress, seconds) -> layer.updater().update(pattern, frame, progress, seconds)
    );
  }

  private static void configureCompositeChannel(
    LXChannel channel,
    CompositeClip clip,
    List<CompositeRuntime> runtimes
  ) {
    channel.patternEngine.compositeDampingEnabled.setValue(false);
    for (int index = 0; index < runtimes.size(); ++index) {
      final CompositeRuntime runtime = runtimes.get(index);
      final LXPattern pattern = runtime.pattern();
      pattern.compositeLevel.setValue(runtime.compositeLevel());
      setCompositeBlend(pattern, (index == 0) ? "Normal" : clip.blendMode());
      pattern.enabled.setValue(true);
    }
    channel.patternEngine.compositeMode.setValue(CompositeMode.BLEND);
    if (!channel.isComposite()) {
      throw new IllegalStateException("Channel did not enter blend composite mode");
    }

    final StringBuilder description = new StringBuilder();
    for (int index = 0; index < runtimes.size(); ++index) {
      final CompositeRuntime runtime = runtimes.get(index);
      if (channel.getPattern(index) != runtime.pattern()) {
        throw new IllegalStateException("Composite pattern order changed at index " + index);
      }
      if (!description.isEmpty()) {
        description.append(',');
      }
      description.append(index).append(':').append(runtime.name())
        .append('@').append(runtime.compositeLevel())
        .append('[').append((index == 0) ? "Normal" : clip.blendMode()).append(']');
    }
    LX.log(
      "RenderSpike compositeClip=" + clip.name() +
      " blendMode=" + clip.blendMode() +
      " layers=" + description
    );
  }

  private static void setCompositeBlend(LXPattern pattern, String requestedName) {
    final List<String> available = new ArrayList<>();
    for (LXBlend blend : pattern.compositeBlend.getObjects()) {
      final String label = blend.getLabel();
      final String className = blend.getClass().getSimpleName();
      available.add(label);
      if (matchesBlendName(requestedName, label) ||
          matchesBlendName(requestedName, blend.getName()) ||
          matchesBlendName(requestedName, className) ||
          matchesBlendName(requestedName, className.replaceFirst("Blend$", ""))) {
        pattern.compositeBlend.setValue(blend);
        return;
      }
    }
    throw new IllegalArgumentException(
      "Unknown composite blend mode '" + requestedName + "'. Available modes: " +
      String.join(", ", available)
    );
  }

  private static boolean matchesBlendName(String requestedName, String candidateName) {
    return candidateName != null && requestedName.strip().equalsIgnoreCase(candidateName.strip());
  }

  private static void renderCompositeClip(
    LX lx,
    CompositeClip clip,
    List<CompositeRuntime> runtimes,
    AnimatedOptions options,
    ColumnCrop crop,
    Path temporaryFramesRoot
  ) throws IOException, InterruptedException {
    final int engineFrameCount = clip.engineFrameCount();
    final LXEngine.Frame outputFrame = new LXEngine.Frame(lx);
    outputFrame.setModel(lx.getModel());
    final EnumSet<RenderSurface> availableSurfaces = availableSurfaces();
    final EnumSet<RenderSurface> drivenSurfaces = EnumSet.noneOf(RenderSurface.class);
    final double[] peakNonBlackFractions = new double[RenderSurface.values().length];
    final int expectedGifFrames = engineFrameCount / options.gifFrameInterval();
    if (expectedGifFrames == 0) {
      throw new IllegalArgumentException(
        "Composite clip " + clip.name() + " is too short to produce a GIF frame at interval " +
        options.gifFrameInterval()
      );
    }
    final List<int[]> gifFrames = new ArrayList<>(expectedGifFrames);
    long totalFrameNanos = 0;
    double totalMeanBrightness = 0;

    for (int frameNumber = 1; frameNumber <= engineFrameCount; ++frameNumber) {
      final double elapsedSeconds = frameNumber * FIXED_DELTA_MS / 1000.;
      final double progress = frameNumber / (double) engineFrameCount;
      for (CompositeRuntime runtime : runtimes) {
        runtime.updater().update(frameNumber, progress, elapsedSeconds);
      }

      assertOutputDisabled(lx, clip.name() + " before frame " + frameNumber);
      final long frameStartNanos = System.nanoTime();
      lx.engine.run();
      totalFrameNanos += System.nanoTime() - frameStartNanos;
      assertOutputDisabled(lx, clip.name() + " after frame " + frameNumber);

      lx.engine.copyFrameThreadSafe(outputFrame);
      final int[] colors = outputFrame.getMain();
      totalMeanBrightness += meanBrightness(colors);
      updateSurfaceCoverage(availableSurfaces, drivenSurfaces, peakNonBlackFractions, colors);
      if (frameNumber % options.gifFrameInterval() == 0) {
        gifFrames.add(colors.clone());
      }
    }

    if (gifFrames.size() != expectedGifFrames) {
      throw new IllegalStateException(
        "Expected " + expectedGifFrames + " GIF frames, got " + gifFrames.size()
      );
    }
    logSkippedSurfaces(
      runtimes.get(0).pattern(),
      availableSurfaces,
      drivenSurfaces,
      peakNonBlackFractions,
      clip.name()
    );
    validateCrop(crop, drivenSurfaces);
    final Path clipOutputDirectory = Files.createDirectories(options.outputDirectory().resolve(clip.name()));
    for (RenderSurface surface : drivenSurfaces) {
      writeAnimatedSurfaceArtifacts(
        surface,
        gifFrames,
        clipOutputDirectory,
        temporaryFramesRoot.resolve(clip.name()),
        options.gifFrameRate(),
        crop
      );
    }

    final double meanFrameMs = totalFrameNanos / 1_000_000. / engineFrameCount;
    LX.log(String.format(
      Locale.ROOT,
      "RenderSpike compositeClip=%s blendMode=%s drivenSurfaces=%s " +
      "peakNonBlackFractions=%s meanBrightnessPct=%.3f meanFrameMs=%.3f " +
      "engineFrames=%d gifFrames=%d",
      clip.name(),
      clip.blendMode(),
      surfaceNames(drivenSurfaces),
      surfaceCoverageSummary(drivenSurfaces, peakNonBlackFractions),
      totalMeanBrightness / engineFrameCount,
      meanFrameMs,
      engineFrameCount,
      gifFrames.size()
    ));
  }

  @FunctionalInterface
  private interface CompositeRuntimeUpdater {
    void update(int engineFrame, double progress, double elapsedSeconds);
  }

  private record CompositeRuntime(
    String name,
    LXPattern pattern,
    double compositeLevel,
    CompositeRuntimeUpdater updater
  ) {
  }

  private static <P extends LXPattern> void renderAnimatedClip(
    LX lx,
    AnimatedClip<P> clip,
    P pattern,
    AnimatedOptions options,
    ColumnCrop crop,
    Path temporaryFramesRoot
  ) throws IOException, InterruptedException {
    final int engineFrameCount = clip.engineFrameCount();
    LX.log(
      "RenderSpike clip=" + clip.name() +
      " durationSeconds=" + clip.durationSeconds() +
      " patternClass=" + pattern.getClass().getName()
    );
    final LXEngine.Frame outputFrame = new LXEngine.Frame(lx);
    outputFrame.setModel(lx.getModel());
    final EnumSet<RenderSurface> availableSurfaces = availableSurfaces();
    final EnumSet<RenderSurface> drivenSurfaces = EnumSet.noneOf(RenderSurface.class);
    final double[] peakNonBlackFractions = new double[RenderSurface.values().length];
    final int expectedGifFrames = engineFrameCount / options.gifFrameInterval();
    if (expectedGifFrames == 0) {
      throw new IllegalArgumentException(
        "Clip " + clip.name() + " is too short to produce a GIF frame at interval " +
        options.gifFrameInterval()
      );
    }
    final List<int[]> gifFrames = new ArrayList<>(expectedGifFrames);
    long totalFrameNanos = 0;
    double totalMeanBrightness = 0;

    for (int frameNumber = 1; frameNumber <= engineFrameCount; ++frameNumber) {
      final double elapsedSeconds = frameNumber * FIXED_DELTA_MS / 1000.;
      final double progress = frameNumber / (double) engineFrameCount;
      clip.updater().update(pattern, frameNumber, progress, elapsedSeconds);

      assertOutputDisabled(lx, clip.name() + " before frame " + frameNumber);
      final long frameStartNanos = System.nanoTime();
      lx.engine.run();
      totalFrameNanos += System.nanoTime() - frameStartNanos;
      assertOutputDisabled(lx, clip.name() + " after frame " + frameNumber);

      lx.engine.copyFrameThreadSafe(outputFrame);
      final int[] colors = outputFrame.getMain();
      totalMeanBrightness += meanBrightness(colors);
      updateSurfaceCoverage(availableSurfaces, drivenSurfaces, peakNonBlackFractions, colors);
      if (frameNumber % options.gifFrameInterval() == 0) {
        gifFrames.add(colors.clone());
      }
    }

    if (gifFrames.size() != expectedGifFrames) {
      throw new IllegalStateException(
        "Expected " + expectedGifFrames + " GIF frames, got " + gifFrames.size()
      );
    }
    logSkippedSurfaces(pattern, availableSurfaces, drivenSurfaces, peakNonBlackFractions, clip.name());
    validateCrop(crop, drivenSurfaces);
    final Path clipOutputDirectory = Files.createDirectories(options.outputDirectory().resolve(clip.name()));
    for (RenderSurface surface : drivenSurfaces) {
      writeAnimatedSurfaceArtifacts(
        surface,
        gifFrames,
        clipOutputDirectory,
        temporaryFramesRoot.resolve(clip.name()),
        options.gifFrameRate(),
        crop
      );
    }

    final double meanFrameMs = totalFrameNanos / 1_000_000. / engineFrameCount;
    LX.log(String.format(
      Locale.ROOT,
      "RenderSpike clip=%s drivenSurfaces=%s peakNonBlackFractions=%s " +
      "meanBrightnessPct=%.3f meanFrameMs=%.3f engineFrames=%d gifFrames=%d",
      clip.name(),
      surfaceNames(drivenSurfaces),
      surfaceCoverageSummary(drivenSurfaces, peakNonBlackFractions),
      totalMeanBrightness / engineFrameCount,
      meanFrameMs,
      engineFrameCount,
      gifFrames.size()
    ));
  }

  private static <P extends LXPattern> String clipNames(List<AnimatedClip<P>> clips) {
    final List<String> names = new ArrayList<>(clips.size());
    for (AnimatedClip<P> clip : clips) {
      names.add(clip.name());
    }
    return String.join(", ", names);
  }

  private static void renderVariant(
    LX lx,
    LXPattern pattern,
    String variant,
    String fileSuffix,
    Path temporaryFramesRoot,
    ColumnCrop crop
  ) throws IOException, InterruptedException {
    if (!fileSuffix.isEmpty()) {
      LX.log("RenderSpike variant=" + variant);
    }
    final LXEngine.Frame outputFrame = new LXEngine.Frame(lx);
    outputFrame.setModel(lx.getModel());
    final EnumSet<RenderSurface> availableSurfaces = availableSurfaces();
    final EnumSet<RenderSurface> drivenSurfaces = EnumSet.noneOf(RenderSurface.class);
    final double[] peakNonBlackFractions = new double[RenderSurface.values().length];
    final List<int[]> gifFrames = new ArrayList<>(GIF_FRAME_COUNT);
    long totalFrameNanos = 0;
    for (int frameNumber = 1; frameNumber <= ENGINE_FRAME_COUNT; ++frameNumber) {
      assertOutputDisabled(lx, "before frame " + frameNumber);
      final long frameStartNanos = System.nanoTime();
      lx.engine.run();
      final long frameNanos = System.nanoTime() - frameStartNanos;
      totalFrameNanos += frameNanos;
      assertOutputDisabled(lx, "after frame " + frameNumber);

      lx.engine.copyFrameThreadSafe(outputFrame);
      final int[] colors = outputFrame.getMain();
      logFrameStats(frameNumber, colors, frameNanos);
      updateSurfaceCoverage(availableSurfaces, drivenSurfaces, peakNonBlackFractions, colors);
      if (frameNumber % GIF_FRAME_INTERVAL == 0) {
        gifFrames.add(colors.clone());
      }
    }

    if (gifFrames.size() != GIF_FRAME_COUNT) {
      throw new IllegalStateException("Expected " + GIF_FRAME_COUNT + " GIF frames, got " + gifFrames.size());
    }
    LX.log("RenderSpike drivenSurfaces=" + surfaceNames(drivenSurfaces));
    logSkippedSurfaces(pattern, availableSurfaces, drivenSurfaces, peakNonBlackFractions, variant);
    validateCrop(crop, drivenSurfaces);
    for (RenderSurface surface : drivenSurfaces) {
      writeSurfaceArtifacts(surface, gifFrames, fileSuffix, temporaryFramesRoot, crop);
    }

    final double meanFrameMs = totalFrameNanos / 1_000_000. / ENGINE_FRAME_COUNT;
    LX.log(String.format(Locale.ROOT, "RenderSpike meanFrameMs=%.3f", meanFrameMs));
    LX.log("RenderSpike engineFrames=" + ENGINE_FRAME_COUNT + " gifFrames=" + gifFrames.size());
  }

  private static Class<? extends LXPattern> resolvePatternClass(String[] args) {
    if (args.length > 3) {
      throw new IllegalArgumentException(
        "Usage: RenderSpike [fully-qualified LXPattern class] [name=value,name=value] " +
        "[fully-qualified LXEffect class,...]"
      );
    }
    final String className = (args.length == 0 || args[0].isBlank()) ? DEFAULT_PATTERN_CLASS_NAME : args[0];
    final Class<?> candidate;
    try {
      candidate = RenderSpike.class.getClassLoader().loadClass(className);
    } catch (ClassNotFoundException cnfx) {
      throw new IllegalArgumentException("Pattern class not found: " + className, cnfx);
    }
    if (!LXPattern.class.isAssignableFrom(candidate)) {
      throw new IllegalArgumentException("Pattern class is not an LXPattern: " + className);
    }
    return candidate.asSubclass(LXPattern.class);
  }

  private static List<Class<? extends LXEffect>> resolveEffectClasses(String[] args) {
    final String effectsArgument = optionalArgument(args, 2, "effects=");
    if (effectsArgument.isBlank()) {
      return List.of();
    }
    final List<Class<? extends LXEffect>> effectClasses = new ArrayList<>();
    for (String classNameEntry : effectsArgument.split(",", -1)) {
      final String className = classNameEntry.strip();
      if (className.isEmpty()) {
        throw new IllegalArgumentException(
          "Invalid effects list '" + effectsArgument +
          "'; expected fully-qualified class names separated by commas"
        );
      }
      final Class<?> candidate;
      try {
        candidate = RenderSpike.class.getClassLoader().loadClass(className);
      } catch (ClassNotFoundException cnfx) {
        throw new IllegalArgumentException("Effect class not found: " + className, cnfx);
      }
      if (!LXEffect.class.isAssignableFrom(candidate)) {
        throw new IllegalArgumentException("Effect class is not an LXEffect: " + className);
      }
      effectClasses.add(candidate.asSubclass(LXEffect.class));
    }
    return effectClasses;
  }

  private static String optionalArgument(String[] args, int index, String mavenPrefix) {
    if (args.length <= index) {
      return "";
    }
    final String argument = args[index];
    return argument.startsWith(mavenPrefix) ? argument.substring(mavenPrefix.length()) : argument;
  }

  private static ColumnCrop resolveCrop() {
    final String startValue = System.getProperty("cropStart", "").strip();
    final String widthValue = System.getProperty("cropWidth", "").strip();
    if (startValue.isEmpty() && widthValue.isEmpty()) {
      LX.log("RenderSpike crop=(none)");
      return null;
    }
    if (startValue.isEmpty() || widthValue.isEmpty()) {
      throw new IllegalArgumentException("cropStart and cropWidth must be specified together");
    }
    try {
      final ColumnCrop crop = new ColumnCrop(Integer.parseInt(startValue), Integer.parseInt(widthValue));
      LX.log("RenderSpike cropStart=" + crop.startColumn() + " cropWidth=" + crop.width());
      return crop;
    } catch (NumberFormatException nfx) {
      throw new IllegalArgumentException("cropStart and cropWidth must be integers", nfx);
    }
  }

  private static List<LXEffect> addEffects(
    LXChannel channel,
    List<Class<? extends LXEffect>> effectClasses,
    LX lx
  ) {
    final List<LXEffect> effects = new ArrayList<>(effectClasses.size());
    for (Class<? extends LXEffect> effectClass : effectClasses) {
      final LXEffect effect = instantiateEffect(effectClass, lx);
      // A bypass must be immediate; effect enable/disable damping otherwise leaks into control frames.
      effect.setDamping(false);
      effect.disable();
      channel.addEffect(effect);
      effects.add(effect);
    }
    LX.log("RenderSpike effects=" + effectClassNames(effectClasses));
    return effects;
  }

  private static LXEffect instantiateEffect(Class<? extends LXEffect> effectClass, LX lx) {
    try {
      return effectClass.getConstructor(LX.class).newInstance(lx);
    } catch (NoSuchMethodException nsx) {
      throw new IllegalArgumentException(
        "Effect class must have a public constructor accepting LX: " + effectClass.getName(),
        nsx
      );
    } catch (InvocationTargetException itx) {
      throw new IllegalStateException("Effect constructor failed: " + effectClass.getName(), itx.getCause());
    } catch (ReflectiveOperationException rox) {
      throw new IllegalStateException("Could not instantiate effect: " + effectClass.getName(), rox);
    }
  }

  private static String effectClassNames(List<Class<? extends LXEffect>> effectClasses) {
    final List<String> names = new ArrayList<>(effectClasses.size());
    for (Class<? extends LXEffect> effectClass : effectClasses) {
      names.add(effectClass.getName());
    }
    return names.isEmpty() ? "(none)" : String.join(",", names);
  }

  private static void setEffectsEnabled(List<LXEffect> effects, boolean enabled) {
    for (LXEffect effect : effects) {
      effect.enabled.setValue(enabled);
    }
  }

  private static void applyParameters(LXPattern pattern, String assignments) {
    if (assignments.isBlank()) {
      LX.log("RenderSpike params=(defaults)");
      return;
    }

    final Map<String, LXParameter> available = new TreeMap<>();
    for (LXParameter parameter : pattern.getParameters()) {
      available.put(parameter.getPath(), parameter);
    }

    final List<String> resolved = new ArrayList<>();
    for (String assignment : assignments.split(",", -1)) {
      final int equals = assignment.indexOf('=');
      if (equals <= 0 || equals == assignment.length() - 1) {
        throw new IllegalArgumentException(
          "Invalid pattern parameter assignment '" + assignment + "'; expected name=value"
        );
      }
      final String name = assignment.substring(0, equals).strip();
      final String requestedValue = assignment.substring(equals + 1).strip();
      final LXParameter parameter = available.get(name);
      if (parameter == null) {
        throw new IllegalArgumentException(
          "Unknown pattern parameter '" + name + "'. Available parameter names: " +
          String.join(", ", available.keySet())
        );
      }
      setParameterValue(name, parameter, requestedValue);
      resolved.add(name + "=" + formatParameterValue(parameter));
    }
    LX.log("RenderSpike params=" + String.join(",", resolved));
  }

  private static void setParameterValue(String name, LXParameter parameter, String value) {
    try {
      if (parameter instanceof BooleanParameter booleanParameter) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
          throw new IllegalArgumentException("expected true or false");
        }
        booleanParameter.setValue(Boolean.parseBoolean(value));
      } else if (parameter instanceof EnumParameter<?> enumParameter) {
        final Enum<?> enumValue = findEnumValue(enumParameter, value);
        if (enumValue == null) {
          throw new IllegalArgumentException(
            "expected one of " + String.join(", ", enumParameter.getOptions())
          );
        }
        enumParameter.setEnum(enumValue.name());
      } else if (parameter instanceof DiscreteParameter discreteParameter) {
        setDiscreteValue(discreteParameter, value);
      } else {
        final double numericValue = Double.parseDouble(value);
        if (!Double.isFinite(numericValue)) {
          throw new IllegalArgumentException("expected a finite number");
        }
        parameter.setValue(numericValue);
      }
    } catch (NumberFormatException nfx) {
      throw new IllegalArgumentException(
        "Invalid value '" + value + "' for pattern parameter '" + name + "': expected a number",
        nfx
      );
    } catch (IllegalArgumentException iax) {
      throw new IllegalArgumentException(
        "Invalid value '" + value + "' for pattern parameter '" + name + "': " + iax.getMessage(),
        iax
      );
    }
  }

  private static Enum<?> findEnumValue(EnumParameter<?> parameter, String value) {
    for (Object candidate : parameter.enumClass.getEnumConstants()) {
      final Enum<?> enumValue = (Enum<?>) candidate;
      if (enumValue.name().equalsIgnoreCase(value) || enumValue.toString().equalsIgnoreCase(value)) {
        return enumValue;
      }
    }
    return null;
  }

  private static void setDiscreteValue(DiscreteParameter parameter, String value) {
    final String[] options = parameter.getOptions();
    if (options != null) {
      for (int index = 0; index < options.length; ++index) {
        if (options[index].equalsIgnoreCase(value)) {
          parameter.setIndex(index);
          return;
        }
      }
    }
    parameter.setValue(Integer.parseInt(value));
  }

  private static String formatParameterValue(LXParameter parameter) {
    if (parameter instanceof BooleanParameter booleanParameter) {
      return Boolean.toString(booleanParameter.isOn());
    }
    if (parameter instanceof EnumParameter<?> enumParameter) {
      return enumParameter.getEnum().toString();
    }
    if (parameter instanceof DiscreteParameter discreteParameter) {
      final String option = discreteParameter.getOption();
      return (option == null) ? Integer.toString(discreteParameter.getValuei()) : option;
    }
    return Double.toString(parameter.getValue());
  }

  private static LXPattern instantiatePattern(Class<? extends LXPattern> patternClass, LX lx) {
    try {
      return patternClass.getConstructor(LX.class).newInstance(lx);
    } catch (NoSuchMethodException nsx) {
      throw new IllegalArgumentException(
        "Pattern class must have a public constructor accepting LX: " + patternClass.getName(),
        nsx
      );
    } catch (InvocationTargetException itx) {
      throw new IllegalStateException("Pattern constructor failed: " + patternClass.getName(), itx.getCause());
    } catch (ReflectiveOperationException rox) {
      throw new IllegalStateException("Could not instantiate pattern: " + patternClass.getName(), rox);
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

  private static EnumSet<RenderSurface> availableSurfaces() {
    final EnumSet<RenderSurface> surfaces = EnumSet.noneOf(RenderSurface.class);
    for (RenderSurface surface : RenderSurface.values()) {
      if (surface.interior && !Apotheneum.hasInterior) {
        continue;
      }
      if (surface.orientation() == null) {
        throw new IllegalStateException("Missing orientation for " + surface.fileStem);
      }
      surfaces.add(surface);
    }
    return surfaces;
  }

  private static void updateSurfaceCoverage(
    EnumSet<RenderSurface> availableSurfaces,
    EnumSet<RenderSurface> drivenSurfaces,
    double[] peakNonBlackFractions,
    int[] colors
  ) {
    for (RenderSurface surface : availableSurfaces) {
      final double nonBlackFraction = surfaceNonBlackFraction(surface.orientation(), colors);
      peakNonBlackFractions[surface.ordinal()] =
        Math.max(peakNonBlackFractions[surface.ordinal()], nonBlackFraction);
      if (nonBlackFraction > 0) {
        drivenSurfaces.add(surface);
      }
    }
  }

  private static double surfaceNonBlackFraction(Apotheneum.Orientation orientation, int[] colors) {
    final Apotheneum.Column[] columns = orientation.columns();
    int usable = 0;
    int nonBlack = 0;
    for (int x = 0; x < columns.length; ++x) {
      final int available = orientation.available(x);
      usable += available;
      for (int y = 0; y < available; ++y) {
        if ((colors[columns[x].points[y].index] & LXColor.RGB_MASK) != 0) {
          ++nonBlack;
        }
      }
    }
    return nonBlack / (double) usable;
  }

  private static void logSkippedSurfaces(
    LXPattern pattern,
    EnumSet<RenderSurface> availableSurfaces,
    EnumSet<RenderSurface> drivenSurfaces,
    double[] peakNonBlackFractions,
    String variant
  ) {
    final ActiveRegion activeRegion = activeRegion(pattern);
    for (RenderSurface surface : availableSurfaces) {
      if (drivenSurfaces.contains(surface)) {
        continue;
      }
      final StringBuilder diagnostic = new StringBuilder(String.format(
        Locale.ROOT,
        "RenderSpike variant=%s surface=%s skipped, never lit, peakNonBlackFraction=%.6f",
        variant,
        surface.fileStem,
        peakNonBlackFractions[surface.ordinal()]
      ));
      if (activeRegion != null) {
        final double nearestPointDistance = nearestPointDistance(surface.orientation(), activeRegion);
        diagnostic.append(String.format(
          Locale.ROOT,
          " nearestPointDistance=%.4f activeRegionGap=%.4f",
          nearestPointDistance,
          Math.max(0, nearestPointDistance - activeRegion.radius)
        ));
      }
      LX.log(diagnostic.toString());
    }
  }

  private static ActiveRegion activeRegion(LXPattern pattern) {
    if (!pattern.hasParameter("originX") || !pattern.hasParameter("originY") ||
        !pattern.hasParameter("originZ") || !pattern.hasParameter("radius") ||
        !pattern.hasParameter("width") || !pattern.hasParameter("shape") ||
        !(pattern.getParameter("shape") instanceof DiscreteParameter shape) ||
        !"Sphere".equalsIgnoreCase(shape.getOption())) {
      return null;
    }
    return new ActiveRegion(
      pattern.getParameter("originX").getValue(),
      pattern.getParameter("originY").getValue(),
      pattern.getParameter("originZ").getValue(),
      pattern.getParameter("radius").getValue() + pattern.getParameter("width").getValue()
    );
  }

  private static double nearestPointDistance(
    Apotheneum.Orientation orientation,
    ActiveRegion activeRegion
  ) {
    double nearest = Double.POSITIVE_INFINITY;
    final Apotheneum.Column[] columns = orientation.columns();
    for (int x = 0; x < columns.length; ++x) {
      for (int y = 0; y < orientation.available(x); ++y) {
        final LXPoint point = columns[x].points[y];
        final double dx = point.xn - activeRegion.x;
        final double dy = point.yn - activeRegion.y;
        final double dz = point.zn - activeRegion.z;
        nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
      }
    }
    return nearest;
  }

  private record ActiveRegion(double x, double y, double z, double radius) {
  }

  private static String surfaceNames(EnumSet<RenderSurface> surfaces) {
    final StringBuilder names = new StringBuilder();
    for (RenderSurface surface : surfaces) {
      if (!names.isEmpty()) {
        names.append(',');
      }
      names.append(surface.fileStem);
    }
    return names.toString();
  }

  private static String surfaceCoverageSummary(
    EnumSet<RenderSurface> surfaces,
    double[] peakNonBlackFractions
  ) {
    final StringBuilder summary = new StringBuilder();
    for (RenderSurface surface : surfaces) {
      if (!summary.isEmpty()) {
        summary.append(',');
      }
      summary.append(surface.fileStem).append('=').append(String.format(
        Locale.ROOT,
        "%.6f",
        peakNonBlackFractions[surface.ordinal()]
      ));
    }
    return summary.toString();
  }

  private static void validateCrop(ColumnCrop crop, EnumSet<RenderSurface> surfaces) {
    if (crop == null) {
      return;
    }
    for (RenderSurface surface : surfaces) {
      crop.sourceColumn(0, surface.orientation().width());
    }
  }

  private static double meanBrightness(int[] colors) {
    double totalBrightness = 0;
    for (int color : colors) {
      totalBrightness += LXColor.b(color);
    }
    return totalBrightness / colors.length;
  }

  private static void logFrameStats(int frameNumber, int[] colors, long frameNanos) {
    int nonBlack = 0;
    double totalBrightness = 0;
    for (int color : colors) {
      if ((color & LXColor.RGB_MASK) != 0) {
        ++nonBlack;
      }
      totalBrightness += LXColor.b(color);
    }
    final double nonBlackFraction = nonBlack / (double) colors.length;
    final double meanBrightness = totalBrightness / colors.length;
    final double frameMs = frameNanos / 1_000_000.;
    LX.log(String.format(
      Locale.ROOT,
      "RenderSpike frame=%03d nonBlackFraction=%.6f meanBrightnessPct=%.3f frameMs=%.3f",
      frameNumber,
      nonBlackFraction,
      meanBrightness,
      frameMs
    ));
  }

  private static void writeSurfaceArtifacts(
    RenderSurface surface,
    List<int[]> gifFrames,
    String fileSuffix,
    Path temporaryFramesRoot,
    ColumnCrop crop
  ) throws IOException, InterruptedException {
    final Path frameDirectory =
      Files.createDirectories(temporaryFramesRoot.resolve(surface.fileStem + fileSuffix));
    final List<BufferedImage> sampledFrames = new ArrayList<>(CONTACT_SAMPLE_COUNT);
    for (int frameIndex = 0; frameIndex < gifFrames.size(); ++frameIndex) {
      final BufferedImage image = renderSurface(surface, gifFrames.get(frameIndex), null);
      writePng(image, frameDirectory.resolve("frame-%03d.png".formatted(frameIndex + 1)));
      final int engineFrameNumber = (frameIndex + 1) * GIF_FRAME_INTERVAL;
      if (engineFrameNumber % CONTACT_SAMPLE_INTERVAL == 0) {
        sampledFrames.add(image);
      }
    }
    if (sampledFrames.size() != CONTACT_SAMPLE_COUNT) {
      throw new IllegalStateException(
        "Expected " + CONTACT_SAMPLE_COUNT + " contact frames, got " + sampledFrames.size()
      );
    }

    final BufferedImage firstFrame = renderSurface(surface, gifFrames.get(0), null);
    final Path gifPath = OUTPUT_DIRECTORY.resolve(surface.fileStem + fileSuffix + ".gif");
    assembleGif(frameDirectory, gifPath, 30);
    logOutput(gifPath, firstFrame.getWidth(), firstFrame.getHeight());

    final BufferedImage contactSheet = buildContactSheet(sampledFrames);
    final Path contactPath = OUTPUT_DIRECTORY.resolve(surface.fileStem + fileSuffix + "-contact.png");
    writePng(contactSheet, contactPath);
    logOutput(contactPath, contactSheet.getWidth(), contactSheet.getHeight());
    deleteTree(frameDirectory);

    if (crop != null) {
      final Path cropFrameDirectory = Files.createDirectories(
        temporaryFramesRoot.resolve(surface.fileStem + fileSuffix + "-crop")
      );
      BufferedImage cropFirstFrame = null;
      for (int frameIndex = 0; frameIndex < gifFrames.size(); ++frameIndex) {
        final BufferedImage image = renderSurface(surface, gifFrames.get(frameIndex), crop);
        if (cropFirstFrame == null) {
          cropFirstFrame = image;
        }
        writePng(image, cropFrameDirectory.resolve("frame-%03d.png".formatted(frameIndex + 1)));
      }
      final Path cropGifPath = OUTPUT_DIRECTORY.resolve(surface.fileStem + fileSuffix + "-crop.gif");
      assembleGif(cropFrameDirectory, cropGifPath, 30);
      logOutput(cropGifPath, cropFirstFrame.getWidth(), cropFirstFrame.getHeight());
      deleteTree(cropFrameDirectory);
    }
  }

  private static void writeAnimatedSurfaceArtifacts(
    RenderSurface surface,
    List<int[]> gifFrames,
    Path outputDirectory,
    Path temporaryFramesRoot,
    int gifFrameRate,
    ColumnCrop crop
  ) throws IOException, InterruptedException {
    writeAnimatedGif(
      surface,
      gifFrames,
      outputDirectory.resolve(surface.fileStem + ".gif"),
      temporaryFramesRoot.resolve(surface.fileStem),
      gifFrameRate,
      null
    );
    if (crop != null) {
      writeAnimatedGif(
        surface,
        gifFrames,
        outputDirectory.resolve(surface.fileStem + "-crop.gif"),
        temporaryFramesRoot.resolve(surface.fileStem + "-crop"),
        gifFrameRate,
        crop
      );
    }
  }

  private static void writeAnimatedGif(
    RenderSurface surface,
    List<int[]> gifFrames,
    Path outputPath,
    Path frameDirectory,
    int gifFrameRate,
    ColumnCrop crop
  ) throws IOException, InterruptedException {
    Files.createDirectories(frameDirectory);
    BufferedImage firstFrame = null;
    for (int frameIndex = 0; frameIndex < gifFrames.size(); ++frameIndex) {
      final BufferedImage image = renderSurface(surface, gifFrames.get(frameIndex), crop);
      if (firstFrame == null) {
        firstFrame = image;
      }
      writePng(image, frameDirectory.resolve("frame-%03d.png".formatted(frameIndex + 1)));
    }
    assembleGif(frameDirectory, outputPath, gifFrameRate);
    logOutput(outputPath, firstFrame.getWidth(), firstFrame.getHeight());
    deleteTree(frameDirectory);
  }

  private static BufferedImage renderSurface(RenderSurface surface, int[] colors, ColumnCrop crop) {
    final Apotheneum.Orientation orientation = surface.orientation();
    final int ringWidth = orientation.width();
    final int width = (crop == null) ? ringWidth : crop.width();
    final int height = orientation.height();
    final int scaledWidth = width * SCALE;
    final BufferedImage scaled = new BufferedImage(scaledWidth, height * SCALE, BufferedImage.TYPE_INT_RGB);
    final int[] pixels = ((DataBufferInt) scaled.getRaster().getDataBuffer()).getData();
    final Apotheneum.Column[] columns = orientation.columns();
    for (int x = 0; x < width; ++x) {
      final int sourceX = (crop == null) ? x : crop.sourceColumn(x, ringWidth);
      final int available = orientation.available(sourceX);
      for (int y = 0; y < height; ++y) {
        final int color = (y < available) ? colors[columns[sourceX].points[y].index] : LXColor.BLACK;
        // Review-only: frame statistics consume the untouched post-effect colors before this method.
        final int reviewColor = boostForReview(color);
        for (int scaleY = 0; scaleY < SCALE; ++scaleY) {
          final int pixelOffset = (y * SCALE + scaleY) * scaledWidth + x * SCALE;
          Arrays.fill(pixels, pixelOffset, pixelOffset + SCALE, reviewColor);
        }
      }
    }
    if (surface.cube) {
      markFaceBoundaries(scaled, (crop == null) ? 0 : crop.normalizedStart(ringWidth), width);
    }
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

  private static void markFaceBoundaries(BufferedImage image, int startColumn, int columnCount) {
    final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    for (int outputColumn = 0; outputColumn < columnCount; ++outputColumn) {
      final int sourceColumn = (startColumn + outputColumn) % Apotheneum.cube.exterior.width();
      if (sourceColumn % Apotheneum.GRID_WIDTH != 0 || (startColumn == 0 && outputColumn == 0)) {
        continue;
      }
      final int boundaryX = outputColumn * SCALE;
      for (int offset = 0; offset < FACE_BOUNDARY_WIDTH; ++offset) {
        final int x = boundaryX - FACE_BOUNDARY_WIDTH / 2 + offset;
        if (x < 0 || x >= image.getWidth()) {
          continue;
        }
        for (int y = 0; y < image.getHeight(); ++y) {
          pixels[y * image.getWidth() + x] = FACE_BOUNDARY_COLOR;
        }
      }
    }
  }

  private static BufferedImage buildContactSheet(List<BufferedImage> frames) {
    final BufferedImage first = frames.get(0);
    final int rows = (frames.size() + CONTACT_COLUMNS - 1) / CONTACT_COLUMNS;
    final int width = CONTACT_COLUMNS * first.getWidth() + (CONTACT_COLUMNS - 1) * CONTACT_GAP;
    final int height = rows * first.getHeight() + (rows - 1) * CONTACT_GAP;
    final BufferedImage contact = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    final int[] contactPixels = ((DataBufferInt) contact.getRaster().getDataBuffer()).getData();
    Arrays.fill(contactPixels, 0x202020);
    for (int i = 0; i < frames.size(); ++i) {
      final int tileX = (i % CONTACT_COLUMNS) * (first.getWidth() + CONTACT_GAP);
      final int tileY = (i / CONTACT_COLUMNS) * (first.getHeight() + CONTACT_GAP);
      final BufferedImage frame = frames.get(i);
      final int[] framePixels = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
      for (int y = 0; y < frame.getHeight(); ++y) {
        System.arraycopy(
          framePixels,
          y * frame.getWidth(),
          contactPixels,
          (tileY + y) * width + tileX,
          frame.getWidth()
        );
      }
    }
    return contact;
  }

  private static void assembleGif(
    Path frameDirectory,
    Path outputPath,
    int frameRate
  ) throws IOException, InterruptedException {
    final Process process = new ProcessBuilder(
      "ffmpeg",
      "-nostdin",
      "-y",
      "-loglevel",
      "error",
      "-framerate",
      Integer.toString(frameRate),
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

  private static void logOutput(Path path, int width, int height) throws IOException {
    LX.log(
      "RenderSpike output=" + path.toAbsolutePath() +
      " dimensions=" + width + "x" + height +
      " bytes=" + Files.size(path)
    );
  }

  private static void writePng(BufferedImage image, Path path) throws IOException {
    if (!ImageIO.write(image, "png", path.toFile())) {
      throw new IOException("No PNG writer available for " + path);
    }
  }

  private static void resetOutputDirectory() throws IOException {
    resetDirectory(OUTPUT_DIRECTORY);
  }

  private static void resetDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      deleteTree(directory);
    }
    Files.createDirectories(directory);
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
