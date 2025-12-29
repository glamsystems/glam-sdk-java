package systems.glam.sdk;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.SPLClient;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.staging.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.staging.mint.gen.GlamMintProgram;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.StateAccount;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class GlamStagingAccountClientImpl extends GlamAccountClientImpl {

  GlamStagingAccountClientImpl(final SPLClient splClient, final GlamVaultAccounts glamVaultAccounts) {
    super(splClient, glamVaultAccounts);
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
}
