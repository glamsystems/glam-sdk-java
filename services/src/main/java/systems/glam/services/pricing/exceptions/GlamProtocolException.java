package systems.glam.services.pricing.exceptions;

import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;

public final class GlamProtocolException extends RuntimeException {

  private final GlamProtocolError error;

  public GlamProtocolException(final GlamProtocolError error) {
    this.error = error;
  }
}
