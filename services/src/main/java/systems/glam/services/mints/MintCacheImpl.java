package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

final class MintCacheImpl implements MintCache {

  private static final int ENTRY_SIZE = PublicKey.PUBLIC_KEY_LENGTH + 1 + 1;
  private static final byte TOKEN_PROGRAM = 0;
  private static final byte TOKEN_2022_PROGRAM = 1;

  private final PublicKey tokenProgram;
  private final Map<PublicKey, MintContext> mintMap;
  private final ReentrantLock lock;
  private final FileChannel fileChannel;

  MintCacheImpl(final SolanaAccounts solanaAccounts,
                final Map<PublicKey, MintContext> mintMap,
                final FileChannel fileChannel) {
    this.tokenProgram = solanaAccounts.tokenProgram();
    this.mintMap = mintMap;
    this.lock = new ReentrantLock();
    this.fileChannel = fileChannel;
  }

  static void loadFromFile(final SolanaAccounts solanaAccounts,
                           final Path cacheFile,
                           final Map<PublicKey, MintContext> mintMap) throws IOException {
    final byte[] data = Files.readAllBytes(cacheFile);
    if (data.length == 0) {
      return;
    }

    int offset = 0;
    while (offset + ENTRY_SIZE <= data.length) {
      final var mintKey = PublicKey.readPubKey(data, offset);
      offset += PublicKey.PUBLIC_KEY_LENGTH;

      final int decimals = data[offset++] & 0xFF;

      final var readMintMeta = AccountMeta.createRead(mintKey);
      final var readTokenProgram = data[offset++] == TOKEN_PROGRAM
          ? solanaAccounts.readTokenProgram()
          : solanaAccounts.readToken2022Program();

      final var mintContext = new MintContext(readMintMeta, decimals, readTokenProgram);
      mintMap.put(mintKey, mintContext);
    }
  }

  private void writeEntry(final MintContext mintContext) {
    final byte[] entry = new byte[ENTRY_SIZE];
    mintContext.mint().write(entry, 0);
    entry[PublicKey.PUBLIC_KEY_LENGTH] = (byte) mintContext.decimals();
    entry[PublicKey.PUBLIC_KEY_LENGTH + 1] = mintContext.tokenProgram().equals(tokenProgram)
        ? TOKEN_PROGRAM
        : TOKEN_2022_PROGRAM;

    try {
      fileChannel.write(ByteBuffer.wrap(entry));
      fileChannel.force(false);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  @Override
  public MintContext get(final PublicKey mintPubkey) {
    return mintMap.get(mintPubkey);
  }

  @Override
  public MintContext setGet(final MintContext mintContext) {
    final var previous = mintMap.putIfAbsent(mintContext.mint(), mintContext);
    if (previous != null) {
      return previous;
    }
    lock.lock();
    try {
      writeEntry(mintContext);
      return mintContext;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public MintContext delete(final PublicKey mintPubkey) {
    lock.lock();
    try {
      final var removed = mintMap.remove(mintPubkey);
      if (removed == null) {
        return null;
      }
      final long fileSize = fileChannel.size();
      if (fileSize == 0) {
        return removed;
      }

      final byte[] mintKeyBytes = mintPubkey.toByteArray();
      final var mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);

      final byte[] keyBytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
      int deleteOffset = -1;
      for (int i = 0; i < fileSize; i += ENTRY_SIZE) {
        mappedBuffer.position(i);
        mappedBuffer.get(keyBytes);
        if (Arrays.equals(mintKeyBytes, keyBytes)) {
          deleteOffset = i;
          break;
        }
      }

      if (deleteOffset == -1) {
        return removed;
      }

      final long newSize = fileSize - ENTRY_SIZE;
      if (deleteOffset != newSize) {
        final byte[] lastEntry = new byte[ENTRY_SIZE];
        mappedBuffer.position(Math.toIntExact(newSize));
        mappedBuffer.get(lastEntry);
        mappedBuffer.position(deleteOffset);
        mappedBuffer.put(lastEntry);
        mappedBuffer.force();
      }

      fileChannel.truncate(newSize);
      fileChannel.position(newSize);
      fileChannel.force(false);

      return removed;
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
