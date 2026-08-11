package apotheneum.doved.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ColorizeMultiplyCatalogProvenanceTest {

  private static final String CATALOG_RESOURCE =
    "/catalog/apotheneum.doved.effects.ColorizeMultiplyEffect.md";
  private static final Path SOURCE_PATH =
    Path.of("src/main/java/apotheneum/doved/effects/ColorizeMultiplyEffect.java");

  @Test
  void catalogHashesMatchSourceAndCompiledClass() throws IOException, NoSuchAlgorithmException {
    String catalog = resourceText(CATALOG_RESOURCE);
    assertEquals(field(catalog, "sourceSha256"), sha256(Files.readAllBytes(SOURCE_PATH)));
    assertEquals(field(catalog, "classBytesSha256"), sha256(resourceBytes(
      "/apotheneum/doved/effects/ColorizeMultiplyEffect.class")));
  }

  @Test
  void catalogGenerationTimestampIsRealAndNotInTheFuture() throws IOException {
    Instant generatedAt = Instant.parse(field(resourceText(CATALOG_RESOURCE), "generatedAt"));
    assertTrue(generatedAt.isAfter(Instant.parse("2026-08-11T18:00:00Z")));
    assertFalse(generatedAt.isAfter(Instant.now()));
  }

  private static String field(String catalog, String name) {
    Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(name) + ": (\\S+)$").matcher(catalog);
    assertTrue(matcher.find(), "catalog field is present: " + name);
    return matcher.group(1);
  }

  private static String resourceText(String path) throws IOException {
    return new String(resourceBytes(path), StandardCharsets.UTF_8);
  }

  private static byte[] resourceBytes(String path) throws IOException {
    try (InputStream input = ColorizeMultiplyEffect.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Missing classpath resource: " + path);
      }
      return input.readAllBytes();
    }
  }

  private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
