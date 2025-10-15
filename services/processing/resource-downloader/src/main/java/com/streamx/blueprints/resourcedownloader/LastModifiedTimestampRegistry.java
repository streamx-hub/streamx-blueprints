package com.streamx.blueprints.resourcedownloader;

import com.streamx.blueprints.data.DownloadRequest;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
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
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LastModifiedTimestampRegistry {

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

  private static final String LAST_MODIFIED_HEADER = HttpHeaders.LAST_MODIFIED;
  private static final String IF_MODIFIED_SINCE_HEADER = HttpHeaders.IF_MODIFIED_SINCE;

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  CloseableHttpClient httpClient;

  private final Map<String, LastModifiedTimestamp> timestampsStore = new ConcurrentHashMap<>();

  private int headTimeoutMillis;

  @PostConstruct
  void init() {
    headTimeoutMillis = configuration.headTimeoutMilliseconds();
  }

  public void store(DownloadRequest request) {
    String url = request.url();

    HttpHead headRequest = prepareHttpHeadRequest(url);
    try (CloseableHttpResponse response = httpClient.execute(headRequest)) {
      timestampsStore.put(url, getLastModifiedTimestamp(url, response));
    } catch (Exception ex) {
      log.debugf(ex, "Failure performing HEAD for %s", url);
      timestampsStore.put(url, cachedOrMinTimestamp(url, 500));
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
      request.addHeader(IF_MODIFIED_SINCE_HEADER, lastModifiedTimestamp.lastModifiedGmt());
    }
    return request;
  }

  private LastModifiedTimestamp getLastModifiedTimestamp(String url,
      CloseableHttpResponse response) {
    int status = response.getStatusLine().getStatusCode();
    if (SUCCESS_STATUSES.contains(status)) {
      Header lastModifiedHeader = response.getFirstHeader(LAST_MODIFIED_HEADER);
      if (lastModifiedHeader != null) {
        String lastModifiedGmt = lastModifiedHeader.getValue();
        return new LastModifiedTimestamp(lastModifiedGmt, status);
      } else {
        return cachedOrMinTimestamp(url, status);
      }
    } else {
      log.debugf("Unexpected HTTP status %d for %s", status, url);
      return cachedOrMinTimestamp(url, status);
    }
  }

  private LastModifiedTimestamp cachedOrMinTimestamp(String url, int status) {
    LastModifiedTimestamp previousTimestamp = timestampsStore.get(url);
    if (previousTimestamp != null) {
      return new LastModifiedTimestamp(previousTimestamp.lastModifiedGmt(), status);
    } else {
      return new LastModifiedTimestamp(MIN_GMT_TIMESTAMP, status);
    }
  }

  public int getLastHttpHeadStatus(String url) {
    return timestampsStore.get(url).httpHeadStatus();
  }
}
