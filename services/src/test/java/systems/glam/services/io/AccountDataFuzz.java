package systems.glam.services.io;

import software.sava.core.accounts.PublicKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Jazzer entry point for the compressed account-data persistence format — the
/// on-disk representation every cache warm start reads (`KaminoCache.initService`,
/// `GlobalConfigCache.initCache`) and deletes-on-corruption, so its bytes are
/// external input by the time they are read back.
///
/// Two halves, one input:
///
/// 1. **Decode arbitrary bytes.** The fuzz payload is written verbatim as a
///    `<key>.dat.gz` file and fed to `readAccountData`. Malformed-input
///    contract: garbage in -> `RuntimeException` out (the warm starts catch,
///    log, and delete). Jazzer flags what the contract forbids — hangs, memory
///    exhaustion, and any non-`RuntimeException` throwable.
/// 2. **Round-trip differential.** The same payload is persisted through
///    `writeCompressedAccountData` and read back: the write and read paths are
///    two representations of one format, and they must *agree* — same key, same
///    bytes — not merely not crash. A silent mismatch here resurrects the
///    "dropped configuration reappears on restart" bug family.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test
/// sources.
///
/// Run with `./gradlew :services:fuzzAccountData [-PmaxFuzzTime=<seconds>]`.
public final class AccountDataFuzz {

  private static final Path DIR;
  private static final PublicKey KEY = PublicKey.createPubKey(new byte[PublicKey.PUBLIC_KEY_LENGTH]);

  static {
    try {
      DIR = Files.createTempDirectory("account-data-fuzz");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void fuzzerTestOneInput(final byte[] data) {
    final var rawFile = FileUtils.resolveCompressedAccountPath(DIR, KEY);
    try {
      Files.write(rawFile, data);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    try {
      FileUtils.readAccountData(rawFile);
    } catch (final RuntimeException tolerated) {
      // malformed gzip or truncated payload — rejection is in contract
    }

    try {
      FileUtils.writeCompressedAccountData(DIR, KEY, data);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    final var roundTripped = FileUtils.readAccountData(FileUtils.resolveCompressedAccountPath(DIR, KEY));
    if (!KEY.equals(roundTripped.pubKey())) {
      throw new IllegalStateException("round-trip changed the key: " + roundTripped.pubKey());
    }
    if (!java.util.Arrays.equals(data, roundTripped.data())) {
      throw new IllegalStateException(
          "round-trip changed the payload: wrote " + data.length + " bytes, read " + roundTripped.data().length
      );
    }
  }
}
