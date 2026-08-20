package apotheneum.doved.patterns;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.utils.LXUtils;

/**
 * Uses Raybeam's distance fields and local orientation to cut out a dark central volume,
 * then draws a grayscale gradient with animated ripples outside its boundary.
 */
@LXCategory("Apotheneum/doved")
@LXComponent.Name("Skyspace")
@LXComponent.Description("Renders a dark sky opening with an outward gradient and ripples")
public class Skyspace extends Raybeam {

  private static final double TWO_PI = 2 * Math.PI;

  /** Applies signed concentric bands while preserving the distance-field envelope. */
  static final class Ripple {
    private Ripple() {}

    static double brightness(double envelope, double distance, double amount,
      double spacing, double phase, double sharpness, double decay) {

      if (amount <= 0 || envelope <= 0) {
        return envelope;
      }

      final double wave = Math.cos(TWO_PI * (distance / spacing - phase));
      final double shapedWave = Math.copySign(Math.pow(Math.abs(wave), sharpness), wave);
      final double amplitude = amount * Math.pow(envelope, decay);
      return LXUtils.constrain(envelope + amplitude * shapedWave, 0, 1);
    }
  }

  public final CompoundParameter rippleAmount =
    new CompoundParameter("Ripple Amt", .35)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Strength of concentric brightness ripples outside the opening");

  public final CompoundParameter rippleSpacing =
    new CompoundParameter("Ripple Gap", .08, .005, .5)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Distance between successive ripple crests");

  public final CompoundParameter ripplePhase =
    new CompoundParameter("Ripple Phase", 0)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setWrappable(true)
    .setDescription("Modulate upward from 0 to 1 to move ripples away from the opening");

  public final CompoundParameter rippleSharpness =
    new CompoundParameter("Ripple Sharp", 1, .25, 4)
    .setDescription("Shape of ripple bands; higher values make them narrower");

  public final CompoundParameter rippleDecay =
    new CompoundParameter("Ripple Decay", 1.5, .1, 8)
    .setDescription("How quickly ripple amplitude fades away from the opening");

  public Skyspace(LX lx) {
    super(lx);
    addParameter("rippleAmount", this.rippleAmount);
    addParameter("rippleSpacing", this.rippleSpacing);
    addParameter("ripplePhase", this.ripplePhase);
    addParameter("rippleSharpness", this.rippleSharpness);
    addParameter("rippleDecay", this.rippleDecay);

    this.shape.setValue(Shape.SPHERE);
    this.width.setValue(.5);
  }

  @Override
  protected double getBrightness(
    Shape shape, double x, double y, double z, double radius, double minorRadius,
    double coneSin, double coneCos, double distance, double envelope) {

    if (shape.contains(x, y, z, radius, minorRadius, coneSin, coneCos)) {
      return 0;
    }
    return Ripple.brightness(
      envelope, distance, this.rippleAmount.getValue(), this.rippleSpacing.getValue(),
      this.ripplePhase.getValue(), this.rippleSharpness.getValue(),
      this.rippleDecay.getValue());
  }

  @Override
  public void buildDeviceControls(
    UI ui, UIDevice uiDevice, Raybeam raybeam) {

    super.buildDeviceControls(ui, uiDevice, raybeam);
    final Skyspace skyspace = (Skyspace) raybeam;

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Ripple",
      newKnob(skyspace.rippleAmount),
      newKnob(skyspace.rippleSpacing),
      newKnob(skyspace.ripplePhase));

    addVerticalBreak(ui, uiDevice);
    addColumn(uiDevice, "Ripple Shape",
      newKnob(skyspace.rippleSharpness),
      newKnob(skyspace.rippleDecay));
  }
}
