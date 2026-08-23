package apotheneum.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import apotheneum.doved.patterns.Flood;
import org.junit.jupiter.api.Test;

class RenderSpikeTest {

  @Test
  void cropWrapsAcrossRingSeam() {
    final RenderSpike.ColumnCrop crop = new RenderSpike.ColumnCrop(190, 60);

    assertEquals(190, crop.sourceColumn(0, 200));
    assertEquals(199, crop.sourceColumn(9, 200));
    assertEquals(0, crop.sourceColumn(10, 200));
    assertEquals(49, crop.sourceColumn(59, 200));
  }

  @Test
  void cropNormalizesStartForEachSurfaceRing() {
    final RenderSpike.ColumnCrop crop = new RenderSpike.ColumnCrop(190, 60);

    assertEquals(190, crop.normalizedStart(200));
    assertEquals(70, crop.normalizedStart(120));
    assertEquals(70, crop.sourceColumn(0, 120));
    assertEquals(9, crop.sourceColumn(59, 120));
  }

  @Test
  void cropRejectsInvalidWindows() {
    final RenderSpike.ColumnCrop crop = new RenderSpike.ColumnCrop(0, 121);

    assertThrows(IllegalArgumentException.class, () -> crop.sourceColumn(0, 120));
    assertThrows(IllegalArgumentException.class, () -> new RenderSpike.ColumnCrop(-1, 60));
    assertThrows(IllegalArgumentException.class, () -> new RenderSpike.ColumnCrop(0, 0));
  }

  @Test
  void animatedOptionsMustPreserveRealTimeMotion() {
    assertEquals(
      30,
      new RenderSpike.AnimatedOptions(Path.of("target/test"), 2, 30).gifFrameRate()
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new RenderSpike.AnimatedOptions(Path.of("target/test"), 2, 20)
    );
  }

  @Test
  void compositeClipPreservesDeclaredLayerOrderAndLevels() {
    final RenderSpike.CompositeLayer<Flood> first = new RenderSpike.CompositeLayer<>(
      "first",
      Flood::new,
      .75,
      (pattern, frame, progress, seconds) -> { }
    );
    final RenderSpike.CompositeLayer<Flood> second = new RenderSpike.CompositeLayer<>(
      "second",
      Flood::new,
      .5,
      (pattern, frame, progress, seconds) -> { }
    );

    final RenderSpike.CompositeClip clip = new RenderSpike.CompositeClip(
      "ordered",
      "Lightest",
      5,
      List.of(first, second)
    );

    assertEquals(List.of(first, second), clip.layers());
    assertEquals(.75, clip.layers().get(0).compositeLevel());
    assertEquals(.5, clip.layers().get(1).compositeLevel());
  }

  @Test
  void compositeConfigurationRejectsAmbiguousOrInvalidValues() {
    final RenderSpike.CompositeLayer<Flood> layer = new RenderSpike.CompositeLayer<>(
      "flood",
      Flood::new,
      1,
      (pattern, frame, progress, seconds) -> { }
    );

    assertThrows(
      IllegalArgumentException.class,
      () -> new RenderSpike.CompositeLayer<>(
        "bad-level",
        Flood::new,
        1.01,
        (pattern, frame, progress, seconds) -> { }
      )
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new RenderSpike.CompositeClip("no-blend", " ", 5, List.of(layer))
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new RenderSpike.CompositeClip("duplicate", "Add", 5, List.of(layer, layer))
    );
  }
}
