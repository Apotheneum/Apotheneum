package apotheneum.render;

/**
 * Convenience entry point for reviewing Flood through {@link RenderSpike}'s
 * standard harness. Sweeps the water level from empty to full over the
 * harness's fixed five-second render window using its built-in
 * {@code -Dmodulate=} support -- one full sweep at 0.2 cycles/second -- so no
 * pattern-specific rendering code lives here or in the shared harness.
 *
 * <p>Run directly on the built test classpath (see "Invoke RenderSpike
 * directly for -D properties and tight iteration" in
 * docs/headless-rendering.md):
 * {@code java -Djava.awt.headless=true -cp <test-classpath> apotheneum.render.FloodRenderSpike}
 */
public final class FloodRenderSpike {

  private FloodRenderSpike() {
  }

  public static void main(String[] args) throws Exception {
    RenderSpike.main(new String[] {
      "apotheneum.doved.patterns.Flood",
      "",
      "",
      "",
      "",
      "level:0.2"
    });
  }
}
