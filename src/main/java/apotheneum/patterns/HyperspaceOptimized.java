package apotheneum.patterns;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@LXCategory("Apotheneum")
@LXComponent.Name("Hyperspace-Optimized")
public class HyperspaceOptimized extends ApotheneumPattern implements UIDeviceControls<HyperspaceOptimized> {

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

  // LED Spatial grid for efficient nearest-neighbor search.
  // Cell size is matched to the search radius used at query time (see
  // LED_SEARCH_RADIUS) so a query only has to visit a 3x3x3 block of cells
  // instead of a 7x7x7 block - the mismatch between cell size and search
  // radius was the single largest cost in the original implementation.
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

  // Search radius for the closest-LED lookup. The spatial grid's cell size is
  // matched to this value (see constructor) to minimize the number of grid
  // cells visited per star.
  private static final float LED_SEARCH_RADIUS = 30.0f;

  private final Star[] starPool = new Star[MAX_STARS];
  private int activeStarCount = 0;
  private LXPoint[] allPoints; // Cache of all LED points for targeting
  private LEDSpatialGrid ledGrid; // Spatial grid for efficient LED search

  // Pre-computed LED mappings for performance, keyed directly by LXPoint.index.
  // Plain primitive arrays instead of HashMap<Integer,...> avoid boxing and
  // hashing on every candidate LED for every star, every frame - this lookup
  // sits directly in the hottest path in the pattern.
  private boolean[] isCubeLed;
  private int[] cubeFace; // -1 = not a cube LED, otherwise 0=front, 1=right, 2=back, 3=left
  private int[] exteriorToInterior; // -1 = no mapping

  // Performance monitoring (frame-level only; per-star instrumentation was
  // itself a measurable cost on the hot path and has been removed)
  private long frameCount = 0;
  private long totalUpdateTime = 0;
  private long totalRenderTime = 0;
  private int cachedCubeCount = 0;
  private int cachedCylinderCount = 0;

  public final CompoundParameter speed = new CompoundParameter("Speed", 0.5, 0.1, 50.0)
    .setDescription("Speed of hyperspace travel");

  public final CompoundParameter density = new CompoundParameter("Density", 100, 10, 2000)
    .setDescription("Stars spawned per second");

  public final CompoundParameter starSize = new CompoundParameter("Star Size", 0.1, 0.05, 0.3)
    .setDescription("Size of stars and trails");

  public final CompoundParameter duration = new CompoundParameter("Duration", 3000, 1000, 8000)
    .setDescription("How long stars live (milliseconds)");

  public final CompoundParameter brightness = new CompoundParameter("Bright", 1.0, 0.0, 1.0)
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

  public HyperspaceOptimized(LX lx) {
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

    // Cache all LED points for star targeting, build the spatial grid, and
    // precompute LED mappings - also redone in onModelChanged() below, since
    // all of this is derived from the model and goes stale if it changes.
    rebuildModelCaches();

    // Pre-allocate all star objects (object pool to avoid GC)
    double maxLifespan = duration.getValue();
    for (int i = 0; i < MAX_STARS; i++) {
      starPool[i] = new Star(maxLifespan, allPoints);
    }
  }

  @Override
  protected void onModelChanged(LXModel model) {
    // allPoints, ledGrid, and the isCubeLed/cubeFace/exteriorToInterior
    // mappings are all derived from the model and were previously only ever
    // built in the constructor - loading a different model left them stale,
    // risking wrong results or index-out-of-bounds if the point count changed.
    rebuildModelCaches();
  }

