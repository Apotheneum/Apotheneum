package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import apotheneum.doved.modulators.ApotheneumColor;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;

public class FireballTest extends HeadlessLxTest {

  private static final int WIDTH = 12;
  private static final int HEIGHT = 9;
  private static final float EPSILON = 1e-6f;
  private static final ApotheneumColor.Surface SURFACE = ApotheneumColor.Surface.CUBE_EXTERIOR;

  @Test
  void doorCellsNeverAccumulateHeat() {
    final Fireball fireball = new Fireball(newHeadlessLx());
    try {
      final Fireball.Fire fire = fireball.new Fire(
        WIDTH, HEIGHT, x -> ((x == 3) || (x == 4)) ? 4 : HEIGHT);
      for (int step = 0; step < 120; ++step) {
        fire.step(.25f, .45f);
      }

      for (int x : new int[] { 3, 4 }) {
        for (int y = 4; y < HEIGHT; ++y) {
          assertEquals(0, fire.heatAt(x, y), EPSILON, "door cell must stay dark");
        }
      }
    } finally {
      fireball.dispose();
    }
  }

  @Test
  void extremeControlsKeepHeatFiniteAndBounded() {
    final Fireball fireball = new Fireball(newHeadlessLx());
    try {
      fireball.coreSize.setValue(6);
      fireball.auraSize.setValue(25);
      fireball.intensity.setValue(1);
      fireball.cooling.setValue(.15);
      fireball.turbulence.setValue(3);
      fireball.buoyancy.setValue(1);
      final Fireball.Fire fire = fireball.new Fire(WIDTH, HEIGHT, x -> HEIGHT);

      for (int step = 0; step < 1200; ++step) {
        fire.step(.25f, .45f);
      }
      for (int x = 0; x < WIDTH; ++x) {
        for (int y = 0; y < HEIGHT; ++y) {
          final float heat = fire.heatAt(x, y);
          assertTrue(Float.isFinite(heat), "heat must be finite");
          assertTrue((heat >= 0) && (heat <= 1), "heat must remain normalized: " + heat);
        }
      }
    } finally {
      fireball.dispose();
    }
  }

  @Test
  void heatDecaysFullyAfterInjectionStops() {
    final Fireball fireball = new Fireball(newHeadlessLx());
    try {
      final Fireball.Fire fire = fireball.new Fire(WIDTH, HEIGHT, x -> HEIGHT);
      fire.step(.25f, .45f);
      assertTrue(hasHeat(fire));

      fireball.intensity.setValue(0);
      for (int step = 0; step < 600; ++step) {
        fire.step(.25f, .45f);
      }
      assertTrue(!hasHeat(fire), "a trail with no injection must eventually extinguish");
    } finally {
      fireball.dispose();
    }
  }

  @Test
  void sparkPoolIsBoundedAndLiveSparksStayPacked() {
    final Fireball fireball = new Fireball(newHeadlessLx());
    try {
      fireball.sparkDensity.setValue(256);
      fireball.sparkLife.setValue(.2);
      final Fireball.Fire fire = fireball.new Fire(WIDTH, HEIGHT, x -> HEIGHT);

      for (int step = 0; step < 600; ++step) {
        fire.step(.25f, .45f);
        assertTrue(fire.sparkCount() <= 256, "spark pool must not grow past its capacity");
        assertTrue(fire.liveSparksArePacked(), "live sparks must occupy the front of the pool");
      }
    } finally {
      fireball.dispose();
    }
  }

  @Test
  void staticAzimuthKeepsTheHeadStationary() {
    final Fireball fireball = new Fireball(newHeadlessLx());
    try {
      final Fireball.Fire fire = fireball.new Fire(WIDTH, HEIGHT, x -> HEIGHT);
      fire.step(fireball.azimuth.getValuef(), fireball.elevation.getValuef());
      final float startX = fire.headX();
      final float startY = fire.headY();
      for (int step = 0; step < 600; ++step) {
        fire.step(fireball.azimuth.getValuef(), fireball.elevation.getValuef());
      }
      assertEquals(startX, fire.headX(), EPSILON);
      assertEquals(startY, fire.headY(), EPSILON);
    } finally {
      fireball.dispose();
    }
  }

  @Test
  void monochromeHeatOutputHasEqualChannels() {
    final Fireball fireball = new Fireball(newHeadlessLx());
    try {
      fireball.monochrome.setValue(true);
      fireball.buildHeatCurve();
      for (float heat : new float[] { .05f, .25f, .5f, 1f }) {
        final int color = fireball.colorHeat(SURFACE, heat, 0);
        assertTrue(LXColor.b(color) > 0, "test heat must produce a lit pixel");
        assertEquals(red(color), green(color));
        assertEquals(green(color), blue(color));
      }
    } finally {
      fireball.dispose();
    }
  }

