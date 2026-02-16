package com.streamx.blueprints.resource.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.test.unit.StateRepositoryClearer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RepeatableResourceHttpDownloaderFunctionTest extends AbstractDownloaderFunctionTest {

  private static final String RESOURCE_CONTENT = "{\"foo\": \"bar\"}";
  private static final String TEST_LAST_MODIFIED_HEADER_VALUE = "Tue, 05 Aug 2025 11:13:45 GMT";

  @InjectSpy
  HttpDownloaderFunction httpDownloaderFunction;

  @InjectSpy
  LastModifiedTimestampRegistry lastModifiedTimestampRegistry;

  @Inject
  Configuration configuration;

  @Inject
  RepositoryFactory repositoryFactory;

  private final CloseableHttpResponse headHttpResponse = mock();
  private final StatusLine headStatusLine = mock();
  private final CloseableHttpResponse getHttpResponse = mock();
  private final StatusLine getStatusLine = mock();
  private final HttpEntity httpEntity = mock();

  @BeforeEach
  void doInit() throws IOException {
    configureHttpHeadResponse();
    configureHttpGetResponse();
  }

  private void configureHttpHeadResponse() throws IOException {
    doReturn(headHttpResponse).when(lastModifiedTimestampRegistry).executeHead(any());
    when(headHttpResponse.getStatusLine()).thenReturn(headStatusLine);
    when(headHttpResponse.getFirstHeader(HttpHeaders.LAST_MODIFIED))
        .thenReturn(new BasicHeader(HttpHeaders.LAST_MODIFIED, TEST_LAST_MODIFIED_HEADER_VALUE));
  }

  private void configureHttpGetResponse() throws IOException {
    doReturn(getHttpResponse).when(httpDownloaderFunction).executeGet(any());
    when(getHttpResponse.getStatusLine()).thenReturn(getStatusLine);
    when(getStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    when(getHttpResponse.getEntity()).thenReturn(httpEntity);
    when(getHttpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE))
        .thenReturn(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
    doAnswer(invocationOnMock -> new ByteArrayInputStream(RESOURCE_CONTENT.getBytes()))
        .when(httpEntity).getContent();
  }

  @AfterEach
  void resetStore() {
    StateRepositoryClearer.clear(repositoryFactory, "repeatable-downloads", DownloadRequest.class);
  }

  @Test
  void shouldDownloadRepeatableResourceOnlyOnce() throws IOException {
    // given
    String resourcePath = "/resource-1.json";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, RESOURCE_CONTENT);

    configureSubsequentHeadResponseCodes(
        HttpStatus.SC_OK,
        HttpStatus.SC_NOT_MODIFIED // the resource is not modified when 2nd download
    );

    // when
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then
    waitForSingleDownloadedWebResource(resourcePath, RESOURCE_CONTENT);

    // when 2
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then: expect no re-download
    waitForSingleDownloadedWebResource(resourcePath, RESOURCE_CONTENT);
    verify(lastModifiedTimestampRegistry, atLeast(3)).executeHead(any());
    verify(httpDownloaderFunction, atLeast(1)).executeGet(any());
    verify(httpEntity, times(1)).getContent();
  }

  @Test
  void shouldDownloadRepeatableResourceTwice() throws IOException {
    // given
    String resourcePath = "/resource-2.json";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, RESOURCE_CONTENT);

    configureSubsequentHeadResponseCodes(
        HttpStatus.SC_OK,
        HttpStatus.SC_OK,
        HttpStatus.SC_NOT_MODIFIED // the resource is not modified when 3rd download
    );

    // when
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);
    // when 2
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);
    // when 3
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then
    waitForDownloadedWebResources(resourcePath, 2, RESOURCE_CONTENT);

    // then: expect no re-download
    verify(lastModifiedTimestampRegistry, atLeast(3)).executeHead(any());
    verify(httpDownloaderFunction, times(2)).executeGet(any());
    verify(httpEntity, times(2)).getContent();
  }

  @Test
  void shouldStopDownloadingRepeatableResource() {
    // given
    String resourcePath = "/resource-3.json";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, RESOURCE_CONTENT);

    configureSubsequentHeadResponseCodes(
        HttpStatus.SC_OK // the resource is changed when every download
    );

    // when
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then: wait for some downloads (3+)
    waitForAtLeastDownloadedWebResources(resourcePath, 3);

    // and when:
    sendStopRepeatableDownloadRequest(testContentUrl);
    AtomicLong downloadsCount = new AtomicLong(getDownloadedWebResourcesCount(resourcePath));

    // then: expect eventual stop of new downloads
    Duration pollInterval = Duration.ofMillis(configuration.repeatIntervalMillis() * 3);
    await()
        .atMost(AWAIT_DURATION)
        .pollInterval(pollInterval)
        .untilAsserted(() -> {
          long currentDownloadsCount = getDownloadedWebResourcesCount(resourcePath);
          try {
            // after 3x the time of repeating download interval - we expect no more downloads
            assertThat(currentDownloadsCount).isEqualTo(downloadsCount.get());
          } finally {
            // this happens if a download was already in process when receiving the stop request
            downloadsCount.set(currentDownloadsCount);
          }
        });
  }

  @Test
  void shouldRepeatablyDownloadResourceIfServiceConfigSaysSo() {
    // given
    String resourcePath = "/repeatable-resource.json";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, RESOURCE_CONTENT);

    configureSubsequentHeadResponseCodes(
        HttpStatus.SC_OK // the resource is changed when every download
    );

    // when: send standard download request but when the URL matches repeatable-url-pattern
    assertThat(resourcePath).matches(configuration.repeatableUrlPattern().get().pattern());
    sendDownloadRequest(testContentUrl, resourcePath, DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE);

    // then: expect repeatable downloads
    waitForAtLeastDownloadedWebResources(resourcePath, 3);
  }

  private void configureSubsequentHeadResponseCodes(Integer first, Integer... next) {
    when(headStatusLine.getStatusCode()).thenReturn(first, next);
  }

  private void sendRepeatableDownloadRequest(String url, String emitKey) {
    sendDownloadRequest(url, emitKey, DownloadRequest.DOWNLOAD_SCHEDULE_EVENT_TYPE);
  }

  private void sendStopRepeatableDownloadRequest(String url) {
    DownloadRequest downloadRequest = new DownloadRequest(url, null, null, null, null);
    sendDownloadRequest(downloadRequest, "any-emit-key",
        DownloadRequest.DOWNLOAD_UNSCHEDULE_EVENT_TYPE);
  }

}