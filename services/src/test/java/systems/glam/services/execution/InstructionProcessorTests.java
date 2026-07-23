package systems.glam.services.execution;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionResult;
import systems.glam.sdk.GlamAccounts;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/// Drives InstructionProcessorImpl against a scripted InstructionService: each
/// call's batch size is recorded and the next scripted result returned, so the
/// halving ladder, the account-limit batching, and the error ladder are exact.
final class InstructionProcessorTests {

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) (id >> 8);
    bytes[1] = (byte) id;
    bytes[31] = 11;
    return PublicKey.createPubKey(bytes);
  }

  private static Instruction instruction(final int id, final int numAccounts) {
    final var accounts = new ArrayList<AccountMeta>(numAccounts);
    for (int i = 0; i < numAccounts; ++i) {
      accounts.add(AccountMeta.createRead(key(id * 100 + i)));
    }
    return Instruction.createInstruction(AccountMeta.createInvoked(key(id)), accounts, new byte[]{(byte) id});
  }

  private static TransactionResult result(final List<Instruction> batch, final TransactionError error) {
    final var tx = Transaction.createTx(key(9_999), batch.isEmpty() ? List.of(instruction(99, 1)) : List.copyOf(batch));
    return new TransactionResult(List.copyOf(batch), false, 200_000, 1L, tx, 100, null, error, "sig", null);
  }

  /// Scripted service: records each batch size, pops the next result factory.
  private static final class ScriptedService {

    final List<Integer> batchSizes = new ArrayList<>();
    final List<Function<List<Instruction>, TransactionResult>> script = new ArrayList<>();

    InstructionService service() {
      return (InstructionService) Proxy.newProxyInstance(
          InstructionService.class.getClassLoader(),
          new Class<?>[]{InstructionService.class},
          (proxy, method, args) -> {
            if (method.getName().equals("processInstructions")) {
              @SuppressWarnings("unchecked") final var batch = (List<Instruction>) args[1];
              batchSizes.add(batch.size());
              return script.remove(0).apply(batch);
            }
            throw new UnsupportedOperationException(method.getName());
          }
      );
    }
  }

  private static final class RecordingNotify {

    final List<String> messages = new ArrayList<>();

    software.sava.services.core.net.http.NotifyClient client() {
      return msg -> {
        messages.add(msg);
        return List.<CompletableFuture<String>>of();
      };
    }
  }

  private static InstructionProcessorImpl processor(final ScriptedService service, final RecordingNotify notify) {
    return new InstructionProcessorImpl(
        null, service.service(), new BigDecimal("0.001"), notify.client(), 1.2, 3
    );
  }

  private static final Function<List<Instruction>, Transaction> FACTORY =
      batch -> Transaction.createTx(key(9_999), batch);

  @Test
  void aSuccessfulBatchProcessesOnceAndDrainsTheList() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, null));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    try (final var log = systems.glam.services.tests.LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      // null lookup tables are allowed and must not be dereferenced
      assertTrue(processor(service, notify).processInstructions("test", instructions, null, FACTORY));
      log.assertLogged("test Success");
    }
    assertEquals(List.of(2), service.batchSizes);
    assertTrue(instructions.isEmpty(), "the processed batch must drain the caller's list");
    assertTrue(notify.messages.isEmpty(), () -> notify.messages.toString());
  }

  @Test
  void aSizeLimitedBatchIsDroppedAndTheRemainderHalves() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // failed batches are DROPPED, never retried here: the InstructionService
    // owns retries below the send, and a failed result means the caller
    // re-fetches and rebuilds. The halved batch size governs the remainder.
    // Three 30-account instructions split at the 64-account limit into a
    // 2-instruction prefix; that prefix fails on size and is dropped, and the
    // remaining instruction goes alone under the halved size.
    service.script.add(batch -> result(batch, TransactionResult.SIZE_LIMIT_EXCEEDED));
    service.script.add(batch -> result(batch, null));
    final var instructions = new ArrayList<>(List.of(
        instruction(1, 30), instruction(2, 30), instruction(3, 30)));

    try (final var log = systems.glam.services.tests.LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      assertTrue(processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
      // each size-limit drop is warned, never silent
      log.assertLogged("test Failed");
    }
    assertEquals(List.of(2, 1), service.batchSizes);
    assertTrue(instructions.isEmpty());
    assertTrue(notify.messages.isEmpty(), () -> notify.messages.toString());
  }

  @Test
  void anOddBatchSizeHalvesRoundingUp() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // nine instructions: two 40-account ones split into single-instruction
    // batches, so the first batch is [1]; it fails on size and is dropped, and
    // 9 halves to 5 (odd rounds up), bounding the remaining eight as 5 then 3
    service.script.add(batch -> result(batch, TransactionResult.SIZE_LIMIT_EXCEEDED));
    service.script.add(batch -> result(batch, null));
    service.script.add(batch -> result(batch, null));
    final var instructions = new ArrayList<>(List.of(instruction(1, 40), instruction(2, 40)));
    for (int i = 3; i <= 9; ++i) {
      instructions.add(instruction(i, 1));
    }

    assertTrue(processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
    assertEquals(List.of(1, 5, 3), service.batchSizes);
    assertTrue(instructions.isEmpty());
  }

  @Test
  void lookupTableKeysCountAgainstTheAccountLimit() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // 61 table keys + the compute budget program + 4 accounts = 66 > 64:
    // even a single small instruction cannot fit next to that many tables
    final var tables = new ArrayList<PublicKey>(61);
    for (int i = 0; i < 61; ++i) {
      tables.add(key(5_000 + i));
    }
    final var instructions = new ArrayList<>(List.of(instruction(1, 4)));

    final var thrown = assertThrows(IllegalStateException.class, () ->
        processor(service, notify).processInstructions("test", instructions, tables, FACTORY));
    assertTrue(thrown.getMessage().contains("\"numTables\": 61"), thrown.getMessage());

    // exactly at the limit: compute budget + 63 accounts = 64 fits
    final var fits = new ArrayList<>(List.of(instruction(2, 63)));
    service.script.add(batch -> result(batch, null));
    assertTrue(processor(service, notify).processInstructions("test", fits, List.of(), FACTORY));
    assertEquals(List.of(1), service.batchSizes);
  }

  @Test
  void anErrorNotifiesAndStops() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, TransactionResult.EXPIRED));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    assertFalse(processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
    assertEquals(1, notify.messages.size());
    assertTrue(notify.messages.getFirst().contains("test Failed"), notify.messages.getFirst());
  }

  @Test
  void aSingleInstructionOverTheSizeLimitCannotHalve() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, TransactionResult.SIZE_LIMIT_EXCEEDED));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));

    // one instruction cannot be split further: report and stop
    assertFalse(processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
    assertEquals(List.of(1), service.batchSizes);
    assertEquals(1, notify.messages.size());
  }

  @Test
  void onlyAStaleMintPriceRetriesQuietly() throws InterruptedException {
    // three near-misses of the quiet-retry path: every one must page
    record Case(PublicKey program, IxError ixError) {
    }
    final var cases = List.of(
        // right program, wrong code
        new Case(GlamAccounts.MAIN_NET.mintProgram(), new IxError.Custom(48_000L)),
        // wrong program, right code
        new Case(key(500), new IxError.Custom(51_102L)),
        // right program, non-custom error
        new Case(GlamAccounts.MAIN_NET.mintProgram(), new IxError.GenericError())
    );
    for (final var testCase : cases) {
      final var service = new ScriptedService();
      final var notify = new RecordingNotify();
      final var failedIx = Instruction.createInstruction(
          AccountMeta.createInvoked(testCase.program()),
          List.of(AccountMeta.createRead(key(7))),
          new byte[]{7}
      );
      service.script.add(batch -> {
        final var tx = Transaction.createTx(key(9_999), List.of(failedIx));
        return new TransactionResult(
            List.copyOf(batch), false, 200_000, 1L, tx, 100, null,
            new TransactionError.InstructionError(0, testCase.ixError()), "sig", null);
      });
      final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

      assertFalse(processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
      assertEquals(1, notify.messages.size(), testCase::toString);
    }
  }

  @Test
  void duplicateAccountsAcrossInstructionsAreCountedOnce() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, null));
    // two instructions over the SAME 40 accounts: distinct count stays 40,
    // so they ride one transaction
    final var shared = instruction(1, 40).accounts();
    final var a = Instruction.createInstruction(AccountMeta.createInvoked(key(1)), shared, new byte[]{1});
    final var b = Instruction.createInstruction(AccountMeta.createInvoked(key(2)), shared, new byte[]{2});
    final var instructions = new ArrayList<>(List.of(a, b));

    assertTrue(processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
    assertEquals(List.of(2), service.batchSizes);
  }

  @Test
  void aStalePriceOnTheMintProgramRetriesQuietly() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // the failed instruction targets the glam mint program with PriceTooOld
    final var mintIx = Instruction.createInstruction(
        AccountMeta.createInvoked(GlamAccounts.MAIN_NET.mintProgram()),
        List.of(AccountMeta.createRead(key(7))),
        new byte[]{7}
    );
    service.script.add(batch -> {
      final var tx = Transaction.createTx(key(9_999), List.of(mintIx));
      return new TransactionResult(
          List.copyOf(batch), false, 200_000, 1L, tx, 100, null,
          new TransactionError.InstructionError(0, new IxError.Custom(51_102L)), "sig", null);
    });
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    // false = re-fetch and retry, and nobody is paged for a stale oracle
    try (final var log = systems.glam.services.tests.LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      assertFalse(processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
      log.assertLogged("test Failed");
    }
    assertTrue(notify.messages.isEmpty(), () -> notify.messages.toString());
  }

  @Test
  void aSingleInstructionOverTheAccountLimitIsFatal() {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    final var instructions = new ArrayList<>(List.of(instruction(1, 70)));

    final var thrown = assertThrows(IllegalStateException.class, () ->
        processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
    assertTrue(thrown.getMessage().contains("Instruction Exceeds Account Limit"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("\"numTables\": 0"), thrown.getMessage());
    assertEquals(1, notify.messages.size());
    assertTrue(service.batchSizes.isEmpty(), "nothing may be sent");
  }

  @Test
  void batchesSplitAtTheAccountLimit() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, null));
    service.script.add(batch -> result(batch, null));
    // 40 + 40 distinct accounts: the second instruction cannot join the first
    final var instructions = new ArrayList<>(List.of(instruction(1, 40), instruction(2, 40)));

    assertTrue(processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
    assertEquals(List.of(1, 1), service.batchSizes);
    assertTrue(instructions.isEmpty());
  }

  @Test
  void aServiceFailureIsLoggedNotifiedAndRethrown() {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> {
      throw new IllegalStateException("rpc down");
    });
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));

    try (final var log = systems.glam.services.tests.LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      final var thrown = assertThrows(IllegalStateException.class, () ->
          processor(service, notify).processInstructions("test", instructions, List.of(), FACTORY));
      assertEquals("rpc down", thrown.getMessage());
      log.assertLogged("Failed to process test instructions.");
    }
    assertEquals(1, notify.messages.size());
    assertTrue(notify.messages.getFirst().contains("Failed to process test instructions."),
        notify.messages.getFirst());
  }
}
