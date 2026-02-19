package com.streamx.blueprints.rewriter;

import static java.util.Objects.requireNonNull;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.OptimizedAsset;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OptimizedAssetsStore {

  @Inject
  RepositoryFactory repositoryFactory;

  private StateRepository<String> originalPathToOptimizedPath;

  @PostConstruct
  void initRepository() {
    originalPathToOptimizedPath = repositoryFactory.getOrCreate("optimized-assets", String.class);
  }

  @Inject
  Logger log;

  @Incoming(Channels.OPTIMIZED_ASSETS)
  public void registerAsset(CloudEvent optimizedAssetEvent) {
    String key = CloudEventUtils.getSubjectWithoutNamespace(optimizedAssetEvent);
    String eventType = optimizedAssetEvent.getType();
    if (OptimizedAsset.TYPE_PUBLISHED.equals(eventType)) {
      log.tracef("Registering optimized asset %s", key);
      addOptimizedAsset(optimizedAssetEvent, key);
    } else if (OptimizedAsset.TYPE_UNPUBLISHED.equals(eventType)) {
      log.tracef("Unregistering optimized asset %s", key);
      removeOptimizedAsset(key);
    } else {
      log.warnf("Received optimized asset %s with unexpected type: %s", key, eventType);
    }
  }

  private void addOptimizedAsset(CloudEvent optimizedAssetEvent, String optimizedAssetPath) {
    try {
      var optimizedAsset = CloudEventUtils.getData(optimizedAssetEvent, OptimizedAsset.class);
      String originalAssetPath = CloudEventUtils.getSubjectWithoutNamespace(
          requireNonNull(optimizedAsset).getOriginalPath());
      originalPathToOptimizedPath.put(originalAssetPath, optimizedAssetPath);
    } catch (RuntimeException ex) {
      log.warnf(ex, "Error extracting OptimizedAsset from event %s", optimizedAssetPath);
    }
  }

  private void removeOptimizedAsset(String optimizedAssetPath) {
    originalPathToOptimizedPath.removeByValue(optimizedAssetPath);
  }

  @Nullable
  public String getOptimizedAssetPath(String originalImagePath) {
    return originalPathToOptimizedPath.get(originalImagePath);
  }
}
