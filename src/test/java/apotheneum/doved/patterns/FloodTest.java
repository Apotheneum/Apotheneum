package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

public class FloodTest {

  private static final double EPSILON = 1e-9;
  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";
  private static final int SENTINEL = LXColor.rgb(255, 0, 255);

  @Test
  void zeroAmplitudeReportsNoRelief() {
    // At Agitate effectively off there is no wave to report, so this returns exactly 0
    // rather than dividing by zero.
    assertEquals(0, Flood.wavePhysics(0, 0), EPSILON);
    assertEquals(0, Flood.wavePhysics(.3, 0), EPSILON);
  }

  @Test
  void physicsIsAShapeOnlySignalIndependentOfAgitationMagnitude() {
    // A full crest at a small amplitude and a full crest at a large amplitude both read as
    // physics near +1 -- the raw, amplitude-scaled wave value would hug zero at a small
    // Agitate setting, which is exactly what normalizing by the amplitude in use avoids.
    final double smallAmplitude = .05;
    final double largeAmplitude = 2;
    final double smallCrest = smallAmplitude * 1.26;
    final double largeCrest = largeAmplitude * 1.26;

    assertEquals(
      Flood.wavePhysics(smallCrest, smallAmplitude),
      Flood.wavePhysics(largeCrest, largeAmplitude),
      EPSILON
    );
    assertTrue(Flood.wavePhysics(smallCrest, smallAmplitude) > .9);
  }

  @Test
  void physicsIsSignedAndClamped() {
    final double amplitude = .4;
    assertTrue(Flood.wavePhysics(amplitude * 1.26, amplitude) > 0);
    assertTrue(Flood.wavePhysics(-amplitude * 1.26, amplitude) < 0);
    // Clamped even if the raw value somehow exceeded the nominal bound.
    assertEquals(1, Flood.wavePhysics(amplitude * 10, amplitude), EPSILON);
    assertEquals(-1, Flood.wavePhysics(-amplitude * 10, amplitude), EPSILON);
  }

  @Test
  void defaultTurbulenceIsTheLogSpaceFitOfThePriorSpectrum() {
    final double[] amplitudes = new double[OceanField.WAVE_NUMBERS.length];
    OceanField.resolveWaveAmplitudes(Flood.DEFAULT_TURBULENCE, amplitudes);

    // The previous hand-tuned values were approximate rather than an exact power law. The
    // default is their least-squares fit in log amplitude, preserving their spectral character.
    final double[] previous = { .62, .34, .19, .11 };
    for (int i = 0; i < amplitudes.length; ++i) {
      assertEquals(previous[i], amplitudes[i], .024);
    }
  }

  @Test
  void patternViewOnlyWritesItsSelectedPoints() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-flood-view-test-");
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
      assertFalse(lx.engine.output.enabled.isOn(), "fixture load enabled output");
      Apotheneum.initialize(lx);

      final LXViewDefinition cylinderExterior = lx.structure.views.addView();
      cylinderExterior.selector.setValue("cylinderExterior");
      final Flood flood = new Flood(lx);
      // Default Level is 0 (no water at all); raise it so the view actually has something
      // to draw, otherwise "nothing outside the view was touched" would be true vacuously.
      flood.level.setValue(.5);
      final LXChannel channel = lx.engine.mixer.addChannel(new Flood[] { flood });
      flood.view.setValue(cylinderExterior);
      assertTrue(channel.getActivePattern() == flood);

      lx.engine.run();
      Arrays.fill(flood.getColors(), SENTINEL);
      for (int frame = 0; frame < 20; ++frame) {
        flood.loop(1000. / 60.);
      }

      final int[] colors = flood.getColors();
      assertUnwritten(Apotheneum.cube.exterior, colors, "cube exterior was written");
      assertUnwritten(Apotheneum.cube.interior, colors, "cube interior was written");
      // The mirror is the trap: copyExterior() is a raw arraycopy over whole orientations
      // that no guard outside it can reach, so an unmasked mirror would still paint the
      // cylinder's own interior underneath a cylinder-exterior-only view.
      assertUnwritten(Apotheneum.cylinder.interior, colors, "cylinder interior was mirrored");

      boolean lit = false;
      for (Apotheneum.Column column : Apotheneum.cylinder.exterior.columns()) {
        for (LXPoint point : column.points) {
          if (colors[point.index] != SENTINEL) {
            lit = true;
            break;
          }
        }
      }
      assertTrue(lit, "cylinder exterior was not written");
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
