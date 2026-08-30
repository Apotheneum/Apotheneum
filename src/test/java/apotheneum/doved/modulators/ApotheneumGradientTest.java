package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

/**
 * Unit-level coverage of {@link ApotheneumGradient}'s pure direction/projection math -- the
 * wiring-level proof that this actually removes the old raster's seam lives in {@code
 * GradientMultiplyEffectWiringTest.wrappingAroundTheCubeExteriorProducesNoSeamAtAHorizontalDirection},
 * against the real fixture. This class instead pins down the arithmetic itself with plain
 * geometry, so a future change to the projection formula gets a fast, precise failure at the
 * formula rather than only a fuzzy "some pixel changed" from the wiring test.
 */
public class ApotheneumGradientTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;

  @Test
  void directionStraightUpIsPureY() {
    assertEquals(0, ApotheneumGradient.directionX(0, 90), EPSILON);
    assertEquals(1, ApotheneumGradient.directionY(90), EPSILON);
    assertEquals(0, ApotheneumGradient.directionZ(0, 90), EPSILON);

    // Azimuth is meaningless once elevation points straight up -- every azimuth gives the
    // same vertical direction.
    assertEquals(0, ApotheneumGradient.directionX(180, 90), EPSILON);
    assertEquals(1, ApotheneumGradient.directionY(90), EPSILON);
    assertEquals(0, ApotheneumGradient.directionZ(180, 90), EPSILON);
  }

  @Test
  void directionStraightDownIsNegativeY() {
    assertEquals(-1, ApotheneumGradient.directionY(-90), EPSILON);
  }

  @Test
  void directionAtZeroElevationIsHorizontal() {
    // Azimuth 0: due +Z, no X or Y component.
    assertEquals(0, ApotheneumGradient.directionX(0, 0), EPSILON);
    assertEquals(0, ApotheneumGradient.directionY(0), EPSILON);
    assertEquals(1, ApotheneumGradient.directionZ(0, 0), EPSILON);

    // Azimuth 90: due +X, no Y or Z component.
    assertEquals(1, ApotheneumGradient.directionX(90, 0), EPSILON);
    assertEquals(0, ApotheneumGradient.directionY(0), EPSILON);
    assertEquals(0, ApotheneumGradient.directionZ(90, 0), EPSILON);
  }

  @Test
  void directionIsUnitLength() {
    for (double azimuth = 0; azimuth < 360; azimuth += 37) {
      for (double elevation = -90; elevation <= 90; elevation += 23) {
        final double dx = ApotheneumGradient.directionX(azimuth, elevation);
        final double dy = ApotheneumGradient.directionY(elevation);
        final double dz = ApotheneumGradient.directionZ(azimuth, elevation);
        final double lengthSquared = dx * dx + dy * dy + dz * dz;
        assertEquals(1, lengthSquared, 1e-6,
          "azimuth=" + azimuth + " elevation=" + elevation);
      }
    }
  }

  @Test
  void projectedExtentMatchesTheBoundingBoxAlongASingleAxis() {
    final LXModel model = boxModel(-10, 10, -5, 5, -20, 20);
    // Direction (1, 0, 0): the box's own X extent, exactly.
    assertEquals(-10, ApotheneumGradient.projectedMin(model, 1, 0, 0), EPSILON);
    assertEquals(10, ApotheneumGradient.projectedMax(model, 1, 0, 0), EPSILON);
    // A negated direction flips which corner is the minimum.
    assertEquals(-10, ApotheneumGradient.projectedMin(model, -1, 0, 0), EPSILON);
    assertEquals(10, ApotheneumGradient.projectedMax(model, -1, 0, 0), EPSILON);
  }

  @Test
  void projectedExtentOfADiagonalDirectionSumsAllThreeAxes() {
    final LXModel model = boxModel(0, 1, 0, 1, 0, 1);
    final double component = 1 / Math.sqrt(3);
    assertEquals(0, ApotheneumGradient.projectedMin(model, component, component, component), EPSILON);
    assertEquals(3 * component, ApotheneumGradient.projectedMax(model, component, component, component), EPSILON);
  }

  @Test
  void adjacentPointsProjectToNearlyEqualPositionsRegardlessOfIndex() {
    // The whole point of projecting real-world coordinates instead of a 2D raster index:
    // two points close together in space project close together, no matter how far apart
    // their column indices happen to be (the old raster bug's exact failure mode).
    final LXModel model = boxModel(-100, 100, -100, 100, -100, 100);
    final LXPoint near1 = new LXPoint(0, 0, 0);
    final LXPoint near2 = new LXPoint(0.01, 0, 0);
    final LXPoint far = new LXPoint(99, 0, 0);

    final double min = ApotheneumGradient.projectedMin(model, 1, 0, 0);
    final double max = ApotheneumGradient.projectedMax(model, 1, 0, 0);
    final double tNear1 = ApotheneumGradient.normalize(ApotheneumGradient.project(near1, 1, 0, 0), min, max);
    final double tNear2 = ApotheneumGradient.normalize(ApotheneumGradient.project(near2, 1, 0, 0), min, max);
    final double tFar = ApotheneumGradient.normalize(ApotheneumGradient.project(far, 1, 0, 0), min, max);

    assertTrue(Math.abs(tNear1 - tNear2) < 0.001, "physically adjacent points should project close together");
    assertTrue(Math.abs(tNear1 - tFar) > 0.4, "physically distant points should project far apart");
  }

  @Test
  void normalizeClampsOutOfRangeProjections() {
    assertEquals(0, ApotheneumGradient.normalize(-5, 0, 10), EPSILON);
    assertEquals(1, ApotheneumGradient.normalize(15, 0, 10), EPSILON);
    assertEquals(0.5, ApotheneumGradient.normalize(5, 0, 10), EPSILON);
  }

  @Test
  void normalizeResolvesToMidpointOnADegenerateExtent() {
    // A direction perpendicular to a flat model (e.g. a single-point or planar model along
    // that axis) has zero span; the old per-surface raster path picked the midpoint for the
    // equivalent single-column/row case rather than dividing by zero.
    assertEquals(0.5, ApotheneumGradient.normalize(3, 5, 5), EPSILON);
  }

  @Test
  void withNoInstanceRegisteredResolversFallBackToStraightUp() {
    final LX lx = newHeadlessLx();
    assertNull(ApotheneumGradient.get(lx));
    assertEquals(0, ApotheneumGradient.azimuthOrDefault(null), EPSILON);
    assertEquals(90, ApotheneumGradient.elevationOrDefault(null), EPSILON);
  }

  @Test
  void resolversReadTheRegisteredInstancesLiveValue() {
    final LX lx = newHeadlessLx();
    final ApotheneumGradient gradient = new ApotheneumGradient(lx);
    lx.engine.registerComponent(ApotheneumGradient.PATH, gradient);
    gradient.azimuth.setValue(123);
    gradient.elevation.setValue(-45);

    assertEquals(gradient, ApotheneumGradient.get(lx));
    assertEquals(123, ApotheneumGradient.azimuthOrDefault(gradient), EPSILON);
    assertEquals(-45, ApotheneumGradient.elevationOrDefault(gradient), EPSILON);
  }

  private static LXModel boxModel(
    double xMin, double xMax, double yMin, double yMax, double zMin, double zMax
  ) {
    return new LXModel(List.of(
      new LXPoint(xMin, yMin, zMin),
      new LXPoint(xMax, yMax, zMax)
    ));
  }
}
