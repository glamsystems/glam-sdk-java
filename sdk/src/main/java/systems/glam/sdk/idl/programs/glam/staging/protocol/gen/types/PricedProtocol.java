package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import java.math.BigInteger;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt128LE;
import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt128LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record PricedProtocol(long rent,
                             BigInteger amount,
                             int decimals,
                             long lastUpdatedSlot,
                             PublicKey integrationProgram,
                             int protocolBitflag,
                             PublicKey[] positions) implements SerDe {

  public static PricedProtocol read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var rent = getInt64LE(_data, i);
    i += 8;
    final var amount = getInt128LE(_data, i);
    i += 16;
    final var decimals = _data[i] & 0xFF;
    ++i;
    final var lastUpdatedSlot = getInt64LE(_data, i);
    i += 8;
    final var integrationProgram = readPubKey(_data, i);
    i += 32;
    final var protocolBitflag = getInt16LE(_data, i);
    i += 2;
    final var positions = SerDeUtil.readPublicKeyVector(4, _data, i);
    return new PricedProtocol(rent,
                              amount,
                              decimals,
                              lastUpdatedSlot,
                              integrationProgram,
                              protocolBitflag,
                              positions);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt64LE(_data, i, rent);
    i += 8;
    putInt128LE(_data, i, amount);
    i += 16;
    _data[i] = (byte) decimals;
    ++i;
    putInt64LE(_data, i, lastUpdatedSlot);
    i += 8;
    integrationProgram.write(_data, i);
    i += 32;
    putInt16LE(_data, i, protocolBitflag);
    i += 2;
    i += SerDeUtil.writeVector(4, positions, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 8
         + 16
         + 1
         + 8
         + 32
         + 2
         + SerDeUtil.lenVector(4, positions);
  }
}
