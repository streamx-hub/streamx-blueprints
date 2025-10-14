package dev.streamx.blueprints.index;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static dev.streamx.quasar.reactive.messaging.metadata.Action.UNPUBLISH;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractEventTime;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamx.blueprints.data.IndexableResource;
import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.index.IndexableResourceProducer.IndexableResourceContent;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.IOException;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class IndexableResourceProducerTest {

  InMemorySource<Message<Page>> pagesSource;
  InMemorySink<IndexableResource> indexableResourceSink;

  @Inject
  @Any
  InMemoryConnector connector;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void beforeEach() {
    pagesSource = connector.source(IndexableResourceProducer.CHANNEL_PAGES);
    indexableResourceSink = connector.sink(IndexableResourceProducer.CHANNEL_INDEXABLE_RESOURCES);
    indexableResourceSink.clear();
  }

  @Test
  void expectHtmlPageBeProcessed() {
    String payload = """
        <!DOCTYPE html>
        <html>
            <head>
                <title>Hello Title</title>
            </head>
            <body>
                <h1>Hello H1</h1>
                <h2>Hello H2</h2>
                <p>Hello paragraph</p>
            </body>
        </html>
        """;
    var result = getResourceFromPageWithContent(payload);
    assertEquals("Hello Title", result.getTitle());
    assertTrue(result.getContent().contains("Hello Title"));
    assertTrue(result.getContent().contains("Hello H1"));
    assertTrue(result.getContent().contains("Hello H2"));
    assertTrue(result.getContent().contains("Hello paragraph"));
  }

  @Test
  void expectPartialHtmlBeProcessed() {
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
    var result = getResourceFromPageWithContent(payload);
    assertEquals("Hello Title", result.getTitle());
    assertTrue(result.getContent().contains("Hello Title"));
    assertTrue(result.getContent().contains("Hello H1"));
    assertTrue(result.getContent().contains("Hello H2"));
    assertTrue(result.getContent().contains("Hello paragraph"));
  }

  @Test
  void expectInvalidHtmlTitleBeProcessed() {
    String payload = """
            <title>Hello Title</title>
        </head>
            <h1>Hello H1</h1>
            <h2>Hello H2</h2>
            <p>Hello paragraph</p>
        </body>
        """;
    var result = getResourceFromPageWithContent(payload);
    assertEquals("Hello Title", result.getTitle());
    assertTrue(result.getContent().contains("Hello Title"));
    assertTrue(result.getContent().contains("Hello H1"));
    assertTrue(result.getContent().contains("Hello H2"));
    assertTrue(result.getContent().contains("Hello paragraph"));
  }

  @Test
  void expectDocumentWithNoTitleBeProcessed() {
    String payload = """
        <body>
            <h1>Hello H1</h1>
            <h2>Hello H2</h2>
            <p>Hello paragraph</p>
        </body>
        """;
    var result = getResourceFromPageWithContent(payload);
    assertEquals("/test.html", result.getTitle());
    assertTrue(result.getContent().contains("Hello H1"));
    assertTrue(result.getContent().contains("Hello H2"));
    assertTrue(result.getContent().contains("Hello paragraph"));
  }

  @Test
  void expectDocumentWithNoBodyBeProcessed() {
    String payload = """
        <head>
            <title>Hello Title</title>
        </head>
        """;
    var result = getResourceFromPageWithContent(payload);
    assertEquals("Hello Title", result.getTitle());
    assertEquals("Hello Title", result.getContent());
  }

  @Test
  void expectTextDocumentBeProcessed() {
    String payload = """
        Hello Text Content
        """;
    var result = getResourceFromPageWithContent(payload);
    assertEquals("/test.html", result.getTitle());
    assertEquals("Hello Text Content", result.getContent());
  }

  @Test
  void expectEmptyDocumentBeProcessed() {
    String payload = "";
    var result = getResourceFromPageWithContent(payload);
    assertEquals("/test.html", result.getTitle());
    assertEquals("/test.html", result.getContent());
  }

  @Test
  void expectNonAsciiPayloadBeProcessed() {
    String payload = new String(new byte[]{1, 2, 3}, UTF_8);
    var result = getResourceFromPageWithContent(payload);
    assertEquals("/test.html", result.getTitle());
    assertEquals("/test.html", result.getContent());
  }

  @Test
  void expectPageUnpublishBeProcessed() {
    String key = "/test.html";
    Long eventTime = 1L;
    Action action = UNPUBLISH;

    Message<IndexableResource> message = getResourceFrom(Message.of(null, Metadata.of(
        Key.of(key),
        EventTime.of(eventTime),
        action)));

    assertNotNull(message);
    assertNull(message.getPayload());
    assertEquals(key, extractKey(message));
    assertEquals(eventTime, extractEventTime(message));
    assertEquals(action, extractAction(message));
  }

  @Test
  void positiveIndexablePropertyExpectPayloadProcessed() {
    String payload = "Not Indexable Content";
    Message<IndexableResource> resource = getResourceFrom(
        Message.of(new Page(payload), Metadata.of(
            Key.of("/test.html"),
            EventTime.of(System.currentTimeMillis()),
            PUBLISH,
            Properties.of("indexable", "true"))));

    assertNotNull(resource);
    assertNotNull(resource.getPayload());
    assertEquals("/test.html", extractKey(resource));
    assertTrue(extractEventTime(resource) > 0);
    assertEquals(PUBLISH, extractAction(resource));
    assertEquals(payload, getIndexableResourceContent(resource.getPayload()).getContent());
  }

  @Test
  void negativeIndexablePropertySkipsPayloadFromBeingProcessed() {
    String payload = "Not Indexable Content";
    Message<IndexableResource> resource = getResourceFrom(
        Message.of(new Page(payload), Metadata.of(
            Key.of("/test.html"),
            EventTime.of(System.currentTimeMillis()),
            PUBLISH,
            Properties.of("indexable", "false"))));

    assertNotNull(resource);
    assertNull(resource.getPayload());
    assertEquals("/test.html", extractKey(resource));
    assertTrue(extractEventTime(resource) > 0);
    assertEquals(UNPUBLISH, extractAction(resource));
  }

  private IndexableResourceContent getResourceFromPageWithContent(String payload) {
    String key = "/test.html";
    Long eventTime = 1L;
    Action action = PUBLISH;

    Message<IndexableResource> resource = getResourceFrom(
        Message.of(new Page(payload), Metadata.of(
            Key.of(key),
            EventTime.of(eventTime),
            action)));

    assertNotNull(resource);
    assertNotNull(resource.getPayload());
    assertEquals(key, extractKey(resource));
    assertEquals(eventTime, extractEventTime(resource));
    assertEquals(action, extractAction(resource));

    var indexableResource = resource.getPayload();
    return getIndexableResourceContent(indexableResource);
  }

  private IndexableResourceContent getIndexableResourceContent(
      IndexableResource indexableResource) {
    try {
      byte[] bytes = indexableResource.getContent().array();
      return objectMapper.readValue(bytes, IndexableResourceContent.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Message<IndexableResource> getResourceFrom(Message<Page> message) {
    pagesSource.send(message);
    await().until(() -> indexableResourceSink.received().size() == 1);
    return indexableResourceSink.received().get(0);
  }
}
