package com.streamx.blueprints.resourcedownloader.testutils;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.net.MediaType;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;

public class TestWebServer {

  public static final String SLOW_PAGE_TOKEN = "SLOW_PAGE";
  public static final String HTTP_500_PAGE_TOKEN = "HTTP_500_PAGE_TOKEN";

  private static HttpServer httpServer;
  private static int httpPort;

  public static void start() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(0), 0); // random port
    httpPort = httpServer.getAddress().getPort();
    httpServer.start();
  }

  public static void stop() {
    if (httpServer != null) {
      httpServer.stop(0);
    }
  }

  public static String uploadGzippedImage(String relativeUrl, byte[] gzippedContent) {
    return uploadImage(relativeUrl, gzippedContent, true);
  }

  public static String uploadImage(String relativeUrl, byte[] content) {
    return uploadImage(relativeUrl, content, false);
  }

  private static String uploadImage(String relativeUrl, byte[] content, boolean isGzipped) {
    httpServer.createContext(relativeUrl, exchange ->
        handleRequest(exchange, content, isGzipped, MediaType.OCTET_STREAM)
    );
    return computeAbsoluteUrl(relativeUrl);
  }

  public static String uploadXmlFile(String relativeUrl, String content) {
    httpServer.createContext(relativeUrl, exchange ->
        handleStringBodyRequest(exchange, content, MediaType.XML_UTF_8)
    );
    return computeAbsoluteUrl(relativeUrl);
  }

  public static String uploadPage(String relativeUrl, String content) {
    httpServer.createContext(relativeUrl, exchange ->
        handleStringBodyRequest(exchange, content, MediaType.HTML_UTF_8)
    );
    return computeAbsoluteUrl(relativeUrl);
  }

  private static void handleStringBodyRequest(HttpExchange request, String responseBody,
      MediaType contentType) throws IOException {
    handleRequest(request, responseBody.getBytes(UTF_8), false, contentType);
  }

  private static void handleRequest(HttpExchange request, byte[] responseBytes, boolean isGzipped,
      MediaType contentType) throws IOException {
    String requestUrl = request.getRequestURI().toString();
    if (requestUrl.contains(SLOW_PAGE_TOKEN)) {
      simulateSlowProcessing();
    }

    Headers responseHeaders = request.getResponseHeaders();
    responseHeaders.add(HttpHeaders.CONTENT_TYPE, contentType.toString());

    if (isGzipped) {
      responseHeaders.add(HttpHeaders.CONTENT_ENCODING, "gzip");
    }

    Headers requestHeaders = request.getRequestHeaders();
    if (requestHeaders.containsKey(HttpHeaders.IF_MODIFIED_SINCE)) {
      // assuming the caller passes "now" as value of this header and that the page was not edited
      request.sendResponseHeaders(HttpStatus.SC_NOT_MODIFIED, -1);
      return;
    }

    responseHeaders.add(HttpHeaders.LAST_MODIFIED, "Thu, 04 Sep 2025 12:25:10 GMT");

    int status = requestUrl.contains(HTTP_500_PAGE_TOKEN) ? 500 : 200;
    if (request.getRequestMethod().equals("HEAD")) {
      request.sendResponseHeaders(status, -1);
      return;
    }

    request.sendResponseHeaders(status, responseBytes.length);
    try (OutputStream responseBody = request.getResponseBody()) {
      responseBody.write(responseBytes);
    }
  }

  public static String computeAbsoluteUrl(String relativeUrl) {
    return String.format("http://localhost:%d%s", getHttpPort(), relativeUrl);
  }

  public static int getHttpPort() {
    return httpPort;
  }

  private static void simulateSlowProcessing() {
    ThreadUtils.sleepQuietly(Duration.ofMillis(500));
  }
}
