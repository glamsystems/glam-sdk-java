package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.SPLAccountClient;
import software.sava.idl.clients.spl.SPLClient;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateModel;
import systems.glam.sdk.idl.programs.glam.staging.registered_positions.gen.ExtRpiPDAs;

import java.math.BigInteger;
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

  /**
   * Builds guarded fulfillment for the primary share class. The current
   * onchain handler requires {@code mintId == 0}; {@code limit}, when present,
   * is an unsigned u32; and {@code referenceNav} is a mandatory signed i128.
   */
  default Instruction fulfillWithRefNav(final int mintId,
                                        final PublicKey baseAssetMint,
                                        final PublicKey baseAssetTokenProgram,
                                        final OptionalLong limit,
                                        final BigInteger referenceNav) {
    GlamMintReferenceNavCodec.requirePrimaryMintId(mintId);
    GlamMintReferenceNavCodec.requireU32Limit(limit);
    return GlamMintReferenceNavCodec.fulfillWithRefNav(
        glamAccounts().mintProgram(),
        fulfill(mintId, baseAssetMint, baseAssetTokenProgram, limit),
        referenceNav
    );
  }

  default Instruction fulfill(final PublicKey baseAssetMint, final PublicKey baseAssetTokenProgram) {
    return fulfill(0, baseAssetMint, baseAssetTokenProgram, OptionalLong.empty());
  }

  default Instruction fulfillWithRefNav(final PublicKey baseAssetMint,
                                        final PublicKey baseAssetTokenProgram,
                                        final BigInteger referenceNav) {
    return fulfillWithRefNav(
        0,
        baseAssetMint,
        baseAssetTokenProgram,
        OptionalLong.empty(),
        referenceNav
    );
  }

  /**
   * Builds guarded synchronous subscription for the primary share class. The
   * current onchain handler requires {@code mintId == 0}; {@code amountIn}
   * carries raw unsigned-u64 bits in Java's signed {@code long};
   * {@code referenceNav} is a mandatory signed i128; and
   * {@code signerPolicy} may be {@code null} only when the share mint has no
   * lockup transfer hook.
   */
  default Instruction subscribeWithRefNav(final int mintId,
                                          final PublicKey depositAssetMint,
                                          final PublicKey depositAssetTokenProgram,
                                          final PublicKey signerPolicy,
                                          final long amountIn,
                                          final BigInteger referenceNav) {
    GlamMintReferenceNavCodec.requirePrimaryMintId(mintId);
    final var mint = vaultAccounts().mintPDA(mintId).publicKey();
    final var escrow = glamAccounts().escrowPDA(mint).publicKey();
    final var signer = vaultAccounts().feePayer();
    final var signerMintAta = splClient().findATA(signer, solanaAccounts().token2022Program(), mint);
    final var escrowMintAta = escrowMintTokenAccount(mint, escrow);
    final var vaultDepositAta = vaultTokenAccount(depositAssetTokenProgram, depositAssetMint);
    final var signerDepositAta = splClient().findATA(
        signer,
        depositAssetTokenProgram,
        depositAssetMint
    );
    final var subscribe = GlamMintProgram.subscribe(
        glamAccounts().invokedMintProgram(),
        solanaAccounts(),
        vaultAccounts().glamStateKey(),
        vaultAccounts().vaultPublicKey(),
        mint,
        escrow,
        glamAccounts().requestQueuePDA(mint).publicKey(),
        signer,
        signerMintAta.publicKey(),
        escrowMintAta.publicKey(),
        depositAssetMint,
        vaultDepositAta.publicKey(),
        signerDepositAta.publicKey(),
        signerPolicy,
        depositAssetTokenProgram,
        solanaAccounts().token2022Program(),
        glamAccounts().policyProgram(),
        glamAccounts().protocolProgram(),
        amountIn
    );
    return GlamMintReferenceNavCodec.subscribeWithRefNav(
        glamAccounts().mintProgram(),
        subscribe,
        referenceNav
    );
  }

  default Instruction subscribeWithRefNav(final PublicKey depositAssetMint,
                                          final PublicKey depositAssetTokenProgram,
                                          final PublicKey signerPolicy,
                                          final long amountIn,
                                          final BigInteger referenceNav) {
    return subscribeWithRefNav(
        0,
        depositAssetMint,
        depositAssetTokenProgram,
        signerPolicy,
        amountIn,
        referenceNav
    );
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

  Instruction priceExternalPositions(final PublicKey solUSDOracleKey,
                                     final PublicKey baseAssetUsdOracleKey,
                                     final PublicKey observationStateKey, final boolean cpiEmitEvents);

  default Instruction priceExternalPositions(final PublicKey solUSDOracleKey,
                                             final PublicKey baseAssetUsdOracleKey,
                                             final boolean cpiEmitEvents) {
    final var observationPDA = ExtRpiPDAs.observationStatePDA(
        glamAccounts().externalPositionProgram(),
        vaultAccounts().glamStateKey()
    );
    return priceExternalPositions(null, null, observationPDA.publicKey(), cpiEmitEvents);
  }

  default Instruction priceExternalPositions(final PublicKey solUSDOracleKey,
                                             final PublicKey baseAssetUsdOracleKey) {
    return priceExternalPositions(solUSDOracleKey, baseAssetUsdOracleKey, false);
  }

  Instruction priceLoopscaleLoans(final PublicKey solUSDOracleKey,
                                  final PublicKey baseAssetUsdOracleKey,
                                  final boolean cpiEmitEvents);

  default Instruction priceLoopscaleLoans(final PublicKey solUSDOracleKey,
                                          final PublicKey baseAssetUsdOracleKey) {
    return priceLoopscaleLoans(solUSDOracleKey, baseAssetUsdOracleKey, false);
  }

  Instruction priceLoopscaleStrategies(final PublicKey solUSDOracleKey,
                                       final PublicKey baseAssetUsdOracleKey,
                                       final boolean cpiEmitEvents);

  default Instruction priceLoopscaleStrategies(final PublicKey solUSDOracleKey,
                                               final PublicKey baseAssetUsdOracleKey) {
    return priceLoopscaleStrategies(solUSDOracleKey, baseAssetUsdOracleKey, false);
  }

  Instruction priceLoopscaleVaultPositions(final PublicKey solUSDOracleKey,
                                           final PublicKey baseAssetUsdOracleKey,
                                           final int numVaults,
                                           final boolean cpiEmitEvents);

  default Instruction priceLoopscaleVaultPositions(final PublicKey solUSDOracleKey,
                                                   final PublicKey baseAssetUsdOracleKey,
                                                   final int numVaults) {
    return priceLoopscaleVaultPositions(solUSDOracleKey, baseAssetUsdOracleKey, numVaults, false);
  }

  Instruction priceOrcaWhirlpoolPositions(final PublicKey solUSDOracleKey,
                                          final PublicKey baseAssetUsdOracleKey,
                                          final int numPositions,
                                          final boolean cpiEmitEvents);

  default Instruction priceOrcaWhirlpoolPositions(final PublicKey solUSDOracleKey,
                                                  final PublicKey baseAssetUsdOracleKey,
                                                  final int numPositions) {
    return priceOrcaWhirlpoolPositions(solUSDOracleKey, baseAssetUsdOracleKey, numPositions, false);
  }

  Instruction priceStakeAccounts(final PublicKey solUSDOracleKey,
                                 final PublicKey baseAssetUsdOracleKey,
                                 final boolean cpiEmitEvents);

  default Instruction priceStakeAccounts(final PublicKey solUSDOracleKey,
                                         final PublicKey baseAssetUsdOracleKey) {
    return priceStakeAccounts(solUSDOracleKey, baseAssetUsdOracleKey, false);
  }

  Instruction priceMarginfiAccounts(final PublicKey solUSDOracleKey,
                                    final PublicKey baseAssetUsdOracleKey,
                                    final boolean cpiEmitEvents);

  default Instruction priceMarginfiAccounts(final PublicKey solUSDOracleKey,
                                            final PublicKey baseAssetUsdOracleKey) {
    return priceMarginfiAccounts(solUSDOracleKey, baseAssetUsdOracleKey, false);
  }

  Instruction pricePhoenixTraders(final PublicKey solUSDOracleKey,
                                  final PublicKey baseAssetUsdOracleKey,
                                  final boolean cpiEmitEvents);

  default Instruction pricePhoenixTraders(final PublicKey solUSDOracleKey,
                                          final PublicKey baseAssetUsdOracleKey) {
    return pricePhoenixTraders(solUSDOracleKey, baseAssetUsdOracleKey, false);
  }

  Instruction priceBridgeManagedTransfers(final PublicKey solUSDOracleKey,
                                          final PublicKey baseAssetUsdOracleKey,
                                          final boolean cpiEmitEvents);

  default Instruction priceBridgeManagedTransfers(final PublicKey solUSDOracleKey,
                                                  final PublicKey baseAssetUsdOracleKey) {
    return priceBridgeManagedTransfers(solUSDOracleKey, baseAssetUsdOracleKey, false);
  }
}
