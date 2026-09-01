package apotheneum.video;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure byte math on the outgoing frame — no LX instance needed, so this runs
 * without the headless engine fixture.
 */
class RawVideoServerTest {

  @Test
  void fullBrightnessLeavesEveryChannelUntouched() {
    final byte[] buffer = { 0, (byte) 0x7f, (byte) 0xff };
    RawVideoServer.scaleBrightness(buffer, 1.);
    assertArrayEquals(new byte[] { 0, (byte) 0x7f, (byte) 0xff }, buffer);
  }

  @Test
  void halfBrightnessHalvesEveryChannel() {
    final byte[] buffer = { 0, (byte) 0x80, (byte) 0xff };
    RawVideoServer.scaleBrightness(buffer, 0.5);
    assertArrayEquals(new byte[] { 0, 0x40, (byte) 0x7f }, buffer);
  }

  @Test
  void zeroBrightnessBlacksTheFrame() {
    final byte[] buffer = { (byte) 0xff, (byte) 0x80, 1 };
    RawVideoServer.scaleBrightness(buffer, 0.);
    assertArrayEquals(new byte[] { 0, 0, 0 }, buffer);
  }
}
