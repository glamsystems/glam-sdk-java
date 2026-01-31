package systems.glam.services.io;

import software.sava.idl.clients.core.gen.SerDe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public interface KeyedFlatFile<E extends SerDe> {

  static <E extends SerDe> KeyedFlatFile<E> createFlatFile(final int entrySize, final Path filePath) {
    try {
      Files.createDirectories(filePath.getParent());
      final var fileChannel = FileChannel.open(
          filePath,
          StandardOpenOption.CREATE,
          StandardOpenOption.READ,
          StandardOpenOption.WRITE
      );
      return new KeyedFlatFileImpl<>(entrySize, filePath, fileChannel);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  Path filePath();

  void appendEntry(final E entry);

  int deleteEntry(final byte[] key, final E removed);

  void close();
}
