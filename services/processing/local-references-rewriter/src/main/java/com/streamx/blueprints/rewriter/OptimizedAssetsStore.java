package com.streamx.blueprints.rewriter;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.OptimizedAsset;
import io.cloudevents.CloudEvent;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OptimizedAssetsStore {

  // key: original asset path, value: optimized image path
  private static final Map<String, String> publishedOptimizedAssets = new ConcurrentHashMap<>();

  @Inject
  Logger log;

  public void register(CloudEvent optimizedAssetEvent) {
    String key = CloudEventUtils.getSubject(optimizedAssetEvent);
    String eventType = optimizedAssetEvent.getType();
    if (OptimizedAsset.TYPE_PUBLISHED.equals(eventType)) {
      log.tracef("Registering optimized asset %s", key);
      addAsset(optimizedAssetEvent, key);
    } else if (OptimizedAsset.TYPE_UNPUBLISHED.equals(eventType)) {
      log.tracef("Unregistering optimized asset %s", key);
      removeAsset(key);
    } else {
      log.warnf("Received optimized asset %s with unexpected type: %s", key, eventType);
    }
  }

  private void addAsset(CloudEvent optimizedAssetEvent, String key) {
    try {
      var optimizedAsset = CloudEventUtils.getData(optimizedAssetEvent, OptimizedAsset.class);
      publishedOptimizedAssets.put(optimizedAsset.getOriginalPath(), key);
    } catch (RuntimeException ex) {
      log.warnf(ex, "Error extracting OptimizedAsset from event %s", key);
    }
  }

  private void removeAsset(String key) {
    publishedOptimizedAssets.entrySet().removeIf(
        item -> item.getValue().equals(key)
    );
  }

  @Nullable
  public String getOptimizedAssetPath(String originalImagePath) {
    return publishedOptimizedAssets.get(originalImagePath);
  }
}
