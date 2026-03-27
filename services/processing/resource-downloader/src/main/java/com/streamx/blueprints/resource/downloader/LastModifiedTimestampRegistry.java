package com.streamx.blueprints.resource.downloader;

import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.http.HttpHeaders;
import io.vertx.mutiny.ext.web.client.HttpResponse;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LastModifiedTimestampRegistry {

  private static final ZoneId GMT_ZONE = ZoneId.of("GMT");
  private static final DateTimeFormatter formatter = DateTimeFormatter
      .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
  private static final String MIN_GMT_TIMESTAMP = ZonedDateTime.ofInstant(Instant.EPOCH, GMT_ZONE)
      .format(formatter);

  private static final Set<Integer> SUCCESS_STATUSES = Stream.concat(
      IntStream.rangeClosed(200, 299).boxed(),
      IntStream.of(304).boxed()
  ).collect(Collectors.toSet());

  private final Map<String, LastModifiedTimestamp> timestampsStore = new ConcurrentHashMap<>();

  @Inject
  Logger log;

  void storeLastModifiedTimestamp(String url, HttpResponse<Buffer> response) {
    timestampsStore.put(url, readLastModifiedTimestamp(url, response));
  }

  LastModifiedTimestamp readLastModifiedTimestamp(String url, HttpResponse<Buffer> response) {
    int status = response.statusCode();
    if (SUCCESS_STATUSES.contains(status)) {
      String lastModified = response.getHeader(HttpHeaders.LAST_MODIFIED);
      if (lastModified != null) {
        return new LastModifiedTimestamp(lastModified, status);
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
