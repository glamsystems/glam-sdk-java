package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.services.mints.AssetMetaContext;
import systems.glam.services.mints.MintContext;

public interface GlobalConfigListener {

  default void onInvalidDecimals(final PublicKey mint,
                                 final int mintDecimals,
                                 final AssetMetaContext assetMeta,
                                 final GlobalConfigUpdate globalConfigUpdate) {
  }

  default void onAssetMetaRemoved(final long slot,
                                  final AssetMetaContext[] previous,
                                  final AssetMetaContext[] latest) {
  }

  default void onAssetMetaAdded(final long slot,
                                final AssetMetaContext[] previous,
                                final AssetMetaContext[] latest) {
  }

  default void onDecimalsChange(final long slot,
                                final AssetMetaContext previous,
                                final AssetMetaContext latest) {
  }

  default void onInconsistentOracleSource(final long slot,
                                          final AssetMetaContext previous,
                                          final AssetMetaContext latest) {
  }

  default void onOracleConfigurationChange(final long slot,
                                           final AssetMetaContext previous,
                                           final AssetMetaContext latest, final AssetMetaContext[] assetMetaContexts) {
  }

  default void onOracleEntryRotation(final long slot,
                                     final AssetMetaContext previous,
                                     final AssetMetaContext latest, final AssetMetaContext[] assetMetaContexts) {
  }

  default void onUnexpectedOracleChange(final long slot,
                                        final AssetMetaContext previous,
                                        final AssetMetaContext latest) {
  }

  default void onInconsistentDecimals(final long slot,
                                      final AssetMetaContext previous,
                                      final AssetMetaContext latest) {
  }

  default void onDecimalsDoNotMatchMint(final long slot,
                                        final MintContext mintContext,
                                        final AssetMetaContext assetMeta) {
  }

  default void onInvalidOracleSource(final long slot,
                                     final AssetMetaContext assetMeta) {
  }

  default void onInconsistentOracleSourceAcrossConfigs(final long slot,
                                                       final OracleSource previousOracleSource,
                                                       final AssetMetaContext assetMeta) {
  }

  default void onInconsistentOracleSourceWithinConfig(final long slot,
                                                      final AssetMetaContext a,
                                                      final AssetMetaContext b) {
  }

  default void onDuplicateOracleForAsset(final long slot,
                                         final AssetMetaContext a,
                                         final AssetMetaContext b) {
  }

  default void onInconsistentDecimalsWithinConfig(final long slot,
                                                  final AssetMetaContext a,
                                                  final AssetMetaContext b) {
  }
}
