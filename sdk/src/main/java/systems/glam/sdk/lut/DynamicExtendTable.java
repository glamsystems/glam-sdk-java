package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import systems.glam.sdk.GlamAccountClient;

import java.util.List;

final class DynamicExtendTable extends BaseTableTask {

  private final CreateTable createTableTask;

  DynamicExtendTable(final GlamAccountClient accountClient,
                     final List<PublicKey> accounts,
                     final CreateTable createTableTask) {
    super(accountClient, accounts);
    this.createTableTask = createTableTask;
  }

  @Override
  public boolean needsSlot() {
    return false;
  }

  @Override
  public PublicKey tableKey() {
    return createTableTask.tableKey();
  }

  @Override
  public List<Instruction> instructions(final long recentSlot) {
    return List.of(extendTableInstruction(tableKey()));
  }
}
