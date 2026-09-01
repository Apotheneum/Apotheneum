package apotheneum.video;

import heronarts.glx.ui.UI2dComponent;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPlugin;
import heronarts.lx.studio.LXStudio;

/**
 * Studio-only companion to {@link ApotheneumVideoPlugin}: a left-pane panel
 * that launches {@link VideoWallLauncher} on a chosen display. LX loads
 * plugins independently with no ordering guarantee, so this never holds a
 * reference to the core plugin instance — it resolves {@link ApotheneumVideo}
 * from the engine by path instead, and does nothing if the core plugin isn't
 * enabled.
 */
@LXPlugin.Name("Apotheneum Video Output UI")
public class ApotheneumVideoUIPlugin implements LXStudio.Plugin {

  private VideoWallLauncher launcher = null;
  private VideoWallLauncher previewLauncher = null;
  private UIVideoWallPanel panel = null;

  @Override
  public void initialize(LX lx) {}

  @Override
  public void initializeUI(LXStudio lx, LXStudio.UI ui) {}

  @Override
  public void onUIReady(LXStudio lx, LXStudio.UI ui) {
    final LXComponent child = lx.engine.getChild(ApotheneumVideo.PATH);
    if (!(child instanceof ApotheneumVideo)) {
      ApotheneumVideoPlugin.log("core video plugin not enabled; skipping video wall panel");
      return;
    }
    final ApotheneumVideo config = (ApotheneumVideo) child;
    this.launcher = new VideoWallLauncher(config, config.getPort());
    this.previewLauncher = new VideoWallLauncher(config, config.getPort());
    this.panel = new UIVideoWallPanel(
      ui, this.launcher, this.previewLauncher, config, ui.leftPane.global.getContentWidth());
    ui.leftPane.global.addChildren(this.panel);
  }

  /**
   * Builds the video wall panel for the UI render harness
   * ({@code apotheneum.render.RenderLeftPaneSection}), which cannot construct
   * one itself: the panel and its launcher are both package-private here.
   * Constructing a launcher starts no processes — it only holds the config and
   * port until something asks it to play.
   */
  public static UI2dComponent buildPanelForRender(LXStudio.UI ui, ApotheneumVideo config, float width) {
    return new UIVideoWallPanel(
      ui,
      new VideoWallLauncher(config, config.getPort()),
      new VideoWallLauncher(config, config.getPort()),
      config,
      width);
  }

  @Override
  public void dispose() {
    if (this.launcher != null) {
      this.launcher.stop();
      this.launcher = null;
    }
    if (this.previewLauncher != null) {
      this.previewLauncher.stop();
      this.previewLauncher = null;
    }
    if (this.panel != null) {
      this.panel.removeFromContainer();
      this.panel.dispose();
      this.panel = null;
    }
  }

}
