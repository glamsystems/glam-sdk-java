package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;

import java.util.Set;

public interface DriftListener {

  PublicKey key();

  void onCriticalPerpMarketChange(final Set<DriftMarketChange> changes,
                                  final DriftPerpMarketContext previous,
                                  final DriftPerpMarketContext latest);

  void onCriticalSpotMarketChange(final Set<DriftMarketChange> changes,
                                  final DriftSpotMarketContext previous,
                                  final DriftSpotMarketContext latest);
}
