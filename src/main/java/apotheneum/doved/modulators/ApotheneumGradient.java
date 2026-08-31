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

package apotheneum.doved.modulators;

import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

/**
 * The single, engine-owned direction {@code apotheneum.doved.effects.GradientMultiplyEffect}
 * reads instead of owning its own per-surface angles -- the geometric sibling of
 * {@link ApotheneumColor}, which already plays this role for the gradient's two end colours.
 * Registered on {@code lx.engine} at {@link #PATH} by {@code ApotheneumColorPlugin} and shown in
 * the studio left pane's GLOBAL tab by {@code ApotheneumColorUIPlugin}, the same core/UI split
 * {@link ApotheneumColor} and {@code apotheneum.video.ApotheneumVideo} already use -- bundled
 * into that existing plugin pair rather than a new one of its own so enabling "Apotheneum Color"
 * (core) and "Apotheneum Color UI" (studio) is the one on/off switch that brings up the whole
 * GLOBAL panel, colour and gradient together, instead of asking the owner to separately toggle a
 * second plugin pair to get a working gradient.
 *
 * <h2>Why 3D, not a per-surface 2D raster angle</h2>
 *
 * The previous shape of this control put one {@code direction} (0-360&#176;) on each of the four
 * surfaces, computed against that surface's own unwrapped column/row raster. That produced a
 * visible seam on any surface whose horizontal axis wraps in the real installation -- the
 * cylinder is a true circle, and the cube exterior's 200 columns are four flat walls
 * concatenated into a loop -- because a linear ramp across the unwrapped raster does not wrap
 * with the geometry it was drawn from: position 0 and position (width-1) are adjacent in the
 * real room but sit at opposite ends of the ramp. A horizontal direction hit that seam on every
 * lap; vertical never did, because the vertical axis genuinely has two ends. Reported directly
 * by the owner: <em>"for an interior, if I have it turned on for one of them (like the cylinder
 * interior), it looks weird because it's cut off from one side to the other. If I have it going
 * from left to right, it looks weird, but if I have it going up and down, it looks good."</em>
 *
 * <p>A cyclic/triangle-wave patch on the 2D raster would have hidden the seam without fixing
 * what caused it, and would have done nothing for the deeper problem the owner named in the same
 * pass: <em>"it should be multiplying it in 3D space, not in 2D space."</em> Every {@link LXPoint}
 * already carries a real world position ({@code x}/{@code y}/{@code z}) computed by the fixture
 * JSON, the same coordinates {@code apotheneum.patterns.Hyperspace}, {@code
 * apotheneum.doved.patterns.RobotHeart} and {@code apotheneum.doved.patterns.Fireball}'s bearing
 * work (see {@code FireballBearingAlignmentTest}) all read directly rather than re-deriving from
 * column/row indices. Projecting a point's world position onto one direction vector through that
 * same space has no seam to patch: a continuous 3D position has no "wraps here" boundary the way
 * an unwrapped 2D raster coordinate does, so the cylinder's column 0 and column 119 -- physically
 * adjacent -- resolve to nearly identical projections instead of opposite ends of a ramp. This
 * also means cube and cylinder stop being gradiented in their own private 2D spaces and start
 * agreeing spatially: a point on the cube's front wall and a point on the cylinder at the same
 * real-world height and depth resolve to the same gradient position, because they are the same
 * position.
 *
 * <h2>Why one shared direction, not four</h2>
 *
 * <em>"I also think we want the gradient to be a global setting and not per pattern"</em> --
 * and once the direction is a real 3D vector through the one space every surface shares, "one
 * direction per surface" stops meaning anything coherent: there is exactly one direction through
 * 3D space at a time, the same way there is exactly one {@link ApotheneumColor#pair}/{@link
 * ApotheneumColor#swap}. A performer turning this knob is tilting the gradient through the whole
 * room, not one surface's private ramp. If a future need calls for surfaces disagreeing on
 * direction again, that is a new, explicit ask to design for -- not assumed here.
 *
 * <h2>Parameterisation: azimuth + elevation, not a raw unit vector</h2>
 *
 * {@link #azimuth} (0-360&#176;, compass heading in the model's horizontal X/Z plane) and
 * {@link #elevation} (&#177;90&#176;, tilt away from horizontal) are the standard
 * spherical-direction pair -- two bounded, independently modulatable knobs instead of a raw
 * {@code (x, y, z)} unit vector a performer would have to keep normalized by hand.
 * {@code elevation = +-90} is straight up/down regardless of {@code azimuth} (matching the
 * owner's own "up and down looks good" as a reachable, nameable setting rather than an
 * accident of one particular azimuth), and {@code elevation = 0} sweeps the horizontal plane as
 * {@code azimuth} turns -- the control that replaces the old per-surface 0-360&#176; knob, now
 * meaning one true compass heading instead of four independent raster angles. Direction is
 * derived fresh from the two angles wherever it's needed ({@link #directionX}/{@link
 * #directionY}/{@link #directionZ}) rather than cached, since both are ordinary modulatable
 * {@link CompoundParameter}s a performer or an LFO can move continuously.
 *
 * <h2>Normalizing against the model's own extent, not per-surface</h2>
 *
 * A raw dot product has no natural {@code [0, 1]} range -- it's a signed distance along the
 * direction vector, in the fixture's real-world units, with no fixed span to compare against.
 * {@link #projectedMin}/{@link #projectedMax} resolve that span from the whole model's own
 * bounding box (the same {@code xMin}/{@code xMax}/{@code yMin}/{@code yMax}/{@code
 * zMin}/{@code zMax} fields {@code apotheneum.patterns.Hyperspace} and {@code
 * apotheneum.doved.patterns.RobotHeart} already read directly off a real {@code LXModel}, not
 * {@link LXPoint#xn}/{@link LXPoint#yn}/{@link LXPoint#zn} -- those are normalized against
 * whatever the *entire running project's* top-level model happens to contain when it was last
 * normalized, which is not necessarily just Apotheneum, and would silently change this gradient's
 * span if an unrelated fixture were ever added to the project). Because the projection of an
 * axis-aligned box onto any direction is always achieved at one of its corners, the true min and
 * max are computed directly from the six bounds -- {@code min(dx&#183;xMin, dx&#183;xMax) +
 * min(dy&#183;yMin, dy&#183;yMax) + min(dz&#183;zMin, dz&#183;zMax)} for the minimum, the
 * corresponding {@code max(...)} triple for the maximum -- rather than scanning any points.
 * Every surface then maps into the identical {@code [0, 1]} span the whole installation occupies
 * along that direction, so the gradient spans the piece at every azimuth/elevation rather than
 * clipping (a span narrower than what's actually lit) or compressing (wider, wasting most of the
 * 0-1 range on empty space off either end).
 *
 * <h2>Collapsing to flat colour: a continuous {@link #spread}, not a boolean</h2>
 *
 * The owner, watching the piece: <em>"we should also be able to disable the gradient and just
 * make it flat colors."</em> Built as a continuous {@link #spread} (1 = full gradient, 0 =
 * flat) rather than an on/off switch, for two reasons that hold specifically for this rig, not
 * as a general rule: {@code spread} is a strict superset of the requested toggle (either
 * endpoint reproduces it), and this rig's owner has already preferred a knob he can turn down
 * over a switch he flips -- {@code apotheneum.doved.patterns.Grass}'s wind and this project's
 * theremin scalar both ended up continuous for the same reason. A boolean is also not an
 * {@code LXCompoundModulation.Target}, where a {@link CompoundParameter} is, which matters
 * specifically because every control on this rig eventually gets driven by something. No
 * discontinuity or degrading-blend argument turned up against it -- see {@link #applySpread}
 * for the formula and why it stays smooth at every value.
 *
 * <p><b>Flat resolves to the midpoint of primary and secondary, not primary alone.</b> The
 * gradient position {@code t} (from {@link #normalize}) is rescaled toward the center of its own
 * {@code [0, 1]} range -- {@code effectiveT = 0.5 + spread * (t - 0.5)} -- rather than toward
 * zero. Both read as continuous in {@code spread}, but they say different things about what
 * "flat" is: scaling toward zero collapses every surface onto primary alone, with secondary's
 * whole presence fading out as an edge case of one particular colour; scaling toward the center
 * collapses onto the blend of the two, the same way turning a literal "Spread" knob down on any
 * two-sided control narrows the band symmetrically from both ends until it meets in the middle.
 * The owner asked to name this control {@code spread} (or {@code amount}); {@code spread} was
 * chosen for exactly this reason -- the metaphor it names is the symmetric one, not "fade toward
 * the first colour" -- and midpoint-collapse is what makes turning it down look like the
 * gradient tightening toward a colour rather than sliding toward an unrelated one.
 *
 * <h2>The null-instance fallback</h2>
 *
 * Mirrors {@link ApotheneumColor#resolvePrimaryOrNeutral}: with no {@code ApotheneumGradient}
 * registered (the core plugin failed to load, or is disabled), {@link #azimuthOrDefault}/{@link
 * #elevationOrDefault} resolve to a fixed default -- azimuth 0, elevation +90 -- rather than
 * throwing or rendering garbage. Elevation +90 is the one direction the owner confirmed looks
 * right on every surface regardless of azimuth (a horizontal direction that happened to
 * disagree with the real geometry is exactly the bug this class exists to fix), so it is the
 * safe thing to fall back to, not an arbitrary placeholder. Each resolver logs the transition
 * exactly once in either direction, the same one-time-per-transition gate {@code
 * ApotheneumColor}'s equivalent uses and for the same reason (see that class's {@code
 * noteResolution} javadoc): a log that never announces recovery is indistinguishable from "still
 * broken right now".
 */
