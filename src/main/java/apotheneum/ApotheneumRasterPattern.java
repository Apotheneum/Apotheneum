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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import heronarts.glx.ui.UI2dComponent;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIButton;
import heronarts.lx.LX;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;

public abstract class ApotheneumRasterPattern extends ApotheneumPattern {

  protected static final int RASTER_WIDTH = Apotheneum.GRID_WIDTH;
  protected static final int RASTER_HEIGHT = Apotheneum.GRID_HEIGHT;

  private final BufferedImage raster;
  private final Graphics2D graphics;
  private final int[] pixels = new int[RASTER_WIDTH * RASTER_HEIGHT];

  public final BooleanParameter exteriorFront =
    new BooleanParameter("ExtFront", true)
    .setDescription("Render to exterior front");

  public final BooleanParameter exteriorRight =
    new BooleanParameter("ExtRight", true)
    .setDescription("Render to exterior right");

  public final BooleanParameter exteriorBack =
    new BooleanParameter("ExtBack", true)
    .setDescription("Render to exterior back");

  public final BooleanParameter exteriorLeft =
    new BooleanParameter("ExtLeft", true)
    .setDescription("Render to exterior left");

  public final BooleanParameter interiorFront =
    new BooleanParameter("IntFront", false)
    .setDescription("Render to interior front");

  public final BooleanParameter interiorRight =
    new BooleanParameter("IntRight", false)
    .setDescription("Render to interior right");

  public final BooleanParameter interiorBack =
    new BooleanParameter("IntBack", false)
    .setDescription("Render to interior back");

  public final BooleanParameter interiorLeft =
    new BooleanParameter("IntLeft", false)
    .setDescription("Render to interior left");

  protected ApotheneumRasterPattern(LX lx) {
    super(lx);
    this.raster = new BufferedImage(50, 45, BufferedImage.TYPE_INT_ARGB);
    this.graphics = this.raster.createGraphics();
    this.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    addParameter("exteriorFront", this.exteriorFront);
    addParameter("exteriorRight", this.exteriorRight);
    addParameter("exteriorBack", this.exteriorBack);
    addParameter("exteriorLeft", this.exteriorLeft);
    addParameter("interiorFront", this.interiorFront);
    addParameter("interiorRight", this.interiorRight);
    addParameter("interiorBack", this.interiorBack);
    addParameter("interiorLeft", this.interiorLeft);
  }

  protected void writeFace(Apotheneum.Cube.Face face) {
    int i = 0;
    for (LXPoint p : face.model.points) {
      colors[p.index] = this.pixels[(i / RASTER_HEIGHT) + RASTER_WIDTH * (i % RASTER_HEIGHT)];
      ++i;
    }
  }

  protected void writeFace(BooleanParameter write, Apotheneum.Cube.Face face) {
    if (write.isOn()) {
      writeFace(face);
    }
  }

  @Override
  protected final void render(double deltaMs) {
    render(deltaMs, this.graphics);
    this.raster.getRGB(0, 0, RASTER_WIDTH, RASTER_HEIGHT, this.pixels, 0, RASTER_WIDTH);
    writeFace(this.exteriorFront, Apotheneum.cube.exterior.front);
    writeFace(this.exteriorRight, Apotheneum.cube.exterior.right);
    writeFace(this.exteriorBack, Apotheneum.cube.exterior.back);
    writeFace(this.exteriorLeft, Apotheneum.cube.exterior.left);
    writeFace(this.interiorFront, Apotheneum.cube.interior.front);
    writeFace(this.interiorRight, Apotheneum.cube.interior.right);
    writeFace(this.interiorBack, Apotheneum.cube.interior.back);
    writeFace(this.interiorLeft, Apotheneum.cube.interior.left);
  }

  protected void clear() {
    clear(Color.BLACK);
  }

  protected void clear(Color color) {
    this.graphics.setBackground(color);
    this.graphics.clearRect(0, 0, RASTER_WIDTH, RASTER_HEIGHT);
  }

  protected abstract void render(double deltaMs, Graphics2D graphics);

  @Override
  public void dispose() {
    super.dispose();
    this.graphics.dispose();
  }

  protected UI2dComponent buildFaceControls(UI ui, UIDevice uiDevice, float size) {
    final float padding = 28;
    final float buttonWidth = 12;
    final float intPadding = buttonWidth + 2;

    return new UI2dContainer(0, 0, size, size).addChildren(
      new UIButton.Action(0, 0, buttonWidth, buttonWidth) {
        @Override
        public void onToggle(boolean enabled) {
          if (enabled) {
            exteriorFront.toggle();
            exteriorRight.toggle();
            exteriorBack.toggle();
            exteriorLeft.toggle();
          }
        }
      },

      new UIButton.Action(intPadding, intPadding, buttonWidth, buttonWidth) {
        @Override
        public void onToggle(boolean enabled) {
          if (enabled) {
            interiorFront.toggle();
            interiorRight.toggle();
            interiorBack.toggle();
            interiorLeft.toggle();
          }
        }
      },

      new UIButton(padding, 0, size - 2*padding, buttonWidth, this.exteriorBack).setLabel(""),
      new UIButton(0, padding, buttonWidth, size - 2*padding, this.exteriorLeft).setLabel(""),
      new UIButton(size - buttonWidth, padding, buttonWidth, size - 2*padding, this.exteriorRight).setLabel(""),
      new UIButton(padding, size - buttonWidth, size - 2*padding, buttonWidth, this.exteriorFront).setLabel(""),

      new UIButton(padding, intPadding, size - 2*padding, buttonWidth, this.interiorBack).setLabel(""),
      new UIButton(intPadding, padding, buttonWidth, size - 2*padding, this.interiorLeft).setLabel(""),
      new UIButton(size - buttonWidth - intPadding, padding, buttonWidth, size - 2*padding, this.interiorRight).setLabel(""),
      new UIButton(padding, size - buttonWidth - intPadding, size - 2*padding, buttonWidth, this.interiorFront).setLabel("")
    );
  }

}
