package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.lend.gen.types.ScopeConfiguration;
import software.sava.idl.clients.kamino.scope.entries.PriceChains;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntries;
import software.sava.idl.clients.kamino.scope.entries.ScopeReader;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.Arrays;

public record MappingsContext(PublicKey pubKey, byte[] data, ScopeEntries scopeEntries) {

  public static MappingsContext createContext(final AccountInfo<byte[]> accountInfo) {
    return new MappingsContext(
        accountInfo.pubKey(),
        accountInfo.data(),
        ScopeReader.parseEntries(accountInfo)
    );
  }

  public boolean changed(final long slot, final byte[] data) {
    return Long.compareUnsigned(slot, this.slot()) > 0 && !Arrays.equals(this.data, data);
  }

  public boolean changed(final AccountInfo<byte[]> accountInfo) {
    return changed(accountInfo.context().slot(), accountInfo.data());
  }

  public PriceChains readPriceChains(final PublicKey mintKey, final ScopeConfiguration scopeConfiguration) {
    return scopeEntries.readPriceChains(mintKey, scopeConfiguration);
  }

  public PublicKey publicKey() {
    return scopeEntries.pubKey();
  }

  public long slot() {
    return scopeEntries.slot();
  }
}
