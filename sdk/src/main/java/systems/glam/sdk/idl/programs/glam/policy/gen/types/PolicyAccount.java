package systems.glam.sdk.idl.programs.glam.policy.gen.types;

import java.util.function.BiFunction;

import software.sava.core.accounts.PublicKey;
import software.sava.core.programs.Discriminator;
import software.sava.core.rpc.Filter;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.rpc.json.http.response.AccountInfo;

import systems.glam.sdk.idl.programs.glam.protocol.gen.types.TimeUnit;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public record PolicyAccount(PublicKey _address,
                            Discriminator discriminator,
                            PublicKey authority,
                            PublicKey subject,
                            PublicKey mint,
                            PublicKey tokenAccount,
                            long lockedUntil,
                            TimeUnit timeUnit) implements SerDe {

  public static final int BYTES = 145;
  public static final Filter SIZE_FILTER = Filter.createDataSizeFilter(BYTES);

  public static final Discriminator DISCRIMINATOR = toDiscriminator(218, 201, 183, 164, 156, 127, 81, 175);
  public static final Filter DISCRIMINATOR_FILTER = Filter.createMemCompFilter(0, DISCRIMINATOR.data());

  public static final int AUTHORITY_OFFSET = 8;
  public static final int SUBJECT_OFFSET = 40;
  public static final int MINT_OFFSET = 72;
  public static final int TOKEN_ACCOUNT_OFFSET = 104;
  public static final int LOCKED_UNTIL_OFFSET = 136;
  public static final int TIME_UNIT_OFFSET = 144;

  public static Filter createAuthorityFilter(final PublicKey authority) {
    return Filter.createMemCompFilter(AUTHORITY_OFFSET, authority);
  }

  public static Filter createSubjectFilter(final PublicKey subject) {
    return Filter.createMemCompFilter(SUBJECT_OFFSET, subject);
  }

  public static Filter createMintFilter(final PublicKey mint) {
    return Filter.createMemCompFilter(MINT_OFFSET, mint);
  }

  public static Filter createTokenAccountFilter(final PublicKey tokenAccount) {
    return Filter.createMemCompFilter(TOKEN_ACCOUNT_OFFSET, tokenAccount);
  }

  public static Filter createLockedUntilFilter(final long lockedUntil) {
    final byte[] _data = new byte[8];
    putInt64LE(_data, 0, lockedUntil);
    return Filter.createMemCompFilter(LOCKED_UNTIL_OFFSET, _data);
  }

  public static Filter createTimeUnitFilter(final TimeUnit timeUnit) {
    return Filter.createMemCompFilter(TIME_UNIT_OFFSET, timeUnit.write());
  }

  public static PolicyAccount read(final byte[] _data, final int _offset) {
    return read(null, _data, _offset);
  }

  public static PolicyAccount read(final AccountInfo<byte[]> accountInfo) {
    return read(accountInfo.pubKey(), accountInfo.data(), 0);
  }

  public static PolicyAccount read(final PublicKey _address, final byte[] _data) {
    return read(_address, _data, 0);
  }

  public static final BiFunction<PublicKey, byte[], PolicyAccount> FACTORY = PolicyAccount::read;

  public static PolicyAccount read(final PublicKey _address, final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var discriminator = createAnchorDiscriminator(_data, _offset);
    int i = _offset + discriminator.length();
    final var authority = readPubKey(_data, i);
    i += 32;
    final var subject = readPubKey(_data, i);
    i += 32;
    final var mint = readPubKey(_data, i);
    i += 32;
    final var tokenAccount = readPubKey(_data, i);
    i += 32;
    final var lockedUntil = getInt64LE(_data, i);
    i += 8;
    final var timeUnit = TimeUnit.read(_data, i);
    return new PolicyAccount(_address,
                             discriminator,
                             authority,
                             subject,
                             mint,
                             tokenAccount,
                             lockedUntil,
                             timeUnit);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset + discriminator.write(_data, _offset);
    authority.write(_data, i);
    i += 32;
    subject.write(_data, i);
    i += 32;
    mint.write(_data, i);
    i += 32;
    tokenAccount.write(_data, i);
    i += 32;
    putInt64LE(_data, i, lockedUntil);
    i += 8;
    i += timeUnit.write(_data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
