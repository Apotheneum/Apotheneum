package apotheneum.video;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import heronarts.glx.ui.UI;
import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIButton;
import heronarts.glx.ui.component.UICollapsibleSection;
import heronarts.glx.ui.component.UIDropMenu;
import heronarts.glx.ui.component.UIIntegerBox;
import heronarts.glx.ui.component.UILabel;
import heronarts.lx.LXLoopTask;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameterListener;

/**
 * Left-pane panel that launches {@link VideoWallLauncher} on a chosen
 * display, and exposes the active preset's source/layout/processor/gap/panel
 * count controls that shape the picture it sends. Controls are stacked
 * full-width, one per row, rather than the framework's default
 * label-left/60px-control-right {@code controlRow}: that fixed 60px column is
 * wide enough for neither a display name ("Color LCD") nor an enum value, and
 * this panel's whole job is showing those in full.
 *
 * <p>Order follows the operator's mental model: pick which preset is active,
 * then its controls, then where and whether to play. Grouped to respect the
 * 3-controls-per-column UI rule (see AGENTS.md): PRESET (the selector alone),
 * PICTURE (Source/Layout/Processor), PANELS (Gap/Panel Count, hidden unless
 * the active preset's layout is Panels), and PLAYBACK (Display/Play).
 *
 * <p>Editing any of the five preset-owned controls edits the <i>active</i>
 * preset, never a copied-out live set — so switching {@link
 * ApotheneumVideo#activePreset} only ever rebinds which preset's parameters
 * the widgets point at and restarts the pipeline once, rather than copying
 * five values onto five live parameters and firing five separate restarts.
 *
 * <p>Display names come from {@code system_profiler}, run once in a
 * background thread at construction — never on the UI thread, and never
 * blocking it. Until (or unless) that resolves, the dropdown shows generic
 * placeholder labels. Nothing here ever touches {@code java.awt.*}:
 * initializing AWT inside this GLFW/Cocoa process risks a main-thread
 * deadlock.
 */
class UIVideoWallPanel extends UICollapsibleSection {

  private static final int DEFAULT_DISPLAY_COUNT = 4;
  private static final long PROBE_TIMEOUT_SECONDS = 3;

  // A stacked control: a caption above a full-width control.
  private static final float CAPTION_HEIGHT = 12;
  private static final float CAPTION_SPACING = 2;
  private static final float ROW_HEIGHT = CAPTION_HEIGHT + CAPTION_SPACING + CONTROL_HEIGHT;

  // Within a group: heading, then its rows, spaced GROUP_ROW_SPACING apart.
  private static final float GROUP_HEADING_HEIGHT = 12;
  private static final float GROUP_ROW_SPACING = 4;
  private static final float PRESET_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + ROW_HEIGHT;
  private static final float PICTURE_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + 3 * ROW_HEIGHT + 2 * GROUP_ROW_SPACING;
  private static final float PANELS_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + 2 * ROW_HEIGHT + GROUP_ROW_SPACING;
  private static final float PLAYBACK_GROUP_HEIGHT =
    GROUP_HEADING_HEIGHT + GROUP_ROW_SPACING + ROW_HEIGHT + GROUP_ROW_SPACING + CONTROL_HEIGHT;

  // Between groups and the status line.
  private static final float GROUP_SPACING = 6;
  private static final float STATUS_HEIGHT = 32;
  private static final float CONTENT_HEIGHT =
    PRESET_GROUP_HEIGHT + GROUP_SPACING
    + PICTURE_GROUP_HEIGHT + GROUP_SPACING
    + PANELS_GROUP_HEIGHT + GROUP_SPACING
    + PLAYBACK_GROUP_HEIGHT + GROUP_SPACING
    + STATUS_HEIGHT;
  // Inverse of UICollapsibleSection's content sizing: content height is
  // (expandedHeight - PADDING - BAR_HEIGHT), so this is CONTENT_HEIGHT + 24.
  private static final float SECTION_HEIGHT = CONTENT_HEIGHT + PADDING + BAR_HEIGHT;

  private final UI ui;
  private final VideoWallLauncher launcher;
  private final ApotheneumVideo config;
  private final String ffplayPath;

