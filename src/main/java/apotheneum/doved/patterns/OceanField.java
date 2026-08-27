package apotheneum.doved.patterns;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.utils.LXUtils;

/**
 * Shared world-space water surface used by the ocean pattern family.
 *
 * <p>{@link #levelEnvelope(double)} is {@code sin(PI * level)}. At
 * {@code level = 1.0}, the water is at maximum height while the meniscus,
 * agitation, and surge are all zero. Level is therefore not a plain height;
 * patterns must use both {@link #surfaceY(double, double, double, double)} and
 * {@code levelEnvelope} to agree with Flood at the extremes of the macro's
 * travel.
 *
 * <p>Row index 0 is the top of a column ({@code column.points[0]}).
 *
 * <p>World {@code point.y} increases upward, so {@code floorY} is the minimum
 * y-coordinate in the geometry bounds.
 */
public final class OceanField {

  // The surface/shallow and deep water anchors are supplied by the palette-linked
  // primary/secondary roles on ColorNativePattern (see Flood and Breaker), not by fixed
  // constants here.

  /**
   * Foam is white because it is a bubble scatterer, not because of the water's pigment: real
   * ocean foam is white regardless of the water color beneath it, so unlike the surface/deep
   * anchors it deliberately does not track a palette stop and stays a fixed constant.
   */
  public static final int MENISCUS_COLOR = LXColor.hsb(178, 28, 100);

  /**
   * Column-index offset that aligns arc-length 0 on the cube ring with arc-length
   * 0 on the cylinder ring, so azimuthal terms stay in phase across the seam.
   *
   * <p>Cube ring order is front(0-49), right(50-99), back(100-149), left(150-199)
   * (Apotheneum.Cube.Orientation). Cylinder column 0 sits at world (x, z) =
   * (cubeSide/2, cubeSide/2 - radius) - straight out from the shared center
   * toward the front face (Apotheneum.lxf). Front-face column i sits at world x
   * = nodeInset + i * nodeSpacing, which equals cubeSide/2 (the same azimuth) at
   * i = (GRID_WIDTH - 1) / 2.0 = 24.5, for any nodeInset/nodeSpacing, because the
   * 50 columns are spaced evenly across the symmetric [nodeInset, cubeSide -
   * nodeInset] interval. Both rings also increase in the same rotational sense
   * (front -> right -> back -> left matches increasing cylinder azimuth), so no
   * direction flip is needed - only this offset.
   */
  public static final double CUBE_S_OFFSET = (Apotheneum.GRID_WIDTH - 1) / 2.0;

  private OceanField() {}

  public static double surfaceY(
      double level,
      double floorY,
      double ceilingY,
      double rowPitch) {
    return floorY - .5 * rowPitch + level * (ceilingY - floorY + rowPitch);
  }

  public static double levelEnvelope(double level) {
    return Math.sin(Math.PI * level);
  }

  public static boolean isAvailableCell(int row, int available) {
    return row >= 0 && row < available;
  }

  public static double waterCoverage(double signedRows) {
    return smoothstep(-.5, .5, signedRows);
  }

  public static double meniscus(double signedRows, double widthRows) {
    final double halfWidth = .5 * widthRows;
    return 1 - smoothstep(halfWidth, halfWidth + 1, Math.abs(signedRows));
  }

  /**
   * Normalized arc-length position around a ring: 0..1, wrapping. columnIndex
   * is the column's position in orientation.columns() order; columnOffset
   * shifts which column lands at s = 0 (see {@link #CUBE_S_OFFSET}); ringLength
   * is the column count for that orientation's ring (200 cube, 120 cylinder).
   */
  public static double arcLength(int columnIndex, double columnOffset, int ringLength) {
    final double s = (columnIndex - columnOffset) / ringLength;
    return s - Math.floor(s);
  }

  /**
   * Azimuthal undulation as a function of arc length s (0..1, wrapping).
   * Integer wavenumbers make s = 0 and s = 1 agree exactly, so the seam at the
   * ring's start/end closes with no discontinuity - see docs/ocean-and-organic-
   * patterns.md section 1.1.
   */
  public static double waveRows(double s, double phase, double amount) {
    return amount * (
      .82 * Math.sin(LX.TWO_PI * 2 * s + phase) +
      .42 * Math.sin(LX.TWO_PI * 3 * s - .71 * phase + 1.37)
    );
  }

