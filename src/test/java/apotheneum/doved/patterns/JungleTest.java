package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXModel;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.view.LXViewDefinition;

/**
 * {@link Jungle} against the real installation geometry: what a pattern-level model view lets
 * through, and whether a frame is a pure function of the pattern's state.
 *
 * Jungle draws through the global Apotheneum geometry and writes by point index, so a view
 * narrows nothing on its own — it needs the membership mask {@link ViewMaskedPattern} carries,
 * and every write has to consult it. Two of those writes are easy to miss: the blackout of the
 * rows a doorway takes out of a column, and the interior surfaces, which the pattern used to
 * fill with {@code copyExterior()} — a bulk arraycopy over whole orientations that would write
 * straight through any view. That copy is now a second guarded write pass over the same raster,
 * and the widening case below is what holds it to covering every point the copy did.
 *
 * Everything lives in one test method on purpose: {@code Apotheneum} holds its model state
 * statically and {@code initialize()} returns early once it has run, so a second {@code LX} in
 * the same JVM would silently keep the first one's listener. Disposal goes through
 * {@link HeadlessLxTest#track}, since an undisposed instance strands a non-daemon MIDI thread.
 */
public class JungleTest extends HeadlessLxTest {

  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");
  private static final String FIXTURE_NAME = "Apotheneum";

  private static final double FIXED_DELTA_MS = 1000. / 60;
  /** Frames to let the canopy fill before what is lit is meaningful. */
  private static final int WARMUP_FRAMES = 30;
  /** Frames to run behind a sentinel prefill when checking what a view lets through. */
  private static final int VIEW_FRAMES = 20;
  /**
   * A colour the pattern never writes, prefilled so an untouched point is distinguishable from
   * one the pattern deliberately painted black — which it does, on every doorway row.
   */
  private static final int SENTINEL = 0x7f123456;

  private LXViewDefinition cylinderExteriorView;

  @Test
  void jungleOnTheRealInstallationGeometry() throws IOException {
    final LX lx = newApotheneumLx();

    final Jungle jungle = new Jungle(lx);
    final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { jungle });
    channel.fader.setValue(1);
    lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

