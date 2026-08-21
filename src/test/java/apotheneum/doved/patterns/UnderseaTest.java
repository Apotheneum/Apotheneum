package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnderseaTest {

  private static final double EPSILON = 1e-9;

  @Test
  void levelRemainsFullySubmergedUntilTheTransitionRange() {
    assertEquals(1, Undersea.waterLevel(0), EPSILON);
    assertEquals(1, Undersea.waterLevel(.5), EPSILON);
    assertEquals(1, Undersea.waterLevel(Undersea.VISIBLE_WATERLINE_START), EPSILON);
    assertTrue(Undersea.waterLevel(.91) < 1);
    assertTrue(Undersea.waterLevel(.91) > Undersea.LOWEST_WATER_LEVEL);
    assertEquals(Undersea.LOWEST_WATER_LEVEL, Undersea.waterLevel(1), EPSILON);
  }

  @Test
  void impliedDepthUsesTheSubmergedRangeAndThenHolds() {
    assertEquals(0, Undersea.impliedDepth(0), EPSILON);
    assertTrue(Undersea.impliedDepth(.4) > 0);
    assertTrue(Undersea.impliedDepth(.4) < 1);
    assertEquals(1, Undersea.impliedDepth(Undersea.VISIBLE_WATERLINE_START), EPSILON);
    assertEquals(1, Undersea.impliedDepth(1), EPSILON);
  }

  @Test
  void causticRidgeHasASoftMonotonicEdge() {
    assertEquals(1, Undersea.ridgeBand(0), EPSILON);
    assertEquals(1, Undersea.ridgeBand(.045), EPSILON);
    assertTrue(Undersea.ridgeBand(.12) > Undersea.ridgeBand(.2));
    assertTrue(Undersea.ridgeBand(.2) > 0);
    assertEquals(0, Undersea.ridgeBand(.27), EPSILON);
  }

  @Test
  void mergerFlashRequiresTemporalCollapseNearTheRidge() {
    assertEquals(0, Undersea.mergerFlash(.2, .2), EPSILON);
    assertEquals(0, Undersea.mergerFlash(.18, .2), EPSILON);
    assertTrue(Undersea.mergerFlash(.2, .19) > 0);
    assertEquals(0, Undersea.mergerFlash(.6, .5), EPSILON);
  }

  @Test
  void shadowMotionHasAHesitationBetweenBursts() {
    double previous = Undersea.shadowProgress(0);
    for (int i = 1; i <= 100; ++i) {
      final double progress = Undersea.shadowProgress(i / 100.0);
      assertTrue(progress >= previous, "shadow progress must never reverse");
      previous = progress;
    }
    assertEquals(0, Undersea.shadowProgress(0), EPSILON);
    assertEquals(1, Undersea.shadowProgress(1), EPSILON);

    final double hesitationTravel = Undersea.shadowProgress(.6) - Undersea.shadowProgress(.5);
    final double burstTravel = Undersea.shadowProgress(.72) - Undersea.shadowProgress(.62);
    assertTrue(hesitationTravel < burstTravel);
  }

  @Test
  void shadowFootprintWrapsAndTapersBehindItsHead() {
    final double centerS = .99;
    final double centerRow = 13;
    final double body = Undersea.shadowMask(.99, centerRow, centerS, centerRow, 200);
    final double acrossSeam = Undersea.shadowMask(.002, centerRow, centerS, centerRow, 200);
    final double distant = Undersea.shadowMask(.5, centerRow, centerS, centerRow, 200);
    final double tail = Undersea.shadowMask(.965, centerRow, centerS, centerRow, 200);

    assertTrue(body > 0);
    assertTrue(acrossSeam > 0);
    assertEquals(0, distant, EPSILON);
    assertTrue(tail < body);
  }

  @Test
  void circularDistanceUsesTheShortWayAcrossTheSeam() {
    assertEquals(.02, Undersea.circularDistance(.99, .01), EPSILON);
    assertEquals(.25, Undersea.circularDistance(.25, .5), EPSILON);
  }
}
