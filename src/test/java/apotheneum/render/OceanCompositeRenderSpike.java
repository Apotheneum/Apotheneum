package apotheneum.render;

import java.nio.file.Path;
import java.util.List;

import apotheneum.doved.patterns.Breaker;
import apotheneum.doved.patterns.Flood;
import apotheneum.doved.patterns.Undersea;

/**
 * Review clips for the ocean patterns alone and composited on one blend-mode
 * channel. The Flood/Undersea sweep uses their distinct Level macro semantics
 * to keep the physical waterline aligned from fully submerged to level .62.
 */
public final class OceanCompositeRenderSpike {

  private static final double FLOOD_UNDERSEA_SECONDS = 7;
  private static final double BREAKER_SECONDS = 10;
  private static final int BREAKER_TRIGGER_FRAME = (int) (BREAKER_SECONDS * 60 / 2);

  private static final List<RenderSpike.CompositeClip> CLIPS = List.of(
    new RenderSpike.CompositeClip(
      "01-solo-flood-shared-sweep",
      "Normal",
      FLOOD_UNDERSEA_SECONDS,
      List.of(floodSweepLayer())
    ),
    new RenderSpike.CompositeClip(
      "02-solo-undersea-shared-sweep",
      "Normal",
      FLOOD_UNDERSEA_SECONDS,
      List.of(underseaSweepLayer())
    ),
    new RenderSpike.CompositeClip(
      "03-flood-undersea-add",
      "Add",
      FLOOD_UNDERSEA_SECONDS,
      List.of(floodSweepLayer(), underseaSweepLayer())
    ),
    new RenderSpike.CompositeClip(
      "04-flood-undersea-lightest",
      "Lightest",
      FLOOD_UNDERSEA_SECONDS,
      List.of(floodSweepLayer(), underseaSweepLayer())
    ),
    new RenderSpike.CompositeClip(
      "05-flood-undersea-dodge",
      "Dodge",
      FLOOD_UNDERSEA_SECONDS,
      List.of(floodSweepLayer(), underseaSweepLayer())
    ),
    new RenderSpike.CompositeClip(
      "06-solo-flood-breaker-level",
      "Normal",
      BREAKER_SECONDS,
      List.of(floodBreakerLayer())
    ),
    new RenderSpike.CompositeClip(
      "07-solo-breaker",
      "Normal",
      BREAKER_SECONDS,
      List.of(breakerLayer())
    ),
    new RenderSpike.CompositeClip(
      "08-flood-breaker-lightest",
      "Lightest",
      BREAKER_SECONDS,
      List.of(floodBreakerLayer(), breakerLayer())
    )
  );

  private OceanCompositeRenderSpike() {
  }

  private static RenderSpike.CompositeLayer<Flood> floodSweepLayer() {
    return new RenderSpike.CompositeLayer<>("Flood", Flood::new, 1, (flood, frame, progress, t) -> {
      flood.level.setValue(1 - .38 * sweepProgress(t));
    });
  }

  private static RenderSpike.CompositeLayer<Undersea> underseaSweepLayer() {
    return new RenderSpike.CompositeLayer<>(
      "Undersea",
      Undersea::new,
      1,
      (undersea, frame, progress, t) -> undersea.level.setValue(.82 + .18 * sweepProgress(t))
    );
  }

  private static RenderSpike.CompositeLayer<Flood> floodBreakerLayer() {
    return new RenderSpike.CompositeLayer<>("Flood", Flood::new, 1, (flood, frame, progress, t) -> {
      flood.level.setValue(.5);
    });
  }

  private static RenderSpike.CompositeLayer<Breaker> breakerLayer() {
    return new RenderSpike.CompositeLayer<>("Breaker", Breaker::new, 1, (breaker, frame, progress, t) -> {
      breaker.level.setValue(.5);
      breaker.breakAzimuth.setValue(0);
      breaker.snapToFaces.setValue(true);
      if (frame == BREAKER_TRIGGER_FRAME) {
        breaker.breakWave.trigger();
      }
    });
  }

  private static double sweepProgress(double elapsedSeconds) {
    final double linear = Math.max(0, Math.min(1, (elapsedSeconds - 2) / 3));
    return linear * linear * (3 - 2 * linear);
  }

  public static void main(String[] args) throws Exception {
    RenderSpike.renderCompositeClips(
      CLIPS,
      new RenderSpike.AnimatedOptions(Path.of("target/ocean-composite-renders"), 3, 20)
    );
  }
}
