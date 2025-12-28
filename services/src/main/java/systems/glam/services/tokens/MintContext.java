package systems.glam.services.tokens;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.accounts.token.Mint;
import software.sava.core.util.DecimalInteger;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;

import java.math.BigDecimal;

import static java.math.RoundingMode.DOWN;

public record MintContext(PublicKey mint,
                          AccountMeta readMintMeta,
                          int decimals,
                          PublicKey tokenProgram,
                          AccountMeta readTokenProgram,
                          AccountMeta writeVaultATAMeta) implements DecimalInteger {

  private static AccountMeta readTokenProgram(final SolanaAccounts solanaAccounts,
                                              final PublicKey tokenProgram) {
    return solanaAccounts.tokenProgram().equals(tokenProgram)
        ? solanaAccounts.readTokenProgram()
        : solanaAccounts.readToken2022Program();
  }

  public static MintContext createContext(final SolanaAccounts solanaAccounts,
                                          final PublicKey mint,
                                          final int decimals,
                                          final PublicKey tokenProgram,
                                          final PublicKey vaultATA) {
    final var writeVaultAtaMeta = AccountMeta.createWrite(vaultATA);
    return new MintContext(
        mint,
        AccountMeta.createRead(mint),
        decimals,
        tokenProgram,
        readTokenProgram(solanaAccounts, tokenProgram),
        writeVaultAtaMeta
    );
  }

  public static MintContext createContext(final GlamAccountClient glamClient,
                                          final AccountInfo<byte[]> mintAccountInfo) {
    final var mint = Mint.read(mintAccountInfo.pubKey(), mintAccountInfo.data());
    final var mintKey = mint.address();
    final var readMintMeta = AccountMeta.createRead(mintKey);
    final var tokenProgram = mintAccountInfo.owner();
    final var vaultATA = glamClient.findATA(tokenProgram, mintKey).publicKey();
    final var writeVaultAtaMeta = AccountMeta.createWrite(vaultATA);
    final var solanaAccounts = glamClient.solanaAccounts();
    return new MintContext(
        mintKey,
        readMintMeta,
        mint.decimals(),
        tokenProgram,
        readTokenProgram(solanaAccounts, tokenProgram),
        writeVaultAtaMeta
    );
  }

  public PublicKey vaultATA() {
    return writeVaultATAMeta.publicKey();
  }

  public long setScale(final BigDecimal amount) {
    return amount.setScale(decimals, DOWN).longValue();
  }
}
