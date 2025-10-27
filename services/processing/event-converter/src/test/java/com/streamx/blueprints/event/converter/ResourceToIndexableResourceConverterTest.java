package com.streamx.blueprints.event.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.Layout;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
public class ResourceToIndexableResourceConverterTest {

  private static final String KEY = "key";
  private static final String RESOURCE_TYPE = "type";
  private static final OffsetDateTime EVENT_TIME = CloudEventUtils.toOffsetDateTime(1);

  private InMemorySource<CloudEvent> incoming;
  private InMemorySink<CloudEvent> outgoing;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeEach
  void init() {
    incoming = connector.source(Channels.RESOURCES);
    outgoing = connector.sink(Channels.INDEXABLE_RESOURCES);
    outgoing.clear();
  }

  @Test
  void shouldConvertDataPublishEvent_ToIndexableResourcePublishEvent() {
    // given
    Data data = new Data("{\"key\": \"value\"}", RESOURCE_TYPE);

    // when
    CloudEvent incomingEvent = publish(data, Data.TYPE_PUBLISHED);

    // then
    CloudEvent outgoingEvent = waitForSingleOutgoingEvent();
    assertOutgoingPublishEvent(outgoingEvent, incomingEvent, data);
  }

  @Test
  void shouldConvertWebResourcePublishEvent_ToIndexableResourcePublishEvent() {
    // given
    WebResource webResource = new WebResource("{\"key\": \"value\"}", RESOURCE_TYPE);

    // when
    CloudEvent incomingEvent = publish(webResource, WebResource.TYPE_PUBLISHED);

    // then
    CloudEvent outgoingEvent = waitForSingleOutgoingEvent();
    assertOutgoingPublishEvent(outgoingEvent, incomingEvent, webResource);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      Data.TYPE_UNPUBLISHED,
      WebResource.TYPE_UNPUBLISHED,
      Page.TYPE_UNPUBLISHED,
      Fragment.TYPE_UNPUBLISHED,
      Layout.TYPE_UNPUBLISHED,
  })
  void shouldConvertUnpublishEvent_ToIndexableResourceUnpublishEvent(String eventType) {
    // when
    CloudEvent incomingEvent = unpublish(eventType);

    // then
    CloudEvent outgoingEvent = waitForSingleOutgoingEvent();
    assertOutgoingUnpublishEvent(outgoingEvent, incomingEvent);
  }

  @Test
  void shouldSkipConvertingNonResourceIncomingEvent() {
    // given
    DownloadRequest downloadRequest = new DownloadRequest("any", "any", "any", "any", "any");

    // when
    publish(downloadRequest, DownloadRequest.EVENT_TYPE);

    // then
    assertNoOutgoingEvents();
  }

  @Test
  void shouldSkipConvertingPublishEvent_WhenResourceWithoutContent() {
    // given
    Data data = new Data((ByteBuffer) null, RESOURCE_TYPE);

    // when
    publish(data, Data.TYPE_PUBLISHED);

    // then
    assertNoOutgoingEvents();
  }

  @Test
  void shouldSkipConvertingEvent_WithNeitherPublishingOrUnpublishingType() {
    // given
    Data data = new Data("{\"key\": \"value\"}", RESOURCE_TYPE);

    // when
    publish(data, "com.streamx.blueprints.data.edited.v1");

    // then
    assertNoOutgoingEvents();
  }

  private CloudEvent publish(Object resource, String eventType) {
    CloudEvent event = CloudEventUtils.eventWithData(KEY, eventType, resource, EVENT_TIME);
    incoming.send(event);
    return event;
  }

  private CloudEvent unpublish(String eventType) {
    CloudEvent event = CloudEventUtils.eventWithoutData(KEY, eventType, EVENT_TIME);
    incoming.send(event);
    return event;
  }

  private void assertOutgoingPublishEvent(CloudEvent outgoingEvent, CloudEvent incomingEvent,
      Resource incomingResource) {
    assertThat(outgoingEvent.getSubject()).isEqualTo(KEY);
    assertThat(outgoingEvent.getTime()).isEqualTo(incomingEvent.getTime());
    assertThat(outgoingEvent.getType()).isEqualTo(IndexableResource.TYPE_PUBLISHED);

    IndexableResource outgoingResource = CloudEventUtils.getData(outgoingEvent,
        IndexableResource.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isNotNull().isEqualTo(incomingResource.getType());
    assertThat(outgoingResource.getContent())
        .isNotNull()
        .isEqualTo(incomingResource.getContent());
  }

  private void assertOutgoingUnpublishEvent(CloudEvent outgoingEvent, CloudEvent incomingEvent) {
    assertThat(outgoingEvent.getSubject()).isEqualTo(KEY);
    assertThat(outgoingEvent.getTime()).isEqualTo(incomingEvent.getTime());
    assertThat(outgoingEvent.getType()).isEqualTo(IndexableResource.TYPE_UNPUBLISHED);
    assertThat(outgoingEvent.getData()).isNull();
  }

  private CloudEvent waitForSingleOutgoingEvent() {
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(outgoing.received()).hasSize(1));
    return outgoing.received().getFirst().getPayload();
  }

  private void assertNoOutgoingEvents() {
    await()
        .during(Duration.ofMillis(300))
        .untilAsserted(() -> assertThat(outgoing.received()).isEmpty());
  }
}
