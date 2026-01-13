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
    when(httpClient.execute(any(HttpHead.class))).thenReturn(headHttpResponse);
    when(headHttpResponse.getStatusLine()).thenReturn(headStatusLine);
    when(headHttpResponse.getFirstHeader(HttpHeaders.LAST_MODIFIED))
        .thenReturn(new BasicHeader(HttpHeaders.LAST_MODIFIED, TEST_LAST_MODIFIED_HEADER_VALUE));

    when(httpClient.execute(any(HttpGet.class))).thenReturn(getHttpResponse);
    when(getHttpResponse.getStatusLine()).thenReturn(getStatusLine);
    when(getStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
  }

  @AfterEach
  void resetStore() {
    httpDownloaderFunction.resetStore();
  }

  @Test
  void shouldDownloadRepeatableResourceOnlyOnce() throws IOException {
    // given
    String resourcePath = "/resource-1.json";
    String resourceContent = "{\"foo\": \"bar-1\"}";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, resourceContent);

    configureSubsequentHeadResponseCodes(
        HttpStatus.SC_OK,
        HttpStatus.SC_NOT_MODIFIED // the resource is not modified when 2nd download
    );

    mockGetResponseEntity(resourceContent);

    // when
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then
    CloudEvent webResourceEvent = waitForSingleDownloadedWebResource(resourcePath);
    assertEventContent(webResourceEvent, resourceContent);

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
    String resourceContent = "{\"foo\": \"bar-2\"}";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, resourceContent);

    configureSubsequentHeadResponseCodes(
        HttpStatus.SC_OK,
        HttpStatus.SC_OK,
        HttpStatus.SC_NOT_MODIFIED // the resource is not modified when 3rd download
    );

    mockGetResponseEntity(resourceContent);

    // when
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);
    // when 2
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);
    // when 3
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then
    List<CloudEvent> webResourceEvents = waitForDownloadedWebResources(resourcePath, 2);
    assertEventContent(webResourceEvents.getFirst(), resourceContent);
    assertEventContent(webResourceEvents.get(1), resourceContent);

    // then: expect no re-download
    verify(httpClient, atLeast(3)).execute(any(HttpHead.class));
    verify(httpClient, times(2)).execute(any(HttpGet.class));
    verify(httpEntity, times(2)).getContent();
  }

  @Test
  void shouldStopDownloadingRepeatableResource() throws IOException {
    // given
    String resourcePath = "/resource-3.json";
    String resourceContent = "{\"foo\": \"bar-3\"}";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, resourceContent);

    configureSubsequentHeadResponseCodes(
        HttpStatus.SC_OK // the resource is changed when every download
    );

    mockGetResponseEntity(resourceContent);

    // when
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then: wait for some downloads (3)
    List<CloudEvent> webResourceEvents = waitForDownloadedWebResources(resourcePath, 3);
    webResourceEvents.forEach(e -> assertEventContent(e, resourceContent));

    // and when:
    sendStopRepeatableDownloadRequest(testContentUrl);

    // then: expect no re-download (allow one more download until the stop request gets processed)
    long millisToWait = configuration.repeatIntervalMillis() * 3;
    await().during(Duration.ofMillis(millisToWait)).untilAsserted(() ->
        assertThat(downloadedWebResourcesSink.received()).hasSizeBetween(3, 4)
    );
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

  private void mockGetResponseEntity(String firstResponse) throws IOException {
    when(getHttpResponse.getEntity()).thenReturn(httpEntity);
    when(getHttpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE))
        .thenReturn(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
    doAnswer(invocationOnMock -> new ByteArrayInputStream(firstResponse.getBytes()))
        .when(httpEntity).getContent();
  }

  public static class TestConfig implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "repeatable-download-test";
    }
  }

}