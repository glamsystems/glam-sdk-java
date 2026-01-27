package systems.glam.services.io;

import software.sava.core.accounts.PublicKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public final class FileUtils {

  public static final String ACCOUNT_FILE_EXTENSION = ".dat";
  public static final String COMPRESSED_ACCOUNT_FILE_EXTENSION = ".dat.zip";

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

  public static AccountData readCompressedAccountData(final Path path) {
    final var fileName = path.getFileName().toString();
    if (fileName.endsWith(COMPRESSED_ACCOUNT_FILE_EXTENSION)) {
      try (final var zipFile = new ZipFile(path.toFile())) {
        final var entries = zipFile.entries();
        if (entries.hasMoreElements()) {
          final var entry = entries.nextElement();
          try (final var inputStream = zipFile.getInputStream(entry)) {
            return AccountData.createData(entry.getName(), inputStream.readAllBytes());
          }
        } else {
          return AccountData.EMPTY;
        }
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      return readAccountData(path);
    }
  }

  private FileUtils() {
  }
}
