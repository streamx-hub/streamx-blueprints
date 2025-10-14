package dev.streamx.blueprints.index;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamx.blueprints.data.IndexableResource;
import dev.streamx.blueprints.data.Page;
import dev.streamx.content.parser.urlinclude.UrlInclude;
import dev.streamx.content.parser.urlinclude.UrlIncludeCollector;
import dev.streamx.content.parser.urlinclude.UrlIncludeRemover;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.quarkus.runtime.util.StringUtil;
import io.quarkus.tika.TikaContent;
import io.quarkus.tika.TikaMetadata;
import io.quarkus.tika.TikaParser;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class IndexableResourceProducer extends AbstractIndexableResourceProducer {

  public static final String CHANNEL_PAGES = "pages";
  public static final String CHANNEL_INDEXABLE_RESOURCES = "indexable-resources";

  @Inject
  Logger log;

  @Inject
  TikaParser parser;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  UrlIncludeCollector urlIncludeCollector;

  @Inject
  UrlIncludeRemover urlIncludeRemover;

  @Incoming(CHANNEL_PAGES)
  @Outgoing(CHANNEL_INDEXABLE_RESOURCES)
  public GenericPayload<IndexableResource> produceFrom(Page payload, Key key, Action action,
      EventTime eventTime, Properties properties) {
    log.tracef("Processing of incoming key=%s action=%s eventTime=%s payload=%s",
        key, action, eventTime, payload);
    boolean indexable = isIndexable(properties);
    if (indexable && Action.PUBLISH.equals(action)) {
      return GenericPayload.of(getIndexableResource(payload, key.getValue(), action));
    } else {
      return GenericPayload.of((IndexableResource) null)
          .withMetadata(Metadata.of(Action.UNPUBLISH));
    }
  }

  private IndexableResource getIndexableResource(Page page, String key, Action action) {
    if (Action.UNPUBLISH.equals(action)) {
      return null;
    } else {
      String title = null;
      String content = null;
      if (hasContent(page)) {
        var indexableResourceContent = getIndexableResource(page);
        title = indexableResourceContent.getTitle();
        content = indexableResourceContent.getContent();
      }
      var resourceTitle = StringUtil.isNullOrEmpty(title) ? key : title;
      var sourceResourceContent = StringUtil.isNullOrEmpty(content) ? key : content;
      var urlIncludes = urlIncludeCollector.collect(
          ByteBuffer.wrap(sourceResourceContent.getBytes(StandardCharsets.UTF_8)));

      var resourceContent = dropUrlIncludes(sourceResourceContent);
      var indexableResourceContent = new IndexableResourceContent(resourceTitle, resourceContent);

      var fragments = urlIncludes.stream()
          .map(UrlInclude::url)
          .collect(Collectors.toSet());

      log.tracef("Generated indexableResource fragments=%s payload=%s",
          fragments, indexableResourceContent);

      try {
        byte[] bytes = objectMapper.writeValueAsBytes(indexableResourceContent);

        return new IndexableResource(bytes, fragments);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Payload could not be serialized.", e);
      }
    }
  }

  private IndexableResourceContent getIndexableResource(Page page) {
    try (var input = new ByteArrayInputStream(page.getContent().array())) {
      String title = null;
      String body;
      TikaContent content = parser.parse(input);
      TikaMetadata metadata = content.getMetadata();

      List<String> titleMetadataValues = metadata.getValues("dc:title");
      if (titleMetadataValues != null && !titleMetadataValues.isEmpty()) {
        title = titleMetadataValues.get(0);
      }
      body = content.getText();

      log.tracef("Parsed page title and content.", title, body);

      return new IndexableResourceContent(title, body);
    } catch (IOException e) {
      throw new RuntimeException("Unable to parse metadata", e);
    }
  }

  private String dropUrlIncludes(String sourceResourceContent) {
    ByteBuffer inputBuffer = ByteBuffer.wrap(sourceResourceContent.getBytes(UTF_8));
    ByteBuffer result = urlIncludeRemover.replace(inputBuffer);

    return new String(result.array(), UTF_8);
  }

  private boolean hasContent(Page page) {
    return page != null && page.getContent() != null && page.getContent().array().length != 0;
  }

  static final class IndexableResourceContent {

    private String title;
    private String content;

    public IndexableResourceContent() {
    }

    public IndexableResourceContent(String title, String content) {
      this.title = title;
      this.content = content;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }
  }
}
