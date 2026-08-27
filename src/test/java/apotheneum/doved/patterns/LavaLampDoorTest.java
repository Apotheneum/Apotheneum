package apotheneum.doved.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import apotheneum.Apotheneum;
import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXModel;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.view.LXViewDefinition;

/**
 * {@link LavaLamp} against the real installation geometry, which is the only place the two
 * things asserted here exist at all: the doorways, which are part of the vessel rather than a
 * mask over it and which the lamp must keep its blobs out of, and the exterior and interior
 * surfaces the render toggles select between.
 *
 * Everything lives in one test method on purpose. {@code Apotheneum} holds its model state
 * statically and {@code initialize()} returns early once it has run, so a second {@code LX}
 * in the same JVM would silently keep the first one's listener — one instance per process is
 * the rule, and one method is the simplest way to honour it. That one instance runs the
 * containment assertions at the default Size and at the top of its range, because the
 * headroom a blob needs above a lintel scales with Size and the default leaves rows to spare:
 * a solver that lifts a blob higher than the panel allows and lets the ceiling clamp put it
 * straight back is invisible at Size 5 and puts the blob most of a door-height into the
 * opening at Size 12. At each of those it sweeps Threshold across its range as well, since
 * Threshold decides how far out the blob a viewer actually sees extends — see
 * {@link #assertContainmentHoldsAcrossThresholds}. Disposal still goes through
 * {@link HeadlessLxTest#track}, since an undisposed instance strands a non-daemon MIDI thread.
 */
public class LavaLampDoorTest extends HeadlessLxTest {

  private static final int FRAMES = 1200;
  /**
   * Frames per Threshold in the sweep below. Shorter than a full run because the sweep is
   * about the contour the knob selects rather than about how long the lamp survives, and the
   * non-vacuity guard scales with it — still several hundred approaches to a doorway apiece.
   */
  private static final int SWEEP_FRAMES = 400;
  /**
   * The Threshold settings the containment invariant is asserted at: both ends of the
   * parameter's range and its default. The iso level is what decides how far out a blob's
   * drawn contour actually falls, so a containment radius that is right at one setting is a
   * knob turn away from being wrong — the low end is where the contour is widest and the
   * doorway had been biting into it.
   */
  private static final double[] THRESHOLD_SWEEP = { .12, .42, .8 };
  private static final double FIXED_DELTA_MS = 1000. / 60;
  /** Rows of slack: a blob left resting against a wall sits exactly on the boundary. */
  private static final double TOLERANCE = 1e-6;
  /**
   * How close a blob has to be to the doorway bound holding it up for the frame to count as
   * one where the containment machinery is what is keeping it out of the opening. A row, so
   * a blob merely passing a doorway at speed does not count while one settled on a lintel or
   * pressed against a jamb does.
   */
  private static final double RESTING_ROWS = 1;
  /**
   * The body size the lamp has to be holding before the Size knob goes up, as a multiple of
   * the nominal radius. Just under the 2.5 a merge will not exceed, so the reproduction turns
   * the knob on a lamp holding about the largest body it ever holds — which is the operator
   * action that makes the geometry impossible and the case the defect lived in.
   */
  private static final double BIG_BODY_SCALE = 2.3;
  /**
   * How far past the top of the panel resting the largest body on a lintel has to fall before
   * the maximum-Size run counts as having reached the conflict at all. Five rows, comfortably
   * past the two or so a freshly seeded lamp at that Size reaches on its own and well short of
   * the ten the knob turn produces once a big body is standing.
   */
  private static final double CONFLICT_ROWS = 5;
  /**
   * The blob seed to run on. Pinned because the reproduction turns on how large a body
   * happens to be standing when the knob moves, which swung the conflict between six rows
   * short and twelve rows over across unseeded runs — a regression test has to fail for a
   * reason someone else can reproduce rather than for one run's luck.
   */
  private static final String SEED = "7";
  /** Frames to let the lamp fill before the toggles are meaningful to test. */
  private static final int TOGGLE_WARMUP_FRAMES = 30;
  /** Frames to run behind a sentinel prefill when checking what a view lets through. */
  private static final int VIEW_FRAMES = 20;
  /**
   * A colour the pattern never writes, prefilled so an untouched point is distinguishable
   * from one the pattern deliberately painted black.
   */
  private static final int SENTINEL = 0x7f123456;

