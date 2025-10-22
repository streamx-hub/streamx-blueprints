package com.streamx.blueprints.event.converter;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.Resource;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ResourceToIndexableResourceConverter {

  @Inject
  Logger log;

  @Incoming(Channels.RESOURCES)
  @Outgoing(Channels.INDEXABLE_RESOURCES)
  public CloudEvent processMessage(CloudEvent incomingEvent) {
    String key = CloudEventUtils.getSubject(incomingEvent);
    String eventType = incomingEvent.getType();
    log.tracef("Converting event with key %s, type %s and time %s to IndexableResource event",
        key, eventType, incomingEvent.getTime());

    Resource incomingResource;
    try {
      incomingResource = CloudEventUtils.getData(incomingEvent, Resource.class);
    } catch (IllegalStateException e) {
      log.warnf(e, "Cannot extract %s from event %s of type %s", Resource.class, key, eventType);
      return null;
    }

    if (CloudEventUtils.isPublishingType(eventType)) {
      return publishedEvent(incomingEvent, incomingResource);
    }

    if (CloudEventUtils.isUnpublishingType(eventType)) {
      return unpublishedEvent(incomingEvent);
    }

    return skippedEvent(incomingEvent);
  }

  private CloudEvent publishedEvent(CloudEvent incomingEvent, Resource incomingResource) {
    if (Resource.isEmpty(incomingResource)) {
      log.warnf("Cannot produce an IndexableResource from empty Resource %s of type %s",
          incomingEvent.getSubject(), incomingEvent.getType());
      return null;
    }

    IndexableResource indexableResource = convertToIndexableResource(incomingResource);
    return CloudEventUtils.eventCopyWithData(incomingEvent, indexableResource)
        .withType(IndexableResource.TYPE_PUBLISHED)
        .build();
  }

  private static IndexableResource convertToIndexableResource(Resource incomingResource) {
    return new IndexableResource(
        incomingResource.getContent(),
        incomingResource.getType(),
        Collections.emptySet()
    );
  }

  private static CloudEvent unpublishedEvent(CloudEvent incomingEvent) {
    return CloudEventUtils.eventCopyWithoutData(incomingEvent)
        .withType(IndexableResource.TYPE_UNPUBLISHED)
        .build();
  }

  private CloudEvent skippedEvent(CloudEvent incomingEvent) {
    log.warnf("Received event %s of unexpected type %s", incomingEvent.getSubject(),
        incomingEvent.getType());
    return null;
  }
}
