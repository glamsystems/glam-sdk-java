package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import java.lang.Boolean;
import java.lang.String;

import java.util.Arrays;
import java.util.OptionalInt;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static java.nio.charset.StandardCharsets.UTF_8;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt32LE;

public record StateModel(AccountType accountType,
                         byte[] name,
                         String uri, byte[] _uri,
                         Boolean enabled,
                         PublicKey[] assets,
                         CreatedModel created,
                         PublicKey owner,
                         byte[] portfolioManagerName,
                         PublicKey[] borrowable,
                         OptionalInt timelockDuration,
                         IntegrationAcl[] integrationAcls,
                         DelegateAcl[] delegateAcls) implements SerDe {

  public static StateModel createRecord(final AccountType accountType,
                                        final byte[] name,
                                        final String uri,
                                        final Boolean enabled,
                                        final PublicKey[] assets,
                                        final CreatedModel created,
                                        final PublicKey owner,
                                        final byte[] portfolioManagerName,
                                        final PublicKey[] borrowable,
                                        final OptionalInt timelockDuration,
                                        final IntegrationAcl[] integrationAcls,
                                        final DelegateAcl[] delegateAcls) {
    return new StateModel(accountType,
                          name,
                          uri, uri == null ? null : uri.getBytes(UTF_8),
                          enabled,
                          assets,
                          created,
                          owner,
                          portfolioManagerName,
                          borrowable,
                          timelockDuration,
                          integrationAcls,
                          delegateAcls);
  }

  public static StateModel read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final AccountType accountType;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      accountType = null;
      ++i;
    } else {
      ++i;
      accountType = AccountType.read(_data, i);
      i += accountType.l();
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

    final Boolean enabled;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      enabled = null;
      ++i;
    } else {
      ++i;
      enabled = _data[i] == 1;
      ++i;
    }
    final PublicKey[] assets;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      assets = null;
      ++i;
    } else {
      ++i;
      assets = SerDeUtil.readPublicKeyVector(4, _data, i);
      i += SerDeUtil.lenVector(4, assets);
    }
    final CreatedModel created;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      created = null;
      ++i;
    } else {
      ++i;
      created = CreatedModel.read(_data, i);
      i += created.l();
    }
    final PublicKey owner;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      owner = null;
      ++i;
    } else {
      ++i;
      owner = readPubKey(_data, i);
      i += 32;
    }
    final byte[] portfolioManagerName;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      portfolioManagerName = null;
      ++i;
    } else {
      ++i;
      portfolioManagerName = new byte[32];
      i += SerDeUtil.readArray(portfolioManagerName, _data, i);
    }
    final PublicKey[] borrowable;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      borrowable = null;
      ++i;
    } else {
      ++i;
      borrowable = SerDeUtil.readPublicKeyVector(4, _data, i);
      i += SerDeUtil.lenVector(4, borrowable);
    }
    final OptionalInt timelockDuration;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      timelockDuration = OptionalInt.empty();
      ++i;
    } else {
      ++i;
      timelockDuration = OptionalInt.of(getInt32LE(_data, i));
      i += 4;
    }
    final IntegrationAcl[] integrationAcls;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      integrationAcls = null;
      ++i;
    } else {
      ++i;
      integrationAcls = SerDeUtil.readVector(4, IntegrationAcl.class, IntegrationAcl::read, _data, i);
      i += SerDeUtil.lenVector(4, integrationAcls);
    }
    final DelegateAcl[] delegateAcls;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      delegateAcls = null;
    } else {
      ++i;
      delegateAcls = SerDeUtil.readVector(4, DelegateAcl.class, DelegateAcl::read, _data, i);
    }
    return new StateModel(accountType,
                          name,
                          uri, _uri,
                          enabled,
                          assets,
                          created,
                          owner,
                          portfolioManagerName,
                          borrowable,
                          timelockDuration,
                          integrationAcls,
                          delegateAcls);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeOptional(1, accountType, _data, i);
    if (name == null || name.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeArrayChecked(name, 32, _data, i);
    }
    i += SerDeUtil.writeOptionalVector(1, 4, _uri, _data, i);
    i += SerDeUtil.writeOptional(1, enabled, _data, i);
    if (assets == null || assets.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeVector(4, assets, _data, i);
    }
    i += SerDeUtil.writeOptional(1, created, _data, i);
    i += SerDeUtil.writeOptional(1, owner, _data, i);
    if (portfolioManagerName == null || portfolioManagerName.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeArrayChecked(portfolioManagerName, 32, _data, i);
    }
    if (borrowable == null || borrowable.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeVector(4, borrowable, _data, i);
    }
    i += SerDeUtil.writeOptional(1, timelockDuration, _data, i);
    if (integrationAcls == null || integrationAcls.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeVector(4, integrationAcls, _data, i);
    }
    if (delegateAcls == null || delegateAcls.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeVector(4, delegateAcls, _data, i);
    }
    return i - _offset;
  }

  @Override
  public int l() {
    return (accountType == null ? 1 : (1 + accountType.l()))
         + (name == null || name.length == 0 ? 1 : (1 + SerDeUtil.lenArray(name)))
         + (_uri == null || _uri.length == 0 ? 1 : (1 + _uri.length))
         + (enabled == null ? 1 : (1 + 1))
         + (assets == null || assets.length == 0 ? 1 : (1 + SerDeUtil.lenVector(4, assets)))
         + (created == null ? 1 : (1 + created.l()))
         + (owner == null ? 1 : (1 + 32))
         + (portfolioManagerName == null || portfolioManagerName.length == 0 ? 1 : (1 + SerDeUtil.lenArray(portfolioManagerName)))
         + (borrowable == null || borrowable.length == 0 ? 1 : (1 + SerDeUtil.lenVector(4, borrowable)))
         + (timelockDuration == null || timelockDuration.isEmpty() ? 1 : (1 + 4))
         + (integrationAcls == null || integrationAcls.length == 0 ? 1 : (1 + SerDeUtil.lenVector(4, integrationAcls)))
         + (delegateAcls == null || delegateAcls.length == 0 ? 1 : (1 + SerDeUtil.lenVector(4, delegateAcls)));
  }
}
