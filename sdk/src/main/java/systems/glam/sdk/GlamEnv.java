package systems.glam.sdk;

import software.sava.core.accounts.PublicKey;

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

  public PublicKey protocolProgram() {
    return glamAccounts.protocolProgram();
  }

  public GlamAccounts glamAccounts() {
    return glamAccounts;
  }
}
