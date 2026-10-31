package systems.glam.services.execution;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.LatestBlockHash;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.rpc.json.http.response.TxSimulation;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.core.remote.load_balance.BalancedItem;
import software.sava.services.core.remote.load_balance.LoadBalancer;
import software.sava.services.core.request_capacity.CapacityConfig;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TransactionResult;
import software.sava.services.solana.config.ChainItemFormatter;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.CallWeights;
import software.sava.services.solana.remote.call.RpcCaller;
import software.sava.services.solana.transactions.SendLedger;
import software.sava.services.solana.transactions.SendNotAcceptedException;
import software.sava.services.solana.transactions.SendTxContext;
import software.sava.services.solana.transactions.SimulationFutures;
import software.sava.services.solana.transactions.TxMonitorConfig;
import software.sava.services.solana.transactions.TxMonitorService;
import software.sava.services.solana.transactions.TxMonitorStoppedException;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.tests.LogCapture;
import systems.glam.services.tests.Workers;

import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.UnknownServiceException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.rpc.json.http.request.Commitment.CONFIRMED;

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
  private static final ChainItemFormatter FORMATTER = ChainItemFormatter.createDefault();

  /// The processor asks the transaction processor for two things here: whose transaction it
  /// builds, and the formatter a page renders a signature with.
  private static TransactionProcessor feePayer() {
    return (TransactionProcessor) Proxy.newProxyInstance(
        TransactionProcessor.class.getClassLoader(),
        new Class<?>[]{TransactionProcessor.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "feePayer" -> FEE_PAYER;
          case "formatter" -> FORMATTER;
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  private static InstructionProcessorImpl processor(final ScriptedService service, final RecordingNotify notify) {
    return processor(service, notify, GlamAccounts.MAIN_NET.mintProgram());
  }

  private static InstructionProcessorImpl processor(final ScriptedService service,
                                                    final RecordingNotify notify,
                                                    final PublicKey mintProgram) {
    return processor(feePayer(), service.service(), notify, mintProgram);
  }

  /// A processor over `service`, whose transactions `transactionProcessor` builds and sends.
  private static InstructionProcessorImpl processor(final TransactionProcessor transactionProcessor,
                                                    final InstructionService service,
                                                    final RecordingNotify notify,
                                                    final PublicKey mintProgram) {
    return new InstructionProcessorImpl(
        transactionProcessor, service, mintProgram, new BigDecimal("0.001"), notify.client(), 1.2, 3
    );
  }

  /// The transaction's instruction at `failedIndex` fails with the protocol's PriceTooOld, which
  /// reaches a mint instruction through its pricing CPI; `programs` are the programs the
  /// transaction's instructions invoke, in order.
  private static Function<List<Instruction>, TransactionResult> stalePriceAt(final int failedIndex,
                                                                            final PublicKey... programs) {
    final var txInstructions = new ArrayList<Instruction>(programs.length);
    for (final var program : programs) {
      txInstructions.add(Instruction.createInstruction(
          AccountMeta.createInvoked(program),
          List.of(AccountMeta.createRead(key(7))),
          new byte[]{7}
      ));
    }
    return batch -> new TransactionResult(
        List.copyOf(batch), false, 200_000, 1L, Transaction.createTx(FEE_PAYER, txInstructions), 100, null,
        new TransactionError.InstructionError(failedIndex, new IxError.Custom(51_102L)), "sig", null);
  }

  private static Function<List<Instruction>, TransactionResult> stalePrice(final PublicKey program) {
    return stalePriceAt(0, program);
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

  /// The log and the page carry the batch's real instruction count although the processor clears
  /// the batch list once the batch is done. Since ravina's d0262bc a result copies the batch on
  /// construction, so no fixture can alias the list any more; formatting before the clear matters
  /// again only for a result that holds the caller's list.
  @Test
  void theReportCountsTheBatchBeforeItIsCleared() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    // handed the batch reference itself, which the result's constructor copies
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
  void aStalePriceInASingleInstructionBatchRetriesQuietly() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(stalePrice(GlamAccounts.MAIN_NET.mintProgram()));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));

    try (final var log = systems.glam.services.tests.LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      assertFalse(processor(service, notify).processInstructions("test", instructions));
      log.assertLogged(Level.WARNING, "test Failed");
    }
    assertTrue(notify.messages.isEmpty(), () -> notify.messages.toString());
    assertEquals(List.of(1), service.batchSizes);
  }

  @Test
  void aStalePriceRetriesQuietlyOnTheProcessorsOwnMintProgram() throws InterruptedException {
    final var stagingMint = GlamAccounts.MAIN_NET_STAGING.mintProgram();
    assertNotEquals(GlamAccounts.MAIN_NET.mintProgram(), stagingMint);
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(stalePrice(stagingMint));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    assertFalse(processor(service, notify, stagingMint).processInstructions("test", instructions));
    assertTrue(notify.messages.isEmpty(), () -> notify.messages.toString());
  }

  @Test
  void theStalePriceMustComeFromTheFailedInstruction() throws InterruptedException {
    final var mint = GlamAccounts.MAIN_NET.mintProgram();
    // the mint instruction is the one that failed: quiet
    final var quietService = new ScriptedService();
    final var quietNotify = new RecordingNotify();
    quietService.script.add(stalePriceAt(1, key(500), mint));
    assertFalse(processor(quietService, quietNotify)
        .processInstructions("test", new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)))));
    assertTrue(quietNotify.messages.isEmpty(), () -> quietNotify.messages.toString());

    // a mint instruction rides along, but another program failed with the same code: it pages
    final var pagedService = new ScriptedService();
    final var pagedNotify = new RecordingNotify();
    pagedService.script.add(stalePriceAt(1, mint, key(500)));
    assertFalse(processor(pagedService, pagedNotify)
        .processInstructions("test", new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)))));
    assertEquals(1, pagedNotify.messages.size());
  }

  /// The processor decodes a staging mint failure with the production protocol's error table, which
  /// is right only while both deployments number PriceTooOld the same.
  @Test
  void bothDeploymentsNumberPriceTooOldAlike() {
    assertEquals(
        systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError.PriceTooOld.INSTANCE.code(),
        systems.glam.sdk.idl.programs.glam.staging.protocol.gen.GlamProtocolError.PriceTooOld.INSTANCE.code()
    );
  }

  @Test
  void theFactoryServesTheDeploymentItIsGiven() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    final var processor = InstructionProcessor.createProcessor(
        feePayer(), service.service(), GlamAccounts.MAIN_NET_STAGING, new BigDecimal("0.001"), notify.client(), 1.2, 3
    );
    assertEquals(GlamAccounts.MAIN_NET_STAGING.mintProgram(), processor.mintProgram());
    service.script.add(stalePrice(GlamAccounts.MAIN_NET_STAGING.mintProgram()));

    assertFalse(processor.processInstructions("test", new ArrayList<>(List.of(instruction(1, 2)))));
    assertTrue(notify.messages.isEmpty(), () -> notify.messages.toString());
  }

  @Test
  void aStalePriceOnAnotherDeploymentsMintProgramPages() throws InterruptedException {
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(stalePrice(GlamAccounts.MAIN_NET.mintProgram()));
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    final var stagingProcessor = processor(service, notify, GlamAccounts.MAIN_NET_STAGING.mintProgram());
    assertFalse(stagingProcessor.processInstructions("test", instructions));
    assertEquals(1, notify.messages.size());
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

  // ------------------------------------------------------------- a stopped monitor --

  /// A real transaction monitor whose loop has ended: its wait for the epoch info fails, which
  /// stops it, and a stopped monitor fails every queue and refuses every call. That is the only
  /// way a consumer comes by a TxMonitorStoppedException, whose constructors are ravina's own.
  private static TxMonitorService stoppedMonitor() {
    final var epochInfoService = (EpochInfoService) Proxy.newProxyInstance(
        EpochInfoService.class.getClassLoader(),
        new Class<?>[]{EpochInfoService.class},
        (proxy, method, args) -> {
          if (method.getName().equals("awaitInitialized")) {
            throw new IllegalStateException("the epoch info never arrives");
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
    final var monitor = TxMonitorService.createService(
        ChainItemFormatter.createDefault(), null, epochInfoService, null, TxMonitorConfig.createDefault(), null
    );
    // Both loops, on this thread, through the public run(Executor): each wait fails, each
    // loop's finally stops its monitor, and run returns. Their stop logs are captured here, so
    // that the ERROR records they write do not reach the console.
    try (final var log = LogCapture.attach("software.sava.services.solana.transactions.TxCommitmentMonitorService")) {
      monitor.run(Runnable::run);
      // what the stop log says, and at which level, is ravina's policy, not pinned here
      assertFalse(log.messages().isEmpty(), "the stop is logged");
    }
    assertTrue(monitor.stopped());
    return monitor;
  }

  /// The failure a stopped monitor hands a caller whose transaction it will never poll: sent,
  /// outcome unknown.
  private static TxMonitorStoppedException stoppedWithPending(final TxMonitorService monitor) {
    final var transaction = Transaction.createTx(FEE_PAYER, List.of(instruction(1, 1)));
    final var sendTxContext = new SendTxContext(null, new CompletableFuture<>(), transaction, "base64", 4_242L, 0L);
    // a stopped monitor fails the queue at once, so its failure is readable without a wait
    return assertInstanceOf(
        TxMonitorStoppedException.class,
        monitor.queueResult(CONFIRMED, CONFIRMED, "sig", sendTxContext, true, false).exceptionNow()
    );
  }

  /// A monitor that stopped under the send ends the call as itself, after one page that names
  /// the transaction that may still land and its block hash's last valid height: the batch's
  /// outcome is unknown, a rebuild could execute it twice, and the service exits on the rethrow
  /// rather than re-fetching state and rebuilding as it does for a failed batch.
  @Test
  void aStoppedMonitorEndsTheCallAsItselfAndNamesTheTransactionThatMayStillLand() {
    final var stopped = stoppedWithPending(stoppedMonitor());
    assertTrue(stopped.sent());
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> {
      throw stopped;
    });
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));

    // the page renders the signature the way the formatter does, never bare
    final var formattedSig = FORMATTER.formatSig(stopped.sig());

    try (final var log = LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      final var thrown = assertThrows(TxMonitorStoppedException.class, () ->
          processor(service, notify).processInstructions("test", instructions));
      assertSame(stopped, thrown, "thrown as itself, not wrapped: the caller tells it apart by type");
      log.assertLogged(Level.SEVERE, "transaction " + formattedSig + " may still land until block height 4242 passes");
    }
    assertEquals(1, notify.messages.size());
    final var msg = notify.messages.getFirst();
    assertTrue(msg.contains(
        "Transaction monitor stopped; test transaction " + formattedSig
            + " may still land until block height 4242 passes, so these instructions must not be rebuilt before its status is known."
    ), msg);
    assertEquals(List.of(1), service.batchSizes, "nothing more is sent");
    assertEquals(1, instructions.size(), "the batch is left with the caller, not cleared as a failed batch is");
  }

  /// A call refused before an attempt, because the monitor had already stopped, sent nothing
  /// that can land, and the page says so instead of naming a transaction. The refusal is ravina's
  /// own, on the processor's own call: its service reads only the fee cap's sign and `stopped()`
  /// before refusing a first attempt, so it needs no other collaborator.
  @Test
  void aCallRefusedByAStoppedMonitorPagesThatNothingCanLand() {
    final var feePayer = feePayer();
    final var notify = new RecordingNotify();
    final var processor = processor(
        feePayer,
        InstructionService.createService(null, feePayer, null, null, stoppedMonitor()),
        notify,
        GlamAccounts.MAIN_NET.mintProgram()
    );
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));

    try (final var log = LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      final var thrown = assertThrows(TxMonitorStoppedException.class, () ->
          processor.processInstructions("test", instructions));
      assertFalse(thrown.sent());
      log.assertLogged(Level.SEVERE, "nothing sent for test can still land");
    }
    assertEquals(1, notify.messages.size());
    final var msg = notify.messages.getFirst();
    assertTrue(msg.contains("Transaction monitor stopped; nothing sent for test can still land."), msg);
    assertEquals(1, instructions.size());
  }

  /// A notify client that throws, as ravina's does once its executor has shut down, loses the
  /// page and nothing else: the call still ends with the monitor's exception, as itself and
  /// without the notify failure attached, and the lost page is logged beside the stop.
  @Test
  void aNotifyClientThatThrowsDoesNotReplaceTheStoppedMonitorException() {
    final var stopped = stoppedWithPending(stoppedMonitor());
    final var service = new ScriptedService();
    service.script.add(batch -> {
      throw stopped;
    });
    final software.sava.services.core.net.http.NotifyClient notify = msg -> {
      throw new RejectedExecutionException("the notify executor has shut down");
    };
    final var processor = new InstructionProcessorImpl(
        feePayer(), service.service(), GlamAccounts.MAIN_NET.mintProgram(), new BigDecimal("0.001"), notify, 1.2, 3
    );
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));

    try (final var log = LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      final var thrown = assertThrows(TxMonitorStoppedException.class, () ->
          processor.processInstructions("test", instructions));
      assertSame(stopped, thrown, "the stop is the caller's, whatever became of the page");
      assertEquals(0, thrown.getSuppressed().length, "not attached to an instance other waiters may hold");
      log.assertLogged(Level.SEVERE, "transaction " + FORMATTER.formatSig(stopped.sig()) + " may still land until block height 4242 passes");
      log.assertLogged(Level.SEVERE, "Failed to post the page: Transaction monitor stopped;");
    }
    assertEquals(List.of(1), service.batchSizes, "nothing more is sent");
    assertEquals(1, instructions.size());
  }

  /// The catch-all arm guards its page the same way: a notify client that throws loses the page,
  /// and the service failure is still what ends the call, as itself and with nothing attached.
  @Test
  void aNotifyClientThatThrowsDoesNotReplaceAServiceFailure() {
    final var service = new ScriptedService();
    final var failure = new IllegalStateException("rpc down");
    service.script.add(batch -> {
      throw failure;
    });
    final software.sava.services.core.net.http.NotifyClient notify = msg -> {
      throw new RejectedExecutionException("the notify executor has shut down");
    };
    final var processor = new InstructionProcessorImpl(
        feePayer(), service.service(), GlamAccounts.MAIN_NET.mintProgram(), new BigDecimal("0.001"), notify, 1.2, 3
    );
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));

    try (final var log = LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      final var thrown = assertThrows(IllegalStateException.class, () ->
          processor.processInstructions("test", instructions));
      assertSame(failure, thrown);
      assertEquals(0, thrown.getSuppressed().length);
      log.assertLogged(Level.SEVERE, "Failed to process test instructions.");
      log.assertLogged(Level.SEVERE, "Failed to post the page: Failed to process test instructions.");
    }
    assertEquals(1, instructions.size(), "a batch whose send threw heads the list");
  }

  // ------------------------------------------------------------- sends not accepted --

  /// Signs the transactions ravina's own service sends below: `SendTxContext.sig()` is derived
  /// from the fee payer's signature, so the fee payer those tests build for is this key.
  private static final Signer SIGNER = Signer.createFromPrivateKey(privateKey());

  private static byte[] privateKey() {
    final byte[] bytes = new byte[Signer.KEY_LENGTH];
    for (int i = 0; i < bytes.length; ++i) {
      bytes[i] = (byte) (i + 1);
    }
    return bytes;
  }

  /// A successful simulation: 100,000 compute units consumed, 77,777 bytes of account data loaded.
  private static final TxSimulation SIMULATION = new TxSimulation(
      null, null, OptionalLong.empty(), 77_777,
      List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
      null, OptionalInt.of(100_000), null, null
  );

  private static final LatestBlockHash BLOCK_HASH = new LatestBlockHash(null, key(0xCAFE).toBase58(), 8_642L);

  /// Runs each task on the calling thread, so the block hash lookup's `supplyAsync` has completed
  /// when ravina's send path joins it: nothing here waits on another thread.
  private static final class InlineExecutor extends AbstractExecutorService {

    private volatile boolean shutdown;

    @Override
    public void execute(final Runnable command) {
      command.run();
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
      return shutdown;
    }
  }

  /// An `RpcCaller` over one proxy client that serves `getLatestBlockHash` inline: ravina's send
  /// path takes a fresh block hash through it, with no network and no thread.
  private static RpcCaller blockHashServingRpcCaller() {
    final var client = (SolanaRpcClient) Proxy.newProxyInstance(
        SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{SolanaRpcClient.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getLatestBlockHash" -> CompletableFuture.completedFuture(BLOCK_HASH);
          case "toString" -> "block hash client";
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
    // capacity no call here ever waits on; the monitor's error tracker is consulted only by a
    // call that fails, and none does
    final var second = Duration.ofSeconds(1);
    final var capacity = new CapacityConfig(0, 100_000, second, 8, second, second, second, second);
    final var item = BalancedItem.createItem(
        client, capacity.createHttpResponseMonitor("block hash client"), Backoff.single(TimeUnit.MILLISECONDS, 0)
    );
    return new RpcCaller(new InlineExecutor(), LoadBalancer.createBalancer(item), CallWeights.createDefault());
  }

  /// The failure sava's client answers an HTTP error status with, which ravina records in the
  /// attempt's ledger: the status survives only in its message text.
  private static UncheckedIOException refusal(final int httpStatus) {
    return new UncheckedIOException(new UnknownServiceException(String.format(
        "HTTP request failed with [httpCode:%d], [body=%s]", httpStatus, "upstream unavailable"
    )));
  }

  /// ravina's own instruction service over in-memory collaborators: one attempt, whose sends are
  /// answered as `sendResponses` says (the first send and its failover, recorded in the ledger as
  /// every monitor must) and whose status then settles as not landed. A consumer comes by a
  /// SendNotAcceptedException only this way, since its factory is ravina's own. `sent` holds each
  /// transaction signed and sent.
  private static final class RefusedSends {

    final List<Transaction> sent = new ArrayList<>();
    final TransactionProcessor transactionProcessor;
    final InstructionService service;

    RefusedSends(final List<CompletableFuture<String>> sendResponses) {
      this.transactionProcessor = (TransactionProcessor) Proxy.newProxyInstance(
          TransactionProcessor.class.getClassLoader(),
          new Class<?>[]{TransactionProcessor.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "feePayer" -> SIGNER.publicKey();
            case "formatter" -> FORMATTER;
            case "simulateAndEstimate" -> {
              @SuppressWarnings("unchecked") final var batch = (List<Instruction>) args[1];
              yield new SimulationFutures(
                  (Commitment) args[0], batch, Transaction.createTx(SIGNER.publicKey(), batch), 1_234,
                  CompletableFuture.completedFuture(SIMULATION), CompletableFuture.completedFuture(BigDecimal.valueOf(25))
              );
            }
            case "createTransaction" -> {
              // the four-argument overload ravina's send path calls, by the real v1 recipe
              if (args.length != 4) {
                throw new UnsupportedOperationException("createTransaction with " + args.length + " arguments");
              }
              yield ((SimulationFutures) args[0]).createTransaction((BigDecimal) args[1], (int) args[2], (int) args[3]);
            }
            case "setBlockHash" -> {
              if (!(args[1] instanceof LatestBlockHash blockHash)) {
                throw new UnsupportedOperationException("setBlockHash from " + args[1]);
              }
              ((Transaction) args[0]).setRecentBlockHash(blockHash.blockHash());
              yield blockHash.lastValidBlockHeight();
            }
            case "signAndSendTx" -> {
              final var transaction = (Transaction) args[0];
              transaction.sign(SIGNER);
              sent.add(transaction);
              yield new SendTxContext(null, new CompletableFuture<>(), transaction, "base64", (long) args[1], 0L);
            }
            default -> throw new UnsupportedOperationException(method.getName());
          }
      );
      final var monitor = (TxMonitorService) Proxy.newProxyInstance(
          TxMonitorService.class.getClassLoader(),
          new Class<?>[]{TxMonitorService.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "stopped" -> false;
            case "validateResponseAndAwaitCommitmentViaWebSocket" -> {
              // the sends, recorded; no websocket notification arrives
              final var ledger = (SendLedger) args[4];
              sendResponses.forEach(ledger::add);
              yield null;
            }
            // settled as not landed: with verifyExpired true, only once the block hash expired
            // and a history-searching status read behind the settle buffer still found nothing
            case "queueResult" -> CompletableFuture.completedFuture(null);
            default -> throw new UnsupportedOperationException(method.getName());
          }
      );
      this.service = InstructionService.createService(blockHashServingRpcCaller(), transactionProcessor, null, null, monitor);
    }
  }

  private static InstructionProcessorImpl processor(final RefusedSends sends, final RecordingNotify notify) {
    return processor(sends.transactionProcessor, sends.service, notify, GlamAccounts.MAIN_NET.mintProgram());
  }

  /// ravina's own exception from its own call over `sends`, with `verifyExpired` as given: the
  /// processor passes true, so an unverified one reaches it only relayed by a scripted service.
  private static SendNotAcceptedException notAccepted(final RefusedSends sends, final boolean verifyExpired) {
    final var thrown = assertThrows(SendNotAcceptedException.class, () -> sends.service.processInstructions(
        1.2, List.of(instruction(1, 2)), new BigDecimal("0.001"), CONFIRMED, CONFIRMED, verifyExpired, true, 3, "test"
    ));
    assertEquals(verifyExpired, thrown.expiryVerified());
    return thrown;
  }

  /// A batch no send of whose transaction was accepted, its block hash expired with nothing of it
  /// landed, on the processor's own call: dropped like a failed batch, with one page carrying the
  /// sends attempted and the last failure, and false, so the caller backs off and rebuilds where
  /// the catch-all arm ended the service.
  @Test
  void aVerifiedNotAcceptedBatchIsDroppedWithAPageAndTheCallerRebuilds() throws InterruptedException {
    // the first send paced by a rate limit, its failover refused by the key: the page carries the last
    final var sends = new RefusedSends(List.of(
        CompletableFuture.failedFuture(refusal(429)), CompletableFuture.failedFuture(refusal(401))
    ));
    final var notify = new RecordingNotify();
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));

    try (final var log = LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      assertFalse(processor(sends, notify).processInstructions("test", instructions));
      log.assertLogged(Level.WARNING, "No send of the test transaction was accepted (2 attempted) and nothing landed");
    }
    assertEquals(1, sends.sent.size(), "one attempt, and nothing more is sent");
    assertTrue(instructions.isEmpty(), "the batch is dropped like every failed batch");
    assertEquals(1, notify.messages.size(), () -> notify.messages.toString());
    final var msg = notify.messages.getFirst();
    assertTrue(msg.contains("No send of the test transaction was accepted (2 attempted) and nothing landed"), msg);
    assertTrue(msg.contains("so the caller backs off and rebuilds"), msg);
    assertTrue(msg.contains("Last failure: " + refusal(401) + "."), msg);
    assertFalse(msg.contains("httpCode:429"), "the first failure is the exception's suppressed one, not the page's");
  }

  /// A send that was never answered leaves ravina's exception without a cause, and the page says so
  /// in place of a last failure.
  @Test
  void aVerifiedNotAcceptedBatchWithNoAnsweredSendSaysSo() throws InterruptedException {
    final var sends = new RefusedSends(List.of(new CompletableFuture<>()));
    final var notify = new RecordingNotify();
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));

    try (final var log = LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      assertFalse(processor(sends, notify).processInstructions("test", instructions));
      log.assertLogged(Level.WARNING, "(1 attempted) and nothing landed");
    }
    assertEquals(1, sends.sent.size());
    assertTrue(instructions.isEmpty());
    assertEquals(1, notify.messages.size(), () -> notify.messages.toString());
    final var msg = notify.messages.getFirst();
    assertTrue(msg.contains("No send of the test transaction was accepted (1 attempted) and nothing landed"), msg);
    assertTrue(msg.contains("Last failure: no send was answered."), msg);
  }

  /// Unreachable from the processor's own call, which verifies every expiry, but not misreported
  /// should ravina's flag ever change: with no expiry verified this attempt or an earlier one may
  /// still land, so the exception is thrown as itself after a page naming the transaction, and the
  /// batch heads the list, exactly as a stopped monitor's sent() batch does.
  @Test
  void anUnverifiedNotAcceptedBatchIsThrownAsItselfAndHeadsTheList() {
    final var notAccepted = notAccepted(new RefusedSends(List.of(CompletableFuture.failedFuture(refusal(429)))), false);
    final var service = new ScriptedService();
    final var notify = new RecordingNotify();
    service.script.add(batch -> {
      throw notAccepted;
    });
    final var instructions = new ArrayList<>(List.of(instruction(1, 2), instruction(2, 2)));
    final var formattedSig = FORMATTER.formatSig(notAccepted.sig());

    try (final var log = LogCapture.attach(InstructionProcessorImpl.class.getName())) {
      final var thrown = assertThrows(SendNotAcceptedException.class, () ->
          processor(service, notify).processInstructions("test", instructions));
      assertSame(notAccepted, thrown, "thrown as itself: the caller tells it apart by type");
      log.assertLogged(Level.SEVERE, "No send of the test transaction " + formattedSig + " was accepted (1 attempted)");
    }
    assertEquals(List.of(2), service.batchSizes, "nothing more is sent");
    assertEquals(2, instructions.size(), "the batch heads the list, not cleared as a failed batch is");
    assertEquals(1, notify.messages.size(), () -> notify.messages.toString());
    final var msg = notify.messages.getFirst();
    assertTrue(msg.contains(
        "No send of the test transaction " + formattedSig
            + " was accepted (1 attempted) and its expiry was not verified: this attempt or an earlier one of the call may still land, so these instructions must not be rebuilt before its status is known."
    ), msg);
    assertFalse(msg.contains("nothing landed"), msg);
  }

  // ------------------------------------------------------------------ fatal pages --

  /// Returns once `runner` parks in a timed wait, the page await being the only one on its path.
  /// A runner that ends first fails `expectation` at once rather than spinning out the deadline.
  private static void awaitPageAwait(final Thread runner, final String expectation) throws InterruptedException {
    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Workers.FIXTURE_DEADLINE_MILLIS);
    while (runner.getState() != Thread.State.TIMED_WAITING) {
      assertTrue(runner.isAlive(), expectation);
      assertTrue(System.nanoTime() < deadline, "the call never parked to await its page");
      //noinspection BusyWait
      Thread.sleep(1L);
    }
  }

  /// The fulfillment entrypoint exits the process once its worker ends on a thrown failure, and
  /// ravina's notify client posts asynchronously, so on the arms that throw the posts are awaited
  /// before the throw: the page that says a transaction may still land must not be cut off by the
  /// exit. The post here completes only when the test lets it, so the call is parked in its await
  /// when the test looks, and ends once the post completes, with the stop thrown as itself.
  @Test
  void aFatalPageIsAwaitedBeforeTheCallEnds() throws InterruptedException {
    final var stopped = stoppedWithPending(stoppedMonitor());
    final var service = new ScriptedService();
    service.script.add(batch -> {
      throw stopped;
    });
    final var post = new CompletableFuture<String>();
    final software.sava.services.core.net.http.NotifyClient notify = msg -> List.of(post);
    final var processor = new InstructionProcessorImpl(
        feePayer(), service.service(), GlamAccounts.MAIN_NET.mintProgram(), new BigDecimal("0.001"), notify, 1.2, 3
    );
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));
    final var thrown = new AtomicReference<Throwable>();
    final var postedWhenThrown = new AtomicBoolean();
    final var runner = new Thread(() -> {
      try {
        processor.processInstructions("test", instructions);
      } catch (final Throwable failure) {
        postedWhenThrown.set(post.isDone());
        thrown.set(failure);
      }
    }, "fatal page runner");
    runner.start();
    try {
      awaitPageAwait(runner, "the call ended before its page was delivered");
      assertFalse(post.isDone(), "the post completes only when this test lets it");
      post.complete("delivered");
      Workers.joinWithin(runner, "the call must end once its page is delivered");
    } finally {
      runner.interrupt();
      runner.join(Workers.FIXTURE_DEADLINE_MILLIS);
    }
    assertSame(stopped, thrown.get(), "thrown as itself once the page has left");
    assertTrue(postedWhenThrown.get(), "the page must be delivered before the call ends");
    assertEquals(1, instructions.size());
  }

  /// The verified not-accepted arm returns false for the worker to back off and rebuild, so it
  /// does not wait for its page: the call returns while the post completes on its own.
  @Test
  void aVerifiedNotAcceptedPageIsNotAwaited() throws InterruptedException {
    final var sends = new RefusedSends(List.of(CompletableFuture.failedFuture(refusal(429))));
    final var post = new CompletableFuture<String>();
    final software.sava.services.core.net.http.NotifyClient notify = msg -> List.of(post);
    final var processor = new InstructionProcessorImpl(
        sends.transactionProcessor, sends.service, GlamAccounts.MAIN_NET.mintProgram(), new BigDecimal("0.001"), notify, 1.2, 3
    );
    final var instructions = new ArrayList<>(List.of(instruction(1, 2)));
    final var returned = new AtomicReference<Boolean>();
    final var runner = new Thread(() -> {
      try {
        returned.set(processor.processInstructions("test", instructions));
      } catch (final InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }, "verified page runner");
    runner.start();
    Workers.joinWithin(runner, "the verified arm must return without awaiting its page");
    assertEquals(Boolean.FALSE, returned.get());
    assertFalse(post.isDone(), "the post completes on its own, after the call");
    assertTrue(instructions.isEmpty());
  }
}
