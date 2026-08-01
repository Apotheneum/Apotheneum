package apotheneum.drmrrdmr;

import apotheneum.ApotheneumPattern;
import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.Apotheneum.Cylinder.Ring;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/drmrrdmr")
@LXComponent.Name("Rebirth Grid Reborn")
public class RebirthGridReborn extends ApotheneumPattern {

  private final CompoundParameter evolution = new CompoundParameter("Evolution", 0.5, 0.0, 1.0)
    .setDescription("Grid evolution progress");
  private final CompoundParameter gridScale = new CompoundParameter("Grid", 6.0, 3.0, 12.0)
    .setDescription("Base grid density");
  private final CompoundParameter hybridMix = new CompoundParameter("Hybrid", 0.7, 0.0, 1.0)
    .setDescription("Hybrid tiling blend");
  private final CompoundParameter growthRate = new CompoundParameter("Growth", 0.8, 0.1, 2.0)
    .setDescription("Growth animation speed");
  private final CompoundParameter organicness = new CompoundParameter("Organic", 0.6, 0.0, 1.0)
    .setDescription("Organic distortion amount");
  private final CompoundParameter exteriorBrightness = new CompoundParameter("Ext Bright", 85.0, 30.0, 100.0)
    .setDescription("Exterior brightness");
  private final CompoundParameter interiorBrightness = new CompoundParameter("Int Bright", 85.0, 30.0, 100.0)
    .setDescription("Interior brightness");
  private final CompoundParameter saturation = new CompoundParameter("Sat", 70.0, 0.0, 100.0)
    .setDescription("Color saturation");
  private final BooleanParameter symmetricGrowth = new BooleanParameter("Symmetric", true)
    .setDescription("Symmetric growth pattern");

  private float time = 0f;
  private int[] exteriorCache;
  private int[] interiorCache;

  // Pre-computed values for hybrid tiling patterns
  private static final float SQRT3 = 1.73205080757f;

  // Constants for the O(1) sine range-reduction
  private static final float PI = (float) Math.PI;
  private static final float HALF_PI = PI * 0.5f;
  private static final float TWO_PI = PI * 2f;

  // Interior runs on the same evolution "style" as the exterior (same tiling
  // weights, same brightness/saturation curve) so it doesn't collapse into a
  // sparse, dim, opposite-phase regime. It's kept visually distinct by
  // running on a time offset - a different, still-animating moment of the
  // same evolving system - rather than a mirrored or inverted one.
  private static final float INTERIOR_TIME_OFFSET = 4.2f;

  // t (time, or time+INTERIOR_TIME_OFFSET) grows without bound for the life
  // of the pattern. Every other use of t is already naturally periodic (fed
  // through linearSin(), or reduced mod hueArray.length), but the growth-mask
  // radius below adds t directly with no such wrap - left alone, it would
  // permanently exceed the maximum possible r (~0.707) after a few seconds,
  // and the Symmetric mask would never be visible again for the rest of the
  // run. Wrapping just this contribution into a repeating cycle keeps the
  // "grow from center" animation actually recurring.
  private static final float GROWTH_CYCLE_PERIOD = 6.0f;

  // Evolution color palette - from old forms to new hybrid
  private static final float[] OLD_FORM_HUES = { 20f, 40f, 60f };      // Warm dissolution
  private static final float[] HYBRID_HUES = { 180f, 200f, 220f, 240f, 260f }; // Cool emergence
  private static final float[] NEW_FORM_HUES = { 120f, 140f, 160f };   // Fresh green growth

