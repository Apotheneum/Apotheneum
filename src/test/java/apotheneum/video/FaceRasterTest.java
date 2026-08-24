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
    final LXModel model = exteriorPerimeterModel();
    final int doorX = Apotheneum.Cube.DOOR_START_COLUMN;
    final int doorY = Apotheneum.GRID_HEIGHT - 1;

    final int[] indices = new FaceRaster().resolve(
      model,
      VideoSource.EXTERIOR_PERIMETER,
      doorX,
      doorY,
      1,
      1
    );

    assertEquals(model.sub("cubeFrontExterior").get(0).children[doorX].points[doorY].index, indices[0]);
  }

  @Test
  void reconstructsDoorAreaFromAdjacentColumns() {
    final int width = Apotheneum.GRID_WIDTH;
    final int height = Apotheneum.GRID_HEIGHT;
    final byte[] frame = new byte[width * height * 3];
    final int row = height - 1;
    setPixel(frame, width, Apotheneum.Cube.DOOR_START_COLUMN - 1, row, 0, 22, 44);
    setPixel(
      frame,
      width,
      Apotheneum.Cube.DOOR_START_COLUMN + Apotheneum.DOOR_WIDTH,
      row,
      110,
      132,
      154
    );

    RawVideoServer.bridgeDoorAreas(
      frame,
      VideoSource.EXTERIOR_PERIMETER,
      0,
      0,
      width,
      height
    );

    assertPixel(frame, width, Apotheneum.Cube.DOOR_START_COLUMN, row, 10, 32, 54);
    assertPixel(
      frame,
      width,
      Apotheneum.Cube.DOOR_START_COLUMN + Apotheneum.DOOR_WIDTH - 1,
      row,
      100,
      122,
      144
    );
  }

  private static void setPixel(byte[] frame, int width, int x, int y, int red, int green, int blue) {
    final int at = (x + y * width) * 3;
    frame[at] = (byte) red;
    frame[at + 1] = (byte) green;
    frame[at + 2] = (byte) blue;
  }

  private static void assertPixel(
    byte[] frame,
    int width,
    int x,
    int y,
    int red,
    int green,
    int blue
  ) {
    final int at = (x + y * width) * 3;
    assertEquals(red, frame[at] & 0xff);
    assertEquals(green, frame[at + 1] & 0xff);
    assertEquals(blue, frame[at + 2] & 0xff);
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

  private static LXModel exteriorPerimeterModel() {
    return new LXModel(new LXModel[] {
      faceModel("cubeFrontExterior"),
      faceModel("cubeRightExterior"),
      faceModel("cubeBackExterior"),
      faceModel("cubeLeftExterior")
    }).reindexPoints();
  }
}
