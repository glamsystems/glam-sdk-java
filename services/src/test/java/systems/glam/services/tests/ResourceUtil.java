package systems.glam.services.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class ResourceUtil {

  private ResourceUtil() {
  }

  public static byte[] readResource(final String resourcePath) throws IOException {
    try (final var in = ResourceUtil.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertNotNull(in, resourcePath + " not found on classpath");
      try {
        if (resourcePath.endsWith(".gz")) {
          try (final var gzin = new GZIPInputStream(in)) {
            return gzin.readAllBytes();
          }
        } else {
          return in.readAllBytes();
        }
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
