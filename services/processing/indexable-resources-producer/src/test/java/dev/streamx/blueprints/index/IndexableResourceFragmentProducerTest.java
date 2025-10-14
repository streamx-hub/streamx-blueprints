package dev.streamx.blueprints.index;

import static dev.streamx.blueprints.index.IndexableResourceFragmentProducer.CHANNEL_FRAGMENTS;
import static dev.streamx.blueprints.index.IndexableResourceFragmentProducer.CHANNEL_INDEXABLE_RESOURCE_FRAGMENTS;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamx.blueprints.data.Fragment;
import dev.streamx.blueprints.data.IndexableResourceFragment;
import dev.streamx.blueprints.index.IndexableResourceFragmentProducer.IndexableResourceFragmentContent;
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
public class IndexableResourceFragmentProducerTest {

  InMemorySource<Message<Fragment>> fragmentsSource;
  InMemorySink<IndexableResourceFragment> indexableResourceFragmentSink;

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() {
    fragmentsSource = connector.source(CHANNEL_FRAGMENTS);
    indexableResourceFragmentSink = connector.sink(CHANNEL_INDEXABLE_RESOURCE_FRAGMENTS);
    indexableResourceFragmentSink.clear();
  }

  @Test
  void expectFragmentBeProcessed() {
    String payload = "Fragment";
    var result = getResourceFromFragmentWithContent(payload);
    assertEquals(payload, result);
  }

  @Test
  void expectNonIndexableFragmentBeProcessed() {
    String payload = "Fragment";

    String key = "/fragment/test.html";
    Long eventTime = 1L;

    var properties = Properties.of(
        IndexableResourceFragmentProducer.MESSAGE_PN_INDEXABLE, Boolean.FALSE.toString()
    );

    var message = getResourceFrom(
        Message.of(new Fragment(payload), Metadata.of(
            Key.of(key),
            EventTime.of(eventTime),
            PUBLISH,
            properties
        ))
    );

    assertNotNull(message);
    assertNull(message.getPayload());
    assertEquals(key, extractKey(message));
    assertEquals(eventTime, extractEventTime(message));
    assertEquals(UNPUBLISH, extractAction(message));
    assertEquals(properties, extractProperties(message));
  }

  @Test
  void expectNonAsciiPayloadBeProcessed() {
    String payload = new String(new byte[]{1, 2, 3}, UTF_8);
    var result = getResourceFromFragmentWithContent(payload);
    assertEquals(payload, result);
  }

  @Test
  void expectPageUnpublishBeProcessed() {
    String key = "/test.html";
    Long eventTime = 1L;
    Action action = UNPUBLISH;

    var message = getResourceFrom(Message.of(null, Metadata.of(
        Key.of(key),
        EventTime.of(eventTime),
        action)));

    assertNotNull(message);
    assertNull(message.getPayload());
    assertEquals(key, extractKey(message));
    assertEquals(eventTime, extractEventTime(message));
    assertEquals(action, extractAction(message));
  }

  private String getResourceFromFragmentWithContent(String payload) {
    String key = "/fragment/test.html";
    Long eventTime = 1L;
    Action action = PUBLISH;

    var resource = getResourceFrom(
        Message.of(new Fragment(payload), Metadata.of(
            Key.of(key),
            EventTime.of(eventTime),
            action)));

    assertNotNull(resource);
    assertNotNull(resource.getPayload());
    assertEquals(key, extractKey(resource));
    assertEquals(eventTime, extractEventTime(resource));
    assertEquals(action, extractAction(resource));

    var indexableResourceFragment = resource.getPayload();

    byte[] array = indexableResourceFragment.getContent().array();
    try {
      var content =  objectMapper.readValue(array, IndexableResourceFragmentContent.class);
      return content.getContent();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Message<IndexableResourceFragment> getResourceFrom(Message<Fragment> message) {
    fragmentsSource.send(message);
    await().until(() -> indexableResourceFragmentSink.received().size() == 1);
    return indexableResourceFragmentSink.received().get(0);
  }

  private static <T> T extractProperties(Message<?> message) {
    return message == null ? null : (T) Properties.from(message);
  }
}