public class ApotheneumGradient extends LXComponent implements LXOscComponent {

  /** Engine path; parameters live at {@code /lx/apotheneumGradient/*}. */
  public static final String PATH = "apotheneumGradient";

  private static final double AZIMUTH_MAX_DEGREES = 360;
  private static final double ELEVATION_MAX_DEGREES = 90;

  /** See the class javadoc's fallback section for why +90 (straight up) is the safe default. */
  private static final double DEFAULT_AZIMUTH_DEGREES = 0;
  private static final double DEFAULT_ELEVATION_DEGREES = 90;

  /** Full gradient -- matches this class's only behavior before {@link #spread} existed, so a
   * project with no {@code ApotheneumGradient} (or one saved before this control existed) renders
   * exactly as it did before. */
  private static final double DEFAULT_SPREAD = 1;

  /**
   * The engine's registered {@code ApotheneumGradient}, or {@code null} if
   * {@code apotheneum.doved.ApotheneumColorPlugin} did not register one. Resolved fresh every
   * call, exactly as {@link ApotheneumColor#get(LX)} is -- see that method's javadoc.
   */
  public static ApotheneumGradient get(LX lx) {
    final LXComponent child = lx.engine.getChild(PATH);
    if (child instanceof ApotheneumGradient) {
      return (ApotheneumGradient) child;
    }
    return mirrorOfStale(lx, child);
  }

