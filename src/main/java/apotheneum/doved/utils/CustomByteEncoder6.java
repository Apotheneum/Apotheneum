package apotheneum.doved.utils;

import heronarts.lx.output.LXOutput.GammaTable.Curve;

public class CustomByteEncoder6 {

  /*
   * Static methods getNumBytes() and writeBytes(...)
   * need to match the interface LXBufferOutput.ByteEncoder
   */

  /**
   * How many bytes should be written per point?
   */
  public static int getNumBytes() {
    return 6;
  }

  static int rOffset = 0;
  static int gOffset = 1;
  static int bOffset = 2;
  static int xOffset = 3;
  static int xxOffset = 4;
  static int xxxOffset = 5;

  /**
   * Convert a single point to output bytes. Pad with any control values as
   * needed.
   * This is called per-point, not per-fixture.
   */
  public static void writeBytes(int color, Curve gamma, byte[] output, int offset) {
    final int r = ((color >> 16) & 0xff);
    final int g = ((color >> 8) & 0xff);
    final int b = (color & 0xff);

    output[offset + rOffset] = gamma.red[r];
    output[offset + gOffset] = gamma.green[g];
    output[offset + bOffset] = gamma.blue[b];
    output[offset + xOffset] = (byte) (((int) (gamma.red[r] + gamma.green[g] + gamma.blue[b])) / 3);
    output[offset + xxOffset] = 0;
    output[offset + xxxOffset] = 0;
  }

}
