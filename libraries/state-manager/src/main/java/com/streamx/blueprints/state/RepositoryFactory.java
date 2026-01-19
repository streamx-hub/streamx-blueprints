package com.streamx.blueprints.state;

import com.streamx.blueprints.state.repository.inmemory.InMemoryRepository;
import com.streamx.blueprints.state.repository.inmemory.InMemoryRepositoryManager;
import com.streamx.blueprints.state.repository.rocksdb.RocksDbRepository;
import org.eclipse.microprofile.config.Config;

public final class RepositoryFactory {

  private RepositoryFactory() {
    // no instances
  }

  public static <T> StateRepository<T> createRepository(Config config, Class<T> valueClass,
      String identifier) {
    String backend = config.getOptionalValue(PropertyNames.STATE_BACKEND, String.class)
        .orElse(InMemoryRepository.BACKEND);
    String instanceId = config.getOptionalValue(PropertyNames.SERVICE_INSTANCE_ID, String.class)
        .orElse("unnamed");
    if (backend.equals(RocksDbRepository.BACKEND)) {
      return new RocksDbRepository<>(config, valueClass, instanceId, identifier);
    }
    if (backend.equals(InMemoryRepository.BACKEND)) {
      return InMemoryRepositoryManager.getOrCreate(instanceId, identifier);
    }
    throw new UnsupportedOperationException("No StateRepository for backend " + backend);
  }

}

