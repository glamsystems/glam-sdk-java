package systems.glam.sdk.idl.programs.glam.mint.gen.types;

import java.lang.Boolean;
import java.lang.String;

import java.util.Arrays;
import java.util.OptionalInt;
import java.util.OptionalLong;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;

import systems.glam.sdk.idl.programs.glam.protocol.gen.types.FeeStructure;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.NotifyAndSettle;

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
                        PublicKey[] blocklist) implements Borsh {

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
    return new MintModel(symbol, Borsh.getBytes(symbol),
                         name,
                         uri, Borsh.getBytes(uri),
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
    final String symbol;
    if (_data[i] == 0) {
      symbol = null;
      ++i;
    } else {
      ++i;
      final int _symbolLength = getInt32LE(_data, i);
      i += 4;
      final byte[] _symbol = Arrays.copyOfRange(_data, i, i + _symbolLength);
      symbol = new String(_symbol, UTF_8);
      i += _symbol.length;
    }
    final byte[] name;
    if (_data[i] == 0) {
      name = null;
      ++i;
    } else {
      ++i;
      name = new byte[32];
      i += Borsh.readArray(name, _data, i);
    }
    final String uri;
    if (_data[i] == 0) {
      uri = null;
      ++i;
    } else {
      ++i;
      final int _uriLength = getInt32LE(_data, i);
      i += 4;
      final byte[] _uri = Arrays.copyOfRange(_data, i, i + _uriLength);
      uri = new String(_uri, UTF_8);
      i += _uri.length;
    }
    final OptionalInt yearInSeconds;
    if (_data[i] == 0) {
      yearInSeconds = OptionalInt.empty();
      ++i;
    } else {
      ++i;
      yearInSeconds = OptionalInt.of(getInt32LE(_data, i));
      i += 4;
    }
    final PublicKey permanentDelegate;
    if (_data[i] == 0) {
      permanentDelegate = null;
      ++i;
    } else {
      ++i;
      permanentDelegate = readPubKey(_data, i);
      i += 32;
    }
    final Boolean defaultAccountStateFrozen;
    if (_data[i] == 0) {
      defaultAccountStateFrozen = null;
      ++i;
    } else {
      ++i;
      defaultAccountStateFrozen = _data[i] == 1;
      ++i;
    }
    final FeeStructure feeStructure;
    if (_data[i] == 0) {
      feeStructure = null;
      ++i;
    } else {
      ++i;
      feeStructure = FeeStructure.read(_data, i);
      i += feeStructure.l();
    }
    final NotifyAndSettle notifyAndSettle;
    if (_data[i] == 0) {
      notifyAndSettle = null;
      ++i;
    } else {
      ++i;
      notifyAndSettle = NotifyAndSettle.read(_data, i);
      i += notifyAndSettle.l();
    }
    final OptionalInt lockupPeriod;
    if (_data[i] == 0) {
      lockupPeriod = OptionalInt.empty();
      ++i;
    } else {
      ++i;
      lockupPeriod = OptionalInt.of(getInt32LE(_data, i));
      i += 4;
    }
    final OptionalLong maxCap;
    if (_data[i] == 0) {
      maxCap = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      maxCap = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong minSubscription;
    if (_data[i] == 0) {
      minSubscription = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      minSubscription = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong minRedemption;
    if (_data[i] == 0) {
      minRedemption = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      minRedemption = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final PublicKey[] allowlist;
    if (_data[i] == 0) {
      allowlist = null;
      ++i;
    } else {
      ++i;
      allowlist = Borsh.readPublicKeyVector(_data, i);
      i += Borsh.lenVector(allowlist);
    }
    final PublicKey[] blocklist;
    if (_data[i] == 0) {
      blocklist = null;
    } else {
      ++i;
      blocklist = Borsh.readPublicKeyVector(_data, i);
    }
    return new MintModel(symbol, Borsh.getBytes(symbol),
                         name,
                         uri, Borsh.getBytes(uri),
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
    i += Borsh.writeOptionalVector(_symbol, _data, i);
    if (name == null || name.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeArrayChecked(name, 32, _data, i);
    }
    i += Borsh.writeOptionalVector(_uri, _data, i);
    i += Borsh.writeOptional(yearInSeconds, _data, i);
    i += Borsh.writeOptional(permanentDelegate, _data, i);
    i += Borsh.writeOptional(defaultAccountStateFrozen, _data, i);
    i += Borsh.writeOptional(feeStructure, _data, i);
    i += Borsh.writeOptional(notifyAndSettle, _data, i);
    i += Borsh.writeOptional(lockupPeriod, _data, i);
    i += Borsh.writeOptional(maxCap, _data, i);
    i += Borsh.writeOptional(minSubscription, _data, i);
    i += Borsh.writeOptional(minRedemption, _data, i);
    if (allowlist == null || allowlist.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeVector(allowlist, _data, i);
    }
    if (blocklist == null || blocklist.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeVector(blocklist, _data, i);
    }
    return i - _offset;
  }

  @Override
  public int l() {
    return (_symbol == null || _symbol.length == 0 ? 1 : (1 + _symbol.length))
         + (name == null || name.length == 0 ? 1 : (1 + Borsh.lenArray(name)))
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
         + (allowlist == null || allowlist.length == 0 ? 1 : (1 + Borsh.lenVector(allowlist)))
         + (blocklist == null || blocklist.length == 0 ? 1 : (1 + Borsh.lenVector(blocklist)));
  }
}
