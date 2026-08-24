package apotheneum.render;

import apotheneum.Apotheneum;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import heronarts.glx.WindowCloser;
import heronarts.glx.ui.UI;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContext;
import heronarts.glx.ui.UIObject;
import heronarts.glx.ui.component.UILabel;
import heronarts.glx.ui.component.UIParameterComponent;
import heronarts.glx.ui.vg.VGraphics;
import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.midi.template.LXMidiTemplate;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.studio.LXStudio;
import heronarts.lx.studio.ui.device.UIDeviceBin;
import heronarts.lx.studio.ui.device.UIPatternDevice;
import heronarts.lx.studio.ui.midi.template.UIMidiTemplate;
import heronarts.lx.structure.JsonFixture;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.lwjgl.bgfx.BGFX;
import org.lwjgl.glfw.GLFW;

/**
 * Renders one pattern or MIDI template's real Chromatik device panel to PNG and JSON.
 *
 * <p>This class deliberately lives in test scope. It is launched by {@code scripts/render-ui}
 * against the official Chromatik application runtime and never enters the package jar.
 */
public final class RenderDeviceUI {
  private static final String PREFIX = "RenderDeviceUI: ";
  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
  private static final int DRAW_FRAMES_BEFORE_READBACK = 60;
  private static final int READBACK_SETTLE_FRAMES = 30;
  private static final String FIXTURE_NAME = "Apotheneum";
  private static final Path SOURCE_FIXTURE =
    Path.of("src", "main", "resources", "fixtures", FIXTURE_NAME + ".lxf");
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private static Class<? extends LXPattern> patternClass;
  private static Class<? extends LXMidiTemplate> midiTemplateClass;
  private static Class<?> componentClass;
  private static Path outputDirectory;
  private static volatile Throwable failure;
  private static volatile boolean completed;

