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

/**
 * Fireball writes by global point index through cached geometry — the heat pass, the ember
 * pass and the interior mirror all do — so a pattern-level model view constrains it only if
 * every one of those writes is guarded. The mirror is the trap: it used to be
 * {@code copyExterior()}, an arraycopy over whole orientations that no guard outside it can
 * reach, so a view selecting the cylinder exterior would still have had the cube's and the
 * cylinder's interiors painted underneath it.
 *
 * <p>The buffer is prefilled with a sentinel rather than checked for black, so "never written"
 * is distinguishable from "written black" — a pattern that blacked the whole installation
 * every frame would pass the latter check while trampling every point outside its view.</p>
 */
public class FireballViewTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";
  private static final int SENTINEL = LXColor.rgb(255, 0, 255);

  @Test
  void patternViewOnlyWritesItsSelectedPoints() throws Exception {
    final Path mediaPath = Files.createTempDirectory("apotheneum-fireball-view-test-");
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
      final Fireball fireball = new Fireball(lx);
      final LXChannel channel = lx.engine.mixer.addChannel(new Fireball[] { fireball });
      fireball.view.setValue(cylinderExterior);
      assertTrue(channel.getActivePattern() == fireball);

      lx.engine.run();
      Arrays.fill(fireball.getColors(), SENTINEL);
      for (int frame = 0; frame < 20; ++frame) {
        fireball.loop(1000. / 60.);
      }

      final int[] colors = fireball.getColors();
      assertUnwritten(Apotheneum.cube.exterior, colors, "cube exterior was written");
      assertUnwritten(Apotheneum.cube.interior, colors, "cube interior was written");
      // The mirror is the one an unguarded block copy would have reached even though the
      // exterior writes themselves were guarded.
      assertUnwritten(Apotheneum.cylinder.interior, colors, "cylinder interior was mirrored");

      // Non-vacuity from the other side: the view is not merely being left alone. The frame's
      // clear would satisfy "not the sentinel" on its own, so require heat actually drawn.
      boolean lit = false;
      for (Apotheneum.Column column : Apotheneum.cylinder.exterior.columns()) {
        for (LXPoint point : column.points) {
          if (LXColor.b(colors[point.index]) > 0) {
            lit = true;
            break;
          }
        }
      }
      assertTrue(lit, "cylinder exterior drew nothing inside the view");
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
