package systems.glam.services.fulfillment;

import org.junit.jupiter.api.Test;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.websocket.WebSocketManager;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

final class SingleAssetFulfillmentServiceEntrypointTests {

  @Test
  void dryRunDefaultsOff() {
    assertFalse(SingleAssetFulfillmentServiceEntrypoint.DRY_RUN);
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
