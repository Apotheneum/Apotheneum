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

/**
 * Registers the single, engine-owned {@link ApotheneumColor} on {@code lx.engine} at
 * {@link ApotheneumColor#PATH} — mirroring {@code apotheneum.video.ApotheneumVideoPlugin}'s
 * {@code getOrRegisterConfig}, the precedent this whole relocation is copied from. A plain
 * {@link LXPlugin} with no {@code heronarts.lx.studio.*} dependency, so the component (and every
 * colour-native pattern/{@code GradientMultiplyEffect} reading it) keeps working in a headless
 * runtime with no {@code glxstudio} on the classpath; the left-pane panel is the separate studio
 * companion, {@link ApotheneumColorUIPlugin}, exactly as video's core/UI plugins are split.
 */
@LXPlugin.Name("Apotheneum Color")
public class ApotheneumColorPlugin implements LXPlugin {

  @Override
  public void initialize(LX lx) {
    getOrRegisterConfig(lx);
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

  private static final String PREFIX = "[APOTHENEUM COLOR] ";

  static void log(String msg) {
    LX.log(PREFIX + msg);
  }

}
