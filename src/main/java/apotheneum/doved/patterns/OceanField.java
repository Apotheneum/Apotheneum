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
 * agitation and meniscus are both zero. Level is therefore not a plain height;
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

  /** Integer spatial frequencies preserve continuity at the ring seam. */
  static final int[] WAVE_NUMBERS = { 2, 3, 5, 8 };

  /** The fixed total wave magnitude, retained as Flood's physics normalizer. */
  static final double WAVE_AMPLITUDE_SUM = .62 + .34 + .19 + .11;

  /** Smooth-swell to turbulent-chop spectral falloff limits. */
  private static final double MAX_SPECTRAL_FALLOFF = 2;
  private static final double MIN_SPECTRAL_FALLOFF = .5;

  // The surface/shallow and deep water anchors are supplied by the palette-linked
  // primary/secondary roles on ColorNativePattern (see Flood), not by fixed
  // constants here.

  /**
   * Foam is white because it is a bubble scatterer, not because of the water's pigment: real
   * ocean foam is white regardless of the water color beneath it, so unlike the surface/deep
   * anchors it deliberately does not track a palette stop and stays a fixed constant.
   */

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
  /**
   * Resolves the four spectral amplitudes for {@code turbulence} into a caller-owned array.
   * Their sum is always {@link #WAVE_AMPLITUDE_SUM}, so turbulence changes wave shape without
   * becoming another wave-height control.
   */
  static void resolveWaveAmplitudes(double turbulence, double[] amplitudes) {
    if (amplitudes.length < WAVE_NUMBERS.length) {
      throw new IllegalArgumentException("Need one amplitude per wave number");
    }
    final double falloff = spectralFalloff(turbulence);
    double sum = 0;
    for (int i = 0; i < WAVE_NUMBERS.length; ++i) {
      final double amplitude = Math.pow(WAVE_NUMBERS[i], -falloff);
      amplitudes[i] = amplitude;
      sum += amplitude;
    }
    final double normalization = WAVE_AMPLITUDE_SUM / sum;
    for (int i = 0; i < WAVE_NUMBERS.length; ++i) {
      amplitudes[i] *= normalization;
    }
  }

  static double spectralFalloff(double turbulence) {
    return LXUtils.lerp(MAX_SPECTRAL_FALLOFF, MIN_SPECTRAL_FALLOFF, turbulence);
  }

  public static double waveRows(double s, double phase, double amount, double[] amplitudes) {
    return amount * (
      amplitudes[0] * Math.sin(LX.TWO_PI * WAVE_NUMBERS[0] * s + phase) +
      amplitudes[1] * Math.sin(LX.TWO_PI * WAVE_NUMBERS[1] * s - Math.sqrt(3.0 / 2) * phase + 1.37) +
      amplitudes[2] * Math.sin(LX.TWO_PI * WAVE_NUMBERS[2] * s + Math.sqrt(5.0 / 2) * phase + 2.71) +
      amplitudes[3] * Math.sin(LX.TWO_PI * WAVE_NUMBERS[3] * s - 2 * phase + .58)
    );
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

}
