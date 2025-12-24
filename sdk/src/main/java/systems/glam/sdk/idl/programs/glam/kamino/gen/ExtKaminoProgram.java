package systems.glam.sdk.idl.programs.glam.kamino.gen;

import java.math.BigInteger;

import java.util.List;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.kamino.lend.gen.types.InitObligationArgs;

import systems.glam.sdk.idl.programs.glam.kamino.gen.types.LendingPolicy;
import systems.glam.sdk.idl.programs.glam.kamino.gen.types.VaultsPolicy;

import static java.util.Objects.requireNonNullElse;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.accounts.meta.AccountMeta.createRead;
import static software.sava.core.accounts.meta.AccountMeta.createWritableSigner;
import static software.sava.core.accounts.meta.AccountMeta.createWrite;
import static software.sava.core.encoding.ByteUtil.getInt128LE;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt128LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public final class ExtKaminoProgram {

  public static final Discriminator FARMS_HARVEST_REWARD_DISCRIMINATOR = toDiscriminator(244, 248, 124, 210, 194, 52, 75, 152);

  public static List<AccountMeta> farmsHarvestRewardKeys(final AccountMeta invokedExtKaminoProgramMeta,
                                                         final SolanaAccounts solanaAccounts,
                                                         final PublicKey glamStateKey,
                                                         final PublicKey glamVaultKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey integrationAuthorityKey,
                                                         final PublicKey cpiProgramKey,
                                                         final PublicKey glamProtocolProgramKey,
                                                         final PublicKey userStateKey,
                                                         final PublicKey farmStateKey,
                                                         final PublicKey globalConfigKey,
                                                         final PublicKey rewardMintKey,
                                                         final PublicKey userRewardAtaKey,
                                                         final PublicKey rewardsVaultKey,
                                                         final PublicKey rewardsTreasuryVaultKey,
                                                         final PublicKey farmVaultsAuthorityKey,
                                                         final PublicKey scopePricesKey,
                                                         final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userStateKey),
      createWrite(farmStateKey),
      createRead(globalConfigKey),
      createRead(rewardMintKey),
      createWrite(userRewardAtaKey),
      createWrite(rewardsVaultKey),
      createWrite(rewardsTreasuryVaultKey),
      createRead(farmVaultsAuthorityKey),
      createRead(requireNonNullElse(scopePricesKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction farmsHarvestReward(final AccountMeta invokedExtKaminoProgramMeta,
                                               final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PublicKey userStateKey,
                                               final PublicKey farmStateKey,
                                               final PublicKey globalConfigKey,
                                               final PublicKey rewardMintKey,
                                               final PublicKey userRewardAtaKey,
                                               final PublicKey rewardsVaultKey,
                                               final PublicKey rewardsTreasuryVaultKey,
                                               final PublicKey farmVaultsAuthorityKey,
                                               final PublicKey scopePricesKey,
                                               final PublicKey tokenProgramKey,
                                               final long rewardIndex) {
    final var keys = farmsHarvestRewardKeys(
      invokedExtKaminoProgramMeta,
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userStateKey,
      farmStateKey,
      globalConfigKey,
      rewardMintKey,
      userRewardAtaKey,
      rewardsVaultKey,
      rewardsTreasuryVaultKey,
      farmVaultsAuthorityKey,
      scopePricesKey,
      tokenProgramKey
    );
    return farmsHarvestReward(invokedExtKaminoProgramMeta, keys, rewardIndex);
  }

  public static Instruction farmsHarvestReward(final AccountMeta invokedExtKaminoProgramMeta,
                                               final List<AccountMeta> keys,
                                               final long rewardIndex) {
    final byte[] _data = new byte[16];
    int i = FARMS_HARVEST_REWARD_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, rewardIndex);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record FarmsHarvestRewardIxData(Discriminator discriminator, long rewardIndex) implements SerDe {  

    public static FarmsHarvestRewardIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static FarmsHarvestRewardIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var rewardIndex = getInt64LE(_data, i);
      return new FarmsHarvestRewardIxData(discriminator, rewardIndex);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, rewardIndex);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator FARMS_INITIALIZE_USER_DISCRIMINATOR = toDiscriminator(188, 148, 82, 37, 44, 46, 162, 34);

  public static List<AccountMeta> farmsInitializeUserKeys(final SolanaAccounts solanaAccounts,
                                                          final PublicKey glamStateKey,
                                                          final PublicKey glamVaultKey,
                                                          final PublicKey glamSignerKey,
                                                          final PublicKey integrationAuthorityKey,
                                                          final PublicKey cpiProgramKey,
                                                          final PublicKey glamProtocolProgramKey,
                                                          final PublicKey userStateKey,
                                                          final PublicKey farmStateKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userStateKey),
      createWrite(farmStateKey),
      createRead(solanaAccounts.rentSysVar())
    );
  }

  public static Instruction farmsInitializeUser(final AccountMeta invokedExtKaminoProgramMeta,
                                                final SolanaAccounts solanaAccounts,
                                                final PublicKey glamStateKey,
                                                final PublicKey glamVaultKey,
                                                final PublicKey glamSignerKey,
                                                final PublicKey integrationAuthorityKey,
                                                final PublicKey cpiProgramKey,
                                                final PublicKey glamProtocolProgramKey,
                                                final PublicKey userStateKey,
                                                final PublicKey farmStateKey) {
    final var keys = farmsInitializeUserKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userStateKey,
      farmStateKey
    );
    return farmsInitializeUser(invokedExtKaminoProgramMeta, keys);
  }

  public static Instruction farmsInitializeUser(final AccountMeta invokedExtKaminoProgramMeta,
                                                final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, FARMS_INITIALIZE_USER_DISCRIMINATOR);
  }

  public static final Discriminator FARMS_STAKE_DISCRIMINATOR = toDiscriminator(224, 105, 208, 179, 98, 200, 213, 238);

  public static List<AccountMeta> farmsStakeKeys(final AccountMeta invokedExtKaminoProgramMeta,
                                                 final SolanaAccounts solanaAccounts,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey glamSignerKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PublicKey cpiProgramKey,
                                                 final PublicKey glamProtocolProgramKey,
                                                 final PublicKey userStateKey,
                                                 final PublicKey farmStateKey,
                                                 final PublicKey farmVaultKey,
                                                 final PublicKey userAtaKey,
                                                 final PublicKey tokenMintKey,
                                                 final PublicKey scopePricesKey,
                                                 final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userStateKey),
      createWrite(farmStateKey),
      createWrite(farmVaultKey),
      createWrite(userAtaKey),
      createRead(tokenMintKey),
      createRead(requireNonNullElse(scopePricesKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction farmsStake(final AccountMeta invokedExtKaminoProgramMeta,
                                       final SolanaAccounts solanaAccounts,
                                       final PublicKey glamStateKey,
                                       final PublicKey glamVaultKey,
                                       final PublicKey glamSignerKey,
                                       final PublicKey integrationAuthorityKey,
                                       final PublicKey cpiProgramKey,
                                       final PublicKey glamProtocolProgramKey,
                                       final PublicKey userStateKey,
                                       final PublicKey farmStateKey,
                                       final PublicKey farmVaultKey,
                                       final PublicKey userAtaKey,
                                       final PublicKey tokenMintKey,
                                       final PublicKey scopePricesKey,
                                       final PublicKey tokenProgramKey,
                                       final long amount) {
    final var keys = farmsStakeKeys(
      invokedExtKaminoProgramMeta,
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userStateKey,
      farmStateKey,
      farmVaultKey,
      userAtaKey,
      tokenMintKey,
      scopePricesKey,
      tokenProgramKey
    );
    return farmsStake(invokedExtKaminoProgramMeta, keys, amount);
  }

  public static Instruction farmsStake(final AccountMeta invokedExtKaminoProgramMeta,
                                       final List<AccountMeta> keys,
                                       final long amount) {
    final byte[] _data = new byte[16];
    int i = FARMS_STAKE_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, amount);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record FarmsStakeIxData(Discriminator discriminator, long amount) implements SerDe {  

    public static FarmsStakeIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static FarmsStakeIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var amount = getInt64LE(_data, i);
      return new FarmsStakeIxData(discriminator, amount);
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

  public static final Discriminator FARMS_UNSTAKE_DISCRIMINATOR = toDiscriminator(180, 131, 50, 144, 26, 242, 175, 242);

  public static List<AccountMeta> farmsUnstakeKeys(final AccountMeta invokedExtKaminoProgramMeta,
                                                   final SolanaAccounts solanaAccounts,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamVaultKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey integrationAuthorityKey,
                                                   final PublicKey cpiProgramKey,
                                                   final PublicKey glamProtocolProgramKey,
                                                   final PublicKey userStateKey,
                                                   final PublicKey farmStateKey,
                                                   final PublicKey scopePricesKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userStateKey),
      createWrite(farmStateKey),
      createRead(requireNonNullElse(scopePricesKey, invokedExtKaminoProgramMeta.publicKey()))
    );
  }

  public static Instruction farmsUnstake(final AccountMeta invokedExtKaminoProgramMeta,
                                         final SolanaAccounts solanaAccounts,
                                         final PublicKey glamStateKey,
                                         final PublicKey glamVaultKey,
                                         final PublicKey glamSignerKey,
                                         final PublicKey integrationAuthorityKey,
                                         final PublicKey cpiProgramKey,
                                         final PublicKey glamProtocolProgramKey,
                                         final PublicKey userStateKey,
                                         final PublicKey farmStateKey,
                                         final PublicKey scopePricesKey,
                                         final BigInteger amount) {
    final var keys = farmsUnstakeKeys(
      invokedExtKaminoProgramMeta,
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userStateKey,
      farmStateKey,
      scopePricesKey
    );
    return farmsUnstake(invokedExtKaminoProgramMeta, keys, amount);
  }

  public static Instruction farmsUnstake(final AccountMeta invokedExtKaminoProgramMeta,
                                         final List<AccountMeta> keys,
                                         final BigInteger amount) {
    final byte[] _data = new byte[24];
    int i = FARMS_UNSTAKE_DISCRIMINATOR.write(_data, 0);
    putInt128LE(_data, i, amount);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record FarmsUnstakeIxData(Discriminator discriminator, BigInteger amount) implements SerDe {  

    public static FarmsUnstakeIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 24;

    public static FarmsUnstakeIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var amount = getInt128LE(_data, i);
      return new FarmsUnstakeIxData(discriminator, amount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt128LE(_data, i, amount);
      i += 16;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator FARMS_WITHDRAW_UNSTAKED_DEPOSITS_DISCRIMINATOR = toDiscriminator(107, 97, 50, 15, 211, 245, 52, 96);

  public static List<AccountMeta> farmsWithdrawUnstakedDepositsKeys(final SolanaAccounts solanaAccounts,
                                                                    final PublicKey glamStateKey,
                                                                    final PublicKey glamVaultKey,
                                                                    final PublicKey glamSignerKey,
                                                                    final PublicKey integrationAuthorityKey,
                                                                    final PublicKey cpiProgramKey,
                                                                    final PublicKey glamProtocolProgramKey,
                                                                    final PublicKey userStateKey,
                                                                    final PublicKey farmStateKey,
                                                                    final PublicKey userAtaKey,
                                                                    final PublicKey farmVaultKey,
                                                                    final PublicKey farmVaultsAuthorityKey,
                                                                    final PublicKey tokenProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(userStateKey),
      createWrite(farmStateKey),
      createWrite(userAtaKey),
      createWrite(farmVaultKey),
      createRead(farmVaultsAuthorityKey),
      createRead(tokenProgramKey)
    );
  }

  public static Instruction farmsWithdrawUnstakedDeposits(final AccountMeta invokedExtKaminoProgramMeta,
                                                          final SolanaAccounts solanaAccounts,
                                                          final PublicKey glamStateKey,
                                                          final PublicKey glamVaultKey,
                                                          final PublicKey glamSignerKey,
                                                          final PublicKey integrationAuthorityKey,
                                                          final PublicKey cpiProgramKey,
                                                          final PublicKey glamProtocolProgramKey,
                                                          final PublicKey userStateKey,
                                                          final PublicKey farmStateKey,
                                                          final PublicKey userAtaKey,
                                                          final PublicKey farmVaultKey,
                                                          final PublicKey farmVaultsAuthorityKey,
                                                          final PublicKey tokenProgramKey) {
    final var keys = farmsWithdrawUnstakedDepositsKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      userStateKey,
      farmStateKey,
      userAtaKey,
      farmVaultKey,
      farmVaultsAuthorityKey,
      tokenProgramKey
    );
    return farmsWithdrawUnstakedDeposits(invokedExtKaminoProgramMeta, keys);
  }

  public static Instruction farmsWithdrawUnstakedDeposits(final AccountMeta invokedExtKaminoProgramMeta,
                                                          final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, FARMS_WITHDRAW_UNSTAKED_DEPOSITS_DISCRIMINATOR);
  }

  public static final Discriminator LENDING_BORROW_OBLIGATION_LIQUIDITY_V_2_DISCRIMINATOR = toDiscriminator(149, 226, 84, 157, 124, 178, 35, 122);

  public static List<AccountMeta> lendingBorrowObligationLiquidityV2Keys(final AccountMeta invokedExtKaminoProgramMeta,
                                                                         final SolanaAccounts solanaAccounts,
                                                                         final PublicKey glamStateKey,
                                                                         final PublicKey glamVaultKey,
                                                                         final PublicKey glamSignerKey,
                                                                         final PublicKey integrationAuthorityKey,
                                                                         final PublicKey cpiProgramKey,
                                                                         final PublicKey glamProtocolProgramKey,
                                                                         final PublicKey obligationKey,
                                                                         final PublicKey lendingMarketKey,
                                                                         final PublicKey lendingMarketAuthorityKey,
                                                                         final PublicKey borrowReserveKey,
                                                                         final PublicKey borrowReserveLiquidityMintKey,
                                                                         final PublicKey reserveSourceLiquidityKey,
                                                                         final PublicKey borrowReserveLiquidityFeeReceiverKey,
                                                                         final PublicKey userDestinationLiquidityKey,
                                                                         final PublicKey referrerTokenStateKey,
                                                                         final PublicKey tokenProgramKey,
                                                                         final PublicKey instructionSysvarAccountKey,
                                                                         final PublicKey obligationFarmUserStateKey,
                                                                         final PublicKey reserveFarmStateKey,
                                                                         final PublicKey farmsProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(obligationKey),
      createRead(lendingMarketKey),
      createRead(lendingMarketAuthorityKey),
      createWrite(borrowReserveKey),
      createRead(borrowReserveLiquidityMintKey),
      createWrite(reserveSourceLiquidityKey),
      createWrite(borrowReserveLiquidityFeeReceiverKey),
      createWrite(userDestinationLiquidityKey),
      createWrite(requireNonNullElse(referrerTokenStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(tokenProgramKey),
      createRead(instructionSysvarAccountKey),
      createWrite(requireNonNullElse(obligationFarmUserStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createWrite(requireNonNullElse(reserveFarmStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(farmsProgramKey)
    );
  }

  public static Instruction lendingBorrowObligationLiquidityV2(final AccountMeta invokedExtKaminoProgramMeta,
                                                               final SolanaAccounts solanaAccounts,
                                                               final PublicKey glamStateKey,
                                                               final PublicKey glamVaultKey,
                                                               final PublicKey glamSignerKey,
                                                               final PublicKey integrationAuthorityKey,
                                                               final PublicKey cpiProgramKey,
                                                               final PublicKey glamProtocolProgramKey,
                                                               final PublicKey obligationKey,
                                                               final PublicKey lendingMarketKey,
                                                               final PublicKey lendingMarketAuthorityKey,
                                                               final PublicKey borrowReserveKey,
                                                               final PublicKey borrowReserveLiquidityMintKey,
                                                               final PublicKey reserveSourceLiquidityKey,
                                                               final PublicKey borrowReserveLiquidityFeeReceiverKey,
                                                               final PublicKey userDestinationLiquidityKey,
                                                               final PublicKey referrerTokenStateKey,
                                                               final PublicKey tokenProgramKey,
                                                               final PublicKey instructionSysvarAccountKey,
                                                               final PublicKey obligationFarmUserStateKey,
                                                               final PublicKey reserveFarmStateKey,
                                                               final PublicKey farmsProgramKey,
                                                               final long liquidityAmount) {
    final var keys = lendingBorrowObligationLiquidityV2Keys(
      invokedExtKaminoProgramMeta,
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      obligationKey,
      lendingMarketKey,
      lendingMarketAuthorityKey,
      borrowReserveKey,
      borrowReserveLiquidityMintKey,
      reserveSourceLiquidityKey,
      borrowReserveLiquidityFeeReceiverKey,
      userDestinationLiquidityKey,
      referrerTokenStateKey,
      tokenProgramKey,
      instructionSysvarAccountKey,
      obligationFarmUserStateKey,
      reserveFarmStateKey,
      farmsProgramKey
    );
    return lendingBorrowObligationLiquidityV2(invokedExtKaminoProgramMeta, keys, liquidityAmount);
  }

  public static Instruction lendingBorrowObligationLiquidityV2(final AccountMeta invokedExtKaminoProgramMeta,
                                                               final List<AccountMeta> keys,
                                                               final long liquidityAmount) {
    final byte[] _data = new byte[16];
    int i = LENDING_BORROW_OBLIGATION_LIQUIDITY_V_2_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, liquidityAmount);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record LendingBorrowObligationLiquidityV2IxData(Discriminator discriminator, long liquidityAmount) implements SerDe {  

    public static LendingBorrowObligationLiquidityV2IxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static LendingBorrowObligationLiquidityV2IxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var liquidityAmount = getInt64LE(_data, i);
      return new LendingBorrowObligationLiquidityV2IxData(discriminator, liquidityAmount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, liquidityAmount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator LENDING_DEPOSIT_RESERVE_LIQUIDITY_AND_OBLIGATION_COLLATERAL_V_2_DISCRIMINATOR = toDiscriminator(33, 146, 50, 121, 127, 94, 92, 192);

  public static List<AccountMeta> lendingDepositReserveLiquidityAndObligationCollateralV2Keys(final AccountMeta invokedExtKaminoProgramMeta,
                                                                                              final SolanaAccounts solanaAccounts,
                                                                                              final PublicKey glamStateKey,
                                                                                              final PublicKey glamVaultKey,
                                                                                              final PublicKey glamSignerKey,
                                                                                              final PublicKey integrationAuthorityKey,
                                                                                              final PublicKey cpiProgramKey,
                                                                                              final PublicKey glamProtocolProgramKey,
                                                                                              final PublicKey obligationKey,
                                                                                              final PublicKey lendingMarketKey,
                                                                                              final PublicKey lendingMarketAuthorityKey,
                                                                                              final PublicKey reserveKey,
                                                                                              final PublicKey reserveLiquidityMintKey,
                                                                                              final PublicKey reserveLiquiditySupplyKey,
                                                                                              final PublicKey reserveCollateralMintKey,
                                                                                              final PublicKey reserveDestinationDepositCollateralKey,
                                                                                              final PublicKey userSourceLiquidityKey,
                                                                                              final PublicKey placeholderUserDestinationCollateralKey,
                                                                                              final PublicKey collateralTokenProgramKey,
                                                                                              final PublicKey liquidityTokenProgramKey,
                                                                                              final PublicKey instructionSysvarAccountKey,
                                                                                              final PublicKey obligationFarmUserStateKey,
                                                                                              final PublicKey reserveFarmStateKey,
                                                                                              final PublicKey farmsProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(obligationKey),
      createRead(lendingMarketKey),
      createRead(lendingMarketAuthorityKey),
      createWrite(reserveKey),
      createRead(reserveLiquidityMintKey),
      createWrite(reserveLiquiditySupplyKey),
      createWrite(reserveCollateralMintKey),
      createWrite(reserveDestinationDepositCollateralKey),
      createWrite(userSourceLiquidityKey),
      createRead(requireNonNullElse(placeholderUserDestinationCollateralKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(collateralTokenProgramKey),
      createRead(liquidityTokenProgramKey),
      createRead(instructionSysvarAccountKey),
      createWrite(requireNonNullElse(obligationFarmUserStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createWrite(requireNonNullElse(reserveFarmStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(farmsProgramKey)
    );
  }

  public static Instruction lendingDepositReserveLiquidityAndObligationCollateralV2(final AccountMeta invokedExtKaminoProgramMeta,
                                                                                    final SolanaAccounts solanaAccounts,
                                                                                    final PublicKey glamStateKey,
                                                                                    final PublicKey glamVaultKey,
                                                                                    final PublicKey glamSignerKey,
                                                                                    final PublicKey integrationAuthorityKey,
                                                                                    final PublicKey cpiProgramKey,
                                                                                    final PublicKey glamProtocolProgramKey,
                                                                                    final PublicKey obligationKey,
                                                                                    final PublicKey lendingMarketKey,
                                                                                    final PublicKey lendingMarketAuthorityKey,
                                                                                    final PublicKey reserveKey,
                                                                                    final PublicKey reserveLiquidityMintKey,
                                                                                    final PublicKey reserveLiquiditySupplyKey,
                                                                                    final PublicKey reserveCollateralMintKey,
                                                                                    final PublicKey reserveDestinationDepositCollateralKey,
                                                                                    final PublicKey userSourceLiquidityKey,
                                                                                    final PublicKey placeholderUserDestinationCollateralKey,
                                                                                    final PublicKey collateralTokenProgramKey,
                                                                                    final PublicKey liquidityTokenProgramKey,
                                                                                    final PublicKey instructionSysvarAccountKey,
                                                                                    final PublicKey obligationFarmUserStateKey,
                                                                                    final PublicKey reserveFarmStateKey,
                                                                                    final PublicKey farmsProgramKey,
                                                                                    final long liquidityAmount) {
    final var keys = lendingDepositReserveLiquidityAndObligationCollateralV2Keys(
      invokedExtKaminoProgramMeta,
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      obligationKey,
      lendingMarketKey,
      lendingMarketAuthorityKey,
      reserveKey,
      reserveLiquidityMintKey,
      reserveLiquiditySupplyKey,
      reserveCollateralMintKey,
      reserveDestinationDepositCollateralKey,
      userSourceLiquidityKey,
      placeholderUserDestinationCollateralKey,
      collateralTokenProgramKey,
      liquidityTokenProgramKey,
      instructionSysvarAccountKey,
      obligationFarmUserStateKey,
      reserveFarmStateKey,
      farmsProgramKey
    );
    return lendingDepositReserveLiquidityAndObligationCollateralV2(invokedExtKaminoProgramMeta, keys, liquidityAmount);
  }

  public static Instruction lendingDepositReserveLiquidityAndObligationCollateralV2(final AccountMeta invokedExtKaminoProgramMeta,
                                                                                    final List<AccountMeta> keys,
                                                                                    final long liquidityAmount) {
    final byte[] _data = new byte[16];
    int i = LENDING_DEPOSIT_RESERVE_LIQUIDITY_AND_OBLIGATION_COLLATERAL_V_2_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, liquidityAmount);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record LendingDepositReserveLiquidityAndObligationCollateralV2IxData(Discriminator discriminator, long liquidityAmount) implements SerDe {  

    public static LendingDepositReserveLiquidityAndObligationCollateralV2IxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static LendingDepositReserveLiquidityAndObligationCollateralV2IxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var liquidityAmount = getInt64LE(_data, i);
      return new LendingDepositReserveLiquidityAndObligationCollateralV2IxData(discriminator, liquidityAmount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, liquidityAmount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator LENDING_INIT_OBLIGATION_DISCRIMINATOR = toDiscriminator(188, 161, 62, 142, 106, 232, 77, 135);

  public static List<AccountMeta> lendingInitObligationKeys(final SolanaAccounts solanaAccounts,
                                                            final PublicKey glamStateKey,
                                                            final PublicKey glamVaultKey,
                                                            final PublicKey glamSignerKey,
                                                            final PublicKey integrationAuthorityKey,
                                                            final PublicKey cpiProgramKey,
                                                            final PublicKey glamProtocolProgramKey,
                                                            final PublicKey feePayerKey,
                                                            final PublicKey obligationKey,
                                                            final PublicKey lendingMarketKey,
                                                            final PublicKey seed1AccountKey,
                                                            final PublicKey seed2AccountKey,
                                                            final PublicKey ownerUserMetadataKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createWritableSigner(feePayerKey),
      createWrite(obligationKey),
      createRead(lendingMarketKey),
      createRead(seed1AccountKey),
      createRead(seed2AccountKey),
      createRead(ownerUserMetadataKey),
      createRead(solanaAccounts.rentSysVar()),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction lendingInitObligation(final AccountMeta invokedExtKaminoProgramMeta,
                                                  final SolanaAccounts solanaAccounts,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamVaultKey,
                                                  final PublicKey glamSignerKey,
                                                  final PublicKey integrationAuthorityKey,
                                                  final PublicKey cpiProgramKey,
                                                  final PublicKey glamProtocolProgramKey,
                                                  final PublicKey feePayerKey,
                                                  final PublicKey obligationKey,
                                                  final PublicKey lendingMarketKey,
                                                  final PublicKey seed1AccountKey,
                                                  final PublicKey seed2AccountKey,
                                                  final PublicKey ownerUserMetadataKey,
                                                  final InitObligationArgs args) {
    final var keys = lendingInitObligationKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      feePayerKey,
      obligationKey,
      lendingMarketKey,
      seed1AccountKey,
      seed2AccountKey,
      ownerUserMetadataKey
    );
    return lendingInitObligation(invokedExtKaminoProgramMeta, keys, args);
  }

  public static Instruction lendingInitObligation(final AccountMeta invokedExtKaminoProgramMeta,
                                                  final List<AccountMeta> keys,
                                                  final InitObligationArgs args) {
    final byte[] _data = new byte[8 + args.l()];
    int i = LENDING_INIT_OBLIGATION_DISCRIMINATOR.write(_data, 0);
    args.write(_data, i);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record LendingInitObligationIxData(Discriminator discriminator, InitObligationArgs args) implements SerDe {  

    public static LendingInitObligationIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 10;

    public static LendingInitObligationIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var args = InitObligationArgs.read(_data, i);
      return new LendingInitObligationIxData(discriminator, args);
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

  public static final Discriminator LENDING_INIT_OBLIGATION_FARMS_FOR_RESERVE_DISCRIMINATOR = toDiscriminator(3, 234, 110, 39, 12, 147, 175, 185);

  public static List<AccountMeta> lendingInitObligationFarmsForReserveKeys(final SolanaAccounts solanaAccounts,
                                                                           final PublicKey glamStateKey,
                                                                           final PublicKey glamVaultKey,
                                                                           final PublicKey glamSignerKey,
                                                                           final PublicKey integrationAuthorityKey,
                                                                           final PublicKey cpiProgramKey,
                                                                           final PublicKey glamProtocolProgramKey,
                                                                           final PublicKey payerKey,
                                                                           final PublicKey obligationKey,
                                                                           final PublicKey lendingMarketAuthorityKey,
                                                                           final PublicKey reserveKey,
                                                                           final PublicKey reserveFarmStateKey,
                                                                           final PublicKey obligationFarmKey,
                                                                           final PublicKey lendingMarketKey,
                                                                           final PublicKey farmsProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createWritableSigner(payerKey),
      createWrite(obligationKey),
      createRead(lendingMarketAuthorityKey),
      createWrite(reserveKey),
      createWrite(reserveFarmStateKey),
      createWrite(obligationFarmKey),
      createRead(lendingMarketKey),
      createRead(farmsProgramKey),
      createRead(solanaAccounts.rentSysVar()),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction lendingInitObligationFarmsForReserve(final AccountMeta invokedExtKaminoProgramMeta,
                                                                 final SolanaAccounts solanaAccounts,
                                                                 final PublicKey glamStateKey,
                                                                 final PublicKey glamVaultKey,
                                                                 final PublicKey glamSignerKey,
                                                                 final PublicKey integrationAuthorityKey,
                                                                 final PublicKey cpiProgramKey,
                                                                 final PublicKey glamProtocolProgramKey,
                                                                 final PublicKey payerKey,
                                                                 final PublicKey obligationKey,
                                                                 final PublicKey lendingMarketAuthorityKey,
                                                                 final PublicKey reserveKey,
                                                                 final PublicKey reserveFarmStateKey,
                                                                 final PublicKey obligationFarmKey,
                                                                 final PublicKey lendingMarketKey,
                                                                 final PublicKey farmsProgramKey,
                                                                 final int mode) {
    final var keys = lendingInitObligationFarmsForReserveKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      payerKey,
      obligationKey,
      lendingMarketAuthorityKey,
      reserveKey,
      reserveFarmStateKey,
      obligationFarmKey,
      lendingMarketKey,
      farmsProgramKey
    );
    return lendingInitObligationFarmsForReserve(invokedExtKaminoProgramMeta, keys, mode);
  }

  public static Instruction lendingInitObligationFarmsForReserve(final AccountMeta invokedExtKaminoProgramMeta,
                                                                 final List<AccountMeta> keys,
                                                                 final int mode) {
    final byte[] _data = new byte[9];
    int i = LENDING_INIT_OBLIGATION_FARMS_FOR_RESERVE_DISCRIMINATOR.write(_data, 0);
    _data[i] = (byte) mode;

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record LendingInitObligationFarmsForReserveIxData(Discriminator discriminator, int mode) implements SerDe {  

    public static LendingInitObligationFarmsForReserveIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 9;

    public static LendingInitObligationFarmsForReserveIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var mode = _data[i] & 0xFF;
      return new LendingInitObligationFarmsForReserveIxData(discriminator, mode);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      _data[i] = (byte) mode;
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator LENDING_INIT_USER_METADATA_DISCRIMINATOR = toDiscriminator(68, 236, 138, 146, 124, 228, 247, 241);

  public static List<AccountMeta> lendingInitUserMetadataKeys(final AccountMeta invokedExtKaminoProgramMeta,
                                                              final SolanaAccounts solanaAccounts,
                                                              final PublicKey glamStateKey,
                                                              final PublicKey glamVaultKey,
                                                              final PublicKey glamSignerKey,
                                                              final PublicKey integrationAuthorityKey,
                                                              final PublicKey cpiProgramKey,
                                                              final PublicKey glamProtocolProgramKey,
                                                              final PublicKey feePayerKey,
                                                              final PublicKey userMetadataKey,
                                                              final PublicKey referrerUserMetadataKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createWritableSigner(feePayerKey),
      createWrite(userMetadataKey),
      createRead(requireNonNullElse(referrerUserMetadataKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(solanaAccounts.rentSysVar()),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction lendingInitUserMetadata(final AccountMeta invokedExtKaminoProgramMeta,
                                                    final SolanaAccounts solanaAccounts,
                                                    final PublicKey glamStateKey,
                                                    final PublicKey glamVaultKey,
                                                    final PublicKey glamSignerKey,
                                                    final PublicKey integrationAuthorityKey,
                                                    final PublicKey cpiProgramKey,
                                                    final PublicKey glamProtocolProgramKey,
                                                    final PublicKey feePayerKey,
                                                    final PublicKey userMetadataKey,
                                                    final PublicKey referrerUserMetadataKey,
                                                    final PublicKey userLookupTable) {
    final var keys = lendingInitUserMetadataKeys(
      invokedExtKaminoProgramMeta,
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      feePayerKey,
      userMetadataKey,
      referrerUserMetadataKey
    );
    return lendingInitUserMetadata(invokedExtKaminoProgramMeta, keys, userLookupTable);
  }

  public static Instruction lendingInitUserMetadata(final AccountMeta invokedExtKaminoProgramMeta,
                                                    final List<AccountMeta> keys,
                                                    final PublicKey userLookupTable) {
    final byte[] _data = new byte[40];
    int i = LENDING_INIT_USER_METADATA_DISCRIMINATOR.write(_data, 0);
    userLookupTable.write(_data, i);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record LendingInitUserMetadataIxData(Discriminator discriminator, PublicKey userLookupTable) implements SerDe {  

    public static LendingInitUserMetadataIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 40;

    public static LendingInitUserMetadataIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var userLookupTable = readPubKey(_data, i);
      return new LendingInitUserMetadataIxData(discriminator, userLookupTable);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      userLookupTable.write(_data, i);
      i += 32;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator LENDING_REPAY_OBLIGATION_LIQUIDITY_V_2_DISCRIMINATOR = toDiscriminator(79, 34, 126, 170, 174, 156, 174, 29);

  public static List<AccountMeta> lendingRepayObligationLiquidityV2Keys(final AccountMeta invokedExtKaminoProgramMeta,
                                                                        final SolanaAccounts solanaAccounts,
                                                                        final PublicKey glamStateKey,
                                                                        final PublicKey glamVaultKey,
                                                                        final PublicKey glamSignerKey,
                                                                        final PublicKey integrationAuthorityKey,
                                                                        final PublicKey cpiProgramKey,
                                                                        final PublicKey glamProtocolProgramKey,
                                                                        final PublicKey obligationKey,
                                                                        final PublicKey lendingMarketKey,
                                                                        final PublicKey repayReserveKey,
                                                                        final PublicKey reserveLiquidityMintKey,
                                                                        final PublicKey reserveDestinationLiquidityKey,
                                                                        final PublicKey userSourceLiquidityKey,
                                                                        final PublicKey tokenProgramKey,
                                                                        final PublicKey instructionSysvarAccountKey,
                                                                        final PublicKey obligationFarmUserStateKey,
                                                                        final PublicKey reserveFarmStateKey,
                                                                        final PublicKey lendingMarketAuthorityKey,
                                                                        final PublicKey farmsProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(obligationKey),
      createRead(lendingMarketKey),
      createWrite(repayReserveKey),
      createRead(reserveLiquidityMintKey),
      createWrite(reserveDestinationLiquidityKey),
      createWrite(userSourceLiquidityKey),
      createRead(tokenProgramKey),
      createRead(instructionSysvarAccountKey),
      createWrite(requireNonNullElse(obligationFarmUserStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createWrite(requireNonNullElse(reserveFarmStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(lendingMarketAuthorityKey),
      createRead(farmsProgramKey)
    );
  }

  public static Instruction lendingRepayObligationLiquidityV2(final AccountMeta invokedExtKaminoProgramMeta,
                                                              final SolanaAccounts solanaAccounts,
                                                              final PublicKey glamStateKey,
                                                              final PublicKey glamVaultKey,
                                                              final PublicKey glamSignerKey,
                                                              final PublicKey integrationAuthorityKey,
                                                              final PublicKey cpiProgramKey,
                                                              final PublicKey glamProtocolProgramKey,
                                                              final PublicKey obligationKey,
                                                              final PublicKey lendingMarketKey,
                                                              final PublicKey repayReserveKey,
                                                              final PublicKey reserveLiquidityMintKey,
                                                              final PublicKey reserveDestinationLiquidityKey,
                                                              final PublicKey userSourceLiquidityKey,
                                                              final PublicKey tokenProgramKey,
                                                              final PublicKey instructionSysvarAccountKey,
                                                              final PublicKey obligationFarmUserStateKey,
                                                              final PublicKey reserveFarmStateKey,
                                                              final PublicKey lendingMarketAuthorityKey,
                                                              final PublicKey farmsProgramKey,
                                                              final long liquidityAmount) {
    final var keys = lendingRepayObligationLiquidityV2Keys(
      invokedExtKaminoProgramMeta,
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      obligationKey,
      lendingMarketKey,
      repayReserveKey,
      reserveLiquidityMintKey,
      reserveDestinationLiquidityKey,
      userSourceLiquidityKey,
      tokenProgramKey,
      instructionSysvarAccountKey,
      obligationFarmUserStateKey,
      reserveFarmStateKey,
      lendingMarketAuthorityKey,
      farmsProgramKey
    );
    return lendingRepayObligationLiquidityV2(invokedExtKaminoProgramMeta, keys, liquidityAmount);
  }

  public static Instruction lendingRepayObligationLiquidityV2(final AccountMeta invokedExtKaminoProgramMeta,
                                                              final List<AccountMeta> keys,
                                                              final long liquidityAmount) {
    final byte[] _data = new byte[16];
    int i = LENDING_REPAY_OBLIGATION_LIQUIDITY_V_2_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, liquidityAmount);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record LendingRepayObligationLiquidityV2IxData(Discriminator discriminator, long liquidityAmount) implements SerDe {  

    public static LendingRepayObligationLiquidityV2IxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static LendingRepayObligationLiquidityV2IxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var liquidityAmount = getInt64LE(_data, i);
      return new LendingRepayObligationLiquidityV2IxData(discriminator, liquidityAmount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, liquidityAmount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator LENDING_WITHDRAW_OBLIGATION_COLLATERAL_AND_REDEEM_RESERVE_COLLATERAL_V_2_DISCRIMINATOR = toDiscriminator(217, 223, 173, 35, 64, 225, 161, 222);

  public static List<AccountMeta> lendingWithdrawObligationCollateralAndRedeemReserveCollateralV2Keys(final AccountMeta invokedExtKaminoProgramMeta,
                                                                                                      final SolanaAccounts solanaAccounts,
                                                                                                      final PublicKey glamStateKey,
                                                                                                      final PublicKey glamVaultKey,
                                                                                                      final PublicKey glamSignerKey,
                                                                                                      final PublicKey integrationAuthorityKey,
                                                                                                      final PublicKey cpiProgramKey,
                                                                                                      final PublicKey glamProtocolProgramKey,
                                                                                                      final PublicKey obligationKey,
                                                                                                      final PublicKey lendingMarketKey,
                                                                                                      final PublicKey lendingMarketAuthorityKey,
                                                                                                      final PublicKey withdrawReserveKey,
                                                                                                      final PublicKey reserveLiquidityMintKey,
                                                                                                      final PublicKey reserveSourceCollateralKey,
                                                                                                      final PublicKey reserveCollateralMintKey,
                                                                                                      final PublicKey reserveLiquiditySupplyKey,
                                                                                                      final PublicKey userDestinationLiquidityKey,
                                                                                                      final PublicKey placeholderUserDestinationCollateralKey,
                                                                                                      final PublicKey collateralTokenProgramKey,
                                                                                                      final PublicKey liquidityTokenProgramKey,
                                                                                                      final PublicKey instructionSysvarAccountKey,
                                                                                                      final PublicKey obligationFarmUserStateKey,
                                                                                                      final PublicKey reserveFarmStateKey,
                                                                                                      final PublicKey farmsProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(obligationKey),
      createRead(lendingMarketKey),
      createRead(lendingMarketAuthorityKey),
      createWrite(withdrawReserveKey),
      createRead(reserveLiquidityMintKey),
      createWrite(reserveSourceCollateralKey),
      createWrite(reserveCollateralMintKey),
      createWrite(reserveLiquiditySupplyKey),
      createWrite(userDestinationLiquidityKey),
      createRead(requireNonNullElse(placeholderUserDestinationCollateralKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(collateralTokenProgramKey),
      createRead(liquidityTokenProgramKey),
      createRead(instructionSysvarAccountKey),
      createWrite(requireNonNullElse(obligationFarmUserStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createWrite(requireNonNullElse(reserveFarmStateKey, invokedExtKaminoProgramMeta.publicKey())),
      createRead(farmsProgramKey)
    );
  }

  public static Instruction lendingWithdrawObligationCollateralAndRedeemReserveCollateralV2(final AccountMeta invokedExtKaminoProgramMeta,
                                                                                            final SolanaAccounts solanaAccounts,
                                                                                            final PublicKey glamStateKey,
                                                                                            final PublicKey glamVaultKey,
                                                                                            final PublicKey glamSignerKey,
                                                                                            final PublicKey integrationAuthorityKey,
                                                                                            final PublicKey cpiProgramKey,
                                                                                            final PublicKey glamProtocolProgramKey,
                                                                                            final PublicKey obligationKey,
                                                                                            final PublicKey lendingMarketKey,
                                                                                            final PublicKey lendingMarketAuthorityKey,
                                                                                            final PublicKey withdrawReserveKey,
                                                                                            final PublicKey reserveLiquidityMintKey,
                                                                                            final PublicKey reserveSourceCollateralKey,
                                                                                            final PublicKey reserveCollateralMintKey,
                                                                                            final PublicKey reserveLiquiditySupplyKey,
                                                                                            final PublicKey userDestinationLiquidityKey,
                                                                                            final PublicKey placeholderUserDestinationCollateralKey,
                                                                                            final PublicKey collateralTokenProgramKey,
                                                                                            final PublicKey liquidityTokenProgramKey,
                                                                                            final PublicKey instructionSysvarAccountKey,
                                                                                            final PublicKey obligationFarmUserStateKey,
                                                                                            final PublicKey reserveFarmStateKey,
                                                                                            final PublicKey farmsProgramKey,
                                                                                            final long collateralAmount) {
    final var keys = lendingWithdrawObligationCollateralAndRedeemReserveCollateralV2Keys(
      invokedExtKaminoProgramMeta,
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      obligationKey,
      lendingMarketKey,
      lendingMarketAuthorityKey,
      withdrawReserveKey,
      reserveLiquidityMintKey,
      reserveSourceCollateralKey,
      reserveCollateralMintKey,
      reserveLiquiditySupplyKey,
      userDestinationLiquidityKey,
      placeholderUserDestinationCollateralKey,
      collateralTokenProgramKey,
      liquidityTokenProgramKey,
      instructionSysvarAccountKey,
      obligationFarmUserStateKey,
      reserveFarmStateKey,
      farmsProgramKey
    );
    return lendingWithdrawObligationCollateralAndRedeemReserveCollateralV2(invokedExtKaminoProgramMeta, keys, collateralAmount);
  }

  public static Instruction lendingWithdrawObligationCollateralAndRedeemReserveCollateralV2(final AccountMeta invokedExtKaminoProgramMeta,
                                                                                            final List<AccountMeta> keys,
                                                                                            final long collateralAmount) {
    final byte[] _data = new byte[16];
    int i = LENDING_WITHDRAW_OBLIGATION_COLLATERAL_AND_REDEEM_RESERVE_COLLATERAL_V_2_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, collateralAmount);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record LendingWithdrawObligationCollateralAndRedeemReserveCollateralV2IxData(Discriminator discriminator, long collateralAmount) implements SerDe {  

    public static LendingWithdrawObligationCollateralAndRedeemReserveCollateralV2IxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static LendingWithdrawObligationCollateralAndRedeemReserveCollateralV2IxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var collateralAmount = getInt64LE(_data, i);
      return new LendingWithdrawObligationCollateralAndRedeemReserveCollateralV2IxData(discriminator, collateralAmount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, collateralAmount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator SET_LENDING_POLICY_DISCRIMINATOR = toDiscriminator(226, 185, 23, 3, 113, 88, 118, 176);

  public static List<AccountMeta> setLendingPolicyKeys(final PublicKey glamStateKey,
                                                       final PublicKey glamSignerKey,
                                                       final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(glamProtocolProgramKey)
    );
  }

  public static Instruction setLendingPolicy(final AccountMeta invokedExtKaminoProgramMeta,
                                             final PublicKey glamStateKey,
                                             final PublicKey glamSignerKey,
                                             final PublicKey glamProtocolProgramKey,
                                             final LendingPolicy policy) {
    final var keys = setLendingPolicyKeys(
      glamStateKey,
      glamSignerKey,
      glamProtocolProgramKey
    );
    return setLendingPolicy(invokedExtKaminoProgramMeta, keys, policy);
  }

  public static Instruction setLendingPolicy(final AccountMeta invokedExtKaminoProgramMeta,
                                             final List<AccountMeta> keys,
                                             final LendingPolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_LENDING_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record SetLendingPolicyIxData(Discriminator discriminator, LendingPolicy policy) implements SerDe {  

    public static SetLendingPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static SetLendingPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = LendingPolicy.read(_data, i);
      return new SetLendingPolicyIxData(discriminator, policy);
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

  public static final Discriminator SET_VAULTS_POLICY_DISCRIMINATOR = toDiscriminator(211, 177, 22, 152, 235, 59, 192, 62);

  public static List<AccountMeta> setVaultsPolicyKeys(final PublicKey glamStateKey,
                                                      final PublicKey glamSignerKey,
                                                      final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(glamProtocolProgramKey)
    );
  }

  public static Instruction setVaultsPolicy(final AccountMeta invokedExtKaminoProgramMeta,
                                            final PublicKey glamStateKey,
                                            final PublicKey glamSignerKey,
                                            final PublicKey glamProtocolProgramKey,
                                            final VaultsPolicy policy) {
    final var keys = setVaultsPolicyKeys(
      glamStateKey,
      glamSignerKey,
      glamProtocolProgramKey
    );
    return setVaultsPolicy(invokedExtKaminoProgramMeta, keys, policy);
  }

  public static Instruction setVaultsPolicy(final AccountMeta invokedExtKaminoProgramMeta,
                                            final List<AccountMeta> keys,
                                            final VaultsPolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_VAULTS_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record SetVaultsPolicyIxData(Discriminator discriminator, VaultsPolicy policy) implements SerDe {  

    public static SetVaultsPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static SetVaultsPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = VaultsPolicy.read(_data, i);
      return new SetVaultsPolicyIxData(discriminator, policy);
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

  public static final Discriminator VAULTS_DEPOSIT_DISCRIMINATOR = toDiscriminator(124, 173, 191, 223, 48, 26, 84, 84);

  public static List<AccountMeta> vaultsDepositKeys(final SolanaAccounts solanaAccounts,
                                                    final PublicKey glamStateKey,
                                                    final PublicKey glamVaultKey,
                                                    final PublicKey glamSignerKey,
                                                    final PublicKey integrationAuthorityKey,
                                                    final PublicKey cpiProgramKey,
                                                    final PublicKey glamProtocolProgramKey,
                                                    final PublicKey vaultStateKey,
                                                    final PublicKey tokenVaultKey,
                                                    final PublicKey tokenMintKey,
                                                    final PublicKey baseVaultAuthorityKey,
                                                    final PublicKey sharesMintKey,
                                                    final PublicKey userTokenAtaKey,
                                                    final PublicKey userSharesAtaKey,
                                                    final PublicKey klendProgramKey,
                                                    final PublicKey tokenProgramKey,
                                                    final PublicKey sharesTokenProgramKey,
                                                    final PublicKey eventAuthorityKey,
                                                    final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(vaultStateKey),
      createWrite(tokenVaultKey),
      createRead(tokenMintKey),
      createRead(baseVaultAuthorityKey),
      createWrite(sharesMintKey),
      createWrite(userTokenAtaKey),
      createWrite(userSharesAtaKey),
      createRead(klendProgramKey),
      createRead(tokenProgramKey),
      createRead(sharesTokenProgramKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction vaultsDeposit(final AccountMeta invokedExtKaminoProgramMeta,
                                          final SolanaAccounts solanaAccounts,
                                          final PublicKey glamStateKey,
                                          final PublicKey glamVaultKey,
                                          final PublicKey glamSignerKey,
                                          final PublicKey integrationAuthorityKey,
                                          final PublicKey cpiProgramKey,
                                          final PublicKey glamProtocolProgramKey,
                                          final PublicKey vaultStateKey,
                                          final PublicKey tokenVaultKey,
                                          final PublicKey tokenMintKey,
                                          final PublicKey baseVaultAuthorityKey,
                                          final PublicKey sharesMintKey,
                                          final PublicKey userTokenAtaKey,
                                          final PublicKey userSharesAtaKey,
                                          final PublicKey klendProgramKey,
                                          final PublicKey tokenProgramKey,
                                          final PublicKey sharesTokenProgramKey,
                                          final PublicKey eventAuthorityKey,
                                          final PublicKey programKey,
                                          final long maxAmount) {
    final var keys = vaultsDepositKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      vaultStateKey,
      tokenVaultKey,
      tokenMintKey,
      baseVaultAuthorityKey,
      sharesMintKey,
      userTokenAtaKey,
      userSharesAtaKey,
      klendProgramKey,
      tokenProgramKey,
      sharesTokenProgramKey,
      eventAuthorityKey,
      programKey
    );
    return vaultsDeposit(invokedExtKaminoProgramMeta, keys, maxAmount);
  }

  public static Instruction vaultsDeposit(final AccountMeta invokedExtKaminoProgramMeta,
                                          final List<AccountMeta> keys,
                                          final long maxAmount) {
    final byte[] _data = new byte[16];
    int i = VAULTS_DEPOSIT_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, maxAmount);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record VaultsDepositIxData(Discriminator discriminator, long maxAmount) implements SerDe {  

    public static VaultsDepositIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static VaultsDepositIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var maxAmount = getInt64LE(_data, i);
      return new VaultsDepositIxData(discriminator, maxAmount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, maxAmount);
      i += 8;
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
                                                     final PublicKey withdrawFromAvailableVaultStateKey,
                                                     final PublicKey withdrawFromAvailableTokenVaultKey,
                                                     final PublicKey withdrawFromAvailableBaseVaultAuthorityKey,
                                                     final PublicKey withdrawFromAvailableUserTokenAtaKey,
                                                     final PublicKey withdrawFromAvailableTokenMintKey,
                                                     final PublicKey withdrawFromAvailableUserSharesAtaKey,
                                                     final PublicKey withdrawFromAvailableSharesMintKey,
                                                     final PublicKey withdrawFromAvailableTokenProgramKey,
                                                     final PublicKey withdrawFromAvailableSharesTokenProgramKey,
                                                     final PublicKey withdrawFromAvailableKlendProgramKey,
                                                     final PublicKey withdrawFromAvailableEventAuthorityKey,
                                                     final PublicKey withdrawFromAvailableProgramKey,
                                                     final PublicKey withdrawFromReserveVaultStateKey,
                                                     final PublicKey withdrawFromReserveReserveKey,
                                                     final PublicKey withdrawFromReserveCtokenVaultKey,
                                                     final PublicKey withdrawFromReserveLendingMarketKey,
                                                     final PublicKey withdrawFromReserveLendingMarketAuthorityKey,
                                                     final PublicKey withdrawFromReserveReserveLiquiditySupplyKey,
                                                     final PublicKey withdrawFromReserveReserveCollateralMintKey,
                                                     final PublicKey withdrawFromReserveReserveCollateralTokenProgramKey,
                                                     final PublicKey withdrawFromReserveInstructionSysvarAccountKey,
                                                     final PublicKey eventAuthorityKey,
                                                     final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(withdrawFromAvailableVaultStateKey),
      createWrite(withdrawFromAvailableTokenVaultKey),
      createRead(withdrawFromAvailableBaseVaultAuthorityKey),
      createWrite(withdrawFromAvailableUserTokenAtaKey),
      createWrite(withdrawFromAvailableTokenMintKey),
      createWrite(withdrawFromAvailableUserSharesAtaKey),
      createWrite(withdrawFromAvailableSharesMintKey),
      createRead(withdrawFromAvailableTokenProgramKey),
      createRead(withdrawFromAvailableSharesTokenProgramKey),
      createRead(withdrawFromAvailableKlendProgramKey),
      createRead(withdrawFromAvailableEventAuthorityKey),
      createRead(withdrawFromAvailableProgramKey),
      createWrite(withdrawFromReserveVaultStateKey),
      createWrite(withdrawFromReserveReserveKey),
      createWrite(withdrawFromReserveCtokenVaultKey),
      createRead(withdrawFromReserveLendingMarketKey),
      createRead(withdrawFromReserveLendingMarketAuthorityKey),
      createWrite(withdrawFromReserveReserveLiquiditySupplyKey),
      createWrite(withdrawFromReserveReserveCollateralMintKey),
      createRead(withdrawFromReserveReserveCollateralTokenProgramKey),
      createRead(withdrawFromReserveInstructionSysvarAccountKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction vaultsWithdraw(final AccountMeta invokedExtKaminoProgramMeta,
                                           final SolanaAccounts solanaAccounts,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamVaultKey,
                                           final PublicKey glamSignerKey,
                                           final PublicKey integrationAuthorityKey,
                                           final PublicKey cpiProgramKey,
                                           final PublicKey glamProtocolProgramKey,
                                           final PublicKey withdrawFromAvailableVaultStateKey,
                                           final PublicKey withdrawFromAvailableTokenVaultKey,
                                           final PublicKey withdrawFromAvailableBaseVaultAuthorityKey,
                                           final PublicKey withdrawFromAvailableUserTokenAtaKey,
                                           final PublicKey withdrawFromAvailableTokenMintKey,
                                           final PublicKey withdrawFromAvailableUserSharesAtaKey,
                                           final PublicKey withdrawFromAvailableSharesMintKey,
                                           final PublicKey withdrawFromAvailableTokenProgramKey,
                                           final PublicKey withdrawFromAvailableSharesTokenProgramKey,
                                           final PublicKey withdrawFromAvailableKlendProgramKey,
                                           final PublicKey withdrawFromAvailableEventAuthorityKey,
                                           final PublicKey withdrawFromAvailableProgramKey,
                                           final PublicKey withdrawFromReserveVaultStateKey,
                                           final PublicKey withdrawFromReserveReserveKey,
                                           final PublicKey withdrawFromReserveCtokenVaultKey,
                                           final PublicKey withdrawFromReserveLendingMarketKey,
                                           final PublicKey withdrawFromReserveLendingMarketAuthorityKey,
                                           final PublicKey withdrawFromReserveReserveLiquiditySupplyKey,
                                           final PublicKey withdrawFromReserveReserveCollateralMintKey,
                                           final PublicKey withdrawFromReserveReserveCollateralTokenProgramKey,
                                           final PublicKey withdrawFromReserveInstructionSysvarAccountKey,
                                           final PublicKey eventAuthorityKey,
                                           final PublicKey programKey,
                                           final long sharesAmount) {
    final var keys = vaultsWithdrawKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      withdrawFromAvailableVaultStateKey,
      withdrawFromAvailableTokenVaultKey,
      withdrawFromAvailableBaseVaultAuthorityKey,
      withdrawFromAvailableUserTokenAtaKey,
      withdrawFromAvailableTokenMintKey,
      withdrawFromAvailableUserSharesAtaKey,
      withdrawFromAvailableSharesMintKey,
      withdrawFromAvailableTokenProgramKey,
      withdrawFromAvailableSharesTokenProgramKey,
      withdrawFromAvailableKlendProgramKey,
      withdrawFromAvailableEventAuthorityKey,
      withdrawFromAvailableProgramKey,
      withdrawFromReserveVaultStateKey,
      withdrawFromReserveReserveKey,
      withdrawFromReserveCtokenVaultKey,
      withdrawFromReserveLendingMarketKey,
      withdrawFromReserveLendingMarketAuthorityKey,
      withdrawFromReserveReserveLiquiditySupplyKey,
      withdrawFromReserveReserveCollateralMintKey,
      withdrawFromReserveReserveCollateralTokenProgramKey,
      withdrawFromReserveInstructionSysvarAccountKey,
      eventAuthorityKey,
      programKey
    );
    return vaultsWithdraw(invokedExtKaminoProgramMeta, keys, sharesAmount);
  }

  public static Instruction vaultsWithdraw(final AccountMeta invokedExtKaminoProgramMeta,
                                           final List<AccountMeta> keys,
                                           final long sharesAmount) {
    final byte[] _data = new byte[16];
    int i = VAULTS_WITHDRAW_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, sharesAmount);

    return Instruction.createInstruction(invokedExtKaminoProgramMeta, keys, _data);
  }

  public record VaultsWithdrawIxData(Discriminator discriminator, long sharesAmount) implements SerDe {  

    public static VaultsWithdrawIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static VaultsWithdrawIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var sharesAmount = getInt64LE(_data, i);
      return new VaultsWithdrawIxData(discriminator, sharesAmount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, sharesAmount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  private ExtKaminoProgram() {
  }
}
