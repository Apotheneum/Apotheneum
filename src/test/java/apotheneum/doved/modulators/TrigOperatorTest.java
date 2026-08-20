package apotheneum.doved.modulators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.studio.ui.modulation.UIModulatorControls;

public class TrigOperatorTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-9;

  private LX lx;
  private TrigOperator trigOperator;

  @BeforeEach
  void setUp() {
    this.lx = newHeadlessLx();
    this.trigOperator = this.lx.engine.modulation.addModulator(new TrigOperator());
    this.trigOperator.start();
  }

  private double evaluate(TrigOperator.Function function, double argument) {
    this.trigOperator.function.setValue(function);
    this.trigOperator.input.setValue(0);
    this.trigOperator.freq.setValue(0);
    this.trigOperator.phase.setValue(argument);
    this.trigOperator.gain.setValue(.25);
    this.trigOperator.offset.setValue(.5);
    this.trigOperator.loop(0);
    return this.trigOperator.getValue();
  }

  @Test
  void defaultsProduceOneSineCycleInTheNormalizedRange() {
    this.trigOperator.input.setValue(.25);
    this.trigOperator.loop(0);
    assertEquals(1, this.trigOperator.getValue(), EPSILON);

    this.trigOperator.input.setValue(.75);
    this.trigOperator.loop(0);
    assertEquals(0, this.trigOperator.getValue(), EPSILON);
  }

  @Test
  void evaluatesEveryFunction() {
    assertEquals(.5 + .25 * Math.sin(.4), evaluate(TrigOperator.Function.SIN, .4), EPSILON);
    assertEquals(.5 + .25 * Math.cos(.4), evaluate(TrigOperator.Function.COS, .4), EPSILON);
    assertEquals(.75, evaluate(TrigOperator.Function.TAN, Math.PI / 4), EPSILON);
    assertEquals(.5 + .25 * Math.asin(.4), evaluate(TrigOperator.Function.ASIN, .4), EPSILON);
    assertEquals(.5 + .25 * Math.acos(.4), evaluate(TrigOperator.Function.ACOS, .4), EPSILON);
    assertEquals(.5 + .25 * Math.atan(.4), evaluate(TrigOperator.Function.ATAN, .4), EPSILON);
    assertEquals(.5 + .25 * Math.sqrt(.4), evaluate(TrigOperator.Function.SQRT, .4), EPSILON);
    assertEquals(.6, evaluate(TrigOperator.Function.ABS, -.4), EPSILON);
    assertEquals(.54, evaluate(TrigOperator.Function.POW, -.4), EPSILON);
  }

  @Test
  void appliesFrequencyPhaseGainAndOffsetInTheDocumentedOrder() {
    this.trigOperator.function.setValue(TrigOperator.Function.SIN);
    this.trigOperator.input.setValue(.25);
    this.trigOperator.freq.setValue(2);
    this.trigOperator.phase.setValue(.25);
    this.trigOperator.gain.setValue(.2);
    this.trigOperator.offset.setValue(.3);
    this.trigOperator.loop(0);

    assertEquals(.2 * Math.sin(.75) + .3, this.trigOperator.getValue(), EPSILON);
  }

  @Test
  void clampsAllOutputsToTheNormalizedRange() {
    this.trigOperator.function.setValue(TrigOperator.Function.ABS);
    this.trigOperator.input.setValue(1);
    this.trigOperator.freq.setValue(1);
    this.trigOperator.phase.setValue(0);

    this.trigOperator.gain.setValue(4);
    this.trigOperator.offset.setValue(2);
    this.trigOperator.loop(0);
    assertEquals(1, this.trigOperator.getValue(), EPSILON);

    this.trigOperator.gain.setValue(-4);
    this.trigOperator.offset.setValue(-2);
    this.trigOperator.loop(0);
    assertEquals(0, this.trigOperator.getValue(), EPSILON);
  }

  @Test
  void tangentSaturatesSafelyOnBothSidesOfAnAsymptote() {
    this.trigOperator.function.setValue(TrigOperator.Function.TAN);
    this.trigOperator.input.setValue(0);
    this.trigOperator.freq.setValue(0);
    this.trigOperator.gain.setValue(1);
    this.trigOperator.offset.setValue(.5);

    this.trigOperator.phase.setValue(Math.PI / 2 - 1e-9);
    this.trigOperator.loop(0);
    assertEquals(1, this.trigOperator.getValue(), EPSILON);

    this.trigOperator.phase.setValue(Math.PI / 2 + 1e-9);
    this.trigOperator.loop(0);
    assertEquals(0, this.trigOperator.getValue(), EPSILON);
  }

  @Test
  void undefinedFunctionResultsBecomeZeroInsteadOfNaN() {
    assertEquals(0, evaluate(TrigOperator.Function.SQRT, -.1), EPSILON);
    assertEquals(0, evaluate(TrigOperator.Function.ASIN, 1.1), EPSILON);
    assertEquals(0, evaluate(TrigOperator.Function.ACOS, -1.1), EPSILON);
  }

  @Test
  void readsAModulatedInputFreshEachFrame() throws Exception {
    final FixedSource source = this.lx.engine.modulation.addModulator(new FixedSource(.25));
    this.lx.engine.modulation.addModulation(
      new LXCompoundModulation(this.lx.engine.modulation, source, this.trigOperator.input));
    this.lx.engine.modulation.modulations.get(0).range.setValue(1);

    this.trigOperator.function.setValue(TrigOperator.Function.ABS);
    this.trigOperator.freq.setValue(1);
    this.trigOperator.phase.setValue(0);
    this.trigOperator.gain.setValue(1);
    this.trigOperator.offset.setValue(0);
    this.trigOperator.loop(0);
    assertEquals(.25, this.trigOperator.getValue(), EPSILON);

    source.setNormalized(.75);
    this.trigOperator.loop(0);
    assertEquals(.75, this.trigOperator.getValue(), EPSILON);
  }

  @Test
  void registersTheCompleteParameterSurface() {
    assertEquals(this.trigOperator.input, this.trigOperator.getParameter("input"));
    assertEquals(this.trigOperator.function, this.trigOperator.getParameter("function"));
    assertEquals(this.trigOperator.freq, this.trigOperator.getParameter("freq"));
    assertEquals(this.trigOperator.phase, this.trigOperator.getParameter("phase"));
    assertEquals(this.trigOperator.gain, this.trigOperator.getParameter("gain"));
    assertEquals(this.trigOperator.offset, this.trigOperator.getParameter("offset"));
    assertEquals(LXParameter.Units.RADIANS, this.trigOperator.freq.getUnits());
    assertEquals(LXParameter.Units.RADIANS, this.trigOperator.phase.getUnits());
  }

  @Test
  void isANormalizedReadOnlyMappingSourceWithItsOwnUI() {
    this.trigOperator.input.setValue(.25);
    this.trigOperator.loop(0);
    assertEquals(this.trigOperator.getValue(), this.trigOperator.getNormalized(), EPSILON);
    assertThrows(UnsupportedOperationException.class,
      () -> this.trigOperator.setNormalized(.5));
    assertTrue(this.trigOperator instanceof UIModulatorControls);
  }

  /** A stationary source lets the test move the live modulation between frames. */
  private static class FixedSource extends LXModulator implements LXNormalizedParameter {

    private FixedSource(double value) {
      super("Source");
      setValue(value);
    }

    @Override
    protected double computeValue(double deltaMs) {
      return getValue();
    }

    @Override
    public LXNormalizedParameter setNormalized(double value) {
      setValue(value);
      return this;
    }

    @Override
    public double getNormalized() {
      return getValue();
    }
  }

}
