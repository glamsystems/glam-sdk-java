package systems.glam.services.fulfillment;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.transactions.TxMonitorService;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.Protocol;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;
import systems.glam.services.tests.LogCapture;
import systems.glam.services.tests.Workers;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

final class SingleAssetFulfillmentServiceEntrypointTests {

  @Test
  void dryRunDefaultsOff() {
    assertFalse(SingleAssetFulfillmentServiceEntrypoint.DRY_RUN);
  }

  private static final PublicKey FEE_PAYER =
      PublicKey.fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY =
      PublicKey.fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey DELEGATE =
      PublicKey.fromBase58Encoded("EMou4Rxje9ddgFubx92Grg3doP2vvKrxJiGdyiv6jxQY");

  private static StateAccountClient stateClient(
      final DelegateAcl... delegateAcls) {
    final var name = Arrays.copyOf(
        "Fulfill".getBytes(US_ASCII),
        StateAccount.NAME_LEN);
    final var stateAccount = new StateAccount(
        STATE_KEY,
        StateAccount.DISCRIMINATOR,
        AccountType.TokenizedVault,
        true,
        DELEGATE, DELEGATE,
        new byte[StateAccount.PORTFOLIO_MANAGER_NAME_LEN],
        new CreatedModel(new byte[8], FEE_PAYER, 1_650_000_000L),
        DELEGATE, 9, 0,
        name,
        0L, 0L,
        GlamAccounts.MAIN_NET.mintPDA(STATE_KEY, 0).publicKey(),
        new PublicKey[0],
        new IntegrationAcl[0],
        delegateAcls,
        new PublicKey[0],
        new PricedProtocol[0],
        new EngineField[0][]
    );
    return StateAccountClient.createClient(stateAccount, FEE_PAYER);
  }

  /// The permission gate is what stands between a misconfigured delegate and
  /// a fulfillment run loop that fails on every transaction: misses must be
  /// reported, not just refused.
  @Test
  void delegatePermissionsAreValidatedAndMissesAreNeverSilent() {
    final var mintProgram = GlamAccounts.MAIN_NET.mintIntegrationProgram();
    final long fulfill = GlamMintConstants.PROTO_MINT_PERM_FULFILL;
    final var required = Map.of(mintProgram, Protocol.MINT.permissions(fulfill));

    final var loggerName = SingleAssetFulfillmentServiceEntrypoint.class.getName();
    // a missing state account is fatal and says so
    try (final var log = LogCapture.attach(loggerName)) {
      assertFalse(SingleAssetFulfillmentServiceEntrypoint.validateDelegatePermissions(required, DELEGATE, null));
      log.assertLogged("Glam account does not exist");
    }

    // a delegate without the fulfill grant is refused and named
    final var ungranted = stateClient(new DelegateAcl(
        DELEGATE,
        new IntegrationPermissions[0],
        Long.MAX_VALUE
    ));
    try (final var log = LogCapture.attach(loggerName)) {
      assertFalse(SingleAssetFulfillmentServiceEntrypoint.validateDelegatePermissions(required, DELEGATE, ungranted));
      log.assertLogged(DELEGATE + " does not have the required permissions");
    }

    // the granted delegate passes without noise
    final var granted = stateClient(new DelegateAcl(
        DELEGATE,
        new IntegrationPermissions[]{
            new IntegrationPermissions(
                mintProgram,
                new ProtocolPermissions[]{
                    new ProtocolPermissions(
                        Protocol.MINT.protocolBitFlag(), fulfill)
                }
            )
        },
        Long.MAX_VALUE
    ));
    try (final var log = LogCapture.attach(loggerName)) {
      assertTrue(SingleAssetFulfillmentServiceEntrypoint.validateDelegatePermissions(required, DELEGATE, granted));
      assertTrue(log.messages().isEmpty(), () -> log.messages().toString());
    }
  }

  private static final String LOGGER_NAME = SingleAssetFulfillmentServiceEntrypoint.class.getName();

  /// A call budget for the connection-check stub. The monitor loop checks once per 3 s pacing
  /// wait, so each test here sees a single call. A loop that lost its pacing, or kept going after
  /// the worker ended, where the wait on its finished future returns at once, would spin on the
  /// stub, where nothing is interruptible, so past the budget the stub throws: that ends the loop
  /// instead of leaving it spinning into the next test or the next mutant.
  private static final int CONNECTION_CHECK_BUDGET = 10;

