package com.streamx.blueprints.opensearch.sink;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.opensearch.sink.index.DefaultIndexUpdater;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FragmentSearchServiceSink {

  @Inject
  Logger log;

  @Inject
  DefaultIndexUpdater defaultIndexUpdater;

  @Incoming(Channels.INDEXABLE_RESOURCE_FRAGMENTS)
  public Uni<Void> consume(CloudEvent event) {
    String key = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    OffsetDateTime eventTime = event.getTime();

    log.tracef("Indexing resource fragment: key %s, event type %s, event time %s",
        key, eventType, eventTime);

    var resource = CloudEventUtils.getData(event, IndexableResourceFragment.class);
    return defaultIndexUpdater.updateFragment(key, eventType, eventTime, resource);
  }

}