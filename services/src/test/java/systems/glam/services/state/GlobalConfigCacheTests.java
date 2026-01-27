package systems.glam.services.state;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.services.mints.MintCache;
import systems.glam.services.mints.MintContext;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

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

  @BeforeAll
  static void beforeAll() throws IOException {
    Logger.getLogger(GlobalConfigCache.class.getName()).setLevel(Level.OFF);
    globalConfigData = ResourceUtil.readResource("accounts/glam/global/" + GLOBAL_CONFIG_KEY + ".zip");
  }

  private static GlobalConfigCacheImpl createCache(final Path tempDir) {
    final var globalConfigFile = tempDir.resolve("global_config.bin");
    try {
      Files.write(globalConfigFile, globalConfigData);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var cacheFuture = GlobalConfigCache.initCache(
        globalConfigFile,
        GlamAccounts.MAIN_NET.configProgram(),
        GLOBAL_CONFIG_KEY,
        SolanaAccounts.MAIN_NET,
        NULL_MINT_CACHE,
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

    final var globalConfig = cache.globalConfig();
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

    final int lastIndex = globalConfig.assetMetas().length - 1;
    final var lastAsset = cache.getByIndex(lastIndex);
    assertNotNull(lastAsset);
    assertEquals(PublicKey.fromBase58Encoded("XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB"), lastAsset.asset());
    assertEquals(8, lastAsset.decimals());
    assertEquals(PublicKey.fromBase58Encoded("18MAcEvgzQoAktRTsNhPo4Cb6Gtp7L2X5hAEeiYtD13"), lastAsset.oracle());
    assertEquals(OracleSource.ChainlinkRWA, lastAsset.oracleSource());
    assertEquals(30, lastAsset.maxAgeSeconds());
    assertEquals(0, lastAsset.priority());

    // Verify out of bounds index returns null
    assertNull(cache.getByIndex(lastIndex + 1));
    assertNull(cache.getByIndex(Integer.MAX_VALUE));
    assertNull(cache.topPriorityForMint(PublicKey.NONE));
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenOracleRemoved(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfig = cache.globalConfig();
    final var previousAssetMetas = previousGlobalConfig.assetMetas();
    assertTrue(previousAssetMetas.length > 1);

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
    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfig,
        cache.assetMetaMap,
        invalidGlobalConfig,
        NULL_MINT_CACHE
    );
    assertNull(result);
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenEntryChangedWithoutNegativePriority(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfig = cache.globalConfig();
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

    assertNull(GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfig,
        cache.assetMetaMap,
        oracleChangedConfig,
        NULL_MINT_CACHE
    ));

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

    assertNull(GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfig,
        cache.assetMetaMap,
        assetChangedConfig,
        NULL_MINT_CACHE
    ));
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenSameOracleDifferentSourceWithinConfig(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfig = cache.globalConfig();
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

    final var result = GlobalConfigCacheImpl.createMapChecked(1L, invalidConfig, NULL_MINT_CACHE);
    assertNull(result);
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenOracleSourceChangedAcrossConfigs(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfig = cache.globalConfig();
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

    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfig,
        cache.assetMetaMap,
        newGlobalConfig,
        NULL_MINT_CACHE
    );
    assertNull(result);
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenDuplicateOracleForAsset(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfig = cache.globalConfig();
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

    final var result = GlobalConfigCacheImpl.createMapChecked(1L, invalidConfig, NULL_MINT_CACHE);
    assertNull(result);
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenInconsistentDecimalsForAsset(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfig = cache.globalConfig();
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

    final var result = GlobalConfigCacheImpl.createMapChecked(1L, invalidConfig, NULL_MINT_CACHE);
    assertNull(result);
  }

  @Test
  void testCreateMapCheckedReturnsNullWhenDecimalsChangedForExistingEntry(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    final var previousGlobalConfig = cache.globalConfig();
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

    final var result = GlobalConfigCacheImpl.createMapChecked(
        1L,
        previousGlobalConfig,
        cache.assetMetaMap,
        invalidConfig,
        NULL_MINT_CACHE
    );
    assertNull(result);
  }
}
