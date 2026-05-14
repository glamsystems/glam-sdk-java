package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;
import systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types.*;

import java.util.List;
import java.util.OptionalLong;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.accounts.meta.AccountMeta.*;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public final class ExtPhoenixProgram {

  public static final Discriminator CANCEL_ALL_DISCRIMINATOR = toDiscriminator(98, 191, 75, 220, 115, 40, 71, 237);

  public static List<AccountMeta> cancelAllKeys(final SolanaAccounts solanaAccounts,
                                                final PublicKey glamStateKey,
                                                final PublicKey glamVaultKey,
                                                final PublicKey glamSignerKey,
                                                final PublicKey integrationAuthorityKey,
                                                final PublicKey cpiProgramKey,
                                                final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction cancelAll(final AccountMeta invokedExtPhoenixProgramMeta,
                                      final SolanaAccounts solanaAccounts,
                                      final PublicKey glamStateKey,
                                      final PublicKey glamVaultKey,
                                      final PublicKey glamSignerKey,
                                      final PublicKey integrationAuthorityKey,
                                      final PublicKey cpiProgramKey,
                                      final PublicKey glamProtocolProgramKey,
                                      final PublicKey market,
                                      final PhoenixMarketAccountArgs accountArgs) {
    final var keys = cancelAllKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return cancelAll(invokedExtPhoenixProgramMeta, keys, market, accountArgs);
  }

  public static Instruction cancelAll(final AccountMeta invokedExtPhoenixProgramMeta,
                                      final List<AccountMeta> keys,
                                      final PublicKey market,
                                      final PhoenixMarketAccountArgs accountArgs) {
    final byte[] _data = new byte[40 + accountArgs.l()];
    int i = CANCEL_ALL_DISCRIMINATOR.write(_data, 0);
    market.write(_data, i);
    i += 32;
    accountArgs.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record CancelAllIxData(Discriminator discriminator, PublicKey market, PhoenixMarketAccountArgs accountArgs) implements SerDe {  

    public static CancelAllIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 44;

    public static final int MARKET_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;

    public static CancelAllIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var market = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixMarketAccountArgs.read(_data, i);
      return new CancelAllIxData(discriminator, market, accountArgs);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      market.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator CANCEL_ORDERS_BY_ID_DISCRIMINATOR = toDiscriminator(234, 204, 126, 94, 222, 22, 141, 24);

  public static List<AccountMeta> cancelOrdersByIdKeys(final SolanaAccounts solanaAccounts,
                                                       final PublicKey glamStateKey,
                                                       final PublicKey glamVaultKey,
                                                       final PublicKey glamSignerKey,
                                                       final PublicKey integrationAuthorityKey,
                                                       final PublicKey cpiProgramKey,
                                                       final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction cancelOrdersById(final AccountMeta invokedExtPhoenixProgramMeta,
                                             final SolanaAccounts solanaAccounts,
                                             final PublicKey glamStateKey,
                                             final PublicKey glamVaultKey,
                                             final PublicKey glamSignerKey,
                                             final PublicKey integrationAuthorityKey,
                                             final PublicKey cpiProgramKey,
                                             final PublicKey glamProtocolProgramKey,
                                             final PublicKey market,
                                             final PhoenixMarketAccountArgs accountArgs,
                                             final CancelId[] orderIds) {
    final var keys = cancelOrdersByIdKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return cancelOrdersById(
      invokedExtPhoenixProgramMeta,
      keys,
      market,
      accountArgs,
      orderIds
    );
  }

  public static Instruction cancelOrdersById(final AccountMeta invokedExtPhoenixProgramMeta,
                                             final List<AccountMeta> keys,
                                             final PublicKey market,
                                             final PhoenixMarketAccountArgs accountArgs,
                                             final CancelId[] orderIds) {
    final byte[] _data = new byte[40 + accountArgs.l() + SerDeUtil.lenVector(4, orderIds)];
    int i = CANCEL_ORDERS_BY_ID_DISCRIMINATOR.write(_data, 0);
    market.write(_data, i);
    i += 32;
    i += accountArgs.write(_data, i);
    SerDeUtil.writeVector(4, orderIds, _data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record CancelOrdersByIdIxData(Discriminator discriminator,
                                       PublicKey market,
                                       PhoenixMarketAccountArgs accountArgs,
                                       CancelId[] orderIds) implements SerDe {  

    public static CancelOrdersByIdIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int MARKET_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;
    public static final int ORDER_IDS_OFFSET = 44;

    public static CancelOrdersByIdIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var market = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixMarketAccountArgs.read(_data, i);
      i += accountArgs.l();
      final var orderIds = SerDeUtil.readVector(4, CancelId.class, CancelId::read, _data, i);
      return new CancelOrdersByIdIxData(discriminator, market, accountArgs, orderIds);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      market.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      i += SerDeUtil.writeVector(4, orderIds, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + 32 + accountArgs.l() + SerDeUtil.lenVector(4, orderIds);
    }
  }

  public static final Discriminator CANCEL_UP_TO_DISCRIMINATOR = toDiscriminator(26, 209, 244, 253, 59, 175, 227, 54);

  public static List<AccountMeta> cancelUpToKeys(final SolanaAccounts solanaAccounts,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey glamSignerKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PublicKey cpiProgramKey,
                                                 final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction cancelUpTo(final AccountMeta invokedExtPhoenixProgramMeta,
                                       final SolanaAccounts solanaAccounts,
                                       final PublicKey glamStateKey,
                                       final PublicKey glamVaultKey,
                                       final PublicKey glamSignerKey,
                                       final PublicKey integrationAuthorityKey,
                                       final PublicKey cpiProgramKey,
                                       final PublicKey glamProtocolProgramKey,
                                       final PublicKey market,
                                       final PhoenixMarketAccountArgs accountArgs,
                                       final CancelUpToArgs args) {
    final var keys = cancelUpToKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return cancelUpTo(
      invokedExtPhoenixProgramMeta,
      keys,
      market,
      accountArgs,
      args
    );
  }

  public static Instruction cancelUpTo(final AccountMeta invokedExtPhoenixProgramMeta,
                                       final List<AccountMeta> keys,
                                       final PublicKey market,
                                       final PhoenixMarketAccountArgs accountArgs,
                                       final CancelUpToArgs args) {
    final byte[] _data = new byte[40 + accountArgs.l() + args.l()];
    int i = CANCEL_UP_TO_DISCRIMINATOR.write(_data, 0);
    market.write(_data, i);
    i += 32;
    i += accountArgs.write(_data, i);
    args.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record CancelUpToIxData(Discriminator discriminator,
                                 PublicKey market,
                                 PhoenixMarketAccountArgs accountArgs,
                                 CancelUpToArgs args) implements SerDe {  

    public static CancelUpToIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int MARKET_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;
    public static final int ARGS_OFFSET = 44;

    public static CancelUpToIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var market = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixMarketAccountArgs.read(_data, i);
      i += accountArgs.l();
      final var args = CancelUpToArgs.read(_data, i);
      return new CancelUpToIxData(discriminator, market, accountArgs, args);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      market.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      i += args.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + 32 + accountArgs.l() + args.l();
    }
  }

  public static final Discriminator DEPOSIT_FUNDS_DISCRIMINATOR = toDiscriminator(202, 39, 52, 211, 53, 20, 250, 88);

  public static List<AccountMeta> depositFundsKeys(final SolanaAccounts solanaAccounts,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamVaultKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey integrationAuthorityKey,
                                                   final PublicKey cpiProgramKey,
                                                   final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction depositFunds(final AccountMeta invokedExtPhoenixProgramMeta,
                                         final SolanaAccounts solanaAccounts,
                                         final PublicKey glamStateKey,
                                         final PublicKey glamVaultKey,
                                         final PublicKey glamSignerKey,
                                         final PublicKey integrationAuthorityKey,
                                         final PublicKey cpiProgramKey,
                                         final PublicKey glamProtocolProgramKey,
                                         final PublicKey mint,
                                         final PhoenixAccountWindowArgs accountArgs,
                                         final long amount) {
    final var keys = depositFundsKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return depositFunds(
      invokedExtPhoenixProgramMeta,
      keys,
      mint,
      accountArgs,
      amount
    );
  }

  public static Instruction depositFunds(final AccountMeta invokedExtPhoenixProgramMeta,
                                         final List<AccountMeta> keys,
                                         final PublicKey mint,
                                         final PhoenixAccountWindowArgs accountArgs,
                                         final long amount) {
    final byte[] _data = new byte[48 + accountArgs.l()];
    int i = DEPOSIT_FUNDS_DISCRIMINATOR.write(_data, 0);
    mint.write(_data, i);
    i += 32;
    i += accountArgs.write(_data, i);
    putInt64LE(_data, i, amount);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record DepositFundsIxData(Discriminator discriminator,
                                   PublicKey mint,
                                   PhoenixAccountWindowArgs accountArgs,
                                   long amount) implements SerDe {  

    public static DepositFundsIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 52;

    public static final int MINT_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;
    public static final int AMOUNT_OFFSET = 44;

    public static DepositFundsIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var mint = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixAccountWindowArgs.read(_data, i);
      i += accountArgs.l();
      final var amount = getInt64LE(_data, i);
      return new DepositFundsIxData(discriminator, mint, accountArgs, amount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      mint.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      putInt64LE(_data, i, amount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator EMBER_DEPOSIT_DISCRIMINATOR = toDiscriminator(129, 244, 126, 98, 200, 114, 197, 241);

  public static List<AccountMeta> emberDepositKeys(final SolanaAccounts solanaAccounts,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamVaultKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey integrationAuthorityKey,
                                                   final PublicKey cpiProgramKey,
                                                   final PublicKey glamProtocolProgramKey,
                                                   final PublicKey emberStateKey,
                                                   final PublicKey inputMintKey,
                                                   final PublicKey outputMintKey,
                                                   final PublicKey inputTokenAccountKey,
                                                   final PublicKey outputTokenAccountKey,
                                                   final PublicKey emberVaultKey,
                                                   final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(emberStateKey),
      createRead(inputMintKey),
      createWrite(outputMintKey),
      createWrite(inputTokenAccountKey),
      createWrite(outputTokenAccountKey),
      createWrite(emberVaultKey),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction emberDeposit(final AccountMeta invokedExtPhoenixProgramMeta,
                                         final SolanaAccounts solanaAccounts,
                                         final PublicKey glamStateKey,
                                         final PublicKey glamVaultKey,
                                         final PublicKey glamSignerKey,
                                         final PublicKey integrationAuthorityKey,
                                         final PublicKey cpiProgramKey,
                                         final PublicKey glamProtocolProgramKey,
                                         final PublicKey emberStateKey,
                                         final PublicKey inputMintKey,
                                         final PublicKey outputMintKey,
                                         final PublicKey inputTokenAccountKey,
                                         final PublicKey outputTokenAccountKey,
                                         final PublicKey emberVaultKey,
                                         final PublicKey tokenProgramKey,
                                         final long amount) {
    final var keys = emberDepositKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      emberStateKey,
      inputMintKey,
      outputMintKey,
      inputTokenAccountKey,
      outputTokenAccountKey,
      emberVaultKey,
      tokenProgramKey
    );
    return emberDeposit(invokedExtPhoenixProgramMeta, keys, amount);
  }

  public static Instruction emberDeposit(final AccountMeta invokedExtPhoenixProgramMeta,
                                         final List<AccountMeta> keys,
                                         final long amount) {
    final byte[] _data = new byte[16];
    int i = EMBER_DEPOSIT_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, amount);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record EmberDepositIxData(Discriminator discriminator, long amount) implements SerDe {  

    public static EmberDepositIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static final int AMOUNT_OFFSET = 8;

    public static EmberDepositIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var amount = getInt64LE(_data, i);
      return new EmberDepositIxData(discriminator, amount);
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

  public static final Discriminator EMBER_WITHDRAW_DISCRIMINATOR = toDiscriminator(189, 35, 37, 116, 123, 40, 98, 216);

  public static List<AccountMeta> emberWithdrawKeys(final SolanaAccounts solanaAccounts,
                                                    final PublicKey glamStateKey,
                                                    final PublicKey glamVaultKey,
                                                    final PublicKey glamSignerKey,
                                                    final PublicKey integrationAuthorityKey,
                                                    final PublicKey cpiProgramKey,
                                                    final PublicKey glamProtocolProgramKey,
                                                    final PublicKey emberStateKey,
                                                    final PublicKey inputMintKey,
                                                    final PublicKey outputMintKey,
                                                    final PublicKey inputTokenAccountKey,
                                                    final PublicKey outputTokenAccountKey,
                                                    final PublicKey emberVaultKey,
                                                    final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(emberStateKey),
      createRead(inputMintKey),
      createWrite(outputMintKey),
      createWrite(inputTokenAccountKey),
      createWrite(outputTokenAccountKey),
      createWrite(emberVaultKey),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction emberWithdraw(final AccountMeta invokedExtPhoenixProgramMeta,
                                          final SolanaAccounts solanaAccounts,
                                          final PublicKey glamStateKey,
                                          final PublicKey glamVaultKey,
                                          final PublicKey glamSignerKey,
                                          final PublicKey integrationAuthorityKey,
                                          final PublicKey cpiProgramKey,
                                          final PublicKey glamProtocolProgramKey,
                                          final PublicKey emberStateKey,
                                          final PublicKey inputMintKey,
                                          final PublicKey outputMintKey,
                                          final PublicKey inputTokenAccountKey,
                                          final PublicKey outputTokenAccountKey,
                                          final PublicKey emberVaultKey,
                                          final PublicKey tokenProgramKey,
                                          final OptionalLong amount) {
    final var keys = emberWithdrawKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      emberStateKey,
      inputMintKey,
      outputMintKey,
      inputTokenAccountKey,
      outputTokenAccountKey,
      emberVaultKey,
      tokenProgramKey
    );
    return emberWithdraw(invokedExtPhoenixProgramMeta, keys, amount);
  }

  public static Instruction emberWithdraw(final AccountMeta invokedExtPhoenixProgramMeta,
                                          final List<AccountMeta> keys,
                                          final OptionalLong amount) {
    final byte[] _data = new byte[
    8
    + (amount == null || amount.isEmpty() ? 1 : 9)
    ];
    int i = EMBER_WITHDRAW_DISCRIMINATOR.write(_data, 0);
    SerDeUtil.writeOptional(1, amount, _data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record EmberWithdrawIxData(Discriminator discriminator, OptionalLong amount) implements SerDe {  

    public static EmberWithdrawIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int AMOUNT_OFFSET = 9;

    public static EmberWithdrawIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final OptionalLong amount;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        amount = OptionalLong.empty();
      } else {
        ++i;
        amount = OptionalLong.of(getInt64LE(_data, i));
      }
      return new EmberWithdrawIxData(discriminator, amount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeOptional(1, amount, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + (amount == null || amount.isEmpty() ? 1 : (1 + 8));
    }
  }

  public static final Discriminator INITIALIZE_TRADER_DISCRIMINATOR = toDiscriminator(141, 33, 115, 6, 185, 109, 20, 117);

  public static List<AccountMeta> initializeTraderKeys(final SolanaAccounts solanaAccounts,
                                                       final PublicKey glamStateKey,
                                                       final PublicKey glamVaultKey,
                                                       final PublicKey glamSignerKey,
                                                       final PublicKey integrationAuthorityKey,
                                                       final PublicKey cpiProgramKey,
                                                       final PublicKey glamProtocolProgramKey,
                                                       final PublicKey logAuthorityKey,
                                                       final PublicKey globalConfigKey,
                                                       final PublicKey traderAccountKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(logAuthorityKey),
      createWrite(globalConfigKey),
      createWrite(traderAccountKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction initializeTrader(final AccountMeta invokedExtPhoenixProgramMeta,
                                             final SolanaAccounts solanaAccounts,
                                             final PublicKey glamStateKey,
                                             final PublicKey glamVaultKey,
                                             final PublicKey glamSignerKey,
                                             final PublicKey integrationAuthorityKey,
                                             final PublicKey cpiProgramKey,
                                             final PublicKey glamProtocolProgramKey,
                                             final PublicKey logAuthorityKey,
                                             final PublicKey globalConfigKey,
                                             final PublicKey traderAccountKey,
                                             final RegisterTraderArgs args) {
    final var keys = initializeTraderKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      logAuthorityKey,
      globalConfigKey,
      traderAccountKey
    );
    return initializeTrader(invokedExtPhoenixProgramMeta, keys, args);
  }

  public static Instruction initializeTrader(final AccountMeta invokedExtPhoenixProgramMeta,
                                             final List<AccountMeta> keys,
                                             final RegisterTraderArgs args) {
    final byte[] _data = new byte[8 + args.l()];
    int i = INITIALIZE_TRADER_DISCRIMINATOR.write(_data, 0);
    args.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record InitializeTraderIxData(Discriminator discriminator, RegisterTraderArgs args) implements SerDe {  

    public static InitializeTraderIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 18;

    public static final int ARGS_OFFSET = 8;

    public static InitializeTraderIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var args = RegisterTraderArgs.read(_data, i);
      return new InitializeTraderIxData(discriminator, args);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += args.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator PLACE_LIMIT_ORDER_DISCRIMINATOR = toDiscriminator(108, 176, 33, 186, 146, 229, 1, 197);

  public static List<AccountMeta> placeLimitOrderKeys(final SolanaAccounts solanaAccounts,
                                                      final PublicKey glamStateKey,
                                                      final PublicKey glamVaultKey,
                                                      final PublicKey glamSignerKey,
                                                      final PublicKey integrationAuthorityKey,
                                                      final PublicKey cpiProgramKey,
                                                      final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction placeLimitOrder(final AccountMeta invokedExtPhoenixProgramMeta,
                                            final SolanaAccounts solanaAccounts,
                                            final PublicKey glamStateKey,
                                            final PublicKey glamVaultKey,
                                            final PublicKey glamSignerKey,
                                            final PublicKey integrationAuthorityKey,
                                            final PublicKey cpiProgramKey,
                                            final PublicKey glamProtocolProgramKey,
                                            final PublicKey market,
                                            final PhoenixMarketAccountArgs accountArgs,
                                            final ReferencePriceArgs referencePrice,
                                            final LimitOrderPacket packet) {
    final var keys = placeLimitOrderKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return placeLimitOrder(
      invokedExtPhoenixProgramMeta,
      keys,
      market,
      accountArgs,
      referencePrice,
      packet
    );
  }

  public static Instruction placeLimitOrder(final AccountMeta invokedExtPhoenixProgramMeta,
                                            final List<AccountMeta> keys,
                                            final PublicKey market,
                                            final PhoenixMarketAccountArgs accountArgs,
                                            final ReferencePriceArgs referencePrice,
                                            final LimitOrderPacket packet) {
    final byte[] _data = new byte[
    40 + accountArgs.l()
    + (referencePrice == null ? 1 : (1 + referencePrice.l())) + packet.l()
    ];
    int i = PLACE_LIMIT_ORDER_DISCRIMINATOR.write(_data, 0);
    market.write(_data, i);
    i += 32;
    i += accountArgs.write(_data, i);
    i += SerDeUtil.writeOptional(1, referencePrice, _data, i);
    packet.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record PlaceLimitOrderIxData(Discriminator discriminator,
                                      PublicKey market,
                                      PhoenixMarketAccountArgs accountArgs,
                                      ReferencePriceArgs referencePrice,
                                      LimitOrderPacket packet) implements SerDe {  

    public static PlaceLimitOrderIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int MARKET_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;
    public static final int REFERENCE_PRICE_OFFSET = 45;

    public static PlaceLimitOrderIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var market = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixMarketAccountArgs.read(_data, i);
      i += accountArgs.l();
      final ReferencePriceArgs referencePrice;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        referencePrice = null;
        ++i;
      } else {
        ++i;
        referencePrice = ReferencePriceArgs.read(_data, i);
        i += referencePrice.l();
      }
      final var packet = LimitOrderPacket.read(_data, i);
      return new PlaceLimitOrderIxData(discriminator,
                                       market,
                                       accountArgs,
                                       referencePrice,
                                       packet);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      market.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      i += SerDeUtil.writeOptional(1, referencePrice, _data, i);
      i += packet.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + 32 + accountArgs.l() + (referencePrice == null ? 1 : (1 + referencePrice.l())) + packet.l();
    }
  }

  public static final Discriminator PLACE_MARKET_ORDER_DISCRIMINATOR = toDiscriminator(90, 118, 192, 252, 192, 99, 39, 145);

  public static List<AccountMeta> placeMarketOrderKeys(final SolanaAccounts solanaAccounts,
                                                       final PublicKey glamStateKey,
                                                       final PublicKey glamVaultKey,
                                                       final PublicKey glamSignerKey,
                                                       final PublicKey integrationAuthorityKey,
                                                       final PublicKey cpiProgramKey,
                                                       final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction placeMarketOrder(final AccountMeta invokedExtPhoenixProgramMeta,
                                             final SolanaAccounts solanaAccounts,
                                             final PublicKey glamStateKey,
                                             final PublicKey glamVaultKey,
                                             final PublicKey glamSignerKey,
                                             final PublicKey integrationAuthorityKey,
                                             final PublicKey cpiProgramKey,
                                             final PublicKey glamProtocolProgramKey,
                                             final PublicKey market,
                                             final PhoenixMarketAccountArgs accountArgs,
                                             final ReferencePriceArgs referencePrice,
                                             final ImmediateOrCancelOrderPacket packet) {
    final var keys = placeMarketOrderKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return placeMarketOrder(
      invokedExtPhoenixProgramMeta,
      keys,
      market,
      accountArgs,
      referencePrice,
      packet
    );
  }

  public static Instruction placeMarketOrder(final AccountMeta invokedExtPhoenixProgramMeta,
                                             final List<AccountMeta> keys,
                                             final PublicKey market,
                                             final PhoenixMarketAccountArgs accountArgs,
                                             final ReferencePriceArgs referencePrice,
                                             final ImmediateOrCancelOrderPacket packet) {
    final byte[] _data = new byte[
    40 + accountArgs.l()
    + (referencePrice == null ? 1 : (1 + referencePrice.l())) + packet.l()
    ];
    int i = PLACE_MARKET_ORDER_DISCRIMINATOR.write(_data, 0);
    market.write(_data, i);
    i += 32;
    i += accountArgs.write(_data, i);
    i += SerDeUtil.writeOptional(1, referencePrice, _data, i);
    packet.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record PlaceMarketOrderIxData(Discriminator discriminator,
                                       PublicKey market,
                                       PhoenixMarketAccountArgs accountArgs,
                                       ReferencePriceArgs referencePrice,
                                       ImmediateOrCancelOrderPacket packet) implements SerDe {  

    public static PlaceMarketOrderIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int MARKET_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;
    public static final int REFERENCE_PRICE_OFFSET = 45;

    public static PlaceMarketOrderIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var market = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixMarketAccountArgs.read(_data, i);
      i += accountArgs.l();
      final ReferencePriceArgs referencePrice;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        referencePrice = null;
        ++i;
      } else {
        ++i;
        referencePrice = ReferencePriceArgs.read(_data, i);
        i += referencePrice.l();
      }
      final var packet = ImmediateOrCancelOrderPacket.read(_data, i);
      return new PlaceMarketOrderIxData(discriminator,
                                        market,
                                        accountArgs,
                                        referencePrice,
                                        packet);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      market.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      i += SerDeUtil.writeOptional(1, referencePrice, _data, i);
      i += packet.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + 32 + accountArgs.l() + (referencePrice == null ? 1 : (1 + referencePrice.l())) + packet.l();
    }
  }

  public static final Discriminator PLACE_MULTI_LIMIT_ORDER_DISCRIMINATOR = toDiscriminator(236, 208, 221, 172, 141, 226, 129, 84);

  public static List<AccountMeta> placeMultiLimitOrderKeys(final SolanaAccounts solanaAccounts,
                                                           final PublicKey glamStateKey,
                                                           final PublicKey glamVaultKey,
                                                           final PublicKey glamSignerKey,
                                                           final PublicKey integrationAuthorityKey,
                                                           final PublicKey cpiProgramKey,
                                                           final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction placeMultiLimitOrder(final AccountMeta invokedExtPhoenixProgramMeta,
                                                 final SolanaAccounts solanaAccounts,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey glamSignerKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PublicKey cpiProgramKey,
                                                 final PublicKey glamProtocolProgramKey,
                                                 final PublicKey market,
                                                 final PhoenixMarketAccountArgs accountArgs,
                                                 final ReferencePriceArgs referencePrice,
                                                 final MultipleOrderPacket packet) {
    final var keys = placeMultiLimitOrderKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return placeMultiLimitOrder(
      invokedExtPhoenixProgramMeta,
      keys,
      market,
      accountArgs,
      referencePrice,
      packet
    );
  }

  public static Instruction placeMultiLimitOrder(final AccountMeta invokedExtPhoenixProgramMeta,
                                                 final List<AccountMeta> keys,
                                                 final PublicKey market,
                                                 final PhoenixMarketAccountArgs accountArgs,
                                                 final ReferencePriceArgs referencePrice,
                                                 final MultipleOrderPacket packet) {
    final byte[] _data = new byte[
    40 + accountArgs.l()
    + (referencePrice == null ? 1 : (1 + referencePrice.l())) + packet.l()
    ];
    int i = PLACE_MULTI_LIMIT_ORDER_DISCRIMINATOR.write(_data, 0);
    market.write(_data, i);
    i += 32;
    i += accountArgs.write(_data, i);
    i += SerDeUtil.writeOptional(1, referencePrice, _data, i);
    packet.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record PlaceMultiLimitOrderIxData(Discriminator discriminator,
                                           PublicKey market,
                                           PhoenixMarketAccountArgs accountArgs,
                                           ReferencePriceArgs referencePrice,
                                           MultipleOrderPacket packet) implements SerDe {  

    public static PlaceMultiLimitOrderIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int MARKET_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;
    public static final int REFERENCE_PRICE_OFFSET = 45;

    public static PlaceMultiLimitOrderIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var market = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixMarketAccountArgs.read(_data, i);
      i += accountArgs.l();
      final ReferencePriceArgs referencePrice;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        referencePrice = null;
        ++i;
      } else {
        ++i;
        referencePrice = ReferencePriceArgs.read(_data, i);
        i += referencePrice.l();
      }
      final var packet = MultipleOrderPacket.read(_data, i);
      return new PlaceMultiLimitOrderIxData(discriminator,
                                            market,
                                            accountArgs,
                                            referencePrice,
                                            packet);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      market.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      i += SerDeUtil.writeOptional(1, referencePrice, _data, i);
      i += packet.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + 32 + accountArgs.l() + (referencePrice == null ? 1 : (1 + referencePrice.l())) + packet.l();
    }
  }

  public static final Discriminator PLACE_POST_ONLY_ORDER_DISCRIMINATOR = toDiscriminator(253, 171, 187, 207, 158, 181, 93, 176);

  public static List<AccountMeta> placePostOnlyOrderKeys(final SolanaAccounts solanaAccounts,
                                                         final PublicKey glamStateKey,
                                                         final PublicKey glamVaultKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey integrationAuthorityKey,
                                                         final PublicKey cpiProgramKey,
                                                         final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction placePostOnlyOrder(final AccountMeta invokedExtPhoenixProgramMeta,
                                               final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PublicKey market,
                                               final PhoenixMarketAccountArgs accountArgs,
                                               final ReferencePriceArgs referencePrice,
                                               final PostOnlyOrderPacket packet) {
    final var keys = placePostOnlyOrderKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return placePostOnlyOrder(
      invokedExtPhoenixProgramMeta,
      keys,
      market,
      accountArgs,
      referencePrice,
      packet
    );
  }

  public static Instruction placePostOnlyOrder(final AccountMeta invokedExtPhoenixProgramMeta,
                                               final List<AccountMeta> keys,
                                               final PublicKey market,
                                               final PhoenixMarketAccountArgs accountArgs,
                                               final ReferencePriceArgs referencePrice,
                                               final PostOnlyOrderPacket packet) {
    final byte[] _data = new byte[
    40 + accountArgs.l()
    + (referencePrice == null ? 1 : (1 + referencePrice.l())) + packet.l()
    ];
    int i = PLACE_POST_ONLY_ORDER_DISCRIMINATOR.write(_data, 0);
    market.write(_data, i);
    i += 32;
    i += accountArgs.write(_data, i);
    i += SerDeUtil.writeOptional(1, referencePrice, _data, i);
    packet.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record PlacePostOnlyOrderIxData(Discriminator discriminator,
                                         PublicKey market,
                                         PhoenixMarketAccountArgs accountArgs,
                                         ReferencePriceArgs referencePrice,
                                         PostOnlyOrderPacket packet) implements SerDe {  

    public static PlacePostOnlyOrderIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int MARKET_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;
    public static final int REFERENCE_PRICE_OFFSET = 45;

    public static PlacePostOnlyOrderIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var market = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixMarketAccountArgs.read(_data, i);
      i += accountArgs.l();
      final ReferencePriceArgs referencePrice;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        referencePrice = null;
        ++i;
      } else {
        ++i;
        referencePrice = ReferencePriceArgs.read(_data, i);
        i += referencePrice.l();
      }
      final var packet = PostOnlyOrderPacket.read(_data, i);
      return new PlacePostOnlyOrderIxData(discriminator,
                                          market,
                                          accountArgs,
                                          referencePrice,
                                          packet);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      market.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      i += SerDeUtil.writeOptional(1, referencePrice, _data, i);
      i += packet.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + 32 + accountArgs.l() + (referencePrice == null ? 1 : (1 + referencePrice.l())) + packet.l();
    }
  }

  public static final Discriminator SET_EMBER_POLICY_DISCRIMINATOR = toDiscriminator(141, 246, 28, 33, 39, 88, 193, 181);

  public static List<AccountMeta> setEmberPolicyKeys(final PublicKey glamStateKey,
                                                     final PublicKey glamSignerKey,
                                                     final PublicKey glamProtocolProgramKey,
                                                     final PublicKey usdcMintKey,
                                                     final PublicKey canonicalCollateralMintKey,
                                                     final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(glamProtocolProgramKey),
      createRead(usdcMintKey),
      createRead(canonicalCollateralMintKey),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction setEmberPolicy(final AccountMeta invokedExtPhoenixProgramMeta,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamSignerKey,
                                           final PublicKey glamProtocolProgramKey,
                                           final PublicKey usdcMintKey,
                                           final PublicKey canonicalCollateralMintKey,
                                           final PublicKey tokenProgramKey,
                                           final EmberPolicy policy) {
    final var keys = setEmberPolicyKeys(
      glamStateKey,
      glamSignerKey,
      glamProtocolProgramKey,
      usdcMintKey,
      canonicalCollateralMintKey,
      tokenProgramKey
    );
    return setEmberPolicy(invokedExtPhoenixProgramMeta, keys, policy);
  }

  public static Instruction setEmberPolicy(final AccountMeta invokedExtPhoenixProgramMeta,
                                           final List<AccountMeta> keys,
                                           final EmberPolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_EMBER_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record SetEmberPolicyIxData(Discriminator discriminator, EmberPolicy policy) implements SerDe {  

    public static SetEmberPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int POLICY_OFFSET = 8;

    public static SetEmberPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = EmberPolicy.read(_data, i);
      return new SetEmberPolicyIxData(discriminator, policy);
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

  public static final Discriminator SET_PHOENIX_POLICY_DISCRIMINATOR = toDiscriminator(57, 235, 161, 196, 65, 41, 80, 85);

  public static List<AccountMeta> setPhoenixPolicyKeys(final PublicKey glamStateKey,
                                                       final PublicKey glamSignerKey,
                                                       final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(glamProtocolProgramKey)
    );
  }

  public static Instruction setPhoenixPolicy(final AccountMeta invokedExtPhoenixProgramMeta,
                                             final PublicKey glamStateKey,
                                             final PublicKey glamSignerKey,
                                             final PublicKey glamProtocolProgramKey,
                                             final PhoenixPolicy policy) {
    final var keys = setPhoenixPolicyKeys(
      glamStateKey,
      glamSignerKey,
      glamProtocolProgramKey
    );
    return setPhoenixPolicy(invokedExtPhoenixProgramMeta, keys, policy);
  }

  public static Instruction setPhoenixPolicy(final AccountMeta invokedExtPhoenixProgramMeta,
                                             final List<AccountMeta> keys,
                                             final PhoenixPolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_PHOENIX_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record SetPhoenixPolicyIxData(Discriminator discriminator, PhoenixPolicy policy) implements SerDe {  

    public static SetPhoenixPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int POLICY_OFFSET = 8;

    public static SetPhoenixPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = PhoenixPolicy.read(_data, i);
      return new SetPhoenixPolicyIxData(discriminator, policy);
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

  public static final Discriminator SET_TRADER_CAPABILITIES_DISCRIMINATOR = toDiscriminator(62, 229, 116, 154, 147, 69, 49, 46);

  public static List<AccountMeta> setTraderCapabilitiesKeys(final SolanaAccounts solanaAccounts,
                                                            final PublicKey glamStateKey,
                                                            final PublicKey glamVaultKey,
                                                            final PublicKey glamSignerKey,
                                                            final PublicKey integrationAuthorityKey,
                                                            final PublicKey cpiProgramKey,
                                                            final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction setTraderCapabilities(final AccountMeta invokedExtPhoenixProgramMeta,
                                                  final SolanaAccounts solanaAccounts,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamVaultKey,
                                                  final PublicKey glamSignerKey,
                                                  final PublicKey integrationAuthorityKey,
                                                  final PublicKey cpiProgramKey,
                                                  final PublicKey glamProtocolProgramKey,
                                                  final PhoenixAccountWindowArgs accountArgs,
                                                  final SetTraderCapabilitiesArgs args) {
    final var keys = setTraderCapabilitiesKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return setTraderCapabilities(invokedExtPhoenixProgramMeta, keys, accountArgs, args);
  }

  public static Instruction setTraderCapabilities(final AccountMeta invokedExtPhoenixProgramMeta,
                                                  final List<AccountMeta> keys,
                                                  final PhoenixAccountWindowArgs accountArgs,
                                                  final SetTraderCapabilitiesArgs args) {
    final byte[] _data = new byte[8 + accountArgs.l() + args.l()];
    int i = SET_TRADER_CAPABILITIES_DISCRIMINATOR.write(_data, 0);
    i += accountArgs.write(_data, i);
    args.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record SetTraderCapabilitiesIxData(Discriminator discriminator, PhoenixAccountWindowArgs accountArgs, SetTraderCapabilitiesArgs args) implements SerDe {  

    public static SetTraderCapabilitiesIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int ACCOUNT_ARGS_OFFSET = 8;
    public static final int ARGS_OFFSET = 12;

    public static SetTraderCapabilitiesIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var accountArgs = PhoenixAccountWindowArgs.read(_data, i);
      i += accountArgs.l();
      final var args = SetTraderCapabilitiesArgs.read(_data, i);
      return new SetTraderCapabilitiesIxData(discriminator, accountArgs, args);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += accountArgs.write(_data, i);
      i += args.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + accountArgs.l() + args.l();
    }
  }

  public static final Discriminator SYNC_PARENT_TO_CHILD_DISCRIMINATOR = toDiscriminator(175, 137, 217, 11, 235, 39, 150, 19);

  public static List<AccountMeta> syncParentToChildKeys(final SolanaAccounts solanaAccounts,
                                                        final PublicKey glamStateKey,
                                                        final PublicKey glamVaultKey,
                                                        final PublicKey glamSignerKey,
                                                        final PublicKey integrationAuthorityKey,
                                                        final PublicKey cpiProgramKey,
                                                        final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction syncParentToChild(final AccountMeta invokedExtPhoenixProgramMeta,
                                              final SolanaAccounts solanaAccounts,
                                              final PublicKey glamStateKey,
                                              final PublicKey glamVaultKey,
                                              final PublicKey glamSignerKey,
                                              final PublicKey integrationAuthorityKey,
                                              final PublicKey cpiProgramKey,
                                              final PublicKey glamProtocolProgramKey,
                                              final PhoenixTransferAccountArgs accountArgs) {
    final var keys = syncParentToChildKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return syncParentToChild(invokedExtPhoenixProgramMeta, keys, accountArgs);
  }

  public static Instruction syncParentToChild(final AccountMeta invokedExtPhoenixProgramMeta,
                                              final List<AccountMeta> keys,
                                              final PhoenixTransferAccountArgs accountArgs) {
    final byte[] _data = new byte[8 + accountArgs.l()];
    int i = SYNC_PARENT_TO_CHILD_DISCRIMINATOR.write(_data, 0);
    accountArgs.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record SyncParentToChildIxData(Discriminator discriminator, PhoenixTransferAccountArgs accountArgs) implements SerDe {  

    public static SyncParentToChildIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 14;

    public static final int ACCOUNT_ARGS_OFFSET = 8;

    public static SyncParentToChildIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var accountArgs = PhoenixTransferAccountArgs.read(_data, i);
      return new SyncParentToChildIxData(discriminator, accountArgs);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += accountArgs.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator SYNC_TRADER_CAPABILITIES_DISCRIMINATOR = toDiscriminator(154, 64, 87, 130, 130, 34, 138, 0);

  public static List<AccountMeta> syncTraderCapabilitiesKeys(final SolanaAccounts solanaAccounts,
                                                             final PublicKey glamStateKey,
                                                             final PublicKey glamVaultKey,
                                                             final PublicKey glamSignerKey,
                                                             final PublicKey integrationAuthorityKey,
                                                             final PublicKey cpiProgramKey,
                                                             final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction syncTraderCapabilities(final AccountMeta invokedExtPhoenixProgramMeta,
                                                   final SolanaAccounts solanaAccounts,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamVaultKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey integrationAuthorityKey,
                                                   final PublicKey cpiProgramKey,
                                                   final PublicKey glamProtocolProgramKey,
                                                   final PhoenixAccountWindowArgs accountArgs) {
    final var keys = syncTraderCapabilitiesKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return syncTraderCapabilities(invokedExtPhoenixProgramMeta, keys, accountArgs);
  }

  public static Instruction syncTraderCapabilities(final AccountMeta invokedExtPhoenixProgramMeta,
                                                   final List<AccountMeta> keys,
                                                   final PhoenixAccountWindowArgs accountArgs) {
    final byte[] _data = new byte[8 + accountArgs.l()];
    int i = SYNC_TRADER_CAPABILITIES_DISCRIMINATOR.write(_data, 0);
    accountArgs.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record SyncTraderCapabilitiesIxData(Discriminator discriminator, PhoenixAccountWindowArgs accountArgs) implements SerDe {  

    public static SyncTraderCapabilitiesIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 12;

    public static final int ACCOUNT_ARGS_OFFSET = 8;

    public static SyncTraderCapabilitiesIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var accountArgs = PhoenixAccountWindowArgs.read(_data, i);
      return new SyncTraderCapabilitiesIxData(discriminator, accountArgs);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += accountArgs.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator TRANSFER_COLLATERAL_DISCRIMINATOR = toDiscriminator(157, 163, 63, 27, 242, 72, 251, 97);

  public static List<AccountMeta> transferCollateralKeys(final SolanaAccounts solanaAccounts,
                                                         final PublicKey glamStateKey,
                                                         final PublicKey glamVaultKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey integrationAuthorityKey,
                                                         final PublicKey cpiProgramKey,
                                                         final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction transferCollateral(final AccountMeta invokedExtPhoenixProgramMeta,
                                               final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PhoenixTransferAccountArgs accountArgs,
                                               final long amount) {
    final var keys = transferCollateralKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return transferCollateral(invokedExtPhoenixProgramMeta, keys, accountArgs, amount);
  }

  public static Instruction transferCollateral(final AccountMeta invokedExtPhoenixProgramMeta,
                                               final List<AccountMeta> keys,
                                               final PhoenixTransferAccountArgs accountArgs,
                                               final long amount) {
    final byte[] _data = new byte[16 + accountArgs.l()];
    int i = TRANSFER_COLLATERAL_DISCRIMINATOR.write(_data, 0);
    i += accountArgs.write(_data, i);
    putInt64LE(_data, i, amount);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record TransferCollateralIxData(Discriminator discriminator, PhoenixTransferAccountArgs accountArgs, long amount) implements SerDe {  

    public static TransferCollateralIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 22;

    public static final int ACCOUNT_ARGS_OFFSET = 8;
    public static final int AMOUNT_OFFSET = 14;

    public static TransferCollateralIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var accountArgs = PhoenixTransferAccountArgs.read(_data, i);
      i += accountArgs.l();
      final var amount = getInt64LE(_data, i);
      return new TransferCollateralIxData(discriminator, accountArgs, amount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += accountArgs.write(_data, i);
      putInt64LE(_data, i, amount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator TRANSFER_COLLATERAL_CHILD_TO_PARENT_DISCRIMINATOR = toDiscriminator(51, 100, 177, 115, 139, 135, 247, 139);

  public static List<AccountMeta> transferCollateralChildToParentKeys(final SolanaAccounts solanaAccounts,
                                                                      final PublicKey glamStateKey,
                                                                      final PublicKey glamVaultKey,
                                                                      final PublicKey glamSignerKey,
                                                                      final PublicKey integrationAuthorityKey,
                                                                      final PublicKey cpiProgramKey,
                                                                      final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction transferCollateralChildToParent(final AccountMeta invokedExtPhoenixProgramMeta,
                                                            final SolanaAccounts solanaAccounts,
                                                            final PublicKey glamStateKey,
                                                            final PublicKey glamVaultKey,
                                                            final PublicKey glamSignerKey,
                                                            final PublicKey integrationAuthorityKey,
                                                            final PublicKey cpiProgramKey,
                                                            final PublicKey glamProtocolProgramKey,
                                                            final PhoenixTransferAccountArgs accountArgs) {
    final var keys = transferCollateralChildToParentKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return transferCollateralChildToParent(invokedExtPhoenixProgramMeta, keys, accountArgs);
  }

  public static Instruction transferCollateralChildToParent(final AccountMeta invokedExtPhoenixProgramMeta,
                                                            final List<AccountMeta> keys,
                                                            final PhoenixTransferAccountArgs accountArgs) {
    final byte[] _data = new byte[8 + accountArgs.l()];
    int i = TRANSFER_COLLATERAL_CHILD_TO_PARENT_DISCRIMINATOR.write(_data, 0);
    accountArgs.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record TransferCollateralChildToParentIxData(Discriminator discriminator, PhoenixTransferAccountArgs accountArgs) implements SerDe {  

    public static TransferCollateralChildToParentIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 14;

    public static final int ACCOUNT_ARGS_OFFSET = 8;

    public static TransferCollateralChildToParentIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var accountArgs = PhoenixTransferAccountArgs.read(_data, i);
      return new TransferCollateralChildToParentIxData(discriminator, accountArgs);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += accountArgs.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator UPDATE_TRADER_STATE_DISCRIMINATOR = toDiscriminator(249, 139, 82, 44, 126, 66, 133, 220);

  public static List<AccountMeta> updateTraderStateKeys(final SolanaAccounts solanaAccounts,
                                                        final PublicKey glamStateKey,
                                                        final PublicKey glamVaultKey,
                                                        final PublicKey glamSignerKey,
                                                        final PublicKey integrationAuthorityKey,
                                                        final PublicKey cpiProgramKey,
                                                        final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction updateTraderState(final AccountMeta invokedExtPhoenixProgramMeta,
                                              final SolanaAccounts solanaAccounts,
                                              final PublicKey glamStateKey,
                                              final PublicKey glamVaultKey,
                                              final PublicKey glamSignerKey,
                                              final PublicKey integrationAuthorityKey,
                                              final PublicKey cpiProgramKey,
                                              final PublicKey glamProtocolProgramKey,
                                              final PhoenixAccountWindowArgs accountArgs) {
    final var keys = updateTraderStateKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return updateTraderState(invokedExtPhoenixProgramMeta, keys, accountArgs);
  }

  public static Instruction updateTraderState(final AccountMeta invokedExtPhoenixProgramMeta,
                                              final List<AccountMeta> keys,
                                              final PhoenixAccountWindowArgs accountArgs) {
    final byte[] _data = new byte[8 + accountArgs.l()];
    int i = UPDATE_TRADER_STATE_DISCRIMINATOR.write(_data, 0);
    accountArgs.write(_data, i);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record UpdateTraderStateIxData(Discriminator discriminator, PhoenixAccountWindowArgs accountArgs) implements SerDe {  

    public static UpdateTraderStateIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 12;

    public static final int ACCOUNT_ARGS_OFFSET = 8;

    public static UpdateTraderStateIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var accountArgs = PhoenixAccountWindowArgs.read(_data, i);
      return new UpdateTraderStateIxData(discriminator, accountArgs);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += accountArgs.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator WITHDRAW_FUNDS_DISCRIMINATOR = toDiscriminator(241, 36, 29, 111, 208, 31, 104, 217);

  public static List<AccountMeta> withdrawFundsKeys(final SolanaAccounts solanaAccounts,
                                                    final PublicKey glamStateKey,
                                                    final PublicKey glamVaultKey,
                                                    final PublicKey glamSignerKey,
                                                    final PublicKey integrationAuthorityKey,
                                                    final PublicKey cpiProgramKey,
                                                    final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction withdrawFunds(final AccountMeta invokedExtPhoenixProgramMeta,
                                          final SolanaAccounts solanaAccounts,
                                          final PublicKey glamStateKey,
                                          final PublicKey glamVaultKey,
                                          final PublicKey glamSignerKey,
                                          final PublicKey integrationAuthorityKey,
                                          final PublicKey cpiProgramKey,
                                          final PublicKey glamProtocolProgramKey,
                                          final PublicKey mint,
                                          final PhoenixAccountWindowArgs accountArgs,
                                          final long amount) {
    final var keys = withdrawFundsKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey
    );
    return withdrawFunds(
      invokedExtPhoenixProgramMeta,
      keys,
      mint,
      accountArgs,
      amount
    );
  }

  public static Instruction withdrawFunds(final AccountMeta invokedExtPhoenixProgramMeta,
                                          final List<AccountMeta> keys,
                                          final PublicKey mint,
                                          final PhoenixAccountWindowArgs accountArgs,
                                          final long amount) {
    final byte[] _data = new byte[48 + accountArgs.l()];
    int i = WITHDRAW_FUNDS_DISCRIMINATOR.write(_data, 0);
    mint.write(_data, i);
    i += 32;
    i += accountArgs.write(_data, i);
    putInt64LE(_data, i, amount);

    return Instruction.createInstruction(invokedExtPhoenixProgramMeta, keys, _data);
  }

  public record WithdrawFundsIxData(Discriminator discriminator,
                                    PublicKey mint,
                                    PhoenixAccountWindowArgs accountArgs,
                                    long amount) implements SerDe {  

    public static WithdrawFundsIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 52;

    public static final int MINT_OFFSET = 8;
    public static final int ACCOUNT_ARGS_OFFSET = 40;
    public static final int AMOUNT_OFFSET = 44;

    public static WithdrawFundsIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var mint = readPubKey(_data, i);
      i += 32;
      final var accountArgs = PhoenixAccountWindowArgs.read(_data, i);
      i += accountArgs.l();
      final var amount = getInt64LE(_data, i);
      return new WithdrawFundsIxData(discriminator, mint, accountArgs, amount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      mint.write(_data, i);
      i += 32;
      i += accountArgs.write(_data, i);
      putInt64LE(_data, i, amount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  private ExtPhoenixProgram() {
  }
}
