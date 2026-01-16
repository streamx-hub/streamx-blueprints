package com.streamx.blueprints.resource.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.resource.downloader.RepeatableResourceHttpDownloaderFunctionTest.TestConfig;
import com.streamx.blueprints.resource.downloader.mock.MockWebClientsFactory;
import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(TestConfig.class)
class RepeatableResourceHttpDownloaderFunctionTest extends AbstractDownloaderFunctionTest {

  private static final String RESOURCE_CONTENT = "{\"foo\": \"bar\"}";
  private static final String TEST_LAST_MODIFIED_HEADER_VALUE = "Tue, 05 Aug 2025 11:13:45 GMT";

  @Inject
  MockWebClientsFactory mockWebClientsFactory;

  @Inject
  HttpDownloaderFunction httpDownloaderFunction;

  @Inject
  Configuration configuration;

  private final CloseableHttpResponse headHttpResponse = mock(CloseableHttpResponse.class);
  private final StatusLine headStatusLine = mock(StatusLine.class);
  private final CloseableHttpResponse getHttpResponse = mock(CloseableHttpResponse.class);
  private final StatusLine getStatusLine = mock(StatusLine.class);
  private final HttpEntity httpEntity = mock(HttpEntity.class);
  private CloseableHttpClient httpClient;

  @BeforeEach
  void doInit() throws IOException {
    httpClient = mockWebClientsFactory.httpClient();
    reset(httpClient);
    configureHttpHeadResponse();
    configureHttpGetResponse();
  }

  private void configureHttpHeadResponse() throws IOException {
    when(httpClient.execute(any(HttpHead.class))).thenReturn(headHttpResponse);
    when(headHttpResponse.getStatusLine()).thenReturn(headStatusLine);
    when(headHttpResponse.getFirstHeader(HttpHeaders.LAST_MODIFIED))
        .thenReturn(new BasicHeader(HttpHeaders.LAST_MODIFIED, TEST_LAST_MODIFIED_HEADER_VALUE));
  }

  private void configureHttpGetResponse() throws IOException {
    when(httpClient.execute(any(HttpGet.class))).thenReturn(getHttpResponse);
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
    httpDownloaderFunction.resetStore();
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
    CloudEvent webResourceEvent = waitForSingleDownloadedWebResource(resourcePath);
    assertEventContent(webResourceEvent);

    // when 2
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then: expect no re-download
    waitForSingleDownloadedWebResource(resourcePath);
    verify(httpClient, atLeast(3)).execute(any(HttpHead.class));
    verify(httpClient, atLeast(1)).execute(any(HttpGet.class));
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
    List<CloudEvent> webResourceEvents = waitForDownloadedWebResources(resourcePath, 2);
    assertEventsContent(webResourceEvents);

    // then: expect no re-download
    verify(httpClient, atLeast(3)).execute(any(HttpHead.class));
    verify(httpClient, times(2)).execute(any(HttpGet.class));
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

    // then: wait for some downloads (3)
    List<CloudEvent> webResourceEvents = waitForDownloadedWebResources(resourcePath, 3);
    assertEventsContent(webResourceEvents);

    // and when:
    sendStopRepeatableDownloadRequest(testContentUrl);
    AtomicLong downloadsCount = new AtomicLong(getDownloadedWebResourcesCount());

    // then: expect eventual stop of new downloads
    Duration pollInterval = Duration.ofMillis(configuration.repeatIntervalMillis() * 3);
    await()
        .atMost(Duration.ofSeconds(3))
        .pollInterval(pollInterval)
        .untilAsserted(() -> {
          long currentDownloadsCount = getDownloadedWebResourcesCount();
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
    sendDownloadRequest(testContentUrl, resourcePath, DownloadRequest.DOWNLOAD_EVENT_TYPE);

    // then: expect repeatable downloads
    List<CloudEvent> webResourceEvents = waitForDownloadedWebResources(resourcePath, 3);
    assertEventsContent(webResourceEvents);
  }

  private void configureSubsequentHeadResponseCodes(Integer first, Integer... next) {
    when(headStatusLine.getStatusCode()).thenReturn(first, next);
  }

  private void sendRepeatableDownloadRequest(String url, String emitKey) {
    sendDownloadRequest(url, emitKey, DownloadRequest.REPEATABLE_DOWNLOAD_EVENT_TYPE);
  }

  private void sendStopRepeatableDownloadRequest(String url) {
    DownloadRequest downloadRequest = new DownloadRequest(url, null, null, null, null);
    sendDownloadRequest(downloadRequest, "any-emit-key",
        DownloadRequest.STOP_REPEATABLE_DOWNLOAD_EVENT_TYPE);
  }

  private void assertEventsContent(List<CloudEvent> events) {
    events.forEach(this::assertEventContent);
  }

  private void assertEventContent(CloudEvent event) {
    assertEventContent(event, RESOURCE_CONTENT);
  }

  public static class TestConfig implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "repeatable-download-test";
    }
  }

}