package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.SPLAccountClient;
import software.sava.idl.clients.spl.SPLClient;
import software.sava.solana.programs.clients.NativeProgramAccountClient;
import software.sava.solana.programs.clients.NativeProgramClient;
import software.sava.solana.programs.token.AssociatedTokenProgram;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateModel;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplProgram;

import java.util.List;
import java.util.OptionalInt;

final class GlamAccountClientImpl implements GlamAccountClient {

  private final SolanaAccounts solanaAccounts;
  private final PublicKey wrappedSolPDA;
  private final NativeProgramAccountClient nativeProgramAccountClient;
  private final SPLAccountClient splAccountClient;
  private final GlamVaultAccounts glamVaultAccounts;
  private final GlamAccounts glamAccounts;
  private final AccountMeta invokedProtocolProgram;
  private final AccountMeta feePayer;
  private final PublicKey globalConfigKey;

  GlamAccountClientImpl(final NativeProgramClient nativeProgramClient, final GlamVaultAccounts glamVaultAccounts) {
    this.solanaAccounts = nativeProgramClient.accounts();
    this.glamVaultAccounts = glamVaultAccounts;
    this.feePayer = AccountMeta.createFeePayer(glamVaultAccounts.feePayer());
    this.nativeProgramAccountClient = NativeProgramAccountClient.createClient(solanaAccounts, glamVaultAccounts.vaultPublicKey(), feePayer);
    this.splAccountClient = null;
    this.wrappedSolPDA = nativeProgramAccountClient.wrappedSolPDA().publicKey();
    this.glamAccounts = glamVaultAccounts.glamAccounts();
    this.invokedProtocolProgram = glamAccounts.invokedProtocolProgram();
    this.globalConfigKey = glamVaultAccounts.glamAccounts().globalConfigPDA().publicKey();
  }

  @Override
  public SolanaAccounts solanaAccounts() {
    return solanaAccounts;
  }

  @Override
  public SPLClient splClient() {
    return splAccountClient.splClient();
  }

  @Override
  public GlamVaultAccounts vaultAccounts() {
    return glamVaultAccounts;
  }

  @Override
  public AccountMeta feePayer() {
    return feePayer;
  }

  @Override
  public ProgramDerivedAddress wrappedSolPDA() {
    return nativeProgramAccountClient.wrappedSolPDA();
  }

  @Override
  public ProgramDerivedAddress findATA(final PublicKey tokenProgram, final PublicKey mint) {
    return nativeProgramAccountClient.findATA(tokenProgram, mint);
  }

  @Override
  public Instruction createATAForOwnerFundedByFeePayer(final boolean idempotent,
                                                       final PublicKey ata,
                                                       final PublicKey mint,
                                                       final PublicKey tokenProgram) {
    return splAccountClient.createATAForOwnerFundedByFeePayer(idempotent, ata, mint, tokenProgram);
  }

  @Override
  public Instruction transferSolLamports(final PublicKey toPublicKey, final long lamports) {
    return GlamProtocolProgram.systemTransfer(
        invokedProtocolProgram,
        solanaAccounts,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        toPublicKey,
        lamports
    );
  }

  @Override
  public Instruction syncNative() {
    return splClient().syncNative(wrappedSolPDA);
  }

  @Override
  public List<Instruction> wrapSOL(final long lamports) {
    final var transferIx = transferSolLamports(wrappedSolPDA, lamports);
    transferIx.extraAccount(solanaAccounts.readTokenProgram());
    return List.of(transferIx, syncNative());
  }

  @Override
  public Instruction unwrapSOL() {
    return closeTokenAccount(solanaAccounts.invokedTokenProgram(), wrappedSolPDA);
  }

  @Override
  public Instruction transferTokenChecked(final AccountMeta invokedTokenProgram,
                                          final PublicKey fromTokenAccount,
                                          final PublicKey toTokenAccount,
                                          final long scaledAmount,
                                          final int decimals,
                                          final PublicKey tokenMint) {
    return ExtSplProgram.tokenTransferChecked(
        glamAccounts.invokedSplIntegrationProgram(),
        solanaAccounts,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        glamAccounts.readSplIntegrationAuthority().publicKey(),
        invokedProtocolProgram.publicKey(),
        invokedTokenProgram.publicKey(),
        fromTokenAccount,
        tokenMint,
        toTokenAccount,
        scaledAmount,
        decimals
    );
  }

  @Override
  public Instruction closeTokenAccount(final AccountMeta invokedTokenProgram, final PublicKey tokenAccount) {
    return ExtSplProgram.tokenCloseAccount(
        glamAccounts.invokedSplIntegrationProgram(),
        solanaAccounts,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        glamAccounts.readSplIntegrationAuthority().publicKey(),
        invokedTokenProgram.publicKey(),
        invokedProtocolProgram.publicKey(),
        tokenAccount
    );
  }

