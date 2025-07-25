
package apotheneum.thesilveresa;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import apotheneum.ApotheneumPattern;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Alhambra Memory")
public class AlhambraMemory extends ApotheneumPattern {

  private BufferedImage tileImage;
  private boolean imageLoaded = false;

  final CompoundParameter zoom = new CompoundParameter("Zoom", 1.0, 0.5, 3.0)
    .setDescription("Zoom level of the image sampling");

  final CompoundParameter shimmer = new CompoundParameter("Shimmer", 0.0, 0.0, 0.1)
    .setDescription("Subtle shimmer effect over time");

  final CompoundParameter saturation = new CompoundParameter("Sat", 1.0, 0.0, 1.0)
    .setDescription("Color saturation level (0 = grayscale)");

  public AlhambraMemory(LX lx) {
    super(lx);
    addParameter("zoom", this.zoom);
    addParameter("shimmer", this.shimmer);
    addParameter("sat", this.saturation);

    try {
      tileImage = ImageIO.read(getClass().getResourceAsStream("/images/alhambra_tile.jpg"));
      imageLoaded = true;
    } catch (IOException | NullPointerException e) {
      System.err.println("Failed to load Alhambra tile image.");
      e.printStackTrace();
    }
  }

  private double elapsedTime = 0;

  @Override
  public void render(double deltaMs) {
    if (!imageLoaded) return;

    elapsedTime += deltaMs;
    float shimmerPhase = (float)(Math.sin(elapsedTime * shimmer.getValue()) * 0.05);

    int width = tileImage.getWidth();
    int height = tileImage.getHeight();

    for (LXPoint p : model.points) {
      float x = (p.xn + shimmerPhase) * zoom.getValuef();
      float y = (p.yn + shimmerPhase) * zoom.getValuef();

      int xi = Math.min(Math.max((int)(x * width), 0), width - 1);
      int yi = Math.min(Math.max((int)(y * height), 0), height - 1);

      int rgb = tileImage.getRGB(xi, yi);

      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;

      // Apply desaturation
      float sat = saturation.getValuef();
      int gray = (r + g + b) / 3;
      r = (int)(gray + sat * (r - gray));
      g = (int)(gray + sat * (g - gray));
      b = (int)(gray + sat * (b - gray));

      colors[p.index] = LXColor.rgb(r, g, b);
    }
  }
}
