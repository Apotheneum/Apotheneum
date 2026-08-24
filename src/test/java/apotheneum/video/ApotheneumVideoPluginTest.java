package apotheneum.video;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import apotheneum.HeadlessLxTest;
import heronarts.lx.LX;

class ApotheneumVideoPluginTest extends HeadlessLxTest {

  @Test
  void reusesTheRegisteredConfigAcrossPluginEnableCycles() {
    final LX lx = newHeadlessLx();
    final String previousPort = System.getProperty("apotheneum.video.port");
    System.setProperty("apotheneum.video.port", "0");

    final ApotheneumVideoPlugin plugin = new ApotheneumVideoPlugin();
    try {
      plugin.initialize(lx);
      final ApotheneumVideo first = (ApotheneumVideo) lx.engine.getChild(ApotheneumVideo.PATH);
      plugin.dispose();

      plugin.initialize(lx);
      final ApotheneumVideo second = (ApotheneumVideo) lx.engine.getChild(ApotheneumVideo.PATH);

      assertSame(first, second);
      assertSame(first, ApotheneumVideoPlugin.getOrRegisterConfig(lx));
    } finally {
      plugin.dispose();
      if (previousPort == null) {
        System.clearProperty("apotheneum.video.port");
      } else {
        System.setProperty("apotheneum.video.port", previousPort);
      }
    }
  }
}
