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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

public class Apotheneum {

  public static final int GRID_WIDTH = 50;
  public static final int GRID_HEIGHT = 45;
  public static final int CYLINDER_HEIGHT = 43;
  public static final int RING_LENGTH = 120;

  public static final String IMAGE_CATEGORY = "Image";

  public static class Cube {

    public static class Orientation {

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

        this.columns = new LXModel[this.front.columns.length + this.right.columns.length + this.back.columns.length
            + this.left.columns.length];
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

        this.size = this.front.model.size +
            this.right.model.size +
            this.back.model.size +
            this.left.model.size;
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
          throw new IllegalStateException("Apotheneum face has wrong number of columns: " + this.columns.length);
        }
        for (LXModel column : this.columns) {
          if (column.size != GRID_HEIGHT) {
            throw new IllegalStateException("Apotheneum column has wrong length: " + column.size);
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

    public final Orientation exterior;
    public final Orientation interior;
    public final Face[] faces;

    private Cube(LXModel model) {
      this.exterior = new Orientation(model, "Exterior");
      this.interior = model.sub("interior").isEmpty() ? null : new Orientation(model, "Interior");

      final List<Face> faceList = new ArrayList<>();
      faceList.addAll(Arrays.asList(this.exterior.faces));
      if (this.interior != null) {
        faceList.addAll(Arrays.asList(this.interior.faces));
      }
      this.faces = faceList.toArray(new Face[0]);
    }
  }

  public static class Cylinder {

    public static class Orientation {
      public final int size;
      public final LXModel[] columns;
      public final Ring[] rings;

      private Orientation(LXModel model, String suffix) {
        this.columns = model.sub("cylinder" + suffix).toArray(new LXModel[0]);
        this.rings = new Ring[this.columns[0].size];
        for (int i = 0; i < this.rings.length; ++i) {
          this.rings[i] = new Ring(i, this.columns);
        }
        this.size = this.columns.length * this.columns[0].size;
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

    public Orientation exterior;
    public Orientation interior;

    private Cylinder(LXModel model) {
      this.exterior = new Orientation(model, "Exterior");
      this.interior = model.sub("interior").isEmpty() ? null : new Orientation(model, "Interior");
    }
  }

  public static boolean exists = false;
  public static boolean hasInterior = false;
  public static Cube cube = null;
  public static Cylinder cylinder = null;

  private static boolean initialized = false;
  private static final ModelListener modelListener = new ModelListener();

  public static void initialize(LX lx) {
    if (initialized) {
      return;
    }
    initialized = true;
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
          LX.log("Detected Apotheneum fixtures, hasInterior: " + hasInterior + " numPoints: " + model.size);
        }
      } catch (Exception x) {
        cube = null;
        cylinder = null;
        exists = false;
        LX.error(x, "Error building Apotheneum helpers");
        lx.pushError(x, "Apotheneum model is out of date, you may need to update your fixture files.\n" + x.getMessage());;
      }
    }
  }

}
