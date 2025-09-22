package dev.streamx.blueprints.externalresources.testutils;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public interface UsesTestWebServer {

  @BeforeAll
  static void startTestWebServer() throws IOException {
    TestWebServer.start();
  }

  @AfterAll
  static void stopTestWebServer() {
    TestWebServer.stop();
  }

  default String uploadPage(String relativeUrl, String content) {
    return TestWebServer.uploadPage(relativeUrl, content);
  }

  default String uploadPage(String relativeUrl, String content, int status) {
    return TestWebServer.uploadPage(relativeUrl, content, status);
  }

  default String uploadSlowPage(String relativeUrl, String content) {
    return TestWebServer.uploadSlowPage(relativeUrl, content);
  }

  default String uploadImage(String relativeUrl, byte[] content) {
    return TestWebServer.uploadImage(relativeUrl, content);
  }

  default String computeAbsoluteUrl(String relativeUrl) {
    return TestWebServer.computeAbsoluteUrl(relativeUrl);
  }

  default int getPort() {
    return TestWebServer.getPort();
  }

}
