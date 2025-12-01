package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntries;
import software.sava.idl.clients.kamino.scope.entries.ScopeReader;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.rpc.json.http.response.AccountInfo;

record MappingsContext(PublicKey publicKey,
                       long slot,
                       byte[] data,
                       ScopeEntries scopeEntries) {

  static MappingsContext createContext(final AccountInfo<byte[]> accountInfo) {
    final var pubKey = accountInfo.pubKey();
    final byte[] data = accountInfo.data();
    final var mappings = OracleMappings.read(pubKey, data, 0);
    final var scopeEntries = ScopeReader.parseEntries(mappings);
    return new MappingsContext(pubKey, accountInfo.context().slot(), data, scopeEntries);
  }

  boolean isAfter(final MappingsContext other) {
    return Long.compareUnsigned(this.slot, other.slot) > 0;
  }
}
