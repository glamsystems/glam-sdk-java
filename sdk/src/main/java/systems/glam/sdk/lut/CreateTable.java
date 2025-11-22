package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.solana.programs.address_lookup_table.AddressLookupTableProgram;
import systems.glam.sdk.GlamAccountClient;

import java.util.List;

final class CreateTable extends BaseTableTask {

  private PublicKey tableKey;

  CreateTable(final GlamAccountClient accountClient, final List<PublicKey> accounts) {
    super(accountClient, accounts);
  }

  @Override
  public PublicKey tableKey() {
    return tableKey;
  }

  @Override
  public boolean needsSlot() {
    return true;
  }

  @Override
  public List<Instruction> instructions(final long recentSlot) {
    final var solanaAccounts = accountClient.solanaAccounts();
    final var feePayer = accountClient.feePayerKey();
    final var tablePDA = AddressLookupTableProgram.findLookupTableAddress(solanaAccounts, feePayer, recentSlot);
    this.tableKey = tablePDA.publicKey();

    final var createTableIx = AddressLookupTableProgram.createLookupTable(
        solanaAccounts,
        tableKey,
        feePayer, feePayer,
        recentSlot,
        tablePDA.nonce()
    );
    final var extendTableIx = extendTableInstruction(tableKey);
    return List.of(createTableIx, extendTableIx);
  }
}
