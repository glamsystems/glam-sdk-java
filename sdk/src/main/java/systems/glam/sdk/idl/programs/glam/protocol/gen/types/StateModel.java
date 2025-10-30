package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import java.lang.Boolean;
import java.lang.String;

import java.util.OptionalInt;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;

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
                         DelegateAcl[] delegateAcls) implements Borsh {

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
                          uri, Borsh.getBytes(uri),
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
    if (_data[i] == 0) {
      accountType = null;
      ++i;
    } else {
      ++i;
      accountType = AccountType.read(_data, i);
      i += Borsh.len(accountType);
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
      uri = Borsh.string(_data, i);
      i += (Integer.BYTES + getInt32LE(_data, i));
    }
    final Boolean enabled;
    if (_data[i] == 0) {
      enabled = null;
      ++i;
    } else {
      ++i;
      enabled = _data[i] == 1;
      ++i;
    }
    final PublicKey[] assets;
    if (_data[i] == 0) {
      assets = null;
      ++i;
    } else {
      ++i;
      assets = Borsh.readPublicKeyVector(_data, i);
      i += Borsh.lenVector(assets);
    }
    final CreatedModel created;
    if (_data[i] == 0) {
      created = null;
      ++i;
    } else {
      ++i;
      created = CreatedModel.read(_data, i);
      i += Borsh.len(created);
    }
    final PublicKey owner;
    if (_data[i] == 0) {
      owner = null;
      ++i;
    } else {
      ++i;
      owner = readPubKey(_data, i);
      i += 32;
    }
    final byte[] portfolioManagerName;
    if (_data[i] == 0) {
      portfolioManagerName = null;
      ++i;
    } else {
      ++i;
      portfolioManagerName = new byte[32];
      i += Borsh.readArray(portfolioManagerName, _data, i);
    }
    final PublicKey[] borrowable;
    if (_data[i] == 0) {
      borrowable = null;
      ++i;
    } else {
      ++i;
      borrowable = Borsh.readPublicKeyVector(_data, i);
      i += Borsh.lenVector(borrowable);
    }
    final OptionalInt timelockDuration;
    if (_data[i] == 0) {
      timelockDuration = OptionalInt.empty();
      ++i;
    } else {
      ++i;
      timelockDuration = OptionalInt.of(getInt32LE(_data, i));
      i += 4;
    }
    final IntegrationAcl[] integrationAcls;
    if (_data[i] == 0) {
      integrationAcls = null;
      ++i;
    } else {
      ++i;
      integrationAcls = Borsh.readVector(IntegrationAcl.class, IntegrationAcl::read, _data, i);
      i += Borsh.lenVector(integrationAcls);
    }
    final DelegateAcl[] delegateAcls;
    if (_data[i] == 0) {
      delegateAcls = null;
    } else {
      ++i;
      delegateAcls = Borsh.readVector(DelegateAcl.class, DelegateAcl::read, _data, i);
    }
    return new StateModel(accountType,
                          name,
                          uri, Borsh.getBytes(uri),
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
    i += Borsh.writeOptional(accountType, _data, i);
    if (name == null || name.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeArrayChecked(name, 32, _data, i);
    }
    i += Borsh.writeOptionalVector(_uri, _data, i);
    i += Borsh.writeOptional(enabled, _data, i);
    if (assets == null || assets.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeVector(assets, _data, i);
    }
    i += Borsh.writeOptional(created, _data, i);
    i += Borsh.writeOptional(owner, _data, i);
    if (portfolioManagerName == null || portfolioManagerName.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeArrayChecked(portfolioManagerName, 32, _data, i);
    }
    if (borrowable == null || borrowable.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeVector(borrowable, _data, i);
    }
    i += Borsh.writeOptional(timelockDuration, _data, i);
    if (integrationAcls == null || integrationAcls.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeVector(integrationAcls, _data, i);
    }
    if (delegateAcls == null || delegateAcls.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeVector(delegateAcls, _data, i);
    }
    return i - _offset;
  }

  @Override
  public int l() {
    return (accountType == null ? 1 : (1 + Borsh.len(accountType)))
         + (name == null || name.length == 0 ? 1 : (1 + Borsh.lenArray(name)))
         + (_uri == null || _uri.length == 0 ? 1 : (1 + Borsh.lenVector(_uri)))
         + (enabled == null ? 1 : (1 + 1))
         + (assets == null || assets.length == 0 ? 1 : (1 + Borsh.lenVector(assets)))
         + (created == null ? 1 : (1 + Borsh.len(created)))
         + (owner == null ? 1 : (1 + 32))
         + (portfolioManagerName == null || portfolioManagerName.length == 0 ? 1 : (1 + Borsh.lenArray(portfolioManagerName)))
         + (borrowable == null || borrowable.length == 0 ? 1 : (1 + Borsh.lenVector(borrowable)))
         + (timelockDuration == null || timelockDuration.isEmpty() ? 1 : (1 + 4))
         + (integrationAcls == null || integrationAcls.length == 0 ? 1 : (1 + Borsh.lenVector(integrationAcls)))
         + (delegateAcls == null || delegateAcls.length == 0 ? 1 : (1 + Borsh.lenVector(delegateAcls)));
  }
}
