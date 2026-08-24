/**
 * Copyright 2025- Dan Oved
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Dan Oved
 */

package apotheneum.doved.patterns;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@LXCategory("Apotheneum/doved")
@LXComponent.Name("Boids")
public class Boids extends ApotheneumPattern implements UIDeviceControls<Boids> {

  // Dynamic coordinate system based on shape selection
  // Extend logical space beyond physical boundaries to prevent edge bunching
  private int getRingHeight() {
    int physicalHeight = shape.getValuei() == 0 ? Apotheneum.GRID_HEIGHT : Apotheneum.CYLINDER_HEIGHT;
    return physicalHeight + 20; // Add 10 pixels buffer above and below
  }

  private int getPhysicalRingHeight() {
    return shape.getValuei() == 0 ? Apotheneum.GRID_HEIGHT : Apotheneum.CYLINDER_HEIGHT;
  }

  private int getRingLength() {
    return shape.getValuei() == 0 ? Apotheneum.Cube.Ring.LENGTH : Apotheneum.Cylinder.Ring.LENGTH;
  }

  // Parameters - optimized for tight flocking behavior with higher capacity
  public final CompoundDiscreteParameter maxFlock =
    new CompoundDiscreteParameter("Max Flock", 100, 5, 300)
    .setDescription("Maximum number of boids that can exist in the flock");

