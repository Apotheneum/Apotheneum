package apotheneum.doved.lightning;

import java.awt.Graphics2D;
import java.util.List;

public interface LightningGenerator {
  void generateLightning(List<LightningSegment> segments, Object params);
  void render(Graphics2D graphics, List<LightningSegment> segments, double fadeAmount, 
             double intensityValue, double thicknessValue, double bleedingValue);
}