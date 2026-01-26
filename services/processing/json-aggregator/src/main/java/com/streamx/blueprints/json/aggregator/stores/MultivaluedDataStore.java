package com.streamx.blueprints.json.aggregator.stores;

import com.streamx.blueprints.json.aggregator.Channels;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Dependent
public class MultivaluedDataStore extends BaseDataStore {

  @PostConstruct
  void initRepository() {
    initRepository("multivalued-data");
  }

  @Incoming(Channels.MULTIVALUED_DATA_STATE)
  public void registerMultivaluedData(CloudEvent event) {
    register(event);
  }
}
