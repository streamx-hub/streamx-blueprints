package dev.streamx.blueprints.rendering.engine;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import io.smallrye.mutiny.Uni;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

public class AckHandler {

  private final Message<?> incoming;
  Logger log = Logger.getLogger(AckHandler.class);

  private final List<Uni<Void>> ackUnisList;

  public AckHandler(Message<?> incoming) {
    ackUnisList = new ArrayList<>();
    this.incoming = incoming;
  }

  public Uni<Void> handleIncomingMessageAck() {
    String incomingMessageKey = extractKey(incoming);
    log.tracef(
        "Resolving ack of incoming message with key: %s on outgoing messages stream complete."
            + " Checking %d ack unis.",
        incomingMessageKey, ackUnisList.size());
    if (ackUnisList.isEmpty()) {
      log.tracef("No ackUnis to check. ACK for message with key: %s", incomingMessageKey);
      return Uni.createFrom().completionStage(incoming.ack());
    } else {
      return Uni.combine().all().unis(ackUnisList).discardItems()
          .onItemOrFailure()
          .call(this::handleAcknowledgement);
    }
  }

  private Uni<Void> handleAcknowledgement(Void unused, Throwable throwable) {
    String incomingMessageKey = extractKey(incoming);
    if (throwable == null) {
      log.tracef("All ack unis completed. ACK for message with key: %s", incomingMessageKey);
      return Uni.createFrom().completionStage(incoming.ack());
    } else {
      log.tracef("ACK uni failed. NACK for message with key: %s", incomingMessageKey,
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
