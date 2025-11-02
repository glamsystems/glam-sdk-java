package systems.glam.sdk.idl.programs.glam.mint.gen.events;

import software.sava.core.borsh.Borsh;

public sealed interface GlamMintEvent extends Borsh permits
    AumRecord,
    PricedProtocolRecord {

  static GlamMintEvent read(final byte[] _data, final int _offset) {
    if (AumRecord.DISCRIMINATOR.equals(_data, _offset)) {
      return AumRecord.read(_data, _offset);
    } else if (PricedProtocolRecord.DISCRIMINATOR.equals(_data, _offset)) {
      return PricedProtocolRecord.read(_data, _offset);
    } else {
      return null;
    }
  }
}