package com.streamx.blueprints.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventTestUtils;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class IndexableResourceProducerTest {

  private static final String DEFAULT_KEY = "/test.html";
  private static final String RESOURCE_TYPE = "any";

  private InMemorySource<CloudEvent> pagesSource;
  private InMemorySink<CloudEvent> indexableResourceSink;

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() {
    pagesSource = connector.source(Channels.INCOMING_PAGES);
    indexableResourceSink = connector.sink(Channels.INDEXABLE_RESOURCES);
    indexableResourceSink.clear();
  }

  @Test
  void expectHtmlPageBeProcessed() {
    // given
    String payload = """
        <!DOCTYPE html>
        <html>
            <head>
                <title>Hello Title</title>
                <meta property="facets:technology" content="salesforce">
            </head>
            <body>
                <h1>Hello H1</h1>
                <h2>Hello H2</h2>
                <p>Hello paragraph</p>
            </body>
        </html>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.content()).contains(
        "Hello Title",
        "Hello H1",
        "Hello H2",
        "Hello paragraph"
    );
    assertThat(result.facets()).containsEntry("technology_level0", "salesforce");
  }

  @Test
  void expectPartialHtmlBeProcessed() {
    // given
    String payload = """
        <head>
            <title>Hello Title</title>
        </head>
        <body>
            <h1>Hello H1</h1>
            <h2>Hello H2</h2>
            <p>Hello paragraph</p>
        </body>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.content()).contains(
        "Hello Title",
        "Hello H1",
        "Hello H2",
        "Hello paragraph"
    );
  }

  @Test
  void expectInvalidHtmlTitleBeProcessed() {
    // given
    String payload = """
            <title>Hello Title</title>
        </head>
            <h1>Hello H1</h1>
            <h2>Hello H2</h2>
            <p>Hello paragraph</p>
        </body>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.content()).contains(
        "Hello Title",
        "Hello H1",
        "Hello H2",
        "Hello paragraph"
    );
  }

  @Test
  void expectDocumentWithNoTitleBeProcessed() {
    // given
    String payload = """
        <body>
            <h1>Hello H1</h1>
            <h2>Hello H2</h2>
            <p>Hello paragraph</p>
        </body>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo(DEFAULT_KEY);
    assertThat(result.content()).contains(
        "Hello H1",
        "Hello H2",
        "Hello paragraph"
    );
  }

  @Test
  void expectDocumentWithNoBodyBeProcessed() {
    // given
    String payload = """
        <head>
            <title>Hello Title</title>
        </head>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.content()).isEqualTo("Hello Title");
  }

  @Test
  void expectTextDocumentBeProcessed() {
    // given
    String payload = """
        Hello Text Content
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo(DEFAULT_KEY);
    assertThat(result.content()).isEqualTo("Hello Text Content");
  }

  @Test
  void expectEmptyDocumentBeProcessed() {
    // given
    String payload = "";

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo(DEFAULT_KEY);
    assertThat(result.content()).isEqualTo(DEFAULT_KEY);
  }

  @Test
  void expectNonAsciiPayloadBeProcessed() {
    // given
    String payload = new String(new byte[]{1, 2, 3});

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo(DEFAULT_KEY);
    assertThat(result.content()).isEqualTo(DEFAULT_KEY);
  }

  @Test
  void expectPageUnpublishBeProcessed() {
    // given
    CloudEvent pageEvent = CloudEventUtils.eventWithoutData(
        DEFAULT_KEY,
        Page.TYPE_UNPUBLISHED,
        CloudEventUtils.toOffsetDateTime(1)
    );

    // when
    CloudEvent resultEvent = getResourceFrom(pageEvent);

    // then
    assertThat(resultEvent).isNotNull();
    assertThat(resultEvent.getData()).isNull();
    assertThat(resultEvent.getSubject()).isEqualTo(DEFAULT_KEY);
    assertThat(resultEvent.getTime()).isEqualTo(pageEvent.getTime());
    assertThat(resultEvent.getType()).isEqualTo(IndexableResource.TYPE_UNPUBLISHED);
  }

  @Test
  void positiveIndexablePropertyExpectPayloadProcessed() {
    // given
    String payload = "Not Indexable Content";

    CloudEvent pageEvent = CloudEventTestUtils.cloudEventWithExtensions(
        DEFAULT_KEY,
        Page.TYPE_PUBLISHED,
        new Page(payload, RESOURCE_TYPE),
        Map.of(AbstractIndexableResourceProducer.EXTENSION_NAME_INDEXABLE, "true")
    );

    // when
    CloudEvent resultEvent = getResourceFrom(pageEvent);

    // then
    assertThat(resultEvent).isNotNull();
    assertThat(resultEvent.getData()).isNotNull();
    assertThat(resultEvent.getSubject()).isEqualTo(DEFAULT_KEY);
    assertThat(resultEvent.getTime()).isEqualTo(pageEvent.getTime());
    assertThat(resultEvent.getType()).isEqualTo(IndexableResource.TYPE_PUBLISHED);
    assertThat(getIndexableResource(resultEvent).content()).isEqualTo(payload);
  }

  @Test
  void negativeIndexablePropertySkipsPayloadFromBeingProcessed() {
    // given
    String payload = "Not Indexable Content";

    CloudEvent pageEvent = CloudEventTestUtils.cloudEventWithExtensions(
        DEFAULT_KEY,
        Page.TYPE_PUBLISHED,
        new Page(payload, RESOURCE_TYPE),
        Map.of(AbstractIndexableResourceProducer.EXTENSION_NAME_INDEXABLE, "false")
    );

    // when
    CloudEvent resultEvent = getResourceFrom(pageEvent);

    // then
    assertThat(resultEvent).isNotNull();
    assertThat(resultEvent.getData()).isNull();
    assertThat(resultEvent.getSubject()).isEqualTo(DEFAULT_KEY);
    assertThat(resultEvent.getTime()).isEqualTo(pageEvent.getTime());
    assertThat(resultEvent.getType()).isEqualTo(IndexableResource.TYPE_UNPUBLISHED);
  }

  @Test
  void shouldSkipProcessingPagePublishedWithoutContent() {
    // given
    CloudEvent pageEvent = CloudEventUtils.eventWithoutData(
        DEFAULT_KEY,
        Page.TYPE_PUBLISHED
    );

    // when & then
    assertNoResourceFrom(pageEvent);
  }

  @Test
  void shouldSkipProcessingEventWithUnexpectedType() {
    // given
    CloudEvent pageEvent = CloudEventUtils.eventWithData(
        DEFAULT_KEY,
        WebResource.TYPE_PUBLISHED,
        new Page("content", RESOURCE_TYPE)
    );

    // when & then
    assertNoResourceFrom(pageEvent);
  }

  @Test
  void expectHtmlPageWithTwoFacetsBeProcessed() {
    // given
    String payload = """
        <!DOCTYPE html>
        <html>
            <head>
                <title>Hello Title</title>
                <meta property="facets:technology" content="salesforce">
            </head>
            <body>
                <div class="test" color="red">
            </body>
        </html>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.facets()).containsEntry("technology_level0", "salesforce");
    assertThat(result.facets()).containsEntry("color_level0", "red");
  }

  @Test
  void expectHtmlPageWithTwoSameTypeFacetsBeProcessed() {
    // given
    String payload = """
        <!DOCTYPE html>
        <html>
            <head>
                <title>Hello Title</title>
                <meta property="facets:technology" content="salesforce">
                <meta property="facets:category" content="shoes">                
            </head>
        </html>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.facets()).containsEntry("technology_level0", "salesforce");
    assertThat(result.facets()).containsEntry("category_level0", "shoes");
  }

  @Test
  void expectHtmlPageWithHierarchicalFacetBeProcessed() {
    // given
    String payload = """
        <!DOCTYPE html>
        <html>
            <head>
                <title>Hello Title</title>
                <meta name="facets:category" value="Electronics>Phone>iOS">
            </head>
            <body>
                <h1>Hello H1</h1>
            </body>
        </html>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.facets()).containsEntry("category_path", "Electronics>Phone>iOS");
    assertThat(result.facets()).containsEntry("category_level0", "Electronics");
    assertThat(result.facets()).containsEntry("category_level1", "Phone");
    assertThat(result.facets()).containsEntry("category_level2", "iOS");
    assertThat(result.facets()).containsEntry("category_hierarchy", List.of("Electronics",
        "Electronics>Phone",
        "Electronics>Phone>iOS"));
  }

  @Test
  void expectHtmlPageWithTwoFieldsBeProcessed() {
    // given
    String payload = """
        <!DOCTYPE html>
        <html>
            <head>
                <title>Hello Title</title>
                <meta property="description" content="test">
            </head>
            <body>
            </body>
        </html>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.fields()).containsEntry("description", "test");
  }

  @Test
  void expectHtmlPageWithFunctionSelectorBeProcessed() {
    // given
    String payload = """
        <!DOCTYPE html>
        <html>
            <head>
                <title>Hello Title</title>
            </head>
            <body>
                <div name="description">M</div>
            </body>
        </html>
        """;

    // when
    var result = getResourceFromPageWithContent(payload);

    // then
    assertThat(result.title()).isEqualTo("Hello Title");
    assertThat(result.facets()).containsEntry("description_path", "M");
  }

  @Test
  void shouldSkipProcessingEventWithRobotsNoIndex() {
    // given
    String payload = """
        <!DOCTYPE html>
        <html>
            <head>
                <title>Hello Title</title>
                <meta name="robots" content="nofollow, noindex">
            </head>
            <body>
            </body>
        </html>
        """;

    CloudEvent pageEvent = CloudEventUtils.eventWithData(
        DEFAULT_KEY,
        Page.TYPE_PUBLISHED,
        new Page(payload, RESOURCE_TYPE)
    );

    // when & then
    assertNoResourceFrom(pageEvent);
  }

  private IndexableResourceContent getResourceFromPageWithContent(String payload) {
    CloudEvent pageEvent = CloudEventUtils.eventWithData(
        DEFAULT_KEY,
        Page.TYPE_PUBLISHED,
        new Page(payload, RESOURCE_TYPE),
        CloudEventUtils.toOffsetDateTime(1)
    );

    CloudEvent resultEvent = getResourceFrom(pageEvent);

    assertThat(resultEvent).isNotNull();
    assertThat(resultEvent.getData()).isNotNull();
    assertThat(resultEvent.getSubject()).isEqualTo(DEFAULT_KEY);
    assertThat(resultEvent.getTime()).isEqualTo(pageEvent.getTime());
    assertThat(resultEvent.getType()).isEqualTo(IndexableResource.TYPE_PUBLISHED);

    return getIndexableResource(resultEvent);
  }

  private IndexableResourceContent getIndexableResource(CloudEvent indexableResourceEvent) {
    IndexableResource indexableResource = CloudEventUtils.getData(indexableResourceEvent,
        IndexableResource.class);
    assertThat(indexableResource).isNotNull();
    assertThat(indexableResource.getType()).isEqualTo(RESOURCE_TYPE);
    String json = indexableResource.getContentAsString();
    try {
      return objectMapper.readValue(json, IndexableResourceContent.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private CloudEvent getResourceFrom(CloudEvent pageEvent) {
    pagesSource.send(pageEvent);
    await().until(() -> indexableResourceSink.received().size() == 1);
    return indexableResourceSink.received().getFirst().getPayload();
  }

  private void assertNoResourceFrom(CloudEvent pageEvent) {
    pagesSource.send(pageEvent);
    await()
        .during(Duration.ofMillis(500))
        .untilAsserted(() -> assertThat(indexableResourceSink.received()).isEmpty());
  }
}
