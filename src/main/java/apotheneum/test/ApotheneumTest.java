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

package apotheneum.test;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;
import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.component.UIDropMenu;
import heronarts.glx.ui.component.UILabel;
import heronarts.glx.ui.vg.VGraphics;

@LXCategory("Apotheneum/Test")
@LXComponent.Name("Apotheneum Test")
@LXComponent.Description("Test Routines for Apotheneum mapping")
public class ApotheneumTest extends ApotheneumPattern implements UIDeviceControls<ApotheneumTest> {

  private final static int[][] DIGITS = {
    {
      1, 1, 1, 1, 1,
      1, 0, 0, 0, 1,
      1, 0, 0, 0, 1,
      1, 0, 0, 0, 1,
      1, 1, 1, 1, 1
    },
    {
      0, 1, 1, 0, 0,
      0, 0, 1, 0, 0,
      0, 0, 1, 0, 0,
      0, 0, 1, 0, 0,
      0, 0, 1, 0, 0
    },
    {
      1, 1, 1, 1, 1,
      0, 0, 0, 0, 1,
      1, 1, 1, 1, 1,
      1, 0, 0, 0, 0,
      1, 1, 1, 1, 1
    },
    {
      1, 1, 1, 1, 1,
      0, 0, 0, 0, 1,
      1, 1, 1, 1, 1,
      0, 0, 0, 0, 1,
      1, 1, 1, 1, 1
    },
    {
      1, 0, 0, 0, 1,
      1, 0, 0, 0, 1,
      1, 1, 1, 1, 1,
      0, 0, 0, 0, 1,
      0, 0, 0, 0, 1
    },
    {
      1, 1, 1, 1, 1,
      1, 0, 0, 0, 0,
      1, 1, 1, 1, 1,
      0, 0, 0, 0, 1,
      1, 1, 1, 1, 1
    },
    {
      1, 1, 1, 1, 1,
      1, 0, 0, 0, 0,
      1, 1, 1, 1, 1,
      1, 0, 0, 0, 1,
      1, 1, 1, 1, 1
    },
    { 1, 1, 1, 1, 1,
      0, 0, 0, 1, 0,
      0, 0, 1, 0, 0,
      0, 1, 0, 0, 0,
      1, 0, 0, 0, 0
    },
    { 1, 1, 1, 1, 1,
      1, 0, 0, 0, 1,
      1, 1, 1, 1, 1,
      1, 0, 0, 0, 1,
      1, 1, 1, 1, 1
    },
    { 1, 1, 1, 1, 1,
      1, 0, 0, 0, 1,
      1, 1, 1, 1, 1,
      0, 0, 0, 0, 1,
      0, 0, 0, 0, 1
    }
  };

  public enum Target {
    ALL,
    CUBE,
    CYLINDER,
    SINGLE;
  }

  public enum Side {
    BOTH,
    EXT,
    INT;
  }

  public enum Net {
    CUB01,
    CUB02,
    CUB03,
    CUB04,
    CUB05,
    CUB06,
    CUB07,
    CUB08,
    CUB09,
    CUB10,
    CUB11,
    CUB12,
    CUB13,
    CUB14,
    CUB15,
    CUB16,
    CUB17,
    CUB18,
    CUB19,
    CUB20,
    CYL01,
    CYL02,
    CYL03,
    CYL04,
    CYL05,
    CYL06,
    CYL07,
    CYL08,
    CYL09,
    CYL10,
    CYL11,
    CYL12;

    public boolean isCube() {
      return ordinal() < 20;
    }

    public int getNetIndex() {
      return ordinal() % 20;
    }

    public Apotheneum.Orientation getOrientation() {
      return isCube() ? Apotheneum.cube.exterior : Apotheneum.cylinder.exterior;
    }

    public int getColumnIndex() {
      if (isCube()) {
        return 10 * getNetIndex();
      } else {
        return (110 + 10 * getNetIndex()) % 120;
      }
    }
  }

  public enum Mode {
    GRADIENT,
    HORIZONTAL_STRIPE,
    VERTICAL_STRIPE,
    DMX_CHANNEL;