  @Test
  void lavaLampOnTheRealInstallationGeometry() throws IOException {
    final LX lx = newApotheneumLx();

    // One door profile per lamp covers both surfaces of a chamber. That holds because
    // exterior and interior are instances of one Orientation class whose available() is a
    // pure function of the column index — assert it rather than trust it, since the whole
    // containment model is built on a single profile.
    assertSurfacesAgreeOnDoors(Apotheneum.cube.exterior, Apotheneum.cube.interior);
    assertSurfacesAgreeOnDoors(Apotheneum.cylinder.exterior, Apotheneum.cylinder.interior);

    final String previousSeed = System.getProperty(LavaLamp.RENDER_SEED_PROPERTY);
    System.setProperty(LavaLamp.RENDER_SEED_PROPERTY, SEED);
    final LavaLamp lavaLamp;
    try {
      lavaLamp = new LavaLamp(lx);
    } finally {
      if (previousSeed == null) {
        System.clearProperty(LavaLamp.RENDER_SEED_PROPERTY);
      } else {
        System.setProperty(LavaLamp.RENDER_SEED_PROPERTY, previousSeed);
      }
    }
    // Speed 3 is what the review render runs at: more trips past the openings per second,
    // so the run covers more approaches than it would at the default.
    lavaLamp.speed.setValue(3);
    final LXChannel channel = lx.engine.mixer.addChannel(new LXPattern[] { lavaLamp });
    channel.fader.setValue(1);
    lx.engine.setFixedDeltaMs(FIXED_DELTA_MS);

    // Render toggles and view masking first, while the lamp is still at its defaults and the
    // door runs below have not driven it to the extremes.
    assertRenderTogglesGateOutput(lx, lavaLamp);
    assertNarrowingTheViewClearsWhatItLeavesBehind(lx, lavaLamp);
    assertWideningTheViewResumesFullOutput(lx, lavaLamp);
    assertViewOnlyWritesItsSelectedPoints(lx, lavaLamp);
    // Back to the whole installation for the door runs below.
    lavaLamp.view.setValue(null);
    lx.engine.run();

    // At the default the doorways and the top of the panel never contend, and this run
    // asserts that: it is the control, and it is what says the conflict branch below cannot
    // reach the output anyone actually looks at.
    final double defaultThreshold = lavaLamp.threshold.getValue();
    final double defaultShortfall = assertBlobsStayOutOfTheDoorways(
      lx, lavaLamp, "the default Size", drawnRadiusScale(defaultThreshold), FRAMES
    );
    assertTrue(
      defaultShortfall < 0,
      "the lamp held a body needing " + defaultShortfall + " rows more headroom above a "
        + "lintel than the panel has at the default Size, so this run no longer isolates the "
        + "ordinary case from the conflict one"
    );

    assertContainmentHoldsAcrossThresholds(lx, lavaLamp, "the default Size");
    lavaLamp.threshold.setValue(defaultThreshold);

    // Now the reproduction, which is an operator action rather than a starting state: turn
    // the lamp up on a running one. Size multiplies every standing blob's radius the instant
    // it moves, so waiting until a big body is up and then going to Size 12 leaves a lamp
    // holding a 27-row body — and 27 rows of headroom above an 11-row lintel do not exist on
    // a 45-row cube or a 43-row cylinder. Reseeding at Size 12 instead hides the whole thing:
    // a fresh lamp there holds only about six blobs, they merge to a radius of roughly 21
    // rows, and the two bounds come within a row or two of colliding without ever doing it.
    // Nothing reachable at a fixed setting gets there, on any Size — the conflict is a
    // property of changing one.
    advanceUntilABigBodyStands(lx, lavaLamp);
    lavaLamp.size.setValue(lavaLamp.size.range.max);
    lavaLamp.volume.setValue(lavaLamp.volume.range.max);
    // Coalesce with them, since it is what decides how large a body gets: it sets both how
    // readily two blobs fuse and how fast an oversized one necks apart again.
    lavaLamp.coalesce.setValue(lavaLamp.coalesce.range.max);
    final double maximumShortfall = assertBlobsStayOutOfTheDoorways(
      lx, lavaLamp, "the maximum Size", drawnRadiusScale(defaultThreshold), FRAMES
    );

    // The other half of the control: this run has to actually reach the case the default one
    // cannot, or it is only a slower copy of it. Rows, not frames — the conflict is deepest
    // right after the knob moves and eases as Volume trims the bodies back, so its depth is
    // the honest measure of whether the run got there and a frame count is not.
    assertTrue(
      maximumShortfall > CONFLICT_ROWS,
      "the largest body the lamp held would have cleared the tallest lintel with "
        + (-maximumShortfall) + " rows to spare at the maximum Size, so this run never "
        + "reached the case the default Size cannot"
    );

    // And the two knobs together, which is the harshest geometry the pattern offers: bodies
    // at the maximum Size drawn at the widest contour Threshold selects.
    assertContainmentHoldsAcrossThresholds(lx, lavaLamp, "the maximum Size");
  }

