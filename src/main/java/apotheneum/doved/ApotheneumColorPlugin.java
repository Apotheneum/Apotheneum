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
    if (isStaleReload(existing, ApotheneumColor.class)) {
      warnStaleReload(ApotheneumColor.PATH);
      return null;
    }
    throw new IllegalStateException(
      "Engine child '" + ApotheneumColor.PATH + "' is not an ApotheneumColor: "
      + existing.getClass().getName());
  }

  /**
   * Whether {@code existing} is this same class from a <em>previous</em> load of the package —
   * the signature of a reinstall over a running Chromatik.
   *
   * <p>Reinstalling the package builds new classes under a new classloader, but the component
   * registered at the engine path is still the instance the old classloader made. Its class has
   * the identical name and is a completely different {@code Class} object, so {@code instanceof}
   * against the newly-loaded type is false and every {@code ApotheneumColor.get} returns null —
   * which is why colour silently falls back to neutral white after a reinstall and comes back
   * on the next restart.
   */
  private static boolean isStaleReload(LXComponent existing, Class<?> expected) {
    return existing.getClass().getName().equals(expected.getName());
  }

  /**
   * Says what happened and what to do about it, once, instead of throwing.
   *
   * <p>There is no fix available from here. {@code LXEngine.registerComponent} only adds — LX
   * exposes no child removal, and {@code LXComponent.children} is an unmodifiable view — so the
   * stale component cannot be swapped out for a fresh one. Reaching into the private map by
   * reflection would technically work and is deliberately not done: a live project's modulation
   * mappings address these parameters through this exact instance, so replacing it mid-session
   * would quietly break a performer's wiring to fix a message.
   *
   * <p>Throwing was worse than the problem. It aborted plugin initialization, so the failure
   * arrived as a stack trace about a type mismatch rather than as "you reinstalled; restart" —
   * and the neutral-white fallback that follows is already a defined, survivable state.
   */
  private static void warnStaleReload(String path) {
    LX.error(new IllegalStateException("stale " + path),
      PREFIX + "the package was reinstalled while Chromatik was running, so '" + path
      + "' is still the instance the previous build registered. Colour will resolve neutral "
      + "white until Chromatik is restarted. This is a reinstall-only condition; nothing is "
      + "wrong with the project.");
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
    if (isStaleReload(existing, ApotheneumGradient.class)) {
      warnStaleReload(ApotheneumGradient.PATH);
      return null;
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
