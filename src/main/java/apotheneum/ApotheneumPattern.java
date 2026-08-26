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
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package apotheneum;

import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.pattern.LXPattern;

public abstract class ApotheneumPattern extends LXPattern {

  private static final int[] NO_INDICES = new int[0];

  /**
   * Model the view mask was last built against, or {@code null} when this pattern's own
   * view selector is at {@code Default}. Identity-compared each frame; never dereferenced
   * for content.
   */
  private LXModel viewMaskModel = null;

  /** Global model the view mask was last built against. */
  private LXModel viewMaskGlobalModel = null;

  /**
   * Color-buffer indices that fall <em>outside</em> the pattern-level view. Empty whenever
   * no pattern-level view is selected, which makes {@link #applyModelView()} a no-op.
   */
  private int[] outsideViewIndices = NO_INDICES;

  /** Diagnostic counter, read by tests to prove the mask is not rebuilt per frame. */
  private int viewMaskBuildCount = 0;

  protected ApotheneumPattern(LX lx) {
    super(lx);
    Apotheneum.initialize(lx);
  }

  @Override
  protected final void run(double deltaMs) {
    if (Apotheneum.exists) {
      render(deltaMs);
    } else {
      setColors(LXColor.BLACK);
    }
  }

  /**
   * Hook that runs after {@link #render(double)} and after any layers have looped, in place
   * of overriding {@code afterLayers}, which {@code ApotheneumPattern} reserves so that the
   * pattern-level view mask is always the last thing applied to the buffer.
   */
  protected void afterRenderLayers(double deltaMs) {}

  @Override
  protected final void afterLayers(double deltaMs) {
    super.afterLayers(deltaMs);
    afterRenderLayers(deltaMs);
    applyModelView();
  }

  /**
   * Enforces a pattern-level model view as a hard output boundary.
   *
   * <p>{@code getModelView()} is an input model, not a write mask (see
   * {@code docs/lx-coding-guidelines.md} §18): the helpers on this class resolve geometry from
   * the global {@code Apotheneum.cube} / {@code Apotheneum.cylinder} statics and write absolute
   * point indices, and most subclasses additionally assign {@code colors[point.index]} directly.
   * Masking the helpers alone would therefore constrain the clears and copies but not the draws
   * — leaving stale, uncleared pixels outside the view. Instead every write is allowed to happen
   * normally and anything outside the view is blacked out here, once, after layers.
   *
   * <p>This engages <em>only</em> when the pattern's own view selector is set. At {@code Default}
   * (the state of every existing pattern and saved project) {@code outsideViewIndices} is empty
   * and this method costs two reference comparisons and an empty loop, so output is unchanged.
   * A channel-level view is deliberately not enforced here: the mixer already applies it as a
   * hard boundary, and patterns that intentionally project onto third-party geometry rely on
   * inheriting the channel's model.
   *
   * <p>Points outside the view are set to {@link LXColor#BLACK} rather than left untouched,
   * matching what a view-aware pattern here already does (see
   * {@code doved/patterns/Rockfall.java}); leaving them alone would expose whatever stale
   * content the channel buffer happened to hold.
   */
  private void applyModelView() {
    final LXModel viewModel = (this.view.getObject() != null) ? getModelView() : null;
    final LXModel globalModel = this.lx.getModel();
    if ((viewModel != this.viewMaskModel) || (globalModel != this.viewMaskGlobalModel)) {
      rebuildViewMask(viewModel, globalModel);
    }
    for (int i = 0; i < this.outsideViewIndices.length; ++i) {
      this.colors[this.outsideViewIndices[i]] = LXColor.BLACK;
    }
  }

  private void rebuildViewMask(LXModel viewModel, LXModel globalModel) {
    this.viewMaskModel = viewModel;
    this.viewMaskGlobalModel = globalModel;
    ++this.viewMaskBuildCount;
    if ((viewModel == null) || (viewModel == globalModel)) {
      this.outsideViewIndices = NO_INDICES;
      return;
    }
    final int total = this.colors.length;
    final boolean[] insideView = new boolean[total];
    for (LXPoint point : viewModel.points) {
      if (point.index < total) {
        insideView[point.index] = true;
      }
    }
    int outsideCount = 0;
    for (int i = 0; i < total; ++i) {
      if (!insideView[i]) {
        ++outsideCount;
      }
    }
    final int[] indices = new int[outsideCount];
    int cursor = 0;
    for (int i = 0; i < total; ++i) {
      if (!insideView[i]) {
        indices[cursor++] = i;
      }
    }
    this.outsideViewIndices = indices;
  }

