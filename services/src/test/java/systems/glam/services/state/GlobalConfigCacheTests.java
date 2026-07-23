package systems.glam.services.state;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.services.io.FileUtils;
import systems.glam.services.mints.AssetMetaContext;
import systems.glam.services.mints.MintCache;
import systems.glam.services.mints.MintContext;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

final class GlobalConfigCacheTests {

  private static final PublicKey GLOBAL_CONFIG_KEY = GlamAccounts.MAIN_NET.globalConfigPDA().publicKey();
  private static final MintCache NULL_MINT_CACHE = new MintCache() {

    @Override
    public MintContext get(final PublicKey mintPubkey) {
      return null;
    }

    @Override
    public MintContext setGet(final MintContext mintContext) {
      return null;
    }

    @Override
    public MintContext delete(final PublicKey mintPubkey) {
      return null;
    }

    @Override
    public void close() {

    }
  };

  private static byte[] globalConfigData;

  /// Every rejection path in this cache logs before it returns null or throws.
  /// Capturing the stream keeps the console quiet while pinning the contract
  /// that a rejected config is never silent -- otherwise a dropped
  /// `logger.log` is invisible to every assertion.
  private static final class CapturingHandler extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(final LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  }

  private static final CapturingHandler LOG_RECORDS = new CapturingHandler();

  @BeforeEach
  void clearLog() {
    LOG_RECORDS.records.clear();
  }

  private static void assertLogged(final String event) {
    assertTrue(
        LOG_RECORDS.records.stream().anyMatch(r -> r.getMessage() != null && r.getMessage().contains(event)),
        () -> "expected a log record naming \"" + event + "\", got "
            + LOG_RECORDS.records.stream().map(LogRecord::getMessage).toList()
    );
  }

  @BeforeAll
  static void beforeAll() throws IOException {
    final var logger = Logger.getLogger(GlobalConfigCache.class.getName());
    logger.setLevel(Level.ALL);
    logger.setUseParentHandlers(false);
    logger.addHandler(LOG_RECORDS);
    globalConfigData = ResourceUtil.readResource("accounts/glam/global/" + GLOBAL_CONFIG_KEY + ".json.gz");
  }

  private static GlobalConfigCacheImpl createCache(final Path tempDir) {
    return createCache(tempDir, NULL_MINT_CACHE);
  }

