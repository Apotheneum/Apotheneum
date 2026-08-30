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

package apotheneum.doved.effects;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumEffect;
import apotheneum.doved.modulators.ApotheneumColor;
import apotheneum.doved.modulators.ApotheneumGradient;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

/**
 * Multiplies whatever is already in {@code colors[]} by a gradient computed in real 3D space,
 * with both the gradient's direction ({@link ApotheneumGradient}) and its two end colours
 * ({@link ApotheneumColor}) sourced from shared, engine-registered singletons rather than owned
 * here -- the effect-shaped sibling of how {@code ColorNativePattern} was redesigned to stop
 * owning its own palette knobs. Replaces the live rig's channel-4 "Colorized" arrangement (a
 * stack of stock {@code GradientPattern} instances in Blend mode, each view-scoped to one
 * surface, documented in {@code shows/treetop-live/color.md}): a brightness-only pattern can
 * take this effect directly instead of being routed through a dedicated colorize channel, and
 * every surface -- and every pattern this effect is hosted on -- agrees on one direction and one
 * pair of colours because they all read the same two singletons.
 *
 * <h2>This effect owns nothing per-instance</h2>
 *
 * Both the direction ({@link ApotheneumGradient#azimuth}/{@link ApotheneumGradient#elevation})
 * and the colours ({@link ApotheneumColor}) are global, GLOBAL-tab settings now -- see {@link
 * ApotheneumGradient}'s class javadoc for why a real 3D gradient has exactly one direction
 * through space rather than one per surface, and why that direction is a shared setting rather
 * than a per-pattern one. This class therefore declares no {@link
 * heronarts.lx.parameter.LXParameter} of its own and does not implement {@code
 * UIDeviceControls}: put it on five patterns and all five agree, and turning the one shared knob
 * moves every instance at once. What is left is exactly what {@code enabledAmount} (from {@link
 * heronarts.lx.effect.LXEffect}) already gives every effect for free -- there is nothing else
 * that is genuinely per-instance here.
 *
 * <h2>Resolving which surface a pixel belongs to</h2>
 *
 * Not a per-pixel lookup. This class deliberately processes all four fixed surfaces every
 * frame -- it is not confined to whatever a pattern's view happens to include -- so {@link
 * #render} is simply four explicit passes over {@link Apotheneum#cube}'s and {@link
 * Apotheneum#cylinder}'s own {@code exterior}/{@code interior} orientations, each pass already
 * knowing which {@link ApotheneumColor.Surface} it is. Cost is one pass over every real point
 * once per frame -- the same order of work as any other per-pixel effect or pattern, not four
 * times it, since the four passes partition the model rather than overlapping.
 *
 * <h2>The gradient is computed in real 3D space, not against an unwrapped 2D raster</h2>
 *
 * The previous version of this class gave each surface its own 0-360&#176; direction against
 * that surface's own unwrapped column/row raster, and produced a visible seam on the cylinder
 * and the cube exterior -- both of which wrap horizontally in the real room but do not wrap in
 * an unwrapped raster, so a ramp across the raster hit its own start/end discontinuity every
 * lap. This version projects each {@link LXPoint}'s real world position onto one 3D direction
 * vector ({@link ApotheneumGradient}) instead: a continuous position in a continuous space has
 * no seam to hit. See {@link ApotheneumGradient}'s class javadoc for the full reasoning,
 * including why the projection is normalized against the whole model's own bounding box rather
 * than per-surface bounds or {@link LXPoint#xn}/{@code yn}/{@code zn}.
 *
 * <p>{@link #frameDirX}/{@link #frameDirY}/{@link #frameDirZ} and {@link #frameProjectedMin}/
 * {@link #frameProjectedMax} are resolved once per frame in {@link #render} and reused across
 * all four surface passes and every point within them -- fields mutated in place each frame
 * rather than threaded through as method parameters or reallocated, per this repo's "no
 * per-frame allocation" guideline (docs/lx-coding-guidelines.md &#167;1): the direction and the
 * model's bounds cannot change between one surface pass and the next within the same frame, so
 * there is no reason to recompute either four times, and no reason to allocate anything to carry
 * them.
 *
 * <h2>Reading {@code ApotheneumColor} and {@code ApotheneumGradient}, including their
 * null-instance fallbacks</h2>
 *
 * Both singletons are resolved once per frame (not per surface, not per pixel), then their
 * respective {@code *OrNeutral}/{@code *OrDefault} resolvers are called -- the same
 * once-per-transition-logged fallback pattern both classes implement for the same reason: in
 * practice this should not happen, since both are registered on the engine itself by {@code
 * apotheneum.doved.ApotheneumColorPlugin}, not added by a performer.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Apotheneum Gradient")
@LXComponent.Description("Multiplies content by a shared 3D gradient sourced from ApotheneumColor/ApotheneumGradient")
public class GradientMultiplyEffect extends ApotheneumEffect {

  // Resolved once per frame in render(), reused by every surface pass and every point within
  // them -- see the class javadoc's "no per-frame allocation" note. Primitive fields, not a
  // returned array/record, for the same reason.
  private double frameDirX;
  private double frameDirY;
  private double frameDirZ;
  private double frameProjectedMin;
  private double frameProjectedMax;
  private double frameSpread;

  public GradientMultiplyEffect(LX lx) {
    super(lx);
  }

  @Override
  protected void render(double deltaMs, double enabledAmount) {
    if (enabledAmount <= 0) {
      return;
    }

    final ApotheneumColor color = ApotheneumColor.get(this.lx);
    final ApotheneumGradient gradient = ApotheneumGradient.get(this.lx);

    final double azimuthDegrees = ApotheneumGradient.azimuthOrDefault(gradient);
    final double elevationDegrees = ApotheneumGradient.elevationOrDefault(gradient);
    this.frameDirX = ApotheneumGradient.directionX(azimuthDegrees, elevationDegrees);
    this.frameDirY = ApotheneumGradient.directionY(elevationDegrees);
    this.frameDirZ = ApotheneumGradient.directionZ(azimuthDegrees, elevationDegrees);

    final LXModel model = this.lx.getModel();
    this.frameProjectedMin =
      ApotheneumGradient.projectedMin(model, this.frameDirX, this.frameDirY, this.frameDirZ);
    this.frameProjectedMax =
      ApotheneumGradient.projectedMax(model, this.frameDirX, this.frameDirY, this.frameDirZ);
    this.frameSpread = ApotheneumGradient.spreadOrDefault(gradient);

    multiplySurface(Apotheneum.cube.exterior, ApotheneumColor.Surface.CUBE_EXTERIOR, enabledAmount, color);
    multiplySurface(Apotheneum.cube.interior, ApotheneumColor.Surface.CUBE_INTERIOR, enabledAmount, color);
    multiplySurface(Apotheneum.cylinder.exterior, ApotheneumColor.Surface.CYLINDER_EXTERIOR, enabledAmount, color);
    multiplySurface(Apotheneum.cylinder.interior, ApotheneumColor.Surface.CYLINDER_INTERIOR, enabledAmount, color);
  }

  /**
   * One surface's whole pass: resolve its gradient's two end colours once (not per pixel), then
   * write every real point in this orientation using the direction/extent {@link #render}
   * already resolved for this frame. {@code enabledAmount} &lt; 1 cross-fades between the
   * untouched content and the fully-multiplied result, matching how a fractional effect enable
   * is expected to behave rather than snapping the multiply fully on or off.
   */
  private void multiplySurface(
    Apotheneum.Orientation orientation,
    ApotheneumColor.Surface surface,
    double enabledAmount,
    ApotheneumColor color
  ) {
    if (orientation == null) {
      return;
    }

    final int primaryColor = ApotheneumColor.resolvePrimaryOrNeutral(color, surface);
    final int secondaryColor = ApotheneumColor.resolveSecondaryOrNeutral(color, surface);

    for (Apotheneum.Column column : orientation.columns()) {
      for (LXPoint point : column.points) {
        final double projected =
          ApotheneumGradient.project(point, this.frameDirX, this.frameDirY, this.frameDirZ);
        final double t =
          ApotheneumGradient.normalize(projected, this.frameProjectedMin, this.frameProjectedMax);
        final double effectiveT = ApotheneumGradient.applySpread(t, this.frameSpread);
        final int gradientColor = LXColor.lerp(primaryColor, secondaryColor, effectiveT);
        final int original = colors[point.index];
        final int multiplied = LXColor.multiply(original, gradientColor);
        colors[point.index] = (enabledAmount >= 1)
          ? multiplied
          : LXColor.lerp(original, multiplied, enabledAmount);
      }
    }
  }

}
