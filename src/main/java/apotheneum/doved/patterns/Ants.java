package apotheneum.doved.patterns;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;
import java.util.ArrayList;
import java.util.List;

@LXCategory("Apotheneum/doved")
@LXComponentName("Ants")
public class Ants extends ApotheneumPattern implements UIDeviceControls<Ants> {

  // Dynamic coordinate system based on shape selection
  private int getRingHeight() {
    return shape.getValuei() == 0 ? Apotheneum.GRID_HEIGHT : Apotheneum.CYLINDER_HEIGHT;
  }

  private int getRingLength() {
    return shape.getValuei() == 0 ? Apotheneum.Cube.Ring.LENGTH : Apotheneum.Cylinder.Ring.LENGTH;
  }

  private boolean needsInitialAnt = true; // Flag to spawn first ant immediately
  private SeekerAnt seekerAnt; // Single seeker ant
  private boolean pathFound = false; // Has ant found the path?

  public final CompoundParameter speed = new CompoundParameter("Speed", 0.5, 0.1, 2)
      .setDescription("Base speed of ant movement");

  public final CompoundParameter speedVariation = new CompoundParameter("Speed Var", 0.3, 0, 1)
      .setDescription("Random variation in ant speeds");

  public final CompoundParameter spacing = new CompoundParameter("Spacing", 1, 0.5, 3)
      .setDescription("Spacing between ants in the trail");

  public final CompoundParameter antSize = new CompoundParameter("Size", 0.5, 0.5, 4)
      .setDescription("Size of each ant (circular radius)");

  public final CompoundParameter maxChange = new CompoundParameter("Max Change", 0.3, 0.1, 1)
      .setDescription("Maximum direction change per step");

