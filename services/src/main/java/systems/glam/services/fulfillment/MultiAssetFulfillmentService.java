package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.drift.DriftProgramClient;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.idl.clients.drift.vaults.DriftVaultsProgramClient;
import software.sava.idl.clients.drift.vaults.gen.types.VaultDepositor;
import software.sava.idl.clients.kamino.lend.KaminoLendClient;
import software.sava.idl.clients.kamino.vaults.KaminoVaultsClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.ServiceContext;
import systems.glam.services.fulfillment.drfit.DriftUserPosition;
import systems.glam.services.fulfillment.kamino.KaminoVaultPosition;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.pricing.accounting.TokenPosition;
import systems.glam.services.tokens.MintContext;

import java.util.*;

import static java.lang.System.Logger.Level.WARNING;
import static systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants.PROTO_KAMINO_VAULTS;

public class MultiAssetFulfillmentService extends BaseFulfillmentService {

  private final IntegrationServiceContext integContext;
  private final DriftProgramClient driftProgramClient;
  private final DriftVaultsProgramClient driftVaultClient;
  private final KaminoVaultsClient kaminoVaultsClient;
  private final KaminoLendClient kaminoLendClient;
  private final Set<PublicKey> accountsNeededSet;
  private final Map<PublicKey, Position> positions;

  private StateAccount previousStateAccount;

  MultiAssetFulfillmentService(final ServiceContext serviceContext,
                               final GlamAccountClient glamAccountClient,
                               final StateAccountClient stateAccountClient,
                               final StateAccount stateAccount,
                               final MintContext vaultMintContext,
                               final MintContext baseAssetMintContext,
                               final PublicKey baseAssetVaultAta,
                               final boolean softRedeem,
                               final PublicKey requestQueueKey,
                               final List<PublicKey> accountsNeededList,
                               final List<Instruction> fulFillInstructions,
                               final IntegrationServiceContext integContext,
                               final Map<PublicKey, Position> positions) {
    super(
        serviceContext,
        glamAccountClient,
        stateAccountClient,
        baseAssetMintContext,
        baseAssetVaultAta,
        softRedeem,
        requestQueueKey,
        vaultMintContext,
        accountsNeededList,
        fulFillInstructions
    );
    this.integContext = integContext;
    this.driftProgramClient = DriftProgramClient.createClient(glamAccountClient);
    this.driftVaultClient = DriftVaultsProgramClient.createClient(glamAccountClient);
    this.kaminoLendClient = KaminoLendClient.createClient(glamAccountClient);
    this.kaminoVaultsClient = KaminoVaultsClient.createClient(glamAccountClient);
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
        final var mintContext = integContext.mintContext(assetMint);
        final var tokenPosition = new TokenPosition(mintContext, glamAccountClient);
        positions.put(assetMint, tokenPosition);
        accountsNeededSet.add(tokenPosition.vaultATA());
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
        changed = true; // Need the context of the account to determine the position type.
      } else if (!positions.containsKey(externalAccount)) {
        changed = true;
        final var accountInfo = accountsNeededMap.get(externalAccount);
        if (context.isTokenAccount(accountInfo)) {
          final var tokenAccount = TokenAccount.read(accountInfo.pubKey(), accountInfo.data());
          final var kVaultCache = integContext.kaminoVaultCache();
          final var kVaultContext = kVaultCache.vaultForShareMint(tokenAccount.mint());
          if (kVaultContext == null) {
            if (protocolEnabled(stateAccount, glamAccountClient.glamAccounts().kaminoIntegrationProgram(), PROTO_KAMINO_VAULTS)) {
              // TODO: refresh kamino vaults
              // TODO: consider only fetching this vault with a program account filter by share mint.
            } else {
              final var msg = String.format("""
                      {
                       "event": "Unsupported External Token Position",
                       "tokenAccount": "%s",
                       "mint": "%s",
                       "programOwner": "%s",
                       "owner": "%s"
                      }""",
                  accountInfo.pubKey(), tokenAccount.mint(), accountInfo.owner(), tokenAccount.owner()
              );
              logger.log(WARNING, msg);
              // TODO: Trigger external alert and put this vault on pause.
            }
          } else {
            final var vaultState = kVaultContext.vaultState();
            final var kVaultPosition = new KaminoVaultPosition(
                integContext.mintContext(vaultState.tokenMint()),
                glamAccountClient,
                kaminoVaultsClient,
                tokenAccount.address(),
                kVaultCache,
                vaultState._address()
            );
            kVaultPosition.accountsForPriceInstruction(accountsNeededSet);
            positions.put(externalAccount, kVaultPosition);
          }
        } else {
          final var programOwner = accountInfo.owner();
          final byte[] data = accountInfo.data();
          if (programOwner.equals(integContext.driftProgram())) {
            if (User.BYTES == data.length && User.DISCRIMINATOR.equals(data, 0)) {
              final var driftMarketCache = integContext.driftMarketCache();
              final var driftPosition = positions.values().stream()
                  .filter(position -> position instanceof DriftUserPosition)
                  .findFirst().map(position -> (DriftUserPosition) position)
                  .orElseGet(() -> DriftUserPosition.create(driftMarketCache, glamAccountClient.vaultAccounts().vaultPublicKey()));
              driftPosition.addUserAccount(externalAccount);
              driftPosition.accountsForPriceInstruction(accountsNeededSet);
              positions.put(externalAccount, driftPosition);
            } else {
              final var msg = String.format("""
                      {
                       "event": "Unsupported Drift Position",
                       "account": "%s"
                      }""",
                  accountInfo.pubKey()
              );
              logger.log(WARNING, msg);
              // TODO: Trigger external alert and put this vault on pause.
            }
          } else if (programOwner.equals(integContext.driftVaultsProgram())) {
            if (VaultDepositor.BYTES == data.length && VaultDepositor.DISCRIMINATOR.equals(data, 0)) {

            } else {
              final var msg = String.format("""
                      {
                       "event": "Unsupported Drift Position",
                       "account": "%s"
                      }""",
                  accountInfo.pubKey()
              );
              logger.log(WARNING, msg);
              // TODO: Trigger external alert and put this vault on pause.
            }
          } else if (programOwner.equals(integContext.kLendProgram())) {

          } else if (programOwner.equals(integContext.kVaultsProgram())) {

          }
        }
      }
    }
    return changed;
  }

  static boolean protocolEnabled(final StateAccount stateAccount,
                                 final PublicKey integrationProgram,
                                 final int protocolBitFlag) {
    for (final var integrationAcl : stateAccount.integrationAcls()) {
      if (integrationAcl.integrationProgram().equals(integrationProgram) && (integrationAcl.protocolsBitmask() & protocolBitFlag) == protocolBitFlag) {
        return true;
      }
    }
    return false;
  }

  private boolean isTokenAccount(final AccountInfo<byte[]> accountInfo) {
    final var programOwner = accountInfo.owner();
    if (programOwner.equals(context.tokenProgram()) && accountInfo.data().length == TokenAccount.BYTES) {
      return true;
    } else {
      return programOwner.equals(context.token2022Program());
    }
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
