package apotheneum.doved.utils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class GifFrameExtractor {
  public static Image[] extractFrames(String gifFilePath) throws IOException {
    Image[] imageFrames = null;

    // get the gif image reader
    final ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
    // Create an ImageInputStream from the GIF file
    try (ImageInputStream is = ImageIO.createImageInputStream(new File(gifFilePath))) {
      reader.setInput(is);
      final int numFrames = reader.getNumImages(true);
      imageFrames = new Image[numFrames];

      BufferedImage master = null;
      BufferedImage previous = null;

      for (int i = 0; i < numFrames; i++) {
        BufferedImage image = reader.read(i);

        int width = image.getWidth();
        int height = image.getHeight();

        // we need to draw to a master, because gifs
        // only render the diffs of each frame
        if (master == null) {
          master = new BufferedImage(
              width,
              height,
              BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D g = master.createGraphics();

        // If this is not the first frame, draw the previous image onto the master image
        if (previous != null) {
          g.drawImage(previous, 0, 0, null);
        }

        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Copy the master image to the previous frame
        previous = new BufferedImage(
            master.getColorModel(),
            master.copyData(null),
            master.isAlphaPremultiplied(),
            null);

        // Convert BufferedImage to Image
        // Image image = new Image(master);

        // Add the image to the list
        imageFrames[i] = new Image(master, width, height);
      }
    }

    return imageFrames;
  }
}
