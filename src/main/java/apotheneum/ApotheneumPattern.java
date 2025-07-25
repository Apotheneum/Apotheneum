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

import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.pattern.LXPattern;

public abstract class ApotheneumPattern extends LXPattern {

  protected ApotheneumPattern(LX lx) {
    super(lx);
    Apotheneum.initialize(lx);
  }

  @Override
  protected final void run(double deltaMs) {
    if (Apotheneum.exists) {
      render(deltaMs);
    } else {
      setColors(LXColor.BLACK);
    }
  }

  private void assertExists() {
    if (!Apotheneum.exists) {
      throw new IllegalStateException("Should not call ApothenumPattern utilities when no Apotheneum model loaded");
    }
  }

  private void _copyCubeFace(Apotheneum.Cube.Face from, Apotheneum.Cube.Face to) {
    if (from != to) {
      copy(from, to);
    }
  }

  protected void copyCubeFace(Apotheneum.Cube.Face from) {
    _copyCubeFace(from, Apotheneum.cube.exterior.front);
    _copyCubeFace(from, Apotheneum.cube.exterior.right);
    _copyCubeFace(from, Apotheneum.cube.exterior.back);
    _copyCubeFace(from, Apotheneum.cube.exterior.left);
    _copyCubeFace(from, Apotheneum.cube.interior.front);
    _copyCubeFace(from, Apotheneum.cube.interior.right);
    _copyCubeFace(from, Apotheneum.cube.interior.back);
    _copyCubeFace(from, Apotheneum.cube.interior.left);
  }

  protected void copyCubeExterior() {
    copy(Apotheneum.cube.exterior, Apotheneum.cube.interior);
  }

  protected void copyCylinderExterior() {
    copy(Apotheneum.cylinder.exterior, Apotheneum.cylinder.interior);
  }

  protected void copyExterior() {
    copyCubeExterior();
    copyCylinderExterior();
  }

  protected void copyMirror(Apotheneum.Cube.Face from, Apotheneum.Cube.Face to) {
    assertExists();
    if ((from != null) && (to != null)) {
      int colIndex = 0;
      for (LXModel fromCol : from.columns) {
        LXModel toCol = to.columns[to.columns.length - 1 - colIndex];
        System.arraycopy(colors, fromCol.points[0].index, colors, toCol.points[0].index, fromCol.size);
        ++colIndex;
      }
    }
  }

  protected void copy(Apotheneum.Cube.Face from, Apotheneum.Cube.Face to) {
    assertExists();
    if ((from != null) && (to != null)) {
      System.arraycopy(colors, from.model.points[0].index, colors, to.model.points[0].index, from.model.size);
    }
  }

  protected void copy(Apotheneum.Cube.Orientation from, Apotheneum.Cube.Orientation to) {
    assertExists();
    if ((from != null) && (to != null)) {
      System.arraycopy(colors, from.front.model.points[0].index, colors, to.front.model.points[0].index, from.size);
    }
  }

  protected void copy(Apotheneum.Cylinder.Orientation from, Apotheneum.Cylinder.Orientation to) {
    assertExists();
    if ((from != null) && (to != null)) {
      System.arraycopy(colors, from.columns[0].points[0].index, colors, to.columns[0].points[0].index, from.size);
    }
  }

  protected void setApotheneumColor(int color) {
    setColor(Apotheneum.cube, color);
    setColor(Apotheneum.cylinder, color);
  }

  protected void setColor(Apotheneum.Component component, int color) {
    for (Apotheneum.Orientation orientation : component.orientations()) {
      setColor(orientation, color);
    }
  }

  protected void setColor(Apotheneum.Orientation orientation, int color) {
    for (LXModel column : orientation.columns()) {
      setColor(column, color);
    }
  }

  protected void setColor(Apotheneum.Cube.Face face, int color) {
    for (LXModel column : face.columns) {
      setColor(column, color);
    }
  }

  protected abstract void render(double deltaMs);

}
