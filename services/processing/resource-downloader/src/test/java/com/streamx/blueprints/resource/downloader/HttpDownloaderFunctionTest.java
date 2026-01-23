package com.streamx.blueprints.resource.downloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HttpDownloaderFunctionTest extends AbstractDownloaderFunctionTest {

  @Test
  void shouldDownloadImageAndSupportLastModifiedHeaders() {
    // given
    String imagePath = "/image-1.png";
    byte[] imageContent = {0, 1, 2};
    String imageUrl = TestWebServer.uploadImage(imagePath, imageContent);

    // when
    sendDownloadRequest(imageUrl, imagePath);

    // then
    CloudEvent assetEvent = waitForSingleDownloadedAsset(imagePath);
    assertEventContent(assetEvent, imageContent);

    // when 2
    sendDownloadRequest(imageUrl, imagePath);

    // then: expect no re-download
    waitForSingleDownloadedAsset(imagePath);
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
    CloudEvent webResourceEvent = waitForSingleDownloadedWebResource(filePath);
    assertEventContent(webResourceEvent, fileContent);

    // when 2
    sendDownloadRequest(fileUrl, filePath);

    // then: expect no re-download
    waitForSingleDownloadedWebResource(filePath);
  }

  @Test
  void shouldDownloadPageAndSupportLastModifiedHeaders() {
    // given
    String relativeUrl = "/index.xml";
    String pageContent = "<html />";
    String fileUrl = TestWebServer.uploadPage(relativeUrl, pageContent);

    // when
    sendDownloadRequest(fileUrl, relativeUrl);

    // then
    CloudEvent pageEvent = waitForSingleDownloadedPage(relativeUrl);
    assertEventContent(pageEvent, pageContent);

    // when 2
    sendDownloadRequest(fileUrl, relativeUrl);

    // then: expect no re-download
    waitForSingleDownloadedPage(relativeUrl);
  }

  @Test
  void shouldAutomaticallyUngzipGzippedExternalResource() throws IOException {
    // given
    String imagePath = "/image-2.png";
    byte[] imageContent = {0, 1, 2};
    String imageUrl = TestWebServer.uploadGzippedImage(imagePath, gzip(imageContent));

    // when
    sendDownloadRequest(imageUrl, imagePath);

    // then
    CloudEvent assetEvent = waitForSingleDownloadedAsset(imagePath);
    assertEventContent(assetEvent, imageContent);

    // when 2
    sendDownloadRequest(imageUrl, imagePath);

    // then: expect no re-download
    waitForSingleDownloadedAsset(imagePath);
  }

  @Test
  void shouldSkipProcessingEmptyDownloadRequest() {
    // given
    String pageRelativeUrl = "/pages/null-payload-page.html";

    // when
    var event = CloudEventUtils.eventWithoutData(pageRelativeUrl,
        DownloadRequest.DOWNLOAD_EVENT_TYPE);
    sendDownloadRequest(event);

    // then
    assertNoDownloadedResources();
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