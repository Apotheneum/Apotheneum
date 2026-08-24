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
import apotheneum.ApotheneumPattern;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Crawlers")
@LXComponent.Description("Snake-like objects crawling around the surfaces")
public class Crawlers extends ApotheneumPattern implements UIDeviceControls<Crawlers> {

  public static double bias(LXParameter p1, LXParameter p2, LXParameter bias, double rnd) {
    final double b = bias.getValue();
    return LXUtils.lerp(
      p1.getValue(),
      p2.getValue(),
      (b < 0.5) ?
        Math.pow(rnd, LXUtils.lerp(4, 1, 2*b)) :
        1 - Math.pow(1-rnd, LXUtils.lerp(1, 4, 2 * (b-.5f)))
    );
  }

  private static final int MAX_CRAWLERS = 240;
  private static final int MAX_LENGTH = 240;

  public final CompoundDiscreteParameter numCube =
    new CompoundDiscreteParameter("Num Cube", 64, 0, MAX_CRAWLERS + 1)
    .setDescription("Number of active cube crawlers");

  public final CompoundDiscreteParameter numCylinder =
    new CompoundDiscreteParameter("Num Cylinder", 64, 0, MAX_CRAWLERS + 1)
    .setDescription("Number of active cube crawlers");

  public final CompoundParameter minLength =
    new CompoundParameter("Min Length", 16, 1, 64)
    .setDescription("Minimum crawler length");

  public final CompoundParameter maxLength =
    new CompoundParameter("Max Length", 32, 1, 128)
    .setDescription("Maximum crawler length");

