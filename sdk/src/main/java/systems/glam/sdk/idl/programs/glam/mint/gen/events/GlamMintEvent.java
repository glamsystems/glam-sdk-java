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

  static GlamMintEvent read(final byte[] _data) {
    return read(_data, 0);
  }

  static GlamMintEvent readCPI(final byte[] _data, final int _offset) {
    return read(_data, _offset + 8);
  }

  static GlamMintEvent readCPI(final byte[] _data) {
    return read(_data, 8);
  }
}