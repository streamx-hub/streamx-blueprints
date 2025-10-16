package dev.streamx.blueprints.opensearch.delivery.sink;

import dev.streamx.blueprints.data.IndexableResourceFragment;
import dev.streamx.blueprints.opensearch.delivery.index.DefaultIndexUpdater;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FragmentSearchDeliveryServiceSink {

  public static final String CHANNEL_INDEXABLE_RESOURCE_FRAGMENTS = "indexable-resource-fragments";

  @Inject
  Logger log;

  @Inject
  DefaultIndexUpdater defaultIndexUpdater;

  @Incoming(CHANNEL_INDEXABLE_RESOURCE_FRAGMENTS)
  public Uni<Void> consume(IndexableResourceFragment resource,
      Key key, Action action, EventTime eventTime) {
    log.tracef("Indexing resource fragment: key %s, action %s, event time %s",
        key, action, eventTime);

    return updateIndex(key.getValue(), action, eventTime.getValue(), resource);
  }

  private Uni<Void> updateIndex(String key, Action action, long eventTime,
      IndexableResourceFragment resource) {
    return defaultIndexUpdater.updateFragment(key, action, eventTime, resource);
  }
}