  public final CompoundParameter biasLength =
    new CompoundParameter("Bias Length", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Bias crawlers towards shorter or longer lengths");

  public final CompoundParameter minSpeed =
    new CompoundParameter("Min Speed", 0, 10)
    .setDescription("Minimum crawler speed");

  public final CompoundParameter maxSpeed =
    new CompoundParameter("Max Speed", 10, 100)
    .setDescription("Maximum crawler speed");

  public final CompoundParameter biasSpeed =
    new CompoundParameter("Bias Speed", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Bias crawlers towards slower or faster movement ");

  public final CompoundParameter turnProbability =
    new CompoundParameter("Turn Prob", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Probability of turning on any step");

  public final CompoundDiscreteParameter turnGate =
    new CompoundDiscreteParameter("Turn Gate", 0, 64)
    .setDescription("Require a certain number of steps between turns");

  public final TriggerParameter trigHorizontal =
    new TriggerParameter("Trig-H", this::trigHorizontal)
    .setDescription("Set all to move horizontally");

  public final TriggerParameter trigVertical =
    new TriggerParameter("Trig-V", this::trigVertical)
    .setDescription("Set all to move vertically");

  public final CompoundParameter fadeHead =
    new CompoundParameter("Fade Head", 1, 1, 5)
    .setDescription("How many pixels to fade in at the front");

  public final CompoundParameter fadeTail =
    new CompoundParameter("Fade Tail", 1, 0, 10)
    .setDescription("How many pixels to fade at the tail");

  private final Crawler[] cubeCrawlers = new Crawler[MAX_CRAWLERS];
  private final Crawler[] cylinderCrawlers = new Crawler[MAX_CRAWLERS];
  private final Crawler[] allCrawlers = new Crawler[2*MAX_CRAWLERS];

  public Crawlers(LX lx) {
    super(lx);
    addParameter("numCube", this.numCube);
    addParameter("numCylinder", this.numCylinder);
    addParameter("minLength", this.minLength);
    addParameter("maxLength", this.maxLength);
    addParameter("biasLength", this.biasLength);
    addParameter("minSpeed", this.minSpeed);
    addParameter("maxSpeed", this.maxSpeed);
    addParameter("biasSpeed", this.biasSpeed);
    addParameter("turnProbability", this.turnProbability);
    addParameter("turnGate", this.turnGate);
    addParameter("trigHorizontal", this.trigHorizontal);
    addParameter("trigVertical", this.trigVertical);
    addParameter("fadeHead", this.fadeHead);
    addParameter("fadeTail", this.fadeTail);

    for (int i = 0; i < MAX_CRAWLERS; ++i) {
      this.cubeCrawlers[i] = new Crawler(Apotheneum.cube.exterior, i, this.numCube);
      this.cylinderCrawlers[i] = new Crawler(Apotheneum.cylinder.exterior, i, this.numCylinder);
      this.allCrawlers[i] = this.cubeCrawlers[i];
      this.allCrawlers[MAX_CRAWLERS + i] = this.cylinderCrawlers[i];
    }
  }

  private void trigHorizontal() {
    trigTurn(Crawler.TURN_HORIZONTAL);
  }

  private void trigVertical() {
    trigTurn(Crawler.TURN_VERTICAL);
  }

  private void trigTurn(int turn) {
    for (Crawler crawler : this.allCrawlers) {
      crawler.flagTurn = turn;
    }
  }

  private class Crawler {

    private class Coord {
      private int x;
      private int y;

      private void set(int x, int y) {
        this.x = x;
        this.y = y;
      }
    }

    private enum Direction {
      LEFT(-1, 0),
      RIGHT(1, 0),
      UP(0, 1),
      DOWN(0, -1);

      private final int x, y;

      private Direction(int x, int y) {
        this.x = x;
        this.y = y;
      }

      private boolean isHorizontal() {
        return this.x != 0;
      }

      private boolean isVertical() {
        return !isHorizontal();
      }
    }

    private final Apotheneum.Orientation orientation;

    private final double rnd = Math.random();

    private final int index;
    private final DiscreteParameter num;

    private double basis = 0;
    private int length = 0;
    private final Coord[] coords = new Coord[MAX_LENGTH];
    private int head = 0;
    private Direction direction;
    private int turnCount = 0;
    private int flagTurn = 0;
    private double level = 0;

    private static final int TURN_HORIZONTAL = 1;
    private static final int TURN_VERTICAL = 2;

    private Crawler(Apotheneum.Orientation orientation, int index, DiscreteParameter num) {
      this.index = index;
      this.num = num;
      this.orientation = orientation;
      for (int i = 0; i < MAX_LENGTH; ++i) {
        this.coords[i] = new Coord();
      }
      this.coords[this.head].set(
        LXUtils.randomi(0, orientation.width() - 1),
        LXUtils.randomi(0, orientation.height() - 1)
      );
      this.direction = Direction.values()[(LXUtils.randomi(0, Direction.values().length - 1))];
      this.length = 1;
    }

    private void step() {
      final int width = this.orientation.width();
      final int height = this.orientation.height();

      final Coord current = getCoord(0);
      final Coord next = getCoord(1);
      if (this.flagTurn == TURN_HORIZONTAL) {
        if (!this.direction.isHorizontal()) {
          this.turnCount = 0;
          this.direction = (Math.random() < .5) ? Direction.LEFT : Direction.RIGHT;
        }
        this.flagTurn = 0;
      } else if (this.flagTurn == TURN_VERTICAL) {
        if (!this.direction.isVertical()) {
          this.turnCount = 0;
          this.direction = (Math.random() < .5) ? Direction.UP : Direction.DOWN;
        }
        this.flagTurn = 0;
      } else if ((this.turnCount >= turnGate.getValuei()) && Math.random() < turnProbability.getValue()) {
        this.turnCount = 0;
        if (this.direction.x == 0) {
          this.direction = (Math.random() < .5) ? Direction.LEFT : Direction.RIGHT;
        } else {
          this.direction = (Math.random() < .5) ? Direction.UP : Direction.DOWN;
        }
      } else {
        ++this.turnCount;
      }
      next.x = (current.x + width + this.direction.x) % width;
      next.y = (current.y + height + this.direction.y) % height;
      this.head = (this.head + 1) % this.coords.length;
      this.length = LXUtils.min(MAX_LENGTH, this.length+1);
    }

    private void advance(double deltaMs) {
      final boolean active = this.index < this.num.getValuei();
      this.level = LXUtils.constrain(this.level + (active ? 1 : -1) * deltaMs / 1000, 0, 1);

      this.basis += deltaMs * bias(minSpeed, maxSpeed, biasSpeed, this.rnd) / 1000;
      if (this.basis > 1.) {
        step();
        this.basis = this.basis % 1.;
      }

      if (this.level == 0) {
        return;
      }

      final double length = bias(minLength, maxLength, biasLength, this.rnd);
      final float limit = (int) LXUtils.min(length, this.length);
      final double head = fadeHead.getValue();
      final double tail = LXUtils.min(length - head - 1, fadeTail.getValue());

      for (int i = 0; i < limit; ++i) {
        final Coord coord = getCoord(-i);
        double b = 1;
        if (i < head) {
          b = LXUtils.min(1, LXUtils.lerp(0, 1/head, i + this.basis));
        } else if (i >= length - 1 - tail) {
          b = LXUtils.max(0, LXUtils.lerp(1, 1-1/tail, i - (length - tail - 1) + this.basis));
        }
        if (b > 0) {
          final int idx = this.orientation.point(coord.x, coord.y).index;
          colors[idx] = LXColor.lightest(colors[idx], LXColor.grayn(this.level * b));
        }
      }
    }

    private Coord getCoord(int index) {
      return this.coords[(this.head + this.coords.length + index) % this.coords.length];
    }

    private void render(double deltaMs) {
      advance(deltaMs);
    }
  }


  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    for (Crawler crawler : this.allCrawlers) {
      crawler.render(deltaMs);
    }
    copyCubeExterior();
    copyCylinderExterior();

  }


  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Crawlers crawlers) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL);
    uiDevice.setChildSpacing(2);
    addColumn(uiDevice, UIKnob.WIDTH, "Num",
      newKnob(crawlers.numCube, 0),
      newKnob(crawlers.numCylinder, 0)
    ).setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, UIKnob.WIDTH, "Speed",
      newKnob(crawlers.minSpeed, 0),
      newKnob(crawlers.maxSpeed, 0),
      newKnob(crawlers.biasSpeed, 0)
    ).setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, UIKnob.WIDTH, "Length",
      newKnob(crawlers.minLength, 0),
      newKnob(crawlers.maxLength, 0),
      newKnob(crawlers.biasLength, 0)
    ).setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, UIKnob.WIDTH, "Fade",
      newKnob(crawlers.fadeHead, 0),
      newKnob(crawlers.fadeTail, 0)
    ).setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Turning",
      newKnob(crawlers.turnGate),
      newKnob(crawlers.turnProbability),
      newButton(crawlers.trigHorizontal).setTriggerable(true),
      newButton(crawlers.trigVertical).setTriggerable(true)
    ).setChildSpacing(6);
  }

}
