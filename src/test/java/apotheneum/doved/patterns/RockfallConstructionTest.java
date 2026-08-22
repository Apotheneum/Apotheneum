package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;

public class RockfallConstructionTest extends HeadlessLxTest {

  @Test
  void constructsWithoutApotheneumGeometry() {
    final LX lx = newHeadlessLx();
    assertFalse(Apotheneum.exists);
    final Rockfall rockfall = assertDoesNotThrow(() -> new Rockfall(lx));
    rockfall.dispose();
  }
}
