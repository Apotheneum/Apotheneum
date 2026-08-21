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
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIButton;
import heronarts.glx.ui.component.UICollapsibleSection;
import heronarts.glx.ui.component.UIDropMenu;
import heronarts.glx.ui.component.UILabel;
import heronarts.lx.LXLoopTask;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameterListener;

/**
 * Left-pane panel that launches {@link VideoWallLauncher} on a chosen
 * display. Deliberately minimal: a display picker, a Play/Stop toggle, and a
 * one-line status readout.
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
  private static final float ROW_HEIGHT = CONTROL_HEIGHT;
  private static final float STATUS_HEIGHT = 32;
  private static final float SECTION_HEIGHT = 20 + ROW_HEIGHT + ROW_HEIGHT + STATUS_HEIGHT + 3 * 2 + 8;

  private final UI ui;
  private final VideoWallLauncher launcher;
  private final ApotheneumVideo config;
  private final String ffplayPath;

  private final DiscreteParameter displayIndex;
  private final UIButton playButton;
  private final UILabel status;

  private final AtomicReference<List<String>> resolvedDisplayLabels = new AtomicReference<>();
  private final LXLoopTask applyResolvedLabelsTask = this::applyResolvedLabelsIfReady;
  private final LXLoopTask syncPlayButtonTask = this::syncPlayButton;

  private final LXParameterListener displayChangeListener;

  UIVideoWallPanel(UI ui, VideoWallLauncher launcher, ApotheneumVideo config, float width) {
    super(ui, 0, 0, width, SECTION_HEIGHT);
    this.ui = ui;
    this.launcher = launcher;
    this.config = config;
    this.ffplayPath = VideoWallLauncher.findFfplay();

    setTitle("VIDEO WALL");
    setLayout(UI2dContainer.Layout.VERTICAL, 2);

    this.displayIndex = new DiscreteParameter("Display", genericLabels(DEFAULT_DISPLAY_COUNT));
    this.displayChangeListener = p -> {
      // If already playing, the operator plainly means "show it there instead" —
      // restart on the new display rather than leaving ffplay fullscreen on the old one.
      if (this.launcher.isRunning()) {
        this.launcher.start(this.displayIndex.getValuei());
      }
      updateStatus();
    };
    this.displayIndex.addListener(this.displayChangeListener);

    final UIDropMenu displayMenu = new UIDropMenu(0, 0, CONTROL_WIDTH, ROW_HEIGHT, this.displayIndex);

    this.playButton = new UIButton(0, 0, CONTROL_WIDTH, ROW_HEIGHT) {
      @Override
      protected void onToggle(boolean active) {
        if (active) {
          UIVideoWallPanel.this.launcher.start(UIVideoWallPanel.this.displayIndex.getValuei());
        } else {
          UIVideoWallPanel.this.launcher.stop();
        }
      }
    };
    this.playButton.setActiveLabel("Stop").setInactiveLabel("Play");

    this.status = (UILabel) new UILabel(0, 0, getContentWidth(), STATUS_HEIGHT).setBreakLines(true);

    addChildren(
      controlRow(ui, "Display", displayMenu),
      controlRow(ui, "Wall", this.playButton),
      this.status
    );

    updateStatus();
    probeDisplaysAsync();

    ui.addLoopTask(this.applyResolvedLabelsTask);
    ui.addLoopTask(this.syncPlayButtonTask);
  }

  @Override
  public void dispose() {
    this.ui.removeLoopTask(this.applyResolvedLabelsTask);
    this.ui.removeLoopTask(this.syncPlayButtonTask);
    this.displayIndex.removeListener(this.displayChangeListener);
    super.dispose();
  }

  private void syncPlayButton(double deltaMs) {
    final boolean running = this.launcher.isRunning();
    if (this.playButton.isActive() != running) {
      this.playButton.setActive(running);
    }
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
    this.status.setLabel(displayLabel + " · " + cropWidth + "x" + cropHeight + " · " + ffplayState);
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
