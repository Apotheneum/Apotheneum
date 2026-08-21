package apotheneum.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

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
}
