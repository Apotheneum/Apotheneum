/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
 *
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
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