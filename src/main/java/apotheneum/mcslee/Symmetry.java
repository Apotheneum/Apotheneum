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

package apotheneum.mcslee;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumEffect;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;
import heronarts.glx.ui.component.UIDropMenu;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Description("Implements radial and mirroring symmetry on the cube and cylinder")
public class Symmetry extends ApotheneumEffect implements UIDeviceControls<Symmetry> {

  public enum ReflectionMode {
    MIRROR("Mirror"),
    REPEAT("Repeat");

    public final String label;

    private ReflectionMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  private final Integer[] cubeDivisions = {
    1, 2, 4, 5, 8, 10, 20, 25, 40, 50, 100
  };

  private final Integer[] cylinderDivisions = {
    1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 24, 30, 40, 60
  };

  public final EnumParameter<ReflectionMode> cubeMode =
    new EnumParameter<ReflectionMode>("Cube Reflection Mode", ReflectionMode.MIRROR)
    .setDescription("Whether cube reflections mirror or repeat");

  public final EnumParameter<ReflectionMode> cylinderMode =
    new EnumParameter<ReflectionMode>("Cylinder Reflection Mode", ReflectionMode.MIRROR)
    .setDescription("Whether cylinder reflections mirror or repeat");

  public final ObjectParameter<Integer> cylinderSegments =
    new ObjectParameter<Integer>("Cyl-Seg", this.cylinderDivisions)
    .setDescription("Number of segments on the cylinder");

  public final ObjectParameter<Integer> cubeSegments =
    new ObjectParameter<Integer>("Cube-Seg", this.cubeDivisions)
    .setDescription("Number of segments on the cube");

