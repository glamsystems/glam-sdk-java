package systems.glam.services.integrations.kamino;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultAllocation;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// Drives KaminoVaultContext directly against the mainnet VaultState fixture,
/// with byte surgery for the shapes the fixture does not carry: zeroed
/// (null) farm and lookup-table keys, and a fully packed allocation table.
final class KaminoVaultContextTests {

  private static final PublicKey VAULT_STATE_KEY = fromBase58Encoded("5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH");

  private static byte[] vaultStateData;
  private static PublicKey sharesMint;

  @BeforeAll
  static void beforeAll() throws IOException {
    vaultStateData = ResourceUtil.readResource("accounts/kamino/" + VAULT_STATE_KEY + ".dat.gz");
    sharesMint = PublicKey.readPubKey(vaultStateData, VaultState.SHARES_MINT_OFFSET);
  }

  private static KaminoVaultContext context(final long slot, final byte[] data) {
    return KaminoVaultContext.createContext(slot, data, VAULT_STATE_KEY, sharesMint);
  }

  private static byte[] withZeroedKey(final byte[] data, final int offset) {
    final var zeroed = data.clone();
    Arrays.fill(zeroed, offset, offset + PublicKey.PUBLIC_KEY_LENGTH, (byte) 0);
    return zeroed;
  }

  @Test
  void nullKeysParseAsNullAndSurviveEveryTransition() {
    final var zeroed = withZeroedKey(
        withZeroedKey(vaultStateData, VaultState.VAULT_FARM_OFFSET),
        VaultState.VAULT_LOOKUP_TABLE_OFFSET
    );
    final var nullKeyed = context(1L, zeroed);
    // an all-zero key is "not set", never a NONE-key object
    assertNull(nullKeyed.vaultFarm());
    assertNull(nullKeyed.vaultLookupTable());

    // null -> null: nothing changed, the context is reused
    assertSame(nullKeyed, nullKeyed.createIfChanged(2L, zeroed));

    // null -> set: a new context carrying the real keys
    final var keyed = nullKeyed.createIfChanged(3L, vaultStateData);
    assertNotSame(nullKeyed, keyed);
    assertEquals(PublicKey.readPubKey(vaultStateData, VaultState.VAULT_FARM_OFFSET), keyed.vaultFarm());
    assertEquals(PublicKey.readPubKey(vaultStateData, VaultState.VAULT_LOOKUP_TABLE_OFFSET), keyed.vaultLookupTable());

    // set -> null: the keys are dropped again
    final var unkeyed = keyed.createIfChanged(4L, zeroed);
    assertNotSame(keyed, unkeyed);
    assertNull(unkeyed.vaultFarm());
    assertNull(unkeyed.vaultLookupTable());

    // set -> set (different key): replaced, not reused
    final var swapped = vaultStateData.clone();
    swapped[VaultState.VAULT_FARM_OFFSET] ^= 0x01;
    final var reKeyed = keyed.createIfChanged(5L, swapped);
    assertNotSame(keyed, reKeyed);
    assertNotNull(reKeyed.vaultFarm());
    assertNotEquals(keyed.vaultFarm(), reKeyed.vaultFarm());
  }

  @Test
  void everyComparedFieldTriggersANewContext() {
    final var initial = context(1L, vaultStateData);
    // identical bytes at a newer slot: reused, not reparsed
    assertSame(initial, initial.createIfChanged(2L, vaultStateData));

    final int[] valueOffsets = {
        VaultState.PERFORMANCE_FEE_BPS_OFFSET,
        VaultState.MANAGEMENT_FEE_BPS_OFFSET,
        VaultState.MIN_DEPOSIT_AMOUNT_OFFSET,
        VaultState.MIN_WITHDRAW_AMOUNT_OFFSET,
        VaultState.WITHDRAWAL_PENALTY_LAMPORTS_OFFSET,
        VaultState.WITHDRAWAL_PENALTY_BPS_OFFSET,
        VaultState.VAULT_ALLOCATION_STRATEGY_OFFSET,
    };
    final int[] keyOffsets = {
        VaultState.VAULT_LOOKUP_TABLE_OFFSET,
        VaultState.VAULT_ADMIN_AUTHORITY_OFFSET,
        VaultState.BASE_VAULT_AUTHORITY_OFFSET,
        VaultState.VAULT_FARM_OFFSET,
    };
    long slot = 2L;
    for (final int offset : valueOffsets) {
      final var changed = vaultStateData.clone();
      changed[offset] ^= 0x01;
      final var updated = initial.createIfChanged(++slot, changed);
      assertNotSame(initial, updated, () -> "change at offset " + offset + " went undetected");
      assertEquals(slot, updated.slot(), () -> "slot not adopted for change at offset " + offset);
      // untouched key objects are REUSED, not reparsed into equal copies
      assertSame(initial.vaultFarm(), updated.vaultFarm(), () -> "farm reparsed by change at offset " + offset);
      assertSame(initial.vaultLookupTable(), updated.vaultLookupTable(),
          () -> "lookup table reparsed by change at offset " + offset);
    }
    for (final int offset : keyOffsets) {
      final var changed = vaultStateData.clone();
      changed[offset] ^= 0x01;
      final var updated = initial.createIfChanged(++slot, changed);
      assertNotSame(initial, updated, () -> "change at offset " + offset + " went undetected");
      assertEquals(slot, updated.slot(), () -> "slot not adopted for change at offset " + offset);
    }
  }

  @Test
  void reserveParsingStopsAtTheFirstEmptySlot() {
    // an independent count of leading non-zero allocation keys as the oracle
    int expected = 0;
    for (int offset = VaultState.VAULT_ALLOCATION_STRATEGY_OFFSET; ; offset += VaultAllocation.BYTES) {
      if (PublicKey.readPubKey(vaultStateData, offset).equals(PublicKey.NONE)) {
        break;
      }
      ++expected;
    }
    assertTrue(expected > 0, "fixture should hold at least one reserve allocation");

    final var reserves = KaminoVaultContext.parseReserveKeys(vaultStateData);
    assertEquals(expected, reserves.length);
    for (final var reserve : reserves) {
      assertNotNull(reserve);
      assertNotEquals(PublicKey.NONE, reserve);
    }

    // a fully packed table parses whole without walking off the end; the bytes
    // after the table are made non-zero so an off-by-one read cannot masquerade
    // as the empty-slot terminator
    final var packed = vaultStateData.clone();
    int offset = VaultState.VAULT_ALLOCATION_STRATEGY_OFFSET;
    for (int i = 0; i < VaultState.VAULT_ALLOCATION_STRATEGY_LEN; ++i, offset += VaultAllocation.BYTES) {
      packed[offset] = (byte) (i + 1);
    }
    packed[offset] = (byte) 0xFF;
    assertEquals(VaultState.VAULT_ALLOCATION_STRATEGY_LEN, KaminoVaultContext.parseReserveKeys(packed).length);
  }
}
