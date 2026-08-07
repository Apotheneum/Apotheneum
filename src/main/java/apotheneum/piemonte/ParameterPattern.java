/**
 * Copyright 2026- Heron Arts LLC
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
 * Created by patrick piemonte
 *
 * ParameterPattern — an intermediate base (ApotheneumPattern → ParameterPattern
 * → your pattern) that fixes a consistent leading parameter order: hue,
 * speed, size, wow. Every pattern that extends it gets those four controls in
 * the same place. Subclasses extend this, then add their own parameters (and
 * any extra colors) after super(...). Kept self-contained so
 * last-minute changes don't ripple across other authors' content.
 *
 * "Wow" is a shared performance macro in the spirit of LXStudio-TE's WOW knobs:
 * a 0..1 "extra flourish" each pattern interprets in its own way. It defaults to
 * 0 (inert), so a pattern that ignores it is unaffected and its resting look is
 * unchanged; patterns that read getWow() add a special-effect layer as it rises.
 */

package apotheneum.piemonte;

import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

public abstract class ParameterPattern extends ApotheneumPattern {

  /** Shared render-target selector: both structures, cube only, or cylinder only. */
  public enum Target {
    BOTH,
    CUBE,
    CYLINDER
  }

  /** Created by addTargetParameter(); null when a pattern doesn't opt in. */
  public EnumParameter<Target> target;

  /** Primary hue (degrees) — the single dial that steers pattern color. */
  public final CompoundParameter hue =
    new CompoundParameter("Hue", 0, 0, 360)
    .setDescription("Primary hue")
    .setWrappable(true);

  /** Animation speed. Range/polarity set per-subclass via the constructor. */
  public final CompoundParameter speed;

  /** Element size. Range set per-subclass via the constructor. */
  public final CompoundParameter size;

  /** Shared "wow" performance macro (0..1). Defaults to 0 (inert). */
  public final CompoundParameter wow =
    new CompoundParameter("Wow", 0, 0, 1)
    .setDescription("Extra flourish / special-effect intensity");

  /**
   * External audio inputs. Nothing in these patterns samples the LX audio
   * meter — installation volume is expected to change, which would rescale
   * every internally-analyzed level. Instead the envelope-follower step runs
   * in the audio program (e.g. Ableton feeding the show-control M4L device),
   * which sends a volume-stable signal here over OSC/MIDI modulation.
   */
  public final CompoundParameter level =
    new CompoundParameter("Level", 0, 0, 1)
    .setDescription("Audio level input — map an external envelope follower (OSC/MIDI)");

