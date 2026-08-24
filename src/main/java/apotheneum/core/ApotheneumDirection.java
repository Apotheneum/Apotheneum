/**
 * Copyright 2026- Justin K. Belcher, Heron Arts LLC
 *
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 *
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package apotheneum.core;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumEffect;
import apotheneum.Apotheneum.Cube.Face;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.utils.LXUtils;

import static apotheneum.Apotheneum.CYLINDER_HEIGHT;

@LXCategory("Apotheneum/core")
@LXComponent.Name("Direction")
@LXComponent.Description("Shifts the cube and cylinder content in 90 degree increments")
public class ApotheneumDirection extends ApotheneumEffect {

  private static final int CYLINDER_STEP = Apotheneum.Cylinder.Ring.LENGTH / 4;
  private static final int CYLINDER_CHUNK = CYLINDER_HEIGHT * CYLINDER_STEP;

  public final DiscreteParameter steps =
    new DiscreteParameter("Steps", 0, 0, 4)
    .setDescription("Amount of rotation in 90 degree steps")
    .setWrappable(true)
    .setFormatter((value) -> String.format("%.0f", value * 90));

  private int[] copy = new int[0];

  public ApotheneumDirection(LX lx) {
    super(lx);
    addParameter("steps", this.steps);
  }

  @Override
  protected void render(double deltaMs, double enabledAmount) {
    final int steps = this.steps.getValuei();
    if (steps == 0 || enabledAmount <= 0) {
      return;
    }

    if (this.copy.length != this.colors.length) {
      this.copy = new int[this.colors.length];
    }
    System.arraycopy(this.colors, 0, this.copy, 0, this.colors.length);

    // Rotate Cube
    for (Apotheneum.Cube.Orientation orientation : Apotheneum.cube.orientations) {
      for (int i = 0; i < 4; i++) {
        Face from = orientation.faces[i];
        Face to = orientation.faces[LXUtils.wrap((i - steps), 0, 4)];
        System.arraycopy(
          this.copy,
          from.columns[0].points[0].index,
          this.colors,
          to.columns[0].points[0].index,
          from.model.size
        );
      }
    }

    // Rotate Cylinder
    for (Apotheneum.Cylinder.Orientation orientation : Apotheneum.cylinder.orientations) {
      for (int i = 0; i < 4; ++i) {
        Apotheneum.Column from = orientation.columns[i * CYLINDER_STEP];
        Apotheneum.Column to = orientation.columns[LXUtils.wrap((i - steps), 0, 4) * CYLINDER_STEP];
        System.arraycopy(
          this.copy,
          from.points[0].index,
          this.colors,
          to.points[0].index,
          CYLINDER_CHUNK
        );
      }
    }
  }

}
