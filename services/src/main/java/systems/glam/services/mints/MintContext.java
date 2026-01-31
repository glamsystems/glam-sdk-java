package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.accounts.token.Mint;
import software.sava.core.util.DecimalInteger;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.spl.associated_token.gen.AssociatedTokenPDAs;
import software.sava.rpc.json.http.response.AccountInfo;

import java.math.BigDecimal;

import static java.math.RoundingMode.DOWN;

public record MintContext(AccountMeta readMintMeta,
                          int decimals,
                          int tokenProgramId,
                          AccountMeta readTokenProgram) implements DecimalInteger, SerDe {

  public static final int BYTES = PublicKey.PUBLIC_KEY_LENGTH + 1 + 1;
  private static final byte TOKEN_PROGRAM = 0;
  private static final byte TOKEN_2022_PROGRAM = 1;

  private static int tokenProgram(final SolanaAccounts solanaAccounts, final PublicKey tokenProgram) {
    return solanaAccounts.tokenProgram().equals(tokenProgram)
        ? TOKEN_PROGRAM
        : TOKEN_2022_PROGRAM;
  }

  private static AccountMeta readTokenProgram(final SolanaAccounts solanaAccounts, final PublicKey tokenProgram) {
    return solanaAccounts.tokenProgram().equals(tokenProgram)
        ? solanaAccounts.readTokenProgram()
        : solanaAccounts.readToken2022Program();
  }

  public static MintContext createContext(final SolanaAccounts solanaAccounts,
                                          final PublicKey mint,
                                          final int decimals,
                                          final int tokenProgramId) {
    return new MintContext(
        AccountMeta.createRead(mint),
        decimals,
        tokenProgramId,
        tokenProgramId == MintContext.TOKEN_PROGRAM
            ? solanaAccounts.readTokenProgram()
            : solanaAccounts.readToken2022Program()
    );
  }

  public static MintContext createContext(final SolanaAccounts solanaAccounts,
                                          final PublicKey mint,
                                          final int decimals,
                                          final PublicKey tokenProgram) {
    return new MintContext(
        AccountMeta.createRead(mint),
        decimals,
        tokenProgram(solanaAccounts, tokenProgram),
        readTokenProgram(solanaAccounts, tokenProgram)
    );
  }

  public static MintContext createContext(final SolanaAccounts solanaAccounts,
                                          final AccountInfo<byte[]> mintAccountInfo) {
    final var mint = Mint.read(mintAccountInfo.pubKey(), mintAccountInfo.data());
    final var mintKey = mint.address();
    final var readMintMeta = AccountMeta.createRead(mintKey);
    final var tokenProgram = mintAccountInfo.owner();
    return new MintContext(
        readMintMeta,
        mint.decimals(),
        tokenProgram(solanaAccounts, tokenProgram),
        readTokenProgram(solanaAccounts, tokenProgram)
    );
  }

  public PublicKey mint() {
    return readMintMeta.publicKey();
  }

  public PublicKey tokenProgram() {
    return readTokenProgram.publicKey();
  }

  public long setScale(final BigDecimal amount) {
    return amount.setScale(decimals, DOWN).longValue();
  }

  public PublicKey ata(final PublicKey associatedTokenProgram, final PublicKey owner) {
    return AssociatedTokenPDAs.associatedTokenPDA(
        associatedTokenProgram,
        owner,
        tokenProgram(),
        mint()
    ).publicKey();
  }

  @Override
  public int l() {
    return BYTES;
  }

  @Override
  public int write(final byte[] data, final int offset) {
    mint().write(data, offset);
    data[offset + PublicKey.PUBLIC_KEY_LENGTH] = (byte) decimals;
    data[offset + PublicKey.PUBLIC_KEY_LENGTH + 1] = (byte) tokenProgramId;
    return BYTES;
  }
}
