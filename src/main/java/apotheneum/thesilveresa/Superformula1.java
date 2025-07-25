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
@LXComponentName("Superformula 1")
public class Superformula1 extends ApotheneumPattern {

  // Core superformula parameters
  private final CompoundParameter m = new CompoundParameter("m", 5.0, 1.0, 20.0)
    .setDescription("Rotational symmetry parameter");
  private final CompoundParameter n1 = new CompoundParameter("n1", 1.0, 0.1, 10.0)
    .setDescription("First shape parameter");
  private final CompoundParameter n2 = new CompoundParameter("n2", 1.0, 0.1, 10.0)
    .setDescription("Second shape parameter");
  private final CompoundParameter n3 = new CompoundParameter("n3", 1.0, 0.1, 10.0)
    .setDescription("Third shape parameter");
  private final CompoundParameter a = new CompoundParameter("a", 1.0, 0.1, 3.0)
    .setDescription("X-axis scaling factor");
  private final CompoundParameter b = new CompoundParameter("b", 1.0, 0.1, 3.0)
    .setDescription("Y-axis scaling factor");
    
  // Animation and morphing controls
  private final CompoundParameter morphSpeed = new CompoundParameter("Speed", 0.1, 0.0, 1.0)
    .setDescription("Morph speed between presets");
  private final CompoundParameter morphProgress = new CompoundParameter("Morph", 0.0, 0.0, 1.0)
    .setDescription("Manual morph control");
  private final DiscreteParameter preset = new DiscreteParameter("Preset", 0, 10)
    .setDescription("Shape preset selection");
  private final CompoundParameter sweep = new CompoundParameter("Sweep", 0.0, 0.0, 1.0)
    .setDescription("Sweep through all presets continuously");
    
  // Visual controls
  private final CompoundParameter scale = new CompoundParameter("Scale", 0.8, 0.1, 2.0)
    .setDescription("Shape scale factor");
  private final CompoundParameter rotation = new CompoundParameter("Rotate", 0.0, 0.0, 1.0)
    .setDescription("Shape rotation");
  private final CompoundParameter sat = new CompoundParameter("Sat", 85.0, 0.0, 100.0)
    .setDescription("Color saturation");
  private final CompoundParameter brightness = new CompoundParameter("Bright", 100.0, 10.0, 100.0)
    .setDescription("Base brightness");
  private final BooleanParameter filled = new BooleanParameter("Fill", false)
    .setDescription("Fill shapes vs outline only");
  private final CompoundParameter edgeWidth = new CompoundParameter("Edge", 0.05, 0.01, 0.2)
    .setDescription("Edge detection threshold");
    
  // Animation modes
  private final DiscreteParameter animMode = new DiscreteParameter("Anim", 0, 4)
    .setDescription("Animation mode: 0=Static, 1=Pulse, 2=Rotate, 3=Breathe");
  private final CompoundParameter animRate = new CompoundParameter("Rate", 1.0, 0.1, 5.0)
    .setDescription("Animation rate multiplier");

  // Predefined superformula presets (m, n1, n2, n3, a, b)
  private static final float[][] PRESETS = {
    {5f, 1f, 1f, 1f, 1f, 1f},        // Pentagon
    {3f, 4.5f, 10f, 10f, 1f, 1f},    // Triangle with curves
    {6f, 1f, 7f, 8f, 1f, 1f},        // Hexagon variant
    {2f, 1f, 1f, 1f, 1f, 1f},        // Ellipse
    {4f, 0.5f, 0.5f, 4f, 1f, 1f},    // Square with indentations
    {8f, 1f, 1f, 8f, 1f, 1f},        // Octagon
    {3f, 0.5f, 0.5f, 0.5f, 1f, 1f},  // Triangular star
    {7f, 2f, 6f, 6f, 1f, 1f},        // Complex heptagon
    {4f, 1f, 0.5f, 0.8f, 1f, 1f},    // Square variant
    {6f, 10f, 10f, 10f, 1f, 1f}      // Smooth hexagon
  };
  
  private static final float[] HUES = {50f, 340f, 200f, 300f, 120f, 60f, 280f, 180f, 20f, 240f};

