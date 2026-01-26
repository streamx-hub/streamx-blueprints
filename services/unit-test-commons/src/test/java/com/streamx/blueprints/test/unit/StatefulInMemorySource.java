package com.streamx.blueprints.test.unit;

import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.reactive.messaging.Message;

public class StatefulInMemorySource {

  private static final int WAIT_SECONDS_FOR_STATE_MESSAGE_ACK = 3;

  private final InMemorySource<Message<CloudEvent>> mainChannel;
  private final InMemorySource<Message<CloudEvent>> stateChannel;

  public StatefulInMemorySource(InMemoryConnector connector, String mainChannel,
      String stateChannel) {
    this.mainChannel = connector.source(mainChannel);
    this.stateChannel = connector.source(stateChannel);
  }

  public void send(CloudEvent event) {
    CompletableFuture<Void> ackFuture = new CompletableFuture<>();
    Message<CloudEvent> stateMessage = Message.of(
        event,
        () -> {
          ackFuture.complete(null);
          return CompletableFuture.completedFuture(null);
        }
    );

    stateChannel.send(stateMessage);

    try {
      ackFuture.get(WAIT_SECONDS_FOR_STATE_MESSAGE_ACK, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException("Timeout waiting for state message ACK", e);
    }

    Message<CloudEvent> mainMessage = Message.of(event);
    mainChannel.send(mainMessage);
  }
}
