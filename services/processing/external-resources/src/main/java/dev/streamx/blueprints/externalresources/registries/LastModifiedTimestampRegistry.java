package dev.streamx.blueprints.externalresources.registries;

import jakarta.annotation.Nullable;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LastModifiedTimestampRegistry {

  private static final ZoneId GMT_ZONE = ZoneId.of("GMT");
  private static final DateTimeFormatter formatter = DateTimeFormatter
      .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

  private static final Map<String, String> lastModifiedTimestamps = new ConcurrentHashMap<>();

  private LastModifiedTimestampRegistry() {
    // no instances
  }

  public static void put(String resourceUrl, String gmtTimestamp) {
    lastModifiedTimestamps.put(resourceUrl, gmtTimestamp);
  }

  @Nullable
  public static String get(String resourceUrl) {
    return lastModifiedTimestamps.get(resourceUrl);
  }

  public static String getCurrentGmtTimestamp() {
    return ZonedDateTime.now(GMT_ZONE).format(formatter);
  }

  public static void reset() {
    lastModifiedTimestamps.clear();
  }
}
