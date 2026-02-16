package com.streamx.blueprints.resource.downloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HttpDownloaderFunctionTest extends AbstractDownloaderFunctionTest {

  @Test
  void shouldDownloadImageAndSupportLastModifiedHeaders() {
    // given
    String imagePath = "/image-1.png";
    byte[] imageContent = {0, 1, 2};
    String imageUrl = TestWebServer.uploadImage(imagePath, imageContent);
    configureResourceUnchangedAtSecondDownload(imagePath);

    // when
    sendDownloadRequest(imageUrl, imagePath);

    // then
    waitForSingleDownloadedAsset(imagePath, imageContent);

    // when 2
    sendDownloadRequest(imageUrl, imagePath);

    // then: expect no re-download
    waitForSingleDownloadedAsset(imagePath, imageContent);
  }

  @Test
  void shouldDownloadWebResourceAndSupportLastModifiedHeaders() {
    // given
    String filePath = "/configuration.xml";
    String fileContent = "<root />";
    String fileUrl = TestWebServer.uploadXmlFile(filePath, fileContent);
    configureResourceUnchangedAtSecondDownload(filePath);

    // when
    sendDownloadRequest(fileUrl, filePath);

    // then
    waitForSingleDownloadedWebResource(filePath, fileContent);

    // when 2
    sendDownloadRequest(fileUrl, filePath);

    // then: expect no re-download
    waitForSingleDownloadedWebResource(filePath, fileContent);
  }

  @Test
  void shouldDownloadPageAndSupportLastModifiedHeaders() {
    // given
    String relativeUrl = "/index.html";
    String pageContent = "<html />";
    String fileUrl = TestWebServer.uploadPage(relativeUrl, pageContent);
    configureResourceUnchangedAtSecondDownload(relativeUrl);

    // when
    sendDownloadRequest(fileUrl, relativeUrl);

    // then
    waitForSingleDownloadedPage(relativeUrl, pageContent);

    // when 2
    sendDownloadRequest(fileUrl, relativeUrl);

    // then: expect no re-download
    waitForSingleDownloadedPage(relativeUrl, pageContent);
  }

  @Test
  void shouldAutomaticallyUngzipGzippedExternalResource() throws IOException {
    // given
    String imagePath = "/image-2.png";
    byte[] imageContent = {0, 1, 2};
    String imageUrl = TestWebServer.uploadGzippedImage(imagePath, gzip(imageContent));
    configureResourceUnchangedAtSecondDownload(imagePath);

    // when
    sendDownloadRequest(imageUrl, imagePath);

    // then
    waitForSingleDownloadedAsset(imagePath, imageContent);

    // when 2
    sendDownloadRequest(imageUrl, imagePath);

    // then: expect no re-download
    waitForSingleDownloadedAsset(imagePath, imageContent);
  }

  @Test
  void shouldSkipProcessingEmptyDownloadRequest() {
    // given
    String pageRelativeUrl = "/pages/null-payload-page.html";

    // when
    var event = CloudEventUtils.eventWithoutData(pageRelativeUrl,
        DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE);
    sendDownloadRequest(event);

    // then
    assertNoDownloadedResources();
  }

  @Test
  void shouldFailDownloadingPageWhenStatusIsNotSuccess() {
    // given
    String pageRelativeUrl = "/pages/failing-page.html";
    String pageUrl = TestWebServer.uploadPage(pageRelativeUrl, "Something went wrong");

    // and
    TestWebServer.configureResponseStatus(pageRelativeUrl, HttpStatus.SC_INTERNAL_SERVER_ERROR);

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

  private void configureResourceUnchangedAtSecondDownload(String relativeUrl) {
    TestWebServer.configureResponseStatuses(relativeUrl,
        HttpStatus.SC_OK, HttpStatus.SC_NOT_MODIFIED);
  }
}