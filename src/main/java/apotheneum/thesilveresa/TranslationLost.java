
package apotheneum.thesilveresa;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.BooleanParameter;
import apotheneum.ApotheneumPattern;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Translation Lost")
public class TranslationLost extends ApotheneumPattern {

  private final CompoundParameter jitterAmount = new CompoundParameter("Jitter", 0.1, 0, 1);
  private final CompoundParameter scaleVariance = new CompoundParameter("Scale", 1, 0.1, 2);
  private final CompoundParameter flickerChance = new CompoundParameter("Flicker", 0.05, 0, 1);
  private final CompoundParameter tilingKnob = new CompoundParameter("Tiling", 3, 1, 7);
  private final CompoundParameter rotateX = new CompoundParameter("SpinX", 0, -180, 180);
  private final CompoundParameter rotateY = new CompoundParameter("SpinY", 0, -180, 180);
  private final CompoundParameter rotateZ = new CompoundParameter("SpinZ", 0, -180, 180);
  private final BooleanParameter mirror = new BooleanParameter("Mirror", false);

  public TranslationLost(LX lx) {
    super(lx);
    addParameter("jitterAmount", this.jitterAmount);
    addParameter("scaleVariance", this.scaleVariance);
    addParameter("flickerChance", this.flickerChance);
    addParameter("tilingKnob", this.tilingKnob);
    addParameter("rotateX", this.rotateX);
    addParameter("rotateY", this.rotateY);
    addParameter("rotateZ", this.rotateZ);
    addParameter("mirror", this.mirror);
  }

  private double elapsedMs = 0;

  @Override
  protected void render(double deltaMs) {
    elapsedMs += deltaMs;

    float jitter = jitterAmount.getValuef();
    float scale = scaleVariance.getValuef();
    float flicker = flickerChance.getValuef();
    float tileCount = tilingKnob.getValuef();
    float tileSize = 1.0f / tileCount;
    boolean mirrorOn = mirror.getValueb();

    float xAngle = (float) Math.toRadians(rotateX.getValuef());
    float yAngle = (float) Math.toRadians(rotateY.getValuef());
    float zAngle = (float) Math.toRadians(rotateZ.getValuef());

    float cosX = (float) Math.cos(xAngle), sinX = (float) Math.sin(xAngle);
    float cosY = (float) Math.cos(yAngle), sinY = (float) Math.sin(yAngle);
    float cosZ = (float) Math.cos(zAngle), sinZ = (float) Math.sin(zAngle);

    for (LXPoint p : model.points) {
      float x = p.xn - 0.5f;
      float y = p.yn - 0.5f;
      float z = 0;

      if (mirrorOn && p.xn > 0.5f) {
        x = -x;
      }

      float xz = x * cosZ - y * sinZ;
      float yz = x * sinZ + y * cosZ;
      x = xz;
      y = yz;

      float yx = y * cosX - z * sinX;
      float zx = y * sinX + z * cosX;
      y = yx;
      z = zx;

      float xy = x * cosY + z * sinY;
      float zy = -x * sinY + z * cosY;
      x = xy;
      z = zy;

      x *= scale;
      y *= scale;

      x += 0.5f;
      y += 0.5f;

      int tx = (int)(x / tileSize);
      int ty = (int)(y / tileSize);

      float jitterX = (float)((Math.random() - 0.5f) * jitter * tileSize);
      float jitterY = (float)((Math.random() - 0.5f) * jitter * tileSize);

      float px = tx * tileSize + jitterX;
      float py = ty * tileSize + jitterY;

      float dx = x - px;
      float dy = y - py;
      float dist = (float)Math.sqrt(dx * dx + dy * dy);

      float brightness = Math.max(0, 1 - dist / (tileSize * 0.5f));

      if (flicker > 0 && Math.random() < flicker) {
        brightness = 0;
      }

      int white = rgb(brightness, brightness, brightness);
      colors[p.index] = white;
    }
  }

  private int rgb(float r, float g, float b) {
    return ((0xFF << 24) |
            ((int)(255 * r) << 16) |
            ((int)(255 * g) << 8) |
            (int)(255 * b));
  }
}
