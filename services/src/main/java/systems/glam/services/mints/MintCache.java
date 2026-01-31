package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import systems.glam.services.io.KeyedFlatFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public interface MintCache extends AutoCloseable {

  static MintCache createCache(final SolanaAccounts solanaAccounts, final Path cacheFile) {
    final var mintMap = new ConcurrentHashMap<PublicKey, MintContext>();
    try {
      if (Files.exists(cacheFile)) {
        MintCacheImpl.loadFromFile(solanaAccounts, cacheFile, mintMap);
      }
      return new MintCacheImpl(mintMap, KeyedFlatFile.createFlatFile(MintContext.BYTES, cacheFile));
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
