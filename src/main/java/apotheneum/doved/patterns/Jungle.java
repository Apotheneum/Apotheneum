package apotheneum.doved.patterns;

import java.util.Arrays;
import java.util.Random;

import apotheneum.Apotheneum;
import apotheneum.doved.modulators.ApotheneumColor;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.utils.Noise;

/**
 * A jungle canopy seen from beneath: trunks rising into a leaf ceiling, and
 * daylight falling through the gaps in it.
 *
 * The scene is built as a normalized field over (u, v) — u wrapping once around
 * the surface, v running from the top of the structure down to the floor — so
 * the cube and the cylinder show the same forest, each sampled at its own
 * resolution, and neither has a seam.
 *
 * Light is not painted. Foliage and trunks accumulate an occlusion depth down a
 * slanted ray, and everything visible is what survives that: shafts where the
 * canopy has a hole, silhouette where it does not. Sway therefore moves the
 * light as well as the leaves.
 *
 * Colour-native, on the two roles {@link ColorNativePattern} defines. The scene
 * divides cleanly in two: {@code primary} is the vegetation — leaves, undergrowth
 * and trunks — and {@code secondary} is the daylight behind it, the sky in the
 * gaps and the shafts below them. Trunks share the vegetation role rather than
 * asking for a third: they render close to black, so their hue barely reads, and
 * a third role would not be the generic two-role vocabulary.
 *
 * Each role's physics coupling is driven by how much light reached that pixel:
 * vegetation by {@code through}, so leaves the light found sit brighter and less
 * saturated than the ones deep in the mass, and daylight by {@code direct}, so a
 * hard shaft reads whiter than the diffuse gloom around it.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Jungle")
@LXComponent.Description("Swaying jungle canopy with trunks and shafts of daylight falling through")
public class Jungle extends ColorNativePattern {

  private static final int MAX_TREES = 48;

  /** How much shadow depth carries from one row to the next as light descends. */
  private static final float RETENTION = .97f;

  /** How fast the light gives way to forest gloom below the canopy underside. */
  private static final float GLOOM_FALLOFF = 3.4f;

  /** Leaves are never as bright as open sky, however hard the light hits them. */
  private static final float LEAF_ALBEDO = .2f;

  /** Sharpening applied to the leaf field, turning smooth noise into clumps. */
  private static final float LEAF_EDGE = 5f;

  /** Gust phases wrap here: a multiple of 2pi for every harmonic used below. */
  private static final double GUST_PERIOD = 100 * 2 * Math.PI;

  /**
   * A tileable noise field, generated once and then sampled with a horizontal
   * offset. Drifting the offset advects the whole canopy without evaluating any
   * noise in the render loop.
   */
  private static final class Texture {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 64;

    private final float[] data = new float[WIDTH * HEIGHT];

    void generate(int seed, int period, int octaves, float verticalScale) {
      for (int y = 0; y < HEIGHT; ++y) {
        final float v = verticalScale * y / (HEIGHT - 1f);
        for (int x = 0; x < WIDTH; ++x) {
          final float u = period * x / (float) WIDTH;
          float sum = 0, amplitude = 1, norm = 0;
          int wrap = period;
          for (int octave = 0; octave < octaves; ++octave) {
            final float scale = 1 << octave;
            sum += amplitude * Noise.stb_perlin_noise3_wrap_nonpow2(
              u * scale, v * scale, 0, wrap, 0, 0, (seed + octave) & 0xff);
            norm += amplitude;
            amplitude *= .5f;
            wrap *= 2;
          }
          this.data[y * WIDTH + x] = LXUtils.clampf(.5f + .5f * sum / norm, 0, 1);
        }
      }
    }

    /**
     * Bilinear sample. The horizontal coordinate wraps, so an integer repeat
     * count keeps the field seamless all the way around the surface.
     */
    float sample(float u, int repeat, float offset, float v) {
      float fx = u * repeat * WIDTH + offset;
      fx -= WIDTH * (float) Math.floor(fx / WIDTH);
      final int x0 = ((int) fx) % WIDTH;
      final int x1 = (x0 + 1) % WIDTH;
      final float tx = fx - (float) Math.floor(fx);

      final float fy = LXUtils.clampf(v, 0, 1) * (HEIGHT - 1);
      final int y0 = (int) fy;
      final int y1 = LXUtils.min(y0 + 1, HEIGHT - 1);
      final float ty = fy - y0;

      final int row0 = y0 * WIDTH, row1 = y1 * WIDTH;
      final float top = LXUtils.lerpf(this.data[row0 + x0], this.data[row0 + x1], tx);
      final float bottom = LXUtils.lerpf(this.data[row1 + x0], this.data[row1 + x1], tx);
      return LXUtils.lerpf(top, bottom, ty);
    }
  }

  /** One tree. Fixed geometry from the seed, plus the per-frame sway state. */
  private static final class Tree {
    float u;             // base position around the surface, 0-1
    float depth;         // 0 = distant and hazy, 1 = foreground silhouette
    float halfWidth;     // trunk half-width at the base, normalized
    float lean;          // permanent lean, independent of the wind
    float phase;         // sway phase offset
    float crown;         // crown half-width, normalized
    float drop;          // how far the crown hangs, relative to the canopy
    float swayScale;     // per-tree sway multiplier

    float topV;          // per-frame: where the trunk enters its crown
    float sway;          // per-frame: horizontal displacement at the treetop
  }

  /** Scratch buffers for one surface, laid out column-major. */
  private static final class Raster {
    final int width, height;
    final float[] edge;       // canopy underside, per column
    final float[] crest;      // canopy top, per column
    final float[] floorEdge;  // top of the undergrowth, per column
    final float[] foliage;    // leaf coverage, 0-1
    final float[] trunk;      // trunk coverage, 0-1
    final float[] trunkDepth; // depth of whichever trunk won the pixel
    final float[] shrub;      // undergrowth coverage, 0-1
    final float[] occlusion;  // accumulated shadow depth along the light ray

    Raster(int width, int height) {
      this.width = width;
      this.height = height;
      this.edge = new float[width];
      this.crest = new float[width];
      this.floorEdge = new float[width];
      this.foliage = new float[width * height];
      this.trunk = new float[width * height];
      this.trunkDepth = new float[width * height];
      this.shrub = new float[width * height];
      this.occlusion = new float[width * height];
    }
  }

  public final CompoundParameter wind =
    new CompoundParameter("Wind", .35, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Direction and speed the leaves stream, negative blows the other way");

  public final CompoundParameter gust =
    new CompoundParameter("Gust", .5)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How far the trees rock back and forth in the gusts");

  public final CompoundParameter flutter =
    new CompoundParameter("Flutter", .6)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Shimmer of individual leaves turning over in the wind");

  public final CompoundParameter canopy =
    new CompoundParameter("Canopy", .6)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How far down the leaf ceiling hangs");

  public final CompoundParameter density =
    new CompoundParameter("Density", .62)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How solid the canopy is, and how little sky gets through");

  public final CompoundDiscreteParameter leafScale =
    new CompoundDiscreteParameter("Leaf", 3, 1, 9)
    .setDescription("Size of the leaf clumps, larger numbers give finer leaves");

  public final CompoundDiscreteParameter treeCount =
    new CompoundDiscreteParameter("Trees", 24, 4, 49)
    .setDescription("Number of trees standing around the room");

  public final CompoundParameter trunkWidth =
    new CompoundParameter("Trunk", .5)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Thickness of the trunks");

  public final CompoundDiscreteParameter seed =
    new CompoundDiscreteParameter("Seed", 1, 1, 65)
    .setDescription("Re-rolls the forest");

  public final CompoundParameter brightness =
    new CompoundParameter("Bright", .8)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Overall exposure");

  public final CompoundParameter shafts =
    new CompoundParameter("Shafts", .55)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How sharply the canopy swallows the light, and how defined the shafts are");

  public final CompoundParameter slant =
    new CompoundParameter("Slant", .35, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Angle the light falls at, from straight overhead to low and raking");

  public final CompoundParameter gamma =
    new CompoundParameter("Gamma", .4)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Tone curve, higher values deepen the shadows under the canopy");

  public final CompoundParameter haze =
    new CompoundParameter("Haze", .4)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Airborne haze: makes the shafts visible and lifts the forest floor");

  private final Texture canopyTexture = new Texture();
  private final Texture leafTexture = new Texture();

  private final Tree[] trees = new Tree[MAX_TREES];
  private final Random random = new Random();
  /** Noise-texture rebakes, for the test that a treeCount sweep causes none. */
  int textureGenerations = 0;

  private int texturedSeed = -1;
  private int generatedSeed = -1;
  private int generatedCount = -1;
  private int activeTrees = 0;

  private final Raster cubeRaster =
    new Raster(4 * Apotheneum.GRID_WIDTH, Apotheneum.GRID_HEIGHT);
  private final Raster cylinderRaster =
    new Raster(Apotheneum.RING_LENGTH, Apotheneum.CYLINDER_HEIGHT);

  private double windOffset = 0;
  private double gustPhase = 0;

  // Rebuilt once a frame rather than evaluated once a point: at 28,320 points the
  // transcendentals in the composite cost more than everything else together.
  private static final int SHADOW_STEPS = 512;
  private static final float SHADOW_MAX = 32;
  private static final int TONE_STEPS = 1024;
  private final float[] directLut = new float[SHADOW_STEPS + 1];
  private final float[] ambientLut = new float[SHADOW_STEPS + 1];
  private final float[] throughLut = new float[SHADOW_STEPS + 1];
  private final float[] toneLut = new float[TONE_STEPS + 1];

  // Each role's colour resolves once per frame across the physics range rather
  // than once per point: ColorRole.color() rebuilds an HSB colour every call, and
  // at 28,320 points that is the most expensive thing in the frame by far. The
  // physics driving each role is a function of shadow depth, so the index into
  // these tables is itself precomputed against the shadow tables above.
  //
  // Sized for all four ApotheneumColor.Surface entries, not one shared table: the surface a
  // pixel is on is now part of what decides its color, so a table built once for "the"
  // resolved color would silently pick one surface's answer for all four. See
  // surfaceRgbOffset() -- each surface gets its own 3*(PHYSICS_STEPS+1)-float slice.
  private static final int PHYSICS_STEPS = 64;
  private static final int SURFACE_COUNT = ApotheneumColor.Surface.values().length;
  private final float[] vegetationRgb = new float[SURFACE_COUNT * 3 * (PHYSICS_STEPS + 1)];
  private final float[] daylightRgb = new float[SURFACE_COUNT * 3 * (PHYSICS_STEPS + 1)];
  private final int[] vegetationIndex = new int[SHADOW_STEPS + 1];
  private final int[] daylightIndex = new int[SHADOW_STEPS + 1];

  // Per-frame state, resolved once in render() and read by the surface passes.
  private float canopyV, densityAmount, trunkAmount, flutterAmount;
  private float gustAmplitude, staticLean, absorbDirect, absorbAmbient;
  private float hazeAmount, exposure, toneGamma, columnSlant, leafDrift;
  private int leafRepeat, surfaceRepeat;

  public Jungle(LX lx) {
    // Primary/secondary physics-coupling amounts. Which palette stops those two roles
    // resolve from is no longer a per-pattern choice -- it is ApotheneumColor's shared
    // pair/swap/axis state, read by every ColorNativePattern.
    super(lx, .5, .5);
    addParameter("wind", this.wind);
    addParameter("gust", this.gust);
    addParameter("flutter", this.flutter);
    addParameter("canopy", this.canopy);
    addParameter("density", this.density);
    addParameter("leafScale", this.leafScale);
    addParameter("treeCount", this.treeCount);
    addParameter("trunkWidth", this.trunkWidth);
    addParameter("seed", this.seed);
    addParameter("brightness", this.brightness);
    addParameter("shafts", this.shafts);
    addParameter("slant", this.slant);
    addParameter("gamma", this.gamma);
    addParameter("haze", this.haze);

    for (int i = 0; i < MAX_TREES; ++i) {
      this.trees[i] = new Tree();
    }
  }

  /**
   * Column index wrapped around the surface. Not LXUtils.wrap, whose period is
   * max - min: that is right for a parameter range whose ends are the same point,
   * and off by one for an array index, which would show here as a seam.
   */
  private static int wrapColumn(int x, int width) {
    return Math.floorMod(x, width);
  }

  /** Signed distance around the loop, wrapped into [-.5, .5). */
  private static float wrapDelta(float delta) {
    return delta - (float) Math.floor(delta + .5f);
  }

  /**
   * Rebakes the noise textures. Keyed on the seed alone, and kept apart from the
   * tree layout for that reason: the textures cost about 98,000 noise evaluations
   * to build, and treeCount is modulatable, so folding the two together would
   * rebake both textures on every frame of a sweep across the tree count.
   */
  private void generateTextures(int seed) {
    this.canopyTexture.generate(seed * 13, 6, 3, 6f);
    this.leafTexture.generate(seed * 13 + 71, 8, 3, 9f);
    this.texturedSeed = seed;
    ++this.textureGenerations;
  }

  private void generateTrees(int seed, int count) {
    this.random.setSeed(seed * 2654435761L);
    this.activeTrees = count;
    for (int i = 0; i < count; ++i) {
      final Tree tree = this.trees[i];
      // Stratified around the loop, so the forest is spread but not regular.
      tree.u = (i + .15f + .7f * this.random.nextFloat()) / count;
      tree.depth = this.random.nextFloat();
      tree.halfWidth = (.0035f + .0065f * tree.depth) * (.7f + .6f * this.random.nextFloat());
      tree.lean = .04f * (this.random.nextFloat() - .5f);
      tree.phase = (float) (Math.PI * 2 * this.random.nextFloat());
      tree.crown = (.03f + .055f * tree.depth) * (.75f + .5f * this.random.nextFloat());
      tree.drop = .6f + 1.1f * tree.depth * (.7f + .5f * this.random.nextFloat());
      tree.swayScale = .45f + .9f * tree.depth;
    }
    // Painter's order: distant trees first, so near ones silhouette over them.
    for (int i = 1; i < count; ++i) {
      final Tree tree = this.trees[i];
      int j = i;
      while ((j > 0) && (this.trees[j-1].depth > tree.depth)) {
        this.trees[j] = this.trees[j-1];
        --j;
      }
      this.trees[j] = tree;
    }
    this.generatedSeed = seed;
    this.generatedCount = count;
  }

  @Override
  protected void render(double deltaMs) {
    final int seed = this.seed.getValuei();
    final int count = this.treeCount.getValuei();
    if (seed != this.texturedSeed) {
      generateTextures(seed);
    }
    if ((seed != this.generatedSeed) || (count != this.generatedCount)) {
      generateTrees(seed, count);
    }

    final float windSpeed = this.wind.getValuef();
    this.windOffset += deltaMs * .014 * windSpeed;
    if (this.windOffset >= Texture.WIDTH) {
      this.windOffset -= Texture.WIDTH;
    } else if (this.windOffset < 0) {
      this.windOffset += Texture.WIDTH;
    }
    this.gustPhase += deltaMs * .00085;
    if (this.gustPhase >= GUST_PERIOD) {
      this.gustPhase -= GUST_PERIOD;
    }

    this.canopyV = .18f + .55f * this.canopy.getValuef();
    this.densityAmount = this.density.getValuef();
    this.trunkAmount = .45f + 1.3f * this.trunkWidth.getValuef();
    this.flutterAmount = this.flutter.getValuef();
    this.leafRepeat = this.leafScale.getValuei();
    this.gustAmplitude = .045f * this.gust.getValuef();
    this.staticLean = .025f * windSpeed;
    this.absorbDirect = .05f + .26f * this.shafts.getValuef();
    this.absorbAmbient = .25f * this.absorbDirect;
    this.hazeAmount = this.haze.getValuef();
    this.exposure = 1.15f * this.brightness.getValuef();
    this.toneGamma = .7f + 1.5f * this.gamma.getValuef();
    // The leaf field rocks back and forth on the same gust that moves the trees,
    // and drifts slowly on top of that. Drift alone reads as a scrolling texture;
    // the rocking is what makes it wind.
    final double gustNow =
      .7 * Math.sin(this.gustPhase) + .3 * Math.sin(this.gustPhase * 1.31 + 2.1);
    this.leafDrift = (float) (this.windOffset + 34 * this.gust.getValuef() * gustNow);

    updateViewMask();
    buildTables();
    updateTrees();

    // The interior is written from its chamber's own raster rather than copied
    // from the exterior: copyExterior() is a bulk arraycopy over whole
    // orientations, which would write straight through a pattern-level view. The
    // raster is already built, so this is the same work the copy was, guarded.
    renderSurface(this.cubeRaster, Apotheneum.cube.exterior);
    writeColors(this.cubeRaster, Apotheneum.cube.interior);
    renderSurface(this.cylinderRaster, Apotheneum.cylinder.exterior);
    writeColors(this.cylinderRaster, Apotheneum.cylinder.interior);
  }

  private void buildTables() {
    for (int i = 0; i <= SHADOW_STEPS; ++i) {
      final float shadow = SHADOW_MAX * i / (float) SHADOW_STEPS;
      this.directLut[i] = (float) Math.exp(-this.absorbDirect * shadow);
      this.ambientLut[i] = (float) Math.exp(-this.absorbAmbient * shadow);
      this.throughLut[i] = (float) Math.exp(-.45f * this.absorbDirect * shadow);
    }
    for (int i = 0; i <= TONE_STEPS; ++i) {
      this.toneLut[i] = (float) Math.pow(i / (float) TONE_STEPS, this.toneGamma);
    }

    this.primary.update();
    this.secondary.update();
    for (ApotheneumColor.Surface surface : ApotheneumColor.Surface.values()) {
      final int offset = surfaceRgbOffset(surface);
      for (int i = 0; i <= PHYSICS_STEPS; ++i) {
        final double physics = 2. * i / PHYSICS_STEPS - 1;
        writeRgb(this.vegetationRgb, offset + i, this.primary.color(surface, physics));
        writeRgb(this.daylightRgb, offset + i, this.secondary.color(surface, physics));
      }
    }
    for (int i = 0; i <= SHADOW_STEPS; ++i) {
      this.vegetationIndex[i] = physicsIndex(this.throughLut[i]);
      this.daylightIndex[i] = physicsIndex(this.directLut[i]);
    }
  }

  /**
   * Start, in physics-step index units (not float-array units -- see {@link #writeRgb}), of one
   * surface's slice within {@link #vegetationRgb}/{@link #daylightRgb}.
   */
  private static int surfaceRgbOffset(ApotheneumColor.Surface surface) {
    return surface.ordinal() * (PHYSICS_STEPS + 1);
  }

  /** Maps a 0-1 lighting term onto the roles' -1..1 physics range. */
  private static int physicsIndex(float lighting) {
    return (int) LXUtils.clampf(lighting * PHYSICS_STEPS, 0, PHYSICS_STEPS);
  }

  private static void writeRgb(float[] table, int index, int color) {
    final int slot = 3 * index;
    table[slot] = ((color >> 16) & 0xff) / 255f;
    table[slot + 1] = ((color >> 8) & 0xff) / 255f;
    table[slot + 2] = (color & 0xff) / 255f;
  }

  /** Exposure and the tone curve, applied per channel at the end of the composite. */
  private float tone(float channel) {
    return this.toneLut[(int) (LXUtils.clampf(this.exposure * channel, 0, 1) * TONE_STEPS)];
  }

  private int shadowIndex(float shadow) {
    return (int) LXUtils.clampf(shadow * (SHADOW_STEPS / SHADOW_MAX), 0, SHADOW_STEPS);
  }

  private void updateTrees() {
    for (int i = 0; i < this.activeTrees; ++i) {
      final Tree tree = this.trees[i];
      final double oscillation =
        .7 * Math.sin(this.gustPhase + tree.phase) +
        .3 * Math.sin(this.gustPhase * 1.73 + tree.phase * 2.1);
      tree.topV = LXUtils.clampf(this.canopyV * tree.drop * .6f, .02f, .8f);
      // Deflection is measured at the tip, so it has to scale with how much trunk
      // there is to bend. Without this a low canopy leaves short trunks swinging
      // as far as tall ones, and they lie over like grass instead of swaying.
      final float reach = LXUtils.clampf((1 - tree.topV) / .7f, .2f, 1.2f);
      tree.sway = reach * (tree.lean +
        tree.swayScale * (this.staticLean + this.gustAmplitude * (float) oscillation));
    }
  }

  /** Horizontal displacement of a tree at a given fraction of its height. */
  private static float swayAt(Tree tree, float heightFraction) {
    final float bend = LXUtils.clampf(heightFraction, 0, 1.15f);
    return bend * bend * tree.sway;
  }

  private void renderSurface(Raster raster, Apotheneum.Orientation orientation) {
    // Light angle is defined in normalized units, resolved to columns-per-row
    // here so the cube and the cylinder show the same shafts at the same angle.
    this.columnSlant =
      .45f * this.slant.getValuef() * raster.width / (raster.height - 1f);
    // Leaf clumps are sized in pixels, not in degrees: the cylinder covers the
    // same loop in 120 columns rather than 200, and scaling the repeat keeps its
    // canopy from degenerating into per-pixel noise.
    this.surfaceRepeat = Math.max(1,
      Math.round(this.leafRepeat * raster.width / (float) (4 * Apotheneum.GRID_WIDTH)));
    computeCanopy(raster);
    drawTrunks(raster);
    marchLight(raster);
    writeColors(raster, orientation);
  }

  /** The underside of the leaf ceiling: a lumpy base, pulled lower by each crown. */
  private void computeCanopy(Raster raster) {
    final float drift = (float) this.windOffset;
    for (int x = 0; x < raster.width; ++x) {
      final float u = (x + .5f) / raster.width;
      float edge = this.canopyV *
        (.78f + .3f * this.canopyTexture.sample(u, 1, drift * .3f, .2f));
      for (int i = 0; i < this.activeTrees; ++i) {
        final Tree tree = this.trees[i];
        final float distance = Math.abs(wrapDelta(u - tree.u - swayAt(tree, 1)));
        if (distance < tree.crown) {
          final float t = 1 - distance / tree.crown;
          edge = Math.max(edge, this.canopyV * tree.drop * t * t * (3 - 2 * t));
        }
      }
      raster.edge[x] = edge;
      // Open sky above the canopy, with only the highest fronds breaking into it.
      raster.crest[x] =
        .05f + .085f * this.canopyTexture.sample(u, 1, drift * .2f, .35f);
      // Undergrowth rising off the floor, barely stirred by the wind up above, and
      // thresholded like the canopy so its top edge is ragged rather than a bar
      // drawn across the floor.
      final float scrub = this.canopyTexture.sample(u, 3, drift * .12f, .95f)
        + .6f * this.leafTexture.sample(u, 5, drift * .2f, .7f);
      raster.floorEdge[x] = 1 - .02f - .16f * LXUtils.clampf(scrub - .55f, 0, 1) * 2.2f;
    }
  }

  private void drawTrunks(Raster raster) {
    final float[] trunk = raster.trunk;
    final float[] trunkDepth = raster.trunkDepth;
    Arrays.fill(trunk, 0);

    final int height = raster.height;
    final int width = raster.width;
    for (int i = 0; i < this.activeTrees; ++i) {
      final Tree tree = this.trees[i];
      final float opacity = .45f + .55f * tree.depth;
      final int yTop = (int) (tree.topV * (height - 1));
      final float span = Math.max(.05f, 1 - tree.topV);
      for (int y = yTop; y < height; ++y) {
        final float v = y / (height - 1f);
        final float heightFraction = (1 - v) / span;
        final float center = tree.u + swayAt(tree, heightFraction);
        // Trunks taper: thickest at the base, thinnest where they meet the crown.
        final float halfWidth =
          tree.halfWidth * this.trunkAmount * (.5f + .5f * (1 - heightFraction));
        final int reach = 1 + (int) (halfWidth * width);
        final int centerColumn = (int) Math.floor(center * width);
        for (int d = -reach; d <= reach; ++d) {
          final int x = wrapColumn(centerColumn + d, width);
          final float u = (x + .5f) / width;
          final float distance = Math.abs(wrapDelta(u - center));
          final float coverage =
            LXUtils.clampf((halfWidth - distance) * width + .5f, 0, 1) * opacity;
          final int index = x * height + y;
          if (coverage > trunk[index]) {
            trunk[index] = coverage;
            trunkDepth[index] = tree.depth;
          }
        }
      }
    }
  }

  /**
   * Accumulate occlusion down a slanted ray, one row at a time. Each row reads
   * the row above it at a fractional column offset, so a shaft of light keeps
   * its angle without any per-pixel marching.
   *
   * The accumulator is retained rather than summed outright, so shadow depth
   * saturates instead of running away: light still reaches the forest floor,
   * and a hole in the canopy still throws a shaft the whole way down.
   *
   * <p><b>Rows are the outer loop, and have to be.</b> A slanted ray reads two
   * neighbouring columns of the row above, and which neighbours depends on the
   * sign of the slant. Iterating columns first leaves those neighbours unwritten
   * for the whole negative half of the slant range — the read would land on the
   * previous frame's occlusion, so the shafts would lag the canopy by a frame on
   * one side of centre and not the other. Row-major means the row above is always
   * complete, whichever way the light leans.</p>
   */
  private void marchLight(Raster raster) {
    final int width = raster.width;
    final int height = raster.height;
    final float[] foliage = raster.foliage;
    final float[] trunk = raster.trunk;
    final float[] shrub = raster.shrub;
    final float[] occlusion = raster.occlusion;

    final float drift = this.leafDrift;
    final float softness = .1f;
    final float threshold = .08f + .78f * (1 - this.densityAmount);
    final float step = this.columnSlant;
    final int stepFloor = (int) Math.floor(step);
    final float stepFraction = step - stepFloor;

    for (int y = 0; y < height; ++y) {
      final float v = y / (height - 1f);
      for (int x = 0; x < width; ++x) {
        final float u = (x + .5f) / width;
        final float edge = raster.edge[x];
        final int column = x * height;

        float leaves = 0;
        final float depth = edge - v;
        if (depth > -softness) {
          // How far inside the canopy this pixel sits, 0 at the underside.
          final float body = LXUtils.clampf(depth / softness, 0, 1);
          final float texture = this.canopyTexture.sample(u, this.surfaceRepeat, drift, v * .9f);
          final float grain =
            this.leafTexture.sample(u, this.surfaceRepeat * 2, drift * 1.3f, v * .8f);
          final float field =
            .5f + 1.3f * (texture - .5f) + .9f * (grain - .5f);
          // Thresholding a continuous field is what makes leaf-sized clumps with
          // sky between them; the threshold rises toward the underside so the
          // canopy frays out rather than ending on a drawn line.
          final float top = LXUtils.clampf((v - raster.crest[x]) / .13f, 0, 1);
          final float local = threshold + .15f * (1 - body) + .5f * (1 - top);
          leaves = LXUtils.clampf((field - local) * LEAF_EDGE + .5f, 0, 1);
        }
        foliage[column + y] = leaves;

        final float understory =
          LXUtils.clampf((v - raster.floorEdge[x] + .03f) / .06f, 0, 1);
        shrub[column + y] = understory;

        final float local = leaves + .6f * trunk[column + y] + understory;
        if (y == 0) {
          occlusion[column] = local;
        } else {
          final int a = wrapColumn(x - stepFloor, width) * height + y - 1;
          final int b = wrapColumn(x - stepFloor - 1, width) * height + y - 1;
          occlusion[column + y] =
            RETENTION * LXUtils.lerpf(occlusion[a], occlusion[b], stepFraction) + local;
        }
      }
    }
  }

  private void writeColors(Raster raster, Apotheneum.Orientation orientation) {
    if (orientation == null) {
      return;
    }
    final int width = raster.width;
    final int height = raster.height;
    final float drift = this.leafDrift;
    final int flutterRepeat = this.surfaceRepeat * 3;
    // Which surface's RGB-table slice this call reads -- see the SURFACE_COUNT-sized tables'
    // javadoc above. Falls back to surface 0's slice if the orientation resolves to no known
    // surface (should not happen with Apotheneum loaded, but avoids an NPE/-1 index if it did).
    final ApotheneumColor.Surface writeSurface = ApotheneumColor.Surface.of(orientation);
    final int surfaceOffset = surfaceRgbOffset(
      writeSurface != null ? writeSurface : ApotheneumColor.Surface.CUBE_EXTERIOR);

    int x = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final int available = orientation.available(x);
      final float u = (x + .5f) / width;
      final int base = x * height;
      int y = 0;
      for (LXPoint p : column.points) {
        // Every write goes through the view mask, including the door blackout
        // below: a view is an input model, not an output mask.
        if (!isViewPoint(p.index)) {
          ++y;
          continue;
        }
        if (y >= available) {
          colors[p.index] = LXColor.BLACK;
          ++y;
          continue;
        }
        final float v = y / (height - 1f);
        final float leaves = raster.foliage[base + y];
        final float bark = raster.trunk[base + y];
        final float shadow = raster.occlusion[base + y];

        // Two depths of the same shadow: a crisp one that carves the shafts,
        // and a soft one standing in for skylight bouncing around the leaves.
        final int shadowIndex = shadowIndex(shadow);
        final float direct = this.directLut[shadowIndex];
        final float ambient = this.ambientLut[shadowIndex];

        // Behind everything: sky the whole depth of the canopy, so the leaves read
        // as a lace against it, and gloom below the underside, where the eye is
        // looking sideways into the forest rather than up through it.
        final float below = Math.max(0, v - raster.edge[x]);
        final float sky = (float) Math.exp(-GLOOM_FALLOFF * below);
        // A gap in the canopy is a gap all the way through, so it shows sky at
        // full strength rather than sky dimmed by the shadow depth beside it.
        // Below the underside the eye is looking sideways into the forest, where
        // only what got past the leaves is left.
        // Sky is dimmer the closer you are to the underside: more layers of leaf
        // between you and it. That also keeps the canopy edge from reading as a
        // drawn line where the background steps down to gloom.
        final float body = LXUtils.clampf((raster.edge[x] - v) / .18f, 0, 1);
        final float inside = (.5f + .5f * ambient) * (.6f + .4f * body);
        final float outside = .42f * sky * (.15f + .85f * direct);
        float level = LXUtils.lerpf(inside, outside, LXUtils.clampf(below / .06f, 0, 1))
          + 1.3f * this.hazeAmount * direct * (1 - sky) * (.3f + .7f * v)
          + .03f * this.hazeAmount * ambient * v;

        // Colour last, per the resolution order the base class fixes: the palette
        // stop and its offsets are already resolved, the physics wobble is already
        // in the table, and this is the brightness scaling by the pattern's own
        // intensity mask.
        final int daylight = 3 * (surfaceOffset + this.daylightIndex[shadowIndex]);
        float red = this.daylightRgb[daylight] * level;
        float green = this.daylightRgb[daylight + 1] * level;
        float blue = this.daylightRgb[daylight + 2] * level;

        final int vegetation = 3 * (surfaceOffset + this.vegetationIndex[shadowIndex]);
        final float vegetationR = this.vegetationRgb[vegetation];
        final float vegetationG = this.vegetationRgb[vegetation + 1];
        final float vegetationB = this.vegetationRgb[vegetation + 2];

        // Everything in front of the daylight is backlit, so it occludes rather
        // than adds.
        if (leaves > 0) {
          final float turn =
            .62f * this.leafTexture.sample(u, flutterRepeat, drift * 1.9f, v) +
            .38f * this.leafTexture.sample(u, flutterRepeat, 91 - drift * .8f, .25f + .6f * v);
          // Leaves are lit through less canopy than the shafts cut through, so the
          // mass keeps some depth instead of going flat black underneath.
          final float through = this.throughLut[shadowIndex];
          // A leaf turning edge-on to the light flares, then is gone again.
          final float flare = turn * turn * turn * turn;
          final float lit = LEAF_ALBEDO * (.2f + .8f * through)
            + .8f * this.flutterAmount * flare * through;
          red = LXUtils.lerpf(red, vegetationR * lit, leaves);
          green = LXUtils.lerpf(green, vegetationG * lit, leaves);
          blue = LXUtils.lerpf(blue, vegetationB * lit, leaves);
        }

        if (bark > 0) {
          final float lit = .012f * direct + .03f * this.hazeAmount * ambient;
          final float mix = bark * (1 - .3f * (1 - raster.trunkDepth[base + y]));
          red = LXUtils.lerpf(red, vegetationR * lit, mix);
          green = LXUtils.lerpf(green, vegetationG * lit, mix);
          blue = LXUtils.lerpf(blue, vegetationB * lit, mix);
        }

        // Undergrowth is not quite opaque, so a shaft landing on the floor still
        // shows as dappled light rather than being swallowed by a black bar.
        final float undergrowth = raster.shrub[base + y];
        if (undergrowth > 0) {
          final float lit = .07f * direct + .05f * this.hazeAmount * ambient;
          final float mix = .82f * undergrowth;
          red = LXUtils.lerpf(red, vegetationR * lit, mix);
          green = LXUtils.lerpf(green, vegetationG * lit, mix);
          blue = LXUtils.lerpf(blue, vegetationB * lit, mix);
        }

        colors[p.index] = LXColor.rgbf(tone(red), tone(green), tone(blue));
        ++y;
      }
      ++x;
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Wind",
      newKnob(this.wind),
      newKnob(this.gust),
      newKnob(this.flutter)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Canopy",
      newKnob(this.canopy),
      newKnob(this.density),
      newKnob(this.leafScale)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Trees",
      newKnob(this.treeCount),
      newKnob(this.trunkWidth),
      newKnob(this.seed)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Light",
      newKnob(this.shafts),
      newKnob(this.slant),
      newKnob(this.haze)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Tone",
      newKnob(this.brightness),
      newKnob(this.gamma)
    ).setChildSpacing(6);

    // Colour columns are built by the base class and appended last, so they land
    // at the end of the panel, contiguous with each other.
    buildColorDeviceControls(ui, uiDevice);
  }
}
