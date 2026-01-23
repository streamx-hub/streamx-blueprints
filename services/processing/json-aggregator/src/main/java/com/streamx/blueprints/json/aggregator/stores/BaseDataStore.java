package com.streamx.blueprints.json.aggregator.stores;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.Map.Entry;
import java.util.stream.Stream;

abstract class BaseDataStore {

  @Inject
  RepositoryFactory repositoryFactory;

  private StateRepository<PreservedData> repository;

  protected void initRepository(String identifier) {
    repository = repositoryFactory.getOrCreate(identifier, PreservedData.class);
  }

  protected Uni<Void> register(CloudEvent event) {
    Data data = CloudEventUtils.getData(event, Data.class);
    String eventType = event.getType();
    String subject = CloudEventUtils.getSubject(event);
    repository.put(subject, new PreservedData(data, eventType));
    return Uni.createFrom().voidItem();
  }

  public PreservedData get(String key) {
    return repository.get(key);
  }

  public Stream<Entry<String, PreservedData>> getAll() {
    return repository.entries();
  }
}
