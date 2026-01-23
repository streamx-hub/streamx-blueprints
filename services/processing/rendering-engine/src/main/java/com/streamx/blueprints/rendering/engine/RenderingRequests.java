package com.streamx.blueprints.rendering.engine;

import static com.streamx.blueprints.rendering.engine.RenderingContexts.isMatchingData;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.rendering.engine.converter.PreservedData;
import com.streamx.blueprints.rendering.engine.converter.PreservedDataStore;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RenderingRequests {

  @Inject
  Logger log;

  @Inject
  PreservedDataStore dataStore;

  Multi<Message<CloudEvent>> getFromDataStore(Message<CloudEvent> incoming,
      List<KeyedValue<RenderingContext>> renderingContexts) {
    Stream<KeyedValue<Data>> dataStream = fetchStoredDataContextFromStore();

    return getFrom(incoming, renderingContexts, dataStream);
  }

  Multi<Message<CloudEvent>> getFrom(Message<CloudEvent> incoming,
      List<KeyedValue<RenderingContext>> renderingContexts,
      Stream<KeyedValue<Data>> dataStream) {
    Multi<Message<CloudEvent>> outgoings;
    AckHandler ackHandler = new AckHandler(incoming);
    if (renderingContexts.isEmpty()) {
      outgoings = Multi.createFrom().empty();
    } else {
      CloudEvent event = incoming.getPayload();
      log.tracef("Sending outgoing messages after %s of message with key %s",
          event.getType(), CloudEventUtils.getSubject(event));
      Stream<Message<CloudEvent>> renderingRequests = calculateRenderingRequestForDataStore(
          event.getTime(),
          event.getType(),
          ackHandler,
          renderingContexts,
          dataStream);
      outgoings = Multi.createFrom().items(renderingRequests);
    }
    return outgoings.onCompletion().call(ackHandler::handleIncomingMessageAck);
  }

  private Stream<Message<CloudEvent>> calculateRenderingRequestForDataStore(
      OffsetDateTime eventTime, String eventType,
      AckHandler ackHandler,
      List<KeyedValue<RenderingContext>> renderingContexts,
      Stream<KeyedValue<Data>> dataStream) {
    return dataStream
        .filter(this::skipDataWithNoValue)
        .flatMap(
            data -> findMatchingContextsByDataKeyPattern(data.key(), getDataType(data.key()),
                renderingContexts))
        .filter(dataContext -> dataContext.context() != null)
        .map(dataContext -> {
          var ackCf = ackHandler.registerAckCompletableFeature();
          var value = dataContext.context().value();
          var dataKey = dataContext.dataKey();

          RenderingRequest renderingRequest = new RenderingRequest(
              dataKey,
              value.rendererKey(),
              value.outputKeyTemplate(),
              value.outputTypeTemplate(),
              value.outputFormat()
          );
          CloudEvent event = CloudEventUtils.eventWithData(
              dataContext.buildKey(), eventType, renderingRequest, eventTime
          );

          return Message.of(event,
              () -> {
                ackCf.complete(null);
                return CompletableFuture.completedFuture(null);
              },
              throwable -> {
                ackCf.completeExceptionally(throwable);
                return CompletableFuture.completedFuture(null);
              }
          );
        });
  }

  private Stream<KeyedValue<Data>> fetchStoredDataContextFromStore() {
    return dataStore.getAll()
        .map(entry -> new KeyedValue<>(entry.getKey(), entry.getValue().data()));
  }

  private boolean skipDataWithNoValue(KeyedValue<?> entry) {
    return entry.value() != null
        || dataStore.hasData(entry.key());
  }

  private String getDataType(String key) {
    return Optional.ofNullable(dataStore.get(key))
        .map(PreservedData::data)
        .map(Data::getType)
        .orElse(null);
  }

  private Stream<DataContext> findMatchingContextsByDataKeyPattern(
      String dataKey, String dataType, List<KeyedValue<RenderingContext>> renderingContexts) {
    return renderingContexts.stream()
        .filter(context -> context.value() != null)
        .filter(context -> isMatchingData(context.value(), dataKey, dataType))
        .map(context -> new DataContext(dataKey, context));
  }

  // Util model used for mapping streams.
  private record DataContext(String dataKey, KeyedValue<RenderingContext> context) {

    String buildKey() {
      return context.key() + ":::" + dataKey();
    }
  }

}
