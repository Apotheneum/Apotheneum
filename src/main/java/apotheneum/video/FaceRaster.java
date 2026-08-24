package apotheneum.video;

import java.util.List;

import apotheneum.Apotheneum;
import heronarts.lx.model.LXModel;

/**
 * Flattens a rectangular crop of a {@link VideoSource} into an array of global
 * color buffer indices, so that producing a video frame is a straight gather.
 *
 * <p>The crop is resolved against the {@link LXModel} carried by the rendered
 * frame rather than against the mutable {@link Apotheneum} statics. That is what
 * makes this safe to use off the engine thread: a frame and its model are
 * consistent with each other, and comparing the model by identity detects a
 * model swap without needing a listener.
 */
final class FaceRaster {

  /** Index for a pixel with no lit LED behind it — rendered black. */
  static final int MASKED = -1;

  private LXModel model = null;
  private VideoSource source = null;
  private int x = -1;
  private int y = -1;
  private int width = -1;
  private int height = -1;
  private int[] indices = null;

  /**
   * Global color buffer indices for the crop, row-major from its top-left
   * corner. Rebuilt only when an input changes. Null when the source is absent
   * from the model.
   */
  int[] resolve(LXModel model, VideoSource source, int x, int y, int width, int height) {
    if ((model == this.model) && (source == this.source) && (x == this.x) && (y == this.y) &&
        (width == this.width) && (height == this.height)) {
      return this.indices;
    }
    this.model = model;
    this.source = source;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.indices = build(model, source, x, y, width, height);
    return this.indices;
  }

  /** Columns of every face in the source, laid end to end. Null if any is absent. */
  private static LXModel[] columnsOf(LXModel model, VideoSource source) {
    final LXModel[] columns = new LXModel[source.width()];
    int at = 0;
    for (String tag : source.tags) {
      final List<LXModel> match = model.sub(tag);
      if (match.isEmpty()) {
        return null;
      }
      // A face's children are its columns, top point first (Y=0 is the top).
      final LXModel[] face = match.get(0).children;
      System.arraycopy(face, 0, columns, at, Math.min(face.length, Apotheneum.GRID_WIDTH));
      at += Apotheneum.GRID_WIDTH;
    }
    return columns;
  }

  private static int[] build(LXModel model, VideoSource source, int x, int y, int width, int height) {
    final LXModel[] columns = columnsOf(model, source);
    if (columns == null) {
      return null;
    }

    final int[] indices = new int[width * height];
    for (int row = 0; row < height; ++row) {
      for (int col = 0; col < width; ++col) {
        final int faceX = x + col;
        final int faceY = y + row;
        final boolean inBounds =
          (faceX < columns.length) && (columns[faceX] != null) &&
          (faceY < columns[faceX].points.length);
        indices[col + row * width] =
          inBounds ? columns[faceX].points[faceY].index : MASKED;
      }
    }
    return indices;
  }

}
