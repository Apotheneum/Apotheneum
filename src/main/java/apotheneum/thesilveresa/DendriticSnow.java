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
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Dendritic Snow")
public class DendriticSnow extends ApotheneumPattern {

  private static final float PI     = (float) Math.PI;
  private static final float TWO_PI = 2f * PI;

  // Grid: 128x128, 1 channel per cell (crystal yes/no)
  private static final int GRID_W  = 128;
  private static final int GRID_H  = 128;
  private static final int GRID_CH = 1;  // 0=empty, 1=crystalline

  private final float[] grid = new float[GRID_W * GRID_H];
  private final float[] gridDistance = new float[GRID_W * GRID_H];  // distance from seed
  private boolean dirty = true;

  // Trig LUT
  private static final int     LUT       = 1024;
  private static final float   LUT_SCALE = LUT / TWO_PI;
  private static final float[] SINL      = new float[LUT];
  private static final float[] COSL      = new float[LUT];

  static {
    for (int i = 0; i < LUT; i++) {
      float a = TWO_PI * i / LUT;
      SINL[i] = (float) Math.sin(a);
      COSL[i] = (float) Math.cos(a);
    }
  }

  // Parameters

  // DLA iteration count - higher = more mature crystal.
  // This is essentially "seed" for the random DLA, but also affects
  // density/complexity. Use it to choose between early dendritic vs
  // late dendrite-covered crystal.
  private final CompoundParameter complexity = new CompoundParameter("Complex", 30, 5, 50)
    .setDescription("DLA iteration count x1000 (more = denser crystal)");

  // Random walk step size relative to grid size.
  // Larger steps = sparser, more dendritic crystal.
  // Smaller steps = denser, more filled crystal.
  private final CompoundParameter dendricity = new CompoundParameter("Dendr", 50, 20, 80)
    .setDescription("Walker step size x100 (higher = sparselier dendrites)");

  // Growth animation: 0 = all crystalline, 1 = only seed visible.
  // During playback, sweep time from 1->0 to reveal crystal growth.
  private final CompoundParameter growthSweep = new CompoundParameter("Growth", 0, 0, 200)
    .setDescription("Growth animation x100 (200=seed only, 0=full crystal covering surfaces)");

  private final CompoundParameter iridescence = new CompoundParameter("Iridesc", 50, 0, 100)
    .setDescription("Blue iridescence in branches x100");

  // Overall brightness
  private final CompoundParameter bright = new CompoundParameter("Bright", 95.0, 20.0, 100.0)
    .setDescription("Output brightness");

  // Regrow trigger - when changed, recomputes the DLA grid
  private final CompoundParameter seed = new CompoundParameter("Seed", 0, 0, 1000)
    .setDescription("Random seed for DLA regrowth (change to regrow)");

  private float prevSeed = -1f;
  private float time = 0f;

  // Constructor

  public DendriticSnow(LX lx) {
    super(lx);
    addParameter("Complex", this.complexity);
    addParameter("Dendr",   this.dendricity);
    addParameter("Growth",  this.growthSweep);
    addParameter("Iridesc", this.iridescence);
    addParameter("Bright",  this.bright);
    addParameter("Seed",    this.seed);
  }

  @Override
  public void onParameterChanged(heronarts.lx.parameter.LXParameter p) {
    if (p == complexity || p == dendricity || p == seed) {
      dirty = true;
    }
  }

  // Main render

  @Override
  protected void render(double deltaMs) {
    time += (float)(deltaMs / 1000.0);

    float seedVal = seed.getValuef();
    if (dirty || Math.abs(seedVal - prevSeed) > 0.01f) {
      computeDLA((int)(seedVal * 1000f));
      prevSeed = seedVal;
      dirty = false;
    }

    float growSweep = growthSweep.getValuef() / 100f;
    float iridescentPct = iridescence.getValuef() / 100f;
    float br = bright.getValuef();

    renderCube(growSweep, iridescentPct, br);
    renderCylinder(growSweep, iridescentPct, br);
  }

  // DLA Computation

