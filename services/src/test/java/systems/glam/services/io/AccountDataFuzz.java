package systems.glam.services.io;

import software.sava.core.accounts.PublicKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

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

  /// Operator diagnostics are not fuzzing signal. `readAccountData` logs a WARNING
  /// *with the exception* for every input it rejects, and rejection is this
  /// harness's contract — so the fuzzer logged a full stack trace on essentially
  /// every execution: 85,115 warnings across 85,313 runs and 112MB of log over one
  /// 301s campaign, 64KB after this silence.
  ///
  /// This buys log volume, not throughput — the two were correlated, not causal.
  /// The target runs ~290 exec/s against 33k-220k for its siblings, and silencing
  /// moved that 283 -> 294, inside the run-to-run noise the sibling targets show.
  /// The cost is what each execution actually does: four filesystem round trips
  /// and two gzip passes over a real temp file. Don't re-run that experiment —
  /// make the harness cheaper (in-memory streams) if the depth matters.
  ///
  /// Keyed to the logger's **declaration site**, never to the source class a
  /// formatter prints. The two agree here only because `FileUtils` both declares
  /// the logger under its own name and is the class that logs; a logger inherited
  /// from an interface carries the interface's name, and a silence keyed to the
  /// name in the output would compile, look right, and suppress nothing.
  ///
  /// Held in a static field deliberately: `LogManager` keeps loggers weakly
  /// reachable, so a configured logger nothing references can be collected and
  /// replaced by a fresh one at the default level, and the silence would
  /// evaporate mid-campaign.
  ///
  /// Level rather than handler removal, so the record is dropped before the
  /// message is built or the stack trace is walked. `LogCapture` raises the level
  /// back to ALL for the tests that assert these warnings and restores this one
  /// afterwards, so "a failure is never silent" still holds where it is asserted.
  private static final Logger REJECTION_DIAGNOSTICS = Logger.getLogger(FileUtils.class.getName());

  static {
    REJECTION_DIAGNOSTICS.setLevel(Level.OFF);
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