  // Current interpolated parameters
  private float currentM, currentN1, currentN2, currentN3, currentA, currentB;

  public Superformula1(LX lx) {
    super(lx);
    addParameter("m", this.m);
    addParameter("n1", this.n1);
    addParameter("n2", this.n2);
    addParameter("n3", this.n3);
    addParameter("a", this.a);
    addParameter("b", this.b);
    addParameter("Speed", this.morphSpeed);
    addParameter("Morph", this.morphProgress);
    addParameter("Preset", this.preset);
    addParameter("Sweep", this.sweep);
    addParameter("Scale", this.scale);
    addParameter("Rotate", this.rotation);
    addParameter("Sat", this.sat);
    addParameter("Bright", this.brightness);
    addParameter("Fill", this.filled);
    addParameter("Edge", this.edgeWidth);
    addParameter("Anim", this.animMode);
    addParameter("Rate", this.animRate);

    // Initialize with first preset
    float[] p = PRESETS[0];
    currentM = p[0];
    currentN1 = p[1];
    currentN2 = p[2];
    currentN3 = p[3];
    currentA = p[4];
    currentB = p[5];
  }
  
  private void interpolatePresets(float sweepValue) {
    int numPresets = PRESETS.length;
    
    // Map sweep value to preset range
    float scaledSweep = sweepValue * (numPresets - 1);
    int basePreset = (int)Math.floor(scaledSweep);
    int nextPreset = (basePreset + 1) % numPresets;
    float t = scaledSweep - basePreset;
    
    // Get the two presets to interpolate between
    float[] preset1 = PRESETS[basePreset % numPresets];
    float[] preset2 = PRESETS[nextPreset];
    
    // Smooth interpolation
    t = smoothStep(t);
    
    // Interpolate preset values as base
    float baseM = lerp(preset1[0], preset2[0], t);
    float baseN1 = lerp(preset1[1], preset2[1], t);
    float baseN2 = lerp(preset1[2], preset2[2], t);
    float baseN3 = lerp(preset1[3], preset2[3], t);
    float baseA = lerp(preset1[4], preset2[4], t);
    float baseB = lerp(preset1[5], preset2[5], t);
    
    // Apply manual knob offsets to the interpolated base values
    currentM = baseM * (m.getValuef() / 5.0f);  // 5.0 is the default m value
    currentN1 = baseN1 * (n1.getValuef() / 1.0f);  // 1.0 is the default n1 value
    currentN2 = baseN2 * (n2.getValuef() / 1.0f);  // 1.0 is the default n2 value
    currentN3 = baseN3 * (n3.getValuef() / 1.0f);  // 1.0 is the default n3 value
    currentA = baseA * (a.getValuef() / 1.0f);   // 1.0 is the default a value
    currentB = baseB * (b.getValuef() / 1.0f);   // 1.0 is the default b value
  }
  
  private void updateParameters() {
    float sweepValue = sweep.getValuef();
    
    if (sweepValue > 0.001f) {
      // Use sweep knob to interpolate between presets, with manual knob modulation
      interpolatePresets(sweepValue);
    } else {
      // Use manual parameters directly - preset only affects color now
      currentM = m.getValuef();
      currentN1 = n1.getValuef();
      currentN2 = n2.getValuef();
      currentN3 = n3.getValuef();
      currentA = a.getValuef();
      currentB = b.getValuef();
    }
  }
  
  private float smoothStep(float t) {
    return t * t * (3 - 2 * t);
  }
  
  private float lerp(float start, float end, float t) {
    return start + (end - start) * t;
  }
  
  private float superformula(float angle, float mVal, float n1Val, float n2Val, float n3Val, float aVal, float bVal) {
    float cosComponent = Math.abs((float)Math.cos(mVal * angle / 4.0) / aVal);
    float sinComponent = Math.abs((float)Math.sin(mVal * angle / 4.0) / bVal);
    
    float term1 = (float)Math.pow(cosComponent, n2Val);
    float term2 = (float)Math.pow(sinComponent, n3Val);
    
    float result = (float)Math.pow(term1 + term2, -1.0 / n1Val);
    
    // Handle edge cases
    if (!Float.isFinite(result) || result <= 0) {
      result = 0.001f;
    }
    
    return result;
  }
  
