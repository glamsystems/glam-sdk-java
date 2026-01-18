package systems.glam.sdk.idl.programs.glam.drift.gen;

import java.util.List;
import java.util.OptionalInt;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.idl.clients.drift.gen.types.MarketType;
import software.sava.idl.clients.drift.gen.types.ModifyOrderParams;
import software.sava.idl.clients.drift.gen.types.OrderParams;
import software.sava.idl.clients.drift.gen.types.PositionDirection;
import software.sava.idl.clients.drift.gen.types.SettlePnlMode;
import software.sava.idl.clients.drift.vaults.gen.types.WithdrawUnit;

import systems.glam.sdk.idl.programs.glam.drift.gen.types.DriftProtocolPolicy;
import systems.glam.sdk.idl.programs.glam.drift.gen.types.DriftVaultsPolicy;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.accounts.meta.AccountMeta.createRead;
import static software.sava.core.accounts.meta.AccountMeta.createWritableSigner;
import static software.sava.core.accounts.meta.AccountMeta.createWrite;
import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.getInt32LE;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt32LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public final class ExtDriftProgram {

  public static final Discriminator CANCEL_ORDERS_DISCRIMINATOR = toDiscriminator(238, 225, 95, 158, 227, 103, 8, 194);

  public static List<AccountMeta> cancelOrdersKeys(final SolanaAccounts solanaAccounts,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamVaultKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey integrationAuthorityKey,
                                                   final PublicKey cpiProgramKey,
                                                   final PublicKey glamProtocolProgramKey,
                                                   final PublicKey stateKey,
                                                   final PublicKey userKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(stateKey),
      createWrite(userKey)
    );
  }

  public static Instruction cancelOrders(final AccountMeta invokedExtDriftProgramMeta,
                                         final SolanaAccounts solanaAccounts,
                                         final PublicKey glamStateKey,
                                         final PublicKey glamVaultKey,
                                         final PublicKey glamSignerKey,
                                         final PublicKey integrationAuthorityKey,
                                         final PublicKey cpiProgramKey,
                                         final PublicKey glamProtocolProgramKey,
                                         final PublicKey stateKey,
                                         final PublicKey userKey,
                                         final MarketType marketType,
                                         final OptionalInt marketIndex,
                                         final PositionDirection direction) {
    final var keys = cancelOrdersKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      stateKey,
      userKey
    );
    return cancelOrders(
      invokedExtDriftProgramMeta,
      keys,
      marketType,
      marketIndex,
      direction
    );
  }

  public static Instruction cancelOrders(final AccountMeta invokedExtDriftProgramMeta,
                                         final List<AccountMeta> keys,
                                         final MarketType marketType,
                                         final OptionalInt marketIndex,
                                         final PositionDirection direction) {
    final byte[] _data = new byte[
    8
    + (marketType == null ? 1 : (1 + marketType.l()))
    + (marketIndex == null || marketIndex.isEmpty() ? 1 : 3)
    + (direction == null ? 1 : (1 + direction.l()))
    ];
    int i = CANCEL_ORDERS_DISCRIMINATOR.write(_data, 0);
    i += SerDeUtil.writeOptional(1, marketType, _data, i);
    i += SerDeUtil.writeOptionalshort(1, marketIndex, _data, i);
    SerDeUtil.writeOptional(1, direction, _data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record CancelOrdersIxData(Discriminator discriminator,
                                   MarketType marketType,
                                   OptionalInt marketIndex,
                                   PositionDirection direction) implements SerDe {  

    public static CancelOrdersIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int MARKET_TYPE_OFFSET = 9;

    public static CancelOrdersIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final MarketType marketType;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        marketType = null;
        ++i;
      } else {
        ++i;
        marketType = MarketType.read(_data, i);
        i += marketType.l();
      }
      final OptionalInt marketIndex;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        marketIndex = OptionalInt.empty();
        ++i;
      } else {
        ++i;
        marketIndex = OptionalInt.of(getInt16LE(_data, i));
        i += 2;
      }
      final PositionDirection direction;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        direction = null;
      } else {
        ++i;
        direction = PositionDirection.read(_data, i);
      }
      return new CancelOrdersIxData(discriminator, marketType, marketIndex, direction);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeOptional(1, marketType, _data, i);
      i += SerDeUtil.writeOptionalshort(1, marketIndex, _data, i);
      i += SerDeUtil.writeOptional(1, direction, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + (marketType == null ? 1 : (1 + marketType.l())) + (marketIndex == null || marketIndex.isEmpty() ? 1 : (1 + 2)) + (direction == null ? 1 : (1 + direction.l()));
    }
  }

  public static final Discriminator CANCEL_ORDERS_BY_IDS_DISCRIMINATOR = toDiscriminator(134, 19, 144, 165, 94, 240, 210, 94);

  public static List<AccountMeta> cancelOrdersByIdsKeys(final SolanaAccounts solanaAccounts,
                                                        final PublicKey glamStateKey,
                                                        final PublicKey glamVaultKey,
                                                        final PublicKey glamSignerKey,
                                                        final PublicKey integrationAuthorityKey,
                                                        final PublicKey cpiProgramKey,
                                                        final PublicKey glamProtocolProgramKey,
                                                        final PublicKey stateKey,
                                                        final PublicKey userKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(stateKey),
      createWrite(userKey)
    );
  }

  public static Instruction cancelOrdersByIds(final AccountMeta invokedExtDriftProgramMeta,
                                              final SolanaAccounts solanaAccounts,
                                              final PublicKey glamStateKey,
                                              final PublicKey glamVaultKey,
                                              final PublicKey glamSignerKey,
                                              final PublicKey integrationAuthorityKey,
                                              final PublicKey cpiProgramKey,
                                              final PublicKey glamProtocolProgramKey,
                                              final PublicKey stateKey,
                                              final PublicKey userKey,
                                              final int[] orderIds) {
    final var keys = cancelOrdersByIdsKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      stateKey,
      userKey
    );
    return cancelOrdersByIds(invokedExtDriftProgramMeta, keys, orderIds);
  }

  public static Instruction cancelOrdersByIds(final AccountMeta invokedExtDriftProgramMeta,
                                              final List<AccountMeta> keys,
                                              final int[] orderIds) {
    final byte[] _data = new byte[8 + SerDeUtil.lenVector(4, orderIds)];
    int i = CANCEL_ORDERS_BY_IDS_DISCRIMINATOR.write(_data, 0);
    SerDeUtil.writeVector(4, orderIds, _data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record CancelOrdersByIdsIxData(Discriminator discriminator, int[] orderIds) implements SerDe {  

    public static CancelOrdersByIdsIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int ORDER_IDS_OFFSET = 8;

    public static CancelOrdersByIdsIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var orderIds = SerDeUtil.readintVector(4, _data, i);
      return new CancelOrdersByIdsIxData(discriminator, orderIds);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeVector(4, orderIds, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenVector(4, orderIds);
    }
  }

  public static final Discriminator DELETE_USER_DISCRIMINATOR = toDiscriminator(186, 85, 17, 249, 219, 231, 98, 251);

  public static List<AccountMeta> deleteUserKeys(final SolanaAccounts solanaAccounts,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey glamSignerKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PublicKey cpiProgramKey,
                                                 final PublicKey glamProtocolProgramKey,
                                                 final PublicKey userKey,
                                                 final PublicKey userStatsKey,
                                                 final PublicKey stateKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userKey),
      createWrite(userStatsKey),
      createWrite(stateKey)
    );
  }

  public static Instruction deleteUser(final AccountMeta invokedExtDriftProgramMeta,
                                       final SolanaAccounts solanaAccounts,
                                       final PublicKey glamStateKey,
                                       final PublicKey glamVaultKey,
                                       final PublicKey glamSignerKey,
                                       final PublicKey integrationAuthorityKey,
                                       final PublicKey cpiProgramKey,
                                       final PublicKey glamProtocolProgramKey,
                                       final PublicKey userKey,
                                       final PublicKey userStatsKey,
                                       final PublicKey stateKey) {
    final var keys = deleteUserKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userKey,
      userStatsKey,
      stateKey
    );
    return deleteUser(invokedExtDriftProgramMeta, keys);
  }

  public static Instruction deleteUser(final AccountMeta invokedExtDriftProgramMeta,
                                       final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, DELETE_USER_DISCRIMINATOR);
  }

  public static final Discriminator DEPOSIT_DISCRIMINATOR = toDiscriminator(242, 35, 198, 137, 82, 225, 242, 182);

  public static List<AccountMeta> depositKeys(final SolanaAccounts solanaAccounts,
                                              final PublicKey glamStateKey,
                                              final PublicKey glamVaultKey,
                                              final PublicKey glamSignerKey,
                                              final PublicKey integrationAuthorityKey,
                                              final PublicKey cpiProgramKey,
                                              final PublicKey glamProtocolProgramKey,
                                              final PublicKey stateKey,
                                              final PublicKey userKey,
                                              final PublicKey userStatsKey,
                                              final PublicKey spotMarketVaultKey,
                                              final PublicKey userTokenAccountKey,
                                              final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(stateKey),
      createWrite(userKey),
      createWrite(userStatsKey),
      createWrite(spotMarketVaultKey),
      createWrite(userTokenAccountKey),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction deposit(final AccountMeta invokedExtDriftProgramMeta,
                                    final SolanaAccounts solanaAccounts,
                                    final PublicKey glamStateKey,
                                    final PublicKey glamVaultKey,
                                    final PublicKey glamSignerKey,
                                    final PublicKey integrationAuthorityKey,
                                    final PublicKey cpiProgramKey,
                                    final PublicKey glamProtocolProgramKey,
                                    final PublicKey stateKey,
                                    final PublicKey userKey,
                                    final PublicKey userStatsKey,
                                    final PublicKey spotMarketVaultKey,
                                    final PublicKey userTokenAccountKey,
                                    final PublicKey tokenProgramKey,
                                    final int marketIndex,
                                    final long amount,
                                    final boolean reduceOnly) {
    final var keys = depositKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      stateKey,
      userKey,
      userStatsKey,
      spotMarketVaultKey,
      userTokenAccountKey,
      tokenProgramKey
    );
    return deposit(
      invokedExtDriftProgramMeta,
      keys,
      marketIndex,
      amount,
      reduceOnly
    );
  }

  public static Instruction deposit(final AccountMeta invokedExtDriftProgramMeta,
                                    final List<AccountMeta> keys,
                                    final int marketIndex,
                                    final long amount,
                                    final boolean reduceOnly) {
    final byte[] _data = new byte[19];
    int i = DEPOSIT_DISCRIMINATOR.write(_data, 0);
    putInt16LE(_data, i, marketIndex);
    i += 2;
    putInt64LE(_data, i, amount);
    i += 8;
    _data[i] = (byte) (reduceOnly ? 1 : 0);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record DepositIxData(Discriminator discriminator,
                              int marketIndex,
                              long amount,
                              boolean reduceOnly) implements SerDe {  

    public static DepositIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 19;

    public static final int MARKET_INDEX_OFFSET = 8;
    public static final int AMOUNT_OFFSET = 10;
    public static final int REDUCE_ONLY_OFFSET = 18;

    public static DepositIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var marketIndex = getInt16LE(_data, i);
      i += 2;
      final var amount = getInt64LE(_data, i);
      i += 8;
      final var reduceOnly = _data[i] == 1;
      return new DepositIxData(discriminator, marketIndex, amount, reduceOnly);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt16LE(_data, i, marketIndex);
      i += 2;
      putInt64LE(_data, i, amount);
      i += 8;
      _data[i] = (byte) (reduceOnly ? 1 : 0);
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator INITIALIZE_USER_DISCRIMINATOR = toDiscriminator(111, 17, 185, 250, 60, 122, 38, 254);

  public static List<AccountMeta> initializeUserKeys(final SolanaAccounts solanaAccounts,
                                                     final PublicKey glamStateKey,
                                                     final PublicKey glamVaultKey,
                                                     final PublicKey glamSignerKey,
                                                     final PublicKey integrationAuthorityKey,
                                                     final PublicKey cpiProgramKey,
                                                     final PublicKey glamProtocolProgramKey,
                                                     final PublicKey userKey,
                                                     final PublicKey userStatsKey,
                                                     final PublicKey stateKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createWrite(userKey),
      createWrite(userStatsKey),
      createWrite(stateKey),
      createRead(solanaAccounts.rentSysVar()),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction initializeUser(final AccountMeta invokedExtDriftProgramMeta,
                                           final SolanaAccounts solanaAccounts,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamVaultKey,
                                           final PublicKey glamSignerKey,
                                           final PublicKey integrationAuthorityKey,
                                           final PublicKey cpiProgramKey,
                                           final PublicKey glamProtocolProgramKey,
                                           final PublicKey userKey,
                                           final PublicKey userStatsKey,
                                           final PublicKey stateKey,
                                           final int subAccountId,
                                           final byte[] name) {
    final var keys = initializeUserKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userKey,
      userStatsKey,
      stateKey
    );
    return initializeUser(invokedExtDriftProgramMeta, keys, subAccountId, name);
  }

  public static Instruction initializeUser(final AccountMeta invokedExtDriftProgramMeta,
                                           final List<AccountMeta> keys,
                                           final int subAccountId,
                                           final byte[] name) {
    final byte[] _data = new byte[10 + SerDeUtil.lenArray(name)];
    int i = INITIALIZE_USER_DISCRIMINATOR.write(_data, 0);
    putInt16LE(_data, i, subAccountId);
    i += 2;
    SerDeUtil.writeArrayChecked(name, 32, _data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record InitializeUserIxData(Discriminator discriminator, int subAccountId, byte[] name) implements SerDe {  

    public static InitializeUserIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 42;
    public static final int NAME_LEN = 32;

    public static final int SUB_ACCOUNT_ID_OFFSET = 8;
    public static final int NAME_OFFSET = 10;

    public static InitializeUserIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var subAccountId = getInt16LE(_data, i);
      i += 2;
      final var name = new byte[32];
      SerDeUtil.readArray(name, _data, i);
      return new InitializeUserIxData(discriminator, subAccountId, name);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt16LE(_data, i, subAccountId);
      i += 2;
      i += SerDeUtil.writeArrayChecked(name, 32, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator INITIALIZE_USER_STATS_DISCRIMINATOR = toDiscriminator(254, 243, 72, 98, 251, 130, 168, 213);

  public static List<AccountMeta> initializeUserStatsKeys(final SolanaAccounts solanaAccounts,
                                                          final PublicKey glamStateKey,
                                                          final PublicKey glamVaultKey,
                                                          final PublicKey glamSignerKey,
                                                          final PublicKey integrationAuthorityKey,
                                                          final PublicKey cpiProgramKey,
                                                          final PublicKey glamProtocolProgramKey,
                                                          final PublicKey userStatsKey,
                                                          final PublicKey stateKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createWrite(userStatsKey),
      createWrite(stateKey),
      createRead(solanaAccounts.rentSysVar()),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction initializeUserStats(final AccountMeta invokedExtDriftProgramMeta,
                                                final SolanaAccounts solanaAccounts,
                                                final PublicKey glamStateKey,
                                                final PublicKey glamVaultKey,
                                                final PublicKey glamSignerKey,
                                                final PublicKey integrationAuthorityKey,
                                                final PublicKey cpiProgramKey,
                                                final PublicKey glamProtocolProgramKey,
                                                final PublicKey userStatsKey,
                                                final PublicKey stateKey) {
    final var keys = initializeUserStatsKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userStatsKey,
      stateKey
    );
    return initializeUserStats(invokedExtDriftProgramMeta, keys);
  }

  public static Instruction initializeUserStats(final AccountMeta invokedExtDriftProgramMeta,
                                                final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, INITIALIZE_USER_STATS_DISCRIMINATOR);
  }

  public static final Discriminator MODIFY_ORDER_DISCRIMINATOR = toDiscriminator(47, 124, 117, 255, 201, 197, 130, 94);

  public static List<AccountMeta> modifyOrderKeys(final SolanaAccounts solanaAccounts,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamVaultKey,
                                                  final PublicKey glamSignerKey,
                                                  final PublicKey integrationAuthorityKey,
                                                  final PublicKey cpiProgramKey,
                                                  final PublicKey glamProtocolProgramKey,
                                                  final PublicKey stateKey,
                                                  final PublicKey userKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(stateKey),
      createWrite(userKey)
    );
  }

  public static Instruction modifyOrder(final AccountMeta invokedExtDriftProgramMeta,
                                        final SolanaAccounts solanaAccounts,
                                        final PublicKey glamStateKey,
                                        final PublicKey glamVaultKey,
                                        final PublicKey glamSignerKey,
                                        final PublicKey integrationAuthorityKey,
                                        final PublicKey cpiProgramKey,
                                        final PublicKey glamProtocolProgramKey,
                                        final PublicKey stateKey,
                                        final PublicKey userKey,
                                        final OptionalInt orderId,
                                        final ModifyOrderParams modifyOrderParams) {
    final var keys = modifyOrderKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      stateKey,
      userKey
    );
    return modifyOrder(invokedExtDriftProgramMeta, keys, orderId, modifyOrderParams);
  }

  public static Instruction modifyOrder(final AccountMeta invokedExtDriftProgramMeta,
                                        final List<AccountMeta> keys,
                                        final OptionalInt orderId,
                                        final ModifyOrderParams modifyOrderParams) {
    final byte[] _data = new byte[
    8
    + (orderId == null || orderId.isEmpty() ? 1 : 5) + modifyOrderParams.l()
    ];
    int i = MODIFY_ORDER_DISCRIMINATOR.write(_data, 0);
    i += SerDeUtil.writeOptional(1, orderId, _data, i);
    modifyOrderParams.write(_data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record ModifyOrderIxData(Discriminator discriminator, OptionalInt orderId, ModifyOrderParams modifyOrderParams) implements SerDe {  

    public static ModifyOrderIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int ORDER_ID_OFFSET = 9;

    public static ModifyOrderIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final OptionalInt orderId;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        orderId = OptionalInt.empty();
        ++i;
      } else {
        ++i;
        orderId = OptionalInt.of(getInt32LE(_data, i));
        i += 4;
      }
      final var modifyOrderParams = ModifyOrderParams.read(_data, i);
      return new ModifyOrderIxData(discriminator, orderId, modifyOrderParams);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeOptional(1, orderId, _data, i);
      i += modifyOrderParams.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + (orderId == null || orderId.isEmpty() ? 1 : (1 + 4)) + modifyOrderParams.l();
    }
  }

  public static final Discriminator PLACE_ORDERS_DISCRIMINATOR = toDiscriminator(60, 63, 50, 123, 12, 197, 60, 190);

  public static List<AccountMeta> placeOrdersKeys(final SolanaAccounts solanaAccounts,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamVaultKey,
                                                  final PublicKey glamSignerKey,
                                                  final PublicKey integrationAuthorityKey,
                                                  final PublicKey cpiProgramKey,
                                                  final PublicKey glamProtocolProgramKey,
                                                  final PublicKey stateKey,
                                                  final PublicKey userKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(stateKey),
      createWrite(userKey)
    );
  }

  public static Instruction placeOrders(final AccountMeta invokedExtDriftProgramMeta,
                                        final SolanaAccounts solanaAccounts,
                                        final PublicKey glamStateKey,
                                        final PublicKey glamVaultKey,
                                        final PublicKey glamSignerKey,
                                        final PublicKey integrationAuthorityKey,
                                        final PublicKey cpiProgramKey,
                                        final PublicKey glamProtocolProgramKey,
                                        final PublicKey stateKey,
                                        final PublicKey userKey,
                                        final OrderParams[] params) {
    final var keys = placeOrdersKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      stateKey,
      userKey
    );
    return placeOrders(invokedExtDriftProgramMeta, keys, params);
  }

  public static Instruction placeOrders(final AccountMeta invokedExtDriftProgramMeta,
                                        final List<AccountMeta> keys,
                                        final OrderParams[] params) {
    final byte[] _data = new byte[8 + SerDeUtil.lenVector(4, params)];
    int i = PLACE_ORDERS_DISCRIMINATOR.write(_data, 0);
    SerDeUtil.writeVector(4, params, _data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record PlaceOrdersIxData(Discriminator discriminator, OrderParams[] params) implements SerDe {  

    public static PlaceOrdersIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int PARAMS_OFFSET = 8;

    public static PlaceOrdersIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = SerDeUtil.readVector(4, OrderParams.class, OrderParams::read, _data, i);
      return new PlaceOrdersIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeVector(4, params, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenVector(4, params);
    }
  }

  public static final Discriminator RECLAIM_RENT_DISCRIMINATOR = toDiscriminator(218, 200, 19, 197, 227, 89, 192, 22);

  public static List<AccountMeta> reclaimRentKeys(final SolanaAccounts solanaAccounts,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamVaultKey,
                                                  final PublicKey glamSignerKey,
                                                  final PublicKey integrationAuthorityKey,
                                                  final PublicKey cpiProgramKey,
                                                  final PublicKey glamProtocolProgramKey,
                                                  final PublicKey userKey,
                                                  final PublicKey userStatsKey,
                                                  final PublicKey stateKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userKey),
      createWrite(userStatsKey),
      createRead(stateKey),
      createRead(solanaAccounts.rentSysVar())
    );
  }

  public static Instruction reclaimRent(final AccountMeta invokedExtDriftProgramMeta,
                                        final SolanaAccounts solanaAccounts,
                                        final PublicKey glamStateKey,
                                        final PublicKey glamVaultKey,
                                        final PublicKey glamSignerKey,
                                        final PublicKey integrationAuthorityKey,
                                        final PublicKey cpiProgramKey,
                                        final PublicKey glamProtocolProgramKey,
                                        final PublicKey userKey,
                                        final PublicKey userStatsKey,
                                        final PublicKey stateKey) {
    final var keys = reclaimRentKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userKey,
      userStatsKey,
      stateKey
    );
    return reclaimRent(invokedExtDriftProgramMeta, keys);
  }

  public static Instruction reclaimRent(final AccountMeta invokedExtDriftProgramMeta,
                                        final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, RECLAIM_RENT_DISCRIMINATOR);
  }

  public static final Discriminator SET_DRIFT_PROTOCOL_POLICY_DISCRIMINATOR = toDiscriminator(200, 22, 110, 2, 58, 22, 76, 162);

  public static List<AccountMeta> setDriftProtocolPolicyKeys(final PublicKey glamStateKey,
                                                             final PublicKey glamSignerKey,
                                                             final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(glamProtocolProgramKey)
    );
  }

  public static Instruction setDriftProtocolPolicy(final AccountMeta invokedExtDriftProgramMeta,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey glamProtocolProgramKey,
                                                   final DriftProtocolPolicy policy) {
    final var keys = setDriftProtocolPolicyKeys(
      glamStateKey,
      glamSignerKey,
      glamProtocolProgramKey
    );
    return setDriftProtocolPolicy(invokedExtDriftProgramMeta, keys, policy);
  }

  public static Instruction setDriftProtocolPolicy(final AccountMeta invokedExtDriftProgramMeta,
                                                   final List<AccountMeta> keys,
                                                   final DriftProtocolPolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_DRIFT_PROTOCOL_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record SetDriftProtocolPolicyIxData(Discriminator discriminator, DriftProtocolPolicy policy) implements SerDe {  

    public static SetDriftProtocolPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int POLICY_OFFSET = 8;

    public static SetDriftProtocolPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = DriftProtocolPolicy.read(_data, i);
      return new SetDriftProtocolPolicyIxData(discriminator, policy);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += policy.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + policy.l();
    }
  }

  public static final Discriminator SET_DRIFT_VAULTS_POLICY_DISCRIMINATOR = toDiscriminator(168, 134, 53, 33, 18, 88, 142, 223);

  public static List<AccountMeta> setDriftVaultsPolicyKeys(final PublicKey glamStateKey,
                                                           final PublicKey glamSignerKey,
                                                           final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(glamProtocolProgramKey)
    );
  }

  public static Instruction setDriftVaultsPolicy(final AccountMeta invokedExtDriftProgramMeta,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamSignerKey,
                                                 final PublicKey glamProtocolProgramKey,
                                                 final DriftVaultsPolicy policy) {
    final var keys = setDriftVaultsPolicyKeys(
      glamStateKey,
      glamSignerKey,
      glamProtocolProgramKey
    );
    return setDriftVaultsPolicy(invokedExtDriftProgramMeta, keys, policy);
  }

  public static Instruction setDriftVaultsPolicy(final AccountMeta invokedExtDriftProgramMeta,
                                                 final List<AccountMeta> keys,
                                                 final DriftVaultsPolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_DRIFT_VAULTS_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record SetDriftVaultsPolicyIxData(Discriminator discriminator, DriftVaultsPolicy policy) implements SerDe {  

    public static SetDriftVaultsPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int POLICY_OFFSET = 8;

    public static SetDriftVaultsPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = DriftVaultsPolicy.read(_data, i);
      return new SetDriftVaultsPolicyIxData(discriminator, policy);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += policy.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + policy.l();
    }
  }

  public static final Discriminator SETTLE_MULTIPLE_PNLS_DISCRIMINATOR = toDiscriminator(127, 66, 117, 57, 40, 50, 152, 127);

  public static List<AccountMeta> settleMultiplePnlsKeys(final SolanaAccounts solanaAccounts,
                                                         final PublicKey glamStateKey,
                                                         final PublicKey glamVaultKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey integrationAuthorityKey,
                                                         final PublicKey cpiProgramKey,
                                                         final PublicKey glamProtocolProgramKey,
                                                         final PublicKey stateKey,
                                                         final PublicKey userKey,
                                                         final PublicKey spotMarketVaultKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(stateKey),
      createWrite(userKey),
      createRead(spotMarketVaultKey)
    );
  }

  public static Instruction settleMultiplePnls(final AccountMeta invokedExtDriftProgramMeta,
                                               final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PublicKey stateKey,
                                               final PublicKey userKey,
                                               final PublicKey spotMarketVaultKey,
                                               final short[] marketIndexes,
                                               final SettlePnlMode mode) {
    final var keys = settleMultiplePnlsKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      stateKey,
      userKey,
      spotMarketVaultKey
    );
    return settleMultiplePnls(invokedExtDriftProgramMeta, keys, marketIndexes, mode);
  }

  public static Instruction settleMultiplePnls(final AccountMeta invokedExtDriftProgramMeta,
                                               final List<AccountMeta> keys,
                                               final short[] marketIndexes,
                                               final SettlePnlMode mode) {
    final byte[] _data = new byte[8 + SerDeUtil.lenVector(4, marketIndexes) + mode.l()];
    int i = SETTLE_MULTIPLE_PNLS_DISCRIMINATOR.write(_data, 0);
    i += SerDeUtil.writeVector(4, marketIndexes, _data, i);
    mode.write(_data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record SettleMultiplePnlsIxData(Discriminator discriminator, short[] marketIndexes, SettlePnlMode mode) implements SerDe {  

    public static SettleMultiplePnlsIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int MARKET_INDEXES_OFFSET = 8;

    public static SettleMultiplePnlsIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var marketIndexes = SerDeUtil.readshortVector(4, _data, i);
      i += SerDeUtil.lenVector(4, marketIndexes);
      final var mode = SettlePnlMode.read(_data, i);
      return new SettleMultiplePnlsIxData(discriminator, marketIndexes, mode);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeVector(4, marketIndexes, _data, i);
      i += mode.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenVector(4, marketIndexes) + mode.l();
    }
  }

  public static final Discriminator SETTLE_PNL_DISCRIMINATOR = toDiscriminator(43, 61, 234, 45, 15, 95, 152, 153);

  public static List<AccountMeta> settlePnlKeys(final SolanaAccounts solanaAccounts,
                                                final PublicKey glamStateKey,
                                                final PublicKey glamVaultKey,
                                                final PublicKey glamSignerKey,
                                                final PublicKey integrationAuthorityKey,
                                                final PublicKey cpiProgramKey,
                                                final PublicKey glamProtocolProgramKey,
                                                final PublicKey stateKey,
                                                final PublicKey userKey,
                                                final PublicKey spotMarketVaultKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(stateKey),
      createWrite(userKey),
      createRead(spotMarketVaultKey)
    );
  }

  public static Instruction settlePnl(final AccountMeta invokedExtDriftProgramMeta,
                                      final SolanaAccounts solanaAccounts,
                                      final PublicKey glamStateKey,
                                      final PublicKey glamVaultKey,
                                      final PublicKey glamSignerKey,
                                      final PublicKey integrationAuthorityKey,
                                      final PublicKey cpiProgramKey,
                                      final PublicKey glamProtocolProgramKey,
                                      final PublicKey stateKey,
                                      final PublicKey userKey,
                                      final PublicKey spotMarketVaultKey,
                                      final int marketIndex) {
    final var keys = settlePnlKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      stateKey,
      userKey,
      spotMarketVaultKey
    );
    return settlePnl(invokedExtDriftProgramMeta, keys, marketIndex);
  }

  public static Instruction settlePnl(final AccountMeta invokedExtDriftProgramMeta,
                                      final List<AccountMeta> keys,
                                      final int marketIndex) {
    final byte[] _data = new byte[10];
    int i = SETTLE_PNL_DISCRIMINATOR.write(_data, 0);
    putInt16LE(_data, i, marketIndex);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record SettlePnlIxData(Discriminator discriminator, int marketIndex) implements SerDe {  

    public static SettlePnlIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 10;

    public static final int MARKET_INDEX_OFFSET = 8;

    public static SettlePnlIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var marketIndex = getInt16LE(_data, i);
      return new SettlePnlIxData(discriminator, marketIndex);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt16LE(_data, i, marketIndex);
      i += 2;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator UPDATE_USER_CUSTOM_MARGIN_RATIO_DISCRIMINATOR = toDiscriminator(21, 221, 140, 187, 32, 129, 11, 123);

  public static List<AccountMeta> updateUserCustomMarginRatioKeys(final SolanaAccounts solanaAccounts,
                                                                  final PublicKey glamStateKey,
                                                                  final PublicKey glamVaultKey,
                                                                  final PublicKey glamSignerKey,
                                                                  final PublicKey integrationAuthorityKey,
                                                                  final PublicKey cpiProgramKey,
                                                                  final PublicKey glamProtocolProgramKey,
                                                                  final PublicKey userKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userKey)
    );
  }

  public static Instruction updateUserCustomMarginRatio(final AccountMeta invokedExtDriftProgramMeta,
                                                        final SolanaAccounts solanaAccounts,
                                                        final PublicKey glamStateKey,
                                                        final PublicKey glamVaultKey,
                                                        final PublicKey glamSignerKey,
                                                        final PublicKey integrationAuthorityKey,
                                                        final PublicKey cpiProgramKey,
                                                        final PublicKey glamProtocolProgramKey,
                                                        final PublicKey userKey,
                                                        final int subAccountId,
                                                        final int marginRatio) {
    final var keys = updateUserCustomMarginRatioKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userKey
    );
    return updateUserCustomMarginRatio(invokedExtDriftProgramMeta, keys, subAccountId, marginRatio);
  }

  public static Instruction updateUserCustomMarginRatio(final AccountMeta invokedExtDriftProgramMeta,
                                                        final List<AccountMeta> keys,
                                                        final int subAccountId,
                                                        final int marginRatio) {
    final byte[] _data = new byte[14];
    int i = UPDATE_USER_CUSTOM_MARGIN_RATIO_DISCRIMINATOR.write(_data, 0);
    putInt16LE(_data, i, subAccountId);
    i += 2;
    putInt32LE(_data, i, marginRatio);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record UpdateUserCustomMarginRatioIxData(Discriminator discriminator, int subAccountId, int marginRatio) implements SerDe {  

    public static UpdateUserCustomMarginRatioIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 14;

    public static final int SUB_ACCOUNT_ID_OFFSET = 8;
    public static final int MARGIN_RATIO_OFFSET = 10;

    public static UpdateUserCustomMarginRatioIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var subAccountId = getInt16LE(_data, i);
      i += 2;
      final var marginRatio = getInt32LE(_data, i);
      return new UpdateUserCustomMarginRatioIxData(discriminator, subAccountId, marginRatio);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt16LE(_data, i, subAccountId);
      i += 2;
      putInt32LE(_data, i, marginRatio);
      i += 4;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator UPDATE_USER_DELEGATE_DISCRIMINATOR = toDiscriminator(139, 205, 141, 141, 113, 36, 94, 187);

  public static List<AccountMeta> updateUserDelegateKeys(final SolanaAccounts solanaAccounts,
                                                         final PublicKey glamStateKey,
                                                         final PublicKey glamVaultKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey integrationAuthorityKey,
                                                         final PublicKey cpiProgramKey,
                                                         final PublicKey glamProtocolProgramKey,
                                                         final PublicKey userKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userKey)
    );
  }

  public static Instruction updateUserDelegate(final AccountMeta invokedExtDriftProgramMeta,
                                               final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PublicKey userKey,
                                               final int subAccountId,
                                               final PublicKey delegate) {
    final var keys = updateUserDelegateKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userKey
    );
    return updateUserDelegate(invokedExtDriftProgramMeta, keys, subAccountId, delegate);
  }

  public static Instruction updateUserDelegate(final AccountMeta invokedExtDriftProgramMeta,
                                               final List<AccountMeta> keys,
                                               final int subAccountId,
                                               final PublicKey delegate) {
    final byte[] _data = new byte[42];
    int i = UPDATE_USER_DELEGATE_DISCRIMINATOR.write(_data, 0);
    putInt16LE(_data, i, subAccountId);
    i += 2;
    delegate.write(_data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record UpdateUserDelegateIxData(Discriminator discriminator, int subAccountId, PublicKey delegate) implements SerDe {  

    public static UpdateUserDelegateIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 42;

    public static final int SUB_ACCOUNT_ID_OFFSET = 8;
    public static final int DELEGATE_OFFSET = 10;

    public static UpdateUserDelegateIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var subAccountId = getInt16LE(_data, i);
      i += 2;
      final var delegate = readPubKey(_data, i);
      return new UpdateUserDelegateIxData(discriminator, subAccountId, delegate);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt16LE(_data, i, subAccountId);
      i += 2;
      delegate.write(_data, i);
      i += 32;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator UPDATE_USER_MARGIN_TRADING_ENABLED_DISCRIMINATOR = toDiscriminator(194, 92, 204, 223, 246, 188, 31, 203);

  public static List<AccountMeta> updateUserMarginTradingEnabledKeys(final SolanaAccounts solanaAccounts,
                                                                     final PublicKey glamStateKey,
                                                                     final PublicKey glamVaultKey,
                                                                     final PublicKey glamSignerKey,
                                                                     final PublicKey integrationAuthorityKey,
                                                                     final PublicKey cpiProgramKey,
                                                                     final PublicKey glamProtocolProgramKey,
                                                                     final PublicKey userKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userKey)
    );
  }

  public static Instruction updateUserMarginTradingEnabled(final AccountMeta invokedExtDriftProgramMeta,
                                                           final SolanaAccounts solanaAccounts,
                                                           final PublicKey glamStateKey,
                                                           final PublicKey glamVaultKey,
                                                           final PublicKey glamSignerKey,
                                                           final PublicKey integrationAuthorityKey,
                                                           final PublicKey cpiProgramKey,
                                                           final PublicKey glamProtocolProgramKey,
                                                           final PublicKey userKey,
                                                           final int subAccountId,
                                                           final boolean marginTradingEnabled) {
    final var keys = updateUserMarginTradingEnabledKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userKey
    );
    return updateUserMarginTradingEnabled(invokedExtDriftProgramMeta, keys, subAccountId, marginTradingEnabled);
  }

  public static Instruction updateUserMarginTradingEnabled(final AccountMeta invokedExtDriftProgramMeta,
                                                           final List<AccountMeta> keys,
                                                           final int subAccountId,
                                                           final boolean marginTradingEnabled) {
    final byte[] _data = new byte[11];
    int i = UPDATE_USER_MARGIN_TRADING_ENABLED_DISCRIMINATOR.write(_data, 0);
    putInt16LE(_data, i, subAccountId);
    i += 2;
    _data[i] = (byte) (marginTradingEnabled ? 1 : 0);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record UpdateUserMarginTradingEnabledIxData(Discriminator discriminator, int subAccountId, boolean marginTradingEnabled) implements SerDe {  

    public static UpdateUserMarginTradingEnabledIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 11;

    public static final int SUB_ACCOUNT_ID_OFFSET = 8;
    public static final int MARGIN_TRADING_ENABLED_OFFSET = 10;

    public static UpdateUserMarginTradingEnabledIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var subAccountId = getInt16LE(_data, i);
      i += 2;
      final var marginTradingEnabled = _data[i] == 1;
      return new UpdateUserMarginTradingEnabledIxData(discriminator, subAccountId, marginTradingEnabled);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt16LE(_data, i, subAccountId);
      i += 2;
      _data[i] = (byte) (marginTradingEnabled ? 1 : 0);
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator UPDATE_USER_POOL_ID_DISCRIMINATOR = toDiscriminator(219, 86, 73, 106, 56, 218, 128, 109);

  public static List<AccountMeta> updateUserPoolIdKeys(final SolanaAccounts solanaAccounts,
                                                       final PublicKey glamStateKey,
                                                       final PublicKey glamVaultKey,
                                                       final PublicKey glamSignerKey,
                                                       final PublicKey integrationAuthorityKey,
                                                       final PublicKey cpiProgramKey,
                                                       final PublicKey glamProtocolProgramKey,
                                                       final PublicKey userKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userKey)
    );
  }

  public static Instruction updateUserPoolId(final AccountMeta invokedExtDriftProgramMeta,
                                             final SolanaAccounts solanaAccounts,
                                             final PublicKey glamStateKey,
                                             final PublicKey glamVaultKey,
                                             final PublicKey glamSignerKey,
                                             final PublicKey integrationAuthorityKey,
                                             final PublicKey cpiProgramKey,
                                             final PublicKey glamProtocolProgramKey,
                                             final PublicKey userKey,
                                             final int subAccountId,
                                             final int poolId) {
    final var keys = updateUserPoolIdKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userKey
    );
    return updateUserPoolId(invokedExtDriftProgramMeta, keys, subAccountId, poolId);
  }

  public static Instruction updateUserPoolId(final AccountMeta invokedExtDriftProgramMeta,
                                             final List<AccountMeta> keys,
                                             final int subAccountId,
                                             final int poolId) {
    final byte[] _data = new byte[11];
    int i = UPDATE_USER_POOL_ID_DISCRIMINATOR.write(_data, 0);
    putInt16LE(_data, i, subAccountId);
    i += 2;
    _data[i] = (byte) poolId;

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record UpdateUserPoolIdIxData(Discriminator discriminator, int subAccountId, int poolId) implements SerDe {  

    public static UpdateUserPoolIdIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 11;

    public static final int SUB_ACCOUNT_ID_OFFSET = 8;
    public static final int POOL_ID_OFFSET = 10;

    public static UpdateUserPoolIdIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var subAccountId = getInt16LE(_data, i);
      i += 2;
      final var poolId = _data[i] & 0xFF;
      return new UpdateUserPoolIdIxData(discriminator, subAccountId, poolId);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt16LE(_data, i, subAccountId);
      i += 2;
      _data[i] = (byte) poolId;
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator VAULTS_CANCEL_REQUEST_WITHDRAW_DISCRIMINATOR = toDiscriminator(188, 93, 159, 202, 157, 47, 143, 219);

  public static List<AccountMeta> vaultsCancelRequestWithdrawKeys(final SolanaAccounts solanaAccounts,
                                                                  final PublicKey glamStateKey,
                                                                  final PublicKey glamVaultKey,
                                                                  final PublicKey glamSignerKey,
                                                                  final PublicKey integrationAuthorityKey,
                                                                  final PublicKey cpiProgramKey,
                                                                  final PublicKey glamProtocolProgramKey,
                                                                  final PublicKey vaultKey,
                                                                  final PublicKey vaultDepositorKey,
                                                                  final PublicKey driftUserStatsKey,
                                                                  final PublicKey driftUserKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(vaultKey),
      createWrite(vaultDepositorKey),
      createRead(driftUserStatsKey),
      createRead(driftUserKey)
    );
  }

  public static Instruction vaultsCancelRequestWithdraw(final AccountMeta invokedExtDriftProgramMeta,
                                                        final SolanaAccounts solanaAccounts,
                                                        final PublicKey glamStateKey,
                                                        final PublicKey glamVaultKey,
                                                        final PublicKey glamSignerKey,
                                                        final PublicKey integrationAuthorityKey,
                                                        final PublicKey cpiProgramKey,
                                                        final PublicKey glamProtocolProgramKey,
                                                        final PublicKey vaultKey,
                                                        final PublicKey vaultDepositorKey,
                                                        final PublicKey driftUserStatsKey,
                                                        final PublicKey driftUserKey) {
    final var keys = vaultsCancelRequestWithdrawKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      vaultKey,
      vaultDepositorKey,
      driftUserStatsKey,
      driftUserKey
    );
    return vaultsCancelRequestWithdraw(invokedExtDriftProgramMeta, keys);
  }

  public static Instruction vaultsCancelRequestWithdraw(final AccountMeta invokedExtDriftProgramMeta,
                                                        final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, VAULTS_CANCEL_REQUEST_WITHDRAW_DISCRIMINATOR);
  }

  public static final Discriminator VAULTS_DEPOSIT_DISCRIMINATOR = toDiscriminator(124, 173, 191, 223, 48, 26, 84, 84);

  public static List<AccountMeta> vaultsDepositKeys(final SolanaAccounts solanaAccounts,
                                                    final PublicKey glamStateKey,
                                                    final PublicKey glamVaultKey,
                                                    final PublicKey glamSignerKey,
                                                    final PublicKey integrationAuthorityKey,
                                                    final PublicKey cpiProgramKey,
                                                    final PublicKey glamProtocolProgramKey,
                                                    final PublicKey vaultKey,
                                                    final PublicKey vaultDepositorKey,
                                                    final PublicKey vaultTokenAccountKey,
                                                    final PublicKey driftUserStatsKey,
                                                    final PublicKey driftUserKey,
                                                    final PublicKey driftStateKey,
                                                    final PublicKey driftSpotMarketVaultKey,
                                                    final PublicKey userTokenAccountKey,
                                                    final PublicKey driftProgramKey,
                                                    final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(vaultKey),
      createWrite(vaultDepositorKey),
      createWrite(vaultTokenAccountKey),
      createWrite(driftUserStatsKey),
      createWrite(driftUserKey),
      createRead(driftStateKey),
      createWrite(driftSpotMarketVaultKey),
      createWrite(userTokenAccountKey),
      createRead(driftProgramKey),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction vaultsDeposit(final AccountMeta invokedExtDriftProgramMeta,
                                          final SolanaAccounts solanaAccounts,
                                          final PublicKey glamStateKey,
                                          final PublicKey glamVaultKey,
                                          final PublicKey glamSignerKey,
                                          final PublicKey integrationAuthorityKey,
                                          final PublicKey cpiProgramKey,
                                          final PublicKey glamProtocolProgramKey,
                                          final PublicKey vaultKey,
                                          final PublicKey vaultDepositorKey,
                                          final PublicKey vaultTokenAccountKey,
                                          final PublicKey driftUserStatsKey,
                                          final PublicKey driftUserKey,
                                          final PublicKey driftStateKey,
                                          final PublicKey driftSpotMarketVaultKey,
                                          final PublicKey userTokenAccountKey,
                                          final PublicKey driftProgramKey,
                                          final PublicKey tokenProgramKey,
                                          final long amount) {
    final var keys = vaultsDepositKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      vaultKey,
      vaultDepositorKey,
      vaultTokenAccountKey,
      driftUserStatsKey,
      driftUserKey,
      driftStateKey,
      driftSpotMarketVaultKey,
      userTokenAccountKey,
      driftProgramKey,
      tokenProgramKey
    );
    return vaultsDeposit(invokedExtDriftProgramMeta, keys, amount);
  }

  public static Instruction vaultsDeposit(final AccountMeta invokedExtDriftProgramMeta,
                                          final List<AccountMeta> keys,
                                          final long amount) {
    final byte[] _data = new byte[16];
    int i = VAULTS_DEPOSIT_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, amount);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record VaultsDepositIxData(Discriminator discriminator, long amount) implements SerDe {  

    public static VaultsDepositIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static final int AMOUNT_OFFSET = 8;

    public static VaultsDepositIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var amount = getInt64LE(_data, i);
      return new VaultsDepositIxData(discriminator, amount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, amount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator VAULTS_INITIALIZE_VAULT_DEPOSITOR_DISCRIMINATOR = toDiscriminator(135, 5, 41, 254, 229, 75, 138, 49);

  public static List<AccountMeta> vaultsInitializeVaultDepositorKeys(final SolanaAccounts solanaAccounts,
                                                                     final PublicKey glamStateKey,
                                                                     final PublicKey glamVaultKey,
                                                                     final PublicKey glamSignerKey,
                                                                     final PublicKey integrationAuthorityKey,
                                                                     final PublicKey cpiProgramKey,
                                                                     final PublicKey glamProtocolProgramKey,
                                                                     final PublicKey vaultKey,
                                                                     final PublicKey vaultDepositorKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(vaultKey),
      createWrite(vaultDepositorKey),
      createRead(solanaAccounts.rentSysVar()),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction vaultsInitializeVaultDepositor(final AccountMeta invokedExtDriftProgramMeta,
                                                           final SolanaAccounts solanaAccounts,
                                                           final PublicKey glamStateKey,
                                                           final PublicKey glamVaultKey,
                                                           final PublicKey glamSignerKey,
                                                           final PublicKey integrationAuthorityKey,
                                                           final PublicKey cpiProgramKey,
                                                           final PublicKey glamProtocolProgramKey,
                                                           final PublicKey vaultKey,
                                                           final PublicKey vaultDepositorKey) {
    final var keys = vaultsInitializeVaultDepositorKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      vaultKey,
      vaultDepositorKey
    );
    return vaultsInitializeVaultDepositor(invokedExtDriftProgramMeta, keys);
  }

  public static Instruction vaultsInitializeVaultDepositor(final AccountMeta invokedExtDriftProgramMeta,
                                                           final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, VAULTS_INITIALIZE_VAULT_DEPOSITOR_DISCRIMINATOR);
  }

  public static final Discriminator VAULTS_REQUEST_WITHDRAW_DISCRIMINATOR = toDiscriminator(138, 91, 50, 130, 167, 165, 120, 175);

  public static List<AccountMeta> vaultsRequestWithdrawKeys(final SolanaAccounts solanaAccounts,
                                                            final PublicKey glamStateKey,
                                                            final PublicKey glamVaultKey,
                                                            final PublicKey glamSignerKey,
                                                            final PublicKey integrationAuthorityKey,
                                                            final PublicKey cpiProgramKey,
                                                            final PublicKey glamProtocolProgramKey,
                                                            final PublicKey vaultKey,
                                                            final PublicKey vaultDepositorKey,
                                                            final PublicKey driftUserStatsKey,
                                                            final PublicKey driftUserKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(vaultKey),
      createWrite(vaultDepositorKey),
      createRead(driftUserStatsKey),
      createRead(driftUserKey)
    );
  }

  public static Instruction vaultsRequestWithdraw(final AccountMeta invokedExtDriftProgramMeta,
                                                  final SolanaAccounts solanaAccounts,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamVaultKey,
                                                  final PublicKey glamSignerKey,
                                                  final PublicKey integrationAuthorityKey,
                                                  final PublicKey cpiProgramKey,
                                                  final PublicKey glamProtocolProgramKey,
                                                  final PublicKey vaultKey,
                                                  final PublicKey vaultDepositorKey,
                                                  final PublicKey driftUserStatsKey,
                                                  final PublicKey driftUserKey,
                                                  final long withdrawAmount,
                                                  final WithdrawUnit withdrawUnit) {
    final var keys = vaultsRequestWithdrawKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      vaultKey,
      vaultDepositorKey,
      driftUserStatsKey,
      driftUserKey
    );
    return vaultsRequestWithdraw(invokedExtDriftProgramMeta, keys, withdrawAmount, withdrawUnit);
  }

  public static Instruction vaultsRequestWithdraw(final AccountMeta invokedExtDriftProgramMeta,
                                                  final List<AccountMeta> keys,
                                                  final long withdrawAmount,
                                                  final WithdrawUnit withdrawUnit) {
    final byte[] _data = new byte[16 + withdrawUnit.l()];
    int i = VAULTS_REQUEST_WITHDRAW_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, withdrawAmount);
    i += 8;
    withdrawUnit.write(_data, i);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record VaultsRequestWithdrawIxData(Discriminator discriminator, long withdrawAmount, WithdrawUnit withdrawUnit) implements SerDe {  

    public static VaultsRequestWithdrawIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 17;

    public static final int WITHDRAW_AMOUNT_OFFSET = 8;
    public static final int WITHDRAW_UNIT_OFFSET = 16;

    public static VaultsRequestWithdrawIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var withdrawAmount = getInt64LE(_data, i);
      i += 8;
      final var withdrawUnit = WithdrawUnit.read(_data, i);
      return new VaultsRequestWithdrawIxData(discriminator, withdrawAmount, withdrawUnit);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, withdrawAmount);
      i += 8;
      i += withdrawUnit.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator VAULTS_WITHDRAW_DISCRIMINATOR = toDiscriminator(12, 8, 236, 92, 134, 144, 196, 87);

  public static List<AccountMeta> vaultsWithdrawKeys(final SolanaAccounts solanaAccounts,
                                                     final PublicKey glamStateKey,
                                                     final PublicKey glamVaultKey,
                                                     final PublicKey glamSignerKey,
                                                     final PublicKey integrationAuthorityKey,
                                                     final PublicKey cpiProgramKey,
                                                     final PublicKey glamProtocolProgramKey,
                                                     final PublicKey vaultKey,
                                                     final PublicKey vaultDepositorKey,
                                                     final PublicKey vaultTokenAccountKey,
                                                     final PublicKey driftUserStatsKey,
                                                     final PublicKey driftUserKey,
                                                     final PublicKey driftStateKey,
                                                     final PublicKey driftSpotMarketVaultKey,
                                                     final PublicKey driftSignerKey,
                                                     final PublicKey userTokenAccountKey,
                                                     final PublicKey driftProgramKey,
                                                     final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(vaultKey),
      createWrite(vaultDepositorKey),
      createWrite(vaultTokenAccountKey),
      createWrite(driftUserStatsKey),
      createWrite(driftUserKey),
      createRead(driftStateKey),
      createWrite(driftSpotMarketVaultKey),
      createRead(driftSignerKey),
      createWrite(userTokenAccountKey),
      createRead(driftProgramKey),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction vaultsWithdraw(final AccountMeta invokedExtDriftProgramMeta,
                                           final SolanaAccounts solanaAccounts,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamVaultKey,
                                           final PublicKey glamSignerKey,
                                           final PublicKey integrationAuthorityKey,
                                           final PublicKey cpiProgramKey,
                                           final PublicKey glamProtocolProgramKey,
                                           final PublicKey vaultKey,
                                           final PublicKey vaultDepositorKey,
                                           final PublicKey vaultTokenAccountKey,
                                           final PublicKey driftUserStatsKey,
                                           final PublicKey driftUserKey,
                                           final PublicKey driftStateKey,
                                           final PublicKey driftSpotMarketVaultKey,
                                           final PublicKey driftSignerKey,
                                           final PublicKey userTokenAccountKey,
                                           final PublicKey driftProgramKey,
                                           final PublicKey tokenProgramKey) {
    final var keys = vaultsWithdrawKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      vaultKey,
      vaultDepositorKey,
      vaultTokenAccountKey,
      driftUserStatsKey,
      driftUserKey,
      driftStateKey,
      driftSpotMarketVaultKey,
      driftSignerKey,
      userTokenAccountKey,
      driftProgramKey,
      tokenProgramKey
    );
    return vaultsWithdraw(invokedExtDriftProgramMeta, keys);
  }

  public static Instruction vaultsWithdraw(final AccountMeta invokedExtDriftProgramMeta,
                                           final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, VAULTS_WITHDRAW_DISCRIMINATOR);
  }

  public static final Discriminator WITHDRAW_DISCRIMINATOR = toDiscriminator(183, 18, 70, 156, 148, 109, 161, 34);

  public static List<AccountMeta> withdrawKeys(final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PublicKey stateKey,
                                               final PublicKey userKey,
                                               final PublicKey userStatsKey,
                                               final PublicKey spotMarketVaultKey,
                                               final PublicKey driftSignerKey,
                                               final PublicKey userTokenAccountKey,
                                               final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(stateKey),
      createWrite(userKey),
      createWrite(userStatsKey),
      createWrite(spotMarketVaultKey),
      createRead(driftSignerKey),
      createWrite(userTokenAccountKey),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction withdraw(final AccountMeta invokedExtDriftProgramMeta,
                                     final SolanaAccounts solanaAccounts,
                                     final PublicKey glamStateKey,
                                     final PublicKey glamVaultKey,
                                     final PublicKey glamSignerKey,
                                     final PublicKey integrationAuthorityKey,
                                     final PublicKey cpiProgramKey,
                                     final PublicKey glamProtocolProgramKey,
                                     final PublicKey stateKey,
                                     final PublicKey userKey,
                                     final PublicKey userStatsKey,
                                     final PublicKey spotMarketVaultKey,
                                     final PublicKey driftSignerKey,
                                     final PublicKey userTokenAccountKey,
                                     final PublicKey tokenProgramKey,
                                     final int marketIndex,
                                     final long amount,
                                     final boolean reduceOnly) {
    final var keys = withdrawKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      stateKey,
      userKey,
      userStatsKey,
      spotMarketVaultKey,
      driftSignerKey,
      userTokenAccountKey,
      tokenProgramKey
    );
    return withdraw(
      invokedExtDriftProgramMeta,
      keys,
      marketIndex,
      amount,
      reduceOnly
    );
  }

  public static Instruction withdraw(final AccountMeta invokedExtDriftProgramMeta,
                                     final List<AccountMeta> keys,
                                     final int marketIndex,
                                     final long amount,
                                     final boolean reduceOnly) {
    final byte[] _data = new byte[19];
    int i = WITHDRAW_DISCRIMINATOR.write(_data, 0);
    putInt16LE(_data, i, marketIndex);
    i += 2;
    putInt64LE(_data, i, amount);
    i += 8;
    _data[i] = (byte) (reduceOnly ? 1 : 0);

    return Instruction.createInstruction(invokedExtDriftProgramMeta, keys, _data);
  }

  public record WithdrawIxData(Discriminator discriminator,
                               int marketIndex,
                               long amount,
                               boolean reduceOnly) implements SerDe {  

    public static WithdrawIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 19;

    public static final int MARKET_INDEX_OFFSET = 8;
    public static final int AMOUNT_OFFSET = 10;
    public static final int REDUCE_ONLY_OFFSET = 18;

    public static WithdrawIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var marketIndex = getInt16LE(_data, i);
      i += 2;
      final var amount = getInt64LE(_data, i);
      i += 8;
      final var reduceOnly = _data[i] == 1;
      return new WithdrawIxData(discriminator, marketIndex, amount, reduceOnly);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt16LE(_data, i, marketIndex);
      i += 2;
      putInt64LE(_data, i, amount);
      i += 8;
      _data[i] = (byte) (reduceOnly ? 1 : 0);
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  private ExtDriftProgram() {
  }
}
