
package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Crystalline Breath")
public class CrystallineBreath extends ApotheneumPattern {

  // Hexagon grid density
  final CompoundParameter grid =
    new CompoundParameter("Grid", 4.0, 0.01, 20.0)
      .setDescription("Density of hex grid");
  // Base static distortion
  final CompoundParameter warp =
    new CompoundParameter("Warp", 0.0, 0.0, 1.0)
      .setDescription("Static noise warp");
  // LFO motion depth
  final CompoundParameter wave =
    new CompoundParameter("Wave", 0.3, 0.0, 1.0)
      .setDescription("Depth of undulating wave");
  // Edge sharpness
  final CompoundParameter sharp =
    new CompoundParameter("Sharp", 20.0, 1.0, 20.0)
      .setDescription("Higher = crisper hex edges");
  // Invert fill/edge
  final BooleanParameter invert =
    new BooleanParameter("Invert", false)
      .setDescription("Toggle between hex fill lit or edges lit");

  public CrystallineBreath(LX lx) {
    super(lx);
    addParameter("grid", this.grid);
    addParameter("warp", this.warp);
    addParameter("wave", this.wave);
    addParameter("sharp", this.sharp);
    addParameter("invert", this.invert);
  }

  @Override
  public void render(double deltaMs) {
    float time = lx.engine.nowMillis / 1000.f;
    float size = grid.getValuef();
    float baseDist = warp.getValuef();
    float motionAmt = wave.getValuef();
   //float speed = speedKnob.getValuef();
    float sharpVal = sharp.getValuef();
    boolean inv = invert.getValueb();

    // symmetric LFO wave [-1,1]
    float waveAmt = (float)Math.sin(time);

    for (LXPoint p : model.points) {
      // normalized coords
      float x = p.xn;
      float y = p.yn;

      // apply symmetric distortion
      float distTotal = baseDist + motionAmt * waveAmt;
      x += distTotal * (float)Math.sin(5 * y);
      y += distTotal * (float)Math.cos(5 * x);

      // axial hex coords
      float q = (2f/3f * x) * size;
      float r = (-1f/3f * x + (float)Math.sqrt(3)/3f * y) * size;
      float s = -q - r;

      // nearest hex cell
      int qi = Math.round(q);
      int ri = Math.round(r);
      int si = Math.round(s);
      float qDist = Math.abs(q - qi);
      float rDist = Math.abs(r - ri);
      float sDist = Math.abs(s - si);
      if (qDist > rDist && qDist > sDist) {
        qi = -ri - si;
      } else if (rDist > sDist) {
        ri = -qi - si;
      } else {
        si = -qi - ri;
      }

      // distance from hex center for fill
      float hexDist = Math.max(Math.max(Math.abs(q - qi), Math.abs(r - ri)), Math.abs(s - si));
      // compute fill factor
      float rawFill = (0.5f - hexDist) * sharpVal;
      float fill = LXUtils.clampf(rawFill, 0f, 1f);

      // invert if needed
      float val = inv ? (1f - fill) : fill;
      // brightness static
      int brightness = (int)(val * 100f);

      colors[p.index] = LXColor.gray(brightness);
    }
  }
}
