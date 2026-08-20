package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.ModelBuffer;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.studio.ui.device.UIDeviceControls;

public class RaybeamTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;
  private static final double RADIUS = .25;
  private static final double WIDTH = .1;
  private static final double CONE_ANGLE = Math.PI / 4;
  private static final double CONE_SIN = Math.sin(CONE_ANGLE);
  private static final double CONE_COS = Math.cos(CONE_ANGLE);

  @Test
  void everyShapeIsFullOnItsSurfaceAndDarkAtWidth() {
    assertSurface(Raybeam.Shape.POINT,
      0, 0, 0, WIDTH, 0, 0);
    assertSurface(Raybeam.Shape.LINE,
      0, .4, 0, WIDTH, .4, 0);
    assertSurface(Raybeam.Shape.RAY,
      0, .4, 0, WIDTH, .4, 0);
    assertSurface(Raybeam.Shape.PLANE,
      .3, 0, .2, .3, WIDTH, .2);
    assertSurface(Raybeam.Shape.CYLINDER,
      RADIUS, .4, 0, RADIUS + WIDTH, .4, 0);
    assertSurface(Raybeam.Shape.SPHERE,
      RADIUS, 0, 0, RADIUS + WIDTH, 0, 0);

    final double coneX = CONE_SIN;
    final double coneY = CONE_COS;
    final double darkConeX = CONE_SIN + WIDTH * CONE_COS;
    final double darkConeY = CONE_COS - WIDTH * CONE_SIN;
    assertSurface(Raybeam.Shape.CONE,
      coneX, coneY, 0, darkConeX, darkConeY, 0);
  }

  private static void assertSurface(Raybeam.Shape shape,
    double onX, double onY, double onZ, double darkX, double darkY, double darkZ) {

    final double onDistance =
      shape.distance(onX, onY, onZ, RADIUS, CONE_SIN, CONE_COS);
    final double darkDistance =
      shape.distance(darkX, darkY, darkZ, RADIUS, CONE_SIN, CONE_COS);
    assertEquals(1,
      Raybeam.Falloff.LINEAR.brightness(onDistance, WIDTH, 1), EPSILON,
      shape + " should be full brightness on its surface");
    assertEquals(0,
      Raybeam.Falloff.LINEAR.brightness(darkDistance, WIDTH, 1), EPSILON,
      shape + " should be dark one width away");
  }

  @Test
  void rayIsCappedBehindItsOriginWhileLineContinues() {
    assertEquals(0,
      Raybeam.Shape.LINE.distance(0, -.4, 0, RADIUS, CONE_SIN, CONE_COS), EPSILON);
    assertEquals(.4,
      Raybeam.Shape.RAY.distance(0, -.4, 0, RADIUS, CONE_SIN, CONE_COS), EPSILON);
  }

  @Test
  void cylinderAndSphereFillTheirInteriors() {
    assertEquals(0,
      Raybeam.Shape.CYLINDER.distance(0, .4, 0, RADIUS, CONE_SIN, CONE_COS),
      EPSILON);
    assertEquals(0,
      Raybeam.Shape.CYLINDER.distance(RADIUS / 2, -.4, 0, RADIUS, CONE_SIN, CONE_COS),
      EPSILON);
    assertEquals(0,
      Raybeam.Shape.SPHERE.distance(0, 0, 0, RADIUS, CONE_SIN, CONE_COS),
      EPSILON);
    assertEquals(0,
      Raybeam.Shape.SPHERE.distance(RADIUS / 2, 0, 0, RADIUS, CONE_SIN, CONE_COS),
      EPSILON);
  }

  @Test
  void coneFillsItsInteriorAndKeepsItsApex() {
    final double angle = .6;
    final double coneSin = Math.sin(angle);
    final double coneCos = Math.cos(angle);
    assertEquals(0,
      Raybeam.Shape.CONE.distance(
        coneSin, coneCos, 0, RADIUS, coneSin, coneCos), EPSILON);
    assertEquals(0,
      Raybeam.Shape.CONE.distance(0, 1, 0, RADIUS, coneSin, coneCos), EPSILON);
    assertEquals(0,
      Raybeam.Shape.CONE.distance(
        coneSin / 2, coneCos, 0, RADIUS, coneSin, coneCos), EPSILON);
    assertEquals(0,
      Raybeam.Shape.CONE.distance(0, 0, 0, RADIUS, coneSin, coneCos), EPSILON);
    assertEquals(.25,
      Raybeam.Shape.CONE.distance(0, -.25, 0, RADIUS, coneSin, coneCos), EPSILON);
  }

  @Test
  void coneUsesConstantLinearThicknessAlongItsSurface() {
    assertConeNormalDistance(.2, WIDTH);
    assertConeNormalDistance(.8, WIDTH);
  }

  @Test
  void coneIsRotationallySymmetricAroundItsAxis() {
    final double radial = .5;
    final double axial = .4;
    final double expected = Raybeam.Shape.CONE.distance(
      radial, axial, 0, RADIUS, CONE_SIN, CONE_COS);
    assertEquals(expected,
      Raybeam.Shape.CONE.distance(.3, axial, .4, RADIUS, CONE_SIN, CONE_COS),
      EPSILON);
  }

  private static void assertConeNormalDistance(double alongSurface, double normalDistance) {
    final double radial = alongSurface * CONE_SIN + normalDistance * CONE_COS;
    final double axial = alongSurface * CONE_COS - normalDistance * CONE_SIN;
    assertEquals(normalDistance,
      Raybeam.Shape.CONE.distance(
        radial, axial, 0, RADIUS, CONE_SIN, CONE_COS), EPSILON);
  }

  @Test
  void coneLimitsMatchRayAndHalfSpace() {
    final double x = .2;
    final double y = .4;
    final double z = .1;
    assertEquals(
      Raybeam.Shape.RAY.distance(x, y, z, RADIUS, 0, 1),
      Raybeam.Shape.CONE.distance(x, y, z, RADIUS, 0, 1), EPSILON);
    assertEquals(0,
      Raybeam.Shape.CONE.distance(x, y, z, RADIUS, 1, 0), EPSILON);
    assertEquals(y,
      Raybeam.Shape.CONE.distance(x, -y, z, RADIUS, 1, 0), EPSILON);
  }

  @Test
  void shapeSpecificControlsRemainRelevantOnlyForTheirShapes() {
    assertTrue(Raybeam.Shape.CYLINDER.usesRadius());
    assertTrue(Raybeam.Shape.SPHERE.usesRadius());
    assertTrue(Raybeam.Shape.CONE.usesConeAngle());

    for (Raybeam.Shape shape : Raybeam.Shape.values()) {
      assertEquals(
        (shape == Raybeam.Shape.CYLINDER) || (shape == Raybeam.Shape.SPHERE),
        shape.usesRadius());
      assertEquals(shape == Raybeam.Shape.CONE, shape.usesConeAngle());
    }
  }

  @Test
  void azimuthQuarterTurnSelectsAPerpendicularWorldDirection() {
    final Raybeam.LocalFrame frame =
      new Raybeam.LocalFrame();

    frame.update(0, 0, 0, 0, 0, 1, 1, 1);
    assertEquals(1, frame.localY(0, 0, 1), EPSILON);
    assertEquals(0, frame.localY(1, 0, 0), EPSILON);

    frame.update(0, 0, 0, Math.PI / 2, 0, 1, 1, 1);
    assertEquals(0, frame.localY(0, 0, 1), EPSILON);
    assertEquals(1, frame.localY(1, 0, 0), EPSILON);
  }

  @Test
  void identityFramePlaneMatchesOrboxYAxisDistance() {
    final Raybeam.LocalFrame frame =
      new Raybeam.LocalFrame();
    // Azimuth 0 and elevation +pi/2 align local XYZ exactly with world XYZ.
    frame.update(.5, .5, .5, 0, Math.PI / 2, 1, 1, 1);

    final double localY = frame.localY(.2, .8, .7);
    final double planeDistance =
      Raybeam.Shape.PLANE.distance(
        frame.localX(.2, .8, .7), localY, frame.localZ(.2, .8, .7),
        0, CONE_SIN, CONE_COS);

    // Orbox Y is abs(y) - radius before its outer abs; at radius zero this is abs(y).
    assertEquals(Math.abs(.8 - .5), planeDistance, EPSILON);
  }

  @Test
  void scaleIsAppliedInLocalSpace() {
    final Raybeam.LocalFrame frame =
      new Raybeam.LocalFrame();
    frame.update(0, 0, 0, 0, Math.PI / 2, 2, 4, .5);

    assertEquals(.5, frame.localX(1, 0, 0), EPSILON);
    assertEquals(.25, frame.localY(0, 1, 0), EPSILON);
    assertEquals(2, frame.localZ(0, 0, 1), EPSILON);
  }

  @Test
  void allFalloffsReachTheirExactEndpoints() {
    for (Raybeam.Falloff falloff : Raybeam.Falloff.values()) {
      assertEquals(1, falloff.brightness(0, WIDTH, 1), EPSILON);
      assertTrue(falloff.brightness(WIDTH / 2, WIDTH, 1) > 0);
      assertTrue(falloff.brightness(WIDTH / 2, WIDTH, 1) < 1);
      assertEquals(0, falloff.brightness(WIDTH, WIDTH, 1), EPSILON);
    }
  }

  @Test
  void softnessControlsTheFractionOfWidthUsedForTheFeather() {
    assertEquals(1,
      Raybeam.Falloff.LINEAR.brightness(WIDTH * .75, WIDTH, 0), EPSILON);
    assertEquals(1,
      Raybeam.Falloff.LINEAR.brightness(WIDTH * .5, WIDTH, .5), EPSILON);
    assertEquals(.5,
      Raybeam.Falloff.LINEAR.brightness(WIDTH * .75, WIDTH, .5), EPSILON);
    assertEquals(0,
      Raybeam.Falloff.LINEAR.brightness(WIDTH, WIDTH, .5), EPSILON);
  }

  @Test
  void curvedFalloffsUseNormalizedExponentialProfiles() {
    final double edge = Math.exp(-4.5);
    assertEquals(
      (Math.exp(-2.25) - edge) / (1 - edge),
      Raybeam.Falloff.EXPONENTIAL.brightness(WIDTH / 2, WIDTH, 1),
      EPSILON);
    assertEquals(
      (Math.exp(-1.125) - edge) / (1 - edge),
      Raybeam.Falloff.GAUSSIAN.brightness(WIDTH / 2, WIDTH, 1),
      EPSILON);
  }

  @Test
  void zeroRippleAmountPreservesTheDistanceFieldEnvelope() {
    assertEquals(.63,
      Raybeam.Ripple.brightness(.63, .17, 0, .1, .25, 2, 3),
      EPSILON);
  }

  @Test
  void increasingPhaseMovesRippleCrestsAwayFromTheShape() {
    final double spacing = .2;
    final double phase = .25;
    final double atOrigin = Raybeam.Ripple.brightness(
      .5, 0, .5, spacing, phase, 1, 1);
    final double atMovingCrest = Raybeam.Ripple.brightness(
      .5, spacing * phase, .5, spacing, phase, 1, 1);

    assertEquals(.5, atOrigin, EPSILON);
    assertEquals(.75, atMovingCrest, EPSILON);
  }

  @Test
  void rippleAmplitudeFadesWithTheDistanceFieldEnvelope() {
    final double inner = Raybeam.Ripple.brightness(
      .8, .1, .5, .2, 0, 1, 1);
    final double outer = Raybeam.Ripple.brightness(
      .2, .1, .5, .2, 0, 1, 1);

    assertEquals(.4, .8 - inner, EPSILON);
    assertEquals(.1, .2 - outer, EPSILON);
  }

  @Test
  void rippleSharpnessNarrowsIntermediateWaveValues() {
    final double broad = Raybeam.Ripple.brightness(
      .5, 1.0 / 6, 1, 1, 0, 1, 1);
    final double narrow = Raybeam.Ripple.brightness(
      .5, 1.0 / 6, 1, 1, 0, 2, 1);

    assertEquals(.75, broad, EPSILON);
    assertEquals(.625, narrow, EPSILON);
  }

  @Test
  void rippleCannotLightPixelsOutsideTheFieldBoundary() {
    assertEquals(0,
      Raybeam.Ripple.brightness(0, .4, 1, .1, 0, 1, .1),
      EPSILON);
  }

  @Test
  void patternProvidesCustomDeviceControls() {
    assertTrue(UIDeviceControls.class.isAssignableFrom(Raybeam.class));
  }

  @Test
  void renderUsesTheSelectedModelViewAndClearsEverythingElse() {
    final LX lx = newHeadlessLx();
    final Raybeam pattern = new Raybeam(lx);
    final ModelBuffer buffer = new ModelBuffer(lx);
    try {
      final LXModel selectedView = lx.getModel().children[0];
      final LXPoint selected = selectedView.points[0];
      final LXPoint excluded = lx.getModel().children[1].points[0];

      pattern.setBuffer(buffer);
      pattern.originX.setValue(0);
      pattern.originY.setValue(0);
      pattern.originZ.setValue(0);
      pattern.shape.setValue(Raybeam.Shape.POINT);
      pattern.width.setValue(.1);
      pattern.setModel(selectedView);
      pattern.loop(0);

      assertSame(selectedView, pattern.getModel());
      assertEquals(LXColor.WHITE, buffer.getArray()[selected.index]);
      assertEquals(0, buffer.getArray()[excluded.index]);
    } finally {
      pattern.dispose();
      buffer.dispose();
    }
  }

  @Test
  void everyNumericControlIsARegisteredCompoundParameter() {
    final LX lx = newHeadlessLx();
    final Raybeam pattern = new Raybeam(lx);
    try {
      final String[] paths = {
        "originX", "originY", "originZ", "azimuth", "elevation", "width", "radius",
        "softness", "coneAngle", "rippleAmount", "rippleSpacing", "ripplePhase",
        "rippleSharpness", "rippleDecay", "scaleX", "scaleY", "scaleZ"
      };
      for (String path : paths) {
        assertTrue(pattern.getParameter(path) instanceof CompoundParameter,
          path + " must remain a modulatable CompoundParameter");
      }
    } finally {
      pattern.dispose();
    }
  }

  @Override
  protected LXModel newModel() {
    final LXModel selected = new LXModel(List.of(new LXPoint(0, 0, 0)), "selected");
    final LXModel excluded = new LXModel(List.of(new LXPoint(1, 1, 1)), "excluded");
    return new LXModel(new LXModel[] { selected, excluded });
  }
}
