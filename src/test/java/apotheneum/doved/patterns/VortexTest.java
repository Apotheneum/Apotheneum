package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;

public class VortexTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-12;
  private static final double CUBE_ZETA_SPAN = 1.479273;
  private static final double CYLINDER_ZETA_SPAN = 2.1875;

  @Test
  void parameterExtremesProduceFiniteClampedBrightness() {
    final LX lx = newHeadlessLx();
    final Vortex vortex = newTestVortex(lx);
    try {
      vortex.throat.setValue(.05);
      vortex.descent.setValue(1);
      vortex.spin.setValue(1);
      vortex.wobblePhase.setValue(1);

      for (Vortex.Horizon horizon : Vortex.Horizon.values()) {
        vortex.horizon.setValue(horizon);
        for (int arms : new int[] { 0, 8 }) {
          vortex.arms.setValue(arms);
          vortex.step();
          assertFiniteBrightness(vortex, vortex.cubeState);
          assertFiniteBrightness(vortex, vortex.cylinderState);
        }
      }
    } finally {
      vortex.dispose();
    }
  }

  private static void assertFiniteBrightness(Vortex vortex, Vortex.SurfaceState state) {
    for (int x = 0; x < state.azimuth.length; ++x) {
      for (int y = 0; y < state.rowPhase.length; ++y) {
        final double brightness = vortex.brightness(state, x, y);
        assertTrue(Double.isFinite(brightness), "brightness must be finite");
        assertTrue(brightness >= 0 && brightness <= 1,
          "brightness must be normalized: " + brightness);
      }
    }
  }

  @Test
  void unchangedParametersProduceBitIdenticalOutputAcrossFrames() {
    final LX lx = newHeadlessLx();
    final Vortex vortex = newTestVortex(lx);
    try {
      vortex.step();
      final int[] initialOutput = captureOutput(vortex);
      for (int frame = 0; frame < 600; ++frame) {
        vortex.step();
      }
      assertArrayEquals(initialOutput, captureOutput(vortex),
        "unmodulated position parameters must render bit-identically");
    } finally {
      vortex.dispose();
    }
  }

  private static int[] captureOutput(Vortex vortex) {
    final int cubeSize =
      vortex.cubeState.azimuth.length * vortex.cubeState.rowPhase.length;
    final int cylinderSize =
      vortex.cylinderState.azimuth.length * vortex.cylinderState.rowPhase.length;
    final int[] output = new int[cubeSize + cylinderSize];
    int index = 0;
    index = captureOutput(vortex, vortex.cubeState, output, index);
    captureOutput(vortex, vortex.cylinderState, output, index);
    return output;
  }

  private static int captureOutput(
    Vortex vortex,
    Vortex.SurfaceState state,
    int[] output,
    int index
  ) {
    for (int x = 0; x < state.azimuth.length; ++x) {
      for (int y = 0; y < state.rowPhase.length; ++y) {
        output[index++] = LXColor.grayn(vortex.brightness(state, x, y));
      }
    }
    return index;
  }

  @Test
  void maximumControlsRemainBelowNyquist() {
    final LX lx = newHeadlessLx();
    final Vortex vortex = newTestVortex(lx);
    try {
      vortex.twist.setValue(8);
      vortex.shear.setValue(1);
      vortex.throat.setValue(.05);
      vortex.wobble.setValue(1);
      vortex.glow.setValue(0);

      double maximumGradient = 0;
      for (Vortex.Horizon horizon : Vortex.Horizon.values()) {
        vortex.horizon.setValue(horizon);
        for (double phase : new double[] { 0, .25, .5, .75, 1 }) {
          vortex.wobblePhase.setValue(phase);
          vortex.step();
          maximumGradient = Math.max(
            maximumGradient, maximumGradient(vortex.cubeState));
          maximumGradient = Math.max(
            maximumGradient, maximumGradient(vortex.cylinderState));
          assertNoNyquistFade(vortex.cubeState);
          assertNoNyquistFade(vortex.cylinderState);
        }
      }
      assertTrue(maximumGradient > .5,
        "extreme controls must exercise a meaningful row-phase gradient");
      assertTrue(maximumGradient < Math.PI,
        "real axial geometry must remain resolvable at extreme controls");
    } finally {
      vortex.dispose();
    }
  }

  private static double maximumGradient(Vortex.SurfaceState state) {
    double maximum = 0;
    for (int y = 1; y < state.rowPhase.length; ++y) {
      maximum = Math.max(maximum, Math.abs(state.rowPhase[y] - state.rowPhase[y - 1]));
    }
    return maximum;
  }

  private static void assertNoNyquistFade(Vortex.SurfaceState state) {
    for (double env : state.env) {
      assertTrue(Double.isFinite(env));
      assertEquals(1, env, EPSILON,
        "reachable controls should remain below the Nyquist fade threshold");
    }
  }

  @Test
  void fallSweepsWholeCyclesSharedByBothSurfaces() {
    final LX lx = newHeadlessLx();
    final Vortex vortex = newTestVortex(lx);
    try {
      vortex.twist.setValue(1);
      vortex.shear.setValue(0);
      vortex.wobble.setValue(0);

      for (Vortex.Horizon horizon : Vortex.Horizon.values()) {
        vortex.horizon.setValue(horizon);
        vortex.descent.setValue(0);
        vortex.step();
        final double[] cubeStart = vortex.cubeState.rowPhase.clone();
        final double[] cylinderStart = vortex.cylinderState.rowPhase.clone();

        vortex.descent.setValue(1);
        vortex.step();
        final double cubeTravel =
          assertFallTravelIsWholeCycles(cubeStart, vortex.cubeState);
        final double cylinderTravel =
          assertFallTravelIsWholeCycles(cylinderStart, vortex.cylinderState);
        assertEquals(cubeTravel, cylinderTravel, EPSILON,
          "both surfaces must share one Fall travel, or they drift apart as Fall sweeps");
      }
    } finally {
      vortex.dispose();
    }
  }

  /**
   * A full sweep of Fall must advance every row by the same whole number of wave cycles.
   *
   * <p>Whole cycles are what let a looping modulator wrap without a jump. One shared advance
   * across both surfaces is what keeps the cube and cylinder from drifting apart, which is why
   * this asserts a single travel rather than each surface's own geometric span.
   */
  private static double assertFallTravelIsWholeCycles(double[] start, Vortex.SurfaceState state) {
    final double travel = state.rowPhase[0] - start[0];
    final double cycles = travel / Vortex.TWO_PI;
    assertEquals(Math.rint(cycles), cycles, EPSILON,
      "Fall must sweep a whole number of wave cycles so a looping modulator closes");
    assertTrue(Math.abs(cycles) >= 1,
      "Fall must sweep at least one cycle, or it cannot traverse the vortex");
    for (int y = 0; y < start.length; ++y) {
      assertEquals(travel, state.rowPhase[y] - start[y], EPSILON,
        "every row must advance together, or Fall would shear the image");
    }
    return travel;
  }

  @Test
  void maximumShearProfileDoesNotWindUpOverTime() {
    final LX lx = newHeadlessLx();
    final Vortex vortex = newTestVortex(lx);
    try {
      vortex.shear.setValue(1);
      vortex.throat.setValue(.05);
      vortex.wobble.setValue(0);
      vortex.step();

      final int lastRow = vortex.cubeState.rowPhase.length - 1;
      final double initialSpread =
        vortex.cubeState.rowPhase[lastRow] - vortex.cubeState.rowPhase[0];

      for (int frame = 0; frame < 600; ++frame) {
        vortex.step();
      }

      final double finalSpread =
        vortex.cubeState.rowPhase[lastRow] - vortex.cubeState.rowPhase[0];
      assertEquals(initialSpread, finalSpread, EPSILON,
        "static shear profile must not accumulate differential phase");
    } finally {
      vortex.dispose();
    }
  }

  @Test
  void waveLutRebuildsOnlyWhenSharpChanges() {
    final LX lx = newHeadlessLx();
    final Vortex vortex = newTestVortex(lx);
    try {
      final int initialGeneration = vortex.getWaveLutGeneration();
      final double initialQuarterWave = vortex.waveLut[Vortex.LUT_SIZE / 4];

      vortex.updateWaveLut();
      assertEquals(initialGeneration, vortex.getWaveLutGeneration());

      vortex.sharp.setValue(1);
      vortex.updateWaveLut();
      assertEquals(initialGeneration + 1, vortex.getWaveLutGeneration());
      assertNotEquals(initialQuarterWave, vortex.waveLut[Vortex.LUT_SIZE / 4]);

      vortex.updateWaveLut();
      assertEquals(initialGeneration + 1, vortex.getWaveLutGeneration());
      assertEquals(
        Math.pow(.5, 9), vortex.waveLut[Vortex.LUT_SIZE / 4], EPSILON);
    } finally {
      vortex.dispose();
    }
  }

  private static Vortex newTestVortex(LX lx) {
    final Vortex vortex = new Vortex(lx);
    vortex.apexY = 1;
    vortex.baseY = 0;
    setLinearGeometry(vortex.cubeState, CUBE_ZETA_SPAN);
    setLinearGeometry(vortex.cylinderState, CYLINDER_ZETA_SPAN);
    return vortex;
  }

  private static void setLinearGeometry(Vortex.SurfaceState state, double zetaSpan) {
    final int lastRow = state.rowY.length - 1;
    for (int y = 0; y <= lastRow; ++y) {
      state.rowY[y] = 1 - (double) y / lastRow;
      state.rowRadial[y] = 1 / zetaSpan;
    }
    state.topZetaSpan = zetaSpan;
    state.bottomZetaSpan = zetaSpan;
  }
}
