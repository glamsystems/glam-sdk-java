package systems.glam.sdk;

import software.sava.core.accounts.AccountWithSeed;
import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.SPLAccountClient;
import software.sava.idl.clients.spl.SPLClient;
import software.sava.idl.clients.spl.associated_token.gen.AssociatedTokenProgram;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateModel;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplProgram;

import java.util.List;
import java.util.OptionalInt;

class GlamAccountClientImpl implements GlamAccountClient {

  protected final SolanaAccounts solanaAccounts;
  protected final PublicKey wrappedSolPDA;
  protected final SPLAccountClient splAccountClient;
  protected final GlamVaultAccounts glamVaultAccounts;
  protected final GlamAccounts glamAccounts;
  protected final AccountMeta invokedProtocolProgram;
  protected final AccountMeta feePayer;
  protected final PublicKey globalConfigKey;

  GlamAccountClientImpl(final SPLClient splClient, final GlamVaultAccounts glamVaultAccounts) {
    this.solanaAccounts = splClient.solanaAccounts();
    this.glamVaultAccounts = glamVaultAccounts;
    this.feePayer = AccountMeta.createFeePayer(glamVaultAccounts.feePayer());
    this.splAccountClient = SPLAccountClient.createClient(solanaAccounts, glamVaultAccounts.vaultPublicKey(), feePayer);
    this.wrappedSolPDA = splAccountClient.wrappedSolPDA().publicKey();
    this.glamAccounts = glamVaultAccounts.glamAccounts();
    this.invokedProtocolProgram = glamAccounts.invokedProtocolProgram();
    this.globalConfigKey = glamVaultAccounts.glamAccounts().globalConfigPDA().publicKey();
  }

  static ProtocolPermissions adaptPermissions(final GlamAccounts glamAccounts,
                                              final PublicKey integrationProgram,
                                              final int protocolBitFlag,
                                              final long permissionMask) {
    if (integrationProgram.equals(glamAccounts.mintProgram())) {
      return Protocol.fromMintProtocolBitFlag(protocolBitFlag, permissionMask);
    } else if (integrationProgram.equals(glamAccounts.protocolProgram())) {
      return Protocol.fromGlamProtocolBitFlag(protocolBitFlag, permissionMask);
    } else if (integrationProgram.equals(glamAccounts.splIntegrationProgram())) {
      return Protocol.fromSplProtocolBitFlag(protocolBitFlag, permissionMask);
    } else if (integrationProgram.equals(glamAccounts.driftIntegrationProgram())) {
      return Protocol.fromDriftProtocolBitFlag(protocolBitFlag, permissionMask);
    } else if (integrationProgram.equals(glamAccounts.kaminoIntegrationProgram())) {
      return Protocol.fromKaminoProtocolBitFlag(protocolBitFlag, permissionMask);
    } else {
      throw new UnsupportedOperationException("Unknown integration program: " + integrationProgram);
    }
  }

  protected final ProtocolPermissions adaptPermissions(final PublicKey integrationProgram,
                                                       final int protocolBitFlag,
                                                       final long permissionMask) {
    return adaptPermissions(glamAccounts, integrationProgram, protocolBitFlag, permissionMask);
  }

  @Override
  public final SolanaAccounts solanaAccounts() {
    return solanaAccounts;
  }

  @Override
  public final GlamAccounts glamAccounts() {
    return glamAccounts;
  }

  @Override
  public final GlamVaultAccounts vaultAccounts() {
    return glamVaultAccounts;
  }

  @Override
  public StateAccountClient createStateAccountClient(final AccountInfo<byte[]> accountInfo) {
    return StateAccountClient.createClient(StateAccount.read(accountInfo), this);
  }

  @Override
  public final SPLClient splClient() {
    return splAccountClient.splClient();
  }

  @Override
  public final PublicKey owner() {
    return glamVaultAccounts.vaultPublicKey();
  }

