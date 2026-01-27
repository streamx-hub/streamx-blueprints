package com.streamx.blueprints.rendering.engine;

import static com.streamx.blueprints.cloudevents.utils.CloudEventUtils.toOffsetDateTime;
import static org.assertj.core.api.Assertions.fail;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import io.cloudevents.CloudEvent;

abstract class AbstractRenderEngineTest extends BaseInMemoryTest {

  protected CloudEvent dataPublishEvent(String key) {
    return dataEvent(key, Data.TYPE_PUBLISHED, 1L);
  }

  protected CloudEvent dataUnpublishEvent(String key) {
    return dataEvent(key, Data.TYPE_UNPUBLISHED, 1L);
  }

  protected CloudEvent dataEvent(String key, String eventType, long eventTime) {
    return dataEvent(key, eventType, eventTime, null);
  }

  protected CloudEvent dataEvent(String key, String eventType, long eventTime, String dataType) {
    if (Data.TYPE_PUBLISHED.equals(eventType)) {
      Data data = new Data("{ \"id\": \"%s\" }".formatted(key), dataType);
      return CloudEventUtils.eventWithData(key, eventType, data, toOffsetDateTime(eventTime));
    } else if (Data.TYPE_UNPUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithoutData(key, eventType, toOffsetDateTime(eventTime));
    }
    return fail("Unexpected event type: " + eventType);
  }

  protected CloudEvent rendererPublishEvent(String key) {
    return rendererEvent(key, Renderer.TYPE_PUBLISHED, 1L);
  }

  protected CloudEvent rendererEvent(String key, String eventType, long eventTime) {
    if (Renderer.TYPE_PUBLISHED.equals(eventType)) {
      Renderer renderer = new Renderer("id = {{id}}");
      return CloudEventUtils.eventWithData(key, eventType, renderer, toOffsetDateTime(eventTime));
    } else if (Renderer.TYPE_UNPUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithoutData(key, eventType, toOffsetDateTime(eventTime));
    }
    return fail("Unexpected event type: " + eventType);
  }

  protected CloudEvent renderingContextPublishEvent(String key, RenderingContext context) {
    return renderingContextEvent(key, RenderingContext.TYPE_PUBLISHED, 1L, context);
  }

  protected CloudEvent renderingContextEvent(String key, String eventType, long eventTime,
      RenderingContext context) {
    if (RenderingContext.TYPE_PUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithData(key, eventType, context, toOffsetDateTime(eventTime));
    } else if (RenderingContext.TYPE_UNPUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithoutData(key, eventType, toOffsetDateTime(eventTime));
    }
    return fail("Unexpected event type: " + eventType);
  }

  protected CloudEvent renderingRequestEvent(String key, String eventType,
      RenderingRequest request) {
    return CloudEventUtils.eventWithData(key, eventType, request, toOffsetDateTime(1));
  }
}
