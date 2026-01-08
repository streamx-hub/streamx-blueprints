package com.streamx.blueprints.state;

import com.streamx.blueprints.state.repository.inmemory.InMemoryRepository;
import com.streamx.blueprints.state.repository.inmemory.InMemoryRepositoryManager;
import com.streamx.blueprints.state.repository.rocksdb.RocksDbManager;
import com.streamx.blueprints.state.repository.rocksdb.RocksDbRepository;
import org.eclipse.microprofile.config.Config;
import org.rocksdb.RocksDB;

public final class RepositoryFactory {

  private RepositoryFactory() {
    // no instances
  }

  public static <T> StateRepository<T> createRepository(Config config, Class<T> valueClass,
      String identifier) {
    String backend = config.getOptionalValue(PropertyNames.STATE_BACKEND, String.class)
        .orElse(InMemoryRepository.BACKEND);
    if (backend.equals(RocksDbRepository.BACKEND)) {
      RocksDB rocksDb = RocksDbManager.getOrCreateDb(config, identifier);
      return new RocksDbRepository<>(rocksDb, valueClass);
    }
    if (backend.equals(InMemoryRepository.BACKEND)) {
      return InMemoryRepositoryManager.getOrCreate(identifier);
    }
    throw new UnsupportedOperationException("No StateRepository for backend " + backend);
  }

}

