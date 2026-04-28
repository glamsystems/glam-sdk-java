package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.ix.proxy.TransactionMapper;
import systems.glam.sdk.idl.programs.glam.config.gen.GlamConfigPDAs;
import systems.glam.sdk.idl.programs.glam.drift.gen.ExtDriftPDAs;
import systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoPDAs;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplPDAs;
import systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.ExtLoopscalePDAs;
import systems.glam.sdk.proxy.DynamicGlamAccountFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static software.sava.core.accounts.meta.AccountMeta.createInvoked;

public interface GlamAccounts {

  // https://github.com/glamsystems/glam-sdk/tree/main/idl
  GlamAccounts MAIN_NET = createAccounts(
      "GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz",
      "gConFzxKL9USmwTdJoeQJvfKmqhJ2CyUaXTyQ8v9TGX",
      "GM1NtvvnSXUptTrMCqbogAdZJydZSNv98DoU5AZVLmGh",
      "po1iCYakK3gHCLbuju4wGzFowTMpAJxkqK1iwUqMonY",
      "G1NTsQ36mjPe89HtPYqxKsjY5HmYsDR6CbD2gd2U2pta",
      "G1NTdrBmBpW43msRQmsf7qXSw3MFBNaqJcAkGiRmRq2F",
      null,
      "G1NTkDEUR3pkEqGCKZtmtmVzCUEdYa86pezHkwYbLyde",
      null
  );

  GlamAccounts MAIN_NET_STAGING = createAccounts(
      "gstgptmbgJVi5f8ZmSRVZjZkDQwqKa3xWuUtD5WmJHz",
      "gConFzxKL9USmwTdJoeQJvfKmqhJ2CyUaXTyQ8v9TGX",
      "gstgm1M39mhgnvgyScGUDRwNn5kNVSd97hTtyow1Et5",
      "po1iCYakK3gHCLbuju4wGzFowTMpAJxkqK1iwUqMonY",
      "gstgs9nJgX8PmRHWAAEP9H7xT3ZkaPWSGPYbj3mXdTa",
      "gstgdpMFXKobURsFtStdaMLRSuwdmDUsrndov7kyu9h",
      "gstge5RzNEGQwpwBPKTJbP9yczoFEzzm5upSSsie9fX",
      "gstgKa2Gq9wf5hM3DFWx1TvUrGYzDYszyFGq3XBY9Uq",
      "gstgL6y4uWjsfM3Qjs5euoTDmEcXoUjqx8rkYJhYngG"
  );

  private static void putIfNotNull(final Map<PublicKey, AccountMeta> map,
                                   final PublicKey key,
                                   final PublicKey value) {
    if (!key.equals(PublicKey.NONE)) {
      map.put(key, AccountMeta.createRead(value));
    }
  }

  static GlamAccounts createAccounts(final PublicKey protocolProgram,
                                     final PublicKey configProgram,
                                     final PublicKey mintProgram,
                                     final PublicKey policyProgram,
                                     final PublicKey splIntegrationProgram,
                                     final PublicKey driftIntegrationProgram,
                                     final PublicKey externalPositionProgram,
                                     final PublicKey kaminoIntegrationProgram,
                                     final PublicKey loopscaleIntegrationProgram) {
    final var mintIntegrationAuthority = GlamMintPDAs.integrationAuthorityPDA(mintProgram).publicKey();
    final var splIntegrationAuthority = ExtSplPDAs.integrationAuthorityPDA(splIntegrationProgram).publicKey();
    final var driftIntegrationAuthority = ExtDriftPDAs.integrationAuthorityPDA(driftIntegrationProgram).publicKey();
    final var externalPositionAuthority = ExtDriftPDAs.integrationAuthorityPDA(externalPositionProgram).publicKey();
    final var kaminoIntegrationAuthority = ExtKaminoPDAs.integrationAuthorityPDA(kaminoIntegrationProgram).publicKey();
    final var loopscaleIntegrationAuthority = ExtLoopscalePDAs.integrationAuthorityPDA(loopscaleIntegrationProgram).publicKey();
    final var map = HashMap.<PublicKey, AccountMeta>newHashMap(6);
    putIfNotNull(map, mintProgram, mintIntegrationAuthority);
    putIfNotNull(map, splIntegrationProgram, splIntegrationAuthority);
    putIfNotNull(map, driftIntegrationProgram, driftIntegrationAuthority);
    putIfNotNull(map, externalPositionProgram, externalPositionAuthority);
    putIfNotNull(map, kaminoIntegrationProgram, kaminoIntegrationAuthority);
    putIfNotNull(map, loopscaleIntegrationProgram, loopscaleIntegrationAuthority);
    final var integrationAuthorities = Map.copyOf(map);
    return new GlamAccountsRecord(
        createInvoked(protocolProgram),
        configProgram, GlamConfigPDAs.globalConfigPDA(configProgram),
        policyProgram,
        createInvoked(mintProgram),
        integrationAuthorities.get(mintProgram),
        GlamMintPDAs.eventAuthorityPDA(mintProgram).publicKey(),
        createInvoked(splIntegrationProgram),
        integrationAuthorities.get(splIntegrationProgram),
        createInvoked(driftIntegrationProgram),
        integrationAuthorities.get(driftIntegrationProgram),
        createInvoked(externalPositionProgram),
        integrationAuthorities.get(externalPositionProgram),
        createInvoked(kaminoIntegrationProgram),
        integrationAuthorities.get(kaminoIntegrationProgram),
        createInvoked(loopscaleIntegrationProgram),
        integrationAuthorities.get(loopscaleIntegrationProgram),
        integrationAuthorities
    );
  }

