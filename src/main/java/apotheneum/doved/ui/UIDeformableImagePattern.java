package apotheneum.doved.ui;

/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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

import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.studio.ui.pattern.UIImagePattern;
import apotheneum.doved.components.DeformableImage;
import apotheneum.doved.patterns.DeformableImagePattern;

/**
 * Device controls for {@link DeformableImagePattern}.
 *
 * <p>
 * This extends Chromatik's own {@link UIImagePattern.ImageControls} rather than
 * reimplementing it. Everything to do with picking, loading, displaying and
 * undoing an image path — including the file textbox, the Open dialog, the
 * Reload button and the GIF controls — comes from upstream. The only addition
 * here is the pair of kaleidoscope columns.
 */
public class UIDeformableImagePattern implements UIDeviceControls<DeformableImagePattern> {

  public static class ImageControls extends UIImagePattern.ImageControls {

    protected ImageControls(UI ui, DeformableImage image) {
      super(ui, image);
    }

    /**
     * Appends the kaleidoscope controls after the stock image column.
     *
     * <p>
     * Called from the superclass constructor, so subclass fields are not yet
     * initialized — read the image off the inherited {@code image} field, which
     * upstream assigns before this runs.
     */
    @Override
    protected void addImageColumn(UI ui) {
      super.addImageColumn(ui);

      final DeformableImage image = (DeformableImage) this.image;

      addVerticalBreak(ui, this);

      addColumn(this, UIKnob.WIDTH, "Kaleidoscope",
          newKnob(image.kaleidoscope.params.segments, 0),
          newKnob(image.kaleidoscope.params.rotatePhi, 0),
          newKnob(image.kaleidoscope.params.rotateTheta, 0)).setChildSpacing(6);

      addColumn(this, UIKnob.WIDTH, "K-Center",
          newKnob(image.kaleidoscope.params.x, 0),
          newKnob(image.kaleidoscope.params.y, 0),
          newKnob(image.kaleidoscope.params.z, 0)).setChildSpacing(6);
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, DeformableImagePattern device) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);
    new ImageControls(ui, device.image).addToContainer(uiDevice);
  }

}
