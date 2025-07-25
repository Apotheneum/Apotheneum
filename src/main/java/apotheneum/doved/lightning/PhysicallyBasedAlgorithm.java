package apotheneum.doved.lightning;

import heronarts.lx.utils.LXUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Physically-based lightning algorithm inspired by actual lightning formation.
 * Simulates the stepped leader process and return stroke based on real lightning physics.
 */
public class PhysicallyBasedAlgorithm implements LightningGenerator {

  public static class Parameters {
    public final double electricPotential;
    public final double stepLength;
    public final int maxSteps;
    public final double branchingProbability;
    public final double stepAngleVariation;
    public final double chargeDecay;
    public final double connectionDistance;
    public final double startX;
    public final int rasterWidth;
    public final int rasterHeight;

    public Parameters(double electricPotential, double stepLength, int maxSteps,
        double branchingProbability, double stepAngleVariation, double chargeDecay,
        double connectionDistance, double startX, int rasterWidth, int rasterHeight) {
      this.electricPotential = electricPotential;
      this.stepLength = stepLength;
      this.maxSteps = maxSteps;
      this.branchingProbability = branchingProbability;
      this.stepAngleVariation = stepAngleVariation;
      this.chargeDecay = chargeDecay;
      this.connectionDistance = connectionDistance;
      this.startX = startX;
      this.rasterWidth = rasterWidth;
      this.rasterHeight = rasterHeight;
    }
  }

  /**
   * Represents a stepped leader channel - the downward-propagating negative charge channel
   */
  private static class SteppedLeader {
    public final double x, y;
    public final SteppedLeader parent;
    public final double charge; // Electric potential at this step
    public final double direction; // Angle in radians
    public final int stepNumber;
    public final boolean isActive; // Whether this branch is still growing
    public final List<SteppedLeader> branches;
    public boolean reachedGround;

    public SteppedLeader(double x, double y, SteppedLeader parent, double charge, 
                        double direction, int stepNumber, boolean isActive) {
      this.x = x;
      this.y = y;
      this.parent = parent;
      this.charge = charge;
      this.direction = direction;
      this.stepNumber = stepNumber;
      this.isActive = isActive;
      this.branches = new ArrayList<>();
      this.reachedGround = false;
    }

    public double distanceToGround(double groundY) {
      return Math.abs(y - groundY);
    }

    public double calculateElectricField(double targetX, double targetY) {
      // Simplified electric field calculation based on inverse square law
      double dx = targetX - x;
      double dy = targetY - y;
      double distance = Math.sqrt(dx * dx + dy * dy);
      return charge / (distance * distance + 1e-6); // Avoid division by zero
    }
  }

  /**
   * Represents the return stroke - the bright upward surge after connection
   */
  private static class ReturnStroke {
    public final List<SteppedLeader> path;
    public final double intensity;
    public final double propagationSpeed;

    public ReturnStroke(List<SteppedLeader> path, double intensity, double propagationSpeed) {
      this.path = new ArrayList<>(path);
      this.intensity = intensity;
      this.propagationSpeed = propagationSpeed;
    }
  }

  @Override
  public void generateLightning(List<LightningSegment> segments, Object params) {
    generateLightning(segments, (Parameters) params);
  }
  
