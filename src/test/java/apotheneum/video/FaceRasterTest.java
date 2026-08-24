package apotheneum.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

class FaceRasterTest {

  @Test
  void includesPixelsInTheDoorArea() {
    final LXModel face = faceModel("cubeFrontExterior");
    final LXModel model = new LXModel(new LXModel[] { face }).reindexPoints();
    final int doorX = Apotheneum.Cube.DOOR_START_COLUMN;
    final int doorY = Apotheneum.GRID_HEIGHT - 1;

    final int[] indices = new FaceRaster().resolve(
      model,
      VideoSource.EXTERIOR_FRONT,
      doorX,
      doorY,
      1,
      1
    );

    assertEquals(face.children[doorX].points[doorY].index, indices[0]);
  }

  private static LXModel faceModel(String tag) {
    final LXModel[] columns = new LXModel[Apotheneum.GRID_WIDTH];
    final List<LXPoint> facePoints = new ArrayList<>(
      Apotheneum.GRID_WIDTH * Apotheneum.GRID_HEIGHT
    );
    for (int x = 0; x < columns.length; ++x) {
      final List<LXPoint> points = new ArrayList<>(Apotheneum.GRID_HEIGHT);
      for (int y = 0; y < Apotheneum.GRID_HEIGHT; ++y) {
        final LXPoint point = new LXPoint(x, y);
        points.add(point);
        facePoints.add(point);
      }
      columns[x] = new LXModel(points);
    }
    return new LXModel(facePoints, columns, tag);
  }
}
