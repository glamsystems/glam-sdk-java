package systems.glam.services.state;

import systems.glam.services.mints.AssetMetaContext;

public record GlobalConfigUpdate(long slot, AssetMetaContext[] assetMetaContexts, byte[] data) {

  public AssetMetaContext get(final int index) {
    return index < assetMetaContexts.length ? assetMetaContexts[index] : null;
  }
}