  /// A websocket manager that counts its connection checks, throwing past the budget, and counts
  /// `closed` down when it is closed.
  private static WebSocketManager webSocketManager(final AtomicInteger connectionChecks, final CountDownLatch closed) {
    return (WebSocketManager) Proxy.newProxyInstance(
        WebSocketManager.class.getClassLoader(),
        new Class<?>[]{WebSocketManager.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "checkConnection" -> {
            if (connectionChecks.incrementAndGet() > CONNECTION_CHECK_BUDGET) {
              throw new IllegalStateException("the monitor loop is spinning on checkConnection");
            }
            yield null;
          }
          case "close" -> {
            closed.countDown();
            yield null;
          }
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  /// An epoch service whose run() is `body`.
  private static EpochInfoService epochInfoService(final Runnable body) {
    return (EpochInfoService) Proxy.newProxyInstance(
        EpochInfoService.class.getClassLoader(),
        new Class<?>[]{EpochInfoService.class},
        (proxy, method, args) -> {
          if (method.getName().equals("run")) {
            body.run();
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  /// A transaction monitor whose run(Executor) hands `loop` to the executor it is given, where the
  /// real monitor starts its loops: `loop` running proves that executor live.
  private static TxMonitorService txMonitorService(final Runnable loop) {
    return (TxMonitorService) Proxy.newProxyInstance(
        TxMonitorService.class.getClassLoader(),
        new Class<?>[]{TxMonitorService.class},
        (proxy, method, args) -> {
          if (method.getName().equals("run") && args != null && args.length == 1) {
            ((Executor) args[0]).execute(loop);
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  /// A fulfillment service whose run() is `body`; nothing here subscribes it.
  private static FulfillmentService fulfillmentService(final Runnable body) {
    return new FulfillmentService() {
      @Override
      public void run() {
        body.run();
      }

      @Override
      public void subscribe(final SolanaRpcWebsocket websocket) {
        throw new UnsupportedOperationException();
      }
    };
  }

  /// Blocks until this thread is interrupted, as a live service loop does, then counts
  /// `interrupted` down: the latch it waits on is never counted down, so only an interrupt ends
  /// the wait. Each caller runs on the entrypoint's pool, whose close awaits it, and every test
  /// interrupts its runner in a finally, which makes that close interrupt the pool.
  private static void awaitInterrupt(final CountDownLatch interrupted) {
    try {
      new CountDownLatch(1).await();
    } catch (final InterruptedException e) {
      interrupted.countDown();
    }
  }

  /// Returns once the monitor loop parks in its pacing wait, the timed wait on the fulfillment
  /// worker's future. run() takes no timed wait before it, so the runner's first TIMED_WAITING is
  /// that wait itself.
  private static void awaitPacingWait(final Thread runner, final AtomicInteger connectionChecks) throws InterruptedException {
    final long deadline = System.nanoTime() + MILLISECONDS.toNanos(Workers.FIXTURE_DEADLINE_MILLIS);
    while (runner.getState() != Thread.State.TIMED_WAITING) {
      assertTrue(runner.isAlive(),
          () -> "the monitor loop ended without pausing, after " + connectionChecks.get() + " connection checks");
      assertTrue(System.nanoTime() < deadline, "the monitor loop never paused between connection checks");
      //noinspection BusyWait
      Thread.sleep(1L);
    }
  }

  @Test
  void runExecutesTheServicesAndMonitorsTheConnectionUntilInterrupted() throws InterruptedException {
    final var connectionChecks = new AtomicInteger();
    final var closed = new CountDownLatch(1);
    final var webSocketManager = webSocketManager(connectionChecks, closed);

    final var epochServiceRan = new CountDownLatch(1);
    final var epochInfoService = epochInfoService(epochServiceRan::countDown);

    // a live worker, as the real one is until its loop fails: only an interrupt ends it here
    final var fulfillmentServiceRan = new CountDownLatch(1);
    final var fulfillmentServiceInterrupted = new CountDownLatch(1);
    final var fulfillmentService = fulfillmentService(() -> {
      fulfillmentServiceRan.countDown();
      awaitInterrupt(fulfillmentServiceInterrupted);
    });

    // the monitor is started through its own run(Executor), which must receive a live executor
    // of the entrypoint's: the stub proves it by running a task on what it was given
    final var monitorRan = new CountDownLatch(1);
    final var txMonitorService = txMonitorService(monitorRan::countDown);

    final var entrypoint = new SingleAssetFulfillmentServiceEntrypoint(
        webSocketManager, epochInfoService, txMonitorService, fulfillmentService
    );
    assertSame(webSocketManager, entrypoint.webSocketManager());
    assertSame(epochInfoService, entrypoint.epochInfoService());
    assertSame(txMonitorService, entrypoint.txMonitorService());
    assertSame(fulfillmentService, entrypoint.fulfillmentService());

    // an exception escaping run() is recorded rather than printed: an unmutated run ends on
    // the interrupt and lets none escape
    final var escaped = new AtomicReference<Throwable>();
    final var runner = new Thread(entrypoint::run, "fulfillment-entrypoint");
    runner.setUncaughtExceptionHandler((thread, ex) -> escaped.set(ex));
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      runner.start();
      try {
        assertTrue(epochServiceRan.await(Workers.FIXTURE_DEADLINE_MILLIS, MILLISECONDS),
            "the epoch service was never executed");
        assertTrue(monitorRan.await(Workers.FIXTURE_DEADLINE_MILLIS, MILLISECONDS),
            "the transaction monitor was never started on the entrypoint's executor");
        assertTrue(fulfillmentServiceRan.await(Workers.FIXTURE_DEADLINE_MILLIS, MILLISECONDS),
            "the fulfillment service was never executed");

        // the loop paces itself: one connection check, then its wait on the live worker. Without
        // the check it parks having checked nothing; without the wait it spins into the budget
        awaitPacingWait(runner, connectionChecks);
        assertEquals(1, connectionChecks.get(), "the monitor loop must check the connection once, then pause");

        runner.interrupt();
        Workers.joinWithin(runner, "the monitor loop must exit on interrupt");
        // the runner has terminated, so its pool's close has awaited every task and its finally
        // has run
        assertEquals(0L, fulfillmentServiceInterrupted.getCount(),
            "an interrupt must stop the fulfillment worker, or the pool's close waits on it for good");
        assertEquals(0L, closed.getCount(), "the websocket manager was not closed on exit");
        // the interrupt ended the first pause, so no second check ever ran
        assertEquals(1, connectionChecks.get(), "the interrupt must end the loop inside its first pause");
        assertNull(escaped.get(), "run() must end on the interrupt, not on an exception");
        // an interrupt is a shutdown, not the worker's end: it ends the loop quietly
        assertTrue(log.messages().isEmpty(), () -> log.messages().toString());
      } finally {
        runner.interrupt();
        runner.join(Workers.FIXTURE_DEADLINE_MILLIS);
      }
    }
  }

  /// Runs an entrypoint over `fulfillmentService`, whose run() ends by itself, and over an epoch
  /// service and a monitor that, like the real loops, end only on an interrupt. Then asserts what
  /// every end of the worker must leave: run() returned with no interrupt sent, within the fixture
  /// deadline, and let no exception escape; the epoch service and the monitor were interrupted,
  /// without which the pool's close would wait on them for good; the websocket manager was closed.
  private static void runUntilTheFulfillmentServiceEnds(final FulfillmentService fulfillmentService) throws InterruptedException {
    final var closed = new CountDownLatch(1);
    final var webSocketManager = webSocketManager(new AtomicInteger(), closed);
    final var epochServiceInterrupted = new CountDownLatch(1);
    final var epochInfoService = epochInfoService(() -> awaitInterrupt(epochServiceInterrupted));
    final var monitorInterrupted = new CountDownLatch(1);
    final var txMonitorService = txMonitorService(() -> awaitInterrupt(monitorInterrupted));
    final var entrypoint = new SingleAssetFulfillmentServiceEntrypoint(
        webSocketManager, epochInfoService, txMonitorService, fulfillmentService
    );

    final var escaped = new AtomicReference<Throwable>();
    final var runner = new Thread(entrypoint::run, "fulfillment-entrypoint");
    runner.setUncaughtExceptionHandler((thread, ex) -> escaped.set(ex));
    runner.start();
    try {
      // nothing interrupts the runner before this join: the worker's end alone must end run()
      Workers.joinWithin(runner, "run() must return by itself once the fulfillment service has ended");
      assertNull(escaped.get(), "run() must return, not throw, once the fulfillment service has ended");
      // the runner has terminated, so its pool's close has awaited every task and its finally
      // has run
      assertEquals(0L, epochServiceInterrupted.getCount(),
          "the epoch service must be interrupted once the fulfillment service has ended");
      assertEquals(0L, monitorInterrupted.getCount(),
          "the transaction monitor must be interrupted once the fulfillment service has ended");
      assertEquals(0L, closed.getCount(), "the websocket manager was not closed on exit");
    } finally {
      runner.interrupt();
      runner.join(Workers.FIXTURE_DEADLINE_MILLIS);
    }
  }

  /// The worker's loop ends for good (as when the transaction monitor has stopped, which never
  /// restarts), so its end is the service's: run() returns by itself, for main to exit the
  /// process for a supervisor to restart, and says so at ERROR.
  @Test
  void runReturnsOnceTheFulfillmentServiceEnds() throws InterruptedException {
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      // BaseFulfillmentService.run returns once its loop has failed
      runUntilTheFulfillmentServiceEnds(fulfillmentService(() -> {
      }));
      log.assertLogged(Level.SEVERE, "The fulfillment service has ended, exiting for a supervisor to restart the process.");
    }
  }

  /// A worker that throws ends run() the same way, and the log names the failure itself, not
  /// the ExecutionException its future wraps it in.
  @Test
  void runReturnsOnceTheFulfillmentServiceFailsAndLogsTheFailure() throws InterruptedException {
    final var failure = new IllegalStateException("the stub fulfillment worker failed");
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      runUntilTheFulfillmentServiceEnds(fulfillmentService(() -> {
        throw failure;
      }));
      log.assertLogged(Level.SEVERE,
          "The fulfillment service failed, exiting for a supervisor to restart the process: " + failure);
    }
  }
}