  @Override
  public Instruction fulfill(final int mintId,
                             final PublicKey baseAssetMint,
                             final PublicKey baseAssetTokenProgram,
                             final OptionalInt limit) {
    final var glamProgram = invokedProtocolProgram.publicKey();

    final var escrow = GlamMintPDAs.glamEscrowPDA(glamProgram, glamVaultAccounts.glamStateKey()).publicKey();

    final var mint = glamVaultAccounts.mintPDA(mintId).publicKey();
    final var escrowMintTokenAccount = AssociatedTokenProgram.findATA(solanaAccounts, escrow, solanaAccounts.token2022Program(), mint);

    final var vault = glamVaultAccounts.vaultPublicKey();
    final var vaultTokenAccount = AssociatedTokenProgram.findATA(solanaAccounts, vault, baseAssetTokenProgram, baseAssetMint);

    final var escrowTokenAccount = AssociatedTokenProgram.findATA(solanaAccounts, escrow, baseAssetTokenProgram, baseAssetMint);

    final var requestQueueKey = GlamMintPDAs.requestQueuePDA(glamAccounts.mintProgram(), mint).publicKey();
    return GlamMintProgram.fulfill(
        glamAccounts.invokedMintProgram(),
        solanaAccounts,
        glamVaultAccounts.glamStateKey(),
        vault,
        mint,
        escrow,
        requestQueueKey,
        feePayer.publicKey(),
        escrowMintTokenAccount.publicKey(),
        baseAssetMint,
        vaultTokenAccount.publicKey(),
        escrowTokenAccount.publicKey(),
        baseAssetTokenProgram,
        invokedProtocolProgram.publicKey(),
        limit
    );
  }

//  public Instruction disburseFees(final int mintId,
//                                  final PublicKey baseAssetMint,
//                                  final PublicKey baseAssetTokenProgram) {
//    final var glamProgram = invokedProgram.publicKey();
//
//    final var escrow = GlamProtocolPDAs.glamEscrowPDA(glamProgram, glamVaultAccounts.glamPublicKey()).publicKey();
//
//    final var mint = glamVaultAccounts.mintPDA(mintId).publicKey();
//    final var escrowMintTokenAccount = AssociatedTokenProgram.findATA(solanaAccounts, escrow, solanaAccounts.token2022Program(), mint);
//
//    final var vault = glamVaultAccounts.vaultPublicKey();
//    final var vaultTokenAccount = AssociatedTokenProgram.findATA(solanaAccounts, vault, baseAssetTokenProgram, baseAssetMint);
//
//    final var escrowTokenAccount = AssociatedTokenProgram.findATA(solanaAccounts, escrow, baseAssetTokenProgram, baseAssetMint);
//
//    return GlamProtocolProgram.disburseFees(
//        invokedProgram,
//        solanaAccounts,
//        glamVaultAccounts.glamPublicKey(),
//        vault,
//        escrow,
//        mint,
//        feePayer.publicKey(),
//        escrowMintTokenAccount.publicKey(),
//        baseAssetMint,
//        vaultTokenAccount.publicKey(),
//        escrowTokenAccount.publicKey(),
//        glamAccounts.glamConfigKey(),
//        baseAssetTokenProgram,
//        mintId
//    );
//  }

  @Override
  public Instruction priceVaultTokens(final PublicKey solUsdOracleKey,
                                      final PublicKey baseAssetUsdOracleKey,
                                      final short[][] aggIndexes,
                                      final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.priceVaultTokens(
        invoked,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        solUsdOracleKey,
        baseAssetUsdOracleKey,
        glamAccounts.readMintIntegrationAuthority().publicKey(),
        globalConfigKey,
        invokedProtocolProgram.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram,
        aggIndexes
    );
  }

  @Override
  public Instruction priceDriftUsers(final PublicKey solUSDOracleKey,
                                     final PublicKey baseAssetUsdOracleKey,
                                     final int numUsers,
                                     final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.priceDriftUsers(
        invoked,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        solUSDOracleKey,
        baseAssetUsdOracleKey,
        glamAccounts.readMintIntegrationAuthority().publicKey(),
        globalConfigKey,
        invokedProtocolProgram.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram,
        numUsers
    );
  }

  @Override
  public Instruction priceDriftVaultDepositors(final PublicKey solOracleKey,
                                               final PublicKey baseAssetUsdOracleKey,
                                               final int numVaultDepositors,
                                               final int numSpotMarkets,
                                               final int numPerpMarkets,
                                               final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.priceDriftVaultDepositors(
        invoked,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        solOracleKey,
        baseAssetUsdOracleKey,
        glamAccounts.readMintIntegrationAuthority().publicKey(),
        globalConfigKey,
        invokedProtocolProgram.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram,
        numVaultDepositors,
        numSpotMarkets,
        numPerpMarkets
    );
  }

  @Override
  public Instruction priceKaminoObligations(final PublicKey kaminoLendingProgramKey,
                                            final PublicKey solUSDOracleKey,
                                            final PublicKey baseAssetUsdOracleKey,
                                            final PublicKey pythOracleKey,
                                            final PublicKey switchboardPriceOracleKey,
                                            final PublicKey switchboardTwapOracleKey,
                                            final PublicKey scopePricesKey,
                                            final int numObligations,
                                            final int numMarkets,
                                            final int numReserves,
                                            final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.priceKaminoObligations(
        invoked,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        kaminoLendingProgramKey,
        solUSDOracleKey,
        baseAssetUsdOracleKey,
        glamAccounts.readMintIntegrationAuthority().publicKey(),
        globalConfigKey,
        invokedProtocolProgram.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram,
        pythOracleKey,
        switchboardPriceOracleKey,
        switchboardTwapOracleKey,
        scopePricesKey,
        numObligations,
        numMarkets,
        numReserves
    );
  }

  @Override
  public Instruction priceKaminoVaultShares(final PublicKey solUSDOracleKey,
                                            final PublicKey baseAssetUsdOracleKey,
                                            final int numVaults,
                                            final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.priceKaminoVaultShares(
        invoked,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        solUSDOracleKey,
        baseAssetUsdOracleKey,
        glamAccounts.readMintIntegrationAuthority().publicKey(),
        globalConfigKey,
        invokedProtocolProgram.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram,
        numVaults
    );
  }


  @Override
  public Instruction updateState(final StateModel state) {
    return GlamProtocolProgram.updateState(
        invokedProtocolProgram,
        glamVaultAccounts.glamStateKey(),
        feePayer.publicKey(),
        state
    );
  }
}
