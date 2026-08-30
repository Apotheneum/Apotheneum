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

package apotheneum.doved;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPlugin;

import apotheneum.doved.modulators.ApotheneumColor;
import apotheneum.doved.modulators.ApotheneumGradient;

/**
 * Registers this codebase's engine-owned "doved" global singletons on {@code lx.engine}: {@link
 * ApotheneumColor} at {@link ApotheneumColor#PATH} and {@link ApotheneumGradient} at {@link
 * ApotheneumGradient#PATH} — mirroring {@code apotheneum.video.ApotheneumVideoPlugin}'s {@code
 * getOrRegisterConfig}, the precedent this whole relocation is copied from. {@link
 * ApotheneumGradient} was added to this same plugin rather than getting a plugin of its own so
 * that enabling "Apotheneum Color" is the one on/off switch for every global singleton this
 * package registers, not the first of an ever-growing list of plugin pairs a performer has to
 * separately enable to get a working panel — see {@link ApotheneumGradient}'s class javadoc.
 * A plain {@link LXPlugin} with no {@code heronarts.lx.studio.*} dependency, so both components
 * (and every colour-native pattern/{@code GradientMultiplyEffect} reading them) keep working in
 * a headless runtime with no {@code glxstudio} on the classpath; the left-pane panels are the
 * separate studio companion, {@link ApotheneumColorUIPlugin}, exactly as video's core/UI plugins
 * are split. <b>Both the core plugin (this class) and the UI plugin must be enabled in Chromatik
 * for the GLOBAL-tab panels to appear</b> — the core plugin registers the components headlessly,
 * the UI plugin builds the panels that show and drive them, and neither alone produces a visible
 * result.
 */
@LXPlugin.Name("Apotheneum Color")
public class ApotheneumColorPlugin implements LXPlugin {

  @Override
  public void initialize(LX lx) {
    getOrRegisterConfig(lx);
    getOrRegisterGradient(lx);
  }

  @Override
  public void dispose() {
    // The component is an engine child; LX owns its eventual disposal, exactly as
    // ApotheneumVideoPlugin's own comment notes for the same reason.
  }

  /**
   * Components registered on the engine outlive an individual plugin enable cycle. LX exposes
   * registration but no corresponding child removal API, so re-use the canonical component when
   * the plugin is disabled and re-enabled rather than replacing it and leaking its parameter/
   * listener graph -- identical reasoning to {@code ApotheneumVideoPlugin.getOrRegisterConfig}.
   */
  public static ApotheneumColor getOrRegisterConfig(LX lx) {
    final LXComponent existing = lx.engine.getChild(ApotheneumColor.PATH);
    if (existing == null) {
      final ApotheneumColor config = new ApotheneumColor(lx);
      lx.engine.registerComponent(ApotheneumColor.PATH, config);
      return config;
    }
    if (existing instanceof ApotheneumColor) {
      return (ApotheneumColor) existing;
    }
    throw new IllegalStateException(
      "Engine child '" + ApotheneumColor.PATH + "' is not an ApotheneumColor: "
      + existing.getClass().getName());
  }

  /** {@link #getOrRegisterConfig}'s counterpart for {@link ApotheneumGradient}. */
  public static ApotheneumGradient getOrRegisterGradient(LX lx) {
    final LXComponent existing = lx.engine.getChild(ApotheneumGradient.PATH);
    if (existing == null) {
      final ApotheneumGradient gradient = new ApotheneumGradient(lx);
      lx.engine.registerComponent(ApotheneumGradient.PATH, gradient);
      return gradient;
    }
    if (existing instanceof ApotheneumGradient) {
      return (ApotheneumGradient) existing;
    }
    throw new IllegalStateException(
      "Engine child '" + ApotheneumGradient.PATH + "' is not an ApotheneumGradient: "
      + existing.getClass().getName());
  }

  private static final String PREFIX = "[APOTHENEUM COLOR] ";

  static void log(String msg) {
    LX.log(PREFIX + msg);
  }

}
