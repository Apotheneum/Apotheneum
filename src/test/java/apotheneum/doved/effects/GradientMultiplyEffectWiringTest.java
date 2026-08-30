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
import apotheneum.doved.modulators.ApotheneumGradient;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.color.SolidPattern;
import heronarts.lx.structure.JsonFixture;

/**
 * Exercises the wiring {@code ColorNativePatternWiringTest} established for the pattern side:
 * a real fixture, a real channel, a real effect added to it, the shared {@code ApotheneumColor}/
 * {@code ApotheneumGradient} singletons changed, and an assertion that the rendered
 * {@code colors()} actually move -- not a direct call to a private helper method with a
 * hand-picked surface.
 *
 * <p>{@code GradientMultiplyEffect} used to own a per-surface {@code direction} parameter; since
 * the 3D-gradient redesign it owns nothing, and direction comes entirely from a real, separately
 * registered {@link ApotheneumGradient} (or its documented null-instance default) -- so this
 * class registers one the same way {@code registerApotheneumColor} already registers an {@code
 * ApotheneumColor}, rather than setting a field on the effect itself.
 */
public class GradientMultiplyEffectWiringTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";

  @Test
  void changingPairChangesTheMultipliedCubeExteriorColors() throws Exception {
    final int[] pairZero = renderCubeExteriorColors(0, 0, 0);
    final int[] pairOne = renderCubeExteriorColors(1, 0, 0);

    assertDiffers(
      pairZero, pairOne,
      "GradientMultiplyEffect's rendered colors did not change when ApotheneumColor.pair did "
      + "-- the effect is not actually reading the shared ApotheneumColor on its real render path"
    );
  }

  @Test
  void azimuthChangesWhichPixelsReadPrimaryVersusSecondary() throws Exception {
    final int[] azimuth0 = renderCubeExteriorColors(0, 0, 0);
    final int[] azimuth90 = renderCubeExteriorColors(0, 90, 0);

    assertDiffers(
      azimuth0, azimuth90,
      "Rotating ApotheneumGradient.azimuth 90 degrees did not change any rendered cube-exterior "
      + "pixel -- the shared direction is not reaching the gradient projection"
    );
  }

  @Test
  void elevationChangesWhichPixelsReadPrimaryVersusSecondary() throws Exception {
    final int[] elevation0 = renderCubeExteriorColors(0, 0, 0);
    final int[] elevation90 = renderCubeExteriorColors(0, 0, 90);

    assertDiffers(
      elevation0, elevation90,
      "Tilting ApotheneumGradient.elevation to vertical did not change any rendered "
      + "cube-exterior pixel -- the shared direction is not reaching the gradient projection"
    );
  }

  /**
   * The whole reason this redesign exists: a horizontal direction used to hit a hard seam where
   * the cube exterior's four concatenated walls (or the cylinder's true circle) wrapped back to
   * their own start, because the old per-surface gradient ran across each surface's unwrapped
   * 2D raster rather than through real 3D space. Column 0 and the last column of a ring are
   * physically adjacent in the real room; projecting their real-world positions onto a single 3D
   * direction must therefore put them close together, not at opposite ends of the ramp.
   */
  @Test
  void wrappingAroundTheCubeExteriorProducesNoSeamAtAHorizontalDirection() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-gradient-seam-test-");
    LX lx = null;
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

      // A due-north horizontal direction: exactly the case that hit the old raster's seam
      // (the cylinder is a true circle, the cube exterior's 200 columns are four walls
      // concatenated into a loop -- see ApotheneumGradient's class javadoc).
      final double dirX = ApotheneumGradient.directionX(0, 0);
      final double dirY = ApotheneumGradient.directionY(0);
      final double dirZ = ApotheneumGradient.directionZ(0, 0);
      final double min = ApotheneumGradient.projectedMin(lx.getModel(), dirX, dirY, dirZ);
      final double max = ApotheneumGradient.projectedMax(lx.getModel(), dirX, dirY, dirZ);

      final Apotheneum.Column[] columns = Apotheneum.cube.exterior.columns();
      final int lastRow = columns[0].points.length / 2;
      final LXPoint firstColumnPoint = columns[0].points[lastRow];
      final LXPoint lastColumnPoint = columns[columns.length - 1].points[lastRow];

      final double firstT = ApotheneumGradient.normalize(
        ApotheneumGradient.project(firstColumnPoint, dirX, dirY, dirZ), min, max);
      final double lastT = ApotheneumGradient.normalize(
        ApotheneumGradient.project(lastColumnPoint, dirX, dirY, dirZ), min, max);

      // Adjacent physical columns should differ by roughly one column's worth of the full
      // [0, 1] span, not by anywhere near the full span itself -- the old raster's seam put
      // column 0 and the last column at u = -0.5 and +0.5, a difference of 1.0 (the maximum
      // possible), regardless of how many columns separated them in the real room.
      final double perColumnSpan = 1.0 / columns.length;
      final double seamGap = Math.abs(firstT - lastT);
      assertTrue(
        seamGap < 10 * perColumnSpan,
        "Column 0 and the last column of the cube exterior ring -- physically adjacent -- "
        + "projected " + seamGap + " apart on a [0,1] gradient span, far more than one "
        + "column's worth (" + perColumnSpan + "); this is the seam the 3D redesign exists "
        + "to remove"
      );
    } finally {
      if (lx != null) {
        lx.dispose();
      }
      deleteTree(mediaPath);
    }
  }

  /**
   * Loads the real fixture, registers real {@code ApotheneumColor}/{@code ApotheneumGradient}
   * singletons with {@code pair}/{@code azimuth}/{@code elevation} set to the given values,
   * hosts {@code GradientMultiplyEffect} on a plain white {@code SolidPattern} channel (per
   * docs/headless-rendering.md's own guidance: a uniform field is the host that isolates the
   * effect's own transformation), runs 60 frames, and returns the final colors of every
   * cube-exterior point in column order.
   */
  private static int[] renderCubeExteriorColors(int pairValue, double azimuthDegrees, double elevationDegrees)
    throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-gradient-multiply-test-");
    LX lx = null;
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

      final ApotheneumColor apotheneumColor = registerApotheneumColor(lx);
      apotheneumColor.pair.setValue(pairValue);
      apotheneumColor.swap.setValue(0);

      final ApotheneumGradient gradient = registerApotheneumGradient(lx);
      gradient.azimuth.setValue(azimuthDegrees);
      gradient.elevation.setValue(elevationDegrees);

      final SolidPattern host = new SolidPattern(lx, LXColor.WHITE);
      final GradientMultiplyEffect effect = new GradientMultiplyEffect(lx);
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

  private static void assertDiffers(int[] before, int[] after, String message) {
    boolean differed = false;
    for (int i = 0; i < before.length; ++i) {
      if (before[i] != after[i]) {
        differed = true;
        break;
      }
    }
    assertTrue(differed, message);
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

  private static ApotheneumGradient registerApotheneumGradient(LX lx) {
    final ApotheneumGradient gradient = new ApotheneumGradient(lx);
    lx.engine.registerComponent(ApotheneumGradient.PATH, gradient);
    return gradient;
  }
}
