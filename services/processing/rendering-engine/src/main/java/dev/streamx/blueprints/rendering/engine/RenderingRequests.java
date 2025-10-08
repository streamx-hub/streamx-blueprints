package dev.streamx.blueprints.rendering.engine;

import static dev.streamx.blueprints.rendering.engine.RenderingContexts.isMatchingData;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractEventTime;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.RenderingContext;
import dev.streamx.blueprints.rendering.engine.converter.PreservedData;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RenderingRequests {

  @Inject
  Logger log;

  @FromChannel(Channels.Incoming.DATA)
  Store<PreservedData> dataStore;

  Multi<Message<RenderingRequest>> getFromDataStore(Message<?> incoming,
      List<KeyedValue<RenderingContext>> renderingContexts) {
    Supplier<Stream<KeyedValue<Data>>> streamSupplier = this::fetchStoredDataContextFromStore;

    return getFrom(incoming, renderingContexts, streamSupplier);
  }

  Multi<Message<RenderingRequest>> getFromDataEntries(Message<?> incoming,
      List<KeyedValue<RenderingContext>> renderingContexts, List<KeyedValue<Data>> dataEntry) {
    Supplier<Stream<KeyedValue<Data>>> streamSupplier = dataEntry::stream;

    return getFrom(incoming, renderingContexts, streamSupplier);
  }

  Multi<Message<RenderingRequest>> getFrom(Message<?> incoming,
      List<KeyedValue<RenderingContext>> renderingContexts,
      Supplier<Stream<KeyedValue<Data>>> streamSupplier) {
    Multi<Message<RenderingRequest>> outgoings;
    try {
      AckHandler ackHandler = new AckHandler(incoming);
      if (renderingContexts.isEmpty()) {
        outgoings = Multi.createFrom().empty();
      } else {
        log.tracef("Sending outgoing messages after %s of message with key %s",
            extractAction(incoming), extractKey(incoming));
        Stream<Message<RenderingRequest>> renderingRequests = calculateRenderingRequestForDataStore(
            extractEventTime(incoming),
            extractAction(incoming),
            ackHandler,
            renderingContexts,
            streamSupplier);
        outgoings = Multi.createFrom().items(renderingRequests);
      }
      return outgoings.onCompletion().call(ackHandler::handleIncomingMessageAck);
    } catch (Exception e) {
      incoming.nack(e);
      return Multi.createFrom().empty();
    }
  }

  private Stream<Message<RenderingRequest>> calculateRenderingRequestForDataStore(
      Long eventTime, Action action,
      AckHandler ackHandler,
      List<KeyedValue<RenderingContext>> renderingContexts,
      Supplier<Stream<KeyedValue<Data>>> dataStream) {
    return dataStream.get()
        .filter(this::skipDataWithNoValue)
        .flatMap(
            data -> findMatchingContextsByDataKeyPattern(data.key(), getDataType(data.key()),
                renderingContexts))
        .filter(dataContext -> dataContext.context() != null)
        .map(dataContext -> {
          var ackCf = ackHandler.registerAckCompletableFeature();
          var value = dataContext.context().value();
          var dataKey = dataContext.dataKey();

          return Message.of(
              new RenderingRequest(
                  dataKey,
                  value.getRendererKey(),
                  value.getOutputKeyTemplate(),
                  value.getOutputTypeTemplate(),
                  value.getOutputFormat()),
              Metadata.of(
                  Key.of(dataContext.buildKey()),
                  EventTime.of(eventTime),
                  action),
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
    return dataStore.entriesWithMetadata()
        .map(entry -> new KeyedValue<>(entry.key(), entry.value().getPayload().getData()));
  }

  private boolean skipDataWithNoValue(KeyedValue<?> entry) {
    return entry.value() != null
        || dataStore.get(entry.key()).getData() != null;
  }

  private String getDataType(String key) {
    return Properties.from(dataStore.getWithMetadata(key))
        .getType()
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
