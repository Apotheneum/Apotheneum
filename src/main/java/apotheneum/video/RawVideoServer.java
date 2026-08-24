package apotheneum.video;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXEngine;
import heronarts.lx.color.LXColor;

/**
 * Serves the cropped picture as raw, uncompressed {@code rgb24} frames back to
 * back over a plain TCP socket — no header, no framing, no delimiters. Scaling
 * and letterboxing to the video processor's signal size is the consumer's job
 * (an ffmpeg {@code scale}/{@code pad} chain), not ours.
 *
 * <p>Each viewer gets its own {@link LXEngine.Frame}, {@link FaceRaster}, and
 * output buffer, and pulls from the engine's double buffer at its own rate via
 * {@link LXEngine#copyFrameThreadSafe}. Nothing is shared between connections,
 * and no work lands on the engine thread beyond that copy — so no
 * synchronization of our own is needed anywhere here.
 */
final class RawVideoServer {

  static final int DEFAULT_PORT = 7878;

  private static final String PORT_PROPERTY = "apotheneum.video.port";
  private static final int MAX_VIEWERS = 4;
  private static final int OUTPUT_BUFFER_SIZE = 1 << 16;

  private final LX lx;
  private final ApotheneumVideo config;
  private final ServerSocket server;
  private final ExecutorService executor;
  private final Thread acceptThread;
  private final Set<Socket> liveSockets = ConcurrentHashMap.newKeySet();

  private volatile boolean running = false;

  RawVideoServer(LX lx, ApotheneumVideo config) {
    this.lx = lx;
    this.config = config;

    final int port = port();
    try {
      this.server = new ServerSocket(port);
    } catch (IOException iox) {
      throw new UncheckedIOException("Apotheneum video output could not bind port " + port, iox);
    }

    // A SynchronousQueue rejects rather than queues once MAX_VIEWERS workers are
    // busy, so an accepted socket can never sit parked holding a file descriptor.
    this.executor = new ThreadPoolExecutor(
      MAX_VIEWERS,
      MAX_VIEWERS,
      0L,
      TimeUnit.MILLISECONDS,
      new SynchronousQueue<>(),
      runnable -> {
        final Thread thread = new Thread(runnable, "Apotheneum Video");
        thread.setDaemon(true);
        return thread;
      }
    );

    this.acceptThread = new Thread(this::acceptLoop, "Apotheneum Video Accept");
    this.acceptThread.setDaemon(true);
  }

  void start() {
    this.running = true;
    this.acceptThread.start();
    ApotheneumVideoPlugin.log("streaming raw rgb24 on tcp://localhost:" + this.server.getLocalPort());
  }

  /** The port actually bound — matches {@link #DEFAULT_PORT} unless overridden by system property. */
  int getPort() {
    return this.server.getLocalPort();
  }

  void stop() {
    this.running = false;
    try {
      // Unblocks the pending accept() in the loop thread.
      this.server.close();
    } catch (IOException iox) {
      // Already shutting down; nothing to do about a failed close.
    }
    // Ordinary socket writes aren't interruptible, so a worker blocked on a
    // stalled viewer's full send buffer won't respond to shutdownNow() alone —
    // closing its socket from here is what actually unblocks it.
    for (Socket socket : this.liveSockets) {
      try {
        socket.close();
      } catch (IOException iox) {
        // Best effort; the socket may already be closed.
      }
    }
    this.executor.shutdownNow();
  }

  private void acceptLoop() {
    while (this.running) {
      final Socket socket;
      try {
        socket = this.server.accept();
      } catch (IOException iox) {
        // Expected once stop() closes the server socket underneath us.
        break;
      }
      try {
        this.executor.execute(() -> serve(socket));
      } catch (RejectedExecutionException rex) {
        ApotheneumVideoPlugin.log("refused viewer: already serving " + MAX_VIEWERS);
        try {
          socket.close();
        } catch (IOException iox) {
          // Nothing to do about a failed close of a socket we're refusing anyway.
        }
      }
    }
  }

