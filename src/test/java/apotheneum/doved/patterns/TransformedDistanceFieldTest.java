package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.studio.ui.device.UIDeviceControls;

public class TransformedDistanceFieldTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;
  private static final double RADIUS = .25;
  private static final double WIDTH = .1;

  @Test
  void everyShapeIsFullOnItsSurfaceAndDarkAtWidth() {
    assertSurface(TransformedDistanceField.Shape.POINT,
      0, 0, 0, WIDTH, 0, 0);
    assertSurface(TransformedDistanceField.Shape.LINE,
      0, .4, 0, WIDTH, .4, 0);
    assertSurface(TransformedDistanceField.Shape.RAY,
      0, .4, 0, WIDTH, .4, 0);
    assertSurface(TransformedDistanceField.Shape.PLANE,
      .3, 0, .2, .3, WIDTH, .2);
    assertSurface(TransformedDistanceField.Shape.CYLINDER,
      RADIUS, .4, 0, RADIUS + WIDTH, .4, 0);
    assertSurface(TransformedDistanceField.Shape.SPHERE,
      RADIUS, 0, 0, RADIUS + WIDTH, 0, 0);

    final double coneX = Math.sin(WIDTH);
    final double coneY = Math.cos(WIDTH);
    assertSurface(TransformedDistanceField.Shape.CONE,
      0, 1, 0, coneX, coneY, 0);
  }

  private static void assertSurface(TransformedDistanceField.Shape shape,
    double onX, double onY, double onZ, double darkX, double darkY, double darkZ) {

    final double onDistance = shape.distance(onX, onY, onZ, RADIUS);
    final double darkDistance = shape.distance(darkX, darkY, darkZ, RADIUS);
    assertEquals(1,
      TransformedDistanceField.Falloff.LINEAR.brightness(onDistance, WIDTH), EPSILON,
      shape + " should be full brightness on its surface");
    assertEquals(0,
      TransformedDistanceField.Falloff.LINEAR.brightness(darkDistance, WIDTH), EPSILON,
      shape + " should be dark one width away");
  }

  @Test
  void rayIsCappedBehindItsOriginWhileLineContinues() {
    assertEquals(0,
      TransformedDistanceField.Shape.LINE.distance(0, -.4, 0, RADIUS), EPSILON);
    assertEquals(.4,
      TransformedDistanceField.Shape.RAY.distance(0, -.4, 0, RADIUS), EPSILON);
  }

  @Test
  void azimuthQuarterTurnSelectsAPerpendicularWorldDirection() {
    final TransformedDistanceField.LocalFrame frame =
      new TransformedDistanceField.LocalFrame();

    frame.update(0, 0, 0, 0, 0, 1, 1, 1);
    assertEquals(1, frame.localY(0, 0, 1), EPSILON);
    assertEquals(0, frame.localY(1, 0, 0), EPSILON);

    frame.update(0, 0, 0, Math.PI / 2, 0, 1, 1, 1);
    assertEquals(0, frame.localY(0, 0, 1), EPSILON);
    assertEquals(1, frame.localY(1, 0, 0), EPSILON);
  }

  @Test
  void identityFramePlaneMatchesOrboxYAxisDistance() {
    final TransformedDistanceField.LocalFrame frame =
      new TransformedDistanceField.LocalFrame();
    // Azimuth 0 and elevation +pi/2 align local XYZ exactly with world XYZ.
    frame.update(.5, .5, .5, 0, Math.PI / 2, 1, 1, 1);

    final double localY = frame.localY(.2, .8, .7);
    final double planeDistance =
      TransformedDistanceField.Shape.PLANE.distance(
        frame.localX(.2, .8, .7), localY, frame.localZ(.2, .8, .7), 0);

    // Orbox Y is abs(y) - radius before its outer abs; at radius zero this is abs(y).
    assertEquals(Math.abs(.8 - .5), planeDistance, EPSILON);
  }

  @Test
  void scaleIsAppliedInLocalSpace() {
    final TransformedDistanceField.LocalFrame frame =
      new TransformedDistanceField.LocalFrame();
    frame.update(0, 0, 0, 0, Math.PI / 2, 2, 4, .5);

    assertEquals(.5, frame.localX(1, 0, 0), EPSILON);
    assertEquals(.25, frame.localY(0, 1, 0), EPSILON);
    assertEquals(2, frame.localZ(0, 0, 1), EPSILON);
  }

  @Test
  void allFalloffsReachTheirExactEndpoints() {
    for (TransformedDistanceField.Falloff falloff : TransformedDistanceField.Falloff.values()) {
      assertEquals(1, falloff.brightness(0, WIDTH), EPSILON);
      assertTrue(falloff.brightness(WIDTH / 2, WIDTH) > 0);
      assertTrue(falloff.brightness(WIDTH / 2, WIDTH) < 1);
      assertEquals(0, falloff.brightness(WIDTH, WIDTH), EPSILON);
    }
  }

  @Test
  void patternProvidesCustomDeviceControls() {
    assertTrue(UIDeviceControls.class.isAssignableFrom(TransformedDistanceField.class));
  }

  @Test
  void renderUsesTheSelectedModelViewAndClearsEverythingElse() {
    final LX lx = newHeadlessLx();
    final TransformedDistanceField pattern = addPattern(lx);
    final LXModel selectedView = lx.getModel().children[0];
    final LXPoint selected = selectedView.points[0];
    final LXPoint excluded = lx.getModel().children[1].points[0];
    final int[] colors = new int[lx.getModel().size];

    pattern.originX.setValue(0);
    pattern.originY.setValue(0);
    pattern.originZ.setValue(0);
    pattern.shape.setValue(TransformedDistanceField.Shape.POINT);
    pattern.width.setValue(.1);
    pattern.setModel(selectedView);
    pattern.setBuffer(() -> colors);
    pattern.loop(0);

    assertSame(selectedView, pattern.getModel());
    assertEquals(LXColor.WHITE, colors[selected.index]);
    assertEquals(0, colors[excluded.index]);
  }

  @Test
  void everyNumericControlIsARegisteredCompoundParameter() {
    final LX lx = newHeadlessLx();
    final TransformedDistanceField pattern = addPattern(lx);
    final String[] paths = {
      "originX", "originY", "originZ", "azimuth", "elevation", "width", "radius",
      "scaleX", "scaleY", "scaleZ"
    };
    for (String path : paths) {
      assertTrue(pattern.getParameter(path) instanceof CompoundParameter,
        path + " must remain a modulatable CompoundParameter");
    }
  }

  private static TransformedDistanceField addPattern(LX lx) {
    final TransformedDistanceField pattern = new TransformedDistanceField(lx);
    lx.engine.mixer.addChannel(new LXPattern[] { pattern });
    return pattern;
  }

  @Override
  protected LXModel newModel() {
    final LXModel selected = new LXModel(List.of(new LXPoint(0, 0, 0)), "selected");
    final LXModel excluded = new LXModel(List.of(new LXPoint(1, 1, 1)), "excluded");
    return new LXModel(new LXModel[] { selected, excluded });
  }
}
