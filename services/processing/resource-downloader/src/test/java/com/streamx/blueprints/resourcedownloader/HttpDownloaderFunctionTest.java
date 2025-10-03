package com.streamx.blueprints.resourcedownloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.resourcedownloader.testutils.TestWebServer;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HttpDownloaderFunctionTest extends AbstractDownloaderFunctionTest {

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
    CloudEvent event1 = CloudEventUtils.eventWithData("payload",
        Page.TYPE_PUBLISHED, "key");
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

}