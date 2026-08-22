package apotheneum.render;

import java.nio.file.Path;
import java.util.List;

import apotheneum.doved.patterns.Breaker;

/** Breaker's pattern-owned travel, seam, and cube-corner review clips. */
public final class BreakerRenderSpike {

  private static final List<RenderSpike.AnimatedClip<Breaker>> CLIPS = List.of(
    new RenderSpike.AnimatedClip<>("01-full-travel", Breaker::new, 6.5,
      (breaker, frame, progress, seconds) -> {
        breaker.breakAzimuth.setValue(0);
        breaker.snapToFaces.setValue(true);
        breaker.travelSpeed.setValue(.14);
        if (frame == 1) {
          breaker.breakWave.trigger();
        }
      }),

    new RenderSpike.AnimatedClip<>("02-seam-crossing", Breaker::new, 4,
      (breaker, frame, progress, seconds) -> {
        breaker.breakAzimuth.setValue(342);
        breaker.snapToFaces.setValue(false);
        breaker.travelSpeed.setValue(.14);
        if (frame == 1) {
          breaker.breakWave.trigger();
        }
      }),

    new RenderSpike.AnimatedClip<>("03-cube-corner", Breaker::new, 4,
      (breaker, frame, progress, seconds) -> {
        breaker.eventWidth.setValue(36);
        breaker.breakAzimuth.setValue(7.2);
        breaker.snapToFaces.setValue(false);
        breaker.travelSpeed.setValue(.14);
        if (frame == 1) {
          breaker.breakWave.trigger();
        }
      })
  );

  private BreakerRenderSpike() {
  }

  public static void main(String[] args) throws Exception {
    RenderSpike.renderClips(
      CLIPS,
      new RenderSpike.AnimatedOptions(Path.of("target/breaker-renders"), 3, 20)
    );
  }
}