  @Test
  void lowHeatFavorsSecondaryAndPeakHeatIsWhiteHot() {
    final LX lx = newHeadlessLx();
    final Fireball fireball = new Fireball(lx);
    // core (primary) reads the shared index 1, ember (secondary) reads the shared index 2 --
    // pair=0/swap=0 reproduces the same (1, 2) pair ColorRole used to default to on its own,
    // before palette selection moved to ApotheneumColor.
    final ApotheneumColor apotheneumColor = registerApotheneumColor(lx);
    apotheneumColor.pair.setValue(0);
    apotheneumColor.swap.setValue(0);
    try {
      lx.engine.palette.swatch.addColor();
      lx.engine.palette.swatch.colors.get(0).primary.setColor(LXColor.hsb(0, 100, 100));
      lx.engine.palette.swatch.colors.get(1).primary.setColor(LXColor.hsb(240, 100, 100));
      fireball.coreColor.update();
      fireball.emberColor.update();
      fireball.buildHeatCurve();

      final int low = fireball.colorHeat(SURFACE, .1f, 0);
      assertTrue(blue(low) > red(low), "low heat should favor the blue secondary role");

      final int peak = fireball.colorHeat(SURFACE, 1, 0);
      assertEquals(red(peak), green(peak));
      assertEquals(green(peak), blue(peak));
      assertEquals(255, blue(peak));
    } finally {
      fireball.dispose();
    }
  }

  @Test
  void identicalSimulationsProduceIdenticalHeatFields() {
    final LX lx = newHeadlessLx();
    final Fireball first = new Fireball(lx);
    final Fireball second = new Fireball(lx);
    try {
      final Fireball.Fire firstFire = first.new Fire(WIDTH, HEIGHT, x -> HEIGHT);
      final Fireball.Fire secondFire = second.new Fire(WIDTH, HEIGHT, x -> HEIGHT);
      for (int step = 0; step < 300; ++step) {
        firstFire.step(.25f, .45f);
        secondFire.step(.25f, .45f);
      }
      assertArrayEquals(captureHeat(firstFire), captureHeat(secondFire));
    } finally {
      first.dispose();
      second.dispose();
    }
  }

  /**
   * The ring wrap must be width-periodic. {@code LXUtils.wrap(x, 0, width - 1)} is not: it
   * treats both endpoints as inside the range, so its period is {@code width - 1} and it
   * returns 1 for {@code width}, {@code width - 2} for -1, and drifts a further column every
   * lap. See docs/lx-coding-guidelines.md section 19.
   */
  @Test
  void columnWrapIsWidthPeriodic() {
    for (int width : new int[] { WIDTH, 120, 200 }) {
      assertEquals(width - 1, Fireball.wrapColumn(-1, width), "one before the seam");
      assertEquals(0, Fireball.wrapColumn(0, width), "the seam itself");
      assertEquals(width - 1, Fireball.wrapColumn(width - 1, width), "the last column");
      assertEquals(0, Fireball.wrapColumn(width, width), "one past the last column");
      assertEquals(width - 1, Fireball.wrapColumn(2 * width - 1, width), "a full lap later");
    }
  }

  /**
   * A stamp centered on column 0 is radially symmetric, so the columns either side of the
   * seam must receive identical heat. An off-by-one wrap shifts the whole negative side one
   * column inward, starving the last column and doubling the contribution to its neighbor.
   */
  @Test
  void seamStampIsSymmetricAroundColumnZero() {
    final Fireball fireball = new Fireball(newHeadlessLx());
    try {
      final Fireball.Fire fire = fireball.new Fire(WIDTH, HEIGHT, x -> HEIGHT);
      fire.step(0f, .45f);

      for (int offset = 1; offset <= 3; ++offset) {
        for (int y = 0; y < HEIGHT; ++y) {
          assertEquals(
            fire.heatAt(offset, y), fire.heatAt(WIDTH - offset, y), EPSILON,
            "columns " + offset + " and " + (WIDTH - offset) + " straddle the seam symmetrically");
        }
      }
    } finally {
      fireball.dispose();
    }
  }

  private static boolean hasHeat(Fireball.Fire fire) {
    for (int x = 0; x < WIDTH; ++x) {
      for (int y = 0; y < HEIGHT; ++y) {
        if (fire.heatAt(x, y) > 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static float[] captureHeat(Fireball.Fire fire) {
    final float[] heat = new float[WIDTH * HEIGHT];
    int index = 0;
    for (int x = 0; x < WIDTH; ++x) {
      for (int y = 0; y < HEIGHT; ++y) {
        heat[index++] = fire.heatAt(x, y);
      }
    }
    return heat;
  }

  private static int red(int color) {
    return (color >>> 16) & 0xff;
  }

  private static int green(int color) {
    return (color >>> 8) & 0xff;
  }

  private static int blue(int color) {
    return color & 0xff;
  }

  private static ApotheneumColor registerApotheneumColor(LX lx) {
    final ApotheneumColor color = new ApotheneumColor(lx);
    lx.engine.registerComponent(ApotheneumColor.PATH, color);
    return color;
  }
}
