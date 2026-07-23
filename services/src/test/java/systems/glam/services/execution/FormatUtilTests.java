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

  private static software.sava.rpc.json.http.response.TxSimulation simulation(final List<String> logs) {
    return new software.sava.rpc.json.http.response.TxSimulation(
        null, null, java.util.OptionalLong.empty(), 0, logs,
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, java.util.OptionalInt.empty(), null, null
    );
  }

  private static software.sava.core.tx.Transaction transaction() {
    return software.sava.core.tx.Transaction.createTx(key(8), List.of(ix()));
  }

  @Test
  void formatInstructionsJoinsWithCommas() {
    final var single = FormatUtil.formatInstruction(ix());
    assertEquals(single + ",\n" + single, FormatUtil.formatInstructions(List.of(ix(), ix())));
  }

  @Test
  void simulationsFormatTheTransactionAndLogs() {
    final var tx = transaction();
    final var formatted = FormatUtil.formatSimulation("simulated", tx, simulation(List.of("Program log: x")));
    assertTrue(formatted.contains("\"event\": \"simulated\""), formatted);
    assertTrue(formatted.contains(tx.base64EncodeToString()), formatted);
    assertTrue(formatted.contains("Program log: x"), formatted);
    assertTrue(formatted.contains(key(1).toBase58()), formatted);
  }

  private static software.sava.services.solana.transactions.TransactionResult result(
      final software.sava.rpc.json.http.response.TransactionError error,
      final software.sava.rpc.json.http.response.TxSimulation simulation,
      final String formattedSig) {
    return new software.sava.services.solana.transactions.TransactionResult(
        List.of(ix()), false, 200_000, 42L,
        transaction(), 123, simulation, error, "sig", formattedSig
    );
  }

  @Test
  void successfulResultsFormatTheFeeAndSig() {
    final var formatted = FormatUtil.formatTransactionResult(result(null, null, "https://solscan.io/tx/abc"));
    assertTrue(formatted.contains("\"cuBudget\": 200000"), formatted);
    assertTrue(formatted.contains("\"cuPrice\": 42"), formatted);
    assertTrue(formatted.contains("\"sig\": \"https://solscan.io/tx/abc\""), formatted);
    assertTrue(formatted.contains("\"numInstructions\": 1"), formatted);

    // a blank signature renders as a JSON null, unquoted; so does an absent one
    final var unsigned = FormatUtil.formatTransactionResult(result(null, null, " "));
    assertTrue(unsigned.contains("\"sig\": null"), unsigned);
    final var absent = FormatUtil.formatTransactionResult(result(null, null, null));
    assertTrue(absent.contains("\"sig\": null"), absent);
    // the fee is rendered as a plain decimal with trailing zeros stripped,
    // and the whole block is indented one space
    assertTrue(unsigned.startsWith(" "), unsigned);
  }

  @Test
  void aGlamInstructionErrorNamesTheProtocolError() {
    final var error = new software.sava.rpc.json.http.response.TransactionError.InstructionError(
        0, new software.sava.rpc.json.http.response.IxError.Custom(48_000L));
    final var formatted = FormatUtil.formatTransactionResult(
        result(error, simulation(List.of("Program log: failed")), null));
    assertTrue(formatted.contains("\"failedIx\""), formatted);
    assertTrue(formatted.contains("\"index\": 0"), formatted);
    // 48000 is UnauthorizedSigner in the glam protocol error table
    assertTrue(formatted.toLowerCase().contains("signer"), formatted);
    assertTrue(formatted.contains("Program log: failed"), formatted);
  }

  @Test
  void unknownErrorsFallBackToTheirToString() {
    // a custom code outside the glam table renders the raw error
    final var unknownCode = new software.sava.rpc.json.http.response.TransactionError.InstructionError(
        0, new software.sava.rpc.json.http.response.IxError.Custom(1L));
    final var custom = FormatUtil.formatTransactionResult(result(unknownCode, null, null));
    assertTrue(custom.contains("\"failedIx\""), custom);
    assertTrue(custom.contains("Custom"), custom);

    // a non-custom instruction error renders the raw transaction error too
    final var generic = new software.sava.rpc.json.http.response.TransactionError.InstructionError(
        0, new software.sava.rpc.json.http.response.IxError.GenericError());
    final var genericFormatted = FormatUtil.formatTransactionResult(result(generic, null, null));
    assertTrue(genericFormatted.contains("\"failedIx\""), genericFormatted);
    assertTrue(genericFormatted.contains("GenericError"), genericFormatted);

    // a transaction-level error has no failed instruction to name; the error
    // block is indented one space like the success form
    final var expired = FormatUtil.formatTransactionResult(
        result(software.sava.services.solana.transactions.TransactionResult.EXPIRED, null, null));
    assertFalse(expired.contains("failedIx"), expired);
    assertTrue(expired.contains("EXPIRED"), expired);
    assertTrue(expired.startsWith(" "), expired);
  }

  @Test
  void durationsFormatWithoutThePtPrefix() {
    assertEquals("1M30S", FormatUtil.formatDuration(java.time.Duration.ofSeconds(90)));
    assertEquals("2M", FormatUtil.formatDuration(
        java.time.Duration.ofSeconds(150), java.time.temporal.ChronoUnit.MINUTES));
    assertEquals("3S", FormatUtil.formatDuration(3_444L, java.time.temporal.ChronoUnit.SECONDS));
  }

  @Test
  void fixedLengthStringsTrimTrailingPadding() {
    final byte[] padded = new byte[8];
    padded[0] = 'G';
    padded[1] = 'M';
    padded[2] = ' ';
    assertEquals("GM", FormatUtil.parseFixLengthString(padded));
    final byte[] empty = new byte[4];
    assertEquals(new String(empty), FormatUtil.parseFixLengthString(empty));
    assertEquals("GLAM", FormatUtil.parseFixLengthString(
        "GLAM".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    // a single leading character: the scan must include index zero
    final byte[] single = new byte[4];
    single[0] = 'X';
    assertEquals("X", FormatUtil.parseFixLengthString(single));
  }
}