    public String getDescription() {
      return switch (this) {
      case GRADIENT -> "Generates a gradient pattern with net number. Top left green, bottom right red.";
      case HORIZONTAL_STRIPE -> "Renders a stripe across the net with defined color + offset.";
      case VERTICAL_STRIPE -> "Renders a stripe down the net with defined color + strip number.";
      case DMX_CHANNEL -> "Output matches DMX channel on all universes, values range from 0-255.";
      };
    }
  }

  public enum Color {
    WHITE(LXColor.WHITE),
    RED(LXColor.RED),
    GREEN(LXColor.GREEN),
    BLUE(LXColor.BLUE);

    public final int color;

    private Color(int color) {
      this.color = color;
    }
  }

  public final EnumParameter<Target> target =
    new EnumParameter<>("Target", Target.ALL)
    .setDescription("Which nets to light");

  public final EnumParameter<Side> side =
    new EnumParameter<>("Side", Side.BOTH)
    .setDescription("Which side to light");

  public final EnumParameter<Net> net =
    new EnumParameter<>("Net", Net.CUB01)
    .setDescription("Which net to test");

  public final EnumParameter<Mode> mode =
    new EnumParameter<>("Mode", Mode.GRADIENT)
    .setDescription("Which test mode");

  public final EnumParameter<Color> color =
    new EnumParameter<>("Color", Color.WHITE)
    .setDescription("Which test color");

  public final DiscreteParameter stripeX =
    new DiscreteParameter("Pos X", 0, 10)
    .setDescription("Stripe X position");

  public final DiscreteParameter stripeY =
    new DiscreteParameter("Pos Y", 0, 45)
    .setDescription("Stripe Y position");

  public ApotheneumTest(LX lx) {
    super(lx);
    addParameter("target", this.target);
    addParameter("net", this.net);
    addParameter("side", this.side);
    addParameter("mode", this.mode);
    addParameter("color", this.color);
    addParameter("stripeX", this.stripeX);
    addParameter("stripeY", this.stripeY);
  }

  private static final int NET_WIDTH = 10;

  private void renderColumn(Apotheneum.Orientation orientation, Apotheneum.Column column, int columnIndex) {
    switch (this.mode.getEnum()) {
    case HORIZONTAL_STRIPE -> renderHorizontal(orientation, column, columnIndex);
    case VERTICAL_STRIPE -> renderVertical(orientation, column, columnIndex);
    case DMX_CHANNEL -> renderDMX(orientation, column, columnIndex);
    case GRADIENT -> renderGradient(orientation, column, columnIndex);
    }
  }

  private void renderGradient(Apotheneum.Orientation orientation, Apotheneum.Column column, int columnIndex) {
    final int height = orientation.available(columnIndex);
    switch (columnIndex % NET_WIDTH) {
      case 0 -> {
        setColor(column, LXColor.WHITE);
        colors[column.points[0].index] = LXColor.GREEN;
      }
      case 9 -> {
        setColor(column, LXColor.WHITE);
        colors[column.points[height-1].index] = LXColor.RED;
      }
      default -> {
        int h = 0;
        for (LXPoint p : column.points) {
          if ((h == 0) || (h == height-1)) {
            colors[p.index] = LXColor.WHITE;
          } else {
            colors[p.index] = LXColor.hsb(h*7, 100, 100 - 10 * ((columnIndex - 1) % 10));
          }
          ++h;
        }
      }
    }

    if (LXUtils.inRange(columnIndex % 10, 2, 6)) {
      final int x = (columnIndex % 10) - 2;
      int netNumber = columnIndex/10+1;
      if (orientation instanceof Apotheneum.Cylinder.Orientation) {
        netNumber = netNumber + 1;
        if (netNumber > 12) {
          netNumber = 1;
        }
      }
      final int digit0 = netNumber / 10;
      final int digit1 = netNumber % 10;
      final int[] top = DIGITS[digit0];
      final int[] bottom = DIGITS[digit1];
      for (int y = 0; y < 5; ++y) {
        if (top[x + 5*y] == 1) {
          colors[column.points[15+y].index] = LXColor.WHITE;
        }
        if (bottom[x + 5*y] == 1) {
          colors[column.points[22+y].index] = LXColor.WHITE;
        }
      }
    }
  }

