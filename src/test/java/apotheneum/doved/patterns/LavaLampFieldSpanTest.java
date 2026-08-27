package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;

/**
 * The bound on a fused <i>rendered</i> blob radius in {@link LavaLamp}, which is a different
 * bound from the one on a fused simulated radius and was previously only the latter.
 *
 * <p>{@code merge()} declines to fuse a pair whose combined area would put the survivor's
 * {@code scale} past {@code MAX_SCALE}. It then computed {@code fusedRenderScale} from the two
 * bodies' {@code renderScale}s and committed it with no bound of its own. {@code renderScale}
 * eases toward {@code scale} rather than tracking it, so while the lamp is shrinking — which is
 * what a Volume reduction makes it do — every body's rendered radius lags <i>above</i> its
 * simulated one by about 20%. A pair that comfortably satisfies the area guard can therefore
 * fuse to a rendered radius past {@code MAX_SCALE}.</p>
 *
 * <p>That matters because {@code MAX_SCALE} is exactly what keeps {@code renderField}'s sweep
 * inside the cylinder. The sweep spans {@code 2 * renderScale * baseRadius * INFLUENCE_RADIUS}
 * unwrapped columns, at most {@code 2 * 2.5 * 12 * 2 = 120} — the cylinder's circumference —
 * so every column it visits wraps to a distinct one. Past that bound {@code floorMod} folds
 * columns onto themselves and a cell takes the same blob's contribution twice, which reads as a
 * seam of doubled density down the unwrap.</p>
 *
 * <p>Size is a live performance control, so the Size the lamp happens to be at when the
 * oversized radius is committed is not the Size that matters: the overrun is asserted at the
 * top of the Size range, which is one knob turn away at every instant of the run.</p>
 *
 * <p>Separate class from {@link LavaLampDoorTest} rather than a second method on it, because
 * {@code Apotheneum} holds its model state statically and {@code initialize()} returns early
 * once it has run — one {@code LX} per process. Surefire runs {@code reuseForks=false}, so a
 * separate class is a separate JVM and therefore a separate process.</p>
 */
public class LavaLampFieldSpanTest extends HeadlessLxTest {

  private static final double FIXED_DELTA_MS = 1000. / 60;
  /** Frames to grow the lamp to a full population of large bodies before Volume drops. */
  private static final int GROW_FRAMES = 900;
  /** Frames to run the Volume collapse, over which the fuse bound is asserted every frame. */
  private static final int COLLAPSE_FRAMES = 400;
  /** Floating-point slack on a bound the arithmetic lands on exactly when a body is at it. */
  private static final double TOLERANCE = 1e-9;
  /**
   * The blob seed to run on. Pinned because the defect turns on which pair of bodies happens
   * to be adjacent while the lamp is shrinking through the narrow band of scales that can
   * reach it — a regression test has to fail for a reason someone else can reproduce.
   */
  private static final String SEED = "7";
  /**
   * How large a body the lamp has to be holding when Volume drops, as a multiple of the
   * nominal radius. Below this the collapse never brings a pair through the band of scales
   * whose fusion can overrun the bound, and the run would pass without testing anything.
   */
  private static final double GROWN_BODY_SCALE = 2;
  /**
   * How far a rendered radius has to lag above its simulated one, in nominal radii, for a
   * frame to count as one where the defect's precondition actually held.
   */
  private static final double LAGGING_ROWS = .1;
  /**
   * Frames of that lag the collapse has to produce before the run counts as non-vacuous. Well
   * short of the roughly fifty the collapse actually takes to bring the area back to target,
   * since that count moves with the shrink rate and this only has to establish that the run
   * spent real time in the shrinking state rather than passing through it.
   */
  private static final int LAGGING_FRAMES = 25;

