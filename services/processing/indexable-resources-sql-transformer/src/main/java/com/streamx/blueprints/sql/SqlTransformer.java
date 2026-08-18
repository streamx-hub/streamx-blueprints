package com.streamx.blueprints.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.sql.configuration.Configuration;
import com.streamx.blueprints.sql.configuration.Configuration.Transformation;
import com.streamx.blueprints.sql.database.IndexableResourcesRepository;
import com.streamx.blueprints.sql.database.IndexableSqlResources;
import io.cloudevents.CloudEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

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

  @Incoming(Channels.INDEXABLE_RESOURCES)
  public void produceFrom(CloudEvent event) {
    IndexableResource resource = CloudEventUtils.getData(event, IndexableResource.class);
    if (resource == null) {
      return;
    }

    for (String transformationName : configuration.transformations().keySet()) {
      try {
        save(resource, transformationName);
      } catch (JsonProcessingException | SQLException e) {
        throw new RuntimeException(e);
      }
    }
    dirtySequenceStateManager.newDirtyResource();
  }

  @Scheduled(
      every = "${streamx.blueprints.indexable-resources-sql-transformer.dirty-check.interval}",
      delayed = "${streamx.blueprints.indexable-resources-sql-transformer.dirty-check.delay}",
      concurrentExecution = ConcurrentExecution.SKIP
  )
  public void publishFeedsIfNeeded() {
    if (dirtySequenceStateManager.checkIfActionIsNeededForNewSequence()) {
      // Serving JSON to outgoing channel
      for (Map.Entry<String, Transformation> entry : configuration.transformations().entrySet()) {
        indexableResourcesRepository.read(entry.getValue().sqlQuery());
      }


      CloudEvent sitemapEvent = CloudEventUtils.eventWithData(
          configuration.outputKey(), WebResource.TYPE_PUBLISHED, sitemapWebResource
      );
      emitter.send(sitemapEvent);
    }
  }

  private void save(IndexableResource resource, String subject)
      throws JsonProcessingException, SQLException {
    IndexableResourceContent indexableResourceContent = objectMapper.readValue(
        resource.getContentAsString(), IndexableResourceContent.class);

    indexableResourcesRepository.save(new IndexableSqlResources(subject,
        indexableResourceContent.title(),
        indexableResourceContent.content(),
        getFields(getConfiguredFields("facets"), indexableResourceContent.facets()),
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