  int viewMaskBuildCount() {
    return this.viewMaskBuildCount;
  }

  private void assertExists() {
    if (!Apotheneum.exists) {
      throw new IllegalStateException("Should not call ApothenumPattern utilities when no Apotheneum model loaded");
    }
  }

  private void _copyCubeFace(Apotheneum.Cube.Face from, Apotheneum.Cube.Face to) {
    if (from != to) {
      copy(from, to);
    }
  }

  protected void copyCubeFace(Apotheneum.Cube.Face from) {
    _copyCubeFace(from, Apotheneum.cube.exterior.front);
    _copyCubeFace(from, Apotheneum.cube.exterior.right);
    _copyCubeFace(from, Apotheneum.cube.exterior.back);
    _copyCubeFace(from, Apotheneum.cube.exterior.left);
    _copyCubeFace(from, Apotheneum.cube.interior.front);
    _copyCubeFace(from, Apotheneum.cube.interior.right);
    _copyCubeFace(from, Apotheneum.cube.interior.back);
    _copyCubeFace(from, Apotheneum.cube.interior.left);
  }

  protected void copyCubeExterior() {
    copy(Apotheneum.cube.exterior, Apotheneum.cube.interior);
  }

  protected void copyCylinderExterior() {
    copy(Apotheneum.cylinder.exterior, Apotheneum.cylinder.interior);
  }

  protected void copyExterior() {
    copyCubeExterior();
    copyCylinderExterior();
  }

  protected void copyMirror(Apotheneum.Cube.Face from, Apotheneum.Cube.Face to) {
    assertExists();
    if ((from != null) && (to != null)) {
      int colIndex = 0;
      for (Apotheneum.Column fromCol : from.columns) {
        Apotheneum.Column toCol = to.columns[to.columns.length - 1 - colIndex];
        System.arraycopy(colors, fromCol.points[0].index, colors, toCol.points[0].index, fromCol.size);
        ++colIndex;
      }
    }
  }

  protected void copy(Apotheneum.Cube.Face from, Apotheneum.Cube.Face to) {
    assertExists();
    if ((from != null) && (to != null)) {
      System.arraycopy(colors, from.model.points[0].index, colors, to.model.points[0].index, from.model.size);
    }
  }

  protected void copy(Apotheneum.Cube.Orientation from, Apotheneum.Cube.Orientation to) {
    assertExists();
    if ((from != null) && (to != null)) {
      System.arraycopy(colors, from.front.model.points[0].index, colors, to.front.model.points[0].index, from.size);
    }
  }

  protected void copy(Apotheneum.Cylinder.Orientation from, Apotheneum.Cylinder.Orientation to) {
    assertExists();
    if ((from != null) && (to != null)) {
      System.arraycopy(colors, from.columns[0].points[0].index, colors, to.columns[0].points[0].index, from.size);
    }
  }

  protected void setApotheneumColor(int color) {
    setColor(Apotheneum.cube, color);
    setColor(Apotheneum.cylinder, color);
  }

  protected void setColor(Apotheneum.Component component, int color) {
    for (Apotheneum.Orientation orientation : component.orientations()) {
      setColor(orientation, color);
    }
  }

  protected void setColor(Apotheneum.Orientation orientation, int color) {
    for (Apotheneum.Column column : orientation.columns()) {
      setColor(column, color);
    }
  }

  protected void setColor(Apotheneum.Cube.Face face, int color) {
    for (Apotheneum.Column column : face.columns) {
      setColor(column, color);
    }
  }

  protected void setColor(Apotheneum.Column column, int color) {
    setColor(column.model, color);
  }

  protected abstract void render(double deltaMs);

}
