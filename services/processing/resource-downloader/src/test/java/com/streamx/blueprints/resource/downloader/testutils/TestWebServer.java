package com.streamx.blueprints.resource.downloader.testutils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.MediaType;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;

public class TestWebServer {

  public static final String SLOW_PAGE_TOKEN = "SLOW_PAGE";

  private static final int DEFAULT_RESPONSE_STATUS = HttpStatus.SC_OK;

  // resource paths and their overridden response statuses (if any)
  private static final Map<String, List<Integer>> uploadedResources = new HashMap<>();

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
    uploadResource(relativeUrl, exchange ->
        handleRequest(exchange, content, isGzipped, MediaType.OCTET_STREAM)
    );
    return computeAbsoluteUrl(relativeUrl);
  }

  public static String uploadXmlFile(String relativeUrl, String content) {
    uploadResource(relativeUrl, exchange ->
        handleStringBodyRequest(exchange, content, MediaType.XML_UTF_8)
    );
    return computeAbsoluteUrl(relativeUrl);
  }

  public static String uploadJsonFile(String relativeUrl, String content) {
    uploadResource(relativeUrl, exchange ->
        handleStringBodyRequest(exchange, content, MediaType.JSON_UTF_8)
    );
    return computeAbsoluteUrl(relativeUrl);
  }

  public static String uploadPage(String relativeUrl, String content) {
    uploadResource(relativeUrl, exchange ->
        handleStringBodyRequest(exchange, content, MediaType.HTML_UTF_8)
    );
    return computeAbsoluteUrl(relativeUrl);
  }

  private static void uploadResource(String relativeUrl, HttpHandler handler) {
    if (uploadedResources.containsKey(relativeUrl)) {
      httpServer.removeContext(relativeUrl);
      uploadedResources.remove(relativeUrl);
    }
    httpServer.createContext(relativeUrl, handler);
    uploadedResources.put(relativeUrl, Collections.emptyList());
  }

  public static void configureResponseStatus(String relativeUrl, int status) {
    configureResponseStatuses(relativeUrl, status);
  }

  /**
   * Last status from the httpStatuses list will be returned for remaining calls
   */
  public static void configureResponseStatuses(String relativeUrl, int first, int... next) {
    assertThat(uploadedResources).containsKey(relativeUrl);
    List<Integer> statusesList = new ArrayList<>();
    statusesList.add(first);
    for (int httpStatus : next) {
      statusesList.add(httpStatus);
    }
    int lastStatus = statusesList.getLast();
    for (int i = 0; i < 1000; i++) {
      statusesList.add(lastStatus);
    }
    uploadedResources.put(relativeUrl, statusesList);
  }

  private static void handleStringBodyRequest(HttpExchange request, String responseBody,
      MediaType contentType) throws IOException {
    handleRequest(request, responseBody.getBytes(), false, contentType);
  }

  private static void handleRequest(HttpExchange request, byte[] responseBytes, boolean isGzipped,
      MediaType contentType) throws IOException {
    String requestUrl = request.getRequestURI().toString();
    if (requestUrl.contains(SLOW_PAGE_TOKEN)) {
      ThreadUtils.sleepQuietly(Duration.ofMillis(500));
    }

    Headers responseHeaders = request.getResponseHeaders();
    responseHeaders.add(HttpHeaders.CONTENT_TYPE, contentType.toString());

    if (isGzipped) {
      responseHeaders.add(HttpHeaders.CONTENT_ENCODING, "gzip");
    }

    responseHeaders.add(HttpHeaders.LAST_MODIFIED, "Thu, 04 Sep 2025 12:25:10 GMT");

    List<Integer> statusCodes = uploadedResources.get(requestUrl);
    int statusCode = statusCodes.isEmpty()
        ? DEFAULT_RESPONSE_STATUS
        : statusCodes.removeFirst();

    if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
      request.sendResponseHeaders(statusCode, -1);
      return;
    }

    request.sendResponseHeaders(statusCode, responseBytes.length);
    try (OutputStream responseBody = request.getResponseBody()) {
      responseBody.write(responseBytes);
    }
  }

  public static String computeAbsoluteUrl(String relativeUrl) {
    return String.format("http://localhost:%d%s", httpPort, relativeUrl);
  }

}
