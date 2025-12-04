package apotheneum.patterns;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@LXCategory("Apotheneum")
@LXComponentName("Hyperspace")
public class Hyperspace extends ApotheneumPattern implements UIDeviceControls<Hyperspace> {
  
  // Star particle in 3D space
  private static class Star {
    float x, y, z;  // Position in model space (0-1)
    float vx, vy, vz;  // Velocity
    float speed;    // Individual star speed multiplier
    int color;      // Star color
    double lifespan; // Total lifespan in milliseconds
    double age;     // Current age in milliseconds
    boolean dead;   // Mark for removal
    
    
    Star(double maxLifespan, LXPoint[] allPoints) {
      this.lifespan = Math.random() * maxLifespan + maxLifespan * 0.5; // 50%-150% of max
      reset();
    }
    
    void reset() {
      // Reset is called during initialization and when stars die
      // We'll set a default position here, but actual spawning position
      // will be set by the main pattern based on motion direction
      x = (float)Math.random();
      y = (float)Math.random(); 
      z = (float)Math.random();
      
      
      // Stars don't have individual velocities - they're static
      // Only the motion controls move them
      vx = 0;
      vy = 0;
      vz = 0;
      
      speed = 0.5f + (float)Math.random() * 1.0f; // Individual speed variation
      age = 0; // Reset age
      dead = false;
      
      // Pure white stars with brightness variation
      float brightness = 0.8f + (float)Math.random() * 0.2f;
      color = LXColor.rgb(
        (int)(brightness * 255),
        (int)(brightness * 255),
        (int)(brightness * 255)
      );
    }
    
    void update(double deltaMs, float baseSpeed, int axis, float direction) {
      // Age the star
      age += deltaMs;

      float currentSpeed = baseSpeed * speed;
      
      // Motion control moves the entire star field in one axis
      // Stars themselves are static - only the field moves
      float movement = direction * currentSpeed;
      
      switch (axis) {
        case 0: // X axis
          x += movement;
          break;
        case 1: // Y axis
          y += movement;
          break;
        case 2: // Z axis
          z += movement;
          break;
      }
      
      
      // Mark as dead if out of bounds
      if (x < -0.2f || x > 1.2f || y < -0.2f || y > 1.2f || z < -0.2f || z > 1.2f) {
        dead = true;
        return;
      }

      // Mark as dead if exceeded lifespan
      if (age >= lifespan) {
        dead = true;
      }
    }
    
    // Spawn star behind the installation based on motion direction
    void spawnBehind(int axis, float direction) {
      // Mark as alive
      dead = false;
      age = 0;

      // Random position in the two perpendicular axes
      float rand1 = (float)Math.random();
      float rand2 = (float)Math.random();
      
      switch (axis) {
        case 0: // X-axis motion
          y = rand1;
          z = rand2;
          if (direction > 0) {
            x = -0.1f; // Spawn just behind negative X
          } else {
            x = 1.1f; // Spawn just behind positive X
          }
          break;
          
        case 1: // Y-axis motion
          x = rand1;
          z = rand2;
          if (direction > 0) {
            y = -0.1f; // Spawn just behind negative Y
          } else {
            y = 1.1f; // Spawn just behind positive Y
          }
          break;
          
        case 2: // Z-axis motion
          x = rand1;
          y = rand2;
          if (direction > 0) {
            z = -0.1f; // Spawn just behind negative Z
          } else {
            z = 1.1f; // Spawn just behind positive Z
          }
          break;
      }
    }
    
    float getBrightness() {
      // Smooth fade in/out
      double lifeFraction = age / lifespan;
      
      if (lifeFraction < 0.1) {
        // Fade in over first 10% of life
        return (float)(lifeFraction / 0.1);
      } else if (lifeFraction > 0.9) {
        // Fade out over last 10% of life
        return (float)((1.0 - lifeFraction) / 0.1);
      } else {
        // Full brightness in middle
        return 1.0f;
      }
    }
  }
  
  // LED Spatial grid for efficient nearest-neighbor search
  private static class LEDSpatialGrid {
    private final float cellSize;
    private final Map<Long, List<LXPoint>> grid;
    private final int gridWidth, gridHeight, gridDepth;
    private final float xMin, yMin, zMin;
    private final float xMax, yMax, zMax;
    private final List<LXPoint> reusableList = new ArrayList<>(); // Reusable list to avoid allocations
    
