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
@LXComponentName("Superformula 2")
public class Superformula2 extends ApotheneumPattern {

  // Pattern selection and morphing
  private final DiscreteParameter pattern = new DiscreteParameter("Pattern", 0, 8)
    .setDescription("Mathematical pattern type");
  private final CompoundParameter morphSpeed = new CompoundParameter("Speed", 0.2, 0.0, 2.0)
    .setDescription("Pattern morph speed");
  private final BooleanParameter autoMorph = new BooleanParameter("Auto", true)
    .setDescription("Auto-cycle through patterns");
    
  // Core mathematical parameters
  private final CompoundParameter paramA = new CompoundParameter("A", 1.0, 0.1, 10.0)
    .setDescription("Primary parameter A");
  private final CompoundParameter paramB = new CompoundParameter("B", 1.0, 0.1, 10.0)
    .setDescription("Secondary parameter B");
  private final CompoundParameter paramC = new CompoundParameter("C", 1.0, 0.1, 10.0)
    .setDescription("Tertiary parameter C");
  private final CompoundParameter paramD = new CompoundParameter("D", 1.0, 0.1, 10.0)
    .setDescription("Quaternary parameter D");
    
  // Visual and animation controls
  private final CompoundParameter scale = new CompoundParameter("Scale", 0.4, 0.01, 1.0)
    .setDescription("Pattern scale");
  private final CompoundParameter rotation = new CompoundParameter("Rotate", 0.0, 0.0, 1.0)
    .setDescription("Pattern rotation");
  private final CompoundParameter lineWidth = new CompoundParameter("Width", 0.02, 0.005, 0.1)
    .setDescription("Line thickness");
  private final CompoundParameter sat = new CompoundParameter("Sat", 85.0, 0.0, 100.0)
    .setDescription("Color saturation");
  private final CompoundParameter brightness = new CompoundParameter("Bright", 100.0, 20.0, 100.0)
    .setDescription("Base brightness");
    
  // Animation modes
  private final DiscreteParameter animMode = new DiscreteParameter("Anim", 0, 4)
    .setDescription("Animation mode: 0=Static, 1=Pulse, 2=Rotate, 3=Breathe");
  private final CompoundParameter animRate = new CompoundParameter("Rate", 1.0, 0.1, 3.0)
    .setDescription("Animation rate");

  // Pattern definitions matching the reference image
  private static final PatternDef[] PATTERNS = {
    // Top row patterns
    new PatternDef("Diamond Grid", 0, 1, 1, 4, 6, 240f), // Blue diamond/square grid
    new PatternDef("Polar Rose", 1, 2, 1, 2, 3, 280f),   // Purple 4-petal rose
    new PatternDef("Star Burst", 2, 16, 1, 2, 10, 200f), // Cyan star pattern
    new PatternDef("Flower", 3, 10, 2, 1, 2, 320f),      // Pink/magenta flower
    
    // Bottom row patterns
    new PatternDef("Ray Burst", 4, 0.2f, 1, 2, 44, 60f),  // Yellow radiating lines
    new PatternDef("Cross Star", 5, 0.2f, 1, 6, 40, 300f), // Purple cross pattern
    new PatternDef("Plus Grid", 6, 3, 1, 80, 4, 180f),   // Cyan plus/cross grid
    new PatternDef("Octagon", 7, 20, 1, 80, 4, 240f)     // Blue octagonal pattern
  };
  
  private static class PatternDef {
    String name;
    int type;
    float a, b, c, d;
    float hue;
    
    PatternDef(String name, int type, float a, float b, float c, float d, float hue) {
      this.name = name;
      this.type = type;
      this.a = a; this.b = b; this.c = c; this.d = d;
      this.hue = hue;
    }
  }

  private float morphTime = 0f;
  private int currentPatternIndex = 0;

  public Superformula2(LX lx) {
    super(lx);
    addParameter("Auto", this.autoMorph);
    addParameter("Speed", this.morphSpeed);
    addParameter("Pattern", this.pattern);
    addParameter("Bright", this.brightness);
    addParameter("Sat", this.sat);
    addParameter("A", this.paramA);
    addParameter("B", this.paramB);
    addParameter("C", this.paramC);
    addParameter("D", this.paramD);
    addParameter("Scale", this.scale);
    addParameter("Rotate", this.rotation);
    addParameter("Width", this.lineWidth);
    addParameter("Anim", this.animMode);
    addParameter("Rate", this.animRate);
  }

