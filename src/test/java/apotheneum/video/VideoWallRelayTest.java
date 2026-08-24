package apotheneum.video;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class VideoWallRelayTest {

  private static final int TEST_FRAME_BYTES = 3;

  @Test
  void retainsOldSourceUntilReplacementHasACompleteFrame() throws Exception {
    final ControlledFrameSource oldSource = new ControlledFrameSource();
    final ControlledFrameSource newSource = new ControlledFrameSource();
    final ByteArrayOutputStream displayed = new ByteArrayOutputStream();
    final AtomicInteger firstFrameCallbacks = new AtomicInteger();
    final VideoWallLauncher.FrameRelay relay = new VideoWallLauncher.FrameRelay(
      oldSource,
      displayed,
      TEST_FRAME_BYTES,
      firstFrameCallbacks::incrementAndGet
    );

    relay.start();
    try {
      oldSource.write(1, 1, 1);
      awaitSize(displayed, 3);

      assertTrue(relay.switchWhenReady(newSource));
      newSource.write(2, 2);

      // A partial replacement frame is not a switch point: the active source
      // must remain connected and continue feeding the display.
      oldSource.write(1, 1, 1);
      awaitSize(displayed, 6);
      assertFalse(oldSource.isStopped());

      newSource.write(2);
      awaitSize(displayed, 9);
      assertTrue(oldSource.isStopped());

      newSource.write(3, 3, 3);
      awaitSize(displayed, 12);

      assertArrayEquals(new byte[] {
        1, 1, 1,
        1, 1, 1,
        2, 2, 2,
        3, 3, 3,
      }, displayed.toByteArray());
      assertEquals(1, firstFrameCallbacks.get());
    } finally {
      relay.stop();
    }
  }

  @Test
  void newerReplacementSupersedesOneThatIsStillWarming() throws Exception {
    final ControlledFrameSource oldSource = new ControlledFrameSource();
    final ControlledFrameSource superseded = new ControlledFrameSource();
    final ControlledFrameSource latest = new ControlledFrameSource();
    final ByteArrayOutputStream displayed = new ByteArrayOutputStream();
    final VideoWallLauncher.FrameRelay relay = new VideoWallLauncher.FrameRelay(
      oldSource,
      displayed,
      TEST_FRAME_BYTES,
      null
    );

    relay.start();
    try {
      oldSource.write(1, 1, 1);
      awaitSize(displayed, 3);

      assertTrue(relay.switchWhenReady(superseded));
      superseded.write(2);
      assertTrue(relay.switchWhenReady(latest));
      awaitStopped(superseded);

      latest.write(3, 3, 3);
      awaitSize(displayed, 6);

      assertArrayEquals(new byte[] { 1, 1, 1, 3, 3, 3 }, displayed.toByteArray());
      assertEquals(1, superseded.stopCount());
    } finally {
      relay.stop();
    }
  }

  private static void awaitSize(ByteArrayOutputStream output, int expected) {
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      while (output.size() < expected) {
        Thread.onSpinWait();
      }
    });
    assertEquals(expected, output.size());
  }

  private static void awaitStopped(ControlledFrameSource source) {
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      while (!source.isStopped()) {
        Thread.onSpinWait();
      }
    });
  }

  private static final class ControlledFrameSource implements VideoWallLauncher.FrameSource {

    private final PipedInputStream input;
    private final PipedOutputStream output;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicInteger stopCount = new AtomicInteger(0);

    private ControlledFrameSource() throws IOException {
      this.input = new PipedInputStream();
      this.output = new PipedOutputStream(this.input);
    }

    private void write(int... bytes) throws IOException {
      for (int value : bytes) {
        this.output.write(value);
      }
      this.output.flush();
    }

    @Override
    public InputStream input() {
      return this.input;
    }

    @Override
    public void stop() {
      if (!this.stopped.compareAndSet(false, true)) {
        return;
      }
      this.stopCount.incrementAndGet();
      try {
        this.output.close();
      } catch (IOException iox) {
        // Already closed by the test or relay.
      }
      try {
        this.input.close();
      } catch (IOException iox) {
        // Already closed by the test or relay.
      }
    }

    private boolean isStopped() {
      return this.stopped.get();
    }

    private int stopCount() {
      return this.stopCount.get();
    }
  }
}
