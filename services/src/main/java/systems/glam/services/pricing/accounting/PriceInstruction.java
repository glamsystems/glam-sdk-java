package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PriceInstruction {

  boolean priceInstructions(final Map<PublicKey, AccountInfo<byte[]>> accounts,
                            final Map<PublicKey, AssetMeta> assetMetaMap,
                            final StateAccount stateAccount,
                            final Set<PublicKey> externalVaultAccounts,
                            final List<Instruction> instructions);
}
