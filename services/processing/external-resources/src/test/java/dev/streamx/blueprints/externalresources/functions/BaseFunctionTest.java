package dev.streamx.blueprints.externalresources.functions;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.streamx.blueprints.externalresources.registries.LastModifiedTimestampRegistry;
import dev.streamx.blueprints.externalresources.services.HttpDownloader;
import dev.streamx.blueprints.externalresources.testutils.SkipVerifyingEachExternalResourceWasDownloadedExactlyOnce;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

abstract class BaseFunctionTest {

  private final List<String> urlsToDownloadByCurrentTest = new ArrayList<>();

  @InjectSpy
  HttpDownloader downloader;

  @Inject
  @Any
  InMemoryConnector connector;

  protected InMemorySource<CloudEvent> getSource(String channel) {
    return connector.source(channel);
  }

  protected InMemorySink<CloudEvent> getSink(String channel) {
    InMemorySink<CloudEvent> sink = connector.sink(channel);
    sink.clear();
    return sink;
  }

  @BeforeEach
  void resetState() {
    LastModifiedTimestampRegistry.reset();
  }

  protected void mockDownloadResponses(Object... urlAndResponsePairs) {
    for (int i = 0; i < urlAndResponsePairs.length; i += 2) {
      String url = (String) urlAndResponsePairs[i];
      Object response = urlAndResponsePairs[i + 1];
      mockDownloadResponse(url, response);
    }
  }

  protected void mockGzippedDownloadResponse(String url, byte[] response) {
    mockDownloadResponse(url, response, Map.of(HttpDownloader.CONTENT_ENCODING_HEADER, "gzip"));
  }

  protected void mockDownloadResponse(String url, Object response) {
    mockDownloadResponse(url, response, Collections.emptyMap());
  }

  private void mockDownloadResponse(String url, Object response, Map<String, String> headers) {
    urlsToDownloadByCurrentTest.add(url);
    byte[] responseBytes = response instanceof String responseHtml
        ? responseHtml.getBytes(UTF_8)
        : response instanceof byte[] bytes ? bytes : null;

    Buffer bufferMock = mock(Buffer.class);
    doReturn(responseBytes).when(bufferMock).getBytes();

    HttpResponse<Buffer> responseMock = mock(HttpResponse.class);
    doReturn(bufferMock).when(responseMock).body();

    String contentType = detectContentType(url);
    setHeaders(headers, contentType, responseMock);

    doReturn(Uni.createFrom().item(responseMock)).when(downloader).download(url);
  }

  private static void setHeaders(Map<String, String> headers, String contentType,
      HttpResponse<Buffer> responseMock) {

    headers.forEach((key, value) -> doReturn(value).when(responseMock).getHeader(key));
    doReturn(contentType).when(responseMock).getHeader(HttpDownloader.CONTENT_TYPE_HEADER);
  }

  protected void mockDownloadCallsThrowException(String... urls) {
    for (String url : urls) {
      doReturn(Uni.createFrom().failure(new IOException("HTTP 404")))
          .when(downloader).download(url);
    }
  }

  private static String detectContentType(String url) {
    if (StringUtils.containsAny(url, ".html?", ".html#")) {
      return "text/html";
    }
    return switch (FilenameUtils.getExtension(url)) {
      case "html", "" -> "text/html"; // assume URL with no extension is an index page
      case "json" -> "application/json";
      case "xml" -> "application/xml";
      default -> "application/octet-stream";
    };
  }

  protected void verifyDownloadedOnce(String url) {
    verifyDownloadedTimes(url, 1);
  }

  protected void verifyDownloadedTimes(String url, int expectedTimes) {
    verify(downloader, times(expectedTimes)).download(url);
  }

  protected void verifyNoDownloads() {
    verify(downloader, never()).download(any());
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
