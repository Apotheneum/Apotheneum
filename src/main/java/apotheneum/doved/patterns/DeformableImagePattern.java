/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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

package apotheneum.doved.patterns;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent.Description;
import heronarts.lx.pattern.LXPattern;
import apotheneum.doved.components.DeformableImage;
import apotheneum.Apotheneum;

@LXCategory(Apotheneum.IMAGE_CATEGORY)
@Description("Renders a 2D image with Deformations")
public class DeformableImagePattern extends LXPattern {
  public final DeformableImage image;

  public DeformableImagePattern(LX lx) {
    super(lx);
    this.image = new DeformableImage(this.lx);
    this.addAutomationChild("image", this.image);
  }

  protected void run(double deltaMs) {
    this.image.animateGif(deltaMs);
    this.image.render(this.model, this.colors);
  }

  public void dispose() {
    LX.dispose(this.image);
    super.dispose();
  }
}