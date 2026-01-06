package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.fulfillment.accounting.RedemptionSummary;
import systems.glam.services.tokens.MintContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.lang.Long.toUnsignedString;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

final class SingleAssetFulfillmentService extends BaseFulfillmentService {

  private final AtomicReference<TokenBalance> baseAssetTokenBalance;
  private final AtomicReference<RedemptionSummary> redemptionSummary;

  SingleAssetFulfillmentService(final EpochInfoService epochInfoService,
                                final GlamAccountClient glamAccountClient,
                                final PublicKey glamMintProgram,
                                final StateAccountClient stateAccountClient,
                                final MintContext vaultMintContext,
                                final MintContext baseAssetMintContext,
                                final PublicKey clockSysVar,
                                final boolean softRedeem,
                                final PublicKey requestQueueKey,
                                final List<PublicKey> accountsNeededList,
                                final RpcCaller rpcCaller,
                                final List<Instruction> fulFillInstructions,
                                final InstructionProcessor instructionProcessor,
                                final Function<List<Instruction>, Transaction> transactionFactory,
                                final PublicKey feePayerKey,
                                final BigInteger warnFeePayerBalance,
                                final BigInteger minFeePayerBalance,
                                final Duration minCheckStateDelay,
                                final Duration maxCheckStateDelay,
                                final Backoff backoff) {
    super(
        epochInfoService,
        glamAccountClient,
        glamMintProgram,
        stateAccountClient,
        baseAssetMintContext,
        clockSysVar,
        softRedeem,
        requestQueueKey,
        vaultMintContext,
        accountsNeededList,
        rpcCaller,
        fulFillInstructions,
        instructionProcessor,
        transactionFactory,
        feePayerKey,
        warnFeePayerBalance, minFeePayerBalance,
        minCheckStateDelay, maxCheckStateDelay,
        backoff
    );
    this.baseAssetTokenBalance = new AtomicReference<>();
    this.redemptionSummary = new AtomicReference<>();
  }

  @Override
  protected void handleVault() throws InterruptedException {
    final var tokenAccountInfo = accountsNeededMap.get(baseAssetTokenAccountKey);
    if (tokenAccountInfo == null) { // May be null if yet to receive a deposit.
      logger.log(INFO, "Waiting for base asset token account");
      awaitChange();
      return;
    }

    final var vaultMint = vaultMint();
    final var vaultMintSupply = new BigDecimal(toUnsignedString(vaultMint.supply()))
        .movePointLeft(vaultMintContext.decimals())
        .stripTrailingZeros();

    final var baseAssetTokenAccount = TokenAccount.read(tokenAccountInfo.pubKey(), tokenAccountInfo.data());
    compareAndSet(new TokenBalance(tokenAccountInfo.context().slot(), baseAssetTokenAccount.amount()));
    final var vaultHoldings = new BigDecimal(toUnsignedString(baseAssetTokenAccount.amount()))
        .movePointLeft(baseAssetMintContext.decimals())
        .stripTrailingZeros();

    // TODO: account for vault fees
    // SEVERE: Vault mint supply 999000 is greater than holdings 1
//        if (vaultMintSupply.compareTo(vaultHoldings) > 0) {
//          final var msg = String.format("Vault mint supply %s is greater than holdings %s", vaultMintSupply, vaultHoldings);
//          logger.log(ERROR, msg);
//          throw new IllegalStateException(msg);
//        }

    final var redemptionSummary = redemptionSummary();
    compareAndSet(redemptionSummary);

    if (hasFulfillableRedemptions(redemptionSummary)) {
      final var fulfilled = executeRedemptions(redemptionSummary);
      if (fulfilled) {
        failureCount = 0;
      } else {
        backoff(++failureCount);
        return;
      }
    }

    final var nav = vaultHoldings.divide(vaultMintSupply, 4, RoundingMode.HALF_EVEN).stripTrailingZeros();
    logger.log(INFO, String.format("""
                {
                 "name": "%s",
                 "mintSupply": %s,
                 "baseSupply": %s,
                 "nav": "%s"
                }""",
            vaultName,
            vaultMintSupply.toPlainString(),
            vaultHoldings.toPlainString(),
            nav.toPlainString()
        )
    );

    awaitChange(redemptionAvailableIn(redemptionSummary));
  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    websocket.accountSubscribe(requestQueueKey, this);
    websocket.accountSubscribe(baseAssetTokenAccountKey, this);
  }

  record TokenBalance(long slot, long amount) {

  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    try {
      final long slot = accountInfo.context().slot();
      final byte[] data = accountInfo.data();
      final var owner = accountInfo.owner();

      if (RequestQueue.DISCRIMINATOR.equals(data, 0) && owner.equals(glamMintProgram)) {
        final var redemptionSummary = RedemptionSummary.createSummary(
            Instant.now().getEpochSecond(), slot,
            RequestQueue.read(accountInfo),
            redeemNoticePeriod, redeemWindowInSeconds
        );
        final var previousAmount = compareAndSet(redemptionSummary);
        if (previousAmount != null && previousAmount.compareTo(redemptionSummary.outstandingShares()) != 0) {
          wakeUp();
        }
      } else if (data.length == TokenAccount.BYTES && owner.equals(baseAssetMintContext.tokenProgram())) {
        final var tokenAccount = TokenAccount.read(accountInfo.pubKey(), data);
        if (tokenAccount.mint().equals(baseAssetMintContext.mint())) {
          final var baseAssetAmount = tokenAccount.amount();
          final var tokenBalance = new TokenBalance(slot, baseAssetAmount);
          final long previousAmount = compareAndSet(tokenBalance);
          if (Long.compareUnsigned(baseAssetAmount, previousAmount) > 0) {
            final var redemptionSummary = this.redemptionSummary.get();
            if (redemptionSummary != null && redemptionSummary.outstandingShares().signum() > 0) {
              wakeUp();
            }
          }
        }
      }
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Failed to process Glam State Change: " + Base64.getEncoder().encodeToString(accountInfo.data()), ex);
    }
  }

  private BigDecimal compareAndSet(final RedemptionSummary redemptionSummary) {
    final long slot = redemptionSummary.slot();
    for (RedemptionSummary previous = this.redemptionSummary.get(), witness; ; ) {
      if (previous == null) {
        witness = this.redemptionSummary.compareAndExchange(null, redemptionSummary);
        if (witness == null) {
          return BigDecimal.ZERO;
        } else {
          previous = witness;
        }
      } else if (Long.compareUnsigned(slot, previous.slot()) > 0) {
        witness = this.redemptionSummary.compareAndExchange(previous, redemptionSummary);
        if (witness == previous) {
          return previous.outstandingShares();
        } else {
          previous = witness;
        }
      } else {
        return null;
      }
    }
  }

  private long compareAndSet(final TokenBalance tokenBalance) {
    final long slot = tokenBalance.slot();
    for (TokenBalance previous = this.baseAssetTokenBalance.get(), witness; ; ) {
      if (previous == null) {
        witness = this.baseAssetTokenBalance.compareAndExchange(null, tokenBalance);
        if (witness == null) {
          return 0;
        } else {
          previous = witness;
        }
      } else if (Long.compareUnsigned(slot, previous.slot()) > 0) {
        witness = this.baseAssetTokenBalance.compareAndExchange(previous, tokenBalance);
        if (witness == previous) {
          return previous.amount();
        } else {
          previous = witness;
        }
      } else {
        return -1;
      }
    }
  }
}
