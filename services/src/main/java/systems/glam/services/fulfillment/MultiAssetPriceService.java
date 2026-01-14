package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.LookupTableAccountMeta;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.idl.clients.drift.vaults.gen.types.VaultDepositor;
import software.sava.idl.clients.kamino.lend.gen.types.Obligation;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamUtil;
import systems.glam.sdk.idl.programs.glam.mint.gen.events.AumRecord;
import systems.glam.sdk.idl.programs.glam.mint.gen.events.GlamMintEvent;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.ServiceContext;
import systems.glam.services.fulfillment.drfit.DriftUserPosition;
import systems.glam.services.fulfillment.kamino.KaminoVaultPosition;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.PositionReport;
import systems.glam.services.pricing.RunnableAccountConsumer;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.pricing.accounting.TokenPosition;

import java.math.BigInteger;
import java.util.*;

import static java.lang.System.Logger.Level.WARNING;
import static systems.glam.services.pricing.accounting.TxSimulationPriceGroup.MAIN_NET_CU_LIMIT_SIMULATION;

public class MultiAssetPriceService extends BaseDelegateService implements RunnableAccountConsumer {

  private static final System.Logger logger = System.getLogger(MultiAssetPriceService.class.getName());

  private final IntegrationServiceContext integContext;
  private final Set<PublicKey> accountsNeededSet;
  private final Map<PublicKey, Position> positions;
  private final Instruction validateAUMInstruction;

  private StateAccount previousStateAccount;
  private volatile Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap;

  MultiAssetPriceService(final ServiceContext serviceContext,
                         final IntegrationServiceContext integContext,
                         final GlamAccountClient glamAccountClient,
                         final StateAccount stateAccount,
                         final List<PublicKey> accountsNeededList,
                         final Map<PublicKey, Position> positions) {
    super(
        serviceContext,
        glamAccountClient,
        GlamUtil.parseFixLengthString(stateAccount.name())
    );
    this.integContext = integContext;
    this.positions = positions;
    this.previousStateAccount = stateAccount;
    this.accountsNeededSet = HashSet.newHashSet(accountsNeededList.size());
    this.accountsNeededSet.addAll(accountsNeededList);
    this.validateAUMInstruction = glamAccountClient.validateAum(true);
  }

  enum ChangeState {
    NO_CHANGE,
    ACCOUNTS_NEEDED,
    UNSUPPORTED
  }

  private ChangeState createPosition(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap,
                                     final StateAccount stateAccount,
                                     final PublicKey externalAccount) {
    final var accountInfo = accountsNeededMap.get(externalAccount);
    if (context.isTokenAccount(accountInfo)) {
      return createKVaultPosition(stateAccount, accountInfo);
    } else {
      final var programOwner = accountInfo.owner();
      if (programOwner.equals(integContext.driftProgram())) {
        return createDriftPosition(accountInfo);
      } else if (programOwner.equals(integContext.driftVaultsProgram())) {
        return createDriftVaultPosition(accountInfo);
      } else if (programOwner.equals(integContext.kLendProgram())) {
        return createKaminoLendPosition(accountInfo);
      } else if (programOwner.equals(integContext.kVaultsProgram())) {
        return createKaminoVaultPosition(accountInfo);
      } else {
        logger.log(WARNING, "Unsupported integration program: {0}", programOwner);
        return ChangeState.UNSUPPORTED;
      }
    }
  }

