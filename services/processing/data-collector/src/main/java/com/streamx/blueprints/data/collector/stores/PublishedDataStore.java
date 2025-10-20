package com.streamx.blueprints.data.collector.stores;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.Dependent;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Dependent
public class PublishedDataStore {

  private static final Map<String, PreservedData> store = new ConcurrentHashMap<>();

  public void register(CloudEvent dataEvent) {
    String key = CloudEventUtils.getSubject(dataEvent);
    Data data = CloudEventUtils.getData(dataEvent, Data.class);
    String eventType = dataEvent.getType();
    if (Data.TYPE_PUBLISHED.equals(eventType)) {
      store.put(key, new PreservedData(key, data, eventType));
    } else {
      store.remove(key);
    }
  }

  public Collection<PreservedData> getAll() {
    return store.values();
  }
}
