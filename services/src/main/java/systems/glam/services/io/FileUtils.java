package systems.glam.services.io;

import software.sava.core.accounts.PublicKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileUtils {

  public static final String ACCOUNT_FILE_EXTENSION = ".dat";

  public static Path resolveAccountPath(final Path path, final PublicKey pubKey) {
    return path.resolve(pubKey.toBase58() + ACCOUNT_FILE_EXTENSION);
  }

  public static AccountData readAccountData(final Path path) {
    final var fileName = path.getFileName().toString();
    if (fileName.endsWith(ACCOUNT_FILE_EXTENSION)) {
      try {
        return AccountData.createData(fileName, Files.readAllBytes(path));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      return AccountData.EMPTY;
    }
  }

  private FileUtils() {
  }
}
