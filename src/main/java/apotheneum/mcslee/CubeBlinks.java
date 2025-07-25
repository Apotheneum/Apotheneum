package apotheneum.mcslee;

import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIButton;
import heronarts.glx.ui.component.UIKnob;
import heronarts.glx.ui.component.UILabel;
import heronarts.glx.ui.vg.VGraphics;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.LXComponentName;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.LXLayer;
import heronarts.lx.color.LXColor;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

import java.util.ArrayList;
import java.util.List;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;

@LXCategory("Apotheneum/mcslee")
@LXComponentName("Cube Blinks")
@LXComponent.Description("MIDI-Triggered ")
public class CubeBlinks extends ApotheneumPattern implements LXDeviceComponent.Midi, UIDeviceControls<CubeBlinks> {

  private class Blink extends LXLayer {

    private final double releaseMs;
    private final double peak;
    private final double releaseShape;
    private final double posShape;
    private final Algo algo;
    private final Apotheneum.Cube.Face face;
    private double basis = 0;

    protected Blink(LX lx, Algo algo, Apotheneum.Cube.Face face) {
      super(lx);
      this.algo = algo;
      this.face = face;
      this.releaseMs = LXUtils.lerp(1, triggerVelocity, velocityRelease.getValue()) * CubeBlinks.this.releaseMs.getValue();
      this.releaseShape = CubeBlinks.this.releaseShape.getValue();
      this.posShape = CubeBlinks.this.posShape.getValue();
      this.peak = LXUtils.lerp(1, triggerVelocity, velocityPeak.getValue()) * CubeBlinks.this.peak.getValue();
    }

    @Override
    public void run(double deltaMs) {

      this.basis += deltaMs / this.releaseMs;
      if (this.basis >= 1) {
        remove();
        return;
      }

      final double position = this.algo.getPosition(Math.pow(this.basis, this.posShape));
      final double contrast = CubeBlinks.this.contrast.getValue();
      final double releaseLevel = LXUtils.lerp(1, 0, Math.pow(this.basis, this.releaseShape));

      int ci = 0;
      for (LXModel column : this.face.columns) {
        final float xn = ci / (Apotheneum.GRID_WIDTH-1f);
        int pi = 0;
        for (LXPoint p : column.points) {
          final float yn = 1 - pi / (Apotheneum.GRID_HEIGHT-1f);
          double b = 1 - this.basis - contrast * this.algo.getDistance(this.basis, position, xn, yn);
          if (b > 0) {
            addColor(p.index, LXColor.grayn(this.peak * releaseLevel * LXUtils.min(1, b)));
          }
          ++pi;
        }
        ++ci;
      }
    }

  }

  private class BlinkTrigger extends TriggerParameter {

    private final String label;

    private BlinkTrigger(LX lx, Algo algo, String label, Apotheneum.Cube.Face ... faces) {
      super(algo.getClass().getSimpleName() + "-" + label, () -> {
        if (algo instanceof RandomEach) {
          for (Apotheneum.Cube.Face face : faces) {
            Algo algo2 = randomAlgo();
            if (algo2 != null) {
              addLayer(new Blink(lx, algo2, face));
            }
          }
        } else {
          Algo algo2 = algo;
          if (algo instanceof RandomAll) {
            algo2 = randomAlgo();
          }
          if (algo2 != null) {
            for (Apotheneum.Cube.Face face : faces) {
              addLayer(new Blink(lx, algo2, face));
            }
          }
        }

      });
      this.label = label;
    }
  }

  private final List<Algo> eligible = new ArrayList<>();

  private Algo randomAlgo() {
    this.eligible.clear();
    for (int i = 2; i < this.algos.length; ++i) {
      if (this.randomEligible.get(i).isOn()) {
        this.eligible.add(this.algos[i]);
      }
    }
    if (this.eligible.isEmpty()) {
      return null;
    }
    return this.eligible.get(LXUtils.randomi(0, this.eligible.size()-1));
  }