  /**
   * Raised-cosine falloff of a surge centered at {@code position}, both in
   * arc-length s (0..1). Distance wraps across the s = 0 / s = 1 seam so the
   * surge reads as travelling around the ring rather than stopping at an
   * artificial edge. {@code position} need not itself be pre-wrapped - it is
   * taken modulo 1 here - so callers can track it as a monotonically increasing
   * value while active.
   */
  public static double surgeProfile(double s, double position, double width) {
    final double halfWidth = .5 * width;
    final double wrappedPosition = position - Math.floor(position);
    double distance = Math.abs(s - wrappedPosition);
    distance = Math.min(distance, 1 - distance);
    if (distance >= halfWidth) {
      return 0;
    }
    return .5 + .5 * Math.cos(Math.PI * distance / halfWidth);
  }

  public static double sparkle(int pointIndex, double elapsedSeconds, double amount) {
    final double pointOffset = hash01(pointIndex * 0x1f123bb5);
    final double localTime = elapsedSeconds * 2.2 + pointOffset;
    final int cycle = (int) Math.floor(localTime);
    final double basis = localTime - cycle;
    final double gate = hash01(pointIndex * 0x6d2b79f5 ^ cycle * 0x1b873593);
    if (gate < 1 - .12 * amount) {
      return 0;
    }
    final double envelope = Math.sin(Math.PI * basis);
    return amount * envelope * envelope * (.55 + .45 * hash01(pointIndex ^ cycle));
  }

  public static double smoothstep(double edge0, double edge1, double value) {
    final double t = LXUtils.clamp((value - edge0) / (edge1 - edge0), 0, 1);
    return t * t * (3 - 2 * t);
  }

  public static double hash01(int value) {
    int hash = value;
    hash ^= hash >>> 16;
    hash *= 0x7feb352d;
    hash ^= hash >>> 15;
    hash *= 0x846ca68b;
    hash ^= hash >>> 16;
    return (hash & 0x7fffffff) / (double) Integer.MAX_VALUE;
  }

  /**
   * Reusable cache of the shared cube/cylinder water geometry. Construct once
   * per pattern and call {@link #update()} from the render loop; unchanged model
   * geometry returns without allocating or sweeping points again.
   */
  public static final class GeometryCache {

    private Apotheneum.Cube cachedCube;
    private Apotheneum.Cylinder cachedCylinder;
    private double floorY;
    private double ceilingY;
    private double rowPitch;
    private final Bounds bounds = new Bounds();

    public void update() {
      if ((this.cachedCube == Apotheneum.cube) &&
          (this.cachedCylinder == Apotheneum.cylinder)) {
        return;
      }

      this.cachedCube = Apotheneum.cube;
      this.cachedCylinder = Apotheneum.cylinder;

      this.bounds.reset();
      includeGeometry(Apotheneum.cube.exterior);
      includeGeometry(Apotheneum.cylinder.exterior);

      this.floorY = this.bounds.minY;
      this.ceilingY = this.bounds.maxY;

      final LXPoint[] referenceColumn = Apotheneum.cube.exterior.columns[0].points;
      this.rowPitch = Math.abs(referenceColumn[1].y - referenceColumn[0].y);
      if (this.rowPitch <= 0) {
        this.rowPitch = 1;
      }
    }

    public double floorY() {
      return this.floorY;
    }

    public double ceilingY() {
      return this.ceilingY;
    }

    public double rowPitch() {
      return this.rowPitch;
    }

    private void includeGeometry(Apotheneum.Orientation orientation) {
      int columnIndex = 0;
      for (Apotheneum.Column column : orientation.columns()) {
        final int available = orientation.available(columnIndex++);
        for (int row = 0; row < available; ++row) {
          this.bounds.include(column.points[row]);
        }
      }
    }
  }

  private static final class Bounds {
    double minY;
    double maxY;

    void reset() {
      this.minY = Double.POSITIVE_INFINITY;
      this.maxY = Double.NEGATIVE_INFINITY;
    }

