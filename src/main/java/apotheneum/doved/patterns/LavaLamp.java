/**
 * Copyright 2026- Dan Oved
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
 *
 * @author Dan Oved
 */

package apotheneum.doved.patterns;

import java.util.Arrays;
import java.util.Random;

import apotheneum.Apotheneum;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;

/**
 * Lava lamp blobs, coloured natively as a temperature gradient rather than rendered in
 * white for a downstream Colorize effect to tint.
 *
 * Three well-known pieces are doing the work here.
 *
 * Shape: Blinn's blobby model (1982), better known as metaballs. Every blob
 * contributes a radially decreasing scalar field, the fields sum, and the blob
 * surface is the iso-contour of that sum. Two blobs that drift together grow a
 * neck and become one shape with no special-case code, which is exactly the
 * behavior a lava lamp is famous for. The falloff is Wyvill's polynomial
 * (1 - d^2/R^2)^3 rather than Blinn's original exponential, because it reaches
 * zero at a finite radius and so costs a small bounded box per blob instead of a
 * pass over the whole surface.
 *
 * Motion: the thermal relaxation oscillator that a real lamp runs on, built as a
 * Schmitt trigger rather than a restoring force. The heater is a shallow zone at
 * the floor, so ambient temperature reads as a switch — hot on the pad, cold
 * everywhere above it — and each blob carries a temperature that lags it. What
 * makes a trip a trip is the latch: a blob commits to rising or sinking and holds
 * that direction until its temperature crosses the *far* side of a hysteresis
 * band around its neutral point, so it must reach the pad to reheat or leave the
 * heat entirely to cool. Buoyancy is tanh of the distance to that threshold, so a
 * freshly latched blob travels at essentially terminal speed and only eases off
 * as it nears the turnover. Both halves are needed. Without saturation the drive
 * is linear and the lamp is a damped oscillator that settles at one height;
 * without the latch the blob reverses the moment it passes neutral and hovers
 * just above the heater.
 *
 * Continuity: the simulation's bookkeeping — two bodies becoming one, one necking
 * into two, everything trimming back when Volume drops — is discrete, and the eye
 * reads a discrete radius change as a pop. Two things keep it off the wall. Bodies
 * fuse only once their centres nearly coincide, far later than the field fuses
 * them, so the change is small and happens inside a shape that already looks like
 * one blob. And every blob carries a rendered radius that chases its simulated one
 * on a fraction-of-a-second time constant; the field draws that, the physics uses
 * the true value, and so a merge, a split, a volume trim or a fresh blob welling up
 * all arrive as a swell rather than a step.
 *
 * Containment: the lamp's vessel is the real wall, doorways included. A door column is
 * logically shorter, not physically shorter — {@link Apotheneum.Orientation#available} says
 * how much of it exists, and what is missing is the bottom of it — so in the simulation's
 * coordinates a doorway is a solid block reaching from the floor up to its lintel, and the
 * lamp treats it as one. Blobs collide with those blocks the way they collide with the floor:
 * the resolution is the shorter of lifting the blob onto the lintel and pushing it clear of
 * the jamb, and off the end of a lintel it becomes a push out of the corner, so a blob that
 * has drifted to the edge slides off rather than perching on nothing. Restitution is low and
 * anisotropic, because lava is viscous rather than springy — a blob landing on a lintel loses
 * its descent entirely and rests there, while one meeting a jamb keeps a fifth of its
 * sideways speed and nudges away. Blobs well up from the floor beneath the column they pick,
 * which over a doorway is that doorway's lintel, and the warm-start fill measures its spread
 * from the same local floor, so nothing is ever placed inside an opening. The discrete
 * bookkeeping is held to the same rule from the other side: a fuse or a neck that would put a
 * body in a wall is declined rather than performed and corrected, since the correction would
 * be a whole radius of travel in one frame and would read as a jump — the same answer the
 * oversize guard already gives to a fuse it cannot honour. The radius all of this collides
 * with is the one the field draws — {@link #drawnRadius}, solved from the iso level rather
 * than assumed, since Threshold moves a blob's contour between 1.42 and .54 nominal radii —
 * and not the squashed one the floor uses, which is what leaves a blob tangent to a doorway
 * rather than hanging some fraction of a radius inside it. The profile comes from
 * {@code available()} on the first render and is rebuilt if the model changes; the door
 * positions are never written down here. What all of it buys is the difference between lava
 * that a doorway takes a bite out of and lava that flows around one.
 *
 * Blobs are deliberately not identical: each draws its own neutral point and its
 * own thermal rate when it spawns. Merging area-weights both, which is a phase
 * lock, so without that spread the population synchronizes and the whole lamp
 * rises and falls as one body. Thermal mass also goes as the square root of
 * scale, so big blobs make slow, tall trips and small ones fidget.
 *
 * Colour: the temperature that drives the whole rise-and-fall is what the pattern paints
 * with, so the lamp is hot where it is climbing and cool where it is sinking. The two
 * {@link ColorNativePattern} roles are the ends of that ramp — {@link #hotColor}
 * ({@code primary}) and {@link #coolColor} ({@code secondary}); the user-facing controls stay
 * "Primary"/"Secondary" and the addresses stay {@code .../primary/...}. A temperature field
 * accumulates alongside the density field, each blob contributing its temperature weighted by
 * the same Wyvill falloff, so every cell carries the falloff-weighted mean temperature of the
 * blobs reaching it. That is what makes the neck between a hot rising lobe and a cold sinking
 * one a real gradient rather than a flat fill: the field fuses the two shapes and the
 * temperature field fuses their heat, over the same reach.
 *
 * The ramp is normalized against a fixed 0.18 to 0.72, not the nominal 0..1 a blob temperature
 * lives in. Histogramming every lit cell of a five-second run at Speed 3 (1.8M samples, all
 * other controls at their defaults) puts the median at 0.42, the middle half between 0.27 and
 * 0.59, and the 5th and 95th percentiles at 0.17 and 0.73; only one lit cell in a hundred is
 * below 0.14 or above 0.79. A blob never approaches the ends of its nominal range, because
 * ambient only reaches 1 on the heater pad itself, the thermal lag keeps it from settling
 * anywhere, and the Schmitt trigger turns it around at the hysteresis band well before it
 * saturates. Normalizing against 0..1 would spend two fifths of the ramp on temperatures no
 * cell ever holds and squeeze the whole visible gradient into its middle. These bounds are
 * fixed rather than auto-ranged per frame, which would pump the whole lamp's hue every time
 * one blob touched the pad.
 *
 * Shading: what each role's physics perturbation is driven by is not the field depth but a lit
 * term, so a blob reads as a sphere rather than a disc. The metaball field is an implicit
 * surface, so a surface normal falls straight out of its gradient — a central difference on the
 * field {@link Lamp#renderField} already wrote, negated because the field decreases outward, with
 * a constant out-of-wall component setting how domed the result is. That normal is lit by a
 * Lambertian term plus a Blinn specular one, and Shade blends the result against the old field
 * depth: at zero the lamp is bit-identically the flat look it had before, so the two are a clean
 * A/B. Light is where the source sits, 0 directly below the blob — a real lamp's bulb is under
 * the wax — running counter-clockwise across the unwrapped wall; Gloss is the highlight, which
 * at LED resolution carries the wet, waxy read that a subtle diffuse falloff cannot.
 *
 * Taking the normal from the summed field rather than from each blob's own radius is the point
 * of doing it this way. A merged pair is one iso-surface, and its gradient describes the shape
 * that is actually on the wall: where two lobes neck together the surface genuinely pinches, so
 * the shading puts a shadow in the waist and the pair reads as one peanut. Per-blob radial
 * gradients would light two separate spheres inside a silhouette that is visibly not two spheres.
 *
 * How far the relief actually travels is each role's Amount, the same coupling the field depth
 * it replaces went through — a role at Amount 0 takes no perturbation at all, so Shade does
 * nothing to it. And since a palette stop on this rig sits at full brightness, there is no
 * headroom to brighten: the lit half of a blob reads mostly as desaturation toward white, which
 * the highlight rides on, while the shadowed half is where the darkening happens.
 *
 * Shading is resolved once per lamp, in a pass over the cells the field reached, rather than once
 * per surface — the cube and the cylinder each drive an exterior and an interior from a single
 * field, so this is half the work the per-surface field depth it replaces was doing. The halo the
 * Glow knob lights is deliberately dimmed lava of the temperature of the blob casting it, not a
 * separate cool fringe: a cool halo would paint a blue rim around every hot blob and contradict
 * the ramp. Shading never darkens a cell to black, so the iso-surface and the lit footprint are
 * exactly what they were; only the colour inside them changes.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Lava Lamp")
@LXComponent.Description("Metaball blobs rising and falling on a thermal convection cycle")
public class LavaLamp extends ColorNativePattern {

  private static final double MIN_SIZE = 2.5;
  private static final double MAX_SIZE = 12;
  private static final double MAX_VOLUME = .3;

  private static final double MAX_DELTA_SECONDS = .05;

  // Wyvill falloff reaches zero at this multiple of a blob's nominal radius.
  private static final double INFLUENCE_RADIUS = 2;

  // Blob radii are carried as a multiple of the Size parameter, so Size scales
  // the whole lamp while merges and splits stay relative.
  private static final double MIN_SCALE = .45;
  /** Package-private so {@code LavaLampFieldSpanTest} can assert the bound it tests. */
  static final double MAX_SCALE = 2.5;
  private static final double SPLIT_SCALE = 1.5;
  private static final double SPLIT_RATE = 1.5;
  private static final double SPLIT_KICK = .6;
  private static final double SPAWN_SCALE_MIN = .7;
  private static final double SPAWN_SCALE_RANGE = .6;

  // BUOYANCY / VERTICAL_DRAG is the terminal climb rate in rows per second, so a
  // blob of nominal size crosses the 45-row cube panel in a little under six.
  private static final double BUOYANCY = 18;
  private static final double VERTICAL_DRAG = 2.25;
  // Buoyancy is tanh of the temperature offset rather than the offset itself, so
  // a blob is decisively hot or decisively cold instead of proportionally so.
  private static final double BUOYANCY_SHARPNESS = 6;
  // A blob commits to a direction and holds it until its temperature crosses the
  // far threshold, half a band away. Without that latch it turns around the
  // moment it passes its neutral point, which puts it back where it started: a
  // slow hover just above the heater. The latch is what makes the trip a trip.
  private static final double THERMAL_HYSTERESIS = .2;
  // Each blob's own neutral point, drawn at spawn. Without the spread they share
  // one turnover altitude and the lamp bands into a horizontal line.
  private static final double NEUTRAL_MIN = .38;
  private static final double NEUTRAL_RANGE = .24;
  // Thermal time constant, a little over three seconds at nominal size: long
  // enough that a blob stays hot through the whole climb, short enough that it
  // does not idle on the pad. Thermal mass goes as the square root of scale, so
  // big blobs make slow, tall trips and small ones fidget.
  private static final double THERMAL_RATE = .3;
  // Per-blob multiplier on that rate, drawn at spawn alongside the neutral point.
  // Two blobs of the same size otherwise run at the same period, and merging —
  // which averages both — is a phase lock, so without a spread the population
  // decoheres far too slowly and the lamp rises and falls as one body.
  private static final double THERMAL_MIN = .7;
  private static final double THERMAL_RANGE = .7;
  private static final double HEAT_DEPTH_MIN = .1;
  private static final double HEAT_DEPTH_MAX = .34;
  private static final double WALL_SQUASH = .55;
  // Restitution on a lateral wall contact. Lava is viscous, so this is a nudge away from
  // the jamb rather than a bounce; a vertical contact gets none at all and simply rests.
  private static final double WALL_RESTITUTION = .2;
  // Slack on the containment test, in rows. A blob left resting against a wall sits exactly
  // on the boundary, and rounding decides whether "exactly" reads as a hair inside it.
  private static final double CONTACT_EPSILON = 1e-6;

  private static final double WANDER_ACCEL = 24;
  private static final double LATERAL_DRAG = 1.4;

  // Bodies fuse only once their centres are close to coincident — roughly half a
  // lobe radius apart at the middle of the Coalesce range, since the reach is
  // (a.scale + b.scale) * baseRadius * mergeFactor. Merging at the distance where
  // the summed field first draws a neck instead loses the dumbbell's length and
  // gains its width in a single frame; by the time these thresholds trip, the
  // silhouette is already one round shape and the fuse happens inside it.
  private static final double MERGE_MIN = .12;
  private static final double MERGE_RANGE = .26;
  private static final double SPLIT_TENDENCY_BASE = 1.4;
  private static final double SHRINK_RATE = .5;
  private static final double AREA_TOLERANCE = .02;
  // Volume is topped up at a bounded rate once the lamp is running, so a Volume move or a
  // cascade of removals wells up over a fraction of a second instead of arriving whole. The
  // budget accrues with simulated time rather than with frames, which matters twice: the fill
  // takes the same wall-clock time at 30fps and at 120fps instead of running four times faster
  // on the faster host, and it neither accrues nor is spent at Speed 0, so a lamp held under its
  // target volume genuinely holds instead of welling up a blob every frame. Sixty a second is
  // the rate the old per-frame cap gave at the frame rate it was tuned at.
  //
  // The very first fill ignores the budget: startup stays instant.
  private static final double SPAWNS_PER_SECOND = 60;
  // Credit carried into a frame, so a lamp that sat at its target volume for a minute does not
  // arrive at a Volume raise with a minute of unspent budget and fill in one frame. Within a
  // frame the accrual is spent in full, so a long frame still spawns its share and the rate
  // stays frame-rate independent.
  private static final double SPAWN_BUDGET_CARRY = 1;

  // Time constant for the rendered radius chasing the simulated one. Merges,
  // splits and volume trims all change a blob's scale discontinuously; the physics
  // takes the new value immediately and the field draws the old one decaying into
  // it, which is the difference between a join that pops and one that swells.
  private static final double RENDER_RELAX_SECONDS = .3;
  // Where the fused blob's rendered radius starts, between the larger of the two
  // lobes (0) and the area-conserving fusion of them (1). Tuned so the lit area
  // neither drops as the second lobe stops being drawn nor jumps as the first one
  // takes over its area.
  private static final double MERGE_RENDER_BLEND = .65;
  // A blob welling up from the pool starts this fraction of its rendered size, so
  // it grows in rather than appearing.
  private static final double SPAWN_RENDER_FRACTION = .25;

  // Sized for the smallest blobs at the largest volume, with headroom for the
  // extra blobs that splitting introduces.
  private static final double CAPACITY_HEADROOM = 1.5;
  private static final int CAPACITY_MARGIN = 16;

  // The two ends of the colour ramp, in blob-temperature units: the measured 5th and 95th
  // percentiles of lit-cell temperature, not the nominal 0..1 a blob temperature lives in,
  // which no blob comes close to spanning. See the class javadoc for the measurement and why
  // this is a fixed range rather than a per-frame auto-range.
  private static final double TEMPERATURE_COOL = .18;
  private static final double TEMPERATURE_HOT = .72;

  // Out-of-wall component of the un-normalized surface normal, in the same units the scaled
  // gradient is measured in — how domed rather than flat a blob reads. It is a constant, not a
  // knob, because it is fixed by the falloff rather than chosen: with the gradient scaled by the
  // nominal influence radius, a lone blob's iso-surface at the middle of the Threshold range
  // sits where the scaled gradient magnitude is about 1.7, so a height of 1 tilts the visible
  // rim about sixty degrees away from the viewer and the centre faces straight out — a
  // hemisphere's read, near enough. Doming is not a separate aesthetic axis from Shade, which
  // already sets how much of the resulting relief reaches the colour.
  private static final double DOME_HEIGHT = 1;
  // The light sits thirty degrees out of the wall rather than in its plane. In the plane, half
  // of every blob would be past the terminator and the lamp would read as a field of crescents;
  // lifting the source toward the viewer keeps the whole face lit and puts the terminator up in
  // the shadowed quarter, which is where a real lamp's bulb-under-the-wax puts it.
  private static final double LIGHT_OUT_OF_WALL = .5;
  private static final double LIGHT_IN_PLANE = Math.sqrt(1 - LIGHT_OUT_OF_WALL * LIGHT_OUT_OF_WALL);

  /**
   * System property pinning the blob random seed. Set by the review render so successive
   * runs are comparable, and by {@code LavaLampDoorTest} so a containment failure is a
   * failure someone else can reproduce rather than one seed's bad luck.
   */
  static final String RENDER_SEED_PROPERTY = "apotheneum.lavalamp.seed";

  public final CompoundParameter size =
    new CompoundParameter("Size", 5, MIN_SIZE, MAX_SIZE)
    .setDescription("Nominal blob radius in pixels");

  public final CompoundParameter volume =
    new CompoundParameter("Volume", .16, .02, MAX_VOLUME)
    .setDescription("How much lava the lamp holds; the halo lights well beyond it");

  public final CompoundParameter coalesce =
    new CompoundParameter("Coalesce", .5)
    .setDescription("How readily blobs merge together rather than break apart");

  /**
   * Rate of the convection cycle, bottoming out at a genuine stop rather than at a crawl.
   *
   * Zero is a working setting, not a degenerate one: it makes {@code dt} exactly zero, and
   * every consumer of {@code dt} is either a multiplication by it (the integration, the split
   * probability, the shrink, the volume replenishment budget) or an exponential/square root of
   * it (the render relaxation, the wander kick), so all of them go to no-op and none divides by
   * it. That includes the hold entered while the lamp is under its target volume — the
   * replenishment is on a time budget that neither accrues nor is spent at a zero step, so a
   * lamp asked to fill and then held does not go on filling, and neither does one that reaches
   * the hold with budget already banked; see {@link Lamp#reconcileVolume}. The containment pass
   * takes no {@code dt} at all and so still pushes a penetrating body out — it corrects, it
   * just does not drift. The lamp therefore holds its exact frame, which is what a performer
   * wants out of the bottom of this knob.
   *
   * The exponent survives the zero minimum: {@code value = 4 * normalized^2}, which is
   * monotonic, reaches exactly zero at the bottom of the knob, and puts the default of 1 at
   * the centre of its travel.
   */
  public final CompoundParameter speed =
    new CompoundParameter("Speed", 1, 0, 4)
    .setExponent(2)
    .setDescription("Rate of the convection cycle; zero holds the lamp still");

  public final CompoundParameter heat =
    new CompoundParameter("Heat", .5)
    .setDescription("Depth of the heated zone; higher drives blobs further up");

  public final CompoundParameter wander =
    new CompoundParameter("Wander", .5)
    .setDescription("Sideways drift of the blobs");

  public final CompoundParameter threshold =
    new CompoundParameter("Threshold", .42, .12, .8)
    .setDescription("Metaball iso-level; lower fattens blobs and merges them sooner");

  public final CompoundParameter edge =
    new CompoundParameter("Edge", .45)
    .setDescription("Softness of the blob edge");

  public final CompoundParameter glow =
    new CompoundParameter("Glow", .1, 0, .6)
    .setDescription("Brightness of the halo outside the blob surface");

  public final CompoundParameter shade =
    new CompoundParameter("Shade", .6)
    .setDescription("Strength of the 3D shading; at zero the blobs read flat");

  public final CompoundParameter light =
    new CompoundParameter("Light", 0, 360)
    .setWrappable(true)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Where the light comes from: 0 is below, running counter-clockwise");

  public final CompoundParameter gloss =
    new CompoundParameter("Gloss", .4)
    .setDescription("Specular highlight on the wax");

  // Where the lamp is drawn. Two independent axes — which chamber, and which side of it.
  //
  // These do not replace the model view, which this pattern also honours (see
  // updateViewMask): a view can express selections four booleans cannot, such as a single
  // face or a band of rings. They earn their place by doing what a view cannot, which is
  // move during a show — a BooleanParameter is MIDI-mappable, modulatable and automatable in
  // a clip, so a chamber can be brought in and out as part of a cue, while a view selector is
  // a static choice. The two compose: a point is painted only if its chamber and its side are
  // both on and it is in the view. All four default on, so an untouched lamp fills the
  // installation exactly as before.

  public final BooleanParameter renderToCube =
    new BooleanParameter("Cube", true)
    .setDescription("Whether the lava is drawn on the cube");

  public final BooleanParameter renderToCylinder =
    new BooleanParameter("Cylinder", true)
    .setDescription("Whether the lava is drawn on the cylinder");

  public final BooleanParameter renderToExterior =
    new BooleanParameter("Exterior", true)
    .setDescription("Whether the lava is drawn on the outward-facing surfaces");

  public final BooleanParameter renderToInterior =
    new BooleanParameter("Interior", true)
    .setDescription("Whether the lava is drawn on the inward-facing surfaces");

  private static final class Blob {
    private double x;
    private double h;
    private double vx;
    private double vh;
    private double scale;
    /** What the field draws, relaxing toward {@link #scale}. Never drives physics. */
    private double renderScale;
    private double temp;
    private double neutral;
    private double thermal;
    private boolean rising;
  }

  private final class Lamp {

    private final int width;
    private final int height;
    private final Blob[] blobs;
    private final double[] field;
    /** Falloff-weighted sum of blob temperature per cell; divide by {@link #temperatureWeight}. */
    private final double[] temperature;
    private final double[] temperatureWeight;
    /** Per-cell physics scalar for {@link ColorRole#color(double)}; see {@link #shadeField}. */
    private final double[] physics;
    private int count;
    private boolean primed;
    /** Blobs {@link #reconcileVolume} may well up right now; see {@link #SPAWNS_PER_SECOND}. */
    private double spawnBudget;

    /** Rows missing from the bottom of each column, from {@link Apotheneum.Orientation#available}. */
    private final int[] floorHeight;
    /** Door openings as obstacle boxes: centre column, half-width, and lintel height. */
    private final double[] doorCenter;
    private final double[] doorHalfSpan;
    private final double[] doorTop;
    private int doorCount;
    /** The shortest column loss anywhere on the surface — the lamp's true floor. */
    private double baseFloor;
    /** Unit separation direction of the last {@link #probeDoor} hit, written in place. */
    private double normalX;
    private double normalH;

    private Lamp(int width, int height) {
      this.width = width;
      this.height = height;
      this.field = new double[width * height];
      this.temperature = new double[width * height];
      this.temperatureWeight = new double[width * height];
      this.physics = new double[width * height];
      this.floorHeight = new int[width];
      // Door runs alternate with at least one full column, so this bounds them.
      final int doorCapacity = width / 2 + 1;
      this.doorCenter = new double[doorCapacity];
      this.doorHalfSpan = new double[doorCapacity];
      this.doorTop = new double[doorCapacity];
      final int capacity = (int) (
        CAPACITY_HEADROOM * MAX_VOLUME * width * height / (Math.PI * MIN_SIZE * MIN_SIZE)
      ) + CAPACITY_MARGIN;
      this.blobs = new Blob[capacity];
      for (int i = 0; i < capacity; ++i) {
        this.blobs[i] = new Blob();
      }
    }

    /**
     * Resolves the floor profile from the model, once per model rather than per frame.
     *
     * A door column is logically shorter, not physically shorter: it carries a full column
     * of points but only {@code available(x)} of them exist, and the missing ones are the
     * bottom rows. In lamp coordinates, where h counts up from the floor, that is a solid
     * block filling h from 0 up to the lintel — an obstacle, not a mask. Runs of columns
     * that lose the same number of rows become the boxes the blobs collide with, found with
     * wrap so an opening straddling the ring seam is one box rather than two.
     */
    private void initializeGeometry(Apotheneum.Orientation orientation) {
      int lowest = this.height;
      for (int x = 0; x < this.width; ++x) {
        this.floorHeight[x] = this.height - orientation.available(x);
        lowest = Math.min(lowest, this.floorHeight[x]);
      }
      this.baseFloor = lowest;
      this.doorCount = 0;
      // Scan from a full-height column so a run crossing the seam is walked as one span.
      int origin = 0;
      while ((origin < this.width) && (this.floorHeight[origin] > lowest)) {
        ++origin;
      }
      if (origin == this.width) {
        // Every column loses the same rows: that is the floor, not an opening.
        return;
      }
      int offset = 0;
      while (offset < this.width) {
        final int start = (origin + offset) % this.width;
        final double top = this.floorHeight[start];
        if (top <= lowest) {
          ++offset;
          continue;
        }
        int length = 1;
        while (
          (offset + length < this.width) &&
          (this.floorHeight[(origin + offset + length) % this.width] == top)
        ) {
          ++length;
        }
        this.doorCenter[this.doorCount] = wrapColumn(start + (length - 1) * .5);
        this.doorHalfSpan[this.doorCount] = (length - 1) * .5;
        this.doorTop[this.doorCount] = top;
        ++this.doorCount;
        offset += length;
      }
    }

    /**
     * Penetration of a blob's collision disc into one opening, in rows — zero when they are
     * clear of each other. On a positive return {@link #normalX}/{@link #normalH} carry the
     * unit minimum-translation direction that separates them: the shorter of lifting the
     * blob onto the lintel and shoving it clear of the jamb, or, when the blob has drifted
     * off the end of the lintel, the direction out of the corner it is caught on. That last
     * case is what makes a blob slide off a ledge instead of perching on nothing.
     */
    private double probeDoor(double x, double h, double radius, int door) {
      final double top = this.doorTop[door];
      final double dx = wrapDelta(x - this.doorCenter[door]);
      final double side = (dx < 0) ? -1 : 1;
      final double outward = Math.abs(dx) - this.doorHalfSpan[door];
      if (outward <= 0) {
        final double lift = top + radius - h;
        if (lift <= 0) {
          return 0;
        }
        final double push = radius - outward;
        if (lift <= push) {
          this.normalX = 0;
          this.normalH = 1;
          return lift;
        }
        this.normalX = side;
        this.normalH = 0;
        return push;
      }
      if (h <= top) {
        final double push = radius - outward;
        if (push <= 0) {
          return 0;
        }
        this.normalX = side;
        this.normalH = 0;
        return push;
      }
      final double above = h - top;
      final double distanceSquared = outward * outward + above * above;
      if (distanceSquared >= radius * radius) {
        return 0;
      }
      // above > 0 here, so the distance is strictly positive and the normal is well defined.
      final double distance = Math.sqrt(distanceSquared);
      this.normalX = side * outward / distance;
      this.normalH = above / distance;
      return radius - distance;
    }

    /**
     * Whether a blob of this radius placed here would be clear of every opening. The
     * bookkeeping passes use it to decline a rearrangement that would land a body in a wall,
     * before it happens rather than after. It leaves {@link #normalX}/{@link #normalH} in an
     * unspecified state; only {@link #resolveDoors} reads those.
     */
    private boolean clearsDoors(double x, double h, double radius) {
      for (int door = 0; door < this.doorCount; ++door) {
        if (probeDoor(x, h, radius, door) > CONTACT_EPSILON) {
          return false;
        }
      }
      return true;
    }

    /**
     * Pushes a blob out of every opening it has entered and takes the impact out of it.
     *
     * This is the physical half of containment — where the blob is pushed and what the
     * contact does to its velocity, and it settles nearly every contact on its own. The
     * guarantee is not here: {@link #contain} checks afterwards whether the blob really did
     * end up clear, and falls back to {@link #doorSupportHeight} for the ones that did not.
     *
     * Where the geometry gets tight the shortest way out is not always available. A blob
     * needs its whole drawn radius of headroom to sit on a lintel, and at large Size that
     * can be more than the panel has left above it — the ceiling clamp in {@link #contain}
     * would take a lift like that straight back, and a correction the next clamp undoes is
     * no correction. So a lift that would carry the blob past the top of the panel is
     * capped there and the rest of the separation is taken sideways instead, which is both
     * available more often and the better picture: lava flowing around a doorway rather
     * than levitating over it. See {@link #escapeSideways}.
     */
    private void resolveDoors(Blob blob, double radius, double ceiling) {
      for (int door = 0; door < this.doorCount; ++door) {
        final double depth = probeDoor(blob.x, blob.h, radius, door);
        if (depth <= 0) {
          continue;
        }
        double nx = this.normalX;
        double nh = this.normalH;
        double dx = nx * depth;
        double dh = nh * depth;
        if ((dh > 0) && (blob.h + dh > ceiling)) {
          dh = Math.max(0, ceiling - blob.h);
          dx = escapeSideways(blob.x, blob.h + dh, radius, door);
          final double length = Math.sqrt(dx * dx + dh * dh);
          if (length <= 0) {
            // Neither direction is open at this radius. Nothing useful to do here; the
            // floor in contain() is what answers that case.
            continue;
          }
          nx = dx / length;
          nh = dh / length;
        }
        blob.x = wrapColumn(blob.x + dx);
        blob.h += dh;
        final double approach = blob.vx * nx + blob.vh * nh;
        if (approach < 0) {
          // Lava is viscous, so restitution is low and anisotropic: a vertical contact
          // keeps none of the approach and simply rests on the lintel, while a lateral one
          // keeps a little, so the blob nudges away from the jamb rather than sticking to
          // it or pinging off like a ball.
          final double restitution = WALL_RESTITUTION * (1 - Math.abs(nh));
          blob.vx -= (1 + restitution) * approach * nx;
          blob.vh -= (1 + restitution) * approach * nh;
        }
      }
    }

    /**
     * The sideways move, signed, that clears a blob of this radius from one opening once it
     * has risen as high as the panel allows — zero when that height alone already clears it.
     *
     * A blob up near the top of the wall is clear of a doorway as soon as the lintel's
     * corner falls outside its disc, which is a shorter move than shoving it clear of the
     * jamb outright: the height it has already gained pays for most of the separation and
     * only the remainder is taken laterally. That keeps the escape as small as the geometry
     * allows and settles the blob tangent to the corner of the opening rather than a full
     * radius away from its edge.
     */
    private double escapeSideways(double x, double h, double radius, int door) {
      final double above = Math.max(0, h - this.doorTop[door]);
      if (above >= radius) {
        return 0;
      }
      final double needed = Math.sqrt(radius * radius - above * above);
      final double dx = wrapDelta(x - this.doorCenter[door]);
      final double outward = Math.abs(dx) - this.doorHalfSpan[door];
      return ((dx < 0) ? -1 : 1) * Math.max(0, needed - outward);
    }

    /**
     * The lowest a blob of this radius may sit above a given column without reaching into an
     * opening — the base floor where the column overlaps none. Since it clears every opening
     * by construction, it is an exact lower bound on the free space at that column, which is
     * what lets {@link #resolveDoors} use it as an unconditional backstop.
     *
     * The overlap is weighted by circle-against-corner geometry rather than taken as the
     * greatest floor loss anywhere under the blob. A blob squarely over an opening rests a
     * full radius above its lintel; one whose rim merely grazes the last door column barely
     * rises at all, because it is caught on the corner rather than sitting on the ledge.
     * Taking the maximum instead would hoist a blob a whole door-height into the air for
     * touching the edge of one, which reads as hovering.
     */
    private double doorSupportHeight(double x, double doorRadius) {
      double support = this.baseFloor;
      for (int door = 0; door < this.doorCount; ++door) {
        final double outward =
          Math.abs(wrapDelta(x - this.doorCenter[door])) - this.doorHalfSpan[door];
        if (outward >= doorRadius) {
          continue;
        }
        final double lift = this.doorTop[door] + ((outward <= 0)
          ? doorRadius
          : Math.sqrt(doorRadius * doorRadius - outward * outward));
        support = Math.max(support, lift);
      }
      return support;
    }

    /**
     * Puts one blob back inside the vessel: out of the doorways, then within floor and
     * ceiling.
     *
     * The doorways enter as part of the floor rather than as a separate pass whose result the
     * clamps can undo — which is the whole fix. Clearing every opening is exactly
     * {@code h >= doorSupportHeight(x)} and nothing else, that function being the lowest a
     * blob of this radius may sit at this column, so a blob the pushes could not place is put
     * on that bound and the clamps below can no longer take it back off.
     *
     * When the two bounds cannot both hold, the doorway wins and the top of the panel gives.
     * That case is a blob drawn so wide that resting on a lintel would carry it past the top
     * of the wall, which means it is clipped whichever bound wins: clipped at the top it
     * reads as lava filling the chamber, clipped at a doorway it reads as a bite taken out
     * of the blob, and only one of those is a lava lamp. It is also the answer to the
     * genuinely unsatisfiable case — a body wider than the pier between two openings, which
     * satisfies neither jamb and cannot be escaped sideways either. It comes to rest bridging
     * both doorways and lying across their lintels, which is what a real one would do.
     */
    private void contain(Blob blob, double contactRadius, double doorRadius) {
      final double ceiling = this.height - 1 - contactRadius;
      // Doors first, for the contact response and so a lift that would overshoot the top of
      // the panel goes sideways instead of being taken back a line later.
      resolveDoors(blob, doorRadius, ceiling);
      double floor = this.baseFloor + contactRadius;
      // Asked at the height the blob will actually come to rest at, not the one the pushes
      // left it at. The ceiling below can only bring a blob down, and a blob lifted onto a
      // lintel it has no headroom for is clear where the pushes put it and back inside the
      // opening once the ceiling has had its say — which is precisely the defect this is
      // here to close. Testing the settled height sees that coming.
      final double settled = Math.min(blob.h, ceiling);
      if (!clearsDoors(blob.x, settled, doorRadius)) {
        // Still in an opening once it settles, so the pushes could not place this blob and
        // it needs the exact bound rather than another nudge.
        //
        // Gated, and the gate matters. The two measures disagree about how far a blob is
        // from an opening because they measure in different directions: a blob sitting just
        // outside a jamb and below the lintel is a hair's push from clear horizontally and
        // very nearly a whole lintel's climb from clear vertically. Applying the vertical
        // bound to a blob the pushes have already settled would hoist it that whole climb
        // for grazing a doorway it is level with — which is the hovering the support height
        // is careful to avoid elsewhere, and which happens a couple of dozen times in a
        // five-second run at the default Size. So the bound is a backstop for blobs the
        // pushes could not place, not the primary mechanism.
        floor = Math.max(floor, doorSupportHeight(blob.x, doorRadius));
      }
      if (ceiling <= floor) {
        blob.h = floor;
        blob.vh = 0;
      } else if (blob.h < floor) {
        blob.h = floor;
        blob.vh = Math.max(0, blob.vh);
      } else if (blob.h > ceiling) {
        blob.h = ceiling;
        blob.vh = Math.min(0, blob.vh);
      }
    }

    /** Deepest incursion of any blob into an opening, in rows; zero when all are clear. */
    private double doorPenetration(double drawnScale) {
      double deepest = 0;
      for (int i = 0; i < this.count; ++i) {
        final Blob blob = this.blobs[i];
        final double radius = blob.renderScale * baseRadius * drawnScale;
        for (int door = 0; door < this.doorCount; ++door) {
          deepest = Math.max(deepest, probeDoor(blob.x, blob.h, radius, door));
        }
      }
      return deepest;
    }

    /** How many blob centres currently sit inside an opening rather than in the lava. */
    private int centersInsideDoors() {
      int inside = 0;
      for (int i = 0; i < this.count; ++i) {
        final Blob blob = this.blobs[i];
        for (int door = 0; door < this.doorCount; ++door) {
          if (
            (Math.abs(wrapDelta(blob.x - this.doorCenter[door])) <= this.doorHalfSpan[door]) &&
            (blob.h < this.doorTop[door])
          ) {
            ++inside;
            break;
          }
        }
      }
      return inside;
    }

    /**
     * How far the nearest blob is above the doorway bound that is holding it up, in rows —
     * positive infinity when no opening reaches any blob at all.
     *
     * This is the exact complement of {@link #doorPenetration}: clearing every opening is
     * {@code h >= doorSupportHeight(x)} and nothing else, so a blob's signed distance from
     * that bound is negative by the penetration when it is inside one and positive by the
     * clearance when it is out. A small positive value means a blob is resting on a lintel
     * or tucked against a jamb with the containment machinery the only thing holding it
     * there, which is what makes a passing containment run mean something.
     *
     * Deliberately not a count of centres over an opening. An opening is ten columns of a
     * hundred and twenty, and at large Size the lamp holds a handful of very wide bodies,
     * so their centres land over one only occasionally even while their discs are pressed
     * against one almost constantly — a guard built on centres is close to vacuous exactly
     * where the geometry is tightest.
     */
    private double doorClearance(double drawnScale) {
      double nearest = Double.POSITIVE_INFINITY;
      for (int i = 0; i < this.count; ++i) {
        final Blob blob = this.blobs[i];
        final double radius = blob.renderScale * baseRadius * drawnScale;
        final double support = doorSupportHeight(blob.x, radius);
        if (support > this.baseFloor + CONTACT_EPSILON) {
          nearest = Math.min(nearest, blob.h - support);
        }
      }
      return nearest;
    }

    /** The largest body the lamp is holding, as a multiple of the nominal blob radius. */
    private double largestScale() {
      double largest = 0;
      for (int i = 0; i < this.count; ++i) {
        largest = Math.max(largest, this.blobs[i].scale);
      }
      return largest;
    }

    /**
     * The largest body the lamp is <i>drawing</i>, as a multiple of the nominal blob radius.
     * Distinct from {@link #largestScale} because the rendered radius eases toward the
     * simulated one rather than tracking it, and it is this one that sets renderField's reach.
     */
    private double largestRenderScale() {
      double largest = 0;
      for (int i = 0; i < this.count; ++i) {
        largest = Math.max(largest, this.blobs[i].renderScale);
      }
      return largest;
    }

    /**
     * How far the widest column span {@link #renderField} would sweep overruns this surface's
     * circumference, in columns, at the given nominal blob radius. At or below zero every
     * unwrapped column the sweep visits is a distinct wrapped column; above it, {@code
     * floorMod} folds columns onto themselves and a cell takes the same blob's contribution
     * more than once.
     *
     * <p>The radius is a parameter rather than the live {@code baseRadius} because Size is a
     * performance control: a rendered radius that is safe at the Size currently dialled in is
     * one knob turn away from being unsafe, and it is the largest Size that has to be clear.
     * </p>
     */
    private double columnSpanOverrun(double nominalRadius) {
      // The sweep runs ceil(x - reach)..floor(x + reach) and drops both ends when |dx| equals
      // reach exactly, so it covers strictly fewer than 2 * reach distinct unwrapped columns.
      return 2 * largestRenderScale() * nominalRadius * INFLUENCE_RADIUS - this.width;
    }

    /**
     * The largest amount by which any blob's rendered radius currently exceeds its simulated
     * one, in nominal radii. Positive only while the lamp is shrinking, which is the state the
     * fused-radius bound exists for; zero or negative the rest of the time.
     */
    private double largestRenderScaleLag() {
      double lag = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < this.count; ++i) {
        lag = Math.max(lag, this.blobs[i].renderScale - this.blobs[i].scale);
      }
      return lag;
    }

    /**
     * How far resting the largest blob on the tallest lintel would carry it past the top of
     * the panel, in rows — the size of the conflict between the two bounds, negative while
     * every blob the lamp holds could sit on a lintel with room above it.
     *
     * Deliberately a function of the blob sizes and the geometry only, never of where the
     * blobs currently are. Containment is the thing under test and it works by moving blobs
     * away from openings, so a shortfall measured at their live positions would be driven
     * down by the very code it is there to prove ran — a guard that reads near zero once the
     * fix lands and cannot tell that from never having reached the case at all. Asking
     * instead whether the lamp is holding bodies too large to rest on a lintel is immune to
     * that, and paired with {@link #doorClearance} — which says the bodies really are pressed
     * against the openings — it pins the case down from both sides.
     */
    private double lintelHeadroomShortfall(double drawnScale) {
      double tallestDoorTop = Double.NEGATIVE_INFINITY;
      for (int door = 0; door < this.doorCount; ++door) {
        tallestDoorTop = Math.max(tallestDoorTop, this.doorTop[door]);
      }
      double worst = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < this.count; ++i) {
        final Blob blob = this.blobs[i];
        final double contactRadius = blob.scale * baseRadius * WALL_SQUASH;
        final double doorRadius = blob.renderScale * baseRadius * drawnScale;
        worst = Math.max(
          worst,
          tallestDoorTop + doorRadius - (this.height - 1 - contactRadius)
        );
      }
      return worst;
    }

    private void update(double dt) {
      final double heatDepth =
        LXUtils.lerp(HEAT_DEPTH_MIN, HEAT_DEPTH_MAX, heatFraction) * this.height;
      // Wander is a diffusion, so its kick scales with the square root of the
      // step. Scaling it linearly would tie the lateral spread to the frame rate.
      final double wanderKick = WANDER_ACCEL * wanderAmount * Math.sqrt(dt);
      // Exponential of the step, not a fixed fraction of it, so the ramp takes the
      // same time whatever the frame rate.
      final double relax = 1 - Math.exp(-dt / RENDER_RELAX_SECONDS);
      for (int i = 0; i < this.count; ++i) {
        final Blob blob = this.blobs[i];
        final double radius = blob.scale * baseRadius;
        // The radius the blob collides with. It squashes against a surface rather than
        // resting a full radius off it, which is what WALL_SQUASH has always meant at the
        // floor; a doorway's lintel and jambs are surfaces of the same wall, so they get
        // the same treatment.
        final double contactRadius = radius * WALL_SQUASH;
        blob.renderScale += (blob.scale - blob.renderScale) * relax;

        // Ambient falls off steeply above the heater at the floor, so it reads
        // as hot pad and cold column rather than as a gradient. A blob above its
        // own neutral point is buoyant, and the lag in reaching ambient is what
        // makes the trip long rather than settling at an equilibrium height.
        // Depth is measured from the blob's underside, not its centre: a big
        // blob resting on the pad is touching the heater just as a small one is,
        // and sampling at the centre would leave it too cold to ever lift off.
        final double depth = Math.max(0, blob.h - contactRadius);
        final double ambient = Math.exp(-depth / heatDepth);
        blob.temp +=
          (ambient - blob.temp) *
          Math.min(1, THERMAL_RATE * blob.thermal * dt / Math.sqrt(blob.scale));
        // Schmitt trigger on temperature: while rising, only a fall past
        // neutral - hysteresis turns the blob around, and vice versa.
        final double trigger = blob.rising
          ? blob.neutral - THERMAL_HYSTERESIS
          : blob.neutral + THERMAL_HYSTERESIS;
        if (blob.rising == (blob.temp < trigger)) {
          blob.rising = !blob.rising;
        }
        blob.vh += (terminalVelocity(blob) - blob.vh) * VERTICAL_DRAG * dt;
        blob.h += blob.vh * dt;

        blob.vx +=
          (random.nextDouble() - .5) * wanderKick - LATERAL_DRAG * blob.vx * dt;
        blob.x = wrapColumn(blob.x + blob.vx * dt);

        contain(blob, contactRadius, drawnRadius(blob.renderScale));
      }
      merge();
      split(dt);
      reconcileVolume(dt);
      // Merging moves a survivor to the pair's centre of area and splitting throws both
      // halves along the parent's heading, neither of which knows about the geometry, so a
      // blob can land in an opening after the integration pass has already run. Containing
      // again here is what keeps that from being drawn for a frame.
      for (int i = 0; i < this.count; ++i) {
        final Blob blob = this.blobs[i];
        contain(blob, blob.scale * baseRadius * WALL_SQUASH, drawnRadius(blob.renderScale));
      }
    }

    /**
     * The speed a blob converges on while its temperature holds. Saturating the
     * offset through tanh is the whole trick: a blob that has just latched is a
     * full hysteresis band away from its trigger, so it travels at essentially
     * terminal speed for the length of the trip and only eases off as it nears
     * the far threshold — a soft turnover rather than a hover at whatever height
     * lift happens to balance weight.
     */
    private double terminalVelocity(Blob blob) {
      final double bias = blob.rising ? -THERMAL_HYSTERESIS : THERMAL_HYSTERESIS;
      return
        BUOYANCY / VERTICAL_DRAG *
        Math.tanh((blob.temp - blob.neutral - bias) * BUOYANCY_SHARPNESS) *
        Math.sqrt(blob.scale);
    }

    /** Empties the lamp so the next update seeds a full, already-running one. */
    private void reset() {
      this.count = 0;
      this.primed = false;
      this.spawnBudget = 0;
    }

    private double wrapColumn(double x) {
      return x - Math.floor(x / this.width) * this.width;
    }

    private double wrapDelta(double dx) {
      if (dx > this.width * .5) {
        return dx - this.width;
      }
      if (dx < -this.width * .5) {
        return dx + this.width;
      }
      return dx;
    }

    private void remove(int index) {
      final Blob removed = this.blobs[index];
      this.blobs[index] = this.blobs[this.count - 1];
      this.blobs[this.count - 1] = removed;
      --this.count;
    }

    /**
     * Blobs whose centres have very nearly coincided are fused into one,
     * conserving area. The metaball field draws them as a single shape long
     * before this; merging keeps the simulation from carrying two bodies where
     * one is visible, and holding off until they are almost on top of each other
     * means the fuse happens inside a silhouette that is already round.
     */
    private void merge() {
      for (int i = 0; i < this.count; ++i) {
        final Blob a = this.blobs[i];
        for (int j = i + 1; j < this.count; ++j) {
          final Blob b = this.blobs[j];
          final double reach = (a.scale + b.scale) * baseRadius * mergeFactor;
          final double dx = wrapDelta(b.x - a.x);
          final double dh = b.h - a.h;
          if (dx * dx + dh * dh >= reach * reach) {
            continue;
          }
          final double aWeight = a.scale * a.scale;
          final double bWeight = b.scale * b.scale;
          final double total = aWeight + bWeight;
          if (total > MAX_SCALE * MAX_SCALE) {
            // Clamping the fused scale here would quietly destroy area, and
            // reconcileVolume would answer by spawning replacements at the
            // floor, which merge in turn — that feedback is what rafts the whole
            // lamp into a bar along the bottom. Leave the pair as two bodies;
            // the field still draws them as one shape.
            continue;
          }
          // b stops contributing to the field this frame, so the survivor has to
          // start out drawing what the pair drew. Two nearly coincident lobes read
          // as wider than the larger one alone and narrower than the area-conserving
          // fusion of the two — their fields overlap, so the sum is not a sum of
          // footprints. Start between the two and let the relaxation carry the rest.
          final double fusedRenderScale = LXUtils.lerp(
            Math.max(a.renderScale, b.renderScale),
            Math.sqrt(a.renderScale * a.renderScale + b.renderScale * b.renderScale),
            MERGE_RENDER_BLEND
          );
          if (fusedRenderScale > MAX_SCALE) {
            // The guard above bounds the fused *simulated* scale, and that is not the same
            // bound. renderScale eases toward scale rather than tracking it, so after a
            // Volume reduction has trimmed both bodies the pair can satisfy the scale guard
            // while their still-lagging rendered radii fuse past MAX_SCALE. That matters
            // because MAX_SCALE is exactly what keeps renderField's reach — renderScale x
            // baseRadius x INFLUENCE_RADIUS, at most 2.5 x 12 x 2 = 60 — inside half the
            // 120-column cylinder. Past it, the unwrapped column span exceeds the
            // circumference and floorMod folds columns onto themselves, so the same cell
            // takes the same blob's contribution twice. Decline the fuse for the same reason
            // the oversize guard does, rather than clamping: the pair still draws as one
            // shape, and the relaxation brings both rendered radii down within a few tenths
            // of a second, after which the fuse goes through.
            continue;
          }
          final double fusedX = wrapColumn(a.x + dx * (bWeight / total));
          final double fusedH = (a.h * aWeight + b.h * bWeight) / total;
          if (!clearsDoors(fusedX, fusedH, drawnRadius(fusedRenderScale))) {
            // The centre of area of two bodies either side of an opening can fall inside
            // it, and the fused body would then be shoved back out far enough to read as a
            // jump. Decline the fuse instead, exactly as the oversize guard above does: the
            // field still draws the pair as one shape, and they fuse on a later frame once
            // they have drifted somewhere the survivor fits.
            continue;
          }
          a.x = fusedX;
          a.h = fusedH;
          a.vx = (a.vx * aWeight + b.vx * bWeight) / total;
          a.vh = (a.vh * aWeight + b.vh * bWeight) / total;
          a.temp = (a.temp * aWeight + b.temp * bWeight) / total;
          a.neutral = (a.neutral * aWeight + b.neutral * bWeight) / total;
          a.thermal = (a.thermal * aWeight + b.thermal * bWeight) / total;
          if (bWeight > aWeight) {
            a.rising = b.rising;
          }
          a.scale = Math.sqrt(total);
          a.renderScale = fusedRenderScale;
          remove(j);
          --j;
        }
      }
    }

    /**
     * Oversized blobs neck and break in two along their direction of travel,
     * splitting the area evenly. Without this everything ends up as one blob.
     */
    private void split(double dt) {
      final int limit = this.count;
      for (int i = 0; i < limit; ++i) {
        if (this.count >= this.blobs.length) {
          return;
        }
        final Blob a = this.blobs[i];
        if (a.scale <= SPLIT_SCALE) {
          continue;
        }
        final double excess = a.scale / SPLIT_SCALE - 1;
        if (random.nextDouble() >= SPLIT_RATE * splitTendency * excess * dt) {
          continue;
        }
        final double scale = a.scale / Math.sqrt(2);
        // Halve the rendered area the same way, so a parent still ramping toward its
        // own scale hands both halves the same fraction of the way along.
        final double renderScale = a.renderScale / Math.sqrt(2);

        final double speed = Math.sqrt(a.vx * a.vx + a.vh * a.vh);
        final double ux = (speed > 1e-3) ? a.vx / speed : 1;
        final double uh = (speed > 1e-3) ? a.vh / speed : 0;
        // Far enough apart that merge() does not immediately undo the split.
        final double offset = scale * baseRadius * Math.max(.8, mergeFactor * 1.15);
        final double halfRadius = drawnRadius(renderScale);
        final double leadX = wrapColumn(a.x + ux * offset);
        final double leadH = a.h + uh * offset;
        final double trailX = wrapColumn(a.x - ux * offset);
        final double trailH = a.h - uh * offset;
        if (
          !clearsDoors(leadX, leadH, halfRadius) ||
          !clearsDoors(trailX, trailH, halfRadius)
        ) {
          // Necking apart throws both halves along the parent's heading with no regard for
          // what is there, and a half landing in a doorway would be shoved back out far
          // enough to read as a jump. Stay one body until the parent has drifted somewhere
          // it can break in two — the same answer merge() gives to the same problem.
          continue;
        }

        final Blob b = this.blobs[this.count++];
        a.scale = scale;
        b.scale = scale;
        a.renderScale = renderScale;
        b.renderScale = renderScale;
        b.x = leadX;
        b.h = leadH;
        a.x = trailX;
        a.h = trailH;
        b.vx = a.vx + ux * SPLIT_KICK;
        b.vh = a.vh + uh * SPLIT_KICK;
        a.vx -= ux * SPLIT_KICK;
        a.vh -= uh * SPLIT_KICK;
        b.temp = a.temp;
        b.neutral = a.neutral;
        b.thermal = a.thermal;
        b.rising = a.rising;
      }
    }

    /** The lava area Volume is asking for, in nominal blob areas. */
    private double areaTarget() {
      return volumeFraction * this.width * this.height / (Math.PI * baseRadius * baseRadius);
    }

    /** The lava area the lamp is actually holding, in the same units as {@link #areaTarget}. */
    private double lavaArea() {
      double area = 0;
      for (int i = 0; i < this.count; ++i) {
        area += this.blobs[i].scale * this.blobs[i].scale;
      }
      return area;
    }

    /** How far this lamp is under its Volume target; see {@link LavaLamp#volumeDeficit}. */
    private double areaDeficit() {
      return areaTarget() - lavaArea();
    }

    /**
     * Holds the total lava area at what Volume asks for: new blobs well up from
     * the pool at the floor when there is too little, and everything shrinks
     * back when there is too much.
     *
     * Welling up draws on a time budget rather than a per-frame count, so the fill runs at the
     * same rate whatever the frame rate. It is the only part of the update that is not already a
     * multiplication by {@code dt}, and so the only one that could keep a held lamp moving, and
     * it is held still by refusing to spend at a zero step rather than merely by refusing to
     * accrue at one. The difference is credit banked before the pause: a lamp that closed a
     * deficit part-way through a frame's accrual keeps the remainder, so it can reach a hold
     * with blobs' worth of budget in hand, and a rule about accrual alone would let that
     * remainder well up into the frame a performer is holding still.
     *
     * The very first fill is the exception. It spreads blobs through the full
     * height with staggered heat and matching velocity, so the lamp is mid-cycle
     * from the first frame instead of opening on a several-second fill-up that
     * a show operator would see every time the pattern goes active. It runs before the lamp is
     * primed and takes no budget at all, so startup is instant even from a standing stop.
     */
    private void reconcileVolume(double dt) {
      final double target = areaTarget();
      double area = lavaArea();
      if (area < target * (1 - AREA_TOLERANCE)) {
        this.spawnBudget =
          Math.min(this.spawnBudget, SPAWN_BUDGET_CARRY) + dt * SPAWNS_PER_SECOND;
        while ((area < target) && (this.count < this.blobs.length)) {
          if (this.primed) {
            // Credit is spendable only while time is passing. Gating the spend on the step
            // rather than only the accrual on it is what makes the hold unconditional: a lamp
            // that closed a deficit part-way through a frame's accrual keeps the remainder, and
            // gating accrual alone would let that remainder well up a blob into a held frame.
            if ((dt <= 0) || (this.spawnBudget < 1)) {
              break;
            }
            --this.spawnBudget;
          }
          final Blob blob = this.blobs[this.count++];
          blob.scale = SPAWN_SCALE_MIN + random.nextDouble() * SPAWN_SCALE_RANGE;
          blob.renderScale = this.primed ? blob.scale * SPAWN_RENDER_FRACTION : blob.scale;
          blob.x = random.nextDouble() * this.width;
          blob.vx = 0;
          blob.neutral = NEUTRAL_MIN + random.nextDouble() * NEUTRAL_RANGE;
          blob.thermal = THERMAL_MIN + random.nextDouble() * THERMAL_RANGE;
          // Staggered heat, so a fresh pool does not launch all at once.
          blob.temp = random.nextDouble();
          blob.rising = blob.temp > blob.neutral;
          // The floor a blob wells up from is the one under the column it picked, which
          // over a doorway is that doorway's lintel — never the row the opening occupies.
          final double radius = blob.scale * baseRadius;
          final double contactRadius = radius * WALL_SQUASH;
          final double support = Math.max(
            this.baseFloor + contactRadius,
            doorSupportHeight(blob.x, drawnRadius(blob.renderScale))
          );
          final double ceiling = this.height - 1 - contactRadius;
          if (this.primed || (ceiling <= support)) {
            blob.h = support;
            blob.vh = 0;
          } else {
            // The warm start spreads blobs up the whole panel; measuring that spread from
            // the local support rather than from row zero keeps it out of the openings too.
            blob.h = LXUtils.lerp(support, ceiling, random.nextDouble());
            blob.vh = terminalVelocity(blob);
          }
          area += blob.scale * blob.scale;
        }
        this.primed = true;
      } else if (area > target * (1 + AREA_TOLERANCE)) {
        final double factor = Math.max(Math.sqrt(target / area), 1 - SHRINK_RATE * dt);
        for (int i = this.count - 1; i >= 0; --i) {
          final Blob blob = this.blobs[i];
          blob.scale *= factor;
          if (blob.scale < MIN_SCALE) {
            remove(i);
          }
        }
      }
    }

    private void renderField() {
      Arrays.fill(this.field, 0);
      Arrays.fill(this.temperature, 0);
      Arrays.fill(this.temperatureWeight, 0);
      for (int i = 0; i < this.count; ++i) {
        final Blob blob = this.blobs[i];
        // renderScale, not scale: the physics has already taken a merge or a split,
        // and this is the radius easing across it.
        final double reach = blob.renderScale * baseRadius * INFLUENCE_RADIUS;
        final double reachSquared = reach * reach;
        final double inverseReachSquared = 1 / reachSquared;
        final int hMin = (int) Math.max(0, Math.ceil(blob.h - reach));
        final int hMax = (int) Math.min(this.height - 1, Math.floor(blob.h + reach));
        final int xMin = (int) Math.ceil(blob.x - reach);
        final int xMax = (int) Math.floor(blob.x + reach);
        for (int x = xMin; x <= xMax; ++x) {
          final double dx = x - blob.x;
          final double dxSquared = dx * dx;
          if (dxSquared >= reachSquared) {
            continue;
          }
          // floorMod, not LXUtils.wrap: that helper's range is inclusive of both
          // ends, so its period is width - 1 and column 0 never gets written.
          final int column = Math.floorMod(x, this.width) * this.height;
          for (int h = hMin; h <= hMax; ++h) {
            final double dh = h - blob.h;
            final double distanceSquared = dxSquared + dh * dh;
            if (distanceSquared < reachSquared) {
              final double falloff = 1 - distanceSquared * inverseReachSquared;
              final int cell = column + h;
              this.field[cell] += falloff * falloff * falloff;
              // The temperature field rides alongside the density field, weighted by the
              // linear falloff rather than its cube: a cell in a neck between two lobes then
              // reads a genuine blend of both their temperatures, spread over the whole reach
              // instead of collapsing onto whichever lobe is marginally nearer.
              this.temperature[cell] += falloff * blob.temp;
              this.temperatureWeight[cell] += falloff;
            }
          }
        }
      }
    }

    /**
     * Resolves the physics scalar each lit cell hands {@link ColorRole#color(double)}, in that
     * method's -1..+1 convention where positive is brighter and less saturated.
     *
     * The blob surface is an iso-contour of the summed field, so the field is an implicit
     * surface and its gradient is the surface normal — no geometry beyond what
     * {@link #renderField} already wrote. A central difference gives the in-wall gradient, the
     * normal is {@code (-dF/dx, -dF/dh, DOME_HEIGHT)} normalized, and the cell is lit by a
     * Lambertian term plus a Blinn specular one. The gradient is scaled by the nominal
     * influence radius before the normal is built, which makes the doming independent of Size:
     * a big blob's field falls off over more rows and so has a shallower per-row gradient, and
     * without the scaling it would read progressively flatter as Size went up.
     *
     * Taking the normal from the *summed* field rather than from each blob's own radial
     * distance is the whole reason for doing it this way. A merged pair is one iso-surface, so
     * its gradient describes the shape that is actually there: the neck between two lobes has a
     * genuine waist, and the shading puts a shadow in it. Per-blob radial gradients would light
     * two independent spheres inside a silhouette that is visibly one peanut.
     *
     * Running once per lamp rather than once per orientation matters: the cube and the cylinder
     * each drive an exterior and an interior from one field, so this is half the work the old
     * per-write field depth was.
     *
     * The horizontal difference wraps with {@code floorMod}, matching {@link #renderField} —
     * the ring is continuous and the seam is not a feature. The vertical difference clamps
     * instead, since the floor and the ceiling are real ends of the panel; a clamped step spans
     * one row rather than two and is divided by that.
     */
    private void shadeField() {
      final double strength = shadeAmount;
      final double scale = gradientScale;
      for (int x = 0; x < this.width; ++x) {
        final int column = x * this.height;
        final int left = Math.floorMod(x - 1, this.width) * this.height;
        final int right = Math.floorMod(x + 1, this.width) * this.height;
        for (int h = 0; h < this.height; ++h) {
          final int cell = column + h;
          final double value = this.field[cell];
          if (value <= 0) {
            continue;
          }
          final double depth = fieldDepth(value);
          if (strength <= 0) {
            // Shade at zero is the pre-shading look exactly, not an approximation of it: the
            // same field depth, by the same expression, so the A/B is clean.
            this.physics[cell] = depth;
            continue;
          }
          final int above = (h + 1 < this.height) ? h + 1 : h;
          final int below = (h > 0) ? h - 1 : h;
          final double gradientX = (this.field[right + h] - this.field[left + h]) * .5 * scale;
          final double gradientH =
            (this.field[column + above] - this.field[column + below]) / (above - below) * scale;
          // The field decreases outward, so the outward normal is the negated gradient.
          double normalX = -gradientX;
          double normalH = -gradientH;
          double normalZ = DOME_HEIGHT;
          final double length =
            Math.sqrt(normalX * normalX + normalH * normalH + normalZ * normalZ);
          normalX /= length;
          normalH /= length;
          normalZ /= length;
          final double lambert = normalX * lightX + normalH * lightH + normalZ * LIGHT_OUT_OF_WALL;
          double lit = 0;
          if (lambert > 0) {
            lit = lambert;
            // Blinn: the highlight is where the normal aligns with the halfway vector between
            // the light and the viewer. Gated on the lit side, so no gloss appears in shadow.
            final double mirror = normalX * halfX + normalH * halfH + normalZ * halfZ;
            if ((glossAmount > 0) && (mirror > 0)) {
              // Four squarings rather than Math.pow: the exponent is fixed at sixteen and this
              // runs on every lit cell of both lamps every frame. Sixteen puts the highlight's
              // half-brightness edge about seventeen degrees off the mirror direction, which at
              // the rate a nominal blob's normal turns is a spot two or three pixels across —
              // small enough to read as gloss, large enough to survive LED resolution.
              final double mirror2 = mirror * mirror;
              final double mirror4 = mirror2 * mirror2;
              final double mirror8 = mirror4 * mirror4;
              lit += glossAmount * mirror8 * mirror8;
            }
          }
          // Lambert lives in 0..1 and the role convention is -1..+1, so a fully lit cell reads
          // as the brightest, least saturated end and a cell past the terminator as the
          // deepest. Shade blends that against the flat field depth rather than replacing it,
          // which is what makes zero an exact A/B rather than a different-looking neutral.
          this.physics[cell] = LXUtils.lerp(depth, LXUtils.clamp(2 * lit - 1, -1, 1), strength);
        }
      }
    }

    /**
     * Falloff-weighted mean temperature of the blobs reaching this cell. Any cell with a
     * non-zero field necessarily has a non-zero weight, since both come from the same
     * strictly positive falloff; the guard returns the middle of the normalized range so an
     * unreachable divide-by-zero would blend the two roles evenly rather than snap to an end.
     */
    private double cellTemperature(int cell) {
      final double weight = this.temperatureWeight[cell];
      return (weight > 0)
        ? this.temperature[cell] / weight
        : .5 * (TEMPERATURE_COOL + TEMPERATURE_HOT);
    }
  }

  private final Random random = createRandom();

  private final Lamp cubeLamp =
    new Lamp(Apotheneum.Cube.Ring.LENGTH, Apotheneum.GRID_HEIGHT);

  private final Lamp cylinderLamp =
    new Lamp(Apotheneum.Cylinder.Ring.LENGTH, Apotheneum.CYLINDER_HEIGHT);

  /** The model both lamps' door profiles were resolved against. */
  private LXModel geometryModel;

  private double baseRadius;
  private double volumeFraction;
  private double mergeFactor;
  private double splitTendency;
  private double heatFraction;
  private double wanderAmount;
  private double edgeLow;
  private double edgeHigh;
  private double glowLevel;
  private double isoLevel;
  /** Where a lone blob's iso-contour falls, in nominal radii, at the current Threshold. */
  private double drawnRadiusScale;
  private double shadeAmount;
  private double glossAmount;
  /** Rows per nominal influence radius: what the field gradient is measured in. */
  private double gradientScale;
  /** Unit vector from the surface toward the light, in-wall components. */
  private double lightX;
  private double lightH;
  /** Unit halfway vector between the light and the viewer, who is straight out of the wall. */
  private double halfX;
  private double halfH;
  private double halfZ;

  /** The hot end of the lava ramp. Alias for {@link ColorNativePattern#primary}. */
  public final ColorRole hotColor;

  /** The cool end of the lava ramp. Alias for {@link ColorNativePattern#secondary}. */
  public final ColorRole coolColor;

  public LavaLamp(LX lx) {
    super(lx, 1, .5, 2, .5);
    this.hotColor = this.primary;
    this.coolColor = this.secondary;
    addParameter("size", this.size);
    addParameter("volume", this.volume);
    addParameter("coalesce", this.coalesce);
    addParameter("speed", this.speed);
    addParameter("heat", this.heat);
    addParameter("wander", this.wander);
    addParameter("threshold", this.threshold);
    addParameter("edge", this.edge);
    addParameter("glow", this.glow);
    addParameter("shade", this.shade);
    addParameter("light", this.light);
    addParameter("gloss", this.gloss);
    addParameter("renderToCube", this.renderToCube);
    addParameter("renderToCylinder", this.renderToCylinder);
    addParameter("renderToExterior", this.renderToExterior);
    addParameter("renderToInterior", this.renderToInterior);
  }

  private static Random createRandom() {
    final String renderSeed = System.getProperty(RENDER_SEED_PROPERTY);
    return (renderSeed == null) ? new Random() : new Random(Long.parseLong(renderSeed));
  }

  @Override
  public void onActive() {
    super.onActive();
    // Reseed rather than resume, so every activation opens mid-cycle.
    this.cubeLamp.reset();
    this.cylinderLamp.reset();
  }

  @Override
  protected void render(double deltaMs) {
    updateViewMask();
    if (this.geometryModel != this.lx.getModel()) {
      initializeGeometry();
    }
    updateDerivedValues();
    this.hotColor.update();
    this.coolColor.update();
    final double dt =
      Math.min(MAX_DELTA_SECONDS, deltaMs * .001) * this.speed.getValue();

    // Everything in the view goes dark first, every frame, and only what the toggles allow is
    // painted back. Metaballs are sparse — most cells are empty on any given frame — so
    // without this the lamp would leave comet trails behind every blob. It is also what makes
    // switching a chamber or a side off actually go off rather than freezing the last frame
    // on those LEDs, and what leaves the rows a doorway occupies black without a second pass.
    clearView();

    // Both lamps keep simulating whether or not they are drawn, so a chamber switched back on
    // rejoins the convection cycle where it would have been rather than restarting cold from
    // a fresh fill. The lamp is a slow oscillator — a trip up the wall is seconds long — so
    // resuming from a reset would be plainly visible as the lava re-forming.
    this.cubeLamp.update(dt);
    this.cylinderLamp.update(dt);

    writeChamber(Apotheneum.cube, this.cubeLamp, this.renderToCube);
    writeChamber(Apotheneum.cylinder, this.cylinderLamp, this.renderToCylinder);
  }

  /**
   * Paints one chamber's enabled sides, if the chamber itself is enabled.
   *
   * The field and the shading are built here rather than alongside the simulation because
   * neither feeds back into it — both are pure functions of the blob state, read only by
   * {@link #write} — so a chamber nobody is looking at can skip the two per-cell passes that
   * dominate the pattern's cost and lose nothing by it. Switching it back on rebuilds them
   * from the live blobs on that same frame.
   */
  private void writeChamber(
    Apotheneum.Component component,
    Lamp lamp,
    BooleanParameter enabled
  ) {
    if (!enabled.isOn()) {
      return;
    }
    final Apotheneum.Orientation[] orientations = component.orientations();
    boolean shaded = false;
    for (int side = 0; side < orientations.length; ++side) {
      // Side comes from the index rather than from comparing against exterior()/interior(),
      // which are themselves defined as index 0 and index 1.
      final boolean sideEnabled = (side == Apotheneum.Orientation.EXTERIOR)
        ? this.renderToExterior.isOn()
        : this.renderToInterior.isOn();
      // The interior is absent entirely on an installation without one.
      if (!sideEnabled || (orientations[side] == null)) {
        continue;
      }
      if (!shaded) {
        lamp.renderField();
        lamp.shadeField();
        shaded = true;
      }
      write(orientations[side], lamp);
    }
  }


  /**
   * Resolves both lamps' floor profiles from the live model. Deferred to the first render
   * because {@link apotheneum.ApotheneumPattern#render} only runs once the Apotheneum model
   * exists, and repeated whenever that model changes.
   *
   * One profile per lamp covers both of a component's surfaces: exterior and interior are
   * instances of the same {@code Orientation} class, whose {@code available()} is a pure
   * function of the column index, so they cannot disagree about where the doors are.
   * {@code LavaLampDoorTest} asserts that on the real fixture rather than trusting it.
   */
  private void initializeGeometry() {
    this.geometryModel = this.lx.getModel();
    this.cubeLamp.initializeGeometry(Apotheneum.cube.exterior);
    this.cylinderLamp.initializeGeometry(Apotheneum.cylinder.exterior);
  }

  /**
   * Deepest incursion of any blob into a door opening, in rows; zero when contained.
   *
   * The multiple of the nominal radius a blob is drawn at is an argument rather than the
   * pattern's own {@link #drawnRadiusScale} because this is the invariant's measuring stick.
   * Reading the solver's own figure would make the assertion a restatement of the solver's
   * arithmetic — it would report zero however wrong that figure was, which is exactly the
   * defect this parameter exists to be able to catch. The caller states the geometry it
   * believes in; the disagreement, if there is one, is the failure.
   */
  double doorPenetration(double drawnScale) {
    return Math.max(
      this.cubeLamp.doorPenetration(drawnScale),
      this.cylinderLamp.doorPenetration(drawnScale)
    );
  }

  /** How many blob centres currently sit inside a door opening rather than in the lava. */
  int blobCentersInsideDoors() {
    return this.cubeLamp.centersInsideDoors() + this.cylinderLamp.centersInsideDoors();
  }

  /**
   * How far the nearest blob on either chamber is above the doorway bound holding it up, in
   * rows; positive infinity when no opening reaches a blob. Negative is a containment failure
   * of exactly that depth.
   */
  double doorClearance(double drawnScale) {
    return Math.min(
      this.cubeLamp.doorClearance(drawnScale),
      this.cylinderLamp.doorClearance(drawnScale)
    );
  }

  /**
   * How far the lava falls short of what Volume is asking for, summed over both chambers, in
   * nominal blob areas. Positive means {@code reconcileVolume} still has blobs to well up;
   * zero or negative means it is done. A hold test uses it to say that the lamp really was
   * under its target while it was held, rather than freezing an already-settled lamp and
   * asserting nothing.
   */
  double volumeDeficit() {
    return this.cubeLamp.areaDeficit() + this.cylinderLamp.areaDeficit();
  }

  /**
   * Spawn credit both chambers are carrying, in blobs; the smaller of the two, so a positive
   * reading means either chamber could well up if the replenishment path let it.
   *
   * A hold test reads it to say that the lamp arrived at the hold with credit in hand, rather
   * than asserting that a lamp with nothing banked spends nothing. See
   * {@link Lamp#reconcileVolume}.
   */
  double spawnCredit() {
    return Math.min(this.cubeLamp.spawnBudget, this.cylinderLamp.spawnBudget);
  }

  /** The largest body either chamber is holding, as a multiple of the nominal blob radius. */
  double largestBlobScale() {
    return Math.max(this.cubeLamp.largestScale(), this.cylinderLamp.largestScale());
  }

  /** The largest body either chamber is drawing, as a multiple of the nominal blob radius. */
  double largestBlobRenderScale() {
    return Math.max(
      this.cubeLamp.largestRenderScale(),
      this.cylinderLamp.largestRenderScale()
    );
  }

  /**
   * How far the widest field sweep on either chamber would overrun that surface's
   * circumference, in columns, at the given nominal blob radius. Positive means
   * {@code renderField} folds columns onto themselves and double-counts a blob into the cells
   * where they land.
   */
  double fieldColumnSpanOverrunAt(double nominalRadius) {
    return Math.max(
      this.cubeLamp.columnSpanOverrun(nominalRadius),
      this.cylinderLamp.columnSpanOverrun(nominalRadius)
    );
  }

  /**
   * The largest amount by which any blob's rendered radius exceeds its simulated one, in
   * nominal radii, across both chambers.
   */
  double largestRenderScaleLag() {
    return Math.max(
      this.cubeLamp.largestRenderScaleLag(),
      this.cylinderLamp.largestRenderScaleLag()
    );
  }


  /**
   * How far resting the largest blob on either chamber on its tallest lintel would carry it
   * past the top of that panel, in rows. Negative while every body the lamp holds fits
   * between the two bounds.
   */
  double lintelHeadroomShortfall(double drawnScale) {
    return Math.max(
      this.cubeLamp.lintelHeadroomShortfall(drawnScale),
      this.cylinderLamp.lintelHeadroomShortfall(drawnScale)
    );
  }

  private void updateDerivedValues() {
    this.baseRadius = this.size.getValue();
    this.volumeFraction = this.volume.getValue();
    final double coalesce = this.coalesce.getValue();
    this.mergeFactor = MERGE_MIN + MERGE_RANGE * coalesce;
    this.splitTendency = SPLIT_TENDENCY_BASE - coalesce;
    this.heatFraction = this.heat.getValue();
    this.wanderAmount = this.wander.getValue();

    final double iso = this.threshold.getValue();
    this.isoLevel = iso;
    this.drawnRadiusScale = drawnRadiusScale(iso);
    final double half = this.edge.getValue() * iso * .5;
    this.edgeLow = Math.max(1e-4, iso - half);
    this.edgeHigh = iso + half;
    this.glowLevel = this.glow.getValue();

    this.shadeAmount = this.shade.getValue();
    this.glossAmount = this.gloss.getValue();
    this.gradientScale = this.baseRadius * INFLUENCE_RADIUS;
    // Light angle: 0 puts the source directly below the blob, where a real lamp's bulb sits,
    // and the angle advances counter-clockwise across the unwrapped wall — 90 lights from the
    // right, 180 from above, 270 from the left. On the unwrap x runs rightward and h runs
    // upward, so "below" is -h and the vector toward the light at angle a is (sin a, -cos a),
    // tilted out of the wall by LIGHT_OUT_OF_WALL.
    final double angle = Math.toRadians(this.light.getValue());
    this.lightX = Math.sin(angle) * LIGHT_IN_PLANE;
    this.lightH = -Math.cos(angle) * LIGHT_IN_PLANE;
    // The viewer is straight out of the wall at (0, 0, 1), so the halfway vector is the light
    // with one added to its out-of-wall component, renormalized.
    final double halfOutward = LIGHT_OUT_OF_WALL + 1;
    final double halfLength = Math.sqrt(
      this.lightX * this.lightX + this.lightH * this.lightH + halfOutward * halfOutward
    );
    this.halfX = this.lightX / halfLength;
    this.halfH = this.lightH / halfLength;
    this.halfZ = halfOutward / halfLength;
  }

  /**
   * Where a lone blob's iso-contour falls, in nominal radii, at a given iso level — the one
   * place the drawn radius is derived, and the answer to the drawn-versus-collision confusion
   * that keeps recurring here.
   *
   * The falloff is Wyvill's, so a lone blob's field at distance d is
   * {@code (1 - d^2/R^2)^3} out to {@code R = INFLUENCE_RADIUS * r}, and its surface is where
   * that equals the iso level. Solving for d:
   *
   * <pre>
   *   (1 - d^2/R^2)^3 = iso
   *          d^2/R^2  = 1 - cbrt(iso)
   *              d/r  = INFLUENCE_RADIUS * sqrt(1 - cbrt(iso))
   * </pre>
   *
   * Threshold moves that by more than a factor of two and a half across its range: 1.42
   * nominal radii at its low bound of .12, 1.00 at its default of .42, and .54 at its high
   * bound of .8. A fixed multiplier is right at exactly one setting, which is why this is
   * resolved per frame in {@link #updateDerivedValues} and read through {@link #drawnRadius}
   * everywhere a blob has to be kept clear of something.
   *
   * The iso level and not the outer end of the Edge ramp, deliberately. Below {@code edgeLow}
   * a cell is halo rather than blob — dimmed to the Glow level and fading to nothing out at
   * the full influence radius — and the halo is meant to spill: it already runs off the top
   * and bottom of the panel. Colliding with it would double every blob's collision radius at
   * every setting and hold the lava a whole diameter off every doorway.
   *
   * The derivation is exact for a lone blob, and only for a lone blob. {@link Lamp#renderField}
   * sums fields, so two bodies near each other but not yet fused raise the level between them
   * and push the combined contour a little past this radius; at a doorway that is a small
   * accepted overhang rather than the bite containment exists to remove. There is no closed
   * form for the summed case — the contour depends on how many bodies are nearby and where —
   * and inflating this factor enough to cover the worst cluster would hold every blob that far
   * off every lintel all the time, which is a permanent dead ring around each doorway in
   * exchange for an occasional pixel. So the containment guarantee, and what
   * {@code LavaLampDoorTest} asserts, is per-blob: every body is clear of every opening at the
   * radius solved here. A cluster's overhang is the residue that leaves.
   *
   * Static, and taking the level as an argument, so a test can restate the geometry it is
   * checking rather than call back into the per-frame value the solver itself uses.
   */
  static double drawnRadiusScale(double isoLevel) {
    // Threshold's range tops out at .8, so the root is real; the guard costs one comparison a
    // frame and keeps a future range change from producing NaN radii rather than a bad look.
    return INFLUENCE_RADIUS * Math.sqrt(Math.max(0, 1 - Math.cbrt(isoLevel)));
  }

  /**
   * The radius, in rows, that a blob of this rendered scale is actually drawn at — the radius
   * the doorways, and everything else that must not cut into a visible blob, collide with.
   *
   * Not {@link #WALL_SQUASH} of the nominal radius, which is what the floor and the ceiling
   * use: squashing against the glass at the top and bottom of the panel is the lamp's look,
   * but a doorway biting into a blob in the middle of the wall is the artifact containment
   * exists to remove, so a blob held this far off a lintel rests on it with nothing to spare
   * and nothing cut away. And not a fixed multiple of the nominal radius either — see
   * {@link #drawnRadiusScale}, which is what Threshold moves.
   */
  private double drawnRadius(double renderScale) {
    return renderScale * this.baseRadius * this.drawnRadiusScale;
  }

  /**
   * Maps the summed field to brightness: solid inside the iso-surface, a ramp
   * across it, and a dim halo out to the edge of the field.
   */
  private double surfaceBrightness(double field) {
    if (field >= this.edgeHigh) {
      return 1;
    }
    if (field >= this.edgeLow) {
      final double t = (field - this.edgeLow) / (this.edgeHigh - this.edgeLow);
      return LXUtils.lerp(this.glowLevel, 1, t * t * (3 - 2 * t));
    }
    final double t = field / this.edgeLow;
    return this.glowLevel * t * t;
  }

  /**
   * Where a cell sits relative to the iso-surface, in the -1..+1 convention
   * {@link ColorRole#color(double)} expects: -1 far out in the halo, 0 on the surface itself,
   * +1 once the field reaches twice the iso level — the dense interior of a blob. Positive
   * drives a role brighter and less saturated, so the core reads as the incandescent part and
   * the rim as the cooler, deeper-tinted skin. Each role's Amount knob sets how far apart the
   * two get.
   *
   * This is the flat look Shade blends away from, and what a cell resolves to exactly when
   * Shade is zero. See {@link Lamp#shadeField}.
   */
  private double fieldDepth(double field) {
    return LXUtils.clamp(field / this.isoLevel - 1, -1, 1);
  }

  /**
   * The lava ramp at one cell: the cool role at {@link #TEMPERATURE_COOL} and below, the hot
   * role at {@link #TEMPERATURE_HOT} and above, interpolated between. Both ends are resolved
   * at the same physics scalar, so the relief is the same on either side of the ramp and only
   * the hue travels.
   */
  private int lavaColor(double physics, double temperature) {
    return LXColor.lerp(
      this.coolColor.color(physics),
      this.hotColor.color(physics),
      LXUtils.clamp(
        (temperature - TEMPERATURE_COOL) / (TEMPERATURE_HOT - TEMPERATURE_COOL), 0, 1
      )
    );
  }

  private void write(Apotheneum.Orientation orientation, Lamp lamp) {
    if (orientation == null) {
      return;
    }
    for (int x = 0; x < lamp.width; ++x) {
      final int available = orientation.available(x);
      final int column = x * lamp.height;
      for (int y = 0; y < available; ++y) {
        // Row 0 is the top of the column; the lamp field is indexed from the floor.
        final int cell = column + lamp.height - 1 - y;
        final double field = lamp.field[cell];
        if (field <= 0) {
          // Empty cell: the frame's opening clear already left it black.
          continue;
        }
        final int index = orientation.point(x, y).index;
        if (isViewPoint(index)) {
          // Brightness scaling is the last step, per ColorNativePattern's resolution order:
          // palette stop, then hue/saturation offsets, then the physics perturbation, then
          // this mask. The halo therefore comes out as dimmed lava of the temperature of the
          // blob casting it -- not as a separate cool fringe, which would paint a blue rim
          // around every hot blob and say the opposite of what the ramp means.
          colors[index] = LXColor.scaleBrightness(
            lavaColor(lamp.physics[cell], lamp.cellTemperature(cell)),
            surfaceBrightness(field)
          );
        }
      }
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, ColorNativePattern pattern) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 2);

    addColumn(uiDevice, "Lava",
      newKnob(this.size),
      newKnob(this.volume),
      newKnob(this.coalesce)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Flow",
      newKnob(this.speed),
      newKnob(this.heat),
      newKnob(this.wander)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Surface",
      newKnob(this.threshold),
      newKnob(this.edge),
      newKnob(this.glow)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Light",
      newKnob(this.shade),
      newKnob(this.light),
      newKnob(this.gloss)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Chamber",
      newButton(this.renderToCube),
      newButton(this.renderToCylinder)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Side",
      newButton(this.renderToExterior),
      newButton(this.renderToInterior)).setChildSpacing(6);

    // Colour columns are built by the base class, not here (ColorNativePattern owns that UI),
    // and last so they land at the end of the panel.
    buildColorDeviceControls(ui, uiDevice);
  }
}
