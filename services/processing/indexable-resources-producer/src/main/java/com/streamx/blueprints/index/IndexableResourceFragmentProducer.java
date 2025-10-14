package com.streamx.blueprints.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.data.Resource;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class IndexableResourceFragmentProducer extends AbstractIndexableResourceProducer {

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Incoming(Channels.INCOMING_FRAGMENTS)
  @Outgoing(Channels.INDEXABLE_RESOURCE_FRAGMENTS)
  public CloudEvent produceFrom(CloudEvent event) {
    Fragment fragment = CloudEventUtils.getData(event, Fragment.class);
    String key = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    OffsetDateTime eventTime = event.getTime();

    log.tracef("Processing of incoming fragment with key=%s eventType=%s eventTime=%s",
        key, eventType, eventTime);

    boolean indexable = isIndexable(event);
    if (indexable && Fragment.TYPE_PUBLISHED.equals(eventType)) {
      if (Resource.isEmpty(fragment)) {
        log.warnf("Skipping processing empty incoming fragment %s", key);
        return null;
      }
      return CloudEventUtils.eventWithData(
          key,
          IndexableResourceFragment.TYPE_PUBLISHED,
          createIndexableResourceFragment(fragment),
          eventTime,
          CloudEventUtils.collectExtensions(event)
      );
    }
    return CloudEventUtils.eventWithoutData(
        key,
        IndexableResourceFragment.TYPE_UNPUBLISHED,
        eventTime,
        CloudEventUtils.collectExtensions(event)
    );
  }

  private IndexableResourceFragment createIndexableResourceFragment(Fragment fragment) {
    try {
      var content = fragment.getContentAsString();
      var fragmentContent = new IndexableResourceFragmentContent(content);
      var bytes = objectMapper.writeValueAsBytes(fragmentContent);

      return new IndexableResourceFragment(bytes);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Payload could not be serialized.", e);
    }
  }

  @Override
  protected boolean isIndexableDefault() {
    return configuration.indexFragments();
  }

  record IndexableResourceFragmentContent(String content) {

  }
}