  private float distanceToPattern(float x, float y, PatternDef pat, float time, float scaleVal, float rotVal) {
    // Apply rotation
    float cosR = (float)Math.cos(rotVal);
    float sinR = (float)Math.sin(rotVal);
    float rx = x * cosR - y * sinR;
    float ry = x * sinR + y * cosR;
    
    float r = (float)Math.sqrt(rx * rx + ry * ry);
    float theta = (float)Math.atan2(ry, rx);
    if (theta < 0) theta += 2f * (float)Math.PI;
    
    switch (pat.type) {
      case 0: // Diamond Grid - creates diamond/square lattice
        return distanceToDiamondGrid(rx, ry, pat.a * scaleVal, pat.b);
        
      case 1: // Polar Rose - r = cos(k*theta)
        return distanceToPolarRose(r, theta, pat.a * scaleVal, pat.b);
        
      case 2: // Star Burst - radiating lines with modulation
        return distanceToStarBurst(r, theta, pat.a * scaleVal, (int)pat.d);
        
      case 3: // Flower - superformula variant
        return distanceToFlower(r, theta, pat.a * scaleVal, pat.b, pat.c);
        
      case 4: // Ray Burst - simple radiating lines
        return distanceToRayBurst(r, theta, pat.a * scaleVal, (int)pat.d);
        
      case 5: // Cross Star - cross pattern with rotation
        return distanceToCrossStar(r, theta, pat.a * scaleVal, (int)pat.c);
        
      case 6: // Plus Grid - grid of plus signs
        return distanceToPlusGrid(rx, ry, pat.a * scaleVal, pat.c);
        
      case 7: // Octagon - regular octagon
        return distanceToOctagon(r, theta, pat.a * scaleVal);
        
      default:
        return Float.MAX_VALUE;
    }
  }
  
  private float distanceToDiamondGrid(float x, float y, float spacing, float thickness) {
    // Create diamond grid pattern
    float diagX = (x + y) / (float)Math.sqrt(2);
    float diagY = (x - y) / (float)Math.sqrt(2);
    
    float modX = Math.abs(diagX % spacing - spacing * 0.5f);
    float modY = Math.abs(diagY % spacing - spacing * 0.5f);
    
    return Math.min(modX, modY) - thickness * 0.01f;
  }
  
  private float distanceToPolarRose(float r, float theta, float scale, float k) {
    float roseR = scale * (float)Math.abs(Math.cos(k * theta));
    return Math.abs(r - roseR) - 0.01f;
  }
  
  private float distanceToStarBurst(float r, float theta, float scale, int rays) {
    float rayAngle = 2f * (float)Math.PI / rays;
    float modTheta = theta % rayAngle;
    float centerAngle = rayAngle * 0.5f;
    float distToRay = Math.abs(modTheta - centerAngle);
    
    return Math.min(distToRay * r, Math.abs(distToRay - centerAngle) * r) - scale * 0.1f;
  }
  
  private float distanceToFlower(float r, float theta, float scale, float petals, float sharpness) {
    float petalR = scale * (0.5f + 0.5f * (float)Math.cos(petals * theta));
    return Math.abs(r - petalR) - 0.01f;
  }
  
  private float distanceToRayBurst(float r, float theta, float scale, int rays) {
    float rayWidth = scale;
    float rayAngle = 2f * (float)Math.PI / rays;
    float modTheta = (theta % rayAngle);
    float centerLine = rayAngle * 0.5f;
    
    return Math.abs(modTheta - centerLine) * r - rayWidth;
  }
  
  private float distanceToCrossStar(float r, float theta, float scale, int arms) {
    float armAngle = 2f * (float)Math.PI / arms;
    float modTheta = theta % armAngle;
    float distToArm = Math.min(modTheta, armAngle - modTheta);
    
    return distToArm * r - scale;
  }
  
  private float distanceToPlusGrid(float x, float y, float spacing, float thickness) {
    float modX = Math.abs(x % spacing - spacing * 0.5f);
    float modY = Math.abs(y % spacing - spacing * 0.5f);
    
    float distX = modX - thickness * 0.01f;
    float distY = modY - thickness * 0.01f;
    
    return Math.min(distX, distY);
  }
  
  private float distanceToOctagon(float r, float theta, float scale) {
    // Regular octagon
    float sides = 8f;
    float sideAngle = 2f * (float)Math.PI / sides;
    float modTheta = theta % sideAngle - sideAngle * 0.5f;
    float octR = scale / (float)Math.cos(modTheta);
    
    return Math.abs(r - octR) - 0.005f;
  }

