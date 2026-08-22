package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.LXEngine;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.view.LXViewDefinition;

public class VortexTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-12;
  private static final int APOTHENEUM_POINT_COUNT = 28_320;
  private static final Path SOURCE_FIXTURES = Path.of("src/main/resources/fixtures");

  @Test
  void pointOnAxisUsesFiniteRadialEpsilon() {
    assertEquals(Vortex.RADIAL_EPSILON, Vortex.radial(0, 0), EPSILON);
    assertEquals(1 / Vortex.RADIAL_EPSILON, 1 / Vortex.radial(0, 0), EPSILON);
  }

  @Test
  void strictSubsetViewIsIsolatedAndFieldInvariantsHold(@TempDir Path mediaPath)
    throws IOException {

    copyFixtureMedia(mediaPath);
    final LX.Flags flags = new LX.Flags();
    flags.loadPreferences = false;
    flags.mediaPath = mediaPath.toString();
    flags.outputMode = LX.Flags.OutputMode.INACTIVE;
    final LX lx = track(new LX(flags));
    lx.engine.output.enabled.setValue(false);

    final JsonFixture fixture = new JsonFixture(lx, "Apotheneum");
    lx.structure.addFixture(fixture);
    lx.structure.beforeEngineRun();
    assertTrue(!fixture.error.isOn(), fixture.errorMessage.getString());
    assertEquals(APOTHENEUM_POINT_COUNT, lx.getModel().size);
    assertTrue(!lx.engine.output.enabled.isOn());

    final LXViewDefinition subset = lx.structure.views.addView();
    subset.selector.setValue("cylinderExterior");
    final LXModel selectedModel = subset.getModelView();
    assertTrue(selectedModel.size > 0, "subset view must contain points");
    assertTrue(selectedModel.size < lx.getModel().size, "view must be a strict subset");

    final Vortex vortex = new Vortex(lx);
    vortex.view.setValue(subset);
    assertSame(selectedModel, vortex.getModelView());

    assertEquals(0, vortex.getGeometryLutGeneration());
    vortex.step();
    assertEquals(1, vortex.getGeometryLutGeneration());
    assertEquals(selectedModel.size, vortex.pointState.pointIndices.length);
    assertTrue(vortex.geometryRebuildMs > 0);
    final int[] initialOutput = captureOutput(vortex);
    for (int frame = 0; frame < 600; ++frame) {
      vortex.step();
    }
    assertEquals(1, vortex.getGeometryLutGeneration(),
      "stable model and view identities must reuse the point LUT");
    assertArrayEquals(initialOutput, captureOutput(vortex),
      "unmodulated position parameters must render bit-identically");

    vortex.throat.setValue(.05);
    vortex.descent.setValue(1);
    vortex.spin.setValue(1);
    vortex.twist.setValue(8);
    vortex.shear.setValue(1);
    vortex.wobble.setValue(1);
    vortex.wobblePhase.setValue(1);
    for (Vortex.Horizon horizon : Vortex.Horizon.values()) {
      vortex.horizon.setValue(horizon);
      for (int arms : new int[] { 0, 8 }) {
        vortex.arms.setValue(arms);
        vortex.step();
        assertFiniteBrightness(vortex, vortex.pointState);
      }
    }

    vortex.twist.setValue(1);
    vortex.shear.setValue(0);
    vortex.wobble.setValue(0);
    for (Vortex.Horizon horizon : Vortex.Horizon.values()) {
      vortex.horizon.setValue(horizon);
      vortex.descent.setValue(0);
      vortex.step();
      final double[] start = vortex.pointState.phase.clone();
      vortex.descent.setValue(1);
      vortex.step();
      final double expectedTravel = -horizon.zetaSpan(vortex.pointState);
      for (int i = 0; i < start.length; ++i) {
        assertEquals(expectedTravel, vortex.pointState.phase[i] - start[i], EPSILON);
      }
    }

    vortex.descent.setValue(0);
    vortex.shear.setValue(1);
    vortex.throat.setValue(.05);
    vortex.step();
    final int lastPoint = vortex.pointState.phase.length - 1;
    final double initialSpread = vortex.pointState.phase[lastPoint] - vortex.pointState.phase[0];
    for (int frame = 0; frame < 600; ++frame) {
      vortex.step();
    }
    final double finalSpread = vortex.pointState.phase[lastPoint] - vortex.pointState.phase[0];
    assertEquals(initialSpread, finalSpread, EPSILON,
      "static shear profile must not accumulate differential phase");

    final int initialWaveGeneration = vortex.getWaveLutGeneration();
    final double initialQuarterWave = vortex.waveLut[Vortex.LUT_SIZE / 4];
    vortex.updateWaveLut();
    assertEquals(initialWaveGeneration, vortex.getWaveLutGeneration());
    vortex.sharp.setValue(1);
    vortex.updateWaveLut();
    assertEquals(initialWaveGeneration + 1, vortex.getWaveLutGeneration());
    assertNotEquals(initialQuarterWave, vortex.waveLut[Vortex.LUT_SIZE / 4]);
    assertEquals(Math.pow(.5, 9), vortex.waveLut[Vortex.LUT_SIZE / 4], EPSILON);

    // Make the field uniformly non-black so the view-isolation assertion tests selection rather
    // than coincidentally sampling a dark point of the wave.
    vortex.arms.setValue(0);
    vortex.twist.setValue(0);
    vortex.shear.setValue(0);
    vortex.wobble.setValue(0);
    vortex.glow.setValue(0);
    vortex.spin.setValue(.25);
    vortex.sharp.setValue(0);
    lx.engine.mixer.addChannel(new LXPattern[] { vortex });
    lx.engine.run();
    assertTrue(!lx.engine.output.enabled.isOn());

    final LXEngine.Frame outputFrame = new LXEngine.Frame(lx);
    outputFrame.setModel(lx.getModel());
    lx.engine.copyFrameThreadSafe(outputFrame);
    final int[] colors = outputFrame.getMain();
    final boolean[] selected = new boolean[colors.length];
    for (LXPoint point : selectedModel.points) {
      selected[point.index] = true;
    }
    for (int i = 0; i < colors.length; ++i) {
      if (selected[i]) {
        assertNotEquals(LXColor.BLACK, colors[i], "selected point must be lit: " + i);
      } else {
        assertEquals(LXColor.BLACK, colors[i], "point outside view must be black: " + i);
      }
    }
  }

  private static int[] captureOutput(Vortex vortex) {
    final int[] output = new int[vortex.pointState.azimuth.length];
    for (int i = 0; i < output.length; ++i) {
      output[i] = LXColor.grayn(vortex.brightness(vortex.pointState, i));
    }
    return output;
  }

  private static void assertFiniteBrightness(Vortex vortex, Vortex.PointState state) {
    for (int i = 0; i < state.azimuth.length; ++i) {
      assertTrue(Double.isFinite(state.topZeta[i]), "top zeta must be finite");
      assertTrue(Double.isFinite(state.bottomZeta[i]), "bottom zeta must be finite");
      final double brightness = vortex.brightness(state, i);
      assertTrue(Double.isFinite(brightness), "brightness must be finite");
      assertTrue(brightness >= 0 && brightness <= 1,
        "brightness must be normalized: " + brightness);
    }
  }

  private static void copyFixtureMedia(Path mediaPath) throws IOException {
    final Path fixtureDirectory = Files.createDirectories(mediaPath.resolve("Fixtures"));
    try (Stream<Path> paths = Files.list(SOURCE_FIXTURES)) {
      for (Path source : paths.filter(Files::isRegularFile).toList()) {
        Files.copy(source, fixtureDirectory.resolve(source.getFileName()),
          StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }
}
