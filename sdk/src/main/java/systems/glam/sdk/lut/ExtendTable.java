package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;

import java.util.List;

record ExtendTable(PublicKey tableKey, List<Instruction> extendTableInstruction) implements TableTask {

  @Override
  public boolean needsSlot() {
    return false;
  }

  @Override
  public List<Instruction> instructions(final long recentSlot) {
    return extendTableInstruction;
  }
}