  public final CompoundParameter flockDensity =
    new CompoundParameter("Density", 50, 0, 100)
    .setDescription("Percentage of max flock that is active (0-100%)");

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 3.0, 0.5, 4.0)
    .setDescription("Base movement speed");

  public final CompoundParameter separation =
    new CompoundParameter("Separation", 1.5, 0, 4)
    .setDescription("Strength of separation force (avoid crowding)");

  public final CompoundParameter alignment =
    new CompoundParameter("Alignment", 1.8, 0, 3)
    .setDescription("Strength of alignment force (match heading)");

  public final CompoundParameter cohesion =
    new CompoundParameter("Cohesion", 1.0, 0, 3)
    .setDescription("Strength of cohesion force (move toward center)");

  public final CompoundParameter neighborRadius =
    new CompoundParameter("Radius", 15, 5, 30)
    .setDescription("Distance to consider other boids as neighbors");

  public final CompoundParameter turbulence =
    new CompoundParameter("Turbulence", 0.2, 0, 1)
    .setDescription("Random force for organic movement");

  public final CompoundParameter blur =
    new CompoundParameter("Blur", 0.85, 0, 0.99)
    .setDescription("Motion blur amount (0=none, 0.99=max)");

  public final CompoundParameter brightness =
    new CompoundParameter("Brightness", 1.5, 0.5, 4.0)
    .setDescription("Brightness multiplier for boids (1=normal, 2=double, etc)");

  public final DiscreteParameter shape =
    new DiscreteParameter("Shape", new String[]{"Cube", "Cylinder"}, 0)
    .setDescription("Which shape to render on");


  // Simple 2D Boid class
  private class Boid {
    float x, y;              // Position in ring coordinates
    float velocityX, velocityY;  // Velocity
    float maxSpeed = 15.0f;
    float maxForce = 0.8f;

    // Individual speed variation for more organic movement
    float currentSpeedMultiplier = 1.0f;  // Current speed multiplier (0.7 to 1.3)
    float targetSpeedMultiplier = 1.0f;   // Target speed to interpolate towards
    double lastSpeedTargetUpdate = 0;      // Time when we last picked a new target speed
    float speedInterpolationRate = 0.5f;  // How quickly we interpolate to target (0-1, higher = faster)


    Boid() {
      // Random initial position across the extended logical space
      x = (float)Math.random() * getRingLength();
      y = LXUtils.randomf(0, getRingHeight()); // Use full extended height

      // Random initial velocity for natural movement
      float initAngle = (float)(Math.random() * 2 * Math.PI);
      velocityX = (float)Math.cos(initAngle) * LXUtils.randomf(0.5f, 2.0f);
      velocityY = (float)Math.sin(initAngle) * LXUtils.randomf(0.5f, 2.0f);

      // Initialize individual speed variation for organic movement
      currentSpeedMultiplier = LXUtils.randomf(0.85f, 1.15f);
      targetSpeedMultiplier = currentSpeedMultiplier;
      lastSpeedTargetUpdate = 0;
    }

    void updateWithSpatialGrid(double deltaMs, SpatialGrid spatialGrid) {
      // Update individual speed variation
      updateSpeedVariation(Boids.this.currentTime);

      float accelerationX = 0;
      float accelerationY = 0;

      // Get nearby boids from spatial grid instead of checking all boids
      float searchRadius = neighborRadius.getValuef() * 2.0f;
      List<Boid> nearbyBoids = spatialGrid.getNearbyBoids(x, y, searchRadius);

      // Classic Boids: Apply the three fundamental forces to all boids
      float[] sep = separateFromNearby(nearbyBoids);
      float[] ali = alignWithNearby(nearbyBoids);
      float[] coh = cohesionWithNearby(nearbyBoids);

      accelerationX += sep[0] * separation.getValuef();
      accelerationY += sep[1] * separation.getValuef();
      accelerationX += ali[0] * alignment.getValuef();
      accelerationY += ali[1] * alignment.getValuef();
      accelerationX += coh[0] * cohesion.getValuef();
      accelerationY += coh[1] * cohesion.getValuef();

      // Add turbulence with extra vertical bias
      if (turbulence.getValuef() > 0) {
        accelerationX += (Math.random() - 0.5) * turbulence.getValuef() * 1.5f;
        accelerationY += (Math.random() - 0.5) * turbulence.getValuef() * 2.0f; // More vertical turbulence
      }

      // Door avoidance as acceleration force (before velocity update)
      if (isInDoorArea(x, y)) {
        accelerationY += maxForce * 0.5f; // Apply upward force to avoid doors
      }

      // Clamp total acceleration to prevent jittery movement at extreme parameter settings
      float totalAcceleration = (float)Math.sqrt(accelerationX * accelerationX + accelerationY * accelerationY);
      float maxAcceleration = maxForce * 3.0f; // Allow up to 3x maxForce for total acceleration
      if (totalAcceleration > maxAcceleration) {
        accelerationX = (accelerationX / totalAcceleration) * maxAcceleration;
        accelerationY = (accelerationY / totalAcceleration) * maxAcceleration;
      }

      // Update velocity
      velocityX += accelerationX;
      velocityY += accelerationY;

      // Limit speed (use base maxSpeed for consistent behavior)
      float speed = (float)Math.sqrt(velocityX * velocityX + velocityY * velocityY);
      if (speed > maxSpeed) {
        velocityX = (velocityX / speed) * maxSpeed;
        velocityY = (velocityY / speed) * maxSpeed;
      }

      // Update position (apply both global speed parameter and individual speed variation)
      float deltaSeconds = (float)(deltaMs * 0.001);
      float speedMultiplier = Boids.this.speed.getValuef() * currentSpeedMultiplier;
      x += velocityX * deltaSeconds * speedMultiplier;
      y += velocityY * deltaSeconds * speedMultiplier;

      // Handle boundaries - wrap X, keep Y within extended bounds
      x = (x + getRingLength()) % getRingLength();

      // Keep Y within extended bounds with gentle redirection
      if (y < 0) {
        y = 0;
        velocityY = Math.abs(velocityY) * 0.5f;
      }
      if (y >= getRingHeight()) {
        y = getRingHeight() - 1;
        velocityY = -Math.abs(velocityY) * 0.5f;
      }
    }

    void updateSpeedVariation(double currentTime) {
      // Pick a new target speed every 2-5 seconds
      double timeSinceLastTarget = currentTime - lastSpeedTargetUpdate;
      if (timeSinceLastTarget > LXUtils.randomf(2000, 5000)) {
        // Choose a new target speed within range [0.7, 1.3]
        targetSpeedMultiplier = LXUtils.randomf(0.7f, 1.3f);
        lastSpeedTargetUpdate = currentTime;

        // Vary the interpolation rate for different boids (some change speed faster than others)
        speedInterpolationRate = LXUtils.randomf(0.3f, 0.8f);
      }

      // Smoothly interpolate current speed towards target
      float speedDiff = targetSpeedMultiplier - currentSpeedMultiplier;
      float maxChange = speedInterpolationRate * 0.01f; // Small increments for smooth transitions

      if (Math.abs(speedDiff) > maxChange) {
        // Move towards target by maxChange amount
        currentSpeedMultiplier += Math.signum(speedDiff) * maxChange;
      } else {
        // Close enough to target, snap to it
        currentSpeedMultiplier = targetSpeedMultiplier;
      }

      // Ensure we stay within bounds
      currentSpeedMultiplier = Math.max(0.7f, Math.min(1.3f, currentSpeedMultiplier));
    }

    // Helper method for proper wrap-around distance calculation
    float[] getWrappedOffset(float x1, float y1, float x2, float y2) {
      float dx = x1 - x2;
      float dy = y1 - y2;

      // Handle X wrapping for ring topology - choose shortest path
      if (Math.abs(dx) > getRingLength() / 2.0f) {
        if (dx > 0) {
          dx = dx - getRingLength();
        } else {
          dx = dx + getRingLength();
        }
      }

      // Y axis is linear with extended bounds - no wrapping

      return new float[]{dx, dy};
    }

    float[] separateFromNearby(List<Boid> nearbyBoids) {
      float desiredSeparation = 6.0f; // Increased separation to prevent clustering
      float steerX = 0, steerY = 0;
      int count = 0;

      for (Boid other : nearbyBoids) {
        if (other == this) continue;

        float[] offset = getWrappedOffset(x, y, other.x, other.y);
        float dx = offset[0];
        float dy = offset[1];

        float distance = (float)Math.sqrt(dx * dx + dy * dy);
        if (distance > 0 && distance < desiredSeparation) {
          dx /= distance;
          dy /= distance;
          dx /= distance; // Weight by distance
          dy /= distance;
          steerX += dx;
          steerY += dy;
          count++;
        }
      }

      if (count > 0) {
        steerX /= count;
        steerY /= count;

        float mag = (float)Math.sqrt(steerX * steerX + steerY * steerY);
        if (mag > 0) {
          steerX = (steerX / mag) * maxSpeed - velocityX;
          steerY = (steerY / mag) * maxSpeed - velocityY;

          float steerMag = (float)Math.sqrt(steerX * steerX + steerY * steerY);
          if (steerMag > maxForce) {
            steerX = (steerX / steerMag) * maxForce;
            steerY = (steerY / steerMag) * maxForce;
          }
        }
      }

      return new float[]{steerX, steerY};
    }

    float[] alignWithNearby(List<Boid> nearbyBoids) {
      float neighborDist = neighborRadius.getValuef();
      float sumX = 0, sumY = 0;
      int count = 0;

      for (Boid other : nearbyBoids) {
        if (other == this) continue;

        float[] offset = getWrappedOffset(x, y, other.x, other.y);
        float dx = offset[0];
        float dy = offset[1];

        float distance = (float)Math.sqrt(dx * dx + dy * dy);
        if (distance > 0 && distance < neighborDist) {
          sumX += other.velocityX;
          sumY += other.velocityY;
          count++;
        }
      }

      if (count > 0) {
        sumX /= count;
        sumY /= count;

        float mag = (float)Math.sqrt(sumX * sumX + sumY * sumY);
        if (mag > 0) {
          sumX = (sumX / mag) * maxSpeed;
          sumY = (sumY / mag) * maxSpeed;

          float steerX = sumX - velocityX;
          float steerY = sumY - velocityY;

          float steerMag = (float)Math.sqrt(steerX * steerX + steerY * steerY);
          if (steerMag > maxForce) {
            steerX = (steerX / steerMag) * maxForce;
            steerY = (steerY / steerMag) * maxForce;
          }

          return new float[]{steerX, steerY};
        }
      }

      return new float[]{0, 0};
    }

    float[] cohesionWithNearby(List<Boid> nearbyBoids) {
      float neighborDist = neighborRadius.getValuef();
      float sumX = 0, sumY = 0;
      int count = 0;

      for (Boid other : nearbyBoids) {
        if (other == this) continue;

        float[] offset = getWrappedOffset(x, y, other.x, other.y);
        float distance = (float)Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);

        if (distance > 0 && distance < neighborDist) {
          sumX += other.x;
          sumY += other.y;
          count++;
        }
      }

      if (count > 0) {
        sumX /= count;
        sumY /= count;

        return seek(sumX, sumY);
      }

      return new float[]{0, 0};
    }


    float[] seek(float targetX, float targetY) {
      float[] offset = getWrappedOffset(targetX, targetY, x, y);
      float dx = offset[0];
      float dy = offset[1];

      float distance = (float)Math.sqrt(dx * dx + dy * dy);
      if (distance > 0) {
        dx = (dx / distance) * maxSpeed;
        dy = (dy / distance) * maxSpeed;

        float steerX = dx - velocityX;
        float steerY = dy - velocityY;

        float steerMag = (float)Math.sqrt(steerX * steerX + steerY * steerY);
        if (steerMag > maxForce) {
          steerX = (steerX / steerMag) * maxForce;
          steerY = (steerY / steerMag) * maxForce;
        }

        return new float[]{steerX, steerY};
      }

      return new float[]{0, 0};
    }
  }

  // Spatial grid for performance optimization
  private class SpatialGrid {
    private final float cellSize;
    private final int gridWidth;
    private final int gridHeight;
    private final Map<Long, List<Boid>> grid;

    public SpatialGrid(float cellSize) {
      this.cellSize = cellSize;
      this.gridWidth = (int) Math.ceil(getRingLength() / cellSize);
      this.gridHeight = (int) Math.ceil(getRingHeight() / cellSize);
      this.grid = new HashMap<>();
    }

    public void clear() {
      for (List<Boid> cell : grid.values()) {
        cell.clear();
      }
    }

    public void addBoid(Boid boid) {
      long key = getCellKey(boid.x, boid.y);
      List<Boid> cell = grid.computeIfAbsent(key, k -> new ArrayList<>());
      cell.add(boid);
    }

    public List<Boid> getNearbyBoids(float x, float y, float radius) {
      List<Boid> nearbyBoids = new ArrayList<>();

      int minGridX = (int) Math.floor((x - radius) / cellSize);
      int maxGridX = (int) Math.ceil((x + radius) / cellSize);
      int minGridY = Math.max(0, (int) Math.floor((y - radius) / cellSize));
      int maxGridY = Math.min(gridHeight - 1, (int) Math.ceil((y + radius) / cellSize));

      for (int gx = minGridX; gx <= maxGridX; gx++) {
        for (int gy = minGridY; gy <= maxGridY; gy++) {
          // Handle X-axis wrapping for ring coordinates
          int wrappedGx = ((gx % gridWidth) + gridWidth) % gridWidth;
          long key = getKey(wrappedGx, gy);

          List<Boid> cell = grid.get(key);
          if (cell != null) {
            nearbyBoids.addAll(cell);
          }
        }
      }

      return nearbyBoids;
    }

    private long getCellKey(float x, float y) {
      int gx = ((int) Math.floor(x / cellSize) % gridWidth + gridWidth) % gridWidth;
      int gy = Math.max(0, Math.min(gridHeight - 1, (int) Math.floor(y / cellSize)));
      return getKey(gx, gy);
    }

    private long getKey(int gx, int gy) {
      return ((long) gx << 32) | (gy & 0xffffffffL);
    }
  }

  // Boid management
  private List<Boid> boids = new ArrayList<>();
  private int activeBoidCount = 0;  // Number of boids currently active based on density
  private double currentTime = 0;
  private SpatialGrid spatialGrid;

  public Boids(LX lx) {
    super(lx);
    addParameter("maxFlock", this.maxFlock);
    addParameter("flockDensity", this.flockDensity);
    addParameter("speed", this.speed);
    addParameter("separation", this.separation);
    addParameter("alignment", this.alignment);
    addParameter("cohesion", this.cohesion);
    addParameter("neighborRadius", this.neighborRadius);
    addParameter("turbulence", this.turbulence);
    addParameter("blur", this.blur);
    addParameter("brightness", this.brightness);
    addParameter("shape", this.shape);

    // Initialize spatial grid with cell size based on neighbor radius
    // Use the initial neighborRadius value to set up grid
    initializeSpatialGrid();
    updateBoidCount();
    updateActiveBoidCount();
  }

  private void initializeSpatialGrid() {
    // Use smaller cell size to ensure better spatial resolution for leader detection
    float cellSize = Math.max(4.0f, neighborRadius.getValuef() * 0.75f);
    spatialGrid = new SpatialGrid(cellSize);
  }

  private void updateBoidCount() {
    int targetCount = maxFlock.getValuei();
    while (boids.size() < targetCount) {
      boids.add(new Boid());
    }
    while (boids.size() > targetCount) {
      boids.remove(boids.size() - 1);
    }
    // After updating max count, also update active count
    updateActiveBoidCount();
  }

  private void updateActiveBoidCount() {
    // Calculate how many boids should be active based on density percentage
    float densityPercent = flockDensity.getValuef() / 100.0f;
    activeBoidCount = Math.round(maxFlock.getValuei() * densityPercent);
    // Ensure we don't exceed actual boid list size
    activeBoidCount = Math.min(activeBoidCount, boids.size());
  }


  @Override
  public void onParameterChanged(heronarts.lx.parameter.LXParameter p) {
    super.onParameterChanged(p);
    if (p == maxFlock) {
      updateBoidCount();
    } else if (p == neighborRadius) {
      // Reinitialize spatial grid when neighbor radius changes
      initializeSpatialGrid();
    } else if (p == shape) {
      // Regenerate boids when switching shapes for immediate visual feedback
      for (Boid boid : boids) {
        boid.x = boid.x * getRingLength() / (shape.getValuei() == 0 ? Apotheneum.Cylinder.Ring.LENGTH : Apotheneum.Cube.Ring.LENGTH);
        // Scale Y position maintaining the extended space proportion
        float oldExtendedHeight = (shape.getValuei() == 0 ? Apotheneum.CYLINDER_HEIGHT : Apotheneum.GRID_HEIGHT) + 20;
        boid.y = boid.y * getRingHeight() / oldExtendedHeight;
      }
      // Also reinitialize grid for new dimensions
      initializeSpatialGrid();
    }
    // Note: flockDensity changes are now handled in render() method for real-time response
  }

  @Override
  protected void render(double deltaMs) {
    // Apply motion blur decay based on blur parameter
    // When blur is 0, clear completely. When blur is 0.99, retain 99% of previous frame
    float blurAmount = blur.getValuef();

    // Debug: Print blur value occasionally
//    if ((int)(currentTime / 1000) % 5 == 0 && (int)(currentTime % 1000) < 50) {
//      System.out.println("Blur amount: " + blurAmount);
//    }

    if (blurAmount > 0.01f) {
      // Apply decay to create motion blur
      for (int i = 0; i < colors.length; i++) {
        int oldColor = colors[i];
        colors[i] = LXColor.scaleBrightness(colors[i], blurAmount);
        // Debug: Check if scaling is working on first few pixels
//        if (i < 3 && oldColor != colors[i] && oldColor != LXColor.BLACK) {
//          System.out.println("Pixel " + i + " scaled from " + Integer.toHexString(oldColor) + " to " + Integer.toHexString(colors[i]));
//        }
      }
    } else {
      // No blur - clear the frame completely
      setApotheneumColor(LXColor.BLACK);
    }

    currentTime += deltaMs;

    // Recalculate active boid count every frame to respond to density changes immediately
    updateActiveBoidCount();

    // Clear and populate spatial grid with only active boid positions
    spatialGrid.clear();
    for (int i = 0; i < activeBoidCount; i++) {
      spatialGrid.addBoid(boids.get(i));
    }

    // Update only active boids using spatial grid for neighbor finding
    for (int i = 0; i < activeBoidCount; i++) {
      boids.get(i).updateWithSpatialGrid(deltaMs, spatialGrid);
    }

    // Render only active boids
    for (int i = 0; i < activeBoidCount; i++) {
      renderBoid(boids.get(i));
    }
  }

  private void renderBoid(Boid boid) {
    // Render with anti-aliasing across neighboring pixels
    // This reduces the jittery appearance by distributing the boid across multiple pixels
    int baseX = (int) Math.floor(boid.x);
    int baseY = (int) Math.floor(boid.y);

    // Get fractional parts for interpolation
    float fracX = boid.x - baseX;
    float fracY = boid.y - baseY;

    // Calculate brightness for each of the 4 neighboring pixels using bilinear interpolation
    // Top-left pixel
    float brightness00 = (1 - fracX) * (1 - fracY);
    // Top-right pixel
    float brightness10 = fracX * (1 - fracY);
    // Bottom-left pixel
    float brightness01 = (1 - fracX) * fracY;
    // Bottom-right pixel
    float brightness11 = fracX * fracY;

    // Render to 4 neighboring pixels with interpolated brightness (all white, varying intensity)
    setPixelOnShapeWithBrightness(baseX, baseY, brightness00);
    setPixelOnShapeWithBrightness(baseX + 1, baseY, brightness10);
    setPixelOnShapeWithBrightness(baseX, baseY + 1, brightness01);
    setPixelOnShapeWithBrightness(baseX + 1, baseY + 1, brightness11);
  }

  private void setPixelOnShapeWithBrightness(int ringX, int ringY, float brightness) {
    // Skip if brightness is too low to be visible
    if (brightness < 0.01f) return;

    // Only render boids within the physical LED boundaries
    if (ringY < 0 || ringY >= getPhysicalRingHeight()) {
      return;
    }

    // Apply brightness multiplier from parameter
    float adjustedBrightness = brightness * this.brightness.getValuef();

    // Create white color with scaled brightness (clamped to 100)
    int color = LXColor.gray(Math.min(100f, adjustedBrightness * 100));

    if (shape.getValuei() == 0) {
      // Cube rendering
      setPixelOnRingAdditive(Apotheneum.cube.exterior.ring(ringY), ringX, color);
      setPixelOnRingAdditive(Apotheneum.cube.interior.ring(ringY), ringX, color);
    } else {
      // Cylinder rendering
      setPixelOnRingAdditive(Apotheneum.cylinder.exterior.ring(ringY), ringX, color);
      setPixelOnRingAdditive(Apotheneum.cylinder.interior.ring(ringY), ringX, color);
    }
  }

  private void setPixelOnShape(float ringX, float ringY, int color) {
    int ringIndex = Math.round(ringY);
    int pointIndex = Math.round(ringX);

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

  private void setPixelOnRing(Apotheneum.Ring ring, int pointIndex, int color) {
    if (ring != null && ring.points.length > 0) {
      int wrappedIndex = ((pointIndex % ring.points.length) + ring.points.length) % ring.points.length;
      colors[ring.points[wrappedIndex].index] = color;
    }
  }

  private void setPixelOnRingAdditive(Apotheneum.Ring ring, int pointIndex, int color) {
    if (ring != null && ring.points.length > 0) {
      int wrappedIndex = ((pointIndex % ring.points.length) + ring.points.length) % ring.points.length;
      int pixelIndex = ring.points[wrappedIndex].index;

      // Additively blend the new color with existing color
      int existingColor = colors[pixelIndex];
      float existingBrightness = LXColor.luminosity(existingColor);
      float newBrightness = LXColor.luminosity(color);

      // Add brightnesses and clamp to 100
      float combinedBrightness = Math.min(100f, existingBrightness + newBrightness);

      // Set as white with combined brightness
      colors[pixelIndex] = LXColor.gray(combinedBrightness);
    }
  }

  private boolean isInDoorArea(float ringX, float ringY) {
    int ringIndex = Math.round(ringY);
    int physicalHeight = getPhysicalRingHeight();
    int ringLength = getRingLength();

    if (ringIndex < 0 || ringIndex >= physicalHeight) {
      return false;
    }

    // Check if at bottom where doors are
    if (ringIndex >= physicalHeight - Apotheneum.DOOR_HEIGHT) {
      int ringPos = Math.round(ringX);
      int wrappedPos = ((ringPos % ringLength) + ringLength) % ringLength;

      if (shape.getValuei() == 0) {
        // Cube door detection
        for (int face = 0; face < 4; face++) {
          int faceStart = face * Apotheneum.GRID_WIDTH;
          int doorStart = faceStart + Apotheneum.Cube.DOOR_START_COLUMN;
          int doorEnd = doorStart + Apotheneum.DOOR_WIDTH - 1;

          if (wrappedPos >= doorStart && wrappedPos <= doorEnd) {
            return true;
          }
        }
      } else {
        // Cylinder door detection
        int doorStart = Apotheneum.Cylinder.DOOR_START_COLUMN;
        int doorEnd = doorStart + Apotheneum.DOOR_WIDTH - 1;
        int posInCycle = wrappedPos % (Apotheneum.Cylinder.Ring.LENGTH / 4);
        if (posInCycle >= doorStart && posInCycle <= doorEnd) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Boids boids) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    // Column 1: max flock, shape
    addColumn(uiDevice, "Config",
      newIntegerBox(boids.maxFlock),
      newDropMenu(boids.shape)
    );

    addVerticalBreak(ui, uiDevice);

    // Column 2: density, cohesion
    addColumn(uiDevice, "Group",
      newKnob(boids.flockDensity),
      newKnob(boids.cohesion)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Column 3: speed, separation
    addColumn(uiDevice, "Motion",
      newKnob(boids.speed),
      newKnob(boids.separation)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Column 4: turbulence, alignment
    addColumn(uiDevice, "Behavior",
      newKnob(boids.turbulence),
      newKnob(boids.alignment)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Column 5: blur, brightness, neighbor radius
    addColumn(uiDevice, "Effects",
      newKnob(boids.blur),
      newKnob(boids.brightness),
      newKnob(boids.neighborRadius)
    ).setChildSpacing(6);
  }
}