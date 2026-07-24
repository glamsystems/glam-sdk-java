package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;

/// Jazzer entry point for the Scope `Configuration`-account reader — the
/// glam-owned layer that turns raw on-chain Configuration bytes into a
/// `ScopeFeedContext` (the upstream `ScopeReader`/`OracleMappings` parser is
/// fuzzed in idl-clients; this covers glam's own byte-offset reads over it).
///
/// `ScopeFeedContext.createContext` reads several `PublicKey`s at fixed offsets
/// straight out of the account bytes, and the accessors (`admin`,
/// `tokensMetadata`, `oracleTwaps`, `adminCached`) and `toJson` read more. In
/// production `KaminoCacheImpl.accept` gates these behind a length +
/// discriminator check, but the reader itself is unguarded — a fuzz target on
/// it pins the parser directly, so a future caller that drops the gate cannot
/// reintroduce an out-of-bounds read silently.
///
/// Malformed-input contract: garbage in -> `RuntimeException` out. Any
/// `RuntimeException` (a short buffer's `IndexOutOfBoundsException`) is
/// tolerated; Jazzer flags hangs, memory exhaustion, and any
/// non-`RuntimeException` throwable.
///
/// Seeded from the real mainnet Configuration snapshot under
/// src/test/resources/fuzz/scopeFeedContext.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test
/// sources.
///
/// Run with `./gradlew :services:fuzzScopeFeedContext [-PmaxFuzzTime=<seconds>]`.
public final class ScopeFeedContextFuzz {

  private static final PublicKey KEY = PublicKey.createPubKey(new byte[PublicKey.PUBLIC_KEY_LENGTH]);

  public static void fuzzerTestOneInput(final byte[] data) {
    // createContext does NOT validate length — a sub-PADDING_OFFSET buffer
    // builds a context whose offset accessors then throw on read. Production
    // gates these bytes behind a length + discriminator check in
    // KaminoCacheImpl.accept, so the whole read surface shares one contract:
    // garbage in -> RuntimeException out. The tolerated region spans the build
    // AND the reads (an IndexOutOfBoundsException from either is in contract);
    // Jazzer still flags hangs, OOM, and any non-RuntimeException throwable.
    try {
      final var context = ScopeFeedContext.createContext(KEY, data);
      context.admin();
      context.tokensMetadata();
      context.oracleTwaps();
      context.adminCached();
      context.oracleMappings();
      context.priceFeed();
      context.toJson();
    } catch (final RuntimeException tolerated) {
      // truncated or malformed configuration bytes — rejection is in contract
    }
  }
}
