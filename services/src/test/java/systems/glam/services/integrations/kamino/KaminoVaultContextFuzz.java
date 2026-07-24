package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;

/// Jazzer entry point for the Kamino `VaultState` reader — `KaminoVaultContext`
/// builds from raw vault-state bytes, reading a dozen pubkeys and int64s at fixed
/// offsets and, the reason this target exists, walking the vault allocation table
/// in `parseReserveKeys`: a stop-at-first-`NONE`-slot loop over
/// `VAULT_ALLOCATION_STRATEGY_LEN` entries. That walk had off-by-one and
/// terminator bugs before (see the kamino vault-context pitest pass), and its
/// slot count comes straight from attacker-controlled bytes.
///
/// The fuzz payload is the vault-state bytes; the shares-mint key is read from
/// them the way `createContext(AccountInfo)` does. Malformed-input contract:
/// garbage in -> `RuntimeException` out (a short buffer's
/// `IndexOutOfBoundsException`, a bad allocation offset). Jazzer flags hangs,
/// memory exhaustion, and any non-`RuntimeException` throwable.
///
/// Seeded from the real mainnet vault-state snapshot under
/// src/test/resources/fuzz/kaminoVaultContext.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test
/// sources.
///
/// Run with `./gradlew :services:fuzzKaminoVaultContext [-PmaxFuzzTime=<seconds>]`.
public final class KaminoVaultContextFuzz {

  private static final PublicKey VAULT_KEY = PublicKey.createPubKey(new byte[PublicKey.PUBLIC_KEY_LENGTH]);

  public static void fuzzerTestOneInput(final byte[] data) {
    final KaminoVaultContext context;
    try {
      final var sharesMint = PublicKey.readPubKey(
          data, software.sava.idl.clients.kamino.vaults.gen.types.VaultState.SHARES_MINT_OFFSET);
      context = KaminoVaultContext.createContext(0L, data, VAULT_KEY, sharesMint);
    } catch (final RuntimeException tolerated) {
      // truncated or malformed vault-state bytes — rejection is in contract
      return;
    }
    // the allocation-table walk's output drives downstream fetches; touch it so
    // a mis-terminated or over-long parse surfaces here
    context.reserves();
    context.tokenMint();
    context.baseVaultAuthority();
  }
}
