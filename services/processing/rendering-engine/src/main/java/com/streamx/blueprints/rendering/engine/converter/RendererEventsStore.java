package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.Renderer;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RendererEventsStore {

  private static final Map<String, RendererEvent> store = new ConcurrentHashMap<>();

  public void register(Renderer renderer, String eventType, String subject) {
    store.put(subject, new RendererEvent(renderer, eventType));
  }

  public RendererEvent get(String key) {
    return store.get(key);
  }
}