  /** The shadow instance {@link #mirrorOfStale} keeps in step with a stale registration. */
  private static ApotheneumGradient staleMirror = null;

  /**
   * Reads a stale registration -- the component left behind when the package is reinstalled
   * over a running Chromatik -- through a locally-built mirror. See {@code
   * ApotheneumColor.mirrorOfStale} for the full reasoning: the new classes load under a new
   * classloader while the engine keeps the old instance, so {@code instanceof} fails, and the
   * way across that boundary is {@link LXComponent#getParameter(String)}, whose {@link
   * heronarts.lx.parameter.LXParameter} return type belongs to LX rather than to this package
   * and is therefore the same type on both sides. The registration is never touched.
   */
  private static ApotheneumGradient mirrorOfStale(LX lx, LXComponent child) {
    if ((child == null)
      || !child.getClass().getName().equals(ApotheneumGradient.class.getName())) {
      return null;
    }
    if (staleMirror == null) {
      staleMirror = new ApotheneumGradient(lx);
    }
    copyParameter(child, "azimuth", staleMirror.azimuth);
    copyParameter(child, "elevation", staleMirror.elevation);
    copyParameter(child, "spread", staleMirror.spread);
    return staleMirror;
  }

  private static void copyParameter(LXComponent from, String path, CompoundParameter to) {
    final LXParameter source = from.getParameter(path);
    if (source != null) {
      to.setValue(source.getValue());
    }
  }

  /**
   * Compass heading of the gradient's direction, measured in the model's horizontal (X/Z)
   * plane. 0-360&#176; rather than a bipolar range: a heading has no natural "center", unlike
   * {@link #elevation}, which does (0 = horizontal).
   */
  public final CompoundParameter azimuth = new CompoundParameter("Azimuth", 0, 0, AZIMUTH_MAX_DEGREES)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription(
      "Compass heading of the gradient's direction through the installation's horizontal "
      + "(X/Z) plane");

