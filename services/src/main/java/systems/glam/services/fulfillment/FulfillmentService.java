package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.StateAccountClient;
import systems.glam.services.ServiceContext;
import systems.glam.services.tokens.MintContext;

import java.util.HashSet;
import java.util.List;

public interface FulfillmentService extends Runnable {

  static FulfillmentService createSingleAssetService(final ServiceContext serviceContext,
                                                     final boolean softRedeem,
                                                     final StateAccountClient stateAccountClient,
                                                     final MintContext vaultMintContext,
                                                     final MintContext baseAssetMintContext) {
    final var accountsNeededSet = HashSet.<PublicKey>newHashSet(5);

    final var glamAccountClient = stateAccountClient.accountClient();
    final var solanaAccounts = glamAccountClient.solanaAccounts();
    final var glamAccounts = glamAccountClient.glamAccounts();
    final var vaultAccounts = glamAccountClient.vaultAccounts();
    final var vaultMintKey = validateMintKey(stateAccountClient, vaultAccounts, vaultMintContext);

    final var clockSysVar = solanaAccounts.clockSysVar();
    accountsNeededSet.add(clockSysVar);
    accountsNeededSet.add(vaultMintKey);
    final var baseAssetVaultAta = baseAssetMintContext.ata(solanaAccounts.associatedTokenAccountProgram(), vaultAccounts.vaultPublicKey());
    accountsNeededSet.add(baseAssetVaultAta);

    final var requestQueueKey = glamAccounts.requestQueuePDA(vaultMintKey).publicKey();
    accountsNeededSet.add(requestQueueKey);

    final var feePayerKey = glamAccountClient.feePayer().publicKey();
    accountsNeededSet.add(feePayerKey);

    final var priceVaultIx = glamAccountClient.priceSingleAssetVault(baseAssetVaultAta, true);
    final var fulFillIx = glamAccountClient.fulfill(baseAssetMintContext.mint(), baseAssetMintContext.tokenProgram());

    final var fulFillInstructions = List.of(priceVaultIx, fulFillIx);

    return new SingleAssetFulfillmentService(
        serviceContext,
        glamAccountClient,
        stateAccountClient,
        vaultMintContext,
        baseAssetMintContext,
        baseAssetVaultAta,
        softRedeem,
        requestQueueKey,
        List.copyOf(accountsNeededSet),
        fulFillInstructions
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
