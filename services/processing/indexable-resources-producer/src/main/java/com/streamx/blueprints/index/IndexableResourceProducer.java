package com.streamx.blueprints.index;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.content.parser.urlinclude.UrlInclude;
import com.streamx.content.parser.urlinclude.UrlIncludeCollector;
import com.streamx.content.parser.urlinclude.UrlIncludeRemover;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.util.StringUtil;
import io.quarkus.tika.TikaContent;
import io.quarkus.tika.TikaMetadata;
import io.quarkus.tika.TikaParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class IndexableResourceProducer extends AbstractIndexableResourceProducer {

  private static final UrlIncludeCollector urlIncludeCollector = new UrlIncludeCollector();
  private static final UrlIncludeRemover urlIncludeRemover = new UrlIncludeRemover();

  @Inject
  Logger log;

  @Inject
  TikaParser parser;

  @Incoming(Channels.INCOMING_PAGES)
  @Outgoing(Channels.INDEXABLE_RESOURCES)
  public CloudEvent produceFrom(CloudEvent event) {
    Page page = CloudEventUtils.getData(event, Page.class);
    String key = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    OffsetDateTime eventTime = event.getTime();

    log.tracef("Processing of incoming page with key=%s eventType=%s eventTime=%s",
        key, eventType, eventTime);

    boolean indexable = isIndexable(event);
    if (indexable && Page.TYPE_PUBLISHED.equals(eventType)) {
      if (Resource.isEmpty(page)) {
        log.warnf("Skipping processing empty incoming page %s", key);
        return null;
      }
      return CloudEventUtils.eventWithData(
          key,
          IndexableResource.TYPE_PUBLISHED,
          getIndexableResource(page, key),
          eventTime
      );
    }
    return CloudEventUtils.eventWithoutData(
        key,
        IndexableResource.TYPE_UNPUBLISHED,
        eventTime
    );
  }

  private IndexableResource getIndexableResource(Page page, String key) {
    String title = null;
    String content = null;
    if (hasContent(page)) {
      var indexableResourceContent = getIndexableResource(page);
      title = indexableResourceContent.title();
      content = indexableResourceContent.content();
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

  private IndexableResourceContent getIndexableResource(Page page) {
    var input = new ByteArrayInputStream(page.getContent().array());
    String title = null;
    TikaContent content = parser.parse(input);
    TikaMetadata metadata = content.getMetadata();

    List<String> titleMetadataValues = metadata.getValues("dc:title");
    if (titleMetadataValues != null && !titleMetadataValues.isEmpty()) {
      title = titleMetadataValues.get(0);
    }
    String body = content.getText();

    log.tracef("Parsed page title and content.", title, body);

    return new IndexableResourceContent(title, body);
  }

  private String dropUrlIncludes(String sourceResourceContent) {
    ByteBuffer inputBuffer = ByteBuffer.wrap(sourceResourceContent.getBytes(UTF_8));
    ByteBuffer result = urlIncludeRemover.replace(inputBuffer);

    return new String(result.array(), UTF_8);
  }

  private boolean hasContent(Page page) {
    return page != null && page.getContent() != null && page.getContent().array().length != 0;
  }

  record IndexableResourceContent(String title, String content) {

  }
}
