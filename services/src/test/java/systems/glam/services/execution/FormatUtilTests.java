package systems.glam.services.execution;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class FormatUtilTests {

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    return PublicKey.createPubKey(bytes);
  }

  private static Instruction ix() {
    return Instruction.createInstruction(
        AccountMeta.createInvoked(key(1)),
        List.of(
            AccountMeta.createWritableSigner(key(2)),
            AccountMeta.createRead(key(3)),
            AccountMeta.createWrite(key(4)),
            AccountMeta.createReadOnlySigner(key(5))
        ),
        new byte[]{1, 2, 3}
    );
  }

  @Test
  void formatAccountsEncodesFlags() {
    assertEquals(
        key(2).toBase58() + ":ws\",\""
            + key(3).toBase58() + ":r\",\""
            + key(4).toBase58() + ":w\",\""
            + key(5).toBase58() + ":rs",
        FormatUtil.formatAccounts(ix())
    );
  }

  @Test
  void formatInstructionIsJsonShaped() {
    final var formatted = FormatUtil.formatInstruction(ix());
    assertTrue(formatted.contains("\"p\": \"" + key(1).toBase58() + '"'), formatted);
    assertTrue(formatted.contains("\"d\": \"" + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}) + '"'), formatted);
  }

  @Test
  void formatInstructionExceptionIncludesTables() {
    final var withTables = FormatUtil.formatInstructionException(
        "simFailure", List.of(ix()), List.of(key(9))
    );
    assertTrue(withTables.contains("\"e\": \"simFailure\""), withTables);
    assertTrue(withTables.contains(key(9).toBase58()), withTables);
    // the instructions must actually be rendered, not just the envelope
    assertTrue(withTables.contains("\"p\": \"" + key(1).toBase58() + '"'), withTables);

    final var withoutTables = FormatUtil.formatInstructionException("simFailure", List.of(ix()));
    assertTrue(withoutTables.contains("\"t\": [\"\"]"), withoutTables);
    // an empty collection renders like a null one
    final var emptyTables = FormatUtil.formatInstructionException("simFailure", List.of(ix()), List.of());
    assertTrue(emptyTables.contains("\"t\": [\"\"]"), emptyTables);
  }

  @Test
  void formatLogs() {
    assertEquals("", FormatUtil.formatLogs(List.of()));
    final var formatted = FormatUtil.formatLogs(List.of("log one", "log two"));
    assertEquals("\n    \"log one\",\n    \"log two\"", formatted);
  }
}