    assertNarrowingTheViewClearsWhatItLeavesBehind(lx, jungle);
    assertWideningTheViewResumesFullOutput(lx, jungle);
    assertViewOnlyWritesItsSelectedPoints(lx, jungle);
    jungle.view.setValue(null);
    assertAFrameDoesNotDependOnTheFrameBeforeIt(jungle);
    assertTreeCountDoesNotRebakeTheTextures(jungle);
  }

  /**
   * The same scene renders the same frame regardless of what was rendered before it.
   *
   * The light march accumulates down a slanted ray, reading two neighbouring columns of the
   * row above. Iterate columns before rows and those neighbours are unwritten for the whole
   * negative half of the slant range, so the read lands on the previous frame's occlusion and
   * the shafts lag the canopy on one side of centre only. That is invisible frame to frame —
   * the previous frame is nearly the right answer — which is why this asserts the property
   * directly: render a scene, render a deliberately different one, come back, and demand the
   * first frame back. Negative slant because that is the half the defect lived in.
   */
  private void assertAFrameDoesNotDependOnTheFrameBeforeIt(Jungle jungle) {
    jungle.slant.setValue(-1);
    jungle.loop(FIXED_DELTA_MS);

    // No time passes from here, so every frame below describes the same instant: any
    // difference between them is carried state, not motion.
    jungle.loop(0);
    final int[] reference = jungle.getColors().clone();

    final double density = jungle.density.getValue();
    jungle.density.setValue(.05);
    jungle.loop(0);
    final int[] different = jungle.getColors().clone();
    assertFalse(
      Arrays.equals(reference, different),
      "the intervening scene rendered identically, so it cannot perturb anything and the "
        + "assertion below would hold against a march that reads the previous frame"
    );

    jungle.density.setValue(density);
    jungle.loop(0);
    assertArrayEquals(
      reference,
      jungle.getColors(),
      "the same scene rendered differently after an intervening frame, so the light march is "
        + "reading occlusion the previous frame left behind rather than this frame's row above"
    );
  }

  /**
   * Sweeping the tree count does not rebake the noise textures.
   *
   * They depend on the seed alone and cost about 98,000 noise evaluations to build, while
   * {@code treeCount} is modulatable — so a modulator on it would rebake both textures every
   * frame if the two were keyed together.
   */
  private void assertTreeCountDoesNotRebakeTheTextures(Jungle jungle) {
    jungle.loop(FIXED_DELTA_MS);
    final int bakes = jungle.textureGenerations;

    for (int count = 4; count <= 48; ++count) {
      jungle.treeCount.setValue(count);
      jungle.loop(FIXED_DELTA_MS);
    }
    assertEquals(
      bakes,
      jungle.textureGenerations,
      "sweeping the tree count rebaked the noise textures, which depend only on the seed"
    );

    jungle.seed.setValue(jungle.seed.getValuei() + 1);
    jungle.loop(FIXED_DELTA_MS);
    assertEquals(
      bakes + 1,
      jungle.textureGenerations,
      "changing the seed did not rebake the textures, so the split dropped the rebake entirely"
    );
  }

  /**
   * Narrowing the view blacks out what the view used to cover.
   *
   * The stale-pixel case: Jungle rewrites every point it owns every frame, so it needs no
   * per-frame clear, but points the view has just stopped covering are never written again and
   * would hold their last colour forever if nothing cleared them at the moment of the change.
   * Lighting the whole installation first is what gives this something to find.
   */
  private void assertNarrowingTheViewClearsWhatItLeavesBehind(LX lx, Jungle jungle) {
    jungle.view.setValue(null);
    for (int frame = 0; frame < WARMUP_FRAMES; ++frame) {
      lx.engine.run();
    }
    // Frames from here go through the pattern's own loop rather than the engine's: on the
    // single-pattern path the engine resets the channel buffer around the frame, which blacks
    // the cube for reasons that have nothing to do with this pattern honouring its view.
    jungle.loop(FIXED_DELTA_MS);
    assertTrue(
      litPoints(jungle, Apotheneum.cube.exterior) > 0,
      "the cube exterior was not lit on the full model, so narrowing would prove nothing"
    );

    jungle.view.setValue(cylinderExteriorView(lx));
    jungle.loop(FIXED_DELTA_MS);
    // Without this the test has a hole: a view that failed to resolve leaves the pattern
    // unmasked and the assertion below would be measuring something else entirely.
    assertTrue(
      jungle.getModelView().size < lx.getModel().size,
      "the pattern view did not actually narrow, so the assertion below proves nothing"
    );
    assertEquals(
      0,
      litPoints(jungle, Apotheneum.cube.exterior),
      "the cube exterior stayed lit after the view narrowed to the cylinder exterior, so "
        + "points the view dropped are frozen on the last frame that drew them"
    );
  }

  /**
   * Widening back to the whole installation writes every surface again, interiors included.
   *
   * This is the assertion that holds the interior write pass to what {@code copyExterior()}
   * used to do. The sentinel is what makes it meaningful: a doorway row is painted black on
   * purpose, so checking for black could not tell a written point from an untouched one.
   */
  private void assertWideningTheViewResumesFullOutput(LX lx, Jungle jungle) {
    jungle.view.setValue(cylinderExteriorView(lx));
    jungle.loop(FIXED_DELTA_MS);
    Arrays.fill(jungle.getColors(), SENTINEL);

    jungle.view.setValue(null);
    jungle.loop(FIXED_DELTA_MS);
    assertSame(
      lx.getModel(),
      jungle.getModelView(),
      "the pattern view did not widen back to the whole model"
    );
    for (Apotheneum.Component component :
      new Apotheneum.Component[] { Apotheneum.cube, Apotheneum.cylinder }) {
      for (Apotheneum.Orientation orientation : component.orientations()) {
        assertNoSentinelLeft(jungle, orientation);
      }
    }
    assertTrue(
      litPoints(jungle, Apotheneum.cube.interior) > 0,
      "the cube interior was written but left entirely black, so the interior pass is "
        + "reaching every point without actually drawing the scene on it"
    );
  }

  /**
   * With a view selected, nothing outside it is written at all.
   *
   * A sentinel prefill rather than a check for black, because black is exactly what this
   * pattern writes on a doorway row: black cannot tell "never touched" from "touched and
   * painted black", and reaching outside the view is the whole question.
   */
  private void assertViewOnlyWritesItsSelectedPoints(LX lx, Jungle jungle) {
    jungle.view.setValue(cylinderExteriorView(lx));
    lx.engine.run();
    // The mask is rebuilt only when getModelView() hands back a different instance, so that
    // early-return is free only if an unchanged view keeps returning the same one. Assert it
    // rather than assume it: were it to churn, the pattern would still be correct but would
    // allocate a mask every frame and flash black doing it.
    final LXModel resolvedView = jungle.getModelView();
    Arrays.fill(jungle.getColors(), SENTINEL);
    for (int frame = 0; frame < VIEW_FRAMES; ++frame) {
      jungle.loop(FIXED_DELTA_MS);
      assertSame(
        resolvedView,
        jungle.getModelView(),
        "getModelView() returned a new instance for an unchanged view on frame " + frame
          + ", so the mask is being rebuilt and the buffer blacked every frame"
      );
    }

    assertUnwritten(jungle, Apotheneum.cube.exterior, "cube exterior");
    assertUnwritten(jungle, Apotheneum.cube.interior, "cube interior");
    assertUnwritten(jungle, Apotheneum.cylinder.interior, "cylinder interior");

    int written = 0;
    final int[] colors = jungle.getColors();
    for (int x = 0; x < Apotheneum.cylinder.exterior.width(); ++x) {
      final int available = Apotheneum.cylinder.exterior.available(x);
      for (int y = 0; y < available; ++y) {
        if (colors[Apotheneum.cylinder.exterior.point(x, y).index] != SENTINEL) {
          ++written;
        }
      }
    }
    assertTrue(
      written > 0,
      "the cylinder exterior was never written even though the view selects it, so the "
        + "assertions above pass only because the pattern drew nothing anywhere"
    );
  }

  /** Points of a surface holding something other than black. */
  private int litPoints(Jungle jungle, Apotheneum.Orientation orientation) {
    final int[] colors = jungle.getColors();
    int lit = 0;
    for (int x = 0; x < orientation.width(); ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        if ((colors[orientation.point(x, y).index] & 0xffffff) != 0) {
          ++lit;
        }
      }
    }
    return lit;
  }

  /** No point of a surface still holds the sentinel, so all of them were written. */
  private void assertNoSentinelLeft(Jungle jungle, Apotheneum.Orientation orientation) {
    final int[] colors = jungle.getColors();
    for (int x = 0; x < orientation.width(); ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        assertNotEquals(
          SENTINEL,
          colors[orientation.point(x, y).index],
          "a point went unwritten after the view widened, at column " + x + " row " + y
        );
      }
    }
  }

  /** Every point of a surface still holds the sentinel, so the pattern never touched it. */
  private void assertUnwritten(
    Jungle jungle,
    Apotheneum.Orientation orientation,
    String name
  ) {
    final int[] colors = jungle.getColors();
    for (int x = 0; x < orientation.width(); ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        assertEquals(
          SENTINEL,
          colors[orientation.point(x, y).index],
          "the " + name + " was written while outside the pattern's view, at column " + x
            + " row " + y
        );
      }
    }
  }

  /** The cylinder-exterior view, created once and reused. */
  private LXViewDefinition cylinderExteriorView(LX lx) {
    if (this.cylinderExteriorView == null) {
      this.cylinderExteriorView = lx.structure.views.addView();
      this.cylinderExteriorView.selector.setValue("cylinderExterior");
    }
    return this.cylinderExteriorView;
  }

  /**
   * Loads the real installation fixture. Mirrors {@code RenderSpike}: output is disabled before
   * the fixture goes in and asserted to have stayed that way, because the fixture carries the
   * installation's real Art-Net addresses.
   */
  private LX newApotheneumLx() throws IOException {
    final Path mediaPath = Files.createTempDirectory("apotheneum-jungle-view-test-");
    // JsonFixture resolves through <mediaPath>/Fixtures/, capitalized; this repo stores them in
    // a lowercase directory, which only works on a case-insensitive filesystem.
    final Path destination = Files.createDirectories(mediaPath.resolve("Fixtures"));
    try (Stream<Path> sources = Files.list(SOURCE_FIXTURES)) {
      for (Path source : sources.filter(Files::isRegularFile).toList()) {
        Files.copy(
          source,
          destination.resolve(source.getFileName()),
          StandardCopyOption.REPLACE_EXISTING
        );
      }
    }

    // Deepest-last registration, and deleteOnExit unwinds LIFO, so children go before parents.
    try (Stream<Path> tree = Files.walk(mediaPath)) {
      tree.forEach(path -> path.toFile().deleteOnExit());
    }

    final LX.Flags flags = new LX.Flags();
    flags.loadPreferences = false;
    flags.mediaPath = mediaPath.toString();
    flags.outputMode = LX.Flags.OutputMode.INACTIVE;

    final LX lx = track(new LX(flags));
    lx.engine.output.enabled.setValue(false);

    final JsonFixture fixture = new JsonFixture(lx, FIXTURE_NAME);
    lx.structure.addFixture(fixture);
    // addFixture only stages regeneration; without this the model is not there yet.
    lx.structure.beforeEngineRun();
    assertFalse(fixture.error.isOn(), "fixture load failed: " + fixture.errorMessage.getString());

    Apotheneum.initialize(lx);
    assertTrue(Apotheneum.exists, "Apotheneum.exists was false after loading the real fixture");
    assertFalse(lx.engine.output.enabled.isOn(), "engine output became enabled");
    return lx;
  }
}
