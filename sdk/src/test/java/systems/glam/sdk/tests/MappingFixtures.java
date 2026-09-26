package systems.glam.sdk.tests;

import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// The recorded accounts under `src/test/resources/accounts` and the keys the mapping tests name in them.
public final class MappingFixtures {

  /// The mainnet GlobalConfig, see `accounts/glam/README.md`.
  public static final PublicKey GLOBAL_CONFIG_KEY = fromBase58Encoded("6avract7PxKqoq6hdmpAgGKgJWoJWdiXPPzzFZ62Hck6");
  public static final PublicKey USDC = fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
  public static final PublicKey USDC_ORACLE = fromBase58Encoded("9VCioxmni2gDLv11qufWzT3RDERhQE4iY5Gf7NTfYyAV");
  public static final PublicKey WSOL = fromBase58Encoded("So11111111111111111111111111111111111111112");
  public static final PublicKey WSOL_ORACLE = fromBase58Encoded("3m6i4RFWEDw2Ft4tFHPJtYgmpPe21k56M3FHeWYrgGBz");
  public static final PublicKey MSOL = fromBase58Encoded("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So");
  public static final PublicKey MSOL_ORACLE = fromBase58Encoded("FY2JMi1vYz1uayVT2GJ96ysZgpagjhdPRG2upNPtSZsC");
  public static final PublicKey META = fromBase58Encoded("METADDFL6wWMWEoKTFJwcThTbUmtarRJZjRpzUvkxhr");
  public static final PublicKey META_ORACLE = fromBase58Encoded("DwYF1yveo8XTF1oqfsqykj332rjSxAd7bR6Gu6i4iUET");
  public static final PublicKey XSTOCK = fromBase58Encoded("XsCPL9dNWBMvFtTmwcCA5v3xWPSMEBCszbQdiLLq6aN");
  public static final PublicKey XSTOCK_ORACLE = fromBase58Encoded("18BUkzs6x4C3mK1W1NPHxdZZvnvFaQ5QXkX5Zx5ggBz");

  /// The mainnet Loopscale strategy, see `accounts/loopscale/README.md`.
  public static final PublicKey STRATEGY_KEY = fromBase58Encoded("13RqwWva17oKpqUwyvrRnaXSth2uzD61nWSYA7rXefEv");
  public static final PublicKey STRATEGY_MARKET = fromBase58Encoded("3iT9TYXjv3FFbHu4ba5zujnesRYgzyy8YPq9kQNRZ25p");
  public static final PublicKey STRATEGY_PRINCIPAL_MINT = fromBase58Encoded("JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN");

  private MappingFixtures() {
  }

  public static byte[] readGzip(final String resource) {
    try (final var in = new GZIPInputStream(
        Objects.requireNonNull(MappingFixtures.class.getResourceAsStream("/" + resource), resource))) {
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static byte[] read(final String resource) {
    try (final var in = Objects.requireNonNull(MappingFixtures.class.getResourceAsStream("/" + resource), resource)) {
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static GlobalConfig globalConfig() {
    return GlobalConfig.readChecked(GLOBAL_CONFIG_KEY, readGzip("accounts/glam/" + GLOBAL_CONFIG_KEY + ".dat.gz"));
  }

  public static byte[] strategy() {
    return readGzip("accounts/loopscale/" + STRATEGY_KEY + ".dat.gz");
  }

  /// A key with the distinguishable byte `id` first and a fixed non-zero tail.
  public static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    bytes[31] = 7;
    return PublicKey.createPubKey(bytes);
  }

  public static AssetMeta meta(final PublicKey asset, final PublicKey oracle, final OracleSource source, final int priority) {
    return new AssetMeta(asset, 9, oracle, source, 30, priority, new byte[AssetMeta.PADDING_LEN]);
  }
}