  /**
   * The containment invariant at each end of the Threshold range and at its default, measured
   * against the contour that setting actually draws.
   *
   * Threshold is the iso level of the metaball field, so it is what decides where a blob's
   * visible surface falls: solving Wyvill's {@code (1 - d^2/R^2)^3 = iso} out to
   * {@code R = 2r} puts a lone blob's contour at 1.42 nominal radii at the bottom of the
   * range and .54 at the top. A containment radius fixed at one nominal radius is right at
   * the default and nowhere else, and at the low end it leaves nearly half the visible blob
   * outside what the solver keeps clear — a doorway-shaped bite out of the lava, which is
   * precisely what containment is here to prevent.
   *
   * The invariant is asserted from the very first frame after the knob moves rather than
   * after a settling period. Containment is unconditional by construction — a blob the pushes
   * cannot place is put on {@code doorSupportHeight}, which clears every opening by
   * definition — so widening every blob's contour by forty percent in one frame is a case it
   * has to answer immediately, not eventually.
   */
  private void assertContainmentHoldsAcrossThresholds(LX lx, LavaLamp lavaLamp, String size) {
    for (double iso : THRESHOLD_SWEEP) {
      lavaLamp.threshold.setValue(iso);
      final double resolved = lavaLamp.threshold.getValue();
      assertEquals(
        iso,
        resolved,
        "Threshold clamped " + iso + " to " + resolved + ", so this step of the sweep is not "
          + "testing the setting it names"
      );
      assertBlobsStayOutOfTheDoorways(
        lx, lavaLamp, size + " at Threshold " + iso, drawnRadiusScale(resolved), SWEEP_FRAMES
      );
    }
  }

  /**
   * Where a lone blob's drawn contour falls, in nominal radii, at a given iso level.
   *
   * Deliberately a restatement of the geometry rather than a call to the pattern's own
   * {@code drawnRadiusScale}. The whole point of measuring penetration against this figure is
   * that it is arrived at independently: were the assertions to measure with whatever radius
   * the solver happens to be using, they would report a clean run however wrong that radius
   * was — which is how a fixed multiplier survived here in the first place.
   *
   * The falloff is Wyvill's, {@code (1 - d^2/R^2)^3}, reaching zero at twice the nominal
   * radius. Setting that equal to the iso level and solving for d gives
   * {@code d/r = 2 * sqrt(1 - cbrt(iso))}.
   */
  private static double drawnRadiusScale(double isoLevel) {
    return 2 * Math.sqrt(1 - Math.cbrt(isoLevel));
  }

