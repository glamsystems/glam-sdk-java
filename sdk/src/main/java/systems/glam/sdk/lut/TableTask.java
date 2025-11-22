package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;

import java.util.List;

public interface TableTask {

  /// May be null if the corresponding instruction to create the table has not yet been created.
  PublicKey tableKey();

  boolean needsSlot();

  List<Instruction> instructions(final long recentSlot);
}
