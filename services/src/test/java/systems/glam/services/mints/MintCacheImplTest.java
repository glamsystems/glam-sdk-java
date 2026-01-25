package systems.glam.services.mints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class MintCacheImplTest {

  private static final SolanaAccounts SOLANA_ACCOUNTS = SolanaAccounts.MAIN_NET;

  @Test
  void testBasicSetGet(@TempDir final Path tempDir) {
    final var cacheFile = tempDir.resolve("mint_cache.dat");
    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {

      final var mintKey = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
      final var mintContext = new MintContext(
          AccountMeta.createRead(mintKey),
          6,
          SOLANA_ACCOUNTS.readTokenProgram()
      );

      final var result = cache.setGet(mintContext);
      assertSame(mintContext, result);

      final var retrieved = cache.get(mintKey);
      assertSame(mintContext, retrieved);
    }
  }

  @Test
  void testSetGetReturnsExisting(@TempDir final Path tempDir) {
    final var cacheFile = tempDir.resolve("mint_cache.dat");
    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {

      final var mintKey = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
      final var mintContext1 = new MintContext(
          AccountMeta.createRead(mintKey),
          6,
          SOLANA_ACCOUNTS.readTokenProgram()
      );
      final var mintContext2 = new MintContext(
          AccountMeta.createRead(mintKey),
          9,
          SOLANA_ACCOUNTS.readTokenProgram()
      );

      final var result1 = cache.setGet(mintContext1);
      assertSame(mintContext1, result1);

      final var result2 = cache.setGet(mintContext2);
      assertSame(mintContext1, result2);
    }
  }

  @Test
  void testFilePersistence(@TempDir final Path tempDir) throws Exception {
    final var cacheFile = tempDir.resolve("mint_cache.dat");

    final var mintKey1 = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    final var mintKey2 = PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112");

    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {
      cache.setGet(new MintContext(
          AccountMeta.createRead(mintKey1),
          6,
          SOLANA_ACCOUNTS.readTokenProgram()
      ));

      cache.setGet(new MintContext(
          AccountMeta.createRead(mintKey2),
          9,
          SOLANA_ACCOUNTS.readToken2022Program()
      ));
    }

    assertTrue(Files.exists(cacheFile));
    assertEquals(34 * 2, Files.size(cacheFile));

    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {
      final var retrieved1 = cache.get(mintKey1);
      assertNotNull(retrieved1);
      assertEquals(mintKey1, retrieved1.mint());
      assertEquals(6, retrieved1.decimals());
      assertEquals(SOLANA_ACCOUNTS.tokenProgram(), retrieved1.readTokenProgram().publicKey());

      final var retrieved2 = cache.get(mintKey2);
      assertNotNull(retrieved2);
      assertEquals(mintKey2, retrieved2.mint());
      assertEquals(9, retrieved2.decimals());
      assertEquals(SOLANA_ACCOUNTS.token2022Program(), retrieved2.readTokenProgram().publicKey());
    }
  }

  @Test
  void testConcurrentAccess(@TempDir final Path tempDir) throws Exception {
    final int numConcurrentWrites = 128;
    final var cacheFile = tempDir.resolve("mint_cache.dat");
    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile);
         final var executor = Executors.newVirtualThreadPerTaskExecutor()) {

      final var latch = new CountDownLatch(numConcurrentWrites);
      final var errors = new ArrayList<Throwable>();

      for (int t = 0; t < numConcurrentWrites; t++) {
        final int threadId = t;
        executor.execute(() -> {
          try {
            final byte[] keyBytes = new byte[32];
            ByteUtil.putInt32LE(keyBytes, 0, threadId);
            final var mintKey = PublicKey.createPubKey(keyBytes);

            final var mintContext = new MintContext(
                AccountMeta.createRead(mintKey),
                threadId % 256,
                threadId % 2 == 0 ? SOLANA_ACCOUNTS.readTokenProgram() : SOLANA_ACCOUNTS.readToken2022Program()
            );

            final var result = cache.setGet(mintContext);
            assertNotNull(result);
            assertEquals(mintKey, result.mint());

            final var retrieved = cache.get(mintKey);
            assertNotNull(retrieved);
            assertEquals(mintKey, retrieved.mint());
          } catch (final Throwable e) {
            synchronized (errors) {
              errors.add(e);
            }
          } finally {
            latch.countDown();
          }
        });
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      if (!errors.isEmpty()) {
        errors.getFirst().printStackTrace();
        fail("Concurrent access test failed with " + errors.size() + " errors");
      }

      assertEquals(34L * numConcurrentWrites, Files.size(cacheFile));
    }
  }

  @Test
  void testGetNonExistent(@TempDir final Path tempDir) {
    final var cacheFile = tempDir.resolve("mint_cache.dat");
    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {
      final var mintKey = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
      assertNull(cache.get(mintKey));
    }
  }

  @Test
  void testCloseAndReopen(@TempDir final Path tempDir) {
    final var cacheFile = tempDir.resolve("mint_cache.dat");

    final var mintKey = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    final var mintContext = new MintContext(
        AccountMeta.createRead(mintKey),
        6,
        SOLANA_ACCOUNTS.readTokenProgram()
    );

    try (final var cache1 = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {
      cache1.setGet(mintContext);
      cache1.close();
      assertDoesNotThrow(cache1::close);
    }

    try (final var cache2 = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {
      final var retrieved = cache2.get(mintKey);
      assertNotNull(retrieved);
      assertEquals(mintKey, retrieved.mint());
      assertEquals(6, retrieved.decimals());
    }
  }

  @Test
  void testDelete(@TempDir final Path tempDir) throws Exception {
    final var mintKey1 = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    final var mintKey2 = PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112");
    final var mintKey3 = PublicKey.fromBase58Encoded("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB");

    final var cacheFile = tempDir.resolve("mint_cache.dat");
    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {

      cache.setGet(new MintContext(AccountMeta.createRead(mintKey1), 6, SOLANA_ACCOUNTS.readTokenProgram()));
      cache.setGet(new MintContext(AccountMeta.createRead(mintKey2), 9, SOLANA_ACCOUNTS.readToken2022Program()));
      cache.setGet(new MintContext(AccountMeta.createRead(mintKey3), 6, SOLANA_ACCOUNTS.readTokenProgram()));

      assertEquals(34 * 3, Files.size(cacheFile));

      final var deleted = cache.delete(mintKey2);
      assertNotNull(deleted);
      assertEquals(mintKey2, deleted.mint());
      assertEquals(9, deleted.decimals());

      assertNull(cache.get(mintKey2));

      assertNotNull(cache.get(mintKey1));
      assertNotNull(cache.get(mintKey3));

      assertEquals(34 * 2, Files.size(cacheFile));

      assertNull(cache.delete(mintKey2));
    }

    try (final var cache2 = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {
      assertNull(cache2.get(mintKey2));
      assertNotNull(cache2.get(mintKey1));
      assertNotNull(cache2.get(mintKey3));
    }
  }
}
