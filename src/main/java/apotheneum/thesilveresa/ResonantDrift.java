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
@LXComponentName("Resonant Drift")
public class ResonantDrift extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.02, 0.0, 0.1)
    .setDescription("Pattern drift speed");
  private final CompoundParameter gridFreq = new CompoundParameter("Frequency", 15.0, 5.0, 40.0)
    .setDescription("Grid line frequency");
  private final CompoundParameter offset = new CompoundParameter("Offset", 0.3, 0.0, 2.0)
    .setDescription("Grid offset between layers");
  private final CompoundParameter angle = new CompoundParameter("Angle", 3.0, 0.0, 15.0)
    .setDescription("Grid rotation angle");
  private final CompoundParameter contrast = new CompoundParameter("Contrast", 0.8, 0.0, 1.0)
    .setDescription("Pattern contrast");
  private final CompoundParameter hue = new CompoundParameter("Hue", 200.0, 0.0, 360.0)
    .setDescription("Base hue");
  private final CompoundParameter saturation = new CompoundParameter("Sat", 70.0, 0.0, 100.0)
    .setDescription("Color saturation");
  private final BooleanParameter squareWave = new BooleanParameter("Square", false)
    .setDescription("Use square wave (sharp lines)");

  // Minimal state
  private float time = 0.0f;
  private float phase1 = 0.0f;
  private float phase2 = 0.0f;
  
  // Cached values
  private float freq, off, ang, cont, h, s;
  private boolean square;
  
  // Pre-computed constants
  private float angleRad, cosAng, sinAng;
  private float freq1, freq2;

  public ResonantDrift(LX lx) {
    super(lx);
    addParameter("Speed", this.speed);
    addParameter("Frequency", this.gridFreq);
    addParameter("Offset", this.offset);
    addParameter("Angle", this.angle);
    addParameter("Contrast", this.contrast);
    addParameter("Hue", this.hue);
    addParameter("Sat", this.saturation);
    addParameter("Square", this.squareWave);
  }

  @Override
  protected void render(double deltaMs) {
    // Minimal time update
    float dt = (float)(deltaMs * 0.001);
    time += dt;
    
    // Cache parameters
    freq = gridFreq.getValuef();
    off = offset.getValuef();
    ang = angle.getValuef();
    cont = contrast.getValuef();
    h = hue.getValuef();
    s = saturation.getValuef();
    square = squareWave.getValueb();
    
    // Pre-compute values
    angleRad = ang * 0.017453f; // PI/180
    cosAng = (float)Math.cos(angleRad);
    sinAng = (float)Math.sin(angleRad);
    freq1 = freq;
    freq2 = freq + off;
    
    // Update phases
    float speedVal = speed.getValuef();
    phase1 += speedVal * dt;
    phase2 += speedVal * dt * 1.1f;
    
    // Render geometries
    renderCube();
    renderCylinder();
  }
  
  private void renderCube() {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    
    // Process exterior faces
    for (Face face : cube.exterior.faces) {
      renderFace(face);
    }
    
    // Process interior faces if available
    if (cube.interior != null) {
      for (Face face : cube.interior.faces) {
        renderFace(face);
      }
    }
  }
  
  private void renderFace(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    
    // Pre-compute UV increments
    float uInc = 1.0f / Math.max(1, cols - 1);
    float vInc = 1.0f / Math.max(1, rows - 1);
    
    for (int r = 0; r < rows; r++) {
      Row row = face.rows[r];
      float v = r * vInc - 0.5f;
      
      for (int c = 0; c < cols; c++) {
        float u = c * uInc - 0.5f;
        
        // Generate moiré pattern
        float pattern = generatePattern(u, v);
        
        // Convert to color
        colors[row.points[c].index] = toColor(pattern);
      }
    }
  }
  
  private void renderCylinder() {
    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder == null) return;
    
    // Process exterior
    renderCylinderOrientation(cylinder.exterior);
    
    // Process interior if available
    if (cylinder.interior != null) {
      renderCylinderOrientation(cylinder.interior);
    }
  }
  
  private void renderCylinderOrientation(Cylinder.Orientation orientation) {
    Ring[] rings = orientation.rings;
    int numRings = rings.length;
    float vInc = 1.0f / Math.max(1, numRings - 1);
    
    for (int r = 0; r < numRings; r++) {
      Ring ring = rings[r];
      int pointsPerRing = ring.points.length;
      float uInc = 1.0f / pointsPerRing;
      float v = r * vInc - 0.5f;
      
      for (int p = 0; p < pointsPerRing; p++) {
        // Seamless cylindrical mapping
        float u = p * uInc - 0.5f;
        
        // Generate moiré pattern
        float pattern = generatePattern(u, v);
        
        // Convert to color
        colors[ring.points[p].index] = toColor(pattern);
      }
    }
  }
  
  private float generatePattern(float u, float v) {
    // Grid 1: Straight lines
    float x1 = u;
    float y1 = v;
    
    // Grid 2: Rotated lines
    float x2 = u * cosAng - v * sinAng;
    float y2 = u * sinAng + v * cosAng;
    
    // Generate grid patterns
    float grid1, grid2;
    
    if (square) {
      // Sharp rectangular grid
      grid1 = generateSquareGrid(x1, y1, freq1, phase1);
      grid2 = generateSquareGrid(x2, y2, freq2, phase2);
    } else {
      // Smooth rectangular grid
      grid1 = generateSineGrid(x1, y1, freq1, phase1);
      grid2 = generateSineGrid(x2, y2, freq2, phase2);
    }
    
    // Create moiré interference
    float interference = grid1 * grid2;
    
    // Apply contrast
    return interference * cont;
  }
  
  private float generateSineGrid(float x, float y, float frequency, float phase) {
    // Pure rectangular grid pattern
    float xPattern = (float)Math.sin(x * frequency + phase);
    float yPattern = (float)Math.sin(y * frequency + phase);
    
    // Multiply for grid intersection
    return xPattern * yPattern;
  }
  
  private float generateSquareGrid(float x, float y, float frequency, float phase) {
    // Sharp rectangular grid pattern
    float xPattern = Math.signum((float)Math.sin(x * frequency + phase));
    float yPattern = Math.signum((float)Math.sin(y * frequency + phase));
    
    // Multiply for grid intersection
    return xPattern * yPattern;
  }
  
  private int toColor(float pattern) {
    // Simple brightness mapping
    float brightness = Math.max(0.0f, Math.min(1.0f, (pattern + 1.0f) * 0.5f));
    
    // Apply gamma for better contrast
    brightness = brightness * brightness;
    
    // Hue shift based on pattern
    float hueShift = pattern * 20.0f;
    float finalHue = (h + hueShift) % 360.0f;
    if (finalHue < 0.0f) finalHue += 360.0f;
    
    return LXColor.hsb(finalHue, s, brightness * 100.0f);
  }
}
