/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package apotheneum;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.utils.LXUtils;

public class Apotheneum {

  public static final int DOOR_WIDTH = 10;
  public static final int DOOR_HEIGHT = 11;
  public static final int GRID_WIDTH = 50;
  public static final int GRID_HEIGHT = 45;
  public static final int CYLINDER_HEIGHT = 43;
  public static final int RING_LENGTH = Cylinder.Ring.LENGTH;
  public static final String IMAGE_CATEGORY = "Image";

  public abstract static class Component {
    public abstract Orientation[] orientations();

    public Orientation exterior() {
      return orientation(Orientation.EXTERIOR);
    }

    public Orientation interior() {
      return orientation(Orientation.INTERIOR);
    }

    public Orientation orientation(int index) {
      return orientations()[index];
    }

    public int width() {
      return exterior().width();
    }

    public int height() {
      return orientations()[0].height();
    }
  }

  public abstract static class Orientation {

    public static final int EXTERIOR = 0;
    public static final int INTERIOR = 1;

    public abstract LXModel[] columns();

    public LXPoint point(int x, int y) {
      return column(x).points[y];
    }

    public LXModel column(int index) {
      return columns()[index];
    }

    public abstract Ring[] rings();

    public abstract int available(int columnIndex);

    public Ring ring(int index) {
      return rings()[index];
    }

    public int width() {
      return columns().length;
    }

    public int height() {
      return columns()[0].points.length;
    }
  }

  public static class Ring {

    public final int index;
    public final LXPoint[] points;

    private Ring(int index, LXModel[] columns) {
      this.index = index;
      this.points = new LXPoint[columns.length];
      int i = 0;
      for (LXModel column : columns) {
        this.points[i++] = column.points[index];
      }
    }
  }

  public static class Cube extends Component {

    public static final int DOOR_START_COLUMN = 20;

    public static class Orientation extends Apotheneum.Orientation {

      public final int size;

      public final Face front;
      public final Face right;
      public final Face back;
      public final Face left;

      public final Face[] faces;

      public final LXModel[] columns;
      public final Ring[] rings;

      private Orientation(LXModel model, String suffix) {
        this.front = new Face(model.sub("cubeFront" + suffix).get(0));
        this.right = new Face(model.sub("cubeRight" + suffix).get(0));
        this.back = new Face(model.sub("cubeBack" + suffix).get(0));
        this.left = new Face(model.sub("cubeLeft" + suffix).get(0));
        this.faces = new Face[] { this.front, this.right, this.back, this.left };

        this.columns = new LXModel[this.front.columns.length + this.right.columns.length + this.back.columns.length + this.left.columns.length];
        int cIndex = 0;
        System.arraycopy(this.front.columns, 0, this.columns, cIndex, this.front.columns.length);
        cIndex += this.front.columns.length;
        System.arraycopy(this.right.columns, 0, this.columns, cIndex, this.right.columns.length);
        cIndex += this.right.columns.length;
        System.arraycopy(this.back.columns, 0, this.columns, cIndex, this.back.columns.length);
        cIndex += this.back.columns.length;
        System.arraycopy(this.left.columns, 0, this.columns, cIndex, this.left.columns.length);

        this.rings = new Ring[this.columns[0].size];
        for (int i = 0; i < this.rings.length; ++i) {
          this.rings[i] = new Ring(i, this.columns);
        }

        this.size =
          this.front.model.size +
          this.right.model.size +
          this.back.model.size +
          this.left.model.size;
      }

      @Override
      public LXModel[] columns() {
        return this.columns;
      }

      @Override
      public Ring[] rings() {
        return this.rings;
      }

      @Override
      public int available(int columnIndex) {
        if (LXUtils.inRange(columnIndex % GRID_WIDTH, DOOR_START_COLUMN, DOOR_START_COLUMN + DOOR_WIDTH - 1)) {
          return GRID_HEIGHT - DOOR_HEIGHT;
        }
        return GRID_HEIGHT;
      }

    }

    public static class Face {
      public final LXModel model;
      public final LXModel[] columns;
      public final Row[] rows;

      private Face(LXModel face) {
        this.model = face;
        this.columns = face.children;
        this.rows = new Row[GRID_HEIGHT];

        if (this.columns.length != GRID_WIDTH) {
          throw new IllegalStateException("Apotheneum face expects " + GRID_WIDTH + " columns, found " + this.columns.length);
        }
        for (LXModel column : this.columns) {
          if (column.size != GRID_HEIGHT) {
            throw new IllegalStateException("Apotheneum cube column expects " + GRID_HEIGHT + " points, found " + column.size);
          }
        }

        for (int i = 0; i < this.rows.length; ++i) {
          this.rows[i] = new Row(i, this.columns);
        }
      }

    }

    public static class Row {

      public final int index;
      public final LXPoint[] points;

      private Row(int index, LXModel[] columns) {
        this.index = index;
        this.points = new LXPoint[columns.length];
        int i = 0;
        for (LXModel column : columns) {
          this.points[i++] = column.points[index];
        }
      }
    }