    void include(LXPoint point) {
      this.minY = Math.min(this.minY, point.y);
      this.maxY = Math.max(this.maxY, point.y);
    }
  }

  /**
   * Caches the world-space geometry a pattern needs to sweep a planar front through the
   * model along a fixed travel direction: the model's XZ center, the direction's unit vector,
   * and {@code R} -- the half-extent of the model along that direction, i.e. the max of
   * {@code |d(point)|} over every point, where {@code d(point) = (point.x - cx) * dirX +
   * (point.z - cz) * dirZ}.
   *
   * <p>The XZ center only depends on the model, so it is recomputed on model change. {@code R}
   * and the direction vector additionally depend on the travel direction, so they are
   * recomputed only when {@link #update(double)} is called with a new angle -- not per frame,
   * not per point. A caller that sweeps the model every frame with an unchanging direction pays
   * only the cost of the two cached-value comparisons.
   */
  public static final class PlanarTravelCache {

    private Apotheneum.Cube cachedCube;
    private Apotheneum.Cylinder cachedCylinder;
    private double centerX;
    private double centerZ;
    private double cachedDirectionRadians = Double.NaN;
    private double dirX;
    private double dirZ;
    private double halfExtent;

    public void update(double directionRadians) {
      final boolean modelChanged =
        (this.cachedCube != Apotheneum.cube) || (this.cachedCylinder != Apotheneum.cylinder);
      if (modelChanged) {
        this.cachedCube = Apotheneum.cube;
        this.cachedCylinder = Apotheneum.cylinder;
        final double[] xzBounds = {
          Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
          Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        };
        includeXZ(Apotheneum.cube.exterior, xzBounds);
        includeXZ(Apotheneum.cylinder.exterior, xzBounds);
        this.centerX = .5 * (xzBounds[0] + xzBounds[1]);
        this.centerZ = .5 * (xzBounds[2] + xzBounds[3]);
        // Force the extent recompute below even if the direction angle itself did not change.
        this.cachedDirectionRadians = Double.NaN;
      }
      if (!modelChanged && (directionRadians == this.cachedDirectionRadians)) {
        return;
      }
      this.cachedDirectionRadians = directionRadians;
      this.dirX = Math.cos(directionRadians);
      this.dirZ = Math.sin(directionRadians);

      double maxAbsD = 0;
      maxAbsD = maxAbsDistance(Apotheneum.cube.exterior, maxAbsD);
      maxAbsD = maxAbsDistance(Apotheneum.cylinder.exterior, maxAbsD);
      this.halfExtent = maxAbsD;
    }

    public double dirX() {
      return this.dirX;
    }

    public double dirZ() {
      return this.dirZ;
    }

    public double centerX() {
      return this.centerX;
    }

    public double centerZ() {
      return this.centerZ;
    }

    /** {@code R}: the model's half-extent along the cached travel direction. */
    public double halfExtent() {
      return this.halfExtent;
    }

    /** Signed distance of world point (x, z) along the cached travel direction. */
    public double distance(double x, double z) {
      return (x - this.centerX) * this.dirX + (z - this.centerZ) * this.dirZ;
    }

    private double maxAbsDistance(Apotheneum.Orientation orientation, double runningMax) {
      int columnIndex = 0;
      for (Apotheneum.Column column : orientation.columns()) {
        final int available = orientation.available(columnIndex++);
        for (int row = 0; row < available; ++row) {
          final LXPoint point = column.points[row];
          final double d = distance(point.x, point.z);
          runningMax = Math.max(runningMax, Math.abs(d));
        }
      }
      return runningMax;
    }

    private void includeXZ(Apotheneum.Orientation orientation, double[] xzBounds) {
      int columnIndex = 0;
      for (Apotheneum.Column column : orientation.columns()) {
        final int available = orientation.available(columnIndex++);
        for (int row = 0; row < available; ++row) {
          final LXPoint point = column.points[row];
          if (point.x < xzBounds[0]) {
            xzBounds[0] = point.x;
          }
          if (point.x > xzBounds[1]) {
            xzBounds[1] = point.x;
          }
          if (point.z < xzBounds[2]) {
            xzBounds[2] = point.z;
          }
          if (point.z > xzBounds[3]) {
            xzBounds[3] = point.z;
          }
        }
      }
    }
  }
}
