package com.streamx.blueprints.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.sql.configuration.Configuration;
import com.streamx.blueprints.sql.configuration.Configuration.Transformation;
import com.streamx.blueprints.sql.database.IndexableResourcesRepository;
import com.streamx.blueprints.sql.database.IndexableSqlResources;
import io.cloudevents.CloudEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
  IndexableResourcesRepository indexableResourcesRepository;
  @Inject
  DirtySequenceStateManager dirtySequenceStateManager;
  @Inject
  protected Logger log;

  @Incoming(Channels.INDEXABLE_RESOURCES)
  public void produceFrom(CloudEvent event) {
    log.infof("Received CloudEvent: subject=%s", event.getSubject());
    IndexableResource resource = CloudEventUtils.getData(event, IndexableResource.class);
    if (resource == null) {
      return;
    }

    for (String transformationName : configuration.transformations().keySet()) {
      try {
        save(resource, transformationName);
        dirtySequenceStateManager.newDirtyResource();
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Payload could not be deserialized.", e);
      }
    }
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
        List<IndexableSqlResources> resources = indexableResourcesRepository.read(
            entry.getValue().sqlQuery());
        CloudEvent event = CloudEventUtils.eventWithData(
            entry.getKey(), Data.TYPE_PUBLISHED,
            new Data(objectMapper.writeValueAsString(Map.of("feeds", resources)), "data/json")
        );
        emitter.send(event);
        log.debugf("Send an event with subject %s", entry.getKey());
      }
    }
  }

  private void save(IndexableResource resource, String subject) throws JsonProcessingException {
    IndexableResourceContent indexableResourceContent = objectMapper.readValue(
        resource.getContentAsString(), IndexableResourceContent.class);

    log.debugf("Saving resource with subject %s", subject);
    indexableResourcesRepository.save(
        IndexableSqlResources.toEntity(subject,
            indexableResourceContent.title(),
            getFields(getConfiguredFields("fields"), indexableResourceContent.fields())));
  }

  private static Map<String, Object> getFields(List<String> configuredFields,
      Map<String, Object> content) {
    if (configuredFields.isEmpty()) {
      return content;
    }
    return content.entrySet()
        .stream()
        .filter(facet -> configuredFields.contains(facet.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private List<String> getConfiguredFields(String fieldName) {
    return Optional.ofNullable(configuration.persistedData().get(fieldName))
        .orElse(Collections.emptyList())
        .stream()
        .toList();
  }
}
