package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.lut.gen.AddressLookupTableProgram;
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
    final var solanaAccounts = accountClient.solanaAccounts();
    return AddressLookupTableProgram.extendLookupTable(
        solanaAccounts.invokedAddressLookupTableProgram(),
        solanaAccounts,
        tableKey,
        feePayer, feePayer,
        accounts.toArray(PublicKey[]::new)
    );
  }
}
