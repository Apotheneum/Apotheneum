package apotheneum.doved.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import heronarts.glx.GLXUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

// copy of heronarts.glx.GLXUtils.Image - but with constructor not being private
public class Image {
  private final int[] pixels;
  public final int width;
  public final int height;
  // public final int components;

  public Image(BufferedImage image, int width, int height) {
    this.width = width;
    this.height = height;

    this.pixels = new int[this.width * this.height];

    image.getRGB(0, 0, this.width, this.height, this.pixels, 0, this.width);
  }

  // return Image or null:
  // how can we make the below function signature allow us to return null?
  // optional Image return type:

  public static Image loadImage(String path) throws IOException {
    ByteBuffer imageBuffer = GLXUtils.loadFile(path);
    if (imageBuffer == null) {
      return null;
    }

    Throwable var2 = null;
    // Object var3 = null;

    try {
      MemoryStack stack = MemoryStack.stackPush();

      try {
        IntBuffer weightProp = stack.mallocInt(1);
        IntBuffer heightProp = stack.mallocInt(1);
        IntBuffer componentsProp = stack.mallocInt(1);
        ByteBuffer bytes = STBImage.stbi_load_from_memory(imageBuffer, weightProp, heightProp, componentsProp, 4);
        MemoryUtil.memFree(imageBuffer);
        if (bytes == null) {
          throw new IOException("STBI failed to load image data");
        } else {
          int width = weightProp.get(0);
          int height = heightProp.get(0);
          int componentsSize = componentsProp.get(0);
          int[] pixels = new int[width * height];

          for (int i = 0; i < width * height; ++i) {
            int ii = i << 2;
            byte r = bytes.get(ii);
            byte g = bytes.get(ii + 1);
            byte b = bytes.get(ii + 2);
            byte a = (componentsSize == 4) ? bytes.get(ii + 3) : (byte) 255; // Set alpha to 255 if RGB
            // byte a = bytes.get(ii + 3);
            pixels[i] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255;
          }

          STBImage.stbi_image_free(bytes);

          return new Image(width, height, pixels);
        }
      } finally {
        if (stack != null) {
          stack.close();
        }

      }
    } catch (Throwable var20) {
      if (var2 == null) {
        var2 = var20;
      } else if (var2 != var20) {
        var2.addSuppressed(var20);
      }
      throw var20;

    }
  }

  public Image(int width, int height, int[] pixels) throws IOException {
    this.width = width;
    this.height = height;
    this.pixels = pixels;
  }

  public int get(int x, int y) {
    return this.pixels[y * this.width + x];
  }

  public int getNormalized(float x, float y) {
    return this.get((int) (x * ((float) this.width - 0.5F)), (int) (y * ((float) this.height - 0.5F)));
  }

  public float getAspectRatio() {
    return (float) this.width / (float) this.height;
  }
}
