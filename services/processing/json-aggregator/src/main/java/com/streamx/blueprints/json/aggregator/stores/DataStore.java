package com.streamx.blueprints.json.aggregator.stores;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.Channels;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Dependent
public class DataStore {

  private StateRepository<PreservedData> state;

  @PostConstruct
  void initRepository() {
    Config config = ConfigProvider.getConfig();
    state = RepositoryFactory.createRepository(config, PreservedData.class, "data");
  }

  @Incoming(Channels.DATA_STATE)
  @Incoming(Channels.MULTIVALUED_DATA_STATE)
  public Uni<Void> register(CloudEvent event) {
    Data data = CloudEventUtils.getData(event, Data.class);
    String eventType = event.getType();
    String subject = CloudEventUtils.getSubject(event);
    state.put(subject, new PreservedData(data, eventType));
    return Uni.createFrom().voidItem();
  }

  public PreservedData get(String key) {
    return state.get(key);
  }

  public Stream<Entry<String, PreservedData>> getAll() {
    return state.entries();
  }
}
