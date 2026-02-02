package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntries;
import software.sava.idl.clients.kamino.scope.entries.ScopeReader;
import software.sava.rpc.json.http.response.AccountInfo;

public record MappingsContext(byte[] data, ScopeEntries scopeEntries) {

  static MappingsContext createContext(final AccountInfo<byte[]> accountInfo) {
    return new MappingsContext(accountInfo.data(), ScopeReader.parseEntries(accountInfo));
  }

  PublicKey publicKey() {
    return scopeEntries.pubKey();
  }

  long slot() {
    return scopeEntries.slot();
  }

  boolean isAfter(final MappingsContext other) {
    return Long.compareUnsigned(this.slot(), other.slot()) > 0;
  }
}