  private void serve(Socket socket) {
    // Locked for the life of the connection: the consumer is told the frame
    // size out-of-band on its command line, so it must never change mid-stream.
    final int cropWidth = this.config.cropWidth.getValuei();
    final int cropHeight = this.config.cropHeight.getValuei();
    final int frameBytes = cropWidth * cropHeight * 3;
    ApotheneumVideoPlugin.log(
      "viewer connected: " + cropWidth + "x" + cropHeight + " rgb24, " + frameBytes + " bytes/frame");

    final LXEngine.Frame frame = new LXEngine.Frame(this.lx);
    final FaceRaster raster = new FaceRaster();
    final byte[] buffer = new byte[frameBytes];

    this.liveSockets.add(socket);
    try (Socket s = socket) {
      final OutputStream out = new BufferedOutputStream(s.getOutputStream(), OUTPUT_BUFFER_SIZE);
      long nextFrameNanos = System.nanoTime();
      while (this.running) {
        if (this.config.enabled.isOn()) {
          this.lx.engine.copyFrameThreadSafe(frame);
          final int[] indices = raster.resolve(
            frame.getModel(),
            this.config.source.getEnum(),
            this.config.cropX.getValuei(),
            this.config.cropY.getValuei(),
            cropWidth,
            cropHeight
          );
          fill(buffer, frame.getMain(), indices);
          bridgeDoorAreas(
            buffer,
            this.config.source.getEnum(),
            this.config.cropX.getValuei(),
            this.config.cropY.getValuei(),
            cropWidth,
            cropHeight
          );
        } else {
          // Hold the connection open so re-enabling resumes without a reconnect.
          Arrays.fill(buffer, (byte) 0);
        }

        out.write(buffer);
        out.flush();

        final long periodNanos = (long) (1_000_000_000.0 / this.config.fps.getValue());
        nextFrameNanos += periodNanos;

        final long sleepNanos = nextFrameNanos - System.nanoTime();
        if (sleepNanos > 0) {
          Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
        } else if (sleepNanos < -periodNanos) {
          // More than a whole frame behind — a stalled consumer or a paused engine.
          // Resync to now rather than bursting frames to catch up.
          nextFrameNanos = System.nanoTime();
        }
      }
    } catch (IOException iox) {
      // Viewer went away, or we were stopped mid-write. Neither is worth logging.
    } catch (InterruptedException ix) {
      Thread.currentThread().interrupt();
    } finally {
      this.liveSockets.remove(socket);
    }
  }

  /** Gathers colors into {@code rgb24}; masked or out-of-range indices render black. */
  private static void fill(byte[] buffer, int[] colors, int[] indices) {
    if (indices == null) {
      Arrays.fill(buffer, (byte) 0);
      return;
    }
    int at = 0;
    for (int index : indices) {
      final int color = ((index >= 0) && (index < colors.length)) ? colors[index] : LXColor.BLACK;
      buffer[at++] = (byte) ((color >> 16) & 0xff);
      buffer[at++] = (byte) ((color >> 8) & 0xff);
      buffer[at++] = (byte) (color & 0xff);
    }
  }

  /**
   * Reconstructs the virtual picture behind each cube doorway by blending the
   * nearest real columns on either side. The master {@code Doors} effect has
   * already blacked the doorway pixels by the time the engine frame reaches
   * this server, so merely gathering their model indices still leaves a black
   * door-shaped hole in the exported video.
   */
  static void bridgeDoorAreas(
    byte[] buffer,
    VideoSource source,
    int cropX,
    int cropY,
    int cropWidth,
    int cropHeight
  ) {
    final int doorTop = Apotheneum.GRID_HEIGHT - Apotheneum.DOOR_HEIGHT;
    final int firstRow = Math.max(0, doorTop - cropY);
    if (firstRow >= cropHeight) {
      return;
    }

    final int faceCount = source.width() / Apotheneum.GRID_WIDTH;
    for (int face = 0; face < faceCount; ++face) {
      final int doorStart = face * Apotheneum.GRID_WIDTH + Apotheneum.Cube.DOOR_START_COLUMN;
      final int doorEnd = doorStart + Apotheneum.DOOR_WIDTH;
      final int left = doorStart - 1 - cropX;
      final int right = doorEnd - cropX;
      if ((left < 0) || (right >= cropWidth)) {
        // A partial crop that excludes either side of the doorway does not
        // contain enough image data to reconstruct the missing span.
        continue;
      }

      final int firstColumn = Math.max(0, doorStart - cropX);
      final int lastColumn = Math.min(cropWidth, doorEnd - cropX);
      for (int row = firstRow; row < cropHeight; ++row) {
        final int leftAt = (left + row * cropWidth) * 3;
        final int rightAt = (right + row * cropWidth) * 3;
        for (int column = firstColumn; column < lastColumn; ++column) {
          final int step = column + cropX - doorStart + 1;
          final int at = (column + row * cropWidth) * 3;
          for (int channel = 0; channel < 3; ++channel) {
            final int leftValue = buffer[leftAt + channel] & 0xff;
            final int rightValue = buffer[rightAt + channel] & 0xff;
            buffer[at + channel] = (byte) (
              (leftValue * (Apotheneum.DOOR_WIDTH + 1 - step) + rightValue * step)
                / (Apotheneum.DOOR_WIDTH + 1)
            );
          }
        }
      }
    }
  }

  private static int port() {
    final String property = System.getProperty(PORT_PROPERTY);
    if (property != null) {
      try {
        return Integer.parseInt(property);
      } catch (NumberFormatException nfx) {
        ApotheneumVideoPlugin.error("Ignoring invalid " + PORT_PROPERTY + ": " + property);
      }
    }
    return DEFAULT_PORT;
  }

}
