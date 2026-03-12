package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.util.DecimalInteger;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

public interface AssetMetaContext extends Comparable<AssetMetaContext>, DecimalInteger {

  static AssetMetaContext create(final int index, final AssetMeta assetMeta) {
    final var asset = assetMeta.asset();
    final var oracle = assetMeta.oracle();
    return new AssetMetaContextRecord(
        index,
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
    final var assetMetas = globalConfig.assetMetas();
    final var assetMetaContext = new AssetMetaContext[assetMetas.length];
    for (int i = 0; i < assetMetas.length; i++) {
      assetMetaContext[i] = AssetMetaContext.create(i, assetMetas[i]);
    }
    return assetMetaContext;
  }

  int index();

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
