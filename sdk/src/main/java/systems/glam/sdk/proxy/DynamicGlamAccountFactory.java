package systems.glam.sdk.proxy;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.ix.proxy.DynamicAccount;
import systems.glam.ix.proxy.DynamicAccountConfig;
import systems.glam.sdk.GlamVaultAccounts;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public interface DynamicGlamAccountFactory extends Function<DynamicAccountConfig, DynamicAccount<GlamVaultAccounts>> {

  static DynamicGlamAccountFactory createFactory(final Map<PublicKey, AccountMeta> extensionAuthorities,
                                                 final int initialCacheSize) {
    final var cache = HashMap.<DynamicAccount<GlamVaultAccounts>, DynamicAccount<GlamVaultAccounts>>newHashMap(initialCacheSize);
    return new CachedDynamicGlamAccountFactory(extensionAuthorities, cache);
  }
}
