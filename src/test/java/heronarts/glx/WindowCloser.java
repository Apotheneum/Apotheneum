package heronarts.glx;

/** Test-scope bridge to the package-private GLX window shutdown API. */
public final class WindowCloser {
  private WindowCloser() {}

  public static void close(WindowEngine windowEngine) {
    windowEngine.setShouldClose(true);
  }
}
