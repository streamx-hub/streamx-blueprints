package com.streamx.blueprints.state.repository.inmemory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRepositoryManager {

  private static final Map<String, InMemoryRepository<?>> repositories = new ConcurrentHashMap<>();

  private InMemoryRepositoryManager() {
    // no instances
  }

  @SuppressWarnings("unchecked")
  public static <T> InMemoryRepository<T> getOrCreate(String instanceId, String identifier) {
    String fullIdentifier = instanceId + '\0' + identifier;
    return (InMemoryRepository<T>) repositories.computeIfAbsent(fullIdentifier, i ->
        new InMemoryRepository<>()
    );
  }
}
