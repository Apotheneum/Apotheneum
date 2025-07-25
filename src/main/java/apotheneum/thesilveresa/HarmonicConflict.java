package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Harmonic Conflict")
public class HarmonicConflict extends ApotheneumPattern {

  // Grid controls
  private final CompoundParameter spacing    = new CompoundParameter("Spacing",   4.0, 1.0, 20.0)
    .setDescription("How tight the grid is");
  private final CompoundParameter shiftAmp   = new CompoundParameter("ShiftAmp",  0.1, 0.0, 0.5)
    .setDescription("How far the two grids slide");
  private final CompoundParameter shiftSpeed = new CompoundParameter("ShiftSpeed",0.5, 0.0, 5.0)
    .setDescription("How fast the grids slide");
  private final CompoundParameter lineWidth  = new CompoundParameter("Width",     0.05,0.01,0.2)
    .setDescription("Thickness of each grid line");

  // Color & pulsation
  private final CompoundParameter hue1       = new CompoundParameter("Hue1",      0.0, 0.0, 360.0)
    .setDescription("Color of vertical lines");
  private final CompoundParameter hue2       = new CompoundParameter("Hue2",    180.0, 0.0, 360.0)
    .setDescription("Color of horizontal lines");
  private final CompoundParameter baseBri    = new CompoundParameter("Bri",      50.0, 0.0, 100.0)
    .setDescription("Base brightness of lines");
  private final CompoundParameter pulseDepth = new CompoundParameter("Pulse",     0.5, 0.0, 1.0)
    .setDescription("How much intersections brighten");
  private final CompoundParameter pulseSpeed = new CompoundParameter("PulseRate", 1.0, 0.1, 5.0)
    .setDescription("Speed of intersection pulse");
  private final CompoundParameter sat        = new CompoundParameter("Sat",     100.0, 0.0, 100.0)
    .setDescription("Saturation");

  public HarmonicConflict(LX lx) {
    super(lx);
    addParameter("Spacing", this.spacing);
    addParameter("ShiftAmp", this.shiftAmp);
    addParameter("ShiftSpeed", this.shiftSpeed);
    addParameter("Width", this.lineWidth);
    addParameter("Hue1", this.hue1);
    addParameter("Hue2", this.hue2);
    addParameter("Bri", this.baseBri);
    addParameter("Pulse", this.pulseDepth);
    addParameter("PulseRate", this.pulseSpeed);
    addParameter("Sat", this.sat);
  }

  @Override
  public void render(double deltaMs) {
    float t = (lx.engine.nowMillis / 1000f);

    // shifting offset for the two grids
    float shift = shiftAmp.getValuef()
      * (float)Math.sin(t * shiftSpeed.getValuef() * 2f * (float)Math.PI);

    // pulsation multiplier [1 - pd .. 1 + pd]
    float pulse = 1f + pulseDepth.getValuef()
      * (float)Math.sin(t * pulseSpeed.getValuef() * 2f * (float)Math.PI);

    float sz = spacing.getValuef();
    float w  = lineWidth.getValuef();
    float b  = baseBri.getValuef();
    float s  = sat.getValuef();

    for (LXPoint p : model.points) {
      // map into two moving grid spaces
      float u = p.x * sz + shift;
      float v = p.z * sz - shift;

      // distance-to-line for each grid
      float fx = Math.abs(fract(u) - 0.5f);
      float fy = Math.abs(fract(v) - 0.5f);

      boolean vert    = fx < w;
      boolean horiz   = fy < w;

      float bri;
      float hue;
      if (vert && horiz) {
        // intersection: pulse up
        hue = (hue1.getValuef() + hue2.getValuef()) * 0.5f;
        bri = b * pulse;
      } else if (vert) {
        hue = hue1.getValuef();
        bri = b;
      } else if (horiz) {
        hue = hue2.getValuef();
        bri = b;
      } else {
        bri = 0f;
        hue = 0f;
      }

      colors[p.index] = LXColor.hsb(hue, s, bri);
    }
  }

  private float fract(float x) {
    return x - (float)Math.floor(x);
  }
}
