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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RenderingRequests {

  @Inject
  Logger log;

  @Inject
  PreservedDataStore dataStore;

  Multi<CloudEvent> getFromDataStore(CloudEvent incomingEvent,
      List<KeyedValue<RenderingContext>> renderingContexts) {
    Stream<KeyedValue<Data>> dataStream = fetchStoredDataContextFromStore();

    return getFrom(incomingEvent, renderingContexts, dataStream);
  }

  Multi<CloudEvent> getFrom(CloudEvent incomingEvent,
      List<KeyedValue<RenderingContext>> renderingContexts,
      Stream<KeyedValue<Data>> dataStream) {
    if (renderingContexts.isEmpty()) {
      return Multi.createFrom().empty();
    }
    log.tracef("Sending outgoing messages after %s of message with key %s",
        incomingEvent.getType(), incomingEvent.getSubject());

    Stream<CloudEvent> renderingRequests = dataStream
        .filter(this::hasValue)
        .flatMap(data -> findMatchingContextsByDataKeyPattern(data.key(), renderingContexts))
        .filter(dataContext -> dataContext.context() != null)
        .map(dataContext -> renderingRequestEvent(incomingEvent, dataContext));

    return Multi.createFrom().items(renderingRequests);
  }

  private CloudEvent renderingRequestEvent(CloudEvent incomingEvent, DataContext dataContext) {
    RenderingContext renderingContext = dataContext.context().value();
    String dataKey = dataContext.dataKey();

    RenderingRequest renderingRequest = new RenderingRequest(
        dataKey,
        renderingContext.rendererKey(),
        renderingContext.outputKeyTemplate(),
        renderingContext.outputTypeTemplate(),
        renderingContext.outputFormat()
    );

    return CloudEventUtils.eventWithData(
        dataContext.buildKey(),
        incomingEvent.getType(),
        renderingRequest,
        incomingEvent.getTime()
    );
  }

  private Stream<KeyedValue<Data>> fetchStoredDataContextFromStore() {
    return dataStore.getAll()
        .map(entry -> new KeyedValue<>(entry.getKey(), entry.getValue().data()));
  }

  private boolean hasValue(KeyedValue<Data> entry) {
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
      String dataKey, List<KeyedValue<RenderingContext>> renderingContexts) {
    String dataType = getDataType(dataKey);
    return renderingContexts.stream()
        .filter(context -> context.value() != null)
        .filter(context -> isMatchingData(context.value(), dataKey, dataType))
        .map(context -> new DataContext(dataKey, context));
  }

  // Util model used for mapping streams.
  private record DataContext(String dataKey, KeyedValue<RenderingContext> context) {

    String buildKey() {
      return context.key() + ":::" + dataKey;
    }
  }

}