  public final CompoundParameter startX = new CompoundParameter("Start-X", 0.1, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("X position of start point (0=left, 1=right)");

  public final CompoundParameter startY = new CompoundParameter("Start-Y", 0.1, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Y position of start point (0=top, 1=bottom)");

  public final CompoundParameter targetX = new CompoundParameter("Target-X", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("X position of target point (0=left, 1=right)");

  public final CompoundParameter targetY = new CompoundParameter("Target-Y", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Y position of target point (0=top, 1=bottom)");

  public final CompoundParameter attraction = new CompoundParameter("Attraction", 0.5, 0, 1)
      .setDescription("How strongly ants are attracted to target");

  public final TriggerParameter clearAnts = new TriggerParameter("Clear", this::clearAllAnts)
      .setDescription("Clear all ants and restart pattern");

  public final BooleanParameter debugStart = new BooleanParameter("Debug", false)
      .setDescription("Show start point in green");

  public final BooleanParameter debugTarget = new BooleanParameter("Debug", false)
      .setDescription("Show target point in red");

  public final CompoundParameter explorerRate = new CompoundParameter("Explorers", 0.1, 0, 0.5)
      .setDescription("Rate of explorer ants seeking new paths");

  public final CompoundParameter quantity = new CompoundParameter("Quantity", 1.0, 0.1, 3.0)
      .setDescription("Number of ants spawned when an ant returns home");

  public final CompoundParameter maxAnts = new CompoundParameter("Max Ants", 200, 10, 400)
      .setDescription("Maximum number of ants allowed on screen");

  public final BooleanParameter laneSeparation = new BooleanParameter("Lane Sep", true)
      .setDescription("Separate lanes for opposing directions");

  public final CompoundParameter laneDistance = new CompoundParameter("Lane Dist", 3.0, 0.5, 8.0)
      .setDescription("Distance between opposing lanes");

  public final DiscreteParameter forwardRender = new DiscreteParameter("Forward",
      new String[] { "Exterior", "Interior", "Both" }, 0)
      .setDescription("Where to render ants going to target");

  public final DiscreteParameter returnRender = new DiscreteParameter("Return",
      new String[] { "Exterior", "Interior", "Both" }, 1)
      .setDescription("Where to render ants returning to start");

  public final DiscreteParameter shape = new DiscreteParameter("Shape",
      new String[] { "Cube", "Cylinder" }, 0)
      .setDescription("Which shape to render on");

  private static class AntSegment {
    float x, y;
    boolean goingToTarget; // Direction when this segment was created

    AntSegment(float x, float y, boolean goingToTarget) {
      this.x = x;
      this.y = y;
      this.goingToTarget = goingToTarget;
    }
  }

  private static class MovingAnt {
    float position; // 0.0 to 2.0 (0-1 = going to target, 1-2 = returning to start)
    int color; // Fixed color for entire journey
    int pathIndex; // Which path this ant is following (0 = main path)
    float speedMultiplier; // Individual speed variation for this ant

    // Wandering behavior
    boolean isWandering = false;
    float wanderX = 0, wanderY = 0; // Current wandering position offset
    float wanderDirection = 0; // Direction of wandering movement
    double wanderStartTime = 0; // When wandering began
    double wanderDuration = 0; // How long to wander

    MovingAnt(float position, int color, int pathIndex, float speedMultiplier) {
      this.position = position;
      this.color = color;
      this.pathIndex = pathIndex;
      this.speedMultiplier = speedMultiplier;
      this.wanderDirection = (float) (Math.random() * 2 * Math.PI);
    }

    boolean isGoingToTarget() {
      return position <= 1.0f;
    }

    float getPathPosition() {
      if (isGoingToTarget()) {
        return position; // 0.0 to 1.0 going forward
      } else {
        return 2.0f - position; // 2.0->1.0 becomes 0.0->1.0 going backward
      }
    }
  }

  private static class SeekerAnt {
    float headX, headY; // Current position
    float direction; // Direction this ant is moving
    boolean reachedTarget = false;
    boolean returningHome = false;
    float startX, startY; // Remember where home is
    List<AntSegment> path; // The path this ant is creating

    // Wandering state tracking
    boolean isWandering = true; // Start in wandering mode
    double wanderStartTime = 0; // When wandering began
    double wanderDuration = 1000; // How long to wander (in milliseconds)

    // Long-term curve bias for overarching path randomness
    float longTermCurveBias = 0; // Persistent curve direction
    double lastCurveUpdateTime = 0; // When curve bias was last updated

    SeekerAnt(float startX, float startY) {
      this.headX = startX;
      this.headY = startY;
      this.startX = startX;
      this.startY = startY;
      this.path = new ArrayList<>();
      this.direction = (float) (Math.random() * 2 * Math.PI); // Random initial direction
      this.wanderDuration = 800 + Math.random() * 800; // Random 0.8-1.6 seconds
    }

    boolean isGoingToTarget() {
      return !returningHome;
    }
  }

  private final List<List<AntSegment>> discoveredPaths = new ArrayList<>(); // Multiple discovered paths
  private final List<MovingAnt> movingAnts = new ArrayList<>(); // Ants moving along paths

  // Delayed spawn system
  private static class DelayedSpawn {
    public final double spawnTime; // When to spawn (in milliseconds)
    public final int pathIndex;
    public final int quantity;

    public DelayedSpawn(double spawnTime, int pathIndex, int quantity) {
      this.spawnTime = spawnTime;
      this.pathIndex = pathIndex;
      this.quantity = quantity;
    }
  }

  private final List<DelayedSpawn> delayedSpawns = new ArrayList<>();
  private double currentTime = 0; // Track current time for delay system

  public Ants(LX lx) {
    super(lx);
    addParameter("speed", this.speed);
    addParameter("speedVariation", this.speedVariation);
    addParameter("spacing", this.spacing);
    addParameter("antSize", this.antSize);
    addParameter("maxChange", this.maxChange);
    addParameter("wanderChance", this.wanderChance);
    addParameter("wanderDistance", this.wanderDistance);
    addParameter("startX", this.startX);
    addParameter("startY", this.startY);
    addParameter("targetX", this.targetX);
    addParameter("targetY", this.targetY);
    addParameter("attraction", this.attraction);
    addParameter("clearAnts", this.clearAnts);
    addParameter("debugTarget", this.debugTarget);
    addParameter("debugStart", this.debugStart);
    addParameter("explorerRate", this.explorerRate);
    addParameter("quantity", this.quantity);
    addParameter("maxAnts", this.maxAnts);
    addParameter("laneSeparation", this.laneSeparation);
    addParameter("laneDistance", this.laneDistance);
    addParameter("forwardRender", this.forwardRender);
    addParameter("returnRender", this.returnRender);
    addParameter("shape", this.shape);

    // Don't initialize start position yet - wait for scout phase to complete
    // initializeStartPosition();
  }

  private void initializeStartPosition() {
    // This method is now empty since seeker ant initialization is handled in
    // spawnSeekerAnt
  }

  @Override
  public void onActive() {
    super.onActive();
    // Reinitialize when pattern becomes active to ensure proper start position
    initializeStartPosition();
  }

  @Override
  public void onInactive() {
    super.onInactive();
    // Clear the pattern when it becomes inactive
    clearAllAnts();
  }

  @Override
  protected void render(double deltaMs) {
    // Only run if pattern is not enabled
    if (!this.enabled.isOn()) {
      setApotheneumColor(0); // Clear all
      return;
    }

    setApotheneumColor(0); // Clear all

    // Update current time for delay system
    currentTime += deltaMs;

    // Process delayed spawns
    processDelayedSpawns();

    updateSnake(deltaMs);
    renderTrail();

    // Show debug points if enabled
    if (debugTarget.isOn()) {
      renderDebugTarget();
    }
    if (debugStart.isOn()) {
      renderDebugStart();
    }
  }

  private void processDelayedSpawns() {
    for (int i = delayedSpawns.size() - 1; i >= 0; i--) {
      DelayedSpawn spawn = delayedSpawns.get(i);

      if (currentTime >= spawn.spawnTime) {
        // Time to spawn the ants, but respect max ant limit
        int maxAntsAllowed = (int) maxAnts.getValue();
        for (int j = 0; j < spawn.quantity && movingAnts.size() < maxAntsAllowed; j++) {
          movingAnts.add(createAnt(spawn.pathIndex, spawn.quantity));
        }

        // Remove the processed spawn
        delayedSpawns.remove(i);
      }
    }
  }

  private void updateSnake(double deltaMs) {
    float moveSpeed = (float) (speed.getValue() * deltaMs * 0.01);

    // Calculate target position
    float targetPosX = (float) (targetX.getValue() * (getRingLength() - 1));
    float targetPosY = (float) (targetY.getValue() * (getRingHeight() - 1));

    // Always spawn and update seeker ant
    spawnSeekerAnt(targetPosX, targetPosY);
    updateSeekerAnt(deltaMs, targetPosX, targetPosY, moveSpeed);

    // Only spawn following ants after path is found
    if (pathFound) {
      spawnNewAnts(deltaMs);
      updateMovingAnts(deltaMs);
    }
  }

  private void spawnSeekerAnt(float targetPosX, float targetPosY) {
    if (needsInitialAnt) {
      needsInitialAnt = false;
      float startPosX = (float) (startX.getValue() * (getRingLength() - 1));
      float startPosY = (float) (startY.getValue() * (getRingHeight() - 1));

      seekerAnt = new SeekerAnt(startPosX, startPosY);

      // Set wandering start time - seeker will wander before heading to target
      seekerAnt.wanderStartTime = currentTime;
      // Initial direction is already random from constructor
    }
  }

  private void spawnNewAnts(double deltaMs) {
    // Reduced time-based spawning - primary spawning is now return-home based
    // Only spawn initial ants or explorers at a very low rate
    float antSpawnRate = (float) (speed.getValue() * deltaMs * 0.001); // Reduced from 0.005
    if (Math.random() < antSpawnRate) {
      boolean isExplorer = Math.random() < explorerRate.getValue();
      int pathIndex = choosePathForNewAnt(isExplorer);

      // For initial seeding, spawn just one ant (return-home will handle quantity)
      movingAnts.add(createAnt(pathIndex, 1));
    }
  }

  private int choosePathForNewAnt(boolean isExplorer) {
    if (isExplorer && discoveredPaths.size() < 3) {
      // Explorer might create a new path
      return discoveredPaths.size(); // Will trigger new path creation
    } else {
      // Regular ant or no room for new paths - use existing path
      return (int) (Math.random() * discoveredPaths.size());
    }
  }

  private void updateSeekerAnt(double deltaMs, float targetPosX, float targetPosY, float moveSpeed) {
    if (seekerAnt == null)
      return;

    if (!seekerAnt.reachedTarget) {
      // Moving toward target
      boolean reachedTarget = moveSeekerTowards(seekerAnt, targetPosX, targetPosY, moveSpeed);

      if (reachedTarget) {
        seekerAnt.reachedTarget = true;
        seekerAnt.returningHome = true;

        // Reset wandering state for return journey
        seekerAnt.isWandering = true;
        seekerAnt.wanderStartTime = currentTime;
        seekerAnt.wanderDuration = 800 + Math.random() * 800; // Random 0.8-1.6 seconds
        seekerAnt.direction = (float) (Math.random() * 2 * Math.PI); // Random direction

        // Add target position to path if not already there
        if (seekerAnt.path.isEmpty() ||
            Math.abs(seekerAnt.headX - seekerAnt.path.get(seekerAnt.path.size() - 1).x) > 1.0f ||
            Math.abs(seekerAnt.headY - seekerAnt.path.get(seekerAnt.path.size() - 1).y) > 1.0f) {
          seekerAnt.path.add(new AntSegment(seekerAnt.headX, seekerAnt.headY, true)); // true = going to target (final
                                                                                      // destination)
        }
      }
    } else if (seekerAnt.returningHome) {
      // Moving toward home
      boolean reachedHome = moveSeekerTowards(seekerAnt, seekerAnt.startX, seekerAnt.startY, moveSpeed);

      if (reachedHome) {
        // Ant made it home! Use only the forward path (going to target)
        discoveredPaths.clear();
        List<AntSegment> forwardPath = new ArrayList<>();
        for (AntSegment segment : seekerAnt.path) {
          if (segment.goingToTarget) {
            forwardPath.add(segment);
          }
        }
        discoveredPaths.add(forwardPath);
        pathFound = true;
      }
    }
  }

  private void updateMovingAnts(double deltaMs) {
    // Normal ant movement with individual speed variations
    float baseMoveSpeed = (float) (speed.getValue() * deltaMs * 0.0002);

    for (int i = movingAnts.size() - 1; i >= 0; i--) {
      MovingAnt ant = movingAnts.get(i);

      // Update wandering behavior
      updateAntWandering(ant, deltaMs);

      // Apply individual speed variation to this ant
      float antMoveSpeed = baseMoveSpeed * ant.speedMultiplier;

      // Update ant position - always move, collision avoidance only affects rendering
      ant.position += antMoveSpeed;

      if (ant.position >= 2.0f) {
        // Ant completed round trip - schedule delayed spawn
        int numAntsToSpawn = Math.max(1, (int) Math.round(quantity.getValue()));

        // Random delay between 0 and 2000ms (2 seconds)
        double spawnDelay = Math.random() * 2000.0;
        double spawnTime = currentTime + spawnDelay;

        // Add to delayed spawn queue
        delayedSpawns.add(new DelayedSpawn(spawnTime, ant.pathIndex, numAntsToSpawn));

        // Remove the completed ant
        movingAnts.remove(i);
      }
    }
  }

  private boolean moveSeekerTowards(SeekerAnt seeker, float targetX, float targetY, float moveSpeed) {
    // Check if still in wandering phase
    if (seeker.isWandering && (currentTime - seeker.wanderStartTime) < seeker.wanderDuration) {
      // Pure wandering - create curved exploration
      float randomChange = (float) ((Math.random() - 0.5) * 1.5); // High random variation for curves
      seeker.direction += randomChange;
    } else {
      // Switch to target-seeking mode with curved paths
      seeker.isWandering = false;

      // Update long-term curve bias occasionally for overarching path randomness
      if (currentTime - seeker.lastCurveUpdateTime > 2000) { // Update every 2 seconds
        seeker.longTermCurveBias = (float) ((Math.random() - 0.5) * 0.8); // Random bias direction
        seeker.lastCurveUpdateTime = currentTime;
      }

      // Calculate direction to target
      float dx = targetX - seeker.headX;
      float dy = targetY - seeker.headY;
      float targetDirection = (float) Math.atan2(dy, dx);

      // Create curved path by adding perpendicular bias and random curves
      float distanceToTarget = (float) Math.sqrt(dx * dx + dy * dy);
      float curveIntensity = Math.min(0.8f, distanceToTarget / 20.0f); // More curves when farther away

      // Add short-term perpendicular curve bias (creates S-curves)
      float shortTermCurveBias = (float) Math.sin(currentTime * 0.003) * curveIntensity * 0.4f;

      // Add long-term curve bias for overarching path direction
      float longTermBias = seeker.longTermCurveBias * curveIntensity * 0.6f;

      // Random wandering component for natural variation
      float randomChange = (float) ((Math.random() - 0.5) * 1.0); // Strong random variation

      // Gentle target bias (weaker so curves dominate)
      float targetBias = (float) Math.sin(targetDirection - seeker.direction) * 0.3f; // Weak target bias

      seeker.direction += randomChange + targetBias + shortTermCurveBias + longTermBias;
    }

    // Move seeker in current direction
    updateNewXAndY(seeker.headX, seeker.headY, seeker.direction, moveSpeed);
    float newX = tempPosition[0];
    float newY = tempPosition[1];

    // Check if new position is in door area and adjust direction
    if (isInDoorArea(newX, newY)) {
      // If we hit a door, redirect the direction upward to go around it
      seeker.direction = (float) (-Math.PI / 2); // Point upward
      // Recalculate movement with new direction
      updateNewXAndY(seeker.headX, seeker.headY, seeker.direction, moveSpeed);
      newX = tempPosition[0];
      newY = tempPosition[1];
    }

    seeker.headX = newX;
    seeker.headY = newY;

    // Add to seeker's path every few steps
    if (seeker.path.isEmpty() ||
        Math.abs(seeker.headX - seeker.path.get(seeker.path.size() - 1).x) > 1.0f ||
        Math.abs(seeker.headY - seeker.path.get(seeker.path.size() - 1).y) > 1.0f) {
      seeker.path.add(new AntSegment(seeker.headX, seeker.headY, seeker.isGoingToTarget()));
    }

    // Check if reached destination
    float distance = (float) Math.sqrt(
        Math.pow(seeker.headX - targetX, 2) + Math.pow(seeker.headY - targetY, 2));
    return distance < 2.0f;
  }

  // Reusable array to avoid allocation in render loop
  private final float[] tempPosition = new float[2];

  private void updateNewXAndY(float currentX, float currentY, float direction, float moveSpeed) {
    // Calculate new position based on direction and speed
    float newX = currentX + (float) Math.cos(direction) * moveSpeed;
    float newY = currentY + (float) Math.sin(direction) * moveSpeed;

    // Keep within bounds - X wraps around the ring, Y is clamped
    newX = (newX + getRingLength()) % getRingLength();
    newY = Math.max(0, Math.min(getRingHeight() - 1, newY));

    tempPosition[0] = newX;
    tempPosition[1] = newY;
  }

  private void renderTrail() {
    float baseSize = (float) antSize.getValue(); // Use full size value

    if (!pathFound) {
      // Render seeker ant only
      renderSeekerAnt(baseSize);
    } else {
      // Path found, render discovered path and normal ant flow
      renderSeekerPath(baseSize);
      renderMovingAnts(baseSize);
    }
  }

  private void renderSeekerAnt(float baseSize) {
    if (seekerAnt == null)
      return;

    // Purple path rendering removed - no trail behind seeker ant

    // Draw the seeker ant itself with direction-based rendering
    // Use white color for seeker ant
    int seekerColor = 0xFFFFFFFF; // White for all ants
    drawAntAtPositionWithDirection(seekerAnt.headX, seekerAnt.headY, seekerColor, baseSize,
        seekerAnt.isGoingToTarget());
  }

  private void renderSeekerPath(float baseSize) {
    // Purple path rendering removed - no visible trail paths
    // Only moving ants are rendered, not the discovered path segments
  }

  private void renderMovingAnts(float baseSize) {
    for (MovingAnt ant : movingAnts) {
      float pathPos = ant.getPathPosition();
      float[] position = getPositionAlongPath(pathPos, ant.pathIndex);
      if (position != null) {
        // Apply lane separation
        float[] adjustedPos = applyLaneSeparation(position, ant);
        // Apply wandering behavior
        float[] wanderPos = applyWandering(adjustedPos, ant);
        drawAntAtPositionWithDirection(wanderPos[0], wanderPos[1], ant.color, baseSize, ant.isGoingToTarget());
      }
    }
  }

  private void updateAntWandering(MovingAnt ant, double deltaMs) {
    // Check if ant should start wandering
    if (!ant.isWandering && Math.random() < wanderChance.getValue()) {
      ant.isWandering = true;
      ant.wanderStartTime = currentTime;
      ant.wanderDuration = 500 + Math.random() * 1000; // Wander for 0.5-1.5 seconds
      ant.wanderDirection = (float) (Math.random() * 2 * Math.PI);
      ant.wanderX = 0;
      ant.wanderY = 0;
    }

    if (ant.isWandering) {
      // Check if wandering period is over
      if (currentTime - ant.wanderStartTime > ant.wanderDuration) {
        // Return to path gradually
        float returnSpeed = (float) (deltaMs * 0.002);
        ant.wanderX *= (1.0f - returnSpeed);
        ant.wanderY *= (1.0f - returnSpeed);

        // Stop wandering when close enough to path
        if (Math.abs(ant.wanderX) < 0.1f && Math.abs(ant.wanderY) < 0.1f) {
          ant.isWandering = false;
          ant.wanderX = 0;
          ant.wanderY = 0;
        }
      } else {
        // Continue wandering
        float wanderSpeed = (float) (deltaMs * 0.001);
        float maxDistance = (float) wanderDistance.getValue();

        // Add some randomness to direction
        ant.wanderDirection += (Math.random() - 0.5) * 0.3;

        // Move in wander direction
        float newWanderX = ant.wanderX + (float) Math.cos(ant.wanderDirection) * wanderSpeed;
        float newWanderY = ant.wanderY + (float) Math.sin(ant.wanderDirection) * wanderSpeed;

        // Limit wandering distance and check for doors
        float wanderDist = (float) Math.sqrt(newWanderX * newWanderX + newWanderY * newWanderY);
        if (wanderDist <= maxDistance) {
          // Check if the new wandering position would be in a door area
          float[] currentPos = getPositionAlongPath(ant.getPathPosition(), ant.pathIndex);
          if (currentPos != null) {
            float testX = currentPos[0] + newWanderX;
            float testY = currentPos[1] + newWanderY;

            if (!isInDoorArea(testX, testY)) {
              ant.wanderX = newWanderX;
              ant.wanderY = newWanderY;
            } else {
              // Redirect away from door area
              ant.wanderDirection += (float) Math.PI; // Turn around
            }
          } else {
            ant.wanderX = newWanderX;
            ant.wanderY = newWanderY;
          }
        } else {
          // Redirect toward center when hitting wander limit
          ant.wanderDirection = (float) Math.atan2(-ant.wanderY, -ant.wanderX);
        }
      }
    }
  }

  private float[] applyWandering(float[] position, MovingAnt ant) {
    return new float[] { position[0] + ant.wanderX, position[1] + ant.wanderY };
  }

  private float[] applyLaneSeparation(float[] position, MovingAnt ant) {
    if (!laneSeparation.isOn()) {
      return position; // No lane separation
    }

    // Offset ants based on direction to create lanes
    float laneOffset = ant.isGoingToTarget() ? -(float) laneDistance.getValue() : (float) laneDistance.getValue();

    // Apply offset perpendicular to path direction (simplified as Y offset)
    return new float[] { position[0], position[1] + laneOffset };
  }

  private void drawAntAtPosition(float x, float y, int color, float size) {
    // For debug rendering (no direction)
    int radius = (int) Math.ceil(size);

    // Draw center pixel
    setPixelOnShape(x, y, color);

    // Draw surrounding pixels in a circular pattern
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = -radius; dy <= radius; dy++) {
        if (dx == 0 && dy == 0)
          continue; // Skip center pixel (already drawn)

        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= size) {
          setPixelOnShape(x + dx, y + dy, color);
        }
      }
    }
  }

  private void drawAntAtPositionWithDirection(float x, float y, int color, float size, boolean isGoingToTarget) {
    // For ant rendering with direction-based surface selection
    int radius = (int) Math.ceil(size);

    // Draw center pixel
    setPixelOnShapeWithDirection(x, y, color, isGoingToTarget);

    // Draw surrounding pixels in a circular pattern
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = -radius; dy <= radius; dy++) {
        if (dx == 0 && dy == 0)
          continue; // Skip center pixel (already drawn)

        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= size) {
          setPixelOnShapeWithDirection(x + dx, y + dy, color, isGoingToTarget);
        }
      }
    }
  }

  private void setPixelOnShape(float ringX, float ringY, int color) {
    // Convert ring coordinates to actual pixels - render on both surfaces
    int ringIndex = (int) Math.round(ringY);
    int pointIndex = (int) Math.round(ringX);

    if (shape.getValuei() == 0) {
      // Cube rendering
      if (ringIndex >= 0 && ringIndex < Apotheneum.GRID_HEIGHT) {
        setPixelOnRing(Apotheneum.cube.exterior.ring(ringIndex), pointIndex, color);
        setPixelOnRing(Apotheneum.cube.interior.ring(ringIndex), pointIndex, color);
      }
    } else {
      // Cylinder rendering
      if (ringIndex >= 0 && ringIndex < Apotheneum.CYLINDER_HEIGHT) {
        setPixelOnRing(Apotheneum.cylinder.exterior.ring(ringIndex), pointIndex, color);
        setPixelOnRing(Apotheneum.cylinder.interior.ring(ringIndex), pointIndex, color);
      }
    }
  }

  private void setPixelOnShapeWithDirection(float ringX, float ringY, int color, boolean isGoingToTarget) {
    // Convert ring coordinates to actual pixels with direction-based rendering
    int ringIndex = (int) Math.round(ringY);
    int pointIndex = (int) Math.round(ringX);

    DiscreteParameter renderParam = isGoingToTarget ? forwardRender : returnRender;
    int renderMode = renderParam.getValuei();

    if (shape.getValuei() == 0) {
      // Cube rendering
      if (ringIndex >= 0 && ringIndex < Apotheneum.GRID_HEIGHT) {
        // 0=Exterior, 1=Interior, 2=Both
        if (renderMode == 0 || renderMode == 2) {
          setPixelOnRing(Apotheneum.cube.exterior.ring(ringIndex), pointIndex, color);
        }
        if (renderMode == 1 || renderMode == 2) {
          setPixelOnRing(Apotheneum.cube.interior.ring(ringIndex), pointIndex, color);
        }
      }
    } else {
      // Cylinder rendering
      if (ringIndex >= 0 && ringIndex < Apotheneum.CYLINDER_HEIGHT) {
        // 0=Exterior, 1=Interior, 2=Both
        if (renderMode == 0 || renderMode == 2) {
          setPixelOnRing(Apotheneum.cylinder.exterior.ring(ringIndex), pointIndex, color);
        }
        if (renderMode == 1 || renderMode == 2) {
          setPixelOnRing(Apotheneum.cylinder.interior.ring(ringIndex), pointIndex, color);
        }
      }
    }
  }

  private void setPixelOnRing(Apotheneum.Ring ring, int pointIndex, int color) {
    // Set pixel on ring, wrapping around if necessary
    if (ring != null && ring.points.length > 0) {
      int wrappedIndex = ((pointIndex % ring.points.length) + ring.points.length) % ring.points.length;
      colors[ring.points[wrappedIndex].index] = color;
    }
  }

  private boolean isInDoorArea(float ringX, float ringY) {
    // Check if position is in a door area where pixels are blocked
    int ringIndex = (int) Math.round(ringY);
    int ringHeight = getRingHeight();
    int ringLength = getRingLength();

    if (ringIndex < 0 || ringIndex >= ringHeight) {
      return false;
    }

    // Check if the ring position is at the bottom (where doors are)
    if (ringIndex >= ringHeight - Apotheneum.DOOR_HEIGHT) {
      // Check if X position is in door area
      int ringPos = (int) Math.round(ringX);
      int wrappedPos = ((ringPos % ringLength) + ringLength) % ringLength;

      if (shape.getValuei() == 0) {
        // Cube door detection - check each face for door positions
        for (int face = 0; face < 4; face++) {
          int faceStart = face * Apotheneum.GRID_WIDTH; // Each face is 50 pixels wide
          int doorStart = faceStart + Apotheneum.Cube.DOOR_START_COLUMN; // Door starts at column 20 on each face
          int doorEnd = doorStart + Apotheneum.DOOR_WIDTH - 1;

          if (wrappedPos >= doorStart && wrappedPos <= doorEnd) {
            return true;
          }
        }
      } else {
        // Cylinder door detection - doors repeat every 30 pixels
        int doorStart = Apotheneum.Cylinder.DOOR_START_COLUMN; // Door starts at column 10
        int doorEnd = doorStart + Apotheneum.DOOR_WIDTH - 1;

        // Check if we're in a door area (doors repeat every 30 pixels around cylinder)
        int posInCycle = wrappedPos % 30;
        if (posInCycle >= doorStart && posInCycle <= doorEnd) {
          return true;
        }
      }
    }

    return false;
  }

  private void clearAllAnts() {
    discoveredPaths.clear();
    movingAnts.clear();
    seekerAnt = null;
    needsInitialAnt = true; // Reset flag to spawn first ant immediately
    pathFound = false;
    initializeStartPosition();
  }

  private void renderDebugTarget() {
    float targetPosX = (float) (targetX.getValue() * (getRingLength() - 1));
    float targetPosY = (float) (targetY.getValue() * (getRingHeight() - 1));
    int redColor = 0xFFFF0000; // Bright red

    // Draw a cross at the target location
    drawAntAtPosition(targetPosX, targetPosY, redColor, 1.0f);
    // Add cross lines
    if (targetPosX > 1)
      setPixelOnShape(targetPosX - 2, targetPosY, redColor);
    if (targetPosX < getRingLength() - 2)
      setPixelOnShape(targetPosX + 2, targetPosY, redColor);
    if (targetPosY > 1)
      setPixelOnShape(targetPosX, targetPosY - 2, redColor);
    if (targetPosY < getRingHeight() - 2)
      setPixelOnShape(targetPosX, targetPosY + 2, redColor);
  }

  private void renderDebugStart() {
    float startPosX = (float) (startX.getValue() * (getRingLength() - 1));
    float startPosY = (float) (startY.getValue() * (getRingHeight() - 1));
    int greenColor = 0xFF00FF00; // Bright green

    // Draw a cross at the start location
    drawAntAtPosition(startPosX, startPosY, greenColor, 1.0f);
    // Add cross lines
    if (startPosX > 1)
      setPixelOnShape(startPosX - 2, startPosY, greenColor);
    if (startPosX < getRingLength() - 2)
      setPixelOnShape(startPosX + 2, startPosY, greenColor);
    if (startPosY > 1)
      setPixelOnShape(startPosX, startPosY - 2, greenColor);
    if (startPosY < getRingHeight() - 2)
      setPixelOnShape(startPosX, startPosY + 2, greenColor);
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    if (parameter == targetX || parameter == targetY || parameter == startX || parameter == startY) {
      // Target or start position changed, reset the path
      resetPath();
    }
    super.onParameterChanged(parameter);
  }

  private void resetPath() {
    // Clear the seeker ant to restart the pathfinding process
    seekerAnt = null;
    discoveredPaths.clear();
    movingAnts.clear();
    pathFound = false;
    needsInitialAnt = true;
  }

  private MovingAnt createAnt(int pathIndex, int quantity) {
    int antColor = 0xFFFFFFFF; // White for all ants
    float speedVar = 1.0f + ((float) (Math.random() - 0.5) * 2.0f * (float) speedVariation.getValue());
    return new MovingAnt(0.0f, antColor, pathIndex, speedVar);
  }

  public final CompoundParameter wanderChance = new CompoundParameter("Wander", 0.02, 0, 0.1)
      .setDescription("Chance per frame for ants to wander off path");

  public final CompoundParameter wanderDistance = new CompoundParameter("Wander Dist", 3.0, 0.5, 8.0)
      .setDescription("How far ants wander from their path");

  private float[] getPositionAlongPath(float t, int pathIndex) {
    // Ensure we have at least one path
    if (discoveredPaths.isEmpty()) {
      discoveredPaths.add(new ArrayList<>());
    }

    if (pathIndex >= discoveredPaths.size()) {
      pathIndex = 0; // Fallback to main path
    }

    List<AntSegment> path = discoveredPaths.get(pathIndex);
    if (path.size() == 0) {
      // No path yet, show ant at start position
      float startPosX = (float) (startX.getValue() * (getRingLength() - 1));
      float startPosY = (float) (startY.getValue() * (getRingHeight() - 1));
      return new float[] { startPosX, startPosY };
    } else if (path.size() == 1) {
      // Only one segment, interpolate between start and first segment
      float startPosX = (float) (startX.getValue() * (getRingLength() - 1));
      float startPosY = (float) (startY.getValue() * (getRingHeight() - 1));
      AntSegment first = path.get(0);
      return new float[] {
          startPosX + (first.x - startPosX) * t,
          startPosY + (first.y - startPosY) * t
      };
    }

    float totalIndex = t * (path.size() - 1);
    int index = (int) totalIndex;
    float fraction = totalIndex - index;

    if (index >= path.size() - 1) {
      AntSegment last = path.get(path.size() - 1);
      return new float[] { last.x, last.y };
    }

    AntSegment current = path.get(index);
    AntSegment next = path.get(index + 1);

    float x = current.x + (next.x - current.x) * fraction;
    float y = current.y + (next.y - current.y) * fraction;

    return new float[] { x, y };
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Ants ants) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 6);

    // Start column
    addColumn(uiDevice, "Start",
        newKnob(ants.startX),
        newKnob(ants.startY),
        newButton(ants.debugStart).setTriggerable(true)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Target column
    addColumn(uiDevice, "Target",
        newKnob(ants.targetX),
        newKnob(ants.targetY),
        newButton(ants.debugTarget).setTriggerable(true)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Movement controls - split to avoid overflow
    addColumn(uiDevice, "Movement",
        newKnob(ants.speed),
        newKnob(ants.speedVariation),
        newKnob(ants.spacing)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Movement controls 2
    addColumn(uiDevice, "Movement 2",
        newKnob(ants.maxChange),
        newKnob(ants.attraction),
        newKnob(ants.wanderChance)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Behavior controls
    addColumn(uiDevice, "Behavior",
        newKnob(ants.explorerRate),
        newKnob(ants.wanderDistance)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Visual controls
    addColumn(uiDevice, "Visual",
        newKnob(ants.antSize),
        newKnob(ants.quantity),
        newButton(ants.clearAnts).setTriggerable(true)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Path controls
    addColumn(uiDevice, "Path",
        newDropMenu(ants.forwardRender),
        newDropMenu(ants.returnRender)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Lane controls
    addColumn(uiDevice, "Lanes",
        newButton(ants.laneSeparation).setTriggerable(true),
        newKnob(ants.laneDistance)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Shape controls
    addColumn(uiDevice, "Shape",
        newDropMenu(ants.shape)).setChildSpacing(6);
  }
}