package com.streamx.blueprints.resourcedownloader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.resourcedownloader.testutils.TestWebServer;
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

  protected InMemorySource<CloudEvent> downloadRequestsChannel;
  protected InMemorySink<CloudEvent> downloadedPagesSink;
  protected InMemorySink<CloudEvent> downloadedAssetsSink;
  protected InMemorySink<CloudEvent> downloadedWebResourcesSink;

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  HttpDownloaderFunction httpDownloaderFunction;

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
    DownloadRequest downloadRequest = new DownloadRequest(url, emitKey,
        EMITTED_PAGE_TYPE, EMITTED_WEB_RESOURCE_TYPE, EMITTED_ASSET_TYPE);

    CloudEvent event = CloudEventUtils
        .eventWithData(downloadRequest, DownloadRequest.EVENT_TYPE, emitKey);

    downloadRequestsChannel.send(event);
  }

  protected CloudEvent waitForSingleDownloadedPage() {
    return waitForSingleDownloadedResource(downloadedPagesSink, EMITTED_PAGE_TYPE);
  }

  protected CloudEvent waitForSingleDownloadedAsset() {
    return waitForSingleDownloadedResource(downloadedAssetsSink, EMITTED_ASSET_TYPE);
  }

  protected CloudEvent waitForSingleDownloadedWebResource() {
    return waitForSingleDownloadedResource(downloadedWebResourcesSink, EMITTED_WEB_RESOURCE_TYPE);
  }

  private CloudEvent waitForSingleDownloadedResource(InMemorySink<CloudEvent> sink,
      String payloadType) {
    return waitForDownloadedResources(sink, payloadType, 1).get(0);
  }

  protected List<CloudEvent> waitForDownloadedResources(InMemorySink<CloudEvent> sink,
      String payloadType, int expectedSize) {
    AtomicReference<List<CloudEvent>> matchingEventsRef = new AtomicReference<>();
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      List<CloudEvent> matchingEvents = sink.received().stream()
          .map(Message::getPayload)
          .filter(event ->
              CloudEventUtils.getDataOrThrow(event, Resource.class).getType().equals(payloadType))
          .toList();
      assertThat(matchingEvents).hasSize(expectedSize);
      matchingEventsRef.set(matchingEvents);
    });
    return matchingEventsRef.get();
  }

  protected void assertNoDownloadedResources() {
    await().during(Duration.ofMillis(300)).untilAsserted(() -> {
      assertThat(downloadedPagesSink.received()).isEmpty();
      assertThat(downloadedAssetsSink.received()).isEmpty();
      assertThat(downloadedWebResourcesSink.received()).isEmpty();
    });
  }

  protected void assertEvent(CloudEvent actualEvent, String expectedSubject,
      String expectedContent) {
    assertEvent(actualEvent, expectedSubject, expectedContent.getBytes(UTF_8));
  }

  protected void assertEvent(CloudEvent actualEvent, String expectedSubject,
      byte[] expectedContent) {
    assertThat(actualEvent.getSubject()).isEqualTo(expectedSubject);

    Resource resource = CloudEventUtils.getDataOrThrow(actualEvent, Resource.class);
    byte[] resourceContent = resource.getContent().array();
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
