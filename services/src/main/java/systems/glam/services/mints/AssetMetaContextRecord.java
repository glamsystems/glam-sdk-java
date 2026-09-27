package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.sdk.mapping.RegisteredOracles;

public record AssetMetaContextRecord(int index,
                                     PublicKey asset,
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
    final int byPriority;
    if (this.priority < 0) {
      byPriority = oPriority < 0 ? Integer.compare(-this.priority, -oPriority) : 1;
    } else if (oPriority < 0) {
      byPriority = -1;
    } else {
      byPriority = Integer.compare(this.priority, oPriority);
    }
    // Among equal priorities GLAM's oracle-selection rule decides by source: the Kamino reserve
    // first, then the program's declaration order. Equal priority and source keep the stored order.
    return byPriority != 0
        ? byPriority
        : Integer.compare(RegisteredOracles.sourceRank(oracleSource), RegisteredOracles.sourceRank(o.oracleSource()));
  }

  @Override
  public String toJson() {
    return String.format("""
            {
             "index": %d,
             "asset": "%s",
             "decimals": %d,
             "oracle": "%s",
             "oracleSource": "%s",
             "priority": %d,
             "maxAgeSeconds": %d
            }""",
        index,
        asset.toBase58(),
        decimals,
        oracle.toBase58(),
        oracleSource,
        priority,
        maxAgeSeconds
    );
  }
}