  private ChangeState stateAccountChanged(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap,
                                          final StateAccount stateAccount) {
    final var assets = stateAccount.assets();

    Arrays.sort(assets);
    for (final var previousMint : previousStateAccount.assets()) {
      if (Arrays.binarySearch(assets, previousMint) < 0) {
        accountsNeededSet.remove(previousMint);
        positions.remove(previousMint);
      }
    }

    var changeState = ChangeState.NO_CHANGE;
    for (final var assetMint : assets) {
      if (accountsNeededSet.add(assetMint)) {
        final var mintContext = integContext.mintContext(assetMint);
        final var tokenPosition = new TokenPosition(mintContext, glamAccountClient);
        positions.put(assetMint, tokenPosition);
        accountsNeededSet.add(tokenPosition.vaultATA());
        changeState = ChangeState.ACCOUNTS_NEEDED;
      }
    }

    final var externalPositions = stateAccount.externalPositions();

    Arrays.sort(externalPositions);
    for (final var externalAccount : previousStateAccount.externalPositions()) {
      if (Arrays.binarySearch(externalPositions, externalAccount) < 0) {
        accountsNeededSet.remove(externalAccount);
        final var position = positions.remove(externalAccount);
        position.removeAccount(externalAccount);
      }
    }

    for (final var externalAccount : stateAccount.externalPositions()) {
      if (accountsNeededSet.add(externalAccount)) {
        changeState = ChangeState.ACCOUNTS_NEEDED;
      } else if (!positions.containsKey(externalAccount)) {
        final var positionChangeState = createPosition(accountsNeededMap, stateAccount, externalAccount);
        if (positionChangeState == ChangeState.UNSUPPORTED) {
          return ChangeState.UNSUPPORTED;
        } else if (positionChangeState == ChangeState.ACCOUNTS_NEEDED) {
          changeState = ChangeState.ACCOUNTS_NEEDED;
        }
      }
    }

    return changeState;
  }

  @Override
  public boolean unsupported() {
    return this.accountsNeededMap == null;
  }

  @Override
  public void run() {
    final var accountsNeededMap = this.accountsNeededMap;
    final var stateAccount = stateAccount(accountsNeededMap);
    // Persist Position State locally.
    final var changeState = stateAccountChanged(accountsNeededMap, stateAccount);
    if (changeState == ChangeState.UNSUPPORTED) {
      this.accountsNeededMap = null;
      accountsNeededMap.clear();
      accountsNeededSet.clear();
      this.previousStateAccount = null;
      // TODO: Add ability to remove this from scheduling via integContext.
    } else if (changeState == ChangeState.ACCOUNTS_NEEDED) {
      integContext.queue(accountsNeededSet, this);
    } else {
      final var positions = this.positions.values();
      final var instructions = new ArrayList<Instruction>(2 + positions.size());
      instructions.add(MAIN_NET_CU_LIMIT_SIMULATION);
      final var returnAccountsSet = HashSet.<PublicKey>newHashSet(accountsNeededMap.size());
      for (final var position : positions) {
        final var priceInstruction = position.priceInstruction(
            glamAccountClient,
            integContext.solUSDOracleKey(), integContext.baseAssetUSDOracleKey(),
            accountsNeededMap, returnAccountsSet
        );
        instructions.add(priceInstruction);
      }
      instructions.add(validateAUMInstruction);

      final var tableAccounts = new LookupTableAccountMeta[0]; // TODO
      final var transaction = Transaction.createTx(glamAccountClient.feePayer(), instructions, tableAccounts);
      final var base64Encoded = transaction.base64EncodeToString();
      final var rpcCaller = context.rpcCaller();
      final var returnAccounts = List.copyOf(returnAccountsSet);
      final var simulationResult = rpcCaller.courteousGet(
          rpcClient -> rpcClient.simulateTransaction(
              Commitment.PROCESSED, base64Encoded,
              true, true,
              returnAccounts
          ),
          "rpcClient::simulatePriceGLAMVault"
      );

      if (simulationResult.error() instanceof TransactionError.InstructionError(_, final IxError ixError)) {
        if (ixError instanceof IxError.Custom(final long errorCode)) {
          try {
            final var error = GlamProtocolError.getInstance(Math.toIntExact(errorCode));
            if (error instanceof GlamProtocolError.PriceTooOld) {
              // TODO: retry in N seconds up to X times.
            } else if (error instanceof GlamProtocolError.InvalidPricingOracle) {
              // TODO: refresh oracle caches, trigger alert.
            } else {
              // TODO: trigger alert and remove vault
            }
          } catch (final RuntimeException e) {
            // TODO: trigger alert and remove vault
          }
        } else {
          // TODO: trigger alert and remove vault
        }
      } else {
        int ixIndex = 0;
        final var innerInstructionsList = simulationResult.innerInstructions();
        final var returnedAccounts = simulationResult.accounts();
        for (final var returnedAccount : returnedAccounts) {
          accountsNeededMap.put(returnedAccount.pubKey(), returnedAccount);
        }
        final var mintProgram = context.glamMintProgram();
        final var positionReports = new ArrayList<PositionReport>(positions.size());
        var eventSum = BigInteger.ZERO;
        for (final var position : positions) {
          final var priceInstruction = innerInstructionsList.get(++ixIndex);
          final var positionReport = position.positionReport(mintProgram, stateAccount.baseAssetDecimals(), accountsNeededMap, priceInstruction);
          positionReports.add(positionReport);
        }

        final var aumInstruction = innerInstructionsList.get(++ixIndex);
        for (final var innerIx : aumInstruction.instructions()) {
          if (innerIx.programId().equals(mintProgram)) {
            final var event = GlamMintEvent.readCPI(innerIx.data());
            if (event instanceof AumRecord(_, final BigInteger vaultAum)) {
              if (vaultAum.equals(eventSum)) {
                // Persist to DB
              } else {
                // TODO trigger alert and remove from pricing.
              }
            }
          }
        }
      }
    }
  }

