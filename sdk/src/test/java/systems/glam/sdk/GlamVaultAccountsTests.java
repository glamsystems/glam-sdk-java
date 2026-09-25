package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class GlamVaultAccountsTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey VAULT_KEY = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");

  @Test
  void createAccountsRolesAndFlags() {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);

    assertSame(GlamAccounts.MAIN_NET, vaultAccounts.glamAccounts());
    assertEquals(FEE_PAYER, vaultAccounts.feePayer());
    assertEquals(STATE_KEY, vaultAccounts.glamStateKey());
    // vault PDA derived from the state key, known-good from GlamPDATests
    assertEquals(VAULT_KEY, vaultAccounts.vaultPublicKey());
    assertEquals(vaultAccounts.vaultPDA().publicKey(), vaultAccounts.vaultPublicKey());

    // the read/write metas must reference the same key with opposite write flags
    assertEquals(AccountMeta.createRead(STATE_KEY), vaultAccounts.readGlamState());
    assertEquals(AccountMeta.createWrite(STATE_KEY), vaultAccounts.writeGlamState());
    assertEquals(AccountMeta.createRead(VAULT_KEY), vaultAccounts.readVault());
    assertEquals(AccountMeta.createWrite(VAULT_KEY), vaultAccounts.writeVault());
    assertFalse(vaultAccounts.readGlamState().write());
    assertTrue(vaultAccounts.writeGlamState().write());
    assertFalse(vaultAccounts.readVault().write());
    assertTrue(vaultAccounts.writeVault().write());
  }

  @Test
  void explicitGlamAccountsOverload() {
    final var staging = GlamVaultAccounts.createAccounts(GlamAccounts.MAIN_NET_STAGING, FEE_PAYER, STATE_KEY);
    assertSame(GlamAccounts.MAIN_NET_STAGING, staging.glamAccounts());
    // a different protocol program must derive a different vault PDA
    assertNotEquals(VAULT_KEY, staging.vaultPublicKey());
  }

  @Test
  void mintPDADelegatesWithShareClassId() {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);
    assertEquals(
        fromBase58Encoded("GBCZzkTU2enaarFqBxJ2Z16yk1Rpa2hq2SKrHAywUq9V"),
        vaultAccounts.mintPDA().publicKey()
    );
    assertEquals(
        vaultAccounts.mintPDA(0).publicKey(),
        vaultAccounts.mintPDA().publicKey()
    );
    assertNotEquals(
        vaultAccounts.mintPDA(0).publicKey(),
        vaultAccounts.mintPDA(1).publicKey()
    );
  }

  /// The context hands the ix-mapper exactly this vault: its state and vault keys, the fee payer as
  /// the signer, and the integration authority of each proxy program its GLAM accounts hold.
  @Test
  void mappingContextCarriesTheVaultAndItsAuthorities() {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(GlamAccounts.MAIN_NET_STAGING, FEE_PAYER, STATE_KEY);
    final var context = vaultAccounts.mappingContext();

    assertEquals(STATE_KEY, context.glamState());
    assertEquals(vaultAccounts.vaultPublicKey(), context.glamVault());
    assertEquals(FEE_PAYER, context.glamSigner());

    final var glamAccounts = GlamAccounts.MAIN_NET_STAGING;
    final var authorities = glamAccounts.integrationAuthorities();
    assertFalse(authorities.isEmpty());
    for (final var entry : authorities.entrySet()) {
      assertEquals(entry.getValue().publicKey(), context.integrationAuthority().apply(entry.getKey()));
    }
    assertEquals(
        glamAccounts.readKaminoIntegrationAuthority().publicKey(),
        context.integrationAuthority().apply(glamAccounts.kaminoIntegrationProgram())
    );
    // a program these accounts do not hold has no authority: the mapper refuses the instruction
    // with a context reason rather than signing with someone else's
    assertNull(context.integrationAuthority().apply(FEE_PAYER));
    assertNull(glamAccounts.integrationAuthority(GlamAccounts.MAIN_NET.kaminoIntegrationProgram()));
  }
}
