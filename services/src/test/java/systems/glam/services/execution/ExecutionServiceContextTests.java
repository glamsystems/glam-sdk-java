package systems.glam.services.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.token.Mint;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.epoch.Epoch;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.GlamEnv;
import systems.glam.services.ServiceContextImpl;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class ExecutionServiceContextTests {

  private static final PublicKey SERVICE_KEY = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final SolanaAccounts SOLANA = SolanaAccounts.MAIN_NET;

  private static ServiceContextImpl serviceContext(final Path cacheDirectory, final Duration minCheckDelay) {
    final var rpcCaller = new RpcCaller(null, null, null);
    return new ServiceContextImpl(
        SERVICE_KEY,
        BigInteger.ONE, BigInteger.ONE,
        cacheDirectory,
        minCheckDelay, Duration.ofMinutes(5),
        Executors.newVirtualThreadPerTaskExecutor(),
        Backoff.single(TimeUnit.MILLISECONDS, 1),
        SOLANA,
        GlamAccounts.MAIN_NET, GlamAccounts.MAIN_NET_STAGING,
        null, rpcCaller, null
    );
  }

  private static AccountInfo<byte[]> account(final PublicKey owner, final byte[] data) {
    return new AccountInfo<>(SERVICE_KEY, new Context(1L, null), false, 0, owner, BigInteger.ZERO, 0, data);
  }

  @Test
  void theContextDelegatesToItsCollaborators(@TempDir final Path tempDir) throws InterruptedException {
    final var serviceContext = serviceContext(tempDir, Duration.ofMillis(30));

    final var epoch = new Epoch(0L, 0L, null, null, 411, null, 0L, 0.0, 0.0);
    final var epochInfoService = (EpochInfoService) Proxy.newProxyInstance(
        EpochInfoService.class.getClassLoader(),
        new Class<?>[]{EpochInfoService.class},
        (proxy, method, args) -> {
          if (method.getName().equals("epochInfo")) {
            return epoch;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );

    final var processedContexts = new java.util.ArrayList<String>();
    final var processedInstructions = new java.util.ArrayList<List<Instruction>>();
    final var results = new java.util.ArrayDeque<Boolean>(List.of(true, false));
    final Function<List<Instruction>, Transaction> transactionFactory = ixs -> null;
    final var instructionProcessor = (InstructionProcessor) Proxy.newProxyInstance(
        InstructionProcessor.class.getClassLoader(),
        new Class<?>[]{InstructionProcessor.class},
        (proxy, method, args) -> {
          if (method.getName().equals("processInstructions")) {
            processedContexts.add((String) args[0]);
            @SuppressWarnings("unchecked") final var ixs = (List<Instruction>) args[1];
            processedInstructions.add(ixs);
            assertSame(transactionFactory, args[args.length - 1]);
            return results.removeFirst();
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );

    final var context = new ExecutionServiceContextImpl(
        serviceContext, epochInfoService, instructionProcessor, transactionFactory
    );

    assertSame(epochInfoService, context.epochInfoService());
    assertSame(instructionProcessor, context.instructionProcessor());
    assertSame(transactionFactory, context.transactionFactory());
    assertFalse(context.feePayerBalanceLow());
    assertEquals(411L, context.medianMillisPerSlot());

    final var instructions = List.<Instruction>of();
    assertTrue(context.processInstructions("first", instructions));
    assertFalse(context.processInstructions("second", instructions));
    assertEquals(List.of("first", "second"), processedContexts);
    assertSame(instructions, processedInstructions.getFirst());
  }

  @Test
  void aLowFeePayerBalanceIsReportedThrough() {
    final var lowBalanceContext = (systems.glam.services.ServiceContext) Proxy.newProxyInstance(
        systems.glam.services.ServiceContext.class.getClassLoader(),
        new Class<?>[]{systems.glam.services.ServiceContext.class},
        (proxy, method, args) -> {
          if (method.getName().equals("feePayerBalanceLow")) {
            return true;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
    final var context = new ExecutionServiceContextImpl(lowBalanceContext, null, null, null);
    assertTrue(context.feePayerBalanceLow());
  }

  @Test
  void theBaseContextDelegatesToTheServiceContext(@TempDir final Path tempDir) throws InterruptedException {
    final var serviceContext = serviceContext(tempDir, Duration.ofMillis(30));
    final var context = new ExecutionServiceContextImpl(serviceContext, null, null, null);

    assertEquals(Duration.ofMillis(30).toNanos(), context.minCheckStateDelayNanos());
    assertEquals(Duration.ofMinutes(5).toNanos(), context.maxCheckStateDelayNanos());
    assertSame(serviceContext.rpcCaller(), context.rpcCaller());
    assertSame(serviceContext.taskExecutor(), context.taskExecutor());

    final byte[] clockData = new byte[40];
    ByteUtil.putInt64LE(clockData, 0, 77L);
    final var clock = context.clock(Map.of(SOLANA.clockSysVar(), account(SOLANA.clockSysVar(), clockData)));
    assertEquals(77L, clock.slot());

    assertTrue(context.isTokenMint(account(SOLANA.tokenProgram(), new byte[Mint.BYTES])));
    assertFalse(context.isTokenMint(account(SOLANA.tokenProgram(), new byte[TokenAccount.BYTES])));
    assertTrue(context.isTokenAccount(account(SOLANA.tokenProgram(), new byte[TokenAccount.BYTES])));
    assertFalse(context.isTokenAccount(account(SOLANA.tokenProgram(), new byte[Mint.BYTES])));

    final var stateKey = fromBase58Encoded("3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7");
    assertEquals(
        serviceContext.resolveGlamStateFilePath(GlamEnv.STAGING, stateKey),
        context.resolveGlamStateFilePath(GlamEnv.STAGING, stateKey)
    );

    final var ran = new java.util.concurrent.CountDownLatch(1);
    context.executeTask(ran::countDown);
    assertDoesNotThrow(() -> assertTrue(ran.await(5, TimeUnit.SECONDS)));

    // removing the delegated backoff call would return immediately
    final long start = System.nanoTime();
    context.backoff(1L);
    final long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
    assertTrue(elapsedMillis >= 20, () -> "slept only " + elapsedMillis + "ms");
  }

  @Test
  void theDefaultProcessInstructionsPassesNoLookupTables() throws InterruptedException {
    final var seenTables = new java.util.ArrayList<Object>();
    final var results = new java.util.ArrayDeque<>(List.of(true, false));
    final var processor = new InstructionProcessor() {
      @Override
      public software.sava.services.core.net.http.NotifyClient notifyClient() {
        throw new UnsupportedOperationException();
      }

      @Override
      public software.sava.services.solana.transactions.TransactionProcessor transactionProcessor() {
        throw new UnsupportedOperationException();
      }

      @Override
      public software.sava.services.solana.transactions.InstructionService instructionService() {
        throw new UnsupportedOperationException();
      }

      @Override
      public java.math.BigDecimal maxLamportPriorityFee() {
        throw new UnsupportedOperationException();
      }

      @Override
      public double cuBudgetMultiplier() {
        throw new UnsupportedOperationException();
      }

      @Override
      public int maxRetries() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean processInstructions(final String logContext,
                                         final List<Instruction> instructions,
                                         final java.util.Collection<PublicKey> lookupTableKeys,
                                         final Function<List<Instruction>, Transaction> transactionFactory) {
        seenTables.add(lookupTableKeys);
        return results.removeFirst();
      }

      @Override
      public boolean processInstructions(final String logContext,
                                         final List<Instruction> instructions,
                                         final double cuBudgetMultiplier,
                                         final java.math.BigDecimal maxLamportPriorityFee,
                                         final int maxRetries,
                                         final java.util.Collection<PublicKey> lookupTableKeys,
                                         final Function<List<Instruction>, Transaction> transactionFactory) {
        throw new UnsupportedOperationException();
      }
    };
    assertTrue(processor.processInstructions("first", List.of(), ixs -> null));
    assertFalse(processor.processInstructions("second", List.of(), ixs -> null));
    assertEquals(java.util.Arrays.asList(null, null), seenTables);
  }
}
