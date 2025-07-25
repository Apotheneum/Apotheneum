package apotheneum.doved.lightning;

import heronarts.lx.utils.LXUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.List;

public class MidpointDisplacementAlgorithm implements LightningGenerator {
  
  public static class Parameters {
    public final double displacement;
    public final int recursionDepth;
    public final double startX;
    public final double startSpread;
    public final double endSpread;
    public final double branchProbability;
    public final double branchDistance;
    public final double branchAngle;
    public final int rasterWidth;
    public final int rasterHeight;
    
    public Parameters(double displacement, int recursionDepth, double startX, double startSpread, double endSpread,
                     double branchProbability, double branchDistance, double branchAngle, 
                     int rasterWidth, int rasterHeight) {
      this.displacement = displacement;
      this.recursionDepth = recursionDepth;
      this.startX = startX;
      this.startSpread = startSpread;
      this.endSpread = endSpread;
      this.branchProbability = branchProbability;
      this.branchDistance = branchDistance;
      this.branchAngle = branchAngle;
      this.rasterWidth = rasterWidth;
      this.rasterHeight = rasterHeight;
    }
  }
  
  
  @Override
  public void generateLightning(List<LightningSegment> segments, Object params) {
    generateLightning(segments, (Parameters) params);
  }
  
  public static void generateLightning(List<LightningSegment> segments, Parameters params) {
    // Generate lightning from top to bottom using spread controls
    double baseStartX = params.startX * params.rasterWidth; // Convert 0-1 to pixel coordinates
    double startRange = params.startSpread * params.rasterWidth;
    double endRange = params.endSpread * params.rasterWidth;
    
    double startX = baseStartX + (Math.random() - 0.5) * startRange;
    double startY = 0;
    double centerX = params.rasterWidth / 2.0;
    double endX = centerX + (Math.random() - 0.5) * endRange;
    double endY = params.rasterHeight - 1;
    
    // Constrain to bounds
    startX = LXUtils.constrain(startX, 0, params.rasterWidth - 1);
    endX = LXUtils.constrain(endX, 0, params.rasterWidth - 1);
    
    generateLightningSegments(segments, params, startX, startY, endX, endY, false, 1.0, 0);
  }
  
  private static void generateLightningSegments(List<LightningSegment> segments, Parameters params,
                                              double x1, double y1, double x2, double y2, 
                                              boolean isBranch, double intensity, int depth) {
    if (depth >= params.recursionDepth) {
      segments.add(new LightningSegment(x1, y1, x2, y2, isBranch, intensity, depth));
      return;
    }

    // Calculate midpoint
    double midX = (x1 + x2) / 2;
    double midY = (y1 + y2) / 2;

    // Calculate perpendicular vector
    double dx = x2 - x1;
    double dy = y2 - y1;
    double perpX = -dy;
    double perpY = dx;
    
    // Normalize perpendicular vector
    double perpLen = Math.sqrt(perpX * perpX + perpY * perpY);
    if (perpLen > 0) {
      perpX /= perpLen;
      perpY /= perpLen;
    }

    // Apply random displacement
    double displaceAmount = params.displacement * 10 * (Math.random() - 0.5);
    midX += perpX * displaceAmount;
    midY += perpY * displaceAmount;

    // Constrain to bounds
    midX = LXUtils.constrain(midX, 0, params.rasterWidth - 1);
    midY = LXUtils.constrain(midY, 0, params.rasterHeight - 1);

    // Recursively generate segments
    generateLightningSegments(segments, params, x1, y1, midX, midY, isBranch, intensity, depth + 1);
    generateLightningSegments(segments, params, midX, midY, x2, y2, isBranch, intensity, depth + 1);

    // Create branches using controlled parameters
    if (!isBranch && Math.random() < params.branchProbability) {
      double maxBranchDist = params.branchDistance * 20;
      double angleVariation = params.branchAngle;
      
      // Calculate main bolt direction
      double mainAngle = Math.atan2(y2 - y1, x2 - x1);
      
      // Create branch angle based on main direction and variation control
      double branchAngleRad = mainAngle + (Math.random() - 0.5) * angleVariation * Math.PI;
      
      // Calculate branch end point
      double branchEndX = midX + Math.cos(branchAngleRad) * maxBranchDist;
      double branchEndY = midY + Math.sin(branchAngleRad) * maxBranchDist;
      
      // Constrain to bounds
      branchEndX = LXUtils.constrain(branchEndX, 0, params.rasterWidth - 1);
      branchEndY = LXUtils.constrain(branchEndY, 0, params.rasterHeight - 1);
      
      generateLightningSegments(segments, params, midX, midY, branchEndX, branchEndY, true, intensity * 0.7, depth + 1);
    }
  }
  
  @Override
  public void render(Graphics2D graphics, List<LightningSegment> segments, double fadeAmount, double intensityValue, double thicknessValue, double bleedingValue) {
    renderStatic(graphics, segments, fadeAmount, intensityValue, thicknessValue, bleedingValue);
  }
  
  public static void renderStatic(Graphics2D graphics, List<LightningSegment> segments, double fadeAmount, double intensityValue, double thicknessValue, double bleedingValue) {
    double alpha = fadeAmount * intensityValue;
    
    for (LightningSegment segment : segments) {
      double segmentAlpha = alpha * segment.intensity;
      if (segment.isBranch) {
        segmentAlpha *= 0.7;
      }
      
      // Create lightning color with fade
      Color lightningColor = new Color(
        (float) LXUtils.constrain(0.8 + 0.2 * segmentAlpha, 0, 1),  // R
        (float) LXUtils.constrain(0.9 + 0.1 * segmentAlpha, 0, 1),  // G
        (float) LXUtils.constrain(1.0, 0, 1),                       // B
        (float) LXUtils.constrain(segmentAlpha, 0, 1)               // A
      );
      
      graphics.setColor(lightningColor);
      
      // Set stroke thickness - branches are thinner
      float strokeWidth = (float) (thicknessValue * (segment.isBranch ? 0.5 : 1.0));
      graphics.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      
      // Draw the lightning segment
      Path2D path = new Path2D.Double();
      path.moveTo(segment.x1, segment.y1);
      path.lineTo(segment.x2, segment.y2);
      graphics.draw(path);
      
      // Add glow effect for bright segments
      if (segmentAlpha > 0.3 && bleedingValue > 0) {
        Color glowColor = new Color(
          (float) LXUtils.constrain(0.6 + 0.4 * segmentAlpha, 0, 1),
          (float) LXUtils.constrain(0.8 + 0.2 * segmentAlpha, 0, 1),
          (float) LXUtils.constrain(1.0, 0, 1),
          (float) LXUtils.constrain(segmentAlpha * 0.3 * bleedingValue, 0, 1)
        );
        graphics.setColor(glowColor);
        graphics.setStroke(new BasicStroke((float)(strokeWidth * (1 + bleedingValue)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(path);
      }
    }
  }
}