  /**
   * Runs on at the default Size until the lamp is holding a body of {@link #BIG_BODY_SCALE},
   * so the Size knob turns on a full lamp rather than at whatever the previous run happened
   * to end on. Containment is asserted throughout, since these are ordinary frames.
   */
  private void advanceUntilABigBodyStands(LX lx, LavaLamp lavaLamp) {
    for (int frame = 0; frame < FRAMES; ++frame) {
      lx.engine.run();
      assertContained(
        lavaLamp, frame, "the wait for a big body", drawnRadiusScale(lavaLamp.threshold.getValue())
      );
      if (lavaLamp.largestBlobScale() >= BIG_BODY_SCALE) {
        return;
      }
    }
    fail(
      "no body reached " + BIG_BODY_SCALE + " times the nominal radius within " + FRAMES
        + " frames, so the maximum-Size run below would not start from a full lamp"
    );
  }

  /**
   * Runs the lamp for {@link #FRAMES} frames asserting containment every frame, and returns
   * the worst {@link LavaLamp#lintelHeadroomShortfall} seen over the run — how far resting
   * the largest body it held on a lintel would have carried it past the top of the panel,
   * which is what tells the two runs apart.
   */
  private double assertBlobsStayOutOfTheDoorways(
    LX lx,
    LavaLamp lavaLamp,
    String phase,
    double drawnScale,
    int frames
  ) {
    int framesRestingOnDoors = 0;
    double worstShortfall = Double.NEGATIVE_INFINITY;
    for (int frame = 0; frame < frames; ++frame) {
      lx.engine.run();
      assertContained(lavaLamp, frame, phase, drawnScale);
      if (lavaLamp.doorClearance(drawnScale) <= RESTING_ROWS) {
        ++framesRestingOnDoors;
      }
      worstShortfall = Math.max(worstShortfall, lavaLamp.lintelHeadroomShortfall(drawnScale));
    }

    // Without this the run could pass by never bringing a blob near a doorway. Clearance is
    // the signed distance from the very bound the penetration assertion tests, so a frame
    // counted here is one where a blob's disc was within a row of an opening and the
    // containment machinery is the only reason it was not in one. A count of centres over an
    // opening would not do: an opening is ten columns of a hundred and twenty, and at large
    // Size a handful of very wide bodies put their rims in one constantly and their centres
    // over one only rarely, so that guard goes vacuous exactly where the geometry is tightest.
    assertTrue(
      framesRestingOnDoors > frames / 10,
      "a blob was within " + RESTING_ROWS + " row of a doorway bound on only "
        + framesRestingOnDoors + " of " + frames + " frames at " + phase
        + ", so the containment assertions above were close to vacuous"
    );
    return worstShortfall;
  }

  /** The containment invariant itself: no blob overlapping an opening, by any measure. */
  private void assertContained(
    LavaLamp lavaLamp,
    int frame,
    String phase,
    double drawnScale
  ) {
    assertEquals(
      0,
      lavaLamp.blobCentersInsideDoors(),
      "blob centre inside a door opening on frame " + frame + " at " + phase
    );
    final double penetration = lavaLamp.doorPenetration(drawnScale);
    assertTrue(
      penetration <= TOLERANCE,
      // Penetration is measured against the drawn disc, not the centre: a blob whose centre
      // is well clear of an opening still reports the depth its rim reaches into one, and a
      // blob over a lintel reports how far short of resting on it it is.
      "blob reached " + penetration + " rows into a door opening on frame " + frame
        + " at " + phase
    );
  }

