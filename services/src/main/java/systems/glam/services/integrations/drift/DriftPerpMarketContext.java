package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.drift.gen.types.PerpMarket;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public record DriftPerpMarketContext(long slot,
                                     AccountMeta readMarket,
                                     AccountMeta readOracle,
                                     PerpMarket perpMarket) implements DriftMarketContext {

  @Override
  public int marketIndex() {
    return perpMarket.marketIndex();
  }

  @Override
  public int poolId() {
    return perpMarket.poolId();
  }

  static DriftPerpMarketContext createContext(final long slot, final byte[] data) {
    final var perpMarket = PerpMarket.read(data, 0);
    return new DriftPerpMarketContext(
        slot,
        AccountMeta.createRead(perpMarket.pubkey()),
        AccountMeta.createRead(perpMarket.amm().oracle()),
        perpMarket
    );
  }

  static DriftPerpMarketContext createContext(final AccountInfo<byte[]> accountInfo) {
    final long slot = accountInfo.context().slot();
    return createContext(slot, accountInfo.data());
  }

  DriftPerpMarketContext createContextFrom(final AccountInfo<byte[]> accountInfo) {
    final long slot = accountInfo.context().slot();
    if (Long.compareUnsigned(this.slot, slot) >= 0) {
      return null;
    }
    final byte[] data = accountInfo.data();
    final var perpMarket = PerpMarket.read(accountInfo);

    if (Arrays.equals(
        data, PerpMarket.AMM_OFFSET, PerpMarket.AMM_OFFSET + PublicKey.PUBLIC_KEY_LENGTH,
        this.readOracle.publicKey().toByteArray(), 0, PublicKey.PUBLIC_KEY_LENGTH
    )) {
      return new DriftPerpMarketContext(
          slot,
          this.readMarket,
          this.readOracle,
          perpMarket
      );
    } else {
      return new DriftPerpMarketContext(
          slot,
          this.readMarket,
          AccountMeta.createRead(perpMarket.amm().oracle()),
          perpMarket
      );
    }
  }

  public Set<DriftMarketChange> criticalChanges(final PerpMarket previousPerpMarket) {
    final var perpMarket = this.perpMarket;
    Set<DriftMarketChange> changes = null;
    if (!perpMarket.amm().oracle().equals(previousPerpMarket.amm().oracle())) {
      changes = EnumSet.of(DriftMarketChange.ORACLE);
    }
    if (!perpMarket.status().equals(previousPerpMarket.status())) {
      if (changes == null) {
        changes = EnumSet.of(DriftMarketChange.STATUS);
      } else {
        changes.add(DriftMarketChange.STATUS);
      }
    }
    return changes;
  }
}
