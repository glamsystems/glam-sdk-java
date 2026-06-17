package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.ix.proxy.TransactionMapper;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;
import systems.glam.sdk.proxy.DynamicGlamAccountFactory;

import java.nio.file.Path;
import java.util.Map;

public interface GlamAccounts {

  // https://github.com/glamsystems/glam-sdk/tree/main/idl
  GlamAccounts MAIN_NET = GlamAccountsBuilder.builder()
      .protocolProgram("GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz")
      .configProgram("gConFzxKL9USmwTdJoeQJvfKmqhJ2CyUaXTyQ8v9TGX")
      .mintProgram("GM1NtvvnSXUptTrMCqbogAdZJydZSNv98DoU5AZVLmGh")
      .policyProgram("po1iCYakK3gHCLbuju4wGzFowTMpAJxkqK1iwUqMonY")
      .cctpIntegrationProgram("G1NTcMDYgNLpDwgnrpSZvoSKQuR9NXG7S3DmtNQCDmrK")
      .kaminoIntegrationProgram("G1NTkDEUR3pkEqGCKZtmtmVzCUEdYa86pezHkwYbLyde")
      .splIntegrationProgram("G1NTsQ36mjPe89HtPYqxKsjY5HmYsDR6CbD2gd2U2pta")
      .create();

  GlamAccounts MAIN_NET_STAGING = GlamAccountsBuilder.builder()
      .protocolProgram("gstgptmbgJVi5f8ZmSRVZjZkDQwqKa3xWuUtD5WmJHz")
      .configProgram("gConFzxKL9USmwTdJoeQJvfKmqhJ2CyUaXTyQ8v9TGX")
      .mintProgram("gstgm1M39mhgnvgyScGUDRwNn5kNVSd97hTtyow1Et5")
      .policyProgram("po1iCYakK3gHCLbuju4wGzFowTMpAJxkqK1iwUqMonY")
      .bridgeIntegrationProgram("gstgxS9yTioViNKdsM4DC33k1TU9un2VCYDQK8fAeSA")
      .cctpIntegrationProgram("gstgcuRwiX2FpmtigowB1TnVi3fPkZC9TEmVnc5sdxW")
      .externalPositionProgram("gstge5RzNEGQwpwBPKTJbP9yczoFEzzm5upSSsie9fX")
      .jupiterIntegrationProgram("gstgJbGqoE3p1SdFA2dET9tcaCzNqGcdD8wpbGctnU9")
      .kaminoIntegrationProgram("gstgKa2Gq9wf5hM3DFWx1TvUrGYzDYszyFGq3XBY9Uq")
      .loopscaleIntegrationProgram("gstgL6y4uWjsfM3Qjs5euoTDmEcXoUjqx8rkYJhYngG")
      .marinadeIntegrationProgram("gstgmvM2o7h7GcScvXymH1oFgWskukWWxRHC1UJJ9FJ")
      .neutralTradeIntegrationProgram("gstgNyHgtURH7iuMn19GQczzv6Wc9fhPV2WDySZVyKx")
      .phoenixIntegrationProgram("gstgPL7r9aYedDDsXNtLpr4atYtNvY7zubAWWstqS3L")
      .orcaIntegrationProgram("gstgo1EgmTp2PbLSaL6Qg57P7uADx3aiRZrMsewEdSy")
      .splIntegrationProgram("gstgs9nJgX8PmRHWAAEP9H7xT3ZkaPWSGPYbj3mXdTa")
      .stakePoolIntegrationProgram("gstgS4dNeT3BTEQa1aaTS2b8CsAUz1SmwQDGosHSPsw")
      .create();

  AccountMeta invokedProtocolProgram();

  default PublicKey protocolProgram() {
    return invokedProtocolProgram().publicKey();
  }

  PublicKey configProgram();

  PublicKey policyProgram();

  ProgramDerivedAddress globalConfigPDA();

  ProgramDerivedAddress mintPDA(final PublicKey glamPublicKey, final int shareClassId);

  default ProgramDerivedAddress escrowPDA(final PublicKey mint) {
    return GlamMintPDAs.glamEscrowPDA(mintProgram(), mint);
  }

  default ProgramDerivedAddress requestQueuePDA(final PublicKey mint) {
    return GlamMintPDAs.requestQueuePDA(mintProgram(), mint);
  }

  default TransactionMapper<GlamVaultAccounts> createMapper(final Path mappingsDirectory,
                                                            final DynamicGlamAccountFactory dynamicGlamAccountFactory) {
    return GlamVaultAccounts.createMapper(invokedProtocolProgram(), mappingsDirectory, dynamicGlamAccountFactory);
  }

  Map<PublicKey, AccountMeta> integrationAuthorities();

  AccountMeta readMintIntegrationAuthority();

  PublicKey mintEventAuthority();

  AccountMeta invokedBridgeIntegrationProgram();

  PublicKey bridgeIntegrationProgram();

  AccountMeta readBridgeIntegrationAuthority();

  AccountMeta invokedCctpIntegrationProgram();

  PublicKey cctpIntegrationProgram();

  AccountMeta readCctpIntegrationAuthority();

  AccountMeta invokedExternalPositionProgram();

  AccountMeta readExternalPositionAuthority();

  PublicKey externalPositionProgram();

  AccountMeta invokedKaminoIntegrationProgram();

  PublicKey kaminoIntegrationProgram();

  AccountMeta readKaminoIntegrationAuthority();

  AccountMeta invokedJupiterIntegrationProgram();

  PublicKey jupiterIntegrationProgram();

  AccountMeta readJupiterIntegrationAuthority();

  AccountMeta invokedLoopscaleIntegrationProgram();

  PublicKey loopscaleIntegrationProgram();

  AccountMeta readLoopscaleIntegrationAuthority();

  AccountMeta invokedMarinadeIntegrationProgram();

  PublicKey marinadeIntegrationProgram();

  AccountMeta readMarinadeIntegrationAuthority();

  AccountMeta invokedMintIntegrationProgram();

  default AccountMeta invokedMintProgram() {
    return invokedMintIntegrationProgram();
  }

  default PublicKey mintProgram() {
    return invokedMintProgram().publicKey();
  }

  PublicKey mintIntegrationProgram();

  AccountMeta invokedNeutralTradeIntegrationProgram();

  PublicKey neutralTradeIntegrationProgram();

  AccountMeta readNeutralTradeIntegrationAuthority();

  AccountMeta invokedOrcaIntegrationProgram();

  PublicKey orcaIntegrationProgram();

  AccountMeta readOrcaIntegrationAuthority();

  AccountMeta invokedPhoenixIntegrationProgram();

  PublicKey phoenixIntegrationProgram();

  AccountMeta readPhoenixIntegrationAuthority();

  AccountMeta invokedSplIntegrationProgram();

  PublicKey splIntegrationProgram();

  AccountMeta readSplIntegrationAuthority();

  AccountMeta invokedStakePoolIntegrationProgram();

  PublicKey stakePoolIntegrationProgram();

  AccountMeta readStakePoolIntegrationAuthority();
}