  public static void generateLightning(List<LightningSegment> segments, Parameters params) {
    // Phase 1: Stepped Leader Formation
    List<SteppedLeader> allLeaders = new ArrayList<>();
    List<SteppedLeader> activeLeaders = new ArrayList<>();
    
    // Initialize with cloud base (high electric potential)
    double startX = params.startX * params.rasterWidth;
    double startY = 0;
    SteppedLeader initialLeader = new SteppedLeader(
        startX, startY, null, params.electricPotential, Math.PI / 2, 0, true);
    
    allLeaders.add(initialLeader);
    activeLeaders.add(initialLeader);

    SteppedLeader connectionPoint = null;
    double groundLevel = params.rasterHeight - 1;

    // Stepped leader propagation
    for (int step = 0; step < params.maxSteps && connectionPoint == null; step++) {
      List<SteppedLeader> newLeaders = new ArrayList<>();
      
      for (SteppedLeader leader : activeLeaders) {
        if (!leader.isActive) continue;

        // Check if this leader reaches the ground
        if (leader.distanceToGround(groundLevel) <= params.connectionDistance) {
          connectionPoint = leader;
          leader.reachedGround = true;
          break;
        }

        // Extend this leader with a new step
        SteppedLeader newStep = createNextStep(leader, params, groundLevel);
        if (newStep != null) {
          allLeaders.add(newStep);
          newLeaders.add(newStep);
          leader.branches.add(newStep);
        }

        // Create branches based on electric field conditions
        if (Math.random() < params.branchingProbability) {
          List<SteppedLeader> branches = createBranches(leader, params, groundLevel);
          for (SteppedLeader branch : branches) {
            allLeaders.add(branch);
            newLeaders.add(branch);
            leader.branches.add(branch);
          }
        }
      }
      
      activeLeaders = newLeaders;
    }

    // Phase 2: Return Stroke Generation (if connection made)
    List<ReturnStroke> returnStrokes = new ArrayList<>();
    if (connectionPoint != null) {
      List<SteppedLeader> mainPath = tracePath(connectionPoint);
      ReturnStroke mainStroke = new ReturnStroke(mainPath, 1.0, 1.0);
      returnStrokes.add(mainStroke);

      // Create secondary return strokes for major branches
      for (SteppedLeader leader : allLeaders) {
        if (leader.branches.size() > 0 && leader.charge > params.electricPotential * 0.5) {
          List<SteppedLeader> branchPath = tracePath(leader);
          if (branchPath.size() > 3) { // Only significant branches
            ReturnStroke branchStroke = new ReturnStroke(branchPath, 
                leader.charge / params.electricPotential, 0.8);
            returnStrokes.add(branchStroke);
          }
        }
      }
    }

    // Phase 3: Convert to Lightning Segments
    convertToSegments(allLeaders, returnStrokes, segments, params);
  }

  private static SteppedLeader createNextStep(SteppedLeader current, Parameters params, double groundLevel) {
    // Calculate electric field influence on step direction
    double fieldDirection = calculateFieldDirection(current, groundLevel, params);
    
    // Add some randomness based on step angle variation
    double angleVariation = (Math.random() - 0.5) * params.stepAngleVariation;
    double newDirection = fieldDirection + angleVariation;
    
    // Calculate new position
    double newX = current.x + Math.cos(newDirection) * params.stepLength;
    double newY = current.y + Math.sin(newDirection) * params.stepLength;
    
    // Check bounds
    if (newX < 0 || newX >= params.rasterWidth || newY < 0 || newY >= params.rasterHeight) {
      return null;
    }
    
    // Charge decreases with distance from cloud
    double newCharge = current.charge * (1.0 - params.chargeDecay);
    
    return new SteppedLeader(newX, newY, current, newCharge, newDirection, 
                           current.stepNumber + 1, newCharge > params.electricPotential * 0.1);
  }

  private static double calculateFieldDirection(SteppedLeader current, double groundLevel, Parameters params) {
    // Bias toward ground (downward direction)
    double groundBias = Math.PI / 2; // Straight down
    
    // Add attraction to ground based on electric field
    double distanceToGround = groundLevel - current.y;
    double groundAttraction = 1.0 / (1.0 + distanceToGround * 0.01);
    
    // Add some lateral spreading based on current direction
    double lateralSpread = current.direction + (Math.random() - 0.5) * 0.3;
    
    // Combine influences
    return LXUtils.lerp(lateralSpread, groundBias, groundAttraction);
  }

  private static List<SteppedLeader> createBranches(SteppedLeader parent, Parameters params, double groundLevel) {
    List<SteppedLeader> branches = new ArrayList<>();
    
    // Create 1-3 branches from this point
    int numBranches = 1 + (int)(Math.random() * 3);
    
    for (int i = 0; i < numBranches; i++) {
      // Branch at angles roughly perpendicular to main direction
      double branchAngle = parent.direction + (Math.random() - 0.5) * Math.PI;
      
      double branchX = parent.x + Math.cos(branchAngle) * params.stepLength * 0.7;
      double branchY = parent.y + Math.sin(branchAngle) * params.stepLength * 0.7;
      
      // Check bounds
      if (branchX >= 0 && branchX < params.rasterWidth && 
          branchY >= 0 && branchY < params.rasterHeight) {
        
        // Branches have reduced charge
        double branchCharge = parent.charge * (0.6 + Math.random() * 0.3);
        
        SteppedLeader branch = new SteppedLeader(branchX, branchY, parent, branchCharge,
                                               branchAngle, parent.stepNumber + 1, true);
        branches.add(branch);
      }
    }
    
    return branches;
  }

