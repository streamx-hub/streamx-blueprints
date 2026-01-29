package com.streamx.blueprints.test.unit;

import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class StateRepositoryClearer {

  private StateRepositoryClearer() {
    // no instances
  }

  public static <T> void clear(RepositoryFactory repositoryFactory, String identifier,
      Class<T> valueClass) {
    StateRepository<T> repository = repositoryFactory.getOrCreate(identifier, valueClass);
    Set<String> allKeys = repository.entries().map(Map.Entry::getKey).collect(Collectors.toSet());
    allKeys.forEach(repository::remove);
  }
}
