package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.studio.ui.device.UIDeviceControls;

public class SkyspaceTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;

  @Test
  void inheritsRaybeamControlsAndOrientationImplementation() {
    assertTrue(Raybeam.class.isAssignableFrom(Skyspace.class));
    assertTrue(UIDeviceControls.class.isAssignableFrom(Skyspace.class));

    final LX lx = newHeadlessLx();
    final Skyspace pattern = new Skyspace(lx);
    try {
      assertSame(pattern.originX, pattern.getParameter("originX"));
      assertSame(pattern.originY, pattern.getParameter("originY"));
      assertSame(pattern.originZ, pattern.getParameter("originZ"));
      assertSame(pattern.azimuth, pattern.getParameter("azimuth"));
      assertSame(pattern.elevation, pattern.getParameter("elevation"));
      assertSame(pattern.azimuthOffset, pattern.getParameter("roll"));
      assertSame(pattern.shape, pattern.getParameter("shape"));
      assertSame(pattern.showRay, pattern.getParameter("showRay"));
      assertSame(pattern.scaleX, pattern.getParameter("scaleX"));
      assertSame(pattern.scaleY, pattern.getParameter("scaleY"));
      assertSame(pattern.scaleZ, pattern.getParameter("scaleZ"));
    } finally {
      pattern.dispose();
    }
  }

  @Test
  void rippleLabelsUseTheirColumnContext() {
    final LX lx = newHeadlessLx();
    final Skyspace pattern = new Skyspace(lx);
    try {
      assertEquals("Amount", pattern.rippleAmount.getLabel());
      assertEquals("Spacing", pattern.rippleSpacing.getLabel());
      assertEquals("Phase", pattern.ripplePhase.getLabel());
      assertEquals("Sharp", pattern.rippleSharpness.getLabel());
      assertEquals("Decay", pattern.rippleDecay.getLabel());
    } finally {
      pattern.dispose();
    }
  }

  @Test
  void sphereStartsAsDarkNegativeSpaceWithGradientOutside() {
    final LX lx = newHeadlessLx();
    final Skyspace pattern = new Skyspace(lx);
    try {
      pattern.rippleAmount.setValue(0);
      final double radius = pattern.radius.getValue();
      final double width = pattern.width.getValue();
      final Raybeam.Shape shape = pattern.shape.getEnum();

      assertEquals(Raybeam.Shape.SPHERE, shape);
      assertEquals(0, brightness(pattern, shape, 0, 0, 0, radius, width), EPSILON);
      assertEquals(1, brightness(pattern, shape, radius, 0, 0, radius, width), EPSILON);
      assertEquals(.5,
        brightness(pattern, shape, radius + width / 2, 0, 0, radius, width), EPSILON);
      assertEquals(0,
        brightness(pattern, shape, radius + width, 0, 0, radius, width), EPSILON);
    } finally {
      pattern.dispose();
    }
  }

  private static double brightness(Skyspace pattern, Raybeam.Shape shape,
    double x, double y, double z, double radius, double width) {

    final double coneAngle = pattern.coneAngle.getValue();
    final double coneSin = Math.sin(coneAngle);
    final double coneCos = Math.cos(coneAngle);
    final double distance = shape.distance(
      x, y, z, radius, pattern.minorRadius.getValue(), coneSin, coneCos);
    final double envelope = Raybeam.Falloff.LINEAR.brightness(distance, width, 1);
    return pattern.getBrightness(
      shape, x, y, z, radius, pattern.minorRadius.getValue(),
      coneSin, coneCos, distance, envelope);
  }

  @Test
  void zeroRippleAmountPreservesTheExteriorEnvelope() {
    assertEquals(.63,
      Skyspace.Ripple.brightness(.63, .17, 0, .1, .25, 2, 3),
      EPSILON);
  }

  @Test
  void increasingPhaseMovesRippleCrestsAwayFromTheOpening() {
    final double spacing = .2;
    final double phase = .25;
    final double atOrigin = Skyspace.Ripple.brightness(
      .5, 0, .5, spacing, phase, 1, 1);
    final double atMovingCrest = Skyspace.Ripple.brightness(
      .5, spacing * phase, .5, spacing, phase, 1, 1);

    assertEquals(.5, atOrigin, EPSILON);
    assertEquals(.75, atMovingCrest, EPSILON);
  }

  @Test
  void rippleAmplitudeFadesWithTheExteriorEnvelope() {
    final double inner = Skyspace.Ripple.brightness(
      .8, .1, .5, .2, 0, 1, 1);
    final double outer = Skyspace.Ripple.brightness(
      .2, .1, .5, .2, 0, 1, 1);

    assertEquals(.4, .8 - inner, EPSILON);
    assertEquals(.1, .2 - outer, EPSILON);
  }

  @Test
  void rippleSharpnessNarrowsIntermediateWaveValues() {
    final double broad = Skyspace.Ripple.brightness(
      .5, 1.0 / 6, 1, 1, 0, 1, 1);
    final double narrow = Skyspace.Ripple.brightness(
      .5, 1.0 / 6, 1, 1, 0, 2, 1);

    assertEquals(.75, broad, EPSILON);
    assertEquals(.625, narrow, EPSILON);
  }

  @Test
  void rippleCannotLightPixelsOutsideTheGradientBoundary() {
    assertEquals(0,
      Skyspace.Ripple.brightness(0, .4, 1, .1, 0, 1, .1),
      EPSILON);
  }

  @Test
  void everyNumericControlIsARegisteredCompoundParameter() {
    final LX lx = newHeadlessLx();
    final Skyspace pattern = new Skyspace(lx);
    try {
      final String[] paths = {
        "originX", "originY", "originZ", "azimuth", "elevation", "roll", "width",
        "radius", "minorRadius", "softness", "coneAngle", "scaleX", "scaleY", "scaleZ",
        "rippleAmount", "rippleSpacing", "ripplePhase", "rippleSharpness", "rippleDecay"
      };
      for (String path : paths) {
        assertTrue(pattern.getParameter(path) instanceof CompoundParameter,
          path + " must remain a modulatable CompoundParameter");
      }
    } finally {
      pattern.dispose();
    }
  }
}
