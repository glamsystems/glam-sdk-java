package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.oracles.scope.ScopeFeedContext;

import java.util.Set;

public interface KaminoListener {

  PublicKey key();

  default void onReserveChange(final ReserveContext previous,
                               final ReserveContext reserveContext,
                               final Set<ReserveChange> changes) {

  }

  default void onMappingChange(final ScopeFeedContext scopeFeedContext,
                               final MappingsContext witness,
                               final MappingsContext mappingContext) {

  }

  default void onKaminoVaultChange(final KaminoVaultContext vaultContext) {

  }

  default void onScopeAccountDeleted(final PublicKey deletedAccount, final ScopeFeedContext scopeFeedContext) {

  }

  default void onNewScopeConfiguration(final PublicKey newAccount, final ScopeFeedContext scopeFeedContext) {

  }

  default void onScopeConfigurationChange(final ScopeFeedContext witness, final ScopeFeedContext latest) {

  }
}
