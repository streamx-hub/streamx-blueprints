package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.rendering.engine.Channels;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class PreservedDataStore extends BaseStore<PreservedData> {

  @PostConstruct
  void initRepository() {
    initRepository("preserved-data", PreservedData.class);
  }

  @Incoming(Channels.Incoming.DATA_STATE)
  void register(CloudEvent event) {
    String subject = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    Data data = CloudEventUtils.getData(event, Data.class);
    if (Data.TYPE_UNPUBLISHED.equals(eventType)) {
      PreservedData preserved = store.get(subject);
      if (preserved == null) {
        store.remove(subject);
      } else {
        store.put(subject, new PreservedData(preserved.data(), eventType));
      }
    } else if (Data.TYPE_PUBLISHED.equals(eventType)) {
      store.put(subject, new PreservedData(data, eventType));
    }
  }

  public boolean hasData(String key) {
    return Optional.ofNullable(store.get(key)).isPresent();
  }

  public PreservedData get(String key) {
    return store.get(key);
  }

  public Stream<Entry<String, PreservedData>> getAll() {
    return store.entries();
  }
}
