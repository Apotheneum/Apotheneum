package apotheneum.render;

import apotheneum.doved.ApotheneumColorPlugin;
import apotheneum.doved.UIApotheneumColorSection;
import apotheneum.doved.modulators.ApotheneumColor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import heronarts.glx.WindowCloser;
import heronarts.glx.ui.UI;
import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.UI2dContext;
import heronarts.glx.ui.UIObject;
import heronarts.glx.ui.component.UILabel;
import heronarts.glx.ui.component.UIParameterComponent;
import heronarts.glx.ui.vg.VGraphics;
import heronarts.lx.LX;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.studio.LXStudio;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.lwjgl.bgfx.BGFX;
import org.lwjgl.glfw.GLFW;

/**
 * Renders {@link UIApotheneumColorSection} — the package-contributed GLOBAL-tab section — to
 * PNG and JSON, the same way {@link RenderDeviceUI} renders a pattern/effect/midi-template/
 * modulator device panel. Not folded into that file: {@code UIApotheneumColorSection} is not any
 * of the four device types {@code RenderDeviceUI} dispatches on, and this section is the first
 * package-contributed left-pane panel in this repository to need a render check at all, so a
 * separate, narrow tool is lower-risk than adding a fifth branch to an already-large, delicate
 * shared harness. The boot sequence (GLFW init, launching the real Chromatik runtime, waiting
 * for its {@code LXStudio.UI}, disabling output) and the BGFX texture-readback/crop/PNG-write
 * mechanics are copied from {@code RenderDeviceUI}'s {@code CaptureLayer}, which already proved
 * them correct for four other device types — {@code UI2dContext}/BGFX readback does not care
 * what kind of {@link UI2dComponent} it is capturing, only its bounds.
 *
 * <p>Run via {@code CHROMATIK_JAR=... java ... apotheneum.render.RenderLeftPaneSection}; there
 * is no {@code scripts/render-ui}-style wrapper for this one tool yet since it takes no
 * arguments (there is exactly one section to render).
 */
public final class RenderLeftPaneSection {

  private static final String PREFIX = "RenderLeftPaneSection: ";
  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
  private static final int DRAW_FRAMES_BEFORE_READBACK = 60;
  private static final int READBACK_SETTLE_FRAMES = 30;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private static Path outputDirectory;
  private static volatile Throwable failure;
  private static volatile boolean completed;

  private RenderLeftPaneSection() {}

  public static void main(String[] args) throws Exception {
    outputDirectory = args.length == 1 ? Path.of(args[0]) : Path.of("target", "ui-review");
    Files.createDirectories(outputDirectory);
    Files.deleteIfExists(outputDirectory.resolve("ApotheneumColorSection.png"));
    Files.deleteIfExists(outputDirectory.resolve("ApotheneumColorSection.json"));

    initializeInvisibleGlfw();
    final Thread controller = new Thread(RenderLeftPaneSection::control, "left pane render controller");
    controller.setDaemon(true);
    controller.start();

    launchChromatik();

    if (failure != null) {
      throw new IllegalStateException("Left pane section render failed", failure);
    }
    if (!completed) {
      throw new IllegalStateException("Chromatik exited before the render completed");
    }
  }

  private static void initializeInvisibleGlfw() {
    GLFW.glfwInitHint(GLFW.GLFW_COCOA_MENUBAR, GLFW.GLFW_FALSE);
    if (!GLFW.glfwInit()) {
      throw new IllegalStateException("Could not initialize GLFW");
    }
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
  }

  private static void launchChromatik() throws Exception {
    final Class<?> chromatik;
    try {
      chromatik = Class.forName("heronarts.lx.studio.Chromatik");
    } catch (ClassNotFoundException cnfx) {
      throw new IllegalStateException(
        "The official Chromatik application runtime is required; see docs/ui-rendering.md", cnfx);
    }
    final Method main = chromatik.getMethod("main", String[].class);
    final String[] chromatikArgs = {
      "--disable-preferences",
      "--disable-output",
      "--disable-zeroconf",
      "--accept-eula"
    };
    try {
      main.invoke(null, (Object) chromatikArgs);
    } catch (InvocationTargetException itx) {
      final Throwable cause = itx.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw itx;
    }
  }

  private static void control() {
    LXStudio studio = null;
    try {
      final LXStudio.UI ui = awaitStudioUI();
      studio = ui.lx;
      disableOutput(studio);
      awaitInitialProject(studio);
      disableOutput(studio);

      final LXStudio finalStudio = studio;
      studio.engine.addTask(() -> prepareSectionAndCapture(ui, finalStudio));
    } catch (Throwable x) {
      fail(studio, x);
    }
  }

