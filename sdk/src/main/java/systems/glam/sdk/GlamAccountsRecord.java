package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;

import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public record GlamAccountsRecord(AccountMeta invokedProtocolProgram,
                                 PublicKey configProgram,
                                 ProgramDerivedAddress globalConfigPDA,
                                 PublicKey policyProgram,
                                 AccountMeta invokedBridgeIntegrationProgram,
                                 AccountMeta invokedCctpIntegrationProgram,
                                 AccountMeta invokedDriftIntegrationProgram,
                                 AccountMeta invokedExternalPositionProgram,
                                 AccountMeta invokedJupiterIntegrationProgram,
                                 AccountMeta invokedKaminoIntegrationProgram,
                                 AccountMeta invokedLoopscaleIntegrationProgram,
                                 AccountMeta invokedMarinadeIntegrationProgram,
                                 AccountMeta invokedMintIntegrationProgram,
                                 PublicKey mintEventAuthority,
                                 AccountMeta invokedNeutralTradeIntegrationProgram,
                                 AccountMeta invokedOrcaIntegrationProgram,
                                 AccountMeta invokedPhoenixIntegrationProgram,
                                 AccountMeta invokedSplIntegrationProgram,
                                 AccountMeta invokedStakePoolIntegrationProgram,
                                 Map<PublicKey, AccountMeta> integrationAuthorities) implements GlamAccounts {

  @Override
  public ProgramDerivedAddress mintPDA(final PublicKey glamPublicKey, final int shareClassId) {
    if (shareClassId < 0 || shareClassId > 256) {
      throw new IllegalStateException("Invalid share class id: " + shareClassId);
    }
    return PublicKey.findProgramAddress(
        List.of(
            "mint".getBytes(UTF_8),
            new byte[]{(byte) shareClassId},
            glamPublicKey.toByteArray()
        ), mintProgram()
    );
  }

  @Override
  public PublicKey bridgeIntegrationProgram() {
    return invokedBridgeIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readBridgeIntegrationAuthority() {
    return integrationAuthorities.get(bridgeIntegrationProgram());
  }

  @Override
  public PublicKey cctpIntegrationProgram() {
    return invokedCctpIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readCctpIntegrationAuthority() {
    return integrationAuthorities.get(cctpIntegrationProgram());
  }

  @Override
  public PublicKey driftIntegrationProgram() {
    return invokedDriftIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readDriftIntegrationAuthority() {
    return integrationAuthorities.get(driftIntegrationProgram());
  }

  @Override
  public PublicKey externalPositionProgram() {
    return invokedExternalPositionProgram.publicKey();
  }

  @Override
  public AccountMeta readExternalPositionAuthority() {
    return integrationAuthorities.get(externalPositionProgram());
  }

  @Override
  public PublicKey jupiterIntegrationProgram() {
    return invokedJupiterIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readJupiterIntegrationAuthority() {
    return integrationAuthorities.get(jupiterIntegrationProgram());
  }

  @Override
  public PublicKey kaminoIntegrationProgram() {
    return invokedKaminoIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readKaminoIntegrationAuthority() {
    return integrationAuthorities.get(kaminoIntegrationProgram());
  }

  @Override
  public PublicKey loopscaleIntegrationProgram() {
    return invokedLoopscaleIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readLoopscaleIntegrationAuthority() {
    return integrationAuthorities.get(loopscaleIntegrationProgram());
  }

  @Override
  public PublicKey mintIntegrationProgram() {
    return invokedMintIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readMintIntegrationAuthority() {
    return integrationAuthorities.get(mintIntegrationProgram());
  }

  @Override
  public PublicKey marinadeIntegrationProgram() {
    return invokedMarinadeIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readMarinadeIntegrationAuthority() {
    return integrationAuthorities.get(marinadeIntegrationProgram());
  }

  @Override
  public PublicKey neutralTradeIntegrationProgram() {
    return invokedNeutralTradeIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readNeutralTradeIntegrationAuthority() {
    return integrationAuthorities.get(neutralTradeIntegrationProgram());
  }

  @Override
  public PublicKey orcaIntegrationProgram() {
    return invokedOrcaIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readOrcaIntegrationAuthority() {
    return integrationAuthorities.get(orcaIntegrationProgram());
  }

  @Override
  public PublicKey phoenixIntegrationProgram() {
    return invokedPhoenixIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readPhoenixIntegrationAuthority() {
    return integrationAuthorities.get(phoenixIntegrationProgram());
  }

  @Override
  public PublicKey splIntegrationProgram() {
    return invokedSplIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readSplIntegrationAuthority() {
    return integrationAuthorities.get(splIntegrationProgram());
  }

  @Override
  public PublicKey stakePoolIntegrationProgram() {
    return invokedStakePoolIntegrationProgram.publicKey();
  }

  @Override
  public AccountMeta readStakePoolIntegrationAuthority() {
    return integrationAuthorities.get(stakePoolIntegrationProgram());
  }
}
