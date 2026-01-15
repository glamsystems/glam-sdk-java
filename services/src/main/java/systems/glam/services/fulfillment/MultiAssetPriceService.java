package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
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
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.Logger.Level.WARNING;
import static systems.glam.services.pricing.accounting.TxSimulationPriceGroup.MAIN_NET_CU_LIMIT_SIMULATION;

public class MultiAssetPriceService extends BaseDelegateService implements RunnableAccountConsumer, VaultPriceService {

  private static final System.Logger logger = System.getLogger(MultiAssetPriceService.class.getName());

  private final IntegrationServiceContext integContext;
  private final Set<PublicKey> accountsNeededSet;
  private final Map<PublicKey, Position> positions;
  private final Instruction validateAUMInstruction;

  private StateAccount previousStateAccount;
  private volatile Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap;
  private final AtomicReference<LookupTableAccountMeta[]> glamVaultTables;

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
    this.glamVaultTables = new AtomicReference<>(null);
  }

  enum ChangeState {
    NO_CHANGE,
    ACCOUNTS_NEEDED,
    UNSUPPORTED
  }

  private ChangeState createPosition(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap,
                                     final PublicKey externalAccount) {
    final var accountInfo = accountsNeededMap.get(externalAccount);
    if (context.isTokenAccount(accountInfo)) {
      return createKVaultPosition(accountInfo);
    } else {
      final var programOwner = accountInfo.owner();
      if (programOwner.equals(integContext.driftProgram())) {
        return createDriftPosition(accountInfo);
      } else if (programOwner.equals(integContext.driftVaultsProgram())) {
        return createDriftVaultPosition(accountInfo);
      } else if (programOwner.equals(integContext.kLendProgram())) {
        return createKaminoLendPosition(accountInfo);
      } else {
        logger.log(WARNING, "Unsupported integration program: {0}", programOwner);
        return ChangeState.UNSUPPORTED;
      }
    }
  }

  private LookupTableAccountMeta[] createTableMetaArray(final StateAccount stateAccount) {
    int numKVaults = 0;
    for (final var externalAccount : stateAccount.externalPositions()) {
      final var accountInfo = accountsNeededMap.get(externalAccount);
      if (context.isTokenAccount(accountInfo)) {
        ++numKVaults;
      }
    }

    final var driftTableKeys = integContext.driftAccounts().marketLookupTables();
    final var kaminoTableKeys = integContext.kaminoTableKeys();

    final var tableCache = integContext.integTableCache();

    final var glamTables = glamVaultTables.get();
    int i = glamTables.length;
    final LookupTableAccountMeta[] tableMetas = new LookupTableAccountMeta[driftTableKeys.size() + kaminoTableKeys.size() + numKVaults + i];
    System.arraycopy(glamTables, 0, tableMetas, 0, i);

    for (final var driftTableKey : driftTableKeys) {
      final var table = tableCache.getTable(driftTableKey);
      tableMetas[i++] = LookupTableAccountMeta.createMeta(table, Transaction.MAX_ACCOUNTS);
    }

    for (final var kaminoTableKey : kaminoTableKeys) {
      final var table = tableCache.getTable(kaminoTableKey);
      tableMetas[i++] = LookupTableAccountMeta.createMeta(table, Transaction.MAX_ACCOUNTS);
    }

    for (final var externalAccount : stateAccount.externalPositions()) {
      final var accountInfo = accountsNeededMap.get(externalAccount);
      if (context.isTokenAccount(accountInfo)) {
        final var mint = PublicKey.readPubKey(accountInfo.data(), TokenAccount.MINT_OFFSET);
        final var vaultContext = integContext.kaminoVaultCache().vaultForShareMint(mint);
        final var vaultTable = vaultContext.table();
        if (vaultTable != null) {
          tableMetas[i++] = LookupTableAccountMeta.createMeta(vaultTable, Transaction.MAX_ACCOUNTS);
        }
      }
    }

    return i < tableMetas.length
        ? Arrays.copyOfRange(tableMetas, 0, i)
        : tableMetas;
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
        final var positionChangeState = createPosition(accountsNeededMap, externalAccount);
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
  public boolean stateChange(final AccountInfo<byte[]> account) {
    // TODO: Only check asset and external account changes.
    return false;
  }

  @Override
  public void removeTable(final PublicKey tableKey) {
    SPIN_LOOP:
    for (; ; ) {
      final var witness = this.glamVaultTables.get();
      if (witness == null) {
        return;
      } else if (witness.length == 1) {
        if (!witness[0].lookupTable().address().equals(tableKey)) {
          return;
        }
        if (this.glamVaultTables.compareAndSet(witness, null)) {
          return;
        }
      } else {
        for (int i = 0; i < witness.length; i++) {
          if (witness[i].lookupTable().address().equals(tableKey)) {
            final var newArray = new LookupTableAccountMeta[witness.length - 1];
            System.arraycopy(witness, 0, newArray, 0, i);
            System.arraycopy(witness, i + 1, newArray, i, witness.length - i - 1);
            if (this.glamVaultTables.compareAndSet(witness, newArray)) {
              return;
            } else {
              continue SPIN_LOOP;
            }
          }
        }
        return;
      }
    }
  }

  @Override
  public void glamVaultTableUpdate(final AddressLookupTable addressLookupTable) {
    final var tableMeta = LookupTableAccountMeta.createMeta(addressLookupTable, Transaction.MAX_ACCOUNTS);
    final var tableKey = addressLookupTable.address();
    SPIN_LOOP:
    for (; ; ) {
      final var witness = this.glamVaultTables.get();
      if (witness == null) {
        if (this.glamVaultTables.compareAndSet(null, new LookupTableAccountMeta[]{tableMeta})) {
          return;
        }
      } else {
        final int numAccounts = addressLookupTable.numUniqueAccounts();
        for (int i = 0; i < witness.length; i++) {
          final var previous = witness[i];
          final var previousTable = previous.lookupTable();
          if (previousTable.address().equals(tableKey)) {
            if (numAccounts <= previousTable.numAccounts()) {
              return;
            }
            final var newArray = new LookupTableAccountMeta[witness.length];
            System.arraycopy(witness, 0, newArray, 0, witness.length);
            newArray[i] = tableMeta;
            if (this.glamVaultTables.compareAndSet(witness, newArray)) {
              return;
            } else {
              continue SPIN_LOOP;
            }
          }
        }
        final var newArray = new LookupTableAccountMeta[witness.length + 1];
        System.arraycopy(witness, 0, newArray, 0, witness.length);
        newArray[witness.length] = tableMeta;
        if (this.glamVaultTables.compareAndSet(witness, newArray)) {
          return;
        }
      }
    }
  }

  @Override
  public void run() {
    final var accountsNeededMap = this.accountsNeededMap;
    final var stateAccount = stateAccount(accountsNeededMap);
    // TODO: Persist Position State locally.
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

      final var tableAccounts = createTableMetaArray(stateAccount);
      final var transaction = Transaction.createTx(glamAccountClient.feePayer(), instructions, tableAccounts);
      final var base64Encoded = transaction.base64EncodeToString();
      final var returnAccounts = List.copyOf(returnAccountsSet);
      final var simulationResult = context.rpcCaller().courteousGet(
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
      // TODO: Check if cache is missing
      return ChangeState.NO_CHANGE;
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

  private ChangeState createKVaultPosition(final AccountInfo<byte[]> accountInfo) {
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
  }
}
