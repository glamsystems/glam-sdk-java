package systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record WormholeNttPolicy(WormholeWttRoute[] routes) implements SerDe {

  public static final int ROUTES_OFFSET = 0;

  public static WormholeNttPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var routes = SerDeUtil.readVector(4, WormholeWttRoute.class, WormholeWttRoute::read, _data, _offset);
    return new WormholeNttPolicy(routes);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, routes, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, routes);
  }
}
