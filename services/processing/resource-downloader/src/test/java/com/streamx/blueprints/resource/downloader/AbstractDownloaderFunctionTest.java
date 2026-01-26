package com.streamx.blueprints.resource.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractDownloaderFunctionTest {

  protected static final String EMITTED_PAGE_TYPE = "page/external";
  protected static final String EMITTED_WEB_RESOURCE_TYPE = "web-resource/external";
  protected static final String EMITTED_ASSET_TYPE = "asset/external";

  private InMemorySource<CloudEvent> downloadRequestsChannel;
  private InMemorySink<CloudEvent> downloadedPagesSink;
  private InMemorySink<CloudEvent> downloadedAssetsSink;
  private InMemorySink<CloudEvent> downloadedWebResourcesSink;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeAll
  static void startTestWebServer() throws IOException {
    TestWebServer.start();
  }

  @AfterAll
  static void stopTestWebServer() {
    TestWebServer.stop();
  }

  @BeforeEach
  void init() {
    downloadRequestsChannel = connector.source(Channels.DOWNLOAD_REQUESTS);
    downloadedPagesSink = connector.sink(Channels.DOWNLOADED_PAGES);
    downloadedAssetsSink = connector.sink(Channels.DOWNLOADED_ASSETS);
    downloadedWebResourcesSink = connector.sink(Channels.DOWNLOADED_WEB_RESOURCES);
    downloadedPagesSink.clear();
    downloadedAssetsSink.clear();
    downloadedWebResourcesSink.clear();
  }

  protected void sendDownloadRequest(String url, String emitKey) {
    sendDownloadRequest(url, emitKey, DownloadRequest.DOWNLOAD_EVENT_TYPE);
  }

  protected void sendDownloadRequest(String url, String emitKey, String eventType) {
    DownloadRequest downloadRequest = new DownloadRequest(url, emitKey,
        EMITTED_PAGE_TYPE, EMITTED_WEB_RESOURCE_TYPE, EMITTED_ASSET_TYPE);
    sendDownloadRequest(downloadRequest, emitKey, eventType);
  }

  protected void sendDownloadRequest(DownloadRequest request, String emitKey, String eventType) {
    CloudEvent event = CloudEventUtils.eventWithData(emitKey, eventType, request);
    sendDownloadRequest(event);
  }

  protected void sendDownloadRequest(CloudEvent downloadRequestEvent) {
    downloadRequestsChannel.send(downloadRequestEvent);
  }

  protected CloudEvent waitForSingleDownloadedPage(String key) {
    return waitForSingleDownloadedResource(key, downloadedPagesSink, EMITTED_PAGE_TYPE);
  }

  protected CloudEvent waitForSingleDownloadedAsset(String key) {
    return waitForSingleDownloadedResource(key, downloadedAssetsSink, EMITTED_ASSET_TYPE);
  }

  protected CloudEvent waitForSingleDownloadedWebResource(String key) {
    return waitForSingleDownloadedResource(key, downloadedWebResourcesSink,
        EMITTED_WEB_RESOURCE_TYPE);
  }

  private CloudEvent waitForSingleDownloadedResource(String key, InMemorySink<CloudEvent> sink,
      String payloadType) {
    return waitForDownloadedResources(key, sink, payloadType, 1).getFirst();
  }

  protected List<CloudEvent> waitForDownloadedWebResources(String key, int exactCount) {
    return waitForDownloadedResources(key, downloadedWebResourcesSink, EMITTED_WEB_RESOURCE_TYPE,
        exactCount);
  }

  protected void waitForAtLeastDownloadedWebResources(String key, int atLeastCount) {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      List<CloudEvent> matchingEvents = getDownloadedWebResources(key);
      assertThat(matchingEvents).hasSizeGreaterThanOrEqualTo(atLeastCount);
    });
  }

  protected List<CloudEvent> getDownloadedWebResources(String key) {
    return getMatchingEvents(key, downloadedWebResourcesSink, EMITTED_WEB_RESOURCE_TYPE);
  }

  protected long getDownloadedWebResourcesCount(String key) {
    return getDownloadedWebResources(key).size();
  }

  private static List<CloudEvent> waitForDownloadedResources(String key,
      InMemorySink<CloudEvent> sink, String payloadType, int expectedSize) {
    AtomicReference<List<CloudEvent>> result = new AtomicReference<>();
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      List<CloudEvent> matchingEvents = getMatchingEvents(key, sink, payloadType);
      assertThat(matchingEvents).hasSize(expectedSize);
      result.set(matchingEvents);
    });
    return result.get();
  }

  private static List<CloudEvent> getMatchingEvents(String key, InMemorySink<CloudEvent> sink,
      String payloadType) {
    return sink.received().stream()
        .map(Message::getPayload)
        .filter(event -> {
          Resource resource = CloudEventUtils.getData(event, Resource.class);
          return resource != null && payloadType.equals(resource.getType());
        })
        .filter(event -> key.equals(event.getSubject()))
        .toList();
  }

  protected void assertNoDownloadedResources() {
    await().during(Duration.ofMillis(300)).untilAsserted(() -> {
      assertThat(downloadedPagesSink.received()).isEmpty();
      assertThat(downloadedAssetsSink.received()).isEmpty();
      assertThat(downloadedWebResourcesSink.received()).isEmpty();
    });
  }

  protected void assertEventContent(CloudEvent actualEvent, String expectedContent) {
    assertEventContent(actualEvent, expectedContent.getBytes());
  }

  protected void assertEventContent(CloudEvent actualEvent, byte[] expectedContent) {
    Resource resource = CloudEventUtils.getData(actualEvent, Resource.class);
    assertThat(resource).isNotNull();
    byte[] resourceContent = resource.getContentAsBytes();
    assertThat(resourceContent).isEqualTo(expectedContent);
  }

  protected byte[] gzip(byte[] content) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
      gzipOutputStream.write(content);
      gzipOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

}
