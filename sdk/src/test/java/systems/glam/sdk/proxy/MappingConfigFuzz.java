package systems.glam.sdk.proxy;

import software.sava.core.accounts.meta.AccountMeta;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.ix.proxy.IndexedAccountMeta;
import systems.glam.ix.proxy.ProgramMapConfig;

import java.util.HashMap;

/// Jazzer entry point for the ix-mapper mapping-config JSON parser — the exact
/// path `GlamVaultAccounts.loadMappingConfigs` drives over every `*.json` file
/// in the untracked `glam/` download (`downloadMappings.sh`). Those files are
/// external input: fetched from `glamsystems/ix-mapper-ts` and embedded in the
/// sdk jar, then parsed on the account-remapping hot path.
///
/// The fuzz payload is arbitrary bytes parsed as the config JSON, exactly as
/// `loadMappingConfigs` parses a file's bytes: `JsonIterator.parse(bytes)` then
/// `ProgramMapConfig.parseConfig`. Malformed-input contract: garbage in ->
/// `RuntimeException` out (a bad config file is a startup failure, not a hang).
/// Jazzer flags what the contract forbids — hangs (deeply nested JSON, huge
/// number literals), memory exhaustion, and any non-`RuntimeException`
/// throwable.
///
/// Seeded from the real system-program mapping config under
/// src/test/resources/fuzz/mappingConfig — the nested instruction/account
/// structure is unreachable from scratch, so a mutator only makes progress
/// from a real seed.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test
/// sources.
///
/// Run with `./gradlew :sdk:fuzzMappingConfig [-PmaxFuzzTime=<seconds>]`.
public final class MappingConfigFuzz {

  public static void fuzzerTestOneInput(final byte[] data) {
    final ProgramMapConfig config;
    try {
      final var ji = JsonIterator.parse(data);
      final var accountMetaCache = new HashMap<AccountMeta, AccountMeta>();
      final var indexedAccountMetaCache = new HashMap<IndexedAccountMeta, IndexedAccountMeta>();
      config = ProgramMapConfig.parseConfig(accountMetaCache, indexedAccountMetaCache, ji);
    } catch (final RuntimeException tolerated) {
      // malformed or truncated config JSON — rejection is in contract
      return;
    }
    if (config == null) {
      return;
    }
    // touch the parsed structure the way the mapper does, so a config that
    // parses into a nonsense shape (negative counts, dangling indices) surfaces
    // here rather than at first use on the remapping path
    config.invokedProxyProgram();
    config.ixMapConfigs().forEach(ix -> {
      ix.cpiDiscriminator();
      ix.proxyDiscriminator();
      ix.dynamicAccounts();
      ix.staticAccounts();
    });
  }
}
