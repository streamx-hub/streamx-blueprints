package com.streamx.blueprints.test.unit;

import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;

public class StatefulInMemorySource {

  private final InMemorySource<CloudEvent> mainChannel;
  private final InMemorySource<CloudEvent> stateChannel;

  public StatefulInMemorySource(InMemoryConnector connector, String mainChannel,
      String stateChannel) {
    this.mainChannel = connector.source(mainChannel);
    this.stateChannel = connector.source(stateChannel);
  }

  public void send(CloudEvent event) {
    stateChannel.send(event);
    mainChannel.send(event);
  }
}
