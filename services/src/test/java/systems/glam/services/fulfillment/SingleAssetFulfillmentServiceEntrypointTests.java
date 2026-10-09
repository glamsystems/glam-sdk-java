package systems.glam.services.fulfillment;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

  /// A call budget for the connection-check stub. The monitor loop checks once per 3 s pacing
  /// sleep, so this test sees a single call. A loop that lost its sleep would spin on the stub,
  /// where nothing is interruptible, so past the budget the stub throws: that ends the loop
  /// instead of leaving it spinning into the next test or the next mutant.
  private static final int CONNECTION_CHECK_BUDGET = 10;

  /// Returns once the monitor loop parks in its pacing sleep. run() takes no timed wait
  /// before that sleep, so the runner's first TIMED_WAITING is the sleep itself.
  private static void awaitPacingSleep(final Thread runner, final AtomicInteger connectionChecks) throws InterruptedException {
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
    final var webSocketManager = (WebSocketManager) Proxy.newProxyInstance(
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

    final var epochServiceRan = new CountDownLatch(1);
    final var epochInfoService = (software.sava.services.solana.epoch.EpochInfoService) Proxy.newProxyInstance(
        software.sava.services.solana.epoch.EpochInfoService.class.getClassLoader(),
        new Class<?>[]{software.sava.services.solana.epoch.EpochInfoService.class},
        (proxy, method, args) -> {
          if (method.getName().equals("run")) {
            epochServiceRan.countDown();
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );

    final var fulfillmentServiceRan = new CountDownLatch(1);
    final var fulfillmentService = new FulfillmentService() {
      @Override
      public void run() {
        fulfillmentServiceRan.countDown();
      }

      @Override
      public void subscribe(final SolanaRpcWebsocket websocket) {
        throw new UnsupportedOperationException();
      }
    };

    // the monitor is started through its own run(Executor), which must receive a live executor
    // of the entrypoint's: the stub proves it by running a task on what it was given
    final var monitorRan = new CountDownLatch(1);
    final var txMonitorService = (software.sava.services.solana.transactions.TxMonitorService) Proxy.newProxyInstance(
        software.sava.services.solana.transactions.TxMonitorService.class.getClassLoader(),
        new Class<?>[]{software.sava.services.solana.transactions.TxMonitorService.class},
        (proxy, method, args) -> {
          if (method.getName().equals("run") && args != null && args.length == 1) {
            ((java.util.concurrent.Executor) args[0]).execute(monitorRan::countDown);
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );

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
    runner.start();
    try {
      assertTrue(epochServiceRan.await(Workers.FIXTURE_DEADLINE_MILLIS, MILLISECONDS),
          "the epoch service was never executed");
      assertTrue(monitorRan.await(Workers.FIXTURE_DEADLINE_MILLIS, MILLISECONDS),
          "the transaction monitor was never started on the entrypoint's executor");
      assertTrue(fulfillmentServiceRan.await(Workers.FIXTURE_DEADLINE_MILLIS, MILLISECONDS),
          "the fulfillment service was never executed");

      // the loop paces itself: one connection check, then its sleep. Without the check it
      // parks having checked nothing; without the sleep it spins into the stub's budget
      awaitPacingSleep(runner, connectionChecks);
      assertEquals(1, connectionChecks.get(), "the monitor loop must check the connection once, then pause");

      runner.interrupt();
      Workers.joinWithin(runner, "the monitor loop must exit on interrupt");
      // the runner has terminated, so its finally has run
      assertEquals(0L, closed.getCount(), "the websocket manager was not closed on exit");
      // the interrupt ended the first pause, so no second check ever ran
      assertEquals(1, connectionChecks.get(), "the interrupt must end the loop inside its first pause");
      assertNull(escaped.get(), "run() must end on the interrupt, not on an exception");
    } finally {
      runner.interrupt();
      runner.join(Workers.FIXTURE_DEADLINE_MILLIS);
    }
  }
}
