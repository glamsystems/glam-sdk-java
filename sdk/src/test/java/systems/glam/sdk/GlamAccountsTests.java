package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.GlamConfigPDAs;
import systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoPDAs;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplPDAs;
import systems.glam.sdk.idl.programs.glam.staging.cctp.gen.ExtCctpPDAs;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class GlamAccountsTests {

  private static final PublicKey PROTOCOL_PROGRAM = fromBase58Encoded("GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz");
  private static final PublicKey CONFIG_PROGRAM = fromBase58Encoded("gConFzxKL9USmwTdJoeQJvfKmqhJ2CyUaXTyQ8v9TGX");
  private static final PublicKey MINT_PROGRAM = fromBase58Encoded("GM1NtvvnSXUptTrMCqbogAdZJydZSNv98DoU5AZVLmGh");
  private static final PublicKey POLICY_PROGRAM = fromBase58Encoded("po1iCYakK3gHCLbuju4wGzFowTMpAJxkqK1iwUqMonY");
  private static final PublicKey CCTP_PROGRAM = fromBase58Encoded("G1NTcMDYgNLpDwgnrpSZvoSKQuR9NXG7S3DmtNQCDmrK");
  private static final PublicKey KAMINO_PROGRAM = fromBase58Encoded("G1NTkDEUR3pkEqGCKZtmtmVzCUEdYa86pezHkwYbLyde");
  private static final PublicKey SPL_PROGRAM = fromBase58Encoded("G1NTsQ36mjPe89HtPYqxKsjY5HmYsDR6CbD2gd2U2pta");

  // built inside the test method rather than referencing GlamAccounts.MAIN_NET
  // so builder coverage is attributed to a @Test, not a static initializer
  private static GlamAccounts buildMainNet() {
    return GlamAccountsBuilder.builder()
        .protocolProgram("GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz")
        .configProgram("gConFzxKL9USmwTdJoeQJvfKmqhJ2CyUaXTyQ8v9TGX")
        .mintProgram("GM1NtvvnSXUptTrMCqbogAdZJydZSNv98DoU5AZVLmGh")
        .policyProgram("po1iCYakK3gHCLbuju4wGzFowTMpAJxkqK1iwUqMonY")
        .cctpIntegrationProgram("G1NTcMDYgNLpDwgnrpSZvoSKQuR9NXG7S3DmtNQCDmrK")
        .kaminoIntegrationProgram("G1NTkDEUR3pkEqGCKZtmtmVzCUEdYa86pezHkwYbLyde")
        .splIntegrationProgram("G1NTsQ36mjPe89HtPYqxKsjY5HmYsDR6CbD2gd2U2pta")
        .create();
  }

  @Test
  void programKeysAndInvokedMetas() {
    final var accounts = buildMainNet();
    assertEquals(AccountMeta.createInvoked(PROTOCOL_PROGRAM), accounts.invokedProtocolProgram());
    assertEquals(PROTOCOL_PROGRAM, accounts.protocolProgram());
    assertEquals(CONFIG_PROGRAM, accounts.configProgram());
    assertEquals(POLICY_PROGRAM, accounts.policyProgram());

    assertEquals(AccountMeta.createInvoked(MINT_PROGRAM), accounts.invokedMintIntegrationProgram());
    assertSame(accounts.invokedMintIntegrationProgram(), accounts.invokedMintProgram());
    assertEquals(MINT_PROGRAM, accounts.mintProgram());
    assertEquals(MINT_PROGRAM, accounts.mintIntegrationProgram());

    assertEquals(AccountMeta.createInvoked(CCTP_PROGRAM), accounts.invokedCctpIntegrationProgram());
    assertEquals(CCTP_PROGRAM, accounts.cctpIntegrationProgram());
    assertEquals(AccountMeta.createInvoked(KAMINO_PROGRAM), accounts.invokedKaminoIntegrationProgram());
    assertEquals(KAMINO_PROGRAM, accounts.kaminoIntegrationProgram());
    assertEquals(AccountMeta.createInvoked(SPL_PROGRAM), accounts.invokedSplIntegrationProgram());
    assertEquals(SPL_PROGRAM, accounts.splIntegrationProgram());
  }

  @Test
  void absentIntegrationsDefaultToSystemProgram() {
    final var accounts = buildMainNet();
    final var invokedSystem = SolanaAccounts.MAIN_NET.invokedSystemProgram();
    final var systemProgram = SolanaAccounts.MAIN_NET.systemProgram();

    assertEquals(invokedSystem, accounts.invokedBridgeIntegrationProgram());
    assertEquals(systemProgram, accounts.bridgeIntegrationProgram());
    assertNull(accounts.readBridgeIntegrationAuthority());
    assertEquals(invokedSystem, accounts.invokedExternalPositionProgram());
    assertEquals(systemProgram, accounts.externalPositionProgram());
    assertNull(accounts.readExternalPositionAuthority());
    assertEquals(invokedSystem, accounts.invokedJupiterIntegrationProgram());
    assertNull(accounts.readJupiterIntegrationAuthority());
    assertEquals(invokedSystem, accounts.invokedLoopscaleIntegrationProgram());
    assertNull(accounts.readLoopscaleIntegrationAuthority());
    assertEquals(invokedSystem, accounts.invokedMarginFiIntegrationProgram());
    assertNull(accounts.readMarginFiIntegrationAuthority());
    assertEquals(invokedSystem, accounts.invokedMarinadeIntegrationProgram());
    assertNull(accounts.readMarinadeIntegrationAuthority());
    assertEquals(invokedSystem, accounts.invokedNeutralTradeIntegrationProgram());
    assertNull(accounts.readNeutralTradeIntegrationAuthority());
    assertEquals(invokedSystem, accounts.invokedOrcaIntegrationProgram());
    assertNull(accounts.readOrcaIntegrationAuthority());
    assertEquals(invokedSystem, accounts.invokedPhoenixIntegrationProgram());
    assertNull(accounts.readPhoenixIntegrationAuthority());
    assertEquals(invokedSystem, accounts.invokedStakePoolIntegrationProgram());
    assertNull(accounts.readStakePoolIntegrationAuthority());
  }

  @Test
  void integrationAuthorities() {
    final var accounts = buildMainNet();
    final var authorities = accounts.integrationAuthorities();
    // one entry per present integration program: cctp, kamino, spl, mint
    assertEquals(4, authorities.size());

    final var cctpAuthority = ExtCctpPDAs.integrationAuthorityPDA(CCTP_PROGRAM).publicKey();
    assertEquals(AccountMeta.createRead(cctpAuthority), authorities.get(CCTP_PROGRAM));
    assertSame(authorities.get(CCTP_PROGRAM), accounts.readCctpIntegrationAuthority());

    final var kaminoAuthority = ExtKaminoPDAs.integrationAuthorityPDA(KAMINO_PROGRAM).publicKey();
    assertEquals(AccountMeta.createRead(kaminoAuthority), authorities.get(KAMINO_PROGRAM));
    assertSame(authorities.get(KAMINO_PROGRAM), accounts.readKaminoIntegrationAuthority());

    final var splAuthority = ExtSplPDAs.integrationAuthorityPDA(SPL_PROGRAM).publicKey();
    assertEquals(AccountMeta.createRead(splAuthority), authorities.get(SPL_PROGRAM));
    assertSame(authorities.get(SPL_PROGRAM), accounts.readSplIntegrationAuthority());

    final var mintAuthority = GlamMintPDAs.integrationAuthorityPDA(MINT_PROGRAM).publicKey();
    assertEquals(AccountMeta.createRead(mintAuthority), authorities.get(MINT_PROGRAM));
    assertSame(authorities.get(MINT_PROGRAM), accounts.readMintIntegrationAuthority());
  }

  @Test
  void configAndMintPDAs() {
    final var accounts = buildMainNet();
    // PDARecord seeds are byte[] lists comparing by identity: assert the
    // derived key and nonce, not the record
    final var expectedGlobalConfig = GlamConfigPDAs.globalConfigPDA(CONFIG_PROGRAM);
    assertEquals(expectedGlobalConfig.publicKey(), accounts.globalConfigPDA().publicKey());
    assertEquals(expectedGlobalConfig.nonce(), accounts.globalConfigPDA().nonce());
    assertEquals(GlamMintPDAs.eventAuthorityPDA(MINT_PROGRAM).publicKey(), accounts.mintEventAuthority());

    final var stateKey = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
    // known-good mint PDA for share class 0, cross-checked in GlamPDATests
    final var mint = accounts.mintPDA(stateKey, 0).publicKey();
    assertEquals(fromBase58Encoded("GBCZzkTU2enaarFqBxJ2Z16yk1Rpa2hq2SKrHAywUq9V"), mint);

    assertEquals(
        fromBase58Encoded("fbHfSgPnFQYfSFebaekokALjmTPSx7poqXydCduDALy"),
        accounts.escrowPDA(mint).publicKey()
    );
    assertEquals(
        fromBase58Encoded("3nTj8ZQ9518G66K23D15nfiv8N8fpoBQ3r6tqL3ZDiQa"),
        accounts.requestQueuePDA(mint).publicKey()
    );
  }

  @Test
  void mintPDAShareClassBounds() {
    final var accounts = buildMainNet();
    final var stateKey = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");

    final var idZero = accounts.mintPDA(stateKey, 0).publicKey();
    final var idMax = accounts.mintPDA(stateKey, 255).publicKey();
    assertNotEquals(idZero, idMax);

    assertThrows(IllegalStateException.class, () -> accounts.mintPDA(stateKey, -1));
    // 256 cast to a byte is 0: accepting it would alias share class 0's PDA
    assertThrows(IllegalStateException.class, () -> accounts.mintPDA(stateKey, 256));
  }

  @Test
  void duplicateIntegrationProgramThrows() {
    final var builder = GlamAccountsBuilder.builder()
        .protocolProgram(PROTOCOL_PROGRAM)
        .configProgram(CONFIG_PROGRAM)
        .mintProgram(MINT_PROGRAM)
        .policyProgram(POLICY_PROGRAM)
        .kaminoIntegrationProgram(KAMINO_PROGRAM)
        .splIntegrationProgram(KAMINO_PROGRAM);
    assertThrows(IllegalStateException.class, builder::create);
  }

  // GlamAccountsRecord equality is unusable in tests: its globalConfigPDA is a
  // PDARecord whose byte[] seed list compares by identity. Compare the fields
  // that matter instead.
  private static void assertSameAccounts(final GlamAccounts expected, final GlamAccounts actual) {
    assertEquals(expected.invokedProtocolProgram(), actual.invokedProtocolProgram());
    assertEquals(expected.configProgram(), actual.configProgram());
    assertEquals(expected.policyProgram(), actual.policyProgram());
    assertEquals(expected.globalConfigPDA().publicKey(), actual.globalConfigPDA().publicKey());
    assertEquals(expected.invokedMintIntegrationProgram(), actual.invokedMintIntegrationProgram());
    assertEquals(expected.mintEventAuthority(), actual.mintEventAuthority());
    assertEquals(expected.invokedBridgeIntegrationProgram(), actual.invokedBridgeIntegrationProgram());
    assertEquals(expected.invokedCctpIntegrationProgram(), actual.invokedCctpIntegrationProgram());
    assertEquals(expected.invokedExternalPositionProgram(), actual.invokedExternalPositionProgram());
    assertEquals(expected.invokedJupiterIntegrationProgram(), actual.invokedJupiterIntegrationProgram());
    assertEquals(expected.invokedKaminoIntegrationProgram(), actual.invokedKaminoIntegrationProgram());
    assertEquals(expected.invokedLoopscaleIntegrationProgram(), actual.invokedLoopscaleIntegrationProgram());
    assertEquals(expected.invokedMarginFiIntegrationProgram(), actual.invokedMarginFiIntegrationProgram());
    assertEquals(expected.invokedMarinadeIntegrationProgram(), actual.invokedMarinadeIntegrationProgram());
    assertEquals(expected.invokedNeutralTradeIntegrationProgram(), actual.invokedNeutralTradeIntegrationProgram());
    assertEquals(expected.invokedOrcaIntegrationProgram(), actual.invokedOrcaIntegrationProgram());
    assertEquals(expected.invokedPhoenixIntegrationProgram(), actual.invokedPhoenixIntegrationProgram());
    assertEquals(expected.invokedSplIntegrationProgram(), actual.invokedSplIntegrationProgram());
    assertEquals(expected.invokedStakePoolIntegrationProgram(), actual.invokedStakePoolIntegrationProgram());
    assertEquals(expected.integrationAuthorities(), actual.integrationAuthorities());
  }

  @Test
  void publicKeySettersMatchStringSetters() {
    final var fromKeys = GlamAccountsBuilder.builder()
        .protocolProgram(PROTOCOL_PROGRAM)
        .configProgram(CONFIG_PROGRAM)
        .mintProgram(MINT_PROGRAM)
        .policyProgram(POLICY_PROGRAM)
        .cctpIntegrationProgram(CCTP_PROGRAM)
        .kaminoIntegrationProgram(KAMINO_PROGRAM)
        .splIntegrationProgram(SPL_PROGRAM)
        .create();
    assertSameAccounts(buildMainNet(), fromKeys);
  }

  @Test
  void driftIntegrationProgramIsIgnored() {
    final var withDrift = GlamAccountsBuilder.builder()
        .protocolProgram(PROTOCOL_PROGRAM)
        .configProgram(CONFIG_PROGRAM)
        .mintProgram(MINT_PROGRAM)
        .policyProgram(POLICY_PROGRAM)
        .cctpIntegrationProgram(CCTP_PROGRAM)
        .kaminoIntegrationProgram(KAMINO_PROGRAM)
        .splIntegrationProgram(SPL_PROGRAM)
        .driftIntegrationProgram(fromBase58Encoded("dRiftyHA39MWEi3m9aunc5MzRF1JYuBsbn6VPcn33UH"))
        .create();
    assertSameAccounts(buildMainNet(), withDrift);
  }

  @Test
  void stagingBuilderInTest() {
    // exercises every integration setter from a @Test: coverage attributed to
    // the MAIN_NET_STAGING static initializer is unstable under mutation
    final var built = GlamAccountsBuilder.builder()
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
        .marginFiIntegrationProgram("gstgMghFitRBz2GXKwgpMd7L1JXd1sg59q2v5Y83vSY")
        .marinadeIntegrationProgram("gstgmvM2o7h7GcScvXymH1oFgWskukWWxRHC1UJJ9FJ")
        .neutralTradeIntegrationProgram("gstgNyHgtURH7iuMn19GQczzv6Wc9fhPV2WDySZVyKx")
        .phoenixIntegrationProgram("gstgPL7r9aYedDDsXNtLpr4atYtNvY7zubAWWstqS3L")
        .orcaIntegrationProgram("gstgo1EgmTp2PbLSaL6Qg57P7uADx3aiRZrMsewEdSy")
        .splIntegrationProgram("gstgs9nJgX8PmRHWAAEP9H7xT3ZkaPWSGPYbj3mXdTa")
        .stakePoolIntegrationProgram("gstgS4dNeT3BTEQa1aaTS2b8CsAUz1SmwQDGosHSPsw")
        .create();
    assertSameAccounts(GlamAccounts.MAIN_NET_STAGING, built);
    // and the PublicKey overloads of the setters MAIN_NET never uses
    final var fromKeys = GlamAccountsBuilder.builder()
        .protocolProgram(GlamAccounts.MAIN_NET_STAGING.protocolProgram())
        .configProgram(CONFIG_PROGRAM)
        .mintProgram(GlamAccounts.MAIN_NET_STAGING.mintProgram())
        .policyProgram(POLICY_PROGRAM)
        .bridgeIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.bridgeIntegrationProgram())
        .cctpIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.cctpIntegrationProgram())
        .externalPositionProgram(GlamAccounts.MAIN_NET_STAGING.externalPositionProgram())
        .jupiterIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.jupiterIntegrationProgram())
        .kaminoIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.kaminoIntegrationProgram())
        .loopscaleIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.loopscaleIntegrationProgram())
        .marginFiIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.marginFiIntegrationProgram())
        .marinadeIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.marinadeIntegrationProgram())
        .neutralTradeIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.neutralTradeIntegrationProgram())
        .phoenixIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.phoenixIntegrationProgram())
        .orcaIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.orcaIntegrationProgram())
        .splIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.splIntegrationProgram())
        .stakePoolIntegrationProgram(GlamAccounts.MAIN_NET_STAGING.stakePoolIntegrationProgram())
        .create();
    assertSameAccounts(GlamAccounts.MAIN_NET_STAGING, fromKeys);
  }

  @Test
  void stagingAccounts() {
    final var staging = GlamAccounts.MAIN_NET_STAGING;
    assertEquals(
        fromBase58Encoded("gstgptmbgJVi5f8ZmSRVZjZkDQwqKa3xWuUtD5WmJHz"),
        staging.protocolProgram()
    );
    // every staging integration program is present: 14 distinct authorities
    assertEquals(14, staging.integrationAuthorities().size());
    assertNotNull(staging.readBridgeIntegrationAuthority());
    assertNotNull(staging.readExternalPositionAuthority());
    assertNotNull(staging.readJupiterIntegrationAuthority());
    assertNotNull(staging.readLoopscaleIntegrationAuthority());
    assertNotNull(staging.readMarginFiIntegrationAuthority());
    assertNotNull(staging.readMarinadeIntegrationAuthority());
    assertNotNull(staging.readNeutralTradeIntegrationAuthority());
    assertNotNull(staging.readOrcaIntegrationAuthority());
    assertNotNull(staging.readPhoenixIntegrationAuthority());
    assertNotNull(staging.readStakePoolIntegrationAuthority());
  }
}
