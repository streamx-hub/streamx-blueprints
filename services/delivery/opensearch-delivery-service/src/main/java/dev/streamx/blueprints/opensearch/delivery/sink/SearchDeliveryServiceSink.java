package dev.streamx.blueprints.opensearch.delivery.sink;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.UNPUBLISH;

import dev.streamx.blueprints.data.IndexableResource;
import dev.streamx.blueprints.opensearch.delivery.index.DefaultIndexUpdater;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SearchDeliveryServiceSink {

  public static final String CHANNEL_INDEXABLE_RESOURCES = "indexable-resources";

  @ConfigProperty(
      name = "streamx.blueprints.opensearch-delivery-service.type-required",
      defaultValue = "true")
  boolean typeRequired;

  @Inject
  Logger log;

  @Inject
  DefaultIndexUpdater defaultIndexUpdater;

  // Store for channel is needed to auto reject outdated messages from the channel.
  @FromChannel(CHANNEL_INDEXABLE_RESOURCES)
  Store<Long> indexableResourceEventTimeByKey;

  @Incoming(CHANNEL_INDEXABLE_RESOURCES)
  public Uni<Void> consume(IndexableResource resource,
      Key key, Action action, EventTime eventTime, Properties properties) {
    log.tracef("Indexing resource: key %s, action %s, event time %s, properties %s",
        key, action, eventTime, properties);

    var type = properties.getType().orElse(null);
    if (UNPUBLISH.equals(action) || StringUtils.isNotBlank(type) || !typeRequired) {
      return updateIndex(key, action, resource, type);
    }
    return Uni.createFrom().voidItem();
  }

  private Uni<Void> updateIndex(
      Key key, Action action, IndexableResource resource, String type) {
    Uni<Void> result;
    if (Action.PUBLISH.equals(action) && resource != null) {
      result = defaultIndexUpdater
          .add(key.getValue(), resource, key.getNamespace().orElse(null), type);
    } else if (UNPUBLISH.equals(action)) {
      result = defaultIndexUpdater.delete(key.getValue());
    } else {
      log.tracef("Skipping storing of page with action %s", action);
      result = Uni.createFrom().voidItem();
    }
    return result;
  }
}
