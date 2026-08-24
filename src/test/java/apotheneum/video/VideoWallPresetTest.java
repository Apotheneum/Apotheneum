package apotheneum.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.parameter.LXParameterListener;

/**
 * Covers the two things a live rig can't demonstrate without a real display
 * and ffmpeg/ffplay: that each preset's five values independently drive the
 * generated filter graph, and that switching {@link ApotheneumVideo#activePreset}
 * — the operation the panel wires to a single pipeline restart — only ever
 * touches that one parameter, never the presets' own layout/processor/gap/panel
 * count parameters. Those are exactly the listeners
 * {@link UIVideoWallPanel#bindActivePreset} attaches restart behavior to, so a
 * switch that fires none of them is structurally guaranteed to restart once.
 */
class VideoWallPresetTest extends HeadlessLxTest {

  @Test
  void presetsProduceIndependentFilterGraphs() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, config);

    // Preset A: Panels, Crop(Top336), gap 240, 4 panels, Ext Perimeter — the shipped defaults.
    config.presetA.source.setValue(VideoSource.EXTERIOR_PERIMETER);
    config.presetA.layout.setValue(ApotheneumVideo.Layout.PANELS);
    config.presetA.processor.setValue(ApotheneumVideo.Processor.TOP_336);
    config.presetA.gap.setValue(240);
    config.presetA.panelCount.setValue(4);

    // Preset B: Fit, Scale(Shrink600), Int Perimeter — a deliberately different look.
    config.presetB.source.setValue(VideoSource.INTERIOR_PERIMETER);
    config.presetB.layout.setValue(ApotheneumVideo.Layout.FIT);
    config.presetB.processor.setValue(ApotheneumVideo.Processor.SHRINK_600);

    final VideoWallLauncher launcher = new VideoWallLauncher(config, 7878);

    config.activePreset.setValue(ApotheneumVideo.PresetSlot.A);
    final List<String> commandA = launcher.buildFfmpegCommand("/usr/bin/ffmpeg");
    final String filterA = commandA.get(commandA.indexOf("-filter_complex") + 1);

    config.activePreset.setValue(ApotheneumVideo.PresetSlot.B);
    final List<String> commandB = launcher.buildFfmpegCommand("/usr/bin/ffmpeg");
    final String filterB = commandB.get(commandB.indexOf("-filter_complex") + 1);

    System.out.println("preset A filter graph: " + filterA);
    System.out.println("preset B filter graph: " + filterB);

    assertTrue(filterA.contains("split=4"), "Panels layout should split into 4 faces: " + filterA);
    assertTrue(filterA.contains("pad=2688:600:0:0:black"), "Crop processor should pad below: " + filterA);
    assertTrue(filterB.contains("pad=2688:336"), "Fit layout should pad into the wall rect: " + filterB);
    assertTrue(filterB.contains("scale=2688:600"), "Scale processor should stretch to 600: " + filterB);
  }

  @Test
  void switchingActivePresetTouchesOnlyTheSelector() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, config);

    final AtomicInteger presetFieldChanges = new AtomicInteger(0);
    final LXParameterListener counter = p -> presetFieldChanges.incrementAndGet();
    // Exactly the parameters UIVideoWallPanel.bindActivePreset() attaches
    // restart-on-change listeners to, on both presets.
    for (Preset preset : List.of(config.presetA, config.presetB)) {
      preset.source.addListener(counter);
      preset.layout.addListener(counter);
      preset.processor.addListener(counter);
      preset.gap.addListener(counter);
      preset.panelCount.addListener(counter);
    }

    config.activePreset.setValue(ApotheneumVideo.PresetSlot.B);

    assertEquals(0, presetFieldChanges.get(),
      "switching activePreset must not itself change any preset's own parameters");
  }

  @Test
  void panelsFallsBackToFitForASingleFaceSource() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, config);
    config.presetA.source.setValue(VideoSource.EXTERIOR_FRONT);
    config.presetA.layout.setValue(ApotheneumVideo.Layout.PANELS);

    final String filter = filterGraph(new VideoWallLauncher(config, 7878));

    assertTrue(filter.contains("force_original_aspect_ratio=decrease"),
      "single-face Panels must fall back to Fit: " + filter);
    assertTrue(!filter.contains("split="), "single-face source must not produce panels: " + filter);
  }

  @Test
  void fitUsesBothWallDimensions() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, config);
    config.presetA.layout.setValue(ApotheneumVideo.Layout.FIT);
    config.cropHeight.setValue(20);

    final String filter = filterGraph(new VideoWallLauncher(config, 7878));

    assertTrue(filter.contains("scale=w=2688:h=336:force_original_aspect_ratio=decrease"),
      "Fit must constrain a wide crop by wall width: " + filter);
    assertTrue(filter.contains("pad=2688:336:(ow-iw)/2:(oh-ih)/2:black"),
      "Fit must center the constrained image in the wall: " + filter);
  }

  @Test
  void fillUsesBothWallDimensions() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, config);
    config.presetA.layout.setValue(ApotheneumVideo.Layout.FILL);
    config.cropHeight.setValue(20);

    final String filter = filterGraph(new VideoWallLauncher(config, 7878));

    assertTrue(filter.contains("scale=w=2688:h=336:force_original_aspect_ratio=increase"),
      "Fill must constrain a wide crop by wall height: " + filter);
    assertTrue(filter.contains("crop=2688:336:(iw-ow)/2:(ih-oh)/2"),
      "Fill must center-crop the expanded image: " + filter);
  }

  @Test
  void panelsFallsBackToFitWithHorizontalCropOffset() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, config);
    config.presetA.layout.setValue(ApotheneumVideo.Layout.PANELS);
    config.cropX.setValue(1);

    final String filter = filterGraph(new VideoWallLauncher(config, 7878));

    assertTrue(filter.contains("force_original_aspect_ratio=decrease"),
      "offset Panels must fall back to Fit: " + filter);
    assertTrue(!filter.contains("split="), "offset crop must not produce panels: " + filter);
  }

  @Test
  void commandsTrackTheConfiguredFrameRate() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, config);
    config.fps.setValue(48);

    final VideoWallLauncher launcher = new VideoWallLauncher(config, 7878);

    assertEquals("48", optionValue(launcher.buildFfmpegCommand("/usr/bin/ffmpeg"), "-framerate"));
    assertEquals("48", optionValue(launcher.buildFfplayCommand("/usr/bin/ffplay"), "-framerate"));
  }

  @Test
  void previewCommandIsWindowedAndTitled() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    final List<String> command = new VideoWallLauncher(config, 7878)
      .buildPreviewFfplayCommand("/usr/bin/ffplay");

    assertEquals("Apotheneum Video Preview", optionValue(command, "-window_title"));
    assertEquals("1344", optionValue(command, "-x"));
    assertEquals("300", optionValue(command, "-y"));
    assertTrue(!command.contains("-fs"), "local preview must remain a normal window");
  }

  @Test
  void commandTracksCropDimensions() {
    final LX lx = newHeadlessLx();
    final ApotheneumVideo config = new ApotheneumVideo(lx);
    lx.engine.registerComponent(ApotheneumVideo.PATH, config);
    config.cropWidth.setValue(150);
    config.cropHeight.setValue(20);

    assertEquals("150x20", optionValue(
      new VideoWallLauncher(config, 7878).buildFfmpegCommand("/usr/bin/ffmpeg"), "-video_size"));
  }

  private static String filterGraph(VideoWallLauncher launcher) {
    final List<String> command = launcher.buildFfmpegCommand("/usr/bin/ffmpeg");
    return command.get(command.indexOf("-filter_complex") + 1);
  }

  private static String optionValue(List<String> command, String option) {
    return command.get(command.indexOf(option) + 1);
  }

}
