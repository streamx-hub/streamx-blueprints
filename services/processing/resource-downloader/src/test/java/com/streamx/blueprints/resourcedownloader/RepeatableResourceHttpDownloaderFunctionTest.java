package com.streamx.blueprints.resourcedownloader;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.resourcedownloader.mock.MockWebClientsFactory;
import com.streamx.blueprints.resourcedownloader.testutils.TestWebServer;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(RepeatableResourceHttpDownloaderFunctionTest.Configuration.class)
class RepeatableResourceHttpDownloaderFunctionTest extends AbstractDownloaderFunctionTest {

  private static final String TEST_LAST_MODIFIED_HEADER_VALUE = "Tue, 05 Aug 2025 11:13:45 GMT";

  @Inject
  MockWebClientsFactory mockWebClientsFactory;

  private CloseableHttpClient httpClient;
  private CloseableHttpResponse headHttpResponse;
  private StatusLine headStatusLine;
  private CloseableHttpResponse getHttpResponse;
  private StatusLine getStatusLine;


  @BeforeEach
  void doInit() throws IOException {
    super.init();
    httpClient = mockWebClientsFactory.httpClient();
    headHttpResponse = mock(CloseableHttpResponse.class);
    when(httpClient.execute(any(HttpHead.class))).thenReturn(headHttpResponse);
    headStatusLine = mock(StatusLine.class);
    when(headHttpResponse.getStatusLine()).thenReturn(headStatusLine);
    when(headHttpResponse.getFirstHeader(HttpHeaders.LAST_MODIFIED))
        .thenReturn(new BasicHeader(HttpHeaders.LAST_MODIFIED, TEST_LAST_MODIFIED_HEADER_VALUE));

    getHttpResponse = mock(CloseableHttpResponse.class);
    when(httpClient.execute(any(HttpGet.class))).thenReturn(getHttpResponse);
    getStatusLine = mock(StatusLine.class);
    when(getHttpResponse.getStatusLine()).thenReturn(getStatusLine);
  }

  @Test
  void shouldDownloadRepeatableResourceOnlyOnce() throws IOException {
    // given
    String testPath = "/repeating-test-download-1.json";
    String testContent = """
        {
          "name": "shouldDownloadRepeatableResourceOnlyOnce"
        }
        """;

    when(headStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK, HttpStatus.SC_NOT_MODIFIED);

    when(getStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    HttpEntity httpEntity = mockGetResponseEntity();
    when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(testContent.getBytes()));

    String testContentUrl = TestWebServer.uploadJsonFile(testPath, testContent);

    // when
    sendDownloadRequest(testContentUrl, testPath);

    // then
    List<CloudEvent> webResourceEvents = waitForSingleDownloadedResource(EMITTED_WEB_RESOURCE_TYPE);
    assertEvent(webResourceEvents.get(0), testPath, testContent);

    // when 2
    sendDownloadRequest(testContentUrl, testPath);

    // then: expect no re-download
    waitForSingleDownloadedResource(EMITTED_WEB_RESOURCE_TYPE);
    verify(httpClient, atLeast(3)).execute(any(HttpHead.class));
    verify(httpClient, atLeast(1)).execute(any(HttpGet.class));
    verify(httpEntity, times(1)).getContent();
  }

  @Test
  void shouldDownloadRepeatableResourceTwice() throws IOException {
    // given
    String testPath = "/repeating-test-download-2.json";
    String testContent = """
        {
          "name": "shouldDownloadRepeatableResourceTwice"
        }
        """;

    when(headStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK, HttpStatus.SC_OK,
        HttpStatus.SC_NOT_MODIFIED);

    when(getStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);

    String testContentUrl = TestWebServer.uploadJsonFile(testPath, testContent);
    HttpEntity httpEntity = mockGetResponseEntity();
    when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(testContent.getBytes()),
        new ByteArrayInputStream(testContent.getBytes()));
    // when
    sendDownloadRequest(testContentUrl, testPath);
    // when
    sendDownloadRequest(testContentUrl, testPath);
    // when 3
    sendDownloadRequest(testContentUrl, testPath);

    // then
    List<CloudEvent> webResourceEvents = waitForDownloadedResource(EMITTED_WEB_RESOURCE_TYPE,
        2);
    assertEvent(webResourceEvents.get(0), testPath, testContent);
    assertEvent(webResourceEvents.get(1), testPath, testContent);

    // then: expect no re-download
    verify(httpClient, atLeast(3)).execute(any(HttpHead.class));
    verify(httpClient, times(2)).execute(any(HttpGet.class));
    verify(httpEntity, times(2)).getContent();
  }

  private HttpEntity mockGetResponseEntity() throws IOException {
    HttpEntity httpEntity = mock(HttpEntity.class);
    when(getHttpResponse.getEntity()).thenReturn(httpEntity);
    when(getHttpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE))
        .thenReturn(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
    return httpEntity;
  }

  public static class Configuration implements
      QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "repeating-test-download";
    }
  }

}