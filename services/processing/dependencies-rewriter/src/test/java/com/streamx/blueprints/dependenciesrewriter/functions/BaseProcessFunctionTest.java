package com.streamx.blueprints.dependenciesrewriter.functions;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventTestUtils;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.dependenciesrewriter.Channels;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseProcessFunctionTest extends BaseMockedDownloaderTest {

  // payload types (as in src/test/resources/application.properties)
  public static final String PAGE = "page/blog";
  public static final String WEB_RESOURCE = "web-resource/static";
  public static final String DATA = "product/simple";
  public static final String EXTERNAL_PAGE = "page/blog/external";

  protected InMemorySource<CloudEvent> resourcesChannel;
  protected InMemorySink<CloudEvent> resourcesSink;
  protected InMemorySink<CloudEvent> downloadRequestsSink;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeEach
  void initSourcesAndSinks() {
    resourcesChannel = getSource(Channels.INCOMING_RESOURCES);
    resourcesSink = getSink(Channels.OUTGOING_RESOURCES);
    downloadRequestsSink = getSink(Channels.DOWNLOAD_REQUESTS);
  }

  protected InMemorySource<CloudEvent> getSource(String channel) {
    return connector.source(channel);
  }

  protected InMemorySink<CloudEvent> getSink(String channel) {
    InMemorySink<CloudEvent> sink = connector.sink(channel);
    sink.clear();
    return sink;
  }

  protected CloudEvent publishPage(String path, String content) {
    return publishPage(path, content, PAGE);
  }

  protected CloudEvent publishPage(String path, String content, String payloadType) {
    return sendToChannel(path, new Page(content, payloadType), Page.TYPE_PUBLISHED);
  }

  protected CloudEvent publishWebResource(String path, String content) {
    return publishWebResource(path, content, WEB_RESOURCE);
  }

  protected CloudEvent publishWebResource(String path, String content, String payloadType) {
    return sendToChannel(path, new WebResource(content, payloadType), WebResource.TYPE_PUBLISHED);
  }

  protected CloudEvent publishData(String path, String content) {
    return sendToChannel(path, new Data(content, DATA), Data.TYPE_PUBLISHED);
  }

  protected CloudEvent unpublishPage(String path) {
    return sendToChannel(path, new Page((ByteBuffer) null, PAGE), Page.TYPE_UNPUBLISHED);
  }

  private <T extends Resource> CloudEvent sendToChannel(String path, T resource, String eventType) {
    CloudEvent event = CloudEventUtils.eventWithData(resource, eventType, path);
    resourcesChannel.send(event);
    return event;
  }

  protected List<CloudEvent> waitForEventsInSink(String payloadType, int expectedCount) {
    AtomicReference<List<CloudEvent>> matchingEventsRef = new AtomicReference<>();
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      List<CloudEvent> matchingEvents = resourcesSink.received().stream()
          .map(Message::getPayload)
          .filter(event -> {
            Resource payload = CloudEventUtils.getData(event, Resource.class);
            return payload != null && Objects.equals(payloadType, payload.getType());
          })
          .toList();
      assertThat(matchingEvents).hasSize(expectedCount);
      matchingEventsRef.set(matchingEvents);
    });
    return matchingEventsRef.get();
  }

  protected void assertPublishedPage(CloudEvent event, String expectedKey, String expectedContent) {
    assertPublishedTextResource(event, expectedKey, expectedContent);
  }

  protected void assertPublishedWebResource(CloudEvent event, String expectedKey,
      String expectedContent) {
    assertPublishedTextResource(event, expectedKey, expectedContent);
  }

  protected void assertPublishedData(CloudEvent event, String expectedKey, String expectedContent) {
    assertPublishedTextResource(event, expectedKey, expectedContent);
  }

  private static void assertPublishedTextResource(CloudEvent event,
      String expectedKey, String expectedContent) {
    assertThat(event.getSubject()).isEqualTo(expectedKey);
    String content = requireNonNull(CloudEventUtils.getData(event, Resource.class))
        .getContentAsString();
    assertThat(content).isEqualTo(expectedContent);
  }

  protected static void assertSameEvents(CloudEvent actual, CloudEvent expected) {
    CloudEventTestUtils.assertEventsData(expected, actual);
  }
}