  private static PublicKey createKey(final String program) {
    return program == null ? PublicKey.NONE : PublicKey.fromBase58Encoded(program);
  }

  static GlamAccounts createAccounts(final String protocolProgram,
                                     final String configProgram,
                                     final String mintProgram,
                                     final String policyProgram,
                                     final String splIntegrationProgram,
                                     final String driftIntegrationProgram,
                                     final String externalPositionProgram,
                                     final String kaminoIntegrationProgram,
                                     final String loopscaleIntegrationProgram) {
    return createAccounts(
        PublicKey.fromBase58Encoded(protocolProgram),
        PublicKey.fromBase58Encoded(configProgram),
        PublicKey.fromBase58Encoded(mintProgram),
        PublicKey.fromBase58Encoded(policyProgram),
        PublicKey.fromBase58Encoded(splIntegrationProgram),
        PublicKey.fromBase58Encoded(driftIntegrationProgram),
        createKey(externalPositionProgram),
        PublicKey.fromBase58Encoded(kaminoIntegrationProgram),
        createKey(loopscaleIntegrationProgram)
    );
  }

  AccountMeta invokedProtocolProgram();

  default PublicKey protocolProgram() {
    return invokedProtocolProgram().publicKey();
  }

  PublicKey configProgram();

  default PublicKey mintProgram() {
    return invokedMintProgram().publicKey();
  }

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

  AccountMeta invokedMintIntegrationProgram();

  default AccountMeta invokedMintProgram() {
    return invokedMintIntegrationProgram();
  }

  default PublicKey mintIntegrationProgram() {
    return invokedMintIntegrationProgram().publicKey();
  }

  AccountMeta readMintIntegrationAuthority();

  PublicKey mintEventAuthority();

  AccountMeta invokedSplIntegrationProgram();

  default PublicKey splIntegrationProgram() {
    return invokedSplIntegrationProgram().publicKey();
  }

  AccountMeta readSplIntegrationAuthority();

  AccountMeta invokedDriftIntegrationProgram();

  default PublicKey driftIntegrationProgram() {
    return invokedDriftIntegrationProgram().publicKey();
  }

  AccountMeta readDriftIntegrationAuthority();

  AccountMeta invokedKaminoIntegrationProgram();

  default PublicKey kaminoIntegrationProgram() {
    return invokedKaminoIntegrationProgram().publicKey();
  }

  AccountMeta readKaminoIntegrationAuthority();

  AccountMeta invokedLoopscaleIntegrationProgram();

  default PublicKey loopscaleIntegrationProgram() {
    return invokedLoopscaleIntegrationProgram().publicKey();
  }

  AccountMeta readLoopscaleIntegrationAuthority();

  Map<PublicKey, AccountMeta> integrationAuthorities();

  AccountMeta invokedExternalPositionProgram();

  AccountMeta readExternalPositionAuthority();

  default PublicKey externalPositionProgram() {
    return invokedExternalPositionProgram().publicKey();
  }
}
