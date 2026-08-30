/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 */

package apotheneum.doved.patterns;

import java.util.Arrays;

import apotheneum.Apotheneum;
import apotheneum.doved.modulators.ApotheneumColor;
import apotheneum.mcslee.Surfacing;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.utils.Noise;

/**
 * A sheet of water spilling from a lip near the top, breaking into strands as it
 * falls, and churning where it lands.
 *
 * The curtain is not simulated. A parcel released at the lip reaches depth
 * {@code h} after {@code tau = sqrt(2h/g)}, so the water visible at {@code h} was
 * released at {@code t - tau}. Indexing a noise field by release time rather than
 * by position gives the fall its physics for free: because {@code dtau/dh} shrinks
 * as {@code h} grows, equal noise intervals cover larger vertical spans further
 * down and the streaks elongate toward the bottom on their own.
 *
 * Only the upward spray at the impact line carries per-particle state, from a
 * fixed pool. Everything else is a field evaluated per point.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Waterfall")
@LXComponent.Description("A sheet of water pouring from a lip, shattering into strands, splashing at the floor")
public class Waterfall extends ColorNativePattern {

  public final CompoundParameter flow =
    new CompoundParameter("Flow", 2.4, 0, 8)
    .setDescription("How fast water is released from the lip");

  public final CompoundParameter stretch =
    new CompoundParameter("Stretch", .5, .15, 1.6)
    .setDescription("Vertical extent of a streak; lower stretches streaks further down the fall");

  public final CompoundParameter turbulence =
    new CompoundParameter("Turbulence", .5, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Warps the field so strands wander and braid instead of falling dead straight");

  public final CompoundParameter scale =
    new CompoundParameter("Scale", 5, 1, 14)
    .setDescription("Horizontal detail of the sheet; higher makes narrower strands");

  public final CompoundParameter lip =
    new CompoundParameter("Lip", .12, 0, .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Height of the lip the water spills from, measured down from the top");

  public final CompoundParameter ledge =
    new CompoundParameter("Ledge", .6, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How broken the rock at the lip is; notches sit lower and pour harder");

  public final CompoundParameter variation =
    new CompoundParameter("Variation", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Morphs which notches cut the ledge; wire a modulator here for evolving rock");

  public final CompoundDiscreteParameter falls =
    new CompoundDiscreteParameter("Falls", 1, 1, 9)
    .setDescription("How many separate falls are spaced around the ring");

  public final CompoundParameter width =
    new CompoundParameter("Width", 1, .04, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Fraction of each fall's arc that carries water");

  public final CompoundParameter rotate =
    new CompoundParameter("Rotate", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Rotates the falls around the ring");

  public final CompoundParameter breakup =
    new CompoundParameter("Breakup", .18, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Depth at which the solid sheet starts coming apart");

  public final CompoundParameter shatter =
    new CompoundParameter("Shatter", .75, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How far apart the sheet comes below the breakup point");

  public final CompoundParameter floor =
    new CompoundParameter("Floor", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Height the water lands at, 0 being the base");

  public final BooleanParameter linkFloor =
    new BooleanParameter("Link", false)
    .setDescription("Take the landing height from a Surfacing pattern on this channel");

  public final CompoundParameter churn =
    new CompoundParameter("Churn", .9, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Brightness of the broken water at the impact line");

  public final CompoundParameter spray =
    new CompoundParameter("Spray", .5, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of spray thrown back up out of the impact");

  public final CompoundParameter mist =
    new CompoundParameter("Mist", .35, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Soft haze hanging above the impact");

  public final CompoundParameter spill =
    new CompoundParameter("Spill", .6, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("How much water landing on a door top runs sideways and pours off its corners");

  private static final int CUBE = 0;
  private static final int CYLINDER = 1;

  private static final int CUBE_WIDTH = Apotheneum.Cube.Ring.LENGTH;
  private static final int CYLINDER_WIDTH = Apotheneum.RING_LENGTH;

  /** Threshold applied at the lip: low enough that the sheet reads as solid, with striations still visible in it. */
  private static final float THRESHOLD_LIP = .22f;
  private static final float THRESHOLD_FLOOR_MIN = .4f;
  private static final float THRESHOLD_FLOOR_MAX = .8f;
  /** Noise band over which a strand's edge fades in, once past the threshold. */
  private static final float EDGE = .07f;

  private static final float NOISE_GAIN = .55f;

  // Domain warp. Without it the curtain is a rigid texture translating past a window —
  // every frame shows the same shapes at a new offset, which is what makes an unwarped
  // field read as mechanical however good the motion curve is. Displacing the sample
  // point by a second, slower, evolving noise lets strands wander, braid and merge.
  private static final float WARP_SCALE = .5f;
  private static final float WARP_DEPTH = .35f;
  private static final float WARP_RATE = .35f;
  private static final float WARP_ANGLE = 1.6f;
  private static final float WARP_TIME = .7f;

  // Per-column fall rate. Every strand otherwise descends on identical timing, which
  // reads as a comb falling rather than as water. Static, like the ledge: a chute's
  // character persists rather than shimmering.
  private static final float SPEED_SCALE = .25f;
  private static final float SPEED_SEED = 5.7f;
  private static final float SPEED_GAIN = 1.5f;
  private static final float SPEED_SPREAD = .4f;

  /** How much the noise dims the water it lets through, leaving striations in the sheet. */
  private static final float TEXTURE = .35f;

  /** Deepest a notch in the rock can cut below the nominal lip, in rows. */
  private static final float LEDGE_ROWS = 8f;

  /** Ledge features run wider than the strands do — rocks, not water. */
  private static final float LEDGE_SCALE = .2f;

  /** Fixed sample offset. At Variation=0 the rock is static and does not crawl. */
  private static final float LEDGE_SEED = 11.3f;

  /** Widens the notch distribution. Raw Perlin barely leaves its midpoint, which flattens
      the ledge to a row or two of wobble instead of real rock. */
  private static final float LEDGE_GAIN = 1.7f;

  // Variation walks a circle through noise space rather than sliding linearly through it, so a
  // modulator wired here (LFO, Sample & Hold) can loop or reverse with no seam at 0/1 and no
  // "scrolling" look — the notches genuinely reshuffle rather than drifting past like a texture.
  private static final float VARIATION_RADIUS = 6f;

  /** How far a notch shifts the threshold, so deep notches pour harder than shallow ones. */
  private static final float CHUTE = .13f;

  /** Rows of solid crest drawn right at the lip, where the water has not begun to break. */
  private static final int CREST_ROWS = 2;

  /** The sheet fans out as it falls; the arc at the floor is this much wider than at the lip. */
  private static final float SPREAD = .25f;

  /** Width of a fall's soft rim, as a floor plus a fraction of the fall's own width. */
  private static final float EDGE_MIN = .03f;
  private static final float EDGE_FRACTION = .12f;

  /** Impact and mist reach wider than the water that feeds them. */
  private static final float IMPACT_SPREAD = 1.25f;

  private static final float CHURN_ROWS = 2.4f;
  private static final float MIST_ROWS = 5f;
  private static final float MIST_LEVEL = .35f;

  // Ledge model for a door top. A door column is only logically shorter (see
  // Apotheneum.Orientation#available) — every column carries a full-height run of
  // points, so the last usable row is real rock, not a gap. Water reaching it is
  // diverted sideways along the lintel toward whichever corner is nearer, gathering
  // as it goes, then pours down the full-height column just past that corner (see
  // renderDoorSpill). Diverting the water also quiets the door's own churn/mist in
  // the main loop below (see churnDivert) — water running off sideways isn't
  // churning in place, so the door goes quieter as spill rises while the corners
  // get louder, which is what makes it read as concentrating the flow rather than
  // as a uniformly brighter smear.
  /** Fraction of a column's lateral flow that survives into its neighbour, per step. */
  private static final float SPILL_RETENTION = .75f;
  // At a door column, floorRow IS the lintel, so normalized depth there is always
  // ~1 — the coherence gradient has already pushed the shatter threshold to its
  // highest point, so "presence" sampled at the bottom of the fall is near-empty
  // almost everywhere by construction, not because there's no water. The sheet
  // shattering is a statement about how the fall LOOKS, not about how much water
  // is in it: the mass that left the lip in a column still reaches the bottom of
  // that column. So arriving water is driven mostly by mass at the lip (whether a
  // fall is aimed here, and how much this notch concentrates flow), with only a
  // minority instantaneous term so the pour still breathes with the strands above.
  /** Floor under the notch-driven mass weight, so a shallow (non-chute) column still
      carries a sane fraction of the flow rather than dropping toward zero. */
  private static final float SPILL_MASS_FLOOR = .5f;
  /** How much of "arriving" is mass-driven vs the instantaneous curtain average. */
  private static final float SPILL_MASS_WEIGHT = .7f;
  /** Rows the instantaneous term is averaged over (the lintel and rows above it). */
  private static final int SPILL_ARRIVAL_ROWS = 3;
  /** Rows of lintel sheet drawn above the ledge itself. */
  private static final int SPILL_ROWS = 3;
  /** How fast the lintel sheet dims moving up off the ledge. */
  private static final float SPILL_DECAY_ROWS = 1.5f;
  /** Tuned so a full-strength arriving sheet reads roughly 30% brightness mid-lintel,
      against the normalized flow scale in spillFlowScale. */
  private static final float SPILL_LEVEL = .9f;
  // Slow and coarse: a per-pixel or fast-moving sample would read as noise rather
  // than the gentle shimmer of a thin sheet of water.
  private static final float SPILL_SHIMMER_SCALE = .3f;
  private static final float SPILL_SHIMMER_RATE = .12f;
  /** Rows below a corner at full pour strength before it eases to a sustained trickle. */
  private static final float SPILL_POUR_ROWS = 3f;
  private static final float SPILL_POUR_SUSTAIN = .4f;
  /** Full strength for a fully-fed corner: the flow feeding it (spillFlowScale) is
      already normalized to top out at 1, so this needs no further discount. */
  private static final float SPILL_POUR_LEVEL = 1f;
  /** How much of a door column's own churn/mist is diverted away, proportional to
      spill: the water that would have churned there has run off sideways instead.
      Kept modest so the lintel reads as quieter, not as the whole visible effect —
      the corners pouring are what the eye should go to. */
  private static final float SPILL_CHURN_DIVERT = .5f;
  /** A door run is at most Apotheneum.DOOR_WIDTH columns; sized with headroom, not exactly. */
  private static final int SPILL_RUN_CAPACITY = Apotheneum.DOOR_WIDTH * 2;

  // Two colors, via ColorNativePattern's primary/secondary roles: each reads live from
  // the project palette and stays read-only in the device UI, so the palette remains
  // the single source of truth. Each role's "Amount" knob lets it shift saturation/
  // brightness in response to a physics signal specific to what it's painting —
  // cragginess for rock, fall speed for water.
  public final ColorRole rockColor;
  public final ColorRole waterColor;

  // The rock face above the lip is a flat height field (no SDF needed, unlike a
  // free-standing boulder): a column is rock for every row above its own lipRow. Rim
  // brightness catches light near the water's edge; fine noise breaks up the flat
  // color into something with grain to it.
  private static final float ROCK_BASE = .22f;
  private static final float ROCK_RIM = .5f;
  private static final float ROCK_RIM_ROWS = 3f;
  private static final float ROCK_TEXTURE = .25f;
  private static final float ROCK_TEXTURE_SCALE = 1.1f;

  private static final int SPRAY_MAX = 280;
  private static final float SPRAY_SPAWN_RATE = 230f;
  private static final float SPRAY_GRAVITY = 30f;
  private static final float SPRAY_VELOCITY_MIN = 8f;
  private static final float SPRAY_VELOCITY_MAX = 24f;
  private static final float SPRAY_DRIFT = 2.5f;
  private static final int SPRAY_SPAWN_TRIES = 6;

  // Per-column unit circle coordinates, so noise wraps seamlessly around the ring
  // with no seam at column zero.
  private final float[] cubeCos = new float[CUBE_WIDTH];
  private final float[] cubeSin = new float[CUBE_WIDTH];
  private final float[] cylinderCos = new float[CYLINDER_WIDTH];
  private final float[] cylinderSin = new float[CYLINDER_WIDTH];

  private final float[] sqrtDepth = new float[Apotheneum.GRID_HEIGHT + 1];

  // Both decays are parameterised by constants, so their falloff by row is fixed for
  // the life of the pattern and there is no reason to call exp() per point.
  private final float[] churnDecay = new float[Apotheneum.GRID_HEIGHT + 1];
  private final float[] mistDecay = new float[Apotheneum.GRID_HEIGHT + 1];
  private final float[] rockRimDecay = new float[Apotheneum.GRID_HEIGHT + 1];

  // Spray is accumulated into these before the field pass, so every point takes a
  // single write and the curtain, churn, mist and spray stay separate scalars
  // until that write.
  private final float[] cubeSpray = new float[CUBE_WIDTH * Apotheneum.GRID_HEIGHT];
  private final float[] cylinderSpray = new float[CYLINDER_WIDTH * Apotheneum.CYLINDER_HEIGHT];

  // Door spill is accumulated the same way, into its own grid, so it stays a
  // separate additive scalar until the single existing composite write.
  private final float[] cubeSpill = new float[CUBE_WIDTH * Apotheneum.GRID_HEIGHT];
  private final float[] cylinderSpill = new float[CYLINDER_WIDTH * Apotheneum.CYLINDER_HEIGHT];

  // Scratch for the door-spill pre-pass: one door run's worth of columns at a time,
  // reused across every run and both shapes since renderShape runs strictly
  // sequentially, never concurrently.
  private final int[] spillRunColumns = new int[SPILL_RUN_CAPACITY];
  private final int[] spillRunLintel = new int[SPILL_RUN_CAPACITY];
  private final float[] spillRunArriving = new float[SPILL_RUN_CAPACITY];
  private final float[] spillRunFlow = new float[SPILL_RUN_CAPACITY];

  // spillFlowScale[m] normalizes a half-run of m columns so a fully-fed half
  // (arriving=1 throughout) reaches exactly 1 at its far end. The recurrence sums a
  // geometric series 1+R+R^2+...+R^(m-1) = (1-R^m)/(1-R), so multiplying by the
  // reciprocal of that undoes the sum instead of leaving the corner near m columns'
  // worth of water. Indexed by half-run length rather than calling Math.pow per frame.
  private final float[] spillFlowScale = new float[SPILL_RUN_CAPACITY + 1];

  private final float[] spillDecay = new float[SPILL_ROWS];
  private final float[] spillPourDecay = new float[Apotheneum.GRID_HEIGHT + 1];

  private final int[] sprayColumns = new int[Math.max(CUBE_WIDTH, CYLINDER_WIDTH)];

  private final int[] sprayShape = new int[SPRAY_MAX];
  private final float[] sprayX = new float[SPRAY_MAX];
  private final float[] sprayRise = new float[SPRAY_MAX];
  private final float[] sprayVelocityX = new float[SPRAY_MAX];
  private final float[] sprayVelocityRise = new float[SPRAY_MAX];
  private final boolean[] sprayAlive = new boolean[SPRAY_MAX];
  private int sprayNext = 0;
  private double sprayAccumulator = 0;

  private double time = 0;
  private Surfacing surfacing = null;

  public Waterfall(LX lx) {
    super(lx, .7, .7);
    this.rockColor = this.primary;
    this.waterColor = this.secondary;

    addParameter("flow", this.flow);
    addParameter("stretch", this.stretch);
    addParameter("turbulence", this.turbulence);
    addParameter("scale", this.scale);
    addParameter("lip", this.lip);
    addParameter("ledge", this.ledge);
    addParameter("variation", this.variation);
    addParameter("falls", this.falls);
    addParameter("width", this.width);
    addParameter("rotate", this.rotate);
    addParameter("breakup", this.breakup);
    addParameter("shatter", this.shatter);
    addParameter("floor", this.floor);
    addParameter("linkFloor", this.linkFloor);
    addParameter("churn", this.churn);
    addParameter("spray", this.spray);
    addParameter("mist", this.mist);
    addParameter("spill", this.spill);

    for (int i = 0; i < CUBE_WIDTH; ++i) {
      final double theta = LX.TWO_PI * i / CUBE_WIDTH;
      this.cubeCos[i] = (float) Math.cos(theta);
      this.cubeSin[i] = (float) Math.sin(theta);
    }
    for (int i = 0; i < CYLINDER_WIDTH; ++i) {
      final double theta = LX.TWO_PI * i / CYLINDER_WIDTH;
      this.cylinderCos[i] = (float) Math.cos(theta);
      this.cylinderSin[i] = (float) Math.sin(theta);
    }
    for (int d = 0; d < this.sqrtDepth.length; ++d) {
      this.sqrtDepth[d] = (float) Math.sqrt(d);
      this.churnDecay[d] = (float) Math.exp(-d / CHURN_ROWS);
      this.mistDecay[d] = (float) Math.exp(-d / MIST_ROWS);
      this.rockRimDecay[d] = (float) Math.exp(-d / ROCK_RIM_ROWS);
      this.spillPourDecay[d] = SPILL_POUR_SUSTAIN + (1f - SPILL_POUR_SUSTAIN) * (float) Math.exp(-d / SPILL_POUR_ROWS);
    }
    for (int r = 0; r < SPILL_ROWS; ++r) {
      this.spillDecay[r] = (float) Math.exp(-r / SPILL_DECAY_ROWS);
    }
    for (int m = 1; m <= SPILL_RUN_CAPACITY; ++m) {
      this.spillFlowScale[m] = (1f - SPILL_RETENTION) / (1f - (float) Math.pow(SPILL_RETENTION, m));
    }
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);
    this.time += deltaMs * .001;

    this.rockColor.update();
    this.waterColor.update();

    this.surfacing = null;
    if (this.linkFloor.isOn()) {
      if (getParent() instanceof LXChannel channel) {
        for (LXPattern pattern : channel.patterns) {
          if (pattern instanceof Surfacing found) {
            this.surfacing = found;
            break;
          }
        }
      }
    }

    Arrays.fill(this.cubeSpray, 0f);
    Arrays.fill(this.cylinderSpray, 0f);
    Arrays.fill(this.cubeSpill, 0f);
    Arrays.fill(this.cylinderSpill, 0f);
    updateSpray(deltaMs);

    renderShape(Apotheneum.cube.exterior, CUBE, this.cubeCos, this.cubeSin, this.cubeSpray, this.cubeSpill, Apotheneum.GRID_HEIGHT);
    renderShape(Apotheneum.cylinder.exterior, CYLINDER, this.cylinderCos, this.cylinderSin, this.cylinderSpray, this.cylinderSpill, Apotheneum.CYLINDER_HEIGHT);

    copyExterior();
  }

  private void renderShape(Apotheneum.Orientation orientation, int shape, float[] cos, float[] sin, float[] sprayGrid, float[] spillGrid, int height) {
    final int columnCount = cos.length;
    final ApotheneumColor.Surface surface = ApotheneumColor.Surface.of(orientation);

    final float radius = this.scale.getValuef() * columnCount / 100f;
    final float flow = this.flow.getValuef() * (float) this.time;
    final float stretch = this.stretch.getValuef();
    final float breakup = this.breakup.getValuef();
    final float thresholdFloor = LXUtils.lerpf(THRESHOLD_FLOOR_MIN, THRESHOLD_FLOOR_MAX, this.shatter.getValuef());
    final float turbulence = this.turbulence.getValuef();
    final float warpAngle = turbulence * WARP_ANGLE;
    final float warpTime = turbulence * WARP_TIME;
    final float churn = this.churn.getValuef();
    final float mist = this.mist.getValuef();
    final float noiseTime = (float) this.time;

    final int baseLipRow = Math.round(this.lip.getValuef() * (height - 1));
    final float ledge = this.ledge.getValuef();
    final float ledgeRows = ledge * LEDGE_ROWS;

    // A closed loop in noise space: 0 and 1 land on the same point, so a modulator wired to
    // Variation can free-run or loop without the ledge shape jumping at the wrap.
    final float variationPhase = (float) (this.variation.getValuef() * LX.TWO_PI);
    // Shifted so Variation=0 lands exactly on (0,0): existing presets and the render
    // evidence already published for this pattern stay bit-identical by default.
    final float variationX = VARIATION_RADIUS * ((float) Math.cos(variationPhase) - 1f);
    final float variationY = VARIATION_RADIUS * (float) Math.sin(variationPhase);

    // Skip the whole pre-pass at spill=0: the grid is already zeroed for the frame,
    // so this leaves output bit-identical to a pattern with no spill mechanism at all.
    final float spillAmount = this.spill.getValuef();
    if (spillAmount > 0) {
      renderDoorSpill(orientation, shape, cos, sin, spillGrid, height, columnCount, radius, flow,
        stretch, breakup, thresholdFloor, turbulence, warpAngle, warpTime, noiseTime, ledge,
        ledgeRows, baseLipRow, variationX, variationY, spillAmount);
    }

    for (int x = 0; x < columnCount; ++x) {
      final float sheet = lipMask(x, columnCount, 1f);
      final float impact = lipMask(x, columnCount, IMPACT_SPREAD);
      if ((sheet <= 0) && (impact <= 0)) {
        continue;
      }

      final float cx = cos[x] * radius;
      final float cz = sin[x] * radius;

      // One noise field describes the rock at the lip, and it does two jobs at once:
      // a notch both sits lower and pours harder. Tying them together is what stops the
      // strands reading as an evenly spaced comb — a real lip has a few heavy chutes and
      // a lot of thin trickle. At Variation=0 there is no time term: rock does not move.
      final float notch = LXUtils.clampf(.5f + LEDGE_GAIN * Noise.stb_perlin_noise3(
        cx * LEDGE_SCALE + variationX, LEDGE_SEED + variationY, cz * LEDGE_SCALE, 0, 0, 0), 0, 1);
      final int lipRow = Math.min(height - 2, baseLipRow + Math.round(ledgeRows * (1f - notch)));
      final float chute = CHUTE * ledge * (2f * notch - 1f);

      // Independent of the ledge: a chute can be heavy and slow, or thin and quick.
      final float speed = LXUtils.clampf(.5f + SPEED_GAIN * Noise.stb_perlin_noise3(
        cx * SPEED_SCALE, SPEED_SEED, cz * SPEED_SCALE, 0, 0, 0), 0, 1);
      final float columnStretch = stretch * (1f + SPEED_SPREAD * (2f * speed - 1f));

      final int floorRow = floorRow(orientation, shape, x, height);
      final int span = floorRow - lipRow;
      if (span <= 0) {
        continue;
      }

      final Apotheneum.Column column = orientation.column(x);

      // One color per contribution, resolved once per column: rock reads its own
      // cragginess, water reads its own fall speed. Both are static per column, like
      // the fields that drive them, so there is nothing to gain sampling per pixel.
      final int rockPixelColor = this.rockColor.color(surface, 2f * notch - 1f);
      final int waterPixelColor = this.waterColor.color(surface, 2f * speed - 1f);

      // The rock face: everything above the water's own lip on this column. A flat
      // height field, not a distance field — a column simply is rock above lipRow —
      // so no per-pixel geometry test is needed, only a rim highlight and a little
      // grain so it doesn't read as a solid block of color.
      for (int y = 0; y < lipRow; ++y) {
        final int depthFromEdge = lipRow - y;
        final float rim = ROCK_RIM * this.rockRimDecay[depthFromEdge - 1];
        final float fine = .5f + .5f * Noise.stb_perlin_noise3(
          cx * ROCK_TEXTURE_SCALE, cz * ROCK_TEXTURE_SCALE, y * ROCK_TEXTURE_SCALE, 0, 0, 0);
        final float rockIntensity = LXUtils.clampf(ROCK_BASE + rim + ROCK_TEXTURE * (fine - .5f), 0, 1);
        if (rockIntensity > 0) {
          colors[column.points[y].index] = LXColor.scaleBrightness(rockPixelColor, rockIntensity);
        }
      }

      // Water that runs off sideways at a door isn't churning in place there, so
      // divert the door's own churn/mist away in proportion to how much is being
      // carried off. At spill=0 the multiplier is exactly 1 for every column, door
      // or not, so this is a no-op there — the bit-identical guarantee holds.
      final float churnDivert = (orientation.available(x) < height)
        ? (1f - spillAmount * SPILL_CHURN_DIVERT) : 1f;
      final float columnChurn = churn * churnDivert;
      final float columnMist = mist * churnDivert;

      // Churn and mist vary slowly across the ring, so one sample per column each.
      final float churnNoise = (impact > 0) && (columnChurn > 0)
        ? .5f + .5f * Noise.stb_perlin_noise3(cx * .8f, noiseTime * 5.5f, cz * .8f, 0, 0, 0)
        : 0f;
      final float mistNoise = (impact > 0) && (columnMist > 0)
        ? .5f + .5f * Noise.stb_perlin_noise3(cx * .25f, noiseTime * .18f, cz * .25f, 0, 0, 0)
        : 0f;

      final int sprayBase = x * height;

      for (int y = lipRow; y <= floorRow; ++y) {
        final float curtain = curtainAt(x, columnCount, cx, cz, sheet, lipRow, span, columnStretch,
          chute, flow, breakup, thresholdFloor, turbulence, warpAngle, warpTime, noiseTime, y);

        float band = 0;
        float haze = 0;
        if (impact > 0) {
          final int above = floorRow - y;
          if (columnChurn > 0) {
            final int ci = Math.min(this.churnDecay.length - 1,
              Math.round(above * (.6f + .9f * churnNoise)));
            band = impact * columnChurn * this.churnDecay[ci] * (.5f + .5f * churnNoise);
          }
          if (columnMist > 0) {
            haze = impact * columnMist * MIST_LEVEL * this.mistDecay[above] * (.3f + .7f * mistNoise);
          }
        }

        final float drops = sprayGrid[sprayBase + y];
        final float spill = spillGrid[sprayBase + y];

        final float level = curtain + band + haze + drops + spill;
        if (level > 0) {
          final LXPoint p = column.points[y];
          colors[p.index] = LXColor.scaleBrightness(waterPixelColor, LXUtils.clampf(level, 0, 1));
        }
      }
    }
  }

  /**
   * The curtain's intensity at one point, factored out of the main pass so the
   * door-spill pre-pass can ask it the exact same question — "how much water is
   * here?" — instead of guessing at a value that would fall out of sync with the
   * strands actually falling above it. Same inputs, same arithmetic, same answer.
   */
  private float curtainAt(int columnIndex, int columnCount, float cx, float cz, float sheet,
      int lipRow, int span, float columnStretch, float chute, float flow, float breakup,
      float thresholdFloor, float turbulence, float warpAngle, float warpTime, float noiseTime,
      int y) {
    if ((y < lipRow) || (sheet <= 0) || (span <= 0)) {
      return 0f;
    }

    final int depth = y - lipRow;
    final float normalized = depth / (float) span;

    // The arc fans out as the water falls away from the lip.
    final float mask = lipMask(columnIndex, columnCount, 1f + SPREAD * normalized);

    if (depth < CREST_ROWS) {
      return mask;
    }
    if (mask <= 0) {
      return 0f;
    }

    // Two octaves, because one gives strands with no detail in them. Scaled
    // wide because raw Perlin clusters hard around its midpoint, which would
    // leave most of the threshold range below doing nothing at all.
    final float release = flow - columnStretch * this.sqrtDepth[depth];
    final float warp = (turbulence > 0) ? Noise.stb_perlin_noise3(
      cx * WARP_SCALE, release * WARP_DEPTH + noiseTime * WARP_RATE, cz * WARP_SCALE, 0, 0, 0) : 0f;
    final float raw = LXUtils.clampf(.5f + NOISE_GAIN * Noise.stb_perlin_fbm_noise3(
      cx + warpAngle * warp, release + warpTime * warp, cz, 2f, .5f, 2), 0, 1);

    // A threshold that rises with depth: everything survives at the lip, only
    // the peaks survive further down, so the sheet shatters into strands.
    // Eased so the sheet loses coherence quickly once it passes the breakup
    // depth, rather than staying solid most of the way down.
    final float linear = (breakup >= 1f) ? 0f
      : LXUtils.clampf((normalized - breakup) / (1f - breakup), 0, 1);
    // Square-rooted, so the threshold climbs fast just past the breakup depth
    // instead of leaving the sheet coherent most of the way down.
    final float ramp = (float) Math.sqrt(linear);
    final float threshold = LXUtils.lerpf(THRESHOLD_LIP, thresholdFloor, ramp) - chute;

    // Where water is, and how bright it is, are two things. The threshold only
    // decides the first: water that survives it is full strength with a soft
    // rim, so strands stay as bright as the sheet they came out of. The second
    // is a gentle modulation that leaves striations in the solid part.
    final float presence = LXUtils.clampf((raw - threshold) / EDGE, 0, 1);
    return mask * presence * presence * (3f - 2f * presence) * (1f - TEXTURE + TEXTURE * raw);
  }

  /**
   * Door-spill pre-pass. A door column is only logically shorter — every column
   * carries a full-height run of points (see Apotheneum.Orientation#available) —
   * so the last usable row of a door column is real rock, a lintel, not a gap.
   * Water reaching it doesn't stop dead: it runs sideways along the lintel toward
   * whichever corner of the door is nearer, gathering as it goes (a 1D flow
   * accumulation, not a fluid sim), then pours down the full-height column just
   * past that corner and continues on to the real floor. The result is a door
   * that concentrates water into two heavy corner pours with a thin shimmering
   * sheet running between them, rather than a dead stop on the lintel.
   *
   * Runs strictly before the main column loop, into the caller's spill grid;
   * skipped entirely at spill=0 (see the call site), leaving that grid zeroed.
   */
  private void renderDoorSpill(Apotheneum.Orientation orientation, int shape, float[] cos, float[] sin,
      float[] spillGrid, int height, int columnCount, float radius, float flow, float stretch,
      float breakup, float thresholdFloor, float turbulence, float warpAngle, float warpTime,
      float noiseTime, float ledge, float ledgeRows, int baseLipRow, float variationX, float variationY,
      float spillAmount) {

    for (int x = 0; x < columnCount; ++x) {
      if (orientation.available(x) >= height) {
        continue;
      }
      // Ring wrap: column 0 and columnCount-1 are physically adjacent, so a run's
      // start is wherever a door column follows a non-door column going backward.
      final int prev = Math.floorMod(x - 1, columnCount);
      if (orientation.available(prev) < height) {
        continue; // x is mid-run, not its start
      }

      // Walk the run forward from its start, with wrap, into the scratch arrays.
      int n = 0;
      int scan = x;
      while ((n < this.spillRunColumns.length) && (orientation.available(scan) < height)) {
        this.spillRunColumns[n] = scan;
        ++n;
        scan = Math.floorMod(scan + 1, columnCount);
        if (scan == x) {
          break; // the whole ring is doors; not a geometry this installation has
        }
      }

      // How much water actually arrives at each column's lintel, straight from the
      // same curtain formula the main pass uses — not a guess at it.
      for (int i = 0; i < n; ++i) {
        final int col = this.spillRunColumns[i];
        final int lintel = orientation.available(col) - 1;
        this.spillRunLintel[i] = lintel;

        final float cx = cos[col] * radius;
        final float cz = sin[col] * radius;
        final float sheet = lipMask(col, columnCount, 1f);

        final float notch = LXUtils.clampf(.5f + LEDGE_GAIN * Noise.stb_perlin_noise3(
          cx * LEDGE_SCALE + variationX, LEDGE_SEED + variationY, cz * LEDGE_SCALE, 0, 0, 0), 0, 1);
        final int lipRow = Math.min(height - 2, baseLipRow + Math.round(ledgeRows * (1f - notch)));
        final float chute = CHUTE * ledge * (2f * notch - 1f);

        final float speed = LXUtils.clampf(.5f + SPEED_GAIN * Noise.stb_perlin_noise3(
          cx * SPEED_SCALE, SPEED_SEED, cz * SPEED_SCALE, 0, 0, 0), 0, 1);
        final float columnStretch = stretch * (1f + SPEED_SPREAD * (2f * speed - 1f));

        if (lipRow >= lintel) {
          // The lip already sits at or below the lintel: no fall reaches it to spill.
          this.spillRunArriving[i] = 0f;
          continue;
        }

        final int floorRow = floorRow(orientation, shape, col, height);
        final int span = floorRow - lipRow;

        // Mass at the lip: is a fall aimed here at all (sheet), weighted by how
        // much this notch concentrates flow (the same notch field the ledge and
        // chute already use — a deep notch pours harder). Floored so a shallow
        // stretch of rock still carries a sane fraction rather than trailing to
        // zero, since it's still catching whatever the sheet delivers.
        final float massWeight = SPILL_MASS_FLOOR + (1f - SPILL_MASS_FLOOR) * notch;
        final float massArriving = sheet * massWeight;

        // A minority instantaneous term, averaged over a short window so a single
        // noise gap doesn't zero it out, keeps the pour breathing and shimmering
        // in sympathy with the strands above instead of reading as a static bar.
        float instantSum = 0f;
        int instantRows = 0;
        for (int r = 0; r < SPILL_ARRIVAL_ROWS; ++r) {
          final int row = lintel - r;
          if (row < lipRow) {
            break;
          }
          instantSum += curtainAt(col, columnCount, cx, cz, sheet, lipRow, span, columnStretch,
            chute, flow, breakup, thresholdFloor, turbulence, warpAngle, warpTime, noiseTime, row);
          ++instantRows;
        }
        final float instantArriving = (instantRows > 0) ? (instantSum / instantRows) : 0f;

        this.spillRunArriving[i] = SPILL_MASS_WEIGHT * massArriving + (1f - SPILL_MASS_WEIGHT) * instantArriving;
      }

      // Split at the midpoint: each half flows toward its own nearer corner,
      // gathering what it passes rather than just averaging what lands on it.
      final int mid = n / 2;
      for (int i = mid - 1; i >= 0; --i) {
        final float upstream = (i + 1 <= mid - 1) ? this.spillRunFlow[i + 1] : 0f;
        this.spillRunFlow[i] = this.spillRunArriving[i] + upstream * SPILL_RETENTION;
      }
      for (int i = mid; i < n; ++i) {
        final float upstream = (i - 1 >= mid) ? this.spillRunFlow[i - 1] : 0f;
        this.spillRunFlow[i] = this.spillRunArriving[i] + upstream * SPILL_RETENTION;
      }
      // Each half is its own geometric series of length m columns (1+R+...+R^(m-1)),
      // so it is normalized against its own far-end maximum — spillFlowScale[m] —
      // rather than the run's total length. A fully-fed half then reaches exactly 1
      // at the corner regardless of how many columns feed it, which is the whole
      // point: the corner should hit full strength, not top out near one column's
      // worth of water the way a flat (1-R) scale would.
      final int leftLength = mid;
      final int rightLength = n - mid;
      final float leftScale = (leftLength > 0) ? this.spillFlowScale[leftLength] : 0f;
      final float rightScale = (rightLength > 0) ? this.spillFlowScale[rightLength] : 0f;
      for (int i = 0; i < mid; ++i) {
        this.spillRunFlow[i] *= leftScale;
      }
      for (int i = mid; i < n; ++i) {
        this.spillRunFlow[i] *= rightScale;
      }

      // The lintel sheet itself: a thin, shimmering film sitting on top of the
      // ledge. One noise sample per column, not per pixel, or it reads as static.
      for (int i = 0; i < n; ++i) {
        final float level = spillAmount * SPILL_LEVEL * this.spillRunFlow[i];
        if (level <= 0) {
          continue;
        }
        final int col = this.spillRunColumns[i];
        final int lintel = this.spillRunLintel[i];
        final float cx = cos[col] * radius;
        final float cz = sin[col] * radius;
        final float shimmer = .5f + .5f * Noise.stb_perlin_noise3(
          cx * SPILL_SHIMMER_SCALE, noiseTime * SPILL_SHIMMER_RATE, cz * SPILL_SHIMMER_SCALE, 0, 0, 0);
        for (int r = 0; r < SPILL_ROWS; ++r) {
          accumulate(spillGrid, columnCount, height, col, lintel - r, level * this.spillDecay[r] * shimmer);
        }
      }

      // The corner pours: what leaves each end of the run continues down the
      // full-height column beside it, to the real floor. spillRunFlow is already
      // normalized to reach 1 at a fully-fed corner, so at spill=1 a fully-fed run
      // pours at full strength (SPILL_POUR_LEVEL) rather than a further-discounted
      // fraction of it. Those columns already carry their own curtain from the main
      // pass — the spill adds to it, which is intended: the corners read heavier.
      pourCorner(orientation, shape, spillGrid, height, columnCount,
        Math.floorMod(this.spillRunColumns[0] - 1, columnCount),
        this.spillRunLintel[0], spillAmount * this.spillRunFlow[0] * SPILL_POUR_LEVEL);
      pourCorner(orientation, shape, spillGrid, height, columnCount,
        Math.floorMod(this.spillRunColumns[n - 1] + 1, columnCount),
        this.spillRunLintel[n - 1], spillAmount * this.spillRunFlow[n - 1] * SPILL_POUR_LEVEL);
    }
  }

  /**
   * The pour down a full-height column just past a door's corner: bright for the
   * first few rows below the lintel, easing to a lower sustained trickle for the
   * rest of the drop to the real floor, so it reads as falling water rather than
   * a flat stripe.
   */
  private void pourCorner(Apotheneum.Orientation orientation, int shape, float[] spillGrid,
      int height, int columnCount, int column, int lintel, float amount) {
    if (amount <= 0) {
      return;
    }
    final int floorRow = floorRow(orientation, shape, column, height);
    for (int y = lintel; y <= floorRow; ++y) {
      final int depth = Math.min(this.spillPourDecay.length - 1, y - lintel);
      accumulate(spillGrid, columnCount, height, column, y, amount * this.spillPourDecay[depth]);
    }
  }

  /**
   * Coverage of the falls at a column, in [0,1]. {@code reach} is the fraction of each
   * fall's arc carried at full strength, with the soft rim outside it rather than eaten
   * out of it — so a width of one wraps the ring with no seam, which is the whole point
   * of the setting. {@code widen} scales the arc for the impact and mist, which spread
   * beyond the water feeding them.
   */
  private float lipMask(int columnIndex, int columnCount, float widen) {
    final int falls = this.falls.getValuei();
    final float reach = this.width.getValuef() * widen;
    final float u = (columnIndex / (float) columnCount - this.rotate.getValuef()) * falls;
    final float phase = u - (float) Math.floor(u);
    final float offset = Math.abs(phase - .5f) * 2f;
    final float soft = EDGE_MIN + EDGE_FRACTION * reach;
    return LXUtils.clampf((reach + soft - offset) / soft, 0, 1);
  }

  /** Row the water lands on, clamped into the usable height of a door column. */
  private int floorRow(Apotheneum.Orientation orientation, int shape, int columnIndex, int height) {
    double position = this.floor.getValue();
    if ((this.surfacing != null) && (shape == CYLINDER)) {
      position = this.surfacing.getCylinderLevel(columnIndex);
    }
    final int row = (int) Math.round(LXUtils.lerp(height - 1, 0, position));
    return (int) LXUtils.constrain(row, 0, orientation.available(columnIndex) - 1);
  }

  private void updateSpray(double deltaMs) {
    final float amount = this.spray.getValuef();
    final float elapsed = (float) (deltaMs * .001);

    if (amount > 0) {
      this.sprayAccumulator += amount * SPRAY_SPAWN_RATE * elapsed;
      while (this.sprayAccumulator >= 1) {
        this.sprayAccumulator -= 1;
        spawnSpray();
      }
    } else {
      this.sprayAccumulator = 0;
    }

    for (int i = 0; i < SPRAY_MAX; ++i) {
      if (!this.sprayAlive[i]) {
        continue;
      }
      this.sprayRise[i] += this.sprayVelocityRise[i] * elapsed;
      this.sprayVelocityRise[i] -= SPRAY_GRAVITY * elapsed;
      this.sprayX[i] += this.sprayVelocityX[i] * elapsed;
      if (this.sprayRise[i] <= 0) {
        this.sprayAlive[i] = false;
        continue;
      }
      renderSprayParticle(i);
    }
  }

  private void spawnSpray() {
    final boolean cylinder = Math.random() < .5;
    final int shape = cylinder ? CYLINDER : CUBE;
    final int columnCount = cylinder ? CYLINDER_WIDTH : CUBE_WIDTH;

    // Rejection sample so spray only leaves the ground where water is landing.
    int column = -1;
    for (int i = 0; i < SPRAY_SPAWN_TRIES; ++i) {
      final int candidate = LXUtils.randomi(0, columnCount - 1);
      if (lipMask(candidate, columnCount, IMPACT_SPREAD) > Math.random()) {
        column = candidate;
        break;
      }
    }
    if (column < 0) {
      return;
    }

    final int index = this.sprayNext;
    this.sprayNext = (this.sprayNext + 1) % SPRAY_MAX;
    this.sprayShape[index] = shape;
    this.sprayX[index] = column;
    this.sprayRise[index] = .1f;
    this.sprayVelocityRise[index] = (float) LXUtils.random(SPRAY_VELOCITY_MIN, SPRAY_VELOCITY_MAX);
    this.sprayVelocityX[index] = (float) LXUtils.random(-SPRAY_DRIFT, SPRAY_DRIFT);
    this.sprayAlive[index] = true;
  }

  private void renderSprayParticle(int i) {
    final boolean cylinder = this.sprayShape[i] == CYLINDER;
    final Apotheneum.Orientation orientation = cylinder ? Apotheneum.cylinder.exterior : Apotheneum.cube.exterior;
    final int height = cylinder ? Apotheneum.CYLINDER_HEIGHT : Apotheneum.GRID_HEIGHT;
    final int columnCount = cylinder ? CYLINDER_WIDTH : CUBE_WIDTH;
    final float[] grid = cylinder ? this.cylinderSpray : this.cubeSpray;

    int column = (int) Math.floor(this.sprayX[i]) % columnCount;
    if (column < 0) {
      column += columnCount;
    }
    this.sprayX[i] = column + (this.sprayX[i] - (float) Math.floor(this.sprayX[i]));

    final int floorRow = floorRow(orientation, cylinder ? CYLINDER : CUBE, column, height);
    final float row = floorRow - this.sprayRise[i];

    // Fades out at the apex, where a real droplet has thinned to nothing.
    final float fade = LXUtils.clampf(1f - this.sprayRise[i] / 14f, .3f, 1f);

    final int low = (int) Math.floor(row);
    final float frac = row - low;
    accumulate(grid, columnCount, height, column, low, fade * (1f - frac));
    accumulate(grid, columnCount, height, column, low + 1, fade * frac);
  }

  private void accumulate(float[] grid, int columnCount, int height, int column, int row, float value) {
    if ((row < 0) || (row >= height) || (value <= 0)) {
      return;
    }
    final int index = column * height + row;
    grid[index] = Math.min(1f, grid[index] + value);
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Flow",
      newKnob(this.flow),
      newKnob(this.stretch),
      newKnob(this.turbulence)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Sheet",
      newKnob(this.scale),
      newKnob(this.breakup),
      newKnob(this.shatter)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Lip",
      newKnob(this.lip),
      newKnob(this.ledge),
      newKnob(this.variation)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Falls",
      newIntegerBox(this.falls),
      newKnob(this.width),
      newKnob(this.rotate)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Ground",
      newKnob(this.floor),
      newButton(this.linkFloor),
      newKnob(this.churn)
    ).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Impact",
      newKnob(this.spray),
      newKnob(this.mist),
      newKnob(this.spill)
    ).setChildSpacing(6);

    // Colour columns are built by the base class, not here (ColorNativePattern owns that UI),
    // and are appended last so they land at the end of the panel, contiguous with each other.
    buildColorDeviceControls(ui, uiDevice);
  }

}
