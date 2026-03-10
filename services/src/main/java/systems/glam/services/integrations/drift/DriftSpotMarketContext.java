package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.drift.gen.types.SpotMarket;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public record DriftSpotMarketContext(long slot,
                                     AccountMeta readMarket,
                                     AccountMeta readOracle,
                                     SpotMarket spotMarket) implements DriftMarketContext {

  @Override
  public int marketIndex() {
    return spotMarket.marketIndex();
  }

  @Override
  public int poolId() {
    return spotMarket.poolId();
  }

  static DriftSpotMarketContext createContext(final long slot, final byte[] data) {
    final var spotMarket = SpotMarket.read(data, 0);
    return new DriftSpotMarketContext(
        slot,
        AccountMeta.createRead(spotMarket.pubkey()),
        AccountMeta.createRead(spotMarket.oracle()),
        spotMarket
    );
  }

  static DriftSpotMarketContext createContext(final AccountInfo<byte[]> accountInfo) {
    final long slot = accountInfo.context().slot();
    return createContext(slot, accountInfo.data());
  }

  DriftSpotMarketContext createContextFrom(final AccountInfo<byte[]> accountInfo) {
    final long slot = accountInfo.context().slot();
    if (Long.compareUnsigned(this.slot, slot) >= 0) {
      return null;
    }
    final byte[] data = accountInfo.data();
    final var spotMarket = SpotMarket.read(accountInfo);

    if (Arrays.equals(
        data, SpotMarket.ORACLE_OFFSET, SpotMarket.ORACLE_OFFSET + PublicKey.PUBLIC_KEY_LENGTH,
        this.readOracle.publicKey().toByteArray(), 0, PublicKey.PUBLIC_KEY_LENGTH
    )) {
      return new DriftSpotMarketContext(
          slot,
          this.readMarket,
          this.readOracle,
          spotMarket
      );
    } else {
      return new DriftSpotMarketContext(
          slot,
          this.readMarket,
          AccountMeta.createRead(spotMarket.oracle()),
          spotMarket
      );
    }
  }

  public Set<DriftMarketChange> criticalChanges(final SpotMarket previousPerpMarket) {
    final var spotMarket = this.spotMarket;
    Set<DriftMarketChange> changes = null;
    if (!spotMarket.oracle().equals(previousPerpMarket.oracle())) {
      changes = EnumSet.of(DriftMarketChange.ORACLE);
    }
    if (!spotMarket.status().equals(previousPerpMarket.status())) {
      if (changes == null) {
        changes = EnumSet.of(DriftMarketChange.STATUS);
      } else {
        changes.add(DriftMarketChange.STATUS);
      }
    }
    return changes;
  }
}