  /**
   * Tilt of the gradient's direction away from the horizontal plane. &#177;90&#176; is straight
   * up/down (a vertical gradient, identical at every azimuth); 0&#176; is fully horizontal, at
   * the heading {@link #azimuth} names.
   */
  public final CompoundParameter elevation =
    new CompoundParameter("Elevation", DEFAULT_ELEVATION_DEGREES, -ELEVATION_MAX_DEGREES, ELEVATION_MAX_DEGREES)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription(
      "Tilt of the gradient's direction away from horizontal -- +-90 is a vertical gradient, "
      + "0 is horizontal at the Azimuth heading");

  /**
   * How much of the projected {@code [0, 1]} gradient position survives, {@code 1} the full
   * gradient down to {@code 0} flat -- see the class javadoc's collapsing-to-flat section for
   * why this is continuous rather than a boolean, and why it narrows toward the midpoint of
   * primary/secondary rather than toward primary alone. Applied via {@link #applySpread},
   * alongside this class's other per-point projection math.
   */
  public final CompoundParameter spread = new CompoundParameter("Spread", DEFAULT_SPREAD, 0, 1)
    .setDescription(
      "How much of the gradient survives -- 1 is the full gradient, 0 collapses every surface "
      + "to one flat color (the midpoint blend of primary and secondary)");

  public ApotheneumGradient(LX lx) {
    super(lx, "Apotheneum Gradient");
    addParameter("azimuth", this.azimuth);
    addParameter("elevation", this.elevation);
    addParameter("spread", this.spread);
  }

  /**
   * Which of "no instance" / "an instance" this process last logged for each resolver -- see
   * the class javadoc's fallback section. Independent per resolver (azimuth vs. elevation),
   * matching how {@link ApotheneumColor#resolvePrimaryOrNeutral}/{@code
   * resolveSecondaryOrNeutral} each track their own transition rather than sharing one flag.
   */
  private enum ResolutionState { UNKNOWN, MISSING, PRESENT }

  private static ResolutionState loggedAzimuthState = ResolutionState.UNKNOWN;
  private static ResolutionState loggedElevationState = ResolutionState.UNKNOWN;
  private static ResolutionState loggedSpreadState = ResolutionState.UNKNOWN;

  /** {@link #azimuth}'s value, or {@link #DEFAULT_AZIMUTH_DEGREES} with a one-time-per-transition
   * log line if {@code gradient} is {@code null}. */
  public static double azimuthOrDefault(ApotheneumGradient gradient) {
    if (gradient == null) {
      if (loggedAzimuthState != ResolutionState.MISSING) {
        loggedAzimuthState = ResolutionState.MISSING;
        LX.log(
          "[APOTHENEUM] ApotheneumGradient: no instance registered on the engine -- "
          + "GradientMultiplyEffect is resolving the default direction (azimuth "
          + DEFAULT_AZIMUTH_DEGREES + ", elevation " + DEFAULT_ELEVATION_DEGREES + "). Expected "
          + "only if apotheneum.doved.ApotheneumColorPlugin failed to load; check the log above "
          + "this line for why.");
      }
      return DEFAULT_AZIMUTH_DEGREES;
    }
    if (loggedAzimuthState != ResolutionState.PRESENT) {
      loggedAzimuthState = ResolutionState.PRESENT;
      LX.log(
        "[APOTHENEUM] ApotheneumGradient: resolving azimuth from the instance at "
        + gradient.getPath() + " -- GradientMultiplyEffect is live again.");
    }
    return gradient.azimuth.getValue();
  }

  /** {@link #elevation}'s value, or {@link #DEFAULT_ELEVATION_DEGREES} with a
   * one-time-per-transition log line if {@code gradient} is {@code null}. */
  public static double elevationOrDefault(ApotheneumGradient gradient) {
    if (gradient == null) {
      if (loggedElevationState != ResolutionState.MISSING) {
        loggedElevationState = ResolutionState.MISSING;
        LX.log(
          "[APOTHENEUM] ApotheneumGradient: no instance registered on the engine -- "
          + "GradientMultiplyEffect is resolving the default elevation ("
          + DEFAULT_ELEVATION_DEGREES + "). Expected only if "
          + "apotheneum.doved.ApotheneumColorPlugin failed to load; check the log above this "
          + "line for why.");
      }
      return DEFAULT_ELEVATION_DEGREES;
    }
    if (loggedElevationState != ResolutionState.PRESENT) {
      loggedElevationState = ResolutionState.PRESENT;
      LX.log(
        "[APOTHENEUM] ApotheneumGradient: resolving elevation from the instance at "
        + gradient.getPath() + " -- GradientMultiplyEffect is live again.");
    }
    return gradient.elevation.getValue();
  }