    public static class Ring extends Apotheneum.Ring {

      public static final int LENGTH = 200;

      private Ring(int index, LXModel[] columns) {
        super(index, columns);
      }
    }

    public final Orientation exterior;
    public final Orientation interior;
    public final Orientation[] orientations;
    public final Face[] faces;

    private Cube(LXModel model) {
      this.exterior = new Orientation(model, "Exterior");
      this.interior = model.sub("interior").isEmpty() ? null : new Orientation(model, "Interior");
      this.orientations = new Orientation[] { this.exterior, this.interior };

      final List<Face> faceList = new ArrayList<>();
      faceList.addAll(Arrays.asList(this.exterior.faces));
      if (this.interior != null) {
        faceList.addAll(Arrays.asList(this.interior.faces));
      }
      this.faces = faceList.toArray(new Face[0]);
    }

    @Override
    public Orientation[] orientations() {
      return this.orientations;
    }
  }

  public static class Cylinder extends Component {

    public static final int DOOR_START_COLUMN = 10;

    public static class Orientation extends Apotheneum.Orientation {
      public final int size;
      public final LXModel[] columns;
      public final Ring[] rings;

      private Orientation(LXModel model, String suffix) {
        this.columns = model.sub("cylinder" + suffix).toArray(new LXModel[0]);
        if (this.columns.length != Ring.LENGTH) {
          throw new IllegalStateException("Apotheneum cylinder expects " + Ring.LENGTH + " columns, found " + this.columns.length);
        }
        for (LXModel column : this.columns) {
          if (column.size != CYLINDER_HEIGHT) {
            throw new IllegalStateException("Apotheneum cylinder column expects " + CYLINDER_HEIGHT + " points, found " + column.size);
          }
        }

        this.rings = new Ring[this.columns[0].size];
        for (int i = 0; i < this.rings.length; ++i) {
          this.rings[i] = new Ring(i, this.columns);
        }
        this.size = this.columns.length * this.columns[0].size;
      }

      @Override
      public LXModel[] columns() {
        return this.columns;
      }

      @Override
      public Ring[] rings() {
        return this.rings;
      }

      @Override
      public int available(int columnIndex) {
        if (LXUtils.inRange(columnIndex % 30, 10, 10 + DOOR_WIDTH - 1)) {
          return CYLINDER_HEIGHT - DOOR_HEIGHT;
        }
        return CYLINDER_HEIGHT;
      }
    }

    public static class Ring extends Apotheneum.Ring {

      public static final int LENGTH = 120;

      private Ring(int index, LXModel[] columns) {
        super(index, columns);
      }
    }

    public final Orientation exterior;
    public final Orientation interior;
    public final Orientation[] orientations;

    private Cylinder(LXModel model) {
      this.exterior = new Orientation(model, "Exterior");
      this.interior = model.sub("interior").isEmpty() ? null : new Orientation(model, "Interior");
      this.orientations = new Orientation[] { this.exterior, this.interior };
    }

    @Override
    public Orientation[] orientations() {
      return this.orientations;
    }
  }

  public static boolean exists = false;
  public static boolean hasInterior = false;
  public static Cube cube = null;
  public static Cylinder cylinder = null;

  private static LX lx = null;
  private static boolean initialized = false;
  private static final ModelListener modelListener = new ModelListener();

  public static void initialize(LX lx) {
    if (initialized) {
      return;
    }
    initialized = true;
    Apotheneum.lx = lx;
    modelListener.modelChanged(lx, lx.getModel());
    lx.addListener(modelListener);
  }

  private static class ModelListener implements LX.Listener {
    public void modelChanged(LX lx, LXModel model) {
      LX.log("Apotheneum.modelChanged");

      cube = null;
      cylinder = null;
      exists = false;
      try {
        if (!model.sub("Apotheneum").isEmpty()) {
          cube = new Cube(model);
          cylinder = new Cylinder(model);
          hasInterior = (cube.interior != null);
          exists = true;
          LX.log("Detected Apotheneum fixtures, hasInterior: " + hasInterior +  " numPoints: " + model.size);
        }
      } catch (Exception x) {
        cube = null;
        cylinder = null;
        exists = false;
        LX.error(x, "Error building Apotheneum helpers");
        lx.pushError(x, "Apotheneum detected but contains errors. Fixture files may be out of date or multiple instances loaded?\n" + x.getMessage());;
      }
    }
  }

  private static LXOscEngine.Transmitter oscTransmitter = null;

  public static void osc2Ableton(OscMessage message) {
    if ((oscTransmitter == null) && (lx != null)) {
      try {
        oscTransmitter = lx.engine.osc.transmitter(InetAddress.getLoopbackAddress(), 5050);
      } catch (Exception x) {
        LX.error(x, "Apotheneum couldn't create local OSC transmitter: " + x.getMessage());
      }
    }
    if (oscTransmitter != null) {
      try {
        oscTransmitter.send(message);
      } catch (IOException iox) {
        LX.error(iox, "Failed to send OSC message to Ableton: " + iox.getMessage());
      }
    }
  }

}
