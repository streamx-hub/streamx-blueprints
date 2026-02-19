package com.streamx.blueprints.data.collector.stores;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.collector.Channels;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Dependent
public class PublishedDataStore {

  @Inject
  RepositoryFactory repositoryFactory;

  private StateRepository<PublishedData> store;

  @PostConstruct
  void initRepository() {
    store = repositoryFactory.getOrCreate("published-data", PublishedData.class);
  }

  @Incoming(Channels.Incoming.DATA_STATE)
  void registerData(CloudEvent dataEvent) {
    String key = CloudEventUtils.getSubject(dataEvent);
    String eventType = dataEvent.getType();
    if (Data.TYPE_PUBLISHED.equals(eventType)) {
      Data data = CloudEventUtils.getData(dataEvent, Data.class);
      store.put(key, new PublishedData(key, data, eventType));
    } else {
      store.remove(key);
    }
  }

  public Stream<PublishedData> getValues() {
    return store.values();
  }
}
