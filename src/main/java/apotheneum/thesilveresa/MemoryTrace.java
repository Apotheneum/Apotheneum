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
@LXComponentName("Memory Trace")
public class MemoryTrace extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.5, 0.1, 2.0)
    .setDescription("Animation speed");
  private final CompoundParameter grid = new CompoundParameter("Grid", 8.0, 2.0, 20.0)
    .setDescription("Grid density");
  private final CompoundParameter offset = new CompoundParameter("Offset", 0.3, 0.0, 1.0)
    .setDescription("Grid offset amount");
  private final CompoundParameter memory = new CompoundParameter("Memory", 3.0, 1.0, 8.0)
    .setDescription("Memory trail length");
  private final CompoundParameter decay = new CompoundParameter("Decay", 0.7, 0.3, 0.9)
    .setDescription("Memory fade rate");
  private final CompoundParameter spread = new CompoundParameter("Spread", 0.5, 0.1, 1.0)
    .setDescription("Memory time spread");
  private final CompoundParameter sat = new CompoundParameter("Sat", 85.0, 0.0, 100.0)
    .setDescription("Saturation");
  private final CompoundParameter interf = new CompoundParameter("Interf", 0.6, 0.0, 1.0)
    .setDescription("Interference strength");
  private final CompoundParameter ghost = new CompoundParameter("Ghost", 0.4, 0.1, 0.8)
    .setDescription("Ghost opacity");
  private final CompoundParameter hue = new CompoundParameter("Hue", 240.0, 0.0, 360.0)
    .setDescription("Base hue");

  public MemoryTrace(LX lx) {
    super(lx);
    addParameter("Speed", this.speed);
    addParameter("Grid", this.grid);
    addParameter("Offset", this.offset);
    addParameter("Memory", this.memory);
    addParameter("Decay", this.decay);
    addParameter("Spread", this.spread);
    addParameter("Saturate", this.sat);
    addParameter("Interfere", this.interf);
    addParameter("Ghost", this.ghost);
    addParameter("Hue", this.hue);
  }

  @Override
  protected void render(double deltaMs) {
    float t = (float)(lx.engine.nowMillis / 1000.0) * speed.getValuef();
    
    int geoMode = 2;
    
    // Render cube if enabled
    if (geoMode == 0 || geoMode == 2) {
      Cube cube = Apotheneum.cube;
      if (cube != null) {
        for (Face face : cube.exterior.faces) {
          processFace(face, t);
        }
        if (cube.interior != null) {
          for (Face face : cube.interior.faces) {
            processFace(face, t);
          }
        }
      }
    }
    
    // Render cylinder if enabled
    if (geoMode == 1 || geoMode == 2) {
      Cylinder cylinder = Apotheneum.cylinder;
      if (cylinder != null) {
        processCylinder(cylinder, t);
      }
    }
  }

  private void processFace(Face face, float t) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);
    
    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        float u = cx * invCols - 0.5f;
        float v = row.index * invRows - 0.5f;
        
        float brightness = calculateMemoryBrightness(u, v, t);
        float pixelHue = calculateMemoryHue(u, v, t);
        
        colors[p.index] = LXColor.hsb(pixelHue, sat.getValuef(), brightness);
      }
    }
  }
  
  private void processCylinder(Cylinder cylinder, float t) {
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

          float brightness = calculateMemoryBrightness(u, v, t);
          float pixelHue = calculateMemoryHue(u, v, t);
          
          colors[p.index] = LXColor.hsb(pixelHue, sat.getValuef(), brightness);
        }
      }
    }
  }

  private float calculateMemoryBrightness(float u, float v, float t) {
    float scale = grid.getValuef();
    float offsetAmt = offset.getValuef();
    int numLayers = (int)memory.getValuef();
    float decayRate = decay.getValuef();
    float timeSpread = spread.getValuef();
    float interference = interf.getValuef();
    float ghostOpacity = ghost.getValuef();
    
    float totalBrightness = 0f;
    
    // Current grid
    float currentOffset = offsetAmt * (float)Math.sin(t * 0.7f);
    float currentGrid = calculateGrid(u + currentOffset, v + currentOffset * 0.6f, scale, t);
    totalBrightness += currentGrid;
    
    // Memory layers
    for (int i = 1; i <= numLayers; i++) {
      float layerTime = t - i * timeSpread;
      float layerOffset = offsetAmt * (float)Math.sin(layerTime * 0.7f);
      float layerGrid = calculateGrid(u + layerOffset, v + layerOffset * 0.6f, scale, layerTime);
      
      float layerDecay = (float)Math.pow(decayRate, i);
      totalBrightness += layerGrid * layerDecay * ghostOpacity;
    }
    
    // Add interference between layers
    if (numLayers > 1) {
      float interferencePattern = (float)Math.sin((u + v) * scale * 0.5f + t);
      totalBrightness += interferencePattern * interference * 0.3f;
    }
    
    return Math.max(0f, Math.min(100f, totalBrightness * 50f));
  }
  
  private float calculateMemoryHue(float u, float v, float t) {
    float baseHue = hue.getValuef();
    float memoryShift = ((u + v) * 0.5f + 0.5f) * 60.0f;
    float timeShift = (float)Math.sin(t * 0.3f) * 30.0f;
    
    return (baseHue + memoryShift + timeShift) % 360.0f;
  }
  
  private float calculateGrid(float u, float v, float scale, float t) {
    float grid1 = (float)Math.sin(u * scale * 2.0f * Math.PI + t) *
                  (float)Math.sin(v * scale * 2.0f * Math.PI + t);
    
    float grid2 = (float)Math.sin(u * scale * 2.2f * Math.PI + t * 0.8f) *
                  (float)Math.sin(v * scale * 2.2f * Math.PI + t * 0.8f);
    
    return (grid1 + grid2 * 0.6f) / 1.6f;
  }
}
