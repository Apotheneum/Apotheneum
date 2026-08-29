package apotheneum.doved.patterns;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.structure.JsonFixture;

/**
 * Ground-truth diagnostic, NOT reusing Fireball.Fire's bearingLut/bearingAt/arcFractionForBearing
 * anywhere. Independently recomputes real-world bearing straight from the raw fixture point
 * coordinates (Math.atan2 around each surface's own mean center), for whatever column the
 * ACTUAL running Fireball selects. This is the check the coordinator asked for: does the
 * shipped code's output agree with the building, not with itself.
 *
 * <p>This exists because the original alignment test composed {@code bearingAt} with
 * {@code arcFractionForBearing} — the two halves of the same lookup table — which is
 * self-consistent whichever way round they are applied, so it passed on a build whose
 * correctness had never been checked against the real geometry. A green suite that
 * cannot distinguish a correct transform from an inverted one is worse than no test.
 * Ground-truth against the fixture coordinates, never against the LUT.
 */
public class FireballGroundTruthDiagnosticTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";

  @Test
  void groundTruthAtKnownAzimuths() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-fireball-groundtruth-");
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
      Apotheneum.initialize(lx);

      final Apotheneum.Orientation cube = Apotheneum.cube.exterior;
      final Apotheneum.Orientation cylinder = Apotheneum.cylinder.exterior;
      final double cubeCx = surfaceCenterX(cube);
      final double cubeCz = surfaceCenterZ(cube);
      final double cylCx = surfaceCenterX(cylinder);
      final double cylCz = surfaceCenterZ(cylinder);

      final Fireball fireball = new Fireball(lx);
      final LXChannel channel = lx.engine.mixer.addChannel(new Fireball[] { fireball });
      lx.engine.run();

      for (float azimuth : new float[] { 0f, .1225f, .25f, .5f, .75f, .9f }) {
        fireball.azimuth.setValue(azimuth);
        fireball.loop(1000. / 60.);

        final Fireball.Fire cubeFire = fireball.cubeFire();
        final Fireball.Fire cylinderFire = fireball.cylinderFire();
        final float cubeX = cubeFire.headX();
        final float cylX = cylinderFire.headX();

        final double cubeBearingGT = groundTruthBearing(cube, cubeX, cubeCx, cubeCz);
        final double cylBearingGT = groundTruthBearing(cylinder, cylX, cylCx, cylCz);
        final double diff = angleDeltaDeg(cubeBearingGT, cylBearingGT);

        assertEquals(0., diff, 1e-2, String.format(
          "cube and cylinder must burn at the same real-world bearing; "
          + "azimuth=%.4f cubeX=%.3f cubeBearingGT=%.3f cylX=%.3f cylBearingGT=%.3f diffDeg=%.3f",
          azimuth, cubeX, cubeBearingGT, cylX, cylBearingGT, diff));
      }
    } finally {
      if (lx != null) {
        lx.dispose();
      }
      deleteTree(mediaPath);
    }
  }

  /** Ground truth: atan2 around the surface's own center, interpolated between the two raw
   * fixture points straddling fractional arc position x. Does NOT touch Fire.bearingLut. */
  private static double groundTruthBearing(
    Apotheneum.Orientation orientation, float x, double cx, double cz) {
    final int width = orientation.width();
    float wrapped = x % width;
    if (wrapped < 0) {
      wrapped += width;
    }
    final int i0 = (int) Math.floor(wrapped);
    final int i1 = (i0 + 1) % width;
    final float frac = wrapped - i0;

    final LXPoint p0 = orientation.point(i0, 0);
    final LXPoint p1 = orientation.point(i1, 0);
    double b0 = Math.toDegrees(Math.atan2(p0.z - cz, p0.x - cx));
    double b1 = Math.toDegrees(Math.atan2(p1.z - cz, p1.x - cx));
    // unwrap b1 relative to b0 for correct interpolation across the seam
    while (b1 - b0 > 180) {
      b1 -= 360;
    }
    while (b1 - b0 < -180) {
      b1 += 360;
    }
    return b0 + (b1 - b0) * frac;
  }

  private static double angleDeltaDeg(double a, double b) {
    double d = (a - b) % 360;
    if (d > 180) {
      d -= 360;
    } else if (d < -180) {
      d += 360;
    }
    return Math.abs(d);
  }

  private static double surfaceCenterX(Apotheneum.Orientation orientation) {
    double sum = 0;
    final Apotheneum.Column[] columns = orientation.columns();
    for (Apotheneum.Column column : columns) {
      sum += column.points[0].x;
    }
    return sum / columns.length;
  }

  private static double surfaceCenterZ(Apotheneum.Orientation orientation) {
    double sum = 0;
    final Apotheneum.Column[] columns = orientation.columns();
    for (Apotheneum.Column column : columns) {
      sum += column.points[0].z;
    }
    return sum / columns.length;
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
