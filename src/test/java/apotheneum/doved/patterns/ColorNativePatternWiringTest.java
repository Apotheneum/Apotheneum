package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import apotheneum.doved.modulators.ApotheneumColor;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.structure.JsonFixture;

/**
 * Exercises the wiring between {@code ColorNativePattern} and {@link ApotheneumColor} end to
 * end: a real pattern, added to a real channel, rendered through the real fixture, with an
 * {@code ApotheneumColor} actually driving what comes out of {@code getColors()}.
 *
 * <p>This is deliberately not another unit test of {@code ColorRole.color(surface, physics)}
 * called directly with a hand-picked {@code Surface} literal -- {@link ColorNativePatternTest}
 * already covers that, and it kept passing while the live rig visibly was not colorizing Grass
 * or Rockfall. What was never exercised is the path a real render actually takes: {@code
 * pattern.loop(...)} -&gt; {@code render(deltaMs)} -&gt; {@code ApotheneumColor.Surface.of(the
 * orientation the pattern is actually iterating)} -&gt; {@code ApotheneumColor.instance}
 * resolved fresh -&gt; written into the pattern's own {@code colors[]}. A green suite that only
 * ever unit-tests {@code ColorRole} in isolation cannot catch a break anywhere in that chain --
 * including, per 2026-08-29's incident, a live Chromatik instance that hot-reloaded the new
 * {@code ApotheneumColor} class but kept running Grass/Rockfall instances constructed from the
 * project file before this redesign landed, still holding the old class's fields. No unit test
 * run against a freshly-built JVM can reproduce that specific failure (a fresh test always
 * constructs current-class instances), but this test is what should have existed all along at
 * the level the bug actually lives: does turning {@code ApotheneumColor}'s shared knob move a
 * real pattern's real output, not just a directly-invoked helper method's return value.
 */
public class ColorNativePatternWiringTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";

  @Test
  void changingPairChangesGrasssRenderedCylinderExteriorColors() throws Exception {
    final int[] pairZero = renderCylinderExteriorColors(Grass::new, 0);
    final int[] pairOne = renderCylinderExteriorColors(Grass::new, 1);
    assertColorsDiffer(pairZero, pairOne,
      "Grass's rendered colors did not change when ApotheneumColor.pair did -- the pattern is "
      + "not actually reading the shared ApotheneumColor on its real render path");
  }

  @Test
  void changingPairChangesRockfallsRenderedCubeExteriorColors() throws Exception {
    final int[] pairZero = renderCubeExteriorColors(Rockfall::new, 0);
    final int[] pairOne = renderCubeExteriorColors(Rockfall::new, 1);
    assertColorsDiffer(pairZero, pairOne,
      "Rockfall's rendered colors did not change when ApotheneumColor.pair did -- the pattern "
      + "is not actually reading the shared ApotheneumColor on its real render path");
  }

  private static void assertColorsDiffer(int[] before, int[] after, String message) {
    assertTrue(before.length > 0 && before.length == after.length, "sanity: comparable buffers");
    boolean differed = false;
    for (int i = 0; i < before.length; ++i) {
      if (before[i] != after[i]) {
        differed = true;
        break;
      }
    }
    assertTrue(differed, message);
  }

  private interface PatternFactory<T extends heronarts.lx.pattern.LXPattern> {
    T create(LX lx);
  }

  /**
   * Loads the real fixture, adds a real {@code ApotheneumColor} set to {@code pairValue},
   * constructs the pattern via {@code factory}, runs it on a real channel for 60 frames, and
   * returns the final colors of every cylinder-exterior point in column order -- mirroring
   * {@code GrassTest.renderGrassCylinderExteriorColors}, generalized to take any
   * {@code ColorNativePattern} factory and the knob value under test.
   */
  private static int[] renderCylinderExteriorColors(
    PatternFactory<? extends ColorNativePattern> factory, int pairValue
  ) throws Exception {
    return render(factory, pairValue, true);
  }

  private static int[] renderCubeExteriorColors(
    PatternFactory<? extends ColorNativePattern> factory, int pairValue
  ) throws Exception {
    return render(factory, pairValue, false);
  }

  private static int[] render(
    PatternFactory<? extends ColorNativePattern> factory,
    int pairValue,
    boolean cylinder
  ) throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-color-wiring-test-");
    LX lx = null;
    ApotheneumColor apotheneumColor = null;
    try {
      copyFixtureMedia(mediaPath);
      final LX.Flags flags = new LX.Flags();
      flags.loadPreferences = false;
      flags.mediaPath = mediaPath.toString();
      flags.outputMode = LX.Flags.OutputMode.INACTIVE;
      lx = new LX(flags);
      lx.engine.output.enabled.setValue(false);

      final JsonFixture fixture = new JsonFixture(lx, FIXTURE_NAME);
      lx.structure.addFixture(fixture);
      lx.structure.beforeEngineRun();
      assertFalse(fixture.error.isOn(), fixture.errorMessage.getString());
      Apotheneum.initialize(lx);

      lx.engine.palette.swatch.addColor();
      lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(20, 90, 70));
      lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(200, 90, 70));

      apotheneumColor = registerApotheneumColor(lx);
      apotheneumColor.pair.setValue(pairValue);
      apotheneumColor.swap.setValue(0);

      final ColorNativePattern pattern = factory.create(lx);
      lx.engine.mixer.addChannel(new heronarts.lx.pattern.LXPattern[] { pattern });

      lx.engine.run();
      for (int frame = 0; frame < 60; ++frame) {
        pattern.loop(1000. / 60.);
      }

      final int[] colors = pattern.getColors();
      final Apotheneum.Column[] columns = cylinder
        ? Apotheneum.cylinder.exterior.columns()
        : Apotheneum.cube.exterior.columns();
      int count = 0;
      for (Apotheneum.Column column : columns) {
        count += column.points.length;
      }
      final int[] result = new int[count];
      int i = 0;
      for (Apotheneum.Column column : columns) {
        for (LXPoint point : column.points) {
          result[i++] = colors[point.index];
        }
      }
      return result;
    } finally {
      if (lx != null) {
        lx.dispose();
      }
      deleteTree(mediaPath);
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

  private static void deleteTree(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  private static ApotheneumColor registerApotheneumColor(LX lx) {
    final ApotheneumColor color = new ApotheneumColor(lx);
    lx.engine.registerComponent(ApotheneumColor.PATH, color);
    return color;
  }
}