  private static GlobalConfigCacheImpl createCache(final Path tempDir, final MintCache mintCache) {
    final var globalConfigFile = FileUtils.resolveCompressedAccountPath(tempDir, GLOBAL_CONFIG_KEY);
    try {
      FileUtils.writeCompressedAccountData(tempDir, GLOBAL_CONFIG_KEY, globalConfigData);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var cacheFuture = GlobalConfigCache.initCache(
        globalConfigFile,
        GlamAccounts.MAIN_NET.configProgram(),
        GLOBAL_CONFIG_KEY,
        SolanaAccounts.MAIN_NET,
        mintCache,
        null,
        null,
        Duration.ofSeconds(1)
    );

    final var cache = (GlobalConfigCacheImpl) cacheFuture.join();
    assertNotNull(cache);
    return cache;
  }

  @Test
  void testInitCacheFromDisk(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var globalConfigUpdate = cache.globalConfigUpdate();
    final var globalConfig = GlobalConfig.read(globalConfigUpdate.data(), 0);
    assertEquals(
        PublicKey.fromBase58Encoded("9oWi2MjrAujYNTUXXNBLk1ugioaF1mJHc7EoamX4eQLZ"),
        globalConfig.admin()
    );
    assertEquals(
        PublicKey.fromBase58Encoded("9oWi2MjrAujYNTUXXNBLk1ugioaF1mJHc7EoamX4eQLZ"),
        globalConfig.feeAuthority()
    );
    assertEquals(
        PublicKey.fromBase58Encoded("GLAMrG37ZqioqvzBNQGCfCUueDz3tsr7MwMFyRk9PS89"),
        globalConfig.referrer()
    );
    assertEquals(1, globalConfig.baseFeeBps());
    assertEquals(0, globalConfig.flowFeeBps());

    // Verify USDC
    final var usdc = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    final var usdcMeta = cache.topPriorityForMint(usdc);
    assertNotNull(usdcMeta);
    assertEquals(usdc, usdcMeta.asset());
    assertEquals(6, usdcMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("9VCioxmni2gDLv11qufWzT3RDERhQE4iY5Gf7NTfYyAV"), usdcMeta.oracle());
    assertEquals(OracleSource.PythLazerStableCoin, usdcMeta.oracleSource());
    assertEquals(30, usdcMeta.maxAgeSeconds());
    assertEquals(0, usdcMeta.priority());

    // Verify SOL
    final var sol = PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112");
    final var solMeta = cache.topPriorityForMint(sol);
    assertNotNull(solMeta);
    assertEquals(sol, solMeta.asset());
    assertEquals(9, solMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("3m6i4RFWEDw2Ft4tFHPJtYgmpPe21k56M3FHeWYrgGBz"), solMeta.oracle());
    assertEquals(OracleSource.PythLazer, solMeta.oracleSource());
    assertEquals(30, solMeta.maxAgeSeconds());
    assertEquals(0, solMeta.priority());

    // Verify mSOL
    final var msol = PublicKey.fromBase58Encoded("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So");
    final var msolMeta = cache.topPriorityForMint(msol);
    assertNotNull(msolMeta);
    assertEquals(msol, msolMeta.asset());
    assertEquals(9, msolMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("FY2JMi1vYz1uayVT2GJ96ysZgpagjhdPRG2upNPtSZsC"), msolMeta.oracle());
    assertEquals(OracleSource.PythLazer, msolMeta.oracleSource());
    assertEquals(30, msolMeta.maxAgeSeconds());
    assertEquals(0, msolMeta.priority());

    // Verify WBTC (3NZ9...)
    final var wbtc = PublicKey.fromBase58Encoded("3NZ9JMVBmGAqocybic2c7LQCJScmgsAZ6vQqTDzcqmJh");
    final var wbtcMeta = cache.topPriorityForMint(wbtc);
    assertNotNull(wbtcMeta);
    assertEquals(wbtc, wbtcMeta.asset());
    assertEquals(8, wbtcMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("fqPfDa6uQr9ndMvwaFp4mUBeUrHmLop8Jxfb1XJNmVm"), wbtcMeta.oracle());
    assertEquals(OracleSource.PythLazer, wbtcMeta.oracleSource());
    assertEquals(30, wbtcMeta.maxAgeSeconds());
    assertEquals(0, wbtcMeta.priority());

    // Verify ETH (7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs) - uses PythPull
    final var eth = PublicKey.fromBase58Encoded("7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs");
    final var ethMeta = cache.topPriorityForMint(eth);
    assertNotNull(ethMeta);
    assertEquals(eth, ethMeta.asset());
    assertEquals(8, ethMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("6bEp2MiyoiiiDxcVqE8rUHQWwHirXUXtKfAEATTVqNzT"), ethMeta.oracle());
    assertEquals(OracleSource.PythPull, ethMeta.oracleSource());
    assertEquals(30, ethMeta.maxAgeSeconds());
    assertEquals(0, ethMeta.priority());

    // Verify USDT
    final var usdt = PublicKey.fromBase58Encoded("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB");
    final var usdtMeta = cache.topPriorityForMint(usdt);
    assertNotNull(usdtMeta);
    assertEquals(usdt, usdtMeta.asset());
    assertEquals(6, usdtMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("JDKJSkxjasBGL3ce1pkrN6tqDzuVUZPWzzkGuyX8m9yN"), usdtMeta.oracle());
    assertEquals(OracleSource.PythLazerStableCoin, usdtMeta.oracleSource());
    assertEquals(30, usdtMeta.maxAgeSeconds());
    assertEquals(0, usdtMeta.priority());

    // Verify JitoSOL
    final var jitosol = PublicKey.fromBase58Encoded("J1toso1uCk3RLmjorhTtrVwY9HJ7X8V9yYac6Y7kGCPn");
    final var jitosolMeta = cache.topPriorityForMint(jitosol);
    assertNotNull(jitosolMeta);
    assertEquals(jitosol, jitosolMeta.asset());
    assertEquals(9, jitosolMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("2cHCtAkMnttMh3bNKSCgSKSP5D4yN3p8bfnMdS3VZsDf"), jitosolMeta.oracle());
    assertEquals(OracleSource.PythLazer, jitosolMeta.oracleSource());
    assertEquals(30, jitosolMeta.maxAgeSeconds());
    assertEquals(0, jitosolMeta.priority());

    // Verify PYUSD
    final var pyusd = PublicKey.fromBase58Encoded("2b1kV6DkPAnxd5ixfnxCpjxmKwqjjaYmCZfHsFu24GXo");
    final var pyusdMeta = cache.topPriorityForMint(pyusd);
    assertNotNull(pyusdMeta);
    assertEquals(pyusd, pyusdMeta.asset());
    assertEquals(6, pyusdMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("5QZMnsyndmphvZF4BNgoMHwVZaREXeE2rpBoCPMxgCCd"), pyusdMeta.oracle());
    assertEquals(OracleSource.PythLazerStableCoin, pyusdMeta.oracleSource());
    assertEquals(30, pyusdMeta.maxAgeSeconds());
    assertEquals(0, pyusdMeta.priority());

    // Verify META (METADDFL6wWMWEoKTFJwcThTbUmtarRJZjRpzUvkxhr) - uses SwitchboardOnDemand
    final var meta = PublicKey.fromBase58Encoded("METADDFL6wWMWEoKTFJwcThTbUmtarRJZjRpzUvkxhr");
    final var metaMeta = cache.topPriorityForMint(meta);
    assertNotNull(metaMeta);
    assertEquals(meta, metaMeta.asset());
    assertEquals(9, metaMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("DwYF1yveo8XTF1oqfsqykj332rjSxAd7bR6Gu6i4iUET"), metaMeta.oracle());
    assertEquals(OracleSource.SwitchboardOnDemand, metaMeta.oracleSource());
    assertEquals(30, metaMeta.maxAgeSeconds());
    assertEquals(0, metaMeta.priority());

    // Verify ChainlinkRWA oracle source (XsCPL9dNWBMvFtTmwcCA5v3xWPSMEBCszbQdiLLq6aN)
    final var xsCpl = PublicKey.fromBase58Encoded("XsCPL9dNWBMvFtTmwcCA5v3xWPSMEBCszbQdiLLq6aN");
    final var xsCplMeta = cache.topPriorityForMint(xsCpl);
    assertNotNull(xsCplMeta);
    assertEquals(xsCpl, xsCplMeta.asset());
    assertEquals(8, xsCplMeta.decimals());
    assertEquals(PublicKey.fromBase58Encoded("18BUkzs6x4C3mK1W1NPHxdZZvnvFaQ5QXkX5Zx5ggBz"), xsCplMeta.oracle());
    assertEquals(OracleSource.ChainlinkRWA, xsCplMeta.oracleSource());
    assertEquals(30, xsCplMeta.maxAgeSeconds());
    assertEquals(0, xsCplMeta.priority());

    final var firstAsset = cache.getByIndex(0);
    assertNotNull(firstAsset);
    assertEquals(usdc, firstAsset.asset());

    final var secondAsset = cache.getByIndex(1);
    assertNotNull(secondAsset);
    assertEquals(sol, secondAsset.asset());

    final int lastIndex = globalConfigUpdate.assetMetaContexts().length - 1;
    final var lastAsset = cache.getByIndex(lastIndex);
    assertNotNull(lastAsset);
    assertEquals(PublicKey.fromBase58Encoded("XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB"), lastAsset.asset());
    assertEquals(8, lastAsset.decimals());
    assertEquals(PublicKey.fromBase58Encoded("18MAcEvgzQoAktRTsNhPo4Cb6Gtp7L2X5hAEeiYtD13"), lastAsset.oracle());
    assertEquals(OracleSource.ChainlinkRWA, lastAsset.oracleSource());
    assertEquals(30, lastAsset.maxAgeSeconds());
    assertEquals(0, lastAsset.priority());

    // Verify out-of-bounds index returns null
    assertNull(cache.getByIndex(lastIndex + 1));
    assertNull(cache.getByIndex(Integer.MAX_VALUE));
    assertNull(cache.topPriorityForMint(PublicKey.NONE));
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenOracleRemoved(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfigUpdate = cache.globalConfigUpdate();
    final var previousGlobalConfig = GlobalConfig.read(previousGlobalConfigUpdate.data(), 0);
    final var previousAssetMetas = previousGlobalConfig.assetMetas();
    assertTrue(previousAssetMetas.length > 1);
    assertEquals(previousAssetMetas.length, previousGlobalConfigUpdate.assetMetaContexts().length);

    // Create an invalid GlobalConfig with fewer asset metas (simulating oracle removal)
    final var reducedAssetMetas = Arrays.copyOf(previousAssetMetas, previousAssetMetas.length - 1);
    final var invalidGlobalConfig = new GlobalConfig(
        previousGlobalConfig._address(),
        previousGlobalConfig.discriminator(),
        previousGlobalConfig.admin(),
        previousGlobalConfig.feeAuthority(),
        previousGlobalConfig.referrer(),
        previousGlobalConfig.baseFeeBps(),
        previousGlobalConfig.flowFeeBps(),
        reducedAssetMetas
    );

    // Call createMapChecked with the invalid config - should return null
    final var called = new AtomicReference<String>();
    final var listener = new TestGlobalConfigListener(called);
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfigUpdate.assetMetaContexts(),
        cache.assetMetaMap,
        AssetMetaContext.mapAssetMetas(invalidGlobalConfig),
        NULL_MINT_CACHE,
        Set.of(listener)
    );
    assertNotNull(result);
    // assertNull(result); // TODO: Update once v2 is deployed to production.
    assertEquals("onAssetMetaRemoved", called.get());
    assertLogged("GlobalConfig Oracle Removed");
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenEntryChangedWithoutNegativePriority(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfigUpdate = cache.globalConfigUpdate();
    final var previousGlobalConfig = GlobalConfig.read(previousGlobalConfigUpdate.data(), 0);
    final var previousAssetMetas = previousGlobalConfig.assetMetas();
    assertTrue(previousAssetMetas.length > 1);

    final var originalMeta = previousAssetMetas[0];
    final var differentPubKey = PublicKey.NONE;

    // Case 1: Same asset but different oracle (without negative priority)
    final var oracleChangedMetas = Arrays.copyOf(previousAssetMetas, previousAssetMetas.length);
    oracleChangedMetas[0] = new AssetMeta(
        originalMeta.asset(),       // same asset
        originalMeta.decimals(),
        differentPubKey,            // different oracle
        originalMeta.oracleSource(),
        originalMeta.maxAgeSeconds(),
        originalMeta.priority(),    // priority is 0 (not negative)
        originalMeta.padding()
    );

    final var oracleChangedConfig = new GlobalConfig(
        previousGlobalConfig._address(),
        previousGlobalConfig.discriminator(),
        previousGlobalConfig.admin(),
        previousGlobalConfig.feeAuthority(),
        previousGlobalConfig.referrer(),
        previousGlobalConfig.baseFeeBps(),
        previousGlobalConfig.flowFeeBps(),
        oracleChangedMetas
    );

    final var called = new AtomicReference<String>();
    final var listener = new TestGlobalConfigListener(called);
    var globalConfigCache = GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfigUpdate.assetMetaContexts(),
        cache.assetMetaMap,
        AssetMetaContext.mapAssetMetas(oracleChangedConfig),
        NULL_MINT_CACHE,
        Set.of(listener)
    );
    assertNotNull(globalConfigCache);
    // assertNull(globalConfigCache); TODO: Update once v2 is deployed to production.
    assertEquals("onUnexpectedOracleChange", called.get());

    // Case 2: Different asset (without negative priority)
    final var assetChangedMetas = Arrays.copyOf(previousAssetMetas, previousAssetMetas.length);
    assetChangedMetas[0] = new AssetMeta(
        differentPubKey,            // different asset
        originalMeta.decimals(),
        originalMeta.oracle(),      // same oracle
        originalMeta.oracleSource(),
        originalMeta.maxAgeSeconds(),
        originalMeta.priority(),    // priority is 0 (not negative)
        originalMeta.padding()
    );

    final var assetChangedConfig = new GlobalConfig(
        previousGlobalConfig._address(),
        previousGlobalConfig.discriminator(),
        previousGlobalConfig.admin(),
        previousGlobalConfig.feeAuthority(),
        previousGlobalConfig.referrer(),
        previousGlobalConfig.baseFeeBps(),
        previousGlobalConfig.flowFeeBps(),
        assetChangedMetas
    );

    called.set(null);
    globalConfigCache = GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfigUpdate.assetMetaContexts(),
        cache.assetMetaMap,
        AssetMetaContext.mapAssetMetas(assetChangedConfig),
        NULL_MINT_CACHE,
        Set.of(listener)
    );
    assertNotNull(globalConfigCache);
    // assertNull(globalConfigCache); TODO: Update once v2 is deployed to production.
    assertEquals("onUnexpectedOracleChange", called.get());
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenSameOracleDifferentSourceWithinConfig(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfigUpdate = cache.globalConfigUpdate();
    final var previousGlobalConfig = GlobalConfig.read(previousGlobalConfigUpdate.data(), 0);
    final var previousAssetMetas = previousGlobalConfig.assetMetas();
    assertTrue(previousAssetMetas.length > 1);

    final var firstMeta = previousAssetMetas[0];
    assertEquals(OracleSource.PythLazerStableCoin, firstMeta.oracleSource());

    final var modifiedMetas = Arrays.copyOf(previousAssetMetas, previousAssetMetas.length + 1);

    modifiedMetas[previousAssetMetas.length] = new AssetMeta(
        firstMeta.asset(),
        firstMeta.decimals(),
        firstMeta.oracle(),
        OracleSource.PythLazer,
        firstMeta.maxAgeSeconds(),
        1,
        firstMeta.padding()
    );

    final var invalidConfig = new GlobalConfig(
        previousGlobalConfig._address(),
        previousGlobalConfig.discriminator(),
        previousGlobalConfig.admin(),
        previousGlobalConfig.feeAuthority(),
        previousGlobalConfig.referrer(),
        previousGlobalConfig.baseFeeBps(),
        previousGlobalConfig.flowFeeBps(),
        modifiedMetas
    );

    final var called = new AtomicReference<String>();
    final var listener = new TestGlobalConfigListener(called);
    final var result = GlobalConfigCacheImpl.createMapChecked(1L, AssetMetaContext.mapAssetMetas(invalidConfig), Map.of(), NULL_MINT_CACHE, Set.of(listener));
    assertNull(result);
    assertEquals("onInconsistentOracleSourceWithinConfig", called.get());
    assertLogged("Inconsistent OracleSource Within GlobalConfig");
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenOracleSourceChangedAcrossConfigs(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfigUpdate = cache.globalConfigUpdate();
    final var previousGlobalConfig = GlobalConfig.read(previousGlobalConfigUpdate.data(), 0);
    final var previousAssetMetas = previousGlobalConfig.assetMetas();
    assertTrue(previousAssetMetas.length > 1);

    final var firstMeta = previousAssetMetas[0];
    assertEquals(OracleSource.PythLazerStableCoin, firstMeta.oracleSource());

    final var modifiedMetas = Arrays.copyOf(previousAssetMetas, previousAssetMetas.length);
    modifiedMetas[0] = new AssetMeta(
        firstMeta.asset(),
        firstMeta.decimals(),
        firstMeta.oracle(),
        OracleSource.PythPull,
        firstMeta.maxAgeSeconds(),
        firstMeta.priority(),
        firstMeta.padding()
    );

    final var newGlobalConfig = new GlobalConfig(
        previousGlobalConfig._address(),
        previousGlobalConfig.discriminator(),
        previousGlobalConfig.admin(),
        previousGlobalConfig.feeAuthority(),
        previousGlobalConfig.referrer(),
        previousGlobalConfig.baseFeeBps(),
        previousGlobalConfig.flowFeeBps(),
        modifiedMetas
    );

    final var called = new AtomicReference<String>();
    final var listener = new TestGlobalConfigListener(called);
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfigUpdate.assetMetaContexts(),
        cache.assetMetaMap,
        AssetMetaContext.mapAssetMetas(newGlobalConfig),
        NULL_MINT_CACHE,
        Set.of(listener)
    );
    assertNull(result);
    assertEquals("onInconsistentOracleSource", called.get());
    assertLogged("Inconsistent Asset OracleSource Across GlobalConfig's");
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenDuplicateOracleForAsset(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfigUpdate = cache.globalConfigUpdate();
    final var previousGlobalConfig = GlobalConfig.read(previousGlobalConfigUpdate.data(), 0);
    final var previousAssetMetas = previousGlobalConfig.assetMetas();
    assertTrue(previousAssetMetas.length > 0);

    final var firstMeta = previousAssetMetas[0];
    final var duplicatedMetas = Arrays.copyOf(previousAssetMetas, previousAssetMetas.length + 1);
    duplicatedMetas[previousAssetMetas.length] = firstMeta;

    final var invalidConfig = new GlobalConfig(
        previousGlobalConfig._address(),
        previousGlobalConfig.discriminator(),
        previousGlobalConfig.admin(),
        previousGlobalConfig.feeAuthority(),
        previousGlobalConfig.referrer(),
        previousGlobalConfig.baseFeeBps(),
        previousGlobalConfig.flowFeeBps(),
        duplicatedMetas
    );

    final var called = new AtomicReference<String>();
    final var listener = new TestGlobalConfigListener(called);
    final var result = GlobalConfigCacheImpl.createMapChecked(1L, AssetMetaContext.mapAssetMetas(invalidConfig), Map.of(), NULL_MINT_CACHE, Set.of(listener));
    assertNull(result);
    assertEquals("onDuplicateOracleForAsset", called.get());
    assertLogged("Duplicate Oracle For Asset");
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenInconsistentDecimalsForAsset(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfigUpdate = cache.globalConfigUpdate();
    final var previousGlobalConfig = GlobalConfig.read(previousGlobalConfigUpdate.data(), 0);
    final var previousAssetMetas = previousGlobalConfig.assetMetas();
    assertTrue(previousAssetMetas.length > 0);

    final var firstMeta = previousAssetMetas[0];
    assertEquals(OracleSource.PythLazerStableCoin, firstMeta.oracleSource());

    final var newEntryWithWrongDecimals = new AssetMeta(
        firstMeta.asset(),
        firstMeta.decimals() + 1,
        PublicKey.NONE,
        OracleSource.PythPull,
        firstMeta.maxAgeSeconds(),
        1,
        firstMeta.padding()
    );

    final var modifiedMetas = Arrays.copyOf(previousAssetMetas, previousAssetMetas.length + 1);
    modifiedMetas[previousAssetMetas.length] = newEntryWithWrongDecimals;

    final var invalidConfig = new GlobalConfig(
        previousGlobalConfig._address(),
        previousGlobalConfig.discriminator(),
        previousGlobalConfig.admin(),
        previousGlobalConfig.feeAuthority(),
        previousGlobalConfig.referrer(),
        previousGlobalConfig.baseFeeBps(),
        previousGlobalConfig.flowFeeBps(),
        modifiedMetas
    );

    final var called = new AtomicReference<String>();
    final var listener = new TestGlobalConfigListener(called);
    final var result = GlobalConfigCacheImpl.createMapChecked(1L, AssetMetaContext.mapAssetMetas(invalidConfig), Map.of(), NULL_MINT_CACHE, Set.of(listener));
    assertNull(result);
    assertEquals("onInconsistentDecimalsWithinConfig", called.get());
    assertLogged("Inconsistent Asset Decimals Within Config");
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenDecimalsChangedForExistingEntry(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfigUpdate = cache.globalConfigUpdate();
    final var previousGlobalConfig = GlobalConfig.read(previousGlobalConfigUpdate.data(), 0);
    final var previousAssetMetas = previousGlobalConfig.assetMetas();
    assertTrue(previousAssetMetas.length > 0);

    final var firstMeta = previousAssetMetas[0];

    final var modifiedMetas = Arrays.copyOf(previousAssetMetas, previousAssetMetas.length);
    modifiedMetas[0] = new AssetMeta(
        firstMeta.asset(),
        firstMeta.decimals() + 1,  // different decimals - this is invalid
        firstMeta.oracle(),         // same oracle
        firstMeta.oracleSource(),
        firstMeta.maxAgeSeconds(),
        firstMeta.priority(),
        firstMeta.padding()
    );

    final var invalidConfig = new GlobalConfig(
        previousGlobalConfig._address(),
        previousGlobalConfig.discriminator(),
        previousGlobalConfig.admin(),
        previousGlobalConfig.feeAuthority(),
        previousGlobalConfig.referrer(),
        previousGlobalConfig.baseFeeBps(),
        previousGlobalConfig.flowFeeBps(),
        modifiedMetas
    );

    final var called = new AtomicReference<String>();
    final var listener = new TestGlobalConfigListener(called);
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfigUpdate.assetMetaContexts(),
        cache.assetMetaMap,
        AssetMetaContext.mapAssetMetas(invalidConfig),
        NULL_MINT_CACHE,
        Set.of(listener)
    );
    assertNull(result);
    assertEquals("onDecimalsChange", called.get());
    assertLogged("Inconsistent Asset Decimals Across GlobalConfig");
  }

  @Test
  void checkAccountValidatesOwnerAndDiscriminator() {
    final var configProgram = GlamAccounts.MAIN_NET.configProgram();
    assertTrue(GlobalConfigCacheImpl.checkAccount(
        configProgram, configProgram, 1L, GLOBAL_CONFIG_KEY, globalConfigData
    ));
    // wrong owner
    assertFalse(GlobalConfigCacheImpl.checkAccount(
        configProgram, PublicKey.NONE, 1L, GLOBAL_CONFIG_KEY, globalConfigData
    ));
    // wrong discriminator
    final var corrupted = Arrays.copyOf(globalConfigData, globalConfigData.length);
    corrupted[0] ^= 1;
    assertFalse(GlobalConfigCacheImpl.checkAccount(
        configProgram, configProgram, 1L, GLOBAL_CONFIG_KEY, corrupted
    ));
    // a rejected account is never silent
    assertLogged("Unexpected GlobalConfig Account");
  }

  /// Every entry point takes a read or write lock in a try/finally. A leaked
  /// lock blocks every other caller and no result assertion can see it.
  private static void assertUnlocked(final GlobalConfigCacheImpl cache) {
    assertFalse(cache.lock.isWriteLocked());
    assertEquals(0, cache.lock.getReadLockCount());
  }

  @Test
  void queryHelpers(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var usdc = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    assertTrue(cache.hasAssetMetaForMint(usdc));
    assertFalse(cache.hasAssetMetaForMint(PublicKey.NONE));

    final var solMeta = cache.solAssetMeta();
    assertNotNull(solMeta);
    assertEquals(SolanaAccounts.MAIN_NET.wrappedSolTokenMint(), solMeta.asset());
    assertEquals(9, solMeta.decimals());
    assertSame(cache.topPriorityForMint(SolanaAccounts.MAIN_NET.wrappedSolTokenMint()), solMeta);

    // an unknown mint has no mint context, so the checked lookup yields null
    assertNull(cache.topPriorityForMintChecked(usdc));
    assertUnlocked(cache);
    // and a mint context for a mint the config does not carry yields null too
    final var strange = PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111111");
    assertNull(cache.topPriorityForMintChecked(
        MintContext.createContext(SolanaAccounts.MAIN_NET, strange, 6, 0)
    ));
    assertUnlocked(cache);
  }

  private static final class NoopTracker extends software.sava.services.core.request_capacity.trackers.RootErrorTracker<software.sava.rpc.json.http.client.SolanaRpcClient, byte[]> {

    NoopTracker(final software.sava.services.core.request_capacity.CapacityState capacityState) {
      super(capacityState);
    }

    @Override
    protected boolean isServerError(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean isRequestError(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean isRateLimited(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean updateGroupedErrorResponseCount(final long now,
                                                      final software.sava.rpc.json.http.client.SolanaRpcClient response,
                                                      final byte[] body) {
      return false;
    }

    @Override
    protected void logResponse(final software.sava.rpc.json.http.client.SolanaRpcClient response, final byte[] body) {
    }
  }

  private static RpcCaller rpcCaller(final AccountInfo<byte[]> accountInfo) {
    final var client = (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getAccountInfo")) {
            return java.util.concurrent.CompletableFuture.completedFuture(accountInfo);
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
    final var resetDuration = Duration.ofSeconds(1);
    final var config = new software.sava.services.core.request_capacity.CapacityConfig(
        0, 1_000, resetDuration, 8, resetDuration, resetDuration, resetDuration, resetDuration);
    final var monitor = config.createMonitor("test", NoopTracker::new);
    final var item = software.sava.services.core.remote.load_balance.BalancedItem.createItem(
        client, monitor, software.sava.services.core.remote.call.Backoff.single(java.util.concurrent.TimeUnit.MILLISECONDS, 0));
    return new RpcCaller(
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(),
        software.sava.services.core.remote.load_balance.LoadBalancer.createBalancer(item),
        software.sava.services.solana.remote.call.CallWeights.createDefault()
    );
  }

  private static systems.glam.services.rpc.AccountFetcher recordingFetcher(final List<List<PublicKey>> queued) {
    return (systems.glam.services.rpc.AccountFetcher) java.lang.reflect.Proxy.newProxyInstance(
        systems.glam.services.rpc.AccountFetcher.class.getClassLoader(),
        new Class<?>[]{systems.glam.services.rpc.AccountFetcher.class},
        (proxy, method, args) -> {
          if (method.getName().equals("priorityQueueBatchable")) {
            @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
            queued.add(List.copyOf(keys));
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  @Test
  void initCacheFetchesWhenTheFileIsMissing(@TempDir final Path tempDir) {
    // a nested path: the missing parent directories must be created too
    final var globalConfigFile = FileUtils.resolveCompressedAccountPath(tempDir.resolve("fresh"), GLOBAL_CONFIG_KEY);
    final var queued = new ArrayList<List<PublicKey>>();
    final var cacheFuture = GlobalConfigCache.initCache(
        globalConfigFile,
        GlamAccounts.MAIN_NET.configProgram(),
        GLOBAL_CONFIG_KEY,
        SolanaAccounts.MAIN_NET,
        NULL_MINT_CACHE,
        rpcCaller(accountInfo(7L, GlamAccounts.MAIN_NET.configProgram(), globalConfigData)),
        recordingFetcher(queued),
        Duration.ofSeconds(1)
    );
    final var cache = (GlobalConfigCacheImpl) cacheFuture.join();

    assertEquals(7L, cache.globalConfigUpdate().slot());
    // the fetched config was persisted for the next start
    assertTrue(Files.exists(globalConfigFile));
    assertArrayEquals(globalConfigData, FileUtils.readAccountData(globalConfigFile).data());
    // every asset mint is unknown to the null cache, so all were queued
    final var config = GlobalConfig.read(cache.globalConfigUpdate().data(), 0);
    assertEquals(1, queued.size());
    assertEquals(config.assetMetas().length, queued.getFirst().size());
    // the fetched config was actually indexed, not just stored
    assertNotNull(cache.assetMetaMap.get(config.assetMetas()[0].asset()));
    assertUnlocked(cache);
  }

  @Test
  void initCacheSkipsTheMintFetchWhenEveryMintIsCached(@TempDir final Path tempDir) {
    final var config = GlobalConfig.read(globalConfigData, 0);
    final var decimalsByMint = new java.util.HashMap<PublicKey, Integer>();
    for (final var meta : config.assetMetas()) {
      decimalsByMint.putIfAbsent(meta.asset(), (int) meta.decimals());
    }
    final var agreeing = new MintCache() {
      @Override
      public MintContext get(final PublicKey mintPubkey) {
        final var decimals = decimalsByMint.get(mintPubkey);
        return decimals == null
            ? null
            : MintContext.createContext(SolanaAccounts.MAIN_NET, mintPubkey, decimals, 0);
      }

      @Override
      public MintContext setGet(final MintContext mintContext) {
        return mintContext;
      }

      @Override
      public MintContext delete(final PublicKey mintPubkey) {
        return null;
      }

      @Override
      public void close() {
      }
    };
    final var globalConfigFile = FileUtils.resolveCompressedAccountPath(tempDir.resolve("fresh"), GLOBAL_CONFIG_KEY);
    final var queued = new ArrayList<List<PublicKey>>();
    final var cache = (GlobalConfigCacheImpl) GlobalConfigCache.initCache(
        globalConfigFile,
        GlamAccounts.MAIN_NET.configProgram(),
        GLOBAL_CONFIG_KEY,
        SolanaAccounts.MAIN_NET,
        agreeing,
        rpcCaller(accountInfo(7L, GlamAccounts.MAIN_NET.configProgram(), globalConfigData)),
        recordingFetcher(queued),
        Duration.ofSeconds(1)
    ).join();

    assertEquals(7L, cache.globalConfigUpdate().slot());
    // nothing was missing, so no mint fetch may be queued -- not even an empty one
    assertEquals(List.of(), queued);
  }

  @Test
  void initCacheRejectsAForeignOwnerOnFetch(@TempDir final Path tempDir) {
    final var globalConfigFile = FileUtils.resolveCompressedAccountPath(tempDir.resolve("fresh"), GLOBAL_CONFIG_KEY);
    final var cacheFuture = GlobalConfigCache.initCache(
        globalConfigFile,
        GlamAccounts.MAIN_NET.configProgram(),
        GLOBAL_CONFIG_KEY,
        SolanaAccounts.MAIN_NET,
        NULL_MINT_CACHE,
        rpcCaller(accountInfo(7L, PublicKey.NONE, globalConfigData)),
        recordingFetcher(new ArrayList<>()),
        Duration.ofSeconds(1)
    );
    final var failure = assertThrows(java.util.concurrent.CompletionException.class, cacheFuture::join);
    assertInstanceOf(IllegalStateException.class, failure.getCause());
    assertTrue(failure.getCause().getMessage().contains("Unexpected GlobalConfig Account"), failure.getCause().getMessage());
  }

  @Test
  void initCacheIgnoresAnEmptyPersistedFile(@TempDir final Path tempDir) {
    final var globalConfigFile = FileUtils.resolveCompressedAccountPath(tempDir, GLOBAL_CONFIG_KEY);
    GlobalConfigCacheImpl.persistGlobalConfig(globalConfigFile, new byte[0]);
    assertTrue(Files.exists(globalConfigFile));

    final var cache = (GlobalConfigCacheImpl) GlobalConfigCache.initCache(
        globalConfigFile,
        GlamAccounts.MAIN_NET.configProgram(),
        GLOBAL_CONFIG_KEY,
        SolanaAccounts.MAIN_NET,
        NULL_MINT_CACHE,
        rpcCaller(accountInfo(7L, GlamAccounts.MAIN_NET.configProgram(), globalConfigData)),
        recordingFetcher(new ArrayList<>()),
        Duration.ofSeconds(1)
    ).join();

    // an empty file is not a config: the cache came from the fetch, not disk
    assertEquals(7L, cache.globalConfigUpdate().slot());
  }

  @Test
  void aFileLoadedConfigSortsMultipleOraclesPerAsset(@TempDir final Path tempDir) {
    final var config = GlobalConfig.read(globalConfigData, 0);
    final var metas = config.assetMetas();
    final var first = metas[0];
    // demote the existing entry in place and append a BETTER priority under a
    // new oracle: only the load-path per-asset sort can serve the appended first
    final var extended = Arrays.copyOf(metas, metas.length + 1);
    extended[0] = withPriority(first, first.priority() + 5);
    extended[metas.length] = new AssetMeta(first.asset(), first.decimals(), PublicKey.NONE,
        first.oracleSource(), first.maxAgeSeconds(), first.priority(), first.padding()
    );
    final var modified = new GlobalConfig(
        config._address(), config.discriminator(), config.admin(), config.feeAuthority(),
        config.referrer(), config.baseFeeBps(), config.flowFeeBps(), extended
    );
    final byte[] data = new byte[modified.l()];
    modified.write(data, 0);

    final var globalConfigFile = FileUtils.resolveCompressedAccountPath(tempDir, GLOBAL_CONFIG_KEY);
    GlobalConfigCacheImpl.persistGlobalConfig(globalConfigFile, data);
    final var cache = (GlobalConfigCacheImpl) GlobalConfigCache.initCache(
        globalConfigFile,
        GlamAccounts.MAIN_NET.configProgram(),
        GLOBAL_CONFIG_KEY,
        SolanaAccounts.MAIN_NET,
        NULL_MINT_CACHE,
        null,
        null,
        Duration.ofSeconds(1)
    ).join();

    final var entries = cache.assetMetaMap.get(first.asset());
    assertEquals(2, entries.length, "both oracles for the asset must be indexed");
    assertEquals(PublicKey.NONE, entries[0].oracle(), "the better priority entry is served first");
    assertEquals(first.priority(), entries[0].priority());
    assertEquals(first.priority() + 5, entries[1].priority());
  }

  private static AccountInfo<byte[]> accountInfo(final long slot, final PublicKey owner, final byte[] data) {
    return new AccountInfo<>(
        GLOBAL_CONFIG_KEY, new Context(slot, null), false, 0, owner,
        BigInteger.ZERO, 0, data
    );
  }

  @Test
  void acceptIgnoresUnchangedOlderAndForeignAccounts(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var before = cache.globalConfigUpdate();
    final var configProgram = GlamAccounts.MAIN_NET.configProgram();

    // identical data: no update regardless of slot
    cache.accept(accountInfo(before.slot() + 10, configProgram, globalConfigData));
    assertSame(before, cache.globalConfigUpdate());

    final var globalConfig = GlobalConfig.read(globalConfigData, 0);
    final var changed = new GlobalConfig(
        globalConfig._address(), globalConfig.discriminator(),
        globalConfig.admin(), globalConfig.feeAuthority(), globalConfig.referrer(),
        globalConfig.baseFeeBps() + 1, globalConfig.flowFeeBps(),
        globalConfig.assetMetas()
    ).write();

    // changed data but not a newer slot: ignored
    cache.accept(accountInfo(before.slot(), configProgram, changed));
    assertSame(before, cache.globalConfigUpdate());
    // changed data from the wrong owner: ignored
    cache.accept(accountInfo(before.slot() + 10, PublicKey.NONE, changed));
    assertSame(before, cache.globalConfigUpdate());
  }

  @Test
  void acceptValidNewerConfigReplacesPersistsAndSignals(@TempDir final Path tempDir) throws Exception {
    final var cache = createCache(tempDir);
    final var before = cache.globalConfigUpdate();
    final var configProgram = GlamAccounts.MAIN_NET.configProgram();

    final var globalConfig = GlobalConfig.read(globalConfigData, 0);
    final byte[] changed = new GlobalConfig(
        globalConfig._address(), globalConfig.discriminator(),
        globalConfig.admin(), globalConfig.feeAuthority(), globalConfig.referrer(),
        globalConfig.baseFeeBps() + 1, globalConfig.flowFeeBps(),
        globalConfig.assetMetas()
    ).write();

    final long newSlot = before.slot() + 42;
    cache.accept(accountInfo(newSlot, configProgram, changed));

    final var after = cache.globalConfigUpdate();
    assertNotSame(before, after);
    assertEquals(newSlot, after.slot());
    assertArrayEquals(changed, after.data());
    // asset metas were re-validated and survive intact
    assertNotNull(cache.topPriorityForMint(SolanaAccounts.MAIN_NET.wrappedSolTokenMint()));

    // a caller holding the previous update sees the new one without waiting
    assertSame(after, cache.awaitNewGlobalConfig(before, 1L));
    assertUnlocked(cache);

    // and the update was persisted for the next restart
    final var persistedPath = FileUtils.resolveCompressedAccountPath(tempDir, GLOBAL_CONFIG_KEY);
    try (final var in = new GZIPInputStream(Files.newInputStream(persistedPath))) {
      assertArrayEquals(changed, in.readAllBytes());
    }
  }

  @Test
  void acceptInvalidNewerConfigInvalidatesTheCache(@TempDir final Path tempDir) throws Exception {
    final var cache = createCache(tempDir);
    final var before = cache.globalConfigUpdate();
    final var configProgram = GlamAccounts.MAIN_NET.configProgram();
    final var called = new AtomicReference<String>();
    cache.subscribe(new TestGlobalConfigListener(called));

    final var globalConfig = GlobalConfig.read(globalConfigData, 0);
    final var metas = Arrays.copyOf(globalConfig.assetMetas(), globalConfig.assetMetas().length);
    final var first = metas[0];
    metas[0] = new AssetMeta(
        first.asset(), first.decimals() + 1, first.oracle(),
        first.oracleSource(), first.maxAgeSeconds(), first.priority(), first.padding()
    );
    final byte[] invalid = new GlobalConfig(
        globalConfig._address(), globalConfig.discriminator(),
        globalConfig.admin(), globalConfig.feeAuthority(), globalConfig.referrer(),
        globalConfig.baseFeeBps(), globalConfig.flowFeeBps(),
        metas
    ).write();

    cache.accept(accountInfo(before.slot() + 1, configProgram, invalid));

    assertNull(cache.globalConfig());
    assertEquals("onDecimalsChange", called.get());
    assertLogged("Inconsistent Asset Decimals Across GlobalConfig");
    // a waiter learns the config is gone rather than blocking
    assertNull(cache.awaitNewGlobalConfig(before, 1L));
    // and so do checked lookups after the invalidation
    assertNull(cache.topPriorityForMintChecked(
        MintContext.createContext(SolanaAccounts.MAIN_NET,
            PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"), 6, 0
        )
    ));
  }

  @Test
  void topPriorityForMintCheckedValidatesDecimals(@TempDir final Path tempDir) {
    final var usdc = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");

    final var matching = createCache(tempDir, new MintCache() {
          @Override
          public MintContext get(final PublicKey mintPubkey) {
            return mintPubkey.equals(usdc)
                ? MintContext.createContext(SolanaAccounts.MAIN_NET, usdc, 6, 0)
                : null;
          }

          @Override
          public MintContext setGet(final MintContext mintContext) {
            return mintContext;
          }

          @Override
          public MintContext delete(final PublicKey mintPubkey) {
            return null;
          }

          @Override
          public void close() {
          }
        }
    );
    final var meta = matching.topPriorityForMintChecked(usdc);
    assertNotNull(meta);
    assertEquals(usdc, meta.asset());
    assertSame(meta, matching.topPriorityForMintChecked(MintContext.createContext(SolanaAccounts.MAIN_NET, usdc, 6, 0)));
    assertNotNull(matching.globalConfig());

    final var mismatched = createCache(tempDir, new MintCache() {
          @Override
          public MintContext get(final PublicKey mintPubkey) {
            return mintPubkey.equals(usdc)
                ? MintContext.createContext(SolanaAccounts.MAIN_NET, usdc, 9, 0)
                : null;
          }

          @Override
          public MintContext setGet(final MintContext mintContext) {
            return mintContext;
          }

          @Override
          public MintContext delete(final PublicKey mintPubkey) {
            return null;
          }

          @Override
          public void close() {
          }
        }
    );
    final var called = new AtomicReference<String>();
    mismatched.subscribe(new TestGlobalConfigListener(called));
    // decimals disagreement is unrecoverable: throw and drop the cached config
    assertThrows(IllegalStateException.class, () -> mismatched.topPriorityForMintChecked(usdc));
    // the write lock is released even on the throwing path
    assertUnlocked(mismatched);
    assertEquals("onInvalidDecimals", called.get());
    assertLogged("GlobalConfig decimals for Asset does not match Mint");
    assertNull(mismatched.globalConfig());
  }


  private static AssetMetaContext[] contexts(final GlobalConfig config, final AssetMeta[] metas) {
    return AssetMetaContext.mapAssetMetas(new GlobalConfig(
        config._address(), config.discriminator(), config.admin(), config.feeAuthority(),
        config.referrer(), config.baseFeeBps(), config.flowFeeBps(), metas
    ));
  }

  private static AssetMeta withPriority(final AssetMeta meta, final int priority) {
    return new AssetMeta(meta.asset(), meta.decimals(), meta.oracle(), meta.oracleSource(),
        meta.maxAgeSeconds(), priority, meta.padding()
    );
  }

  /// The rejection paths are covered above; these are the transitions that must
  /// be *accepted* and reported, where a dropped notification leaves listeners
  /// unaware that an oracle moved.
  @Test
  void anOracleConfigurationChangeIsAcceptedAndReported(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var metas = config.assetMetas();

    // same asset and oracle, different maxAgeSeconds
    final var ageChanged = Arrays.copyOf(metas, metas.length);
    final var first = metas[0];
    ageChanged[0] = new AssetMeta(first.asset(), first.decimals(), first.oracle(), first.oracleSource(),
        first.maxAgeSeconds() + 5, first.priority(), first.padding()
    );

    var called = new AtomicReference<String>();
    var result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, ageChanged), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    assertNotNull(result, "a changed max age is a valid config");
    assertEquals("onOracleConfigurationChange", called.get());
    assertLogged("Oracle Configuration Change");

    // and the same via priority alone
    final var priorityChanged = Arrays.copyOf(metas, metas.length);
    priorityChanged[0] = withPriority(first, first.priority() + 3);
    called = new AtomicReference<>();
    result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, priorityChanged), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    assertNotNull(result);
    assertEquals("onOracleConfigurationChange", called.get());
  }

  @Test
  void anUnchangedConfigIsAcceptedWithoutNotifying(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);

    final var called = new AtomicReference<String>();
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, config.assetMetas()), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    // identical lengths are not a removal, and nothing moved
    assertNotNull(result);
    assertNull(called.get(), () -> "unexpected notification: " + called.get());
    assertEquals(previous.assetMetaContexts().length, result.values().stream().mapToInt(v -> v.length).sum());
  }

  @Test
  void aRotatedOracleEntryIsReported(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var metas = config.assetMetas();

    // a negative priority marks an entry as replaceable: swapping its oracle is
    // a rotation rather than an unexpected change
    final var previousMetas = Arrays.copyOf(metas, metas.length);
    previousMetas[0] = withPriority(metas[0], -1);
    final var rotated = Arrays.copyOf(metas, metas.length);
    rotated[0] = new AssetMeta(metas[0].asset(), metas[0].decimals(), PublicKey.NONE, metas[0].oracleSource(),
        metas[0].maxAgeSeconds(), metas[0].priority(), metas[0].padding()
    );

    final var called = new AtomicReference<String>();
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, contexts(config, previousMetas), cache.assetMetaMap,
        contexts(config, rotated), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    assertNotNull(result);
    assertEquals("onOracleEntryRotation", called.get());
    assertLogged("GlobalConfig Oracle Entry Rotation");
  }

  @Test
  void addedOraclesAreReported(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var metas = config.assetMetas();

    // one fewer entry previously means this config added one
    final var fewer = Arrays.copyOf(metas, metas.length - 1);

    final var called = new AtomicReference<String>();
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, contexts(config, fewer), cache.assetMetaMap,
        contexts(config, metas), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    assertNotNull(result, "adding an oracle is valid");
    assertEquals("onAssetMetaAdded", called.get());
    // the added entry is named in the log, not merely counted: the listener
    // fires from outside this loop, so only the log proves it ran
    assertLogged("New GlobalConfig Oracle Entry");
  }


  @Test
  void anEntryChangedWithoutRotationRightsIsLogged(@TempDir final Path tempDir) {
    // covers the two ERROR logs on the rejection branches driven by existing
    // listener tests: the same-index oracle-source change and the unexpected
    // (non-negative-priority) oracle swap
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var metas = config.assetMetas();
    final var first = metas[0];

    final var swapped = Arrays.copyOf(metas, metas.length);
    swapped[0] = new AssetMeta(first.asset(), first.decimals(), PublicKey.NONE,
        first.oracleSource(), first.maxAgeSeconds(), first.priority(), first.padding()
    );
    final var called = new AtomicReference<String>();
    assertNotNull(previous);
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, swapped), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    // deliberately flag-and-continue: the swap is logged and notified but the
    // config is still accepted (see the TODO on this branch in createMapChecked)
    assertNotNull(result, "an unexpected oracle swap is currently allowed");
    assertEquals("onUnexpectedOracleChange", called.get());
    assertLogged("Unexpected GlobalConfig Oracle Change");
  }

  @Test
  void aDeprecatedOracleSourceIsRejected(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var metas = Arrays.copyOf(config.assetMetas(), config.assetMetas().length);
    final var first = metas[0];
    // Pyth (push) is retired; only the pull/lazer family is valid
    metas[0] = new AssetMeta(first.asset(), first.decimals(), first.oracle(),
        OracleSource.Pyth, first.maxAgeSeconds(), first.priority(), first.padding()
    );

    final var called = new AtomicReference<String>();
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, contexts(config, metas), cache.assetMetaMap,
        contexts(config, metas), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    assertNull(result);
    assertEquals("onInvalidOracleSource", called.get());
    assertLogged("GlobalConfig Invalid OracleSource");
  }

  @Test
  void aMintCacheDecimalsMismatchRejectsTheConfig(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var usdc = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");

    final var wrongDecimals = new MintCache() {
      @Override
      public MintContext get(final PublicKey mintPubkey) {
        return mintPubkey.equals(usdc)
            ? MintContext.createContext(SolanaAccounts.MAIN_NET, usdc, 9, 0)
            : null;
      }

      @Override
      public MintContext setGet(final MintContext mintContext) {
        return mintContext;
      }

      @Override
      public MintContext delete(final PublicKey mintPubkey) {
        return null;
      }

      @Override
      public void close() {
      }
    };
    final var called = new AtomicReference<String>();
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, config.assetMetas()), wrongDecimals, Set.of(new TestGlobalConfigListener(called))
    );
    assertNull(result, "the on-chain mint is the authority on decimals");
    assertEquals("onDecimalsDoNotMatchMint", called.get());
    assertLogged("GlobalConfig Asset Decimals Does Not Match Mint");
  }

  @Test
  void aCrossConfigDecimalsChangeIsRejected(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var metas = Arrays.copyOf(config.assetMetas(), config.assetMetas().length);
    final var first = metas[0];
    // the oracle changes too, so the per-index comparison flags-and-continues
    // and the cross-config decimals sweep is what must reject the config
    metas[0] = new AssetMeta(first.asset(), first.decimals() + 1, PublicKey.NONE,
        first.oracleSource(), first.maxAgeSeconds(), first.priority(), first.padding()
    );

    final var called = new AtomicReference<String>();
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, metas), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    assertNull(result, "an asset cannot change decimals between configs");
    assertEquals("onInconsistentDecimals", called.get());
    assertLogged("Inconsistent Asset Decimals Across GlobalConfig's");
  }

  @Test
  void anOracleReusedWithADifferentSourceIsRejected(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var metas = Arrays.copyOf(config.assetMetas(), config.assetMetas().length);
    final var first = metas[0];
    int j = -1;
    for (int i = 1; i < metas.length; ++i) {
      if (metas[i].oracleSource() != first.oracleSource()) {
        j = i;
        break;
      }
    }
    assertTrue(j > 0, "fixture holds multiple oracle sources");
    final var reused = metas[j];
    metas[j] = new AssetMeta(reused.asset(), reused.decimals(), first.oracle(),
        reused.oracleSource(), reused.maxAgeSeconds(), reused.priority(), reused.padding()
    );

    final var called = new AtomicReference<String>();
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, metas), NULL_MINT_CACHE, Set.of(new TestGlobalConfigListener(called))
    );
    assertNull(result, "one oracle account cannot serve two sources");
    assertEquals("onInconsistentOracleSourceAcrossConfigs", called.get());
    assertLogged("Inconsistent OracleSource Across Configs");
  }

  @Test
  void aMatchingMintCacheAcceptsTheConfig(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var usdc = PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");

    final var agreeing = new MintCache() {
      @Override
      public MintContext get(final PublicKey mintPubkey) {
        return mintPubkey.equals(usdc)
            ? MintContext.createContext(SolanaAccounts.MAIN_NET, usdc, 6, 0)
            : null;
      }

      @Override
      public MintContext setGet(final MintContext mintContext) {
        return mintContext;
      }

      @Override
      public MintContext delete(final PublicKey mintPubkey) {
        return null;
      }

      @Override
      public void close() {
      }
    };
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, config.assetMetas()), agreeing, Set.of()
    );
    assertNotNull(result, "an agreeing mint cache is not a rejection");
    assertNotNull(result.get(usdc));
  }

  @Test
  void multipleOraclesForOneAssetServeTopPriorityFirst(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var previous = cache.globalConfigUpdate();
    final var config = GlobalConfig.read(previous.data(), 0);
    final var metas = config.assetMetas();
    final var first = metas[0];

    // demote the existing entry in place and append a BETTER priority under a
    // new oracle: only the final per-asset sort can serve the appended entry first
    final var extended = Arrays.copyOf(metas, metas.length + 1);
    extended[0] = withPriority(first, first.priority() + 5);
    extended[metas.length] = new AssetMeta(first.asset(), first.decimals(), PublicKey.NONE,
        first.oracleSource(), first.maxAgeSeconds(), first.priority(), first.padding()
    );

    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L, previous.assetMetaContexts(), cache.assetMetaMap,
        contexts(config, extended), NULL_MINT_CACHE, Set.of()
    );
    assertNotNull(result);
    final var entries = result.get(first.asset());
    assertEquals(2, entries.length);
    assertEquals(PublicKey.NONE, entries[0].oracle(), "the appended better priority entry is served first");
    assertEquals(first.priority(), entries[0].priority());
    assertEquals(first.priority() + 5, entries[1].priority());
    assertEquals(first.oracle(), entries[1].oracle());
  }

  private record TestGlobalConfigListener(AtomicReference<String> called) implements GlobalConfigListener {

    @Override
    public void onInvalidDecimals(final PublicKey mint,
                                  final int mintDecimals,
                                  final AssetMetaContext assetMeta,
                                  final GlobalConfigUpdate globalConfigUpdate) {
      called.set("onInvalidDecimals");
    }

    @Override
    public void onAssetMetaRemoved(final long slot,
                                   final AssetMetaContext[] previous,
                                   final AssetMetaContext[] latest) {
      called.set("onAssetMetaRemoved");
    }

    @Override
    public void onAssetMetaAdded(final long slot,
                                 final AssetMetaContext[] previous,
                                 final AssetMetaContext[] latest) {
      called.set("onAssetMetaAdded");
    }

    @Override
    public void onDecimalsChange(final long slot,
                                 final AssetMetaContext previous,
                                 final AssetMetaContext latest) {
      called.set("onDecimalsChange");
    }

    @Override
    public void onInconsistentOracleSource(final long slot,
                                           final AssetMetaContext previous,
                                           final AssetMetaContext latest) {
      called.set("onInconsistentOracleSource");
    }

    @Override
    public void onOracleConfigurationChange(final long slot,
                                            final AssetMetaContext previous,
                                            final AssetMetaContext latest, final AssetMetaContext[] assetMetaContexts) {
      called.set("onOracleConfigurationChange");
    }

    @Override
    public void onOracleEntryRotation(final long slot,
                                      final AssetMetaContext previous,
                                      final AssetMetaContext latest, final AssetMetaContext[] assetMetaContexts) {
      called.set("onOracleEntryRotation");
    }

    @Override
    public void onUnexpectedOracleChange(final long slot,
                                         final AssetMetaContext previous,
                                         final AssetMetaContext latest) {
      called.set("onUnexpectedOracleChange");
    }

    @Override
    public void onInconsistentDecimals(final long slot,
                                       final AssetMetaContext previous,
                                       final AssetMetaContext latest) {
      called.set("onInconsistentDecimals");
    }

    @Override
    public void onDecimalsDoNotMatchMint(final long slot,
                                         final MintContext mintContext,
                                         final AssetMetaContext assetMeta) {
      called.set("onDecimalsDoNotMatchMint");
    }

    @Override
    public void onInvalidOracleSource(final long slot,
                                      final AssetMetaContext assetMeta) {
      called.set("onInvalidOracleSource");
    }

    @Override
    public void onInconsistentOracleSourceAcrossConfigs(final long slot,
                                                        final OracleSource previousOracleSource,
                                                        final AssetMetaContext assetMeta) {
      called.set("onInconsistentOracleSourceAcrossConfigs");
    }

    @Override
    public void onInconsistentOracleSourceWithinConfig(final long slot,
                                                       final AssetMetaContext a,
                                                       final AssetMetaContext b) {
      called.set("onInconsistentOracleSourceWithinConfig");
    }

    @Override
    public void onDuplicateOracleForAsset(final long slot,
                                          final AssetMetaContext a,
                                          final AssetMetaContext b) {
      called.set("onDuplicateOracleForAsset");
    }

    @Override
    public void onInconsistentDecimalsWithinConfig(final long slot,
                                                   final AssetMetaContext a,
                                                   final AssetMetaContext b) {
      called.set("onInconsistentDecimalsWithinConfig");
    }
  }
}