    LEDSpatialGrid(LXPoint[] points, float cellSize) {
      this.cellSize = cellSize;
      this.grid = new HashMap<>();
      
      // Find bounds
      float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
      float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;
      
      for (LXPoint p : points) {
        minX = Math.min(minX, p.x);
        minY = Math.min(minY, p.y);
        minZ = Math.min(minZ, p.z);
        maxX = Math.max(maxX, p.x);
        maxY = Math.max(maxY, p.y);
        maxZ = Math.max(maxZ, p.z);
      }
      
      // Store bounds with some padding
      this.xMin = minX - cellSize;
      this.yMin = minY - cellSize;
      this.zMin = minZ - cellSize;
      this.xMax = maxX + cellSize;
      this.yMax = maxY + cellSize;
      this.zMax = maxZ + cellSize;
      
      // Calculate grid dimensions
      this.gridWidth = (int) Math.ceil((xMax - xMin + 2 * cellSize) / cellSize);
      this.gridHeight = (int) Math.ceil((yMax - yMin + 2 * cellSize) / cellSize);
      this.gridDepth = (int) Math.ceil((zMax - zMin + 2 * cellSize) / cellSize);
      
      // Add all points to grid
      for (LXPoint p : points) {
        long key = getCellKey(p.x, p.y, p.z);
        List<LXPoint> cell = grid.computeIfAbsent(key, k -> new ArrayList<>());
        cell.add(p);
      }
      
      LX.log(String.format("LED Spatial Grid initialized: %d cells, %d LEDs, grid size %dx%dx%d", 
        grid.size(), points.length, gridWidth, gridHeight, gridDepth));
    }
    
    List<LXPoint> getNearbyLEDs(float x, float y, float z, float radius) {
      reusableList.clear(); // Clear previous results
      
      // Calculate cell range to search
      int minGridX = Math.max(0, (int) Math.floor((x - radius - xMin) / cellSize));
      int maxGridX = Math.min(gridWidth - 1, (int) Math.ceil((x + radius - xMin) / cellSize));
      int minGridY = Math.max(0, (int) Math.floor((y - radius - yMin) / cellSize));
      int maxGridY = Math.min(gridHeight - 1, (int) Math.ceil((y + radius - yMin) / cellSize));
      int minGridZ = Math.max(0, (int) Math.floor((z - radius - zMin) / cellSize));
      int maxGridZ = Math.min(gridDepth - 1, (int) Math.ceil((z + radius - zMin) / cellSize));
      
      // Search cells in range
      for (int gx = minGridX; gx <= maxGridX; gx++) {
        for (int gy = minGridY; gy <= maxGridY; gy++) {
          for (int gz = minGridZ; gz <= maxGridZ; gz++) {
            long key = getKey(gx, gy, gz);
            List<LXPoint> cell = grid.get(key);
            if (cell != null) {
              reusableList.addAll(cell);
            }
          }
        }
      }
      
      return reusableList;
    }
    
    private long getCellKey(float x, float y, float z) {
      int gx = Math.max(0, Math.min(gridWidth - 1, (int) Math.floor((x - xMin) / cellSize)));
      int gy = Math.max(0, Math.min(gridHeight - 1, (int) Math.floor((y - yMin) / cellSize)));
      int gz = Math.max(0, Math.min(gridDepth - 1, (int) Math.floor((z - zMin) / cellSize)));
      return getKey(gx, gy, gz);
    }
    
    private long getKey(int gx, int gy, int gz) {
      return ((long) gx << 42) | ((long) gy << 21) | gz;
    }
  }
  
  private static final int MAX_STARS = 5000; // Pre-allocated pool size
  private final Star[] starPool = new Star[MAX_STARS];
  private int activeStarCount = 0;
  private LXPoint[] allPoints; // Cache of all LED points for targeting
  private LEDSpatialGrid ledGrid; // Spatial grid for efficient LED search
  
