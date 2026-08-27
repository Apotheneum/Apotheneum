package apotheneum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;
import heronarts.lx.structure.JsonFixture;

/**
 * Shared headless-LX fixture. Each test constructs its own {@link LX} — over a small stand-in
 * grid via {@link #newHeadlessLx()}, or over the real installation geometry via
 * {@link #newApotheneumLx()} — and the instance is disposed after each test. {@code new LX(...)}
 * starts a non-daemon MIDI device-update thread that, on macOS, contends on a static
 * CoreMIDI lock; without disposal these accumulate across tests and deadlock construction.
 * Every instance handed to {@link #track(LX)} is disposed, so none outlives its test.
 */
public abstract class HeadlessLxTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String APOTHENEUM_FIXTURE = "Apotheneum";

  private final List<LX> tracked = new ArrayList<LX>();

  protected LX newHeadlessLx() {
    return track(new LX(newModel()));
  }

  /**
   * Registers an externally constructed LX (e.g. with custom flags) for after-test disposal.
   * Every registered instance is disposed — a test comparing two configurations must not leak
   * the first one's device-update thread.
   */
  protected LX track(LX lx) {
    this.tracked.add(lx);
    return lx;
  }

  /**
   * Builds an LX over the real installation geometry: the shipped fixtures are staged into a
   * throwaway media directory, the {@code Apotheneum} fixture is loaded, and
   * {@link Apotheneum#initialize(LX)} is run. The returned instance is {@link #track(LX) tracked},
   * so it is disposed after the test like any other.
   *
   * <p>The fixture carries the installation's real Art-Net addresses, so output is held off in
   * two places — {@code OutputMode.INACTIVE} plus an explicit disable before the fixture loads —
   * and asserted still off once the model is up. A test that renders this model must never put
   * packets on a network.
   */
  protected LX newApotheneumLx() throws IOException {
    final Path mediaPath = Files.createTempDirectory("apotheneum-headless-");
    stageFixtureMedia(mediaPath);

    final LX.Flags flags = new LX.Flags();
    flags.loadPreferences = false;
    flags.mediaPath = mediaPath.toString();
    flags.outputMode = LX.Flags.OutputMode.INACTIVE;

    final LX lx = track(new LX(flags));
    lx.engine.output.enabled.setValue(false);

    final JsonFixture fixture = new JsonFixture(lx, APOTHENEUM_FIXTURE);
    lx.structure.addFixture(fixture);
    // addFixture only stages regeneration; without this the model is not there yet.
    lx.structure.beforeEngineRun();
    assertFalse(fixture.error.isOn(), "fixture load failed: " + fixture.errorMessage.getString());

    Apotheneum.initialize(lx);
    assertTrue(Apotheneum.exists, "Apotheneum.exists was false after loading the real fixture");
    assertFalse(lx.engine.output.enabled.isOn(), "engine output became enabled");
    return lx;
  }

  /**
   * Copies the repository's fixtures into {@code <mediaPath>/Fixtures/}. The capital F matters:
   * JsonFixture resolves names through {@code <mediaPath>/Fixtures/}, while this repo stores them
   * in a lowercase directory that only resolves on a case-insensitive filesystem.
   *
   * <p>Cleanup rides on {@link java.io.File#deleteOnExit()}, which unwinds LIFO. Walking the
   * staged tree registers it in pre-order — parents before children — so children are deleted
   * first and each directory is empty by the time its own turn comes.
   */
  private static void stageFixtureMedia(Path mediaPath) throws IOException {
    final Path destination = Files.createDirectories(mediaPath.resolve("Fixtures"));
    try (Stream<Path> sources = Files.list(SOURCE_FIXTURES)) {
      for (Path source : sources.filter(Files::isRegularFile).toList()) {
        Files.copy(source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    try (Stream<Path> tree = Files.walk(mediaPath)) {
      tree.forEach(path -> path.toFile().deleteOnExit());
    }
  }

  /** Override to customize the model. */
  protected LXModel newModel() {
    return new GridModel(8, 8);
  }

  @AfterEach
  final void disposeLx() {
    Throwable failure = null;
    // Reverse order, and keep going past a failure: a dispose that throws must not
    // strand the remaining instances' non-daemon threads. Throwable, not
    // RuntimeException — an Error here (a NoClassDefFoundError out of LX teardown,
    // say) would otherwise leave those threads alive and wedge the fork.
    for (int i = this.tracked.size() - 1; i >= 0; --i) {
      try {
        this.tracked.get(i).dispose();
      } catch (Throwable x) {
        if (failure == null) {
          failure = x;
        } else {
          failure.addSuppressed(x);
        }
      }
    }
    this.tracked.clear();
    // dispose() declares no checked exceptions, so the first two branches cover it;
    // the third only keeps the compiler happy.
    if (failure instanceof RuntimeException runtime) {
      throw runtime;
    } else if (failure instanceof Error error) {
      throw error;
    } else if (failure != null) {
      throw new AssertionError("LX disposal failed", failure);
    }
  }
}