  // Diffusion-limited aggregation: compute the crystalline structure.
  // The grid stores 1.0 for crystalline cells, 0.0 for empty.
  // gridDistance stores the Manhattan distance from seed to each crystal cell.
  // Algorithm:
  // 1. Seed the grid: plant one crystalline cell at center
  // 2. For N iterations: random walk from outside boundary, step toward
  // center, if adjacent to crystal -> stick (mark cell as crystalline)
  // 3. Compute distance field: breadth-first search from seed to all crystals
  // Cylinder aspect ratio: 120 cols x 43 rows. To make the DLA look isometric
  // on the cylinder, we scale X coordinates by 43/120 during the walk generation,
  // so equal pixel distances in both axes map to equal grid distances.
  private void computeDLA(int randSeed) {
    // Clear grid
    for (int i = 0; i < grid.length; i++) {
      grid[i] = 0f;
      gridDistance[i] = Float.MAX_VALUE;
    }

    // Seed: center cell
    int seedIdx = (GRID_H / 2) * GRID_W + (GRID_W / 2);
    grid[seedIdx] = 1f;
    gridDistance[seedIdx] = 0f;

    // DLA walkers
    float stepSize = dendricity.getValuef() / 100f * 0.15f;  // ~0.03-0.12 grid units
    int numIter = (int)(complexity.getValuef() * 1000f);

    java.util.Random rng = new java.util.Random((long)randSeed);
    final float XS = 43f / 120f;  // aspect ratio stretch for X

    for (int iter = 0; iter < numIter; iter++) {
      // Random walk from boundary toward center
      float x = (rng.nextFloat() - 0.5f) * 1.8f * XS;  // stretch X
      float y = (rng.nextFloat() - 0.5f) * 1.8f;
      float vx = -x / Math.max(0.001f, (float) Math.sqrt(x*x + y*y));
      float vy = -y / Math.max(0.001f, (float) Math.sqrt(x*x + y*y));

      // Walk until stuck to crystal or out of bounds
      int maxSteps = 500;
      for (int step = 0; step < maxSteps; step++) {
        x += vx * stepSize + (rng.nextFloat() - 0.5f) * stepSize * 0.5f;
        y += vy * stepSize + (rng.nextFloat() - 0.5f) * stepSize * 0.5f;

        // Out of bounds? restart (note: X bounds stretched)
        if (Math.abs(x) > 1f * XS || Math.abs(y) > 1f) break;

        // Convert to grid coords - unstretch X back to grid space
        int gx = (int)(((x / XS + 1f) * 0.5f) * GRID_W);
        int gy = (int)(((y + 1f) * 0.5f) * GRID_H);
        if (gx < 0 || gx >= GRID_W || gy < 0 || gy >= GRID_H) break;

        int idx = gy * GRID_W + gx;

        // Adjacent to crystal? stick
        if (isAdjacentToCrystal(gx, gy)) {
          grid[idx] = 1f;
          break;
        }
      }
    }

    // Compute distance field via breadth-first search from seed
    computeDistanceField();
  }

