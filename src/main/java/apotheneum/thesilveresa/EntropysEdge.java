package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Entropy's Edge")
public class EntropysEdge extends ApotheneumPattern {

  final CompoundParameter order =
    new CompoundParameter("Order", 1.0, 0.0, 1.0)
      .setDescription("Degree of structured grid order");

  final CompoundParameter disrupt =
    new CompoundParameter("Disrupt", 0.3, 0.0, 1.0)
      .setDescription("Distortion strength of spatial offsets");

  final CompoundParameter warp =
    new CompoundParameter("Warp", 0.5, 0.0, 2.0)
      .setDescription("Wave warping along position axis");

  final CompoundParameter contrast =
    new CompoundParameter("Contrast", 1.0, 0.3, 2.0)
      .setDescription("Contrast of edge definition");

  final CompoundParameter scale =
    new CompoundParameter("Scale", 1.0, 0.7, 1.3)
      .setDescription("Scale of spatial pattern");

  final CompoundParameter saturation =
    new CompoundParameter("Sat", 1.0, 0.0, 1.0)
      .setDescription("Color saturation from grayscale to full color");

  final CompoundParameter shiftX =
    new CompoundParameter("ShiftX", 0.0, -1.0, 1.0)
      .setDescription("Horizontal translation of pattern");

  final CompoundParameter shiftY =
    new CompoundParameter("ShiftY", 0.0, -1.0, 1.0)
      .setDescription("Vertical translation of pattern");

  final BooleanParameter mirror =
    new BooleanParameter("Mirror", false)
      .setDescription("Mirror pattern horizontally");

  final CompoundParameter hue1 =
    new CompoundParameter("Hue1", 0, 0, 360)
      .setDescription("Base hue 1");

  final CompoundParameter hue2 =
    new CompoundParameter("Hue2", 120, 0, 360)
      .setDescription("Base hue 2");

  final CompoundParameter hue3 =
    new CompoundParameter("Hue3", 240, 0, 360)
      .setDescription("Base hue 3");

  public EntropysEdge(LX lx) {
    super(lx);
    addParameter("order", this.order);
    addParameter("disrupt", this.disrupt);
    addParameter("warp", this.warp);
    addParameter("contrast", this.contrast);
    addParameter("scale", this.scale);
    addParameter("saturate", this.saturation);
    addParameter("shiftX", this.shiftX);
    addParameter("shiftY", this.shiftY);
    addParameter("mirror", this.mirror);
    addParameter("hue1", this.hue1);
    addParameter("hue2", this.hue2);
    addParameter("hue3", this.hue3);
  }

  @Override
  public void render(double deltaMs) {
    float time = lx.engine.nowMillis / 1000.f;
    float o = order.getValuef();
    float d = disrupt.getValuef();
    float w = warp.getValuef();
    float c = contrast.getValuef();
    float scaleVal = scale.getValuef();
    float s = saturation.getValuef();
    float shiftXVal = shiftX.getValuef();
    float shiftYVal = shiftY.getValuef();

    float h1 = hue1.getValuef();
    float h2 = hue2.getValuef();
    float h3 = hue3.getValuef();
    boolean doMirror = mirror.getValueb();

    for (LXPoint p : model.points) {
      float x = (p.xn - 0.5f + shiftXVal) * scaleVal;
      float y = (p.yn - 0.5f + shiftYVal) * scaleVal;

      if (doMirror) {
        x = Math.abs(x);
      }

      float r = (float)Math.sqrt(x * x + y * y);

      float gridPattern = (float)Math.sin(10 * (x + y));
      float warpDistortion = (float)Math.sin(w * (x + y));
      float localNoise = (float)Math.sin(20 * (x * y));

      float base = gridPattern * o + warpDistortion * d + localNoise * (1 - o);
      base = (float)Math.tanh(base * c);
      float normalized = 0.5f + 0.5f * base;

      float hueBlend = (float)Math.sin(4 * r + base * 4);
      hueBlend = LXUtils.clampf(hueBlend, -1f, 1f);
      float finalHue;
      if (hueBlend < 0) {
        finalHue = (float)LXUtils.lerp(h1, h2, hueBlend + 1f);
      } else {
        finalHue = (float)LXUtils.lerp(h2, h3, hueBlend);
      }

      int gray = LXColor.gray((int)(255 * normalized));
      int color = LXColor.hsba(finalHue, 100, 100 * normalized, 100);
      colors[p.index] = LXColor.lerp(gray, color, s);
    }
  }
}

