package com.streamx.blueprints.data.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WebResourceProducingTest {

  private static final String DEFAULT_KEY = "collected:test";
  private static final String DEFAULT_CONTENT = "{\"key\": \"value\"}";

  @Inject
  @Any
  InMemoryConnector connector;

  InMemorySource<CloudEvent> dataSource;
  InMemorySink<CloudEvent> webResourceSink;

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Channels.Incoming.DATA);
    webResourceSink = connector.sink(Channels.Outgoing.WEB_RESOURCES);
    webResourceSink.clear();
  }

  @Test
  void shouldProduceWebResource() {
    // given
    CloudEvent dataEvent = createPublishDataEvent(DEFAULT_KEY);

    // when
    dataSource.send(dataEvent);

    // then
    CloudEvent result = waitForSingleResultEvent();
    WebResource resource = CloudEventUtils.getData(result, WebResource.class);
    assertThat(resource).isNotNull();
    assertThat(resource.getContentAsString()).isEqualTo(DEFAULT_CONTENT);
    // configured prefix + incoming data key + json extension
    assertThat(result.getSubject()).isEqualTo("_data/" + DEFAULT_KEY + ".json");
    assertThat(result.getTime()).isEqualTo(dataEvent.getTime());
    assertThat(result.getType()).isEqualTo(WebResource.TYPE_PUBLISHED);
  }

  @Test
  void shouldUnpublishProducedWebResource() {
    // given
    CloudEvent publishDataEvent = createPublishDataEvent(DEFAULT_KEY);
    dataSource.send(publishDataEvent);
    waitForSingleResultEvent();
    webResourceSink.clear();

    // when
    CloudEvent unpublishDataEvent = createUnpublishDataEvent(DEFAULT_KEY);
    dataSource.send(unpublishDataEvent);

    // when
    CloudEvent result = waitForSingleResultEvent();
    assertThat(result.getData()).isNull();
    assertThat(result.getSubject()).isEqualTo("_data/" + DEFAULT_KEY + ".json");
    assertThat(result.getTime()).isEqualTo(unpublishDataEvent.getTime());
    assertThat(result.getType()).isEqualTo(WebResource.TYPE_UNPUBLISHED);
  }

  @Test
  void shouldNotProduceWebResourceIfKeyDoesNotMachPrefix() {
    // given
    CloudEvent dataEvent1 = createPublishDataEvent("not-matching-the-filter");
    CloudEvent dataEvent2 = createPublishDataEvent(DEFAULT_KEY);

    // when
    dataSource.send(dataEvent1); // should be skipped
    dataSource.send(dataEvent2);

    // then
    CloudEvent result = waitForSingleResultEvent();
    // configured prefix + incoming data key + json extension
    assertThat(result.getSubject()).isEqualTo("_data/" + DEFAULT_KEY + ".json");
  }

  @Test
  void shouldNotProduceWebResourceIfNoPayloadInIncomingDataEvent() {
    // given
    CloudEvent dataEvent = CloudEventUtils.eventWithoutData(DEFAULT_KEY, Data.TYPE_PUBLISHED);

    // when
    dataSource.send(dataEvent);

    // then
    assertNoResultEvents();
  }

  @Test
  void shouldNotProduceWebResourceIfWrongTypeOfIncomingEvent() {
    // given
    CloudEvent dataEvent = CloudEventUtils.eventWithData(
        DEFAULT_KEY, Page.TYPE_PUBLISHED, new Data(DEFAULT_CONTENT));

    // when
    dataSource.send(dataEvent);

    // then
    assertNoResultEvents();
  }

  private static CloudEvent createPublishDataEvent(String key) {
    return CloudEventUtils.eventWithData(
        key,
        Data.TYPE_PUBLISHED,
        new Data(DEFAULT_CONTENT),
        CloudEventUtils.toOffsetDateTime(1)
    );
  }

  private static CloudEvent createUnpublishDataEvent(String key) {
    return CloudEventUtils.eventWithoutData(
        key,
        Data.TYPE_UNPUBLISHED,
        CloudEventUtils.toOffsetDateTime(1)
    );
  }

  private CloudEvent waitForSingleResultEvent() {
    await().atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(webResourceSink.received()).hasSize(1));
    return webResourceSink.received().getFirst().getPayload();
  }

  private void assertNoResultEvents() {
    await().during(Duration.ofMillis(300))
        .untilAsserted(() -> assertThat(webResourceSink.received()).isEmpty());
  }

}