  /** {@link #spread}'s value, or {@link #DEFAULT_SPREAD} (full gradient, matching this class's
   * pre-{@code spread} behavior) with a one-time-per-transition log line if {@code gradient} is
   * {@code null}. */
  public static double spreadOrDefault(ApotheneumGradient gradient) {
    if (gradient == null) {
      if (loggedSpreadState != ResolutionState.MISSING) {
        loggedSpreadState = ResolutionState.MISSING;
        LX.log(
          "[APOTHENEUM] ApotheneumGradient: no instance registered on the engine -- "
          + "GradientMultiplyEffect is resolving the default spread ("
          + DEFAULT_SPREAD + "). Expected only if apotheneum.doved.ApotheneumColorPlugin failed "
          + "to load; check the log above this line for why.");
      }
      return DEFAULT_SPREAD;
    }
    if (loggedSpreadState != ResolutionState.PRESENT) {
      loggedSpreadState = ResolutionState.PRESENT;
      LX.log(
        "[APOTHENEUM] ApotheneumGradient: resolving spread from the instance at "
        + gradient.getPath() + " -- GradientMultiplyEffect is live again.");
    }
    return gradient.spread.getValue();
  }

  /**
   * The X component of the unit direction vector for the given azimuth/elevation, in the
   * model's real-world coordinate space. Static and parameterised by plain degrees (not an
   * instance method reading {@link #azimuth}/{@link #elevation} directly) so a caller that
   * already resolved the null-instance fallback via {@link #azimuthOrDefault}/{@link
   * #elevationOrDefault} can derive a direction without a second null check, and so this math is
   * unit-testable with no {@code LX}/{@code LXComponent} construction at all.
   */
  public static double directionX(double azimuthDegrees, double elevationDegrees) {
    return Math.cos(Math.toRadians(elevationDegrees)) * Math.sin(Math.toRadians(azimuthDegrees));
  }

  /** The Y (vertical) component -- independent of azimuth, since a heading is meaningless once
   * pointed straight up or down. */
  public static double directionY(double elevationDegrees) {
    return Math.sin(Math.toRadians(elevationDegrees));
  }

  /** The Z component; see {@link #directionX}. */
  public static double directionZ(double azimuthDegrees, double elevationDegrees) {
    return Math.cos(Math.toRadians(elevationDegrees)) * Math.cos(Math.toRadians(azimuthDegrees));
  }

  /**
   * The minimum value {@code dx*x + dy*y + dz*z} takes anywhere in {@code model}'s axis-aligned
   * bounding box -- see the class javadoc's normalization section for why this is computed
   * directly from the box's six bounds rather than scanned over points. {@code dx}/{@code
   * dy}/{@code dz} need not be literally unit length for this to be correct, but should be
   * ({@link #directionX}/{@link #directionY}/{@link #directionZ} already are) so the result is a
   * true distance along that direction, matching {@link #project}.
   */
  public static double projectedMin(LXModel model, double dx, double dy, double dz) {
    return Math.min(dx * model.xMin, dx * model.xMax)
      + Math.min(dy * model.yMin, dy * model.yMax)
      + Math.min(dz * model.zMin, dz * model.zMax);
  }

  /** The corresponding maximum -- see {@link #projectedMin}. */
  public static double projectedMax(LXModel model, double dx, double dy, double dz) {
    return Math.max(dx * model.xMin, dx * model.xMax)
      + Math.max(dy * model.yMin, dy * model.yMax)
      + Math.max(dz * model.zMin, dz * model.zMax);
  }

  /**
   * The tag {@code Apotheneum.initialize} itself keys off to decide whether the installation is
   * present ({@code model.sub("Apotheneum")}), reused here so this class scopes its gradient to
   * exactly the geometry {@code Apotheneum} considers the installation.
   */
  private static final String APOTHENEUM_TAG = "Apotheneum";

