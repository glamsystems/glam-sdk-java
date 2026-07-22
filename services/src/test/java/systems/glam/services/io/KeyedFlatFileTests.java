package systems.glam.services.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.idl.clients.core.gen.SerDe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class KeyedFlatFileTests {

  private static final int KEY_LEN = 4;
  private static final int ENTRY_SIZE = 8;

  /// 4-byte key + 4-byte value
  private record Entry(int key, int value) implements SerDe {

    static byte[] keyBytes(final int key) {
      final byte[] bytes = new byte[KEY_LEN];
      bytes[0] = (byte) key;
      bytes[1] = (byte) (key >> 8);
      bytes[2] = (byte) (key >> 16);
      bytes[3] = (byte) (key >> 24);
      return bytes;
    }

    @Override
    public int l() {
      return ENTRY_SIZE;
    }

    @Override
    public int write(final byte[] data, final int offset) {
      System.arraycopy(keyBytes(key), 0, data, offset, KEY_LEN);
      System.arraycopy(keyBytes(value), 0, data, offset + KEY_LEN, KEY_LEN);
      return ENTRY_SIZE;
    }
  }

  private static Entry readEntry(final byte[] fileData, final int index) {
    final int offset = index * ENTRY_SIZE;
    return new Entry(readInt(fileData, offset), readInt(fileData, offset + KEY_LEN));
  }

  private static int readInt(final byte[] data, final int offset) {
    return (data[offset] & 0xFF)
        | (data[offset + 1] & 0xFF) << 8
        | (data[offset + 2] & 0xFF) << 16
        | (data[offset + 3] & 0xFF) << 24;
  }

  private static KeyedFlatFile<Entry> createFile(final Path tempDir) {
    return KeyedFlatFile.createFlatFile(ENTRY_SIZE, tempDir.resolve("sub").resolve("entries.dat"));
  }

  /// Every entry point takes the lock in a try/finally. A leaked lock blocks
  /// every other caller and no result assertion can see it, so each test that
  /// mutates the file checks the lock was handed back.
  private static void assertUnlocked(final KeyedFlatFile<Entry> file) {
    assertFalse(((KeyedFlatFileImpl<Entry>) file).lock.isLocked());
  }

  @Test
  void appendPersistsEntriesInOrder(@TempDir final Path tempDir) throws Exception {
    try (final var file = createFile(tempDir)) {
      file.appendEntry(new Entry(1, 11));
      file.appendEntry(new Entry(2, 22));

      final byte[] data = Files.readAllBytes(file.filePath());
      assertEquals(2 * ENTRY_SIZE, data.length);
      assertEquals(new Entry(1, 11), readEntry(data, 0));
      assertEquals(new Entry(2, 22), readEntry(data, 1));
      assertUnlocked(file);
    }
  }

  @Test
  void deleteEntrySwapsLastIntoPlace(@TempDir final Path tempDir) throws Exception {
    try (final var file = createFile(tempDir)) {
      file.appendEntry(new Entry(1, 11));
      file.appendEntry(new Entry(2, 22));
      file.appendEntry(new Entry(3, 33));

      assertEquals(1, file.deleteEntry(Entry.keyBytes(1), null));

      final byte[] data = Files.readAllBytes(file.filePath());
      assertEquals(2 * ENTRY_SIZE, data.length);
      // the last entry moved into the deleted slot; order is not preserved
      assertEquals(new Entry(3, 33), readEntry(data, 0));
      assertEquals(new Entry(2, 22), readEntry(data, 1));
      assertUnlocked(file);
    }
  }

  @Test
  void deleteLastEntryTruncatesWithoutSwap(@TempDir final Path tempDir) throws Exception {
    try (final var file = createFile(tempDir)) {
      file.appendEntry(new Entry(1, 11));
      file.appendEntry(new Entry(2, 22));

      assertEquals(1, file.deleteEntry(Entry.keyBytes(2), null));

      final byte[] data = Files.readAllBytes(file.filePath());
      assertEquals(ENTRY_SIZE, data.length);
      assertEquals(new Entry(1, 11), readEntry(data, 0));
    }
  }

  @Test
  void deleteEntryOnEmptyFileAndMissingKey(@TempDir final Path tempDir) throws Exception {
    try (final var file = createFile(tempDir)) {
      assertEquals(0, file.deleteEntry(Entry.keyBytes(1), null));
      file.appendEntry(new Entry(1, 11));
      assertEquals(0, file.deleteEntry(Entry.keyBytes(9), null));
      assertEquals(ENTRY_SIZE, Files.readAllBytes(file.filePath()).length);
    }
  }

  @Test
  void deleteEntryRemovesEveryMatch(@TempDir final Path tempDir) throws Exception {
    try (final var file = createFile(tempDir)) {
      // duplicate key at the head AND the tail: the tail duplicate is swapped
      // into the deleted slot and must be re-examined, not skipped
      file.appendEntry(new Entry(1, 11));
      file.appendEntry(new Entry(2, 22));
      file.appendEntry(new Entry(1, 33));

      assertEquals(2, file.deleteEntry(Entry.keyBytes(1), null));

      final byte[] data = Files.readAllBytes(file.filePath());
      assertEquals(ENTRY_SIZE, data.length);
      assertEquals(new Entry(2, 22), readEntry(data, 0));
    }
  }

  @Test
  void deleteEntryRemovesConsecutiveMatches(@TempDir final Path tempDir) throws Exception {
    try (final var file = createFile(tempDir)) {
      file.appendEntry(new Entry(1, 11));
      file.appendEntry(new Entry(1, 22));
      file.appendEntry(new Entry(1, 33));

      assertEquals(3, file.deleteEntry(Entry.keyBytes(1), null));
      assertEquals(0, Files.readAllBytes(file.filePath()).length);
    }
  }

  @Test
  void writeEntriesReplacesFileContents(@TempDir final Path tempDir) throws Exception {
    try (final var file = createFile(tempDir)) {
      file.appendEntry(new Entry(9, 99));
      file.writeEntries(List.of(new Entry(1, 11), new Entry(2, 22)));

      final byte[] data = Files.readAllBytes(file.filePath());
      assertEquals(2 * ENTRY_SIZE, data.length);
      assertEquals(new Entry(1, 11), readEntry(data, 0));
      assertEquals(new Entry(2, 22), readEntry(data, 1));
    }
  }

  @Test
  void overwriteFileTruncatesTrailingData(@TempDir final Path tempDir) throws Exception {
    try (final var file = createFile(tempDir)) {
      file.appendEntry(new Entry(1, 11));
      file.appendEntry(new Entry(2, 22));

      final byte[] single = new byte[ENTRY_SIZE];
      new Entry(7, 77).write(single, 0);
      file.overwriteFile(single);

      final byte[] data = Files.readAllBytes(file.filePath());
      assertEquals(ENTRY_SIZE, data.length);
      assertEquals(new Entry(7, 77), readEntry(data, 0));
      assertUnlocked(file);
    }
  }

  @Test
  void closeIsIdempotent(@TempDir final Path tempDir) {
    final var file = createFile(tempDir);
    file.close();
    assertUnlocked(file);
    assertDoesNotThrow(file::close);
    assertUnlocked(file);
  }

  @Test
  void appendingToAReopenedFileSeeksToTheEnd(@TempDir final Path tempDir) throws Exception {
    // a cache file outlives the process: a fresh channel starts at position 0,
    // so appending without seeking to the end overwrites the first entry
    final var filePath = tempDir.resolve("sub").resolve("entries.dat");
    try (final var file = KeyedFlatFile.<Entry>createFlatFile(ENTRY_SIZE, filePath)) {
      file.appendEntry(new Entry(1, 11));
      file.appendEntry(new Entry(2, 22));
    }

    try (final var reopened = KeyedFlatFile.<Entry>createFlatFile(ENTRY_SIZE, filePath)) {
      reopened.appendEntry(new Entry(3, 33));
    }

    final byte[] data = Files.readAllBytes(filePath);
    assertEquals(3 * ENTRY_SIZE, data.length);
    assertEquals(new Entry(1, 11), readEntry(data, 0));
    assertEquals(new Entry(2, 22), readEntry(data, 1));
    assertEquals(new Entry(3, 33), readEntry(data, 2));
  }
}
