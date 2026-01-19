package com.streamx.blueprints.data.collector.stores;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.collector.Channels;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Dependent
public class PublishedDataStore {

  private StateRepository<PreservedData> store;

  @PostConstruct
  void initRepository() {
    store = RepositoryFactory.createRepository(PreservedData.class, "preserved-data");
  }

  @Incoming(Channels.Incoming.DATA_STATE)
  Uni<Void> registerData(CloudEvent dataEvent) {
    String key = CloudEventUtils.getSubject(dataEvent);
    Data data = CloudEventUtils.getData(dataEvent, Data.class);
    String eventType = dataEvent.getType();
    if (Data.TYPE_PUBLISHED.equals(eventType)) {
      store.put(key, new PreservedData(key, data, eventType));
    } else {
      store.remove(key);
    }
    return Uni.createFrom().voidItem();
  }

  public Stream<Entry<String, PreservedData>> getEntriesStream() {
    return store.entries();
  }
}