  public final CompoundParameter cubeAngle =
    new CompoundParameter("Cube Angle", 0, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setWrappable(true)
    .setDescription("Angle of cube radial mirror position");

  public final CompoundParameter cylinderAngle =
    new CompoundParameter("Cylinder Angle", 45, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setWrappable(true)
    .setDescription("Angle of cylinder radial mirror position");

  public final CompoundParameter horizonPosition =
    new CompoundParameter("Horizon", .5f)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Position of the horizon");

  public final BooleanParameter horizonCylinder =
    new BooleanParameter("Cyl-Horizon", false)
    .setDescription("Whether to mirror the cylinder vertically across the horizon");

  public final BooleanParameter horizonCube =
    new BooleanParameter("Cube-Horizon", false)
    .setDescription("Whether to mirror the cube vertically across the horizon");

  public final BooleanParameter invertHorizon =
    new BooleanParameter("Invert", false)
    .setDescription("Direction of horizon mirror");

  public Symmetry(LX lx) {
    super(lx);
    addParameter("cubeMode", this.cubeMode);
    addParameter("cubeAngle", this.cubeAngle);
    addParameter("cubeSegments", this.cubeSegments);

    addParameter("cylinderMode", this.cylinderMode);
    addParameter("cylinderAngle", this.cylinderAngle);
    addParameter("cylinderSegments", this.cylinderSegments);

    addParameter("horizonPosition", this.horizonPosition);
    addParameter("horizonCylinder", this.horizonCylinder);
    addParameter("horizonCube", this.horizonCube);
    addParameter("invertHorizon", this.invertHorizon);
    setDamping(false);
  }

  @Override
  protected void render(double deltaMs, double enabledAmount) {
    if (enabledAmount <= 0) {
      return;
    }


    final int segmentsCylinder = this.cylinderSegments.getObject();
    final boolean mirror = this.cubeMode.getEnum() == ReflectionMode.MIRROR;
    if (segmentsCylinder > 1) {
      final float angle = this.cylinderAngle.getNormalizedf();
      copyCols(Apotheneum.cylinder.exterior, segmentsCylinder, 0, angle, mirror);
      copyCols(Apotheneum.cylinder.interior, segmentsCylinder, 0, angle, mirror);
    }
    final int segmentsCube = this.cubeSegments.getObject();
    if (segmentsCube > 1) {
      final float angle = this.cubeAngle.getNormalizedf();
      copyCols(Apotheneum.cube.exterior, segmentsCube, Apotheneum.GRID_WIDTH/2, angle, mirror);
      copyCols(Apotheneum.cube.interior, segmentsCube, Apotheneum.GRID_WIDTH/2, angle, mirror);
    }

    final float horizonPosition = this.horizonPosition.getValuef();
    final boolean invertHorizon = this.invertHorizon.isOn();
    if (this.horizonCube.isOn()) {
      mirrorRings(Apotheneum.cube.exterior, horizonPosition, invertHorizon);
      mirrorRings(Apotheneum.cube.interior, horizonPosition, invertHorizon);
    }
    if (this.horizonCylinder.isOn()) {
      mirrorRings(Apotheneum.cylinder.exterior, horizonPosition, invertHorizon);
      mirrorRings(Apotheneum.cylinder.interior, horizonPosition, invertHorizon);
    }
  }

  private void copyCols(Apotheneum.Orientation orientation, int numSegments, int offset, float angle, boolean mirror) {
    final int numCols = orientation.columns().length;
    final int fromCol = (LXUtils.lerpi(0, numCols, angle) + offset) % numCols;
    final int segmentCols = numCols / numSegments;
    for (int s = 1; s < numSegments; ++s) {
      final int toCol = fromCol + segmentCols * s;
      for (int i = 0; i < segmentCols; ++i) {
        int fromIndex = (mirror && ((s % 2) == 1)) ?
          (fromCol + segmentCols - 1 - i) :
          (fromCol + i);
        copyPoints(
          orientation.column(fromIndex % numCols).points,
          orientation.column((toCol + i) % numCols).points
        );
      }
    }
  }

  private void mirrorRings(Apotheneum.Orientation orientation, float horizonPosition, boolean invertHorizon) {
    final int numRings = orientation.rings().length;
    final int center = LXUtils.lerpi(numRings-1, 0, horizonPosition);
    if (!invertHorizon) {
      // Copy bottom to top
      final int avail = numRings - center;
      for (int i = 0; i < center; ++i) {
        int offset = 1 + i;
        int fromIndex = center + offset;
        if ((offset / avail) % 2 == 1) {
          fromIndex = center + (avail - 1  - offset % avail);
        } else {
          fromIndex = center + (offset % avail);
        }
        copyPoints(
          orientation.ring(fromIndex).points,
          orientation.ring(center - offset).points
        );
      }
    } else {
      // Copy top to bottom
      final int avail = center + 1;
      for (int i = 0; i < numRings - 1 - center; ++i) {
        int offset = 1 + i;
        int fromIndex = center - offset;
        if ((offset / avail) % 2 == 1) {
          fromIndex = center - (avail - 1  - offset % avail);
        } else {
          fromIndex = center - (offset % avail);
        }
        copyPoints(
          orientation.ring(fromIndex).points,
          orientation.ring(center + offset).points
        );
      }
    }
  }

  private void copyPoints(LXPoint[] from, LXPoint[] to) {
    int pi = 0;
    for (LXPoint p : from) {
      colors[to[pi++].index] = colors[p.index];
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Symmetry symmetry) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);

    addColumn(uiDevice, "Cube",
      newDropMenu(symmetry.cubeMode),
      newDropMenu(symmetry.cubeSegments).setDirection(UIDropMenu.Direction.UP),
      newDoubleBox(symmetry.cubeAngle)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Cylinder",
      newDropMenu(symmetry.cylinderMode),
      newDropMenu(symmetry.cylinderSegments).setDirection(UIDropMenu.Direction.UP),
      newDoubleBox(symmetry.cylinderAngle)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Horizon",
      newKnob(symmetry.horizonPosition),
      newButton(symmetry.invertHorizon).setTriggerable(true),
      newButton(symmetry.horizonCube).setTriggerable(true).setLabel("Cube"),
      newButton(symmetry.horizonCylinder).setTriggerable(true).setLabel("Cylinder")
    ).setChildSpacing(4);

  }
}

