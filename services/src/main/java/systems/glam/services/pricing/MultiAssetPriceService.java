package systems.glam.services.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.accounts.meta.LookupTableAccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.idl.clients.drift.vaults.gen.types.VaultDepositor;
import software.sava.idl.clients.kamino.lend.gen.types.Obligation;
import software.sava.idl.clients.spl.token.gen.types.Mint;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.mint.gen.events.AumRecord;
import systems.glam.sdk.idl.programs.glam.mint.gen.events.GlamMintEvent;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;
import systems.glam.services.BaseDelegateService;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.integrations.drift.DriftUsersPosition;
import systems.glam.services.integrations.kamino.KaminoLendingPositions;
import systems.glam.services.pricing.accounting.*;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.state.MinGlamStateAccount;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;
import static systems.glam.services.pricing.accounting.TxSimulationPriceGroup.MAIN_NET_CU_LIMIT_SIMULATION;

public class MultiAssetPriceService extends BaseDelegateService
    implements AccountConsumer, VaultPriceService, Runnable {

  private static final System.Logger logger = System.getLogger(MultiAssetPriceService.class.getName());
  private static final Logger log = LoggerFactory.getLogger(MultiAssetPriceService.class);

  private final IntegrationServiceContext serviceContext;
  private final PublicKey mintPDA;
  private final Set<PublicKey> accountsNeededSet;
  private final Map<PublicKey, Position> positions;
  private final VaultTokensPosition vaultTokensPosition;
  private final Instruction validateAUMInstruction;

  private final AtomicReference<MinGlamStateAccount> stateAccount;
  private final AtomicReference<AumTransaction> aumTransaction;
  private final AtomicReference<LookupTableAccountMeta[]> glamVaultTables;
  private volatile Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap; // Reference to a shared batch of accounts.

  MultiAssetPriceService(final IntegrationServiceContext serviceContext,
                         final PublicKey mintPDA,
                         final GlamAccountClient glamAccountClient,
                         final MinGlamStateAccount stateAccount,
                         final Set<PublicKey> accountsNeededSet) {
    super(glamAccountClient);
    this.serviceContext = serviceContext;
    this.mintPDA = mintPDA;
    this.accountsNeededSet = accountsNeededSet;
    this.positions = HashMap.newHashMap(stateAccount.numAccounts());
    this.vaultTokensPosition = new VaultTokensPosition(stateAccount.assets().length);
    this.stateAccount = new AtomicReference<>(stateAccount);
    this.aumTransaction = new AtomicReference<>(null);
    this.validateAUMInstruction = glamAccountClient.validateAum(true).extraAccount(AccountMeta.createRead(mintPDA));
    this.glamVaultTables = new AtomicReference<>(null);
    this.accountsNeededMap = Map.of();
  }

  enum StateChange {

    UNSUPPORTED,
    ACCOUNTS_NEEDED,
    NEW_POSITION,
    NO_CHANGE;

    StateChange escalate(final StateChange stateChange) {
      return ordinal() <= stateChange.ordinal() ? this : stateChange;
    }
  }

  private boolean isKVaultTokenAccount(final PublicKey account) {
    final var position = this.positions.get(account);
    if (position instanceof KaminoLendingPositions kaminoPositions) {
      return kaminoPositions.isVaultTokenAccount(account);
    } else {
      return false;
    }
  }

  private StateChange createPosition(final AccountInfo<byte[]> accountInfo) {
    if (serviceContext.isTokenAccount(accountInfo)) {
      final var mint = PublicKey.readPubKey(accountInfo.data(), 0);
      final var kVaultCache = serviceContext.kaminoCache();
      final var kVaultContext = kVaultCache.vaultForShareMint(mint);
      if (kVaultContext != null) {
        final var kaminoPositions = kaminoPositions();
        final var tokenAccountKey = accountInfo.pubKey();
        kaminoPositions.addVaultTokenAccount(tokenAccountKey, kVaultContext);
        this.positions.put(tokenAccountKey, kaminoPositions);
        this.accountsNeededSet.remove(tokenAccountKey);
        final var vaultTable = kVaultContext.vaultLookupTable();
        if (vaultTable != null) {
          final var tableCache = serviceContext.integTableCache();
          final var lookupTable = tableCache.table(vaultTable);
          if (lookupTable == null) {
            this.accountsNeededSet.add(vaultTable);
            return StateChange.ACCOUNTS_NEEDED;
          }
        }
        return StateChange.NEW_POSITION;
      } else {
        logger.log(WARNING, "Unsupported Kamino Vault Mint? {0}", mint);
        return StateChange.UNSUPPORTED;
      }
    } else {
      final var programOwner = accountInfo.owner();
      if (programOwner.equals(serviceContext.driftProgram())) {
        return createDriftPosition(accountInfo);
      } else if (programOwner.equals(serviceContext.kLendProgram())) {
        return createKaminoLendPosition(accountInfo);
      } else if (programOwner.equals(serviceContext.driftVaultsProgram())) {
        return createDriftVaultPosition(accountInfo);
      } else {
        logger.log(WARNING, "Unsupported integration program: {0}", accountInfo.owner());
        return StateChange.UNSUPPORTED;
      }
    }
  }

  /**
   * Registers the Mint to be fetched if it does not exist in the MintCache.
   * Otherwise, derives the Vaults token account and ensures the mint is no longer fetched.
   */
  private void putTokenPosition(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap,
                                final PublicKey assetMint) {
    var mintContext = this.serviceContext.mintContext(assetMint);
    if (mintContext == null) {
      final var mintAccountInfo = accountsNeededMap.get(assetMint);
      if (mintAccountInfo == null) {
        this.accountsNeededSet.add(assetMint);
        return;
      }
      mintContext = this.serviceContext.setMintContext(mintAccountInfo);
    }
    final var vaultATA = this.glamAccountClient.findATA(mintContext.tokenProgram(), mintContext.mint()).publicKey();
    this.vaultTokensPosition.addVaultATA(assetMint, vaultATA);
    this.accountsNeededSet.add(vaultATA);
    this.accountsNeededSet.remove(assetMint);
  }

  private StateChange stateAccountChanged(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap,
                                          final MinGlamStateAccount stateAccount) {
    // Add New Assets
    var stateChange = StateChange.NO_CHANGE;
    for (final var assetMint : stateAccount.assets()) {
      if (!this.vaultTokensPosition.hasContext(assetMint)) {
        var assetMeta = serviceContext.globalConfigAssetMeta(assetMint);
        if (assetMeta == null) {
          assetMeta = serviceContext.watchForMint(assetMint, this.stateAccountKey());
          if (assetMeta == null) {
            return StateChange.UNSUPPORTED;
          }
        }
        putTokenPosition(accountsNeededMap, assetMint);
        stateChange = StateChange.ACCOUNTS_NEEDED; // Either a new Mint or ATA needs to be fetched.
      }
    }

    // Remove Old Assets
    this.vaultTokensPosition.removeOldAccounts(stateAccount, this.accountsNeededSet);

    // Remove Old Positions
    final var externalPositions = stateAccount.externalPositions();
    for (final var positionKey : this.positions.keySet()) {
      if (Arrays.binarySearch(externalPositions, positionKey) < 0) {
        this.accountsNeededSet.remove(positionKey);
        // TODO: Remove upstream accounts.
        final var position = this.positions.remove(positionKey);
        position.removeAccount(positionKey);
      }
    }

    // Add New Positions
    for (final var externalAccount : stateAccount.externalPositions()) {
      if (!this.positions.containsKey(externalAccount)) {
        if (this.accountsNeededSet.add(externalAccount)) {
          stateChange = StateChange.ACCOUNTS_NEEDED;
        } else {
          final var positionChangeState = createPosition(accountsNeededMap.get(externalAccount));
          stateChange = stateChange.escalate(positionChangeState);
          if (stateChange == StateChange.UNSUPPORTED) {
            return StateChange.UNSUPPORTED;
          }
        }
      }
    }

    return stateChange;
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    final var accountInfo = accountMap.get(stateKey());
    if (accountInfo == null) {
      this.stateAccount.set(null);
    } else {
      this.accountsNeededMap = accountMap;
      stateChange(accountInfo, true);
    }
  }

  @Override
  public boolean stateChange(final AccountInfo<byte[]> account) {
    if (account == null) {
      clearState();
      deleteStateAccount();
      return false;
    } else {
      return stateChange(account, false);
    }
  }

  private boolean stateChange(final AccountInfo<byte[]> account, final boolean forceExecute) {
    var witness = this.stateAccount.get();
    if (witness == null) {
      return false;
    }
    final long slot = account.context().slot();
    if (Long.compareUnsigned(slot, witness.slot()) <= 0) {
      return true; // Ignore stale update.
    }

    for (final var stateAccount = witness.createIfChanged(slot, account.pubKey(), account.data()); ; ) {
      if (stateAccount == null) {
        if (forceExecute) {
          serviceContext.executeTask(this);
        }
        return true; // No Change.
      }
      if (this.stateAccount.compareAndSet(witness, stateAccount)) {
        serviceContext.executeTask(this);
        return true;
      } else { // Spin Loop
        witness = this.stateAccount.get();
        if (Long.compareUnsigned(slot, witness.slot()) <= 0) {
          return true; // Ignore stale update.
        }
      }
    }
  }

  @Override
  public void init() {
    serviceContext.executeTask(this);
  }

  private void clearState() {
    this.stateAccount.set(null);
    this.accountsNeededMap = null;
    this.accountsNeededSet.clear();
    this.vaultTokensPosition.clear();
    this.glamVaultTables.set(null);
    this.aumTransaction.set(null);
  }

  @Override
  public void run() {
    try {
      final var stateAccount = this.stateAccount.get();
      if (stateAccount == null) {
        return;
      }
      final var accountsNeededMap = this.accountsNeededMap;
      if (accountsNeededMap == null) {
        return;
      }
      final var stateChange = stateAccountChanged(accountsNeededMap, stateAccount);
      switch (stateChange) {
        case UNSUPPORTED -> clearState();
        case ACCOUNTS_NEEDED -> {
          this.aumTransaction.set(null);
          serviceContext.queue(accountsNeededSet, this);
          persist(stateAccount);
        }
        case NEW_POSITION -> prepareAUMTransaction(stateAccount, accountsNeededMap);
        case NO_CHANGE -> {
          if (this.aumTransaction.get() == null) { // Happens the first time an ATA is retrieved.
            prepareAUMTransaction(stateAccount, accountsNeededMap);
          }
        }
      }
    } catch (final RuntimeException ex) {
      logger.log(WARNING, "Error processing state account update", ex);
    }
  }

  private Path stateFilePath() {
    return serviceContext.resolveGlamStateFilePath(stateAccountKey());
  }

  private void deleteStateAccount() {
    try {
      Files.deleteIfExists(stateFilePath());
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to delete state file", e);
    }
  }

  private void persist(final MinGlamStateAccount stateAccount) {
    final var stateAccountPath = stateFilePath();
    final byte[] stateAccountData = stateAccount.serialize();
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

  record AumTransaction(MinGlamStateAccount stateAccount,
                        List<Position> positions,
                        Transaction transaction,
                        String base64Encoded,
                        List<PublicKey> returnAccounts) {

  }

  private static final Instruction MAIN_NET_CU_LIMIT_WITH_CLOCK = MAIN_NET_CU_LIMIT_SIMULATION.extraAccount(SolanaAccounts.MAIN_NET.readClockSysVar());

  private void prepareAUMTransaction(final MinGlamStateAccount stateAccount,
                                     final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    final var positions = List.copyOf(this.positions.values());
    final var instructions = new ArrayList<Instruction>(2 + (positions.size() << 2));
    instructions.add(MAIN_NET_CU_LIMIT_SIMULATION);

    final var returnAccountsSet = HashSet.<PublicKey>newHashSet(Transaction.MAX_ACCOUNTS);

    // TODO: Check if State overrides default oracle.
    final var solAssetMeta = serviceContext.solAssetMeta();
    if (solAssetMeta == null) {
      throw new IllegalStateException("Missing SOL AssetMeta");
    }
    final var solOracle = solAssetMeta.oracle();
    final var baseAssetMeta = serviceContext.globalConfigAssetMeta(stateAccount.baseAssetMint());
    if (baseAssetMeta == null) {
      throw new IllegalStateException(String.format("Missing base AssetMeta for %s", stateAccount.baseAssetMint()));
    }
    final var baseAssetOracle = baseAssetMeta.oracle();
    if (!this.vaultTokensPosition.priceInstruction(
        serviceContext,
        glamAccountClient,
        solOracle,
        baseAssetOracle,
        stateAccount,
        accountsNeededMap,
        instructions,
        returnAccountsSet
    )) {
      return;
    }
    // TODO: If Vault only contains base asset skip tx simulation.
    for (final var position : positions) {
      if (!position.priceInstruction(
          serviceContext,
          glamAccountClient,
          solOracle,
          baseAssetOracle,
          stateAccount,
          accountsNeededMap,
          instructions,
          returnAccountsSet
      )) {
        return;
      }
    }
    instructions.add(validateAUMInstruction);
    returnAccountsSet.add(mintPDA);

    final var accounts = HashSet.newHashSet(Transaction.MAX_ACCOUNTS);
    for (final var ix : instructions) {
      accounts.add(ix.programId());
      accounts.addAll(ix.accounts());
    }
    final int numAccounts = accounts.size();
    final var clockSysVar = SolanaAccounts.MAIN_NET.clockSysVar();
    if (numAccounts < Transaction.MAX_ACCOUNTS) {
      if (!accounts.contains(clockSysVar)) {
        instructions.set(0, MAIN_NET_CU_LIMIT_WITH_CLOCK);
      }
      returnAccountsSet.add(clockSysVar);
    } else if (numAccounts > Transaction.MAX_ACCOUNTS) {
      logger.log(WARNING, "Vault state {0} needs {1} accounts for pricing.", stateKey(), numAccounts);
      clearState();
      return;
    }

    final var tableAccounts = createTableMetaArray(stateAccount, accountsNeededMap);
    final var transaction = Transaction.createTx(glamAccountClient.feePayer(), instructions, tableAccounts);
    final var base64Encoded = transaction.base64EncodeToString();
    final var returnAccounts = List.copyOf(returnAccountsSet);
    final var aumTransaction = new AumTransaction(stateAccount, positions, transaction, base64Encoded, returnAccounts);
    this.aumTransaction.set(aumTransaction);
  }

  @Override
  public long usdValue() {
    return -1;
  }

  @Override
  public CompletableFuture<PositionReport> priceVault() {
    final var aumTransaction = this.aumTransaction.get();
    if (aumTransaction == null) {
      return null;
    }
    final var base64Encoded = aumTransaction.base64Encoded();
    final var returnAccounts = aumTransaction.returnAccounts();
    final var simulationResultFuture = serviceContext.rpcCaller().courteousCall(
        rpcClient -> rpcClient.simulateTransaction(
            Commitment.CONFIRMED, base64Encoded,
            true, true,
            returnAccounts
        ),
        "rpcClient::simulatePriceGLAMVault"
    );
    return simulationResultFuture.thenApplyAsync(simulationResult -> {
      try {
        if (simulationResult.error() instanceof TransactionError.InstructionError(_, final IxError ixError)) {
          if (ixError instanceof IxError.Custom(final long errorCode)) {
            try {
              final var error = GlamProtocolError.getInstance(Math.toIntExact(errorCode));
              if (error instanceof GlamProtocolError.PriceTooOld) { // 51102
                // TODO: retry in N seconds up to X times.
              } else if (error instanceof GlamProtocolError.PriceDivergenceTooLarge) {

              } else if (error instanceof GlamProtocolError.InvalidPricingOracle) {
                // TODO: refresh oracle caches, trigger alert.
              } else {
                // 51108 GlamProtocolError.TypeCastingError
                // TODO: trigger alert and remove vault
                logger.log(WARNING, "Error processing price vault simulation: {0}", error);
              }
            } catch (final RuntimeException e) {
              // TODO: trigger alert and remove vault
              logger.log(WARNING, "Error processing price vault simulation: {0}", ixError);
            }
          } else {
            // TODO: trigger alert and remove vault
          }
        } else {
          final var returnedAccounts = simulationResult.accounts();
          final var returnedAccountsMap = HashMap.<PublicKey, AccountInfo<byte[]>>newHashMap(returnedAccounts.size());
          for (final var returnedAccount : returnedAccounts) {
            returnedAccountsMap.put(returnedAccount.pubKey(), returnedAccount);
          }

          final long slot = simulationResult.context().slot();
          final var clockSysVarAccount = returnedAccountsMap.get(SolanaAccounts.MAIN_NET.clockSysVar());
          final Instant timestamp;
          if (clockSysVarAccount == null) {
            timestamp = Instant.now();
          } else {
            final long epochSeconds = ByteUtil.getInt64LE(clockSysVarAccount.data(), 32);
            timestamp = Instant.ofEpochSecond(epochSeconds);
          }

          final var instructions = aumTransaction.transaction().instructions();
          final var innerInstructionsArray = new InnerInstructions[instructions.size()];
          for (final var innerInstructions : simulationResult.innerInstructions()) {
            innerInstructionsArray[innerInstructions.index()] = innerInstructions;
          }
          final var mintProgram = glamAccountClient.glamAccounts().mintProgram();
          final var positionReports = new ArrayList<AggregatePositionReport>(1 + positions.size());
          final var stateAccount = aumTransaction.stateAccount();
          final var assetPrices = HashMap.<PublicKey, BigDecimal>newHashMap(stateAccount.numAssets() + 1);

          int ixIndex = vaultTokensPosition.positionReport(
              serviceContext,
              mintProgram,
              stateAccount,
              returnedAccountsMap,
              1,
              instructions,
              innerInstructionsArray,
              assetPrices,
              positionReports
          );
          for (final var position : aumTransaction.positions()) {
            ixIndex = position.positionReport(
                serviceContext,
                mintProgram,
                stateAccount,
                returnedAccountsMap,
                ixIndex,
                instructions,
                innerInstructionsArray,
                assetPrices,
                positionReports
            );
          }

          final var mintAccount = returnedAccountsMap.get(mintPDA);
          final long supply = ByteUtil.getInt64LE(mintAccount.data(), Mint.SUPPLY_OFFSET);

          final var baseAssetAUM = innerInstructionsArray[ixIndex].instructions().stream().<BigInteger>mapMulti((innerIx, downstream) -> {
            if (innerIx.programId().equals(mintProgram)) {
              final var event = GlamMintEvent.readCPI(innerIx.data());
              if (event instanceof AumRecord(_, final BigInteger baseAssetAmount)) {
                downstream.accept(baseAssetAmount);
              }
            }
          }).findFirst().orElseThrow();
          final var baseAssetMintContext = serviceContext.mintContext(stateAccount.baseAssetMint());
          final var baseAssetPrice = assetPrices.get(baseAssetMintContext.mint());
          if (baseAssetPrice == null) {
            logger.log(WARNING, "No price found for base asset mint: {0}", baseAssetMintContext.mint());
            return null;
          }
          final var decimalBaseAssetAUM = baseAssetMintContext.toDecimal(baseAssetAUM);
          final var decimalBaseAssetUSD = decimalBaseAssetAUM.multiply(baseAssetPrice).setScale(2, RoundingMode.HALF_EVEN);
          final var stateKey = stateKey();
          logger.log(INFO, String.format("""
                      {
                       "state": "%s",
                       "vault": "%s",
                       "slot": %d,
                       "timestamp": "%s",
                       "baseAsset": "%s",
                       "supply": %s,
                       "baseAUM": "%s",
                       "usdAUM": "%s",
                       "sum": "%s"
                      }""",
                  stateKey,
                  glamAccountClient.vaultAccounts().vaultPublicKey(),
                  slot, timestamp,
                  baseAssetMintContext.mint(),
                  Long.toUnsignedString(supply),
                  decimalBaseAssetAUM.toPlainString(),
                  decimalBaseAssetUSD.toPlainString(),
                  positionReports
              )
          );

          final var aumRecord = new VaultAumRecord(
              stateKey, timestamp, slot, supply, baseAssetAUM, decimalBaseAssetUSD
          );
          serviceContext.persistAumRecord(aumRecord);
        }
      } catch (final RuntimeException e) {
        logger.log(WARNING, "Error processing price vault simulation", e);
      } catch (final InterruptedException e) {
        // exit
      }
      return null;
    });
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
      return StateChange.NEW_POSITION;
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

  private KaminoLendingPositions kaminoPositions() {
    for (final var position : positions.values()) {
      if (position instanceof KaminoLendingPositions kLendPositions) {
        return kLendPositions;
      }
    }
    return new KaminoLendingPositions(serviceContext.kaminoAccounts());
  }

  private StateChange createKaminoLendPosition(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (Obligation.BYTES == data.length && Obligation.DISCRIMINATOR.equals(data, 0)) {
      final var kLendPosition = kaminoPositions();
      kLendPosition.addObligation(accountInfo.pubKey());
      positions.put(accountInfo.pubKey(), kLendPosition);
      return StateChange.NEW_POSITION;
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

  private LookupTableAccountMeta[] createTableMetaArray(final MinGlamStateAccount stateAccount,
                                                        final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    int numKVaults = 0;
    for (final var externalAccount : stateAccount.externalPositions()) {
      if (isKVaultTokenAccount(externalAccount)) {
        ++numKVaults;
      }
    }

    final var driftTableKeys = serviceContext.driftAccounts().marketLookupTables();

    final var glamTables = glamVaultTables.get();
    final LookupTableAccountMeta[] tableMetas;
    int i;
    if (glamTables == null) {
      i = 0;
      tableMetas = new LookupTableAccountMeta[driftTableKeys.size() + 1 + numKVaults];
    } else {
      i = glamTables.length;
      tableMetas = new LookupTableAccountMeta[i + driftTableKeys.size() + 1 + numKVaults];
      System.arraycopy(glamTables, 0, tableMetas, 0, i);
    }

    final var tableCache = serviceContext.integTableCache();
    // Always include drift and kamino main tables as they are typically useful regardless.
    // Tables will be scored on Transaction creation for optimal usage.
    for (final var driftTableKey : driftTableKeys) {
      final var table = tableCache.table(driftTableKey);
      tableMetas[i++] = LookupTableAccountMeta.createMeta(table, Transaction.MAX_ACCOUNTS);
    }
    final var table = tableCache.table(serviceContext.kaminoAccounts().mainMarketLUT());
    tableMetas[i++] = LookupTableAccountMeta.createMeta(table, Transaction.MAX_ACCOUNTS);

    for (final var externalAccount : stateAccount.externalPositions()) {
      final var position = this.positions.get(externalAccount);
      if (position instanceof KaminoLendingPositions kaminoPositions) {
        final var mint = kaminoPositions.sharesMint(externalAccount);
        if (mint == null) {
          continue;
        }
        final var vaultContext = serviceContext.kaminoCache().vaultForShareMint(mint);
        final var vaultTableKey = vaultContext.vaultLookupTable();
        if (vaultTableKey != null) {
          var vaultTable = tableCache.table(vaultTableKey);
          if (vaultTable == null) {
            final var tableAccountInfo = accountsNeededMap.get(vaultTableKey);
            if (tableAccountInfo == null) {
              continue;
            }
            vaultTable = tableCache.acceptTableAccount(tableAccountInfo);
            if (vaultTable == null) {
              continue;
            }
            this.accountsNeededSet.remove(vaultTableKey);
          }
          tableMetas[i++] = LookupTableAccountMeta.createMeta(vaultTable, Transaction.MAX_ACCOUNTS);
        }
      }
    }

    return i < tableMetas.length
        ? Arrays.copyOfRange(tableMetas, 0, i)
        : tableMetas;
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
          break;
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
              break SPIN_LOOP;
            } else {
              continue SPIN_LOOP;
            }
          }
        }
        return;
      }
    }
    this.aumTransaction.set(null);
    this.serviceContext.executeTask(this);
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
          break;
        }
      } else {
        final int numAccounts = addressLookupTable.numUniqueAccounts();
        for (int i = 0; i < witness.length; i++) {
          final var previous = witness[i];
          final var previousTable = previous.lookupTable();
          if (previousTable.address().equals(tableKey)) {
            if (numAccounts <= previousTable.numAccounts()) {
              return; // Assume stale view
            }
            final var newArray = new LookupTableAccountMeta[witness.length];
            System.arraycopy(witness, 0, newArray, 0, witness.length);
            newArray[i] = tableMeta;
            if (this.glamVaultTables.compareAndSet(witness, newArray)) {
              break SPIN_LOOP;
            } else {
              continue SPIN_LOOP;
            }
          }
        }
        final var newArray = new LookupTableAccountMeta[witness.length + 1];
        System.arraycopy(witness, 0, newArray, 0, witness.length);
        newArray[witness.length] = tableMeta;
        if (this.glamVaultTables.compareAndSet(witness, newArray)) {
          break;
        }
      }
    }
    this.aumTransaction.set(null);
    this.serviceContext.executeTask(this);
  }
}
