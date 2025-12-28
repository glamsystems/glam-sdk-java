package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

public record FeeStructure(EntryExitFees vault,
                           EntryExitFees manager,
                           ManagementFee management,
                           PerformanceFee performance,
                           ProtocolFees protocol) implements SerDe {

  public static final int BYTES = 19;

  public static FeeStructure read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var vault = EntryExitFees.read(_data, i);
    i += vault.l();
    final var manager = EntryExitFees.read(_data, i);
    i += manager.l();
    final var management = ManagementFee.read(_data, i);
    i += management.l();
    final var performance = PerformanceFee.read(_data, i);
    i += performance.l();
    final var protocol = ProtocolFees.read(_data, i);
    return new FeeStructure(vault,
                            manager,
                            management,
                            performance,
                            protocol);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += vault.write(_data, i);
    i += manager.write(_data, i);
    i += management.write(_data, i);
    i += performance.write(_data, i);
    i += protocol.write(_data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
