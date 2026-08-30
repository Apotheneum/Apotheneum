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
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.lx.utils.LXUtils;

/**
 * Multiplies whatever is already in {@code colors[]} by a per-surface gradient, with the
 * gradient's two end colours sourced from the shared {@link ApotheneumColor} rather than owned
 * here — the effect-shaped sibling of how {@code ColorNativePattern} was redesigned to stop
 * owning its own palette knobs. Replaces the live rig's channel-4 "Colorized" arrangement (a
 * stack of stock {@code GradientPattern} instances in Blend mode, each view-scoped to one
 * surface, documented in {@code shows/treetop-live/color.md}): a brightness-only pattern can
 * take this effect directly instead of being routed through a dedicated colorize channel, and
 * every surface still agrees on which two colours it is multiplying by because they all read
 * the same {@code ApotheneumColor}.
 *
 * <h2>Why an effect here, when one was rejected for {@code ColorNativePattern}</h2>
 *
 * That redesign needed a pattern's own per-pixel physics to couple into {@code amount} — an
 * effect only ever sees finished {@code colors[]}, with no way to reach a pattern's internal
 * state. This class needs no such thing: it multiplies a gradient over content that is already
 * finished, which is structurally identical to what channel 4 already does today. An effect is
 * the right component for exactly that reason.
 *
 * <h2>Resolving which surface a pixel belongs to</h2>
 *
 * Not a per-pixel lookup. A {@code ColorNativePattern} subclass resolves its surface once per
 * geometry loop because it is already iterating one physical orientation at a time; this class
 * gets the same thing even more directly, because it is not confined to whatever a pattern's
 * view happens to include — it deliberately processes all four fixed surfaces every frame, so
 * {@link #render} is simply four explicit passes over {@link Apotheneum#cube}'s and
 * {@link Apotheneum#cylinder}'s own {@code exterior}/{@code interior} orientations, each pass
 * already knowing which {@link ApotheneumColor.Surface} it is. There is no
 * {@code Surface.of(LXPoint)}-style per-pixel resolution anywhere in this class, and none is
 * needed: that helper exists for code that receives an arbitrary orientation from a caller and
 * must identify it, not for code that already enumerates the four surfaces itself. Cost is one
 * pass over every real point once per frame — the same order of work as any other per-pixel
 * effect or pattern, not four times it, since the four passes partition the model rather than
 * overlapping.
 *
 * <h2>Direction, parameterised per surface</h2>
 *
 * Each surface gets exactly one new control, {@code direction} (0-360&#176;), against that
 * surface's own unwrapped column/row raster — not a shared 3D yaw/pitch/roll axis the way stock
 * {@code GradientPattern} points one gradient through the whole installation. A per-surface 2D
 * angle is what "directable on the four corners" asks for: cube exterior and cylinder interior
 * have different unwrapped dimensions and no shared 3D relationship a performer would reliably
 * predict, so directing each surface's own local raster independently is both the simpler
 * implementation and the more legible control. For a pixel at normalized position
 * {@code (u, v)} centred at the surface's own middle (so {@code u, v} range roughly
 * {@code [-0.5, 0.5]}), the gradient position is {@code 0.5 + u*cos(direction) +
 * v*sin(direction)}, clamped to {@code [0, 1]} (a hard-edged linear ramp, matching a plain
 * two-colour gradient rather than a repeating one — this effect has no repeat/scale control
 * because the owner asked specifically for direction, and the per-frame budget already spent on
 * {@link ApotheneumColor}'s own panel is exactly what taught this codebase not to add controls
 * beyond what was asked without checking they fit).
 *
 * <h2>Reading {@code ApotheneumColor}, including the null-instance fallback</h2>
 *
 * Resolves {@link ApotheneumColor#get(heronarts.lx.LX)} once per frame (not per surface,
 * not per pixel), then calls {@link ApotheneumColor#resolvePrimaryOrNeutral} /
 * {@code resolveSecondaryOrNeutral} against that result — the same shared,
 * once-per-transition-logged fallback {@code ColorNativePattern.ColorRole} now calls too,
 * extracted onto {@code ApotheneumColor} itself specifically so this class does not carry a
 * second copy of the null check and the log gate. With no {@code ApotheneumColor} registered on
 * the engine both ends of the gradient resolve to neutral white, so multiplying by it is a
 * no-op (white multiplied by anything is that thing unchanged) — the same "absence reads as
 * doing nothing, not as broken" behaviour {@code ColorRole} already has, for the same reason.
 * In practice this should not happen: {@code ApotheneumColor} is registered by
 * {@code apotheneum.doved.ApotheneumColorPlugin} on the engine itself, not added by a
 * performer, so there is no user action that removes it.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Apotheneum Gradient")
@LXComponent.Description("Multiplies content by a per-surface gradient sourced from ApotheneumColor")
public class GradientMultiplyEffect extends ApotheneumEffect
  implements UIDeviceControls<GradientMultiplyEffect> {

  private static final double DIRECTION_MAX_DEGREES = 360;

  public final CompoundParameter cubeExteriorDirection = newDirection("Cub Ext");
  public final CompoundParameter cubeInteriorDirection = newDirection("Cub Int");
  public final CompoundParameter cylinderExteriorDirection = newDirection("Cyl Ext");
  public final CompoundParameter cylinderInteriorDirection = newDirection("Cyl Int");

  private static CompoundParameter newDirection(String label) {
    return new CompoundParameter(label, 0, 0, DIRECTION_MAX_DEGREES)
      .setUnits(CompoundParameter.Units.DEGREES)
      .setDescription(
        "Gradient direction across " + label + "'s own unwrapped raster, independent of every "
        + "other surface's direction");
  }

  public GradientMultiplyEffect(LX lx) {
    super(lx);
    addParameter("cubeExteriorDirection", this.cubeExteriorDirection);
    addParameter("cubeInteriorDirection", this.cubeInteriorDirection);
    addParameter("cylinderExteriorDirection", this.cylinderExteriorDirection);
    addParameter("cylinderInteriorDirection", this.cylinderInteriorDirection);
  }

  @Override
  protected void render(double deltaMs, double enabledAmount) {
    if (enabledAmount <= 0) {
      return;
    }
    // Resolved once per frame, not once per surface/pixel -- ApotheneumColor.get(LX) is a
    // single lx.engine.getChild(...) lookup, but there is still no reason to repeat it four
    // times when one frame's answer cannot change between these four calls.
    final ApotheneumColor color = ApotheneumColor.get(this.lx);
    multiplySurface(
      Apotheneum.cube.exterior, ApotheneumColor.Surface.CUBE_EXTERIOR,
      this.cubeExteriorDirection, enabledAmount, color);
    multiplySurface(
      Apotheneum.cube.interior, ApotheneumColor.Surface.CUBE_INTERIOR,
      this.cubeInteriorDirection, enabledAmount, color);
    multiplySurface(
      Apotheneum.cylinder.exterior, ApotheneumColor.Surface.CYLINDER_EXTERIOR,
      this.cylinderExteriorDirection, enabledAmount, color);
    multiplySurface(
      Apotheneum.cylinder.interior, ApotheneumColor.Surface.CYLINDER_INTERIOR,
      this.cylinderInteriorDirection, enabledAmount, color);
  }

  /**
   * One surface's whole pass: resolve its gradient's two end colours once (not per pixel), then
   * write every real point in this orientation. {@code enabledAmount} &lt; 1 cross-fades between
   * the untouched content and the fully-multiplied result, matching how a fractional effect
   * enable is expected to behave rather than snapping the multiply fully on or off.
   */
  private void multiplySurface(
    Apotheneum.Orientation orientation,
    ApotheneumColor.Surface surface,
    CompoundParameter directionParam,
    double enabledAmount,
    ApotheneumColor color
  ) {
    if (orientation == null) {
      return;
    }

    final int primaryColor = ApotheneumColor.resolvePrimaryOrNeutral(color, surface);
    final int secondaryColor = ApotheneumColor.resolveSecondaryOrNeutral(color, surface);

    final double radians = Math.toRadians(directionParam.getValue());
    final double dx = Math.cos(radians);
    final double dy = Math.sin(radians);

    final int width = orientation.width();
    final int height = orientation.height();
    final double widthSpan = Math.max(1, width - 1);
    final double heightSpan = Math.max(1, height - 1);

    int x = 0;
    for (Apotheneum.Column column : orientation.columns()) {
      final double u = (x / widthSpan) - .5;
      int y = 0;
      for (LXPoint point : column.points) {
        final double v = (y / heightSpan) - .5;
        final double t = LXUtils.clamp(.5 + u * dx + v * dy, 0, 1);
        final int gradientColor = LXColor.lerp(primaryColor, secondaryColor, t);
        final int original = colors[point.index];
        final int multiplied = LXColor.multiply(original, gradientColor);
        colors[point.index] = (enabledAmount >= 1)
          ? multiplied
          : LXColor.lerp(original, multiplied, enabledAmount);
        ++y;
      }
      ++x;
    }
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice device, GradientMultiplyEffect effect) {
    device.setLayout(UIDevice.Layout.HORIZONTAL, 4);
    addColumn(device, "Cub Ext", newKnob(effect.cubeExteriorDirection));
    addVerticalBreak(ui, device);
    addColumn(device, "Cub Int", newKnob(effect.cubeInteriorDirection));
    addVerticalBreak(ui, device);
    addColumn(device, "Cyl Ext", newKnob(effect.cylinderExteriorDirection));
    addVerticalBreak(ui, device);
    addColumn(device, "Cyl Int", newKnob(effect.cylinderInteriorDirection));
  }

}
