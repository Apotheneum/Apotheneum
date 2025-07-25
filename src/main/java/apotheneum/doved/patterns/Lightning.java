package apotheneum.doved.patterns;

import apotheneum.ApotheneumRasterPattern;
import apotheneum.doved.lightning.LightningSegment;
import apotheneum.doved.lightning.MidpointDisplacementAlgorithm;
import apotheneum.doved.lightning.LSystemAlgorithm;
import apotheneum.doved.lightning.RRTAlgorithm;
import apotheneum.doved.lightning.PhysicallyBasedAlgorithm;
import apotheneum.doved.lightning.LightningGenerator;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.LXComponentName;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio.UI;
import heronarts.lx.studio.ui.device.UIDevice;
import heronarts.lx.studio.ui.device.UIDeviceControls;
import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.UI2dComponent;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

@LXCategory("Apotheneum/doved")
@LXComponentName("Lightning")
@LXComponent.Description("Lightning strikes using multiple algorithms")
public class Lightning extends ApotheneumRasterPattern
    implements ApotheneumRasterPattern.Midi, UIDeviceControls<Lightning> {

  public final TriggerParameter trig = new TriggerParameter("Trig", this::trig)
      .setDescription("Trigger a lightning strike");

<<<<<<< HEAD
  public final DiscreteParameter algorithm = new DiscreteParameter("Algorithm",
      new String[] { "Midpoint", "L-System", "RRT", "Physical" }, 0)
      .setDescription("Lightning generation algorithm");

  public final CompoundParameter intensity = new CompoundParameter("Intensity", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Overall brightness of lightning");

  public final CompoundParameter branchProbability = new CompoundParameter("Branch", 0.3, 0, 1)
      .setDescription("Likelihood of creating branches during displacement");

  public final CompoundParameter displacement = new CompoundParameter("Displace", 0.5, 0, 1)
      .setDescription("Maximum perpendicular displacement for midpoint algorithm");

  public final CompoundParameter recursionDepth = new CompoundParameter("Depth", 6, 3, 10)
      .setDescription("Maximum subdivision levels for detail");

  public final CompoundParameter fade = new CompoundParameter("Fade", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("External envelope control for lightning fade");

  public final CompoundParameter thickness = new CompoundParameter("Thickness", 1, 0.5, 3)
      .setDescription("Base thickness of lightning bolts");

  public final CompoundParameter startX = new CompoundParameter("Start X", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Starting X position across the top (0=left, 1=right)");

  public final CompoundParameter startSpread = new CompoundParameter("Start Spread", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("How spread out lightning start points are across the top");

  public final CompoundParameter endSpread = new CompoundParameter("End Spread", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("How spread out lightning end points are across the bottom");

  public final CompoundParameter branchDistance = new CompoundParameter("Branch Dist", 0.5, 0.1, 2)
      .setDescription("Maximum distance branches can extend from main bolt");

=======
  public enum Algorithm {
    MIDPOINT("Midpoint"),
    L_SYSTEM("L-System"),
    RRT("RRT"),
    PHYSICAL("Physical");

    private final String displayName;

    Algorithm(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public static String[] getDisplayNames() {
      Algorithm[] values = values();
      String[] names = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        names[i] = values[i].displayName;
      }
      return names;
    }
  }

  public final DiscreteParameter algorithm = new DiscreteParameter("Algorithm", Algorithm.getDisplayNames(), 0)
      .setDescription("Lightning generation algorithm");

  public final CompoundParameter intensity = new CompoundParameter("Intensity", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Overall brightness of lightning");

  public final CompoundParameter branchProbability = new CompoundParameter("Branch", 0.3, 0, 1)
      .setDescription("Likelihood of creating branches during displacement");

  public final CompoundParameter displacement = new CompoundParameter("Displace", 0.5, 0, 1)
      .setDescription("Maximum perpendicular displacement for midpoint algorithm");

  public final CompoundParameter recursionDepth = new CompoundParameter("Depth", 6, 3, 10)
      .setDescription("Maximum subdivision levels for detail");

  public final CompoundParameter fade = new CompoundParameter("Fade", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("External envelope control for lightning fade");

  public final CompoundParameter thickness = new CompoundParameter("Thickness", 1, 0.5, 3)
      .setDescription("Base thickness of lightning bolts");

  public final CompoundParameter startX = new CompoundParameter("Start X", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Starting X position across the top (0=left, 1=right)");

  public final CompoundParameter startSpread = new CompoundParameter("Start Spread", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("How spread out lightning start points are across the top");

  public final CompoundParameter endSpread = new CompoundParameter("End Spread", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("How spread out lightning end points are across the bottom");

  public final CompoundParameter branchDistance = new CompoundParameter("Branch Dist", 0.5, 0.1, 2)
      .setDescription("Maximum distance branches can extend from main bolt");

>>>>>>> main
  public final CompoundParameter branchAngle = new CompoundParameter("Branch Angle", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("How much branches can deviate from the main bolt direction");

  // L-System specific parameters
  public final CompoundParameter lsIterations = new CompoundParameter("LS Iterations", 4, 2, 8)
      .setDescription("Number of L-system iterations");

  public final CompoundParameter lsSegmentLength = new CompoundParameter("LS Segment Len", 8, 2, 20)
      .setDescription("Base length of L-system segments");

  public final CompoundParameter lsAngleVariation = new CompoundParameter("LS Angle Var", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Random variation in L-system angles");

  public final CompoundParameter lsLengthVariation = new CompoundParameter("LS Length Var", 0.3, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Random variation in L-system segment lengths");

  public final CompoundParameter lsBranchAngle = new CompoundParameter("LS Branch Angle", 45, 15, 90)
      .setDescription("Base angle for L-system branches in degrees");

  public final CompoundParameter bleeding = new CompoundParameter("Bleeding", 1, 0, 3)
      .setDescription("Glow/bleeding effect strength for all algorithms");

  // RRT specific parameters
  public final CompoundParameter rrtStepSize = new CompoundParameter("RRT Step Size", 12, 5, 25)
      .setDescription("Distance of each RRT tree extension");

  public final CompoundParameter rrtGoalBias = new CompoundParameter("RRT Goal Bias", 0.1, 0, 0.5)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Probability of sampling from goal region");

  public final CompoundParameter rrtMaxIterations = new CompoundParameter("RRT Max Iter", 150, 50, 300)
      .setDescription("Maximum number of RRT tree extensions");

  public final CompoundParameter rrtJaggedness = new CompoundParameter("RRT Jaggedness", 0.3, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Amount of random perpendicular displacement");

  public final CompoundParameter rrtGoalRadius = new CompoundParameter("RRT Goal Radius", 20, 10, 50)
      .setDescription("Size of target region considered reached");

  public final CompoundParameter rrtElectricalField = new CompoundParameter("RRT Elec Field", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Electrical field strength for biased sampling");

  // Physically-based parameters
  public final CompoundParameter electricPotential = new CompoundParameter("Electric Potential", 0.8, 0.1, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Initial electric potential of the cloud");

  public final CompoundParameter stepLength = new CompoundParameter("Step Length", 8, 2, 20)
      .setDescription("Length of each stepped leader step");

  public final CompoundParameter maxSteps = new CompoundParameter("Max Steps", 100, 50, 200)
      .setDescription("Maximum number of stepped leader steps");

  public final CompoundParameter physicalBranching = new CompoundParameter("Physical Branch", 0.3, 0, 0.8)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Probability of stepped leader branching");

  public final CompoundParameter stepAngleVariation = new CompoundParameter("Step Angle Var", 0.5, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Random variation in stepped leader direction");

  public final CompoundParameter chargeDecay = new CompoundParameter("Charge Decay", 0.02, 0, 0.1)
      .setDescription("Rate of charge decay along stepped leader");

  private static class LightningBolt {
    public final List<LightningSegment> segments = new ArrayList<>();

    public LightningBolt() {
      // Lightning bolts are now controlled by external envelope, no internal timing
    }
  }

  private final List<LightningBolt> bolts = new ArrayList<>();

  public Lightning(LX lx) {
    super(lx);
    addParameter("trig", this.trig);
    addParameter("algorithm", this.algorithm);
    addParameter("intensity", this.intensity);
    addParameter("branchProbability", this.branchProbability);
    addParameter("displacement", this.displacement);
    addParameter("recursionDepth", this.recursionDepth);
    addParameter("fade", this.fade);
    addParameter("thickness", this.thickness);
    addParameter("startX", this.startX);
    addParameter("startSpread", this.startSpread);
    addParameter("endSpread", this.endSpread);
    addParameter("branchDistance", this.branchDistance);
    addParameter("branchAngle", this.branchAngle);
    addParameter("lsIterations", this.lsIterations);
    addParameter("lsSegmentLength", this.lsSegmentLength);
    addParameter("lsAngleVariation", this.lsAngleVariation);
    addParameter("lsLengthVariation", this.lsLengthVariation);
    addParameter("lsBranchAngle", this.lsBranchAngle);
    addParameter("bleeding", this.bleeding);
    addParameter("rrtStepSize", this.rrtStepSize);
    addParameter("rrtGoalBias", this.rrtGoalBias);
    addParameter("rrtMaxIterations", this.rrtMaxIterations);
    addParameter("rrtJaggedness", this.rrtJaggedness);
    addParameter("rrtGoalRadius", this.rrtGoalRadius);
    addParameter("rrtElectricalField", this.rrtElectricalField);
    addParameter("electricPotential", this.electricPotential);
    addParameter("stepLength", this.stepLength);
    addParameter("maxSteps", this.maxSteps);
    addParameter("physicalBranching", this.physicalBranching);
    addParameter("stepAngleVariation", this.stepAngleVariation);
    addParameter("chargeDecay", this.chargeDecay);
  }

  private void trig() {
    // Clear existing bolts and create new one (single bolt controlled by envelope)
    bolts.clear();
    LightningBolt bolt = new LightningBolt();

    // Generate lightning using the interface
    LightningGenerator generator = getLightningGenerator();
    Object params = getAlgorithmParameters();
    generator.generateLightning(bolt.segments, params);

    bolts.add(bolt);
  }

  @Override
  protected void render(double deltaMs, Graphics2D graphics) {
    clear();

    // Use fade envelope value directly
    double fadeAmount = fade.getValue();

    for (LightningBolt bolt : bolts) {
      renderBolt(graphics, bolt, fadeAmount);
    }
  }

  private void renderBolt(Graphics2D graphics, LightningBolt bolt, double fadeAmount) {
    double intensityValue = intensity.getValue();
    double thicknessValue = thickness.getValue();
    double bleedingValue = bleeding.getValue();

    LightningGenerator generator = getLightningGenerator();
    generator.render(graphics, bolt.segments, fadeAmount, intensityValue, thicknessValue, bleedingValue);
  }

  private LightningGenerator getLightningGenerator() {
    Algorithm selectedAlgorithm = Algorithm.values()[algorithm.getValuei()];
    switch (selectedAlgorithm) {
      case MIDPOINT:
        return new MidpointDisplacementAlgorithm();
      case L_SYSTEM:
        return new LSystemAlgorithm();
      case RRT:
        return new RRTAlgorithm();
      case PHYSICAL:
        return new PhysicallyBasedAlgorithm();
      default:
        return new MidpointDisplacementAlgorithm();
    }
  }

  private Object getAlgorithmParameters() {
    Algorithm selectedAlgorithm = Algorithm.values()[algorithm.getValuei()];
    switch (selectedAlgorithm) {
      case MIDPOINT:
        return new MidpointDisplacementAlgorithm.Parameters(
            displacement.getValue(),
            (int) recursionDepth.getValue(),
            startX.getValue(),
            startSpread.getValue(),
            endSpread.getValue(),
            branchProbability.getValue(),
            branchDistance.getValue(),
            branchAngle.getValue(),
            RASTER_WIDTH,
            RASTER_HEIGHT);
      case L_SYSTEM:
        return new LSystemAlgorithm.Parameters(
            (int) lsIterations.getValue(),
            lsSegmentLength.getValue(),
            lsAngleVariation.getValue(),
            lsLengthVariation.getValue(),
            lsBranchAngle.getValue(),
            startX.getValue(),
            RASTER_WIDTH,
            RASTER_HEIGHT);
      case RRT:
        return new RRTAlgorithm.Parameters(
            rrtStepSize.getValue(),
            rrtGoalBias.getValue(),
            (int) rrtMaxIterations.getValue(),
            branchProbability.getValue(),
            rrtJaggedness.getValue(),
            rrtGoalRadius.getValue(),
            rrtElectricalField.getValue(),
            startX.getValue(),
            RASTER_WIDTH,
            RASTER_HEIGHT);
      case PHYSICAL:
        return new PhysicallyBasedAlgorithm.Parameters(
            electricPotential.getValue(),
            stepLength.getValue(),
            (int) maxSteps.getValue(),
            physicalBranching.getValue(),
            stepAngleVariation.getValue() * Math.PI, // Convert to radians
            chargeDecay.getValue(),
            10.0, // Connection distance
            startX.getValue(),
            RASTER_WIDTH,
            RASTER_HEIGHT);
      default:
        return new MidpointDisplacementAlgorithm.Parameters(
            displacement.getValue(),
            (int) recursionDepth.getValue(),
            startX.getValue(),
            startSpread.getValue(),
            endSpread.getValue(),
            branchProbability.getValue(),
            branchDistance.getValue(),
            branchAngle.getValue(),
            RASTER_WIDTH,
            RASTER_HEIGHT);
    }
  }

  @Override
  public void noteOnReceived(MidiNoteOn midiNote) {
    trig();
  }

  @Override
  public void buildDeviceControls(UI ui, UIDevice uiDevice, Lightning lightning) {
    uiDevice.setLayout(UIDevice.Layout.HORIZONTAL, 6);

    // Always show trigger and algorithm controls
    addColumn(uiDevice, "Trigger",
        newButton(lightning.trig).setTriggerable(true).setBorderRounding(4),
        newDropMenu(lightning.algorithm).setTopMargin(6),
        newKnob(lightning.intensity).setTopMargin(6)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Always show common settings
    addColumn(uiDevice, "Settings",
        newKnob(lightning.startX),
        newKnob(lightning.fade),
        newKnob(lightning.thickness)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    // Create all algorithm-specific controls but manage their visibility
    final UI2dComponent midpointBreak1 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer midpointCol1 = addColumn(uiDevice, "Midpoint",
        newKnob(lightning.displacement),
        newKnob(lightning.recursionDepth),
        newKnob(lightning.startSpread)).setChildSpacing(6);

    final UI2dComponent midpointBreak2 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer midpointCol2 = addColumn(uiDevice, "M-Spread",
        newKnob(lightning.endSpread),
        newKnob(lightning.branchProbability),
        newKnob(lightning.branchDistance)).setChildSpacing(6);

    final UI2dComponent midpointBreak3 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer midpointCol3 = addColumn(uiDevice, "M-Branch",
        newKnob(lightning.branchAngle)).setChildSpacing(6);

    final UI2dComponent lsystemBreak1 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer lsystemCol1 = addColumn(uiDevice, "L-System",
        newKnob(lightning.lsIterations),
        newKnob(lightning.lsSegmentLength),
        newKnob(lightning.lsBranchAngle)).setChildSpacing(6);

    final UI2dComponent lsystemBreak2 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer lsystemCol2 = addColumn(uiDevice, "LS-Variation",
        newKnob(lightning.lsAngleVariation),
        newKnob(lightning.lsLengthVariation)).setChildSpacing(6);

    final UI2dComponent rrtBreak1 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer rrtCol1 = addColumn(uiDevice, "RRT",
        newKnob(lightning.rrtStepSize),
        newKnob(lightning.rrtGoalBias),
        newKnob(lightning.rrtMaxIterations)).setChildSpacing(6);

    final UI2dComponent rrtBreak2 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer rrtCol2 = addColumn(uiDevice, "RRT-Advanced",
        newKnob(lightning.rrtJaggedness),
        newKnob(lightning.rrtGoalRadius),
        newKnob(lightning.rrtElectricalField)).setChildSpacing(6);

    final UI2dComponent physicalBreak1 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer physicalCol1 = addColumn(uiDevice, "Physical",
        newKnob(lightning.electricPotential),
        newKnob(lightning.stepLength),
        newKnob(lightning.maxSteps)).setChildSpacing(6);

    final UI2dComponent physicalBreak2 = addVerticalBreak(ui, uiDevice);
    final UI2dContainer physicalCol2 = addColumn(uiDevice, "P-Stepped",
        newKnob(lightning.physicalBranching),
        newKnob(lightning.stepAngleVariation),
        newKnob(lightning.chargeDecay)).setChildSpacing(6);

    // Always show visual and face controls
    addColumn(uiDevice, "Visual",
        newKnob(lightning.bleeding)).setChildSpacing(6);

    addVerticalBreak(ui, uiDevice);

    addColumn(uiDevice, "Faces",
        buildFaceControls(ui, uiDevice, 80));

    // Add listener to show/hide algorithm-specific controls
    uiDevice.addListener(lightning.algorithm, p -> {
      Algorithm selectedAlgorithm = Algorithm.values()[lightning.algorithm.getValuei()];

      // Midpoint controls
      boolean showMidpoint = (selectedAlgorithm == Algorithm.MIDPOINT);
      midpointBreak1.setVisible(showMidpoint);
      midpointCol1.setVisible(showMidpoint);
      midpointBreak2.setVisible(showMidpoint);
      midpointCol2.setVisible(showMidpoint);
      midpointBreak3.setVisible(showMidpoint);
      midpointCol3.setVisible(showMidpoint);

      // L-System controls
      boolean showLSystem = (selectedAlgorithm == Algorithm.L_SYSTEM);
      lsystemBreak1.setVisible(showLSystem);
      lsystemCol1.setVisible(showLSystem);
      lsystemBreak2.setVisible(showLSystem);
      lsystemCol2.setVisible(showLSystem);

      // RRT controls
      boolean showRRT = (selectedAlgorithm == Algorithm.RRT);
      rrtBreak1.setVisible(showRRT);
      rrtCol1.setVisible(showRRT);
      rrtBreak2.setVisible(showRRT);
      rrtCol2.setVisible(showRRT);

      // Physical controls
      boolean showPhysical = (selectedAlgorithm == Algorithm.PHYSICAL);
      physicalBreak1.setVisible(showPhysical);
      physicalCol1.setVisible(showPhysical);
      physicalBreak2.setVisible(showPhysical);
      physicalCol2.setVisible(showPhysical);
    }, true);
  }

}