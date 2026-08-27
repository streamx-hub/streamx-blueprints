package com.streamx.blueprints.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.sql.configuration.Configuration;
import com.streamx.blueprints.sql.configuration.Configuration.Transformation;
import io.cloudevents.CloudEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SqlTransformer {

  static final ObjectMapper objectMapper = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
  );

  @Inject
  @Channel(Channels.DATA)
  Emitter<CloudEvent> emitter;

  @Inject
  Configuration configuration;
  @Inject
  StateRepository indexableResourcesState;
  @Inject
  DirtySequenceStateManager dirtySequenceStateManager;
  @Inject
  protected Logger log;

  @Incoming(Channels.INDEXABLE_RESOURCES)
  public void produceFrom(CloudEvent event) {
    log.debugf("Received CloudEvent: subject=%s", event.getSubject());
    IndexableResource resource = CloudEventUtils.getData(event, IndexableResource.class);
    if (resource == null) {
      return;
    }

    dirtySequenceStateManager.newDirtyResource();
  }

  @Scheduled(
      every = "${streamx.blueprints.indexable-resources-sql-transformer.dirty-check.interval}",
      delayed = "${streamx.blueprints.indexable-resources-sql-transformer.dirty-check.delay}",
      concurrentExecution = ConcurrentExecution.SKIP
  )
  public void publishFeedsIfNeeded() throws JsonProcessingException {
    log.debugf("Publishing feeds");
    if (dirtySequenceStateManager.checkIfActionIsNeededForNewSequence()) {
      for (Map.Entry<String, Transformation> entry : configuration.transformations().entrySet()) {
        List<ResourceEntity> resources = indexableResourcesState.read(
            entry.getValue().sqlQuery());
        CloudEvent event = CloudEventUtils.eventWithData(
            entry.getKey(), Data.TYPE_PUBLISHED,
            new Data(objectMapper.writeValueAsString(Map.of("resources", resources)), "data/json")
        );
        emitter.send(event);
        log.debugf("Send an event with subject %s", entry.getKey());
      }
    }
  }

}
