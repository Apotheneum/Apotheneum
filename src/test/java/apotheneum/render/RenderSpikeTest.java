package apotheneum.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class RenderSpikeTest {

  @Test
  void paletteComponentsMustBeFiniteAndInRange() {
    assertEquals(192, RenderSpike.parsePaletteComponent("192,95,75", "192", 360));
    assertThrows(
      IllegalArgumentException.class,
      () -> RenderSpike.parsePaletteComponent("NaN,95,75", "NaN", 360)
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> RenderSpike.parsePaletteComponent("Infinity,95,75", "Infinity", 360)
    );
  }
}
