package com.streamx.blueprints.image.optimizer.image;

import com.streamx.blueprints.data.Asset;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AssetEventTypeStore {

  private static final Map<String, String> assetEventTypeByKey = new ConcurrentHashMap<>();

  public void registerAsset(String key, String eventType) {
    assetEventTypeByKey.put(key, eventType);
  }

  public String getOptimizedImageEventType(String optimizedImagePath) {
    return assetEventTypeByKey.get(optimizedImagePath);
  }

  public boolean isOptimizedImagePublished(String optimizedImagePath) {
    String eventType = getOptimizedImageEventType(optimizedImagePath);
    return Asset.TYPE_PUBLISHED.equals(eventType);
  }

  public Map<String, String> getAssetEventTypeByKey() {
    return assetEventTypeByKey;
  }
}
