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

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
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

  @Test
  void runExecutesTheServicesAndMonitorsTheConnectionUntilInterrupted() throws InterruptedException {
    final var connectionChecks = new AtomicInteger();
    final var closed = new CountDownLatch(1);
    final var webSocketManager = (WebSocketManager) Proxy.newProxyInstance(
        WebSocketManager.class.getClassLoader(),
        new Class<?>[]{WebSocketManager.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "checkConnection" -> {
            connectionChecks.incrementAndGet();
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

    final var entrypoint = new SingleAssetFulfillmentServiceEntrypoint(
        webSocketManager, epochInfoService, fulfillmentService
    );
    assertSame(webSocketManager, entrypoint.webSocketManager());
    assertSame(epochInfoService, entrypoint.epochInfoService());
    assertSame(fulfillmentService, entrypoint.fulfillmentService());

    final var runner = new Thread(entrypoint::run);
    runner.start();
    assertTrue(epochServiceRan.await(5, SECONDS), "the epoch service was never executed");
    assertTrue(fulfillmentServiceRan.await(5, SECONDS), "the fulfillment service was never executed");

    Thread.sleep(250L);
    final int checks = connectionChecks.get();
    assertTrue(checks >= 1, "the connection was never checked");
    // the loop paces itself: without the sleep this would be in the thousands
    assertTrue(checks < 10, () -> "the monitor loop is spinning: " + checks + " checks in 250ms");

    runner.interrupt();
    assertTrue(closed.await(5, SECONDS), "the websocket manager was not closed on exit");
    runner.join(5_000L);
    assertFalse(runner.isAlive());
  }
}
