package com.streamx.blueprints.resource.downloader;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LastModifiedTimestampRegistry extends BaseHttpRequestExecutor {

  private static final ZoneId GMT_ZONE = ZoneId.of("GMT");
  private static final DateTimeFormatter formatter = DateTimeFormatter
      .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
  private static final String MIN_GMT_TIMESTAMP = ZonedDateTime.ofInstant(Instant.EPOCH, GMT_ZONE)
      .format(formatter);

  private static final int NOT_MODIFIED_STATUS = HttpStatus.SC_NOT_MODIFIED;
  private static final Set<Integer> SUCCESS_STATUSES = Stream.concat(
      IntStream.rangeClosed(200, 299).boxed(),
      IntStream.of(NOT_MODIFIED_STATUS).boxed()
  ).collect(Collectors.toSet());

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  private final Map<String, LastModifiedTimestamp> timestampsStore = new ConcurrentHashMap<>();

  private int headTimeoutMillis;

  @PostConstruct
  void init() {
    headTimeoutMillis = configuration.headTimeoutMillis();
  }

  public void store(String url) throws DownloadException {
    HttpHead headRequest = prepareHttpHeadRequest(url);
    try (CloseableHttpResponse response = executeHead(headRequest)) {
      timestampsStore.put(url, getLastModifiedTimestamp(url, response));
    } catch (IOException ex) {
      reset(url);
      throw new DownloadException("Exception at HEAD request for " + url, ex);
    }
  }

  private HttpHead prepareHttpHeadRequest(String url) {
    HttpHead request = new HttpHead(url);
    request.setConfig(RequestConfig.copy(RequestConfig.DEFAULT)
        .setConnectTimeout(headTimeoutMillis)
        .setSocketTimeout(headTimeoutMillis)
        .build());

    LastModifiedTimestamp lastModifiedTimestamp = timestampsStore.get(url);
    if (lastModifiedTimestamp != null) {
      request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, lastModifiedTimestamp.lastModifiedGmt());
    }
    return request;
  }

  private LastModifiedTimestamp getLastModifiedTimestamp(String url,
      CloseableHttpResponse response) {
    int status = response.getStatusLine().getStatusCode();
    if (SUCCESS_STATUSES.contains(status)) {
      Header lastModifiedHeader = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);
      if (lastModifiedHeader != null) {
        String lastModifiedGmt = lastModifiedHeader.getValue();
        return new LastModifiedTimestamp(lastModifiedGmt, status);
      }
    } else {
      log.debugf("Unexpected HTTP status %d for %s", status, url);
    }

    String timestamp =  Optional.ofNullable(timestampsStore.get(url))
        .map(LastModifiedTimestamp::lastModifiedGmt)
        .orElse(MIN_GMT_TIMESTAMP);
    return new LastModifiedTimestamp(timestamp, status);
  }

  public int getLastHttpHeadStatus(String url) {
    return timestampsStore.get(url).httpHeadStatus();
  }

  public void reset(String url) {
    timestampsStore.remove(url);
  }
}
