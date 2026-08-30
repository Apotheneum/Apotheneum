package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import heronarts.lx.structure.JsonFixture;

/**
 * Wiring-level proof that {@code Waterfall}'s adoption of {@code ColorNativePattern
 * .colorizeCells} actually resolves colour independently per real surface, against the real
 * fixture -- not a unit test of a colour-resolution helper in isolation, which would keep
 * passing even if {@code render()} silently went back to a bulk {@code copyExterior()}. Before
 * this adoption, {@code Waterfall} rendered its exterior only and copied the finished,
 * already-colourized pixels onto the interior with a raw {@code System.arraycopy}; {@code
 * ApotheneumColor.Axis.INSIDE_OUTSIDE} therefore used to collapse Waterfall to look exactly like
 * {@code Axis.NONE}. This test is the specific regression check for that: it must now show a
 * real, non-neutral difference between a cube exterior point and its interior mirror once
 * {@code Axis.INSIDE_OUTSIDE} is selected, and mirrors {@code FireballInteriorColorWiringTest}'s
 * structure so both patterns are held to the same standard.
 */
public class WaterfallInteriorColorWiringTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";

  @Test
  void insideOutsideAxisMakesCubeExteriorAndInteriorGenuinelyDiffer() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-waterfall-interior-test-");
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

      // Two visibly distinct stops, so a one-stop shift is unmistakable in the resolved color,
      // not just numerically different.
      lx.engine.palette.swatch.addColor();
      lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(20, 95, 90));
      lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(210, 95, 90));

      final ApotheneumColor apotheneumColor = new ApotheneumColor(lx);
      lx.engine.registerComponent(ApotheneumColor.PATH, apotheneumColor);
      apotheneumColor.pair.setValue(0);
      apotheneumColor.swap.setValue(0);
      apotheneumColor.axis.setValue(2); // In/Out

      final Waterfall waterfall = new Waterfall(lx);
      final LXChannel channel = lx.engine.mixer.addChannel(new Waterfall[] { waterfall });
      assertTrue(channel.getActivePattern() == waterfall);

      lx.engine.run();
      for (int frame = 0; frame < 90; ++frame) {
        lx.engine.run();
      }

      final int[] colors = waterfall.getColors();

      // Find a cube-exterior point with real (non-black) water or rock on it, and its exact
      // interior mirror at the same (x, y) -- Waterfall's own attachGeometry() maps both from
      // the same cell.
      final Apotheneum.Column[] exteriorColumns = Apotheneum.cube.exterior.columns();
      final Apotheneum.Column[] interiorColumns = Apotheneum.cube.interior.columns();
      boolean foundLitPoint = false;
      boolean differed = false;
      for (int x = 0; x < exteriorColumns.length && !differed; ++x) {
        final int height = Math.min(exteriorColumns[x].points.length, interiorColumns[x].points.length);
        for (int y = 0; y < height; ++y) {
          final int exteriorColor = colors[exteriorColumns[x].points[y].index];
          if (exteriorColor == LXColor.BLACK) {
            continue;
          }
          foundLitPoint = true;
          final int interiorColor = colors[interiorColumns[x].points[y].index];
          if (exteriorColor != interiorColor) {
            differed = true;
            break;
          }
        }
      }

      assertTrue(foundLitPoint, "no lit cube-exterior point after 90 frames -- Waterfall never poured");
      assertTrue(
        differed,
        "cube exterior and interior stayed identical under Axis.INSIDE_OUTSIDE -- Waterfall's "
        + "adoption of colorizeCells is not resolving colour independently per real surface"
      );
    } finally {
      if (lx != null) {
        lx.dispose();
      }
      deleteTree(mediaPath);
    }
  }

  @Test
  void shapeAxisStillMatchesCubeExteriorAndInteriorOnWaterfall() throws Exception {
    // Sanity check for the other direction: Axis.SHAPE only differentiates cube from cylinder.
    // Waterfall's interior used to agree with its exterior by construction (a bulk
    // copyExterior()); adopting colorizeCells must not have accidentally broken that agreement.
    final Path mediaPath = Files.createTempDirectory("apotheneum-waterfall-interior-test-");
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
      lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(20, 95, 90));
      lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(210, 95, 90));

      final ApotheneumColor apotheneumColor = new ApotheneumColor(lx);
      lx.engine.registerComponent(ApotheneumColor.PATH, apotheneumColor);
      apotheneumColor.pair.setValue(0);
      apotheneumColor.swap.setValue(0);
      apotheneumColor.axis.setValue(1); // Shape

      final Waterfall waterfall = new Waterfall(lx);
      lx.engine.mixer.addChannel(new Waterfall[] { waterfall });

      lx.engine.run();
      for (int frame = 0; frame < 90; ++frame) {
        lx.engine.run();
      }

      final int[] colors = waterfall.getColors();
      final Apotheneum.Column[] exteriorColumns = Apotheneum.cube.exterior.columns();
      final Apotheneum.Column[] interiorColumns = Apotheneum.cube.interior.columns();
      boolean foundLitPoint = false;
      for (int x = 0; x < exteriorColumns.length; ++x) {
        final int height = Math.min(exteriorColumns[x].points.length, interiorColumns[x].points.length);
        for (int y = 0; y < height; ++y) {
          final int exteriorColor = colors[exteriorColumns[x].points[y].index];
          if (exteriorColor == LXColor.BLACK) {
            continue;
          }
          foundLitPoint = true;
          assertEquals(
            exteriorColor,
            colors[interiorColumns[x].points[y].index],
            "Axis.SHAPE must still keep cube exterior and interior identical on Waterfall"
          );
        }
      }
      assertTrue(foundLitPoint, "no lit cube-exterior point after 90 frames -- Waterfall never poured");
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
}
