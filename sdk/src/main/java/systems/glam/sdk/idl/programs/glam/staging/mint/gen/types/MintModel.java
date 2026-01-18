package systems.glam.sdk.idl.programs.glam.staging.mint.gen.types;

import java.lang.Boolean;
import java.lang.String;

import java.util.Arrays;
import java.util.OptionalInt;
import java.util.OptionalLong;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.FeeStructure;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.NotifyAndSettle;

import static java.nio.charset.StandardCharsets.UTF_8;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt32LE;
import static software.sava.core.encoding.ByteUtil.getInt64LE;

public record MintModel(String symbol, byte[] _symbol,
                        byte[] name,
                        String uri, byte[] _uri,
                        OptionalInt yearInSeconds,
                        PublicKey permanentDelegate,
                        Boolean defaultAccountStateFrozen,
                        FeeStructure feeStructure,
                        NotifyAndSettle notifyAndSettle,
                        OptionalInt lockupPeriod,
                        OptionalLong maxCap,
                        OptionalLong minSubscription,
                        OptionalLong minRedemption,
                        PublicKey[] allowlist,
                        PublicKey[] blocklist) implements SerDe {

  public static final int SYMBOL_OFFSET = 1;

  public static MintModel createRecord(final String symbol,
                                       final byte[] name,
                                       final String uri,
                                       final OptionalInt yearInSeconds,
                                       final PublicKey permanentDelegate,
                                       final Boolean defaultAccountStateFrozen,
                                       final FeeStructure feeStructure,
                                       final NotifyAndSettle notifyAndSettle,
                                       final OptionalInt lockupPeriod,
                                       final OptionalLong maxCap,
                                       final OptionalLong minSubscription,
                                       final OptionalLong minRedemption,
                                       final PublicKey[] allowlist,
                                       final PublicKey[] blocklist) {
    return new MintModel(symbol, symbol == null ? null : symbol.getBytes(UTF_8),
                         name,
                         uri, uri == null ? null : uri.getBytes(UTF_8),
                         yearInSeconds,
                         permanentDelegate,
                         defaultAccountStateFrozen,
                         feeStructure,
                         notifyAndSettle,
                         lockupPeriod,
                         maxCap,
                         minSubscription,
                         minRedemption,
                         allowlist,
                         blocklist);
  }

  public static MintModel read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final byte[] _symbol;
    final String symbol;
    if (_data[i++] == 0) {
      _symbol = null;
      symbol = null;
    } else {
      final int _symbolLength = ByteUtil.getInt32LE(_data, i);
      i += 4;
      _symbol = Arrays.copyOfRange(_data, i, i + _symbolLength);
      symbol = new String(_symbol);
      i += _symbolLength;
    }

    final byte[] name;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      name = null;
      ++i;
    } else {
      ++i;
      name = new byte[32];
      i += SerDeUtil.readArray(name, _data, i);
    }
    final byte[] _uri;
    final String uri;
    if (_data[i++] == 0) {
      _uri = null;
      uri = null;
    } else {
      final int _uriLength = ByteUtil.getInt32LE(_data, i);
      i += 4;
      _uri = Arrays.copyOfRange(_data, i, i + _uriLength);
      uri = new String(_uri);
      i += _uriLength;
    }

    final OptionalInt yearInSeconds;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      yearInSeconds = OptionalInt.empty();
      ++i;
    } else {
      ++i;
      yearInSeconds = OptionalInt.of(getInt32LE(_data, i));
      i += 4;
    }
    final PublicKey permanentDelegate;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      permanentDelegate = null;
      ++i;
    } else {
      ++i;
      permanentDelegate = readPubKey(_data, i);
      i += 32;
    }
    final Boolean defaultAccountStateFrozen;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      defaultAccountStateFrozen = null;
      ++i;
    } else {
      ++i;
      defaultAccountStateFrozen = _data[i] == 1;
      ++i;
    }
    final FeeStructure feeStructure;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      feeStructure = null;
      ++i;
    } else {
      ++i;
      feeStructure = FeeStructure.read(_data, i);
      i += feeStructure.l();
    }
    final NotifyAndSettle notifyAndSettle;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      notifyAndSettle = null;
      ++i;
    } else {
      ++i;
      notifyAndSettle = NotifyAndSettle.read(_data, i);
      i += notifyAndSettle.l();
    }
    final OptionalInt lockupPeriod;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      lockupPeriod = OptionalInt.empty();
      ++i;
    } else {
      ++i;
      lockupPeriod = OptionalInt.of(getInt32LE(_data, i));
      i += 4;
    }
    final OptionalLong maxCap;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      maxCap = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      maxCap = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong minSubscription;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      minSubscription = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      minSubscription = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong minRedemption;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      minRedemption = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      minRedemption = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final PublicKey[] allowlist;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      allowlist = null;
      ++i;
    } else {
      ++i;
      allowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
      i += SerDeUtil.lenVector(4, allowlist);
    }
    final PublicKey[] blocklist;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      blocklist = null;
    } else {
      ++i;
      blocklist = SerDeUtil.readPublicKeyVector(4, _data, i);
    }
    return new MintModel(symbol, _symbol,
                         name,
                         uri, _uri,
                         yearInSeconds,
                         permanentDelegate,
                         defaultAccountStateFrozen,
                         feeStructure,
                         notifyAndSettle,
                         lockupPeriod,
                         maxCap,
                         minSubscription,
                         minRedemption,
                         allowlist,
                         blocklist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeOptionalVector(1, 4, _symbol, _data, i);
    if (name == null || name.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeArrayChecked(name, 32, _data, i);
    }
    i += SerDeUtil.writeOptionalVector(1, 4, _uri, _data, i);
    i += SerDeUtil.writeOptional(1, yearInSeconds, _data, i);
    i += SerDeUtil.writeOptional(1, permanentDelegate, _data, i);
    i += SerDeUtil.writeOptional(1, defaultAccountStateFrozen, _data, i);
    i += SerDeUtil.writeOptional(1, feeStructure, _data, i);
    i += SerDeUtil.writeOptional(1, notifyAndSettle, _data, i);
    i += SerDeUtil.writeOptional(1, lockupPeriod, _data, i);
    i += SerDeUtil.writeOptional(1, maxCap, _data, i);
    i += SerDeUtil.writeOptional(1, minSubscription, _data, i);
    i += SerDeUtil.writeOptional(1, minRedemption, _data, i);
    if (allowlist == null || allowlist.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeVector(4, allowlist, _data, i);
    }
    if (blocklist == null || blocklist.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeVector(4, blocklist, _data, i);
    }
    return i - _offset;
  }

  @Override
  public int l() {
    return (_symbol == null || _symbol.length == 0 ? 1 : (1 + _symbol.length))
         + (name == null || name.length == 0 ? 1 : (1 + SerDeUtil.lenArray(name)))
         + (_uri == null || _uri.length == 0 ? 1 : (1 + _uri.length))
         + (yearInSeconds == null || yearInSeconds.isEmpty() ? 1 : (1 + 4))
         + (permanentDelegate == null ? 1 : (1 + 32))
         + (defaultAccountStateFrozen == null ? 1 : (1 + 1))
         + (feeStructure == null ? 1 : (1 + feeStructure.l()))
         + (notifyAndSettle == null ? 1 : (1 + notifyAndSettle.l()))
         + (lockupPeriod == null || lockupPeriod.isEmpty() ? 1 : (1 + 4))
         + (maxCap == null || maxCap.isEmpty() ? 1 : (1 + 8))
         + (minSubscription == null || minSubscription.isEmpty() ? 1 : (1 + 8))
         + (minRedemption == null || minRedemption.isEmpty() ? 1 : (1 + 8))
         + (allowlist == null || allowlist.length == 0 ? 1 : (1 + SerDeUtil.lenVector(4, allowlist)))
         + (blocklist == null || blocklist.length == 0 ? 1 : (1 + SerDeUtil.lenVector(4, blocklist)));
  }
}
