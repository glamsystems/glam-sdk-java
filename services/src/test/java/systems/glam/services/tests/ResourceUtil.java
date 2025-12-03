package systems.glam.services.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public final class ResourceUtil {

  private ResourceUtil() {
  }

  public static byte[] readResource(final String resourcePath) throws IOException {
    try (final var in = ResourceUtil.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertNotNull(in, "Resource scope/klend_reserves.json.json not found on classpath");
      try {
        if (resourcePath.endsWith(".zip")) {
          try (final var zin = new ZipInputStream(in)) {
            final var entry = zin.getNextEntry();
            if (entry == null) {
              fail("Zip resource has no entries: " + resourcePath);
            }
            return zin.readAllBytes();
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
