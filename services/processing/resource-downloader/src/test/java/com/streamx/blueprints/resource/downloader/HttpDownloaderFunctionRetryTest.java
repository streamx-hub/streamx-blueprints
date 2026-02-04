package com.streamx.blueprints.resource.downloader;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import java.io.IOException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HttpDownloaderFunctionRetryTest extends AbstractDownloaderFunctionTest {

  @InjectSpy
  HttpDownloaderFunction httpDownloaderFunction;

  @InjectSpy
  LastModifiedTimestampRegistry lastModifiedTimestampRegistry;

  @Test
  void shouldRetryDownloadingPageOnHttpHeadException() throws Exception {
    // when: make the HEAD request fail in the first attempt, and succeed in the next attempts
    doThrow(new IOException("HEAD error!"))
        .doCallRealMethod()
        .when(lastModifiedTimestampRegistry)
        .executeHead(any());

    // then
    shouldDownloadPageInSecondTry();
  }

  @Test
  void shouldRetryDownloadingPageOnHttpGetException() throws Exception {
    // when: make the GET request fail in the first attempt, and succeed in the next attempts
    doThrow(new IOException("GET error!"))
        .doCallRealMethod()
        .when(httpDownloaderFunction)
        .executeGet(any());

    // then
    shouldDownloadPageInSecondTry();
  }

  @Test
  void shouldRetryDownloadingPageOnHttpHeadUnexpectedStatus() throws Exception {
    // when: make the HEAD request return not success, and succeed in the next attempts
    doReturn(unsuccessfulResponse())
        .doCallRealMethod()
        .when(lastModifiedTimestampRegistry)
        .executeHead(any());

    // and: prevent caching unmodified resource (override how underlying TestWebServer works)
    doCallRealMethod()
        .doReturn(HttpStatus.SC_OK)
        .when(lastModifiedTimestampRegistry)
        .getLastHttpHeadStatus(anyString());

    // then
    shouldDownloadPageInSecondTry();
  }

  @Test
  void shouldRetryDownloadingPageOnHttpGetUnexpectedStatus() throws Exception {
    // when: make the GET request return not success, and succeed in the next attempts
    doReturn(unsuccessfulResponse())
        .doCallRealMethod()
        .when(httpDownloaderFunction)
        .executeGet(any());

    // then
    shouldDownloadPageInSecondTry();
  }

  private void shouldDownloadPageInSecondTry() {
    // given
    String relativeUrl = "/problematic-page-" + randomString() + ".html";
    String pageContent = "<html />";
    String fileUrl = TestWebServer.uploadPage(relativeUrl, pageContent);

    // when
    sendDownloadRequest(fileUrl, relativeUrl);

    // then
    assertDownloadAttemptsCount(relativeUrl, 1);
    assertNoDownloadedResources();

    // when 2:
    sendDownloadRequest(fileUrl, relativeUrl);

    // then: the page should be eventually downloaded
    assertDownloadAttemptsCount(relativeUrl, 2);
    waitForSingleDownloadedPage(relativeUrl, pageContent);
  }

  private void assertDownloadAttemptsCount(String relativeUrl, int wantedNumberOfInvocations) {
    awaitUntilAsserted(() ->
        verify(httpDownloaderFunction, times(wantedNumberOfInvocations))
            .downloadAndEmit(argThat(request -> request.url().endsWith(relativeUrl)))
    );
  }

  private static CloseableHttpResponse unsuccessfulResponse() {
    StatusLine statusLine = mock();
    doReturn(HttpStatus.SC_TOO_MANY_REQUESTS).when(statusLine).getStatusCode();

    CloseableHttpResponse response = mock();
    doReturn(statusLine).when(response).getStatusLine();
    return response;
  }

  private static String randomString() {
    return RandomStringUtils.secure().next(10);
  }

}