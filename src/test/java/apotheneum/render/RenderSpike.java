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
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import apotheneum.Apotheneum;
import apotheneum.doved.modulators.ApotheneumColor;
import apotheneum.doved.modulators.ApotheneumGradient;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXEngine;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXPoint;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXParameterModulation.ModulationException;
import heronarts.lx.modulator.SawLFO;
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

  private enum RenderView {
    UNWRAPPED,
    LOOKUP
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
  private static final int LOOKUP_SIZE = 512;
  private static final double REVIEW_GAIN = 2.;
  private static final double REVIEW_GAMMA = .5;
  private static final double FIXED_DELTA_MS = 1000. / 60.;
  private static final double TWO_PI = 2 * Math.PI;

  private RenderSpike() {
  }

  public static void main(String[] args) throws Exception {
    final Class<? extends LXPattern> patternClass = resolvePatternClass(args);
    final String parameterAssignments = optionalArgument(args, 1, "params=");
    final List<Class<? extends LXEffect>> effectClasses = resolveEffectClasses(args);
    final String paletteAssignments = optionalArgument(args, 3, "palette=");
    final RenderView renderView = resolveRenderView(optionalArgument(args, 4, "view="));
    final String modulationAssignment = optionalArgument(args, 5, "modulate=");
    final String apotheneumColorAssignment = optionalArgument(args, 6, "apotheneumColor=");
    final String apotheneumGradientAssignment = optionalArgument(args, 7, "apotheneumGradient=");
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

      applyPalette(lx, paletteAssignments);
      applyApotheneumColor(lx, apotheneumColorAssignment);
      applyApotheneumGradient(lx, apotheneumGradientAssignment);

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
      LX.log("RenderSpike view=" + renderView.name().toLowerCase(Locale.ROOT));
      applyParameters(pattern, parameterAssignments);
      final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { pattern });
      final SawLFO modulation = applyModulation(lx, pattern, modulationAssignment);
      final List<LXEffect> effects = addEffects(channel, effectClasses, lx);
      lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

      resetOutputDirectory();
      final Path temporaryFramesRoot = mediaPath.resolve("render-frames");
      final List<WrittenArtifact> writtenArtifacts = new ArrayList<>();
      // Cylinder-interior geometry never changes between the bypass/effects variants, so the
      // fisheye projection table is resolved once per run rather than once per variant.
      final LookupProjection lookupProjection =
        (renderView == RenderView.LOOKUP) ? new LookupProjection() : null;
      if (!effects.isEmpty()) {
        LX.log("RenderSpike variant=bypass");
      }
      renderVariant(
        lx, pattern, modulation, renderView, lookupProjection, "bypass", "", temporaryFramesRoot,
        writtenArtifacts);
      if (!effects.isEmpty()) {
        setEffectsEnabled(effects, true);
        renderVariant(
          lx, pattern, modulation, renderView, lookupProjection, "effects", "-effects",
          temporaryFramesRoot, writtenArtifacts);
      }

      LX.log("RenderSpike outputDirectory=" + OUTPUT_DIRECTORY.toAbsolutePath());
      LX.log("RenderSpike outputEnabled=" + lx.engine.output.enabled.isOn());
      logArtifactHandoff(writtenArtifacts, !effects.isEmpty());
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

  private static void renderVariant(
    LX lx,
    LXPattern pattern,
    SawLFO modulation,
    RenderView renderView,
    LookupProjection lookupProjection,
    String variant,
    String fileSuffix,
    Path temporaryFramesRoot,
    List<WrittenArtifact> writtenArtifacts
  ) throws IOException, InterruptedException {
    if (!fileSuffix.isEmpty()) {
      LX.log("RenderSpike variant=" + variant);
    }
    if (modulation != null) {
      // The bypass/effects pair is only readable when the effect is the sole difference between
      // the two renders, so every variant replays the sawtooth from the same basis rather than
      // resuming wherever the previous variant's frames left it.
      modulation.setBasis(0);
      LX.log("RenderSpike modulationBasis=0 variant=" + variant);
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
    if (renderView == RenderView.LOOKUP) {
      if (drivenSurfaces.contains(RenderSurface.CYLINDER_INTERIOR)) {
        writeLookupArtifacts(
          lookupProjection, gifFrames, variant, fileSuffix, temporaryFramesRoot, writtenArtifacts);
      } else {
        LX.log(
          "RenderSpike view=lookup produced no artifacts for variant=" + variant +
          ": pattern never lit cylinder-interior");
      }
    } else {
      for (RenderSurface surface : drivenSurfaces) {
        writeSurfaceArtifacts(
          surface, gifFrames, variant, fileSuffix, temporaryFramesRoot, writtenArtifacts);
      }
    }

    final double meanFrameMs = totalFrameNanos / 1_000_000. / ENGINE_FRAME_COUNT;
    LX.log(String.format(Locale.ROOT, "RenderSpike meanFrameMs=%.3f", meanFrameMs));
    LX.log("RenderSpike engineFrames=" + ENGINE_FRAME_COUNT + " gifFrames=" + gifFrames.size());
  }

  private static Class<? extends LXPattern> resolvePatternClass(String[] args) {
    if (args.length > 8) {
      throw new IllegalArgumentException(
        "Usage: RenderSpike [fully-qualified LXPattern class] [name=value,name=value] " +
        "[fully-qualified LXEffect class,...] [hue,saturation,brightness;...] [unwrapped|lookup] " +
        "[parameter:cyclesPerSecond] " +
        "[cubeExteriorOffset,cubeInteriorOffset,cylinderExteriorOffset,cylinderInteriorOffset] " +
        "[azimuth,elevation]"
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

  private static RenderView resolveRenderView(String argument) {
    if (argument.isBlank() || argument.equalsIgnoreCase("unwrapped")) {
      return RenderView.UNWRAPPED;
    }
    if (argument.equalsIgnoreCase("lookup")) {
      return RenderView.LOOKUP;
    }
    throw new IllegalArgumentException(
      "Unknown render view '" + argument + "'; expected unwrapped or lookup");
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

  /**
   * Sets project palette swatch stops from a {@code h,s,b;h,s,b} spec so that
   * palette-driven patterns can be reviewed headlessly. Stops beyond the swatch's
   * current size are appended.
   */
  private static void applyPalette(LX lx, String assignments) {
    if (assignments.isBlank()) {
      LX.log("RenderSpike palette=(project default)");
      return;
    }
    final String[] stops = assignments.split(";", -1);
    final List<String> resolved = new ArrayList<>();
    for (int i = 0; i < stops.length; ++i) {
      final String[] hsb = stops[i].split(",", -1);
      if (hsb.length != 3) {
        throw new IllegalArgumentException(
          "Invalid palette stop '" + stops[i] + "'; expected hue,saturation,brightness"
        );
      }
      final double hue = parsePaletteComponent(stops[i], hsb[0], 360);
      final double saturation = parsePaletteComponent(stops[i], hsb[1], 100);
      final double brightness = parsePaletteComponent(stops[i], hsb[2], 100);
      while (lx.engine.palette.swatch.colors.size() <= i) {
        lx.engine.palette.swatch.addColor();
      }
      final LXDynamicColor color = lx.engine.palette.swatch.colors.get(i);
      color.primary.hue.setValue(hue);
      color.primary.saturation.setValue(saturation);
      color.primary.brightness.setValue(brightness);
      resolved.add(hue + "/" + saturation + "/" + brightness);
    }
    LX.log("RenderSpike palette=" + String.join(";", resolved));
  }

  /**
   * Builds a global {@code ApotheneumColor} from a
   * {@code cubeExteriorOffset,cubeInteriorOffset,cylinderExteriorOffset,cylinderInteriorOffset}
   * spec so a {@code ColorNativePattern} renders through real per-surface differentiation
   * instead of ApotheneumColor's own neutral-white fallback (see
   * {@code ColorNativePattern.ColorRole#resolveBase}) or a default project palette clamped onto
   * one shared stop for every surface. {@code pair}/{@code swap} are left at their defaults (0):
   * this flag exists to demonstrate the four surfaces differing from each other, which
   * indexOffset alone already proves, not to exercise the shared gesture -- see
   * {@code ApotheneumColorTest} for that. Blank input constructs nothing, matching every other
   * optional render flag's "unset means default behaviour" convention.
   */
  private static void applyApotheneumColor(LX lx, String assignment) {
    if (assignment.isBlank()) {
      LX.log("RenderSpike apotheneumColor=(none)");
      return;
    }
    final String[] offsets = assignment.split(",", -1);
    if (offsets.length != 4) {
      throw new IllegalArgumentException(
        "Invalid apotheneumColor spec '" + assignment + "'; expected "
        + "cubeExteriorOffset,cubeInteriorOffset,cylinderExteriorOffset,cylinderInteriorOffset"
      );
    }
    final ApotheneumColor color = new ApotheneumColor(lx);
    lx.engine.registerComponent(ApotheneumColor.PATH, color);
    color.cubeExterior.indexOffset.setValue(parseIndexOffset(assignment, offsets[0]));
    color.cubeInterior.indexOffset.setValue(parseIndexOffset(assignment, offsets[1]));
    color.cylinderExterior.indexOffset.setValue(parseIndexOffset(assignment, offsets[2]));
    color.cylinderInterior.indexOffset.setValue(parseIndexOffset(assignment, offsets[3]));
    LX.log("RenderSpike apotheneumColor=pair:" + color.pair.getValuei()
      + ",swap:" + color.swap.getValuei()
      + ",cubeExteriorOffset:" + color.cubeExterior.indexOffset.getValuei()
      + ",cubeInteriorOffset:" + color.cubeInterior.indexOffset.getValuei()
      + ",cylinderExteriorOffset:" + color.cylinderExterior.indexOffset.getValuei()
      + ",cylinderInteriorOffset:" + color.cylinderInterior.indexOffset.getValuei());
  }

  /**
   * Builds a global {@code ApotheneumGradient} from an {@code azimuth,elevation} spec (degrees)
   * so {@code GradientMultiplyEffect} -- which as of the 3D-gradient redesign owns no direction
   * of its own -- can be reviewed at a chosen direction instead of always falling back to
   * {@code ApotheneumGradient}'s own straight-up default (see that class's {@code
   * elevationOrDefault} javadoc). Blank input constructs nothing, matching every other optional
   * render flag's "unset means default behaviour" convention -- and in this case "default
   * behaviour" is itself meaningful to render, since it's the same fallback a real project would
   * hit if the core plugin failed to load.
   */
  private static void applyApotheneumGradient(LX lx, String assignment) {
    if (assignment.isBlank()) {
      LX.log("RenderSpike apotheneumGradient=(none)");
      return;
    }
    final String[] components = assignment.split(",", -1);
    if (components.length != 2) {
      throw new IllegalArgumentException(
        "Invalid apotheneumGradient spec '" + assignment + "'; expected azimuth,elevation"
      );
    }
    final ApotheneumGradient gradient = new ApotheneumGradient(lx);
    lx.engine.registerComponent(ApotheneumGradient.PATH, gradient);
    gradient.azimuth.setValue(parseGradientDegrees(assignment, components[0]));
    gradient.elevation.setValue(parseGradientDegrees(assignment, components[1]));
    LX.log("RenderSpike apotheneumGradient=azimuth:" + gradient.azimuth.getValue()
      + ",elevation:" + gradient.elevation.getValue());
  }

  private static double parseGradientDegrees(String assignment, String raw) {
    try {
      return Double.parseDouble(raw.strip());
    } catch (NumberFormatException nfe) {
      throw new IllegalArgumentException(
        "Invalid apotheneumGradient spec '" + assignment + "': '" + raw + "' is not a number", nfe
      );
    }
  }

  private static int parseIndexOffset(String assignment, String raw) {
    try {
      return Integer.parseInt(raw.strip());
    } catch (NumberFormatException nfe) {
      throw new IllegalArgumentException(
        "Invalid apotheneumColor spec '" + assignment + "': '" + raw + "' is not an integer", nfe
      );
    }
  }

  static double parsePaletteComponent(String stop, String raw, double maximum) {
    final double value;
    try {
      value = Double.parseDouble(raw.strip());
    } catch (NumberFormatException nfe) {
      throw new IllegalArgumentException("Invalid palette stop '" + stop + "': " + raw, nfe);
    }
    if (!Double.isFinite(value) || value < 0 || value > maximum) {
      throw new IllegalArgumentException(
        "Palette component " + value + " in '" + stop + "' out of range 0.." + maximum
      );
    }
    return value;
  }

  private static void applyParameters(LXPattern pattern, String assignments) {
    if (assignments.isBlank()) {
      LX.log("RenderSpike params=(defaults)");
      return;
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
      final LXParameter parameter = resolvePatternParameter(pattern, name);
      setParameterValue(name, parameter, requestedValue);
      resolved.add(name + "=" + formatParameterValue(parameter));
    }
    LX.log("RenderSpike params=" + String.join(",", resolved));
  }

  /**
   * Adds a deterministic 0..1 sawtooth to one pattern parameter. The renderer uses a real
   * modulation connection so static-by-default patterns are reviewed through the same path a
   * performer uses. Returns the registered sawtooth so each render variant can replay it from
   * the same basis, or null when no modulation was requested.
   */
  static SawLFO applyModulation(LX lx, LXPattern pattern, String assignment)
    throws ModulationException {
    if (assignment.isBlank()) {
      LX.log("RenderSpike modulation=(none)");
      return null;
    }
    final int colon = assignment.indexOf(':');
    if ((colon <= 0) || (colon == assignment.length() - 1)) {
      throw new IllegalArgumentException(
        "Invalid modulation '" + assignment + "'; expected parameter:cyclesPerSecond"
      );
    }
    final String name = assignment.substring(0, colon).strip();
    final String rawRate = assignment.substring(colon + 1).strip();
    final double cyclesPerSecond;
    try {
      cyclesPerSecond = Double.parseDouble(rawRate);
    } catch (NumberFormatException nfx) {
      throw new IllegalArgumentException(
        "Invalid modulation rate '" + rawRate + "' for pattern parameter '" + name +
        "': expected a finite positive number",
        nfx
      );
    }
    if (!Double.isFinite(cyclesPerSecond) || cyclesPerSecond <= 0) {
      throw new IllegalArgumentException(
        "Invalid modulation rate '" + rawRate + "' for pattern parameter '" + name +
        "': expected a finite positive number"
      );
    }

    final LXCompoundModulation.Target target = resolveModulationTarget(pattern, name);
    final double periodMs = 1000. / cyclesPerSecond;
    // The full-range saw is added on top of the base value, so the base has to sit at the
    // bottom of the target's normalized range for the documented full sweep. Setting the
    // numeric value to zero would instead start a bipolar target such as Gravity's -1..1
    // direction halfway up, leaving the upper half of the saw clipped at the maximum.
    target.setNormalized(0);
    final SawLFO saw = lx.engine.modulation.addModulator(
      new SawLFO("RenderSpike " + name, 0, 1, periodMs));
    final LXCompoundModulation modulation =
      new LXCompoundModulation(lx.engine.modulation, saw, target);
    lx.engine.modulation.addModulation(modulation);
    modulation.range.setValue(1);
    saw.start();
    LX.log(String.format(
      Locale.ROOT,
      "RenderSpike modulation=target=%s,shape=saw,sweepStart=%.6f,sweepEnd=%.6f," +
      "rateHz=%.6f,periodMs=%.3f,range=1",
      name,
      target.getValueFromNormalized(0),
      target.getValueFromNormalized(1),
      cyclesPerSecond,
      periodMs
    ));
    return saw;
  }

  static LXCompoundModulation.Target resolveModulationTarget(LXPattern pattern, String name) {
    final LXParameter parameter = resolvePatternParameter(pattern, name);
    if (!(parameter instanceof LXCompoundModulation.Target target)) {
      throw new IllegalArgumentException(
        "Pattern parameter '" + name + "' is not modulatable; expected a CompoundParameter " +
        "or CompoundDiscreteParameter"
      );
    }
    return target;
  }

  static LXParameter resolvePatternParameter(LXPattern pattern, String name) {
    final Map<String, LXParameter> available = new TreeMap<>();
    for (LXParameter parameter : pattern.getParameters()) {
      available.put(parameter.getPath(), parameter);
    }
    for (Map.Entry<String, LXComponent> child : pattern.children.entrySet()) {
      for (LXParameter parameter : child.getValue().getParameters()) {
        available.put(child.getKey() + "/" + parameter.getPath(), parameter);
      }
    }
    final LXParameter parameter = available.get(name);
    if (parameter == null) {
      throw new IllegalArgumentException(
        "Unknown pattern parameter '" + name + "'. Available parameter names: " +
        String.join(", ", available.keySet())
      );
    }
    return parameter;
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

  private record WrittenArtifact(Path path, String variant) {
  }

  @FunctionalInterface
  private interface FrameRenderer {
    BufferedImage render(int[] colors);
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
    String variant,
    String fileSuffix,
    Path temporaryFramesRoot,
    List<WrittenArtifact> writtenArtifacts
  ) throws IOException, InterruptedException {
    writeArtifacts(
      surface.fileStem,
      gifFrames,
      variant,
      fileSuffix,
      temporaryFramesRoot,
      writtenArtifacts,
      colors -> renderSurface(surface, colors)
    );
  }

  private static void writeLookupArtifacts(
    LookupProjection projection,
    List<int[]> gifFrames,
    String variant,
    String fileSuffix,
    Path temporaryFramesRoot,
    List<WrittenArtifact> writtenArtifacts
  ) throws IOException, InterruptedException {
    writeArtifacts(
      "cylinder-interior-lookup",
      gifFrames,
      variant,
      fileSuffix,
      temporaryFramesRoot,
      writtenArtifacts,
      projection::render
    );
  }

  private static void writeArtifacts(
    String fileStem,
    List<int[]> gifFrames,
    String variant,
    String fileSuffix,
    Path temporaryFramesRoot,
    List<WrittenArtifact> writtenArtifacts,
    FrameRenderer renderer
  ) throws IOException, InterruptedException {
    final Path frameDirectory =
      Files.createDirectories(temporaryFramesRoot.resolve(fileStem + fileSuffix));
    final List<BufferedImage> sampledFrames = new ArrayList<>(CONTACT_SAMPLE_COUNT);
    for (int frameIndex = 0; frameIndex < gifFrames.size(); ++frameIndex) {
      final BufferedImage image = renderer.render(gifFrames.get(frameIndex));
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

    final BufferedImage firstFrame = renderer.render(gifFrames.get(0));
    final Path gifPath = OUTPUT_DIRECTORY.resolve(fileStem + fileSuffix + ".gif");
    assembleGif(frameDirectory, gifPath);
    logOutput(gifPath, firstFrame.getWidth(), firstFrame.getHeight());
    writtenArtifacts.add(new WrittenArtifact(gifPath.toAbsolutePath(), variant));

    final BufferedImage contactSheet = buildContactSheet(sampledFrames);
    final Path contactPath = OUTPUT_DIRECTORY.resolve(fileStem + fileSuffix + "-contact.png");
    writePng(contactSheet, contactPath);
    logOutput(contactPath, contactSheet.getWidth(), contactSheet.getHeight());
    writtenArtifacts.add(new WrittenArtifact(contactPath.toAbsolutePath(), variant));
    deleteTree(frameDirectory);
  }

  private static void logArtifactHandoff(List<WrittenArtifact> writtenArtifacts, boolean hasEffects) {
    LX.log("Renders ready to attach:");
    if (!hasEffects) {
      for (WrittenArtifact artifact : writtenArtifacts) {
        LX.log("  " + artifact.path);
      }
      return;
    }
    logArtifactVariant(writtenArtifacts, "bypass", "Effects bypassed:");
    logArtifactVariant(writtenArtifacts, "effects", "Effects applied:");
  }

  private static void logArtifactVariant(
    List<WrittenArtifact> writtenArtifacts,
    String variant,
    String label
  ) {
    LX.log("  " + label);
    for (WrittenArtifact artifact : writtenArtifacts) {
      if (artifact.variant.equals(variant)) {
        LX.log("    " + artifact.path);
      }
    }
  }

  /**
   * Equidistant 180-degree fisheye projection from the center of the cylinder floor.
   * Image radius is proportional to the point's polar angle away from straight up.
   */
  private static final class LookupProjection {

    private final int[] pointIndices = new int[LOOKUP_SIZE * LOOKUP_SIZE];

    private LookupProjection() {
      Arrays.fill(this.pointIndices, -1);
      final Apotheneum.Orientation orientation = Apotheneum.cylinder.interior;
      final Apotheneum.Column[] columns = orientation.columns();
      final int width = orientation.width();
      final int height = orientation.height();
      final double center = (LOOKUP_SIZE - 1) * .5;
      final double outerRadius = center;

      // The observer stands on the physical floor (y=0, per every project fixture's
      // elevationAbsolute), not at the bottom LED row -- the lowest cylinder LEDs sit
      // above the floor, so averaging their y would place the camera at the LEDs and
      // force that row to exactly 90 degrees polar angle regardless of true geometry.
      double centerX = 0;
      double centerZ = 0;
      final double observerY = 0;
      for (Apotheneum.Column column : columns) {
        final LXPoint bottom = column.points[height - 1];
        centerX += bottom.x;
        centerZ += bottom.z;
      }
      centerX /= width;
      centerZ /= width;

      final double[] columnTheta = new double[width];
      for (int x = 0; x < width; ++x) {
        final LXPoint point = columns[x].points[height / 2];
        columnTheta[x] = wrapAngle(Math.atan2(point.z - centerZ, point.x - centerX));
      }

      final double[] rowRadius = new double[height];
      for (int y = 0; y < height; ++y) {
        double radial = 0;
        double vertical = 0;
        for (Apotheneum.Column column : columns) {
          final LXPoint point = column.points[y];
          radial += Math.hypot(point.x - centerX, point.z - centerZ);
          vertical += Math.abs(point.y - observerY);
        }
        radial /= width;
        vertical /= width;
        final double polarAngle = Math.atan2(radial, vertical);
        rowRadius[y] = outerRadius * polarAngle / (Math.PI * .5);
        if (y > 0 && rowRadius[y] < rowRadius[y - 1]) {
          throw new IllegalStateException("Cylinder rows are not monotonic in lookup projection");
        }
      }

      // Bound sampling half a row beyond the first and last real rows. The fixture's bottom
      // LED row sits near an 80-degree polar angle, well inside the 90-degree image rim, so
      // admitting pixels all the way to outerRadius would let nearestRow clamp that whole
      // outer annulus onto the last row -- drawing the bottom LEDs several times thicker
      // than the geometry warrants. Below the physical LED field the image stays black.
      final int lastRow = rowRadius.length - 1;
      final double innerRadius = Math.max(
        0, rowRadius[0] - .5 * (rowRadius[1] - rowRadius[0]));
      final double sampledOuterRadius = Math.min(
        outerRadius,
        rowRadius[lastRow] + .5 * (rowRadius[lastRow] - rowRadius[lastRow - 1]));
      for (int pixelY = 0; pixelY < LOOKUP_SIZE; ++pixelY) {
        final double imageY = pixelY - center;
        for (int pixelX = 0; pixelX < LOOKUP_SIZE; ++pixelX) {
          final double imageX = pixelX - center;
          final double radius = Math.hypot(imageX, imageY);
          if (radius < innerRadius || radius > sampledOuterRadius) {
            continue;
          }
          final int row = nearestRow(rowRadius, radius);
          final double theta = wrapAngle(Math.atan2(imageY, imageX));
          final int column = nearestColumn(columnTheta, theta);
          if (row < orientation.available(column)) {
            this.pointIndices[pixelY * LOOKUP_SIZE + pixelX] =
              columns[column].points[row].index;
          }
        }
      }

      LX.log(String.format(
        Locale.ROOT,
        "RenderSpike lookupProjection=equidistant-fisheye fovDegrees=180 " +
        "topRadiusPx=%.2f bottomRadiusPx=%.2f sampledInnerPx=%.2f sampledOuterPx=%.2f " +
        "imageRimPx=%.2f",
        rowRadius[0],
        rowRadius[height - 1],
        innerRadius,
        sampledOuterRadius,
        outerRadius
      ));
    }

    private BufferedImage render(int[] colors) {
      final BufferedImage image =
        new BufferedImage(LOOKUP_SIZE, LOOKUP_SIZE, BufferedImage.TYPE_INT_RGB);
      final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
      for (int i = 0; i < pixels.length; ++i) {
        final int pointIndex = this.pointIndices[i];
        pixels[i] = (pointIndex < 0) ? LXColor.BLACK : boostForReview(colors[pointIndex]);
      }
      return image;
    }
  }

  private static int nearestRow(double[] rowRadius, double radius) {
    // rowRadius is monotonic (enforced in LookupProjection's constructor), so the nearest
    // value is adjacent to a binary search's insertion point rather than requiring a scan.
    int insertionPoint = Arrays.binarySearch(rowRadius, radius);
    if (insertionPoint >= 0) {
      return insertionPoint;
    }
    insertionPoint = -insertionPoint - 1;
    if (insertionPoint <= 0) {
      return 0;
    }
    if (insertionPoint >= rowRadius.length) {
      return rowRadius.length - 1;
    }
    final double below = radius - rowRadius[insertionPoint - 1];
    final double above = rowRadius[insertionPoint] - radius;
    return (below <= above) ? insertionPoint - 1 : insertionPoint;
  }

  private static int nearestColumn(double[] columnTheta, double theta) {
    int nearest = 0;
    double nearestDistance = circularDistance(theta, columnTheta[0]);
    for (int column = 1; column < columnTheta.length; ++column) {
      final double distance = circularDistance(theta, columnTheta[column]);
      if (distance < nearestDistance) {
        nearest = column;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  private static double circularDistance(double a, double b) {
    final double distance = Math.abs(a - b);
    return Math.min(distance, TWO_PI - distance);
  }

  private static double wrapAngle(double angle) {
    return (angle < 0) ? angle + TWO_PI : angle;
  }

  private static BufferedImage renderSurface(RenderSurface surface, int[] colors) {
    final Apotheneum.Orientation orientation = surface.orientation();
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
        // Review-only: frame statistics consume the untouched post-effect colors before this method.
        final int reviewColor = boostForReview(color);
        for (int scaleY = 0; scaleY < SCALE; ++scaleY) {
          final int pixelOffset = (y * SCALE + scaleY) * scaledWidth + x * SCALE;
          Arrays.fill(pixels, pixelOffset, pixelOffset + SCALE, reviewColor);
        }
      }
    }
    if (surface.cube) {
      markFaceBoundaries(scaled);
    }
    return scaled;
  }

  /**
   * Lifts brightness only, in HSB, so a colored pattern keeps its hue and saturation
   * under review. The previous version boosted R/G/B independently, which is not a
   * brightness lift despite the name: any channel past ~25% of full saturates on its
   * own, so two channels of a moderately bright color commonly clip to 255 while the
   * third lags behind, reading as a near-white tint instead of the pattern's actual
   * color. Monochrome patterns are colorless in HSB (hue/saturation don't apply), so
   * this is a strict fix, not a trade-off against the grayscale patterns the lift was
   * originally written for.
   */
  private static int boostForReview(int color) {
    final float hue = LXColor.h(color);
    final float saturation = LXColor.s(color);
    final float brightness = LXColor.b(color);
    final double boosted = 100. * Math.min(1., REVIEW_GAIN * Math.pow(brightness / 100., REVIEW_GAMMA));
    return LXColor.hsb(hue, saturation, boosted);
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

  private static void assembleGif(Path frameDirectory, Path outputPath) throws IOException, InterruptedException {
    final Process process = new ProcessBuilder(
      "ffmpeg",
      "-nostdin",
      "-y",
      "-loglevel",
      "error",
      "-framerate",
      "30",
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
    if (Files.exists(OUTPUT_DIRECTORY)) {
      deleteTree(OUTPUT_DIRECTORY);
    }
    Files.createDirectories(OUTPUT_DIRECTORY);
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
