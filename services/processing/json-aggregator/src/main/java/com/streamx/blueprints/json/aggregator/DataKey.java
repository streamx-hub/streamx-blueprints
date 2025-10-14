package com.streamx.blueprints.json.aggregator;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public record DataKey(String namespace, String id, @Nullable String hash, String originalKey) {

  private static final String KEY_SEPARATOR = ":";

  boolean hasNamespaceAndId() {
    return StringUtils.isNoneEmpty(namespace, id);
  }

  boolean hasHash() {
    return StringUtils.isNotEmpty(hash);
  }

  @Override
  public String toString() {
    return originalKey;
  }

  static DataKey fromKey(String key) {
    String[] parts = key.split(KEY_SEPARATOR);
    String namespace = parts[0];
    String id = parts.length > 1 ? parts[1] : null;
    String hash = parts.length > 2 ? parts[2] : null;
    return new DataKey(namespace, id, hash, key);
  }

  static String fromNamespaceAndId(String namespace, String id) {
    return namespace + KEY_SEPARATOR + id;
  }

  static boolean hasHashAndSameNamespaceAndId(String key, String namespace, String id) {
    return key.startsWith(fromNamespaceAndId(namespace, id) + KEY_SEPARATOR);
  }
}

