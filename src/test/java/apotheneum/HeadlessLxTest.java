package apotheneum;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;

/**
 * Shared headless-LX fixture. Each test constructs its own {@link LX} via
 * {@link #newHeadlessLx()}; the instance is disposed after each test. {@code new LX(...)}
 * starts a non-daemon MIDI device-update thread that, on macOS, contends on a static
 * CoreMIDI lock; without disposal these accumulate across tests and deadlock construction.
 * Every instance handed to {@link #track(LX)} is disposed, so none outlives its test.
 */
public abstract class HeadlessLxTest {

  private final List<LX> tracked = new ArrayList<LX>();

  protected LX newHeadlessLx() {
    return track(new LX(newModel()));
  }

  /**
   * Registers an externally constructed LX (e.g. with custom flags) for after-test disposal.
   * Every registered instance is disposed — a test comparing two configurations must not leak
   * the first one's device-update thread.
   */
  protected LX track(LX lx) {
    this.tracked.add(lx);
    return lx;
  }

  /** Override to customize the model. */
  protected LXModel newModel() {
    return new GridModel(8, 8);
  }

  @AfterEach
  final void disposeLx() {
    Throwable failure = null;
    // Reverse order, and keep going past a failure: a dispose that throws must not
    // strand the remaining instances' non-daemon threads. Throwable, not
    // RuntimeException — an Error here (a NoClassDefFoundError out of LX teardown,
    // say) would otherwise leave those threads alive and wedge the fork.
    for (int i = this.tracked.size() - 1; i >= 0; --i) {
      try {
        this.tracked.get(i).dispose();
      } catch (Throwable x) {
        if (failure == null) {
          failure = x;
        } else {
          failure.addSuppressed(x);
        }
      }
    }
    this.tracked.clear();
    // dispose() declares no checked exceptions, so the first two branches cover it;
    // the third only keeps the compiler happy.
    if (failure instanceof RuntimeException runtime) {
      throw runtime;
    } else if (failure instanceof Error error) {
      throw error;
    } else if (failure != null) {
      throw new AssertionError("LX disposal failed", failure);
    }
  }
}
