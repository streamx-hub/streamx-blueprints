package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.data.collector.configuration.ServiceConfigMapping;
import com.streamx.blueprints.data.collector.stores.PublishedDataStore;
import io.cloudevents.CloudEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.common.annotation.NonBlocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
class ProcessDataFunction {

  @Inject
  Logger log;

  @Inject
  PublishedDataStore dataStore;

  @Inject
  @Channel(Channels.Outgoing.COLLECTED_DATA)
  Emitter<CloudEvent> dataEmitter;

  @Inject
  ServiceConfigMapping serviceConfigMapping;

  @Inject
  WebResourcesService webResourcesService;

  @Inject
  Collectors collectors;

  private final AtomicBoolean dirty = new AtomicBoolean(false);
  private final AtomicLong previouslyDirtyCount = new AtomicLong(0);

  @Incoming(Channels.Incoming.DATA)
  @Outgoing(Channels.Outgoing.WEB_RESOURCES)
  CloudEvent process(CloudEvent dataEvent) {
    dataStore.register(dataEvent);
    String key = CloudEventUtils.getSubject(dataEvent);
    Data data = CloudEventUtils.getData(dataEvent, Data.class);
    String eventType = dataEvent.getType();
    OffsetDateTime eventTime = dataEvent.getTime();

    log.tracef("Processing incoming key=%s eventType=%s eventTime=%s", key, eventType, eventTime);

    // To distribute the load for multiple replicas the 'dirty' logic should be done by
    // sending events and the 'generationNeeded' related logic and value should be in converter
    // and in store. The collectors.processData should return exact info which collector require
    // retrigger and the 'generate trigger' event should be sent for this specific collector;
    // then processing of those 'generate trigger' events could be distributed between many
    // replicas.
    dirty.set(collectors.processData(key, data, eventType));

    if (!webResourcesService.isMatchingFilter(key)) {
      log.tracef("Skipping sending incoming data with key=%s as web resource", key);
      return null;
    }

    String publishKey = webResourcesService.mapToWebResourceKey(key);
    log.tracef("Sending incoming data with key=%s as web resource with key=%s", key, publishKey);

    if (Data.TYPE_PUBLISHED.equals(eventType)) {
      if (Resource.isEmpty(data)) {
        log.warnf("Skipping processing publish event %s with no payload", key);
        return null;
      }
      return CloudEventUtils.eventCopyWithData(dataEvent, new WebResource(data.getContent()))
          .withSubject(publishKey)
          .withType(WebResource.TYPE_PUBLISHED)
          .build();
    }

    if (Data.TYPE_UNPUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventCopyWithoutData(dataEvent)
          .withSubject(publishKey)
          .withType(WebResource.TYPE_UNPUBLISHED)
          .build();
    }

    log.warnf("Skipping processing event %s of unexpected type: %s", key, eventType);
    return null;
  }

  @Scheduled(
      every = "${streamx.blueprints.data-collector.dirty-check.interval}",
      delayed = "${streamx.blueprints.data-collector.dirty-check.delay}",
      concurrentExecution = ConcurrentExecution.SKIP
  )
  @NonBlocking
  void trigger() {
    if (generationNeeded()) {
      // The nack handler should set back
      // 'dirty' to true in this service and in collections in collectors as it was before the
      // calling of the collectors.collect; to be verified while add the 'generate trigger' events.
      collectors.collect().forEach(
          collectedOutput -> dataEmitter.send(CloudEventUtils.eventWithData(
              collectedOutput.key(),
              Data.TYPE_PUBLISHED,
              new Data(collectedOutput.dataContent(), collectedOutput.dataType())
          )));
    }
  }

  private boolean generationNeeded() {
    boolean dirty = this.dirty.getAndSet(false);
    long count = previouslyDirtyCount.get();

    if (dirty) {
      if (count < serviceConfigMapping.dirtyCheck().maxDirtySequenceCount()) {
        log.debugf("Publication has been done. "
            + "Waiting for other publications for batch generation. "
            + "DirtySequenceCount = %s", count);
        previouslyDirtyCount.incrementAndGet();
        return false;
      }
      log.debugf("Another publication has been done. "
          + "However dirtySequenceThreshold was exceeded, so generation will be triggered");
      previouslyDirtyCount.set(0);
      return true;
    }

    if (count > 0) {
      log.debugf("There is no another publication, so batch generation "
          + "with previously published pages will be triggered");
      previouslyDirtyCount.set(0);
      return true;
    }

    log.debugf("There is no another publication and "
        + "no previously published data are waiting to trigger generation");
    return false;
  }
}
