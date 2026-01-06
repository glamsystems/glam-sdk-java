package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.TxSimulation;

import java.math.BigDecimal;
import java.util.Map;

public interface TxSimulationPriceGroupConstituent extends PriceInstruction {

  int valuePositions(final Map<PublicKey, AccountInfo<byte[]>> accounts,
                     final Map<PublicKey, BigDecimal> assetPrices,
                     final TxSimulation txSimulation);
}
