package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.RenderingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PreservedRenderingContextStore {

  private static final Map<String, PreservedRenderingContext> store = new ConcurrentHashMap<>();

  public void register(RenderingContext renderingContext, String eventType, String subject) {
    if (RenderingContext.TYPE_UNPUBLISHED.equals(eventType)) {
      PreservedRenderingContext preserved = store.get(subject);
      if (preserved == null) {
        store.put(subject, new PreservedRenderingContext(null, eventType));
      } else {
        store.put(subject, new PreservedRenderingContext(preserved.renderingContext(), eventType));
      }
    } else {
      store.put(subject, new PreservedRenderingContext(renderingContext, eventType));
    }
  }

  public PreservedRenderingContext get(String key) {
    return store.get(key);
  }

  public Set<Entry<String, PreservedRenderingContext>> getAll() {
    return store.entrySet();
  }
}
