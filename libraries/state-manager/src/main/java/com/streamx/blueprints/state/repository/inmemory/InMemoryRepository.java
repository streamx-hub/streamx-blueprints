package com.streamx.blueprints.state.repository.inmemory;

import com.streamx.blueprints.state.StateRepository;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class InMemoryRepository<T> implements StateRepository<T> {

  public static final String BACKEND = "in-memory";

  private final Map<String, T> data = new ConcurrentHashMap<>();

  InMemoryRepository() {

  }

  @Override
  public void put(@Nonnull String key, @Nonnull T value) {
    data.put(key, value);
  }

  @Override
  public T get(@Nonnull String key) {
    return data.get(key);
  }

  @Override
  public Stream<Entry<String, T>> entries() {
    return data.entrySet().stream();
  }

  @Override
  public void remove(@Nonnull String key) {
    data.remove(key);
  }

  @Override
  public void clear() {
    data.clear();
  }
}
