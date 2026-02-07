package systems.glam.sdk.idl.programs.glam.config.gen;

import software.sava.idl.clients.core.gen.ProgramError;

public sealed interface GlamConfigError extends ProgramError permits
    GlamConfigError.InvalidAuthority,
    GlamConfigError.InvalidAssetMeta,
    GlamConfigError.AssetMetaAlreadyExists,
    GlamConfigError.InvalidParameters,
    GlamConfigError.InvalidOracleSource,
    GlamConfigError.InvalidGlobalConfig {

  static GlamConfigError getInstance(final int errorCode) {
    return switch (errorCode) {
      case 6000 -> InvalidAuthority.INSTANCE;
      case 6001 -> InvalidAssetMeta.INSTANCE;
      case 6002 -> AssetMetaAlreadyExists.INSTANCE;
      case 6003 -> InvalidParameters.INSTANCE;
      case 6004 -> InvalidOracleSource.INSTANCE;
      case 6005 -> InvalidGlobalConfig.INSTANCE;
      default -> null;
    };
  }

  record InvalidAuthority(int code, String msg) implements GlamConfigError {

    public static final InvalidAuthority INSTANCE = new InvalidAuthority(
        6000, "Invalid authority"
    );
  }

  record InvalidAssetMeta(int code, String msg) implements GlamConfigError {

    public static final InvalidAssetMeta INSTANCE = new InvalidAssetMeta(
        6001, "Invalid asset meta"
    );
  }

  record AssetMetaAlreadyExists(int code, String msg) implements GlamConfigError {

    public static final AssetMetaAlreadyExists INSTANCE = new AssetMetaAlreadyExists(
        6002, "Asset meta already exists"
    );
  }

  record InvalidParameters(int code, String msg) implements GlamConfigError {

    public static final InvalidParameters INSTANCE = new InvalidParameters(
        6003, "Invalid parameters"
    );
  }

  record InvalidOracleSource(int code, String msg) implements GlamConfigError {

    public static final InvalidOracleSource INSTANCE = new InvalidOracleSource(
        6004, "Invalid oracle source"
    );
  }

  record InvalidGlobalConfig(int code, String msg) implements GlamConfigError {

    public static final InvalidGlobalConfig INSTANCE = new InvalidGlobalConfig(
        6005, "Invalid global config"
    );
  }
}
