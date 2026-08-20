package apotheneum.doved.modulators;

import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UIKnob;
import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.modulation.UIModulator;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;
import heronarts.lx.utils.LXUtils;

/**
 * Applies a unary mathematical function to a continuously sampled modulation input.
 *
 * <p>The output is {@code gain * function(freq * input + phase) + offset}. Angles are in
 * radians. The final value is constrained to the normalized modulation range {@code [0, 1]}.
 * This is also the explicit safety policy for {@link Function#TAN}: values near an asymptote
 * saturate at an endpoint rather than escaping the modulation range. Undefined function
 * results, such as the square root of a negative number, produce zero.
 *
 * <p>{@link Function#POW} is the unary square operation {@code x^2}. A configurable exponent
 * would require another parameter and is intentionally outside the six-parameter surface of
 * this modulator.
 */
@LXModulator.Global("Trig Operator")
@LXModulator.Device("Trig Operator")
@LXCategory(LXCategory.CORE)
public class TrigOperator extends LXModulator implements LXNormalizedParameter, LXOscComponent,
  UIModulatorControls<TrigOperator> {

  private static final double TWO_PI = 2 * Math.PI;

  /** Unary functions available to the operator. */
  public enum Function {
    SIN("Sin") {
      @Override
      double apply(double value) {
        return Math.sin(value);
      }
    },
    COS("Cos") {
      @Override
      double apply(double value) {
        return Math.cos(value);
      }
    },
    TAN("Tan") {
      @Override
      double apply(double value) {
        return Math.tan(value);
      }
    },
    ASIN("Asin") {
      @Override
      double apply(double value) {
        return Math.asin(value);
      }
    },
    ACOS("Acos") {
      @Override
      double apply(double value) {
        return Math.acos(value);
      }
    },
    ATAN("Atan") {
      @Override
      double apply(double value) {
        return Math.atan(value);
      }
    },
    SQRT("Sqrt") {
      @Override
      double apply(double value) {
        return Math.sqrt(value);
      }
    },
    ABS("Abs") {
      @Override
      double apply(double value) {
        return Math.abs(value);
      }
    },
    POW("Pow (x^2)") {
      @Override
      double apply(double value) {
        return value * value;
      }
    };

    private final String label;

    Function(String label) {
      this.label = label;
    }

    abstract double apply(double value);

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter input =
    new CompoundParameter("Input", 0)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Input value; map a modulation source here");

  public final EnumParameter<Function> function =
    new EnumParameter<Function>("Function", Function.SIN)
    .setDescription("Unary function applied to freq * input + phase");

  public final CompoundParameter freq =
    new CompoundParameter("Freq", TWO_PI, -8 * TWO_PI, 8 * TWO_PI)
    .setUnits(LXParameter.Units.RADIANS)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Angular input multiplier in radians");

  public final CompoundParameter phase =
    new CompoundParameter("Phase", 0, -TWO_PI, TWO_PI)
    .setUnits(LXParameter.Units.RADIANS)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Angular offset added before applying the function");

  public final CompoundParameter gain =
    new CompoundParameter("Gain", .5, -4, 4)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Multiplier applied to the function result");

  public final CompoundParameter offset =
    new CompoundParameter("Offset", .5, -2, 2)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Value added after applying gain");

  public TrigOperator() {
    this("Trig Operator");
  }

  public TrigOperator(String label) {
    super(label);
    addParameter("input", this.input);
    addParameter("function", this.function);
    addParameter("freq", this.freq);
    addParameter("phase", this.phase);
    addParameter("gain", this.gain);
    addParameter("offset", this.offset);
    setDescription("Applies a scaled unary math function to a modulation input");
  }

  @Override
  protected double computeValue(double deltaMs) {
    final double argument = this.freq.getValue() * this.input.getValue() + this.phase.getValue();
    final double functionValue = this.function.getEnum().apply(argument);
    if (!Double.isFinite(functionValue)) {
      return 0;
    }

    final double scaled = this.gain.getValue() * functionValue + this.offset.getValue();
    if (!Double.isFinite(scaled)) {
      return 0;
    }
    return LXUtils.constrain(scaled, 0, 1);
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException(
      "TrigOperator value comes from its input and function; it cannot be set directly");
  }

  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, TrigOperator trigOperator) {
    // Two rows keep all six controls visible in both the global pane and device strip.
    uiModulator.setLayout(UI2dContainer.Layout.VERTICAL, 4);
    uiModulator.addChildren(
      UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4,
        newKnob(trigOperator.input),
        newDropMenu(trigOperator.function, 72),
        newKnob(trigOperator.freq)),
      UI2dContainer.newHorizontalContainer(UIKnob.HEIGHT, 4,
        newKnob(trigOperator.phase),
        newKnob(trigOperator.gain),
        newKnob(trigOperator.offset))
    );
  }

}
