package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.collector.configuration.ServiceConfigMapping;
import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
class ProcessDataFunction {

  @Inject
  Logger log;

  @Inject
  @Channel(Channels.Outgoing.COLLECTED_DATA)
  Emitter<Data> dataEmitter;

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
  GenericPayload<WebResource> process(Data data, Key key, Action action, EventTime eventTime,
      Properties properties) {
    GenericPayload<WebResource> outgoing;
    log.tracef("Processing of incoming key=%s action=%s eventTime=%s", key, action, eventTime);
    // To distribute the load for multiple replicas the 'dirty' logic should be done by
    // sending events and the 'generationNeeded' related logic and value should be in converter
    // and in store. The collectors.processData should return exact info which collector require
    // retrigger and the 'generate trigger' event should be sent for this specific collector;
    // then processing of those 'generate trigger' events could be distributed between many
    // replicas.
    dirty.set(collectors.processData(key, data, action, properties));
    if (webResourcesService.isMatchingFilter(key)) {
      log.tracef("Sending incoming data with key=%s as web resource", key);
      outgoing = GenericPayload.of(
          Action.PUBLISH.equals(action) ? new WebResource(data.getContent()) : null,
          Metadata.of(Key.of(webResourcesService.mapToWebResourceKey(key))));
    } else {
      log.tracef("Skipping sending incoming data with key=%s as web resource", key);
      outgoing = null;
    }
    return outgoing;
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
          collectedOutput -> dataEmitter.send(Message.of(
                  collectedOutput.data(),
                  Metadata.of(
                      collectedOutput.key(),
                      Action.PUBLISH,
                      EventTime.of(System.currentTimeMillis()),
                      Properties.empty().withType(collectedOutput.type()))
              )
          ));
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
