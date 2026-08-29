package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.structure.JsonFixture;

/**
 * Fireball's {@code azimuth} means arc-length on the cube (four flat, evenly-columned walls)
 * and true bearing on the cylinder (a real circle), so driving both surfaces from the same raw
 * {@code azimuth * width} put them at different real-world directions almost everywhere - a
 * constant offset only lines them up at the one azimuth it was tuned for. {@link Fireball.Fire
 * #bearingAt} and {@link Fireball.Fire#arcFractionForBearing} convert between the two so the
 * cylinder tracks the compass bearing the cube's own arc-length position implies, at every
 * azimuth, using the installation's real geometry rather than an assumed shape.
 *
 * <p>This test drives the real Apotheneum fixture, not a synthetic one: the whole point being
 * verified is a property of the actual cube and cylinder geometry, not of the simulation math in
 * isolation.
 */
public class FireballBearingAlignmentTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";

  /** Loose relative to the ~43 degree (.75 rad) misalignment the bug produced. */
  private static final float BEARING_TOLERANCE_RADIANS = 1e-3f;

  private static final float TWO_PI = (float) (2 * Math.PI);

  private Path mediaPath;
  private LX lx;
  private Fireball fireball;

  @BeforeEach
  void setUp() throws Exception {
    this.mediaPath = Files.createTempDirectory("apotheneum-fireball-bearing-test-");
    copyFixtureMedia(this.mediaPath);

    final LX.Flags flags = new LX.Flags();
    flags.loadPreferences = false;
    flags.mediaPath = this.mediaPath.toString();
    flags.outputMode = LX.Flags.OutputMode.INACTIVE;
    this.lx = new LX(flags);
    this.lx.engine.output.enabled.setValue(false);

    final JsonFixture fixture = new JsonFixture(this.lx, FIXTURE_NAME);
    this.lx.structure.addFixture(fixture);
    this.lx.structure.beforeEngineRun();
    assertTrue(!fixture.error.isOn(), fixture.errorMessage.getString());
    Apotheneum.initialize(this.lx);

    this.fireball = new Fireball(this.lx);
    final LXChannel channel = this.lx.engine.mixer.addChannel(new Fireball[] { this.fireball });
    assertTrue(channel.getActivePattern() == this.fireball);
    this.lx.engine.run();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (this.lx != null) {
      this.lx.dispose();
    }
    if (this.mediaPath != null) {
      deleteTree(this.mediaPath);
    }
  }

  /**
   * At every azimuth around the full lap, the cylinder's derived position must land on the
   * same real-world bearing the cube's own arc-length position implies. Sampling densely
   * (401 points, including both corners and wall midpoints) means this would have caught the
   * original bug at every point, not only the one azimuth a constant offset happens to fix.
   */
  @Test
  void cylinderMatchesCubeBearingAcrossFullSweep() {
    for (int i = 0; i <= 400; ++i) {
      final float azimuth = i / 400f;
      this.fireball.azimuth.setValue(azimuth);
      this.fireball.loop(1000. / 60.);

      final Fireball.Fire cube = this.fireball.cubeFire();
      final Fireball.Fire cylinder = this.fireball.cylinderFire();
      final float cubeBearing = cube.bearingAt(cube.headX());
      final float cylinderBearing = cylinder.bearingAt(cylinder.headX());

      assertEquals(
        0f, angleDelta(cubeBearing, cylinderBearing), BEARING_TOLERANCE_RADIANS,
        "azimuth=" + azimuth + ": cube bearing=" + cubeBearing
          + " cylinder bearing=" + cylinderBearing);
    }
  }

  /**
   * The pattern is now driven by a continuously ramping azimuth on the live rig, so any
   * discontinuity in the cube-to-cylinder remap - a binary-search branch flipping, a wrap
   * seam not closing - would show up as a visible stutter. Step azimuth in small increments
   * across the full lap, including the 0/1 wrap and every corner, and require the cylinder's
   * position to move by a bounded, small amount each step: a real jump (the kind a broken
   * inverse produces) is on the order of the whole ring, not a fraction of one column.
   */
  @Test
  void cylinderPositionIsContinuousAcrossFullSweep() {
    final int steps = 2000;
    Float previousX = null;
    for (int i = 0; i <= steps; ++i) {
      final float azimuth = (i % steps) / (float) steps;
      this.fireball.azimuth.setValue(azimuth);
      this.fireball.loop(1000. / 60.);

      final Fireball.Fire cylinder = this.fireball.cylinderFire();
      final float x = cylinder.headX();
      if (previousX != null) {
        final float delta = Math.abs(wrappedDelta(x - previousX, cylinder.width()));
        assertTrue(
          delta < 5f,
          "azimuth=" + azimuth + ": cylinder position jumped " + delta
            + " columns from " + previousX + " to " + x);
      }
      previousX = x;
    }
  }

  /** Shortest signed distance between two column positions on a {@code width}-column ring. */
  private static float wrappedDelta(float dx, int width) {
    float wrapped = dx % width;
    if (wrapped > width / 2f) {
      wrapped -= width;
    } else if (wrapped < -width / 2f) {
      wrapped += width;
    }
    return wrapped;
  }

  /** Shortest signed angular distance between two unwrapped bearings, in radians. */
  private static float angleDelta(float a, float b) {
    float delta = (a - b) % TWO_PI;
    if (delta > Math.PI) {
      delta -= TWO_PI;
    } else if (delta < -Math.PI) {
      delta += TWO_PI;
    }
    return Math.abs(delta);
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
