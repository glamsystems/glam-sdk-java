package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.rpc.Filter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.accounts.lookup.AddressLookupTable.LOOKUP_TABLE_META_SIZE;

final class GlamVaultAccountsTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey VAULT_KEY = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");

  @Test
  void createAccountsRolesAndFlags() {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);

    assertSame(GlamAccounts.MAIN_NET, vaultAccounts.glamAccounts());
    assertEquals(FEE_PAYER, vaultAccounts.feePayer());
    assertEquals(STATE_KEY, vaultAccounts.glamStateKey());
    // vault PDA derived from the state key, known-good from GlamPDATests
    assertEquals(VAULT_KEY, vaultAccounts.vaultPublicKey());
    assertEquals(vaultAccounts.vaultPDA().publicKey(), vaultAccounts.vaultPublicKey());

    // the read/write metas must reference the same key with opposite write flags
    assertEquals(AccountMeta.createRead(STATE_KEY), vaultAccounts.readGlamState());
    assertEquals(AccountMeta.createWrite(STATE_KEY), vaultAccounts.writeGlamState());
    assertEquals(AccountMeta.createRead(VAULT_KEY), vaultAccounts.readVault());
    assertEquals(AccountMeta.createWrite(VAULT_KEY), vaultAccounts.writeVault());
    assertFalse(vaultAccounts.readGlamState().write());
    assertTrue(vaultAccounts.writeGlamState().write());
    assertFalse(vaultAccounts.readVault().write());
    assertTrue(vaultAccounts.writeVault().write());
  }

  @Test
  void explicitGlamAccountsOverload() {
    final var staging = GlamVaultAccounts.createAccounts(GlamAccounts.MAIN_NET_STAGING, FEE_PAYER, STATE_KEY);
    assertSame(GlamAccounts.MAIN_NET_STAGING, staging.glamAccounts());
    // a different protocol program must derive a different vault PDA
    assertNotEquals(VAULT_KEY, staging.vaultPublicKey());
  }

  @Test
  void mintPDADelegatesWithShareClassId() {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);
    assertEquals(
        fromBase58Encoded("GBCZzkTU2enaarFqBxJ2Z16yk1Rpa2hq2SKrHAywUq9V"),
        vaultAccounts.mintPDA().publicKey()
    );
    assertEquals(
        vaultAccounts.mintPDA(0).publicKey(),
        vaultAccounts.mintPDA().publicKey()
    );
    assertNotEquals(
        vaultAccounts.mintPDA(0).publicKey(),
        vaultAccounts.mintPDA(1).publicKey()
    );
  }

  @Test
  void vaultTableFilterLayout() {
    final var configProgram = GlamAccounts.MAIN_NET.configProgram();
    final byte[] expectedKeys = new byte[PublicKey.PUBLIC_KEY_LENGTH * 3];
    STATE_KEY.write(expectedKeys, 0);
    VAULT_KEY.write(expectedKeys, PublicKey.PUBLIC_KEY_LENGTH);
    configProgram.write(expectedKeys, PublicKey.PUBLIC_KEY_LENGTH * 2);
    final var expected = Filter.createMemCompFilter(LOOKUP_TABLE_META_SIZE, expectedKeys);

    assertEquals(expected, GlamVaultAccounts.vaultTableFilter(STATE_KEY, VAULT_KEY, configProgram));

    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);
    assertEquals(expected, vaultAccounts.vaultTableFilter());

    // the derive variant computes the vault key from the state key itself
    assertEquals(
        expected,
        GlamVaultAccounts.deriveVaulKeyTableFilter(
            GlamAccounts.MAIN_NET.protocolProgram(), configProgram, STATE_KEY
        )
    );
  }

  @Test
  void activeTableFilters() {
    final var configProgram = GlamAccounts.MAIN_NET.configProgram();
    // active = deactivation slot still u64 max, prefixed by table type 1
    final byte[] notDeActivated = new byte[Integer.BYTES + Long.BYTES];
    ByteUtil.putInt32LE(notDeActivated, 0, 1);
    ByteUtil.putInt64LE(notDeActivated, Integer.BYTES, -1);
    final var expectedActive = Filter.createMemCompFilter(0, notDeActivated);
    final var expectedKeys = GlamVaultAccounts.vaultTableFilter(STATE_KEY, VAULT_KEY, configProgram);

    assertEquals(
        List.of(expectedActive, expectedKeys),
        GlamVaultAccounts.activeVaultTableFilters(STATE_KEY, VAULT_KEY, configProgram)
    );
    assertEquals(
        List.of(expectedActive, expectedKeys),
        GlamVaultAccounts.deriveVaultActiveTableFilters(
            GlamAccounts.MAIN_NET.protocolProgram(), configProgram, STATE_KEY
        )
    );
    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);
    assertEquals(List.of(expectedActive, expectedKeys), vaultAccounts.activeVaultTableFilters());
  }

  @Test
  void vaultTablePrefixKeys() {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);
    assertEquals(
        List.of(STATE_KEY, VAULT_KEY, GlamAccounts.MAIN_NET.configProgram()),
        vaultAccounts.vaultTablePrefixKeys()
    );
  }

  /// The system-program ix-mapper config (glam/mapping-configs-v1 shape),
  /// inlined so the test does not depend on the untracked download.
  private static final String SYSTEM_MAPPING_JSON = """
      {
        "program_id": "11111111111111111111111111111111",
        "proxy_program_id": "GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz",
        "instructions": [
          {
            "src_ix_name": "transfer",
            "src_discriminator": [2, 0, 0, 0],
            "dst_ix_name": "system_transfer",
            "dst_discriminator": [167, 164, 195, 155, 219, 152, 191, 230],
            "dynamic_accounts": [
              {"name": "glam_state", "index": 0, "writable": false, "signer": false},
              {"name": "glam_vault", "index": 1, "writable": true, "signer": false},
              {"name": "glam_signer", "index": 2, "writable": true, "signer": true}
            ],
            "static_accounts": [
              {"account": "11111111111111111111111111111111", "index": 3, "writable": false, "signer": false}
            ],
            "index_map": [-1, 4]
          }
        ]
      }""";

  @Test
  void loadMappingConfigsParsesOnlyReadableTopLevelJsonFiles(
      @org.junit.jupiter.api.io.TempDir final java.nio.file.Path dir) throws java.io.IOException {
    java.nio.file.Files.writeString(dir.resolve("system.json"), SYSTEM_MAPPING_JSON);
    // not a mapping config: wrong extension
    java.nio.file.Files.writeString(dir.resolve("README.txt"), "not a mapping");
    // the walk is depth 1 and regular-file only: a DIRECTORY whose name ends
    // in .json must be filtered before the parser reads it as a file
    java.nio.file.Files.createDirectory(dir.resolve("nested.json"));
    java.nio.file.Files.writeString(dir.resolve("nested.json").resolve("nested.json"), SYSTEM_MAPPING_JSON);
    // unreadable json is filtered, not crashed on
    final var locked = dir.resolve("locked.json");
    java.nio.file.Files.writeString(locked, SYSTEM_MAPPING_JSON);
    java.nio.file.Files.setPosixFilePermissions(locked, java.util.Set.of());
    try {
      final var configs = GlamVaultAccounts.loadMappingConfigs(dir);
      assertEquals(1, configs.size());
      final var config = configs.getFirst();
      final var systemProgram = fromBase58Encoded("11111111111111111111111111111111");
      assertTrue(
          config.programs().stream().anyMatch(meta -> meta.publicKey().equals(systemProgram)),
          "the mapped source program was not parsed");
      assertEquals(1, config.ixMapConfigs().size());
    } finally {
      java.nio.file.Files.setPosixFilePermissions(
          locked, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
    }
  }

  @Test
  void createMapperBuildsFromAMappingsDirectory(
      @org.junit.jupiter.api.io.TempDir final java.nio.file.Path dir) throws java.io.IOException {
    java.nio.file.Files.writeString(dir.resolve("system.json"), SYSTEM_MAPPING_JSON);
    final var invokedProxy = AccountMeta.createInvoked(
        fromBase58Encoded("GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz"));
    final var factory = systems.glam.sdk.proxy.DynamicGlamAccountFactory.createFactory(java.util.Map.of(), 8);
    assertNotNull(factory);
    final var mapper = GlamVaultAccounts.createMapper(invokedProxy, dir, factory);
    assertNotNull(mapper, "the directory overload did not build a mapper");
    final var direct = GlamVaultAccounts.createMapper(
        invokedProxy, GlamVaultAccounts.loadMappingConfigs(dir), factory);
    assertNotNull(direct, "the config-list overload did not build a mapper");
  }
}
