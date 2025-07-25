package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.Apotheneum.Cylinder.Ring;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Perception Fracture")
public class PerceptionFracture extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.2, 0.0, 1.0)
    .setDescription("Speed of motion creating depth illusion");
  private final CompoundParameter depth = new CompoundParameter("Depth", 0.6, 0.0, 1.0)
    .setDescription("Intensity of depth illusion");
  private final CompoundParameter layers = new CompoundParameter("Layers", 4.0, 2.0, 8.0)
    .setDescription("Number of depth layers");
  private final CompoundParameter fracture = new CompoundParameter("Fracture", 0.3, 0.0, 1.0)
    .setDescription("Amount of perceptual fracturing");
  private final CompoundParameter gridSize = new CompoundParameter("Grid", 8.0, 4.0, 16.0)
    .setDescription("Base grid frequency");
  private final CompoundParameter parallax = new CompoundParameter("Parallax", 0.4, 0.0, 1.0)
    .setDescription("Parallax motion strength");
  private final CompoundParameter perspective = new CompoundParameter("Perspective", 0.5, 0.0, 1.0)
    .setDescription("Perspective distortion amount");
  private final CompoundParameter flicker = new CompoundParameter("Flicker", 0.2, 0.0, 1.0)
    .setDescription("Flicker intensity for depth");
  private final CompoundParameter convergence = new CompoundParameter("Converge", 0.3, 0.0, 1.0)
    .setDescription("Convergence point strength");
  private final BooleanParameter invertDepth = new BooleanParameter("Invert", false)
    .setDescription("Invert depth perception");

  private static final float[] HUES = { 280f, 300f, 320f, 340f, 20f };
  private float timeAccum = 0f;
  private float[] depthBuffer = new float[8];

  public PerceptionFracture(LX lx) {
    super(lx);
    addParameter("Speed", this.speed);
    addParameter("Depth", this.depth);
    addParameter("Layers", this.layers);
    addParameter("Fracture", this.fracture);
    addParameter("Grid", this.gridSize);
    addParameter("Parallax", this.parallax);
    addParameter("Perspective", this.perspective);
    addParameter("Flicker", this.flicker);
    addParameter("Converge", this.convergence);
    addParameter("Invert", this.invertDepth);
  }

  @Override
  protected void render(double deltaMs) {
    timeAccum += speed.getValuef() * (float)(deltaMs / 1000.0);
    
    int geoMode = 2; // Both geometries
    
    // Render cube if enabled
    if (geoMode == 0 || geoMode == 2) {
      Cube cube = Apotheneum.cube;
      if (cube != null) {
        for (Face face : cube.exterior.faces) {
          processFace(face);
        }
        if (cube.interior != null) {
          for (Face face : cube.interior.faces) {
            processFace(face);
          }
        }
      }
    }
    
    // Render cylinder if enabled
    if (geoMode == 1 || geoMode == 2) {
      Cylinder cylinder = Apotheneum.cylinder;
      if (cylinder != null) {
        processCylinder(cylinder);
      }
    }
  }

  private void processFace(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);
    
    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        float u = cx * invCols - 0.5f;
        float v = row.index * invRows - 0.5f;
        
        float depthValue = calculateDepthIllusion(u, v);
        colors[p.index] = generateDepthColor(depthValue, u, v);
      }
    }
  }
  
  private void processCylinder(Cylinder cylinder) {
    final float stretchFactor = 0.4f;
    
    Cylinder.Orientation[] faces = (cylinder.interior != null)
      ? new Cylinder.Orientation[]{ cylinder.exterior, cylinder.interior }
      : new Cylinder.Orientation[]{ cylinder.exterior };

    for (Cylinder.Orientation face : faces) {
      Ring[] rings = face.rings;
      int numRings = rings.length;

      for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
        Ring ring = rings[ringIndex];
        int pointsPerRing = ring.points.length;

        for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
          LXPoint p = ring.points[pointIndex];

          float theta = (float)(2 * Math.PI * pointIndex / pointsPerRing);
          float zNorm = (float)ringIndex / (numRings - 1);
          zNorm = Math.min(zNorm * stretchFactor, 1.0f);

          float u = theta / (2 * (float)Math.PI) - 0.5f;
          float v = zNorm - 0.5f;

          float depthValue = calculateDepthIllusion(u, v);
          colors[p.index] = generateDepthColor(depthValue, u, v);
        }
      }
    }
  }

  private float calculateDepthIllusion(float u, float v) {
    int numLayers = (int)layers.getValuef();
    float gridFreq = gridSize.getValuef();
    float parallaxAmount = parallax.getValuef();
    float perspectiveAmount = perspective.getValuef();
    float fractureAmount = fracture.getValuef();
    
    // Calculate convergence point
    float convergenceStrength = convergence.getValuef();
    float centerU = 0f;
    float centerV = 0f;
    float distFromCenter = (float)Math.sqrt((u - centerU) * (u - centerU) + (v - centerV) * (v - centerV));
    
    // Apply perspective distortion
    float perspectiveScale = 1.0f + perspectiveAmount * distFromCenter;
    float perspU = u * perspectiveScale;
    float perspV = v * perspectiveScale;
    
    float totalDepth = 0f;
    float totalWeight = 0f;
    
    for (int layer = 0; layer < numLayers; layer++) {
      float layerDepth = (float)layer / (numLayers - 1);
      float layerSpeed = 1.0f - layerDepth * 0.5f; // Farther layers move slower
      
      // Calculate parallax offset
      float parallaxU = perspU + layerDepth * parallaxAmount * (float)Math.sin(timeAccum * layerSpeed);
      float parallaxV = perspV + layerDepth * parallaxAmount * (float)Math.cos(timeAccum * layerSpeed);
      
      // Create grid pattern for this layer
      float layerFreq = gridFreq * (1.0f + layerDepth * 0.3f);
      float gridValue = (float)Math.sin(parallaxU * layerFreq * 2 * Math.PI) *
                       (float)Math.sin(parallaxV * layerFreq * 2 * Math.PI);
      
      // Apply fracturing
      float fracturePhase = timeAccum * layerSpeed + layerDepth * (float)Math.PI;
      float fractureValue = (float)Math.sin(fracturePhase) * fractureAmount;
      gridValue *= (1.0f + fractureValue);
      
      // Weight by depth and convergence
      float weight = 1.0f - layerDepth * 0.3f;
      weight *= (1.0f + convergenceStrength * (1.0f - distFromCenter));
      
      totalDepth += gridValue * weight * layerDepth;
      totalWeight += weight;
    }
    
    float depthValue = totalWeight > 0 ? totalDepth / totalWeight : 0f;
    
    // Apply flicker for depth enhancement
    float flickerValue = (float)Math.sin(timeAccum * 8 + u * 10 + v * 7) * flicker.getValuef();
    depthValue += flickerValue * 0.2f;
    
    // Apply motion amplification
    float motionAmplifier = 1.0f + depth.getValuef() * (float)Math.sin(timeAccum * 2);
    depthValue *= motionAmplifier;
    
    // Invert if needed
    if (invertDepth.getValueb()) {
      depthValue = -depthValue;
    }
    
    return Math.max(-1f, Math.min(1f, depthValue));
  }

  private int generateDepthColor(float depthValue, float u, float v) {
    // Map depth to brightness with illusion enhancement
    float brightness = 30f + 70f * (0.5f + 0.5f * depthValue);
    
    // Create depth-based color zones
    float normalizedDepth = (depthValue + 1f) * 0.5f;
    int hueIndex = (int)(normalizedDepth * HUES.length) % HUES.length;
    float hue = HUES[hueIndex];
    
    // Add fracture-based hue distortion
    float fractureShift = (float)Math.sin(u * 5 + v * 3 + timeAccum * 3) * fracture.getValuef() * 20f;
    hue = (hue + fractureShift + 360f) % 360f;
    
    // Saturation varies with depth perception
    float saturation = 40f + 60f * Math.abs(depthValue);
    
    // Add flicker to brightness for depth illusion
    float flickerBrightness = (float)Math.sin(timeAccum * 12 + u * 8 + v * 6) * flicker.getValuef() * 20f;
    brightness += flickerBrightness;
    brightness = Math.max(10f, Math.min(100f, brightness));
    
    return LXColor.hsb(hue, saturation, brightness);
  }
}
