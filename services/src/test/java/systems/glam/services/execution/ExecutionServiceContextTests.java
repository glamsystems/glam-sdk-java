package systems.glam.services.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.token.Mint;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.epoch.Epoch;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.GlamEnv;
import systems.glam.services.ServiceContextImpl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class ExecutionServiceContextTests {

  private static final PublicKey SERVICE_KEY = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final SolanaAccounts SOLANA = SolanaAccounts.MAIN_NET;
  /// The service context's notify client, an instance a forward is held to by identity.
  private static final NotifyClient NOTIFY_CLIENT = msg -> List.of();

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
        NOTIFY_CLIENT, rpcCaller, null
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
    final var instructionProcessor = (InstructionProcessor) Proxy.newProxyInstance(
        InstructionProcessor.class.getClassLoader(),
        new Class<?>[]{InstructionProcessor.class},
        (proxy, method, args) -> {
          if (method.getName().equals("processInstructions")) {
            processedContexts.add((String) args[0]);
            @SuppressWarnings("unchecked") final var ixs = (List<Instruction>) args[1];
            processedInstructions.add(ixs);
            assertEquals(2, args.length, "the context passes exactly the log context and the instructions");
            return results.removeFirst();
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );

    final var context = new ExecutionServiceContextImpl(serviceContext, epochInfoService, instructionProcessor);

    assertSame(epochInfoService, context.epochInfoService());
    assertSame(instructionProcessor, context.instructionProcessor());
    assertFalse(context.feePayerBalanceLow());
    assertEquals(411L, context.medianMillisPerSlot());
    // the pages of a service over this context go through the service context's client, not the
    // processor's (the same client in a delegate configuration; here the processor answers none)
    assertSame(NOTIFY_CLIENT, context.notifyClient());

    final var instructions = List.<Instruction>of();
    assertTrue(context.processInstructions("first", instructions));
    assertFalse(context.processInstructions("second", instructions));
    assertEquals(List.of("first", "second"), processedContexts);
    assertSame(instructions, processedInstructions.getFirst());
  }

  /// An implementor that leaves the interface's default in place pages through its instruction
  /// processor's client, which is where the fulfillment loop reached its client before the
  /// context answered one: an external implementor is unchanged by the seam.
  @Test
  void theDefaultNotifyClientIsTheInstructionProcessors() {
    final NotifyClient processorsClient = msg -> List.of();
    final var instructionProcessor = (InstructionProcessor) Proxy.newProxyInstance(
        InstructionProcessor.class.getClassLoader(),
        new Class<?>[]{InstructionProcessor.class},
        (proxy, method, args) -> {
          if (method.getName().equals("notifyClient")) {
            return processorsClient;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
    final var context = (ExecutionServiceContext) Proxy.newProxyInstance(
        ExecutionServiceContext.class.getClassLoader(),
        new Class<?>[]{ExecutionServiceContext.class},
        (proxy, method, args) -> {
          if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
          }
          if (method.getName().equals("instructionProcessor")) {
            return instructionProcessor;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
    assertSame(processorsClient, context.notifyClient());
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
    final var context = new ExecutionServiceContextImpl(lowBalanceContext, null, null);
    assertTrue(context.feePayerBalanceLow());
  }

  @Test
  void theBaseContextDelegatesToTheServiceContext(@TempDir final Path tempDir) throws InterruptedException {
    final var serviceContext = serviceContext(tempDir, Duration.ofMillis(30));
    final var context = new ExecutionServiceContextImpl(serviceContext, null, null);

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
    assertDoesNotThrow(() -> assertTrue(ran.await(1, TimeUnit.SECONDS)));

    // removing the delegated backoff call would return immediately
    final long start = System.nanoTime();
    context.backoff(1L);
    final long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
    assertTrue(elapsedMillis >= 20, () -> "slept only " + elapsedMillis + "ms");
  }
}
