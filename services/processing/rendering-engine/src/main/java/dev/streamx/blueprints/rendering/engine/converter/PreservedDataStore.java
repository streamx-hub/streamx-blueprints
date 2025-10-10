package dev.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.Data;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PreservedDataStore {

  private static final Map<String, PreservedData> store = new ConcurrentHashMap<>();

  public void register(Data data, String eventType, String subject) {
    if (Data.TYPE_UNPUBLISHED.equals(eventType)) {
      PreservedData preserved = store.get(subject);
      if (preserved == null) {
        store.put(subject, new PreservedData(null, eventType));
      } else {
        store.put(subject, new PreservedData(preserved.data(), eventType));
      }
    } else {
      store.put(subject, new PreservedData(data, eventType));
    }
  }

  public PreservedData get(String key) {
    return store.get(key);
  }

  public Set<Entry<String, PreservedData>> getAll() {
    return store.entrySet();
  }
}
