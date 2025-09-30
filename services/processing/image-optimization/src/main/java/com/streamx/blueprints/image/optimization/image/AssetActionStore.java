package com.streamx.blueprints.image.optimization.image;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;

import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssetActionStore {

  @Inject
  @FromChannel(OptimizeImageFunction.INCOMING_ASSETS_CHANNEL)
  Store<String> assetActionByKey;

  Store<String> getAssetActionByKey() {
    return assetActionByKey;
  }

  public String getOptimizedImageAction(String optimizedImagePath) {
    return assetActionByKey.get(optimizedImagePath);
  }

  public boolean isOptimizedImagePublished(String optimizedImagePath) {
    String action = getOptimizedImageAction(optimizedImagePath);
    return PUBLISH.getValue().equals(action);
  }
}
