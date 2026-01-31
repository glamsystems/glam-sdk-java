package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import systems.glam.services.io.KeyedFlatFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class MintCacheImpl implements MintCache {

  private final Map<PublicKey, MintContext> mintMap;
  private final KeyedFlatFile<MintContext> fileChannel;

  MintCacheImpl(final Map<PublicKey, MintContext> mintMap, final KeyedFlatFile<MintContext> fileChannel) {
    this.mintMap = mintMap;
    this.fileChannel = fileChannel;
  }

  static void loadFromFile(final SolanaAccounts solanaAccounts,
                           final Path cacheFile,
                           final Map<PublicKey, MintContext> mintMap) throws IOException {
    final byte[] data = Files.readAllBytes(cacheFile);
    for (int offset = 0; offset < data.length; ) {
      final var mintKey = PublicKey.readPubKey(data, offset);
      offset += PublicKey.PUBLIC_KEY_LENGTH;
      final int decimals = data[offset++] & 0xFF;
      final int tokenProgramId = data[offset++] & 0xFF;
      final var mintContext = MintContext.createContext(solanaAccounts, mintKey, decimals, tokenProgramId);
      mintMap.put(mintKey, mintContext);
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
    } else {
      fileChannel.appendEntry(mintContext);
      return mintContext;
    }
  }

  @Override
  public MintContext delete(final PublicKey mintPubkey) {
    final var removed = mintMap.remove(mintPubkey);
    if (removed == null) {
      return null;
    } else {
      return fileChannel.deleteEntry(mintPubkey.toByteArray(), removed) > 0 ? removed : null;
    }
  }

  @Override
  public void close() {
    fileChannel.close();
  }
}
