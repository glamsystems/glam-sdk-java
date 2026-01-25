package systems.glam.services.pricing;

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
import systems.glam.sdk.Protocol;
import systems.glam.sdk.idl.programs.glam.mint.gen.events.AumRecord;
import systems.glam.sdk.idl.programs.glam.mint.gen.events.GlamMintEvent;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.BaseDelegateService;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.integrations.drift.DriftUsersPosition;
import systems.glam.services.integrations.kamino.KaminoVaultPosition;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.pricing.accounting.VaultTokensPosition;
import systems.glam.services.rpc.AccountConsumer;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.Logger.Level.WARNING;
import static systems.glam.services.pricing.accounting.TxSimulationPriceGroup.MAIN_NET_CU_LIMIT_SIMULATION;

public class MultiAssetPriceService extends BaseDelegateService
    implements AccountConsumer, VaultPriceService, Runnable {

  private static final System.Logger logger = System.getLogger(MultiAssetPriceService.class.getName());

  private final IntegrationServiceContext serviceContext;
  private final PublicKey mintPDA;
  private final int baseAssetDecimals;
  private final Set<PublicKey> accountsNeededSet;
  private final Map<PublicKey, Position> positions;
  private final VaultTokensPosition vaultTokensPosition;
  private final Map<Protocol, Position> protocolPositions;
  private final Instruction validateAUMInstruction;

  private final AtomicReference<MinGlamStateAccount> stateAccount;
  private final AtomicReference<AumTransaction> aumTransaction;
  private volatile Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap;
  private final AtomicReference<LookupTableAccountMeta[]> glamVaultTables;

  MultiAssetPriceService(final IntegrationServiceContext serviceContext,
                         final PublicKey mintPDA,
                         final PublicKey baseAssetMint,
                         final int baseAssetDecimals,
                         final GlamAccountClient glamAccountClient,
                         final MinGlamStateAccount stateAccount,
                         final Set<PublicKey> accountsNeededSet) {
    super(glamAccountClient);
    this.serviceContext = serviceContext;
    this.mintPDA = mintPDA;
    this.baseAssetDecimals = baseAssetDecimals;
    this.accountsNeededSet = accountsNeededSet;
    this.positions = HashMap.newHashMap(stateAccount.numAccounts());
    this.vaultTokensPosition = new VaultTokensPosition(baseAssetMint, stateAccount.assets().length);
    this.protocolPositions = new EnumMap<>(Protocol.class);
    this.stateAccount = new AtomicReference<>(stateAccount);
    this.aumTransaction = new AtomicReference<>(null);
    this.validateAUMInstruction = glamAccountClient.validateAum(true);
    this.glamVaultTables = new AtomicReference<>(null);
  }

  enum StateChange {
    NO_CHANGE,
    ACCOUNTS_NEEDED,
    UNSUPPORTED
  }

  private boolean isKVaultTokenAccount(final AccountInfo<byte[]> accountInfo) {
    if (serviceContext.isTokenAccount(accountInfo)) {
      return true; // TODO: Add more validation.
    } else {
      return false;
    }
  }

  private StateChange createPosition(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap,
                                     final PublicKey externalAccount) {
    final var accountInfo = accountsNeededMap.get(externalAccount);
    if (isKVaultTokenAccount(accountInfo)) {
      return createKVaultPosition(accountInfo);
    } else {
      final var programOwner = accountInfo.owner();
      if (programOwner.equals(serviceContext.driftProgram())) {
        return createDriftPosition(accountInfo);
      } else if (programOwner.equals(serviceContext.driftVaultsProgram())) {
        return createDriftVaultPosition(accountInfo);
      } else if (programOwner.equals(serviceContext.kLendProgram())) {
        return createKaminoLendPosition(accountInfo);
      } else {
        logger.log(WARNING, "Unsupported integration program: {0}", programOwner);
        return StateChange.UNSUPPORTED;
      }
    }
  }

  private LookupTableAccountMeta[] createTableMetaArray(final MinGlamStateAccount stateAccount,
                                                        final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    int numKVaults = 0;
    for (final var externalAccount : stateAccount.externalPositions()) {
      final var accountInfo = accountsNeededMap.get(externalAccount);
      if (isKVaultTokenAccount(accountInfo)) {
        ++numKVaults;
      }
    }

    final var driftTableKeys = serviceContext.driftAccounts().marketLookupTables();
    final var kaminoTableKeys = serviceContext.kaminoTableKeys();

    final var tableCache = serviceContext.integTableCache();

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
      if (isKVaultTokenAccount(accountInfo)) {
        final var mint = PublicKey.readPubKey(accountInfo.data(), TokenAccount.MINT_OFFSET);
        final var vaultContext = serviceContext.kaminoVaultCache().vaultForShareMint(mint);
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

  private void putTokenPosition(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap,
                                final PublicKey assetMint) {
    var mintContext = serviceContext.mintContext(assetMint);
    if (mintContext == null) {
      final var mintAccountInfo = accountsNeededMap.get(assetMint);
      if (mintAccountInfo == null) {
        accountsNeededSet.add(assetMint);
        return;
      }
      mintContext = serviceContext.setMintContext(mintAccountInfo);
    }
    final var vaultATA = glamAccountClient.findATA(mintContext.tokenProgram(), mintContext.mint()).publicKey();
    vaultTokensPosition.addVaultATA(assetMint, vaultATA);
    accountsNeededSet.add(vaultATA);
    accountsNeededSet.remove(assetMint);
  }

  private StateChange stateAccountChanged(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap,
                                          final MinGlamStateAccount stateAccount) {
    final var assets = stateAccount.assets();
    final var iterator = vaultTokensPosition.vaultATAMap().entrySet().iterator();
    while (iterator.hasNext()) {
      final var entry = iterator.next();
      final var mint = entry.getKey();
      if (Arrays.binarySearch(assets, mint) < 0) {
        accountsNeededSet.remove(entry.getValue().publicKey());
        iterator.remove();
      }
    }

    final var externalPositions = stateAccount.externalPositions();
    for (final var positionKey : positions.keySet()) {
      if (Arrays.binarySearch(externalPositions, positionKey) < 0) {
        accountsNeededSet.remove(positionKey);
        // TODO: Remove upstream accounts.
        final var position = positions.remove(positionKey);
        position.removeAccount(positionKey);
      }
    }

    var changeState = StateChange.NO_CHANGE;
    for (final var assetMint : assets) {
      if (!vaultTokensPosition.hasContext(assetMint)) {
        changeState = StateChange.ACCOUNTS_NEEDED;
        putTokenPosition(accountsNeededMap, assetMint);
      }
    }

    for (final var externalAccount : stateAccount.externalPositions()) {
      if (accountsNeededSet.add(externalAccount)) {
        changeState = StateChange.ACCOUNTS_NEEDED;
      } else if (!positions.containsKey(externalAccount)) {
        final var positionChangeState = createPosition(accountsNeededMap, externalAccount);
        if (positionChangeState == StateChange.UNSUPPORTED) {
          return StateChange.UNSUPPORTED;
        } else if (positionChangeState == StateChange.ACCOUNTS_NEEDED) {
          changeState = StateChange.ACCOUNTS_NEEDED;
        }
      }
    }

    // TODO: Check for oracle configuration changes.

    return changeState;
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
            if (i == 0) {
              System.arraycopy(witness, 1, newArray, 0, witness.length - 1);
            } else {
              System.arraycopy(witness, 0, newArray, 0, i);
              if (i < newArray.length) {
                System.arraycopy(witness, i + 1, newArray, i, newArray.length - i);
              }
            }
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
    try {
      final var accountsNeededMap = this.accountsNeededMap;
      if (accountsNeededMap == null) {
        return;
      }
      final var stateAccount = this.stateAccount.get();
      if (stateAccount == null) {
        return;
      }
      final var changeState = stateAccountChanged(accountsNeededMap, stateAccount);
      if (changeState == StateChange.UNSUPPORTED) {
        this.stateAccount.set(null);
        this.accountsNeededMap = null;
        this.accountsNeededSet.clear();
        accountsNeededMap.clear();
        protocolPositions.clear();
        vaultTokensPosition.vaultATAMap().clear();
        glamVaultTables.set(null);
      } else {
        if (changeState == StateChange.ACCOUNTS_NEEDED) {
          serviceContext.queue(accountsNeededSet, this);
        } else {
          prepareAUMTransaction(stateAccount, accountsNeededMap);
        }
        final var stateAccountPath = serviceContext.resolveGlamStateFilePath(glamAccountClient.vaultAccounts().glamStateKey());
        final byte[] stateAccountData = stateAccount.serialize(vaultTokensPosition.baseAssetMint(), baseAssetDecimals);
        try {
          Files.write(
              stateAccountPath,
              stateAccountData,
              StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
          );
        } catch (final IOException e) {
          logger.log(WARNING, "Failed to write state account to file", e);
        }
      }
    } catch (final RuntimeException ex) {
      logger.log(WARNING, "Error processing state account update", ex);
    }
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    this.accountsNeededMap = accountMap;
    stateChange(accountMap.get(stateKey()));
  }

  @Override
  public void init() {
    serviceContext.executeTask(this);
  }

  @Override
  public boolean stateChange(final AccountInfo<byte[]> account) {
    var witness = this.stateAccount.get();
    if (witness == null) {
      return false;
    }
    final long slot = account.context().slot();
    if (Long.compareUnsigned(slot, witness.slot()) <= 0) {
      return true;
    }

    for (final var stateAccount = witness.createIfChanged(slot, account.data()); ; ) {
      if (stateAccount == null) {
        return true;
      }
      if (this.stateAccount.compareAndSet(witness, stateAccount)) {
        serviceContext.executeTask(this);
      } else {
        witness = this.stateAccount.get();
        if (Long.compareUnsigned(slot, witness.slot()) <= 0) {
          return true;
        }
      }
    }
  }

  record AumTransaction(MinGlamStateAccount minGlamStateAccount,
                        Transaction transaction,
                        String base64Encoded,
                        List<PublicKey> returnAccounts) {

    long slot() {
      return minGlamStateAccount.slot();
    }
  }

  private void prepareAUMTransaction(final MinGlamStateAccount stateAccount,
                                     final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    final var positions = this.positions.values();
    final var instructions = new ArrayList<Instruction>(2 + positions.size());
    instructions.add(MAIN_NET_CU_LIMIT_SIMULATION);
    final var returnAccountsSet = HashSet.<PublicKey>newHashSet(accountsNeededMap.size());

    // TODO: Check if State overrides default oracle.
    final var baseAssetMeta = serviceContext.globalConfigAssetMeta(vaultTokensPosition.baseAssetMint());
    if (baseAssetMeta == null) {
      return;
    }
    for (final var position : positions) {
      final var priceInstruction = position.priceInstruction(
          serviceContext,
          glamAccountClient,
          baseAssetMeta.oracle(),
          stateAccount,
          accountsNeededMap,
          returnAccountsSet
      );
      instructions.add(priceInstruction);
    }
    instructions.add(validateAUMInstruction);

    final var tableAccounts = createTableMetaArray(stateAccount, accountsNeededMap);
    final var transaction = Transaction.createTx(glamAccountClient.feePayer(), instructions, tableAccounts);
    final var base64Encoded = transaction.base64EncodeToString();
    final var returnAccounts = List.copyOf(returnAccountsSet);
    final long slot = stateAccount.slot();
    final var aumTransaction = new AumTransaction(stateAccount, transaction, base64Encoded, returnAccounts);

    for (var witness = this.aumTransaction.get(); ; ) {
      if (Long.compareUnsigned(slot, witness.slot()) <= 0) {
        return;
      } else if (this.aumTransaction.compareAndSet(witness, aumTransaction)) {
        return;
      } else {
        witness = this.aumTransaction.get();
      }
    }
  }

  public void simulateAumTransaction() {
    final var aumTransaction = this.aumTransaction.get();
    final var base64Encoded = aumTransaction.base64Encoded();
    final var returnAccounts = aumTransaction.returnAccounts();
    final var simulationResult = serviceContext.rpcCaller().courteousGet(
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
      final var accountsNeededMap = HashMap.<PublicKey, AccountInfo<byte[]>>newHashMap(returnedAccounts.size());
      for (final var returnedAccount : returnedAccounts) {
        accountsNeededMap.put(returnedAccount.pubKey(), returnedAccount);
      }
      final var mintProgram = glamAccountClient.glamAccounts().mintProgram();
      final var positionReports = new ArrayList<PositionReport>(positions.size());
      var eventSum = BigInteger.ZERO;
      for (final var position : positions.values()) {
        final var priceInstruction = innerInstructionsList.get(++ixIndex);
        final var positionReport = position.positionReport(mintProgram, baseAssetDecimals, accountsNeededMap, priceInstruction);
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

  private DriftUsersPosition driftUserPosition() {
    for (final var position : positions.values()) {
      if (position instanceof DriftUsersPosition driftPosition) {
        return driftPosition;
      }
    }
    return DriftUsersPosition.create(serviceContext.driftMarketCache(), glamAccountClient.vaultAccounts().vaultPublicKey());
  }

  private StateChange createDriftPosition(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (User.BYTES == data.length && User.DISCRIMINATOR.equals(data, 0)) {
      final var userKey = accountInfo.pubKey();
      final var driftPosition = driftUserPosition();
      driftPosition.addAccount(userKey);
      positions.put(userKey, driftPosition);
      // TODO: Check if cache is missing
      return StateChange.NO_CHANGE;
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
      return StateChange.UNSUPPORTED;
    }
  }

  private StateChange createDriftVaultPosition(final AccountInfo<byte[]> accountInfo) {
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
    return StateChange.UNSUPPORTED;
  }

  private StateChange createKaminoVaultPosition(final AccountInfo<byte[]> accountInfo) {
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
    return StateChange.UNSUPPORTED;
  }

  private StateChange createKaminoLendPosition(final AccountInfo<byte[]> accountInfo) {
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
    return StateChange.UNSUPPORTED;
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

  private StateChange createKVaultPosition(final AccountInfo<byte[]> accountInfo) {
    final var tokenAccount = TokenAccount.read(accountInfo.pubKey(), accountInfo.data());
    if (tokenAccount.amount() == 0) {
      return StateChange.NO_CHANGE;
    }
    final var kVaultCache = serviceContext.kaminoVaultCache();
    final var kVaultContext = kVaultCache.vaultForShareMint(tokenAccount.mint());
    if (kVaultContext == null) {
      // TODO: refresh kamino vaults
      // TODO: consider only fetching this vault with a program account filter by share mint.
    } else {
      final var vaultState = kVaultContext.vaultState();
      final var kVaultPosition = new KaminoVaultPosition(
          tokenAccount.address(),
          kVaultCache,
          vaultState._address()
      );
      positions.put(accountInfo.pubKey(), kVaultPosition);
    }
    return StateChange.UNSUPPORTED;
  }
}