  private static List<SteppedLeader> tracePath(SteppedLeader endpoint) {
    List<SteppedLeader> path = new ArrayList<>();
    SteppedLeader current = endpoint;
    
    while (current != null) {
      path.add(0, current); // Add to beginning for correct order
      current = current.parent;
    }
    
    return path;
  }

  private static void convertToSegments(List<SteppedLeader> allLeaders, 
                                      List<ReturnStroke> returnStrokes,
                                      List<LightningSegment> segments, 
                                      Parameters params) {
    // First, add stepped leader segments (dimmer)
    for (SteppedLeader leader : allLeaders) {
      if (leader.parent != null) {
        double intensity = (leader.charge / params.electricPotential) * 0.3; // Stepped leaders are dim
        segments.add(new LightningSegment(
            leader.parent.x, leader.parent.y,
            leader.x, leader.y,
            leader.parent.branches.size() > 1, // Is branch if parent has multiple children
            intensity,
            leader.stepNumber));
      }
    }
    
    // Then add return stroke segments (bright)
    for (ReturnStroke stroke : returnStrokes) {
      for (int i = 1; i < stroke.path.size(); i++) {
        SteppedLeader prev = stroke.path.get(i - 1);
        SteppedLeader curr = stroke.path.get(i);
        
        double intensity = stroke.intensity; // Return strokes are bright
        segments.add(new LightningSegment(
            prev.x, prev.y,
            curr.x, curr.y,
            false, // Return strokes follow the main channel
            intensity,
            i));
      }
    }
  }

  @Override
  public void render(Graphics2D graphics, List<LightningSegment> segments, double fadeAmount,
                           double intensityValue, double thicknessValue, double bleedingValue) {
    renderStatic(graphics, segments, fadeAmount, intensityValue, thicknessValue, bleedingValue);
  }
  
  public static void renderStatic(Graphics2D graphics, List<LightningSegment> segments, double fadeAmount,
                           double intensityValue, double thicknessValue, double bleedingValue) {
    double alpha = fadeAmount * intensityValue;

    for (LightningSegment segment : segments) {
      double segmentAlpha = alpha * segment.intensity;

      // Create realistic lightning color (blue-white with slight purple tint)
      Color lightningColor = new Color(
          (float) LXUtils.constrain(0.9 + 0.1 * segmentAlpha, 0, 1), // R
          (float) LXUtils.constrain(0.95 + 0.05 * segmentAlpha, 0, 1), // G  
          (float) LXUtils.constrain(1.0, 0, 1), // B
          (float) LXUtils.constrain(segmentAlpha, 0, 1) // A
      );

      // Thickness varies based on intensity (return strokes are thicker)
      float strokeWidth = (float) (thicknessValue * 
          (segment.isBranch ? 0.5 : 1.0) * 
          (0.5 + segment.intensity * 0.5));
      
      graphics.setColor(lightningColor);
      graphics.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

      // Draw the segment
      Path2D path = new Path2D.Double();
      path.moveTo(segment.x1, segment.y1);
      path.lineTo(segment.x2, segment.y2);
      graphics.draw(path);

      // Add corona glow for high-intensity segments (return strokes)
      if (segment.intensity > 0.7 && bleedingValue > 0) {
        Color glowColor = new Color(
            (float) LXUtils.constrain(0.7 + 0.3 * segmentAlpha, 0, 1),
            (float) LXUtils.constrain(0.8 + 0.2 * segmentAlpha, 0, 1),
            (float) LXUtils.constrain(1.0, 0, 1),
            (float) LXUtils.constrain(segmentAlpha * 0.3 * bleedingValue, 0, 1));
        
        graphics.setColor(glowColor);
        float glowWidth = (float) (strokeWidth * (1.0 + bleedingValue));
        graphics.setStroke(new BasicStroke(glowWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(path);
      }
    }
  }
}