package com.streamx.blueprints.resourcedownloader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.resourcedownloader.testutils.TestWebServer;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
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
import org.junit.jupiter.api.Test;

@QuarkusTest
class HttpDownloaderFunctionTest {

  private static final String EMITTED_PAGE_TYPE = "page/external";
  private static final String EMITTED_WEB_RESOURCE_TYPE = "web-resource/external";
  private static final String EMITTED_ASSET_TYPE = "asset/external";

  private InMemorySource<CloudEvent> downloadRequestsChannel;
  private InMemorySink<CloudEvent> downloadedResourcesSink;

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
    downloadedResourcesSink = connector.sink(Channels.DOWNLOADED_RESOURCES);
    downloadedResourcesSink.clear();
  }

  @Test
  void shouldDownloadImageAndSupportLastModifiedHeaders() {
    // given
    String imagePath = "/image-1.png";
    byte[] imageContent = new byte[]{0, 1, 2};
    String imageUrl = TestWebServer.uploadImage(imagePath, imageContent);

    // when
    sendDownloadRequest(imageUrl, imagePath);

    // then
    List<CloudEvent> assetEvents = waitForSingleDownloadedResource(EMITTED_ASSET_TYPE);
    assertEvent(assetEvents.get(0), imagePath, imageContent);

    // when 2
    sendDownloadRequest(imageUrl, imagePath);

    // then: expect no re-download
    waitForSingleDownloadedResource(EMITTED_ASSET_TYPE);
  }

  @Test
  void shouldDownloadWebResourceAndSupportLastModifiedHeaders() {
    // given
    String filePath = "/configuration.xml";
    String fileContent = "<root />";
    String fileUrl = TestWebServer.uploadXmlFile(filePath, fileContent);

    // when
    sendDownloadRequest(fileUrl, filePath);

    // then
    List<CloudEvent> webResourceEvents = waitForSingleDownloadedResource(EMITTED_WEB_RESOURCE_TYPE);
    assertEvent(webResourceEvents.get(0), filePath, fileContent);

    // when 2
    sendDownloadRequest(fileUrl, filePath);

    // then: expect no re-download
    waitForSingleDownloadedResource(EMITTED_WEB_RESOURCE_TYPE);
  }

  @Test // TODO: when the service is finished, remove this online test
  void shouldSupportLastModifiedHeadersForRealUrl() {
    // given
    String pageUrl = "https://main--arbory-dev--arbory-digital-inc.aem.live/en/";

    // when
    sendDownloadRequest(pageUrl, "/en/");

    // then
    List<CloudEvent> pageEvents = waitForSingleDownloadedResource(EMITTED_PAGE_TYPE);
    CloudEvent downloadedPage = pageEvents.get(0);
    assertThat(downloadedPage.getSubject()).isEqualTo("/en/");

    // when 2
    sendDownloadRequest(pageUrl, "/en/");

    // then: expect no re-download
    waitForSingleDownloadedResource(EMITTED_PAGE_TYPE);
  }

  @Test
  void shouldAutomaticallyUngzipGzippedExternalResource() throws IOException {
    // given
    String imagePath = "/image-2.png";
    byte[] imageContent = new byte[]{0, 1, 2};
    String imageUrl = TestWebServer.uploadGzippedImage(imagePath, gzip(imageContent));

    // when
    sendDownloadRequest(imageUrl, imagePath);

    // then
    List<CloudEvent> assetEvents = waitForSingleDownloadedResource(EMITTED_ASSET_TYPE);
    assertEvent(assetEvents.get(0), imagePath, imageContent);

    // when 2
    sendDownloadRequest(imageUrl, imagePath);

    // then: expect no re-download
    waitForSingleDownloadedResource(EMITTED_ASSET_TYPE);
  }

  @Test
  void shouldSkipProcessingUnexpectedInputEvent() {
    // when
    CloudEvent event1 = CloudEventUtils.eventWithData("payload", Page.TYPE_PUBLISHED, "key");
    httpDownloaderFunction.downloadAndEmit(event1);

    // amd
    CloudEvent event2 = CloudEventUtils.eventWithoutData(Page.TYPE_PUBLISHED, "key");
    httpDownloaderFunction.downloadAndEmit(event2);

    // then: expect no exceptions
  }

  @Test
  void shouldFailDownloadingPageWhenStatusIsNotSuccess() {
    // given
    String pageRelativeUrl = "/pages/test-page-" + TestWebServer.HTTP_500_PAGE_TOKEN + ".html";
    String pageUrl = TestWebServer.uploadPage(pageRelativeUrl, "Something went wrong");

    // when
    sendDownloadRequest(pageUrl, pageRelativeUrl);

    // then
    assertNoDownloadedResources();
  }

  @Test
  void shouldFailDownloadingPageThatDoesNotExist() {
    // given
    String pageRelativeUrl = "/not-existing-page.html";
    String pageUrl = TestWebServer.computeAbsoluteUrl(pageRelativeUrl);

    // when
    sendDownloadRequest(pageUrl, pageRelativeUrl);

    // then
    assertNoDownloadedResources();
  }

  @Test
  void shouldFailDownloadingPageWhenUnreachableServer() {
    // given
    String pageRelativeUrl = "/not-existing-page.html";
    String pageUrl = "http://localhost:12345" + pageRelativeUrl;

    // when
    sendDownloadRequest(pageUrl, pageRelativeUrl);

    // then
    assertNoDownloadedResources();
  }

  private void sendDownloadRequest(String url, String emitKey) {
    DownloadRequest downloadRequest = new DownloadRequest(url, emitKey,
        EMITTED_PAGE_TYPE, EMITTED_WEB_RESOURCE_TYPE, EMITTED_ASSET_TYPE);

    CloudEvent event = CloudEventUtils
        .eventWithData(downloadRequest, DownloadRequest.EVENT_TYPE, emitKey);

    downloadRequestsChannel.send(event);
  }

  private List<CloudEvent> waitForSingleDownloadedResource(String payloadType) {
    AtomicReference<List<CloudEvent>> matchingEventsRef = new AtomicReference<>();
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      List<CloudEvent> matchingEvents = downloadedResourcesSink.received().stream()
          .map(Message::getPayload)
          .filter(event -> CloudEventUtils.getData(event, Resource.class).getType().equals(payloadType))
          .toList();
      assertThat(matchingEvents).hasSize(1);
      matchingEventsRef.set(matchingEvents);
    });
    return matchingEventsRef.get();
  }

  private void assertNoDownloadedResources() {
    await().during(Duration.ofMillis(300)).untilAsserted(() ->
        assertThat(downloadedResourcesSink.received()).isEmpty()
    );
  }

  private static void assertEvent(CloudEvent actualEvent, String expectedSubject,
      String expectedContent) {
    assertEvent(actualEvent, expectedSubject, expectedContent.getBytes(UTF_8));
  }

  private static void assertEvent(CloudEvent actualEvent, String expectedSubject,
      byte[] expectedContent) {
    assertThat(actualEvent.getSubject()).isEqualTo(expectedSubject);

    Resource resource = CloudEventUtils.getData(actualEvent, Resource.class);
    assertThat(resource).isNotNull();

    byte[] resourceContent = resource.getContent().array();
    assertThat(resourceContent).isEqualTo(expectedContent);
  }

  private static byte[] gzip(byte[] content) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
      gzipOutputStream.write(content);
      gzipOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

}