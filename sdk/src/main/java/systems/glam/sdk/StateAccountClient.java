package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static systems.glam.sdk.GlamAccountClientImpl.adaptPermissions;

public interface StateAccountClient {

  static StateAccountClient createClient(final StateAccount stateAccount, final PublicKey feePayer) {
    if (stateAccount == null) {
      return null;
    }
    final var accountClient = GlamAccountClient.createClient(feePayer, stateAccount._address());
    return createClient(stateAccount, accountClient);
  }

  static StateAccountClient createClient(final StateAccount stateAccount, final GlamAccountClient accountClient) {
    if (stateAccount == null) {
      return null;
    }
    final var glamAccounts = accountClient.glamAccounts();
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
          final var adaptedPermissions = adaptPermissions(glamAccounts, integrationProgram, protocolBitFlag, permissionMask);
          protocolPermissionsMap.put(adaptedPermissions.protocol(), adaptedPermissions);
        }
        permissionsMap.put(integrationProgram, protocolPermissionsMap);
      }
      delegatePermissions.put(delegateAcl.pubkey(), permissionsMap);
    }

    return new StateAccountClientImpl(
        stateAccount,
        accountClient,
        escrowAccount,
        integrationAclMap,
        delegatePermissions
    );
  }

  GlamAccountClient accountClient();

  String name();

  PublicKey mint();

  PublicKey baseAssetMint();

  ProgramDerivedAddress escrowAccount();

  PublicKey[] externalPositions();

  PublicKey[] assets();

  long redeemNoticePeriod();

  long redeemSettlementPeriod();

  long redeemCancellationWindow();

  boolean redeemWindowInSeconds();

  boolean softRedeem();

  boolean delegateHasPermissions(final PublicKey delegateKey,
                                 final Map<PublicKey, ProtocolPermissions> requiredPermissions);

  boolean integrationEnabled(final PublicKey integrationProgram, final int bitFlag);

  boolean driftEnabled();

  boolean driftVaultsEnabled();

  boolean kaminoLendEnabled();

  boolean kaminoVaultsEnabled();

  boolean jupiterSwapEnabled();
}