  private void renderHorizontal(Apotheneum.Orientation orientation, Apotheneum.Column column, int columnIndex) {
    final int stripeY = this.stripeY.getValuei();
    final int color = this.color.getEnum().color;
    int py = 0;
    for (LXPoint p : column.points) {
      if (py == stripeY) {
        colors[p.index] = color;
      }
      ++py;
    }
  }

  private void renderVertical(Apotheneum.Orientation orientation, Apotheneum.Column column, int columnIndex) {
    if (columnIndex % 10 == this.stripeX.getValuei()) {
      setColor(column, this.color.getEnum().color);
    }
  }

  private void renderDMX(Apotheneum.Orientation orientation, Apotheneum.Column column, int columnIndex) {

    final int perStrand = orientation.available(columnIndex);
    final int strandIndex = columnIndex % 10;

    int d = (perStrand * strandIndex) % 170;

    if (columnIndex % 2 == 1) {
      for (int i = perStrand-1; i >= 0; --i) {
        int v0 = (3*d) % 256;
        int v1 = (3*d + 1) % 256;
        int v2 = (3*d + 2) % 256;
        d = (d+1) % 170;
        colors[column.points[i].index] = LXColor.rgba(v0, v1, v2, 255);
      }
    } else {
      for (int i = 0; i < perStrand; ++i) {
        int v0 = (3*d) % 256;
        int v1 = (3*d + 1) % 256;
        int v2 = (3*d + 2) % 256;
        d = (d+1) % 170;
        colors[column.points[i].index] = LXColor.rgba(v0, v1, v2, 255);
      }
    }
  }

  private void render(Apotheneum.Orientation orientation) {
    int columnIndex = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      renderColumn(orientation, column, columnIndex++);
    }
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    switch (this.target.getEnum()) {
      case ALL -> {
        render(Apotheneum.cube.exterior);
        render(Apotheneum.cylinder.exterior);
      }
      case CUBE -> render(Apotheneum.cube.exterior);
      case CYLINDER -> render(Apotheneum.cylinder.exterior);
      case SINGLE -> {
        final Net net = this.net.getEnum();
        final Apotheneum.Orientation orientation = net.getOrientation();
        final int columnStart = net.getColumnIndex();
        for (int i = 0; i < 10; ++i) {
          renderColumn(orientation, orientation.column(columnStart + i), columnStart + i);
        }
      }
    }
    switch (this.side.getEnum()) {
      case EXT -> {}
      case BOTH -> copyExterior();
      case INT -> {
        copyExterior();
        setColor(Apotheneum.cube.exterior, LXColor.BLACK);
        setColor(Apotheneum.cylinder.exterior, LXColor.BLACK);
      }
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ApotheneumTest test) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);

    final UIDropMenu net;

    addColumn(uiDevice, "Nets",
      newDropMenu(test.target),
      net = newDropMenu(test.net).setDirection(UIDropMenu.Direction.UP),
      sectionLabel("Side"),
      newDropMenu(test.side)
    ).setChildSpacing(4);

    uiDevice.addListener(test.target, p -> {
      net.setEnabled(test.target.getEnum() == Target.SINGLE);
    }, true);

    addVerticalBreak(ui, uiDevice);

    final UI2dComponent color, stripeX, stripeY;
    final UILabel description;
    final float cw = 120;

    addColumn(uiDevice, cw, "Mode",
      newDropMenu(test.mode).setWidth(cw),
      color = newDropMenu(test.color).setWidth(cw),
      stripeX = newIntegerBox(test.stripeX).setWidth(cw),
      stripeY = newIntegerBox(test.stripeY).setWidth(cw),
      description = (UILabel) new UILabel.Control(ui, cw, 16, "")
        .setBreakLines(true, true)
        .setPadding(0, 2)
        .setTextAlignment(VGraphics.Align.LEFT, VGraphics.Align.TOP)
    ).setChildSpacing(4);

    uiDevice.addListener(test.mode, p -> {
      description.setLabel(test.mode.getEnum().getDescription());
      final boolean isHorizontal = test.mode.getEnum() == Mode.HORIZONTAL_STRIPE;
      final boolean isVertical = test.mode.getEnum() == Mode.VERTICAL_STRIPE;
      color.setVisible(isHorizontal || isVertical);
      stripeX.setVisible(isVertical);
      stripeY.setVisible(isHorizontal);
    }, true);

  }

}