  @Test
  void aVolumeCollapseNeverFusesARenderedRadiusPastTheFieldSpanBound() throws IOException {
    final LX lx = newApotheneumLx();

    final String previousSeed = System.getProperty(LavaLamp.RENDER_SEED_PROPERTY);
    System.setProperty(LavaLamp.RENDER_SEED_PROPERTY, SEED);
    final LavaLamp lavaLamp;
    try {
      lavaLamp = new LavaLamp(lx);
    } finally {
      if (previousSeed == null) {
        System.clearProperty(LavaLamp.RENDER_SEED_PROPERTY);
      } else {
        System.setProperty(LavaLamp.RENDER_SEED_PROPERTY, previousSeed);
      }
    }
    final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { lavaLamp });
    channel.fader.setValue(1);
    lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

    // Grow at the smallest Size and the largest Volume: the area budget goes as the inverse
    // square of the nominal radius, so this is where the lamp holds the most bodies at the
    // largest scales, and where a collapse therefore brings the most pairs through the band
    // that can overrun the bound. Coalesce at the top both fuses readily and stops the
    // oversized survivors necking straight back apart.
    lavaLamp.size.setValue(lavaLamp.size.range.min);
    lavaLamp.volume.setValue(lavaLamp.volume.range.max);
    lavaLamp.coalesce.setValue(lavaLamp.coalesce.range.max);
    // Speed 3 is what the review render runs at: more simulated seconds per frame, so the
    // collapse covers more of the cycle than it would at the default.
    lavaLamp.speed.setValue(3);
    for (int frame = 0; frame < GROW_FRAMES; ++frame) {
      lx.engine.run();
    }
    final double grownScale = lavaLamp.largestBlobScale();
    assertTrue(
      grownScale >= GROWN_BODY_SCALE,
      "the lamp was only holding a body of " + grownScale + " nominal radii when Volume "
        + "dropped, so the collapse below never brings a pair through the band of scales "
        + "whose fusion can overrun the field span"
    );

    // The operator action: Volume to the bottom on a running, full lamp. reconcileVolume then
    // trims every body a fixed fraction per frame for as long as the area is over target,
    // which is what holds the rendered radii lagging above the simulated ones throughout.
    lavaLamp.volume.setValue(lavaLamp.volume.range.min);
    final double maximumSize = lavaLamp.size.range.max;
    int laggingFrames = 0;
    double worstRenderScale = 0;
    double worstOverrun = Double.NEGATIVE_INFINITY;
    for (int frame = 0; frame < COLLAPSE_FRAMES; ++frame) {
      lx.engine.run();
      worstRenderScale = Math.max(worstRenderScale, lavaLamp.largestBlobRenderScale());
      worstOverrun =
        Math.max(worstOverrun, lavaLamp.fieldColumnSpanOverrunAt(maximumSize));
      if (lavaLamp.largestRenderScaleLag() > LAGGING_ROWS) {
        ++laggingFrames;
      }
    }

    assertTrue(
      worstRenderScale <= LavaLamp.MAX_SCALE + TOLERANCE,
      "a merge committed a rendered radius of " + worstRenderScale + " nominal radii, past "
        + "the " + LavaLamp.MAX_SCALE + " the field sweep is bounded by, so renderField would "
        + "wrap columns onto themselves at the top of the Size range"
    );
    assertTrue(
      worstOverrun <= TOLERANCE,
      "the widest field sweep would have covered " + worstOverrun + " columns more than the "
        + "circumference at the top of the Size range, so floorMod folds columns onto "
        + "themselves and those cells take the same blob's contribution twice"
    );

    // Without this the run could pass by never shrinking at all, which is the only state in
    // which a rendered radius is above its simulated one and so the only state the bound can
    // be breached from.
    assertTrue(
      laggingFrames > LAGGING_FRAMES,
      "a rendered radius was more than " + LAGGING_ROWS + " nominal radii above its simulated "
        + "one on only " + laggingFrames + " of " + COLLAPSE_FRAMES + " frames, so this run "
        + "barely reached the state the fused-radius bound exists for"
    );
  }
}
