package apotheneum.render;

import java.nio.file.Path;
import java.util.List;

import apotheneum.doved.patterns.Flood;

/**
 * Flood's pattern-owned animated review clips. The fixture, frame loop,
 * surface rendering, crops, and GIF assembly live in {@link RenderSpike}, so
 * another pattern can add its own catalog without changing this file or the
 * shared harness.
 */
public final class FloodRenderSpike {

  private static final double FIXED_DELTA_SECONDS = 1. / 60.;

  private static final List<RenderSpike.AnimatedClip<Flood>> CLIPS = List.of(
    new RenderSpike.AnimatedClip<>("01-slow-fill", Flood::new, 7, (flood, frame, progress, t) -> {
      flood.level.setValue(progress);
      flood.agitation.setValue(.35);
    }),

    new RenderSpike.AnimatedClip<>("02-surge-lap", Flood::new, 7, (flood, frame, progress, t) -> {
      flood.level.setValue(.5);
      flood.surgeSpeed.setValue(.16);
      flood.surgeWidth.setValue(.06);
      flood.surgeAngle.setValue(0);
      if (frame == 1) {
        flood.surge.trigger();
      }
    }),

    new RenderSpike.AnimatedClip<>("03-glassy-vs-choppy", Flood::new, 8, (flood, frame, progress, t) -> {
      flood.level.setValue(.5);
      flood.agitation.setValue(.05 + .9 * progress);
    }),

    new RenderSpike.AnimatedClip<>("04-sliver-surge", Flood::new, 8, (flood, frame, progress, t) -> {
      flood.level.setValue(.5);
      flood.surgeWidth.setValue(.02);
      flood.surgeSpeed.setValue(.7);
      flood.surgeHeight.setValue(8);
      flood.agitation.setValue(.15);
      // One full-width surge takes (1 + surgeWidth) / surgeSpeed seconds to
      // finish its lap; retrigger a beat after that so a new one launches
      // as the previous one closes out, reading as a repeating ring.
      final double period = 1.5;
      final double sinceLast = t % period;
      if (frame == 1 || sinceLast < FIXED_DELTA_SECONDS) {
        flood.surge.trigger();
      }
    }),

    new RenderSpike.AnimatedClip<>("05-ankle-deep", Flood::new, 6, (flood, frame, progress, t) -> {
      flood.level.setValue(.15);
      flood.meniscusWidth.setValue(.5);
      flood.sparkle.setValue(.85);
      flood.agitation.setValue(.2);
    }),

    new RenderSpike.AnimatedClip<>("06-near-submerged", Flood::new, 6, (flood, frame, progress, t) -> {
      flood.level.setValue(.85);
      flood.meniscusWidth.setValue(4);
      flood.depthFalloff.setValue(.92);
      flood.agitation.setValue(.3);
    }),

    new RenderSpike.AnimatedClip<>("07-undulation", Flood::new, 6, (flood, frame, progress, t) -> {
      flood.level.setValue(.5);
      flood.agitation.setValue(1);
      flood.meniscusWidth.setValue(1);
    }),

    new RenderSpike.AnimatedClip<>("08-seam-surge", Flood::new, 6, (flood, frame, progress, t) -> {
      flood.level.setValue(.5);
      flood.agitation.setValue(.25);
      flood.surgeAngle.setValue(342);
      flood.surgeWidth.setValue(.1);
      flood.surgeSpeed.setValue(.22);
      flood.surgeHeight.setValue(5);
      if (frame == 1) {
        flood.surge.trigger();
      }
    })
  );

  private FloodRenderSpike() {
  }

  public static void main(String[] args) throws Exception {
    RenderSpike.renderClips(
      CLIPS,
      new RenderSpike.AnimatedOptions(Path.of("target/flood-renders"), 3, 20)
    );
  }
}
