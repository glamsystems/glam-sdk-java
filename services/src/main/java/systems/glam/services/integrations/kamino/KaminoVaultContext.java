package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultAllocation;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.Arrays;
import java.util.Objects;

import static systems.glam.services.integrations.kamino.ReserveContext.fixedLengthString;

public record KaminoVaultContext(long slot,
                                 PublicKey publicKey,
                                 PublicKey tokenMint,
                                 long tokenMintDecimals,
                                 PublicKey tokenProgram,
                                 PublicKey sharesMint,
                                 long sharesMintDecimals,
                                 PublicKey[] reserves,
                                 String name,
                                 PublicKey vaultLookupTable) {

  static KaminoVaultContext createContext(final long slot,
                                          final PublicKey publicKey,
                                          final byte[] data,
                                          final PublicKey[] reserveKeys,
                                          final PublicKey vaultLookupTable) {
    final var name = fixedLengthString(data, VaultState.NAME_OFFSET, VaultState.NAME_OFFSET + VaultState.NAME_LEN);
    return new KaminoVaultContext(
        slot,
        publicKey,
        PublicKey.readPubKey(data, VaultState.TOKEN_MINT_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.TOKEN_MINT_DECIMALS_OFFSET),
        PublicKey.readPubKey(data, VaultState.TOKEN_PROGRAM_OFFSET),
        PublicKey.readPubKey(data, VaultState.SHARES_MINT_OFFSET),
        ByteUtil.getInt64LE(data, VaultState.SHARES_MINT_DECIMALS_OFFSET),
        reserveKeys,
        name,
        vaultLookupTable.equals(PublicKey.NONE) ? null : vaultLookupTable
    );
  }

  static KaminoVaultContext createContext(final AccountInfo<byte[]> accountInfo) {
    final long slot = accountInfo.context().slot();
    final var data = accountInfo.data();
    final var reserveKeys = parseReserveKeys(data);
    final var vaultLookupTable = PublicKey.readPubKey(data, VaultState.VAULT_LOOKUP_TABLE_OFFSET);
    return createContext(slot, accountInfo.pubKey(), data, reserveKeys, vaultLookupTable);
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

  KaminoVaultContext withReserves(final long slot, final PublicKey[] reserves, final PublicKey vaultLookupTable) {
    if (Arrays.equals(this.reserves, reserves) && Objects.equals(this.vaultLookupTable, vaultLookupTable)) {
      return this;
    } else {
      return new KaminoVaultContext(
          slot,
          publicKey,
          tokenMint, tokenMintDecimals, tokenProgram,
          sharesMint, sharesMintDecimals,
          reserves,
          name,
          vaultLookupTable.equals(PublicKey.NONE) ? null : vaultLookupTable
      );
    }
  }
}
