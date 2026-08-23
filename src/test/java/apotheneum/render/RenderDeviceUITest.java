package apotheneum.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RenderDeviceUITest {

  @Test
  void clearsOnlyTheSelectedPatternsPreviousArtifacts(@TempDir Path directory) throws Exception {
    final Path png = Files.createFile(directory.resolve("Fireflies.png"));
    final Path json = Files.createFile(directory.resolve("Fireflies.json"));
    final Path otherPattern = Files.createFile(directory.resolve("CubeBlinks.png"));

    RenderDeviceUI.clearPreviousArtifacts(directory, "Fireflies");

    assertFalse(Files.exists(png));
    assertFalse(Files.exists(json));
    assertTrue(Files.exists(otherPattern));
  }
}
