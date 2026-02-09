package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.util.DecimalInteger;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

import java.util.Arrays;

public interface AssetMetaContext extends Comparable<AssetMetaContext>, DecimalInteger {

  static AssetMetaContext create(final AssetMeta assetMeta) {
    final var asset = assetMeta.asset();
    final var oracle = assetMeta.oracle();
    return new AssetMetaContextRecord(
        assetMeta.asset(),
        AccountMeta.createRead(asset),
        assetMeta.decimals(),
        assetMeta.oracle(),
        AccountMeta.createRead(oracle),
        assetMeta.oracleSource(),
        assetMeta.maxAgeSeconds(),
        assetMeta.priority()
    );
  }

  static AssetMetaContext[] mapAssetMetas(final GlobalConfig globalConfig) {
    return Arrays.stream(globalConfig.assetMetas()).map(AssetMetaContext::create).toArray(AssetMetaContext[]::new);
  }

  PublicKey asset();

  AccountMeta readAssetMint();

  int decimals();

  PublicKey oracle();

  AccountMeta readOracle();

  OracleSource oracleSource();

  int maxAgeSeconds();

  int priority();

  String toJson();
}
