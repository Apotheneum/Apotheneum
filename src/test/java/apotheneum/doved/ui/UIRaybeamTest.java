package apotheneum.doved.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class UIRaybeamTest {

  private static final double EPSILON = 1e-9;

  @Test
  void rayStopsAtTheSelectedViewsNormalizedBoundary() {
    assertEquals(.5,
      UIRaybeam.rayExitDistance(.5, .5, .5, 1, 0, 0), EPSILON);
    assertEquals(.75,
      UIRaybeam.rayExitDistance(.75, .5, .5, -1, 0, 0), EPSILON);

    final double diagonal = 1 / Math.sqrt(3);
    assertEquals(Math.sqrt(3) / 2,
      UIRaybeam.rayExitDistance(
        .5, .5, .5, diagonal, diagonal, diagonal), EPSILON);
  }
}
