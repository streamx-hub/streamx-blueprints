package com.streamx.blueprints.data.collector.stores;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

@Dependent
public class PublishedDataStore {

  private StateRepository<PreservedData> store;

  @PostConstruct
  void initRepository() {
    Config config = ConfigProvider.getConfig();
    store = RepositoryFactory.createRepository(config, PreservedData.class, "preserved-data");
  }

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

  public Stream<Entry<String, PreservedData>> getEntriesStream() {
    return store.entries();
  }
}
