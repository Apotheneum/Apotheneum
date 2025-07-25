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

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Morphologies")
public class Morphologies extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.2, 0.05, 1.0)
    .setDescription("Morphing speed between tilings");
  private final CompoundParameter scale = new CompoundParameter("Scale", 5.0, 2.0, 15.0)
    .setDescription("Tiling scale");
  private final CompoundParameter morphBlend = new CompoundParameter("Blend", 0.0, 0.0, 1.0)
    .setDescription("Manual morph control");
  private final CompoundParameter flowRate = new CompoundParameter("Flow", 1.0, 0.0, 3.0)
    .setDescription("Speed of flowing transitions");
  private final CompoundParameter contrast = new CompoundParameter("Contrast", 70.0, 0.0, 100.0)
    .setDescription("Edge contrast between tilings");
  private final BooleanParameter autoMorph = new BooleanParameter("Auto", true)
    .setDescription("Automatic morphing between patterns");
  private final CompoundParameter sat = new CompoundParameter("Sat", 80.0, 0.0, 100.0)
    .setDescription("Color saturation");

  // Tiling system definitions
  private enum TilingType {
    RHOMBIC,
    DUAL_RHOMBIC,
    HEXAGONAL,
    TRIANGULAR
  }

  private static final float[] TILING_HUES = { 30f, 190f, 280f, 340f };
  private float morphPhase = 0f;

  public Morphologies(LX lx) {
    super(lx);
    addParameter("Speed", this.speed);
    addParameter("Scale", this.scale);
    addParameter("Blend", this.morphBlend);
    addParameter("Flow", this.flowRate);
    addParameter("Contrast", this.contrast);
    addParameter("Auto", this.autoMorph);
    addParameter("Sat", this.sat);
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float)(deltaMs / 1000.0);
    
    // Update morph phase
    if (autoMorph.getValueb()) {
      morphPhase += dt * speed.getValuef();
      morphPhase %= 1f;
    } else {
      morphPhase = morphBlend.getValuef();
    }

    // Render geometries
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
    
    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder != null) {
      processCylinder(cylinder);
    }
  }

  private void processFace(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);
    float scl = scale.getValuef();
    float flow = flowRate.getValuef();
    
    float time = (lx.engine.nowMillis % 30000) / 1000.0f;
    
    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        float u = cx * invCols - 0.5f;
        float v = row.index * invRows - 0.5f;
        
        int color = calculateMorphColor(u, v, scl, time, flow);
        colors[p.index] = color;
      }
    }
  }
  
  private void processCylinder(Cylinder cylinder) {
    Cylinder.Orientation[] faces = (cylinder.interior != null)
      ? new Cylinder.Orientation[]{ cylinder.exterior, cylinder.interior }
      : new Cylinder.Orientation[]{ cylinder.exterior };

    float time = (lx.engine.nowMillis % 30000) / 1000.0f;
    float scl = scale.getValuef();
    float flow = flowRate.getValuef();

    for (Cylinder.Orientation face : faces) {
      Ring[] rings = face.rings;
      int numRings = rings.length;
      final float stretchFactor = 0.4f;

      for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
        Ring ring = rings[ringIndex];
        int pointsPerRing = ring.points.length;

        for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
          LXPoint p = ring.points[pointIndex];

          float theta = (float)(2 * Math.PI * pointIndex / pointsPerRing);
          float u = theta / (2 * (float)Math.PI) - 0.5f;
          float v = Math.min((float)ringIndex / (numRings - 1) * stretchFactor, 1.0f) - 0.5f;
          
          int color = calculateMorphColor(u, v, scl, time, flow);
          colors[p.index] = color;
        }
      }
    }
  }
  
  private int calculateMorphColor(float u, float v, float scl, float time, float flow) {
    // Apply flowing offset
    float flowU = u + (float)Math.sin(time * flow * 0.3f) * 0.2f;
    float flowV = v + (float)Math.cos(time * flow * 0.4f) * 0.15f;
    
    // Calculate tiling values for different systems
    float rhombic = evaluateRhombicTiling(flowU, flowV, scl);
    float dualRhombic = evaluateDualRhombicTiling(flowU, flowV, scl);
    float hexagonal = evaluateHexagonalTiling(flowU, flowV, scl);
    float triangular = evaluateTriangularTiling(flowU, flowV, scl);
    
    // Determine current morph state (cycles through 4 tilings)
    float cyclePhase = morphPhase * 4f;
    int currentTiling = (int)cyclePhase;
    float localPhase = cyclePhase - currentTiling;
    
    float value1, value2;
    float hue1, hue2;
    
    switch (currentTiling % 4) {
      case 0: // Rhombic to Dual Rhombic
        value1 = rhombic;
        value2 = dualRhombic;
        hue1 = TILING_HUES[0];
        hue2 = TILING_HUES[1];
        break;
      case 1: // Dual Rhombic to Hexagonal
        value1 = dualRhombic;
        value2 = hexagonal;
        hue1 = TILING_HUES[1];
        hue2 = TILING_HUES[2];
        break;
      case 2: // Hexagonal to Triangular
        value1 = hexagonal;
        value2 = triangular;
        hue1 = TILING_HUES[2];
        hue2 = TILING_HUES[3];
        break;
      case 3: // Triangular to Rhombic
      default:
        value1 = triangular;
        value2 = rhombic;
        hue1 = TILING_HUES[3];
        hue2 = TILING_HUES[0];
        break;
    }
    
    // Smooth transition between tilings
    float smoothPhase = smoothstep(localPhase);
    float blendedValue = value1 * (1f - smoothPhase) + value2 * smoothPhase;
    float blendedHue = hue1 + (hue2 - hue1) * smoothPhase;
    if (blendedHue < 0) blendedHue += 360f;
    if (blendedHue >= 360f) blendedHue -= 360f;
    
    // Apply contrast
    float contrastVal = contrast.getValuef() / 100f;
    float brightness = Math.max(0f, Math.min(100f, blendedValue * 100f * contrastVal));
    
    if (brightness < 5f) return 0;
    
    return LXColor.hsb(blendedHue, sat.getValuef(), brightness);
  }
  
  private float evaluateRhombicTiling(float u, float v, float scale) {
    // Rhombic tiling based on parallelogram lattice
    float x = u * scale;
    float y = v * scale;
    
    // Skew coordinates for rhombic pattern
    float skewX = x + y * 0.5f;
    float skewY = y * 0.866f; // sqrt(3)/2
    
    int ix = (int)Math.floor(skewX);
    int iy = (int)Math.floor(skewY);
    
    float fx = skewX - ix;
    float fy = skewY - iy;
    
    // Rhombic cell pattern
    if (fx + fy < 1f) {
      return ((ix + iy) % 2 == 0) ? 1f : 0f;
    } else {
      return ((ix + iy + 1) % 2 == 0) ? 1f : 0f;
    }
  }
  
  private float evaluateDualRhombicTiling(float u, float v, float scale) {
    // Dual of rhombic - complementary pattern
    return 1f - evaluateRhombicTiling(u + 0.1f, v + 0.1f, scale);
  }
  
  private float evaluateHexagonalTiling(float u, float v, float scale) {
    // Hexagonal tiling
    float x = u * scale * 1.732f; // sqrt(3)
    float y = v * scale;
    
    float q = x * 0.866f - y * 0.5f; // 2/3 * cos(30°), -2/3 * sin(30°)
    float r = y;
    
    int qi = (int)Math.round(q);
    int ri = (int)Math.round(r);
    
    float qf = q - qi;
    float rf = r - ri;
    
    if (Math.abs(qf) + Math.abs(rf) > 0.5f) {
      return ((qi + ri) % 2 == 0) ? 1f : 0.2f;
    } else {
      return ((qi + ri) % 3 == 0) ? 1f : 0f;
    }
  }
  
  private float evaluateTriangularTiling(float u, float v, float scale) {
    // Triangular tiling
    float x = u * scale;
    float y = v * scale * 1.732f; // sqrt(3)
    
    int ix = (int)Math.floor(x);
    int iy = (int)Math.floor(y);
    
    float fx = x - ix;
    float fy = y - iy;
    
    // Triangle orientation alternates
    boolean upperTriangle = (fx + fy > 1f);
    int triangleId = ix + iy * 2 + (upperTriangle ? 1 : 0);
    
    return (triangleId % 3 == 0) ? 1f : 0f;
  }
  
  // Smooth step function for better transitions
  private float smoothstep(float t) {
    t = Math.max(0f, Math.min(1f, t));
    return t * t * (3f - 2f * t);
  }
}
