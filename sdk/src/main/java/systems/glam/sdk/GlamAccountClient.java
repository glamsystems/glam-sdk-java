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

import java.util.OptionalLong;

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

  GlamEnv glamEnv();

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
                      final OptionalLong limit);

  default Instruction fulfill(final PublicKey baseAssetMint, final PublicKey baseAssetTokenProgram) {
    return fulfill(0, baseAssetMint, baseAssetTokenProgram, OptionalLong.empty());
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

  // The integration pricers below are hosted by the ext program that owns the positions (GLAM-1305,
  // glam-next anchor_v1/PRICING.md) and exist on staging only. An ext pricer takes glam_mint's account
  // list without its signer and event accounts, signs with its own integration authority, and emits
  // no event: its record is the argument of the `glam_protocol::update_priced_protocol` call it makes.
  // The vault's integration ACL must hold the ext program.

  /// ext_rpi's `price_registered_positions` over the vault's observation state.
  Instruction priceRegisteredPositions(final PublicKey observationStateKey);

  /// ext_rpi's `price_registered_positions` over the observation state derived for this vault.
  Instruction priceRegisteredPositions();

  /// ext_loopscale's `price_loopscale_loans`.
  Instruction priceLoopscaleLoans(final PublicKey solUSDOracleKey, final PublicKey baseAssetUsdOracleKey);

  /// ext_loopscale's `price_loopscale_strategies`.
  Instruction priceLoopscaleStrategies(final PublicKey solUSDOracleKey, final PublicKey baseAssetUsdOracleKey);

  /// ext_loopscale's `price_loopscale_vault_positions`.
  Instruction priceLoopscaleVaultPositions(final PublicKey solUSDOracleKey,
                                           final PublicKey baseAssetUsdOracleKey,
                                           final int numVaults);

  /// ext_orca's `price_orca_whirlpool_positions`.
  Instruction priceOrcaWhirlpoolPositions(final PublicKey solUSDOracleKey,
                                          final PublicKey baseAssetUsdOracleKey,
                                          final int numPositions);

  Instruction priceStakeAccounts(final PublicKey solUSDOracleKey,
                                 final PublicKey baseAssetUsdOracleKey,
                                 final boolean cpiEmitEvents);

  default Instruction priceStakeAccounts(final PublicKey solUSDOracleKey,
                                         final PublicKey baseAssetUsdOracleKey) {
    return priceStakeAccounts(solUSDOracleKey, baseAssetUsdOracleKey, false);
  }

  /// ext_marginfi's `price_marginfi_accounts`.
  Instruction priceMarginfiAccounts(final PublicKey solUSDOracleKey, final PublicKey baseAssetUsdOracleKey);

  /// ext_phoenix's `price_phoenix_traders`.
  Instruction pricePhoenixTraders(final PublicKey solUSDOracleKey, final PublicKey baseAssetUsdOracleKey);

  /// ext_neutral's `price_neutral_bundle_depositors`.
  Instruction priceNeutralBundleDepositors(final PublicKey solUSDOracleKey, final PublicKey baseAssetUsdOracleKey);

  /// ext_jupiter's `price_jupiter_earn_positions`.
  Instruction priceJupiterEarnPositions(final PublicKey solUSDOracleKey, final PublicKey baseAssetUsdOracleKey);

  /// ext_jupiter's `price_jupiter_borrow_positions`.
  Instruction priceJupiterBorrowPositions(final PublicKey solUSDOracleKey, final PublicKey baseAssetUsdOracleKey);

  /// ext_bridge's `price_managed_transfers` over this vault's bridge registry. It takes no SOL oracle;
  /// the caller appends one oracle per active managed transfer, in registry order. With no transfer it
  /// writes no record. glam_mint's `price_bridge_managed_transfers` always wrote zero and
  /// no positions, so it could replace a real record, and it is not offered.
  Instruction priceBridgeManagedTransfers(final PublicKey baseAssetUsdOracleKey);
}
