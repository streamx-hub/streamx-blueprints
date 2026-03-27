package com.streamx.blueprints.resource.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.resource.downloader.testutils.TestWebServer;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.test.unit.StateRepositoryClearer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RepeatableResourceHttpDownloaderFunctionTest extends AbstractDownloaderFunctionTest {

  private static final String RESOURCE_CONTENT = "{\"foo\": \"bar\"}";

  @InjectSpy
  HttpDownloaderFunction httpDownloaderFunction;

  @InjectSpy
  TargetedProvider targetedProvider;

  @Inject
  Configuration configuration;

  @Inject
  RepositoryFactory repositoryFactory;

  @AfterEach
  void resetStore() {
    StateRepositoryClearer.clear(repositoryFactory, "repeatable-downloads", DownloadRequest.class);
  }

  @Test
  void shouldDownloadRepeatableResourceOnlyOnce() throws IOException {
    // given
    String resourcePath = "/resource-1.json";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, RESOURCE_CONTENT);

    TestWebServer.configureResponseStatuses(resourcePath,
        HttpStatus.SC_OK,
        HttpStatus.SC_NOT_MODIFIED // the resource is not modified when 2nd and next downloads
    );

    // when
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then
    waitForSingleDownloadedWebResource(resourcePath, RESOURCE_CONTENT);

    // when 2
    sendRepeatableDownloadRequest(testContentUrl, resourcePath);

    // then: expect no re-download
    waitForSingleDownloadedWebResource(resourcePath, RESOURCE_CONTENT);
    verify(httpDownloaderFunction, atLeast(2)).get(any());
    verify(targetedProvider, times(1)).createCloudEvent(any(), any(), any());
  }

  @Test
  void shouldDownloadRepeatableResourceTwice() throws IOException {
    // given
    String resourcePath = "/resource-2.json";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, RESOURCE_CONTENT);

    TestWebServer.configureResponseStatuses(resourcePath,
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
    verify(httpDownloaderFunction, atLeast(3)).get(any());
    verify(targetedProvider, times(2)).createCloudEvent(any(), any(), any());
  }

  @Test
  void shouldStopDownloadingRepeatableResource() {
    // given
    String resourcePath = "/resource-3.json";
    String testContentUrl = TestWebServer.uploadJsonFile(resourcePath, RESOURCE_CONTENT);

    TestWebServer.configureResponseStatus(resourcePath,
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

    TestWebServer.configureResponseStatus(resourcePath,
        HttpStatus.SC_OK // the resource is changed when every download
    );

    // when: send standard download request but when the URL matches repeatable-url-pattern
    assertThat(resourcePath).matches(configuration.repeatableUrlPattern().get().pattern());
    sendDownloadRequest(testContentUrl, resourcePath, DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE);

    // then: expect repeatable downloads
    waitForAtLeastDownloadedWebResources(resourcePath, 3);
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