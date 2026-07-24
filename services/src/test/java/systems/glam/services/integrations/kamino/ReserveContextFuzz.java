package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.Map;

import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// Jazzer entry point for the glam-owned Kamino Reserve reader and its price-chain
/// resolution — `ReserveContext.createContext` reads the lending market, mint,
/// token info and scope configuration straight out of raw Reserve bytes, then
/// resolves the reserve's price chain against a `MappingsContext`
/// (`ScopeEntries.readPriceChains`). That resolution is where the composite
/// (`MostRecentOf`) chain handling lives — the documented gap SOL's real chain
/// hits — so the target drives it with attacker-controlled reserve bytes.
///
/// The mappings side is a real mainnet snapshot, registered under the SOL
/// reserve's real price feed: a seed input resolves an actual price chain, and
/// the mutator perturbs the reserve bytes around it (a mutated price-feed offset
/// falls to the missing-feed early return — still exercised). The raw
/// `OracleMappings` parser is fuzzed upstream in idl-clients; this covers glam's
/// reserve-side extraction and the chain lookup it drives.
///
/// Malformed-input contract: garbage in -> `RuntimeException` out. Any
/// `RuntimeException` (a short buffer's `IndexOutOfBoundsException`, an invalid
/// enum ordinal) is tolerated; Jazzer flags hangs, memory exhaustion, and any
/// non-`RuntimeException` throwable — including the `StackOverflowError` a cyclic
/// scope chain would raise, the upstream regression this keeps covered here.
///
/// Seeded from the real mainnet SOL Reserve snapshot under
/// src/test/resources/fuzz/reserveContext.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test
/// sources.
///
/// Run with `./gradlew :services:fuzzReserveContext [-PmaxFuzzTime=<seconds>]`.
public final class ReserveContextFuzz {

  private static final PublicKey SOL_RESERVE_KEY = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");
  private static final PublicKey ORACLE_MAPPINGS_KEY = fromBase58Encoded("4zh6bmb77qX2CL7t5AJYCqa6YqFafbz3QJNeFvZjLowg");
  private static final PublicKey PRICE_FEED_KEY = fromBase58Encoded("3t4JZcueEzTbVP6kLxXrL3VpWx45jDer4eqysweBchNH");

  private static final Map<PublicKey, MappingsContext> MAPPINGS;

  static {
    try {
      final var mappingsData = ResourceUtil.readResource("accounts/kamino/" + ORACLE_MAPPINGS_KEY + ".dat.gz");
      final var mappingsInfo = new AccountInfo<>(
          ORACLE_MAPPINGS_KEY, new Context(0L, null), false, 0, ORACLE_MAPPINGS_KEY,
          BigInteger.ZERO, 0, mappingsData
      );
      // keyed by the SOL reserve's price feed so the seed resolves a real chain
      MAPPINGS = Map.of(PRICE_FEED_KEY, MappingsContext.createContext(mappingsInfo));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void fuzzerTestOneInput(final byte[] data) {
    final ReserveContext context;
    try {
      context = ReserveContext.createContext(SOL_RESERVE_KEY, data, MAPPINGS);
    } catch (final RuntimeException tolerated) {
      // truncated or malformed reserve bytes — rejection is in contract
      return;
    }
    // touch the resolved chain: a chain that parsed into a nonsense shape
    // surfaces here rather than at first pricing
    final var priceChains = context.priceChains();
    if (priceChains != null) {
      priceChains.priceChain();
    }
    context.mint();
    context.market();
    context.tokenName();
  }
}
