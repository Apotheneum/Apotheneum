
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Nonrepeat 1")
public class Nonrepeat1 extends ApotheneumPattern {

  final CompoundParameter flicker =
    new CompoundParameter("Flicker", 0.3, 0, 1)
    .setDescription("Amount of random flicker");

  final CompoundParameter repeatHint =
    new CompoundParameter("Repeat", 0.5, 0, 1)
    .setDescription("How close the pattern appears to repeating");

  final CompoundParameter phaseDrift =
    new CompoundParameter("Phase", 0.2, 0, 2)
    .setDescription("Rate of phase misalignment");

  final CompoundParameter grainSize =
    new CompoundParameter("Granular", 8, 2, 40)
    .setDescription("Spacing or repetition hint");

  final CompoundParameter hueShift =
    new CompoundParameter("Hue", 0, 0, 360)
    .setDescription("Subtle hue drift");

  private float time = 0;

  public Nonrepeat1(LX lx) {
    super(lx);
    addParameter("flicker", this.flicker);
    addParameter("repeat", this.repeatHint);
    addParameter("phaseDrift", this.phaseDrift);
    addParameter("grainSize", this.grainSize);
    addParameter("hueShift", this.hueShift);
  }

  @Override
  public void render(double deltaMs) {
    time += deltaMs / 1000.0;

    float grain = grainSize.getValuef();
    float flickerAmount = flicker.getValuef();
    float hint = repeatHint.getValuef();
    float drift = phaseDrift.getValuef();
    float hueBase = hueShift.getValuef();

    for (LXPoint p : model.points) {
      float x = p.xn;
      float y = p.yn;

      float phase = x * grain + y * grain + time * drift;
      float modulator = (float)Math.sin(phase + Math.sin(y * 3 + time * 0.2f) * hint * 3);

      float flickerVal = (float)Math.random() * flickerAmount;
      float brightness = 100 * Math.max(0, modulator + flickerVal - flickerAmount / 2);
      float hue = (hueBase + modulator * 60 + 360) % 360;

      colors[p.index] = LXColor.hsba(hue, 100, brightness, 100);
    }
  }
}
