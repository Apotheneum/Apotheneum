package apotheneum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.view.LXViewDefinition;

/**
 * Covers {@code ApotheneumPattern}'s pattern-level model view enforcement against the real
 * installation fixture, since the geometry helpers under test resolve the global
 * {@code Apotheneum.cube} / {@code Apotheneum.cylinder} statics and need {@code Apotheneum.exists}.
 *
 * <p>Output is forced inactive and disabled before the fixture is added — it carries real Art-Net
 * addresses for the installation.
 *
 * <p>One {@link LX} instance is shared by the whole class, torn down in {@code @AfterAll}. A second
 * instance in the same fork deadlocks on macOS: {@code LXAudioInput} takes the
 * {@code JSSecurityManager} class-initialization lock from the main thread while LX's MIDI device
 * initialization thread holds it, so construction never returns. Each test instead adds its own
 * channel and views, and {@code @AfterEach} removes them.
 */
public class ApotheneumPatternViewTest {

  private static final String FIXTURE_NAME = "Apotheneum";

  private static final Path SOURCE_FIXTURES =
    Path.of("src", "main", "resources", "fixtures");

  /** Paints the whole installation white through the global-writing base-class helper. */
  private static class FloodPattern extends ApotheneumPattern {
    private FloodPattern(LX lx) {
      super(lx);
    }

    @Override
    protected void render(double deltaMs) {
      setApotheneumColor(LXColor.WHITE);
    }
  }

  private static Path mediaPath;
  private static LX lx;

  private final List<LXChannel> channels = new ArrayList<LXChannel>();
  private final List<LXViewDefinition> views = new ArrayList<LXViewDefinition>();

  @BeforeAll
  static void loadApotheneum() throws IOException {
    mediaPath = Files.createTempDirectory("apotheneum-view-test-");
    final Path fixtures = Files.createDirectories(mediaPath.resolve("Fixtures"));
    try (Stream<Path> sources = Files.list(SOURCE_FIXTURES)) {
      for (Path source : sources.filter(Files::isRegularFile).toList()) {
        Files.copy(
          source,
          fixtures.resolve(source.getFileName()),
          StandardCopyOption.REPLACE_EXISTING
        );
      }
    }

    final LX.Flags flags = new LX.Flags();
    flags.loadPreferences = false;
    flags.mediaPath = mediaPath.toString();
    flags.outputMode = LX.Flags.OutputMode.INACTIVE;

    lx = new LX(flags);
    lx.engine.output.enabled.setValue(false);

    final JsonFixture fixture = new JsonFixture(lx, FIXTURE_NAME);
    lx.structure.addFixture(fixture);
    lx.structure.beforeEngineRun();
    assertFalse(fixture.error.isOn(), () -> "Fixture load failed: " + fixture.errorMessage.getString());
    assertFalse(lx.engine.output.enabled.isOn(), "LX engine output must stay disabled");

    Apotheneum.initialize(lx);
    assertTrue(Apotheneum.exists, "Apotheneum geometry must load from the real fixture");
    lx.engine.setFixedDeltaMs(1000 / 60.);
  }

  @AfterEach
  void clearMixer() {
    for (int i = this.views.size() - 1; i >= 0; --i) {
      lx.structure.views.removeView(this.views.get(i));
    }
    this.views.clear();
    for (int i = this.channels.size() - 1; i >= 0; --i) {
      lx.engine.mixer.removeChannel(this.channels.get(i));
    }
    this.channels.clear();
  }

