package systems.glam.services.execution;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
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

  private static final PublicKey FEE_PAYER = key(9_999);

  /// The processor asks the transaction processor for one thing here: whose transaction it builds.
  private static TransactionProcessor feePayer() {
    return (TransactionProcessor) Proxy.newProxyInstance(
        TransactionProcessor.class.getClassLoader(),
        new Class<?>[]{TransactionProcessor.class},
        (proxy, method, args) -> {
          if (method.getName().equals("feePayer")) {
            return FEE_PAYER;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  private static InstructionProcessorImpl processor(final ScriptedService service, final RecordingNotify notify) {
    return new InstructionProcessorImpl(
        feePayer(), service.service(), new BigDecimal("0.001"), notify.client(), 1.2, 3
    );
  }

  @Test
  void aSuccessfulBatchProcessesOnceAndDrainsTheList() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, null));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    try (final var log = systems.glam.services.tests.LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      assertTrue(processor(service, notify).processInstructions("test", instructions));
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
      assertTrue(processor(service, notify).processInstructions("test", instructions));
      // each size-limit drop is warned, never silent
      log.assertLogged("test Failed");
    }
    assertEquals(List.of(2, 1), service.batchSizes);
    assertTrue(instructions.isEmpty());
    assertTrue(notify.messages.isEmpty(), () -> notify.messages.toString());
  }

  /// A size refusal of the whole remaining list leaves nothing to halve: the call ends with
  /// false and a page, and no empty transaction is sent in the dropped batch's place.
  @Test
  void aSizeRefusalOfTheWholeListEndsTheCall() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, TransactionResult.SIZE_LIMIT_EXCEEDED));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    try (final var log = systems.glam.services.tests.LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      assertFalse(processor(service, notify).processInstructions("test", instructions));
      log.assertLogged("test Failed");
    }
    // one send, of the whole list; the scripted service has nothing for a second call
    assertEquals(List.of(2), service.batchSizes);
    assertTrue(instructions.isEmpty(), "the refused batch is dropped");
    assertEquals(1, notify.messages.size(), () -> notify.messages.toString());
    assertTrue(notify.messages.getFirst().contains("test Failed"), notify.messages.getFirst());
  }

  /// Once the batch size is down to one, a size refusal cannot halve further: the call ends
  /// with false and a page, and whatever the caller had left stays in its list for the rebuild.
  @Test
  void aSizeRefusalThatCannotHalveLeavesTheRemainderToTheCaller() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // 60-account instructions ride alone whatever the batch size, so the ladder is 4 -> 2 -> 1
    // while instructions remain: the third refusal is at batch size 1 with one still queued
    service.script.add(batch -> result(batch, TransactionResult.SIZE_LIMIT_EXCEEDED));
    service.script.add(batch -> result(batch, TransactionResult.SIZE_LIMIT_EXCEEDED));
    service.script.add(batch -> result(batch, TransactionResult.SIZE_LIMIT_EXCEEDED));
    service.script.add(batch -> result(batch, null));
    final var instructions = new ArrayList<>(List.of(
        instruction(1, 60), instruction(2, 60), instruction(3, 60), instruction(4, 60)));

    assertFalse(processor(service, notify).processInstructions("test", instructions));
    assertEquals(List.of(1, 1, 1), service.batchSizes, "nothing may be sent after the unhalvable refusal");
    assertEquals(1, instructions.size(), "the remaining instruction is the caller's to rebuild");
    assertEquals(1, notify.messages.size(), () -> notify.messages.toString());
  }

  /// An even batch size halves exactly: 4 -> 2, so the remainder goes out as a pair then a single.
  @Test
  void anEvenBatchSizeHalves() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, TransactionResult.SIZE_LIMIT_EXCEEDED));
    service.script.add(batch -> result(batch, null));
    service.script.add(batch -> result(batch, null));
    // fee payer + program + 61 accounts = 63: the next instruction's program fits but its
    // account does not, so the first splits off alone and is refused, halving 4 to 2 for the rest
    final var instructions = new ArrayList<>(List.of(
        instruction(1, 61), instruction(2, 1), instruction(3, 1), instruction(4, 1)));

    assertTrue(processor(service, notify).processInstructions("test", instructions));
    assertEquals(List.of(1, 2, 1), service.batchSizes);
    assertTrue(instructions.isEmpty());
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

    assertTrue(processor(service, notify).processInstructions("test", instructions));
    assertEquals(List.of(1, 5, 3), service.batchSizes);
    assertTrue(instructions.isEmpty());
  }

  /// A v1 transaction's 64 accounts are the fee payer, each program invoked and each
  /// instruction account, once each; nothing is reserved for a ComputeBudget program,
  /// which rides as ConfigValues rather than an account.
  @Test
  void theFeePayerAndTheProgramCountAgainstTheAccountLimit() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // fee payer + program + 63 accounts = 65 > 64: a single instruction just under
    // the raw limit still cannot be sent
    final var instructions = new ArrayList<>(List.of(instruction(1, 63)));

    final var thrown = assertThrows(IllegalStateException.class, () ->
        processor(service, notify).processInstructions("test", instructions));
    assertTrue(thrown.getMessage().contains("\"numAccounts\": 63"), thrown.getMessage());
    assertTrue(service.batchSizes.isEmpty(), "nothing may be sent");

    // exactly at the limit: fee payer + program + 62 accounts = 64 fits
    final var fits = new ArrayList<>(List.of(instruction(2, 62)));
    service.script.add(batch -> result(batch, null));
    assertTrue(processor(service, notify).processInstructions("test", fits));
    assertEquals(List.of(1), service.batchSizes);
  }

  @Test
  void theFeePayerAmongTheAccountsIsCountedOnce() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // 62 other accounts + the fee payer as the 63rd + the program = 64: fits, because the
    // fee payer's own slot is the one the instruction names
    final var accounts = new ArrayList<>(instruction(1, 62).accounts());
    accounts.add(AccountMeta.createWritableSigner(FEE_PAYER));
    final var payerSigned = Instruction.createInstruction(AccountMeta.createInvoked(key(1)), accounts, new byte[]{1});
    final var instructions = new ArrayList<>(List.of(payerSigned));
    service.script.add(batch -> result(batch, null));

    assertTrue(processor(service, notify).processInstructions("test", instructions));
    assertEquals(List.of(1), service.batchSizes);

    // two instructions of one program share its slot: program + 31 + 31 accounts + payer = 64
    final var sameProgram = new ArrayList<>(List.of(
        Instruction.createInstruction(AccountMeta.createInvoked(key(3)), instruction(4, 31).accounts(), new byte[]{4}),
        Instruction.createInstruction(AccountMeta.createInvoked(key(3)), instruction(5, 31).accounts(), new byte[]{5})));
    service.script.add(batch -> result(batch, null));
    assertTrue(processor(service, notify).processInstructions("test", sameProgram));
    assertEquals(List.of(1, 2), service.batchSizes);
  }

  /// A full transaction still takes an instruction that names only accounts it already
  /// carries: the limit is on distinct accounts, not on instructions.
  @Test
  void anInstructionAddingNoNewAccountJoinsAFullTransaction() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, null));
    // fee payer + program + 62 accounts = 64: full after the first instruction; the second
    // is the same program over the same accounts, so it adds no key and rides along
    final var shared = instruction(1, 62).accounts();
    final var first = Instruction.createInstruction(AccountMeta.createInvoked(key(1)), shared, new byte[]{1});
    final var second = Instruction.createInstruction(AccountMeta.createInvoked(key(1)), shared, new byte[]{2});
    final var instructions = new ArrayList<>(List.of(first, second));

    assertTrue(processor(service, notify).processInstructions("test", instructions));
    assertEquals(List.of(2), service.batchSizes);
    assertTrue(instructions.isEmpty());

    // one new key on top of a full transaction opens the next one
    final var third = Instruction.createInstruction(AccountMeta.createInvoked(key(1)), instruction(3, 1).accounts(), new byte[]{3});
    final var overflow = new ArrayList<>(List.of(first, third));
    service.script.add(batch -> result(batch, null));
    service.script.add(batch -> result(batch, null));
    assertTrue(processor(service, notify).processInstructions("test", overflow));
    assertEquals(List.of(2, 1, 1), service.batchSizes);
  }

  /// Ravina's result keeps the batch list it was handed, not a copy, and the processor clears
  /// that list once the batch is done: the report must be taken first, so the log and the page
  /// carry the batch's real instruction count. The fixture aliases the list the way ravina does.
  @Test
  void theReportCountsTheBatchBeforeItIsCleared() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // the result over the batch reference itself, as ravina builds it; result(...) copies
    service.script.add(batch -> new TransactionResult(
        batch, false, 200_000, 1L, Transaction.createTx(FEE_PAYER, List.copyOf(batch)), 100, null, null, "sig", null));
    service.script.add(batch -> new TransactionResult(
        batch, false, 200_000, 1L, Transaction.createTx(FEE_PAYER, List.copyOf(batch)), 100, null,
        TransactionResult.EXPIRED, "sig", null));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    try (final var log = systems.glam.services.tests.LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      assertTrue(processor(service, notify).processInstructions("test", instructions));
      log.assertLogged("\"numInstructions\": 2");
    }
    assertTrue(instructions.isEmpty());

    final var failing = new ArrayList<>(List.of(instruction(3, 2), instruction(4, 2), instruction(5, 2)));
    assertFalse(processor(service, notify).processInstructions("test", failing));
    assertEquals(1, notify.messages.size(), () -> notify.messages.toString());
    assertTrue(notify.messages.getFirst().contains("\"numInstructions\": 3"), notify.messages.getFirst());
  }

  @Test
  void anErrorNotifiesAndStops() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> result(batch, TransactionResult.EXPIRED));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    assertFalse(processor(service, notify).processInstructions("test", instructions));
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
    assertFalse(processor(service, notify).processInstructions("test", instructions));
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

      assertFalse(processor(service, notify).processInstructions("test", instructions));
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

    assertTrue(processor(service, notify).processInstructions("test", instructions));
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
      assertFalse(processor(service, notify).processInstructions("test", instructions));
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
        processor(service, notify).processInstructions("test", instructions));
    assertTrue(thrown.getMessage().contains("Instruction Exceeds Account Limit"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("\"numAccounts\": 70"), thrown.getMessage());
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

    assertTrue(processor(service, notify).processInstructions("test", instructions));
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
          processor(service, notify).processInstructions("test", instructions));
      assertEquals("rpc down", thrown.getMessage());
      log.assertLogged("Failed to process test instructions.");
    }
    assertEquals(1, notify.messages.size());
    assertTrue(notify.messages.getFirst().contains("Failed to process test instructions."),
        notify.messages.getFirst());
  }
}
