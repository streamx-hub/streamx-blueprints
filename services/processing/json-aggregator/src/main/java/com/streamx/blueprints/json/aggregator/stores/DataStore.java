package com.streamx.blueprints.json.aggregator.stores;

import com.streamx.blueprints.json.aggregator.Channels;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Dependent
public class DataStore extends BaseDataStore {

  @PostConstruct
  void initRepository() {
    initRepository("data");
  }

  @Incoming(Channels.DATA_STATE)
  public void registerData(CloudEvent event) {
    register(event);
  }
}
