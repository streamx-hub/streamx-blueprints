package dev.streamx.blueprints.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamx.blueprints.data.Fragment;
import dev.streamx.blueprints.data.IndexableResourceFragment;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class IndexableResourceFragmentProducer extends AbstractIndexableResourceProducer {

  public static final String CHANNEL_FRAGMENTS = "fragments";
  public static final String CHANNEL_INDEXABLE_RESOURCE_FRAGMENTS = "indexable-resource-fragments";

  public static final String PN_INDEX_FRAGMENTS =
      "streamx.blueprints.indexable-resources-producer.index-fragments";

  @Inject
  Logger log;

  @Inject
  ObjectMapper objectMapper;

  @ConfigProperty(name = PN_INDEX_FRAGMENTS, defaultValue = "false")
  boolean indexFragmentsByDefault;

  @Incoming(CHANNEL_FRAGMENTS)
  @Outgoing(CHANNEL_INDEXABLE_RESOURCE_FRAGMENTS)
  public GenericPayload<IndexableResourceFragment> produceFrom(
      Fragment payload,
      Key key,
      Action action,
      EventTime eventTime,
      Properties properties) {
    if (log.isTraceEnabled()) {
      log.tracef(
          "Processing of incoming fragment with "
              + "key=%s action=%s eventTime=%s payload=%s, properties=%s",
          key, action, eventTime, payload, properties);
    }

    boolean indexable = isIndexable(properties);

    if (indexable && Action.PUBLISH.equals(action)) {
      return GenericPayload.of(createPublishedFragmentContent(payload));
    } else {
      return GenericPayload.of((IndexableResourceFragment) null)
          .withMetadata(Metadata.of(Action.UNPUBLISH));
    }
  }

  private IndexableResourceFragment createPublishedFragmentContent(Fragment fragment) {
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
    return indexFragmentsByDefault;
  }

  static final class IndexableResourceFragmentContent {
    private String content;

    public IndexableResourceFragmentContent() {
    }

    public IndexableResourceFragmentContent(String content) {
      this.content = content;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }
  }
}
