package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.GlamConfigPDAs;
import systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoPDAs;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplPDAs;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.ExtBridgePDAs;
import systems.glam.sdk.idl.programs.glam.staging.cctp.gen.ExtCctpPDAs;
import systems.glam.sdk.idl.programs.glam.staging.registered_positions.gen.ExtRpiPDAs;
import systems.glam.sdk.idl.programs.glam.staging.jupiter.gen.ExtJupiterPDAs;
import systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.ExtLoopscalePDAs;
import systems.glam.sdk.idl.programs.glam.staging.marginfi.gen.ExtMarginfiPDAs;
import systems.glam.sdk.idl.programs.glam.staging.marinade.gen.ExtMarinadePDAs;
import systems.glam.sdk.idl.programs.glam.staging.nt.gen.ExtNeutralPDAs;
import systems.glam.sdk.idl.programs.glam.staging.orca.gen.ExtOrcaPDAs;
import systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.ExtPhoenixPDAs;
import systems.glam.sdk.idl.programs.glam.staging.stake_pool.gen.ExtStakePoolPDAs;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static software.sava.core.accounts.meta.AccountMeta.createInvoked;

public final class GlamAccountsBuilder {

  private PublicKey protocolProgram;
  private PublicKey configProgram;
  private PublicKey mintProgram;
  private PublicKey policyProgram;
  private PublicKey bridgeIntegrationProgram = PublicKey.NONE;
  private PublicKey legacyCctpIntegrationProgram = PublicKey.NONE;
  private PublicKey externalPositionProgram = PublicKey.NONE;
  private PublicKey jupiterIntegrationProgram = PublicKey.NONE;
  private PublicKey kaminoIntegrationProgram = PublicKey.NONE;
  private PublicKey loopscaleIntegrationProgram = PublicKey.NONE;
  private PublicKey marginFiIntegrationProgram = PublicKey.NONE;
  private PublicKey marinadeIntegrationProgram = PublicKey.NONE;
  private PublicKey neutralTradeIntegrationProgram = PublicKey.NONE;
  private PublicKey orcaIntegrationProgram = PublicKey.NONE;
  private PublicKey phoenixIntegrationProgram = PublicKey.NONE;
  private PublicKey splIntegrationProgram = PublicKey.NONE;
  private PublicKey stakePoolIntegrationProgram = PublicKey.NONE;

  private GlamAccountsBuilder() {
  }

  public static GlamAccountsBuilder builder() {
    return new GlamAccountsBuilder();
  }

  private static PublicKey createKey(final String program) {
    return program == null ? PublicKey.NONE : PublicKey.fromBase58Encoded(program);
  }

  private static AccountMeta putIfNotNull(final Map<PublicKey, AccountMeta> map,
                                          final PublicKey key,
                                          final Function<PublicKey, ProgramDerivedAddress> value) {
    if (key != null && !key.equals(PublicKey.NONE)) {
      if (map.putIfAbsent(key, AccountMeta.createRead(value.apply(key).publicKey())) != null) {
        throw new IllegalStateException("Duplicate key: " + key);
      }
      return AccountMeta.createInvoked(key);
    } else {
      return SolanaAccounts.MAIN_NET.invokedSystemProgram();
    }
  }

