package com.streamx.blueprints.rewriter.functions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.rewriter.data.ExternalResource;
import com.streamx.blueprints.rewriter.services.DownloadRequestsSender;
import com.streamx.blueprints.rewriter.testutils.DownloadedResource;
import com.streamx.blueprints.rewriter.testutils.SkipVerifyingEachExternalResourceWasDownloadedExactlyOnce;
import io.quarkus.test.junit.mockito.InjectSpy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;

abstract class BaseMockedDownloaderTest {

  protected List<DownloadedResource> downloadedPages = new ArrayList<>();
  protected List<DownloadedResource> downloadedWebResources = new ArrayList<>();
  protected List<DownloadedResource> downloadedAssets = new ArrayList<>();

  private final List<String> urlsToDownloadByCurrentTest = new ArrayList<>();

  @InjectSpy
  DownloadRequestsSender downloadRequestsSender;

  protected void mockDownloadResponses(Object... urlAndResponsePairs) {
    for (int i = 0; i < urlAndResponsePairs.length; i += 2) {
      String url = (String) urlAndResponsePairs[i];
      Object response = urlAndResponsePairs[i + 1];
      mockDownloadResponse(url, response);
    }
  }

  protected void mockDownloadResponse(String url, Object response) {
    urlsToDownloadByCurrentTest.add(url);
    byte[] content = response instanceof String s ? s.getBytes() : (byte[]) response;
    doAnswer(invocation -> {
      ExternalResource resource = invocation.getArgument(0);
      String streamxKey = resource.getStreamxKey();
      Class<? extends Resource> resourceType = detectResourceType(url);
      if (resourceType == Page.class) {
        downloadedPages.add(new DownloadedResource(streamxKey, content));
      } else if (resourceType == WebResource.class) {
        downloadedWebResources.add(new DownloadedResource(streamxKey, content));
      } else if (resourceType == Asset.class) {
        downloadedAssets.add(new DownloadedResource(streamxKey, content));
      } else {
        fail("Unhandled resource type: " + resourceType);
      }
      return invocation.callRealMethod();
    }).when(downloadRequestsSender).sendRequest(urlEquals(url));
  }

  protected void mockDownloadResourceFails(String... urls) {
    for (String url : urls) {
      doReturn(CompletableFuture.failedFuture(new RuntimeException("failed to download"))).when(
              downloadRequestsSender)
          .sendRequest(urlEquals(url));
    }
  }

  protected void waitForDownloadedAssets(int expectedCount) {
    waitForDownloadedResources(downloadedAssets, expectedCount);
  }

  protected void waitForDownloadedPages(int expectedCount) {
    waitForDownloadedResources(downloadedPages, expectedCount);
  }

  protected void waitForDownloadedWebResources(int expectedCount) {
    waitForDownloadedResources(downloadedWebResources, expectedCount);
  }

  private void waitForDownloadedResources(List<DownloadedResource> resources, int expectedCount) {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(resources).hasSize(expectedCount)
    );
  }

  protected void assertDownloadedPage(int indexInList, String expectedKey, String expectedContent) {
    assertDownloadedTextResource(downloadedPages, indexInList, expectedKey, expectedContent);
  }

  protected void assertDownloadedWebResource(int indexInList, String expectedKey,
      String expectedContent) {
    assertDownloadedTextResource(downloadedWebResources, indexInList, expectedKey, expectedContent);
  }

  private void assertDownloadedTextResource(List<DownloadedResource> resources, int indexInList,
      String expectedKey, String expectedContent) {
    DownloadedResource resource = resources.get(indexInList);
    assertThat(resource.streamxKey()).isEqualTo(expectedKey);
    assertThat(resource.contentAsString()).isEqualTo(expectedContent);
  }

  protected void assertDownloadedAsset(int indexInList, String expectedKey,
      byte[] expectedContent) {
    DownloadedResource resource = downloadedAssets.get(indexInList);
    assertThat(resource.streamxKey()).isEqualTo(expectedKey);
    assertThat(resource.content()).isEqualTo(expectedContent);
  }

  protected void assertDownloadedAssets(DownloadedResource... expectedAssets) {
    assertDownloadedAssets(List.of(expectedAssets));
  }

  protected void assertDownloadedAssets(List<DownloadedResource> expectedAssets) {
    waitForDownloadedAssets(expectedAssets.size());

    List<DownloadedResource> sortedActualAssets = downloadedAssets.stream()
        .sorted(Comparator.comparing(DownloadedResource::streamxKey))
        .toList();
    assertThat(sortedActualAssets).hasSameSizeAs(expectedAssets);

    List<DownloadedResource> sortedExpectedAssets = expectedAssets.stream()
        .sorted(Comparator.comparing(DownloadedResource::streamxKey))
        .toList();

    for (int i = 0; i < sortedActualAssets.size(); i++) {
      DownloadedResource actualAsset = sortedActualAssets.get(i);
      DownloadedResource expectedAsset = sortedExpectedAssets.get(i);
      assertThat(actualAsset.streamxKey()).isEqualTo(expectedAsset.streamxKey());
      assertThat(actualAsset.content()).isEqualTo(expectedAsset.content());
    }
  }

  protected void verifyDownloadedOnce(String url) {
    verifyDownloadedTimes(url, 1);
  }

  protected void verifyDownloadedTimes(String url, int expectedTimes) {
    verify(downloadRequestsSender, times(expectedTimes)).sendRequest(urlEquals(url));
  }

  protected void verifyNoDownloads() {
    verify(downloadRequestsSender, never()).sendRequest(any());
  }

  private static Class<? extends Resource> detectResourceType(String url) {
    if (StringUtils.containsAny(url, ".html?", ".html#")) {
      return Page.class;
    }
    return switch (FilenameUtils.getExtension(url)) {
      case "html", "" -> Page.class; // assume URL with no extension is an index page
      case "json", "xml" -> WebResource.class;
      default -> Asset.class;
    };
  }

  private static ExternalResource urlEquals(String url) {
    return argThat(
        resource -> resource.getAbsoluteUrl().equals(url)
    );
  }

  @AfterEach
  void verifyEachExternalResourceWasDownloadedExactlyOnce(TestInfo testInfo) {
    if (testInfo.getTestMethod().orElseThrow()
        .isAnnotationPresent(SkipVerifyingEachExternalResourceWasDownloadedExactlyOnce.class)) {
      return;
    }
    for (String url : urlsToDownloadByCurrentTest) {
      verifyDownloadedOnce(url);
    }
  }
}
