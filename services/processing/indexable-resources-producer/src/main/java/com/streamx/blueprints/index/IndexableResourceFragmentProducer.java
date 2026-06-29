package com.streamx.blueprints.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.data.JsonResource;
import com.streamx.blueprints.index.configuration.Configuration;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class IndexableResourceFragmentProducer extends AbstractIndexableResourceProducer<Fragment> {

  @Inject
  Configuration configuration;

  @Override
  protected ProducerSettings<Fragment> producerSettings() {
    return new ProducerSettings<>(
        Fragment.class,
        Fragment.TYPE_PUBLISHED,
        Fragment.TYPE_UNPUBLISHED,
        IndexableResourceFragment.TYPE_PUBLISHED,
        IndexableResourceFragment.TYPE_UNPUBLISHED
     );
  }

  @Incoming(Channels.INCOMING_FRAGMENTS)
  @Outgoing(Channels.INDEXABLE_RESOURCE_FRAGMENTS)
  public CloudEvent produceFrom(CloudEvent event) {
    return produceIndexableResourceFromEvent(event);
  }

  @Override
  protected JsonResource produceIndexableResource(Fragment incomingFragment, String key) {
    try {
      var content = incomingFragment.getContentAsString();
      var fragmentContent = new IndexableResourceFragmentContent(content);
      var json = objectMapper.writeValueAsString(fragmentContent);

      return new IndexableResourceFragment(json, incomingFragment.getType());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Payload could not be serialized.", e);
    }
  }

  @Override
  protected boolean isIndexableDefault() {
    return configuration.indexFragments();
  }

}
