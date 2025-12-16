package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;
import software.sava.core.borsh.RustEnum;

import static java.nio.charset.StandardCharsets.UTF_8;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt32LE;
import static software.sava.core.encoding.ByteUtil.getInt64LE;

public sealed interface EngineFieldValue extends RustEnum permits
  EngineFieldValue.Boolean,
  EngineFieldValue.U8,
  EngineFieldValue.U32,
  EngineFieldValue.U64,
  EngineFieldValue.String,
  EngineFieldValue.Pubkey,
  EngineFieldValue.VecPubkey,
  EngineFieldValue.VecU8,
  EngineFieldValue.VecU32,
  EngineFieldValue.VecDelegateAcl,
  EngineFieldValue.VecIntegrationAcl,
  EngineFieldValue.FeeStructure,
  EngineFieldValue.FeeParams,
  EngineFieldValue.AccruedFees,
  EngineFieldValue.NotifyAndSettle,
  EngineFieldValue.OracleConfigs {

  static EngineFieldValue read(final byte[] _data, final int _offset) {
    final int ordinal = _data[_offset] & 0xFF;
    final int i = _offset + 1;
    return switch (ordinal) {
      case 0 -> Boolean.read(_data, i);
      case 1 -> U8.read(_data, i);
      case 2 -> U32.read(_data, i);
      case 3 -> U64.read(_data, i);
      case 4 -> String.read(_data, i);
      case 5 -> Pubkey.read(_data, i);
      case 6 -> VecPubkey.read(_data, i);
      case 7 -> VecU8.read(_data, i);
      case 8 -> VecU32.read(_data, i);
      case 9 -> VecDelegateAcl.read(_data, i);
      case 10 -> VecIntegrationAcl.read(_data, i);
      case 11 -> FeeStructure.read(_data, i);
      case 12 -> FeeParams.read(_data, i);
      case 13 -> AccruedFees.read(_data, i);
      case 14 -> NotifyAndSettle.read(_data, i);
      case 15 -> OracleConfigs.read(_data, i);
      default -> null;
    };
  }

  record Boolean(boolean val) implements EnumBool, EngineFieldValue {

    public static final Boolean TRUE = new Boolean(true);
    public static final Boolean FALSE = new Boolean(false);

    public static Boolean read(final byte[] _data, int i) {
      return _data[i] == 1 ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override
    public int ordinal() {
      return 0;
    }
  }

  record U8(int val) implements EnumInt8, EngineFieldValue {

    public static U8 read(final byte[] _data, int i) {
      return new U8(_data[i] & 0xFF);
    }

    @Override
    public int ordinal() {
      return 1;
    }
  }

  record U32(int val) implements EnumInt32, EngineFieldValue {

    public static U32 read(final byte[] _data, int i) {
      return new U32(getInt32LE(_data, i));
    }

    @Override
    public int ordinal() {
      return 2;
    }
  }

  record U64(long val) implements EnumInt64, EngineFieldValue {

    public static U64 read(final byte[] _data, int i) {
      return new U64(getInt64LE(_data, i));
    }

    @Override
    public int ordinal() {
      return 3;
    }
  }

  record String(byte[] val, java.lang.String _val) implements EnumString, EngineFieldValue {

    public static String createRecord(final java.lang.String val) {
      return new String(val.getBytes(UTF_8), val);
    }

    public static String read(final byte[] data, final int _offset) {
      return createRecord(Borsh.string(data, _offset));
    }

    @Override
    public int ordinal() {
      return 4;
    }
  }

  record Pubkey(PublicKey val) implements EnumPublicKey, EngineFieldValue {

    public static Pubkey read(final byte[] _data, int i) {
      return new Pubkey(readPubKey(_data, i));
    }

    @Override
    public int ordinal() {
      return 5;
    }
  }

  record VecPubkey(PublicKey[] val) implements EngineFieldValue {

    public static VecPubkey read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var val = Borsh.readPublicKeyVector(_data, _offset);
      return new VecPubkey(val);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = writeOrdinal(_data, _offset);
      i += Borsh.writeVector(val, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 1 + Borsh.lenVector(val);
    }

    @Override
    public int ordinal() {
      return 6;
    }
  }

  record VecU8(byte[] val) implements EngineFieldValue {

    public static VecU8 read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var val = Borsh.readbyteVector(_data, _offset);
      return new VecU8(val);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = writeOrdinal(_data, _offset);
      i += Borsh.writeVector(val, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 1 + Borsh.lenVector(val);
    }

    @Override
    public int ordinal() {
      return 7;
    }
  }

  record VecU32(int[] val) implements EngineFieldValue {

    public static VecU32 read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var val = Borsh.readintVector(_data, _offset);
      return new VecU32(val);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = writeOrdinal(_data, _offset);
      i += Borsh.writeVector(val, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 1 + Borsh.lenVector(val);
    }

    @Override
    public int ordinal() {
      return 8;
    }
  }

  record VecDelegateAcl(DelegateAcl[] val) implements EngineFieldValue {

    public static VecDelegateAcl read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var val = Borsh.readVector(DelegateAcl.class, DelegateAcl::read, _data, _offset);
      return new VecDelegateAcl(val);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = writeOrdinal(_data, _offset);
      i += Borsh.writeVector(val, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 1 + Borsh.lenVector(val);
    }

    @Override
    public int ordinal() {
      return 9;
    }
  }

  record VecIntegrationAcl(IntegrationAcl[] val) implements EngineFieldValue {

    public static VecIntegrationAcl read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var val = Borsh.readVector(IntegrationAcl.class, IntegrationAcl::read, _data, _offset);
      return new VecIntegrationAcl(val);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = writeOrdinal(_data, _offset);
      i += Borsh.writeVector(val, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 1 + Borsh.lenVector(val);
    }

    @Override
    public int ordinal() {
      return 10;
    }
  }

  record FeeStructure(systems.glam.sdk.idl.programs.glam.protocol.gen.types.FeeStructure val) implements BorshEnum, EngineFieldValue {

    public static FeeStructure read(final byte[] _data, final int _offset) {
      return new FeeStructure(systems.glam.sdk.idl.programs.glam.protocol.gen.types.FeeStructure.read(_data, _offset));
    }

    @Override
    public int ordinal() {
      return 11;
    }
  }

  record FeeParams(systems.glam.sdk.idl.programs.glam.protocol.gen.types.FeeParams val) implements BorshEnum, EngineFieldValue {

    public static FeeParams read(final byte[] _data, final int _offset) {
      return new FeeParams(systems.glam.sdk.idl.programs.glam.protocol.gen.types.FeeParams.read(_data, _offset));
    }

    @Override
    public int ordinal() {
      return 12;
    }
  }

  record AccruedFees(systems.glam.sdk.idl.programs.glam.protocol.gen.types.AccruedFees val) implements BorshEnum, EngineFieldValue {

    public static AccruedFees read(final byte[] _data, final int _offset) {
      return new AccruedFees(systems.glam.sdk.idl.programs.glam.protocol.gen.types.AccruedFees.read(_data, _offset));
    }

    @Override
    public int ordinal() {
      return 13;
    }
  }

  record NotifyAndSettle(systems.glam.sdk.idl.programs.glam.protocol.gen.types.NotifyAndSettle val) implements BorshEnum, EngineFieldValue {

    public static NotifyAndSettle read(final byte[] _data, final int _offset) {
      return new NotifyAndSettle(systems.glam.sdk.idl.programs.glam.protocol.gen.types.NotifyAndSettle.read(_data, _offset));
    }

    @Override
    public int ordinal() {
      return 14;
    }
  }

  record OracleConfigs(systems.glam.sdk.idl.programs.glam.protocol.gen.types.OracleConfigs val) implements BorshEnum, EngineFieldValue {

    public static OracleConfigs read(final byte[] _data, final int _offset) {
      return new OracleConfigs(systems.glam.sdk.idl.programs.glam.protocol.gen.types.OracleConfigs.read(_data, _offset));
    }

    @Override
    public int ordinal() {
      return 15;
    }
  }
}
