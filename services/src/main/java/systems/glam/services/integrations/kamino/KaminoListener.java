package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.oracles.scope.ScopeFeedContext;

import java.util.Set;

public interface KaminoListener {

  PublicKey key();

  /// Called for every observed write to a Reserve account, before any change filtering.
  ///
  /// [#onReserveChange(ReserveContext, ReserveContext, Set)] only fires for the configuration
  /// fields tracked by [ReserveChange]; a Reserve that is refreshed with a new price, but is
  /// otherwise unchanged, never reaches it. Listeners which need to observe raw account
  /// activity, such as oracle price update frequency, must use this hook and decode the
  /// fields they care about from the account data themselves.
  ///
  /// Note that the same slot may be delivered more than once, e.g. when a websocket
  /// notification races the polled refresh, so implementations must be idempotent. The polled
  /// paths re-deliver every Reserve each cycle whether or not it was written, so a listener
  /// counting deliveries is counting observations rather than on-chain writes; the account's
  /// own `last_update.slot` is what dates the write it carries.
  ///
  /// Unlike [#onReserveChange(ReserveContext, ReserveContext, Set)] and
  /// [#onNewReserve(ReserveContext)], which are serialized behind the cache's write lock, this
  /// hook is delivered without a lock and can be entered concurrently for the same Reserve —
  /// the websocket, the account fetcher and the poll loop all reach it on their own threads.
  /// Being idempotent is not enough here; an implementation which accumulates has to be thread
  /// safe.
  ///
  /// The `byte[]` is not retained or mutated by the cache, so a listener may hold it.
  ///
  /// A listener which throws is logged and skipped, and does not stop delivery to the others.
  default void onReserveUpdate(final AccountInfo<byte[]> accountInfo) {

  }

  /// Called for every observed write to a Scope `OraclePrices` account.
  ///
  /// This is the account every Kamino reserve actually prices through: a reserve's stored price
  /// is a copy taken whenever somebody last called `refresh_reserve`, while this is what such a
  /// call would pull right now. A listener measuring how fresh a price is obtainable, rather
  /// than how recently a reserve happened to be touched, wants this hook.
  ///
  /// The cache does not subscribe to these accounts — they are far larger than the mappings and
  /// change constantly — so this only fires when one reaches the cache another way, which today
  /// means an `AccountFetcher` batch carrying it. A deployment which never fetches one never
  /// sees this callback, and a listener must treat that as no observation rather than as a
  /// stale price.
  ///
  /// The same threading, idempotency and retention notes as
  /// [#onReserveUpdate(AccountInfo)] apply.
  default void onOraclePricesUpdate(final AccountInfo<byte[]> accountInfo) {

  }

  default void onReserveChange(final ReserveContext previous,
                               final ReserveContext reserveContext,
                               final Set<ReserveChange> changes) {

  }

  default void onNewReserve(final ReserveContext reserveContext) {

  }

  default void onMappingChange(final ScopeFeedContext scopeFeedContext,
                               final MappingsContext witness,
                               final MappingsContext mappingContext) {

  }

  default void onNewKaminoVault(final KaminoVaultContext vaultContext) {

  }

  default void onKaminoVaultChange(final KaminoVaultContext previous, final KaminoVaultContext vaultContext) {

  }

  default void onScopeAccountDeleted(final PublicKey deletedAccount, final ScopeFeedContext scopeFeedContext) {

  }

  default void onNewScopeConfiguration(final PublicKey newAccount, final ScopeFeedContext scopeFeedContext) {

  }

  default void onScopeConfigurationChange(final ScopeFeedContext witness, final ScopeFeedContext latest) {

  }
}