  private RenderDeviceUI() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 1 || args.length > 2) {
      throw new IllegalArgumentException(
        "Usage: RenderDeviceUI <fully-qualified LXPattern or LXMidiTemplate class> [output-directory]");
    }
    loadComponentClass(args[0]);
    outputDirectory = args.length == 2 ? Path.of(args[1]) : Path.of("target", "ui-review");
    Files.createDirectories(outputDirectory);
    clearPreviousArtifacts(outputDirectory, componentClass.getSimpleName());
    if (patternClass != null) {
      installIsolatedFixtureMedia();
    }

    initializeInvisibleGlfw();
    final Thread controller = new Thread(RenderDeviceUI::control, "device UI render controller");
    controller.setDaemon(true);
    controller.start();

    launchChromatik();

    if (failure != null) {
      throw new IllegalStateException("Device UI render failed", failure);
    }
    if (!completed) {
      throw new IllegalStateException("Chromatik exited before the device UI render completed");
    }
  }

  static void clearPreviousArtifacts(Path directory, String baseName) throws IOException {
    Files.deleteIfExists(directory.resolve(baseName + ".png"));
    Files.deleteIfExists(directory.resolve(baseName + ".json"));
  }

  private static void installIsolatedFixtureMedia() throws IOException {
    if (!Files.isRegularFile(SOURCE_FIXTURE)) {
      throw new IOException("Apotheneum fixture does not exist: " + SOURCE_FIXTURE);
    }
    final Path destination = Files.createDirectories(
      Path.of(System.getProperty("user.home"), "Chromatik", "Fixtures"));
    try (Stream<Path> sources = Files.list(SOURCE_FIXTURE.getParent())) {
      for (Path source : sources.filter(Files::isRegularFile).toList()) {
        Files.copy(source, destination.resolve(source.getFileName()),
          StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private static void loadComponentClass(String className)
    throws ClassNotFoundException, NoSuchMethodException {
    final Class<?> candidate = RenderDeviceUI.class.getClassLoader().loadClass(className);
    candidate.getConstructor(LX.class);
    componentClass = candidate;
    if (LXPattern.class.isAssignableFrom(candidate)) {
      patternClass = candidate.asSubclass(LXPattern.class);
    } else if (LXMidiTemplate.class.isAssignableFrom(candidate)) {
      midiTemplateClass = candidate.asSubclass(LXMidiTemplate.class);
    } else {
      throw new IllegalArgumentException(className + " does not extend LXPattern or LXMidiTemplate");
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
        "The official Chromatik application runtime is required; use scripts/render-ui", cnfx);
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
      studio.engine.addTask(() -> prepareInstallationAndCapture(ui, finalStudio));
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

  private static void prepareInstallationAndCapture(LXStudio.UI ui, LXStudio studio) {
    try {
      if (patternClass == null) {
        prepareMidiTemplateAndCapture(ui, studio);
        return;
      }
      studio.structure.removeFixtures(List.copyOf(studio.structure.fixtures));
      final JsonFixture fixture = new JsonFixture(studio, FIXTURE_NAME);
      studio.structure.addFixture(fixture);
      studio.structure.beforeEngineRun();
      if (fixture.error.isOn()) {
        throw new IllegalStateException(
          "Fixture load failed: " + fixture.errorMessage.getString());
      }
      if (studio.engine.output.enabled.isOn()) {
        throw new IllegalStateException("Chromatik output became enabled during fixture load");
      }
      Apotheneum.initialize(studio);
      if (!Apotheneum.exists) {
        throw new IllegalStateException("Apotheneum geometry was unavailable after fixture load");
      }

      final LXPattern pattern = patternClass.getConstructor(LX.class).newInstance(studio);
      final LXChannel channel = studio.engine.mixer.addChannel(new LXPattern[] { pattern });
      studio.engine.mixer.setFocusedChannel(channel);
      ui.addLayer(new CaptureLayer(ui, studio, channel, pattern));
      log("pattern=" + patternClass.getName());
    } catch (Throwable x) {
      fail(studio, x);
    }
  }

  private static void prepareMidiTemplateAndCapture(LXStudio.UI ui, LXStudio studio) {
    try {
      final LXMidiTemplate template = midiTemplateClass.getConstructor(LX.class).newInstance(studio);
      studio.engine.midi.addTemplate(template);
      ui.addLayer(new CaptureLayer(ui, studio, template));
      log("midiTemplate=" + midiTemplateClass.getName());
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

    private CaptureLayer(
      LXStudio.UI ui, LXStudio studio, LXChannel channel, LXPattern pattern) {
      super(ui, 20, 100, 600, UIDeviceBin.HEIGHT);
      this.studio = studio;

      final UIDeviceBin bin = new UIDeviceBin(ui, channel);
      bin.addToContainer(this);
      this.device = findDevice(bin, pattern);
      if (this.device == null) {
        throw new IllegalStateException("Chromatik did not create a device UI for " + patternClass);
      }

      setSize(bin.getWidth(), bin.getHeight());
      this.pixelWidth = Math.round(getWidth() * ui.getContentScaleX());
      this.pixelHeight = Math.round(getHeight() * ui.getContentScaleY());
      this.pixels = ByteBuffer.allocateDirect(this.pixelWidth * this.pixelHeight * 4)
        .order(ByteOrder.nativeOrder());
    }

    private CaptureLayer(LXStudio.UI ui, LXStudio studio, LXMidiTemplate template) {
      super(ui, 20, 100, 600, 900);
      this.studio = studio;
      this.device = new UIMidiTemplate(ui, template, 200);
      this.device.addToContainer(this);

      setSize(this.device.getWidth(), this.device.getHeight());
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
        throw new IllegalStateException("Chromatik device UI framebuffer has no readable texture");
      }
      this.pixels.clear();
      final int readyFrame = BGFX.bgfx_read_texture(texture, this.pixels, 0, 0);
      this.readbackStarted = true;
      log(String.format(Locale.ROOT,
        "framebuffer=%d texture=%d pixels=%dx%d readyFrame=%d",
        framebuffer & 0xffff, texture & 0xffff, this.pixelWidth, this.pixelHeight, readyFrame));
    }

    private void writeArtifacts(UI ui) throws IOException {
      final String baseName = componentClass.getSimpleName();
      final Path pngPath = outputDirectory.resolve(baseName + ".png");
      final Path jsonPath = outputDirectory.resolve(baseName + ".json");

      writeDeviceImage(ui, pngPath);
      writeLayoutJson(ui, pngPath, jsonPath);
      log("png=" + pngPath.toAbsolutePath());
      log("json=" + jsonPath.toAbsolutePath());
    }

    private void writeDeviceImage(UI ui, Path path) throws IOException {
      final float scaleX = ui.getContentScaleX();
      final float scaleY = ui.getContentScaleY();
      final int sourceX = Math.round((this.device.getAbsoluteX() - getAbsoluteX()) * scaleX);
      final int sourceY = Math.round((this.device.getAbsoluteY() - getAbsoluteY()) * scaleY);
      final int width = Math.round(this.device.getWidth() * scaleX);
      final int height = Math.round(this.device.getHeight() * scaleY);
      if (sourceX < 0 || sourceY < 0 || sourceX + width > this.pixelWidth ||
        sourceY + height > this.pixelHeight) {
        throw new IllegalStateException(String.format(Locale.ROOT,
          "Device crop [%d,%d %dx%d] exceeds framebuffer %dx%d",
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
      document.addProperty("componentClass", componentClass.getName());
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

  private static UIPatternDevice findDevice(UIObject object, LXPattern pattern) {
    if (object instanceof UIPatternDevice device && device.pattern == pattern) {
      return device;
    }
    if (object instanceof UI2dContainer container) {
      for (UIObject child : container.getChildren()) {
        final UIPatternDevice found = findDevice(child, pattern);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
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