  // Pre-computed LED mappings for performance
  private Map<Integer, Integer> exteriorToInteriorMap = new HashMap<>();
  private Map<Integer, Boolean> cubeLedsMap = new HashMap<>();
  private Map<Integer, Integer> cubeFaceMap = new HashMap<>(); // Maps LED index to cube face (0=front, 1=right, 2=back, 3=left)
  
  // Performance monitoring
  private long frameCount = 0;
  private long totalUpdateTime = 0;
  private long totalRenderTime = 0;
  private long totalFindLEDTime = 0;
  private long totalLEDsSearched = 0;
  private long totalStarsRendered = 0;
  
  public final CompoundParameter speed = new CompoundParameter("Speed", 0.5, 0.1, 50.0)
    .setDescription("Speed of hyperspace travel");
    
  public final CompoundParameter density = new CompoundParameter("Density", 100, 10, 2000)
    .setDescription("Stars spawned per second");
    
  public final CompoundParameter starSize = new CompoundParameter("Star Size", 0.1, 0.05, 0.3)
    .setDescription("Size of stars and trails");
    
  public final CompoundParameter duration = new CompoundParameter("Duration", 3000, 1000, 8000)
    .setDescription("How long stars live (milliseconds)");
    
  public final CompoundParameter brightness = new CompoundParameter("Bright", 1.0, 0.1, 2.0)
    .setDescription("Overall brightness");
    
    
  public final BooleanParameter pulse = new BooleanParameter("Pulse", false)
    .setDescription("Pulsing speed effect");
    
  public final CompoundParameter motionAxis = new CompoundParameter("Axis", 0, 0, 2)
    .setDescription("Motion axis: 0=X, 1=Y, 2=Z");
    
