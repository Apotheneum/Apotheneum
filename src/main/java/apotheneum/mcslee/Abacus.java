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

import java.util.ArrayList;
import java.util.List;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.Damper;
import heronarts.lx.parameter.CompoundDiscreteParameter;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Abacus")
@LXComponent.Description("Simulates the operation of the abacus")
public class Abacus extends ApotheneumPattern {

  private final static int NUM_CUBE_PLACES = 7;
  private final static int NUM_CYLINDER_PLACES = 11;

  private final static int CUBE_BEAD_WIDTH = 6;
  private final static int CUBE_BEAD_SPACING = CUBE_BEAD_WIDTH + 1;

  private final static int CYLINDER_BEAD_WIDTH = 10;
  private final static int CYLINDER_BEAD_SPACING = CYLINDER_BEAD_WIDTH + 1;

  private final static int DIVIDER = 14;
  private final static int DIVIDER_WIDTH = 2;
  private final static int DIVIDER_GAP = 1;

  private final static int BEAD_HEIGHT = 4;
  private final static int BEAD_GAP = 1;
  private final static int BEAD_SPACING = BEAD_HEIGHT + BEAD_GAP;

  private final static int CUBE_BEAD_SHIFT = 4;
  private final static int CYLINDER_BEAD_SHIFT = 2;

  private static class BeadMetrics {

    private final int beadWidth;
    private final int beadShift;
    private final int ones;
    private final int fives;

    private BeadMetrics(int beadWidth, int beadShift) {
      this.beadWidth = beadWidth;
      this.beadShift = beadShift;
      this.ones = DIVIDER + DIVIDER_WIDTH + DIVIDER_GAP + beadShift;
      this.fives = DIVIDER - DIVIDER_GAP - BEAD_HEIGHT;
    }
  }

  private final static BeadMetrics CUBE_METRICS = new BeadMetrics(CUBE_BEAD_WIDTH, CUBE_BEAD_SHIFT);
  private final static BeadMetrics CYLINDER_METRICS = new BeadMetrics(CYLINDER_BEAD_WIDTH, CYLINDER_BEAD_SHIFT);



  public final CompoundDiscreteParameter[] cubeValue =
    new CompoundDiscreteParameter[NUM_CUBE_PLACES];

  public final CompoundDiscreteParameter[] cylinderValue =
    new CompoundDiscreteParameter[NUM_CYLINDER_PLACES];

  private final List<Digit> digits = new ArrayList<>(NUM_CUBE_PLACES);

  private class Digit {

    private final int NUM_BEADS = 7;
    private final BeadMetrics metrics;
    private final Apotheneum.Column[] columns;
    private final CompoundDiscreteParameter placeValue;
    private final int xPos;

    private final List<Damper> dampers = new ArrayList<>(NUM_BEADS);

    private Digit(Apotheneum.Cube.Face face, CompoundDiscreteParameter placeValue, int xPos) {
      this(face.columns, CUBE_METRICS, placeValue, xPos);
    }

    private Digit(Apotheneum.Cylinder.Orientation orientation, CompoundDiscreteParameter placeValue, int xPos) {
      this(orientation.columns, CYLINDER_METRICS, placeValue, xPos);
    }

    private Digit(Apotheneum.Column[] columns, BeadMetrics metrics, CompoundDiscreteParameter placeValue, int xPos) {
      this.columns = columns;
      this.metrics = metrics;
      this.placeValue = placeValue;
      this.xPos = xPos;
      for (int i = 0; i < NUM_BEADS; ++i) {
        final Damper damper = new Damper();
        damper.sinShaping.setValue(true);
        damper.start();
        damper.periodMs.setValue(250);
        this.dampers.add(damper);
      }
    }

    private final int[] base = new int[NUM_BEADS];

    private void setBead(int i, int val) {
      if (i < 5) {
        this.base[i] = this.metrics.ones + (i*BEAD_SPACING);
        this.dampers.get(i).toggle.setValue((val % 5) > i);
      } else {
        this.base[i] = this.metrics.fives - ((i-5) * BEAD_SPACING);
        this.dampers.get(i).toggle.setValue((val / 5) <= (i-5));
      }
    }

    private void render(double deltaMs) {
      this.dampers.forEach(damper -> damper.loop(deltaMs));
      final int val = placeValue.getValuei();
      for (int i = 0; i < NUM_BEADS; ++i) {
        setBead(i, val);
        int pos = (int) Math.round(this.base[i] - this.dampers.get(i).getValue() * this.metrics.beadShift);

        for (int x = 0; x < this.metrics.beadWidth; ++x) {
          final Apotheneum.Column column = this.columns[this.xPos+x];
          int yMin = 0, yMax = BEAD_HEIGHT;
          if (x == 0 || x == this.metrics.beadWidth - 1) {
            ++yMin;
            --yMax;
          }
          for (int y = yMin; y < yMax; ++y) {
            colors[column.points[pos+y].index] = LXColor.RED;
          }
        }
      }
    }

  }

  public Abacus(LX lx) {
    super(lx);
    for (int i = 0; i < NUM_CUBE_PLACES; ++i) {
      this.cubeValue[i] = new CompoundDiscreteParameter("Cube-" + i, 0, 11);
      addParameter("Cube-" + i, this.cubeValue[i]);
      this.digits.add(new Digit(Apotheneum.cube.exterior.front, this.cubeValue[i], 1 + i * CUBE_BEAD_SPACING));
    }

    for (int i = 0; i < NUM_CYLINDER_PLACES; ++i) {
      this.cylinderValue[i] = new CompoundDiscreteParameter("Cylinder-" + i, 0, 11);
      addParameter("Cylinder-" + i, this.cylinderValue[i]);
      this.digits.add(new Digit(Apotheneum.cylinder.exterior, this.cylinderValue[i], i * CYLINDER_BEAD_SPACING));
    }
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    // NB: the exterior front is used to render, whether or not it's in the view,
    // so make sure that we black it out!
    setColor(Apotheneum.cube.exterior.front.model, LXColor.BLACK);
    for (Apotheneum.Column column : Apotheneum.cylinder.exterior.columns) {
      setColor(column, LXColor.BLACK);
    }

    for (int d = 0; d < DIVIDER_WIDTH; ++d) {
      for (LXPoint p : Apotheneum.cube.exterior.front.rows[DIVIDER + d].points) {
        colors[p.index] = LXColor.WHITE;
      }
      for (LXPoint p : Apotheneum.cylinder.exterior.rings[DIVIDER + d].points) {
        colors[p.index] = LXColor.WHITE;
      }
    }
    this.digits.forEach(digit -> digit.render(deltaMs));
    copyCubeFace(Apotheneum.cube.exterior.front);
    copyCylinderExterior();
  }

}
