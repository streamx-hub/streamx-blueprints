package com.streamx.blueprints.json.aggregator.stores;

import com.streamx.blueprints.data.Data;
import jakarta.enterprise.context.Dependent;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Dependent
public class DataStore {

  private static final Map<String, PreservedData> store = new ConcurrentHashMap<>();

  public void register(Data data, String eventType, String subject) {
    store.put(subject, new PreservedData(data, eventType));
  }

  public PreservedData get(String key) {
    return store.get(key);
  }

  public Set<Entry<String, PreservedData>> getAll() {
    return store.entrySet();
  }
}
