package apotheneum.doved.effects;

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
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.color.SolidPattern;
import heronarts.lx.structure.JsonFixture;

/**
 * Exercises the wiring {@code ColorNativePatternWiringTest} established for the pattern side:
 * a real fixture, a real channel, a real effect added to it, {@code ApotheneumColor.pair}
 * changed, and an assertion that the rendered {@code colors()} actually move -- not a direct
 * call to a private helper method with a hand-picked surface. {@code GradientMultiplyEffect} is
 * new, so there is no incident this reproduces yet, but the whole point of writing this at the
 * wiring level from the start is not needing one.
 */
public class GradientMultiplyEffectWiringTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";

  @Test
  void changingPairChangesTheMultipliedCubeExteriorColors() throws Exception {
    final int[] pairZero = renderCubeExteriorColors(0);
    final int[] pairOne = renderCubeExteriorColors(1);

    boolean differed = false;
    for (int i = 0; i < pairZero.length; ++i) {
      if (pairZero[i] != pairOne[i]) {
        differed = true;
        break;
      }
    }
    assertTrue(
      differed,
      "GradientMultiplyEffect's rendered colors did not change when ApotheneumColor.pair did "
      + "-- the effect is not actually reading the shared ApotheneumColor on its real render path"
    );
  }

  @Test
  void directionChangesWhichPixelsReadPrimaryVersusSecondary() throws Exception {
    final int[] direction0 = renderCubeExteriorColorsAtDirection(0);
    final int[] direction90 = renderCubeExteriorColorsAtDirection(90);

    boolean differed = false;
    for (int i = 0; i < direction0.length; ++i) {
      if (direction0[i] != direction90[i]) {
        differed = true;
        break;
      }
    }
    assertTrue(
      differed,
      "Rotating cubeExteriorDirection 90 degrees did not change any rendered pixel -- the "
      + "per-surface direction control is not reaching the gradient projection"
    );
  }

  /**
   * Loads the real fixture, adds a real {@code ApotheneumColor} with {@code pair} set to
   * {@code pairValue}, hosts {@code GradientMultiplyEffect} on a plain white
   * {@code SolidPattern} channel (per docs/headless-rendering.md's own guidance: a uniform
   * field is the host that isolates the effect's own transformation), runs 60 frames, and
   * returns the final colors of every cube-exterior point in column order.
   */
  private static int[] renderCubeExteriorColors(int pairValue) throws Exception {
    return render(pairValue, 45);
  }

  private static int[] renderCubeExteriorColorsAtDirection(double directionDegrees) throws Exception {
    return render(0, directionDegrees);
  }

  private static int[] render(int pairValue, double directionDegrees) throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-gradient-multiply-test-");
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

      final SolidPattern host = new SolidPattern(lx, LXColor.WHITE);
      final GradientMultiplyEffect effect = new GradientMultiplyEffect(lx);
      effect.cubeExteriorDirection.setValue(directionDegrees);
      final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { host });
      channel.addEffect(effect);

      lx.engine.run();
      for (int frame = 0; frame < 60; ++frame) {
        lx.engine.run();
      }

      final int[] colors = host.getColors();
      final Apotheneum.Column[] columns = Apotheneum.cube.exterior.columns();
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