  private boolean isInsideShape(float u, float v, float time) {
    float scaleVal = scale.getValuef();
    // Get rotation from the knob (0-1 maps to 0-2π)
    float rotVal = rotation.getValuef() * 2f * (float)Math.PI;
    
    // Add animation rotation on top of manual rotation
    int mode = animMode.getValuei();
    if (mode == 2) { // Rotate mode
      rotVal += time * animRate.getValuef() * 0.5f;
    }
    
    // Convert UV to centered coordinates
    float x = (u - 0.5f);
    float y = (v - 0.5f);
    
    // Apply rotation transformation
    float rotX = x * (float)Math.cos(rotVal) - y * (float)Math.sin(rotVal);
    float rotY = x * (float)Math.sin(rotVal) + y * (float)Math.cos(rotVal);
    
    // Convert back to polar coordinates for superformula calculation
    float radius = (float)Math.sqrt(rotX * rotX + rotY * rotY);
    float angle = (float)Math.atan2(rotY, rotX);
    if (angle < 0) angle += 2f * (float)Math.PI;
    
    // Apply animation scaling
    if (mode == 1) { // Pulse mode
      float pulse = 0.5f + 0.5f * (float)Math.sin(time * animRate.getValuef() * 2f);
      scaleVal *= (0.7f + 0.6f * pulse);
    } else if (mode == 3) { // Breathe mode
      float breathe = 0.5f + 0.5f * (float)Math.sin(time * animRate.getValuef());
      scaleVal *= (0.8f + 0.4f * breathe);
    }
    
    float shapeRadius = superformula(angle, currentM, currentN1, currentN2, currentN3, currentA, currentB);
    shapeRadius *= scaleVal * 0.4f; // Scale to fit in UV space
    
    return radius <= shapeRadius;
  }
  
  private boolean isOnEdge(float u, float v, float invCols, float invRows, float time) {
    if (!isInsideShape(u, v, time)) return false;
    
    float threshold = edgeWidth.getValuef();
    
    // Check neighboring pixels
    boolean rightNeighbor = isInsideShape(u + invCols, v, time);
    boolean leftNeighbor = isInsideShape(u - invCols, v, time);
    boolean upNeighbor = isInsideShape(u, v + invRows, time);
    boolean downNeighbor = isInsideShape(u, v - invRows, time);
    
    return !rightNeighbor || !leftNeighbor || !upNeighbor || !downNeighbor;
  }

