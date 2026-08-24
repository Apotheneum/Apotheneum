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
import heronarts.glx.ui.UI2dContainer;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Description("Distorted and multiplying circles on cube faces")
public class Enso extends ApotheneumPattern implements UIDeviceControls<Enso>, ApotheneumPattern.Midi {

  public final CompoundParameter radius =
    new CompoundParameter("Radius", 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Ring radius");

  public final CompoundParameter width =
    new CompoundParameter("Width", 0.2)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Ring width");

  public final CompoundParameter contrast =
    new CompoundParameter("Contrast", 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Ring contrast");

  public final CompoundParameter noise =
    new CompoundParameter("Noise", 0, 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Ring deformation");

  public final CompoundParameter noiseScale =
    new CompoundParameter("NoiseScl", .5, 5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Ring deformation scale");

  public final CompoundParameter noiseSpeed =
    new CompoundParameter("NoiseSpd", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Ring deformation speed");

  public final CompoundParameter stripX =
    new CompoundParameter("StripX", 0, 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strip X deformation");

  public final CompoundParameter stripXPos =
    new CompoundParameter("X-Pos", 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strip X deformation center");

  public final CompoundParameter stripXWidth =
    new CompoundParameter("X-Wid", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strip X deformation width");

  public final CompoundParameter stripY =
    new CompoundParameter("StripY", 0, 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strip X deformation");

  public final CompoundParameter stripYPos =
    new CompoundParameter("Y-Pos", 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strip Y deformation center");

  public final CompoundParameter stripYWidth =
    new CompoundParameter("Y-Wid", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strip Y deformation width");

  public final CompoundParameter stripSpeed =
    new CompoundParameter("StripSpd", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Ring deformation speed");

  public final BooleanParameter[] dup = new BooleanParameter[4];
  public final BooleanParameter[] trip = new BooleanParameter[4];

  public final String[] sides = { "Front", "Right", "Back", "Left" };

  public Enso(LX lx) {
    super(lx);
    addParameter("radius", this.radius);
    addParameter("width", this.width);
    addParameter("contrast", this.contrast);
    addParameter("noise", this.noise);
    addParameter("noiseScale", this.noiseScale);
    addParameter("noiseSpeed", this.noiseSpeed);
    addParameter("stripX", this.stripX);
    addParameter("stripY", this.stripY);
    addParameter("stripSpeed", this.stripSpeed);
    addParameter("stripXPos", this.stripXPos);
    addParameter("stripYPos", this.stripYPos);
    addParameter("stripXWidth", this.stripXWidth);
    addParameter("stripYWidth", this.stripYWidth);
    for (int i = 0; i < 4; ++i) {
      this.dup[i] =
        new BooleanParameter("2x" + sides[i])
        .setDescription("Duplicates face");
      addParameter("dup-" + (i+1), this.dup[i]);
      this.trip[i] =
        new BooleanParameter("3x" + sides[i])
        .setDescription("Triplicates face");
      addParameter("trip-" + (i+1), this.trip[i]);
    }
  }

  private static final float ASPECT = 50f / 45f;
  private static final float XOFF = (ASPECT-1) * .5f;

  private float nz = 0;
  private float sz = 0;

  @Override
  protected void render(double deltaMs) {
    final float radius = .5f * this.radius.getValuef();
    final float width = .5f * this.width.getValuef();
    final float contrast = this.contrast.getValuef();
    final float noise = this.noise.getValuef();
    final float noiseScale = this.noiseScale.getValuef();
    final float stripX = this.stripX.getValuef();
    final float stripY = this.stripY.getValuef();
    final float stripXPos = this.stripXPos.getValuef();
    final float stripYPos = this.stripYPos.getValuef();
    final float stripXWidth = this.stripXWidth.getValuef();
    final float stripYWidth = this.stripYWidth.getValuef();

    this.nz = (float) ((this.nz + this.noiseSpeed.getValuef() * deltaMs / 1000f) % 256.);
    this.sz = (float) ((this.sz + this.stripSpeed.getValuef() * deltaMs / 1000f) % 256.);

    final float max = LXUtils.lerpf(100, 300, contrast);
    final float falloff = max / width;

    final float deformMax = 3;
    final float stripXFalloff = (deformMax + 1) / stripXWidth;
    final float stripYFalloff = (deformMax + 1) / stripYWidth;

    setColors(LXColor.BLACK);
    int faceIndex = 0;
    for (Apotheneum.Cube.Face face : Apotheneum.cube.exterior.faces) {
      final float zoom =
        (this.dup[faceIndex].isOn() ? 2 : 1) *
        (this.trip[faceIndex].isOn() ? 3 : 1);

      for (LXPoint p : face.model.points) {
        // Point xn/yn space
        final float pxn = Math.abs(p.xn - p.zn);
        final float pyn = p.yn;

        // With zoom applied
        final float pxnz = (pxn * zoom) % 1f;
        final float pynz = (pyn * zoom) % 1f;

        final int zIndex =
          (int) (pxn * zoom) +
          3 * (int) (pyn * zoom);

        final float stripXAmount = stripX * LXUtils.constrainf(deformMax - stripXFalloff * Math.abs(pyn - stripXPos), 0, 1);
        final float stripYAmount = stripY * LXUtils.constrainf(deformMax - stripYFalloff * Math.abs(pxn - stripYPos), 0, 1);

        // Into circle radius with strip deformation noise
        final float xn = -XOFF + pxnz * ASPECT + stripXAmount * LXUtils.noise(pyn * 50, faceIndex, this.sz);
        final float yn = pynz + stripYAmount * LXUtils.noise(pxn * 50, faceIndex + 128, this.sz);

        // Normalized for noise deform
        final float mag = LXUtils.distf(xn, yn, .5f, .5f);
        final float magInvSqrt = (float) (1 / Math.sqrt(mag));
        final float xnn = (xn - .5f) * magInvSqrt;
        final float ynn = (yn - .5f) * magInvSqrt;

        final float nv = LXUtils.noise(xnn * noiseScale, ynn * noiseScale, zIndex + faceIndex + this.nz);
        final float r = mag * (1 + noise * nv);

        colors[p.index] = LXColor.gray(LXUtils.constrain(max - falloff * Math.abs(r - radius), 0, 100));
      }
      ++faceIndex;
    }

    copyCubeExterior();
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Enso enso) {
    uiDevice.setLayout(UI2dContainer.Layout.HORIZONTAL, 0);

    addColumn(uiDevice,
      "Size",
      newKnob(enso.radius),
      newKnob(enso.width),
      newKnob(enso.contrast)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Noise",
      newKnob(enso.noise),
      newKnob(enso.noiseScale),
      newKnob(enso.noiseSpeed)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Strip",
      newKnob(enso.stripX),
      newKnob(enso.stripY),
      newKnob(enso.stripSpeed)
    ).setChildSpacing(6);

    addColumn(uiDevice,
      "Pos",
      newKnob(enso.stripXPos),
      newKnob(enso.stripYPos)
    ).setChildSpacing(6);

    addColumn(uiDevice,
      "Width",
      newKnob(enso.stripXWidth),
      newKnob(enso.stripYWidth)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice,
      "Dup",
      newButton(enso.dup[0]).setTriggerable(true),
      newButton(enso.dup[1]).setTriggerable(true),
      newButton(enso.dup[2]).setTriggerable(true),
      newButton(enso.dup[3]).setTriggerable(true),
      newButton(enso.trip[0]).setTriggerable(true),
      newButton(enso.trip[1]).setTriggerable(true),
      newButton(enso.trip[2]).setTriggerable(true),
      newButton(enso.trip[3]).setTriggerable(true)
    )
    .setChildSpacing(2)
    .setLeftMargin(4);

  }

  private void toggleDupTrip(MidiNote note, boolean on) {
    int idx = note.getPitch();
    if (LXUtils.inRange(idx, 0, 3)) {
      this.dup[idx].setValue(on);
    } else if (LXUtils.inRange(idx, 4, 7)) {
      this.trip[idx-4].setValue(on);
    }
  }

  @Override
  public void noteOnReceived(MidiNoteOn noteOn) {
    toggleDupTrip(noteOn, true);
  }

  @Override
  public void noteOffReceived(MidiNote noteOff) {
    toggleDupTrip(noteOff, false);

  }

}
