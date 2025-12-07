package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class GlamPDATests {

  @Test
  void vaultAccounts() {
    final var stateAccount = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
    final var glamClient = GlamAccountClient.createClient(PublicKey.NONE, stateAccount);
    final var vaultAccounts = glamClient.vaultAccounts();

    assertEquals(stateAccount, vaultAccounts.glamStateKey());
    assertEquals(
        fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH"),
        vaultAccounts.vaultPDA().publicKey()
    );
    final var mint = vaultAccounts.mintPDA().publicKey();
    assertEquals(
        fromBase58Encoded("GBCZzkTU2enaarFqBxJ2Z16yk1Rpa2hq2SKrHAywUq9V"),
        mint
    );

    final var glamAccounts = GlamAccounts.MAIN_NET;
    final var escrow = glamAccounts.escrowPDA(mint).publicKey();
    assertEquals(
        fromBase58Encoded("fbHfSgPnFQYfSFebaekokALjmTPSx7poqXydCduDALy"),
        escrow
    );

    assertEquals(
        fromBase58Encoded("8m1hHNSiatkEcggN4TxCkuejG7yioyCcSnJWtdwXq9BZ"),
        glamClient.escrowMintPDA(mint, escrow).publicKey()
    );

    assertEquals(
        fromBase58Encoded("3nTj8ZQ9518G66K23D15nfiv8N8fpoBQ3r6tqL3ZDiQa"),
        glamAccounts.requestQueuePDA(mint).publicKey()
    );
  }
}
