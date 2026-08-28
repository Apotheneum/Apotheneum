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
 *
 * <p>{@link #newHeadlessLx()} also waits out a second, JDK-level hazard before constructing:
 * {@code new LX(...)} spawns a short-lived {@code "LXMidiEngine Device Initialization"}
 * thread, and that thread's very first call into {@code javax.sound.sampled.AudioSystem}
 * (shared JDK Sound plumbing between MIDI and audio) goes through
 * {@code com.sun.media.sound.JSSecurityManager.getProviders}, which takes two different
 * class-level monitors in opposing order depending on which service type is being resolved.
 * One such thread finishes this uncontended in well under a millisecond. Two of them doing it
 * at once — this test's own construction and the previous test's still-finishing one — can
 * each grab one monitor and block on the other: a real, JDK-internal deadlock, confirmed by
 * `jstack` on this environment, not a hypothetical. Once that first cycle forms every later
 * construction's thread piles up blocked on the same two monitors, wedging the fork for the
 * rest of the run. A one-time warm-up call does not help, since the lock is taken on every
 * call, not only the first; disposal already existed to prevent exactly this kind of pile-up
 * but does not block on that specific short-lived thread, leaving a window a fast enough
 * {@code @BeforeEach}/{@code @AfterEach} pair can still land inside. Waiting for any prior
 * instance's initialization thread to finish before starting a new one removes the second
 * thread from the race instead of trying to outrun it.
 */
public abstract class HeadlessLxTest {

  /**
   * Name LX gives the short-lived thread that does the device scan {@link #newHeadlessLx()}
   * must not overlap with a new one of its own. Matched by exact name, not by owning class,
   * since nothing in this package can address the thread object LX creates internally.
   */
  private static final String MIDI_INIT_THREAD_NAME = "LXMidiEngine Device Initialization";

  private static final long MIDI_INIT_JOIN_TIMEOUT_MS = 5_000;

  private final List<LX> tracked = new ArrayList<LX>();

  protected LX newHeadlessLx() {
    awaitMidiDeviceInitializationThreads();
    return track(new LX(newModel()));
  }

  /**
   * Blocks until no thread named {@value #MIDI_INIT_THREAD_NAME} is still running. Under
   * normal, uncontended conditions such a thread finishes almost immediately, so this costs
   * nothing measurable; its entire purpose is to guarantee at most one such thread is ever
   * alive at a time; see this class's javadoc for the deadlock two overlapping ones can hit.
   */
  private static void awaitMidiDeviceInitializationThreads() {
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if (MIDI_INIT_THREAD_NAME.equals(thread.getName()) && thread.isAlive()) {
        try {
          thread.join(MIDI_INIT_JOIN_TIMEOUT_MS);
        } catch (InterruptedException ix) {
          Thread.currentThread().interrupt();
        }
      }
    }
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
