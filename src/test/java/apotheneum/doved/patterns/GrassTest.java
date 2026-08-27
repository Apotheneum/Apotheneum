package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.structure.view.LXViewDefinition;

public class GrassTest extends HeadlessLxTest {

  private static final int SENTINEL = LXColor.rgb(255, 0, 255);

  @Test
  void patternViewOnlyWritesItsSelectedPoints() throws Exception {
    final LX lx = newApotheneumLx();

    final LXViewDefinition cylinderExterior = lx.structure.views.addView();
    cylinderExterior.selector.setValue("cylinderExterior");
    final Grass grass = new Grass(lx);
    final LXChannel channel = lx.engine.mixer.addChannel(new Grass[] { grass });
    grass.view.setValue(cylinderExterior);
    assertTrue(channel.getActivePattern() == grass);

    lx.engine.run();
    Arrays.fill(grass.getColors(), SENTINEL);
    for (int frame = 0; frame < 20; ++frame) {
      grass.loop(1000. / 60.);
    }

    assertUnwritten(Apotheneum.cube.exterior, grass.getColors(), "cube exterior was written");
    assertUnwritten(Apotheneum.cube.interior, grass.getColors(), "cube interior was written");
    boolean cylinderWasWritten = false;
    for (LXPoint point : Apotheneum.cylinder.exterior.columns()[0].points) {
      if (grass.getColors()[point.index] != SENTINEL) {
        cylinderWasWritten = true;
        break;
      }
    }
    assertTrue(cylinderWasWritten, "cylinder exterior was not written");
  }

  private static void assertUnwritten(Apotheneum.Orientation orientation, int[] colors, String message) {
    for (Apotheneum.Column column : orientation.columns()) {
      for (LXPoint point : column.points) {
        assertTrue(colors[point.index] == SENTINEL, message);
      }
    }
  }
}
