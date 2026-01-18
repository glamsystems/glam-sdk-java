package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import java.util.OptionalLong;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;

public record ExtraParams(ActionType actionType,
                          PublicKey pubkey,
                          OptionalLong amount) implements SerDe {

  public static final int ACTION_TYPE_OFFSET = 0;
  public static final int PUBKEY_OFFSET = 1;
  public static final int AMOUNT_OFFSET = 34;

  public static ExtraParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var actionType = ActionType.read(_data, i);
    i += actionType.l();
    final var pubkey = readPubKey(_data, i);
    i += 32;
    final OptionalLong amount;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      amount = OptionalLong.empty();
    } else {
      ++i;
      amount = OptionalLong.of(getInt64LE(_data, i));
    }
    return new ExtraParams(actionType, pubkey, amount);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += actionType.write(_data, i);
    pubkey.write(_data, i);
    i += 32;
    i += SerDeUtil.writeOptional(1, amount, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return actionType.l() + 32 + (amount == null || amount.isEmpty() ? 1 : (1 + 8));
  }
}
