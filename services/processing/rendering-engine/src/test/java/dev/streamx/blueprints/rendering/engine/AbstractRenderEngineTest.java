package dev.streamx.blueprints.rendering.engine;

import static org.junit.jupiter.api.Assertions.fail;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.eclipse.microprofile.reactive.messaging.Message;

abstract class AbstractRenderEngineTest {

  @Inject
  @Any
  InMemoryConnector connector;

  protected CloudEvent dataEvent(String key, String eventType) {
    return dataEvent(key, eventType, 1L);
  }

  protected CloudEvent dataEvent(String key, String eventType, long eventTime) {
    return dataEvent(key, eventType, eventTime, null);
  }

  protected CloudEvent dataEvent(String key, String eventType, long eventTime, String dataType) {
    if (Data.TYPE_PUBLISHED.equals(eventType)) {
      Data data = new Data("{ \"id\": \"%s\" }".formatted(key), dataType);
      return CloudEventUtils.eventWithData(data, eventType, key, toOffsetDateTime(eventTime));
    } else if (Data.TYPE_UNPUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithoutData(eventType, key, toOffsetDateTime(eventTime));
    }
    return fail("Unexpected event type: " + eventType);
  }

  protected CloudEvent rendererPublishEvent(String key) {
    return rendererEvent(key, Renderer.TYPE_PUBLISHED, 1L);
  }

  protected CloudEvent rendererEvent(String key, String eventType, long eventTime) {
    if (Renderer.TYPE_PUBLISHED.equals(eventType)) {
      Renderer renderer = new Renderer("id = {{id}}");
      return CloudEventUtils.eventWithData(renderer, eventType, key, toOffsetDateTime(eventTime));
    } else if (Renderer.TYPE_UNPUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithoutData(eventType, key, toOffsetDateTime(eventTime));
    }
    return fail("Unexpected event type: " + eventType);
  }

  protected CloudEvent renderingContextEvent(String key, String eventType,
      RenderingContext context) {
    return renderingContextEvent(key, eventType, 1L, context);
  }

  protected CloudEvent renderingContextEvent(String key, String eventType, long eventTime,
      RenderingContext context) {
    if (RenderingContext.TYPE_PUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithData(context, eventType, key, toOffsetDateTime(eventTime));
    } else if (RenderingContext.TYPE_UNPUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithoutData(eventType, key, toOffsetDateTime(eventTime));
    }
    return fail("Unexpected event type: " + eventType);
  }

  protected CloudEvent renderingRequestEvent(String key, String eventType,
      RenderingRequest request) {
    return CloudEventUtils.eventWithData(request, eventType, key, toOffsetDateTime(1));
  }

  protected static OffsetDateTime toOffsetDateTime(long utcEpochMillis) {
    Instant instant = Instant.ofEpochMilli(utcEpochMillis);
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
