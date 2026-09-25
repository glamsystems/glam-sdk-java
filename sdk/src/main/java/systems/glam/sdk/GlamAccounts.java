package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.ix.proxy.InstructionMapper;
import systems.glam.ix.proxy.MappingDocument;
import systems.glam.ix.proxy.MappingDocumentException;
import systems.glam.ix.proxy.MappingDocuments;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;

import java.nio.file.Path;
import java.util.Collection;
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
      .exponentIntegrationProgram("gstgx5AbCFq4mfYKbZP23YDC7XZFUx2EURiG3PcWX6W")
      .externalPositionProgram("gstge5RzNEGQwpwBPKTJbP9yczoFEzzm5upSSsie9fX")
      .jupiterIntegrationProgram("gstgJbGqoE3p1SdFA2dET9tcaCzNqGcdD8wpbGctnU9")
      .kaminoIntegrationProgram("gstgKa2Gq9wf5hM3DFWx1TvUrGYzDYszyFGq3XBY9Uq")
      .loopscaleIntegrationProgram("gstgL6y4uWjsfM3Qjs5euoTDmEcXoUjqx8rkYJhYngG")
      .marginFiIntegrationProgram("gstgMghFitRBz2GXKwgpMd7L1JXd1sg59q2v5Y83vSY")
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

  /// The ix-mapper environment these programs are deployed to, as a mapping document names it in its
  /// `environment` field: [GlamEnv#mappingEnvironment()] of the deployment the protocol program belongs to.
  default String mappingEnvironment() {
    return GlamEnv.from(protocolProgram()).mappingEnvironment();
  }

  /// An instruction mapper over `documents`, which must all describe this environment: the proxy programs a
  /// document of another environment names are not the ones these accounts hold, and an instruction mapped
  /// through them would fail on chain rather than here.
  ///
  /// @throws IllegalArgumentException if the documents describe another environment
  /// @throws MappingDocumentException if no mapper can be built from the documents (none, two for one
  ///                                  program, or mixed environments)
  default InstructionMapper createMapper(final Collection<MappingDocument> documents) {
    final var mapper = InstructionMapper.createMapper(documents);
    final var environment = mappingEnvironment();
    if (!environment.equals(mapper.environment())) {
      throw new IllegalArgumentException(String.format(
          "The mapping documents describe the '%s' environment, but these accounts (protocol program %s) are '%s'.",
          mapper.environment(), protocolProgram(), environment
      ));
    }
    return mapper;
  }

  /// [#createMapper(Collection)] over every `.json` document directly under `mappingsDirectory`, such as the
  /// environment's directory the sdk jar embeds under `glam/ix-mappings/`.
  default InstructionMapper createMapper(final Path mappingsDirectory) {
    return createMapper(MappingDocuments.readDirectory(mappingsDirectory));
  }

  Map<PublicKey, AccountMeta> integrationAuthorities();

  /// The authority `integrationProgram`, a GLAM proxy program, signs its CPIs with, or null when these accounts
  /// hold no such program; the lookup a [systems.glam.ix.proxy.MappingContext] takes.
  default PublicKey integrationAuthority(final PublicKey integrationProgram) {
    final var authority = integrationAuthorities().get(integrationProgram);
    return authority == null ? null : authority.publicKey();
  }

  AccountMeta readMintIntegrationAuthority();

  PublicKey mintEventAuthority();

  AccountMeta invokedBridgeIntegrationProgram();

  PublicKey bridgeIntegrationProgram();

  AccountMeta readBridgeIntegrationAuthority();

  AccountMeta invokedCctpIntegrationProgram();

  PublicKey cctpIntegrationProgram();

  AccountMeta readCctpIntegrationAuthority();

  AccountMeta invokedExponentIntegrationProgram();

  PublicKey exponentIntegrationProgram();

  AccountMeta readExponentIntegrationAuthority();

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

  AccountMeta invokedMarginFiIntegrationProgram();

  PublicKey marginFiIntegrationProgram();

  AccountMeta readMarginFiIntegrationAuthority();

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

  static void main() {
    System.out.println(MAIN_NET_STAGING.integrationAuthorities().size());
  }
}
