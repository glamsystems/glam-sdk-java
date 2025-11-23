package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface StateAccountClient {

  static StateAccountClient createClient(final StateAccount stateAccount, final PublicKey feePayer) {
    final var accountClient = GlamAccountClient.createClient(feePayer, stateAccount._address());
    return createClient(stateAccount, accountClient);
  }

  static StateAccountClient createClient(final StateAccount stateAccount, final GlamAccountClient accountClient) {
    final var glamAccounts = accountClient.glamAccounts();
    final var escrowAccount = GlamMintPDAs.glamEscrowPDA(glamAccounts.mintProgram(), stateAccount.mint());

    final var integrationAclMap = Arrays.stream(stateAccount.integrationAcls())
        .collect(Collectors.toUnmodifiableMap(IntegrationAcl::integrationProgram, Function.identity()));

    return new StateAccountClientImpl(
        stateAccount,
        accountClient,
        escrowAccount,
        integrationAclMap
    );
  }

  StateAccount stateAccount();

  ProgramDerivedAddress escrowAccount();

  GlamAccountClient accountClient();

  boolean integrationEnabled(final PublicKey integrationProgram, final int bitFlag);

  boolean driftEnabled();

  boolean driftVaultsEnabled();

  boolean kaminoLendEnabled();

  boolean kaminoVaultsEnabled();

  boolean jupiterSwapEnabled();
}
