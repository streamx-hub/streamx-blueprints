package com.streamx.blueprints.resource.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.mutiny.Uni;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HttpDownloaderFunctionRetryTest extends AbstractDownloaderFunctionTest {

  private static final String DEFAULT_CONTENT = "<html />";

  @InjectSpy
  HttpDownloaderFunction httpDownloaderFunction;

  @Test
  void shouldRetryDownloadingPageOnHttpGetException() throws Exception {
    // given
    String relativeUrl = "/problematic-page-" + randomString() + ".html";
    String pageUrl = TestWebServer.uploadPage(relativeUrl, DEFAULT_CONTENT);

    // when: make the GET request fail in the first attempt, and succeed in the next attempts
    AtomicInteger counter = new AtomicInteger();

    doAnswer(invocation -> {
      if (counter.getAndIncrement() == 0) {
        return Uni.createFrom().failure(new IOException("GET error!"));
      }
      return invocation.callRealMethod();
    }).when(httpDownloaderFunction).get(pageUrl);

    // then
    shouldDownloadPageInSecondTry(pageUrl, relativeUrl);
  }

  @Test
  void shouldRetryDownloadingPageOnHttpGetUnexpectedStatus() {
    // given
    String relativeUrl = "/problematic-page-" + randomString() + ".html";
    String pageUrl = TestWebServer.uploadPage(relativeUrl, DEFAULT_CONTENT);

    // when: make the GET request return not success, and succeed in the next attempts
    TestWebServer.configureResponseStatuses(relativeUrl,
        HttpStatus.SC_TOO_MANY_REQUESTS, HttpStatus.SC_OK);

    // then
    shouldDownloadPageInSecondTry(pageUrl, relativeUrl);
  }

  private void shouldDownloadPageInSecondTry(String pageUrl, String relativeUrl) {
    // given
    DownloadRequestSender downloadRequestSender = new DownloadRequestSender(pageUrl, relativeUrl);

    // when: publish download request message and expect it be NACKed
    downloadRequestSender.sendAndExpectNack();

    // then
    assertDownloadAttemptsCount(relativeUrl, 1);
    assertNoDownloadedResources();

    // when: send again
    downloadRequestSender.sendAndExpectAck();

    // then: the page should be eventually downloaded
    assertDownloadAttemptsCount(relativeUrl, 2);
    waitForSingleDownloadedPage(relativeUrl, DEFAULT_CONTENT);
  }

  private void assertDownloadAttemptsCount(String relativeUrl, int wantedNumberOfInvocations) {
    awaitUntilAsserted(() ->
        verify(httpDownloaderFunction, times(wantedNumberOfInvocations))
            .downloadAndChooseTarget(argThat(request -> request.url().endsWith(relativeUrl)))
    );
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