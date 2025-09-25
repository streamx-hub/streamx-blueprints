package dev.streamx.blueprints.externalresources.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.testutils.UsesTestWebServer;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpDownloaderTest implements UsesTestWebServer {

  private final Configuration configuration = mock(Configuration.class);
  private final HttpDownloader httpDownloader = new HttpDownloader();

  @BeforeEach
  void init() {
    httpDownloader.configuration = configuration;
    httpDownloader.vertx = Vertx.vertx();
    setDownloadTimeoutMilliseconds(1000);
  }

  private void setDownloadTimeoutMilliseconds(int timeout) {
    doReturn(timeout).when(configuration).externalResourceDownloadTimeoutMilliseconds();
    httpDownloader.init();
  }

  @Test
  void shouldDownloadPage() {
    // given
    String pageRelativeUrl = "/pages/test-page-1.html";
    String pageContent = "<html><body>Page 1</body></html>";
    String pageUrl = uploadPage(pageRelativeUrl, pageContent);

    // when
    byte[] content = download(pageUrl).body().getBytes();

    // then
    assertThat(content).asString().isEqualTo(pageContent);
  }

  @Test
  void shouldFailDownloadingPageWhenTimeoutIsReached() {
    // given
    String pageRelativeUrl = "/pages/test-page-2.html";
    String pageContent = StringUtils.repeat('x', 1_000_000);
    String pageUrl = uploadSlowPage(pageRelativeUrl, pageContent);

    // and
    setDownloadTimeoutMilliseconds(1);

    // when/then
    assertThatThrownBy(() -> download(pageUrl))
        .isInstanceOf(CompletionException.class)
        .cause()
        .isInstanceOf(TimeoutException.class)
        .message()
        .satisfies(message -> assertThat(message).isIn(
            "The timeout of 1 ms has been exceeded"
            + " when getting a connection to localhost:" + getPort(),
            "The timeout period of 1ms has been exceeded"
            + " while executing GET " + pageRelativeUrl + " for server null"
        ));
  }

  @ParameterizedTest
  @ValueSource(ints = {199, 999})
  void shouldFailDownloadingPageWhenStatusIsNotSuccess(int status) {
    // given
    String pageRelativeUrl = "/pages/test-page-" + status + ".html";
    String pageUrl = uploadPage(pageRelativeUrl, "Something went wrong", status);

    // when/then
    assertThatThrownBy(() -> download(pageUrl))
        .isInstanceOf(CompletionException.class)
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("Unexpected HTTP status: " + status);
  }

  @Test
  void shouldFailDownloadingPageThatDoesNotExist() {
    // given
    String pageUrl = computeAbsoluteUrl("/not-existing-page.html");

    // when/then
    assertThatThrownBy(() -> download(pageUrl))
        .isInstanceOf(CompletionException.class)
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("Unexpected HTTP status: 404");
  }

  @Test
  void shouldFailDownloadingPageWhenUnreachableServer() {
    // given
    String pageUrl = "http://localhost:12345/not-existing-page.html";

    // when/then
    assertThatThrownBy(() -> download(pageUrl))
        .isInstanceOf(CompletionException.class)
        .hasRootCauseInstanceOf(ConnectException.class)
        .hasRootCauseMessage("Connection refused");
  }

  private HttpResponse<Buffer> download(String pageUrl) {
    return httpDownloader.download(pageUrl).await().indefinitely();
  }
}