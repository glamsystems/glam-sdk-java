package systems.glam.services.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.SequencedCollection;

public final class ConfigUtils {

  public static Properties joinPropertyFiles(final SequencedCollection<String> propertyFiles) {
    return joinPropertyFilePaths(propertyFiles.stream().map(Path::of).toList());
  }

  public static Properties joinPropertyFilePaths(final SequencedCollection<Path> propertyFiles) {
    final var mergedProperties = new Properties();
    for (final var file : propertyFiles) {
      try (final var reader = Files.newBufferedReader(file)) {
        mergedProperties.load(reader);
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to read properties file: " + file, e);
      }
    }
    return mergedProperties;
  }


  private ConfigUtils() {
  }
}
