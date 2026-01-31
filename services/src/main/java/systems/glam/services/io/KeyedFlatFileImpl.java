package systems.glam.services.io;

import software.sava.idl.clients.core.gen.SerDe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;

final class KeyedFlatFileImpl<E extends SerDe> implements KeyedFlatFile<E> {

  private final int entrySize;
  private final ReentrantLock lock;
  private final Path filePath;
  private final FileChannel fileChannel;

  KeyedFlatFileImpl(final int entrySize, final Path filePath, final FileChannel fileChannel) {
    this.entrySize = entrySize;
    this.filePath = filePath;
    this.lock = new ReentrantLock();
    this.fileChannel = fileChannel;
  }

  @Override
  public Path filePath() {
    return filePath;
  }

  @Override
  public void appendEntry(final E entry) {
    final byte[] data = new byte[this.entrySize];
    entry.write(data, 0);
    lock.lock();
    try {
      fileChannel.position(fileChannel.size());
      fileChannel.write(ByteBuffer.wrap(data));
      fileChannel.force(false);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public int deleteEntry(final byte[] key, final E removed) {
    lock.lock();
    try {
      long fileSize = fileChannel.size();
      if (fileSize == 0) {
        return 0;
      }

      final var mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);

      int numRemoved = 0;
      final byte[] keyBytes = new byte[key.length];
      long lastElementOffset = fileSize - entrySize;
      for (int i = 0; i < fileSize; i += entrySize) {
        mappedBuffer.position(i);
        mappedBuffer.get(keyBytes);
        if (Arrays.equals(key, keyBytes)) {
          if (i < lastElementOffset) {
            final byte[] lastEntry = new byte[entrySize];
            mappedBuffer.position(Math.toIntExact(lastElementOffset));
            mappedBuffer.get(lastEntry);
            mappedBuffer.position(i);
            mappedBuffer.put(lastEntry);
            mappedBuffer.force();
          }

          fileChannel.truncate(lastElementOffset);
          fileChannel.force(false);
          fileSize = lastElementOffset;
          lastElementOffset = fileSize - entrySize;
          ++numRemoved;
        }
      }

      return numRemoved;
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void writeEntries(final Collection<E> entries) {
    final byte[] data = new byte[entries.size() * this.entrySize];
    int offset = 0;
    for (final E entry : entries) {
      entry.write(data, offset);
      offset += this.entrySize;
    }
  }

  @Override
  public void overwriteFile(final byte[] data) {
    lock.lock();
    try {
      fileChannel.position(0);
      fileChannel.write(ByteBuffer.wrap(data));
      fileChannel.truncate(fileChannel.position());
      fileChannel.force(false);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    lock.lock();
    try {
      if (fileChannel.isOpen()) {
        fileChannel.close();
      }
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    } finally {
      lock.unlock();
    }
  }
}
