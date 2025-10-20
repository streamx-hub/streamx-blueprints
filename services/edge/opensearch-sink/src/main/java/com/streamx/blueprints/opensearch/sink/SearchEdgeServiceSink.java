package com.streamx.blueprints.opensearch.sink;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.opensearch.sink.index.DefaultIndexUpdater;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SearchEdgeServiceSink {

  @ConfigProperty(
      name = "streamx.blueprints.opensearch-sink.type-required",
      defaultValue = "true")
  boolean typeRequired;

  @Inject
  Logger log;

  @Inject
  DefaultIndexUpdater defaultIndexUpdater;

  @Incoming(Channels.INDEXABLE_RESOURCES)
  public Uni<Void> consume(CloudEvent event) {
    IndexableResource resource = CloudEventUtils.getData(event, IndexableResource.class);
    String key = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    OffsetDateTime eventTime = event.getTime();
    String resourceType = Optional.ofNullable(resource).map(Resource::getType).orElse(null);

    log.tracef("Indexing resource: key %s, event type %s, event time %s, resource type %s",
        key, eventType, eventTime, resourceType);

    if (isUnpublish(eventType) || StringUtils.isNotBlank(resourceType) || !typeRequired) {
      return updateIndex(key, eventType, resource, resourceType);
    }
    return Uni.createFrom().voidItem();
  }

  private Uni<Void> updateIndex(
      String key, String eventType, IndexableResource resource, String resourceType) {
    if (isPublish(eventType) && resource != null) {
      String namespace = CloudEventUtils.getSubjectNamespace(key).orElse(null);
      return defaultIndexUpdater.add(key, resource, namespace, resourceType);
    }

    if (isUnpublish(eventType)) {
      return defaultIndexUpdater.delete(key);
    }

    log.tracef("Skipping storing of page with event type %s", eventType);
    return Uni.createFrom().voidItem();
  }

  private static boolean isPublish(String eventType) {
    return IndexableResource.TYPE_PUBLISHED.equals(eventType);
  }

  private static boolean isUnpublish(String eventType) {
    return IndexableResource.TYPE_UNPUBLISHED.equals(eventType);
  }
}