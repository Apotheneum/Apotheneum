package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.view.LXViewDefinition;

/** Regression coverage for Dunes' pattern-view output discipline. */
public class DunesTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";
  private static final int SENTINEL = LXColor.rgb(255, 0, 255);

  @Test
  void defaultProfileIsMidwallAndModerate() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-dunes-geometry-");
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

      final Dunes dunes = new Dunes(lx);
      final double[] horizon = dunes.cubeHorizonMetrics();
      final double reliefRows = horizon[2] - horizon[1];
      assertTrue(horizon[0] >= 20 && horizon[0] <= 25,
        "mean horizon left mid-wall: " + horizon[0]);
      assertTrue(reliefRows >= 10 && reliefRows <= 16,
        "default relief was not a 10-16 row dune field: " + reliefRows);

      final double[] lowBase = dunes.cubeHorizonMetrics(.2, 0);
      final double[] highBase = dunes.cubeHorizonMetrics(.85, 0);
      assertTrue(lowBase[0] > horizon[0] + 10,
        "low Base did not lower the horizon toward door rows: " + lowBase[0]);
      assertTrue(highBase[0] < horizon[0] - 10,
        "high Base did not raise the horizon: " + highBase[0]);

      final double adjacentCorrelation = dunes.cubeSliceCorrelation(0, 1. / 56.);
      final double nearCorrelation = dunes.cubeSliceCorrelation(0, 4. / 56.);
      final double farCorrelation = dunes.cubeSliceCorrelation(0, .5);
      final double seamCorrelation = dunes.cubeSliceCorrelation(0, 1);
      LX.log("[DunesTest] travel correlation depth=1 " + adjacentCorrelation
        + " depth=4 " + nearCorrelation + " depth=28 " + farCorrelation
        + " depth=56 " + seamCorrelation);
      assertTrue(seamCorrelation > .999999,
        "Travel did not wrap seamlessly: " + seamCorrelation);
      assertTrue(adjacentCorrelation > farCorrelation,
        "nearby Travel slices were not more correlated than distant slices: "
          + adjacentCorrelation + " <= " + farCorrelation);
    } finally {
      if (lx != null) {
        lx.dispose();
      }
      deleteTree(mediaPath);
    }
  }

  @Test
  void patternViewOnlyWritesItsSelectedPoints() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-dunes-test-");
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

      final LXViewDefinition cylinderExterior = lx.structure.views.addView();
      cylinderExterior.selector.setValue("cylinderExterior");
      final Dunes dunes = new Dunes(lx);
      final LXChannel channel = lx.engine.mixer.addChannel(new Dunes[] { dunes });
      dunes.view.setValue(cylinderExterior);
      assertTrue(channel.getActivePattern() == dunes);

      lx.engine.run();
      Arrays.fill(dunes.getColors(), SENTINEL);
      for (int frame = 0; frame < 20; ++frame) {
        dunes.loop(1000. / 60.);
      }

      assertUnwritten(Apotheneum.cube.exterior, dunes.getColors(), "cube exterior was written");
      assertUnwritten(Apotheneum.cube.interior, dunes.getColors(), "cube interior was written");
      boolean cylinderWasWritten = false;
      for (LXPoint point : Apotheneum.cylinder.exterior.columns()[0].points) {
        if (dunes.getColors()[point.index] != SENTINEL) {
          cylinderWasWritten = true;
          break;
        }
      }
      assertTrue(cylinderWasWritten, "cylinder exterior was not written");
    } finally {
      if (lx != null) {
        lx.dispose();
      }
      deleteTree(mediaPath);
    }
  }

  /**
   * Exercises the failure mode that a five-second render cannot show: fixed wind can pull sand
   * into immobile convergence piles. At high Rate, 3,000 frames are a deliberately long run;
   * non-black coverage stays bounded while changing fingerprints show the field is still moving.
   */
  @Test
  void highRateLongHorizonStaysLitAndEvolving() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-dunes-horizon-");
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
      while (lx.engine.palette.swatch.colors.size() < 2) {
        lx.engine.palette.swatch.addColor();
      }
      lx.engine.palette.swatch.colors.get(0).primary.brightness.setValue(70);
      lx.engine.palette.swatch.colors.get(1).primary.brightness.setValue(90);

      final Dunes dunes = new Dunes(lx);
      dunes.rate.setValue(1);
      dunes.wind.setValue(1);
      lx.engine.mixer.addChannel(new Dunes[] { dunes });
      lx.engine.run();

      final int[] sampleFrames = { 1, 750, 1_500, 2_250, 3_000 };
      final double[] fractions = new double[sampleFrames.length];
      final double[] brightness = new double[sampleFrames.length];
      final long[] fingerprints = new long[sampleFrames.length];
      int sample = 0;
      for (int frame = 1; frame <= sampleFrames[sampleFrames.length - 1]; ++frame) {
        dunes.loop(1000. / 60.);
        if (frame == sampleFrames[sample]) {
          final double[] metrics = surfaceMetrics(Apotheneum.cube.exterior, dunes.getColors());
          fractions[sample] = metrics[0];
          brightness[sample] = metrics[1];
          fingerprints[sample] = surfaceFingerprint(Apotheneum.cube.exterior, dunes.getColors());
          LX.log("[DunesTest] highRate frame=" + frame + " nonBlackFraction=" + fractions[sample]
            + " meanBrightnessPct=" + brightness[sample]);
          ++sample;
          if (sample == sampleFrames.length) {
            break;
          }
        }
      }
      for (double fraction : fractions) {
        assertTrue(fraction > .35 && fraction < .75, "coverage collapsed or flooded: " + fraction);
      }
      assertTrue(fingerprints[0] != fingerprints[fingerprints.length - 1],
        "high-rate field stopped changing over the long horizon");
    } finally {
      if (lx != null) {
        lx.dispose();
      }
      deleteTree(mediaPath);
    }
  }

  private static double[] surfaceMetrics(Apotheneum.Orientation orientation, int[] colors) {
    int total = 0;
    int nonBlack = 0;
    double brightness = 0;
    for (int x = 0; x < orientation.width(); ++x) {
      for (int y = 0; y < orientation.available(x); ++y) {
        final int color = colors[orientation.point(x, y).index];
        ++total;
        if (color != LXColor.BLACK) {
          ++nonBlack;
          brightness += LXColor.b(color);
        }
      }
    }
    return new double[] { nonBlack / (double) total, brightness / total };
  }

  private static long surfaceFingerprint(Apotheneum.Orientation orientation, int[] colors) {
    long hash = 0xcbf29ce484222325L;
    for (int x = 0; x < orientation.width(); ++x) {
      for (int y = 0; y < orientation.available(x); ++y) {
        hash ^= colors[orientation.point(x, y).index];
        hash *= 0x100000001b3L;
      }
    }
    return hash;
  }

  private static void copyFixtureMedia(Path mediaPath) throws IOException {
    final Path destination = Files.createDirectories(mediaPath.resolve("Fixtures"));
    try (Stream<Path> sources = Files.list(SOURCE_FIXTURES)) {
      for (Path source : sources.filter(Files::isRegularFile).toList()) {
        Files.copy(source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private static void assertUnwritten(Apotheneum.Orientation orientation, int[] colors, String message) {
    for (Apotheneum.Column column : orientation.columns()) {
      for (LXPoint point : column.points) {
        assertTrue(colors[point.index] == SENTINEL, message);
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