  private DriftUserPosition driftUserPosition() {
    for (final var position : positions.values()) {
      if (position instanceof DriftUserPosition driftPosition) {
        return driftPosition;
      }
    }
    return DriftUserPosition.create(integContext.driftMarketCache(), glamAccountClient.vaultAccounts().vaultPublicKey());
  }

  private ChangeState createDriftPosition(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (User.BYTES == data.length && User.DISCRIMINATOR.equals(data, 0)) {
      final var userKey = accountInfo.pubKey();
      final var driftPosition = driftUserPosition();
      driftPosition.addUserAccount(userKey);
      positions.put(userKey, driftPosition);
      return ChangeState.ACCOUNTS_NEEDED;
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
      return ChangeState.UNSUPPORTED;
    }
  }

  private ChangeState createDriftVaultPosition(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (VaultDepositor.BYTES == data.length && VaultDepositor.DISCRIMINATOR.equals(data, 0)) {
      final var vaultDepositor = VaultDepositor.read(accountInfo.pubKey(), data);
    } else {
      final var msg = String.format("""
              {
               "event": "Unsupported Drift Vault Position",
               "account": "%s"
              }""",
          accountInfo.pubKey()
      );
      logger.log(WARNING, msg);
      // TODO: Trigger external alert and put this vault on pause.
    }
    return ChangeState.UNSUPPORTED;
  }

  private ChangeState createKaminoVaultPosition(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (VaultState.BYTES == data.length && VaultState.DISCRIMINATOR.equals(data, 0)) {
      final var vaultState = VaultState.read(accountInfo.pubKey(), data);
    } else {
      final var msg = String.format("""
              {
               "event": "Unsupported Kamino Vault Position",
               "account": "%s"
              }""",
          accountInfo.pubKey()
      );
      logger.log(WARNING, msg);
      // TODO: Trigger external alert and put this vault on pause.
    }
    return ChangeState.UNSUPPORTED;
  }

  private ChangeState createKaminoLendPosition(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (Obligation.BYTES == data.length && Obligation.DISCRIMINATOR.equals(data, 0)) {
      final var obligation = Obligation.read(accountInfo.pubKey(), data);
    } else {
      final var msg = String.format("""
              {
               "event": "Unsupported Kamino Lend Position",
               "account": "%s"
              }""",
          accountInfo.pubKey()
      );
      logger.log(WARNING, msg);
      // TODO: Trigger external alert and put this vault on pause.
    }
    return ChangeState.UNSUPPORTED;
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

  private ChangeState createKVaultPosition(final StateAccount stateAccount, final AccountInfo<byte[]> accountInfo) {
    final var tokenAccount = TokenAccount.read(accountInfo.pubKey(), accountInfo.data());
    if (tokenAccount.amount() == 0) {
      return ChangeState.NO_CHANGE;
    }
    final var kVaultCache = integContext.kaminoVaultCache();
    final var kVaultContext = kVaultCache.vaultForShareMint(tokenAccount.mint());
    if (kVaultContext == null) {
      // TODO: refresh kamino vaults
      // TODO: consider only fetching this vault with a program account filter by share mint.
    } else {
      final var vaultState = kVaultContext.vaultState();
      final var kVaultPosition = new KaminoVaultPosition(
          integContext.mintContext(vaultState.tokenMint()),
          glamAccountClient,
          tokenAccount.address(),
          kVaultCache,
          vaultState._address()
      );
      positions.put(accountInfo.pubKey(), kVaultPosition);
    }
    return ChangeState.UNSUPPORTED;
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    this.accountsNeededMap = accountMap;
    context.executeTask(this);
  }
}
