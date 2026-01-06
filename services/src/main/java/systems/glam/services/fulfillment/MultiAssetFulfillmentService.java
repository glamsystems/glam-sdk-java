package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.tokens.MintContext;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

public class MultiAssetFulfillmentService extends BaseFulfillmentService {

  private final Set<PublicKey> accountsNeededSet;
  private final Map<PublicKey, Position> positions;

  private StateAccount previousStateAccount;

  MultiAssetFulfillmentService(final EpochInfoService epochInfoService,
                               final GlamAccountClient glamAccountClient,
                               final PublicKey glamMintProgram,
                               final StateAccountClient stateAccountClient,
                               final StateAccount stateAccount,
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
                               final Backoff backoff,
                               final Map<PublicKey, Position> positions) {
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
    this.positions = positions;
    this.previousStateAccount = stateAccount;
    this.accountsNeededSet = HashSet.newHashSet(accountsNeededList.size());
    this.accountsNeededSet.addAll(accountsNeededList);
  }

  private boolean stateAccountChanged(final StateAccount stateAccount) {
    final var assets = stateAccount.assets();
    Arrays.sort(assets);
    for (final var previousMint : previousStateAccount.assets()) {
      if (Arrays.binarySearch(assets, previousMint) < 0) {
        accountsNeededSet.remove(previousMint);
        positions.remove(previousMint);
      }
    }
    boolean changed = false;
    for (final var assetMint : assets) {
      if (accountsNeededSet.add(assetMint)) {
        changed = true;
      }
    }

    final var externalPositions = stateAccount.externalPositions();
    Arrays.sort(externalPositions);
    for (final var externalAccount : previousStateAccount.externalPositions()) {
      if (Arrays.binarySearch(externalPositions, externalAccount) < 0) {
        accountsNeededSet.remove(externalAccount);
        positions.remove(externalAccount);
      }
    }
    for (final var externalAccount : stateAccount.externalPositions()) {
      if (accountsNeededSet.add(externalAccount)) {
        changed = true;
      }
    }
    return changed;
  }

  @Override
  protected void handleVault() throws InterruptedException {
    final var stateAccount = stateAccount();
    if (stateAccountChanged(stateAccount)) {
      return;
    }

  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {

  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {

  }
}