  public final Algo[] algos = {
    new RandomEach(),
    new RandomAll(),
    new Block(),
    new BlockIn(),
    new WipeLeft(),
    new WipeRight(),
    new WipeUp(),
    new WipeDown(),
    new HorizOut(),
    new HorizIn(),
    new VertOut(),
    new VertIn(),
    new XIn(),
    new XOut(),
    new RingIn(),
    new RingOut()
  };

  private double triggerVelocity = 1;

  private final List<BlinkTrigger> blinks = new ArrayList<BlinkTrigger>();
  private final List<BooleanParameter> randomEligible = new ArrayList<BooleanParameter>();

  public final CompoundParameter contrast =
    new CompoundParameter("Contrast", 4, 1, 8)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Contrast of wipe");

  public final CompoundParameter releaseMs =
    new CompoundParameter("Release", 1000, 250, 5000)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Release time of blink");

  public final CompoundParameter releaseShape =
    new CompoundParameter("RelShp", 1, 0.1, 10)
    .setExponent(3)
    .setDescription("Release shape");

  public final CompoundParameter posShape =
    new CompoundParameter("PosShp", 1, 0.1, 10)
    .setExponent(3)
    .setDescription("Position shape");

  public final CompoundParameter peak =
    new CompoundParameter("Peak", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Peak level of blink");

  public final CompoundParameter velocityPeak =
    new CompoundParameter("Vel>Peak", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount velocity influences peak level");

  public final CompoundParameter velocityRelease =
    new CompoundParameter("Vel>Rel", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount velocity influences release time");

  public final BooleanParameter midiAllFaces =
    new BooleanParameter("Midi>A", false)
    .setDescription("Whether MIDI notes always trigger all faces");

  public CubeBlinks(LX lx) {
    super(lx);
    addParameter("contrast", this.contrast);
    addParameter("releaseMs", this.releaseMs);
    addParameter("releaseShape", this.releaseShape);
    addParameter("posShape", this.posShape);
    addParameter("peak", this.peak);
    addParameter("velocityPeak", this.velocityPeak);
    addParameter("velocityRelease", this.velocityRelease);
    addParameter("midiAllFaces", this.midiAllFaces);
    for (Algo algo : this.algos) {
      String prefix = algo.getClass().getSimpleName().toLowerCase() + "-";
      addBlink(lx, prefix + "front", algo, "F", Apotheneum.cube.exterior.front);
      addBlink(lx, prefix + "right", algo, "R", Apotheneum.cube.exterior.right);
      addBlink(lx, prefix + "back", algo, "B", Apotheneum.cube.exterior.back);
      addBlink(lx, prefix + "left", algo, "L", Apotheneum.cube.exterior.left);
      addBlink(lx, prefix + "all", algo, "A", Apotheneum.cube.exterior.left, Apotheneum.cube.exterior.right, Apotheneum.cube.exterior.front, Apotheneum.cube.exterior.back);

      final BooleanParameter random =
        new BooleanParameter(algo.getClass().getSimpleName() + " Random", true)
        .setDescription("Whether " + algo.getClass().getSimpleName() + " is eliglble for random selection");
      this.randomEligible.add(random);
      addParameter(prefix + "random", random);

    }
  }

  private void addBlink(LX lx, String name, Algo algo, String label, Apotheneum.Cube.Face ... faces) {
    final BlinkTrigger blink = new BlinkTrigger(lx, algo, label, faces);
    this.blinks.add(blink);
    addParameter(name, blink);
  }

  private interface Algo {
    default String getLabel() {
      return getClass().getSimpleName();
    }
    double getPosition(double basis);
    double getDistance(double basis, double pos, float xn, float yn);
  }

  private class RandomEach implements Algo {

    public String getLabel() {
      return "?*";
    }

    @Override
    public double getPosition(double basis) {
      return 0;
    }

    @Override
    public double getDistance(double basis, double pos, float xn, float yn) {
      return 0;
    }

  }

  private class RandomAll implements Algo {

    public String getLabel() {
      return "?";
    }

    @Override
    public double getPosition(double basis) {
      return 0;
    }

    @Override
    public double getDistance(double basis, double pos, float xn, float yn) {
      return 0;
    }

  }

  private class Block implements Algo {

    public String getLabel() {
      return "■";
    }

    @Override
    public double getPosition(double basis) {
      return 0;
    }

    @Override
    public double getDistance(double basis, double pos, float xn, float yn) {
      return 0;
    }

  }

  private class BlockIn implements Algo {

    public String getLabel() {
      return "→■";
    }

    @Override
    public double getPosition(double basis) {
      return 0;
    }

    private static final double inv = 1 / Math.sqrt(.5f);

    @Override
    public double getDistance(double basis, double pos, float xn, float yn) {
      return basis * inv * LXUtils.max(Math.abs(xn - .5f), Math.abs(yn - .5f));
    }

  }

  private class HorizOut implements Algo {

    public String getLabel() {
      return "←→";
    }

    public double getPosition(double basis) {
      return LXUtils.lerp(0, .5, basis);
    }

    public double getDistance(double basis, double pos, float xn, float yn) {
      double distFromCenter = Math.abs(xn - .5);
      return Math.abs(distFromCenter - pos);
    }
  }

  private class HorizIn implements Algo {

    public String getLabel() {
      return "→←";
    }

    public double getPosition(double basis) {
      return LXUtils.lerp(.5, 0, basis);
    }

    public double getDistance(double basis, double pos, float xn, float yn) {
      double distFromCenter = Math.abs(xn - .5);
      return Math.abs(distFromCenter - pos);
    }
  }

  private class VertOut implements Algo {

    public String getLabel() {
      return "↕";
    }

    public double getPosition(double basis) {
      return LXUtils.lerp(0, .5, basis);
    }

    public double getDistance(double basis, double pos, float xn, float yn) {
      double distFromCenter = Math.abs(yn - .5);
      return Math.abs(distFromCenter - pos);
    }
  }

  private class VertIn implements Algo {

    public String getLabel() {
      return "▲";
    }

    public double getPosition(double basis) {
      return LXUtils.lerp(.5, 0, basis);
    }

    public double getDistance(double basis, double pos, float xn, float yn) {
      double distFromCenter = Math.abs(yn - .5);
      return Math.abs(distFromCenter - pos);
    }
  }

  private abstract class WipeX implements Algo {
    public double getDistance(double basis, double pos, float xn, float yn) {
      return Math.abs(xn - pos);
    }
  }

  private class WipeRight extends WipeX {

    public String getLabel() {
      return "→";
    }

    @Override
    public double getPosition(double basis) {
      return basis;
    }
  }

  private class WipeLeft extends WipeX {

    public String getLabel() {
      return "←";
    }

    @Override
    public double getPosition(double basis) {
      return 1 - basis;
    }
  }

  private abstract class WipeY implements Algo {
    public double getDistance(double basis, double pos, float xn, float yn) {
      return Math.abs(yn - pos);
    }
  }

  private class WipeUp extends WipeY {

    public String getLabel() {
      return "↑";
    }

    @Override
    public double getPosition(double basis) {
      return basis;
    }
  }

  private class WipeDown extends WipeY {

    public String getLabel() {
      return "↓";
    }

    @Override
    public double getPosition(double basis) {
      return 1 - basis;
    }
  }

  private abstract class X implements Algo {

    public double getDistance(double basis, double pos, float xn, float yn) {
      double xFromCenter = Math.abs(xn - .5);
      double yFromCenter = Math.abs(yn - .5);
      double distanceFromX = Math.abs(xFromCenter - yFromCenter);
      return Math.abs(distanceFromX - pos);
    }
  }

  private class XOut extends X {

    public String getLabel() {
      return "←×→";
    }

    public double getPosition(double basis) {
      return LXUtils.lerp(0, .5, basis);
    }
  }

  private class XIn extends X {

    public String getLabel() {
      return "→×←";
    }

    public double getPosition(double basis) {
      return LXUtils.lerp(.5, 0, basis);
    }
  }

  private abstract class Ring implements Algo {

    public double getDistance(double basis, double pos, float xn, float yn) {
      double radius = LXUtils.dist(xn, yn, .5, .5);
      return Math.abs(radius - pos);
    }
  }

  private class RingOut extends Ring {

    public String getLabel() {
      return "←◦→";
    }

    public double getPosition(double basis) {
      return LXUtils.lerp(0, .5, basis);
    }
  }

  private class RingIn extends Ring {

    public String getLabel() {
      return "→◦←";
    }

    public double getPosition(double basis) {
      return LXUtils.lerp(.5, 0, basis);
    }
  }

  @Override
  public void render(double deltaMs) {
    setColors(LXColor.BLACK);
    setColor(Apotheneum.cube.exterior, LXColor.BLACK);
  }

  @Override
  protected void afterLayers(double deltaMs) {
    copyCubeExterior();
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    this.triggerVelocity = note.getVelocity() / 127.;
    int trig = note.getPitch() % this.blinks.size();
    if (this.midiAllFaces.isOn()) {
      trig = 24 + (trig * 5) % 60;
    }
    this.blinks.get(trig).trigger();

    // Handled in the trigger above, set back to 1 for manual triggers
    this.triggerVelocity = 1;
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, CubeBlinks blinks) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL);
    uiDevice.setChildSpacing(4);
    addColumn(uiDevice, UIKnob.WIDTH, "Shape",
      newKnob(blinks.posShape, 0),
      newKnob(blinks.releaseMs, 0),
      newKnob(blinks.releaseShape, 0)
    ).setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, UIKnob.WIDTH, "Level",
      newKnob(blinks.peak, 0),
      newDoubleBox(blinks.velocityPeak, UIKnob.WIDTH),
      controlLabel(ui, "Vel>Pk").setWidth(UIKnob.WIDTH),
      newDoubleBox(blinks.velocityRelease, UIKnob.WIDTH),
      controlLabel(ui, "Vel>Rls").setWidth(UIKnob.WIDTH),
      newButton(blinks.midiAllFaces, UIKnob.WIDTH)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, UIKnob.WIDTH, "Contrast",
      newKnob(blinks.contrast, 0)
    ).setChildSpacing(6);
    addVerticalBreak(ui, uiDevice);

    final int BLINKS_PER_ALGO = 5;
    for (int i = 0; i < this.blinks.size(); i += BLINKS_PER_ALGO) {
      final int algoIndex = i/BLINKS_PER_ALGO;
      final Algo algo = this.algos[algoIndex];
      final UI2dContainer col = addColumn(uiDevice, BLINK_BUTTON_WIDTH, algo.getLabel(),
        blinkButton(this.blinks.get(i)),
        blinkButton(this.blinks.get(i+1)),
        blinkButton(this.blinks.get(i+2)),
        blinkButton(this.blinks.get(i+3)),
        blinkButton(this.blinks.get(i+4))
      ).setChildSpacing(6);
      if (algoIndex == 1) {
        new UILabel.Control(ui, BLINK_BUTTON_WIDTH, 16, "↳")
          .setTextAlignment(VGraphics.Align.CENTER)
          .setTextOffset(4, 0)
          .addToContainer(col);
      } else if (algoIndex >= 2) {
        newButton(this.randomEligible.get(algoIndex))
        .setLabel("?")
        .setWidth(28)
        .addToContainer(col);
      }
    }
  }

  private final static int BLINK_BUTTON_WIDTH = 28;

  private UIButton blinkButton(BlinkTrigger blink) {
    return (UIButton)
      newButton(blink)
      .setTriggerable(true)
      .setLabel(blink.label)
      .setWidth(BLINK_BUTTON_WIDTH);
  }


}