  /**
   * Each render toggle turns its surfaces off and on again.
   *
   * A pattern-level model view cannot do this job — the lamp writes through the global
   * Apotheneum geometry by point index, so a view never narrows what it touches — which is
   * why these are parameters and not a view. What matters here is that switching one off
   * really goes dark rather than leaving the last frame standing on those LEDs, so every
   * check is made after a fresh frame and looks at the whole surface.
   */
  private void assertRenderTogglesGateOutput(LX lx, LavaLamp lavaLamp) {
    // A few frames first: a lamp that has only just filled may not yet light every surface,
    // and "off" would then be indistinguishable from "not started".
    for (int frame = 0; frame < TOGGLE_WARMUP_FRAMES; ++frame) {
      lx.engine.run();
    }
    assertAllOn(lx, lavaLamp);

    assertToggleGates(lx, lavaLamp, lavaLamp.renderToCube, Apotheneum.cube.exterior, "cube");
    assertToggleGates(
      lx, lavaLamp, lavaLamp.renderToCylinder, Apotheneum.cylinder.exterior, "cylinder"
    );
    assertToggleGates(
      lx, lavaLamp, lavaLamp.renderToExterior, Apotheneum.cube.exterior, "exterior"
    );
    assertToggleGates(
      lx, lavaLamp, lavaLamp.renderToInterior, Apotheneum.cube.interior, "interior"
    );
  }

  /**
   * Turns one toggle off, asserts its surface goes fully dark and that the surfaces the other
   * axis selects are untouched, then turns it back on and asserts the light returns.
   */
  private void assertToggleGates(
    LX lx,
    LavaLamp lavaLamp,
    BooleanParameter toggle,
    Apotheneum.Orientation gated,
    String name
  ) {
    // The surface on the other side of both axes, which this toggle must not affect.
    final Apotheneum.Orientation untouched = (gated == Apotheneum.cube.exterior)
      ? Apotheneum.cylinder.interior
      : Apotheneum.cube.exterior;

    toggle.setValue(false);
    lx.engine.run();
    assertEquals(
      0,
      litPoints(lavaLamp, gated),
      "points still lit on the " + name + " surface with its toggle off, so switching it off "
        + "leaves the last frame frozen there rather than going dark"
    );
    assertTrue(
      litPoints(lavaLamp, untouched) > 0,
      "turning the " + name + " toggle off also darkened a surface it does not select"
    );

    toggle.setValue(true);
    lx.engine.run();
    assertTrue(
      litPoints(lavaLamp, gated) > 0,
      "no points lit on the " + name + " surface after its toggle went back on"
    );
  }

  /** All four toggles on, which is the default, lights every surface. */
  private void assertAllOn(LX lx, LavaLamp lavaLamp) {
    lx.engine.run();
    for (Apotheneum.Component component :
      new Apotheneum.Component[] { Apotheneum.cube, Apotheneum.cylinder }) {
      for (Apotheneum.Orientation orientation : component.orientations()) {
        assertTrue(
          litPoints(lavaLamp, orientation) > 0,
          "a surface was dark with every render toggle on, so this test cannot tell a "
            + "toggle working from the lamp simply not reaching that surface"
        );
      }
    }
  }

