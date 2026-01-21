package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;

public interface MintCache extends AutoCloseable {

  static MintCache createCache(final SolanaAccounts solanaAccounts, final Path cacheFile) {
    final var mintMap = new ConcurrentHashMap<PublicKey, MintContext>();
    try {
      Files.createDirectories(cacheFile.getParent());
      if (Files.exists(cacheFile)) {
        MintCacheImpl.loadFromFile(solanaAccounts, cacheFile, mintMap);
      }
      final var fileChannel = FileChannel.open(
          cacheFile,
          StandardOpenOption.CREATE,
          StandardOpenOption.READ,
          StandardOpenOption.WRITE
      );
      fileChannel.position(fileChannel.size());
      return new MintCacheImpl(solanaAccounts, mintMap, fileChannel);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  MintContext get(final PublicKey mintPubkey);

  MintContext setGet(final MintContext mintContext);

  MintContext delete(final PublicKey mintPubkey);

  @Override
  void close();
}
