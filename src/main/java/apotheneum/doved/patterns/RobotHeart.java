package apotheneum.doved.patterns;

import java.io.IOException;
import java.util.Arrays;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import apotheneum.doved.components.Wireframe;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

/**
 * The Robot Heart, as a 3D object suspended inside Apotheneum.
 *
 * The heart is a real wireframe — the 36 LED bars of the Robot Heart sculpture,
 * baked to {@code /models/robot-heart.obj} — held at the centre of the structure
 * and drawn by projection onto the surfaces enclosing it.
 *
 * Each face projects the heart orthographically along that face's own normal, so
 * the four walls carry four true side-views of one object: walk around the cube
 * and the heart turns with you. Rotation and scale drive all four faces from one
 * shared transform, which is what keeps them agreeing.
 *
 * Nothing here animates itself. Every frame is a pure function of the current
 * parameter values — no accumulated angle, no internal clock, no read of the
 * tempo — so all motion comes from modulators the way it does everywhere else:
 * map a ramp to Yaw to turn it, an envelope to Size to make it beat. That also
 * makes the pattern frame-rate independent and exact under snapshots and clips,
 * which an accumulator would not be.
 *
 * The projection is derived from live point positions rather than assumed axes,
 * so a face lights the physically correct pixels whichever way its columns run.
 *
 * Cube only, deliberately. A convex surface cannot present a correct silhouette
 * from every angle at once, so the cylinder has no equivalent of this that reads
 * as the same object.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Robot Heart")
public class RobotHeart extends ApotheneumPattern implements UIDeviceControls<RobotHeart> {

  private static final String HEART_RESOURCE = "/models/robot-heart.obj";

  private static final int CUBE_WIDTH = Apotheneum.GRID_WIDTH;
  private static final int CUBE_HEIGHT = Apotheneum.GRID_HEIGHT;
  private static final int CYLINDER_WIDTH = Apotheneum.RING_LENGTH;
  private static final int CYLINDER_HEIGHT = Apotheneum.CYLINDER_HEIGHT;

  /**
   * The two shells a view direction draws on: the cube wall it looks through,
   * and the near arc of the ring standing behind that wall.
   *
   * There is no far shell. Content sliced onto the wall opposite a viewer, or
   * onto the arc curving away from them, reads as a second object rather than as
   * the back of the first one, so this draws only what a viewer meets first.
   */
  private static final int LAYER_WALL = 0;
  private static final int LAYER_ARC = 1;

  /** Longest sub-segment, in pixels, when following a line across the cylinder's curve. */
  private static final float ARC_SEGMENT_PIXELS = 2f;

  private static final int MAX_ARC_SUBDIVISIONS = 64;
  private static final double TAU = 2 * Math.PI;

  /** Sample spacing along a stroke; below one pixel so a thick stroke stays solid. */
  private static final float STROKE_SAMPLE_PIXELS = .5f;

  /**
   * How to draw the sculpture. Dots stamp its own LED positions, one mark per
   * real pixel of the Robot Heart; lines stroke each bar as a continuous run.
   */
  public enum Style {
    DOTS("Dots"),
    LINES("Lines");

    private final String label;

    private Style(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  /**
   * Which parts of the sculpture's cage to draw. The bars are its twenty vertical
   * ribs and carry its shape on their own; the rings are the horizontal hoops,
   * which at cube-face resolution fill the silhouette in rather than describing it.
   */
  public enum Structure {
    BARS("Bars", "bar"),
    RINGS("Rings", "ring"),
    BOTH("Both", "");

    private final String label;
    private final String groupPrefix;

    private Structure(String label, String groupPrefix) {
      this.label = label;
      this.groupPrefix = groupPrefix;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter size =
    new CompoundParameter("Size", .7, .1, 1.4)
    .setDescription("Heart height as a fraction of the structure's height. Map an envelope here to make it beat");

  public final CompoundParameter elevation =
    new CompoundParameter("Elev", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Height of the heart's centre within the structure");

  public final CompoundParameter thickness =
    new CompoundParameter("Thick", 1, .5, 4)
    .setDescription("Width of each dot or bar, in pixels");

  public final EnumParameter<Structure> structure =
    new EnumParameter<Structure>("Cage", Structure.BOTH)
    .setDescription("Which parts of the sculpture's cage to draw");

  public final EnumParameter<Style> style =
    new EnumParameter<Style>("Style", Style.DOTS)
    .setDescription("Stamp the sculpture's own LEDs as dots, or stroke its bars as lines");

  public final CompoundParameter yaw =
    new CompoundParameter("Yaw", 0, 0, 360)
    .setDescription("Rotation about the vertical axis, in degrees. Map a sawtooth to turn the heart");

  public final CompoundParameter tilt =
    new CompoundParameter("Tilt", 0, -60, 60)
    .setDescription("Tip of the heart towards or away from the viewer, in degrees");

  public final CompoundParameter shading =
    new CompoundParameter("Shade", .65)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How much the far side of the heart is dimmed, which is what makes it read as solid");

  public final CompoundParameter depth =
    new CompoundParameter("Depth", .35)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How far the heart opens into the depth of the structure. At zero it lies flat on the cube walls; turning it up hands its back away to the cylinder behind them");

  public final CompoundParameter arc =
    new CompoundParameter("Arc", .75, .15, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How much of each cylinder arc is used. Lower crops the grazing edges and spills more onto the cube");

  private final Wireframe heart;

  /** Edge indices to draw for each {@link Structure}, resolved once at construction. */
  private final int[][] structureEdges;

  /** Vertex indices to stamp for each {@link Structure}, resolved once at construction. */
  private final int[][] structureVertices;

  /** Heart vertices in world space, rebuilt once per frame and shared by every surface. */
  private final float[] worldX;
  private final float[] worldY;
  private final float[] worldZ;

  private final float[] cubeRaster = new float[CUBE_WIDTH * CUBE_HEIGHT];
  private final float[] cylinderRaster = new float[CYLINDER_WIDTH * CYLINDER_HEIGHT];

  /** Per-vertex depth towards the wall being drawn, and the shell it lands on. */
  private final float[] layerDepth;
  private final int[] vertexLayer;

  /** Horizontal view frame: right axis, and the normal running front wall to back wall. */
  private float viewRx, viewRz, viewNx, viewNz;

  /** Centre of the cube in world space, refreshed each frame with the transform. */
  private float centerX, centerZ;

  /** The ring's frame, resolved once per frame and shared by placement and drawing. */
  private float cylinderAxisX, cylinderAxisZ, cylinderRadius, cylinderYTop, cylinderYStep;
  private double cylinderAzimuthZero, cylinderWinding;

  /** Scratch basis for whichever cube wall is being tested for door coverage. */
  private float wallOx, wallOy, wallOz;
  private float wallUx, wallUy, wallUz, wallULengthSq;
  private float wallVx, wallVy, wallVz, wallVLengthSq;

  /** Per-vertex projection results for the surface currently being drawn. */
  private final float[] projX;
  private final float[] projY;

  /** Per-vertex distance from the viewer, in arbitrary units; larger is farther. */
  private final float[] projDepth;

  public RobotHeart(LX lx) {
    super(lx);
    Wireframe loaded = Wireframe.EMPTY;
    try {
      loaded = Wireframe.load(HEART_RESOURCE);
    } catch (IOException iox) {
      LX.error(iox, "RobotHeart could not load " + HEART_RESOURCE + ", pattern will render black");
    }
    this.heart = loaded;
    this.structureEdges = new int[Structure.values().length][];
    this.structureVertices = new int[Structure.values().length][];
    for (Structure value : Structure.values()) {
      this.structureEdges[value.ordinal()] = this.heart.edgeGroupIndices(value.groupPrefix);
      this.structureVertices[value.ordinal()] = this.heart.vertexGroupIndices(value.groupPrefix);
    }
    this.worldX = new float[this.heart.vertexCount];
    this.worldY = new float[this.heart.vertexCount];
    this.worldZ = new float[this.heart.vertexCount];
    this.projX = new float[this.heart.vertexCount];
    this.projY = new float[this.heart.vertexCount];
    this.projDepth = new float[this.heart.vertexCount];
    this.layerDepth = new float[this.heart.vertexCount];
    this.vertexLayer = new int[this.heart.vertexCount];

    addParameter("size", this.size);
    addParameter("elevation", this.elevation);
    addParameter("thickness", this.thickness);
    addParameter("structure", this.structure);
    addParameter("style", this.style);
    addParameter("yaw", this.yaw);
    addParameter("tilt", this.tilt);
    addParameter("shading", this.shading);
    addParameter("depth", this.depth);
    addParameter("arc", this.arc);
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    if (this.heart.edgeCount == 0) {
      return;
    }

    transformHeart();

    // The heart carries no colour of its own; it renders as brightness on white
    // and takes whatever colour the channel puts on it.
    final int rgb = LXColor.WHITE;
    final int shape = this.structure.getEnum().ordinal();

    renderLayers(shape, rgb);
  }

  /**
   * Places the normalized heart into the structure: scale, tilt about X,
   * spin about Y, then translate to the centre of the cube at the chosen height.
   */
  private void transformHeart() {
    final Apotheneum.Cube.Face front = Apotheneum.cube.exterior.front;
    final Apotheneum.Cube.Face back = Apotheneum.cube.exterior.back;
    final Apotheneum.Cube.Face left = Apotheneum.cube.exterior.left;
    final Apotheneum.Cube.Face right = Apotheneum.cube.exterior.right;

    final float centerX = .25f * (front.model.cx + back.model.cx + left.model.cx + right.model.cx);
    final float centerZ = .25f * (front.model.cz + back.model.cz + left.model.cz + right.model.cz);
    this.centerX = centerX;
    this.centerZ = centerZ;
    final float yMin = front.model.yMin;
    final float yRange = front.model.yRange;

    // The heart is baked centred on the origin with height 1, so one scalar sizes it.
    final double scale = this.size.getValue() * yRange;
    final double centerY = yMin + this.elevation.getValue() * yRange;

    final double tiltRadians = Math.toRadians(this.tilt.getValue());
    final double cosTilt = Math.cos(tiltRadians);
    final double sinTilt = Math.sin(tiltRadians);

    final double spinRadians = Math.toRadians(this.yaw.getValue());
    final double cosSpin = Math.cos(spinRadians);
    final double sinSpin = Math.sin(spinRadians);

    for (int i = 0; i < this.heart.vertexCount; ++i) {
      final double x0 = this.heart.x[i] * scale;
      final double y0 = this.heart.y[i] * scale;
      final double z0 = this.heart.z[i] * scale;

      final double y1 = y0 * cosTilt - z0 * sinTilt;
      final double z1 = y0 * sinTilt + z0 * cosTilt;

      this.worldX[i] = (float) (centerX + x0 * cosSpin + z1 * sinSpin);
      this.worldY[i] = (float) (centerY + y1);
      this.worldZ[i] = (float) (centerZ - x0 * sinSpin + z1 * cosSpin);
    }
  }

  /**
   * Orthographic projection onto one cube face, along that face's own normal.
   *
   * The face's basis is read from its corner points, so the column and row axes
   * come out of the model rather than an assumption about which way it runs.
   */
  private void renderCubeFace(Apotheneum.Cube.Face face, int shape, int rgb, int layerFilter) {
    final LXPoint origin = face.columns[0].points[0];
    final LXPoint acrossEnd = face.columns[CUBE_WIDTH - 1].points[0];
    final LXPoint downEnd = face.columns[0].points[CUBE_HEIGHT - 1];

    // World-space displacement of a single column step and a single row step.
    final float ux = (acrossEnd.x - origin.x) / (CUBE_WIDTH - 1);
    final float uy = (acrossEnd.y - origin.y) / (CUBE_WIDTH - 1);
    final float uz = (acrossEnd.z - origin.z) / (CUBE_WIDTH - 1);
    final float vx = (downEnd.x - origin.x) / (CUBE_HEIGHT - 1);
    final float vy = (downEnd.y - origin.y) / (CUBE_HEIGHT - 1);
    final float vz = (downEnd.z - origin.z) / (CUBE_HEIGHT - 1);

    final float uLengthSq = ux * ux + uy * uy + uz * uz;
    final float vLengthSq = vx * vx + vy * vy + vz * vz;
    if ((uLengthSq <= 0) || (vLengthSq <= 0)) {
      return;
    }

    // The face normal, oriented to point away from a viewer standing outside the
    // face, so that a larger depth is always a farther edge.
    float nx = uy * vz - uz * vy;
    float ny = uz * vx - ux * vz;
    float nz = ux * vy - uy * vx;
    final float nLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
    if (nLength <= 0) {
      return;
    }
    nx /= nLength;
    ny /= nLength;
    nz /= nLength;
    // Towards the cube's centre, not the face's own: a face is planar, so the
    // vector to its own centroid lies in its plane and dots to zero against the
    // normal, which would leave the orientation up to the fixture's winding.
    if (nx * (this.centerX - origin.x) + nz * (this.centerZ - origin.z) < 0) {
      nx = -nx;
      ny = -ny;
      nz = -nz;
    }

    for (int i = 0; i < this.heart.vertexCount; ++i) {
      final float dx = this.worldX[i] - origin.x;
      final float dy = this.worldY[i] - origin.y;
      final float dz = this.worldZ[i] - origin.z;
      this.projX[i] = (dx * ux + dy * uy + dz * uz) / uLengthSq;
      this.projY[i] = (dx * vx + dy * vy + dz * vz) / vLengthSq;
      this.projDepth[i] = dx * nx + dy * ny + dz * nz;
    }

    Arrays.fill(this.cubeRaster, 0f);

    final float depthMin = minDepth();
    final float depthRange = Math.max(1e-4f, maxDepth() - depthMin);
    final float radius = .5f * this.thickness.getValuef();

    if (this.style.getEnum() == Style.DOTS) {
      stampVertices(this.cubeRaster, CUBE_WIDTH, CUBE_HEIGHT, false,
        this.structureVertices[shape], radius, depthMin, depthRange, layerFilter);
    } else {
      for (int e : this.structureEdges[shape]) {
        final int a = this.heart.edgeA[e];
        final int b = this.heart.edgeB[e];
        if (!edgeInLayer(a, b, layerFilter)) {
          continue;
        }
        drawStroke(
          this.cubeRaster, CUBE_WIDTH, CUBE_HEIGHT, false, radius,
          this.projX[a], this.projY[a], shade(this.projDepth[a], depthMin, depthRange),
          this.projX[b], this.projY[b], shade(this.projDepth[b], depthMin, depthRange)
        );
      }
    }

    writeCubeFace(face, rgb);
  }

  /**
   * Draws the heart once per cube face, each face showing the view from its own
   * direction, and each splitting that view across the two surfaces a viewer
   * there looks through: the cube wall, and the near arc of the ring behind it.
   *
   * Every wall carries the whole object, so all four views are complete. Depth
   * decides how much of each one has moved off its wall and onto the ring, and
   * because the projection is orthographic the two halves register into a single
   * image head-on while separating by their real spacing from anywhere else.
   * That separation is the whole effect.
   *
   * The arcs overlap on the ring where the views do. They accumulate into one
   * raster taking the brighter of any two writes, so an overlap reads as shared
   * structure rather than as one view damaging another.
   */
  private void renderLayers(int shape, int rgb) {
    resolveCylinderGeometry();
    final boolean ring = (this.depth.getValuef() > 0) && (this.cylinderRadius > 0);
    if (ring) {
      // Every arc accumulates into one raster, written once at the end.
      Arrays.fill(this.cylinderRaster, 0f);
    }

    // A fixture may ship without its interior models, in which case these are
    // null; each surface is written only if the installation actually has it.
    final Apotheneum.Cube.Face[] walls = Apotheneum.cube.exterior.faces;
    final Apotheneum.Cube.Face[] innerWalls =
      (Apotheneum.cube.interior != null) ? Apotheneum.cube.interior.faces : null;

    for (int face = 0; face < walls.length; ++face) {
      if (!resolveViewFrame(walls[face])) {
        return;
      }
      assignLayers(walls[face], ring);

      renderCubeFace(walls[face], shape, rgb, LAYER_WALL);
      if (innerWalls != null) {
        renderCubeFace(innerWalls[face], shape, rgb, LAYER_WALL);
      }
      if (ring) {
        drawArc(shape, -1f, LAYER_ARC);
      }
    }

    if (ring) {
      writeCylinder(Apotheneum.cylinder.exterior, rgb);
      if (Apotheneum.cylinder.interior != null) {
        writeCylinder(Apotheneum.cylinder.interior, rgb);
      }
    }
  }

  /**
   * Builds the horizontal view frame for one wall: the right axis across it, and
   * the normal running inward from it.
   */
  private boolean resolveViewFrame(Apotheneum.Cube.Face face) {
    final LXPoint origin = face.columns[0].points[0];
    final LXPoint acrossEnd = face.columns[CUBE_WIDTH - 1].points[0];

    float rx = acrossEnd.x - origin.x;
    float rz = acrossEnd.z - origin.z;
    final float rLength = (float) Math.hypot(rx, rz);
    if (rLength <= 0) {
      return false;
    }
    this.viewRx = rx / rLength;
    this.viewRz = rz / rLength;

    float nx = -this.viewRz;
    float nz = this.viewRx;
    if (nx * (this.centerX - origin.x) + nz * (this.centerZ - origin.z) < 0) {
      nx = -nx;
      nz = -nz;
    }
    this.viewNx = nx;
    this.viewNz = nz;
    return true;
  }

  /**
   * Splits the object between this face's wall and the arc behind it.
   *
   * Depth takes from the back: the surface nearest the viewer holds the wall
   * longest, and the far side is handed to the ring first, which is the
   * direction that reads as the object opening away into the structure.
   *
   * Whatever the arc cannot physically hold stays on the wall — wider than the
   * crop, or landing in one of the ring's door cutouts. Nothing is ever dropped,
   * so the silhouette on a wall is only ever thinned, never broken.
   */
  private void assignLayers(Apotheneum.Cube.Face wall, boolean ring) {
    final float usableRadius = this.cylinderRadius * this.arc.getValuef();
    final float share = this.depth.getValuef();

    float min = Float.MAX_VALUE;
    float max = -Float.MAX_VALUE;
    for (int i = 0; i < this.heart.vertexCount; ++i) {
      final float d = (this.worldX[i] - this.centerX) * this.viewNx
        + (this.worldZ[i] - this.centerZ) * this.viewNz;
      this.layerDepth[i] = d;
      min = Math.min(min, d);
      max = Math.max(max, d);
    }
    final float range = Math.max(1e-4f, max - min);

    resolveWallBasis(wall);
    for (int i = 0; i < this.heart.vertexCount; ++i) {
      // Zero at the surface nearest this viewer, one at the far side.
      final float t = (this.layerDepth[i] - min) / range;
      final boolean fitsArc = ring
        && (Math.abs(horizontalOffset(i)) <= usableRadius)
        && arcHasPixel(i, -1f);

      int layer = ((t > 1 - share) && fitsArc) ? LAYER_ARC : LAYER_WALL;

      // A doorway is a hole, not a shadow: a viewer looking at one sees straight
      // through to the ring, and the projection puts the content back in place.
      if ((layer == LAYER_WALL) && !wallHasPixel(i) && fitsArc) {
        layer = LAYER_ARC;
      }
      this.vertexLayer[i] = layer;
    }
  }

  /** Offset of a vertex from the ring's axis along the view's horizontal axis. */
  private float horizontalOffset(int i) {
    return (this.worldX[i] - this.cylinderAxisX) * this.viewRx
      + (this.worldZ[i] - this.cylinderAxisZ) * this.viewRz;
  }

  private void resolveCylinderGeometry() {
    final Apotheneum.Cylinder.Orientation orientation = Apotheneum.cylinder.exterior;
    final Apotheneum.Column first = orientation.columns[0];
    final LXPoint firstTop = first.points[0];
    final LXPoint oppositeTop = orientation.columns[CYLINDER_WIDTH / 2].points[0];
    final LXPoint quarterTop = orientation.columns[CYLINDER_WIDTH / 4].points[0];

    this.cylinderAxisX = .5f * (firstTop.x + oppositeTop.x);
    this.cylinderAxisZ = .5f * (firstTop.z + oppositeTop.z);
    this.cylinderRadius = (float) Math.hypot(firstTop.x - this.cylinderAxisX, firstTop.z - this.cylinderAxisZ);
    this.cylinderAzimuthZero = Math.atan2(firstTop.z - this.cylinderAxisZ, firstTop.x - this.cylinderAxisX);
    this.cylinderWinding = Math.signum(signedAngle(
      Math.atan2(quarterTop.z - this.cylinderAxisZ, quarterTop.x - this.cylinderAxisX) - this.cylinderAzimuthZero
    ));
    this.cylinderYTop = firstTop.y;
    this.cylinderYStep = (first.points[CYLINDER_HEIGHT - 1].y - this.cylinderYTop) / (CYLINDER_HEIGHT - 1);
  }

  /** Loads a cube wall's basis into the scratch fields for door testing. */
  private void resolveWallBasis(Apotheneum.Cube.Face face) {
    final LXPoint origin = face.columns[0].points[0];
    final LXPoint acrossEnd = face.columns[CUBE_WIDTH - 1].points[0];
    final LXPoint downEnd = face.columns[0].points[CUBE_HEIGHT - 1];
    this.wallOx = origin.x;
    this.wallOy = origin.y;
    this.wallOz = origin.z;
    this.wallUx = (acrossEnd.x - origin.x) / (CUBE_WIDTH - 1);
    this.wallUy = (acrossEnd.y - origin.y) / (CUBE_WIDTH - 1);
    this.wallUz = (acrossEnd.z - origin.z) / (CUBE_WIDTH - 1);
    this.wallVx = (downEnd.x - origin.x) / (CUBE_HEIGHT - 1);
    this.wallVy = (downEnd.y - origin.y) / (CUBE_HEIGHT - 1);
    this.wallVz = (downEnd.z - origin.z) / (CUBE_HEIGHT - 1);
    this.wallULengthSq = this.wallUx * this.wallUx + this.wallUy * this.wallUy + this.wallUz * this.wallUz;
    this.wallVLengthSq = this.wallVx * this.wallVx + this.wallVy * this.wallVy + this.wallVz * this.wallVz;
  }

  /**
   * Whether the wall currently loaded actually has an LED where this vertex lands.
   *
   * Door columns are physically shorter, so a pixel below the cutout does not
   * exist and anything placed there is simply lost.
   */
  private boolean wallHasPixel(int i) {
    if ((this.wallULengthSq <= 0) || (this.wallVLengthSq <= 0)) {
      return false;
    }
    final float dx = this.worldX[i] - this.wallOx;
    final float dy = this.worldY[i] - this.wallOy;
    final float dz = this.worldZ[i] - this.wallOz;
    final int column = Math.round(
      (dx * this.wallUx + dy * this.wallUy + dz * this.wallUz) / this.wallULengthSq);
    final int row = Math.round(
      (dx * this.wallVx + dy * this.wallVy + dz * this.wallVz) / this.wallVLengthSq);
    if ((column < 0) || (column >= CUBE_WIDTH) || (row < 0) || (row >= CUBE_HEIGHT)) {
      return false;
    }
    return row < Apotheneum.cube.exterior.available(column);
  }

  /** Whether the given arc has an LED where this vertex lands, doors included. */
  private boolean arcHasPixel(int i, float side) {
    final float offset = horizontalOffset(i);
    final float squared = this.cylinderRadius * this.cylinderRadius - offset * offset;
    if ((squared < 0) || (this.cylinderYStep == 0)) {
      return false;
    }
    final float perpendicular = side * (float) Math.sqrt(squared);
    final float ox = offset * this.viewRx + perpendicular * this.viewNx;
    final float oz = offset * this.viewRz + perpendicular * this.viewNz;
    final int column = Math.floorMod(Math.round(
      azimuthColumn(ox, oz, this.cylinderAzimuthZero, this.cylinderWinding)), CYLINDER_WIDTH);
    final int row = Math.round((this.worldY[i] - this.cylinderYTop) / this.cylinderYStep);
    if ((row < 0) || (row >= CYLINDER_HEIGHT)) {
      return false;
    }
    return row < Apotheneum.cylinder.exterior.available(column);
  }

  /**
   * Paints the two middle slices onto the cylinder's near and far arcs.
   *
   * A heart point sits at some offset along the view's horizontal axis; the arc
   * carries it at the column that shares that offset, which is where a viewer
   * looking down the axis sees it. Points wider than the cylinder have no such
   * column and are dropped.
   */
  private void drawArc(int shape, float side, int layer) {
    final float axisX = this.cylinderAxisX;
    final float axisZ = this.cylinderAxisZ;
    final float arcRadius = this.cylinderRadius;
    final double azimuthZero = this.cylinderAzimuthZero;
    final double winding = this.cylinderWinding;
    final float yTop = this.cylinderYTop;
    final float yStep = this.cylinderYStep;
    if ((yStep == 0) || (winding == 0) || (arcRadius <= 0)) {
      return;
    }

    final float radius = .5f * this.thickness.getValuef();

    float depthMin = Float.MAX_VALUE;
    float depthMax = -Float.MAX_VALUE;
    for (int i = 0; i < this.heart.vertexCount; ++i) {
      final float offset = (this.worldX[i] - axisX) * this.viewRx + (this.worldZ[i] - axisZ) * this.viewRz;
      final float squared = arcRadius * arcRadius - offset * offset;
      if (squared < 0) {
        this.projX[i] = Float.NaN;
        this.projY[i] = Float.NaN;
        this.projDepth[i] = 0;
        continue;
      }
      final float perpendicular = side * (float) Math.sqrt(squared);
      final float ox = offset * this.viewRx + perpendicular * this.viewNx;
      final float oz = offset * this.viewRz + perpendicular * this.viewNz;
      this.projX[i] = azimuthColumn(ox, oz, azimuthZero, winding);
      this.projY[i] = (this.worldY[i] - yTop) / yStep;
      // Depth towards the viewer this arc faces, in the same sense the cube pass
      // uses: larger is farther, so shade() dims the far side on both surfaces.
      // The side factor belongs to the geometry above, not here - negating it
      // would light the far side and dim the near one.
      this.projDepth[i] = this.layerDepth[i];
      depthMin = Math.min(depthMin, this.projDepth[i]);
      depthMax = Math.max(depthMax, this.projDepth[i]);
    }
    final float depthRange = Math.max(1e-4f, depthMax - depthMin);

    if (this.style.getEnum() == Style.DOTS) {
      stampVertices(this.cylinderRaster, CYLINDER_WIDTH, CYLINDER_HEIGHT, true,
        this.structureVertices[shape], radius, depthMin, depthRange, layer);
    } else {
      for (int e : this.structureEdges[shape]) {
        final int a = this.heart.edgeA[e];
        final int b = this.heart.edgeB[e];
        if (!edgeInLayer(a, b, layer)
          || Float.isNaN(this.projX[a]) || Float.isNaN(this.projX[b])) {
          continue;
        }
        // Column is a non-linear function of horizontal offset, so walk the arc.
        final float spanX = Math.abs(shortestColumnDelta(this.projX[a], this.projX[b]));
        final float spanY = Math.abs(this.projY[b] - this.projY[a]);
        final int steps = LXUtils.constrain(
          (int) Math.ceil(Math.max(spanX, spanY) / ARC_SEGMENT_PIXELS), 1, MAX_ARC_SUBDIVISIONS
        );
        float fromX = this.projX[a];
        float fromY = this.projY[a];
        final float shadeA = shade(this.projDepth[a], depthMin, depthRange);
        final float shadeB = shade(this.projDepth[b], depthMin, depthRange);
        // Brightness is interpolated along with the position. Handing every
        // sub-segment the two endpoint shades would restart the whole gradient
        // at each one, banding a bar that should darken smoothly.
        float fromShade = shadeA;
        for (int step = 1; step <= steps; ++step) {
          final float t = step / (float) steps;
          final float toX = fromX + shortestColumnDelta(fromX, lerpColumn(this.projX[a], this.projX[b], t));
          final float toY = this.projY[a] + t * (this.projY[b] - this.projY[a]);
          final float toShade = shadeA + t * (shadeB - shadeA);
          drawStroke(this.cylinderRaster, CYLINDER_WIDTH, CYLINDER_HEIGHT, true, radius,
            fromX, fromY, fromShade, toX, toY, toShade);
          fromX = toX;
          fromY = toY;
          fromShade = toShade;
        }
      }
    }
  }

  /** Interpolates between two columns the short way round the ring. */
  private static float lerpColumn(float from, float to, float t) {
    return from + t * shortestColumnDelta(from, to);
  }

  /** Column index, possibly fractional, of a point at the given offset from the cylinder axis. */
  private static float azimuthColumn(float offsetX, float offsetZ, double azimuthZero, double winding) {
    final double azimuth = winding * (Math.atan2(offsetZ, offsetX) - azimuthZero);
    return (float) (((azimuth % TAU + TAU) % TAU) / TAU * CYLINDER_WIDTH);
  }

  /** Reduces an angular difference to the equivalent value in (-pi, pi]. */
  private static double signedAngle(double radians) {
    final double wrapped = (radians % TAU + TAU) % TAU;
    return (wrapped > Math.PI) ? wrapped - TAU : wrapped;
  }

  /** Signed column distance from {@code from} to {@code to}, the short way round the ring. */
  private static float shortestColumnDelta(float from, float to) {
    float delta = (to - from) % CYLINDER_WIDTH;
    if (delta > CYLINDER_WIDTH / 2f) {
      delta -= CYLINDER_WIDTH;
    } else if (delta < -CYLINDER_WIDTH / 2f) {
      delta += CYLINDER_WIDTH;
    }
    return delta;
  }

  private void writeCylinder(Apotheneum.Cylinder.Orientation orientation, int rgb) {
    for (int column = 0; column < CYLINDER_WIDTH; ++column) {
      final int available = orientation.available(column);
      final LXPoint[] points = orientation.columns[column].points;
      for (int row = 0; row < available; ++row) {
        this.colors[points[row].index] =
          LXColor.scaleBrightness(rgb, this.cylinderRaster[column + CYLINDER_WIDTH * row]);
      }
    }
  }

  /** Stamps one mark per selected vertex, which is the sculpture's own LED positions. */
  private void stampVertices(float[] raster, int width, int height, boolean wrapX,
      int[] vertices, float radius, float depthMin, float depthRange, int layerFilter) {
    for (int v : vertices) {
      if (this.vertexLayer[v] != layerFilter) {
        continue;
      }
      if (Float.isNaN(this.projX[v])) {
        continue;
      }
      stamp(raster, width, height, wrapX, radius,
        this.projX[v], this.projY[v], shade(this.projDepth[v], depthMin, depthRange));
    }
  }

  /**
   * Whether an edge belongs to the shell currently being drawn.
   *
   * A bar whose two ends landed on different shells is drawn whole on the wall.
   * The wall is the shell with no crop and no ring doors, so it can always hold
   * the bar; giving it to the arc instead would draw the half that was
   * deliberately kept on the wall past the crop, or - when that end falls
   * outside the cylinder entirely and has no arc position at all - drop the bar
   * from both surfaces and leave a gap in the cage.
   */
  private boolean edgeInLayer(int a, int b, int layerFilter) {
    if (this.vertexLayer[a] != this.vertexLayer[b]) {
      return layerFilter == LAYER_WALL;
    }
    return this.vertexLayer[a] == layerFilter;
  }

  private float minDepth() {
    float min = Float.MAX_VALUE;
    for (int i = 0; i < this.heart.vertexCount; ++i) {
      min = Math.min(min, this.projDepth[i]);
    }
    return min;
  }

  private float maxDepth() {
    float max = -Float.MAX_VALUE;
    for (int i = 0; i < this.heart.vertexCount; ++i) {
      max = Math.max(max, this.projDepth[i]);
    }
    return max;
  }

  /**
   * Brightness for a point at the given depth, dimming whatever sits farther from
   * the viewer this surface faces.
   *
   * The range spans the whole object rather than the slice this surface carries,
   * so a shell near the viewer comes out uniformly bright and a distant one
   * uniformly dim. That ordering by brightness is most of what sells the depth.
   */
  private float shade(float depthValue, float min, float range) {
    final float near = 1f - (depthValue - min) / range;
    return (float) LXUtils.lerp(1., near, this.shading.getValue());
  }

  /**
   * Draws a stroke of the given radius into a raster, walking the line in
   * sub-pixel steps and taking the brightest value at each pixel rather than
   * accumulating, so that crossing bars stay legible instead of blowing out.
   */
  private static void drawStroke(float[] raster, int width, int height, boolean wrapX, float radius,
      float x0, float y0, float b0, float x1, float y1, float b1) {
    if ((b0 <= 0) && (b1 <= 0)) {
      return;
    }
    final float dx = x1 - x0;
    final float dy = y1 - y0;
    final int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / STROKE_SAMPLE_PIXELS);
    if (steps <= 0) {
      stamp(raster, width, height, wrapX, radius, x0, y0, b0);
      return;
    }
    for (int i = 0; i <= steps; ++i) {
      final float t = i / (float) steps;
      stamp(raster, width, height, wrapX, radius, x0 + t * dx, y0 + t * dy, b0 + t * (b1 - b0));
    }
  }

  /** Stamps one soft disc of the given radius, which is what gives a bar its thickness. */
  private static void stamp(float[] raster, int width, int height, boolean wrapX, float radius,
      float x, float y, float brightness) {
    if (brightness <= 0) {
      return;
    }
    final int xLow = (int) Math.floor(x - radius);
    final int xHigh = (int) Math.ceil(x + radius);
    final int yLow = (int) Math.floor(y - radius);
    final int yHigh = (int) Math.ceil(y + radius);
    for (int py = yLow; py <= yHigh; ++py) {
      if ((py < 0) || (py >= height)) {
        continue;
      }
      for (int px = xLow; px <= xHigh; ++px) {
        final int column = wrapX ? (((px % width) + width) % width) : px;
        if ((column < 0) || (column >= width)) {
          continue;
        }
        // Soft-edged disc: full coverage inside the radius, falling off over the last pixel.
        final float distance = (float) Math.hypot(px - x, py - y);
        final float coverage = LXUtils.constrainf(radius - distance + .5f, 0, 1);
        if (coverage > 0) {
          final int index = column + width * py;
          raster[index] = Math.max(raster[index], coverage * brightness);
        }
      }
    }
  }

  private void writeCubeFace(Apotheneum.Cube.Face face, int rgb) {
    for (int column = 0; column < CUBE_WIDTH; ++column) {
      final int available = Apotheneum.cube.exterior.available(column);
      final LXPoint[] points = face.columns[column].points;
      for (int row = 0; row < available; ++row) {
        this.colors[points[row].index] = LXColor.scaleBrightness(rgb, this.cubeRaster[column + CUBE_WIDTH * row]);
      }
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, RobotHeart heart) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL);
    uiDevice.setChildSpacing(4);

    addColumn(uiDevice, "Shape",
      newKnob(this.size),
      newKnob(this.elevation)
    );

    addColumn(uiDevice, "Draw",
      newDropMenu(this.style),
      newDropMenu(this.structure),
      newKnob(this.thickness)
    );

    addColumn(uiDevice, "Rotate",
      newKnob(this.yaw),
      newKnob(this.tilt)
    );

    addColumn(uiDevice, "Depth",
      newKnob(this.depth),
      newKnob(this.shading)
    );

    addColumn(uiDevice, "Ring",
      newKnob(this.arc)
    );
  }

}
