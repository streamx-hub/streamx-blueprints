package dev.streamx.blueprints.externalresources.functions;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.streamx.blueprints.cloudevents.utils.CloudEventTestUtils;
import dev.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import dev.streamx.blueprints.data.Asset;
import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.Channels;
import dev.streamx.blueprints.externalresources.testutils.SkipVerifyingNoDownloadErrors;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

abstract class BaseProcessFunctionTest extends BaseFunctionTest {

  // payload types (as in src/test/resources/application.properties)
  public static final String PAGE = "page/blog";
  public static final String WEB_RESOURCE = "web-resource/static";
  public static final String DATA = "product/simple";
  public static final String EXTERNAL_PAGE = "page/blog/external";
  public static final String EXTERNAL_WEB_RESOURCE = "web-resource/static/external";
  public static final String EXTERNAL_ASSET = "asset/static/external";

  @InjectSpy
  ExternalResourcesProcessFunction externalResourcesProcessFunction;

  protected InMemorySource<CloudEvent> resourcesChannel;
  protected InMemorySink<CloudEvent> resourcesSink;

  @BeforeEach
  void initSourcesAndSinks() {
    resourcesChannel = getSource(Channels.INCOMING_RESOURCES);
    resourcesSink = getSink(Channels.OUTGOING_RESOURCES);
  }

  @AfterEach
  void verifyNoDownloadErrors(TestInfo testInfo) {
    if (!testInfo.getTestMethod().orElseThrow()
        .isAnnotationPresent(SkipVerifyingNoDownloadErrors.class)) {
      verify(externalResourcesProcessFunction, never()).logDownloadingError(any(), any());
    }
  }

  protected CloudEvent publishPage(String path, String content) {
    return publishPage(path, content, "page/blog");
  }

  protected CloudEvent publishPage(String path, String content, String payloadType) {
    return sendToChannel(path, new Page(content, payloadType), Page.TYPE_PUBLISHED);
  }

  protected CloudEvent publishWebResource(String path, String content) {
    return publishWebResource(path, content, "web-resource/static");
  }

  protected CloudEvent publishWebResource(String path, String content, String payloadType) {
    return sendToChannel(path, new WebResource(content, payloadType), WebResource.TYPE_PUBLISHED);
  }

  protected CloudEvent publishData(String path, String content, String payloadType) {
    return sendToChannel(path, new Data(content, payloadType), Data.TYPE_PUBLISHED);
  }

  protected CloudEvent unpublishPage(String path) {
    return sendToChannel(path, new Page((ByteBuffer) null, "page/blog"), Page.TYPE_UNPUBLISHED);
  }

  private <T extends Resource> CloudEvent sendToChannel(String path, T resource, String eventType) {
    CloudEvent event = CloudEventUtils.builderWithJsonData(resource)
        .withSubject(path)
        .withType(eventType)
        .build();
    resourcesChannel.send(event);
    return event;
  }

  protected List<CloudEvent> waitForEventsInSink(String payloadType, int expectedCount) {
    AtomicReference<List<CloudEvent>> matchingEventsRef = new AtomicReference<>();
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      List<CloudEvent> matchingEvents = resourcesSink.received().stream()
          .map(Message::getPayload)
          .filter(event -> Objects.equals(payloadType, CloudEventUtils.getData(event, Resource.class).getType()))
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

  protected void assertPublishedAsset(CloudEvent event, String expectedKey,
      byte[] expectedContent) {
    assertThat(event.getSubject()).isEqualTo(expectedKey);
    byte[] content = requireNonNull(CloudEventUtils.getData(event, Resource.class))
        .getContent().array();
    assertThat(content).isEqualTo(expectedContent);
  }

  protected void assertPublishedAssets(String payloadType, Map<String, byte[]> expectedAssets) {
    List<CloudEvent> sortedActualAssets = waitForEventsInSink(payloadType, expectedAssets.size())
        .stream()
        .sorted(Comparator.comparing(asset -> requireNonNull(asset.getSubject())))
        .toList();

    List<CloudEvent> sortedExpectedAssets = expectedAssets.entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> CloudEventUtils.builderWithJsonData(new Asset(entry.getValue()))
            .withSubject(entry.getKey())
            .withType(EXTERNAL_ASSET)
            .build())
        .toList();

    for (int i = 0; i < sortedActualAssets.size(); i++) {
      CloudEvent expected = sortedExpectedAssets.get(i);
      byte[] expectedContent = requireNonNull(CloudEventUtils.getData(expected, Asset.class))
          .getContent().array();
      assertPublishedAsset(sortedActualAssets.get(i), expected.getSubject(), expectedContent);
    }
  }

  protected static void assertSameEvents(CloudEvent actual, CloudEvent expected) {
    CloudEventTestUtils.assertEventsData(expected, actual);
  }
}