  /**
   * {@link #projectedMin} over <em>Apotheneum's own</em> geometry rather than over whatever else
   * the running project's top-level model happens to contain.
   *
   * <p>{@code GradientMultiplyEffect} passed {@code lx.getModel()} straight into {@link
   * #projectedMin}/{@link #projectedMax} until 2026-08-30, which reproduced precisely the failure
   * this class's own javadoc argues against: an unrelated fixture extending the project's
   * bounding box would silently widen the span the gradient normalizes against, so the
   * installation would occupy only part of {@code [0, 1]} and the gradient's endpoint colours
   * would stop appearing on it at all -- compressed rather than clipped, and with nothing on
   * screen naming the added fixture as the cause. The four passes underneath only ever transform
   * the four Apotheneum surfaces, so the span they normalize against has to be those surfaces'
   * span too.
   *
   * <p>Falls back to {@code model} itself when nothing carries the tag, which keeps every
   * geometry-free unit test (a plain {@code GridModel}, no Apotheneum fixture) working against
   * the same span it always did. On the real single-fixture rig the tagged submodel and the
   * top-level model have identical bounds, so this is output-identical there -- the fix is
   * insurance against a second fixture, not a change to what the installation renders today.
   *
   * <p>{@code LXModel.sub(String)} is a lookup in a prebuilt {@code subDict} map returning a
   * cached list, not a scan or a fresh collection, so resolving this once per frame allocates
   * nothing.
   */
  public static double apotheneumProjectedMin(LXModel model, double dx, double dy, double dz) {
    final List<LXModel> scope = model.sub(APOTHENEUM_TAG);
    if (scope.isEmpty()) {
      return projectedMin(model, dx, dy, dz);
    }
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < scope.size(); ++i) {
      min = Math.min(min, projectedMin(scope.get(i), dx, dy, dz));
    }
    return min;
  }

  /** The corresponding maximum -- see {@link #apotheneumProjectedMin}. */
  public static double apotheneumProjectedMax(LXModel model, double dx, double dy, double dz) {
    final List<LXModel> scope = model.sub(APOTHENEUM_TAG);
    if (scope.isEmpty()) {
      return projectedMax(model, dx, dy, dz);
    }
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < scope.size(); ++i) {
      max = Math.max(max, projectedMax(scope.get(i), dx, dy, dz));
    }
    return max;
  }

  /** {@code point}'s real-world position, projected onto the direction {@code (dx, dy, dz)} --
   * pair with {@link #projectedMin}/{@link #projectedMax} (same direction) and {@link #normalize}
   * to get this point's gradient position in {@code [0, 1]}. */
  public static double project(LXPoint point, double dx, double dy, double dz) {
    return point.x * dx + point.y * dy + point.z * dz;
  }

  /**
   * Normalizes {@code projected} (a {@link #project} result) into {@code [0, 1]} against {@code
   * [min, max]} (a {@link #projectedMin}/{@link #projectedMax} pair for the same direction),
   * clamped. A degenerate (zero-or-negative-span) extent resolves to the midpoint rather than
   * dividing by zero -- the same "no usable span, pick the middle" convention the old per-surface
   * raster path used for a single-column/row surface.
   */
  public static double normalize(double projected, double min, double max) {
    final double span = max - min;
    if (span <= 0) {
      return 0.5;
    }
    return LXUtils.clamp((projected - min) / span, 0, 1);
  }

  /**
   * Rescales a normalized gradient position {@code t} (a {@link #normalize} result) toward
   * {@code 0.5} by {@code spread} -- see the class javadoc's collapsing-to-flat section for why
   * this narrows toward the midpoint rather than toward 0 (primary). At {@code spread = 1} this
   * is the identity ({@code t} unchanged, the full gradient); at {@code spread = 0} every input
   * maps to exactly {@code 0.5} regardless of {@code t} (flat, the primary/secondary midpoint
   * blend); every value in between narrows the effective range symmetrically around the center,
   * continuously -- there is no value of {@code spread} at which this formula is discontinuous
   * in either {@code spread} or {@code t}, so a performer sweeping the knob down sees the
   * gradient's band tighten smoothly rather than jump. {@code t} is assumed to already be in
   * {@code [0, 1]} (a {@link #normalize} result); the result is clamped defensively in case a
   * modulation source pushes {@code spread} fractionally outside {@code [0, 1]}, not because the
   * formula itself can leave that range for an in-range {@code spread}.
   */
  public static double applySpread(double t, double spread) {
    return LXUtils.clamp(0.5 + spread * (t - 0.5), 0, 1);
  }

}