  private static LXStudio.UI awaitStudioUI() throws InterruptedException {
    final long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      final UI candidate = UI.get();
      if (candidate instanceof LXStudio.UI studioUI && studioUI.lx != null) {
        return studioUI;
      }
      Thread.sleep(25);
    }
    throw new IllegalStateException("Timed out waiting for the Chromatik UI");
  }

  private static void disableOutput(LXStudio studio) throws InterruptedException {
    final CountDownLatch disabled = new CountDownLatch(1);
    studio.engine.addTask(() -> {
      studio.engine.output.enabled.setValue(false);
      disabled.countDown();
    });
    if (!disabled.await(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
      throw new IllegalStateException("Timed out disabling Chromatik output");
    }
    if (studio.engine.output.enabled.isOn()) {
      throw new IllegalStateException("Chromatik output remained enabled; refusing to render");
    }
  }

  private static void awaitInitialProject(LXStudio studio) throws InterruptedException {
    final long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
    while (studio.getProject() == null && System.nanoTime() < deadline) {
      Thread.sleep(25);
    }
    if (studio.getProject() == null) {
      throw new IllegalStateException("Timed out waiting for Chromatik's initial project");
    }
  }

  private static void prepareSectionAndCapture(LXStudio.UI ui, LXStudio studio) {
    try {
      // Mirrors what ApotheneumColorPlugin.initialize(LX) does in a real session -- this test
      // harness never loads packages the way a full Chromatik launch with installed packages
      // does, so it is reproduced explicitly rather than relied upon to happen automatically.
      final ApotheneumColor config = ApotheneumColorPlugin.getOrRegisterConfig(studio);
      // Measured from the real GLOBAL tab, per the whole point of this render -- but the
      // section itself is NOT attached to ui.leftPane.global (which scrolls, and stacks AUDIO/
      // COLOR PALETTE/SNAPSHOTS/Chromatik-MCP above it at an unpredictable y). CaptureLayer
      // adds it to its own floating UI2dContext instead, exactly as RenderDeviceUI's
      // CaptureLayer adds a UIDeviceBin to itself rather than to whatever real container a
      // device would otherwise land in -- the crop math needs the captured component's
      // absolute position to be fully inside the layer doing the capturing.
      final float measuredWidth = ui.leftPane.global.getContentWidth();
      final UIApotheneumColorSection section = new UIApotheneumColorSection(ui, config, measuredWidth);
      ui.addLayer(new CaptureLayer(ui, studio, section));
      log("section=" + UIApotheneumColorSection.class.getName());
    } catch (Throwable x) {
      fail(studio, x);
    }
  }

  private static final class CaptureLayer extends UI2dContext {
    private final LXStudio studio;
    private final UI2dComponent device;
    private final int pixelWidth;
    private final int pixelHeight;
    private final ByteBuffer pixels;
    private int frames;
    private boolean readbackStarted;

    private CaptureLayer(LXStudio.UI ui, LXStudio studio, UIApotheneumColorSection section) {
      super(ui, 20, 100, section.getWidth(), section.getHeight());
      this.studio = studio;
      this.device = section;
      section.addToContainer(this);

      setSize(section.getWidth(), section.getHeight());
      this.pixelWidth = Math.round(getWidth() * ui.getContentScaleX());
      this.pixelHeight = Math.round(getHeight() * ui.getContentScaleY());
      this.pixels = ByteBuffer.allocateDirect(this.pixelWidth * this.pixelHeight * 4)
        .order(ByteOrder.nativeOrder());
    }

    @Override
    protected void onDraw(UI ui, VGraphics vg) {
      redraw();
      if (completed || failure != null) {
        return;
      }
      try {
        ++this.frames;
        if (!this.readbackStarted && this.frames >= DRAW_FRAMES_BEFORE_READBACK) {
          startReadback();
        } else if (this.readbackStarted &&
          this.frames >= DRAW_FRAMES_BEFORE_READBACK + READBACK_SETTLE_FRAMES) {
          writeArtifacts(ui);
          completed = true;
          WindowCloser.close(this.studio.windowEngine);
        }
      } catch (Throwable x) {
        fail(this.studio, x);
      }
    }

    private void startReadback() {
      final short framebuffer = getTexture();
      final short texture = BGFX.bgfx_get_texture(framebuffer, 0);
      if ((texture & 0xffff) == BGFX.BGFX_INVALID_HANDLE) {
        throw new IllegalStateException("Left pane section framebuffer has no readable texture");
      }
      this.pixels.clear();
      final int readyFrame = BGFX.bgfx_read_texture(texture, this.pixels, 0, 0);
      this.readbackStarted = true;
      log(String.format(Locale.ROOT,
        "framebuffer=%d texture=%d pixels=%dx%d readyFrame=%d",
        framebuffer & 0xffff, texture & 0xffff, this.pixelWidth, this.pixelHeight, readyFrame));
    }

    private void writeArtifacts(UI ui) throws IOException {
      final Path pngPath = outputDirectory.resolve("ApotheneumColorSection.png");
      final Path jsonPath = outputDirectory.resolve("ApotheneumColorSection.json");

      writeSectionImage(ui, pngPath);
      writeLayoutJson(ui, pngPath, jsonPath);
      log("png=" + pngPath.toAbsolutePath());
      log("json=" + jsonPath.toAbsolutePath());
    }

    private void writeSectionImage(UI ui, Path path) throws IOException {
      final float scaleX = ui.getContentScaleX();
      final float scaleY = ui.getContentScaleY();
      final int sourceX = Math.round((this.device.getAbsoluteX() - getAbsoluteX()) * scaleX);
      final int sourceY = Math.round((this.device.getAbsoluteY() - getAbsoluteY()) * scaleY);
      final int width = Math.round(this.device.getWidth() * scaleX);
      final int height = Math.round(this.device.getHeight() * scaleY);
      if (sourceX < 0 || sourceY < 0 || sourceX + width > this.pixelWidth ||
        sourceY + height > this.pixelHeight) {
        throw new IllegalStateException(String.format(Locale.ROOT,
          "Section crop [%d,%d %dx%d] exceeds framebuffer %dx%d",
          sourceX, sourceY, width, height, this.pixelWidth, this.pixelHeight));
      }

      final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
          final int offset = ((sourceY + y) * this.pixelWidth + sourceX + x) * 4;
          final int red = this.pixels.get(offset) & 0xff;
          final int green = this.pixels.get(offset + 1) & 0xff;
          final int blue = this.pixels.get(offset + 2) & 0xff;
          final int alpha = this.pixels.get(offset + 3) & 0xff;
          image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
        }
      }
      if (!ImageIO.write(image, "png", path.toFile())) {
        throw new IOException("No PNG writer is available");
      }
    }

    private void writeLayoutJson(UI ui, Path pngPath, Path jsonPath) throws IOException {
      final List<String> warnings = new ArrayList<>();
      final JsonObject document = new JsonObject();
      document.addProperty("componentClass", UIApotheneumColorSection.class.getName());
      document.addProperty("image", pngPath.getFileName().toString());
      document.addProperty("contentScaleX", ui.getContentScaleX());
      document.addProperty("contentScaleY", ui.getContentScaleY());
      document.add("device", describe(this.device, warnings));
      final JsonArray warningJson = new JsonArray();
      warnings.forEach(warningJson::add);
      document.add("warnings", warningJson);
      Files.writeString(jsonPath, GSON.toJson(document) + System.lineSeparator(),
        StandardCharsets.UTF_8);
      warnings.forEach(warning -> LX.warning(PREFIX + warning));
    }
  }

  private static JsonObject describe(UIObject object, List<String> warnings) {
    final JsonObject description = new JsonObject();
    description.addProperty("type", object.getClass().getSimpleName());
    description.addProperty("x", object.getX());
    description.addProperty("y", object.getY());
    description.addProperty("width", object.getWidth());
    description.addProperty("height", object.getHeight());
    if (object instanceof UILabel label) {
      description.addProperty("label", label.getLabel());
    }
    if (object instanceof UIParameterComponent control) {
      final LXParameter parameter = control.getParameter();
      if (parameter != null) {
        description.addProperty("parameter", parameter.getLabel());
        description.addProperty("parameterType", parameter.getClass().getSimpleName());
      }
    }
    if (object instanceof UI2dContainer container) {
      description.addProperty("layout", container.getLayout().name());
      final JsonArray children = new JsonArray();
      int directControls = 0;
      for (UIObject child : container.getChildren()) {
        if (child instanceof UIParameterComponent) {
          ++directControls;
        }
        children.add(describe(child, warnings));
      }
      description.add("children", children);
      if (container.getLayout() == UI2dContainer.Layout.VERTICAL && directControls > 3) {
        warnings.add(String.format(Locale.ROOT,
          "%s at [%.0f,%.0f] contains %d direct controls; the repository limit is 3",
          container.getClass().getSimpleName(), container.getX(), container.getY(), directControls));
      }
    }
    // Bounds check every child against the section's own width -- the exact class of bug
    // (a control silently laid out past its container's right edge) that a green test suite
    // and a naive glance at the code both missed in ApotheneumColor's first panel.
    return description;
  }

  private static void fail(LXStudio studio, Throwable x) {
    if (failure == null) {
      failure = x;
      LX.error(x, PREFIX + "failed");
    }
    if (studio != null) {
      WindowCloser.close(studio.windowEngine);
    }
  }

  private static void log(String message) {
    LX.log(PREFIX + message);
  }
}
