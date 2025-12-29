package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.StateAccountClient;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.tokens.MintContext;

import java.math.BigInteger;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;

public interface FulfillmentService extends Runnable {

  static FulfillmentService createSingleAssetService(final StateAccountClient stateAccountClient,
                                                     final MintContext vaultMintContext,
                                                     final MintContext baseAssetMintContext,
                                                     final RpcCaller rpcCaller,
                                                     final InstructionProcessor instructionProcessor,
                                                     final GlamAccountClient glamAccountClient,
                                                     final BigInteger warnFeePayerBalance,
                                                     final BigInteger minFeePayerBalance,
                                                     final Duration minCheckStateDelay,
                                                     final Duration maxCheckStateDelay,
                                                     final Backoff backoff) {
    final var accountsNeededSet = HashSet.<PublicKey>newHashSet(4);

    final var glamAccounts = glamAccountClient.glamAccounts();
    final var vaultAccounts = glamAccountClient.vaultAccounts();
    final var vaultMintKey = validateMintKey(stateAccountClient, vaultAccounts, vaultMintContext);

    accountsNeededSet.add(vaultMintKey);
    accountsNeededSet.add(baseAssetMintContext.vaultATA());

    final var requestQueueKey = glamAccounts.requestQueuePDA(vaultMintKey).publicKey();
    accountsNeededSet.add(requestQueueKey);

    final var feePayerKey = glamAccountClient.feePayer().publicKey();
    accountsNeededSet.add(feePayerKey);

    final var mintProgram = glamAccounts.mintProgram();

//    final var solanaAccounts = glamAccountClient.solanaAccounts();
//    final var escrow = glamAccounts.escrowPDA(vaultMintKey).publicKey();
//    final var splClient = glamAccountClient.splClient();

//    final var escrowMintTokenAccount = glamAccountClient.escrowMintTokenAccount(escrow, vaultMintKey).publicKey();
//    final var createEscrowMintAccountIx = glamAccountClient.createEscrowAssociatedTokenIdempotent(
//        escrowMintTokenAccount, escrow,
//        vaultMintKey, solanaAccounts.token2022Program()
//    );
//
//    final var escrowBaseAssetTokenAccount = splClient.findATA(escrow, baseAssetMintContext.tokenProgram(), baseAssetMintContext.mint());
//    final var createEscrowBaseAssetTokenAccountIx = glamAccountClient.createEscrowAssociatedTokenIdempotent(
//        escrowBaseAssetTokenAccount.publicKey(), escrow,
//        baseAssetMintContext.mint(), baseAssetMintContext.tokenProgram()
//    );

    final var priceVaultIx = glamAccountClient.priceSingleAssetVault(baseAssetMintContext.vaultATA(), true);
    final var fulFillIx = glamAccountClient.fulfill(baseAssetMintContext.mint(), baseAssetMintContext.tokenProgram());

    final var fulFillInstructions = List.of(priceVaultIx, fulFillIx);

    return new SingleAssetFulfillmentService(
        mintProgram,
        vaultAccounts.glamStateKey(),
        stateAccountClient.name(),
        vaultMintContext,
        baseAssetMintContext,
        requestQueueKey,
        List.copyOf(accountsNeededSet),
        rpcCaller,
        fulFillInstructions,
        instructionProcessor,
        instructions -> Transaction.createTx(feePayerKey, instructions),
        feePayerKey,
        warnFeePayerBalance, minFeePayerBalance,
        minCheckStateDelay, maxCheckStateDelay,
        backoff
    );
  }

  private static PublicKey validateMintKey(final StateAccountClient stateAccountClient,
                                           final GlamVaultAccounts vaultAccounts,
                                           final MintContext vaultMintContext) {
    final var vaultMintKey = stateAccountClient.mint();
    if (vaultMintKey == null || vaultMintKey.equals(PublicKey.NONE)) {
      throw new IllegalStateException("Must be a tokenized vault");
    }
    final var mint = vaultAccounts.mintPDA().publicKey();
    if (!vaultMintContext.mint().equals(mint)) {
      throw new IllegalStateException("Expected vault ATA to be the mint: " + mint + ", but was: " + vaultMintKey + ".");
    }
    return vaultMintKey;
  }

  void subscribe(final SolanaRpcWebsocket websocket);
}
