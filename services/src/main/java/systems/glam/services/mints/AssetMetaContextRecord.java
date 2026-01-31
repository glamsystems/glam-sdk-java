package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

public record AssetMetaContextRecord(PublicKey asset,
                                     AccountMeta readAssetMint,
                                     int decimals,
                                     PublicKey oracle,
                                     AccountMeta readOracle,
                                     OracleSource oracleSource,
                                     int maxAgeSeconds,
                                     int priority) implements AssetMetaContext {

  @Override
  public int compareTo(final AssetMetaContext o) {
    final int oPriority = o.priority();
    if (this.priority < 0) {
      if (oPriority < 0) {
        return Integer.compare(-this.priority, -oPriority);
      } else {
        return 1;
      }
    } else if (oPriority < 0) {
      return -1;
    } else {
      return Integer.compare(this.priority, oPriority);
    }
  }

  @Override
  public String toJson() {
    return String.format("""
            {
             "asset": "%s",
             "decimals": %d,
             "oracle": "%s",
             "oracleSource": "%s",
             "priority": %d,
             "maxAgeSeconds": %d
            }""",
        asset.toBase58(),
        decimals,
        oracle.toBase58(),
        oracleSource,
        priority,
        maxAgeSeconds
    );
  }
}
