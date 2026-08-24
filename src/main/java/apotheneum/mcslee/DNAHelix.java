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
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("DNA Helix")
@LXComponent.Description("Morphing double-helix with noise deformation")
public class DNAHelix extends ApotheneumPattern {

  public final CompoundParameter twist =
    new CompoundParameter("Twist", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setWrappable(true)
    .setDescription("Twist");

  public final CompoundParameter winding =
    new CompoundParameter("Winding", 1, 10)
    .setDescription("Amount of DNA winding");

  public final CompoundParameter width =
    new CompoundParameter("Width", 5, Apotheneum.RING_LENGTH)
    .setDescription("Width of DNA strands");

  public final CompoundParameter noise =
    new CompoundParameter("Noise", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Noise");

  public final CompoundParameter noiseDepth =
    new CompoundParameter("NsDepth", 0, -1, 3)
    .setDescription("Noise Depth");

  public final CompoundParameter noiseScale =
    new CompoundParameter("NsScale", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Noise Scale");

  public final CompoundParameter noiseFall =
    new CompoundParameter("NsFall", .1, 0, 1)
    .setUnits(CompoundParameter.Units.HERTZ)
    .setDescription("Speed of vertical noise motion");

  public final CompoundParameter noiseMorph =
    new CompoundParameter("NsMorph", .1, 0, 1)
    .setUnits(CompoundParameter.Units.HERTZ)
    .setDescription("Speed of noise morphing");

  public final BooleanParameter cylinder =
    new BooleanParameter("Cylinder", true)
    .setDescription("Whether cylinder rendering is on");

  public final BooleanParameter cube =
    new BooleanParameter("Cube", false)
    .setDescription("Whether cube rendering is on");

  public final BooleanParameter alternate =
    new BooleanParameter("Alternate", false)
    .setDescription("Whether cube twists in the alternate direction");

  public final CompoundParameter offset =
    new CompoundParameter("Offset", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setWrappable(true)
    .setDescription("Phase offset between cylinder and cube");

  private final SawLFO noiseX = new SawLFO(0, 256, FunctionalParameter.create(() -> {
    return 256000 / noiseMorph.getValue();
  }));

  private final SawLFO noiseY = new SawLFO(0, 256, FunctionalParameter.create(() -> {
    return 256000 / noiseFall.getValue();
  }));

  public DNAHelix(LX lx) {
    super(lx);
    addParameter("twist", this.twist);
    addParameter("winding", this.winding);
    addParameter("width", this.width);
    addParameter("noise", this.noise);
    addParameter("noiseDepth", this.noiseDepth);
    addParameter("noiseScale", this.noiseScale);
    addParameter("noiseFall", this.noiseFall);
    addParameter("noiseMorph", this.noiseMorph);
    addParameter("cylinder", this.cylinder);
    addParameter("cube", this.cube);
    addParameter("alternate", this.alternate);
    addParameter("offset", this.offset);
    startModulator(this.noiseX);
    startModulator(this.noiseY);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    final float noise = this.noise.getValuef();
    final float twist = this.twist.getValuef();
    final float winding = LXUtils.lerpf(0, this.winding.getValuef(), (1-noise)*(1-noise));
    final float coeff = winding / Apotheneum.CYLINDER_HEIGHT;
    final float falloff = 100 / this.width.getValuef();

    final float noiseDepth = this.noiseDepth.getValuef();
    final float noiseScale = this.noiseScale.getValuef() * .1f;
    final float noiseX = this.noiseX.getValuef();
    final float noiseY = this.noiseY.getValuef();

    if (this.cylinder.isOn()) {
      int ri = -Apotheneum.CYLINDER_HEIGHT / 2;
      int ni = 0;
      for (Apotheneum.Cylinder.Ring ring : Apotheneum.cylinder.exterior.rings) {
        float basis =
          100 +
          (twist + ri * coeff) +
          noise * noiseDepth * LXUtils.noise(noiseX, 0, -noiseY + (ni * noiseScale));
        float pos = Apotheneum.Cylinder.Ring.LENGTH * (basis % 1f);
        int pi = 0;
        for (LXPoint p : ring.points) {
          float dist = LXUtils.wrapdistf((2 * pi) % ring.points.length, pos, ring.points.length);
          ++pi;
          colors[p.index] = LXColor.gray(LXUtils.max(0, 100 - falloff * dist));
        }
        ++ri;
        ++ni;
      }
      copy(Apotheneum.cylinder.exterior, Apotheneum.cylinder.interior);
    }

    if (this.cube.isOn()) {
      final int alternate = this.alternate.isOn() ? -1 : 1;
      final float offset  = this.offset.getValuef();
      int ri = -Apotheneum.GRID_HEIGHT / 2;
      int ni = 0;
      for (Apotheneum.Cube.Ring ring : Apotheneum.cube.exterior.rings) {
        float basis =
          100 + offset + alternate * (
            (twist + ri * coeff) +
            noise * noiseDepth * LXUtils.noise(noiseX, 0, -noiseY + (ni * noiseScale))
          );
        float pos = Apotheneum.Cube.Ring.LENGTH * (basis % 1f);
        int pi = 0;
        for (LXPoint p : ring.points) {
          float dist = LXUtils.wrapdistf((2 * pi) % ring.points.length, pos, ring.points.length);
          ++pi;
          colors[p.index] = LXColor.gray(LXUtils.max(0, 100 - falloff * dist));
        }
        ++ri;
        ++ni;
      }
      copyCubeExterior();
    }
  }

}
