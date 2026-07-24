package systems.glam.services.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import systems.glam.services.tests.LogCapture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class FileUtilsTests {

  private static final PublicKey KEY = fromBase58Encoded("3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7");

  @Test
  void pathsAppendTheKeyAndExtension(@TempDir final Path tempDir) {
    assertEquals(tempDir.resolve(KEY.toBase58() + ".dat"), FileUtils.resolveAccountPath(tempDir, KEY));
    assertEquals(tempDir.resolve(KEY.toBase58() + ".dat.gz"), FileUtils.resolveCompressedAccountPath(tempDir, KEY));
  }

  @Test
  void keysParseFromEitherExtensionAndNothingElse(@TempDir final Path tempDir) {
    assertEquals(KEY, FileUtils.parseKey(FileUtils.resolveAccountPath(tempDir, KEY)));
    assertEquals(KEY, FileUtils.parseKey(FileUtils.resolveCompressedAccountPath(tempDir, KEY)));
    assertNull(FileUtils.parseKey(tempDir.resolve(KEY.toBase58() + ".json")));
    assertNull(FileUtils.parseKey(tempDir.resolve("README")));
  }

  @Test
  void compressedAccountDataRoundTrips(@TempDir final Path tempDir) throws IOException {
    final byte[] data = {1, 2, 3, 4, 5};
    FileUtils.writeCompressedAccountData(tempDir, KEY, data);
    final var path = FileUtils.resolveCompressedAccountPath(tempDir, KEY);
    assertTrue(Files.exists(path));
    try (final var in = new GZIPInputStream(Files.newInputStream(path))) {
      assertArrayEquals(data, in.readAllBytes());
    }

    final var accountData = FileUtils.readAccountData(path);
    assertEquals(KEY, accountData.pubKey());
    assertArrayEquals(data, accountData.data());
    assertTrue(accountData.isAccount());
  }

  @Test
  void uncompressedAccountDataReadsDirectly(@TempDir final Path tempDir) throws IOException {
    final byte[] data = {9, 8, 7};
    final var path = FileUtils.resolveAccountPath(tempDir, KEY);
    Files.write(path, data);

    final var accountData = FileUtils.readAccountData(path);
    assertEquals(KEY, accountData.pubKey());
    assertArrayEquals(data, accountData.data());
  }

  @Test
  void unknownExtensionsReadAsEmptyWithoutTouchingTheFile(@TempDir final Path tempDir) throws IOException {
    final var path = tempDir.resolve(KEY.toBase58() + ".json");
    Files.write(path, new byte[]{1});
    final var accountData = FileUtils.readAccountData(path);
    assertFalse(accountData.isAccount());
    assertEquals(PublicKey.NONE, accountData.pubKey());
    assertTrue(Files.exists(path));
  }

  @Test
  void corruptCompressedDataIsLoggedDeletedAndEmpty(@TempDir final Path tempDir) throws IOException {
    final var path = FileUtils.resolveCompressedAccountPath(tempDir, KEY);
    Files.write(path, new byte[]{0x1f, (byte) 0x8b, 3, 4}); // truncated gzip stream

    try (final var logs = LogCapture.attach(FileUtils.class.getName())) {
      final var accountData = FileUtils.readAccountData(path);
      assertFalse(accountData.isAccount());
      logs.assertLogged("Failed to read compressed account data");
    }
    assertFalse(Files.exists(path), "the invalid file must be deleted");
  }

  @Test
  void unreadableUncompressedDataIsLoggedDeletedAndEmpty(@TempDir final Path tempDir) throws IOException {
    // a directory where a file is expected: readAllBytes throws IOException
    final var path = FileUtils.resolveAccountPath(tempDir, KEY);
    Files.createDirectory(path);

    try (final var logs = LogCapture.attach(FileUtils.class.getName())) {
      final var accountData = FileUtils.readAccountData(path);
      assertFalse(accountData.isAccount());
      logs.assertLogged("Failed to read account data");
    }
    // an empty directory is deletable, so the cleanup removes it too
    assertFalse(Files.exists(path));
  }

  @Test
  void compressIfNeededSwapsTheFileInPlace(@TempDir final Path tempDir) throws IOException {
    final byte[] data = {4, 4, 4, 4};
    final var uncompressed = FileUtils.resolveAccountPath(tempDir, KEY);
    Files.write(uncompressed, data);
    final var accountData = FileUtils.readAccountData(uncompressed);

    FileUtils.compressIfNeeded(tempDir, uncompressed, accountData);
    assertFalse(Files.exists(uncompressed), "the uncompressed original must be deleted");
    final var compressed = FileUtils.resolveCompressedAccountPath(tempDir, KEY);
    assertTrue(Files.exists(compressed));
    assertArrayEquals(data, FileUtils.readAccountData(compressed).data());

    // an already-compressed path is left alone
    final byte[] before = Files.readAllBytes(compressed);
    FileUtils.compressIfNeeded(tempDir, compressed, FileUtils.readAccountData(compressed));
    assertArrayEquals(before, Files.readAllBytes(compressed));
  }

  @Test
  void aCompressedWriteTruncatesThePreviousContent(@TempDir final Path tempDir) throws IOException {
    final var path = FileUtils.resolveCompressedAccountPath(tempDir, KEY);
    try (final var out = new GZIPOutputStream(Files.newOutputStream(path))) {
      out.write(new byte[64]);
    }
    final byte[] data = {1, 2};
    FileUtils.writeCompressedAccountData(tempDir, KEY, data);
    assertArrayEquals(data, FileUtils.readAccountData(path).data());
  }

  @Test
  void anUndeletableInvalidFileIsLoggedAndLeftInPlace(@TempDir final Path tempDir) throws IOException {
    // a non-empty directory fails both the read and the cleanup delete
    final var datDir = FileUtils.resolveAccountPath(tempDir, KEY);
    Files.createDirectories(datDir.resolve("occupant"));
    try (final var logs = LogCapture.attach(FileUtils.class.getName())) {
      assertFalse(FileUtils.readAccountData(datDir).isAccount());
      logs.assertLogged("Failed to read account data");
      logs.assertLogged("Failed to delete invalid account data");
    }
    assertTrue(Files.exists(datDir));

    final var gzDir = FileUtils.resolveCompressedAccountPath(tempDir, KEY);
    Files.createDirectories(gzDir.resolve("occupant"));
    try (final var logs = LogCapture.attach(FileUtils.class.getName())) {
      assertFalse(FileUtils.readAccountData(gzDir).isAccount());
      logs.assertLogged("Failed to read compressed account data");
      logs.assertLogged("Failed to delete invalid account data");
    }
    assertTrue(Files.exists(gzDir));
  }

  /// Found by AccountDataFuzz: a small compressed file expanding without limit
  /// hung the reader in allocation until memory died. The 10MiB Solana account
  /// cap bounds the read; past it the file is corrupt — logged and deleted,
  /// like any other unreadable account file.
  @org.junit.jupiter.api.Test
  void aDecompressionBombIsRejectedNotInflated(@org.junit.jupiter.api.io.TempDir final java.nio.file.Path dir)
      throws java.io.IOException {
    final var key = software.sava.core.accounts.PublicKey.createPubKey(new byte[32]);
    final var bomb = dir.resolve(key.toBase58() + ".dat.gz");
    try (final var in = FileUtilsTests.class.getResourceAsStream("/fuzz/accountData/bomb-16m-zeros.gz")) {
      java.nio.file.Files.copy(java.util.Objects.requireNonNull(in), bomb);
    }
    final var result = FileUtils.readAccountData(bomb);
    org.junit.jupiter.api.Assertions.assertSame(AccountData.EMPTY, result);
    org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(bomb),
        "the oversized file was not deleted");
  }

  /// The cap is a boundary, not a blanket shrink: data at exactly the Solana
  /// account limit still round-trips.
  @org.junit.jupiter.api.Test
  void dataAtExactlyTheAccountCapRoundTrips(@org.junit.jupiter.api.io.TempDir final java.nio.file.Path dir)
      throws java.io.IOException {
    final var key = software.sava.core.accounts.PublicKey.createPubKey(new byte[32]);
    final byte[] max = new byte[FileUtils.MAX_ACCOUNT_DATA_LENGTH];
    FileUtils.writeCompressedAccountData(dir, key, max);
    final var read = FileUtils.readAccountData(FileUtils.resolveCompressedAccountPath(dir, key));
    org.junit.jupiter.api.Assertions.assertEquals(max.length, read.data().length);
  }
}
