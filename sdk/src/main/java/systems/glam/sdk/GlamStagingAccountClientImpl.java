package systems.glam.sdk;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.SPLClient;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.ExtEpiPDAs;
import systems.glam.sdk.idl.programs.glam.staging.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.staging.mint.gen.GlamMintProgram;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.StateAccount;
import systems.glam.sdk.idl.programs.glam.staging.spl.gen.ExtSplProgram;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

final class GlamStagingAccountClientImpl extends GlamAccountClientImpl implements GlamStagingAccountClient {

  GlamStagingAccountClientImpl(final SPLClient splClient, final GlamVaultAccounts glamVaultAccounts) {
    super(splClient, glamVaultAccounts);
  }

  @Override
  public GlamEnv glamEnv() {
    return GlamEnv.STAGING;
  }

  @Override
  public StateAccountClient createStateAccountClient(final AccountInfo<byte[]> accountInfo) {
    if (accountInfo == null) {
      return null;
    }
    final var stateAccount = StateAccount.read(accountInfo);
    final var escrowAccount = GlamMintPDAs.glamEscrowPDA(glamAccounts.mintProgram(), stateAccount.mint());

    final var integrationAclMap = Arrays.stream(stateAccount.integrationAcls())
        .collect(Collectors.toUnmodifiableMap(IntegrationAcl::integrationProgram, Function.identity()));

    final var delegateAcls = stateAccount.delegateAcls();
    final var delegatePermissions = HashMap.<PublicKey, Map<PublicKey, Map<Protocol, ProtocolPermissions>>>newHashMap(delegateAcls.length);
    for (final var delegateAcl : delegateAcls) {
      final var permissions = delegateAcl.integrationPermissions();
      final var permissionsMap = HashMap.<PublicKey, Map<Protocol, ProtocolPermissions>>newHashMap(permissions.length);
      for (final var permission : permissions) {
        final var protocolPermissions = permission.protocolPermissions();
        final var protocolPermissionsMap = new EnumMap<Protocol, ProtocolPermissions>(Protocol.class);
        final var integrationProgram = permission.integrationProgram();
        for (final var protocolPermission : protocolPermissions) {
          final int protocolBitFlag = protocolPermission.protocolBitflag();
          final long permissionMask = protocolPermission.permissionsBitmask();
          final var adaptedPermissions = adaptPermissions(integrationProgram, protocolBitFlag, permissionMask);
          protocolPermissionsMap.put(adaptedPermissions.protocol(), adaptedPermissions);
        }
        permissionsMap.put(integrationProgram, protocolPermissionsMap);
      }
      delegatePermissions.put(delegateAcl.pubkey(), permissionsMap);
    }

    return new StagingStateAccountClientImpl(
        stateAccount,
        this,
        escrowAccount,
        integrationAclMap,
        delegatePermissions
    );
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
        solanaAccounts.token2022Program(),
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
                                            final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.priceKaminoObligations(
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
        mintProgram
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
  public Instruction priceSingleAssetVault(final PublicKey baseAssetTokenAccount, final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.priceSingleAssetVault(
        invoked,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        baseAssetTokenAccount,
        glamAccounts.readMintIntegrationAuthority().publicKey(),
        invokedProtocolProgram.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram
    );
  }

  @Override
  public Instruction priceExternalPositions(final PublicKey solUSDOracleKey,
                                            final PublicKey baseAssetUsdOracleKey,
                                            final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    final var observationPDA = ExtEpiPDAs.observationStatePDA(
        mintProgram,
        glamVaultAccounts.glamStateKey()
    );
    return GlamMintProgram.priceExternalPositions(
        invoked,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        solUSDOracleKey,
        baseAssetUsdOracleKey,
        glamAccounts.readExternalPositionAuthority().publicKey(),
        globalConfigKey,
        invokedProtocolProgram.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram
    ).extraAccount(AccountMeta.createRead(observationPDA.publicKey()));
  }

  @Override
  public Instruction priceLoopscaleLoans(final PublicKey solUSDOracleKey,
                                         final PublicKey baseAssetUsdOracleKey,
                                         final boolean cpiEmitEvents) {
    final var invoked = glamAccounts.invokedMintIntegrationProgram();
    final var mintProgram = invoked.publicKey();
    return GlamMintProgram.priceLoopscaleLoans(
        invoked,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        solUSDOracleKey,
        baseAssetUsdOracleKey,
        glamAccounts.readLoopscaleIntegrationAuthority().publicKey(),
        globalConfigKey,
        invokedProtocolProgram.publicKey(),
        cpiEmitEvents ? glamAccounts.mintEventAuthority() : mintProgram,
        mintProgram
    );
  }
}