  @AfterAll
  static void disposeLx() throws IOException {
    if (lx != null) {
      lx.dispose();
      lx = null;
    }
    if (mediaPath != null) {
      try (Stream<Path> paths = Files.walk(mediaPath)) {
        for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
          Files.deleteIfExists(path);
        }
      }
      mediaPath = null;
    }
  }

  @Test
  void defaultViewLeavesEveryGlobalWriteIntact() {
    final FloodPattern pattern = new FloodPattern(lx);
    addChannel(pattern);
    run(4);

    final int[] colors = pattern.getColors();
    for (LXPoint point : lx.getModel().points) {
      assertEquals(
        LXColor.WHITE,
        colors[point.index],
        "Point " + point.index + " must still be lit with no pattern-level view"
      );
    }
    // Built once, on the first frame, and never again: the no-view mask is empty.
    assertEquals(1, pattern.viewMaskBuildCount());
  }

  @Test
  void patternViewConstrainsGlobalWrites() {
    final FloodPattern pattern = new FloodPattern(lx);
    addChannel(pattern);
    pattern.view.setValue(newView("cylinderExterior"));
    run(4);

    final int[] colors = pattern.getColors();
    final LXModel viewModel = pattern.getModelView();
    assertTrue(viewModel.size > 0, "View must resolve to a non-empty model");
    assertTrue(viewModel.size < lx.getModel().size, "View must be a strict subset");

    final boolean[] inView = new boolean[colors.length];
    for (LXPoint point : viewModel.points) {
      inView[point.index] = true;
    }
    int lit = 0;
    for (LXPoint point : lx.getModel().points) {
      if (inView[point.index]) {
        assertEquals(LXColor.WHITE, colors[point.index], "In-view point " + point.index);
        ++lit;
      } else {
        assertEquals(LXColor.BLACK, colors[point.index], "Out-of-view point " + point.index);
      }
    }
    assertEquals(viewModel.size, lit);
  }

  @Test
  void patternViewMaskIsBuiltOnceNotPerFrame() {
    final FloodPattern pattern = new FloodPattern(lx);
    addChannel(pattern);
    pattern.view.setValue(newView("cylinderExterior"));

    run(1);
    final int afterFirstFrame = pattern.viewMaskBuildCount();
    run(30);
    assertEquals(
      afterFirstFrame,
      pattern.viewMaskBuildCount(),
      "The view mask must not be rebuilt while the view and model are unchanged"
    );

    // Changing the view is the only thing that rebuilds it.
    pattern.view.setValue(newView("cubeExterior"));
    run(1);
    assertEquals(afterFirstFrame + 1, pattern.viewMaskBuildCount());
  }

  @Test
  void channelViewIsLeftToTheMixer() {
    final FloodPattern pattern = new FloodPattern(lx);
    final LXChannel channel = addChannel(pattern);
    // A channel view is a hard boundary at the mixer, and patterns that project onto
    // third-party geometry rely on inheriting it as an input model. The pattern must keep
    // writing globally so that behavior is unchanged.
    channel.view.setValue(newView("cylinderExterior"));
    run(4);

    assertEquals(1, pattern.viewMaskBuildCount(), "A channel view must resolve to an empty mask");
    final int[] colors = pattern.getColors();
    int lit = 0;
    for (LXPoint point : Apotheneum.cube.exterior.front.model.points) {
      if (colors[point.index] == LXColor.WHITE) {
        ++lit;
      }
    }
    assertEquals(
      Apotheneum.cube.exterior.front.model.size,
      lit,
      "The pattern must still write outside a channel view; masking there is the mixer's job"
    );
  }

  /**
   * {@code afterLayers} is final on {@code ApotheneumPattern}; the five patterns that used to
   * override it now override {@code afterRenderLayers}. This proves the replacement hook still
   * runs at the same point — after {@code render}, before the view mask.
   */
  @Test
  void afterRenderLayersStillRunsAfterRender() {
    final PostCopyPattern pattern = new PostCopyPattern(lx);
    addChannel(pattern);
    run(2);

    assertTrue(pattern.afterRenderLayersCount > 0, "afterRenderLayers must be invoked");
    final int[] colors = pattern.getColors();
    final Apotheneum.Cube.Face exterior = Apotheneum.cube.exterior.front;
    final Apotheneum.Cube.Face interior = Apotheneum.cube.interior.front;
    for (int i = 0; i < exterior.model.size; ++i) {
      assertEquals(
        colors[exterior.model.points[i].index],
        colors[interior.model.points[i].index],
        "Interior must mirror exterior via the post-render copy"
      );
    }
    assertEquals(LXColor.WHITE, colors[interior.model.points[0].index]);
  }

  /** Lights only the cube exterior, then mirrors it inward from the post-render hook. */
  private static class PostCopyPattern extends ApotheneumPattern {
    private int afterRenderLayersCount = 0;

    private PostCopyPattern(LX lx) {
      super(lx);
    }

    @Override
    protected void render(double deltaMs) {
      setColor(Apotheneum.cube.exterior, LXColor.WHITE);
    }

    @Override
    protected void afterRenderLayers(double deltaMs) {
      ++this.afterRenderLayersCount;
      copyCubeExterior();
    }
  }

  private LXChannel addChannel(LXPattern pattern) {
    final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { pattern });
    this.channels.add(channel);
    return channel;
  }

  private LXViewDefinition newView(String selector) {
    final LXViewDefinition view = lx.structure.views.addView();
    view.selector.setValue(selector);
    view.enabled.setValue(true);
    this.views.add(view);
    return view;
  }

  private void run(int frames) {
    for (int i = 0; i < frames; ++i) {
      lx.engine.run();
    }
  }

}
