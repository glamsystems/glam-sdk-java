package systems.glam.services.mints;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class MintContextTests {

  private static final SolanaAccounts SOLANA = SolanaAccounts.MAIN_NET;

  private static PublicKey mint(final int id) {
    final byte[] key = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    key[0] = (byte) id;
    key[PublicKey.PUBLIC_KEY_LENGTH - 1] = (byte) id;
    return PublicKey.createPubKey(key);
  }

  /// `createContext` has two representations of one mapping: the overload taking
  /// the owning program key derives both the id and the read meta from that key,
  /// and the overload taking the id derives the meta from the id. They must agree
  /// for both token programs, and each mint must carry its *own* program's meta —
  /// a context that reports Token-2022 while handing callers the legacy program
  /// meta builds instructions against the wrong program.
  ///
  /// Every other test in this module happens to construct legacy Token mints, so
  /// only the pair below distinguishes the two branches.
  @Test
  void theTokenProgramIdAndItsReadMetaAgreeForBothPrograms() {
    final var legacyMint = mint(7);
    final var legacy = MintContext.createContext(SOLANA, legacyMint, 6, SOLANA.tokenProgram());
    assertEquals(SOLANA.readTokenProgram(), legacy.readTokenProgram(),
        "a legacy Token mint must carry the legacy program meta");
    assertEquals(
        MintContext.createContext(SOLANA, legacyMint, 6, legacy.tokenProgramId()),
        legacy,
        "the id overload must reproduce the key overload"
    );

    final var mint2022 = mint(9);
    final var token2022 = MintContext.createContext(SOLANA, mint2022, 9, SOLANA.token2022Program());
    assertEquals(SOLANA.readToken2022Program(), token2022.readTokenProgram(),
        "a Token-2022 mint must carry the Token-2022 program meta");
    assertEquals(
        MintContext.createContext(SOLANA, mint2022, 9, token2022.tokenProgramId()),
        token2022,
        "the id overload must reproduce the key overload"
    );

    // the two programs are genuinely distinguishable, so neither assertion above
    // can be satisfied by a constant
    assertNotEquals(legacy.tokenProgramId(), token2022.tokenProgramId());
    assertNotEquals(SOLANA.readTokenProgram(), SOLANA.readToken2022Program());
  }

  /// An unknown owner is not a third program: anything that is not the legacy
  /// Token program is treated as Token-2022, and the id and meta must still agree
  /// rather than drifting apart on the fallback leg.
  @Test
  void anUnknownOwnerFallsBackToToken2022Consistently() {
    final var context = MintContext.createContext(SOLANA, mint(11), 0, mint(200));
    assertEquals(SOLANA.readToken2022Program(), context.readTokenProgram());
    assertEquals(
        MintContext.createContext(SOLANA, mint(11), 0, context.tokenProgramId()),
        context
    );
  }
}
