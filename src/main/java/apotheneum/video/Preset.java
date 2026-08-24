package apotheneum.video;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;

/**
 * One configured "look" for the video wall: which source feeds it, and how
 * that source is laid out and mapped onto the processor's native signal.
 *
 * <p>{@link ApotheneumVideo} holds two of these, {@code presetA} and
 * {@code presetB}, plus a selector for which is active — so an operator can
 * stage a second look (e.g. interior panels vs. exterior fit) in advance and
 * switch to it in one step, rather than re-dialing five controls live. Each
 * is a registered child component, so its parameters get their own canonical
 * paths (e.g. <code>/lx/apotheneumVideo/presetA/layout</code>), addressable
 * over OSC and MCP exactly like everything else.
 *
 * <p>{@code cropX}/{@code cropY}/{@code cropWidth}/{@code cropHeight},
 * {@code fps}, {@code enabled}, and {@code maskDoor} are not here: they
 * describe the feed itself, not the wall's presentation of it, so they stay
 * global on {@link ApotheneumVideo}.
 */
public class Preset extends LXComponent {

  public final EnumParameter<VideoSource> source;
  public final EnumParameter<ApotheneumVideo.Layout> layout;
  public final EnumParameter<ApotheneumVideo.Processor> processor;
  public final DiscreteParameter gap;
  public final DiscreteParameter panelCount;

  Preset(
    LX lx,
    VideoSource source,
    ApotheneumVideo.Layout layout,
    ApotheneumVideo.Processor processor,
    int gap,
    int panelCount
  ) {
    super(lx);

    this.source = new EnumParameter<VideoSource>("Source", source)
      .setDescription("Which cube face, or all four wrapped into one strip");

    this.layout = new EnumParameter<ApotheneumVideo.Layout>("Layout", layout)
      .setDescription(
        "How the crop is arranged on the wall: Panels splits it into four faces spread across the "
        + "width with gaps between them; Fit scales the whole crop to fit the wall's width or height "
        + "whichever binds first, letterboxing the rest; Fill scales it to cover the wall completely, "
        + "cropping whatever overhangs");

    this.processor = new EnumParameter<ApotheneumVideo.Processor>("Processor", processor)
      .setDescription(
        "How the laid-out picture reaches the video processor's native signal height: Top 336px sits "
        + "the picture in the top 336px of the signal with a black bar below, and has correct "
        + "proportions on a preview monitor; Shrink 600→336 pre-stretches the picture to fill all 600px "
        + "so that the processor's own vertical squash restores it on the wall — it looks vertically "
        + "stretched on a preview monitor, and that is expected: it only looks correct once it reaches "
        + "the wall");

    this.gap = new DiscreteParameter("Gap", gap, 0, 601)
      .setDescription(
        "Maximum gap in pixels between panels, and at the outer edges — only used by Panels layout. "
        + "Automatically reduced when the panel count needs the space so faces never shrink below the "
        + "largest size the wall's height allows");

    this.panelCount = new DiscreteParameter("Panel Count", panelCount, 4, 13)
      .setDescription(
        "Number of panels to spread across the wall, cycling through the four faces — only used by "
        + "Panels layout");

    addParameter("source", this.source);
    addParameter("layout", this.layout);
    addParameter("processor", this.processor);
    addParameter("gap", this.gap);
    addParameter("panelCount", this.panelCount);
  }

}
