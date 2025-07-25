package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.Apotheneum.Cylinder.Ring;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Syntax Breaks")
public class SyntaxBreaks extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.1, 0.0, 1.0)
    .setDescription("Animation speed (cycles per second)");
  private final DiscreteParameter tiles = new DiscreteParameter("Tiles", 10, 2, 30)
    .setDescription("Number of tiles per row");
  private final CompoundParameter disrupt = new CompoundParameter("Disrupt", 0.2, 0.0, 1.0)
    .setDescription("Amount of grid disruption");
  private final CompoundParameter sat = new CompoundParameter("Sat", 100.0, 0.0, 100.0)
    .setDescription("Color saturation percentage");
  private final BooleanParameter edgesOnly = new BooleanParameter("Edges", false)
    .setDescription("Draw only tile edges");

public SyntaxBreaks(LX lx) {
  super(lx);
  addParameter("Speed", this.speed);
  addParameter("Tiles", this.tiles);
  addParameter("Disrupt", this.disrupt);
  addParameter("Sat", this.sat);
  addParameter("Edges", this.edgesOnly);
}

  @Override
  protected void render(double deltaMs) {
    float time = (float)((lx.engine.nowMillis % 60000) / 1000.0) * speed.getValuef();

    Cube cube = Apotheneum.cube;
    if (cube != null) {
      for (Face face : cube.exterior.faces) {
        processFace(face, time);
      }
      if (cube.interior != null) {
        for (Face face : cube.interior.faces) {
          processFace(face, time);
        }
      }
    }

    Cylinder cyl = Apotheneum.cylinder;
    if (cyl != null) {
      Cylinder.Orientation[] cylFaces = (cyl.interior != null)
        ? new Cylinder.Orientation[]{cyl.exterior, cyl.interior}
        : new Cylinder.Orientation[]{cyl.exterior};
      for (Cylinder.Orientation orientation : cylFaces) {
        Ring[] rings = orientation.rings;
        int numRings = rings.length;
        for (int ringIdx = 0; ringIdx < numRings; ringIdx++) {
          processRing(rings[ringIdx], ringIdx, numRings, time);
        }
      }
    }
  }

  private void processFace(Face face, float time) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);
    int tileCount = tiles.getValuei();
    float disruptAmt = disrupt.getValuef();
    float saturation = sat.getValuef();
    boolean onlyEdges = edgesOnly.getValueb();

    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        float u = cx * invCols;
        float v = row.index * invRows;

        // Compute jittered UV for disruption
        float jitterU = u + disruptAmt * (float)Math.sin(time + u * tileCount);
        float jitterV = v + disruptAmt * (float)Math.cos(time + v * tileCount);

        // Determine cell and edge
        float fracU = (jitterU * tileCount) - (float)Math.floor(jitterU * tileCount);
        float fracV = (jitterV * tileCount) - (float)Math.floor(jitterV * tileCount);
        boolean isEdge = (fracU < 0.1f || fracU > 0.9f || fracV < 0.1f || fracV > 0.9f);

        float brightness = onlyEdges ? (isEdge ? 100f : 0f) : (isEdge ? 100f : 50f);
        int hue = (int)(360 * (jitterU - (float)Math.floor(jitterU)));

        colors[p.index] = LXColor.hsb(hue, saturation, brightness);
      }
    }
  }

  private void processRing(Ring ring, int ringIdx, int numRings, float time) {
    int pointsPerRing = ring.points.length;
    float inv = 1.0f / Math.max(1, pointsPerRing - 1);
    int tileCount = tiles.getValuei();
    float disruptAmt = disrupt.getValuef();
    float saturation = sat.getValuef();
    boolean onlyEdges = edgesOnly.getValueb();

    for (int i = 0; i < pointsPerRing; i++) {
      LXPoint p = ring.points[i];
      float u = i * inv;
      float v = (float)ringIdx / Math.max(1, numRings - 1);

      // Compute jittered UV for disruption
      float jitterU = u + disruptAmt * (float)Math.sin(time + u * tileCount);
      float jitterV = v + disruptAmt * (float)Math.cos(time + v * tileCount);

      // Determine cell and edge
      float fracU = (jitterU * tileCount) - (float)Math.floor(jitterU * tileCount);
      float fracV = (jitterV * tileCount) - (float)Math.floor(jitterV * tileCount);
      boolean isEdge = (fracU < 0.1f || fracU > 0.9f || fracV < 0.1f || fracV > 0.9f);

      float brightness = onlyEdges ? (isEdge ? 100f : 0f) : (isEdge ? 100f : 50f);
      int hue = (int)(360 * (jitterU - (float)Math.floor(jitterU)));

      colors[p.index] = LXColor.hsb(hue, saturation, brightness);
    }
  }
}
