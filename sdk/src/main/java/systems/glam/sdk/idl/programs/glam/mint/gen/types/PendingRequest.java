package systems.glam.sdk.idl.programs.glam.mint.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record PendingRequest(PublicKey user,
                             long incoming,
                             long outgoing,
                             long createdAt,
                             long fulfilledAt,
                             int timeUnit,
                             RequestType requestType,
                             byte[] reserved) implements SerDe {

  public static final int BYTES = 72;
  public static final int RESERVED_LEN = 6;

  public static final int USER_OFFSET = 0;
  public static final int INCOMING_OFFSET = 32;
  public static final int OUTGOING_OFFSET = 40;
  public static final int CREATED_AT_OFFSET = 48;
  public static final int FULFILLED_AT_OFFSET = 56;
  public static final int TIME_UNIT_OFFSET = 64;
  public static final int REQUEST_TYPE_OFFSET = 65;
  public static final int RESERVED_OFFSET = 66;

  public static PendingRequest read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var user = readPubKey(_data, i);
    i += 32;
    final var incoming = getInt64LE(_data, i);
    i += 8;
    final var outgoing = getInt64LE(_data, i);
    i += 8;
    final var createdAt = getInt64LE(_data, i);
    i += 8;
    final var fulfilledAt = getInt64LE(_data, i);
    i += 8;
    final var timeUnit = _data[i] & 0xFF;
    ++i;
    final var requestType = RequestType.read(_data, i);
    i += requestType.l();
    final var reserved = new byte[6];
    SerDeUtil.readArray(reserved, _data, i);
    return new PendingRequest(user,
                              incoming,
                              outgoing,
                              createdAt,
                              fulfilledAt,
                              timeUnit,
                              requestType,
                              reserved);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    user.write(_data, i);
    i += 32;
    putInt64LE(_data, i, incoming);
    i += 8;
    putInt64LE(_data, i, outgoing);
    i += 8;
    putInt64LE(_data, i, createdAt);
    i += 8;
    putInt64LE(_data, i, fulfilledAt);
    i += 8;
    _data[i] = (byte) timeUnit;
    ++i;
    i += requestType.write(_data, i);
    i += SerDeUtil.writeArrayChecked(reserved, 6, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
