package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultAllocation;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.Arrays;

public record KaminoVaultContext(long slot,
                                 AccountMeta readVaultState,
                                 PublicKey vaultAdminAuthority,
                                 PublicKey baseVaultAuthority,
                                 PublicKey tokenMint,
                                 long tokenMintDecimals,
                                 PublicKey tokenProgram,
                                 AccountMeta readSharesMint,
                                 long sharesMintDecimals,
                                 long performanceFeeBps,
                                 long managementFeeBps,
                                 PublicKey[] reserves,
                                 String name,
                                 PublicKey vaultLookupTable,
                                 PublicKey vaultFarm,
                                 long minDepositAmount,
                                 long minWithdrawAmount,
                                 long withdrawalPenaltyLamports,
                                 long withdrawalPenaltyBps) {

  private static final byte[] NULL_KEY_BYTES = PublicKey.NONE.toByteArray();

  static KaminoVaultContext createContext(final long slot,
                                          final byte[] data,
                                          final PublicKey vaultKey,
                                          final PublicKey sharesMintKey) {
    final var name = SerDeUtil.fixedLengthString(
        data, VaultState.NAME_OFFSET, VaultState.NAME_OFFSET + VaultState.NAME_LEN
    );
    final var reserveKeys = KaminoVaultContext.parseReserveKeys(data);
    return new KaminoVaultContext(
        slot,
        AccountMeta.createRead(vaultKey),
        PublicKey.readPubKey(data, VaultState.VAULT_ADMIN_AUTHORITY_OFFSET),
        PublicKey.readPubKey(data, VaultState.BASE_VAULT_AUTHORITY_OFFSET),
        PublicKey.readPubKey(data, VaultState.TOKEN_MINT_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.TOKEN_MINT_DECIMALS_OFFSET),
        PublicKey.readPubKey(data, VaultState.TOKEN_PROGRAM_OFFSET),
        AccountMeta.createRead(sharesMintKey),
        ByteUtil.getInt64LE(data, VaultState.SHARES_MINT_DECIMALS_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.PERFORMANCE_FEE_BPS_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.MANAGEMENT_FEE_BPS_OFFSET),
        reserveKeys,
        name,
        createIfNotNull(data, VaultState.VAULT_LOOKUP_TABLE_OFFSET),
//        PublicKey.readPubKey(data, VaultState.ALLOCATION_ADMIN_OFFSET),
        createIfNotNull(data, VaultState.VAULT_FARM_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.MIN_DEPOSIT_AMOUNT_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.MIN_WITHDRAW_AMOUNT_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.WITHDRAWAL_PENALTY_LAMPORTS_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.WITHDRAWAL_PENALTY_BPS_OFFSET)
    );
  }

  static KaminoVaultContext createContext(final AccountInfo<byte[]> accountInfo) {
    final long slot = accountInfo.context().slot();
    final var data = accountInfo.data();
    final var sharesMintKey = PublicKey.readPubKey(data, VaultState.SHARES_MINT_OFFSET);
    return createContext(slot, data, accountInfo.pubKey(), sharesMintKey);
  }

  static PublicKey[] parseReserveKeys(final byte[] data) {
    final var reserveKeys = new PublicKey[VaultState.VAULT_ALLOCATION_STRATEGY_LEN];
    int offset = VaultState.VAULT_ALLOCATION_STRATEGY_OFFSET;
    for (int i = 0; i < reserveKeys.length; ++i) {
      final var reserveKey = PublicKey.readPubKey(data, offset);
      if (reserveKey.equals(PublicKey.NONE)) {
        return Arrays.copyOfRange(reserveKeys, 0, i);
      } else {
        reserveKeys[i] = reserveKey;
        offset += VaultAllocation.BYTES;
      }
    }
    return reserveKeys;
  }

  private static boolean isNull(final byte[] data, final int offset) {
    return Arrays.equals(
        NULL_KEY_BYTES, 0, NULL_KEY_BYTES.length,
        data, offset, offset + NULL_KEY_BYTES.length
    );
  }

  private static PublicKey createIfNotNull(final byte[] data, final int offset) {
    return isNull(data, offset) ? null : PublicKey.readPubKey(data, offset);
  }

  private static boolean noKeyChange(final PublicKey previous, final byte[] data, final int offset) {
    if (previous == null) {
      return isNull(data, offset);
    } else {
      return Arrays.equals(
          previous.toByteArray(), 0, PublicKey.PUBLIC_KEY_LENGTH,
          data, offset, offset + PublicKey.PUBLIC_KEY_LENGTH
      );
    }
  }

  private static PublicKey createIfChanged(final PublicKey previous, final byte[] data, final int offset) {
    if (noKeyChange(previous, data, offset)) {
      return previous;
    } else {
      return createIfNotNull(data, offset);
    }
  }

  public PublicKey sharesMint() {
    return readSharesMint.publicKey();
  }

  public int numReserves() {
    return reserves.length;
  }

  private KaminoVaultContext createIfChanged(final long slot,
                                             final byte[] data,
                                             final PublicKey[] reserves,
                                             final long minDepositAmount,
                                             final long minWithdrawAmount,
                                             final long withdrawalPenaltyLamports,
                                             final long withdrawalPenaltyBps) {
    return new KaminoVaultContext(
        slot,
        readVaultState,
        createIfChanged(this.vaultAdminAuthority, data, VaultState.VAULT_ADMIN_AUTHORITY_OFFSET),
        createIfChanged(this.baseVaultAuthority, data, VaultState.BASE_VAULT_AUTHORITY_OFFSET),
        tokenMint, tokenMintDecimals, tokenProgram,
        readSharesMint, sharesMintDecimals,
        performanceFeeBps,
        managementFeeBps,
        reserves,
        name,
        createIfChanged(this.vaultLookupTable, data, VaultState.VAULT_LOOKUP_TABLE_OFFSET),
        createIfChanged(this.vaultFarm, data, VaultState.VAULT_FARM_OFFSET),
        minDepositAmount,
        minWithdrawAmount,
        withdrawalPenaltyLamports,
        withdrawalPenaltyBps
    );
  }

  KaminoVaultContext createIfChanged(final long slot, final byte[] data) {
    final var reserves = parseReserveKeys(data);
    if (Arrays.equals(this.reserves, reserves)
        && noKeyChange(this.vaultLookupTable, data, VaultState.VAULT_LOOKUP_TABLE_OFFSET)
        && noKeyChange(this.vaultAdminAuthority, data, VaultState.VAULT_ADMIN_AUTHORITY_OFFSET)
        && noKeyChange(this.baseVaultAuthority, data, VaultState.BASE_VAULT_AUTHORITY_OFFSET)
        && noKeyChange(this.vaultFarm, data, VaultState.VAULT_FARM_OFFSET)) {
      final long performanceFeeBps = ByteUtil.getInt64LE(data, VaultState.PERFORMANCE_FEE_BPS_OFFSET);
      final long managementFeeBps = ByteUtil.getInt64LE(data, VaultState.MANAGEMENT_FEE_BPS_OFFSET);
      final long minDepositAmount = ByteUtil.getInt64LE(data, VaultState.MIN_DEPOSIT_AMOUNT_OFFSET);
      final long minWithdrawAmount = ByteUtil.getInt64LE(data, VaultState.MIN_WITHDRAW_AMOUNT_OFFSET);
      final long withdrawalPenaltyLamports = ByteUtil.getInt64LE(data, VaultState.WITHDRAWAL_PENALTY_LAMPORTS_OFFSET);
      final long withdrawalPenaltyBps = ByteUtil.getInt64LE(data, VaultState.WITHDRAWAL_PENALTY_BPS_OFFSET);
      if (this.performanceFeeBps == performanceFeeBps
          && this.managementFeeBps == managementFeeBps
          && this.minDepositAmount == minDepositAmount
          && this.minWithdrawAmount == minWithdrawAmount
          && this.withdrawalPenaltyLamports == withdrawalPenaltyLamports
          && this.withdrawalPenaltyBps == withdrawalPenaltyBps) {
        return this;
      }
      return createIfChanged(
          slot,
          data,
          reserves,
          minDepositAmount,
          minWithdrawAmount,
          withdrawalPenaltyLamports,
          withdrawalPenaltyBps

      );
    }
    return new KaminoVaultContext(
        slot,
        readVaultState,
        createIfChanged(this.vaultAdminAuthority, data, VaultState.VAULT_ADMIN_AUTHORITY_OFFSET),
        createIfChanged(this.baseVaultAuthority, data, VaultState.BASE_VAULT_AUTHORITY_OFFSET),
        tokenMint, tokenMintDecimals, tokenProgram,
        readSharesMint, sharesMintDecimals,
        ByteUtil.getInt64LE(data, VaultState.PERFORMANCE_FEE_BPS_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.MANAGEMENT_FEE_BPS_OFFSET),
        reserves,
        name,
        createIfChanged(this.vaultLookupTable, data, VaultState.VAULT_LOOKUP_TABLE_OFFSET),
        createIfChanged(this.vaultFarm, data, VaultState.VAULT_FARM_OFFSET),
        performanceFeeBps,
        ByteUtil.getInt64LE(data, VaultState.MIN_WITHDRAW_AMOUNT_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.WITHDRAWAL_PENALTY_LAMPORTS_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.WITHDRAWAL_PENALTY_BPS_OFFSET)
    );
  }
}
