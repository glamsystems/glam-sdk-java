package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.SPLAccountClient;
import software.sava.idl.clients.spl.SPLClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateModel;

import java.util.OptionalInt;

public interface GlamAccountClient extends SPLAccountClient {

  static GlamAccountClient createClient(final SPLClient splClient, final GlamVaultAccounts glamVaultAccounts) {
    return new GlamAccountClientImpl(splClient, glamVaultAccounts);
  }

  static GlamAccountClient createClient(final SolanaAccounts solanaAccounts,
                                        final GlamVaultAccounts glamVaultAccounts) {
    return new GlamAccountClientImpl(SPLClient.createClient(solanaAccounts), glamVaultAccounts);
  }

  static GlamAccountClient createClient(final SolanaAccounts solanaAccounts,
                                        final GlamAccounts glamAccounts,
                                        final PublicKey feePayer,
                                        final PublicKey glamStateKey) {
    return createClient(solanaAccounts, GlamVaultAccounts.createAccounts(glamAccounts, feePayer, glamStateKey));
  }

  static GlamAccountClient createClient(final PublicKey feePayer, final PublicKey glamStatePKey) {
    return createClient(SolanaAccounts.MAIN_NET, GlamAccounts.MAIN_NET, feePayer, glamStatePKey);
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

  ProgramDerivedAddress escrowMintPDA(final PublicKey mint, final PublicKey escrow);

  ProgramDerivedAddress escrowMintPDA();

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
                                     final PublicKey pythOracleKey,
                                     final PublicKey switchboardPriceOracleKey,
                                     final PublicKey switchboardTwapOracleKey,
                                     final PublicKey scopePricesKey,
                                     final int numObligations,
                                     final int numMarkets,
                                     final int numReserves,
                                     final boolean cpiEmitEvents);

  default Instruction priceKaminoObligations(final PublicKey kaminoLendingProgramKey,
                                             final PublicKey solUSDOracleKey,
                                             final PublicKey baseAssetUsdOracleKey,
                                             final PublicKey pythOracleKey,
                                             final PublicKey switchboardPriceOracleKey,
                                             final PublicKey switchboardTwapOracleKey,
                                             final PublicKey scopePricesKey,
                                             final int numObligations,
                                             final int numMarkets,
                                             final int numReserves) {
    return priceKaminoObligations(
        kaminoLendingProgramKey,
        solUSDOracleKey,
        baseAssetUsdOracleKey,
        pythOracleKey,
        switchboardPriceOracleKey,
        switchboardTwapOracleKey,
        scopePricesKey,
        numObligations,
        numMarkets,
        numReserves,
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
}
