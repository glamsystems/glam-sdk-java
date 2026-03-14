package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;

import java.util.Set;

public interface DriftListener {

  PublicKey key();

  default void onCriticalPerpMarketChange(final Set<DriftMarketChange> changes,
                                          final DriftPerpMarketContext previous,
                                          final DriftPerpMarketContext latest) {

  }

  default void onCriticalSpotMarketChange(final Set<DriftMarketChange> changes,
                                          final DriftSpotMarketContext previous,
                                          final DriftSpotMarketContext latest) {

  }

  default void onDriftUserChange(final long slot, final PublicKey userKey, final byte[] userData) {

  }
}
