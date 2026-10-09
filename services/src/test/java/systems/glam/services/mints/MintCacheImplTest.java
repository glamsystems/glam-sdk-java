package systems.glam.services.mints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import systems.glam.services.io.KeyedFlatFile;
import systems.glam.services.io.KeyedFlatFileTestProbe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static systems.glam.services.tests.Workers.FIXTURE_DEADLINE_MILLIS;

final class MintCacheImplTest {

  private static final SolanaAccounts SOLANA_ACCOUNTS = SolanaAccounts.MAIN_NET;

  @Test
  void testBasicSetGet(@TempDir final Path tempDir) {
    final var cacheFile = tempDir.resolve("mint_cache.dat");
    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {

      final var mintKey = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
      final var mintContext = MintContext.createContext(
          SOLANA_ACCOUNTS,
          mintKey,
          6,
          SOLANA_ACCOUNTS.tokenProgram()
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
      final var mintContext1 = MintContext.createContext(
          SOLANA_ACCOUNTS,
          mintKey,
          6,
          SOLANA_ACCOUNTS.tokenProgram()
      );
      final var mintContext2 = MintContext.createContext(
          SOLANA_ACCOUNTS,
          mintKey,
          9,
          SOLANA_ACCOUNTS.tokenProgram()
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
      cache.setGet(MintContext.createContext(
          SOLANA_ACCOUNTS,
          mintKey1,
          6,
          SOLANA_ACCOUNTS.tokenProgram()
      ));

      cache.setGet(MintContext.createContext(
          SOLANA_ACCOUNTS,
          mintKey2,
          9,
          SOLANA_ACCOUNTS.token2022Program()
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
    // built as MintCache.createCache builds it for a new file, with the flat file kept in
    // reach: the writers must be able to see, and hand back, a lock that setGet leaves held
    final var flatFile = KeyedFlatFile.<MintContext>createFlatFile(MintContext.BYTES, cacheFile);
    final var cache = new MintCacheImpl(new ConcurrentHashMap<>(), flatFile);
    final var executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      final var finished = new Semaphore(0);
      final var errors = new ArrayList<Throwable>();

      for (int t = 0; t < numConcurrentWrites; t++) {
        final int threadId = t;
        executor.execute(() -> {
          try {
            final byte[] keyBytes = new byte[32];
            ByteUtil.putInt32LE(keyBytes, 0, threadId);
            final var mintKey = PublicKey.createPubKey(keyBytes);

            final var mintContext = MintContext.createContext(
                SOLANA_ACCOUNTS,
                mintKey,
                threadId % 256,
                threadId % 2 == 0 ? SOLANA_ACCOUNTS.tokenProgram() : SOLANA_ACCOUNTS.token2022Program()
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
            // A writer that returns still holding the file's lock has leaked it, and every
            // other writer parks behind it in lock(), which takes no timeout and ignores
            // interrupts: nothing could end them, and the executor could never terminate.
            // Only the holder can release it, so it records the leak and hands the lock back.
            if (KeyedFlatFileTestProbe.releaseHeldLock(flatFile)) {
              synchronized (errors) {
                errors.add(new AssertionError("setGet returned holding the cache file's lock"));
              }
            }
            finished.release();
          }
        });
      }

      // The appends queue on the file's lock and each is forced to disk, so the whole batch
      // can outlast one fixture deadline (~600ms here). A stall cannot: it is a gap in which
      // no writer finishes, so the deadline bounds each step rather than the batch.
      for (int done = 0; done < numConcurrentWrites; ++done) {
        final int finishedSoFar = done;
        assertTrue(finished.tryAcquire(FIXTURE_DEADLINE_MILLIS, MILLISECONDS),
            () -> "no writer finished inside the deadline after " + finishedSoFar + " of " + numConcurrentWrites);
      }
      executor.shutdown();
      assertTrue(executor.awaitTermination(FIXTURE_DEADLINE_MILLIS, MILLISECONDS), "a writer outlived its task");

      if (!errors.isEmpty()) {
        errors.getFirst().printStackTrace();
        fail("Concurrent access test failed with " + errors.size() + " errors");
      }

      assertFalse(KeyedFlatFileTestProbe.isLocked(flatFile), "the cache file's lock is still held");
      assertEquals(34L * numConcurrentWrites, Files.size(cacheFile));
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(FIXTURE_DEADLINE_MILLIS, MILLISECONDS);
      // close() takes the file's lock with no timeout: a lock still held here may never be
      // released, so the file is closed only when it is free (an assertion above has
      // already failed otherwise)
      if (!KeyedFlatFileTestProbe.isLocked(flatFile)) {
        cache.close();
      }
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
    final var mintContext = MintContext.createContext(
        SOLANA_ACCOUNTS,
        mintKey,
        6,
        SOLANA_ACCOUNTS.tokenProgram()
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
  void aDeleteOnlyReportsTheEntryItActuallyRemovedFromDisk(@TempDir final Path tempDir) {
    final var mintKey = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    final var cacheFile = tempDir.resolve("mint_cache.dat");
    try (final var cacheA = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {
      cacheA.setGet(new MintContext(AccountMeta.createRead(mintKey), 6, 0, SOLANA_ACCOUNTS.readTokenProgram()));
      // a second cache over the same file removes the persistent entry
      try (final var cacheB = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {
        assertNotNull(cacheB.delete(mintKey));
      }
      // A's in-memory map still holds the mint, but the disk entry is gone:
      // the delete must not claim to have removed what was no longer persisted
      assertNull(cacheA.delete(mintKey));
    }
  }

  @Test
  void aClosedCacheRefusesNewEntries(@TempDir final Path tempDir) {
    final var mintKey1 = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    final var mintKey2 = PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112");
    final var cacheFile = tempDir.resolve("mint_cache.dat");
    final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile);
    cache.setGet(new MintContext(AccountMeta.createRead(mintKey1), 6, 0, SOLANA_ACCOUNTS.readTokenProgram()));
    cache.close();
    // the close must release the underlying channel; persisting to it afterwards fails
    assertThrows(RuntimeException.class,
        () -> cache.setGet(new MintContext(AccountMeta.createRead(mintKey2), 9, 1, SOLANA_ACCOUNTS.readToken2022Program())));
  }

  @Test
  void testDelete(@TempDir final Path tempDir) throws Exception {
    final var mintKey1 = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    final var mintKey2 = PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112");
    final var mintKey3 = PublicKey.fromBase58Encoded("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB");

    final var cacheFile = tempDir.resolve("mint_cache.dat");
    try (final var cache = MintCache.createCache(SOLANA_ACCOUNTS, cacheFile)) {

      cache.setGet(new MintContext(AccountMeta.createRead(mintKey1), 6, 0, SOLANA_ACCOUNTS.readTokenProgram()));
      cache.setGet(new MintContext(AccountMeta.createRead(mintKey2), 9, 1, SOLANA_ACCOUNTS.readToken2022Program()));
      cache.setGet(new MintContext(AccountMeta.createRead(mintKey3), 6, 0, SOLANA_ACCOUNTS.readTokenProgram()));

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

  @Test
  void mintContextScalesAmountsDownToRawUnits() {
    final var context = MintContext.createContext(
        software.sava.core.accounts.SolanaAccounts.MAIN_NET,
        software.sava.core.accounts.PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112"),
        6, 0
    );
    // setScale truncates to the mint's decimals and returns the WHOLE units
    assertEquals(1L, context.setScale(new java.math.BigDecimal("1.23456789")));
    assertEquals(2L, context.setScale(new java.math.BigDecimal("2.9999999")));
    assertEquals(0L, context.setScale(new java.math.BigDecimal("0.0000009")));
    assertEquals(MintContext.BYTES, context.l());
  }
}
