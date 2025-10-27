package com.streamx.blueprints.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.Page;
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
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class IndexableResourceProducer extends AbstractIndexableResourceProducer<Page> {

  private static final UrlIncludeCollector urlIncludeCollector = new UrlIncludeCollector();
  private static final UrlIncludeRemover urlIncludeRemover = new UrlIncludeRemover();

  @Inject
  TikaParser parser;

  @Override
  protected ProducerSettings<Page> producerSettings() {
    return new ProducerSettings<>(
        Page.class,
        Page.TYPE_PUBLISHED,
        Page.TYPE_UNPUBLISHED,
        IndexableResource.TYPE_PUBLISHED,
        IndexableResource.TYPE_UNPUBLISHED
    );
  }

  @Incoming(Channels.INCOMING_PAGES)
  @Outgoing(Channels.INDEXABLE_RESOURCES)
  public CloudEvent produceFrom(CloudEvent event) {
    return produceIndexableResourceFromEvent(event);
  }

  @Override
  protected Object produceIndexableResource(Page incomingPage, String key) {
    String title = null;
    String content = null;
    if (hasContent(incomingPage)) {
      var indexableResourceContent = getIndexableResource(incomingPage);
      title = indexableResourceContent.title();
      content = indexableResourceContent.content();
    }
    var resourceTitle = StringUtil.isNullOrEmpty(title) ? key : title;
    var sourceResourceContent = StringUtil.isNullOrEmpty(content) ? key : content;
    var urlIncludes = urlIncludeCollector.collect(
        ByteBuffer.wrap(sourceResourceContent.getBytes()));

    var resourceContent = dropUrlIncludes(sourceResourceContent);
    var indexableResourceContent = new IndexableResourceContent(resourceTitle, resourceContent);

    var fragments = urlIncludes.stream()
        .map(UrlInclude::url)
        .collect(Collectors.toSet());

    log.tracef("Generated indexableResource fragments=%s payload=%s",
        fragments, indexableResourceContent);

    try {
      String json = objectMapper.writeValueAsString(indexableResourceContent);
      return new IndexableResource(json, incomingPage.getType(), fragments);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Payload could not be serialized.", e);
    }
  }

  private IndexableResourceContent getIndexableResource(Page page) {
    var input = new ByteArrayInputStream(page.getContentAsBytes());
    String title = null;
    TikaContent content = parser.parse(input);
    TikaMetadata metadata = content.getMetadata();

    List<String> titleMetadataValues = metadata.getValues("dc:title");
    if (titleMetadataValues != null && !titleMetadataValues.isEmpty()) {
      title = titleMetadataValues.getFirst();
    }
    String body = content.getText();

    log.tracef("Parsed page title and content.", title, body);

    return new IndexableResourceContent(title, body);
  }

  private String dropUrlIncludes(String sourceResourceContent) {
    ByteBuffer inputBuffer = ByteBuffer.wrap(sourceResourceContent.getBytes());
    ByteBuffer result = urlIncludeRemover.replace(inputBuffer);

    return new String(result.array());
  }

  private boolean hasContent(Page page) {
    return page != null && page.getContent() != null && page.getContent().array().length != 0;
  }

}
