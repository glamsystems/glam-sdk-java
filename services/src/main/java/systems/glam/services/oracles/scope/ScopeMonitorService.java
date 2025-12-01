package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.scope.entries.*;
import software.sava.idl.clients.kamino.scope.entries.Deprecated;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class ScopeMonitorService implements Runnable, Consumer<AccountInfo<byte[]>> {

  private static final System.Logger logger = System.getLogger(ScopeMonitorService.class.getName());

  private final RpcCaller rpcCaller;
  private final PublicKey kLendProgram;
  private final Set<PublicKey> accountsNeeded;
  private final long pollingDelayNanos;
  private final Path mappingsPath;
  private final Map<PublicKey, Configuration> configurationMap;
  private final Map<PublicKey, PublicKey> scopeFeedToMappings;
  private final Map<PublicKey, PublicKey> mappingsToScopeFeed;
  private final Map<PublicKey, MappingsContext> scopeEntriesMap;
  private final Map<PublicKey, ReserveContext> reserveContextMap;
  private final List<Map<PublicKey, ReserveContext>> reservesUsingScopeIndex;
  private final ReentrantLock lock;

  public ScopeMonitorService(final RpcCaller rpcCaller,
                             final PublicKey kLendProgram,
                             final Set<PublicKey> accountsNeeded,
                             final Collection<Configuration> configurations,
                             final long pollingDelayNanos,
                             final Path mappingsPath) {
    this.rpcCaller = rpcCaller;
    this.kLendProgram = kLendProgram;
    this.accountsNeeded = accountsNeeded;
    this.pollingDelayNanos = pollingDelayNanos;
    this.mappingsPath = mappingsPath;
    this.configurationMap = new ConcurrentHashMap<>();
    this.scopeFeedToMappings = new ConcurrentHashMap<>();
    this.mappingsToScopeFeed = new ConcurrentHashMap<>();
    for (final var configuration : configurations) {
      if (configurationMap.put(configuration._address(), configuration) == null) {
        final var oraclePrices = configuration.oraclePrices();
        final var mappings = configuration.oracleMappings();
        scopeFeedToMappings.put(oraclePrices, mappings);
        mappingsToScopeFeed.put(mappings, oraclePrices);
      }
    }
    this.scopeEntriesMap = new ConcurrentHashMap<>();
    this.reserveContextMap = new ConcurrentHashMap<>();
    this.reservesUsingScopeIndex = new ArrayList<>(OracleMappings.PRICE_INFO_ACCOUNTS_LEN);
    this.lock = new ReentrantLock();
  }

  private void persistMappings(final MappingsContext mappingContext) {
    final var mappingsPath = this.mappingsPath.resolve(mappingContext.publicKey().toBase58());
    final var encodedData = Base64.getEncoder().encode(mappingContext.data());
    try {
      Files.write(mappingsPath, encodedData, CREATE, TRUNCATE_EXISTING, WRITE);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to persist mappings.", e);
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (data.length == OracleMappings.BYTES && OracleMappings.DISCRIMINATOR.equals(data, 0)) {
      final var mappingContext = MappingsContext.createContext(accountInfo);
      handleMappingChange(mappingContext);
    } else if (data.length == Configuration.BYTES && Configuration.DISCRIMINATOR.equals(data, 0)) {
      final var configuration = Configuration.read(accountInfo);
      handleConfigurationChange(configuration);
    }
  }

  private void handleReserveChange(final ReserveContext reserveContext) {

  }

  private void handleConfigurationChange(final Configuration configuration) {

  }

  private void handleMappingChange(final MappingsContext mappingContext) {
    final var key = mappingContext.publicKey();
    lock.lock();
    try {
      final var previousContext = scopeEntriesMap.put(key, mappingContext);
      if (previousContext == null) {
        persistMappings(mappingContext);
      } else if (mappingContext.isAfter(previousContext) && !Arrays.equals(previousContext.data(), mappingContext.data())) {
        final var previousEntries = previousContext.scopeEntries();
        final var newEntries = mappingContext.scopeEntries();
        final int numEntries = newEntries.numEntries();
        if (previousEntries.numEntries() != numEntries) {
          throw new IllegalStateException(String.format(
              "Scope Mappings changed in length from %d to %d.",
              previousEntries.numEntries(), numEntries
          ));
        }

        final var changes = IntStream.range(0, numEntries).mapToObj(i -> {
          final var prevEntry = previousEntries.scopeEntry(i);
          final var newEntry = newEntries.scopeEntry(i);
          if (prevEntry.equals(newEntry)) {
            return null;
          } else {
            final var affectedReserves = reservesUsingScopeIndex.get(i);
            return String.format("""
                    {
                      "previous": %s,
                      "latest": %s,
                      "reserves": [%s]
                    }""",
                prevEntry, newEntry,
                affectedReserves.values().stream()
                    .map(ReserveContext::keysToJson)
                    .collect(Collectors.joining(",\n"))
            );
          }
        }).filter(Objects::nonNull).toList();
        final var msg = String.format("""
                {
                  "scopeFeed": "%s",
                  "numChanges": %d,
                  "slot": %s,
                  "changes": [%s]
                }""",
            key.toBase58(),
            changes.size(),
            Long.toUnsignedString(mappingContext.slot()),
            String.join(",\n", changes)
        );
        logger.log(INFO, msg);
        // TODO Alert entry change.
        persistMappings(mappingContext);
      }
    } finally {
      lock.unlock();
    }
  }

  private void removePreviousEntry(final ReserveContext previousContext) {
    final var reserve = previousContext.reserve();
    final var scopeConfiguration = reserve.config().tokenInfo().scopeConfiguration();
    for (final short index : scopeConfiguration.priceChain()) {
      if (index < 0) {
        break;
      }
      reservesUsingScopeIndex.get(index).remove(reserve._address());
    }
    for (final short index : scopeConfiguration.twapChain()) {
      if (index < 0) {
        break;
      }
      reservesUsingScopeIndex.get(index).remove(reserve._address());
    }
  }

  private void addEntry(final ReserveContext reserveContext) {
    final var reserve = reserveContext.reserve();
    final var scopeConfiguration = reserve.config().tokenInfo().scopeConfiguration();
    for (final short index : scopeConfiguration.priceChain()) {
      if (index < 0) {
        break;
      }
      reservesUsingScopeIndex.get(index).put(reserve._address(), reserveContext);
    }
    for (final short index : scopeConfiguration.twapChain()) {
      if (index < 0) {
        break;
      }
      reservesUsingScopeIndex.get(index).put(reserve._address(), reserveContext);
    }
  }

  @Override
  public void run() {
    final var reserveAccountFilters = List.of(Reserve.SIZE_FILTER, Reserve.DISCRIMINATOR_FILTER);
    var accountsNeeded = new ArrayList<>(this.accountsNeeded);
    // TODO init scopeEntriesMap
    for (; ; ) {
      final var reserveAccountsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(kLendProgram, reserveAccountFilters),
          "Kamino Reserve's"
      );
      final var _accountsNeeded = accountsNeeded;
      final var priceFeedAccounts = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccounts(_accountsNeeded),
          "Scope accounts"
      ).join();

      final var reserveAccounts = reserveAccountsFuture.join();
      final var mutatedReserves = new ArrayList<ReserveContext>(reserveAccounts.size());
      final var presentReserves = HashSet.<PublicKey>newHashSet(reserveAccounts.size());
      for (final var reserveAccountInfo : reserveAccounts) {
        presentReserves.add(reserveAccountInfo.pubKey());
        final var reserve = Reserve.read(reserveAccountInfo);

        final var reserveContext = ReserveContext.createContext(reserve, scopeEntriesMap);
        if (reserveContext == null) {
          final var scopeConfiguration = reserve.config().tokenInfo().scopeConfiguration();
          if (this.accountsNeeded.add(scopeConfiguration.priceFeed())) {
            accountsNeeded = new ArrayList<>(this.accountsNeeded);
          }
          continue;
        }

        final var previousContext = reserveContextMap.get(reserve._address());
        if (previousContext != null) {
          if (previousContext.changed(reserveContext)) {
            removePreviousEntry(previousContext);
          } else {
            continue;
          }
        }

        mutatedReserves.add(reserveContext);
        addEntry(reserveContext);
      }

      int i = 0;
      for (final var accountInfo : priceFeedAccounts) {
        if (accountInfo == null) {
          final var key = accountsNeeded.get(i);
          if (scopeEntriesMap.containsKey(key)) {
            final var previousContext = scopeEntriesMap.remove(key);
            if (previousContext != null) {
              // TODO: Alert account deletion
              this.accountsNeeded.remove(key);
              accountsNeeded = new ArrayList<>(this.accountsNeeded);
            }
          }
        } else {
          accept(accountInfo);
        }
        ++i;
      }

      for (final var previousContext : reserveContextMap.values()) {
        if (!presentReserves.contains(previousContext.reserve()._address())) {
          removePreviousEntry(previousContext);
        }
      }

      for (final var reserveContext : mutatedReserves) {
        final var reserve = reserveContext.reserve();
        final var previousReserveContext = reserveContextMap.put(reserve._address(), reserveContext);
        if (previousReserveContext != null) {
          handleReserveChange(reserveContext);
        }
      }

      try {
        NANOSECONDS.sleep(pollingDelayNanos);
      } catch (final InterruptedException e) {
        return;
      }
    }
  }

  private static String toJson(final ScopeEntry scopeEntry) {
    if (scopeEntry == null) {
      return null;
    }
    return switch (scopeEntry) {
      case OracleEntry e -> {
        final var prefix = String.format("""
                {
                  "type": "%s",
                  "oracle": "%s",
                  "twapEnabled": %b""",
            e.oracleType().name(),
            e.oracle(),
            e.twapEnabled()
        );
        yield switch (e) {
          case ReferencesEntry re -> {
            final var refPrefix = String.format("""
                    %s,
                      "refPrice": %s""",
                prefix,
                toJson(re.refPrice())
            );
            yield switch (re) {
              case Chainlink chainlink -> String.format("""
                      %s,
                        "confidenceFactor": %d
                      }""",
                  refPrefix,
                  chainlink.confidenceFactor()
              );
              case PythLazer pythLazer -> String.format("""
                      %s,
                        "feedId": %d,
                        "exponent": %d,
                        "confidenceFactor": %d
                      }""",
                  refPrefix,
                  pythLazer.feedId(),
                  pythLazer.exponent(),
                  pythLazer.confidenceFactor()
              );
              default -> prefix + "\n}";
            };
          }
          case ChainlinkStatusEntry chainlinkStatusEntry -> String.format("""
                  %s,
                    "marketStatusBehavior": "%s"
                  }""",
              prefix,
              chainlinkStatusEntry.marketStatusBehavior()
          );
          default -> prefix + "\n}";
        };
      }
      case CappedFloored cappedFloored -> String.format("""
              {
                "type": "%s",
                "source": %s,
                "cap": %s,
                "floor": %s
              }""",
          cappedFloored.oracleType().name(),
          toJson(cappedFloored.sourceEntry()),
          toJson(cappedFloored.capEntry()),
          toJson(cappedFloored.flooredEntry())
      );
      case MostRecentOf mostRecentOf -> {
        final var prefix = String.format("""
                {
                  "type": "%s",
                  "sources": %s,
                  "maxDivergenceBps": %d,
                  "sourcesMaxAgeS": %d,""",
            mostRecentOf.oracleType().name(),
            toJson(mostRecentOf.sources()),
            mostRecentOf.maxDivergenceBps(),
            mostRecentOf.sourcesMaxAgeS()
        );
        yield switch (mostRecentOf) {
          case CappedMostRecentOf cappedMostRecentOf -> String.format("""
                  %s
                    "cap": %s
                  }""",
              prefix,
              toJson(cappedMostRecentOf.capEntry())
          );
          case MostRecentOfRecord mostRecentOfRecord -> String.format("""
                  %s
                    "refPrice": %s
                  }""",
              prefix,
              toJson(mostRecentOfRecord.refPrice())
          );
        };
      }
      case Deprecated _ -> """
          {
            "type": "Deprecated"
          }""";
      case DiscountToMaturity discountToMaturity -> String.format("""
              {
                "type": "%s",
                "discountPerYearBps": %d,
                "maturityTimestamp": %d
              }""",
          discountToMaturity.oracleType().name(),
          discountToMaturity.discountPerYearBps(),
          discountToMaturity.maturityTimestamp()
      );
      case FixedPrice fixedPrice -> String.format("""
              {
                "type": "%s",
                "value": %d,
                "exp": %d,
                "decimal": "%s"
              }""",
          fixedPrice.oracleType().name(),
          fixedPrice.value(), fixedPrice.exp(),
          BigDecimal.valueOf(fixedPrice.value())
              .movePointLeft(Math.toIntExact(fixedPrice.exp()))
              .stripTrailingZeros()
              .toPlainString()
      );
      case NotYetSupported notYetSupported -> String.format("""
              {
                "type": "%s",
                "oracle": "%s",
                "twapSource": %s,
                "twapEnabled": %b,
                "refPrice": %s,
                "generic": "%s"
              }""",
          notYetSupported.oracleType().name(),
          notYetSupported.priceAccount(),
          toJson(notYetSupported.twapSource()),
          notYetSupported.twapEnabled(),
          toJson(notYetSupported.refPrice()),
          Base64.getEncoder().encodeToString(notYetSupported.generic())
      );
      case ScopeTwap scopeTwap -> String.format("""
              {
                "type": "%s",
                "source": %s
              }""",
          scopeTwap.oracleType().name(),
          toJson(scopeTwap.sourceEntry())
      );
      case Unused _ -> """
          {
            "type": "Unused"
          }""";
    };
  }

  static String toJson(final ScopeEntry[] priceChain) {
    return Arrays.stream(priceChain).<String>mapMulti((entry, downstream) -> {
      if (entry != null) {
        final var json = toJson(entry);
        if (json != null) {
          downstream.accept(json.indent(4).stripTrailing());
        }
      }
    }).collect(Collectors.joining(",\n", "[\n", "\n  ]"));
  }
}
