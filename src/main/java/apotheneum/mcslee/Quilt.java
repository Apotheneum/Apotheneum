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

import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;
import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;

import java.util.List;
import java.util.ArrayList;

@LXComponent.Name("Quilt")
@LXCategory("Apotheneum/mcslee")
@LXComponent.Description("A quilted mesh of linear segments overlapping horizontally and vertically")
public class Quilt extends ApotheneumPattern implements UIDeviceControls<Quilt> {

  public final CompoundParameter minLength =
    new CompoundParameter("Min Length", 1, 1, 50)
    .setDescription("Minimum thread length");

  public final CompoundParameter maxLength =
    new CompoundParameter("Max Length", 10, 1, 200)
    .setDescription("Maximum thread length");

  public final CompoundParameter biasLength =
    new CompoundParameter("Bias Length", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Bias crawlers towards shorter or longer lengths");

  public final CompoundParameter minSpeed =
    new CompoundParameter("Min Speed", 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Minimum crawler speed");

  public final CompoundParameter maxSpeed =
    new CompoundParameter("Max Speed", 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Maximum crawler speed");

  public final CompoundParameter biasSpeed =
    new CompoundParameter("Bias Speed", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Bias crawlers towards slower or faster movement ");

  public final ColorParameter hColor =
    new ColorParameter("H-Clor", LXColor.WHITE)
    .setDescription("Horizontal color");

  public final ColorParameter vColor =
    new ColorParameter("V-Clr", LXColor.WHITE)
    .setDescription("Vertical color");

  private abstract class Stripe {
    protected final Apotheneum.Component component;
    protected final int index;
    protected final int size;

    private final double rnd;

    private double basis = 0;

    private Stripe(Apotheneum.Component component, int index, int size) {
      this.component = component;
      this.index = index;
      this.size = size;
      this.basis = Math.random();
      this.rnd = Math.random();
    }

    protected abstract LXPoint[] points();

    protected abstract int getStripeColor(double b);

    protected void render(double deltaMs) {
      final double spd = Crawlers.bias(minSpeed, maxSpeed, biasSpeed, this.rnd);
      this.basis = (this.basis + deltaMs * spd / 5000) % 1.;

      final double length = Crawlers.bias(minLength, maxLength, biasLength, this.rnd);
      final double falloff = 100 / length;

      final double tb = ((this.index % 2) == 0) ? this.basis : 1-this.basis;
      final double pos = tb * this.size;
      int pi = 0;
      for (LXPoint p : points()) {
        final double b = 100 - falloff * LXUtils.wrapdist(pos, pi, this.size);
        if (b > 0) {
          colors[p.index] = LXColor.lightest(colors[p.index], getStripeColor(b));
        }
        ++pi;
      }
    }
  }

  private class VStripe extends Stripe {
    private VStripe(Apotheneum.Component component, int index) {
      super(component, index, component.height());
    }

    @Override
    public int getStripeColor(double b) {
      return LXColor.hsb(vHue, vSaturation, b * vBrightness);
    }

    @Override
    protected LXPoint[] points() {
      return this.component.exterior().column(this.index).points;
    }
  }

  private class HStripe extends Stripe {

    private HStripe(Apotheneum.Component component, int index) {
      super(component, index, component.width());
    }

    @Override
    public int getStripeColor(double b) {
      return LXColor.hsb(hHue, hSaturation, b * hBrightness);
    }

    @Override
    protected LXPoint[] points() {
      return this.component.exterior().ring(this.index).points;
    }

  }

  private final List<VStripe> vStripes = new ArrayList<>();
  private final List<HStripe> hStripes = new ArrayList<>();

  public Quilt(LX lx) {
    super(lx);
    addParameter("minLength", this.minLength);
    addParameter("maxLength", this.maxLength);
    addParameter("biasLength", this.biasLength);
    addParameter("minSpeed", this.minSpeed);
    addParameter("maxSpeed", this.maxSpeed);
    addParameter("biasSpeed", this.biasSpeed);
    addParameter("hColor", this.hColor);
    addParameter("vColor", this.vColor);

    for (int i = 0; i < Apotheneum.cube.exterior.columns.length; ++i) {
      this.vStripes.add(new VStripe(Apotheneum.cube, i));
    }
    for (int i = 0; i < Apotheneum.cube.exterior.height(); ++i) {
      // Cube ratio 200 / 45, do 3x
      this.hStripes.add(new HStripe(Apotheneum.cube, i));
      this.hStripes.add(new HStripe(Apotheneum.cube, i));
      this.hStripes.add(new HStripe(Apotheneum.cube, i));
    }
    for (int i = 0; i < Apotheneum.cylinder.exterior.columns.length; ++i) {
      this.vStripes.add(new VStripe(Apotheneum.cylinder, i));
    }
    for (int i = 0; i < Apotheneum.cylinder.exterior.height(); ++i) {
      this.hStripes.add(new HStripe(Apotheneum.cylinder, i));
      this.hStripes.add(new HStripe(Apotheneum.cylinder, i));
    }
  }

  private double hHue, hSaturation, hBrightness, vHue, vSaturation, vBrightness;

  @Override
  public void render(double deltaMs) {
    setColors(LXColor.BLACK);

    // Ensure cleared regardless of view
    setColor(Apotheneum.cube.exterior, LXColor.BLACK);
    setColor(Apotheneum.cylinder.exterior, LXColor.BLACK);

    this.hHue = this.hColor.hue.getValue();
    this.vHue = this.vColor.hue.getValue();
    this.hSaturation = this.hColor.saturation.getValue();
    this.vSaturation = this.vColor.saturation.getValue();
    this.hBrightness = this.hColor.brightness.getNormalized();
    this.vBrightness = this.vColor.brightness.getNormalized();

    this.vStripes.forEach(vStripe -> vStripe.render(deltaMs));
    this.hStripes.forEach(hStripe -> hStripe.render(deltaMs));
    copyExterior();
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Quilt quilt) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL);
    uiDevice.setChildSpacing(2);
    addColumn(uiDevice, UIKnob.WIDTH, "Speed",
      newKnob(quilt.minSpeed, 0),
      newKnob(quilt.maxSpeed, 0),
      newKnob(quilt.biasSpeed, 0)
    ).setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, UIKnob.WIDTH, "Length",
      newKnob(quilt.minLength, 0),
      newKnob(quilt.maxLength, 0),
      newKnob(quilt.biasLength, 0)
    ).setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, UIKnob.WIDTH, "Color",
      newColorControl(quilt.hColor, 0),
      newColorControl(quilt.vColor, 0)
    );
  }
}
