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

import com.google.gson.JsonObject;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.transform.LXParameterizedMatrix;
import heronarts.lx.utils.LXUtils;

import heronarts.glx.ui.component.UIDropMenu;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("Surfacing")
@LXComponent.Description("Morphing surface across cube and cylinder")
public class Surfacing extends ApotheneumPattern implements UIDeviceControls<Surfacing> {

  private class Wave extends LXComponent implements LXOscComponent {

    public final CompoundParameter amplitude =
      new CompoundParameter("Amplitude", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

    public final CompoundParameter center =
      new CompoundParameter("Center", .5)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

    public final CompoundParameter wavelength =
      new CompoundParameter("Length", 0, 5)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

    public final CompoundParameter yaw =
      new CompoundParameter("Yaw", 0, 360)
      .setWrappable(true)
      .setUnits(CompoundParameter.Units.DEGREES)
      .setDescription("Yaw rotation");

    public final CompoundParameter phase =
      new CompoundParameter("Phase", 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setWrappable(true);

    private final int index;

    private Wave(LX lx, int index) {
      super(lx, "Wave " + (index+1));
      this.index = index;
      setParent(Surfacing.this);
      addParameter("amplitude", this.amplitude);
      addParameter("center", this.center);
      addParameter("wavelength", this.wavelength);
      addParameter("yaw", this.yaw);
      addParameter("phase", this.phase);
    }

    @Override
    public String getPath() {
      return "wave/" + (this.index + 1);
    }

    private float rx = 0;
    private float rz = 0;

    private void update() {
      final double yaw = Math.toRadians(this.yaw.getValue());
      this.rx = (float) Math.cos(yaw);
      this.rz = (float) Math.sin(yaw);
    }

  }

  public interface DistanceFunction {
    public float getDistance(float pos, float wave);
  }

  public enum Fill {
    SURFACE("Surface", (pos, wav) -> { return Math.abs(pos - wav); }),
    BELOW("Below", (pos, wav) -> { return pos - wav; }),
    Above("Above", (pos, wav) -> { return wav - pos; });

    private final String label;
    private final DistanceFunction function;

    private Fill(String label, DistanceFunction function) {
      this.label = label;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter base =
    new CompoundParameter("Base", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter size =
    new CompoundParameter("Size", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter fade =
    new CompoundParameter("Fade", .1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter yaw =
    new CompoundParameter("Yaw", 0, 360)
    .setWrappable(true)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Yaw rotation");

  public final CompoundParameter roll =
      new CompoundParameter("Roll", 0, 360)
    .setWrappable(true)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Roll rotation");

  public final EnumParameter<Fill> fillMode =
    new EnumParameter<Fill>("Fill", Fill.SURFACE)
    .setDescription("How to fill the wave");

  public final BooleanParameter cubeOn =
    new BooleanParameter("Cube", true)
    .setDescription("Whether cube is on");

  public final BooleanParameter cylinderOn =
    new BooleanParameter("Cylinder", true)
    .setDescription("Whether cylinder is on");

  public final CompoundParameter level =
    new CompoundParameter("Level", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  private final List<Wave> waves = new ArrayList<>();

  private final LXParameterizedMatrix transform = new LXParameterizedMatrix();

  public Surfacing(LX lx) {
    super(lx);
    for (int i = 0; i < 3; ++i) {
      this.waves.add(new Wave(lx, i));
    }
    addParameter("base", this.base);
    addParameter("size", this.size);
    addParameter("fade", this.fade);
    addParameter("fillMode", this.fillMode);
    addParameter("level", this.level);
    addParameter("cubeOn", this.cubeOn);
    addParameter("cylinderOn", this.cylinderOn);
    addTransformParameter("yaw", this.yaw);
    addTransformParameter("roll", this.roll);
    addArray("wave", this.waves);
  }

  private void addTransformParameter(String key, LXParameter parameter) {
    addParameter(key, parameter);
    this.transform.addParameter(parameter);
  }

  @Override
  protected void render(double deltaMs) {
    this.transform.update(matrix -> {
      matrix
        .translate(.5f, .5f, .5f)
        .rotateZ((float) Math.toRadians(-this.roll.getValue()))
        .rotateY((float) Math.toRadians(-this.yaw.getValue()))
        .translate(-.5f, -.5f, -.5f);
    });

    this.waves.forEach(wave -> wave.update());

    setApotheneumColor(LXColor.BLACK);

    final double level = this.level.getValue();
    if (level <= 0) {
      return;
    }

    int cylinderIndex = 0;
    if (this.cylinderOn.isOn()) {
      for (Apotheneum.Column column : Apotheneum.cylinder.exterior.columns) {
        renderColumn(column, cylinderIndex, level);
        ++cylinderIndex;
      }
      copyCylinderExterior();
    }

    if (this.cubeOn.isOn()) {
      cylinderIndex = -1;
      for (Apotheneum.Column column : Apotheneum.cube.exterior.columns) {
        renderColumn(column, cylinderIndex, level);
      }
      copyCubeExterior();
    }

  }

  private float[] cylinderLevels = new float[Apotheneum.RING_LENGTH];

  public float getCylinderLevel(int cylinderIndex) {
    return this.cylinderLevels[cylinderIndex];
  }

  private void renderColumn(Apotheneum.Column column, int cylinderIndex, double level) {
    final LXPoint c = column.points[0];

    final float xn =
      this.transform.m11 * c.xn +
      this.transform.m13 * c.zn;

    final float zn =
      this.transform.m31 * c.xn +
      this.transform.m33 * c.zn;

    float pos = this.base.getValuef();
    for (Wave wave : this.waves) {
      float xnn = .5f + wave.rx *(xn-.5f) + wave.rz * (zn-.5f);

      float basis = (xnn - wave.center.getValuef()) * wave.wavelength.getValuef();
      pos += 0.5f * wave.amplitude.getValuef() * Math.sin((wave.phase.getValue() + basis) * LX.TWO_PI);
    }

    final float size = this.size.getValuef();
    final float fade = this.fade.getValuef();
    final float falloff = 1f / (size * fade);

    if (cylinderIndex >= 0) {
      this.cylinderLevels[cylinderIndex] = pos + size;
    }

    final DistanceFunction function = this.fillMode.getEnum().function;
    for (LXPoint p : column.points) {
      float b = .5f - falloff * (function.getDistance(p.yn, pos) - size);
      if (b > 0) {
        colors[p.index] = LXColor.grayn(level * LXUtils.min(1, b));
      }
    }
  }

  private static final String KEY_WAVES = "waves";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_WAVES, LXSerializable.Utils.toArray(lx, this.waves));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    LXSerializable.Utils.loadArray(lx, this.waves, obj, KEY_WAVES);
  }

  @Override
  public void dispose() {
    this.waves.forEach(wave -> wave.dispose());
    super.dispose();
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Surfacing surfacing) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice,
      "Lev",
      newKnob(surfacing.level),
      newButton(surfacing.cubeOn),
      newButton(surfacing.cylinderOn)
    );

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Pos",
      newKnob(surfacing.yaw),
      newKnob(surfacing.roll),
      newHorizontalSlider(surfacing.base)
    );

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Size",
      newKnob(surfacing.size),
      newKnob(surfacing.fade),
      newDropMenu(surfacing.fillMode).setDirection(UIDropMenu.Direction.UP)
    );

    addVerticalBreak(ui, uiDevice);

    surfacing.waves.forEach(wave -> {
      addColumn(uiDevice,
        newHorizontalSlider(wave.amplitude),
        newHorizontalSlider(wave.center),
        newHorizontalSlider(wave.wavelength),
        newHorizontalSlider(wave.yaw),
        newHorizontalSlider(wave.phase)
      ).setChildSpacing(2);
    });
  }


}