  @Override
  protected void render(double deltaMs) {
    updateParameters();
    
    float time = (float)(lx.engine.nowMillis / 1000.0);
    
    // Determine current hue based on sweep position or preset
    float hue;
    float sweepValue = sweep.getValuef();
    if (sweepValue > 0.001f) {
      // Interpolate hue based on sweep position
      float scaledSweep = sweepValue * (HUES.length - 1);
      int baseHue = (int)Math.floor(scaledSweep);
      int nextHue = (baseHue + 1) % HUES.length;
      float t = scaledSweep - baseHue;
      
      float hue1 = HUES[baseHue % HUES.length];
      float hue2 = HUES[nextHue];
      
      // Handle hue wraparound for smooth color transitions
      if (Math.abs(hue2 - hue1) > 180) {
        if (hue1 > hue2) {
          hue2 += 360;
        } else {
          hue1 += 360;
        }
      }
      
      hue = lerp(hue1, hue2, smoothStep(t));
      if (hue >= 360) hue -= 360;
    } else {
      int currentPreset = preset.getValuei();
      hue = HUES[currentPreset % HUES.length];
    }
    
    float saturation = sat.getValuef();
    float baseBright = brightness.getValuef();
    boolean fillMode = filled.getValueb();
    
    // Render cube
    Cube cube = Apotheneum.cube;
    if (cube != null) {
      renderCubeGeometry(cube.exterior, time, hue, saturation, baseBright, fillMode);
      if (cube.interior != null) {
        renderCubeGeometry(cube.interior, time, hue, saturation, baseBright, fillMode);
      }
    }
    
    // Render cylinder
    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder != null) {
      renderCylinderGeometry(cylinder.exterior, time, hue, saturation, baseBright, fillMode);
      if (cylinder.interior != null) {
        renderCylinderGeometry(cylinder.interior, time, hue, saturation, baseBright, fillMode);
      }
    }
  }
  
  private void renderCubeGeometry(Cube.Orientation orientation, float time, float hue, float saturation, float baseBright, boolean fillMode) {
    for (Face face : orientation.faces) {
      int cols = face.columns.length;
      int rows = face.rows.length;
      float invCols = 1.0f / Math.max(1, cols - 1);
      float invRows = 1.0f / Math.max(1, rows - 1);
      
      for (Row row : face.rows) {
        for (int cx = 0; cx < cols; cx++) {
          LXPoint p = row.points[cx];
          float u = cx * invCols;
          float v = row.index * invRows;
          
          boolean inside = isInsideShape(u, v, time);
          boolean onEdge = !fillMode && isOnEdge(u, v, invCols, invRows, time);
          
          if ((fillMode && inside) || (!fillMode && onEdge)) {
            float brightness = baseBright;
            
            // Add animation brightness modulation
            int mode = animMode.getValuei();
            if (mode == 1) { // Pulse brightness
              float pulse = 0.5f + 0.5f * (float)Math.sin(time * animRate.getValuef() * 3f);
              brightness *= (0.6f + 0.4f * pulse);
            }
            
            colors[p.index] = LXColor.hsb(hue, saturation, brightness);
          } else {
            colors[p.index] = 0;
          }
        }
      }
    }
  }
  
  private void renderCylinderGeometry(Cylinder.Orientation orientation, float time, float hue, float saturation, float baseBright, boolean fillMode) {
    Ring[] rings = orientation.rings;
    int numRings = rings.length;
    
    for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
      Ring ring = rings[ringIndex];
      int pointsPerRing = ring.points.length;
      
      for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
        LXPoint p = ring.points[pointIndex];
        
        // Cylindrical to UV mapping
        float u = (float)pointIndex / pointsPerRing;
        float v = (float)ringIndex / Math.max(1, numRings - 1);
        
        boolean inside = isInsideShape(u, v, time);
        boolean onEdge = !fillMode && isCylinderEdge(u, v, pointIndex, ringIndex, pointsPerRing, numRings, time);
        
        if ((fillMode && inside) || (!fillMode && onEdge)) {
          float brightness = baseBright;
          
          // Add animation brightness modulation
          int mode = animMode.getValuei();
          if (mode == 1) { // Pulse brightness
            float pulse = 0.5f + 0.5f * (float)Math.sin(time * animRate.getValuef() * 3f);
            brightness *= (0.6f + 0.4f * pulse);
          }
          
          colors[p.index] = LXColor.hsb(hue, saturation, brightness);
        } else {
          colors[p.index] = 0;
        }
      }
    }
  }
  
  private boolean isCylinderEdge(float u, float v, int pointIndex, int ringIndex, int pointsPerRing, int numRings, float time) {
    if (!isInsideShape(u, v, time)) return false;
    
    // Check circumferential neighbors
    int nextPoint = (pointIndex + 1) % pointsPerRing;
    int prevPoint = (pointIndex - 1 + pointsPerRing) % pointsPerRing;
    float nextU = (float)nextPoint / pointsPerRing;
    float prevU = (float)prevPoint / pointsPerRing;
    
    boolean circumNeighbors = isInsideShape(nextU, v, time) && isInsideShape(prevU, v, time);
    
    // Check height neighbors
    boolean heightNeighbors = true;
    if (ringIndex + 1 < numRings) {
      float nextV = (float)(ringIndex + 1) / Math.max(1, numRings - 1);
      heightNeighbors &= isInsideShape(u, nextV, time);
    }
    if (ringIndex - 1 >= 0) {
      float prevV = (float)(ringIndex - 1) / Math.max(1, numRings - 1);
      heightNeighbors &= isInsideShape(u, prevV, time);
    }
    
    return !circumNeighbors || !heightNeighbors;
  }
}
