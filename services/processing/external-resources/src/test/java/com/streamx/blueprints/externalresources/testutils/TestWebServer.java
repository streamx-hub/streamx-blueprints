package com.streamx.blueprints.externalresources.testutils;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.apache.commons.lang3.ThreadUtils;

class TestWebServer {

  private static HttpServer httpServer;
  private static int httpPort;

  static void start() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(0), 0); // random port
    httpPort = httpServer.getAddress().getPort();
    httpServer.start();
  }

  static void stop() {
    if (httpServer != null) {
      httpServer.stop(0);
    }
  }

  static String uploadPage(String relativeUrl, String content) {
    return uploadPageToHttpServer(relativeUrl, content, 200, false);
  }

  static String uploadPage(String relativeUrl, String content, int status) {
    return uploadPageToHttpServer(relativeUrl, content, status, false);
  }

  static String uploadSlowPage(String relativeUrl, String content) {
    return uploadPageToHttpServer(relativeUrl, content, 200, true);
  }

  public static String uploadImage(String relativeUrl, byte[] content) {
    return uploadImageToHttpServer(relativeUrl, content, 200, false);
  }

  private static String uploadPageToHttpServer(String relativeUrl, String content, int status,
      boolean slow) {
    return uploadResourceToHttpServer(relativeUrl, content.getBytes(UTF_8), MediaType.HTML_UTF_8,
        status, slow);
  }

  private static String uploadImageToHttpServer(String relativeUrl, byte[] content, int status,
      boolean slow) {
    return uploadResourceToHttpServer(relativeUrl, content, MediaType.OCTET_STREAM, status, slow);
  }

  private static String uploadResourceToHttpServer(String relativeUrl, byte[] content,
      MediaType mediaType, int status, boolean slow) {
    httpServer.createContext(relativeUrl, exchange -> {
      if (slow) {
        simulateSlowProcessing();
      }

      Headers responseHeaders = exchange.getResponseHeaders();
      responseHeaders.add(HttpHeaders.CONTENT_TYPE, mediaType.type());

      Headers requestHeaders = exchange.getRequestHeaders();
      if (requestHeaders.containsKey(HttpHeaders.IF_MODIFIED_SINCE)) {
        // assuming the caller passes "now" as value of this header and that the page was not edited
        exchange.sendResponseHeaders(HttpResponseStatus.NOT_MODIFIED.code(), -1);
      } else {
        responseHeaders.add(HttpHeaders.LAST_MODIFIED, "Thu, 04 Sep 2025 12:25:10 GMT");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
          responseBody.write(content);
        }
      }
    });
    return computeAbsoluteUrl(relativeUrl);
  }

  private static void simulateSlowProcessing() {
    ThreadUtils.sleepQuietly(Duration.ofMillis(500));
  }

  static String computeAbsoluteUrl(String relativeUrl) {
    return String.format("http://localhost:%d%s", httpPort, relativeUrl);
  }

  static int getPort() {
    return httpPort;
  }
}
