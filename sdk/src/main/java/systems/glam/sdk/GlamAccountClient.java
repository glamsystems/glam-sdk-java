package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.SPLAccountClient;
import software.sava.idl.clients.spl.SPLClient;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateModel;

import java.util.OptionalInt;

public interface GlamAccountClient extends SPLAccountClient {

  static GlamAccountClient createClient(final SPLClient splClient, final GlamVaultAccounts glamVaultAccounts) {
    final var glamAccounts = glamVaultAccounts.glamAccounts();
    if (GlamAccounts.MAIN_NET_STAGING.protocolProgram().equals(glamAccounts.protocolProgram())) {
      return new GlamStagingAccountClientImpl(splClient, glamVaultAccounts);
    } else {
      return new GlamAccountClientImpl(splClient, glamVaultAccounts);
    }
  }

  static GlamAccountClient createClient(final SolanaAccounts solanaAccounts,
                                        final GlamVaultAccounts glamVaultAccounts) {
    return createClient(SPLClient.createClient(solanaAccounts), glamVaultAccounts);
  }

  static GlamAccountClient createClient(final SolanaAccounts solanaAccounts,
                                        final GlamAccounts glamAccounts,
                                        final PublicKey feePayer,
                                        final PublicKey glamStateKey) {
    return createClient(solanaAccounts, GlamVaultAccounts.createAccounts(glamAccounts, feePayer, glamStateKey));
  }

  static GlamAccountClient createClient(final PublicKey feePayer, final PublicKey glamStateKey) {
    return createClient(SolanaAccounts.MAIN_NET, GlamAccounts.MAIN_NET, feePayer, glamStateKey);
  }

  static boolean isDelegated(final StateAccount glamAccount, final PublicKey delegate) {
    for (final var delegateAcl : glamAccount.delegateAcls()) {
      if (delegate.equals(delegateAcl.pubkey())) {
        return true;
      }
    }
    return false;
  }

  GlamAccounts glamAccounts();

  GlamVaultAccounts vaultAccounts();

  StateAccountClient createStateAccountClient(final AccountInfo<byte[]> accountInfo);

  ProgramDerivedAddress vaultTokenAccount(final PublicKey tokenProgram, final PublicKey mint);

  ProgramDerivedAddress escrowMintTokenAccount(final PublicKey mint, final PublicKey escrow);

  Instruction createEscrowAssociatedTokenIdempotent(final PublicKey escrowTokenAccount,
                                                    final PublicKey escrow,
                                                    final PublicKey mint,
                                                    final PublicKey tokenProgram);

  ProgramDerivedAddress escrowMintTokenAccount();

  Instruction validateAum(boolean cpiEmitEvents);

  Instruction fulfill(final int mintId,
                      final PublicKey baseAssetMint,
                      final PublicKey baseAssetTokenProgram,
                      final OptionalInt limit);

  default Instruction fulfill(final PublicKey baseAssetMint, final PublicKey baseAssetTokenProgram) {
    return fulfill(0, baseAssetMint, baseAssetTokenProgram, OptionalInt.empty());
  }

  Instruction priceVaultTokens(final PublicKey solUsdOracleKey,
                               final PublicKey baseAssetUsdOracleKey,
                               final short[][] aggIndexes,
                               final boolean cpiEmitEvents);

  default Instruction priceVaultTokens(final PublicKey solUsdOracleKey,
                                       final PublicKey baseAssetUsdOracleKey,
                                       final short[][] aggIndexes) {
    return priceVaultTokens(solUsdOracleKey, baseAssetUsdOracleKey, aggIndexes, false);
  }

  Instruction priceDriftUsers(final PublicKey solUSDOracleKey,
                              final PublicKey baseAssetUsdOracleKey,
                              final int numUsers,
                              final boolean cpiEmitEvents);

  default Instruction priceDriftUsers(final PublicKey solUSDOracleKey,
                                      final PublicKey baseAssetUsdOracleKey,
                                      final int numUsers) {
    return priceDriftUsers(solUSDOracleKey, baseAssetUsdOracleKey, numUsers, false);
  }

  Instruction priceDriftVaultDepositors(final PublicKey solOracleKey,
                                        final PublicKey baseAssetUsdOracleKey,
                                        final int numVaultDepositors,
                                        final int numSpotMarkets,
                                        final int numPerpMarkets,
                                        final boolean cpiEmitEvents);

  default Instruction priceDriftVaultDepositors(final PublicKey solOracleKey,
                                                final PublicKey baseAssetUsdOracleKey,
                                                final int numVaultDepositors,
                                                final int numSpotMarkets,
                                                final int numPerpMarkets) {
    return priceDriftVaultDepositors(solOracleKey, baseAssetUsdOracleKey, numVaultDepositors, numSpotMarkets, numPerpMarkets, false);
  }

  Instruction priceKaminoObligations(final PublicKey kaminoLendingProgramKey,
                                     final PublicKey solUSDOracleKey,
                                     final PublicKey baseAssetUsdOracleKey,
                                     final boolean cpiEmitEvents);

  default Instruction priceKaminoObligations(final PublicKey kaminoLendingProgramKey,
                                             final PublicKey solUSDOracleKey,
                                             final PublicKey baseAssetUsdOracleKey) {
    return priceKaminoObligations(
        kaminoLendingProgramKey,
        solUSDOracleKey,
        baseAssetUsdOracleKey,
        false
    );
  }

  Instruction priceKaminoVaultShares(final PublicKey solUSDOracleKey,
                                     final PublicKey baseAssetUsdOracleKey,
                                     final int numVaults,
                                     final boolean cpiEmitEvents);

  default Instruction priceKaminoVaultShares(final PublicKey solUSDOracleKey,
                                             final PublicKey baseAssetUsdOracleKey,
                                             final int numVaults) {
    return priceKaminoVaultShares(
        solUSDOracleKey,
        baseAssetUsdOracleKey,
        numVaults,
        false
    );
  }

  Instruction updateState(final StateModel state);

  Instruction priceSingleAssetVault(final PublicKey baseAssetTokenAccount, final boolean cpiEmitEvents);
}