  private final DiscreteParameter displayIndex;
  private final UIButton playButton;
  private final UIDropMenu presetMenu;
  private final UIDropMenu sourceMenu;
  private final UIDropMenu layoutMenu;
  private final UIDropMenu processorMenu;
  private final UIIntegerBox gapBox;
  private final UIIntegerBox panelCountBox;
  private final UILabel status;

  private final AtomicReference<List<String>> resolvedDisplayLabels = new AtomicReference<>();
  private final LXLoopTask applyResolvedLabelsTask = this::applyResolvedLabelsIfReady;
  private final LXLoopTask refreshTask = this::refresh;

  private final LXParameterListener displayChangeListener;
  private final LXParameterListener fpsChangeListener;
  private final LXParameterListener activePresetChangeListener;
  // Bound to whichever preset is currently active; re-bound in bindActivePreset().
  private final LXParameterListener presetLayoutChangeListener;
  private final LXParameterListener presetFieldChangeListener;

  // The preset the listeners above are currently attached to, so switching
  // presets can unsubscribe from the old one before subscribing to the new.
  private Preset boundPreset = null;

  UIVideoWallPanel(UI ui, VideoWallLauncher launcher, ApotheneumVideo config, float width) {
    super(ui, 0, 0, width, SECTION_HEIGHT);
    this.ui = ui;
    this.launcher = launcher;
    this.config = config;
    this.ffplayPath = VideoWallLauncher.findFfplay();

    setTitle("VIDEO WALL");
    setLayout(UI2dContainer.Layout.VERTICAL, GROUP_SPACING);

    final float contentWidth = getContentWidth();

    this.displayIndex = new DiscreteParameter("Display", genericLabels(DEFAULT_DISPLAY_COUNT));
    // If already playing, the operator plainly means "show it there instead" —
    // restart on the new setting rather than leaving ffplay running on the old one.
    this.displayChangeListener = p -> restartIfRunningElseUpdateStatus();
    this.displayIndex.addListener(this.displayChangeListener);

    // Raw rgb24 carries no timing metadata. Both ffmpeg and ffplay snapshot
    // the configured rate at launch, so a live FPS edit must restart an active
    // pipeline before the producer and consumers drift apart.
    this.fpsChangeListener = p -> restartIfRunningElseUpdateStatus();
    this.config.fps.addListener(this.fpsChangeListener);

    // Switching which preset is active is the one control whose whole job is
    // to change five values at once — it must cause exactly one restart, not
    // five. That is why it never copies values onto shared live parameters:
    // it only rebinds which preset's own parameters the widgets and listeners
    // point at, then restarts (or updates status) a single time.
    this.activePresetChangeListener = p -> bindActivePreset();
    this.config.activePreset.addListener(this.activePresetChangeListener);

    // Same "if playing, apply live; if not, just reflect it in status" rule as
    // the display picker above — a layout change is only worth a restart
    // while ffplay is actually on screen, and it also may reveal/hide Gap and
    // Panel Count.
    this.presetLayoutChangeListener = p -> {
      updateGapEnabled();
      restartIfRunningElseUpdateStatus();
    };
    // Source/Processor/Gap/Panel Count all follow the same live-apply rule.
    this.presetFieldChangeListener = p -> restartIfRunningElseUpdateStatus();

    this.presetMenu = new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, this.config.activePreset);
    this.sourceMenu = new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, this.config.presetA.source);
    this.layoutMenu = new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, this.config.presetA.layout);
    this.processorMenu = new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, this.config.presetA.processor);

    final UIDropMenu displayMenu = new UIDropMenu(0, 0, contentWidth, CONTROL_HEIGHT, this.displayIndex);

    this.gapBox = (UIIntegerBox) controlIntegerBox(this.config.presetA.gap).setWidth(contentWidth);
    this.panelCountBox = (UIIntegerBox) controlIntegerBox(this.config.presetA.panelCount).setWidth(contentWidth);

    this.playButton = new UIButton(0, 0, contentWidth, CONTROL_HEIGHT) {
      @Override
      protected void onToggle(boolean active) {
        // setActive() always calls onToggle() when the flag changes, even when
        // refreshTask is only mirroring the launcher's own state back onto the
        // button — so this must check isRunning() itself rather than blindly
        // re-issuing the action, or that mirroring re-triggers a second,
        // redundant launch.
        if (active) {
          if (!UIVideoWallPanel.this.launcher.isRunning()) {
            UIVideoWallPanel.this.launcher.start(UIVideoWallPanel.this.displayIndex.getValuei());
          }
        } else if (UIVideoWallPanel.this.launcher.isRunning()) {
          UIVideoWallPanel.this.launcher.stop();
        }
      }
    };
    this.playButton.setActiveLabel("Stop").setInactiveLabel("Play");

    this.status = (UILabel) new UILabel(0, 0, contentWidth, STATUS_HEIGHT).setBreakLines(true);

    final UI2dContainer presetGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "PRESET"),
      stackedRow(contentWidth, "Preset", this.presetMenu)
    );

    final UI2dContainer pictureGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "PICTURE"),
      stackedRow(contentWidth, "Source", this.sourceMenu),
      stackedRow(contentWidth, "Layout", this.layoutMenu),
      stackedRow(contentWidth, "Processor", this.processorMenu)
    );

    final UI2dContainer panelsGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "PANELS"),
      stackedRow(contentWidth, "Gap", this.gapBox),
      stackedRow(contentWidth, "Panel Count", this.panelCountBox)
    );

    final UI2dContainer playbackGroup = UI2dContainer.newVerticalContainer(contentWidth, GROUP_ROW_SPACING,
      groupHeading(contentWidth, "PLAYBACK"),
      stackedRow(contentWidth, "Display", displayMenu),
      this.playButton
    );

    addChildren(presetGroup, pictureGroup, panelsGroup, playbackGroup, this.status);

    bindActivePreset();
    probeDisplaysAsync();

    ui.addLoopTask(this.applyResolvedLabelsTask);
    ui.addLoopTask(this.refreshTask);
  }

  /** A caption label above a full-width control, stacked with {@link #CAPTION_SPACING}. */
  private static UI2dContainer stackedRow(float contentWidth, String label, UI2dComponent control) {
    final UILabel caption = new UILabel(0, 0, contentWidth, CAPTION_HEIGHT).setLabel(label);
    return UI2dContainer.newVerticalContainer(contentWidth, CAPTION_SPACING, caption, control);
  }

  private static UILabel groupHeading(float contentWidth, String label) {
    return new UILabel(0, 0, contentWidth, GROUP_HEADING_HEIGHT).setLabel(label);
  }

  /**
   * Points every preset-scoped control and listener at the newly active
   * preset, then applies the change exactly once — the hard requirement that
   * switching presets is a single restart, not one per changed value.
   */
  private void bindActivePreset() {
    final Preset next = this.config.activePreset();
    if (next == this.boundPreset) {
      return;
    }
    if (this.boundPreset != null) {
      this.boundPreset.source.removeListener(this.presetFieldChangeListener);
      this.boundPreset.layout.removeListener(this.presetLayoutChangeListener);
      this.boundPreset.processor.removeListener(this.presetFieldChangeListener);
      this.boundPreset.gap.removeListener(this.presetFieldChangeListener);
      this.boundPreset.panelCount.removeListener(this.presetFieldChangeListener);
    }
    this.boundPreset = next;
    next.source.addListener(this.presetFieldChangeListener);
    next.layout.addListener(this.presetLayoutChangeListener);
    next.processor.addListener(this.presetFieldChangeListener);
    next.gap.addListener(this.presetFieldChangeListener);
    next.panelCount.addListener(this.presetFieldChangeListener);

    this.sourceMenu.setParameter(next.source);
    this.layoutMenu.setParameter(next.layout);
    this.processorMenu.setParameter(next.processor);
    this.gapBox.setParameter(next.gap);
    this.panelCountBox.setParameter(next.panelCount);

    updateGapEnabled();
    restartIfRunningElseUpdateStatus();
  }

  /**
   * Gap and panel count only shape anything under the Panels layout;
   * disabling them under Fit or Fill would need {@code setEnabled}, which
   * {@link UIIntegerBox} doesn't expose, so this hides the rows instead of
   * presenting controls that silently do nothing.
   */
  private void updateGapEnabled() {
    final boolean panels = this.boundPreset.layout.getEnum() == ApotheneumVideo.Layout.PANELS;
    this.gapBox.getParent().setVisible(panels);
    this.panelCountBox.getParent().setVisible(panels);
  }

  @Override
  public void dispose() {
    this.ui.removeLoopTask(this.applyResolvedLabelsTask);
    this.ui.removeLoopTask(this.refreshTask);
    this.displayIndex.removeListener(this.displayChangeListener);
    this.config.fps.removeListener(this.fpsChangeListener);
    this.config.activePreset.removeListener(this.activePresetChangeListener);
    if (this.boundPreset != null) {
      this.boundPreset.source.removeListener(this.presetFieldChangeListener);
      this.boundPreset.layout.removeListener(this.presetLayoutChangeListener);
      this.boundPreset.processor.removeListener(this.presetFieldChangeListener);
      this.boundPreset.gap.removeListener(this.presetFieldChangeListener);
      this.boundPreset.panelCount.removeListener(this.presetFieldChangeListener);
    }
    super.dispose();
  }

  /** Runs every UI loop tick: mirrors the launcher's running state onto Play/Stop, and refreshes the status line. */
  private void refresh(double deltaMs) {
    final boolean running = this.launcher.isRunning();
    if (this.playButton.isActive() != running) {
      this.playButton.setActive(running);
    }
    updateStatus();
  }

  private void restartIfRunningElseUpdateStatus() {
    if (this.launcher.isRunning()) {
      this.launcher.start(this.displayIndex.getValuei());
    }
    updateStatus();
  }

  private void applyResolvedLabelsIfReady(double deltaMs) {
    final List<String> labels = this.resolvedDisplayLabels.getAndSet(null);
    if (labels != null) {
      this.displayIndex.setOptions(labels.toArray(new String[0]));
      updateStatus();
      this.ui.removeLoopTask(this.applyResolvedLabelsTask);
    }
  }

  private void updateStatus() {
    final String displayLabel = this.displayIndex.getOption();
    final int cropWidth = this.config.cropWidth.getValuei();
    final int cropHeight = this.config.cropHeight.getValuei();
    final String ffplayState = (this.ffplayPath != null) ? "ffplay found" : "ffplay NOT FOUND";
    final String litLabel = String.format("%.1f%% lit", this.config.getLitFraction() * 100.0);
    this.status.setLabel(displayLabel + " · " + cropWidth + "x" + cropHeight + " · " + ffplayState + " · " + litLabel);
  }

  private static String[] genericLabels(int count) {
    final String[] labels = new String[count];
    for (int i = 0; i < count; ++i) {
      labels[i] = "Display " + (i + 1);
    }
    return labels;
  }

  private void probeDisplaysAsync() {
    final Thread thread = new Thread(this::probeDisplays, "Apotheneum Video Wall Display Probe");
    thread.setDaemon(true);
    thread.start();
  }

  private void probeDisplays() {
    List<String> labels = null;
    try {
      final Process process = new ProcessBuilder("/usr/sbin/system_profiler", "SPDisplaysDataType", "-json")
        .redirectErrorStream(false)
        .start();
      if (process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        labels = parseDisplayNames(readAll(process.getInputStream()));
      } else {
        process.destroyForcibly();
      }
    } catch (IOException | InterruptedException x) {
      if (x instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      // Fall back to the generic placeholder labels already showing.
    }
    if ((labels != null) && !labels.isEmpty()) {
      this.resolvedDisplayLabels.set(labels);
    }
  }

  private static String readAll(InputStream in) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final byte[] buffer = new byte[4096];
    int read;
    while ((read = in.read(buffer)) >= 0) {
      out.write(buffer, 0, read);
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  /** Parses the {@code _name} of every attached display out of system_profiler's JSON, in order. */
  private static List<String> parseDisplayNames(String json) {
    try {
      final JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      final JsonArray gpus = root.getAsJsonArray("SPDisplaysDataType");
      final List<String> names = new ArrayList<>();
      for (JsonElement gpuElement : gpus) {
        final JsonArray displays = gpuElement.getAsJsonObject().getAsJsonArray("spdisplays_ndrvs");
        if (displays == null) {
          continue;
        }
        for (JsonElement displayElement : displays) {
          final JsonObject display = displayElement.getAsJsonObject();
          final JsonElement name = display.get("_name");
          names.add((name != null) ? name.getAsString() : ("Display " + (names.size() + 1)));
        }
      }
      return names;
    } catch (Exception x) {
      return null;
    }
  }

}
