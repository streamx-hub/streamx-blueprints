package com.streamx.blueprints.rendering.engine;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

public class AckHandler {

  private static final Logger log = Logger.getLogger(AckHandler.class);

  private final Message<CloudEvent> incoming;
  private final String incomingEventKey;

  private final List<Uni<Void>> ackUnisList = new ArrayList<>();

  // TODO consider removing AckHandler and migrate from Message<CloudEvent> to CloudEvent
  //  when https://github.com/smallrye/smallrye-reactive-messaging/issues/3232 is fixed
  public AckHandler(Message<CloudEvent> incoming) {
    this.incoming = incoming;
    this.incomingEventKey = CloudEventUtils.getSubject(incoming.getPayload());
  }

  public Uni<Void> handleIncomingMessageAck() {
    log.tracef(
        "Resolving ack of incoming message with key: %s on outgoing messages stream complete."
            + " Checking %d ack unis.",
        incomingEventKey, ackUnisList.size());
    if (ackUnisList.isEmpty()) {
      log.tracef("No ackUnis to check. ACK for message with key: %s", incomingEventKey);
      return Uni.createFrom().completionStage(incoming.ack());
    } else {
      return Uni.combine().all().unis(ackUnisList).discardItems()
          .onItemOrFailure()
          .call(this::handleAcknowledgement);
    }
  }

  private Uni<Void> handleAcknowledgement(Void unused, Throwable throwable) {
    if (throwable == null) {
      log.tracef("All ack unis completed. ACK for message with key: %s", incomingEventKey);
      return Uni.createFrom().completionStage(incoming.ack());
    } else {
      log.tracef("ACK uni failed. NACK for message with key: %s", incomingEventKey,
          throwable);
      return Uni.createFrom().completionStage(incoming.nack(throwable));
    }
  }

  public CompletableFuture<Void> registerAckCompletableFeature() {
    CompletableFuture<Void> ackCf = new CompletableFuture<>();
    ackUnisList.add(Uni.createFrom().completionStage(ackCf));
    return ackCf;
  }
}
