package dev.streamx.blueprints.rendering.engine;

import io.smallrye.reactive.messaging.Targeted;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NullableTargeted implements Targeted {

  private final Map<String, Object> backend;

  public NullableTargeted(Map<String, Object> map) {
    this.backend = new HashMap<>(map);
  }

  public NullableTargeted() {
    this.backend = new HashMap<>();
  }

  @Override
  public Object get(String channel) {
    return backend.get(channel);
  }

  @Override
  public Targeted with(String channel, Object payload) {
    Map<String, Object> copy = new HashMap<>(backend);
    copy.put(channel, payload);
    return new NullableTargeted(copy);
  }

  @Override
  public Iterator<Map.Entry<String, Object>> iterator() {
    return backend.entrySet().iterator();
  }
}
