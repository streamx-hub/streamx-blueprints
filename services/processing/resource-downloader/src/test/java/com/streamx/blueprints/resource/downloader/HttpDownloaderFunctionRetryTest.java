package com.streamx.blueprints.resource.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.eclipse.microprofile.reactive.messaging.Message;
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

    DownloadRequestSender downloadRequestSender = new DownloadRequestSender(fileUrl, relativeUrl);

    // when: publish download request message and expect it be NACKed
    downloadRequestSender.sendAndExpectNack();

    // then
    assertDownloadAttemptsCount(relativeUrl, 1);
    assertNoDownloadedResources();

    // when: send again
    downloadRequestSender.sendAndExpectAck();

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
    return RandomStringUtils.secure().nextAlphabetic(10);
  }

  private final class DownloadRequestSender {

    private final AtomicInteger nacks = new AtomicInteger(0);
    private final AtomicInteger acks = new AtomicInteger(0);
    private final String fileUrl;
    private final String relativeUrl;

    private DownloadRequestSender(String fileUrl, String relativeUrl) {
      this.fileUrl = fileUrl;
      this.relativeUrl = relativeUrl;
    }

    private void sendAndExpectAck() {
      send();
      await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
          assertThat(acks).hasValue(1)
      );
      assertThat(nacks).hasValue(0);
    }

    private void sendAndExpectNack() {
      send();
      await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
          assertThat(nacks).hasValue(1)
      );
      assertThat(acks).hasValue(0);
    }

    private void send() {
      acks.set(0);
      nacks.set(0);
      connector.source(Channels.DOWNLOAD_REQUESTS).send(createDownloadMessage());
    }

    private Message<CloudEvent> createDownloadMessage() {
      CloudEvent downloadEvent = createDownloadRequestEvent(fileUrl, relativeUrl);
      return Message.of(downloadEvent)
          .withAck(() -> {
            acks.incrementAndGet();
            return CompletableFuture.completedFuture(null);
          })
          .withNack(throwable -> {
            nacks.incrementAndGet();
            return CompletableFuture.completedFuture(null);
          });
    }

    private static CloudEvent createDownloadRequestEvent(String fileUrl, String relativeUrl) {
      DownloadRequest downloadRequest = new DownloadRequest(
          fileUrl,
          relativeUrl,
          EMITTED_PAGE_TYPE, EMITTED_WEB_RESOURCE_TYPE, EMITTED_ASSET_TYPE);
      return CloudEventUtils.eventWithData(
          relativeUrl,
          DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE,
          downloadRequest);
    }
  }

}