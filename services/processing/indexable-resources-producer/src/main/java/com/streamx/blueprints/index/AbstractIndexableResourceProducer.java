package com.streamx.blueprints.index;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.JsonResource;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jboss.logging.Logger;

abstract class AbstractIndexableResourceProducer<T extends WebResource> {

  static final String EXTENSION_NAME_INDEXABLE = "indexable";

  static final ObjectMapper objectMapper = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
  );

  @Inject
  protected Logger log;

  protected abstract ProducerSettings<T> producerSettings();

  protected abstract JsonResource produceIndexableResource(T incomingResource, String key);

  protected CloudEvent produceIndexableResourceFromEvent(CloudEvent event) {
    ProducerSettings<T> settings = producerSettings();

    T resource = CloudEventUtils.getData(event, settings.incomingType());
    String key = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    OffsetDateTime eventTime = event.getTime();

    String incomingType = settings.incomingType().getSimpleName();
    log.tracef("Processing incoming %s with key=%s eventType=%s eventTime=%s",
        incomingType, key, eventType, eventTime);

    boolean indexable = isIndexable(event);
    if (shouldPublish(indexable, eventType, settings)) {
      if (Resource.isEmpty(resource)) {
        log.warnf("Skipping processing empty incoming %s %s", incomingType, key);
        return null;
      }

      String outgoingEventType = settings.outgoingPublishedEventType();
      log.tracef("Publishing %s as %s", key, outgoingEventType);
      JsonResource indexableResource = produceIndexableResource(resource, key);
      return CloudEventUtils.eventWithData(key, outgoingEventType, indexableResource, eventTime);
    }

    if (shouldUnpublish(indexable, eventType, settings)) {
      String outgoingEventType = settings.outgoingUnpublishedEventType();
      log.tracef("Unpublishing %s as %s", key, outgoingEventType);
      return CloudEventUtils.eventWithoutData(key, outgoingEventType, eventTime);
    }

    log.warnf("Skipping processing event %s with unexpected type: %s", key, eventType);
    return null;
  }

  private static <T extends WebResource> boolean shouldPublish(boolean indexable,
      String eventType, ProducerSettings<T> settings) {
    return indexable && settings.incomingPublishedEventType().equals(eventType);
  }

  private static <T extends WebResource> boolean shouldUnpublish(boolean indexable,
      String eventType, ProducerSettings<T> settings) {
    return !indexable || settings.incomingUnpublishedEventType().equals(eventType);
  }

  private boolean isIndexable(CloudEvent event) {
    return Optional.ofNullable(event.getExtension(EXTENSION_NAME_INDEXABLE))
        .map(Object::toString)
        .map(Boolean::parseBoolean)
        .orElse(isIndexableDefault());
  }

  protected boolean isIndexableDefault() {
    return true;
  }

}
