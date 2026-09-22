package systems.glam.sdk;

import software.sava.core.accounts.PublicKey;

import java.util.Optional;

public enum GlamEnv {

  PRODUCTION(GlamAccounts.MAIN_NET),
  STAGING(GlamAccounts.MAIN_NET_STAGING);

  private final GlamAccounts glamAccounts;

  GlamEnv(final GlamAccounts glamAccounts) {
    this.glamAccounts = glamAccounts;
  }

  public static GlamEnv from(final PublicKey protocolProgram) {
    return protocolProgram.equals(GlamAccounts.MAIN_NET.protocolProgram())
        ? PRODUCTION
        : STAGING;
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

  public PublicKey protocolProgram() {
    return glamAccounts.protocolProgram();
  }

  public GlamAccounts glamAccounts() {
    return glamAccounts;
  }
}
