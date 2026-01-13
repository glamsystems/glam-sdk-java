package systems.glam.services.fulfillment.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.kamino.vaults.KaminoVaultsClient;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.pricing.accounting.BasePosition;
import systems.glam.services.tokens.MintContext;

import java.util.Map;
import java.util.Set;

public final class KaminoVaultPosition extends BasePosition {

  private final KaminoVaultsClient kaminoVaultsClient;
  private final PublicKey glamVaultSharesTokenAccount;
  private final KaminoVaultCache kaminoVaultCache;
  private final PublicKey kVaultKey;

  public KaminoVaultPosition(final MintContext mintContext,
                             final GlamAccountClient glamClient,
                             final KaminoVaultsClient kaminoVaultsClient,
                             final PublicKey glamVaultSharesTokenAccount,
                             final KaminoVaultCache kaminoVaultCache,
                             final PublicKey kVaultKey) {
    super(mintContext, glamClient);
    this.kaminoVaultsClient = kaminoVaultsClient;
    this.glamVaultSharesTokenAccount = glamVaultSharesTokenAccount;
    this.kaminoVaultCache = kaminoVaultCache;
    this.kVaultKey = kVaultKey;
  }

  @Override
  public void accountsForPriceInstruction(final Set<PublicKey> keys) {
    keys.add(kVaultKey);
    keys.add(glamVaultSharesTokenAccount);
  }

  @Override
  public Instruction priceInstruction(final GlamAccountClient glamAccountClient,
                                      final PublicKey solUSDOracleKey,
                                      final PublicKey baseAssetUSDOracleKey,
                                      final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                      final Set<PublicKey> returnAccounts) {
    return null;
  }
}