  public final BooleanParameter pulse =
    new BooleanParameter("Pulse", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Beat trigger input — map an external beat source (OSC/MIDI)");

  // --- audio state derived from the external inputs, once per frame ---
  private boolean prevPulse;
  private double prevLevel, sinceBeatMs = 1e9;
  /** Slow-average of the level input; useful for contrast ratios. */
  protected double levelAvg;
  /** True on the frame a beat fires (Pulse edge, or a Level onset). */
  protected boolean beat;
  /** Normalized beat strength 0..1 for the current frame. */
  protected double beatLevel;
  /** Level input shaped by a 25ms attack / 220ms release envelope. */
  protected double levelEnv;
  /** Accumulated pattern time in ms. */
  protected double timeMs = 0;

  /** Defaults: bipolar speed -1..1 (0.4), size 0..1 (0.5). */
  protected ParameterPattern(LX lx) {
    this(lx, 0.4, -1, 1, 0.5, 0, 1);
  }

  /**
   * CompoundParameter ranges are immutable after construction, so subclasses
   * pass their desired speed/size ranges here. Speed becomes bipolar when its
   * minimum is negative.
   */
  protected ParameterPattern(LX lx,
      double speedDef, double speedMin, double speedMax,
      double sizeDef, double sizeMin, double sizeMax) {
    super(lx);

    this.speed = new CompoundParameter("Speed", speedDef, speedMin, speedMax)
      .setDescription("Animation speed");
    if (speedMin < 0) {
      this.speed.setPolarity(CompoundParameter.Polarity.BIPOLAR);
    }
    this.size = new CompoundParameter("Size", sizeDef, sizeMin, sizeMax)
      .setDescription("Element size");

    // Canonical leading order: hue, speed, size, wow. Subclasses add the rest.
    addParameter("hue", this.hue);
    addParameter("speed", this.speed);
    addParameter("size", this.size);
    addParameter("wow", this.wow);
    addParameter("level", this.level);
    addParameter("pulse", this.pulse);
  }

  /** Subclasses call this LAST in their constructor to keep Target at the end. */
  protected void addTargetParameter() {
    addTargetParameter(Target.BOTH);
  }

  /** Target with a pattern-specific default. */
  protected void addTargetParameter(Target def) {
    this.target = new EnumParameter<Target>("Target", def)
      .setDescription("Which structures to render to");
    addParameter("target", this.target);
  }

  /** Current target; BOTH when the pattern doesn't expose the selector. */
  protected Target getTarget() {
    return (this.target != null) ? this.target.getEnum() : Target.BOTH;
  }

  /** Beat threshold multiplier for Level onsets; override to wire a Sens param. */
  protected double beatThreshold() {
    return 1.4;
  }

  /**
   * Advance the derived audio state from the external Level/Pulse inputs.
   * StrandPattern calls this automatically each frame; patterns that extend
   * ParameterPattern directly call it at the top of render().
   */
  protected void stepAudio(double deltaMs) {
    this.timeMs += deltaMs;
    final double lvl = this.level.getValue();

    // attack/release envelope + slow average for contrast ratios
    final double alpha = (lvl > this.levelEnv)
      ? 1 - Math.exp(-deltaMs / 25.0)
      : 1 - Math.exp(-deltaMs / 220.0);
    this.levelEnv += (lvl - this.levelEnv) * alpha;
    this.levelAvg += (lvl - this.levelAvg) * (1 - Math.exp(-deltaMs / 2000.0));

    // beats: a Pulse edge always fires; Level onsets fire when it climbs
    // clear of its own slow average (volume-stable, since the follower is
    // upstream of any installation volume changes)
    this.sinceBeatMs += deltaMs;
    final boolean p = this.pulse.isOn();
    final boolean pulseEdge = p && !this.prevPulse;
    this.prevPulse = p;
    final boolean onset = (lvl > this.levelAvg * beatThreshold())
      && (lvl > this.prevLevel)
      && (this.sinceBeatMs >= 130)
      && (lvl > 0.01);
    this.prevLevel = lvl;
    this.beat = pulseEdge || onset;
    if (this.beat) {
      this.sinceBeatMs = 0;
    }
    this.beatLevel = pulseEdge ? 1.0
      : LXUtils.clamp((lvl / Math.max(1e-4, this.levelAvg) - 1.0) / 1.5, 0, 1);
  }

  /**
   * Pseudo-spectrum: the level envelope spread across n bands with slow
   * per-band drift, so band-indexed visuals keep independent motion without
   * sampling the internal meter.
   */
  protected double band(int i, int n) {
    final double drift = 0.5 + 0.5 * Math.sin(
      this.timeMs * (0.0009 + 0.0008 * phash(i * 31 + n * 7)) + i * 2.1);
    return this.levelEnv * (0.45 + 0.55 * drift);
  }

  private boolean prevPulseLocal;

  /** True exactly once per Pulse press; for patterns running bespoke detectors. */
  protected boolean pulseHit() {
    final boolean p = this.pulse.isOn();
    final boolean edge = p && !this.prevPulseLocal;
    this.prevPulseLocal = p;
    return edge;
  }

  private static double phash(int n) {
    int h = n * 374761393;
    h = (h ^ (h >>> 13)) * 1103515245;
    h ^= (h >>> 16);
    return (h & 0x7fffffff) / (double) 0x7fffffff;
  }

  // Convenience getters. Subclasses can also tweak the
  // inherited params directly, e.g. this.speed.setExponent(2) or
  // this.size.setUnits(LXParameter.Units.INTEGER), in their constructor.

  /** Resolved primary color for this frame — the Hue knob at full strength. */
  protected int getColor() {
    return LXColor.hsb(this.hue.getValuef() % 360, 100, 100);
  }

  /** Current speed value (bipolar when the configured minimum is negative). */
  protected double getSpeed() {
    return this.speed.getValue();
  }

  /** Current size value. */
  protected double getSize() {
    return this.size.getValue();
  }

  /** Current "wow" macro value (0..1); 0 means the flourish is off. */
  protected double getWow() {
    return this.wow.getValue();
  }
}
