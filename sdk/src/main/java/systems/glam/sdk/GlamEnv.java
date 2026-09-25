package systems.glam.sdk;

import software.sava.core.accounts.PublicKey;

import java.util.Optional;

public enum GlamEnv {

  PRODUCTION(GlamAccounts.MAIN_NET, "production"),
  STAGING(GlamAccounts.MAIN_NET_STAGING, "staging");

  private final GlamAccounts glamAccounts;
  private final String mappingEnvironment;

  GlamEnv(final GlamAccounts glamAccounts, final String mappingEnvironment) {
    this.glamAccounts = glamAccounts;
    this.mappingEnvironment = mappingEnvironment;
  }

  /// The deployment whose protocol program this is, or empty for one the sdk does not know:
  /// unlike [#from], which reads anything but production as staging, nothing is assumed.
  public static Optional<GlamEnv> ofProtocolProgram(final PublicKey protocolProgram) {
    for (final var env : values()) {
      if (env.protocolProgram().equals(protocolProgram)) {
        return Optional.of(env);
      }
    }
    return Optional.empty();
  }

  public static GlamEnv from(final PublicKey protocolProgram) {
    return protocolProgram.equals(GlamAccounts.MAIN_NET.protocolProgram())
        ? PRODUCTION
        : STAGING;
  }

  public PublicKey protocolProgram() {
    return glamAccounts.protocolProgram();
  }

  public GlamAccounts glamAccounts() {
    return glamAccounts;
  }

  /// The name an ix-mapper mapping document carries in its `environment` field for this deployment; the
  /// documents of one environment live under `glam/ix-mappings/<mappingEnvironment>` in the sdk jar.
  public String mappingEnvironment() {
    return mappingEnvironment;
  }
}
