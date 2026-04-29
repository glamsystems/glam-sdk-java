package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;

public record ProtocolIntegration(PublicKey integrationProgram,
                                  int protocolsBitmask) implements Comparable<ProtocolIntegration> {

  @Override
  public int compareTo(final ProtocolIntegration o) {
    return integrationProgram.compareTo(o.integrationProgram);
  }
}