  @Override
  public final AccountMeta feePayer() {
    return feePayer;
  }

  @Override
  public final ProgramDerivedAddress wrappedSolPDA() {
    return splAccountClient.wrappedSolPDA();
  }

  @Override
  public final ProgramDerivedAddress findATA(final PublicKey tokenProgram, final PublicKey mint) {
    return splAccountClient.findATA(tokenProgram, mint);
  }

  @Override
  public final Instruction createATAForOwnerFundedByFeePayer(final boolean idempotent,
                                                             final PublicKey ata,
                                                             final PublicKey mint,
                                                             final PublicKey tokenProgram) {
    return splAccountClient.createATAForOwnerFundedByFeePayer(idempotent, ata, mint, tokenProgram);
  }

  @Override
  public final Instruction createAccount(final PublicKey newAccountPublicKey,
                                         final long lamports,
                                         final long space,
                                         final PublicKey programOwner) {
    return splAccountClient.createAccount(newAccountPublicKey, lamports, space, programOwner);
  }

  @Override
  public final Instruction createAccountWithSeed(final AccountWithSeed accountWithSeed,
                                                 final long lamports,
                                                 final long space,
                                                 final PublicKey programOwner) {
    return splAccountClient.createAccountWithSeed(accountWithSeed, lamports, space, programOwner);
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
  public final Instruction syncNative() {
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
  public final ProgramDerivedAddress vaultTokenAccount(final PublicKey tokenProgram, final PublicKey mint) {
    return splClient().findATA(vaultAccounts().vaultPublicKey(), tokenProgram, mint);
  }

  @Override
  public final ProgramDerivedAddress escrowMintTokenAccount(final PublicKey mint, final PublicKey escrow) {
    return splClient().findATA(escrow, solanaAccounts.token2022Program(), mint);
  }

  @Override
  public final ProgramDerivedAddress escrowMintTokenAccount() {
    final var mint = glamVaultAccounts.mintPDA().publicKey();
    final var escrow = glamAccounts.escrowPDA(mint).publicKey();
    return escrowMintTokenAccount(mint, escrow);
  }

  @Override
  public Instruction createEscrowAssociatedTokenIdempotent(final PublicKey escrowTokenAccount,
                                                           final PublicKey escrow,
                                                           final PublicKey mint, final PublicKey tokenProgram) {
    return AssociatedTokenProgram.createAssociatedTokenIdempotent(
        solanaAccounts.invokedAssociatedTokenAccountProgram(), solanaAccounts,
        feePayerKey(),
        escrowTokenAccount, escrow,
        mint, tokenProgram
    );
  }

  @Override
  public Instruction validateAum(final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.validateAum(
        invoked,
        glamVaultAccounts.glamStateKey(),
        feePayer.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram
    );
  }

  @Override
  public Instruction fulfill(final int mintId,
                             final PublicKey baseAssetMint,
                             final PublicKey baseAssetTokenProgram,
                             final OptionalInt limit) {
    final var mint = glamVaultAccounts.mintPDA(mintId).publicKey();
    final var escrow = glamAccounts.escrowPDA(mint).publicKey();

    final var escrowMintTokenAccount = escrowMintTokenAccount(mint, escrow);

    final var vault = glamVaultAccounts.vaultPublicKey();
    final var vaultTokenAccount = vaultTokenAccount(baseAssetTokenProgram, baseAssetMint);

    final var escrowTokenAccount = splClient().findATA(escrow, baseAssetTokenProgram, baseAssetMint);

    final var requestQueueKey = glamAccounts.requestQueuePDA(mint).publicKey();

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

  private RuntimeException throwStagingOnly() {
    throw new IllegalStateException("Functionality only available in staging environment.");
  }

  @Override
  public Instruction priceSingleAssetVault(final PublicKey baseAssetTokenAccount, final boolean cpiEmitEvents) {
    throw throwStagingOnly();
  }
}