  public final CompoundParameter motionDirection = new CompoundParameter("Direction", 1, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Motion direction: -1=Negative, +1=Positive");
    
    
  public final BooleanParameter renderToCube = new BooleanParameter("Cube", true)
    .setDescription("Render stars to cube surfaces");
    
  public final BooleanParameter renderToCylinder = new BooleanParameter("Cylinder", true)
    .setDescription("Render stars to cylinder surfaces");

  public final BooleanParameter clearStars = new BooleanParameter("Clear", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Clear and respawn all stars");

  private double pulsePhase = 0;
  private double spawnAccumulator = 0; // Accumulates fractional star spawns
  
  public Hyperspace(LX lx) {
    super(lx);
    addParameter("speed", this.speed);
    addParameter("density", this.density);
    addParameter("starSize", this.starSize);
    addParameter("duration", this.duration);
    addParameter("brightness", this.brightness);
    addParameter("pulse", this.pulse);
    addParameter("motionAxis", this.motionAxis);
    addParameter("motionDirection", this.motionDirection);
    addParameter("renderToCube", this.renderToCube);
    addParameter("renderToCylinder", this.renderToCylinder);
    addParameter("clearStars", this.clearStars);

    // Cache all LED points for star targeting
    allPoints = model.points;
    
    // Initialize LED spatial grid for efficient nearest-neighbor search
    // Cell size of 10 units seems reasonable for the installation scale
    ledGrid = new LEDSpatialGrid(allPoints, 10.0f);
    
    // Pre-compute LED mappings for performance
    precomputeLEDMappings();

    // Pre-allocate all star objects (object pool to avoid GC)
    double maxLifespan = duration.getValue();
    for (int i = 0; i < MAX_STARS; i++) {
      starPool[i] = new Star(maxLifespan, allPoints);
    }
  }
  
  private void spawnStars(double deltaMs, int axis, float direction) {
    double spawnRate = density.getValue(); // stars per second
    double maxLifespan = duration.getValue();

    // Accumulate fractional spawns
    spawnAccumulator += spawnRate * deltaMs / 1000.0;

    // Spawn whole stars from the pool
    while (spawnAccumulator >= 1.0 && activeStarCount < MAX_STARS) {
      spawnAccumulator -= 1.0;
      Star star = starPool[activeStarCount];
      star.lifespan = Math.random() * maxLifespan + maxLifespan * 0.5;
      star.spawnBehind(axis, direction);
      activeStarCount++;
    }

    // Cap accumulator if pool is full
    if (activeStarCount >= MAX_STARS) {
      spawnAccumulator = 0;
    }
  }

  private void removeDeadStars() {
    // Compact the array by swapping dead stars with active ones from the end
    int writeIndex = 0;
    for (int readIndex = 0; readIndex < activeStarCount; readIndex++) {
      if (!starPool[readIndex].dead) {
        if (writeIndex != readIndex) {
          // Swap to keep active stars contiguous
          Star temp = starPool[writeIndex];
          starPool[writeIndex] = starPool[readIndex];
          starPool[readIndex] = temp;
        }
        writeIndex++;
      }
    }
    activeStarCount = writeIndex;
  }
  
  @Override
  public void onParameterChanged(heronarts.lx.parameter.LXParameter parameter) {
    if (parameter == clearStars && clearStars.isOn()) {
      // Clear all stars - just reset the active count, pool stays allocated
      activeStarCount = 0;
      spawnAccumulator = 0;
    }
    super.onParameterChanged(parameter);
  }
  
  @Override
  protected void render(double deltaMs) {
    long frameStartTime = System.nanoTime();

    int axis = (int)motionAxis.getValue();
    float direction = (float)motionDirection.getValue();

    // Spawn new stars based on density (spawn rate)
    spawnStars(deltaMs, axis, direction);

    // Update pulse phase
    if (pulse.isOn()) {
      pulsePhase += deltaMs * 0.003;
    }

    // Calculate current speed with optional pulse
    float currentSpeed = (float)(speed.getValue() * deltaMs * 0.0001);
    if (pulse.isOn()) {
      currentSpeed *= 1.0f + (float)Math.sin(pulsePhase) * 0.5f;
    }

    // Update all active stars
    long updateStartTime = System.nanoTime();

    for (int i = 0; i < activeStarCount; i++) {
      starPool[i].update(deltaMs, currentSpeed, axis, direction);
    }

    // Remove dead stars (exceeded lifespan or out of bounds)
    removeDeadStars();
    long updateEndTime = System.nanoTime();
    totalUpdateTime += (updateEndTime - updateStartTime);

    // Clear all points first
    for (LXPoint p : model.points) {
      colors[p.index] = 0;
    }

    // Now render each star as a sharp point
    long renderStartTime = System.nanoTime();
    float brightnessMult = (float)brightness.getValue();
    int starsRendered = 0;

    for (int i = 0; i < activeStarCount; i++) {
      Star star = starPool[i];
      // Only render stars that are inside the visible cube [0,1]
      if (star.x >= 0f && star.x <= 1f &&
          star.y >= 0f && star.y <= 1f &&
          star.z >= 0f && star.z <= 1f) {

        // Render the star using closest LED algorithm
        float starBrightness = star.getBrightness() * brightnessMult;
        renderStar(star.x, star.y, star.z, star.color, starBrightness, axis);
        starsRendered++;
      }
    }
    long renderEndTime = System.nanoTime();
    totalRenderTime += (renderEndTime - renderStartTime);
    
    frameCount++;
    
    // Print performance stats every 60 frames (roughly 1 second at 60fps)
    if (frameCount % 60 == 0) {
      double avgUpdateMs = (totalUpdateTime / (double)frameCount) / 1_000_000.0;
      double avgRenderMs = (totalRenderTime / (double)frameCount) / 1_000_000.0;
      double avgFindLEDMs = (totalFindLEDTime / (double)frameCount) / 1_000_000.0;
      double totalFrameMs = avgUpdateMs + avgRenderMs;
      double avgLEDsPerStar = totalStarsRendered > 0 ? (double)totalLEDsSearched / totalStarsRendered : 0;
      
      // Debug cube vs cylinder detection
      int cubeCount = 0, cylinderCount = 0;
      for (LXPoint p : allPoints) {
        if (isPointOnCube(p)) cubeCount++;
        else cylinderCount++;
      }
      
      LX.log(String.format(
        "Hyperspace Performance - Stars: %d (rendered: %d) | Update: %.2fms | Render: %.2fms (LED search: %.2fms, avg %.0f LEDs/star) | Total: %.2fms | FPS potential: %.0f | Cube LEDs: %d, Cylinder LEDs: %d",
        activeStarCount, starsRendered, avgUpdateMs, avgRenderMs, avgFindLEDMs, avgLEDsPerStar, totalFrameMs, 1000.0 / totalFrameMs, cubeCount, cylinderCount
      ));
      
      // Reset counters
      frameCount = 0;
      totalUpdateTime = 0;
      totalRenderTime = 0;
      totalFindLEDTime = 0;
      totalLEDsSearched = 0;
      totalStarsRendered = 0;
    }
  }
  
  // Helper method to convert normalized coordinates to model space
  private float[] convertToModelSpace(float x, float y, float z) {
    return new float[] {
      x * (model.xMax - model.xMin) + model.xMin,
      y * (model.yMax - model.yMin) + model.yMin,
      z * (model.zMax - model.zMin) + model.zMin
    };
  }
  
  
  // Helper method to track LED search performance
  private void trackLEDSearchPerformance(long startTime, int ledCount) {
    long endTime = System.nanoTime();
    totalFindLEDTime += (endTime - startTime);
    totalLEDsSearched += ledCount;
    totalStarsRendered++;
  }
  
  // Render star using closest LED algorithm
  private void renderStar(float x, float y, float z, int color, float brightness, int motionAxis) {
    float[] modelCoords = convertToModelSpace(x, y, z);
    float starX = modelCoords[0], starY = modelCoords[1], starZ = modelCoords[2];

    long ledSearchStart = System.nanoTime();
    float minDistanceSquared = Float.MAX_VALUE;
    int closestIndex = -1;

    // Use spatial grid to find nearby LEDs only
    // Search radius of 30 units should be enough to find closest LED
    List<LXPoint> nearbyLEDs = ledGrid.getNearbyLEDs(starX, starY, starZ, 30.0f);

    // Find closest LED among nearby candidates (filtered by surface toggles)
    for (LXPoint p : nearbyLEDs) {
      boolean isCubeLED = isPointOnCube(p);

      // Skip this LED if its surface is disabled
      if (isCubeLED && !renderToCube.isOn()) continue;
      if (!isCubeLED && !renderToCylinder.isOn()) continue;

      // For cube LEDs, skip faces perpendicular to motion axis
      if (isCubeLED) {
        Integer faceIndex = cubeFaceMap.get(p.index);
        if (faceIndex != null && shouldSkipCubeFace(faceIndex, motionAxis)) {
          continue;
        }
      }

      float dx = p.x - starX;
      float dy = p.y - starY;
      float dz = p.z - starZ;
      float distanceSquared = dx*dx + dy*dy + dz*dz;

      if (distanceSquared < minDistanceSquared) {
        minDistanceSquared = distanceSquared;
        closestIndex = p.index;
      }
    }
    trackLEDSearchPerformance(ledSearchStart, nearbyLEDs.size());

    // Always render to the closest LED (both exterior and interior for cube only)
    if (closestIndex >= 0) {
      int finalColor = LXColor.scaleBrightness(color, brightness);
      colors[closestIndex] = LXColor.blend(colors[closestIndex], finalColor, LXColor.Blend.ADD);

      // Also render to corresponding exterior/interior LED (cube only)
      boolean isCubeLED = isPointOnCube(allPoints[closestIndex]);
      if (isCubeLED) {
        int correspondingIndex = findCorrespondingLED(closestIndex, true);
        if (correspondingIndex >= 0) {
          colors[correspondingIndex] = LXColor.blend(colors[correspondingIndex], finalColor, LXColor.Blend.ADD);
        }
      }
    }
  }
  
  // Helper method to determine if a point is on the cube (vs cylinder) - optimized
  private boolean isPointOnCube(LXPoint p) {
    return cubeLedsMap.getOrDefault(p.index, false);
  }
  
  // Pre-compute LED mappings once for performance
  private void precomputeLEDMappings() {
    if (Apotheneum.cube == null) return;
    
    LX.log("Pre-computing LED mappings...");
    
    // Mark all cube LEDs
    markCubeLEDs(Apotheneum.cube.exterior());
    if (Apotheneum.cube.interior() != null) {
      markCubeLEDs(Apotheneum.cube.interior());
    }

    // Mark which face each cube LED belongs to
    markCubeFaces(Apotheneum.cube.exterior);
    if (Apotheneum.cube.interior != null) {
      markCubeFaces(Apotheneum.cube.interior);
    }
    
    // Build exterior <-> interior mapping
    if (Apotheneum.cube.interior() != null) {
      buildExteriorInteriorMapping();
    }
    
    LX.log(String.format("LED mappings complete: %d cube LEDs, %d exterior-interior pairs", 
      cubeLedsMap.size(), exteriorToInteriorMap.size()));
  }
  
  private void markCubeLEDs(Apotheneum.Orientation orientation) {
    for (int y = 0; y < orientation.height(); y++) {
      for (int x = 0; x < orientation.width(); x++) {
        int ledIndex = orientation.point(x, y).index;
        cubeLedsMap.put(ledIndex, true);
      }
    }
  }

  private void markCubeFaces(Apotheneum.Cube.Orientation orientation) {
    // Mark each face's LEDs with their face index (0=front, 1=right, 2=back, 3=left)
    markFaceLEDs(orientation.front, 0);
    markFaceLEDs(orientation.right, 1);
    markFaceLEDs(orientation.back, 2);
    markFaceLEDs(orientation.left, 3);
  }

  private void markFaceLEDs(Apotheneum.Cube.Face face, int faceIndex) {
    for (LXPoint p : face.model.points) {
      cubeFaceMap.put(p.index, faceIndex);
    }
  }

  // Check if a cube face should be skipped based on motion axis
  // X axis (0): skip left (3) and right (1) faces
  // Z axis (2): skip front (0) and back (2) faces
  // Y axis (1): render all faces
  private boolean shouldSkipCubeFace(int faceIndex, int motionAxis) {
    if (motionAxis == 0) { // X axis - skip left and right
      return faceIndex == 1 || faceIndex == 3;
    } else if (motionAxis == 2) { // Z axis - skip front and back
      return faceIndex == 0 || faceIndex == 2;
    }
    return false; // Y axis - render all faces
  }
  
  private void buildExteriorInteriorMapping() {
    Apotheneum.Orientation exterior = Apotheneum.cube.exterior();
    Apotheneum.Orientation interior = Apotheneum.cube.interior();
    
    for (int y = 0; y < exterior.height(); y++) {
      for (int x = 0; x < exterior.width(); x++) {
        if (x < interior.width() && y < interior.height()) {
          int exteriorIndex = exterior.point(x, y).index;
          int interiorIndex = interior.point(x, y).index;
          
          // Build bidirectional mapping
          exteriorToInteriorMap.put(exteriorIndex, interiorIndex);
          exteriorToInteriorMap.put(interiorIndex, exteriorIndex);
        }
      }
    }
  }
  
  // Helper method to find corresponding exterior/interior LED for cube faces (optimized)
  private int findCorrespondingLED(int ledIndex, boolean isCube) {
    if (!isCube) return -1;
    return exteriorToInteriorMap.getOrDefault(ledIndex, -1);
  }
  
  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Hyperspace pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 4);
    
    // Movement controls
    addColumn(uiDevice, "Movement",
      newKnob(pattern.speed),
      newKnob(pattern.density),
      newKnob(pattern.duration)).setChildSpacing(6);
    
    addVerticalBreak(ui, uiDevice);
    
    // Motion controls
    addColumn(uiDevice, "Motion",
      newKnob(pattern.motionAxis),
      newKnob(pattern.motionDirection)).setChildSpacing(6);
    
    addVerticalBreak(ui, uiDevice);
    
    // Visual controls
    addColumn(uiDevice, "Visual",
      newKnob(pattern.brightness),
      newKnob(pattern.starSize)).setChildSpacing(6);
      
    addVerticalBreak(ui, uiDevice);
    
    // Surface controls
    addColumn(uiDevice, "Surfaces",
      newButton(pattern.renderToCube).setTriggerable(true),
      newButton(pattern.renderToCylinder).setTriggerable(true)).setChildSpacing(6);
      
    addVerticalBreak(ui, uiDevice);
    
    // Additional controls
    addColumn(uiDevice, "Effects",
      newButton(pattern.pulse).setTriggerable(true),
      newButton(pattern.clearStars).setTriggerable(true)).setChildSpacing(6);
  }
}