  /** How many of one surface's points the pattern painted something other than black. */
  private int litPoints(LavaLamp lavaLamp, Apotheneum.Orientation orientation) {
    final int[] colors = lavaLamp.getColors();
    int lit = 0;
    // Walked the way the pattern writes it: only the rows a column actually has, so a door
    // column's missing pixels are never counted as dark.
    for (int x = 0; x < orientation.width(); ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        if ((colors[orientation.point(x, y).index] & LXColor.RGB_MASK) != 0) {
          ++lit;
        }
      }
    }
    return lit;
  }

  /**
   * Narrowing the view blacks out what the view used to cover.
   *
   * This is the stale-pixel case, and the one most likely to be wrong: the per-frame clear
   * only reaches the current view, so points the view has just stopped covering would keep
   * whatever they were last painted forever unless something clears them at the moment of
   * the change. Lighting the whole installation first is what gives the test something to
   * find — asserting black on points that were never lit would pass against no
   * implementation at all.
   */
  private void assertNarrowingTheViewClearsWhatItLeavesBehind(LX lx, LavaLamp lavaLamp) {
    lavaLamp.view.setValue(null);
    for (int frame = 0; frame < TOGGLE_WARMUP_FRAMES; ++frame) {
      lx.engine.run();
    }
    // Frames from here go through the pattern's own loop rather than the engine's. In the
    // normal single-pattern path the engine hands the pattern the channel buffer and resets
    // it around the frame, which blacks the cube for reasons that have nothing to do with
    // this pattern honouring its view — running the pattern directly is what makes the
    // assertion below about the pattern's own behaviour.
    lavaLamp.loop(FIXED_DELTA_MS);
    assertTrue(
      litPoints(lavaLamp, Apotheneum.cube.exterior) > 0,
      "the cube exterior was not lit on the full model, so the narrowing below would prove "
        + "nothing"
    );

    lavaLamp.view.setValue(cylinderExteriorView(lx));
    lavaLamp.loop(FIXED_DELTA_MS);
    // Without this the test has a hole: a view that failed to resolve leaves the pattern
    // unmasked, its per-frame clear covers the whole buffer, and the cube goes black anyway.
    assertTrue(
      lavaLamp.getModelView().size < lx.getModel().size,
      "the pattern view did not actually narrow, so the assertion below would pass on the "
        + "full-model clear rather than on anything to do with views"
    );
    assertEquals(
      0,
      litPoints(lavaLamp, Apotheneum.cube.exterior),
      "the cube exterior stayed lit after the view narrowed to the cylinder exterior, so "
        + "points the view dropped are frozen on the last frame that drew them"
    );
  }

  /**
   * Widening back to the whole installation resumes full output, with nothing stuck.
   *
   * The mirror of the narrowing case and the easier one to get wrong in the other direction:
   * nothing needs clearing when a view grows, because every point is written again from the
   * next frame on, so an implementation that over-clears here or one that never notices the
   * view grew both show up as surfaces that stay dark. The sentinel is what proves the point
   * was actually written rather than merely left black.
   */
  private void assertWideningTheViewResumesFullOutput(LX lx, LavaLamp lavaLamp) {
    lavaLamp.view.setValue(cylinderExteriorView(lx));
    lavaLamp.loop(FIXED_DELTA_MS);
    Arrays.fill(lavaLamp.getColors(), SENTINEL);

    lavaLamp.view.setValue(null);
    lavaLamp.loop(FIXED_DELTA_MS);
    assertSame(
      lx.getModel(),
      lavaLamp.getModelView(),
      "the pattern view did not widen back to the whole model"
    );
    for (Apotheneum.Component component :
      new Apotheneum.Component[] { Apotheneum.cube, Apotheneum.cylinder }) {
      for (Apotheneum.Orientation orientation : component.orientations()) {
        assertNoSentinelLeft(lavaLamp, orientation);
      }
    }

    for (int frame = 0; frame < TOGGLE_WARMUP_FRAMES; ++frame) {
      lavaLamp.loop(FIXED_DELTA_MS);
    }
    assertTrue(
      litPoints(lavaLamp, Apotheneum.cube.exterior) > 0,
      "the cube exterior stayed dark after the view widened back to the whole installation"
    );
  }

  /** No point of a surface still holds the sentinel, so all of them were written. */
  private void assertNoSentinelLeft(LavaLamp lavaLamp, Apotheneum.Orientation orientation) {
    final int[] colors = lavaLamp.getColors();
    for (int x = 0; x < orientation.width(); ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        assertNotEquals(
          SENTINEL,
          colors[orientation.point(x, y).index],
          "a point went unwritten after the view widened, at column " + x + " row " + y
        );
      }
    }
  }

  /**
   * With a view selected, nothing outside it is written at all.
   *
   * A sentinel prefill rather than a check for black, because black is what an unmasked
   * pattern writes on an empty cell: it cannot tell "never touched" from "touched and
   * painted black", and the whole question here is whether the pattern reaches outside its
   * view. Frames go through the pattern's own loop so the prefill survives into them.
   */
  private void assertViewOnlyWritesItsSelectedPoints(LX lx, LavaLamp lavaLamp) {
    lavaLamp.view.setValue(cylinderExteriorView(lx));
    lx.engine.run();
    // The mask is rebuilt only when getModelView() returns a different instance, so that
    // early-return is only free if an unchanged view keeps handing back the same one. Assert
    // it rather than assume it: were it to churn, the pattern would still be correct — it
    // would rebuild and clear — but it would allocate every frame and flash black doing it.
    final LXModel resolvedView = lavaLamp.getModelView();
    Arrays.fill(lavaLamp.getColors(), SENTINEL);
    for (int frame = 0; frame < VIEW_FRAMES; ++frame) {
      lavaLamp.loop(FIXED_DELTA_MS);
      assertSame(
        resolvedView,
        lavaLamp.getModelView(),
        "getModelView() returned a new instance for an unchanged view on frame " + frame
          + ", so the mask is being rebuilt and the buffer blacked every frame"
      );
    }

    assertUnwritten(lavaLamp, Apotheneum.cube.exterior, "cube exterior");
    assertUnwritten(lavaLamp, Apotheneum.cube.interior, "cube interior");
    assertUnwritten(lavaLamp, Apotheneum.cylinder.interior, "cylinder interior");

    int written = 0;
    final int[] colors = lavaLamp.getColors();
    for (int x = 0; x < Apotheneum.cylinder.exterior.width(); ++x) {
      final int available = Apotheneum.cylinder.exterior.available(x);
      for (int y = 0; y < available; ++y) {
        if (colors[Apotheneum.cylinder.exterior.point(x, y).index] != SENTINEL) {
          ++written;
        }
      }
    }
    assertTrue(
      written > 0,
      "the cylinder exterior was never written even though the view selects it, so the "
        + "assertions above pass only because the pattern drew nothing anywhere"
    );
  }

  /** Every point of a surface still holds the sentinel, so the pattern never touched it. */
  private void assertUnwritten(
    LavaLamp lavaLamp,
    Apotheneum.Orientation orientation,
    String name
  ) {
    final int[] colors = lavaLamp.getColors();
    for (int x = 0; x < orientation.width(); ++x) {
      final int available = orientation.available(x);
      for (int y = 0; y < available; ++y) {
        assertEquals(
          SENTINEL,
          colors[orientation.point(x, y).index],
          "the " + name + " was written while outside the pattern's view, at column " + x
            + " row " + y
        );
      }
    }
  }

  /** The cylinder-exterior view, created once and reused. */
  private LXViewDefinition cylinderExteriorView(LX lx) {
    if (this.cylinderExteriorView == null) {
      this.cylinderExteriorView = lx.structure.views.addView();
      this.cylinderExteriorView.selector.setValue("cylinderExterior");
    }
    return this.cylinderExteriorView;
  }

  private LXViewDefinition cylinderExteriorView;

  private void assertSurfacesAgreeOnDoors(
    Apotheneum.Orientation exterior,
    Apotheneum.Orientation interior
  ) {
    for (int x = 0; x < exterior.width(); ++x) {
      assertEquals(
        exterior.available(x),
        interior.available(x),
        "exterior and interior disagree on the usable height of column " + x
      );
    }
  }

  /**
   * Loads the real installation fixture, which is where the doorways are. Mirrors
   * {@code RenderSpike}: output is disabled before the fixture goes in and asserted to have
   * stayed that way, because the fixture carries the installation's real Art-Net addresses.
   */
}