  public RebirthGridReborn(LX lx) {
    super(lx);
    addParameter("Evolution", this.evolution);
    addParameter("Grid", this.gridScale);
    addParameter("Hybrid", this.hybridMix);
    addParameter("Growth", this.growthRate);
    addParameter("Organic", this.organicness);
    addParameter("Ext Bright", this.exteriorBrightness);
    addParameter("Int Bright", this.interiorBrightness);
    addParameter("Sat", this.saturation);
    addParameter("Symmetric", this.symmetricGrowth);
  }

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0) * growthRate.getValuef();

    // Render cube with dual-cache optimization
    Cube cube = Apotheneum.cube;
    if (cube != null) {
      Face referenceFace = cube.exterior.faces[0];
      computeExteriorPattern(referenceFace);

      for (Face face : cube.exterior.faces) {
        copyFromExteriorCache(face);
      }

      if (cube.interior != null) {
        computeInteriorPattern(cube.interior.faces[0]);
        for (Face face : cube.interior.faces) {
          copyFromInteriorCache(face);
        }
      }
    }

    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder != null) {
      processCylinder(cylinder);
    }
  }

  private void computeExteriorPattern(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;

    if (exteriorCache == null || exteriorCache.length != cols * rows) {
      exteriorCache = new int[cols * rows];
    }

    computeFacePattern(face, exteriorCache, false);
  }

  private void computeInteriorPattern(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;

    if (interiorCache == null || interiorCache.length != cols * rows) {
      interiorCache = new int[cols * rows];
    }

    computeFacePattern(face, interiorCache, true);
  }

  private void computeFacePattern(Face face, int[] cache, boolean isInterior) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);

    float evol = evolution.getValuef();
    float grid = gridScale.getValuef();
    float hybrid = hybridMix.getValuef();
    float organic = organicness.getValuef();
    boolean symmetric = symmetricGrowth.getValueb();
    float satBase = saturation.getValuef();
    float briBase = isInterior ? interiorBrightness.getValuef() : exteriorBrightness.getValuef();
    float t = isInterior ? time + INTERIOR_TIME_OFFSET : time;

    int cacheIndex = 0;
    for (int rowIdx = 0; rowIdx < rows; rowIdx++) {
      for (int colIdx = 0; colIdx < cols; colIdx++) {
        float u = colIdx * invCols - 0.5f;
        float v = rowIdx * invRows - 0.5f;

        cache[cacheIndex++] = calculateHybridTileColor(u, v, grid, hybrid, organic, evol, symmetric, isInterior, satBase, briBase, t);
      }
    }
  }

  private void copyFromExteriorCache(Face face) {
    int cols = face.columns.length;
    int cacheIndex = 0;

    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        colors[p.index] = exteriorCache[cacheIndex++];
      }
    }
  }

  private void copyFromInteriorCache(Face face) {
    int cols = face.columns.length;
    int cacheIndex = 0;

    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        colors[p.index] = interiorCache[cacheIndex++];
      }
    }
  }

  private void processCylinder(Cylinder cylinder) {
    processCylinderOrientation(cylinder.exterior, false);

    if (cylinder.interior != null) {
      processCylinderOrientation(cylinder.interior, true);
    }
  }

  private void processCylinderOrientation(Cylinder.Orientation orientation, boolean isInterior) {
    Ring[] rings = orientation.rings;
    int numRings = rings.length;

    float evol = evolution.getValuef();
    float grid = gridScale.getValuef();
    float hybrid = hybridMix.getValuef();
    float organic = organicness.getValuef();
    boolean symmetric = symmetricGrowth.getValueb();
    float satBase = saturation.getValuef();
    float briBase = isInterior ? interiorBrightness.getValuef() : exteriorBrightness.getValuef();
    float t = isInterior ? time + INTERIOR_TIME_OFFSET : time;

    for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
      Ring ring = rings[ringIndex];
      int pointsPerRing = ring.points.length;

      float v = (float)ringIndex / Math.max(1, numRings - 1) - 0.5f;

      for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
        LXPoint p = ring.points[pointIndex];
        float u = (float)pointIndex / pointsPerRing - 0.5f;

        int color = calculateHybridTileColor(u, v, grid, hybrid, organic, evol, symmetric, isInterior, satBase, briBase, t);
        colors[p.index] = color;
      }
    }
  }

  private int calculateHybridTileColor(float x, float y, float grid, float hybrid, float organic, float evol, boolean symmetric, boolean isInterior, float satBase, float briBase, float t) {
    // Apply organic distortion that grows with evolution
    float organicFactor = organic * evol;
    float distortX = x + organicFactor * 0.1f * linearSin(y * 8f + t * 0.7f);
    float distortY = y + organicFactor * 0.1f * linearSin(x * 8f + t * 0.5f);

    // Three tiling systems blend together during evolution
    float squareTile = calculateSquareTile(distortX, distortY, grid);
    float hexTile = calculateHexTile(distortX, distortY, grid * 0.8f);
    float organicTile = calculateOrganicTile(distortX, distortY, grid * 1.2f, t);

    // Evolution phases: 0 = square dominant, 0.5 = hybrid, 1 = organic dominant
    float squareWeight, hexWeight, organicWeight;

    if (evol < 0.33f) {
      float phase = evol / 0.33f;
      squareWeight = 1.0f - phase * 0.7f;
      hexWeight = phase * hybrid;
      organicWeight = phase * (1.0f - hybrid) * 0.3f;
    } else if (evol < 0.67f) {
      float phase = (evol - 0.33f) / 0.34f;
      squareWeight = 0.3f - phase * 0.2f;
      hexWeight = hybrid * (0.3f + phase * 0.5f);
      organicWeight = (1.0f - hybrid) * phase * 0.7f + 0.1f;
    } else {
      float phase = (evol - 0.67f) / 0.33f;
      squareWeight = 0.1f - phase * 0.1f;
      hexWeight = hybrid * (0.8f - phase * 0.3f);
      organicWeight = 0.8f + phase * 0.2f;
    }

    float combinedIntensity = squareTile * squareWeight + hexTile * hexWeight + organicTile * organicWeight;

    if (combinedIntensity < 0.05f) return 0;

    // Growth animation - patterns emerge from center if symmetric
    float r = (float)Math.sqrt(x*x + y*y);
    float growthPhase = t % GROWTH_CYCLE_PERIOD;
    float growthRadius = evol * (symmetric ? 0.8f : 1.2f) + growthPhase * 0.3f;
    float growthMask = 1.0f;

    if (symmetric && r > growthRadius) {
      growthMask = Math.max(0f, 1.0f - (r - growthRadius) * 3f);
    }

    combinedIntensity *= growthMask;

    if (combinedIntensity < 0.05f) return 0;

    // Color evolution from old to new forms
    float[] hueArray;
    float colorEvol = evol;

    if (colorEvol < 0.3f) {
      hueArray = OLD_FORM_HUES;
    } else if (colorEvol < 0.7f) {
      hueArray = HYBRID_HUES;
      colorEvol = (colorEvol - 0.3f) / 0.4f;
    } else {
      hueArray = NEW_FORM_HUES;
      colorEvol = (colorEvol - 0.7f) / 0.3f;
    }

    // Select hue based on position and evolution
    int hueIndex = (int)((colorEvol + r * 2f + t * 0.4f) * hueArray.length) % hueArray.length;
    float baseHue = hueArray[hueIndex];

    // Subtle hue shift with evolution - interior gets an additional rotation
    // so it reads as distinct from the exterior even though it shares the
    // same tiling style and brightness curve.
    float evolHue = (baseHue + evol * 30f + (isInterior ? 180f : 0f)) % 360f;
    if (evolHue < 0) evolHue += 360f;

    float evolSaturation = satBase * (0.4f + 0.6f * evol);
    float finalBrightness = Math.min(combinedIntensity * briBase * (0.6f + 0.4f * evol), 100f);

    return LXColor.hsb(evolHue, evolSaturation, finalBrightness);
  }

  private float calculateSquareTile(float x, float y, float scale) {
    float gridX = Math.abs((x * scale) % 1.0f - 0.5f);
    float gridY = Math.abs((y * scale) % 1.0f - 0.5f);

    float lineThickness = 0.15f;
    boolean onLineX = gridX > 0.5f - lineThickness;
    boolean onLineY = gridY > 0.5f - lineThickness;

    if (onLineX || onLineY) {
      float distToEdge = Math.min(
        onLineX ? (0.5f - gridX) / lineThickness : 1f,
        onLineY ? (0.5f - gridY) / lineThickness : 1f
      );
      return Math.max(0f, distToEdge);
    }
    return 0f;
  }

  private float calculateHexTile(float x, float y, float scale) {
    float hexX = x * scale;
    float hexY = y * scale * SQRT3;

    float col = hexX;
    float row = hexY;

    float hexCenterX = (float)Math.floor(col + 0.5f);
    float hexCenterY = (float)Math.floor(row + 0.5f);

    float dx = Math.abs(col - hexCenterX);
    float dy = Math.abs(row - hexCenterY);
    float hexDist = Math.max(dx, dy * 0.866f + dx * 0.5f);

    float hexSize = 0.4f;
    if (hexDist < hexSize) {
      return 1.0f - (hexDist / hexSize);
    }
    return 0f;
  }

  private float calculateOrganicTile(float x, float y, float scale, float t) {
    float cellX = x * scale + 0.3f * linearSin(y * 3f + t * 0.6f);
    float cellY = y * scale + 0.3f * linearSin(x * 3f + t * 0.4f);

    float gridX = cellX - (float)Math.floor(cellX);
    float gridY = cellY - (float)Math.floor(cellY);

    float dx = gridX - 0.5f;
    float dy = gridY - 0.5f;
    float cellDist = dx*dx + dy*dy;

    float cellSize = 0.15f + 0.1f * linearSin(cellX + cellY + t);
    if (cellDist < cellSize) {
      return 1.0f - (cellDist / cellSize);
    }
    return 0f;
  }

  // Linear approximation of sine for performance - good enough for organic
  // distortion. Range-reduction to (-PI, PI] via a single modulo (O(1)
  // regardless of how large x grows over a long-running session).
  private float linearSin(float x) {
    x = x % TWO_PI;
    if (x > PI) {
      x -= TWO_PI;
    } else if (x < -PI) {
      x += TWO_PI;
    }

    if (x >= 0) {
      if (x <= HALF_PI) {
        return x / HALF_PI;
      } else {
        return 1.0f - (x - HALF_PI) / HALF_PI;
      }
    } else {
      if (x >= -HALF_PI) {
        return x / HALF_PI;
      } else {
        return -1.0f - (x + HALF_PI) / HALF_PI;
      }
    }
  }
}
