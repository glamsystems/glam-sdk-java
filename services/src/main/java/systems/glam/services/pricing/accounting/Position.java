package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.mint.gen.events.GlamMintEvent;
import systems.glam.sdk.idl.programs.glam.mint.gen.events.PricedProtocolRecord;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.state.MinGlamStateAccount;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

public interface Position {

  void removeAccount(final PublicKey account);

  void accountsForPriceInstruction(final Set<PublicKey> keys);

  Instruction priceInstruction(final IntegrationServiceContext serviceContext,
                               final GlamAccountClient glamAccountClient,
                               final PublicKey solUSDOracleKey,
                               final PublicKey baseAssetUSDOracleKey,
                               final MinGlamStateAccount stateAccount,
                               final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                               final Set<PublicKey> returnAccounts);

  static BigDecimal parseAnchorEvent(final InnerInstructions priceInstruction,
                                     final PublicKey mintProgram,
                                     final int decimals) {
    for (final var innerIx : priceInstruction.instructions()) {
      if (innerIx.programId().equals(mintProgram)) {
        final var event = GlamMintEvent.readCPI(innerIx.data());
        if (event instanceof PricedProtocolRecord(_, final BigInteger baseAssetAmount)) {
          return baseAssetAmount.signum() == 0
              ? BigDecimal.ZERO
              : new BigDecimal(baseAssetAmount).movePointRight(decimals);
        }
      }
    }
    return null;
  }

  PositionReport positionReport(final PublicKey mintProgram,
                                final int baseAssetDecimals,
                                final Map<PublicKey, AccountInfo<byte[]>> returnedAccountsMap,
                                final InnerInstructions innerInstructions);
}
