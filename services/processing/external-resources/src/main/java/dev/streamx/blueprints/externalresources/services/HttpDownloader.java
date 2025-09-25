package dev.streamx.blueprints.externalresources.services;

import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.registries.LastModifiedTimestampRegistry;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpDownloader {

  private static final String[] PAGE_CONTENT_TYPES = {
      "application/xhtml+xml",
      "text/html"
  };

  private static final String[] WEB_RESOURCE_CONTENT_TYPES = {
      "application/json",
      "application/xml",
      "application/javascript",
      "text/plain",
      "text/xml",
      "text/javascript",
      "text/css"
  };

  private static final int NOT_MODIFIED_STATUS = HttpResponseStatus.NOT_MODIFIED.code();
  private static final Set<Integer> SUCCESS_STATUSES = Stream.concat(
      IntStream.rangeClosed(200, 299).boxed(),
      IntStream.of(NOT_MODIFIED_STATUS).boxed()
  ).collect(Collectors.toSet());

  public static final String CONTENT_TYPE_HEADER = HttpHeaderNames.CONTENT_TYPE.toString();
  public static final String CONTENT_ENCODING_HEADER = HttpHeaderNames.CONTENT_ENCODING.toString();
  private static final String LAST_MODIFIED_HEADER = HttpHeaderNames.LAST_MODIFIED.toString();
  private static final String IF_MODIFIED_SINCE_HEADER = HttpHeaderNames.IF_MODIFIED_SINCE.toString();

  private int downloadTimeoutMillis;

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  Vertx vertx;

  private WebClient webClient;

  @PostConstruct
  void init() {
    WebClientOptions options = new WebClientOptions().setFollowRedirects(true);
    webClient = WebClient.create(vertx, options);
    downloadTimeoutMillis = configuration.externalResourceDownloadTimeoutMilliseconds();
  }

  public Uni<HttpResponse<Buffer>> download(String url) {
    return Uni.createFrom().emitter(emitter ->
        prepareRequest(url)
            .send()
            .timeout(100 + downloadTimeoutMillis, TimeUnit.MILLISECONDS)
            .onSuccess(response -> {
              int status = response.statusCode();
              if (SUCCESS_STATUSES.contains(status)) {
                emitter.complete(response);
                saveLastModified(response, url);
              } else {
                String exceptionMessage = "Unexpected HTTP status: " + status;
                emitter.fail(new IOException(exceptionMessage));
              }
            })
            .onFailure(emitter::fail));
  }

  private HttpRequest<Buffer> prepareRequest(String url) {
    HttpRequest<Buffer> request = webClient
        .getAbs(url)
        .timeout(downloadTimeoutMillis);
    Optional.ofNullable(LastModifiedTimestampRegistry.get(url))
        .map(lastModified -> request.putHeader(IF_MODIFIED_SINCE_HEADER, lastModified));
    return request;
  }

  public static boolean isUnchanged(HttpResponse<Buffer> response) {
    return response.statusCode() == NOT_MODIFIED_STATUS;
  }

  private void saveLastModified(HttpResponse<Buffer> response, String absoluteUrl) {
    String lastModified = Optional
        .ofNullable(response.getHeader(LAST_MODIFIED_HEADER))
        .orElseGet(LastModifiedTimestampRegistry::getCurrentGmtTimestamp);
    LastModifiedTimestampRegistry.put(absoluteUrl, lastModified);
  }

  public byte[] getResponseBytes(HttpResponse<Buffer> response, String sourceUrl) {
    byte[] bytes = response.body().getBytes();
    if ("gzip".equalsIgnoreCase(response.getHeader(CONTENT_ENCODING_HEADER))) {
      return ungzipBytes(sourceUrl, bytes);
    }
    return bytes;
  }

  private byte[] ungzipBytes(String sourceUrl, byte[] bytes) {
    try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      IOUtils.copy(gzipInputStream, outputStream);
      return outputStream.toByteArray();
    } catch (IOException ex) {
      log.warnf(ex, "Error un-gzipping content for %s", sourceUrl);
      return bytes;
    }
  }

  public static boolean isHtmlPage(HttpResponse<Buffer> response) {
    return contentTypeStartsWithAny(response, PAGE_CONTENT_TYPES);
  }

  public static boolean isWebResource(HttpResponse<Buffer> response) {
    return contentTypeStartsWithAny(response, WEB_RESOURCE_CONTENT_TYPES);
  }

  private static boolean contentTypeStartsWithAny(HttpResponse<Buffer> response,
      String... prefixes) {
    String contentType = response.getHeader(CONTENT_TYPE_HEADER);
    return StringUtils.startsWithAny(contentType, prefixes);
  }

}
