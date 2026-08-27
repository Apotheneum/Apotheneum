package apotheneum.render;

/**
 * Convenience entry point for reviewing Breaker through {@link RenderSpike}'s
 * standard harness. Circle mode is self-starting -- the wave relaunches itself
 * in place each time it completes a lap -- so a static parameter assignment is
 * enough to show continuous motion within the harness's fixed five-second
 * render window; no trigger call or modulation is required, and none of that
 * lives in the shared harness.
 *
 * <p>Run directly on the built test classpath (see "Invoke RenderSpike
 * directly for -D properties and tight iteration" in
 * docs/headless-rendering.md):
 * {@code java -Djava.awt.headless=true -cp <test-classpath> apotheneum.render.BreakerRenderSpike}
 */
public final class BreakerRenderSpike {

  private BreakerRenderSpike() {
  }

  public static void main(String[] args) throws Exception {
    RenderSpike.main(new String[] {
      "apotheneum.doved.patterns.Breaker",
      "circle=true",
      "",
      "",
      "",
      ""
    });
  }
}
