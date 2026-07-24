package systems.glam.sdk.proxy;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.ix.proxy.DynamicAccountConfig;
import systems.glam.sdk.GlamVaultAccounts;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class CachedDynamicGlamAccountFactoryTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    bytes[31] = 7;
    return PublicKey.createPubKey(bytes);
  }

  private static DynamicAccountConfig config(final String name, final int index, final boolean writable) {
    return new DynamicAccountConfig(name, index, writable, false);
  }

  /// Routes each dynamic-account name through setAccount into a live array:
  /// the mapped slot must hold exactly the meta that name stands for.
  @Test
  void everyDynamicAccountNameRoutesToItsAccount() {
    final var proxyProgram = key(77);
    final var extAuthority = AccountMeta.createRead(key(78));
    final var factory = DynamicGlamAccountFactory.createFactory(Map.of(proxyProgram, extAuthority), 16);
    assertNotNull(factory);

    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);
    final var cpiProgram = AccountMeta.createRead(key(79));
    final var feePayer = AccountMeta.createWritableSigner(FEE_PAYER);

    final var mapped = new AccountMeta[7];
    factory.apply(config("glam_state", 0, false)).setAccount(mapped, proxyProgram, cpiProgram, feePayer, vaultAccounts);
    factory.apply(config("glam_state", 1, true)).setAccount(mapped, proxyProgram, cpiProgram, feePayer, vaultAccounts);
    factory.apply(config("glam_vault", 2, false)).setAccount(mapped, proxyProgram, cpiProgram, feePayer, vaultAccounts);
    factory.apply(config("glam_vault", 3, true)).setAccount(mapped, proxyProgram, cpiProgram, feePayer, vaultAccounts);
    factory.apply(config("glam_signer", 4, true)).setAccount(mapped, proxyProgram, cpiProgram, feePayer, vaultAccounts);
    factory.apply(config("cpi_program", 5, false)).setAccount(mapped, proxyProgram, cpiProgram, feePayer, vaultAccounts);
    factory.apply(config("integration_authority", 6, false)).setAccount(mapped, proxyProgram, cpiProgram, feePayer, vaultAccounts);

    assertSame(vaultAccounts.readGlamState(), mapped[0]);
    assertSame(vaultAccounts.writeGlamState(), mapped[1]);
    assertSame(vaultAccounts.readVault(), mapped[2]);
    assertSame(vaultAccounts.writeVault(), mapped[3]);
    assertSame(feePayer, mapped[4]);
    assertSame(cpiProgram, mapped[5]);
    assertSame(extAuthority, mapped[6]);

    // the state/vault routing is by distinct record type, not shared logic
    assertInstanceOf(IndexedReadState.class, factory.apply(config("glam_state", 0, false)));
    assertInstanceOf(IndexedWriteState.class, factory.apply(config("glam_state", 0, true)));
    assertInstanceOf(IndexedReadVault.class, factory.apply(config("glam_vault", 0, false)));
    assertInstanceOf(IndexedWriteVault.class, factory.apply(config("glam_vault", 0, true)));
    assertInstanceOf(IndexedExtAuthority.class, factory.apply(config("integration_authority", 0, false)));
  }

  @Test
  void unknownDynamicAccountNamesAreRejected() {
    final var factory = DynamicGlamAccountFactory.createFactory(Map.of(), 4);
    assertThrows(IllegalStateException.class, () -> factory.apply(config("glam_escrow", 0, false)));
    assertThrows(IllegalStateException.class, () -> factory.apply(config(null, 0, false)));
  }

  /// Equal configs must share one cached instance; the cache key includes
  /// the index and the read/write direction.
  @Test
  void equalConfigsShareOneCachedInstance() {
    final var factory = DynamicGlamAccountFactory.createFactory(Map.of(), 4);
    final var first = factory.apply(config("glam_state", 1, false));
    assertSame(first, factory.apply(config("glam_state", 1, false)));
    assertNotSame(first, factory.apply(config("glam_state", 2, false)));
    assertNotSame(first, factory.apply(config("glam_state", 1, true)));
  }
}
