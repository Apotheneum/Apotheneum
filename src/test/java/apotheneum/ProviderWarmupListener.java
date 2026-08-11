package apotheneum;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Initializes the macOS audio and MIDI providers serially before headless LX tests.
 * LX starts asynchronous device discovery, which can otherwise race the JVM's first
 * sound-provider initialization and deadlock on opposing class-initialization locks.
 */
public class ProviderWarmupListener implements LauncherSessionListener {

  @Override
  public void launcherSessionOpened(LauncherSession session) {
    try {
      javax.sound.midi.MidiSystem.getMidiDeviceInfo();
      javax.sound.sampled.AudioSystem.getMixerInfo();
    } catch (Throwable ignored) {
      // Soundless runners may throw after the classloading side effect we need.
    }
  }
}