  private void rebuildModelCaches() {
    allPoints = model.points;

    // Spatial grid for efficient nearest-neighbor search. Cell size matches
    // LED_SEARCH_RADIUS so each query visits ~27 cells instead of ~343.
    ledGrid = new LEDSpatialGrid(allPoints, LED_SEARCH_RADIUS);

    precomputeLEDMappings();
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
    Arrays.fill(colors, 0);

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
      double totalFrameMs = avgUpdateMs + avgRenderMs;

      LX.log(String.format(
        "Hyperspace Performance - Stars: %d (rendered: %d) | Update: %.2fms | Render: %.2fms | Total: %.2fms | FPS potential: %.0f | Cube LEDs: %d, Cylinder LEDs: %d",
        activeStarCount, starsRendered, avgUpdateMs, avgRenderMs, totalFrameMs, 1000.0 / totalFrameMs, cachedCubeCount, cachedCylinderCount
      ));

      // Reset counters
      frameCount = 0;
      totalUpdateTime = 0;
      totalRenderTime = 0;
    }
  }

  // Render star using closest LED algorithm
  private void renderStar(float x, float y, float z, int color, float brightness, int motionAxis) {
    // Inline model-space conversion to avoid a per-star, per-frame array allocation
    float starX = x * (model.xMax - model.xMin) + model.xMin;
    float starY = y * (model.yMax - model.yMin) + model.yMin;
    float starZ = z * (model.zMax - model.zMin) + model.zMin;

    float minDistanceSquared = Float.MAX_VALUE;
    int closestIndex = -1;

    // Use spatial grid to find nearby LEDs only
    List<LXPoint> nearbyLEDs = ledGrid.getNearbyLEDs(starX, starY, starZ, LED_SEARCH_RADIUS);

    // Find closest LED among nearby candidates (filtered by surface toggles).
    // Indexed loop instead of for-each to avoid allocating an Iterator per star.
    int nearbyCount = nearbyLEDs.size();
    for (int i = 0; i < nearbyCount; i++) {
      LXPoint p = nearbyLEDs.get(i);
      boolean isCubeLED = isCubeLed[p.index];

      // Skip this LED if its surface is disabled
      if (isCubeLED && !renderToCube.isOn()) continue;
      if (!isCubeLED && !renderToCylinder.isOn()) continue;

      // For cube LEDs, skip faces perpendicular to motion axis
      if (isCubeLED) {
        int faceIndex = cubeFace[p.index];
        if (faceIndex >= 0 && shouldSkipCubeFace(faceIndex, motionAxis)) {
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

    // Always render to the closest LED (both exterior and interior for cube only)
    if (closestIndex >= 0) {
      int finalColor = LXColor.scaleBrightness(color, brightness);
      colors[closestIndex] = LXColor.blend(colors[closestIndex], finalColor, LXColor.Blend.ADD);

      // Also render to corresponding exterior/interior LED (cube only)
      if (isCubeLed[closestIndex]) {
        int correspondingIndex = exteriorToInterior[closestIndex];
        if (correspondingIndex >= 0) {
          colors[correspondingIndex] = LXColor.blend(colors[correspondingIndex], finalColor, LXColor.Blend.ADD);
        }
      }
    }
  }

  // Pre-compute LED mappings once for performance
  private void precomputeLEDMappings() {
    // Note: `colors` is not allocated yet at construction time (it is sized
    // once the pattern is attached to the engine) - size these arrays off
    // `allPoints` instead, which is available immediately and covers the
    // same index range.
    int n = allPoints.length;
    isCubeLed = new boolean[n];
    cubeFace = new int[n];
    Arrays.fill(cubeFace, -1);
    exteriorToInterior = new int[n];
    Arrays.fill(exteriorToInterior, -1);

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

    int cubeCount = 0;
    for (boolean b : isCubeLed) {
      if (b) cubeCount++;
    }
    cachedCubeCount = cubeCount;
    cachedCylinderCount = n - cubeCount;

    LX.log(String.format("LED mappings complete: %d cube LEDs, %d cylinder LEDs",
      cachedCubeCount, cachedCylinderCount));
  }

  private void markCubeLEDs(Apotheneum.Orientation orientation) {
    for (int y = 0; y < orientation.height(); y++) {
      for (int x = 0; x < orientation.width(); x++) {
        isCubeLed[orientation.point(x, y).index] = true;
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
      cubeFace[p.index] = faceIndex;
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
          exteriorToInterior[exteriorIndex] = interiorIndex;
          exteriorToInterior[interiorIndex] = exteriorIndex;
        }
      }
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, HyperspaceOptimized pattern) {
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
