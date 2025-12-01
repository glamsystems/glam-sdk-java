package systems.glam.services.oracles.scope;

import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.scope.entries.PriceChains;

public record ReservePriceChains(Reserve reserve, PriceChains priceChains) {
}
