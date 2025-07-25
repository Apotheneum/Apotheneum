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
@LXComponentName("Platonic Parade")
public class PlatonicParade extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.1, 0.0, 1.0)
    .setDescription("Morph speed (cycles per second)");
  private final CompoundParameter morph = new CompoundParameter("Morph", 0.0, 0.0, 1.0)
    .setDescription("Morph progress through shapes");
  private final CompoundParameter size  = new CompoundParameter("Size", 4.0, 1.0, 10.0)
    .setDescription("Controls tile size and density");
  private final CompoundParameter sat = new CompoundParameter("Sat", 100.0, 0.0, 100.0)
    .setDescription("Saturation percentage");
  private final BooleanParameter edgeTrace = new BooleanParameter("Trace", true)
    .setDescription("Draw only tile edges");
  private final CompoundParameter edgeSpeed = new CompoundParameter("Rate", 1.0, 0.1, 5.0)
    .setDescription("Speed of edge animation");
  private final CompoundParameter edgeBrightness = new CompoundParameter("Bright", 100.0, 10.0, 100.0)
    .setDescription("Base brightness of edges");
  private final CompoundParameter pulseDepth = new CompoundParameter("Pulse", 50.0, 0.0, 100.0)
    .setDescription("How much edges pulse (0=no pulse, 100=full pulse)");
  private final CompoundParameter animMode = new CompoundParameter("Mode", 0.0, 0.0, 3.0)
    .setDescription("Animation type: 0=TileChase, 1=Radial, 2=Serpentine, 3=Pulse");
  private final CompoundParameter waveLength = new CompoundParameter("Wave", 5.0, 1.0, 20.0)
    .setDescription("Spatial frequency of wave patterns");
  private final CompoundParameter chaseWidth = new CompoundParameter("Chase", 0.1, 0.05, 0.5)
    .setDescription("Width of chase segments (as fraction of perimeter)");
  private final CompoundParameter repeatCount = new CompoundParameter("Repeat", 1.0, 1.0, 8.0)
    .setDescription("Number of simultaneous chase segments");

  private static final float[] HUES = { 50f, 340f, 200f, 300f, 350f };

  public PlatonicParade(LX lx) {
    super(lx);
    addParameter("Speed", this.speed);
    addParameter("Morph", this.morph);
    addParameter("Size", this.size);
    addParameter("Sat", this.sat);
    addParameter("Trace", this.edgeTrace);
    addParameter("Rate", this.edgeSpeed);
    addParameter("Bright", this.edgeBrightness);
    addParameter("Pulse", this.pulseDepth);
    addParameter("Mode", this.animMode);
    addParameter("Wave", this.waveLength);
    addParameter("Chase", this.chaseWidth);
    addParameter("Repeat", this.repeatCount);
  }

  // Calculate position along tile edge perimeter for a given tile
  private float getTileEdgePosition(float u, float v, int tileIdx, int cols, int rows, float sz) {
    TileBounds tileBounds = getTileBounds(u, v, tileIdx, sz);
    if (tileBounds == null) return -1f;
    
    float tilePerimeter = 2 * (tileBounds.width + tileBounds.height);
    float position = 0f;
    
    float localU = (u - tileBounds.centerU) / tileBounds.width * 2;
    float localV = (v - tileBounds.centerV) / tileBounds.height * 2;
    
    float absU = Math.abs(localU);
    float absV = Math.abs(localV);
    float edgeThreshold = 0.8f;
    
    if (absU > edgeThreshold || absV > edgeThreshold) {
      if (absV <= absU) {
        if (localU > 0) {
          position = tileBounds.width + (localV + 1) * tileBounds.height / 2;
        } else {
          position = tileBounds.width + tileBounds.height + tileBounds.width +
                    (1 - localV) * tileBounds.height / 2;
        }
      } else {
        if (localV > 0) {
          position = tileBounds.width + tileBounds.height + (1 - localU) * tileBounds.width / 2;
        } else {
          position = (localU + 1) * tileBounds.width / 2;
        }
      }
      return position / tilePerimeter;
    }
    
    return -1f;
  }
  
  // Calculate position along cylinder circumference for cylindrical coordinates
  private float getCylinderEdgePosition(float theta, float z, int tileIdx, int rings, int pointsPerRing, float sz) {
    // For cylinder, we can create tile patterns based on theta (circumferential) and z (height)
    // Normalize coordinates to [0,1] range
    float u = theta / (2.0f * (float)Math.PI); // 0 to 1 around circumference
    float v = z; // Assuming z is already normalized
    
    return getTileEdgePosition(u, v, tileIdx, pointsPerRing, rings, sz);
  }
  
  // Helper class to store tile boundary information
  private static class TileBounds {
    float centerU, centerV;
    float width, height;
    
    TileBounds(float centerU, float centerV, float width, float height) {
      this.centerU = centerU;
      this.centerV = centerV;
      this.width = width;
      this.height = height;
    }
  }
  
  private TileBounds getTileBounds(float u, float v, int tileIdx, float sz) {
    switch (tileIdx) {
      case 0:
      case 2:
        return getTriangleTileBounds(u, v, sz * (tileIdx == 2 ? 2 : 1));
      case 1:
        return getSquareTileBounds(u, v, sz);
      default:
        return null;
    }
  }
  
  private TileBounds getTriangleTileBounds(float u, float v, float sz) {
    float tileSize = 1.0f / sz;
    float centerU = Math.round(u / tileSize) * tileSize;
    float centerV = Math.round(v / tileSize) * tileSize;
    return new TileBounds(centerU, centerV, tileSize * 0.8f, tileSize * 0.8f);
  }
  
  private TileBounds getSquareTileBounds(float u, float v, float sz) {
    float tileSize = 1.0f / sz;
    float centerU = Math.round(u / tileSize) * tileSize;
    float centerV = Math.round(v / tileSize) * tileSize;
    return new TileBounds(centerU, centerV, tileSize, tileSize);
  }

  private float getPerimeterPosition(float u, float v, int cols, int rows) {
    float pixelU = (u + 0.5f) * (cols - 1);
    float pixelV = (v + 0.5f) * (rows - 1);
    
    int px = Math.round(pixelU);
    int py = Math.round(pixelV);
    
    boolean onPerimeter = (px == 0 || px == cols-1 || py == 0 || py == rows-1);
    if (!onPerimeter) return -1f;
    
    float perimeter = 2 * (cols - 1) + 2 * (rows - 1);
    float position = 0f;
    
    if (py == 0) {
      position = px;
    } else if (px == cols - 1) {
      position = (cols - 1) + py;
    } else if (py == rows - 1) {
      position = (cols - 1) + (rows - 1) + (cols - 1 - px);
    } else if (px == 0) {
      position = (cols - 1) + (rows - 1) + (cols - 1) + (rows - 1 - py);
    }
    
    return position / perimeter;
  }

  @Override
  protected void render(double deltaMs) {
    float m = morph.getValuef() + speed.getValuef() * (float)(deltaMs / 1000.0);
    morph.setValue(m % 1.0f);
    float t = morph.getValuef();

    int steps = 2;
    float segF = t * steps;
    int idx = (int)Math.floor(segF);
    float fSeg = segF - idx;

    int geoMode = 2;
    
    // Render cube if enabled
    if (geoMode == 0 || geoMode == 2) {
      Cube cube = Apotheneum.cube;
      if (cube != null) {
        for (Face face : cube.exterior.faces) {
          processFace(face, idx, fSeg);
        }
        if (cube.interior != null) {
          for (Face face : cube.interior.faces) {
            processFace(face, idx, fSeg);
          }
        }
      }
    }
    
    // Render cylinder if enabled
    if (geoMode == 1 || geoMode == 2) {
      Cylinder cylinder = Apotheneum.cylinder;
      if (cylinder != null) {
        processCylinder(cylinder, idx, fSeg);
      }
    }
  }

  private void processFace(Face face, int idx, float fSeg) {
    float t = (lx.engine.nowMillis % 60000) / 1000.0f;
    int cols = face.columns.length;
    int rows = face.rows.length;
    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);
    float sz = size.getValuef();
    
    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        float u = cx * invCols - 0.5f;
        float v = row.index * invRows - 0.5f;

        int currIdx = fSeg <= 0.5f ? idx : (idx + 1) % 3;
        boolean lit = shapeTest(currIdx, u, v, sz);

        if (edgeTrace.getValueb()) {
          boolean isEdge = false;
          if (lit) {
            boolean rightNeighbor = (cx + 1 < cols) ? shapeTest(currIdx, (cx + 1) * invCols - 0.5f, v, sz) : false;
            boolean leftNeighbor = (cx - 1 >= 0) ? shapeTest(currIdx, (cx - 1) * invCols - 0.5f, v, sz) : false;
            boolean upNeighbor = (row.index + 1 < rows) ? shapeTest(currIdx, u, (row.index + 1) * invRows - 0.5f, sz) : false;
            boolean downNeighbor = (row.index - 1 >= 0) ? shapeTest(currIdx, u, (row.index - 1) * invRows - 0.5f, sz) : false;
            
            isEdge = !rightNeighbor || !leftNeighbor || !upNeighbor || !downNeighbor;
          }
          
          if (isEdge) {
            float brightness = calculateEdgeBrightness(u, v, t, currIdx, cols, rows, sz);
            colors[p.index] = LXColor.hsb(HUES[currIdx], sat.getValuef(), brightness);
          } else {
            colors[p.index] = 0;
          }
        } else {
          colors[p.index] = lit
            ? LXColor.hsb(HUES[currIdx], sat.getValuef(), 100f)
            : 0;
        }
      }
    }
  }
  
  private void processCylinder(Cylinder cylinder, int idx, float fSeg) {
    float t = (lx.engine.nowMillis % 60000) / 1000.0f;
    float sz = size.getValuef();
    final float stretchFactor = 0.4f;
      
    // Build faces array
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

          // Cylindrical coords
          float theta = (float)(2 * Math.PI * pointIndex / pointsPerRing);
          // Original normalized height
          float zNorm = (float)ringIndex / (numRings - 1);
          // Apply stretch and clamp
          zNorm = Math.min(zNorm * stretchFactor, 1.0f);

          // UV mapping for tiling
          float u = theta / (2 * (float)Math.PI) - 0.5f;
          float v = zNorm - 0.5f;

          int currIdx = (fSeg <= 0.5f) ? idx : (idx + 1) % 3;
          boolean lit = shapeTest(currIdx, u, v, sz);

          if (edgeTrace.getValueb()) {
            // Edge detection
            boolean isEdge = false;
            if (lit) {
              // circum neighbors
              int nextPt = (pointIndex + 1) % pointsPerRing;
              int prevPt = (pointIndex - 1 + pointsPerRing) % pointsPerRing;
              float nextU = (2 * (float)Math.PI * nextPt / pointsPerRing) / (2 * (float)Math.PI) - 0.5f;
              float prevU = (2 * (float)Math.PI * prevPt / pointsPerRing) / (2 * (float)Math.PI) - 0.5f;
              boolean c1 = shapeTest(currIdx, nextU, v, sz);
              boolean c2 = shapeTest(currIdx, prevU, v, sz);

              // height neighbors (stretch consistent)
              boolean h1 = (ringIndex + 1 < numRings)
                && shapeTest(currIdx, u, Math.min(((ringIndex + 1f)/(numRings-1)) * stretchFactor, 1.0f) - 0.5f, sz);
              boolean h2 = (ringIndex - 1 >= 0)
                && shapeTest(currIdx, u, Math.min(((ringIndex - 1f)/(numRings-1)) * stretchFactor, 1.0f) - 0.5f, sz);

              isEdge = !(c1 && c2 && h1 && h2);
          }

          if (isEdge) {
            float brightness = calculateCylinderEdgeBrightness(theta, zNorm, t, currIdx, numRings, pointsPerRing, sz);
            colors[p.index] = LXColor.hsb(
              HUES[currIdx],
              sat.getValuef(),
              brightness
            );
          } else {
            colors[p.index] = 0;
          }

         } else {
           // Filled
           colors[p.index] = lit
             ? LXColor.hsb(HUES[currIdx], sat.getValuef(), 100f)
             : 0;
         }
      }
    }
  }
}
  
  // Calculate edge brightness for cube faces
  private float calculateEdgeBrightness(float u, float v, float t, int currIdx, int cols, int rows, float sz) {
    float animSpeed = edgeSpeed.getValuef();
    float baseBright = edgeBrightness.getValuef();
    float pulseAmount = pulseDepth.getValuef() / 100.0f;
    float waveFreq = waveLength.getValuef();
    int mode = (int)animMode.getValuef();
    
    float brightnessModulation = 0f;
    
    switch (mode) {
      case 0: // Tile Chase
        float tileEdgePos = getTileEdgePosition(u, v, currIdx, cols, rows, sz);
        if (tileEdgePos >= 0) {
          brightnessModulation = calculateChaseEffect(tileEdgePos, t, animSpeed);
        } else {
          float facePerimPos = getPerimeterPosition(u, v, cols, rows);
          if (facePerimPos >= 0) {
            float chasePhase = (t * animSpeed) % 1.0f;
            brightnessModulation = 0.2f + 0.8f * (float)Math.sin((facePerimPos + chasePhase) * Math.PI * 2);
          } else {
            brightnessModulation = 0.1f;
          }
        }
        break;
        
      case 1: // Radial
        float dist = (float)Math.sqrt(u * u + v * v);
        float radialPhase = (t * animSpeed * 2.0f) - (dist * waveFreq);
        brightnessModulation = 0.3f + 0.7f * (0.5f + 0.5f * (float)Math.sin(radialPhase));
        break;
        
      case 2: // Serpentine
        float serpentine = (float)Math.sin(t * animSpeed * 2.0f + (u + v) * waveFreq);
        brightnessModulation = 0.2f + 0.8f * (0.5f + 0.5f * serpentine);
        break;
        
      case 3: // Simple pulse
      default:
        float pulse = (float)Math.sin(t * animSpeed * 3.0f);
        brightnessModulation = 0.4f + 0.6f * (0.5f + 0.5f * pulse);
        break;
    }
    
    float staticBright = 1.0f;
    float animatedBright = brightnessModulation;
    float finalModulation = staticBright + (animatedBright - staticBright) * pulseAmount;
    
    return Math.max(5f, Math.min(100f, baseBright * finalModulation));
  }
  
  // Calculate edge brightness for cylinder
  private float calculateCylinderEdgeBrightness(float theta, float z, float t, int currIdx,
                                               int rings, int pointsPerRing, float sz) {
    float animSpeed = edgeSpeed.getValuef();
    float baseBright = edgeBrightness.getValuef();
    float pulseAmount = pulseDepth.getValuef() / 100.0f;
    float waveFreq = waveLength.getValuef();
    int mode = (int)animMode.getValuef();
    
    float brightnessModulation = 0f;
    
    // Convert cylindrical coordinates to UV for consistency
    float u = theta / (2.0f * (float)Math.PI) - 0.5f;
    float v = z - 0.5f;
    
    switch (mode) {
      case 0: // Tile Chase - adapted for cylindrical coordinates
        float tileEdgePos = getCylinderEdgePosition(theta, z, currIdx, rings, pointsPerRing, sz);
        if (tileEdgePos >= 0) {
          brightnessModulation = calculateChaseEffect(tileEdgePos, t, animSpeed);
        } else {
          // Fallback to circumferential chase
          float circumPos = theta / (2.0f * (float)Math.PI);
          float chasePhase = (t * animSpeed) % 1.0f;
          brightnessModulation = 0.2f + 0.8f * (float)Math.sin((circumPos + chasePhase) * Math.PI * 4);
        }
        break;
        
      case 1: // Radial - from cylinder axis
        float radialDist = (float)Math.sqrt(u * u + v * v);
        float radialPhase = (t * animSpeed * 2.0f) - (radialDist * waveFreq);
        brightnessModulation = 0.3f + 0.7f * (0.5f + 0.5f * (float)Math.sin(radialPhase));
        break;
        
      case 2: // Serpentine - helical pattern
        float helical = (float)Math.sin(t * animSpeed * 2.0f + theta * waveFreq + z * waveFreq * 2);
        brightnessModulation = 0.2f + 0.8f * (0.5f + 0.5f * helical);
        break;
        
      case 3: // Simple pulse
      default:
        float pulse = (float)Math.sin(t * animSpeed * 3.0f);
        brightnessModulation = 0.4f + 0.6f * (0.5f + 0.5f * pulse);
        break;
    }
    
    float staticBright = 1.0f;
    float animatedBright = brightnessModulation;
    float finalModulation = staticBright + (animatedBright - staticBright) * pulseAmount;
    
    return Math.max(5f, Math.min(100f, baseBright * finalModulation));
  }
  
  // Helper method for chase effect calculation
  private float calculateChaseEffect(float edgePosition, float t, float animSpeed) {
    float chasePhase = (t * animSpeed) % 1.0f;
    int numRepeats = (int)repeatCount.getValuef();
    float chaseWidthVal = chaseWidth.getValuef();
    
    float brightnessModulation = 0.05f; // Dim background
    
    for (int i = 0; i < numRepeats; i++) {
      float segmentStart = (i / (float)numRepeats + chasePhase) % 1.0f;
      float segmentEnd = (segmentStart + chaseWidthVal) % 1.0f;
      
      boolean inSegment = false;
      if (segmentEnd > segmentStart) {
        inSegment = (edgePosition >= segmentStart && edgePosition <= segmentEnd);
      } else {
        inSegment = (edgePosition >= segmentStart || edgePosition <= segmentEnd);
      }
      
      if (inSegment) {
        float segmentProgress;
        if (segmentEnd > segmentStart) {
          segmentProgress = (edgePosition - segmentStart) / (segmentEnd - segmentStart);
        } else {
          if (edgePosition >= segmentStart) {
            segmentProgress = (edgePosition - segmentStart) / (1.0f - segmentStart + segmentEnd);
          } else {
            segmentProgress = (1.0f - segmentStart + edgePosition) / (1.0f - segmentStart + segmentEnd);
          }
        }
        float segmentBrightness = (float)Math.sin(segmentProgress * Math.PI);
        brightnessModulation = Math.max(brightnessModulation, 0.3f + 0.7f * segmentBrightness);
        break;
      }
    }
    
    return brightnessModulation;
  }

  private boolean shapeTest(int idx, float u, float v, float sz) {
    switch (idx) {
      case 0:
        return triangleTiling(u, v, sz);
      case 1:
        return squareTiling(u, v, sz);
      case 2:
        return triangleTiling(u, v, sz * 1.5f);
      default: return false;
    }
  }

  private boolean triangleTiling(float u, float v, float sz) {
    float qx = u * sz;
    float qy = v * sz;
    float x = qx - Math.round(qy * 0.57735f);
    float y = qy;
    float rx = x - (float)Math.floor(x + 0.5f);
    float ry = y - (float)Math.floor(y + 0.5f);
    return Math.abs(rx) + Math.abs(ry) < 0.5f;
  }

  private boolean triangleTilingRot(float u, float v, float sz) {
    float angle = (float)(Math.PI / 3.0);
    float cu = u * (float)Math.cos(angle) - v * (float)Math.sin(angle);
    float cv = u * (float)Math.sin(angle) + v * (float)Math.cos(angle);
    return triangleTiling(cu, cv, sz);
  }

  private boolean squareTiling(float u, float v, float sz) {
    int ix = (int)Math.floor(u * sz + 0.5f);
    int iy = (int)Math.floor(v * sz + 0.5f);
    return ((ix + iy) & 1) == 0;
  }

  private boolean crosshatchTiling(float u, float v, float sz) {
    boolean a = squareTiling(u, v, sz);
    boolean b = triangleTilingRot(u, v, sz);
    return a ^ b;
  }
}
