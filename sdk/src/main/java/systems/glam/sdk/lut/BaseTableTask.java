package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.solana.programs.address_lookup_table.AddressLookupTableProgram;
import systems.glam.sdk.GlamAccountClient;

import java.util.List;

abstract class BaseTableTask implements TableTask {

  protected final GlamAccountClient accountClient;
  protected final List<PublicKey> accounts;

  BaseTableTask(final GlamAccountClient accountClient, final List<PublicKey> accounts) {
    this.accountClient = accountClient;
    this.accounts = accounts;
  }

  protected final Instruction extendTableInstruction(final PublicKey tableKey) {
    final var feePayer = accountClient.feePayerKey();
    return AddressLookupTableProgram.extendLookupTable(
        accountClient.solanaAccounts(),
        tableKey,
        feePayer, feePayer,
        accounts
    );
  }
}