  public GlamAccounts create() {
    final var map = HashMap.<PublicKey, AccountMeta>newHashMap(15);
    return new GlamAccountsRecord(
        createInvoked(protocolProgram),
        configProgram, GlamConfigPDAs.globalConfigPDA(configProgram),
        policyProgram,
        putIfNotNull(map, bridgeIntegrationProgram, ExtBridgePDAs::integrationAuthorityPDA),
        putIfNotNull(map, legacyCctpIntegrationProgram, ExtCctpPDAs::integrationAuthorityPDA),
        putIfNotNull(map, externalPositionProgram, ExtRpiPDAs::integrationAuthorityPDA),
        putIfNotNull(map, jupiterIntegrationProgram, ExtJupiterPDAs::integrationAuthorityPDA),
        putIfNotNull(map, kaminoIntegrationProgram, ExtKaminoPDAs::integrationAuthorityPDA),
        putIfNotNull(map, loopscaleIntegrationProgram, ExtLoopscalePDAs::integrationAuthorityPDA),
        putIfNotNull(map, marginFiIntegrationProgram, ExtMarginfiPDAs::integrationAuthorityPDA),
        putIfNotNull(map, marinadeIntegrationProgram, ExtMarinadePDAs::integrationAuthorityPDA),
        putIfNotNull(map, mintProgram, GlamMintPDAs::integrationAuthorityPDA),
        GlamMintPDAs.eventAuthorityPDA(mintProgram).publicKey(),
        putIfNotNull(map, neutralTradeIntegrationProgram, ExtNeutralPDAs::integrationAuthorityPDA),
        putIfNotNull(map, orcaIntegrationProgram, ExtOrcaPDAs::integrationAuthorityPDA),
        putIfNotNull(map, phoenixIntegrationProgram, ExtPhoenixPDAs::integrationAuthorityPDA),
        putIfNotNull(map, splIntegrationProgram, ExtSplPDAs::integrationAuthorityPDA),
        putIfNotNull(map, stakePoolIntegrationProgram, ExtStakePoolPDAs::integrationAuthorityPDA),
        Map.copyOf(map)
    );
  }

  public GlamAccountsBuilder protocolProgram(final PublicKey protocolProgram) {
    this.protocolProgram = protocolProgram;
    return this;
  }

  public GlamAccountsBuilder protocolProgram(final String protocolProgram) {
    return protocolProgram(PublicKey.fromBase58Encoded(protocolProgram));
  }

  public GlamAccountsBuilder configProgram(final PublicKey configProgram) {
    this.configProgram = configProgram;
    return this;
  }

  public GlamAccountsBuilder configProgram(final String configProgram) {
    return configProgram(PublicKey.fromBase58Encoded(configProgram));
  }

  public GlamAccountsBuilder mintProgram(final PublicKey mintProgram) {
    this.mintProgram = mintProgram;
    return this;
  }

  public GlamAccountsBuilder mintProgram(final String mintProgram) {
    return mintProgram(PublicKey.fromBase58Encoded(mintProgram));
  }

  public GlamAccountsBuilder policyProgram(final PublicKey policyProgram) {
    this.policyProgram = policyProgram;
    return this;
  }

  public GlamAccountsBuilder policyProgram(final String policyProgram) {
    return policyProgram(PublicKey.fromBase58Encoded(policyProgram));
  }

