package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;

import java.util.*;

public record KaminoVaultContext(VaultState vaultState, AddressLookupTable table, List<Reserve> reserves) {

  static Map<PublicKey, KaminoVaultContext> joinContext(final Collection<VaultState> vaults,
                                                        final Map<PublicKey, AddressLookupTable> tables,
                                                        final Map<PublicKey, Reserve> reserves) {
    final var joined = HashMap.<PublicKey, KaminoVaultContext>newHashMap(vaults.size());
    for (final var kVault : vaults) {
      final var table = tables.get(kVault.vaultLookupTable());
      final var reserveList = Arrays.stream(kVault.vaultAllocationStrategy()).<Reserve>mapMulti((vaultAllocation, reserveConsumer) -> {
        final var reserveKey = vaultAllocation.reserve();
        if (!reserveKey.equals(PublicKey.NONE)) {
          final var reserve = reserves.get(reserveKey);
          if (reserve != null) {
            reserveConsumer.accept(reserve);
          }
        }
      }).toList();
      joined.put(kVault.sharesMint(), new KaminoVaultContext(kVault, table, reserveList));
    }
    return joined;
  }
}
