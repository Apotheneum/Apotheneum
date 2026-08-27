package apotheneum.render;

/**
 * Convenience entry point for reviewing Breaker through {@link RenderSpike}'s
 * standard harness. {@code break=true} fires the {@code breakWave} trigger
 * parameter -- {@code setParameterValue} calls {@code setValue(true)}, which
 * fires the trigger's listener before frame 1 -- launching one bounded
 * single-pass wave (a 4.8s event inside the harness's fixed five-second render
 * window) so the review exercises the full approach/collapse/wash arc rather
 * than only the self-starting Circle mode; none of that lives in the shared
 * harness.
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
      "break=true",
      "",
      "",
      "",
      ""
    });
  }
}
