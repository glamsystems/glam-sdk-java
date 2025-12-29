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
import systems.glam.sdk.proxy.DynamicGlamAccountFactory;

import java.nio.file.Path;
import java.util.Map;

import static software.sava.core.accounts.meta.AccountMeta.createRead;

public interface GlamAccounts {

  // https://github.com/glamsystems/glam-sdk/tree/main/idl
  GlamAccounts MAIN_NET = createAccounts(
      "GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz",
      "gConFzxKL9USmwTdJoeQJvfKmqhJ2CyUaXTyQ8v9TGX",
      "GM1NtvvnSXUptTrMCqbogAdZJydZSNv98DoU5AZVLmGh",
      "po1iCYakK3gHCLbuju4wGzFowTMpAJxkqK1iwUqMonY",
      "G1NTsQ36mjPe89HtPYqxKsjY5HmYsDR6CbD2gd2U2pta",
      "G1NTdrBmBpW43msRQmsf7qXSw3MFBNaqJcAkGiRmRq2F",
      "G1NTkDEUR3pkEqGCKZtmtmVzCUEdYa86pezHkwYbLyde"
  );

  GlamAccounts MAIN_NET_STAGING = createAccounts(
      "gstgptmbgJVi5f8ZmSRVZjZkDQwqKa3xWuUtD5WmJHz",
      "gConFzxKL9USmwTdJoeQJvfKmqhJ2CyUaXTyQ8v9TGX",
      "gstgm1M39mhgnvgyScGUDRwNn5kNVSd97hTtyow1Et5",
      "po1iCYakK3gHCLbuju4wGzFowTMpAJxkqK1iwUqMonY",
      "gstgs9nJgX8PmRHWAAEP9H7xT3ZkaPWSGPYbj3mXdTa",
      "gstgdpMFXKobURsFtStdaMLRSuwdmDUsrndov7kyu9h",
      "gstgKa2Gq9wf5hM3DFWx1TvUrGYzDYszyFGq3XBY9Uq"
  );

  static GlamAccounts createAccounts(final PublicKey protocolProgram,
                                     final PublicKey configProgram,
                                     final PublicKey mintProgram,
                                     final PublicKey policyProgram,
                                     final PublicKey splIntegrationProgram,
                                     final PublicKey driftIntegrationProgram,
                                     final PublicKey kaminoIntegrationProgram) {
    final var mintIntegrationAuthority = createRead(GlamMintPDAs.integrationAuthorityPDA(mintProgram).publicKey());
    final var splIntegrationAuthority = createRead(ExtSplPDAs.integrationAuthorityPDA(splIntegrationProgram).publicKey());
    final var driftIntegrationAuthority = createRead(ExtDriftPDAs.integrationAuthorityPDA(driftIntegrationProgram).publicKey());
    final var kaminoIntegrationAuthority = createRead(ExtKaminoPDAs.integrationAuthorityPDA(kaminoIntegrationProgram).publicKey());
    final var IntegrationAuthorities = Map.of(
        mintProgram, mintIntegrationAuthority,
        splIntegrationProgram, splIntegrationAuthority,
        driftIntegrationProgram, driftIntegrationAuthority,
        kaminoIntegrationProgram, kaminoIntegrationAuthority
    );
    return new GlamAccountsRecord(
        AccountMeta.createInvoked(protocolProgram),
        configProgram,
        policyProgram,
        AccountMeta.createInvoked(mintProgram),
        mintIntegrationAuthority,
        GlamMintPDAs.eventAuthorityPDA(mintProgram).publicKey(),
        AccountMeta.createInvoked(splIntegrationProgram),
        splIntegrationAuthority,
        AccountMeta.createInvoked(driftIntegrationProgram),
        driftIntegrationAuthority,
        AccountMeta.createInvoked(kaminoIntegrationProgram),
        kaminoIntegrationAuthority,
        IntegrationAuthorities
    );
  }

  static GlamAccounts createAccounts(final String protocolProgram,
                                     final String configProgram,
                                     final String mintProgram,
                                     final String policyProgram,
                                     final String splIntegrationProgram,
                                     final String driftIntegrationProgram,
                                     final String kaminoIntegrationProgram) {
    return createAccounts(
        PublicKey.fromBase58Encoded(protocolProgram),
        PublicKey.fromBase58Encoded(configProgram),
        PublicKey.fromBase58Encoded(mintProgram),
        PublicKey.fromBase58Encoded(policyProgram),
        PublicKey.fromBase58Encoded(splIntegrationProgram),
        PublicKey.fromBase58Encoded(driftIntegrationProgram),
        PublicKey.fromBase58Encoded(kaminoIntegrationProgram)
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

  default ProgramDerivedAddress globalConfigPDA() {
    return GlamConfigPDAs.globalConfigPDA(configProgram());
  }

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

  Map<PublicKey, AccountMeta> integrationAuthorities();
}