  private boolean isAdjacentToCrystal(int gx, int gy) {
    for (int dy = -1; dy <= 1; dy++)
      for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        int nx = gx + dx, ny = gy + dy;
        if (nx >= 0 && nx < GRID_W && ny >= 0 && ny < GRID_H)
          if (grid[ny * GRID_W + nx] > 0.5f) return true;
      }
    return false;
  }

  private void computeDistanceField() {
    java.util.Queue<Integer> queue = new java.util.LinkedList<>();

    // Seed: distance 0
    int seedIdx = (GRID_H / 2) * GRID_W + (GRID_W / 2);
    queue.add(seedIdx);
    gridDistance[seedIdx] = 0f;

    // BFS from seed
    while (!queue.isEmpty()) {
      int idx = queue.poll();
      int gx = idx % GRID_W, gy = idx / GRID_W;
      float d = gridDistance[idx];

      for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
          int nx = gx + dx, ny = gy + dy;
          if (nx < 0 || nx >= GRID_W || ny < 0 || ny >= GRID_H) continue;
          int nidx = ny * GRID_W + nx;
          if (grid[nidx] < 0.5f) continue;  // not crystal
          float nd = d + ((dx == 0 || dy == 0) ? 1f : 1.414f);
          if (nd < gridDistance[nidx]) {
            gridDistance[nidx] = nd;
            queue.add(nidx);
          }
        }
      }
    }
  }

  // Grid sampling

  private int sampleGrid(float u, float v, float growSweep, float iridescentPct, float br) {
    if (u < 0f) u = 0f; else if (u > 1f) u = 1f;
    if (v < 0f) v = 0f; else if (v > 1f) v = 1f;

    float gx = u * (GRID_W - 1);
    float gy = v * (GRID_H - 1);
    int x0 = (int) gx, y0 = (int) gy;
    float tx = gx - x0, ty = gy - y0;
    if (x0 >= GRID_W - 1) { x0 = GRID_W - 2; tx = 1f; }
    if (y0 >= GRID_H - 1) { y0 = GRID_H - 2; ty = 1f; }
    int x1 = x0 + 1, y1 = y0 + 1;

    int b00 = y0 * GRID_W + x0;
    int b10 = y0 * GRID_W + x1;
    int b01 = y1 * GRID_W + x0;
    int b11 = y1 * GRID_W + x1;

    // Bilinear sample of crystal
    float c00 = grid[b00], c10 = grid[b10], c01 = grid[b01], c11 = grid[b11];
    float cryst = lerp(lerp(c00, c10, tx), lerp(c01, c11, tx), ty);

    // Bilinear sample of distance
    float d00 = gridDistance[b00], d10 = gridDistance[b10];
    float d01 = gridDistance[b01], d11 = gridDistance[b11];
    float dist = lerp(lerp(d00, d10, tx), lerp(d01, d11, tx), ty);

    if (cryst < 0.1f) return LXColor.BLACK;

    // Growth sweep: fade in based on distance from seed
    // At growSweep=1, only seed is visible. At growSweep=0, full crystal visible.
    float sweepThreshold = growSweep * 40f;  // doubled from 20f to show full extent
    if (dist > sweepThreshold) return LXColor.BLACK;

    // Brightness fades in as we approach the growth front
    float frontDist = Math.max(0f, dist - sweepThreshold + 2f);
    float frontFade = clamp01(1f - frontDist * 0.5f);
    float brightness = cryst * (0.5f + 0.5f * frontFade) * br;

    // Iridescence: deeper branches (larger distance) get blue tint
    float maxDist = 20f;  // normalize distance
    float depthT = clamp01(dist / maxDist);
    float hue = 200f + depthT * 45f * iridescentPct;  // cyan to blue
    hue = ((hue % 360f) + 360f) % 360f;
    float sat = 20f + depthT * 80f * iridescentPct;  // subtle -> saturated

    return LXColor.hsb(hue, sat, brightness);
  }

  // Cube rendering

  private void renderCube(float growSweep, float iridescentPct, float br) {
    Cube cube = Apotheneum.cube;
    if (cube == null) return;
    for (Face face : cube.exterior.faces)
      renderCubeFace(face, growSweep, iridescentPct, br);
    if (cube.interior != null)
      for (Face face : cube.interior.faces)
        renderCubeFace(face, growSweep, iridescentPct, br);
  }

  private void renderCubeFace(Face face, float growSweep, float iridescentPct, float br) {
    int cols = Apotheneum.GRID_WIDTH, rows = face.rows.length;
    for (int ri = 0; ri < rows; ri++) {
      Row row = face.rows[ri];
      float v = (float) ri / (rows - 1);
      for (int ci = 0; ci < cols; ci++) {
        float u = (float) ci / (cols - 1);
        colors[row.points[ci].index] = sampleGrid(u, v, growSweep, iridescentPct, br);
      }
    }
  }

  // Cylinder rendering

  private void renderCylinder(float growSweep, float iridescentPct, float br) {
    Cylinder cyl = Apotheneum.cylinder;
    if (cyl == null) return;
    renderCylOrientation(cyl.exterior, growSweep, iridescentPct, br);
    if (cyl.interior != null)
      renderCylOrientation(cyl.interior, growSweep, iridescentPct, br);
  }

  private void renderCylOrientation(Cylinder.Orientation o, float growSweep,
                                     float iridescentPct, float br) {
    Ring[] rings = o.rings;
    int numRings = rings.length;
    final float XS = 120f / 43f;  // stretch X to match cylinder pixel aspect

    for (int ri = 0; ri < numRings; ri++) {
      Ring ring = rings[ri];
      int np = ring.points.length;
      float vN = (float) ri / (numRings - 1);
      float v = vN;
      for (int pi = 0; pi < np; pi++) {
        float u = (float) pi / np;
        // Stretch U to fill cylinder width
        u = 0.5f + (u - 0.5f) * XS;
        // Wrap to [0,1]
        u = ((u % 1f) + 1f) % 1f;

        colors[ring.points[pi].index] = sampleGrid(u, v, growSweep, iridescentPct, br);
      }
    }
  }

  // Helpers

  private float fsin(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return SINL[i < 0 ? i + LUT : i]; }
  private float fcos(float a) { int i = (int)(a * LUT_SCALE) & (LUT - 1); return COSL[i < 0 ? i + LUT : i]; }

  private static float lerp(float a, float b, float t) { return a + t * (b - a); }
  private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }
}
