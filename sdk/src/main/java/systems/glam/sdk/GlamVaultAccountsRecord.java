package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.rpc.Filter;

public record GlamVaultAccountsRecord(GlamAccounts glamAccounts,
                                      AccountMeta readFeePayer,
                                      AccountMeta writeFeePayer,
                                      AccountMeta readGlamState,
                                      AccountMeta writeGlamState,
                                      ProgramDerivedAddress vaultPDA,
                                      AccountMeta readVault,
                                      AccountMeta writeVault) implements GlamVaultAccounts {

  static final Filter ACTIVE_FILTER;

  static {
    final byte[] notDeActivated = new byte[Integer.BYTES + Long.BYTES];
    ByteUtil.putInt32LE(notDeActivated, 0, 1);
    ByteUtil.putInt64LE(notDeActivated, Integer.BYTES, -1);
    ACTIVE_FILTER = Filter.createMemCompFilter(0, notDeActivated);
  }

  @Override
  public PublicKey feePayer() {
    return readFeePayer.publicKey();
  }

  @Override
  public PublicKey glamStateKey() {
    return readGlamState.publicKey();
  }

  @Override
  public ProgramDerivedAddress mintPDA(final int id) {
    return glamAccounts.mintPDA(glamStateKey(), id);
  }
}
