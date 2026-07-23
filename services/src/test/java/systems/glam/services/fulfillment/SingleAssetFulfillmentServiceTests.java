package systems.glam.services.fulfillment;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.PendingRequest;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestType;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;
import systems.glam.services.execution.ExecutionServiceContext;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.fulfillment.accounting.RedemptionSummary;
import systems.glam.services.mints.MintContext;
import systems.glam.services.tests.LogCapture;

import java.util.*;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class SingleAssetFulfillmentServiceTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey BASE_ASSET_MINT = fromBase58Encoded("So11111111111111111111111111111111111111112");
  private static final SolanaAccounts SOLANA = SolanaAccounts.MAIN_NET;
  private static final GlamAccounts GLAM = GlamAccounts.MAIN_NET_STAGING;
  private static final PublicKey MINT_PDA = GLAM.mintPDA(STATE_KEY, 0).publicKey();
  private static final PublicKey REQUEST_QUEUE_KEY = GLAM.requestQueuePDA(MINT_PDA).publicKey();

  private static final String LOGGER_NAME = FulfillmentService.class.getName();

  private static byte[] fixLength(final String value) {
    return Arrays.copyOf(value.getBytes(US_ASCII), StateAccount.NAME_LEN);
  }

  private static NotifyAndSettle notifyAndSettle(final NoticePeriodType redeemType,
                                                 final TimeUnit timeUnit,
                                                 final long redeemNoticePeriod) {
    return new NotifyAndSettle(
        ValuationModel.Continuous, false,
        NoticePeriodType.Hard, 11L, 12L, 13L,
        redeemType, redeemNoticePeriod, 22L, 23L,
        timeUnit, new byte[NotifyAndSettle.PADDING_LEN]
    );
  }

  private static StateAccountClient stateClient(final NoticePeriodType redeemType,
                                                final TimeUnit timeUnit,
                                                final long redeemNoticePeriod) {
    return stateClient(redeemType, timeUnit, redeemNoticePeriod, MINT_PDA);
  }

  private static StateAccountClient stateClient(final NoticePeriodType redeemType,
                                                final TimeUnit timeUnit,
                                                final long redeemNoticePeriod,
                                                final PublicKey mintKey) {
    final var stateAccount = new StateAccount(
        STATE_KEY,
        StateAccount.DISCRIMINATOR,
        AccountType.TokenizedVault,
        true,
        fromBase58Encoded("EMou4Rxje9ddgFubx92Grg3doP2vvKrxJiGdyiv6jxQY"),
        fromBase58Encoded("yuru1ARL4bcmSFpufUCdCrF4joamZRui9CawdgE4ZCW"),
        fixLength("pm"),
        new CreatedModel(new byte[8], FEE_PAYER, 1_650_000_000L),
        BASE_ASSET_MINT,
        9,
        0,
        fixLength("Test Vault"),
        0L,
        0L,
        mintKey,
        new PublicKey[]{BASE_ASSET_MINT},
        new IntegrationAcl[0],
        new DelegateAcl[0],
        new PublicKey[]{FEE_PAYER},
        new PricedProtocol[0],
        new EngineField[][]{{
            new EngineField(EngineFieldName.NotifyAndSettle, new EngineFieldValue.NotifyAndSettle(
                notifyAndSettle(redeemType, timeUnit, redeemNoticePeriod)
            ))
        }}
    );
    final var accountClient = systems.glam.sdk.GlamAccountClient.createClient(SOLANA, GLAM, FEE_PAYER, STATE_KEY);
    return StateAccountClient.createClient(stateAccount, accountClient);
  }

  /// The scripted service context: rpc accounts served from a map, the
  /// fee-payer script doubles as the run-loop exit (an empty script throws).
  private static final class ScriptedContext implements ExecutionServiceContext {

    static final class StopRun extends RuntimeException {
    }

    final Map<PublicKey, AccountInfo<byte[]>> accounts = new HashMap<>();
    final ArrayDeque<Boolean> feePayerLow = new ArrayDeque<>();
    final ArrayDeque<Boolean> processResults = new ArrayDeque<>();
    final List<String> processedContexts = new ArrayList<>();
    final List<List<Instruction>> processedBatches = new ArrayList<>();
    final List<Long> backoffs = new ArrayList<>();
    final long minDelayNanos;
    final long maxDelayNanos;
    Clock clock;
    long medianMillisPerSlot = 400;
    int fetches = 0;
    private final RpcCaller rpcCaller;

    ScriptedContext(final long minDelayNanos, final long maxDelayNanos) {
      this.minDelayNanos = minDelayNanos;
      this.maxDelayNanos = maxDelayNanos;
      final var client = (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
          software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
          new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
          (proxy, method, args) -> {
            if (method.getName().equals("getAccounts")) {
              ++fetches;
              @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
              final var response = new ArrayList<AccountInfo<byte[]>>(keys.size());
              for (final var key : keys) {
                response.add(accounts.get(key));
              }
              return java.util.concurrent.CompletableFuture.completedFuture(response);
            }
            throw new UnsupportedOperationException(method.getName());
          }
      );
      final var resetDuration = java.time.Duration.ofSeconds(1);
      final var config = new software.sava.services.core.request_capacity.CapacityConfig(
          0, 1_000, resetDuration, 8, resetDuration, resetDuration, resetDuration, resetDuration);
      final var monitor = config.createMonitor("test", NoopTracker::new);
      final var item = software.sava.services.core.remote.load_balance.BalancedItem.createItem(
          client, monitor, software.sava.services.core.remote.call.Backoff.single(MILLISECONDS, 0));
      this.rpcCaller = new RpcCaller(
          java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(),
          software.sava.services.core.remote.load_balance.LoadBalancer.createBalancer(item),
          software.sava.services.solana.remote.call.CallWeights.createDefault()
      );
    }

    @Override
    public long minCheckStateDelayNanos() {
      return minDelayNanos;
    }

    @Override
    public long maxCheckStateDelayNanos() {
      return maxDelayNanos;
    }

    @Override
    public Clock clock(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
      return clock;
    }

    @Override
    public boolean feePayerBalanceLow() {
      if (feePayerLow.isEmpty()) {
        throw new StopRun();
      }
      return feePayerLow.removeFirst();
    }

    @Override
    public RpcCaller rpcCaller() {
      return rpcCaller;
    }

    @Override
    public software.sava.services.solana.epoch.EpochInfoService epochInfoService() {
      throw new UnsupportedOperationException();
    }

    @Override
    public InstructionProcessor instructionProcessor() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Function<List<Instruction>, software.sava.core.tx.Transaction> transactionFactory() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long medianMillisPerSlot() {
      return medianMillisPerSlot;
    }

    @Override
    public boolean processInstructions(final String logContext, final List<Instruction> instructions) {
      processedContexts.add(logContext);
      processedBatches.add(List.copyOf(instructions));
      return processResults.removeFirst();
    }

    @Override
    public void backoff(final long failureCount) {
      backoffs.add(failureCount);
    }
  }

  private static final class NoopTracker extends software.sava.services.core.request_capacity.trackers.RootErrorTracker<software.sava.rpc.json.http.client.SolanaRpcClient, byte[]> {

    NoopTracker(final software.sava.services.core.request_capacity.CapacityState capacityState) {
      super(capacityState);
    }

    @Override
    protected boolean isServerError(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean isRequestError(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean isRateLimited(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean updateGroupedErrorResponseCount(final long now,
                                                      final software.sava.rpc.json.http.client.SolanaRpcClient response,
                                                      final byte[] body) {
      return false;
    }

    @Override
    protected void logResponse(final software.sava.rpc.json.http.client.SolanaRpcClient response, final byte[] body) {
    }
  }

  // --- fixtures -------------------------------------------------------------

  private static AccountInfo<byte[]> account(final PublicKey key, final long slot, final PublicKey owner, final byte[] data) {
    return new AccountInfo<>(key, new Context(slot, null), false, 0, owner, java.math.BigInteger.ZERO, 0, data);
  }

  private static PendingRequest pending(final int userId,
                                        final long shares,
                                        final long createdAt) {
    final byte[] userBytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    userBytes[0] = (byte) userId;
    return new PendingRequest(
        PublicKey.createPubKey(userBytes), shares, 0L, createdAt, 0L, 0, RequestType.Redemption,
        new byte[PendingRequest.RESERVED_LEN]
    );
  }

  private static byte[] queueData(final PendingRequest... requests) {
    final var queue = new RequestQueue(
        REQUEST_QUEUE_KEY, RequestQueue.DISCRIMINATOR,
        MINT_PDA, STATE_KEY,
        false, false,
        requests
    );
    final byte[] data = new byte[queue.l()];
    queue.write(data, 0);
    return data;
  }

  private static byte[] mintData(final long supply, final int decimals) {
    final var mint = new software.sava.idl.clients.spl.token.gen.types.Mint(
        MINT_PDA, null, supply, decimals, true, null
    );
    final byte[] data = new byte[software.sava.idl.clients.spl.token.gen.types.Mint.BYTES];
    mint.write(data, 0);
    return data;
  }

  private static byte[] tokenAccountData(final PublicKey mint, final long amount) {
    final byte[] data = new byte[TokenAccount.BYTES];
    mint.write(data, TokenAccount.MINT_OFFSET);
    ByteUtil.putInt64LE(data, TokenAccount.AMOUNT_OFFSET, amount);
    return data;
  }

  private record Harness(SingleAssetFulfillmentService service,
                         ScriptedContext ctx,
                         StateAccountClient stateClient,
                         PublicKey ataKey,
                         MintContext vaultMintContext,
                         MintContext baseAssetMintContext) {

    void serveAccounts(final long slot, final long mintSupply, final Long ataAmount, final byte[] queueData) {
      ctx.accounts.put(MINT_PDA, account(MINT_PDA, slot, SOLANA.tokenProgram(), mintData(mintSupply, 6)));
      if (ataAmount != null) {
        ctx.accounts.put(ataKey, account(ataKey, slot, SOLANA.tokenProgram(), tokenAccountData(BASE_ASSET_MINT, ataAmount)));
      }
      ctx.accounts.put(REQUEST_QUEUE_KEY, account(REQUEST_QUEUE_KEY, slot, GLAM.mintProgram(), queueData));
    }
  }

  private static Harness harness(final NoticePeriodType redeemType,
                                 final TimeUnit timeUnit,
                                 final long noticePeriod,
                                 final boolean softRedeem,
                                 final long minDelayMillis,
                                 final long maxDelayMillis) {
    final var stateClient = stateClient(redeemType, timeUnit, noticePeriod);
    final var ctx = new ScriptedContext(MILLISECONDS.toNanos(minDelayMillis), MILLISECONDS.toNanos(maxDelayMillis));
    final var vaultMintContext = MintContext.createContext(SOLANA, MINT_PDA, 6, SOLANA.tokenProgram());
    final var baseAssetMintContext = MintContext.createContext(SOLANA, BASE_ASSET_MINT, 9, SOLANA.tokenProgram());
    final var service = (SingleAssetFulfillmentService) FulfillmentService.createSingleAssetService(
        ctx, softRedeem, stateClient, vaultMintContext, baseAssetMintContext
    );
    final var ataKey = baseAssetMintContext.ata(
        SOLANA.associatedTokenAccountProgram(),
        stateClient.accountClient().vaultAccounts().vaultPublicKey()
    );
    return new Harness(service, ctx, stateClient, ataKey, vaultMintContext, baseAssetMintContext);
  }

  private static Clock clock(final long slot, final long unixTimestamp) {
    return new Clock(SOLANA.clockSysVar(), slot, 0L, 0L, 0L, unixTimestamp);
  }

  private static void assertSameInstruction(final Instruction expected, final Instruction actual) {
    assertEquals(expected.programId().publicKey(), actual.programId().publicKey());
    assertArrayEquals(expected.copyData(), actual.copyData());
    assertEquals(expected.accounts().size(), actual.accounts().size());
  }

  // --- run-loop behavior ----------------------------------------------------

  @Test
  void aMissingBaseAssetAccountWaitsAndPollsAgain() {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    harness.serveAccounts(500L, 1_000_000L, null, queueData());
    harness.ctx.clock = clock(500L, 10_000L);
    harness.ctx.feePayerLow.addAll(List.of(false, false));

    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      final long start = System.nanoTime();
      harness.service.run();
      final long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
      // two full awaitChange(max) rounds before the script ran out
      assertTrue(elapsedMillis >= 50, () -> "ran only " + elapsedMillis + "ms");
      logs.assertLogged("Waiting for base asset token account");
      logs.assertLogged("Unexpected service failure.");
    }
    assertEquals(3, harness.ctx.fetches);
    assertEquals(List.of(), harness.ctx.processedContexts);
  }

  @Test
  void aLowFeePayerBalanceSkipsTheVaultEntirely() {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    harness.serveAccounts(500L, 1_000_000L, 2_000_000_000L, queueData(pending(1, 40L, 9_000L)));
    harness.ctx.clock = clock(500L, 10_000L);
    harness.ctx.feePayerLow.add(true);

    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      final long start = System.nanoTime();
      harness.service.run();
      final long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
      assertTrue(elapsedMillis >= 25, () -> "ran only " + elapsedMillis + "ms");
    }
    assertEquals(2, harness.ctx.fetches);
    assertEquals(List.of(), harness.ctx.processedContexts);
  }

  @Test
  void fulfillableRedemptionsAreExecutedAndTheNavIsLogged() {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    harness.serveAccounts(500L, 4_000_000L, 2_000_000_000L,
        queueData(pending(1, 40L, 9_000L), pending(2, 10L, 9_950L)));
    harness.ctx.clock = clock(500L, 10_000L);
    harness.ctx.feePayerLow.add(false);
    harness.ctx.processResults.add(true);

    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      final long start = System.nanoTime();
      harness.service.run();
      final long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
      // the redemption maturity wait ran before the script ended the loop
      assertTrue(elapsedMillis >= 25, () -> "ran only " + elapsedMillis + "ms");
      logs.assertLogged("\"name\": \"Test Vault\"");
      logs.assertLogged("\"mintSupply\": 4,");
      logs.assertLogged("\"baseSupply\": 2,");
      logs.assertLogged("\"nav\": \"0.5\"");
    }
    assertEquals(List.of("Test Vault Fulfill Redemptions"), harness.ctx.processedContexts);
    final var batch = harness.ctx.processedBatches.getFirst();
    assertEquals(2, batch.size());
    final var accountClient = harness.stateClient.accountClient();
    assertSameInstruction(accountClient.priceSingleAssetVault(harness.ataKey, true), batch.getFirst());
    assertSameInstruction(
        accountClient.fulfill(BASE_ASSET_MINT, harness.baseAssetMintContext.tokenProgram()),
        batch.getLast()
    );
    assertEquals(List.of(), harness.ctx.backoffs);
  }

  @Test
  void anEmptyQueueSkipsStraightToTheNavLog() {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    harness.serveAccounts(500L, 4_000_000L, 2_000_000_000L, queueData());
    harness.ctx.clock = clock(500L, 10_000L);
    harness.ctx.feePayerLow.add(false);
    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      harness.service.run();
      logs.assertLogged("\"nav\": \"0.5\"");
    }
    assertEquals(List.of(), harness.ctx.processedContexts);
  }

  @Test
  void aFailedFulfillmentBacksOffWithAGrowingCount() {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    harness.serveAccounts(500L, 1_000_000L, 2_000_000_000L, queueData(pending(1, 40L, 9_000L)));
    harness.ctx.clock = clock(500L, 10_000L);
    harness.ctx.feePayerLow.addAll(List.of(false, false));
    harness.ctx.processResults.addAll(List.of(false, false));

    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      harness.service.run();
      // the failure return path never reaches the NAV log
      assertTrue(logs.messages().stream().noneMatch(m -> m != null && m.contains("\"nav\"")), () -> logs.messages().toString());
    }
    assertEquals(List.of(1L, 2L), harness.ctx.backoffs);

    // a success after failures resets the count: next failure backs off with 1 again
    final var recovered = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    recovered.serveAccounts(500L, 1_000_000L, 2_000_000_000L, queueData(pending(1, 40L, 9_000L)));
    recovered.ctx.clock = clock(500L, 10_000L);
    recovered.ctx.feePayerLow.addAll(List.of(false, false, false));
    recovered.ctx.processResults.addAll(List.of(false, true, false));
    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      recovered.service.run();
    }
    assertEquals(List.of(1L, 1L), recovered.ctx.backoffs);
  }

  // --- redemption accounting ------------------------------------------------

  private static RedemptionSummary summary(final long epochSeconds,
                                           final long slot,
                                           final long noticePeriod,
                                           final boolean windowInSeconds,
                                           final PendingRequest... requests) {
    final var queue = new RequestQueue(
        REQUEST_QUEUE_KEY, RequestQueue.DISCRIMINATOR, MINT_PDA, STATE_KEY, false, false, requests
    );
    return RedemptionSummary.createSummary(epochSeconds, slot, queue, noticePeriod, windowInSeconds);
  }

  @Test
  void redemptionAvailabilityInSecondsSlotsAndWithoutSoftRequests() {
    final var seconds = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 77);
    // no soft requests: wait the maximum delay
    assertEquals(
        seconds.ctx.maxDelayNanos,
        seconds.service.redemptionAvailableIn(summary(10_000L, 500L, 100L, true))
    );
    // seconds window: the oldest soft request matures at createdAt + notice
    assertEquals(
        SECONDS.toNanos(50L),
        seconds.service.redemptionAvailableIn(summary(10_000L, 500L, 100L, true, pending(1, 10L, 9_950L)))
    );

    final var slots = harness(NoticePeriodType.Hard, TimeUnit.Slot, 100L, false, 5, 77);
    slots.ctx.medianMillisPerSlot = 400;
    assertEquals(
        MILLISECONDS.toNanos(50L * 400L),
        slots.service.redemptionAvailableIn(summary(10_000L, 500L, 100L, false, pending(1, 10L, 450L)))
    );
  }

  @Test
  void fulfillableRedemptionsRespectTheSoftRedeemFlags() {
    final var hardShares = summary(10_000L, 500L, 100L, true, pending(1, 40L, 9_000L));
    final var softShares = summary(10_000L, 500L, 100L, true, pending(1, 10L, 9_950L));
    final var noShares = summary(10_000L, 500L, 100L, true);

    // hard-fulfillable shares execute regardless of the soft flags
    final var hardState = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, true, 5, 30);
    assertTrue(hardState.service.hasFulfillableRedemptions(hardShares));
    assertFalse(hardState.service.hasFulfillableRedemptions(noShares));
    // soft shares need BOTH the state flag and the service flag
    assertFalse(hardState.service.hasFulfillableRedemptions(softShares));

    final var softState = harness(NoticePeriodType.Soft, TimeUnit.Second, 100L, true, 5, 30);
    assertTrue(softState.service.hasFulfillableRedemptions(softShares));
    assertFalse(softState.service.hasFulfillableRedemptions(noShares));

    final var softStateHardService = harness(NoticePeriodType.Soft, TimeUnit.Second, 100L, false, 5, 30);
    assertFalse(softStateHardService.service.hasFulfillableRedemptions(softShares));
  }

  @Test
  void softRedeemStatesSwapTheFulfillInstruction() throws InterruptedException {
    final var softShares = summary(10_000L, 500L, 100L, true, pending(1, 10L, 9_950L), pending(2, 30L, 9_000L));

    // a soft state fulfilling softly keeps the configured instructions
    final var soft = harness(NoticePeriodType.Soft, TimeUnit.Second, 100L, true, 5, 30);
    soft.ctx.processResults.add(true);
    assertTrue(soft.service.executeRedemptions(softShares));
    final var accountClient = soft.stateClient.accountClient();
    var batch = soft.ctx.processedBatches.getFirst();
    assertEquals(2, batch.size());
    assertSameInstruction(accountClient.priceSingleAssetVault(soft.ataKey, true), batch.getFirst());
    assertSameInstruction(
        accountClient.fulfill(BASE_ASSET_MINT, soft.baseAssetMintContext.tokenProgram()),
        batch.getLast()
    );

    // a soft state fulfilling hard swaps the last instruction for a counted fulfill
    final var counted = harness(NoticePeriodType.Soft, TimeUnit.Second, 100L, false, 5, 30);
    counted.ctx.processResults.add(false);
    assertFalse(counted.service.executeRedemptions(softShares));
    batch = counted.ctx.processedBatches.getFirst();
    assertEquals(2, batch.size());
    assertSameInstruction(accountClient.priceSingleAssetVault(counted.ataKey, true), batch.getFirst());
    assertSameInstruction(
        counted.stateClient.accountClient().fulfill(
            0, BASE_ASSET_MINT, counted.baseAssetMintContext.tokenProgram(),
            OptionalLong.of(softShares.fulfillable().size())
        ),
        batch.getLast()
    );

    // a hard state always keeps the configured instructions
    final var hard = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, true, 5, 30);
    hard.ctx.processResults.add(true);
    assertTrue(hard.service.executeRedemptions(softShares));
    batch = hard.ctx.processedBatches.getFirst();
    assertEquals(2, batch.size());
    assertSameInstruction(
        accountClient.fulfill(BASE_ASSET_MINT, hard.baseAssetMintContext.tokenProgram()),
        batch.getLast()
    );
  }

  @Test
  void fetchedAccountsFeedTheSummaryAndTheVaultMint() {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    // the base asset ATA is deliberately missing: nulls are filtered
    harness.serveAccounts(500L, 5_000_000L, null, queueData(pending(1, 40L, 9_000L)));
    harness.service.fetchAccounts();
    assertEquals(2, harness.service.accountsNeededMap.size());

    final var vaultMint = harness.service.vaultMint();
    assertEquals(5_000_000L, vaultMint.supply());

    final var summary = harness.service.redemptionSummary(clock(500L, 10_000L));
    assertEquals(10_000L, summary.epochSeconds());
    assertEquals(500L, summary.slot());
    assertEquals(java.math.BigDecimal.valueOf(40L), summary.fulfillableShares());

    harness.ctx.clock = clock(501L, 10_001L);
    assertEquals(10_001L, harness.service.redemptionSummary().epochSeconds());

    // a refetch replaces the previous snapshot: vanished accounts drop out
    harness.ctx.accounts.remove(MINT_PDA);
    harness.service.fetchAccounts();
    assertEquals(1, harness.service.accountsNeededMap.size());
    assertFalse(harness.service.accountsNeededMap.containsKey(MINT_PDA));
  }

  @Test
  void serviceCreationRejectsUntokenizedAndMismatchedVaults() {
    final var ctx = new ScriptedContext(1L, 2L);
    final var baseAssetMintContext = MintContext.createContext(SOLANA, BASE_ASSET_MINT, 9, SOLANA.tokenProgram());
    final var vaultMintContext = MintContext.createContext(SOLANA, MINT_PDA, 6, SOLANA.tokenProgram());

    final var untokenized = stateClient(NoticePeriodType.Hard, TimeUnit.Second, 100L, PublicKey.NONE);
    final var none = assertThrows(IllegalStateException.class, () ->
        FulfillmentService.createSingleAssetService(ctx, false, untokenized, vaultMintContext, baseAssetMintContext));
    assertEquals("Must be a tokenized vault", none.getMessage());

    final var tokenized = stateClient(NoticePeriodType.Hard, TimeUnit.Second, 100L, MINT_PDA);
    final var mismatched = MintContext.createContext(SOLANA, BASE_ASSET_MINT, 6, SOLANA.tokenProgram());
    final var mismatch = assertThrows(IllegalStateException.class, () ->
        FulfillmentService.createSingleAssetService(ctx, false, tokenized, mismatched, baseAssetMintContext));
    assertTrue(mismatch.getMessage().startsWith("Expected vault ATA to be the mint"), mismatch::getMessage);
  }

  // --- awaitChange / wakeUp -------------------------------------------------

  private static Thread waiter(final BaseFulfillmentService service, final long delayNanos) throws InterruptedException {
    final var thread = new Thread(() -> {
      try {
        service.awaitChange(delayNanos);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    thread.start();
    final long deadline = System.nanoTime() + SECONDS.toNanos(5);
    while (thread.getState() != Thread.State.TIMED_WAITING) {
      assertTrue(System.nanoTime() < deadline, "waiter never parked");
      //noinspection BusyWait
      Thread.sleep(1L);
    }
    return thread;
  }

  private static void assertWakes(final Thread thread) throws InterruptedException {
    thread.join(2_000L);
    assertFalse(thread.isAlive(), "expected the waiter to be woken");
  }

  private static void assertStillWaiting(final BaseFulfillmentService service, final Thread thread) throws InterruptedException {
    Thread.sleep(50L);
    assertTrue(thread.isAlive(), "expected the waiter to stay parked");
    service.wakeUp();
    thread.join(2_000L);
    assertFalse(thread.isAlive());
  }

  @Test
  void awaitChangeClampsTheDelayAndEnforcesTheFloor() throws InterruptedException {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 50, 90);
    final var service = harness.service;

    // a tiny delay is raised to the floor
    long start = System.nanoTime();
    service.awaitChange(1L);
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
    assertTrue(elapsedMillis >= 40, "slept only " + elapsedMillis + "ms");

    // a huge delay is capped at the ceiling
    start = System.nanoTime();
    service.awaitChange(SECONDS.toNanos(30L));
    elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
    assertTrue(elapsedMillis >= 70, "slept only " + elapsedMillis + "ms");
    assertTrue(elapsedMillis < 5_000, "the cap did not apply: " + elapsedMillis + "ms");

    // a mid-wait wake still sleeps out the minimum, and only the minimum:
    // the top-up is minimum-minus-slept, not minimum-plus-slept
    final var floor = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 300, 10_000);
    start = System.nanoTime();
    final var thread = waiter(floor.service, SECONDS.toNanos(10L));
    Thread.sleep(150L);
    floor.service.wakeUp();
    assertWakes(thread);
    elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
    assertTrue(elapsedMillis >= 280, "slept only " + elapsedMillis + "ms");
    assertTrue(elapsedMillis < 520, "the wake-up was lost or over-slept: " + elapsedMillis + "ms");
  }

  // --- websocket updates ----------------------------------------------------

  private static AccountInfo<byte[]> queueAccount(final long slot, final PendingRequest... requests) {
    return account(REQUEST_QUEUE_KEY, slot, GLAM.mintProgram(), queueData(requests));
  }

  private static AccountInfo<byte[]> ataAccount(final Harness harness, final long slot, final long amount) {
    return account(harness.ataKey, slot, SOLANA.tokenProgram(), tokenAccountData(BASE_ASSET_MINT, amount));
  }

  @Test
  void aRequestQueueUpdateWakesOnAnOutstandingChange() throws InterruptedException {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30_000);
    final var service = harness.service;

    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      // first sighting of outstanding shares
      var thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(queueAccount(10L, pending(1, 100L, 9_000L)));
      assertWakes(thread);

      // a stale slot is ignored
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(queueAccount(10L, pending(1, 150L, 9_000L)));
      assertStillWaiting(service, thread);

      // a newer slot with the same outstanding shares is quiet
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(queueAccount(11L, pending(2, 100L, 9_100L)));
      assertStillWaiting(service, thread);

      // a newer slot with different outstanding shares wakes
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(queueAccount(12L, pending(1, 150L, 9_000L)));
      assertWakes(thread);

      // none of the above may have logged a processing failure
      assertEquals(List.of(), logs.messages());
    }
  }

  @Test
  void aBaseAssetDepositWakesOnlyWithOutstandingShares() throws InterruptedException {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30_000);
    final var service = harness.service;

    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      // no summary yet: a deposit alone does not wake
      var thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(ataAccount(harness, 20L, 5L));
      assertStillWaiting(service, thread);

      // outstanding shares recorded, then a balance increase wakes
      service.accept(queueAccount(10L, pending(1, 100L, 9_000L)));
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(ataAccount(harness, 21L, 9L));
      assertWakes(thread);

      // no increase: quiet
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(ataAccount(harness, 22L, 9L));
      assertStillWaiting(service, thread);

      // the same slot: quiet even with a bigger amount
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(ataAccount(harness, 22L, 50L));
      assertStillWaiting(service, thread);

      // a stale slot: quiet even with a bigger amount
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(ataAccount(harness, 21L, 50L));
      assertStillWaiting(service, thread);

      // another mint's token account is ignored
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(account(harness.ataKey, 23L, SOLANA.tokenProgram(), tokenAccountData(MINT_PDA, 100L)));
      assertStillWaiting(service, thread);

      assertEquals(List.of(), logs.messages());
    }
  }

  @Test
  void theFirstDepositSightingAfterOutstandingSharesWakes() throws InterruptedException {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30_000);
    final var service = harness.service;
    service.accept(queueAccount(10L, pending(1, 100L, 9_000L)));
    final var thread = waiter(service, SECONDS.toNanos(30L));
    service.accept(ataAccount(harness, 20L, 5L));
    assertWakes(thread);
  }

  @Test
  void aDepositWithNothingOutstandingStaysQuiet() throws InterruptedException {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30_000);
    final var service = harness.service;
    service.accept(queueAccount(5L)); // an empty queue: zero outstanding shares

    var thread = waiter(service, SECONDS.toNanos(30L));
    service.accept(ataAccount(harness, 6L, 3L));
    assertStillWaiting(service, thread);

    thread = waiter(service, SECONDS.toNanos(30L));
    service.accept(ataAccount(harness, 7L, 9L));
    assertStillWaiting(service, thread);
  }

  @Test
  void foreignAccountShapesAreIgnored() throws InterruptedException {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30_000);
    final var service = harness.service;
    // seed outstanding shares and a balance so a mistaken match would wake
    service.accept(queueAccount(10L, pending(1, 100L, 9_000L)));
    service.accept(ataAccount(harness, 20L, 5L));

    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      // token-account bytes owned by the mint program: not a queue update
      var thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(account(harness.ataKey, 30L, GLAM.mintProgram(), tokenAccountData(BASE_ASSET_MINT, 99L)));
      assertStillWaiting(service, thread);

      // queue bytes owned by the token program: not a queue update either
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(account(REQUEST_QUEUE_KEY, 31L, SOLANA.tokenProgram(), queueData(pending(1, 500L, 9_000L))));
      assertStillWaiting(service, thread);

      // an oversized account whose head looks like the base asset ATA is not one
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(account(harness.ataKey, 32L, SOLANA.tokenProgram(),
          Arrays.copyOf(tokenAccountData(BASE_ASSET_MINT, 99L), 200)));
      assertStillWaiting(service, thread);

      // the right shape under the wrong owner is ignored
      thread = waiter(service, SECONDS.toNanos(30L));
      service.accept(account(harness.ataKey, 33L, SOLANA.systemProgram(), tokenAccountData(BASE_ASSET_MINT, 99L)));
      assertStillWaiting(service, thread);

      assertEquals(List.of(), logs.messages());
    }
  }

  @Test
  void aMalformedUpdateIsLoggedAndSwallowed() {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    final byte[] truncated = Arrays.copyOf(queueData(pending(1, 100L, 9_000L)), 12);
    try (final var logs = LogCapture.attach(LOGGER_NAME)) {
      harness.service.accept(account(REQUEST_QUEUE_KEY, 10L, GLAM.mintProgram(), truncated));
      logs.assertLogged("Failed to process Glam State Change");
    }
  }

  @Test
  void subscribeRegistersTheQueueAndTheBaseAssetAccount() {
    final var harness = harness(NoticePeriodType.Hard, TimeUnit.Second, 100L, false, 5, 30);
    final var subscribed = new ArrayList<PublicKey>();
    final var websocket = (software.sava.rpc.json.http.ws.SolanaRpcWebsocket) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.ws.SolanaRpcWebsocket.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.ws.SolanaRpcWebsocket.class},
        (proxy, method, args) -> {
          if (method.getName().equals("accountSubscribe")) {
            subscribed.add((PublicKey) args[0]);
            assertSame(harness.service, args[1]);
            return Boolean.TRUE;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
    harness.service.subscribe(websocket);
    assertEquals(List.of(REQUEST_QUEUE_KEY, harness.ataKey), subscribed);
  }
}
