package systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record ExecuteBridgeCpiArgs(byte[] transferId,
                                   ExecuteBridgeCpiProviderInstruction[] providerInstructions,
                                   byte[] providerTransferId,
                                   PublicKey providerEmitter,
                                   long providerSequence) implements SerDe {

  public static final int TRANSFER_ID_LEN = 32;
  public static final int PROVIDER_TRANSFER_ID_LEN = 32;
  public static final int TRANSFER_ID_OFFSET = 0;
  public static final int PROVIDER_INSTRUCTIONS_OFFSET = 32;

  public static ExecuteBridgeCpiArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var transferId = new byte[32];
    i += SerDeUtil.readArray(transferId, _data, i);
    final var providerInstructions = SerDeUtil.readVector(4, ExecuteBridgeCpiProviderInstruction.class, ExecuteBridgeCpiProviderInstruction::read, _data, i);
    i += SerDeUtil.lenVector(4, providerInstructions);
    final var providerTransferId = new byte[32];
    i += SerDeUtil.readArray(providerTransferId, _data, i);
    final var providerEmitter = readPubKey(_data, i);
    i += 32;
    final var providerSequence = getInt64LE(_data, i);
    return new ExecuteBridgeCpiArgs(transferId,
                                    providerInstructions,
                                    providerTransferId,
                                    providerEmitter,
                                    providerSequence);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeArrayChecked(transferId, 32, _data, i);
    i += SerDeUtil.writeVector(4, providerInstructions, _data, i);
    i += SerDeUtil.writeArrayChecked(providerTransferId, 32, _data, i);
    providerEmitter.write(_data, i);
    i += 32;
    putInt64LE(_data, i, providerSequence);
    i += 8;
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenArray(transferId)
         + SerDeUtil.lenVector(4, providerInstructions)
         + SerDeUtil.lenArray(providerTransferId)
         + 32
         + 8;
  }
}
