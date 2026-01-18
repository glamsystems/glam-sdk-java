package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;

public record JupiterSwapPolicy(int maxSlippageBps, PublicKey[] swapAllowlist) implements SerDe {

  public static final int MAX_SLIPPAGE_BPS_OFFSET = 0;
  public static final int SWAP_ALLOWLIST_OFFSET = 3;

  public static JupiterSwapPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var maxSlippageBps = getInt16LE(_data, i);
    i += 2;
    final PublicKey[] swapAllowlist;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      swapAllowlist = null;
    } else {
      ++i;
      swapAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    }
    return new JupiterSwapPolicy(maxSlippageBps, swapAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt16LE(_data, i, maxSlippageBps);
    i += 2;
    if (swapAllowlist == null || swapAllowlist.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += SerDeUtil.writeVector(4, swapAllowlist, _data, i);
    }
    return i - _offset;
  }

  @Override
  public int l() {
    return 2 + (swapAllowlist == null || swapAllowlist.length == 0 ? 1 : (1 + SerDeUtil.lenVector(4, swapAllowlist)));
  }
}
