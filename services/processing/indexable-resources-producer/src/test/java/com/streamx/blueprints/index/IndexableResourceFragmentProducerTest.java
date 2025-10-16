package com.streamx.blueprints.index;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventTestUtils;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.index.IndexableResourceFragmentProducer.IndexableResourceFragmentContent;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class IndexableResourceFragmentProducerTest {

  private InMemorySource<CloudEvent> fragmentsSource;
  private InMemorySink<CloudEvent> indexableResourceFragmentSink;

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() {
    fragmentsSource = connector.source(Channels.INCOMING_FRAGMENTS);
    indexableResourceFragmentSink = connector.sink(Channels.INDEXABLE_RESOURCE_FRAGMENTS);
    indexableResourceFragmentSink.clear();
  }

  @Test
  void expectFragmentBeProcessed() {
    String payload = "Fragment";
    String result = getResourceFromFragmentWithContent(payload);
    assertThat(result).isEqualTo(payload);
  }

  @Test
  void expectNonAsciiPayloadBeProcessed() {
    String payload = new String(new byte[]{1, 2, 3}, UTF_8);
    String result = getResourceFromFragmentWithContent(payload);
    assertThat(result).isEqualTo(payload);
  }

  @Test
  void expectNonIndexableFragmentBeProcessed() {
    // given
    CloudEvent fragmentEvent = CloudEventTestUtils.cloudEventWithExtensions(
        "/fragment/test.html",
        Fragment.TYPE_PUBLISHED,
        new Fragment("Fragment"),
        Map.of(AbstractIndexableResourceProducer.EXTENSION_NAME_INDEXABLE, "false")
    );

    // when
    CloudEvent resultEvent = getResourceFrom(fragmentEvent);

    // then
    assertThat(resultEvent).isNotNull();
    assertThat(resultEvent.getData()).isNull();
    assertThat(resultEvent.getSubject()).isEqualTo(fragmentEvent.getSubject());
    assertThat(resultEvent.getTime()).isEqualTo(fragmentEvent.getTime());
    assertThat(resultEvent.getType()).isEqualTo(IndexableResourceFragment.TYPE_UNPUBLISHED);
  }

  @Test
  void expectFragmentUnpublishBeProcessed() {
    // given
    CloudEvent fragmentEvent = CloudEventUtils.eventWithoutData(
        "/test.html",
        Fragment.TYPE_UNPUBLISHED,
        CloudEventUtils.toOffsetDateTime(1)
    );

    // when
    CloudEvent resultEvent = getResourceFrom(fragmentEvent);

    // then
    assertThat(resultEvent).isNotNull();
    assertThat(resultEvent.getData()).isNull();
    assertThat(resultEvent.getSubject()).isEqualTo(fragmentEvent.getSubject());
    assertThat(resultEvent.getTime()).isEqualTo(fragmentEvent.getTime());
    assertThat(resultEvent.getType()).isEqualTo(IndexableResourceFragment.TYPE_UNPUBLISHED);
  }

  @Test
  void shouldSkipProcessingFragmentPublishedWithoutContent() {
    // given
    CloudEvent fragmentEvent = CloudEventUtils.eventWithoutData(
        "/fragment/test.html",
        Fragment.TYPE_PUBLISHED
    );

    // when & then
    assertNoResourceFrom(fragmentEvent);
  }

  @Test
  void shouldSkipProcessingEventWithUnexpectedType() {
    // given
    CloudEvent pageEvent = CloudEventUtils.eventWithData(
        "/fragment/test.html",
        WebResource.TYPE_PUBLISHED,
        new Fragment("content")
    );

    // when & then
    assertNoResourceFrom(pageEvent);
  }

  private String getResourceFromFragmentWithContent(String payload) {
    // given
    CloudEvent fragmentEvent = CloudEventUtils.eventWithData(
        "/fragment/test.html",
        Fragment.TYPE_PUBLISHED,
        new Fragment(payload),
        CloudEventUtils.toOffsetDateTime(1)
    );

    // when
    CloudEvent resultEvent = getResourceFrom(fragmentEvent);

    // then
    assertThat(resultEvent).isNotNull();
    assertThat(resultEvent.getSubject()).isEqualTo(fragmentEvent.getSubject());
    assertThat(resultEvent.getTime()).isEqualTo(fragmentEvent.getTime());
    assertThat(resultEvent.getType()).isEqualTo(IndexableResourceFragment.TYPE_PUBLISHED);

    IndexableResourceFragment indexableResourceFragment = CloudEventUtils
        .getData(resultEvent, IndexableResourceFragment.class);
    assertThat(indexableResourceFragment).isNotNull();

    String json = indexableResourceFragment.getContentAsString();
    try {
      return objectMapper.readValue(json, IndexableResourceFragmentContent.class).content();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private CloudEvent getResourceFrom(CloudEvent fragmentEvent) {
    fragmentsSource.send(fragmentEvent);
    await().until(() -> indexableResourceFragmentSink.received().size() == 1);
    return indexableResourceFragmentSink.received().get(0).getPayload();
  }

  private void assertNoResourceFrom(CloudEvent fragmentEvent) {
    fragmentsSource.send(fragmentEvent);
    await()
        .during(Duration.ofMillis(500))
        .untilAsserted(() -> assertThat(indexableResourceFragmentSink.received()).isEmpty());
  }
}
