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

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

/**
 * Base class for a pattern that writes its {@code colors[]} buffer through global Apotheneum
 * geometry by point index rather than by walking {@code getModelView().points}, and therefore
 * has to carry a membership mask if it is to honour a pattern-level model view at all.
 *
 * <p>A model view is an <b>input model, not an output mask</b> (see
 * {@code docs/lx-coding-guidelines.md} &#167;18): {@code getModelView()} tells the pattern which
 * model it should draw, and nothing in the framework stops it writing any other index of the
 * shared colour buffer. A pattern that iterates the view's own points stays inside it for free
 * and needs none of this. A pattern that reaches for {@code Apotheneum.cube.exterior} and writes
 * {@code colors[point.index]} does not, so every such write — including clears, copies and
 * post-processing passes — has to be guarded.</p>
 *
 * <p><b>Everything here is opt-in by call.</b> This class overrides no framework method and
 * installs no per-frame behaviour of its own; a subclass that never calls
 * {@link #updateViewMask()} keeps the mask permanently absent, so {@link #isViewPoint(int)} is
 * always true and {@link #clearView()} is exactly {@code setColors(LXColor.BLACK)}. Inheriting
 * from this class therefore cannot change what an existing pattern draws. That matters
 * concretely: {@code Rockfall} deliberately projects onto third-party geometry discovered from
 * its view and must keep writing every index it finds, so it inherits this machinery and calls
 * none of it. Per &#167;18, a pattern like that puts the geometry in the <i>channel</i> view,
 * which is a hard output boundary, and leaves the pattern view at Default.</p>
 *
 * <p>The mask is rebuilt only when the view actually changes, never per frame. In the ordinary
 * case — no view narrowing the pattern — the mask is {@code null} and the whole facility costs
 * one reference comparison per frame and allocates nothing.</p>
 */
public abstract class ViewMaskedPattern extends ApotheneumPattern {

  /** The view last seen from {@code getModelView()}; the mask is rebuilt when it changes. */
  private LXModel viewModel;

  /** Membership by point index, or null when the view is the whole installation. */
  private boolean[] viewMask;

  protected ViewMaskedPattern(LX lx) {
    super(lx);
  }

  /**
   * Rebuilds the view membership mask, but only when the view has actually changed. Call this
   * once at the top of {@code render(double)}, before any write this class is meant to guard.
   *
   * <p>Building the mask here, on change, rather than in the render loop is what keeps it free
   * in the common case: the comparison is by reference, so an unchanged view costs one pointer
   * compare a frame and allocates nothing.</p>
   *
   * <p>Reference equality is the right test even if the framework were ever to hand back a
   * fresh instance for an unchanged view: the cost of that would be one wasted rebuild and one
   * black frame, never a frame drawn unmasked. Comparing point sets instead would cost more
   * every frame than the rebuild it avoided.</p>
   */
  protected final void updateViewMask() {
    final LXModel model = getModelView();
    if (this.viewModel == model) {
      return;
    }
    // The points that can be left stale are exactly those the old view covered and the new one
    // does not: a masked clear reaches only the current view, so it will never touch them again
    // and whatever they were last painted would stand there indefinitely. Clearing the whole
    // buffer instead would reach points this pattern has never owned and does not own now;
    // clearing only the new view would leave precisely the shrunk-away points lit, which is the
    // bug this exists to prevent. So clear the outgoing view: its overlap with the new one is
    // cleared again a moment later, harmlessly.
    if (this.viewModel != null) {
      for (LXPoint point : this.viewModel.points) {
        // A model rebuild can retire indices along with the view that named them.
        if (point.index < this.colors.length) {
          this.colors[point.index] = LXColor.BLACK;
        }
      }
    }
    this.viewModel = model;
    if (model == this.lx.getModel()) {
      // The view is the whole installation, which is the ordinary case. A null mask says so,
      // and every guard then costs one null check and no indirection.
      this.viewMask = null;
      return;
    }
    this.viewMask = new boolean[this.lx.getModel().size];
    for (LXPoint point : model.points) {
      if (point.index < this.viewMask.length) {
        this.viewMask[point.index] = true;
      }
    }
  }

  /**
   * Whether one point index is inside the current view, and so may be written. Always true
   * until {@link #updateViewMask()} has been called at least once.
   */
  protected final boolean isViewPoint(int index) {
    return (this.viewMask == null) || this.viewMask[index];
  }

  /**
   * Blacks out every point of the current view, leaving everything outside it untouched.
   *
   * <p>This is the per-frame companion to {@link #updateViewMask()}, not a one-shot: a pattern
   * whose output is sparse — most cells empty on any given frame — needs the view cleared at
   * the top of every frame or it leaves trails behind whatever it did draw. A pattern whose
   * write pass assigns every point it owns on every frame does not need this at all.</p>
   *
   * <p>With no view narrowing the pattern this is the whole buffer and goes through the
   * framework's own {@code setColors}, so the full-installation path is unchanged down to the
   * write — which is also what makes it correct for a subclass that never calls
   * {@link #updateViewMask()}.</p>
   */
  protected final void clearView() {
    if (this.viewMask == null) {
      setColors(LXColor.BLACK);
      return;
    }
    for (LXPoint point : this.viewModel.points) {
      if (point.index < this.colors.length) {
        this.colors[point.index] = LXColor.BLACK;
      }
    }
  }
}
