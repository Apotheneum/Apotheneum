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
import apotheneum.doved.modulators.ApotheneumColor;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.view.LXViewDefinition;

public class GrassTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";
  private static final int SENTINEL = LXColor.rgb(255, 0, 255);

  @Test
  void patternViewOnlyWritesItsSelectedPoints() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-grass-test-");
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
      final Grass grass = new Grass(lx);
      final LXChannel channel = lx.engine.mixer.addChannel(new Grass[] { grass });
      grass.view.setValue(cylinderExterior);
      assertTrue(channel.getActivePattern() == grass);

      lx.engine.run();
      Arrays.fill(grass.getColors(), SENTINEL);
      for (int frame = 0; frame < 20; ++frame) {
        grass.loop(1000. / 60.);
      }

      assertUnwritten(Apotheneum.cube.exterior, grass.getColors(), "cube exterior was written");
      assertUnwritten(Apotheneum.cube.interior, grass.getColors(), "cube interior was written");
      boolean cylinderWasWritten = false;
      for (LXPoint point : Apotheneum.cylinder.exterior.columns()[0].points) {
        if (grass.getColors()[point.index] != SENTINEL) {
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
   * {@code Grass.output()} used to call {@code primary.color(0)}, hardcoding away the
   * argument {@code primary.amount} exists to scale, so the knob was exposed in the UI and did
   * nothing: every frame read as physics=0 regardless of wind. {@code secondary.color
   * (silveringValue)} right next to it was unaffected, which is why the bug was easy to miss by
   * eye - the pattern still visibly reacted to wind, just only on one of its two tones.
   *
   * <p>Two identically-seeded, identically-driven {@code Grass} instances (the pattern uses a
   * fixed {@code Random} seed, exactly like {@code Fireball}, so this is a real determinism
   * guarantee and not a coincidence) differing only in {@code primary.amount} must render
   * different colors somewhere once wind ({@code silvering}, default .25, nonzero) is flowing -
   * otherwise the argument is still not reaching {@code primary.color(...)}.
   */
  @Test
  void primaryAmountAffectsRenderedColor() throws Exception {
    final int[] off = renderGrassCylinderExteriorColors(0);
    final int[] on = renderGrassCylinderExteriorColors(1);

    boolean differed = false;
    for (int i = 0; i < off.length; ++i) {
      if (off[i] != on[i]) {
        differed = true;
        break;
      }
    }
    assertTrue(
      differed,
      "primary.amount=0 and primary.amount=1 rendered identical colors - "
        + "primary.color(...) is not receiving a live physics argument");
  }

  /**
   * Renders 60 deterministic frames (fixed simulation seed, matching {@code Fireball}) of a
   * fresh {@code Grass} on its own {@code LX}/fixture, with {@code primary.amount} set to
   * {@code primaryAmount}, and returns the final colors of every cylinder-exterior point in
   * column order. A separate {@code LX} per call avoids any cross-channel interaction in the
   * mixer affecting buffer allocation.
   */
  private static int[] renderGrassCylinderExteriorColors(double primaryAmount) throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-grass-amount-test-");
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

      // primary.amount couples a physics wobble on top of ApotheneumColor's resolved base
      // color, and that wobble is invisible against a base already at brightness 100 (see
      // modulatedColor: a positive shift on a maxed brightness silently clamps to itself).
      // Without an ApotheneumColor present, a role falls back to neutral white, which is
      // exactly that maxed-brightness case -- so this regression test needs a real, non-maxed
      // base color for primary.amount to have anything to visibly move.
      apotheneumColor = lx.engine.modulation.addModulator(new ApotheneumColor());
      lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(30, 90, 70));
      apotheneumColor.pair.setValue(0);
      apotheneumColor.swap.setValue(0);

      final Grass grass = new Grass(lx);
      grass.primary.amount.setValue(primaryAmount);
      lx.engine.mixer.addChannel(new Grass[] { grass });

      lx.engine.run();
      for (int frame = 0; frame < 60; ++frame) {
        grass.loop(1000. / 60.);
      }

      final int[] colors = grass.getColors();
      final Apotheneum.Column[] columns = Apotheneum.cylinder.exterior.columns();
      final int height = Apotheneum.CYLINDER_HEIGHT;
      final int[] result = new int[columns.length * height];
      int i = 0;
      for (Apotheneum.Column column : columns) {
        for (LXPoint point : column.points) {
          result[i++] = colors[point.index];
        }
      }
      return result;
    } finally {
      // Removed (not just disposed) before lx.dispose() runs: removeModulator disposes it
      // exactly once and drops it from the engine's tracked list, so the LX-level teardown
      // below never encounters it a second time. Without this it would also dangle in the
      // static singleton, tied to an LX this test is about to dispose, and leak into whichever
      // test runs next in this class within the same JVM fork.
      if (apotheneumColor != null) {
        lx.engine.modulation.removeModulator(apotheneumColor);
      }
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