  @Override
  protected void render(double deltaMs) {
    float time = (float)(lx.engine.nowMillis / 1000.0);
    
    // Handle pattern morphing
    if (autoMorph.getValueb()) {
      morphTime += (float)(deltaMs / 1000.0) * morphSpeed.getValuef();
      if (morphTime >= 4.0f) { // Hold each pattern for 4 seconds
        morphTime = 0f;
        currentPatternIndex = (currentPatternIndex + 1) % PATTERNS.length;
        pattern.setValue(currentPatternIndex);
      }
    } else {
      currentPatternIndex = pattern.getValuei();
    }
    
    PatternDef currentPattern = PATTERNS[currentPatternIndex];
    
    // Get parameters
    float scaleVal = scale.getValuef();
    float rotVal = rotation.getValuef() * 2f * (float)Math.PI;
    float lineWidthVal = lineWidth.getValuef();
    float saturation = sat.getValuef();
    float baseBright = brightness.getValuef();
    
    // Add animation effects
    int mode = animMode.getValuei();
    if (mode == 1) { // Pulse
      float pulse = 0.5f + 0.5f * (float)Math.sin(time * animRate.getValuef() * 2f);
      scaleVal *= (0.7f + 0.6f * pulse);
    } else if (mode == 2) { // Rotate
      rotVal += time * animRate.getValuef();
    } else if (mode == 3) { // Breathe
      float breathe = 0.5f + 0.5f * (float)Math.sin(time * animRate.getValuef() * 0.5f);
      scaleVal *= (0.8f + 0.4f * breathe);
    }
    
    // Render cube
    Cube cube = Apotheneum.cube;
    if (cube != null) {
      renderCubeGeometry(cube.exterior, currentPattern, time, scaleVal, rotVal, lineWidthVal, saturation, baseBright);
      if (cube.interior != null) {
        renderCubeGeometry(cube.interior, currentPattern, time, scaleVal, rotVal, lineWidthVal, saturation, baseBright);
      }
    }
    
    // Render cylinder
    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder != null) {
      renderCylinderGeometry(cylinder.exterior, currentPattern, time, scaleVal, rotVal, lineWidthVal, saturation, baseBright);
      if (cylinder.interior != null) {
        renderCylinderGeometry(cylinder.interior, currentPattern, time, scaleVal, rotVal, lineWidthVal, saturation, baseBright);
      }
    }
  }
  
  private void renderCubeGeometry(Cube.Orientation orientation, PatternDef pat, float time,
                                  float scaleVal, float rotVal, float lineWidthVal,
                                  float saturation, float baseBright) {
    for (Face face : orientation.faces) {
      int cols = face.columns.length;
      int rows = face.rows.length;
      
      for (Row row : face.rows) {
        for (int cx = 0; cx < cols; cx++) {
          LXPoint p = row.points[cx];
          
          // Convert to normalized coordinates centered at origin
          float u = (float)cx / Math.max(1, cols - 1) - 0.5f;
          float v = (float)row.index / Math.max(1, rows - 1) - 0.5f;
          
          float dist = distanceToPattern(u, v, pat, time, scaleVal, rotVal);
          
          if (dist <= lineWidthVal) {
            float brightness = baseBright;
            
            // Distance-based brightness falloff for smoother lines
            if (dist > 0) {
              brightness *= Math.max(0.2f, 1.0f - (dist / lineWidthVal));
            }
            
            colors[p.index] = LXColor.hsb(pat.hue, saturation, brightness);
          } else {
            colors[p.index] = 0;
          }
        }
      }
    }
  }
  
  private void renderCylinderGeometry(Cylinder.Orientation orientation, PatternDef pat, float time,
                                      float scaleVal, float rotVal, float lineWidthVal,
                                      float saturation, float baseBright) {
    Ring[] rings = orientation.rings;
    int numRings = rings.length;
    
    for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
      Ring ring = rings[ringIndex];
      int pointsPerRing = ring.points.length;
      
      for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
        LXPoint p = ring.points[pointIndex];
        
        // Cylindrical to UV mapping, centered at origin
        float u = (float)pointIndex / pointsPerRing - 0.5f;
        float v = (float)ringIndex / Math.max(1, numRings - 1) - 0.5f;
        
        float dist = distanceToPattern(u, v, pat, time, scaleVal, rotVal);
        
        if (dist <= lineWidthVal) {
          float brightness = baseBright;
          
          // Distance-based brightness falloff
          if (dist > 0) {
            brightness *= Math.max(0.2f, 1.0f - (dist / lineWidthVal));
          }
          
          colors[p.index] = LXColor.hsb(pat.hue, saturation, brightness);
        } else {
          colors[p.index] = 0;
        }
      }
    }
  }
}
