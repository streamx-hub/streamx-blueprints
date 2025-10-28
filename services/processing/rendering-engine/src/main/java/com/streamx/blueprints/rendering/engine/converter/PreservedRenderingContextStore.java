package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.RenderingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PreservedRenderingContextStore {

  private static final Map<String, PreservedRenderingContext> store = new ConcurrentHashMap<>();

  public void register(RenderingContext renderingContext, String eventType, String subject) {
    if (RenderingContext.TYPE_UNPUBLISHED.equals(eventType)) {
      PreservedRenderingContext preserved = store.get(subject);
      if (preserved == null) {
        store.remove(subject);
      } else {
        store.put(subject, new PreservedRenderingContext(preserved.renderingContext(), eventType));
      }
    } else if (RenderingContext.TYPE_PUBLISHED.equals(eventType)) {
      store.put(subject, new PreservedRenderingContext(renderingContext, eventType));
    }
  }

  public RenderingContext get(String key) {
    return Optional.ofNullable(store.get(key))
        .map(PreservedRenderingContext::renderingContext)
        .orElse(null);
  }

  public Set<Entry<String, PreservedRenderingContext>> getAll() {
    return store.entrySet();
  }
}
