package com.streamx.blueprints.resource.downloader;

import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.IntegerRange;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LastModifiedTimestampRegistry {

  private static final IntegerRange SUCCESS_STATUSES = IntegerRange.of(200, 299);

  private static final ZoneId GMT_ZONE = ZoneId.of("GMT");
  private static final DateTimeFormatter formatter = DateTimeFormatter
      .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
  private static final String MIN_GMT_TIMESTAMP = ZonedDateTime.ofInstant(Instant.EPOCH, GMT_ZONE)
      .format(formatter);

  private final Map<String, LastModifiedTimestamp> timestampsStore = new ConcurrentHashMap<>();

  @Inject
  Logger log;

  void storeLastModifiedTimestamp(String url, CloseableHttpResponse response) {
    timestampsStore.put(url, readLastModifiedTimestamp(url, response));
  }

  LastModifiedTimestamp readLastModifiedTimestamp(String url, CloseableHttpResponse response) {
    int status = response.getStatusLine().getStatusCode();
    if (SUCCESS_STATUSES.contains(status) || status == HttpStatus.SC_NOT_MODIFIED) {
      Header lastModifiedHeader = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);
      if (lastModifiedHeader != null) {
        String lastModifiedGmt = lastModifiedHeader.getValue();
        return new LastModifiedTimestamp(lastModifiedGmt, status);
      }
    } else {
      log.debugf("Unexpected HTTP status %d for %s", status, url);
    }

    String timestamp = Optional.ofNullable(timestampsStore.get(url))
        .map(LastModifiedTimestamp::lastModifiedGmt)
        .orElse(MIN_GMT_TIMESTAMP);
    return new LastModifiedTimestamp(timestamp, status);
  }

  @Nullable
  LastModifiedTimestamp get(String url) {
    return timestampsStore.get(url);
  }

  void remove(String url) {
    timestampsStore.remove(url);
  }
}