  public GlamAccountsBuilder bridgeIntegrationProgram(final PublicKey bridgeIntegrationProgram) {
    this.bridgeIntegrationProgram = bridgeIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder bridgeIntegrationProgram(final String bridgeIntegrationProgram) {
    return bridgeIntegrationProgram(createKey(bridgeIntegrationProgram));
  }

  public GlamAccountsBuilder legacyCctpIntegrationProgram(final PublicKey legacyCctpIntegrationProgram) {
    this.legacyCctpIntegrationProgram = legacyCctpIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder legacyCctpIntegrationProgram(final String legacyCctpIntegrationProgram) {
    return legacyCctpIntegrationProgram(createKey(legacyCctpIntegrationProgram));
  }

  /**
   * @deprecated Use {@link #bridgeIntegrationProgram(PublicKey)} for current CCTP, or
   * {@link #legacyCctpIntegrationProgram(PublicKey)} for the standalone legacy program.
   */
  @Deprecated(forRemoval = false)
  public GlamAccountsBuilder cctpIntegrationProgram(final PublicKey cctpIntegrationProgram) {
    return legacyCctpIntegrationProgram(cctpIntegrationProgram);
  }

  /**
   * @deprecated Use {@link #bridgeIntegrationProgram(String)} for current CCTP, or
   * {@link #legacyCctpIntegrationProgram(String)} for the standalone legacy program.
   */
  @Deprecated(forRemoval = false)
  public GlamAccountsBuilder cctpIntegrationProgram(final String cctpIntegrationProgram) {
    return legacyCctpIntegrationProgram(cctpIntegrationProgram);
  }

  public GlamAccountsBuilder driftIntegrationProgram(final PublicKey driftIntegrationProgram) {
    return this;
  }

  public GlamAccountsBuilder driftIntegrationProgram(final String driftIntegrationProgram) {
    return driftIntegrationProgram(createKey(driftIntegrationProgram));
  }

  public GlamAccountsBuilder externalPositionProgram(final PublicKey externalPositionProgram) {
    this.externalPositionProgram = externalPositionProgram;
    return this;
  }

  public GlamAccountsBuilder externalPositionProgram(final String externalPositionProgram) {
    return externalPositionProgram(createKey(externalPositionProgram));
  }

  public GlamAccountsBuilder jupiterIntegrationProgram(final PublicKey jupiterIntegrationProgram) {
    this.jupiterIntegrationProgram = jupiterIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder jupiterIntegrationProgram(final String jupiterIntegrationProgram) {
    return jupiterIntegrationProgram(createKey(jupiterIntegrationProgram));
  }

  public GlamAccountsBuilder kaminoIntegrationProgram(final PublicKey kaminoIntegrationProgram) {
    this.kaminoIntegrationProgram = kaminoIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder kaminoIntegrationProgram(final String kaminoIntegrationProgram) {
    return kaminoIntegrationProgram(createKey(kaminoIntegrationProgram));
  }

  public GlamAccountsBuilder loopscaleIntegrationProgram(final PublicKey loopscaleIntegrationProgram) {
    this.loopscaleIntegrationProgram = loopscaleIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder loopscaleIntegrationProgram(final String loopscaleIntegrationProgram) {
    return loopscaleIntegrationProgram(createKey(loopscaleIntegrationProgram));
  }

  public GlamAccountsBuilder marginFiIntegrationProgram(final PublicKey marginFiIntegrationProgram) {
    this.marginFiIntegrationProgram = marginFiIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder marginFiIntegrationProgram(final String marginFiIntegrationProgram) {
    return marginFiIntegrationProgram(createKey(marginFiIntegrationProgram));
  }

  public GlamAccountsBuilder marinadeIntegrationProgram(final PublicKey marinadeIntegrationProgram) {
    this.marinadeIntegrationProgram = marinadeIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder marinadeIntegrationProgram(final String marinadeIntegrationProgram) {
    return marinadeIntegrationProgram(createKey(marinadeIntegrationProgram));
  }

  public GlamAccountsBuilder neutralTradeIntegrationProgram(final PublicKey neutralTradeIntegrationProgram) {
    this.neutralTradeIntegrationProgram = neutralTradeIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder neutralTradeIntegrationProgram(final String neutralTradeIntegrationProgram) {
    return neutralTradeIntegrationProgram(createKey(neutralTradeIntegrationProgram));
  }

  public GlamAccountsBuilder orcaIntegrationProgram(final PublicKey orcaIntegrationProgram) {
    this.orcaIntegrationProgram = orcaIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder orcaIntegrationProgram(final String orcaIntegrationProgram) {
    return orcaIntegrationProgram(createKey(orcaIntegrationProgram));
  }

  public GlamAccountsBuilder phoenixIntegrationProgram(final PublicKey phoenixIntegrationProgram) {
    this.phoenixIntegrationProgram = phoenixIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder phoenixIntegrationProgram(final String phoenixIntegrationProgram) {
    return phoenixIntegrationProgram(createKey(phoenixIntegrationProgram));
  }

  public GlamAccountsBuilder splIntegrationProgram(final PublicKey splIntegrationProgram) {
    this.splIntegrationProgram = splIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder splIntegrationProgram(final String splIntegrationProgram) {
    return splIntegrationProgram(createKey(splIntegrationProgram));
  }

  public GlamAccountsBuilder stakePoolIntegrationProgram(final PublicKey stakePoolIntegrationProgram) {
    this.stakePoolIntegrationProgram = stakePoolIntegrationProgram;
    return this;
  }

  public GlamAccountsBuilder stakePoolIntegrationProgram(final String stakePoolIntegrationProgram) {
    return stakePoolIntegrationProgram(createKey(stakePoolIntegrationProgram));
  }
}
