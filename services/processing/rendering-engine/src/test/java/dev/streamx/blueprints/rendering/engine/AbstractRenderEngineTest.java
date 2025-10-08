package dev.streamx.blueprints.rendering.engine;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.Renderer;
import dev.streamx.blueprints.data.RenderingContext;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

abstract class AbstractRenderEngineTest {

  @Inject
  @Any
  InMemoryConnector connector;

  protected Pair<String, Message<Data>> dataMessage(String key, Action action) {
    return dataMessage(key, action, 1L);
  }

  protected Pair<String, Message<Data>> dataMessage(String key, Action action, long eventTime) {
    return dataMessage(key, action, eventTime, Properties.empty());
  }

  protected Pair<String, Message<Data>> dataMessage(String key, Action action, long eventTime,
      Properties properties) {
    Data data = PUBLISH.equals(action) ? new Data("{ \"id\": \"%s\" }".formatted(key)) : null;
    return Pair.of(key,
        Message.of(data,
            Metadata.of(
                Key.of(key),
                EventTime.of(eventTime),
                action,
                properties)));
  }

  protected Message<Renderer> rendererPublishMessage(String key) {
    return rendererMessage(key, PUBLISH, 1L);
  }

  protected Message<Renderer> rendererMessage(String key, Action action, long eventTime) {
    Renderer renderer = PUBLISH.equals(action) ? new Renderer("id = {{id}}") : null;
    return Message.of(renderer, Metadata.of(
        Key.of(key),
        EventTime.of(eventTime),
        action));
  }

  protected Pair<String, Message<RenderingContext>> renderingContextMessage(String key,
      Action action, RenderingContext context) {
    return renderingContextMessage(key, action, 1L, context);
  }

  protected Pair<String, Message<RenderingContext>> renderingContextMessage(String key,
      Action action, long eventTime, RenderingContext context) {
    return Pair.of(key, Message.of(context, Metadata.of(
        Key.of(key),
        EventTime.of(eventTime),
        action)));
  }

  protected Pair<String, Message<RenderingRequest>> renderingRequestMessage(String key,
      Action action, RenderingRequest request) {
    return Pair.of(key, Message.of(request, Metadata.of(
        Key.of(key),
        EventTime.of(1L),
        action
    )));
  }

  protected <T> Message<T> findByKey(String key, InMemorySink<T> sink) {
    return sink.received().stream()
        .filter(msg -> key.equals(extractKey(msg)))
        .findFirst()
        .orElse(null);
  }
}
