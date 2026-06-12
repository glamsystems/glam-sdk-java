package systems.glam.services.io;

import software.sava.core.accounts.PublicKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.nio.file.Files.newOutputStream;
import static java.nio.file.StandardOpenOption.*;

public final class FileUtils {

  public static final String ACCOUNT_FILE_EXTENSION = ".dat";
  public static final String COMPRESSED_ACCOUNT_FILE_EXTENSION = ".dat.gz";

  public static Path resolveAccountPath(final Path path, final PublicKey pubKey) {
    return path.resolve(pubKey.toBase58() + ACCOUNT_FILE_EXTENSION);
  }

  public static Path resolveCompressedAccountPath(final Path path, final PublicKey pubKey) {
    return path.resolve(pubKey.toBase58() + COMPRESSED_ACCOUNT_FILE_EXTENSION);
  }

  public static PublicKey parseKey(final Path file) {
    final var fileName = file.getFileName().toString();
    if (fileName.endsWith(COMPRESSED_ACCOUNT_FILE_EXTENSION)) {
      final var keyString = fileName.substring(0, fileName.length() - COMPRESSED_ACCOUNT_FILE_EXTENSION.length());
      return PublicKey.fromBase58Encoded(keyString);
    } else if (fileName.endsWith(ACCOUNT_FILE_EXTENSION)) {
      final var keyString = fileName.substring(0, fileName.length() - ACCOUNT_FILE_EXTENSION.length());
      return PublicKey.fromBase58Encoded(keyString);
    } else {
      return null;
    }
  }

  public static AccountData readAccountData(final Path path) {
    final var fileName = path.getFileName().toString();
    if (fileName.endsWith(COMPRESSED_ACCOUNT_FILE_EXTENSION)) {
      final var entryName = fileName.substring(0, fileName.length() - ".gz".length());
      try (final var gzipInputStream = new GZIPInputStream(Files.newInputStream(path))) {
        return AccountData.createData(entryName, gzipInputStream.readAllBytes());
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else if (fileName.endsWith(ACCOUNT_FILE_EXTENSION)) {
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

  public static void compressIfNeeded(final Path directory, final Path path, final AccountData accountData) {
    if (!path.getFileName().toString().endsWith(".gz")) {
      try {
        final var compressedPath = resolveCompressedAccountPath(directory, accountData.pubKey());
        try (final var out = new GZIPOutputStream(newOutputStream(compressedPath, CREATE, TRUNCATE_EXISTING, WRITE))) {
          out.write(accountData.data());
        }
        Files.delete(path);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public static void writeCompressedAccountData(final Path directory,
                                                final PublicKey key,
                                                final byte[] data) throws IOException {
    final var compressedPath = resolveCompressedAccountPath(directory, key);
    try (final var out = new GZIPOutputStream(newOutputStream(compressedPath, CREATE, TRUNCATE_EXISTING, WRITE))) {
      out.write(data);
    }
  }
}
